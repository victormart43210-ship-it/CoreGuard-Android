package com.coldboar.coreguard.attestation

import com.coldboar.coreguard.SecurityCheckState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AttestationEvaluator]. Uses injectable [AttestationResult] fakes.
 */
class AttestationEvaluatorTest {

    @Test
    fun `PASS when MEETS_STRONG_INTEGRITY verdict present`() {
        val result = AttestationEvaluator {
            AttestationResult.Success(setOf(IntegrityVerdicts.MEETS_STRONG_INTEGRITY))
        }.evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
        assertEquals("play_integrity", result.id)
        assertTrue(result.explanation.contains("STRONG", ignoreCase = true))
    }

    @Test
    fun `WARN when only MEETS_BASIC_INTEGRITY verdict present`() {
        val result = AttestationEvaluator {
            AttestationResult.Success(setOf(IntegrityVerdicts.MEETS_BASIC_INTEGRITY))
        }.evaluate()
        assertEquals(SecurityCheckState.WARN, result.state)
        assertTrue(result.explanation.contains("BASIC", ignoreCase = true))
    }

    @Test
    fun `FAIL when success but no positive verdicts`() {
        val result = AttestationEvaluator {
            AttestationResult.Success(emptySet())
        }.evaluate()
        assertEquals(SecurityCheckState.FAIL, result.state)
    }

    @Test
    fun `WARN on attestation failure`() {
        val result = AttestationEvaluator {
            AttestationResult.Failure("Network timeout")
        }.evaluate()
        assertEquals(SecurityCheckState.WARN, result.state)
        assertTrue(result.explanation.contains("Network timeout"))
    }

    @Test
    fun `WARN when API unavailable`() {
        val result = AttestationEvaluator {
            AttestationResult.Unavailable
        }.evaluate()
        assertEquals(SecurityCheckState.WARN, result.state)
        assertTrue(result.explanation.contains("not available", ignoreCase = true))
    }
}
