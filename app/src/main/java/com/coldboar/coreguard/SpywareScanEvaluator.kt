package com.coldboar.coreguard

import com.coldboar.coreguard.mvt.LastScan
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict

/**
 * Surfaces the most recent Nemesis privacy-integrity check as a Security
 * Dashboard item (Tzadkiel · Mercy Scan evidence). Reports WARN when no check
 * has been run yet. Falls back to [ScanHistoryStore] when process memory lost
 * [LastScan] so the choir/Guardian Score survive process death.
 */
class SpywareScanEvaluator(
    private val lastReport: () -> ScanReport? = { LastScan.report },
    private val historyVerdict: () -> Pair<ScanVerdict, Int>? = { null }
) : SecurityCheckEvaluator {

    private val name = "Privacy Integrity"

    override fun evaluate(): SecurityCheckResult {
        val report = lastReport()
        if (report != null) {
            return fromVerdict(
                verdict = report.verdict,
                detectionCount = report.detections.size,
                scannedArtifacts = report.scannedArtifacts
            )
        }
        val hist = historyVerdict()
        if (hist != null) {
            return fromVerdict(
                verdict = hist.first,
                detectionCount = hist.second,
                scannedArtifacts = null
            )
        }
        return SecurityCheckResult(
            id = "spyware_scan",
            displayName = name,
            state = SecurityCheckState.WARN,
            explanation = "No privacy check has been run yet. Open the Nemesis Scanner to verify your device."
        )
    }

    private fun fromVerdict(
        verdict: ScanVerdict,
        detectionCount: Int,
        scannedArtifacts: Int?
    ): SecurityCheckResult = when (verdict) {
        ScanVerdict.CLEAN -> SecurityCheckResult(
            id = "spyware_scan",
            displayName = name,
            state = SecurityCheckState.PASS,
            explanation = if (scannedArtifacts != null) {
                "Last check found nothing flagged across $scannedArtifacts items."
            } else {
                "Last saved check was CLEAN (from scan history)."
            }
        )
        ScanVerdict.SUSPICIOUS -> SecurityCheckResult(
            id = "spyware_scan",
            displayName = name,
            state = SecurityCheckState.WARN,
            explanation = "Last check flagged $detectionCount item(s). Review the Nemesis Scanner."
        )
        ScanVerdict.INFECTED -> SecurityCheckResult(
            id = "spyware_scan",
            displayName = name,
            state = SecurityCheckState.FAIL,
            explanation = "Last check found a serious privacy threat ($detectionCount hit(s)). Review the Nemesis Scanner."
        )
    }

    companion object {
        /** Production wiring: RAM report first, then newest [ScanHistoryStore] row. */
        fun forContext(context: android.content.Context): SpywareScanEvaluator =
            SpywareScanEvaluator(
                lastReport = { LastScan.report },
                historyVerdict = {
                    ScanHistoryStore.load(context).firstOrNull()?.let { it.verdict to it.detectionCount }
                }
            )
    }
}
