package com.coldboar.coreguard.truth

import com.coldboar.coreguard.mvt.ArtifactKind
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.Indicator
import com.coldboar.coreguard.mvt.IndicatorType
import com.coldboar.coreguard.mvt.ThreatSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionMapperTest {

    @Test
    fun `ThreatSeverity CRITICAL maps to FindingSeverity CRITICAL`() {
        assertEquals(FindingSeverity.CRITICAL, ThreatSeverity.CRITICAL.toFindingSeverity())
    }

    @Test
    fun `ThreatSeverity HIGH maps to FindingSeverity HIGH`() {
        assertEquals(FindingSeverity.HIGH, ThreatSeverity.HIGH.toFindingSeverity())
    }

    @Test
    fun `ThreatSeverity MEDIUM maps to FindingSeverity MEDIUM`() {
        assertEquals(FindingSeverity.MEDIUM, ThreatSeverity.MEDIUM.toFindingSeverity())
    }

    @Test
    fun `Detection toFinding maps core fields`() {
        val finding = buildDetection(
            kind = ArtifactKind.PACKAGE,
            severity = ThreatSeverity.CRITICAL
        ).toFinding(timestampMs = 1234L)

        assertTrue(finding.id.startsWith("nemesis.package."))
        assertEquals(EvidenceClass.OBSERVED, finding.evidenceClass)
        assertEquals(FindingSeverity.CRITICAL, finding.severity)
        assertEquals(ConfidenceLevel.MODERATE, finding.confidence)
        assertEquals("NemesisScanner", finding.source)
        assertEquals(1234L, finding.timestampMs)
        assertTrue(finding.observedValues.contains("com.spy.app"))
    }

    @Test
    fun `Detection toFinding carries threat intel references when present`() {
        val finding = buildDetection(reference = "https://example.com/ioc").toFinding()
        assertEquals(listOf("https://example.com/ioc"), finding.threatIntelReferences)
    }

    private fun buildDetection(
        kind: ArtifactKind = ArtifactKind.FILE,
        severity: ThreatSeverity = ThreatSeverity.HIGH,
        reference: String? = null
    ): Detection = Detection(
        kind = kind,
        artifact = "com.spy.app",
        indicator = Indicator(
            type = IndicatorType.PACKAGE,
            value = "com.spy.app",
            malware = "Pegasus",
            reference = reference
        ),
        severity = severity
    )
}
