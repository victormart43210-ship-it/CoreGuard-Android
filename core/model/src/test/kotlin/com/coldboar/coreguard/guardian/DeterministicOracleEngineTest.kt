package com.coldboar.coreguard.guardian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicOracleEngineTest {

    @Test
    fun signatureFailIsHighConfidenceRiskVerified() {
        val finding = DeterministicOracleEngine.explain(
            RawSecuritySignal(
                checkId = "signature",
                displayName = "App signature",
                stateName = "FAIL",
                explanation = "Mismatch"
            )
        )
        assertEquals(Severity.HIGH_CONFIDENCE_RISK, finding.severity)
        assertEquals(Confidence.VERIFIED, finding.confidence)
        assertEquals(EvidenceClass.OBSERVED, finding.primaryEvidenceClass)
        assertTrue(finding.evidence.isNotEmpty())
        assertTrue(finding.possibleBenignCauses.isNotEmpty())
    }

    @Test
    fun passIsProtectedAndInactive() {
        val finding = DeterministicOracleEngine.explain(
            RawSecuritySignal(
                checkId = "debugger",
                displayName = "Debugger",
                stateName = "PASS",
                explanation = "ok"
            )
        )
        assertEquals(Severity.PROTECTED, finding.severity)
        assertEquals(false, finding.active)
    }

    @Test
    fun everyCatalogRuleProducesFinding() {
        OracleRules.catalog.keys.forEach { id ->
            val f = DeterministicOracleEngine.explain(
                RawSecuritySignal(id, id, "WARN", "warn")
            )
            assertEquals("finding-$id", f.id)
            assertTrue(f.whyItMatters.isNotBlank())
        }
    }
}
