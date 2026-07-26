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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.theme.AtmosphereGold
import com.coldboar.coreguard.ui.theme.AtmosphereTeal
import com.coldboar.coreguard.ui.theme.BackgroundDeepBlack
import com.coldboar.coreguard.ui.theme.BackgroundInk
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SurfaceMid
import com.coldboar.coreguard.ui.theme.SurfacePewter
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (eyebrow != null) {
            TechCaption(text = eyebrow, accentDot = true)
            Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = ElectricTeal,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.34f)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(ElectricTeal, RestrainedGold.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText
        )
    }
}

/**
 * Geometric brand seal — double ring, heptagram, and hex core.
 * Decorative only; keep alpha low so content stays primary.
 */
@Composable
fun BrandSeal(
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    color: Color = RestrainedGold,
    alpha: Float = 0.22f,
    rotate: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "brandSeal")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 140_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sealSpin"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sealPulse"
    )

    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = min(this.size.width, this.size.height) / 2f * 0.96f
        val stroke = Stroke(width = (r * 0.012f).coerceAtLeast(1.2f))
        val ink = color.copy(alpha = alpha * pulse)

        fun point(i: Int, n: Int, radius: Float, offsetDeg: Float = -90f): Offset {
            val rad = Math.toRadians(i * 360.0 / n + offsetDeg)
            return Offset(cx + cos(rad).toFloat() * radius, cy + sin(rad).toFloat() * radius)
        }

        rotate(degrees = if (rotate) angle else 0f, pivot = Offset(cx, cy)) {
            drawCircle(color = ink, radius = r, style = stroke)
            drawCircle(color = ink.copy(alpha = ink.alpha * 0.85f), radius = r * 0.9f, style = stroke)

            val glyphs = 21
            for (i in 0 until glyphs) {
                val outer = point(i, glyphs, r * 0.995f)
                val inner = point(i, glyphs, r * 0.905f)
                drawLine(ink, inner, outer, strokeWidth = stroke.width)
            }

            val star = Path().apply {
                val pts = 7
                val step = 3
                val starR = r * 0.78f
                moveTo(point(0, pts, starR).x, point(0, pts, starR).y)
                for (i in 1..pts) {
                    val p = point((i * step) % pts, pts, starR)
                    lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(star, color = ink, style = stroke)

            val hept = Path().apply {
                val heptR = r * 0.78f
                moveTo(point(0, 7, heptR).x, point(0, 7, heptR).y)
                for (i in 1..7) {
                    val p = point(i % 7, 7, heptR)
                    lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(hept, color = ink.copy(alpha = ink.alpha * 0.75f), style = stroke)

            val hexOrder = intArrayOf(0, 3, 1, 4, 2, 5)
            val hex = Path().apply {
                hexOrder.forEachIndexed { idx, v ->
                    val rr = if (v % 2 == 0) r * 0.32f else r * 0.28f
                    val p = point(v, 6, rr)
                    if (idx == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(hex, color = ink, style = stroke)
            drawCircle(color = ink, radius = r * 0.05f, style = stroke)
        }
    }
}

@Composable
fun CoreGuardCard(
    modifier: Modifier = Modifier,
    containerColor: Color = SurfaceMid.copy(alpha = 0.62f),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        ElectricTeal.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.06f),
                        RestrainedGold.copy(alpha = 0.18f)
                    )
                ),
                shape = shape
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun PrimaryTealButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = MaterialTheme.shapes.medium
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        RestrainedGold.copy(alpha = 0.45f),
                        ElectricTeal.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.12f)
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
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PremiumGoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = RestrainedGold,
            contentColor = BackgroundDeepBlack
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Animated full-bleed atmosphere + content column used by primary screens.
 * Precision-grid HUD wash, corner brackets, dual blooms, and drifting motes.
 */
@Composable
fun ScreenAtmosphere(
    modifier: Modifier = Modifier,
    accent: Color = ElectricTeal,
    content: @Composable ColumnScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "screenAtmosphere")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val radar by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar"
    )
    val motes = remember {
        List(22) {
            Triple(
                Random.nextFloat(),
                Random.nextFloat(),
                1.1f + Random.nextFloat() * 2.4f
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDeepBlack)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BackgroundInk,
                        BackgroundDeepBlack,
                        AtmosphereTeal.copy(alpha = 0.38f)
                    )
                )
            )
            // Precision grid — mastery / console feel
            val gridStep = 28.dp.toPx()
            val gridInk = accent.copy(alpha = 0.035f + 0.015f * pulse)
            var gx = 0f
            while (gx < size.width) {
                drawLine(gridInk, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
                gx += gridStep
            }
            var gy = 0f
            while (gy < size.height) {
                drawLine(gridInk, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                gy += gridStep
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.2f * pulse), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.08f),
                    radius = size.minDimension * 0.9f
                ),
                center = Offset(size.width * 0.18f, size.height * 0.08f),
                radius = size.minDimension * 0.9f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AtmosphereGold.copy(alpha = 0.24f), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.05f),
                    radius = size.minDimension * 0.55f
                ),
                center = Offset(size.width * 0.92f, size.height * 0.05f),
                radius = size.minDimension * 0.55f
            )

            // Soft radar wedge in the upper field
            val radarCenter = Offset(size.width * 0.72f, size.height * 0.18f)
            val radarR = size.minDimension * 0.42f
            rotate(degrees = radar, pivot = radarCenter) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = radarCenter
                    ),
                    startAngle = -40f,
                    sweepAngle = 70f,
                    useCenter = true,
                    topLeft = Offset(radarCenter.x - radarR, radarCenter.y - radarR),
                    size = androidx.compose.ui.geometry.Size(radarR * 2f, radarR * 2f)
                )
            }

            motes.forEachIndexed { index, (x, y, r) ->
                val yy = (y + drift * (0.2f + (index % 5) * 0.08f)) % 1.1f
                val xx = x + 0.012f * sin((drift + y) * Math.PI * 2).toFloat()
                val color = if (index % 3 == 0) RestrainedGold else accent
                drawCircle(
                    color = color.copy(alpha = 0.14f + 0.2f * pulse),
                    radius = r * density,
                    center = Offset(xx * size.width, yy * size.height)
                )
            }
            val sweepY = size.height * (0.2f + 0.02f * cos(drift * Math.PI * 2).toFloat())
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        accent.copy(alpha = 0.18f),
                        RestrainedGold.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                ),
                start = Offset(0f, sweepY),
                end = Offset(size.width, sweepY),
                strokeWidth = 1.8f * density
            )
        }
        HudCornerOverlay(color = accent)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun NestedSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = shape
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = SurfacePewter.copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

@Composable
fun SubScreenTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go back",
                tint = ElectricTeal
            )
        }
        ScreenHeader(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun EmptyStatePanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    CoreGuardCard(modifier = modifier, containerColor = SurfacePewter.copy(alpha = 0.92f)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = ElectricTeal)
        Spacer(modifier = Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MutedText)
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryTealButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun LoadingLine(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = ElectricTeal,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MutedText)
    }
}
