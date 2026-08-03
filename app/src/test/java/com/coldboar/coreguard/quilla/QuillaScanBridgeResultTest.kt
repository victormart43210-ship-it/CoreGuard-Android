package com.coldboar.coreguard.quilla

import org.junit.Assert.assertTrue
import org.junit.Test

class QuillaScanBridgeResultTest {

    @Test
    fun `scannerBlurb names choir and Tzadkiel`() {
        val blurb = QuillaScanBridgeResult(
            verdict = "SUSPICIOUS",
            detectionCount = 2,
            hypothesisCount = 2,
            choirSeal = "Choir · Watchtowers · active=3 · watching=2 · breached=0",
            blessingsActive = 3,
            blessingsBreached = 0,
            blessingsWatching = 2,
            tzadkielState = "WATCHING",
            tzadkielDetail = "Nemesis SUSPICIOUS (2 hit(s))",
            dtsScore = 42,
            dtsBand = "WATCH",
            journaled = true,
            swarmNotified = true
        ).scannerBlurb()
        assertTrue(blurb.contains("Quilla"))
        assertTrue(blurb.contains("choir"))
        assertTrue(blurb.contains("Tzadkiel"))
        assertTrue(blurb.contains("SUSPICIOUS"))
        assertTrue(blurb.contains("Journaled"))
        assertTrue(blurb.contains("Swarm"))
    }
}
