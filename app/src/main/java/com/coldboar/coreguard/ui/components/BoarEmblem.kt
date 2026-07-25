package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.ui.theme.CyanPrimary
import com.coldboar.coreguard.ui.theme.CyanShadow
import com.coldboar.coreguard.ui.theme.CyanVibrant
import com.coldboar.coreguard.ui.theme.SurfaceGlass

/**
 * Decorative boar-head emblem used on the Settings and Premium screens.
 *
 * Renders a circular badge with concentric rings and optional rune tick marks,
 * with the app initial "B" centred inside.
 */
@Composable
fun BoarEmblem(sizeDp: Dp, withRunes: Boolean = true) {
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(SurfaceGlass)
            .border(1.dp, CyanShadow, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val ringColor = CyanPrimary.copy(alpha = 0.25f)
        val accentColor = CyanPrimary
        Canvas(modifier = Modifier.size(sizeDp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outer = (size.minDimension / 2f) * 0.90f
            val inner = (size.minDimension / 2f) * 0.68f

            // Outer ring
            drawCircle(
                color = ringColor,
                radius = outer,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx()),
            )
            // Inner ring
            drawCircle(
                color = ringColor,
                radius = inner,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx()),
            )
            // Gradient fill
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CyanVibrant.copy(alpha = 0.12f), SurfaceGlass.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = inner,
                ),
                radius = inner,
                center = Offset(cx, cy),
            )

            if (withRunes) {
                // Draw 8 tick marks around the outer ring
                val tickLen = 6.dp.toPx()
                repeat(8) { i ->
                    val angle = Math.toRadians((i * 45.0) - 90.0)
                    val cosA = Math.cos(angle).toFloat()
                    val sinA = Math.sin(angle).toFloat()
                    drawLine(
                        color = accentColor.copy(alpha = 0.5f),
                        start = Offset(cx + cosA * (outer - tickLen), cy + sinA * (outer - tickLen)),
                        end = Offset(cx + cosA * outer, cy + sinA * outer),
                        strokeWidth = 1.2.dp.toPx(),
                    )
                }
            }
        }

        // Central glyph
        Text(
            text = "B",
            color = CyanPrimary,
            fontSize = (sizeDp.value * 0.28f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
