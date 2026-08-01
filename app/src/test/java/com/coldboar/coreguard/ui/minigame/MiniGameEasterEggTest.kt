package com.coldboar.coreguard.ui.minigame

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniGameEasterEggTest {

    @Test
    fun shouldUnlock_requiresSevenTaps() {
        assertFalse(MiniGameEasterEgg.shouldUnlock(0))
        assertFalse(MiniGameEasterEgg.shouldUnlock(6))
        assertTrue(MiniGameEasterEgg.shouldUnlock(7))
        assertTrue(MiniGameEasterEgg.shouldUnlock(8))
    }

    @Test
    fun unlockTaps_isSeven() {
        assertTrue(MiniGameEasterEgg.UNLOCK_TAPS == 7)
    }
}
