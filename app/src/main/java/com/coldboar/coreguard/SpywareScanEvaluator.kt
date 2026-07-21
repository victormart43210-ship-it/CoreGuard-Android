package com.coldboar.coreguard

import com.coldboar.coreguard.mvt.LastScan
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict

/**
 * Surfaces the most recent Nemesis privacy-integrity check as a Security
 * Dashboard item. Reports WARN when no check has been run yet.
 */
class SpywareScanEvaluator(
    private val lastReport: () -> ScanReport? = { LastScan.report }
) : SecurityCheckEvaluator {

    private val name = "Privacy Integrity"

    override fun evaluate(): SecurityCheckResult {
        val report = lastReport()
            ?: return SecurityCheckResult(
                id = "spyware_scan",
                displayName = name,
                state = SecurityCheckState.WARN,
                explanation = "No privacy check has been run yet. Open the Nemesis Scanner to verify your device."
            )

        return when (report.verdict) {
            ScanVerdict.CLEAN -> SecurityCheckResult(
                id = "spyware_scan",
                displayName = name,
                state = SecurityCheckState.PASS,
                explanation = "Last check found nothing flagged across ${report.scannedArtifacts} items."
            )
            ScanVerdict.SUSPICIOUS -> SecurityCheckResult(
                id = "spyware_scan",
                displayName = name,
                state = SecurityCheckState.WARN,
                explanation = "Last check flagged ${report.detections.size} item(s). Review the Nemesis Scanner."
            )
            ScanVerdict.INFECTED -> SecurityCheckResult(
                id = "spyware_scan",
                displayName = name,
                state = SecurityCheckState.FAIL,
                explanation = "Last check found a serious privacy threat. Review the Nemesis Scanner."
            )
        }
    }
}
