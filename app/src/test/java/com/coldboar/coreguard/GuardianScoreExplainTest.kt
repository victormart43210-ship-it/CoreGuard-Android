package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianScoreExplainTest {

    @Test
    fun `explain marks signature checks verified and others heuristic`() {
        val results = listOf(
            SecurityCheckResult("signature", "Signature", SecurityCheckState.PASS, "ok"),
            SecurityCheckResult("overlay_abuse", "Overlay", SecurityCheckState.WARN, "review")
        )
        val rows = GuardianScore.explain(results, timestampMs = 123L)
        assertEquals(EvidenceKind.VERIFIED, rows[0].confidence)
        assertEquals(EvidenceKind.HEURISTIC, rows[1].confidence)
        assertEquals(123L, rows[0].timestampMs)
        assertTrue(rows[1].recommendedAction.isNotBlank())
    }

    @Test
    fun `empty results score zero`() {
        assertEquals(0, GuardianScore.compute(emptyList()))
    }
}
