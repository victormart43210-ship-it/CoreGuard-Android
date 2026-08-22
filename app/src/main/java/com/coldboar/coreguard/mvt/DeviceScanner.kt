package com.coldboar.coreguard.mvt

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CancellationException
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

    data class ScanOptions(
        val deepFileInspectionEnabled: Boolean = true
    )

    fun scan(
        context: Context,
        listener: ScanProgressListener?,
        cancellation: ScanCancellation = ScanCancellation { false },
        options: ScanOptions = ScanOptions()
    ): ScanReport {
        val emit: (ScanStageId, Int?, Int?, String?) -> Unit = { stage, completed, total, limit ->
            listener?.onStage(
                ScanStageEvent(
                    stageId = stage,
                    completedUnits = completed,
                    totalUnits = total,
                    visibilityLimitation = limit
                )
            )
        }
        try {
            cancellation.throwIfCancelled()
            emit(ScanStageId.PREPARING, null, null, "Android sandbox limits visibility to app-accessible surfaces.")

            cancellation.throwIfCancelled()
            emit(ScanStageId.LOADING_INDICATORS, null, null, null)
            val acquisition = IocRepository.acquire(context)
            val matcher = IocMatcher(acquisition.indicators)

            cancellation.throwIfCancelled()
            emit(ScanStageId.ENUMERATING_PACKAGES, null, null, null)
            val packages = installedPackages(context)
            emit(ScanStageId.ENUMERATING_PACKAGES, packages.size, packages.size, null)

            cancellation.throwIfCancelled()
            emit(ScanStageId.CHECKING_PACKAGE_METADATA, packages.size, packages.size, null)

            cancellation.throwIfCancelled()
            emit(
                ScanStageId.CHECKING_INSTALLER_SOURCES,
                null,
                null,
                "Installer source visibility varies by Android version and package visibility policies."
            )

            cancellation.throwIfCancelled()
            emit(
                ScanStageId.CHECKING_CERTIFICATES,
                null,
                null,
                "Certificate/signing lineage exposure is limited on non-rooted Android."
            )

            cancellation.throwIfCancelled()
            emit(ScanStageId.CHECKING_PROCESSES, null, null, "Process data is restricted by Android hidepid protections.")
            val processes = readableProcessNames()
            emit(ScanStageId.CHECKING_PROCESSES, processes.size, processes.size, "Only visible process names were checked.")

            cancellation.throwIfCancelled()
            val files = if (options.deepFileInspectionEnabled) {
                emit(
                    ScanStageId.CHECKING_ACCESSIBLE_FILES,
                    null,
                    null,
                    "Only app-accessible files are visible; system and other-app private files are not inspected."
                )
                val scanned = accessibleFiles(context, cancellation)
                emit(ScanStageId.CHECKING_ACCESSIBLE_FILES, scanned.size, scanned.size, "Scanned app-accessible storage roots only.")
                scanned
            } else {
                emit(
                    ScanStageId.CHECKING_ACCESSIBLE_FILES,
                    0,
                    0,
                    "Skipped by user preference: Inspect app-accessible files is disabled."
                )
                emptyList()
            }

            cancellation.throwIfCancelled()
            emit(ScanStageId.CORRELATING_INDICATORS, null, null, null)
            val report = NemesisScanner(
                matcher = matcher,
                installedPackages = { packages },
                runningProcesses = { processes },
                accessibleFiles = { files }
            ).scan(cancellation = cancellation).copy(
                iocProvenance = acquisition.provenance.copy(
                    feedLoadedAtMs = if (acquisition.provenance.feedLoadedAtMs > 0L) {
                        acquisition.provenance.feedLoadedAtMs
                    } else {
                        acquisition.loadedAtMs
                    },
                    indicatorCount = acquisition.indicators.size
                ),
                indicatorCount = acquisition.indicators.size
            )

            cancellation.throwIfCancelled()
            emit(ScanStageId.BUILDING_FINDINGS, report.detections.size, report.detections.size, null)
            emit(ScanStageId.COMPLETED, null, null, null)
            return report
        } catch (ce: CancellationException) {
            emit(ScanStageId.CANCELLED, null, null, "Scan cancelled before completion.")
            throw ce
        } catch (t: Throwable) {
            emit(ScanStageId.FAILED, null, null, t.message ?: "Unknown scanner failure")
            throw t
        }
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

    private fun accessibleFiles(context: Context, cancellation: ScanCancellation): List<String> {
        val roots = buildList {
            context.getExternalFilesDir(null)?.takeIf(::isReadableDirectory)?.let { add(it) }
            context.filesDir.takeIf(::isReadableDirectory)?.let { add(it) }
        }
        val out = mutableListOf<String>()
        roots.forEach { root ->
            cancellation.throwIfCancelled()
            runCatching {
                root.walkTopDown().maxDepth(3).forEach { f ->
                    cancellation.throwIfCancelled()
                    if (f.isFile) out += f.absolutePath
                }
            }
        }
        return out
    }

    private fun isReadableDirectory(file: File): Boolean = file.exists() && file.isDirectory && file.canRead()
}
