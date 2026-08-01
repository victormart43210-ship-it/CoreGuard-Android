package com.coldboar.coreguard.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Returns false when the user (or emulator gate) has disabled animator duration
 * scale — infinite decorative motion should freeze instead of looping.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale > 0.01f
    }
}

/** Shared premium easing for score / panel enter transitions. */
fun <T> premiumTween(durationMillis: Int = 420) = tween<T>(
    durationMillis = durationMillis,
    easing = FastOutSlowInEasing
)

/** NavHost tab fade when motion is enabled. */
const val MOTION_TAB_FADE_MS = 220

/** NavHost push fade / slide. */
const val MOTION_PUSH_FADE_MS = 260
const val MOTION_PUSH_SLIDE_MS = 300

/** NavHost pop. */
const val MOTION_POP_FADE_MS = 200
const val MOTION_POP_SLIDE_MS = 280
