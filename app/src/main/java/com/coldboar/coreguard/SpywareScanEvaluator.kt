package com.coldboar.coreguard

import com.coldboar.coreguard.mvt.LastScan
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict

/**
 * Surfaces the most recent Pegasus/MVT scan verdict as a Security Dashboard
 * check. Reports WARN when no scan has been run yet (the user should run one).
 */
class SpywareScanEvaluator(
    private val lastReport: () -> ScanReport? = { LastScan.report }
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        val report = lastReport()
            ?: return SecurityCheckResult(
                id = "spyware_scan",
                displayName = "Spyware Scan (MVT)",
                state = SecurityCheckState.WARN,
                explanation = "No forensic scan has been run yet. Open the Nemesis Scanner to check for clandestine spyware."
            )

        return when (report.verdict) {
            ScanVerdict.CLEAN -> SecurityCheckResult(
                id = "spyware_scan",
                displayName = "Spyware Scan (MVT)",
                state = SecurityCheckState.PASS,
                explanation = "Last scan matched no spyware indicators across ${report.scannedArtifacts} artifacts."
            )
            ScanVerdict.SUSPICIOUS -> SecurityCheckResult(
                id = "spyware_scan",
                displayName = "Spyware Scan (MVT)",
                state = SecurityCheckState.WARN,
                explanation = "Last scan found ${report.detections.size} suspicious artifact(s). Review the Nemesis Scanner."
            )
            ScanVerdict.INFECTED -> SecurityCheckResult(
                id = "spyware_scan",
                displayName = "Spyware Scan (MVT)",
                state = SecurityCheckState.FAIL,
                explanation = "Last scan detected known spyware indicators. Review the Nemesis Scanner immediately."
            )
        }
    }
}
