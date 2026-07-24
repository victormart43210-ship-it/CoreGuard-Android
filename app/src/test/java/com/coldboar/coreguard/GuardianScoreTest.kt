package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Test

class GuardianScoreTest {

    private fun result(state: SecurityCheckState) =
        SecurityCheckResult("id", "name", state, "explanation")

    @Test
    fun `all pass scores 100`() {
        val results = List(5) { result(SecurityCheckState.PASS) }
        assertEquals(100, GuardianScore.compute(results))
    }

    @Test
    fun `all fail scores 0`() {
        val results = List(5) { result(SecurityCheckState.FAIL) }
        assertEquals(0, GuardianScore.compute(results))
    }

    @Test
    fun `all warn scores 50`() {
        val results = List(4) { result(SecurityCheckState.WARN) }
        assertEquals(50, GuardianScore.compute(results))
    }

    @Test
    fun `mixed states are weighted equally per check`() {
        // 1 PASS (1.0) + 1 WARN (0.5) + 2 FAIL (0.0) = 1.5 / 4 = 37.5 -> 38
        val results = listOf(
            result(SecurityCheckState.PASS),
            result(SecurityCheckState.WARN),
            result(SecurityCheckState.FAIL),
            result(SecurityCheckState.FAIL)
        )
        assertEquals(38, GuardianScore.compute(results))
    }

    @Test
    fun `typical debug device scores 80`() {
        // 3 PASS + 2 WARN (debug build + unpinned signature) = 4.0 / 5 = 80
        val results = listOf(
            result(SecurityCheckState.PASS),
            result(SecurityCheckState.PASS),
            result(SecurityCheckState.PASS),
            result(SecurityCheckState.WARN),
            result(SecurityCheckState.WARN)
        )
        assertEquals(80, GuardianScore.compute(results))
    }

    @Test
    fun `empty results score 0`() {
        assertEquals(0, GuardianScore.compute(emptyList()))
    }

    @Test
    fun `rank thresholds`() {
        assertEquals(GuardianRank.AEGIS, GuardianScore.rankFor(100))
        assertEquals(GuardianRank.AEGIS, GuardianScore.rankFor(90))
        assertEquals(GuardianRank.WARDED, GuardianScore.rankFor(89))
        assertEquals(GuardianRank.WARDED, GuardianScore.rankFor(65))
        assertEquals(GuardianRank.EXPOSED, GuardianScore.rankFor(64))
        assertEquals(GuardianRank.EXPOSED, GuardianScore.rankFor(35))
        assertEquals(GuardianRank.BREACHED, GuardianScore.rankFor(34))
        assertEquals(GuardianRank.BREACHED, GuardianScore.rankFor(0))
    }
}
