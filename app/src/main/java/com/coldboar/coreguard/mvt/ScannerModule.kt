package com.coldboar.coreguard.mvt

import android.content.Context
import com.coldboar.coreguard.quilla.QuillaMemoryModule
import com.coldboar.coreguard.quilla.QuillaScanBridgeResult

/**
 * Public module façade for on-device Nemesis scanning.
 *
 * UI and other features should call through this surface instead of reaching into
 * scanner internals ([NemesisScanner], IOC loaders, `/proc` walkers).
 *
 * Scan completion is connected to Quilla Memory + the angelic choir via
 * [QuillaMemoryModule.onScanCompleted] (hypotheses, correlator, Elite DTS,
 * Forensic Journal on hits, swarm alert on WARN+).
 */
object ScannerModule {

    @Volatile
    private var lastBridge: QuillaScanBridgeResult? = null

    fun scanDevice(context: Context): ScanReport {
        val report = DeviceScanner.scan(context)
        LastScan.report = report
        // Persist timeline for Gabriel / SpywareScanEvaluator process-death fallback.
        recordHistory(context, report)
        // Bridge Nemesis evidence → Quilla + choir (+ Elite/Swarm side effects).
        lastBridge = QuillaMemoryModule.onScanCompleted(context, report)
        return report
    }

    fun latestReport(): ScanReport? = LastScan.report

    /** Choir / Quilla bridge result from the most recent [scanDevice] in this process. */
    fun lastQuillaBridge(): QuillaScanBridgeResult? =
        lastBridge ?: QuillaMemoryModule.lastScanBridge()

    fun loadHistory(context: Context): List<ScanHistoryStore.ScanRecord> =
        ScanHistoryStore.load(context)

    fun recordHistory(context: Context, report: ScanReport) {
        ScanHistoryStore.append(context, report)
    }
}
