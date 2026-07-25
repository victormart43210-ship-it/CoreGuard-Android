package com.coldboar.coreguard

import com.coldboar.coreguard.mvt.ArtifactKind
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.Indicator
import com.coldboar.coreguard.mvt.IndicatorType
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict
import com.coldboar.coreguard.mvt.ThreatSeverity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [SpywareScanEvaluator].
 *
 * Runs on the JVM; no Android context required.
 */
class SpywareScanEvaluatorTest {

    private fun makeReport(
        verdict: ScanVerdict,
        detections: List<Detection> = emptyList(),
        scannedArtifacts: Int = 10
    ) = ScanReport(
        startedAtMillis = 0L,
        finishedAtMillis = 100L,
        scannedPackages = scannedArtifacts,
        scannedProcesses = 0,
        scannedFiles = 0,
        indicatorCount = 5,
        detections = detections
    )

    @Test
    fun `returns WARN when no scan has been run`() {
        val evaluator = SpywareScanEvaluator(lastReport = { null })
        val result = evaluator.evaluate()
        assertEquals("spyware_scan", result.id)
        assertEquals(SecurityCheckState.WARN, result.state)
    }

    @Test
    fun `returns PASS when last scan was CLEAN`() {
        val report = makeReport(ScanVerdict.CLEAN)
        val evaluator = SpywareScanEvaluator(lastReport = { report })
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
    }

    @Test
    fun `returns WARN when last scan was SUSPICIOUS`() {
        val indicator = Indicator(IndicatorType.DOMAIN, "evil.com", "Pegasus")
        val detection = Detection(ArtifactKind.DOMAIN, "evil.com", indicator, ThreatSeverity.HIGH)
        val report = makeReport(ScanVerdict.SUSPICIOUS, listOf(detection))
        val evaluator = SpywareScanEvaluator(lastReport = { report })
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.WARN, result.state)
    }

    @Test
    fun `returns FAIL when last scan was INFECTED`() {
        val indicator = Indicator(IndicatorType.PACKAGE, "com.network.android", "Chrysaor")
        val detection = Detection(ArtifactKind.PACKAGE, "com.network.android", indicator, ThreatSeverity.CRITICAL)
        val report = makeReport(ScanVerdict.INFECTED, listOf(detection))
        val evaluator = SpywareScanEvaluator(lastReport = { report })
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.FAIL, result.state)
    }

    @Test
    fun `explanation is non-empty in all states`() {
        val statesWithReports = listOf<() -> ScanReport?>(
            { null },
            { makeReport(ScanVerdict.CLEAN) },
            { makeReport(ScanVerdict.SUSPICIOUS) },
            { makeReport(ScanVerdict.INFECTED) }
        )
        statesWithReports.forEach { provider ->
            val result = SpywareScanEvaluator(lastReport = provider).evaluate()
            assert(result.explanation.isNotEmpty()) {
                "Expected non-empty explanation for state ${result.state}"
            }
        }
    }
}
