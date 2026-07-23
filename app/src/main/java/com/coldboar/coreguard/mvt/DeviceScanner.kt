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

    fun scan(context: Context): ScanReport {
        val matcher = IocRepository.matcher(context)
        return NemesisScanner(
            matcher = matcher,
            installedPackages = { installedPackages(context) },
            runningProcesses = { readableProcessNames() },
            accessibleFiles = { accessibleFiles(context) }
        ).scan()
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
            context.getExternalFilesDir(null)?.let { add(it) }
            add(context.filesDir)
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
}
