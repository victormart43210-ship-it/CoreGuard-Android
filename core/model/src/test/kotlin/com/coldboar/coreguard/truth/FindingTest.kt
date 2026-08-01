package com.coldboar.coreguard.truth

import com.coldboar.coreguard.EvidenceKind
import com.coldboar.coreguard.GuardianScoreEvidence
import com.coldboar.coreguard.SecurityCheckState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Finding], mapper functions, and [formatFindingExplanation].
 * Pure JVM tests — no Android runtime required.
 */
class FindingTest {

    // -----------------------------------------------------------------------
    // EvidenceKind → EvidenceClass mapper
    // -----------------------------------------------------------------------

    @Test
    fun `EvidenceKind VERIFIED maps to EvidenceClass OBSERVED`() {
        assertEquals(EvidenceClass.OBSERVED, EvidenceKind.VERIFIED.toEvidenceClass())
    }

    @Test
    fun `EvidenceKind HEURISTIC maps to EvidenceClass INFERRED`() {
        assertEquals(EvidenceClass.INFERRED, EvidenceKind.HEURISTIC.toEvidenceClass())
    }

    @Test
    fun `EvidenceKind EDUCATIONAL maps to EvidenceClass INFERRED`() {
        assertEquals(EvidenceClass.INFERRED, EvidenceKind.EDUCATIONAL.toEvidenceClass())
    }

    // -----------------------------------------------------------------------
    // EvidenceKind → ConfidenceLevel mapper
    // -----------------------------------------------------------------------

    @Test
    fun `EvidenceKind VERIFIED maps to ConfidenceLevel VERIFIED`() {
        assertEquals(ConfidenceLevel.VERIFIED, EvidenceKind.VERIFIED.toConfidenceLevel())
    }

    @Test
    fun `EvidenceKind HEURISTIC maps to ConfidenceLevel MODERATE`() {
        assertEquals(ConfidenceLevel.MODERATE, EvidenceKind.HEURISTIC.toConfidenceLevel())
    }

    @Test
    fun `EvidenceKind EDUCATIONAL maps to ConfidenceLevel LOW`() {
        assertEquals(ConfidenceLevel.LOW, EvidenceKind.EDUCATIONAL.toConfidenceLevel())
    }

    // -----------------------------------------------------------------------
    // GuardianScoreEvidence → Finding
    // -----------------------------------------------------------------------

    @Test
    fun `GuardianScoreEvidence PASS state maps to INFORMATIONAL severity`() {
        val evidence = makeEvidence(SecurityCheckState.PASS, EvidenceKind.HEURISTIC)
        val finding = evidence.toFinding()
        assertEquals(FindingSeverity.INFORMATIONAL, finding.severity)
    }

    @Test
    fun `GuardianScoreEvidence WARN state maps to MEDIUM severity`() {
        val evidence = makeEvidence(SecurityCheckState.WARN, EvidenceKind.HEURISTIC)
        val finding = evidence.toFinding()
        assertEquals(FindingSeverity.MEDIUM, finding.severity)
    }

    @Test
    fun `GuardianScoreEvidence FAIL state maps to HIGH severity`() {
        val evidence = makeEvidence(SecurityCheckState.FAIL, EvidenceKind.HEURISTIC)
        val finding = evidence.toFinding()
        assertEquals(FindingSeverity.HIGH, finding.severity)
    }

    @Test
    fun `GuardianScoreEvidence VERIFIED confidence maps to OBSERVED class`() {
        val evidence = makeEvidence(SecurityCheckState.PASS, EvidenceKind.VERIFIED)
        val finding = evidence.toFinding()
        assertEquals(EvidenceClass.OBSERVED, finding.evidenceClass)
        assertEquals(ConfidenceLevel.VERIFIED, finding.confidence)
    }

    @Test
    fun `GuardianScoreEvidence toFinding preserves source and id`() {
        val evidence = makeEvidence(SecurityCheckState.FAIL, EvidenceKind.HEURISTIC)
        val finding = evidence.toFinding()
        assertEquals("guardian:${evidence.checkId}", finding.id)
        assertEquals("GuardianScore", finding.source)
    }

