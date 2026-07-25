package com.coldboar.coreguard.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun CoreGuardTheme(
    // CoreGuard is dark-only; this parameter is kept for API compatibility and
    // to allow callers to opt-in to system-theme changes in a future light variant.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = darkColorScheme(
        primary        = CyanPrimary,
        onPrimary      = AbyssBlack,
        secondary      = AcidGreen,
        onSecondary    = AbyssBlack,
        tertiary       = CyanVibrant,
        background     = AbyssBlack,
        onBackground   = TextHigh,
        surface        = SurfaceNight,
        onSurface      = TextHigh,
        surfaceVariant = SurfaceGlassSo,
        outline        = CyanGlow,
        outlineVariant = SurfaceLine,
        error          = CrimsonDanger,
        onError        = TextHigh
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = AbyssBlack.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(colorScheme = colors, typography = CoreGuardTypography, content = content)
}
