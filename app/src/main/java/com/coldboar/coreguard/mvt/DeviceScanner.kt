package com.coldboar.coreguard.mvt

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File

/**
 * Builds a [NemesisScanner] wired to real Android data sources and runs it.
 *
 * Visibility without root is limited, so we scan:
 *  - every installed application package id,
 *  - process/thread names readable under `/proc` (best-effort),
 *  - file names in app-accessible storage (best-effort).
 */
object DeviceScanner {

    private const val TAG = "DeviceScanner"

    /** Backward-compatible overload — no progress reporting. */
    fun scan(context: Context): ScanReport = scan(context, listener = null)

    /**
     * Scans the device and optionally reports engine-driven progress through
     * [listener]. Each [ScanStage] is reported at start (0.0) and end (1.0).
     */
    fun scan(context: Context, listener: ScanProgressListener?): ScanReport {
        val matcher = IocRepository.matcher(context)

        listener?.onStage(ScanStage.SCANNING_PACKAGES, 0f)
        val packages = installedPackages(context)
        listener?.onStage(ScanStage.SCANNING_PACKAGES, 1f)

        listener?.onStage(ScanStage.SCANNING_PROCESSES, 0f)
        val processes = readableProcessNames()
        listener?.onStage(ScanStage.SCANNING_PROCESSES, 1f)

        listener?.onStage(ScanStage.SCANNING_FILES, 0f)
        val files = accessibleFiles(context)
        listener?.onStage(ScanStage.SCANNING_FILES, 1f)

        listener?.onStage(ScanStage.COMPOSING_VERDICT, 0f)
        val report = NemesisScanner(
            matcher = matcher,
            installedPackages = { packages },
            runningProcesses = { processes },
            accessibleFiles = { files }
        ).scan()
        listener?.onStage(ScanStage.COMPOSING_VERDICT, 1f)

        return report
    }

    private fun installedPackages(context: Context): List<String> = try {
        val pm = context.packageManager
        pm.getInstalledApplications(0).map { it.packageName }
    } catch (t: Throwable) {
        Log.w(TAG, "Package enumeration failed: ${t.message}")
        emptyList()
    }

    /**
     * Best-effort enumeration of process command names from `/proc`. Modern
     * Android hides other processes (hidepid), so this typically only sees our
     * own process and a handful of others — still enough to flag an implant that
     * runs inside or beside the app.
     */
    private fun readableProcessNames(): List<String> {
        val names = LinkedHashSet<String>()
        runCatching {
            val proc = File("/proc")
            proc.listFiles { f -> f.isDirectory && f.name.all(Char::isDigit) }?.forEach { pidDir ->
                runCatching {
                    val cmdline = File(pidDir, "cmdline")
                    if (cmdline.canRead()) {
                        val raw = cmdline.readText().substringBefore('\u0000').trim()
                        if (raw.isNotEmpty()) names += raw
                    }
                    val comm = File(pidDir, "comm")
                    if (comm.canRead()) {
                        val raw = comm.readText().trim()
                        if (raw.isNotEmpty()) names += raw
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Process enumeration failed: ${it.message}") }
        return names.toList()
    }

    private fun accessibleFiles(context: Context): List<String> {
        val roots = buildList {
            context.getExternalFilesDir(null)?.takeIf(::isReadableDirectory)?.let { add(it) }
            context.filesDir.takeIf(::isReadableDirectory)?.let { add(it) }
        }
        val out = mutableListOf<String>()
        roots.forEach { root ->
            runCatching {
                root.walkTopDown().maxDepth(3).forEach { f ->
                    if (f.isFile) out += f.absolutePath
                }
            }
        }
        return out
    }

    private fun isReadableDirectory(file: File): Boolean = file.exists() && file.isDirectory && file.canRead()
}
