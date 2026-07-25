package com.coldboar.coreguard.ui.screens

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import com.coldboar.coreguard.ui.theme.AmberWarn
import com.coldboar.coreguard.ui.theme.CyanDeep
import com.coldboar.coreguard.ui.theme.CyanGlow
import com.coldboar.coreguard.ui.theme.CyanPrimary
import com.coldboar.coreguard.ui.theme.CyanShadow
import com.coldboar.coreguard.ui.theme.CyanVibrant
import com.coldboar.coreguard.ui.theme.SurfaceLine
import com.coldboar.coreguard.ui.theme.SurfaceNight
import com.coldboar.coreguard.ui.theme.TextHigh
import com.coldboar.coreguard.ui.theme.TextMid

@Composable
fun SecurityScreen(onTab: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(AbyssBlack)) {
        CornerSigils()
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            StatusBar()
            Spacer(Modifier.height(8.dp))
            Text(
                "Security", color = TextHigh, fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(24.dp))
            ScannerRingHero()
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Box(Modifier.size(8.dp).background(AcidGreen, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Real-time Protection ACTIVE",
                    color = AcidGreen, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(20.dp))
            GlowActionButton(
                "Start Full Scan",
                accent = Brush.horizontalGradient(listOf(CyanVibrant, AcidGreen))
            ) { }

            Spacer(Modifier.height(18.dp))
            GlassCard(accentTint = CyanPrimary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Recent Scans", color = CyanPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("View all", color = TextMid, fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                ScanRow("Network Scan",          "No threats", Verdicts.Clean)
                ScanRow("App Permissions Audit", "3 warnings", Verdicts.Warn)
                ScanRow("Storage Integrity",     "Clean",      Verdicts.Clean)
                ScanRow("Tracker Detection",     "7 blocked",  Verdicts.Track)
            }

            Spacer(Modifier.weight(1f))
            BottomNavBar(
                tabs = listOf(
                    NavTab("Home",     Icons.Filled.Home),
                    NavTab("Scan",     Icons.Filled.Search),
                    NavTab("Privacy",  Icons.Filled.Lock),
                    NavTab("Tools",    Icons.Filled.Build),
                    NavTab("Settings", Icons.Filled.Settings),
                ),
                selectedIndex = 1,
                onSelect = { onTab(Routes.SECURITY) }
            )
        }
    }
}

@Composable
private fun ScannerRingHero() {
    val transition = rememberInfiniteTransition(label = "scan")
    val pulse by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "p"
    )
    Box(
        Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(220.dp)) {
            val s = this.size
            val center = Offset(s.width / 2f, s.height / 2f)
            val r = s.minDimension / 2f
            val stroke = 8f

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(CyanGlow.copy(alpha = 0.35f), Color.Transparent),
                    center = center, radius = r
                ), radius = r, center = center
            )

            drawCircle(
                SurfaceLine, radius = r - stroke,
                center = center, style = Stroke(width = stroke)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(CyanDeep, CyanPrimary, AcidGreen, CyanPrimary, CyanDeep),
                    center = center
                ),
                startAngle = 0f, sweepAngle = 270f + 90f * pulse, useCenter = false,
                topLeft = Offset(center.x - r + stroke / 2, center.y - r + stroke / 2),
                size = Size(2 * r - stroke, 2 * r - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            for (i in 0..11) {
                val a = (i * 30f) * (Math.PI.toFloat() / 180f)
                val x1 = center.x + kotlin.math.cos(a) * (r - stroke * 2)
                val y1 = center.y + kotlin.math.sin(a) * (r - stroke * 2)
                val x2 = center.x + kotlin.math.cos(a) * (r + 2)
                val y2 = center.y + kotlin.math.sin(a) * (r + 2)
                drawLine(
                    CyanGlow,
                    Offset(x1, y1), Offset(x2, y2),
                    strokeWidth = 1f
                )
            }
            drawCircle(SurfaceNight, radius = r * 0.6f, center = center)
            drawCircle(CyanShadow, radius = r * 0.6f, center = center, style = Stroke(1.4f))
            val s1 = center.x - r * 0.20f
            val s2 = center.y + r * 0.05f
            val path = Path().apply {
                moveTo(s1, s2)
                lineTo(s1 + r * 0.15f, s2 + r * 0.15f)
                lineTo(s1 + r * 0.40f, s2 - r * 0.20f)
            }
            drawPath(path, AcidGreen, style = Stroke(width = 5f, cap = StrokeCap.Round))
        }
    }
}

private enum class Verdicts { Clean, Warn, Track }

@Composable
private fun ScanRow(label: String, value: String, state: Verdicts) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextHigh, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        val pillColor = when (state) {
            Verdicts.Clean -> AcidGreen
            Verdicts.Warn  -> AmberWarn
            Verdicts.Track -> AcidGreen
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(pillColor.copy(alpha = 0.15f))
                .border(0.6.dp, pillColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                value, color = pillColor, fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
