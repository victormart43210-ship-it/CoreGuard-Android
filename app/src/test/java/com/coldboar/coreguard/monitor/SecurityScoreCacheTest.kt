package com.coldboar.coreguard.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityScoreCacheTest {

    @Test
    fun `snapshot holds score rank and timestamp`() {
        val snap = SecurityScoreCache.Snapshot(
            score = 88,
            rankLabel = "Mostly protected",
            updatedAtMs = 42L
        )
        assertEquals(88, snap.score)
        assertEquals("Mostly protected", snap.rankLabel)
        assertEquals(42L, snap.updatedAtMs)
    }
}
