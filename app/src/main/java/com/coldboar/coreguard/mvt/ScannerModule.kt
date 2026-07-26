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

    fun scanDevice(context: Context): ScanReport {
        val report = DeviceScanner.scan(context)
        LastScan.report = report
        // Feed MVT-style scan evidence into Quilla threat intelligence (no network).
        QuillaMemoryFactory.ensureLocalIntel(context)
        QuillaIocBridge.recordScanDetections(report, QuillaMemoryFactory.hypothesisStore())
        QuillaIocBridge.correlateScanArtifacts(report, QuillaMemoryFactory.correlationEngine())
        return report
    }

    fun latestReport(): ScanReport? = LastScan.report

    fun loadHistory(context: Context): List<ScanHistoryStore.ScanRecord> =
        ScanHistoryStore.load(context)

    fun recordHistory(context: Context, report: ScanReport) {
        ScanHistoryStore.append(context, report)
    }
}
