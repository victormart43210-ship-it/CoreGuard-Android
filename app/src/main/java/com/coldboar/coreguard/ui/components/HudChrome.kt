package com.coldboar.coreguard.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.coldboar.coreguard.ui.theme.BackgroundDeepBlack
import com.coldboar.coreguard.ui.theme.ElectricCyan
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SoftGold
import kotlin.math.cos
import kotlin.math.sin

/**
 * Small tracked status caption — the “console readout” voice of the UI.
 */
@Composable
fun TechCaption(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ElectricTeal,
    accentDot: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (accentDot) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(color, shape = RoundedCornerShape(1.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.6.sp
            ),
            color = color
        )
    }
}

/**
 * Precision instrument ring: ticks, crosshair, orbiting sweep, dual progress track.
 * Pure decoration + score visualization; callers pass [progress] 0f..1f.
 */
@Composable
fun PrecisionScoreRing(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 210.dp,
    active: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "precisionRing")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )
    val tickPulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ticks"
    )
    val counter by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter"
    )

    Canvas(modifier = modifier.size(size)) {
        val stroke = 11.dp.toPx()
        val radius = (this.size.minDimension - stroke) / 2f - 10.dp.toPx()
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        // Outer orbital track
        drawCircle(
            color = accent.copy(alpha = 0.12f),
            radius = radius + 18.dp.toPx(),
            style = Stroke(width = 1.2.dp.toPx())
        )
        if (active) {
            rotate(degrees = sweep, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.55f),
                            ElectricCyan.copy(alpha = 0.85f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    startAngle = -28f,
                    sweepAngle = 56f,
                    useCenter = false,
                    topLeft = Offset(
                        center.x - (radius + 18.dp.toPx()),
                        center.y - (radius + 18.dp.toPx())
                    ),
                    size = Size((radius + 18.dp.toPx()) * 2f, (radius + 18.dp.toPx()) * 2f),
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Tick marks
        val ticks = 60
        for (i in 0 until ticks) {
            val deg = i * 360.0 / ticks - 90.0
            val rad = Math.toRadians(deg)
            val major = i % 5 == 0
            val innerR = radius + stroke * 0.55f
            val outerR = innerR + if (major) 9.dp.toPx() else 4.dp.toPx()
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            drawLine(
                color = accent.copy(alpha = if (major) tickPulse else tickPulse * 0.45f),
                start = Offset(center.x + c * innerR, center.y + s * innerR),
                end = Offset(center.x + c * outerR, center.y + s * outerR),
                strokeWidth = if (major) 2.4f else 1.1f,
                cap = StrokeCap.Round
            )
        }

        // Crosshair notches
        val notch = 14.dp.toPx()
        val notchR = radius + stroke * 0.2f
        listOf(0.0, 90.0, 180.0, 270.0).forEach { deg ->
            val rad = Math.toRadians(deg - 90.0)
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            drawLine(
                color = SoftGold.copy(alpha = 0.55f),
                start = Offset(center.x + c * (notchR - notch), center.y + s * (notchR - notch)),
                end = Offset(center.x + c * (notchR + notch * 0.35f), center.y + s * (notchR + notch * 0.35f)),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round
            )
        }

        // Counter-rotating hairline
        rotate(degrees = counter, pivot = center) {
            drawArc(
                color = RestrainedGold.copy(alpha = 0.28f),
                startAngle = 20f,
                sweepAngle = 48f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        drawArc(
            color = Color.White.copy(alpha = 0.08f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawCircle(
            color = accent.copy(alpha = 0.14f),
            radius = radius - stroke,
            style = Stroke(width = 1.dp.toPx())
        )

        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) {
            drawArc(
                color = accent.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f * p,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke * 2.05f, cap = StrokeCap.Round)
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * p,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            val tip = -90f + 360f * p - 7f
            drawArc(
                color = SoftGold.copy(alpha = 0.9f),
                startAngle = tip,
                sweepAngle = 12f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke * 0.75f, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Corner bracket HUD frame drawn over a full-bleed surface.
 */
@Composable
fun HudCornerOverlay(
    modifier: Modifier = Modifier,
    color: Color = ElectricTeal,
    inset: Dp = 10.dp,
    arm: Dp = 18.dp
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val stroke = 1.6.dp.toPx()
        val insetPx = inset.toPx()
        val armPx = arm.toPx()
        val ink = color.copy(alpha = 0.38f)
        val corners = listOf(
            Offset(insetPx, insetPx) to listOf(Offset(armPx, 0f), Offset(0f, armPx)),
            Offset(size.width - insetPx, insetPx) to listOf(Offset(-armPx, 0f), Offset(0f, armPx)),
            Offset(insetPx, size.height - insetPx) to listOf(Offset(armPx, 0f), Offset(0f, -armPx)),
            Offset(size.width - insetPx, size.height - insetPx) to listOf(Offset(-armPx, 0f), Offset(0f, -armPx))
        )
        corners.forEach { (origin, arms) ->
            arms.forEach { delta ->
                drawLine(
                    color = ink,
                    start = origin,
                    end = origin + delta,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun TechStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ElectricTeal
) {
    Box(
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
            color = color
        )
    }
}

@Composable
fun SheenPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null
) {
    val shape = MaterialTheme.shapes.medium
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(54.dp)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        SoftGold.copy(alpha = 0.55f),
                        ElectricTeal.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.15f)
                    )
                ),
                shape = shape
            ),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = ElectricTeal,
            contentColor = BackgroundDeepBlack,
            disabledContainerColor = ElectricTeal.copy(alpha = 0.35f),
            disabledContentColor = BackgroundDeepBlack.copy(alpha = 0.6f)
        )
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = BackgroundDeepBlack
        )
    }
}
