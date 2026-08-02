package com.coldboar.coreguard.mvt

import android.content.Context
import com.coldboar.coreguard.quilla.QuillaIocBridge
import com.coldboar.coreguard.quilla.QuillaMemoryFactory

/**
 * Public module façade for on-device Nemesis scanning.
 *
 * UI and other features should call through this surface instead of reaching into
 * scanner internals ([NemesisScanner], IOC loaders, `/proc` walkers).
 */
object ScannerModule {
    private const val SCANNER_ENGINE_VERSION = "nemesis-engine-v2"
    private const val SCAN_SCHEMA_VERSION = 2

    /**
     * Runs a full device scan and returns the [ScanReport].
     *
     * Existing call sites that do not need progress callbacks use this overload.
     */
    fun scanDevice(context: Context): ScanReport = scanDevice(
        context = context,
        listener = null,
        cancellation = ScanCancellation { false },
        deepFileInspectionEnabled = true,
        quillaCorrelationEnabled = true
    )

    /**
     * Runs a full device scan with optional [ScanProgressListener] callbacks.
     *
     * Progress is reported at real engine checkpoints, not a time-driven fake loop.
     * Pass [listener] = null to omit callbacks (equivalent to the no-arg overload).
     */
    fun scanDevice(
        context: Context,
        listener: ScanProgressListener?,
        cancellation: ScanCancellation,
        deepFileInspectionEnabled: Boolean,
        quillaCorrelationEnabled: Boolean
    ): ScanReport {
        val report = DeviceScanner.scan(
            context = context,
            listener = listener,
            cancellation = cancellation,
            options = DeviceScanner.ScanOptions(
                deepFileInspectionEnabled = deepFileInspectionEnabled
            )
        )
        LastScan.report = report

        // Feed MVT-style scan evidence into Quilla threat intelligence (no network).
        QuillaMemoryFactory.ensureLocalIntel(context)
        QuillaIocBridge.recordScanDetections(report, QuillaMemoryFactory.hypothesisStore())
        if (quillaCorrelationEnabled) {
            QuillaIocBridge.correlateScanArtifacts(report, QuillaMemoryFactory.correlationEngine())
        }
        return report
    }

    fun scannerEngineVersion(): String = SCANNER_ENGINE_VERSION
    fun scanSchemaVersion(): Int = SCAN_SCHEMA_VERSION

    fun latestReport(): ScanReport? = LastScan.report

    /**
     * Epoch millis when the active IOC indicator set was last loaded from disk,
     * or 0 if it has not been loaded yet. Useful for surfacing "signatures last
     * refreshed N hours ago" in the Scanner UI without exposing [IocRepository]
     * internals.
     */
    fun iocLoadedAtMs(): Long = IocRepository.loadedAtMs()

    fun loadHistory(context: Context): List<ScanHistoryStore.ScanRecord> =
        ScanHistoryStore.load(context)

    fun recordHistory(context: Context, report: ScanReport) {
        ScanHistoryStore.append(context, report)
    }
}
