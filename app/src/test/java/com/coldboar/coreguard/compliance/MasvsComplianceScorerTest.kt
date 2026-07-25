package com.coldboar.coreguard.compliance

import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MasvsComplianceScorer].
 */
class MasvsComplianceScorerTest {

    @Test
    fun `empty check list yields zero overall score`() {
        val report = MasvsComplianceScorer.score(emptyList())
        assertEquals(0, report.overallScore)
        assertTrue(report.categoryScores.isNotEmpty())
        report.categoryScores.forEach { assertEquals(0, it.score) }
    }

    @Test
    fun `all PASS checks produce high score`() {
        // Overall score averages every MASVS category equally, including empty ones.
        // Cover all mapped categories so a full PASS suite yields a high overall.
        val checks = listOf(
            check("strongbox", SecurityCheckState.PASS),
            check("memory_integrity", SecurityCheckState.PASS),
            check("play_integrity", SecurityCheckState.PASS),
            check("spyware_scan", SecurityCheckState.PASS),
            check("emulator", SecurityCheckState.PASS),
            check("signature", SecurityCheckState.PASS),
            check("debugger", SecurityCheckState.PASS),
            check("frida", SecurityCheckState.PASS),
            check("hook_maps", SecurityCheckState.PASS),
            check("native_debugger", SecurityCheckState.PASS),
            check("mount_integrity", SecurityCheckState.PASS)
        )
        val report = MasvsComplianceScorer.score(checks)
        assertTrue("Expected overall >= 90, got ${report.overallScore}", report.overallScore >= 90)
    }

    @Test
    fun `all FAIL checks produce zero score`() {
        val checks = listOf(
            check("debugger", SecurityCheckState.FAIL),
            check("frida", SecurityCheckState.FAIL),
            check("strongbox", SecurityCheckState.FAIL),
            check("signature", SecurityCheckState.FAIL),
            check("memory_integrity", SecurityCheckState.FAIL)
        )
        val report = MasvsComplianceScorer.score(checks)
        assertEquals(0, report.overallScore)
    }

    @Test
    fun `WARN check contributes half score`() {
        val checks = listOf(check("strongbox", SecurityCheckState.WARN))
        val report = MasvsComplianceScorer.score(checks)
        val storageScore = report.categoryScores.first { it.category == MasvsCategory.STORAGE }.score
        assertEquals(50, storageScore)
    }

    @Test
    fun `unknown check id is silently ignored`() {
        val checks = listOf(check("unknown_future_check", SecurityCheckState.PASS))
        val report = MasvsComplianceScorer.score(checks)
        // Unknown checks contribute no checks to any category
        report.categoryScores.forEach { assertTrue(it.checks.isEmpty()) }
    }

    @Test
    fun `resilience category includes expected checks`() {
        val checks = listOf(
            check("debugger", SecurityCheckState.PASS),
            check("frida", SecurityCheckState.FAIL),
            check("hook_maps", SecurityCheckState.PASS)
        )
        val report = MasvsComplianceScorer.score(checks)
        val resilience = report.categoryScores.first { it.category == MasvsCategory.RESILIENCE }
        assertEquals(3, resilience.checks.size)
        assertEquals(67, resilience.score) // 2/3 PASS = 66.7 → rounds to 67
    }

    @Test
    fun `report includes generation timestamp`() {
        val before = System.currentTimeMillis()
        val report = MasvsComplianceScorer.score(emptyList())
        val after = System.currentTimeMillis()
        assertTrue(report.generatedAtMs in before..after)
    }

    // -------------------------------------------------------------------------

    private fun check(id: String, state: SecurityCheckState) =
        SecurityCheckResult(id = id, displayName = id, state = state, explanation = "")
}
