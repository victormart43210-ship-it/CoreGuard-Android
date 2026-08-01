package com.coldboar.coreguard.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.coldboar.coreguard.ui.theme.AtmosphereGold
import com.coldboar.coreguard.ui.theme.AtmosphereTeal
import com.coldboar.coreguard.ui.theme.BackgroundDeepBlack
import com.coldboar.coreguard.ui.theme.BackgroundInk
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.rememberMotionEnabled
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-bleed atmospheric plane used behind primary screens.
 * Soft dual radial washes + drifting gold motes — presence without noise.
 * Decorative loops freeze when animator duration scale is disabled.
 */
@Composable
fun AtmosphereBackground(
    modifier: Modifier = Modifier,
    accent: Color = ElectricTeal,
    content: @Composable BoxScope.() -> Unit
) {
    val motionEnabled = rememberMotionEnabled()
    val transition = rememberInfiniteTransition(label = "atmosphere")
    val driftAnimated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )
    val pulseAnimated by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    // Freeze decorative loops when the user disables animator duration scale.
    val drift = if (motionEnabled) driftAnimated else 0f
    val pulse = if (motionEnabled) pulseAnimated else 0.75f
    val motes = remember {
        List(18) {
            Mote(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                radius = 1.2f + Random.nextFloat() * 2.4f,
                speed = 0.15f + Random.nextFloat() * 0.45f,
                gold = Random.nextBoolean()
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDeepBlack)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(BackgroundInk, BackgroundDeepBlack, AtmosphereTeal.copy(alpha = 0.35f))
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.16f * pulse), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.12f),
                    radius = size.minDimension * 0.85f
                ),
                center = Offset(size.width * 0.18f, size.height * 0.12f),
                radius = size.minDimension * 0.85f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AtmosphereGold.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.08f),
                    radius = size.minDimension * 0.55f
                ),
                center = Offset(size.width * 0.88f, size.height * 0.08f),
                radius = size.minDimension * 0.55f
            )

            motes.forEach { mote ->
                val y = ((mote.y + drift * mote.speed) % 1.1f)
                val x = mote.x + 0.012f * sin((drift + mote.y) * Math.PI * 2).toFloat()
                val color = if (mote.gold) RestrainedGold else accent
                drawCircle(
                    color = color.copy(alpha = 0.18f + 0.22f * pulse),
                    radius = mote.radius * density,
                    center = Offset(x * size.width, y * size.height)
                )
            }

            // faint horizon sweep
            val sweepY = size.height * (0.28f + 0.02f * cos(drift * Math.PI * 2).toFloat())
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        accent.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                ),
                start = Offset(0f, sweepY),
                end = Offset(size.width, sweepY),
                strokeWidth = 1.5f * density
            )
        }
        content()
    }
}

private data class Mote(
    val x: Float,
    val y: Float,
    val radius: Float,
    val speed: Float,
    val gold: Boolean
)
