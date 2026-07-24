package com.coldboar.coreguard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CoreGuardColorScheme = darkColorScheme(
    primary = ElectricTeal,
    onPrimary = BackgroundDeepBlack,
    secondary = ElectricCyan,
    onSecondary = BackgroundDeepBlack,
    tertiary = RestrainedGold,
    background = BackgroundDeepBlack,
    surface = SurfacePewter,
    surfaceVariant = SurfaceMid,
    onBackground = CoolWhite,
    onSurface = CoolWhite,
    onSurfaceVariant = MutedText,
    error = HighRed,
    onError = CoolWhite
)

@Composable
fun CoreGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CoreGuardColorScheme,
        typography = CoreGuardTypography,
        content = content
    )
}