    // -----------------------------------------------------------------------
    // formatFindingExplanation structure
    // -----------------------------------------------------------------------

    @Test
    fun `formatFindingExplanation contains all five required sections`() {
        val finding = sampleFinding()
        val text = formatFindingExplanation(finding)

        assertTrue("Missing Conclusion section", text.contains("=== Conclusion ==="))
        assertTrue("Missing Evidence section", text.contains("=== Evidence ==="))
        assertTrue("Missing Confidence section", text.contains("=== Confidence ==="))
        assertTrue("Missing Recommended action section", text.contains("=== Recommended action ==="))
        assertTrue(
            "Missing What could change the conclusion section",
            text.contains("=== What could change the conclusion ===")
        )
    }

    @Test
    fun `formatFindingExplanation includes plain summary in Conclusion`() {
        val finding = sampleFinding()
        val text = formatFindingExplanation(finding)
        assertTrue(text.contains(finding.plainSummary))
    }

    @Test
    fun `formatFindingExplanation includes evidence class in Evidence section`() {
        val finding = sampleFinding()
        val text = formatFindingExplanation(finding)
        assertTrue(text.contains(finding.evidenceClass.name))
    }

    @Test
    fun `formatFindingExplanation includes confidence level name`() {
        val finding = sampleFinding()
        val text = formatFindingExplanation(finding)
        assertTrue(text.contains(finding.confidence.name))
    }

    @Test
    fun `formatFindingExplanation includes recommended response`() {
        val finding = sampleFinding()
        val text = formatFindingExplanation(finding)
        assertTrue(text.contains(finding.recommendedResponse))
    }

    @Test
    fun `formatFindingExplanation includes verification method`() {
        val finding = sampleFinding()
        val text = formatFindingExplanation(finding)
        assertTrue(text.contains(finding.verificationMethod))
    }

    @Test
    fun `formatFindingExplanation includes observed values when present`() {
        val finding = sampleFinding().copy(observedValues = listOf("value_a", "value_b"))
        val text = formatFindingExplanation(finding)
        assertTrue(text.contains("value_a"))
        assertTrue(text.contains("value_b"))
    }

    @Test
    fun `formatFindingExplanation omits observed values line when list is empty`() {
        val finding = sampleFinding().copy(observedValues = emptyList())
        val text = formatFindingExplanation(finding)
        assertTrue(!text.contains("Observed"))
    }

    @Test
    fun `formatFindingExplanation includes threat intel references when present`() {
        val finding = sampleFinding().copy(threatIntelReferences = listOf("https://example.com/ioc"))
        val text = formatFindingExplanation(finding)
        assertTrue(text.contains("https://example.com/ioc"))
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun makeEvidence(
        state: SecurityCheckState,
        kind: EvidenceKind
    ) = GuardianScoreEvidence(
        checkId = "test_check_01",
        displayName = "Test Check",
        state = state,
        explanation = "Test explanation",
        severity = state,
        confidence = kind,
        recommendedAction = "Do something",
        timestampMs = 1_700_000_000_000L
    )

    private fun sampleFinding() = Finding(
        id = "test:finding:001",
        title = "Test Finding",
        plainSummary = "A test finding was detected.",
        technicalDescription = "Technical details here.",
        evidenceClass = EvidenceClass.INFERRED,
        severity = FindingSeverity.MEDIUM,
        confidence = ConfidenceLevel.MODERATE,
        source = "TestScanner",
        timestampMs = 1_700_000_000_000L,
        affectedComponent = "Test Component",
        observedValues = listOf("observed_value"),
        baselineValues = listOf("baseline_value"),
        whyItMatters = "Because it matters.",
        recommendedResponse = "Take this action.",
        verificationMethod = "Run this command to verify.",
        verificationStatus = "Not verified",
        threatIntelReferences = emptyList(),
        androidVisibilityLimits = "Limited to app-visible storage."
    )
}
