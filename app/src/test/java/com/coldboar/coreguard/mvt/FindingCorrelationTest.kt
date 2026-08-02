package com.coldboar.coreguard.mvt

import com.coldboar.coreguard.truth.ConfidenceLevel
import com.coldboar.coreguard.truth.EvidenceClass
import com.coldboar.coreguard.truth.Finding
import com.coldboar.coreguard.truth.FindingSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingCorrelationTest {

    @Test
    fun `duplicate evidence from same source does not increase confidence`() {
        val finding = sampleFinding(
            id = "f1",
            observed = "com.bad.app",
            source = "NemesisScanner",
            component = "Installed application"
        )
        val correlated = correlateFindingsDeterministic(listOf(finding, finding.copy(id = "f1b")))
        assertEquals(1, correlated.size)
        assertEquals(ConfidenceLevel.MODERATE, correlated.first().finding.confidence)
        assertEquals(2, correlated.first().childEvidence.size)
    }

    @Test
    fun `independent sources can increase confidence deterministically`() {
        val a = sampleFinding("a", "artifact-1", "NemesisScanner", "Installed application")
        val b = sampleFinding("b", "artifact-1", "Shield", "DNS event")
        val correlated = correlateFindingsDeterministic(listOf(b, a))
        assertEquals(1, correlated.size)
        assertEquals(ConfidenceLevel.HIGH, correlated.first().finding.confidence)
        assertTrue(correlated.first().childEvidence.map { it.id }.containsAll(listOf("a", "b")))
    }

    private fun sampleFinding(
        id: String,
        observed: String,
        source: String,
        component: String
    ) = Finding(
        id = id,
        title = id,
        plainSummary = id,
        technicalDescription = id,
        evidenceClass = EvidenceClass.OBSERVED,
        severity = FindingSeverity.MEDIUM,
        confidence = ConfidenceLevel.MODERATE,
        source = source,
        timestampMs = 1L,
        affectedComponent = component,
        observedValues = listOf(observed),
        baselineValues = emptyList(),
        whyItMatters = "",
        recommendedResponse = "",
        verificationMethod = "",
        verificationStatus = "NOT_ATTEMPTED",
        threatIntelReferences = emptyList(),
        androidVisibilityLimits = ""
    )
}

