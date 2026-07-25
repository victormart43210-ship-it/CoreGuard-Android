package com.coldboar.coreguard.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    accentTint: Color? = CyanPrimary,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(SurfaceGlass)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.Transparent,
                        (accentTint ?: CyanGlow).copy(alpha = 0.35f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(18.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun StatusBar() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("9:41", color = TextHigh, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("100%", color = TextHigh, fontSize = 11.sp)
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(width = 18.dp, height = 9.dp)
                    .border(1.dp, TextMid, RoundedCornerShape(2.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(1.dp)
                        .background(CyanPrimary, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

data class NavTab(val label: String, val icon: ImageVector)

@Composable
fun BottomNavBar(
    tabs: List<NavTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(SurfaceGlassSo)
            .border(0.5.dp, CyanShadow, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEachIndexed { i, tab ->
                val selected = i == selectedIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(i) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (selected) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            CyanShadow,
                                            AcidGreenGlow.copy(alpha = 0.25f),
                                            CyanShadow
                                        )
                                    )
                                )
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Icon(tab.icon, tab.label, tint = CyanPrimary,
                                modifier = Modifier.size(20.dp))
                        }
                    } else {
                        Icon(tab.icon, tab.label, tint = TextLow,
                            modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tab.label,
                        color = if (selected) TextHigh else TextLow,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun CornerSigils() {
    Canvas(Modifier.fillMaxSize()) {
        val s = size
        drawSigil(Offset(0f, 0f), 56f, false, false)
        drawSigil(Offset(s.width, 0f), 56f, true, false)
        drawSigil(Offset(0f, s.height), 56f, false, true)
        drawSigil(Offset(s.width, s.height), 56f, true, true)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSigil(
    anchor: Offset, r: Float, mirrorH: Boolean, mirrorV: Boolean
) {
    val cx = if (mirrorH) anchor.x - r else anchor.x + r
    val cy = if (mirrorV) anchor.y - r else anchor.y + r
    rotate(45f, pivot = Offset(cx, cy)) {
        drawCircle(CyanGlow, radius = r * 0.45f, center = Offset(cx, cy),
            style = Stroke(width = 1.2f))
        drawCircle(CyanShadow, radius = r * 0.9f, center = Offset(cx, cy),
            style = Stroke(width = 0.6f))
        drawCircle(CyanPrimary, radius = r * 0.15f, center = Offset(cx, cy))
    }
}

@Composable
fun NeonCircularGauge(percent: Float, sizeDp: Dp = 220.dp) {
    val animated by animateFloatAsState(targetValue = percent / 100f, label = "gauge")
    Box(Modifier.size(sizeDp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size
            val stroke = 14f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(CyanGlow, Color.Transparent),
                    center = s.center, radius = s.minDimension / 2f
                ),
                radius = s.minDimension / 2f, center = s.center
            )
            drawArc(
                color = SurfaceLine,
                startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = Offset(stroke, stroke),
                size = Size(s.width - 2 * stroke, s.height - 2 * stroke),
                style = Stroke(width = stroke)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(CyanDeep, CyanPrimary, AcidGreen, CyanPrimary, CyanDeep),
                    center = s.center
                ),
                startAngle = 135f, sweepAngle = 270f * animated, useCenter = false,
                topLeft = Offset(stroke, stroke),
                size = Size(s.width - 2 * stroke, s.height - 2 * stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            for (i in 0..8) {
                val a = (135f + i * 33.75f) * (Math.PI.toFloat() / 180f)
                val r1 = s.minDimension / 2f - 8f
                val r2 = s.minDimension / 2f + 2f
                drawLine(
                    CyanShadow,
                    Offset(s.center.x + cos(a) * r1, s.center.y + sin(a) * r1),
                    Offset(s.center.x + cos(a) * r2, s.center.y + sin(a) * r2),
                    strokeWidth = 1.2f
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${percent.toInt()}%",
                color = TextHigh, fontSize = 56.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Memory In Use",
                color = TextMid, fontSize = 11.sp, fontWeight = FontWeight.Medium
            )
            Text(
                "4.2 / 6.0 GB",
                color = CyanPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CpuBar(value: Int) {
    val pct = value / 100f
    Column {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "CPU Utilization", color = TextHigh, fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text(
                "$value%",
                color = CyanPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth().height(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceLine)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(CyanVibrant, AcidGreen)))
            )
        }
    }
}

@Composable
fun TogglePill(checked: Boolean, onChange: (Boolean) -> Unit) {
    val w by animateFloatAsState(if (checked) 1f else 0f, label = "knob")
    Box(
        Modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) CyanPrimary.copy(alpha = 0.15f) else SurfaceLine)
            .border(1.dp, if (checked) CyanPrimary else TextLow, RoundedCornerShape(13.dp))
            .clickable { onChange(!checked) }
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(20.dp)
                .align(if (w > 0.5f) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(CircleShape)
                .background(if (checked) CyanPrimary else TextLow)
        )
    }
}

@Composable
fun QuickActionPill(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceGlass)
            .border(1.dp, CyanShadow, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp)
    ) {
        Box(
            Modifier
                .size(38.dp).clip(CircleShape)
                .background(CyanShadow)
                .border(1.dp, CyanPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = TextHigh, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun BoarEmblem(sizeDp: Dp = 96.dp, withRunes: Boolean = true) {
    Box(
        Modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(SurfaceNight, AbyssBlack)))
            .border(1.dp, CyanPrimary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(sizeDp - 6.dp)) {
            val s = this.size
            val cx = s.width / 2f
            val cy = s.height / 2f
            val r = s.minDimension / 2f

            drawCircle(CyanGlow, radius = r * 0.95f, center = Offset(cx, cy),
                style = Stroke(width = 1f))
            drawCircle(CyanShadow, radius = r * 0.7f, center = Offset(cx, cy),
                style = Stroke(width = 0.7f))

            val tuskPath = Path().apply {
                moveTo(cx - r * 0.35f, cy + r * 0.10f)
                quadraticBezierTo(cx - r * 0.45f, cy + r * 0.45f, cx - r * 0.55f, cy + r * 0.55f)
                quadraticBezierTo(cx - r * 0.40f, cy + r * 0.35f, cx - r * 0.20f, cy + r * 0.15f)
                close()
            }
            val tuskR = Path().apply {
                moveTo(cx + r * 0.35f, cy + r * 0.10f)
                quadraticBezierTo(cx + r * 0.45f, cy + r * 0.45f, cx + r * 0.55f, cy + r * 0.55f)
                quadraticBezierTo(cx + r * 0.40f, cy + r * 0.35f, cx + r * 0.20f, cy + r * 0.15f)
                close()
            }
            drawPath(tuskPath, Brush.linearGradient(listOf(CyanPrimary, CyanVibrant)))
            drawPath(tuskR,   Brush.linearGradient(listOf(CyanVibrant, CyanPrimary)))

            drawCircle(
                AcidGreenGlow.copy(alpha = 0.6f), radius = r * 0.18f,
                center = Offset(cx, cy + r * 0.05f)
            )
            drawCircle(
                CyanPrimary, radius = r * 0.05f,
                center = Offset(cx, cy + r * 0.05f)
            )

            drawCircle(AcidGreen, radius = r * 0.08f,
                center = Offset(cx - r * 0.28f, cy - r * 0.18f))
            drawCircle(AcidGreen, radius = r * 0.08f,
                center = Offset(cx + r * 0.28f, cy - r * 0.18f))
            drawCircle(AbyssBlack, radius = r * 0.04f,
                center = Offset(cx - r * 0.28f, cy - r * 0.18f))
            drawCircle(AbyssBlack, radius = r * 0.04f,
                center = Offset(cx + r * 0.28f, cy - r * 0.18f))

            drawLine(
                AcidGreen,
                Offset(cx - r * 0.45f, cy - r * 0.32f),
                Offset(cx - r * 0.10f, cy - r * 0.22f),
                strokeWidth = 2f
            )
            drawLine(
                AcidGreen,
                Offset(cx + r * 0.10f, cy - r * 0.22f),
                Offset(cx + r * 0.45f, cy - r * 0.32f),
                strokeWidth = 2f
            )

            if (withRunes) {
                for (i in 0..3) {
                    val px = cx - r * 0.30f + i * r * 0.20f
                    drawLine(
                        CyanShadow,
                        Offset(px, cy - r * 0.40f),
                        Offset(px, cy - r * 0.50f),
                        strokeWidth = 1.4f
                    )
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, color: Color = TextMid) {
    Text(
        text.uppercase(),
        color = color, fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp
    )
}

@Composable
fun GlowActionButton(
    label: String,
    accent: Brush = Brush.horizontalGradient(listOf(CyanVibrant, AcidGreen)),
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = AbyssBlack, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
