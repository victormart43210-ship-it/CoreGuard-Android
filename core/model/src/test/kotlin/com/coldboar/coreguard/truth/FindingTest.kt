package com.coldboar.coreguard.truth

import com.coldboar.coreguard.EvidenceKind
import com.coldboar.coreguard.GuardianScoreEvidence
import com.coldboar.coreguard.SecurityCheckState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingTest {

    // -----------------------------------------------------------------------
    // EvidenceKind → EvidenceClass mapper
    // -----------------------------------------------------------------------

    @Test
    fun `EvidenceKind VERIFIED maps to OBSERVED`() {
        assertEquals(EvidenceClass.OBSERVED, EvidenceKind.VERIFIED.toEvidenceClass())
    }

    @Test
    fun `EvidenceKind HEURISTIC maps to INFERRED`() {
        assertEquals(EvidenceClass.INFERRED, EvidenceKind.HEURISTIC.toEvidenceClass())
    }

    @Test
    fun `EvidenceKind EDUCATIONAL maps to UNAVAILABLE`() {
        assertEquals(EvidenceClass.UNAVAILABLE, EvidenceKind.EDUCATIONAL.toEvidenceClass())
    }

    // -----------------------------------------------------------------------
    // EvidenceKind → ConfidenceLevel mapper
    // -----------------------------------------------------------------------

    @Test
    fun `EvidenceKind VERIFIED maps to VERIFIED confidence`() {
        assertEquals(ConfidenceLevel.VERIFIED, EvidenceKind.VERIFIED.toConfidenceLevel())
    }

    @Test
    fun `EvidenceKind HEURISTIC maps to MODERATE confidence`() {
        assertEquals(ConfidenceLevel.MODERATE, EvidenceKind.HEURISTIC.toConfidenceLevel())
    }

    @Test
    fun `EvidenceKind EDUCATIONAL maps to LOW confidence`() {
        assertEquals(ConfidenceLevel.LOW, EvidenceKind.EDUCATIONAL.toConfidenceLevel())
    }

    // -----------------------------------------------------------------------
    // SecurityCheckState → FindingSeverity mapper
    // -----------------------------------------------------------------------

    @Test
    fun `SecurityCheckState PASS maps to INFORMATIONAL severity`() {
        assertEquals(FindingSeverity.INFORMATIONAL, SecurityCheckState.PASS.toFindingSeverity())
    }

    @Test
    fun `SecurityCheckState WARN maps to MEDIUM severity`() {
        assertEquals(FindingSeverity.MEDIUM, SecurityCheckState.WARN.toFindingSeverity())
    }

    @Test
    fun `SecurityCheckState FAIL maps to HIGH severity`() {
        assertEquals(FindingSeverity.HIGH, SecurityCheckState.FAIL.toFindingSeverity())
    }

    // -----------------------------------------------------------------------
    // GuardianScoreEvidence → Finding conversion
    // -----------------------------------------------------------------------

    @Test
    fun `GuardianScoreEvidence toFinding preserves checkId as part of id`() {
        val evidence = buildEvidence(id = "root_check", state = SecurityCheckState.FAIL, kind = EvidenceKind.HEURISTIC)
        val finding = evidence.toFinding()
        assertTrue("Finding id should contain checkId", finding.id.contains("root_check"))
    }

    @Test
    fun `GuardianScoreEvidence HEURISTIC FAIL maps to INFERRED HIGH`() {
        val evidence = buildEvidence(state = SecurityCheckState.FAIL, kind = EvidenceKind.HEURISTIC)
        val finding = evidence.toFinding()
        assertEquals(EvidenceClass.INFERRED, finding.evidenceClass)
        assertEquals(FindingSeverity.HIGH, finding.severity)
        assertEquals(ConfidenceLevel.MODERATE, finding.confidence)
    }

    @Test
    fun `GuardianScoreEvidence VERIFIED PASS maps to OBSERVED INFORMATIONAL`() {
        val evidence = buildEvidence(state = SecurityCheckState.PASS, kind = EvidenceKind.VERIFIED)
        val finding = evidence.toFinding()
        assertEquals(EvidenceClass.OBSERVED, finding.evidenceClass)
        assertEquals(FindingSeverity.INFORMATIONAL, finding.severity)
        assertEquals(ConfidenceLevel.VERIFIED, finding.confidence)
    }

    @Test
    fun `GuardianScoreEvidence source is GuardianScore`() {
        val finding = buildEvidence().toFinding()
        assertEquals("GuardianScore", finding.source)
    }

    @Test
    fun `GuardianScoreEvidence timestampMs is preserved`() {
        val ts = 1_700_000_000_000L
        val finding = buildEvidence(timestampMs = ts).toFinding()
        assertEquals(ts, finding.timestampMs)
    }

    // -----------------------------------------------------------------------
    // formatFindingExplanation structure
    // -----------------------------------------------------------------------

    @Test
    fun `formatFindingExplanation contains all five section headers`() {
        val finding = buildFinding()
        val output = formatFindingExplanation(finding)
        assertTrue("Missing Conclusion section", output.contains("=== Conclusion ==="))
        assertTrue("Missing Evidence section", output.contains("=== Evidence ==="))
        assertTrue("Missing Confidence section", output.contains("=== Confidence ==="))
        assertTrue("Missing Recommended action section", output.contains("=== Recommended action ==="))
        assertTrue("Missing What could change section", output.contains("=== What could change the conclusion ==="))
    }

    @Test
    fun `formatFindingExplanation includes title in Conclusion section`() {
        val finding = buildFinding(title = "Unique Title XYZ")
        val output = formatFindingExplanation(finding)
        assertTrue(output.contains("Unique Title XYZ"))
    }

    @Test
    fun `formatFindingExplanation includes severity label`() {
        val finding = buildFinding(severity = FindingSeverity.CRITICAL)
        val output = formatFindingExplanation(finding)
        assertTrue(output.contains("critical severity"))
    }

    @Test
    fun `formatFindingExplanation includes confidence label`() {
        val finding = buildFinding(confidence = ConfidenceLevel.VERIFIED)
        val output = formatFindingExplanation(finding)
        assertTrue(output.contains("verified"))
    }

    @Test
    fun `formatFindingExplanation includes source`() {
        val finding = buildFinding(source = "TestSource")
        val output = formatFindingExplanation(finding)
        assertTrue(output.contains("TestSource"))
    }

    @Test
    fun `formatFindingExplanation includes recommended response`() {
        val finding = buildFinding(recommendedResponse = "Do XYZ immediately.")
        val output = formatFindingExplanation(finding)
        assertTrue(output.contains("Do XYZ immediately."))
    }

    @Test
    fun `formatFindingExplanation includes threat intel references when present`() {
        val finding = buildFinding(threatIntelReferences = listOf("CVE-2024-001", "https://example.com/ioc"))
        val output = formatFindingExplanation(finding)
        assertTrue(output.contains("CVE-2024-001"))
        assertTrue(output.contains("https://example.com/ioc"))
    }

    @Test
    fun `formatFindingExplanation omits references section when none present`() {
        val finding = buildFinding(threatIntelReferences = emptyList())
        val output = formatFindingExplanation(finding)
        assertTrue(!output.contains("References:"))
    }

    @Test
    fun `formatFindingExplanation is deterministic for same input`() {
        val finding = buildFinding()
        assertEquals(formatFindingExplanation(finding), formatFindingExplanation(finding))
    }

    @Test
    fun `formatFindingExplanation UNAVAILABLE evidence shows correct label`() {
        val finding = buildFinding(evidenceClass = EvidenceClass.UNAVAILABLE)
        val output = formatFindingExplanation(finding)
        assertTrue(output.contains("unavailable"))
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildEvidence(
        id: String = "check_001",
        state: SecurityCheckState = SecurityCheckState.WARN,
        kind: EvidenceKind = EvidenceKind.HEURISTIC,
        timestampMs: Long = 0L
    ): GuardianScoreEvidence = GuardianScoreEvidence(
        checkId = id,
        displayName = "Check $id",
        state = state,
        explanation = "Explanation for $id",
        severity = state,
        confidence = kind,
        recommendedAction = "Review $id",
        timestampMs = timestampMs
    )

    private fun buildFinding(
        title: String = "Test Finding",
        severity: FindingSeverity = FindingSeverity.HIGH,
        confidence: ConfidenceLevel = ConfidenceLevel.MODERATE,
        evidenceClass: EvidenceClass = EvidenceClass.INFERRED,
        source: String = "TestSource",
        recommendedResponse: String = "Take action.",
        threatIntelReferences: List<String> = emptyList()
    ): Finding = Finding(
        id = "test.finding.001",
        title = title,
        plainSummary = "Plain summary.",
        technicalDescription = "Technical description.",
        evidenceClass = evidenceClass,
        severity = severity,
        confidence = confidence,
        source = source,
        timestampMs = 0L,
        affectedComponent = "Component A",
        observedValues = listOf("value1"),
        baselineValues = listOf("baseline1"),
        whyItMatters = "Why it matters.",
        recommendedResponse = recommendedResponse,
        verificationMethod = "Check logs.",
        verificationStatus = "Unverified",
        threatIntelReferences = threatIntelReferences,
        androidVisibilityLimits = "Limited visibility without root."
    )
}
