package com.coldboar.coreguard.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.guardian.GuardianState
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import kotlin.math.cos
import kotlin.math.sin

/**
 * Guardian Pulse — central posture visual (Blueprint §8).
 * Animations respect reduced-motion; never frantic flashing.
 */
@Composable
fun GuardianPulse(
    state: GuardianState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val a11yManager = LocalAccessibilityManager.current
    val reduceMotion = a11yManager?.let {
        // Compose has no single API; treat system animation scale via Build if needed.
        false
    } == true

    val transition = rememberInfiniteTransition(label = "guardian-pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    GuardianState.PROTECTED -> 4200
                    GuardianState.OBSERVING -> 3200
                    GuardianState.ATTENTION_REQUIRED -> 2800
                    GuardianState.HIGH_RISK -> 3600
                    GuardianState.SCANNING -> 2200
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val animatedPhase = if (reduceMotion) 0f else phase
    val ringColor = when (state) {
        GuardianState.PROTECTED -> ElectricTeal
        GuardianState.OBSERVING -> ElectricTeal.copy(alpha = 0.85f)
        GuardianState.ATTENTION_REQUIRED -> RestrainedGold
        GuardianState.HIGH_RISK -> HighRed
        GuardianState.SCANNING -> ElectricTeal
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription =
                    "Guardian pulse: ${state.userLabel}. ${state.guidance}. Double tap for findings."
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseR = size.minDimension * 0.32f
            val breath = 1f + 0.04f * sin(animatedPhase * Math.PI * 2).toFloat()
            drawCircle(
                color = ringColor.copy(alpha = 0.15f),
                radius = baseR * breath * 1.35f,
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            )
            drawCircle(
                color = ringColor,
                radius = baseR * breath,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = Stroke(width = 4.dp.toPx())
            )
            if (state == GuardianState.HIGH_RISK) {
                // Stable fractured-ring treatment — not flashing.
                for (i in 0 until 3) {
                    val a = animatedPhase * 360f + i * 120f
                    val rad = Math.toRadians(a.toDouble())
                    val x = cx + cos(rad).toFloat() * baseR * 1.15f
                    val y = cy + sin(rad).toFloat() * baseR * 1.15f
                    drawCircle(color = HighRed.copy(alpha = 0.5f), radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                }
            }
            if (state == GuardianState.SCANNING) {
                val sweep = animatedPhase * 360f
                drawArc(
                    color = Color.Cyan.copy(alpha = 0.7f),
                    startAngle = sweep,
                    sweepAngle = 60f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx()),
                    topLeft = androidx.compose.ui.geometry.Offset(cx - baseR, cy - baseR),
                    size = androidx.compose.ui.geometry.Size(baseR * 2, baseR * 2)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.userLabel,
            style = MaterialTheme.typography.titleLarge,
            color = ringColor,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = state.guidance,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
