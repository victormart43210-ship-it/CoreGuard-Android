package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.mvt.ArtifactKind
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.Indicator
import com.coldboar.coreguard.mvt.IndicatorType
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ThreatSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuillaIocBridgeTest {

    private lateinit var store: QuillaHypothesisStore
    private lateinit var engine: QuillaCorrelationEngine

    @Before
    fun setUp() {
        store = QuillaHypothesisStore()
        engine = QuillaCorrelationEngine(store, fetcher = { emptyList() })
    }

    @Test
    fun `fromMvtIndicator maps domain type and malware description`() {
        val mvt = Indicator.of(IndicatorType.DOMAIN, "evil.example.com", "Pegasus")!!
        val adapted = QuillaIocBridge.fromMvtIndicator(mvt)
        assertEquals("DOMAIN", adapted.indicatorType)
        assertEquals("evil.example.com", adapted.patternValue)
        assertTrue(adapted.description.contains("Pegasus"))
        assertTrue(adapted.description.contains("MVT-style"))
    }

    @Test
    fun `mergeUnique prefers first occurrence by pattern value`() {
        val a = AmnestyIndicator("a", "DOMAIN", "evil.com", "Amnesty")
        val b = AmnestyIndicator("b", "DOMAIN", "EVIL.COM", "MVT")
        val c = AmnestyIndicator("c", "DOMAIN", "other.com", "Other")
        val merged = QuillaIocBridge.mergeUnique(listOf(a), listOf(b, c))
        assertEquals(2, merged.size)
        assertEquals("a", merged.first { it.patternValue.equals("evil.com", true) }.id)
        assertEquals("c", merged.first { it.patternValue == "other.com" }.id)
    }

    @Test
    fun `recordScanDetections writes MVT_SCAN_IOC_MATCH hypotheses`() {
        val indicator = Indicator.of(IndicatorType.PACKAGE, "com.evil.spy", "Pegasus")!!
        val report = ScanReport(
            startedAtMillis = 1L,
            finishedAtMillis = 2L,
            scannedPackages = 1,
            scannedProcesses = 0,
            scannedFiles = 0,
            indicatorCount = 1,
            detections = listOf(
                Detection(ArtifactKind.PACKAGE, "com.evil.spy", indicator, ThreatSeverity.CRITICAL)
            )
        )
        QuillaIocBridge.recordScanDetections(report, store)
        val hypotheses = store.all()
        assertEquals(1, hypotheses.size)
        assertEquals("MVT_SCAN_IOC_MATCH", hypotheses.first().hypothesisType)
        assertTrue(hypotheses.first().summary.contains("Flagged app"))
        assertEquals(0.95f, hypotheses.first().confidence, 0.001f)
    }

    @Test
    fun `correlateShieldBlock matches MVT parent domain IOC`() {
        engine.loadIndicators(
            listOf(
                AmnestyIndicator(
                    id = "indicator--pegasus",
                    indicatorType = "DOMAIN",
                    patternValue = "evil.com",
                    description = "MVT parent domain"
                )
            )
        )
        QuillaIocBridge.correlateShieldBlock("c2.evil.com", engine)
        val hypotheses = store.all()
        assertEquals(1, hypotheses.size)
        assertEquals("AMNESTY_IOC_BEHAVIORAL_MATCH", hypotheses.first().hypothesisType)
        assertTrue(hypotheses.first().evidenceJson.contains("evil.com"))
    }

    @Test
    fun `correlateScanArtifacts matches package IOC`() {
        engine.loadIndicators(
            listOf(
                AmnestyIndicator(
                    id = "indicator--pkg",
                    indicatorType = "PACKAGE",
                    patternValue = "com.evil.spy",
                    description = "MVT package"
                )
            )
        )
        val indicator = Indicator.of(IndicatorType.PACKAGE, "com.evil.spy", "Pegasus")!!
        val report = ScanReport(
            startedAtMillis = 1L,
            finishedAtMillis = 2L,
            scannedPackages = 1,
            scannedProcesses = 0,
            scannedFiles = 0,
            indicatorCount = 1,
            detections = listOf(
                Detection(ArtifactKind.PACKAGE, "com.evil.spy", indicator, ThreatSeverity.CRITICAL)
            )
        )
        QuillaIocBridge.correlateScanArtifacts(report, engine)
        assertTrue(store.all().any { it.hypothesisType == "AMNESTY_IOC_BEHAVIORAL_MATCH" })
    }
}
