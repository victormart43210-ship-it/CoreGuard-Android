package com.coldboar.coreguard.mvt

import android.content.Context

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
        return report
    }

    fun latestReport(): ScanReport? = LastScan.report

    fun loadHistory(context: Context): List<ScanHistoryStore.ScanRecord> =
        ScanHistoryStore.load(context)

    fun recordHistory(context: Context, report: ScanReport) {
        ScanHistoryStore.append(context, report)
    }
}
