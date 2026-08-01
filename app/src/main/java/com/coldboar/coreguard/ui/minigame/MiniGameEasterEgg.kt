package com.coldboar.coreguard.ui.minigame

/**
 * Hidden unlock for the Quilla mini-game (Settings → About → Version).
 * Tap count is intentionally local UI state — not a security control.
 */
object MiniGameEasterEgg {
    const val UNLOCK_TAPS = 7

    /** Returns true when [tapCount] reaches the unlock threshold. */
    fun shouldUnlock(tapCount: Int): Boolean = tapCount >= UNLOCK_TAPS
}
