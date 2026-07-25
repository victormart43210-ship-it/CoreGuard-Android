package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.ui.components.BottomNavBar
import com.coldboar.coreguard.ui.components.CornerSigils
import com.coldboar.coreguard.ui.components.GlassCard
import com.coldboar.coreguard.ui.components.GlowActionButton
import com.coldboar.coreguard.ui.components.NavTab
import com.coldboar.coreguard.ui.components.SectionLabel
import com.coldboar.coreguard.ui.components.StatusBar
import com.coldboar.coreguard.ui.nav.Routes
import com.coldboar.coreguard.ui.theme.AbyssBlack
import com.coldboar.coreguard.ui.theme.AcidGreen
import com.coldboar.coreguard.ui.theme.CyanGlow
import com.coldboar.coreguard.ui.theme.CyanPrimary
import com.coldboar.coreguard.ui.theme.CyanShadow
import com.coldboar.coreguard.ui.theme.CyanVibrant
import com.coldboar.coreguard.ui.theme.SurfaceGlass
import com.coldboar.coreguard.ui.theme.SurfaceLine
import com.coldboar.coreguard.ui.theme.TextHigh
import com.coldboar.coreguard.ui.theme.TextMid

private val memoSeries = listOf(0.9f, 1.3f, 1.6f, 1.4f, 1.8f, 2.1f, 1.7f, 1.9f, 2.3f, 1.6f, 1.4f, 1.8f)
private val cpuSeries  = listOf(20f, 35f, 22f, 48f, 30f, 15f, 40f, 55f, 28f, 18f, 12f, 23f)

@Composable
fun PerformanceScreen(onTab: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(AbyssBlack)) {
        CornerSigils()
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            StatusBar()
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Performance", color = TextHigh, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceGlass)
                        .border(0.6.dp, CyanShadow, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Last 24h", color = TextMid, fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.ArrowDropDown, null, tint = TextMid,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            GlassCard(accentTint = CyanPrimary) {
                SectionLabel("Memory Usage 24h", color = CyanPrimary)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(140.dp)) {
                    MemoryAreaChart(memoSeries)
                    Text(
                        "1.8 GB Average", color = TextHigh, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.TopStart).padding(top = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            GlassCard(accentTint = CyanPrimary) {
                SectionLabel("Top Memory Consumers", color = CyanPrimary)
                Spacer(Modifier.height(10.dp))
                ConsumerRow("C",  "Chrome",    412f,   0f,    Color(0xFF4A90E2))
                ConsumerRow("I",  "Instagram", 287f,   0.05f, Color(0xFFE2476B))
                ConsumerRow("S",  "Spotify",   198f,   0.10f, Color(0xFF1ED760))
                ConsumerRow("OS", "System",    1.4f,   0.15f, CyanPrimary)
            }

            Spacer(Modifier.height(14.dp))
            GlassCard(accentTint = CyanPrimary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("CPU History", color = CyanPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("Average 23%", color = TextMid, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                CpuBarChart(cpuSeries)
            }

            Spacer(Modifier.height(16.dp))
            GlowActionButton(
                "Optimize Now",
                accent = Brush.horizontalGradient(listOf(CyanVibrant, AcidGreen))
            ) { }

            Spacer(Modifier.weight(1f))
            BottomNavBar(
                tabs = listOf(
                    NavTab("Home",        Icons.Filled.Home),
                    NavTab("Storage",     Icons.Filled.Storage),
                    NavTab("Performance", Icons.Filled.BarChart),
                    NavTab("Apps",        Icons.Filled.Apps),
                    NavTab("Settings",    Icons.Filled.Settings),
                ),
                selectedIndex = 2,
                onSelect = { onTab(Routes.PERFORMANCE) }
            )
        }
    }
}

@Composable
private fun MemoryAreaChart(values: List<Float>) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val maxV = (values.maxOrNull() ?: 1f) * 1.2f
        val stepX = w / (values.size - 1).coerceAtLeast(1)
        val pts = values.mapIndexed { i, v ->
            Offset(i * stepX, h - (v / maxV) * h * 0.85f)
        }
        for (g in 1..3) {
            val y = h * g / 4f
            drawLine(SurfaceLine, Offset(0f, y), Offset(w, y), strokeWidth = 0.5f)
        }
        val fillPath = Path().apply {
            moveTo(pts.first().x, h)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, h)
            close()
        }
        drawPath(
            fillPath,
            Brush.verticalGradient(
                listOf(CyanGlow.copy(alpha = 0.55f), Color.Transparent)
            )
        )
        val linePath = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            linePath,
            Brush.horizontalGradient(listOf(CyanVibrant, AcidGreen)),
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun CpuBarChart(values: List<Float>) {
    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        val w = size.width
        val h = size.height
        val stepX = w / values.size
        val maxV = 100f
        values.forEachIndexed { i, v ->
            val barH = (v / maxV) * h * 0.9f
            val x = i * stepX + stepX * 0.20f
            val barW = stepX * 0.6f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        CyanPrimary.copy(alpha = 0.8f),
                        CyanPrimary.copy(alpha = 0.25f)
                    )
                ),
                topLeft = Offset(x, h - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
        drawLine(SurfaceLine, Offset(0f, h), Offset(w, h), strokeWidth = 0.5f)
    }
}

@Composable
private fun ConsumerRow(
    shortLabel: String, name: String,
    mb: Float, bobOffset: Float, accent: Color
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f))
                .border(0.8.dp, accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(shortLabel, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name, color = TextHigh, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceLine)
            ) {
                Box(
                    Modifier.fillMaxHeight()
                        .fillMaxWidth(0.40f - bobOffset)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(CyanVibrant, AcidGreen)))
                )
            }
        }
        Spacer(Modifier.weight(1f))
        val txt = if (mb < 10f) "%.1f GB".format(mb) else "${mb.toInt()} MB"
        Text(txt, color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
