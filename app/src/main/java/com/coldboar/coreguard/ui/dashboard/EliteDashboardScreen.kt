package com.coldboar.coreguard.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.CpuUsageCalculator
import com.coldboar.coreguard.GuardianScore
import com.coldboar.coreguard.MemoryUsageCalculator
import com.coldboar.coreguard.SecurityCheckRunner
import com.coldboar.coreguard.elite.DynamicThreatEngine
import com.coldboar.coreguard.elite.ScamGuardEngine
import com.coldboar.coreguard.mvt.ScannerModule
import com.coldboar.coreguard.mvt.ShieldState
import com.coldboar.coreguard.swarm.SwarmModule
import com.coldboar.coreguard.ui.dashboard.ElitePalette.CardBackground
import com.coldboar.coreguard.ui.dashboard.ElitePalette.CardBorder
import com.coldboar.coreguard.ui.dashboard.ElitePalette.CyberGreen
import com.coldboar.coreguard.ui.dashboard.ElitePalette.CyberGreenGlow
import com.coldboar.coreguard.ui.dashboard.ElitePalette.DarkBackground
import com.coldboar.coreguard.ui.dashboard.ElitePalette.TextPrimary
import com.coldboar.coreguard.ui.dashboard.ElitePalette.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * CG Elite Home dashboard — sacred-geometry status hub + power-user cards.
 *
 * Metrics are wired to on-device evidence (Guardian Score, Dynamic Threat Score,
 * CPU/RAM, Nemesis scan, Privacy Shield, swarm alert Counter, Scam Guard).
 * Toggles are local UI preferences only — they do **not** enable a cloud LLM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EliteDashboardScreen(
    onNavigateToScanner: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToShield: () -> Unit = {},
    onNavigateToTools: () -> Unit = {},
    onNavigateToOverlayMatrix: () -> Unit = {},
    onNavigateToForensicJournal: () -> Unit = {},
    onNavigateToScamGuard: () -> Unit = {}
) {
    val context = LocalContext.current

    var realTimeEnabled by remember { mutableStateOf(true) }
    var deepScanEnabled by remember { mutableStateOf(true) }
    var quillaCorrelateEnabled by remember { mutableStateOf(true) }
    var intelSyncEnabled by remember { mutableStateOf(true) }

    var score by remember { mutableStateOf<Int?>(null) }
    var dtsScore by remember { mutableStateOf<Int?>(null) }
    var dtsBand by remember { mutableStateOf(DynamicThreatEngine.Band.CLEAR) }
    var scamFinding by remember { mutableStateOf(ScamGuardEngine.latestFinding()) }
    var cpuText by remember { mutableStateOf("…") }
    var ramText by remember { mutableStateOf("…") }
    var lastScanLabel by remember { mutableStateOf("NO SCAN YET") }
    var appsScanned by remember { mutableStateOf("–") }
    var threatsLabel by remember { mutableStateOf("–") }
    var swarmAlerts by remember { mutableStateOf(0) }
    var shieldOn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val results = withContext(Dispatchers.IO) { SecurityCheckRunner.run(context) }
        score = GuardianScore.compute(results)
        shieldOn = ShieldState.isActive
        swarmAlerts = SwarmModule.alertCounter.getState().count

        val report = ScannerModule.latestReport()
        if (report != null) {
            lastScanLabel = "VERDICT ${report.verdict.name}"
            appsScanned = report.scannedPackages.toString()
            threatsLabel = report.detections.size.toString()
        } else {
            lastScanLabel = "LAST SCAN: NONE"
            appsScanned = "0"
            threatsLabel = "0"
        }

        val dts = withContext(Dispatchers.IO) { DynamicThreatEngine.evaluate(context) }
        dtsScore = dts.score
        dtsBand = dts.band
        scamFinding = ScamGuardEngine.latestFinding()

        var ticks = 0
        while (true) {
            cpuText = CpuUsageCalculator.getUsagePercent()?.let { "$it%" } ?: "n/a"
            ramText = MemoryUsageCalculator.formatBytes(
                MemoryUsageCalculator.getUsedRamBytes(context)
            )
            shieldOn = ShieldState.isActive
            swarmAlerts = SwarmModule.alertCounter.getState().count
            scamFinding = ScamGuardEngine.latestFinding()
            ticks++
            if (ticks % 15 == 0) {
                val refreshed = withContext(Dispatchers.IO) { DynamicThreatEngine.evaluate(context) }
                dtsScore = refreshed.score
                dtsBand = refreshed.band
            }
            delay(2_000L)
        }
    }

    val hubStatus = when {
        dtsBand == DynamicThreatEngine.Band.CRITICAL -> "CRITICAL DTS"
        dtsBand == DynamicThreatEngine.Band.ELEVATED -> "ELEVATED DTS"
        score == null -> "CHECKING…"
        (score ?: 0) >= 80 -> "DEVICE SECURE"
        (score ?: 0) >= 50 -> "ELEVATED RISK"
        else -> "ATTENTION"
    }
    val hubSub = buildString {
        append(lastScanLabel)
        append(" · GS ")
        append(score?.toString() ?: "–")
        append(" · DTS ")
        append(dtsScore?.toString() ?: "–")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(CyberGreenGlow)
                                .border(1.dp, CyberGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = CyberGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "CG Elite",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .semantics { contentDescription = "CoreGuard Elite dashboard" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SacredGeometryStatusHub(statusText = hubStatus, subText = hubSub)

            scamFinding?.takeIf { it.score >= 50 }?.let { finding ->
                AmberScamPill(
                    host = finding.host,
                    score = finding.score,
                    onClick = onNavigateToScamGuard
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickStatusItem(
                    icon = Icons.Filled.VerifiedUser,
                    label = if (shieldOn) "SHIELD\nON" else "SHIELD\nOFF",
                    onClick = onNavigateToShield
                )
                QuickStatusItem(
                    icon = Icons.Filled.FindInPage,
                    label = "SCANNED\n$appsScanned",
                    onClick = onNavigateToScanner
                )
                QuickStatusItem(
                    icon = Icons.Filled.Lock,
                    label = if ((threatsLabel.toIntOrNull() ?: 0) == 0) "ZERO\nTHREAT" else "$threatsLabel\nHITS",
                    onClick = onNavigateToTimeline
                )
                QuickStatusItem(
                    icon = Icons.Filled.CloudDone,
                    label = "SWARM\n$swarmAlerts",
                    onClick = onNavigateToTools
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickStatusItem(
                    icon = Icons.Filled.Layers,
                    label = "OVERLAY\nMATRIX",
                    onClick = onNavigateToOverlayMatrix
                )
                QuickStatusItem(
                    icon = Icons.Filled.Lock,
                    label = "FORENSIC\nJOURNAL",
                    onClick = onNavigateToForensicJournal
                )
                QuickStatusItem(
                    icon = Icons.Filled.WarningAmber,
                    label = "SCAM\nGUARD",
                    onClick = onNavigateToScamGuard
                )
                QuickStatusItem(
                    icon = Icons.Filled.Shield,
                    label = "DTS\n${dtsScore ?: "–"}",
                    onClick = onNavigateToForensicJournal
                )
            }

            Text(
                text = "POWER-USER TOOLS",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .semantics { heading() }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PowerUserCard(
                    modifier = Modifier.weight(1f),
                    title = "REAL-TIME MONITOR",
                    statusText = if (realTimeEnabled) "ENABLED" else "DISABLED",
                    statusColor = if (realTimeEnabled) CyberGreen else Color.Red,
                    isChecked = realTimeEnabled,
                    onCheckedChange = { realTimeEnabled = it }
                ) {
                    MetricMiniRow(label = "CPU", value = if (realTimeEnabled) cpuText else "—")
                    MetricMiniRow(label = "MEMORY", value = if (realTimeEnabled) ramText else "—")
                    MetricMiniRow(
                        label = "DTS",
                        value = if (realTimeEnabled) {
                            "${dtsScore ?: "–"} ${dtsBand.name}"
                        } else {
                            "—"
                        }
                    )
                    MetricMiniRow(
                        label = "SHIELD",
                        value = if (shieldOn) "ARMED" else "IDLE"
                    )
                }

                PowerUserCard(
                    modifier = Modifier.weight(1f),
                    title = "DEEP FILE INSPECTION",
                    statusText = if (deepScanEnabled) "ACTIVE" else "PAUSED",
                    statusColor = if (deepScanEnabled) CyberGreen else Color.Yellow,
                    isChecked = deepScanEnabled,
                    onCheckedChange = { deepScanEnabled = it }
                ) {
                    MetricMiniRow(label = "PKGS SCANNED", value = appsScanned)
                    MetricMiniRow(label = "DETECTIONS", value = threatsLabel)
                    Spacer(modifier = Modifier.height(6.dp))
                    // Honest labels: on-device Quilla correlate, not a cloud AI.
                    ToggleMiniRow(
                        label = "Quilla correlate",
                        checked = quillaCorrelateEnabled
                    ) { quillaCorrelateEnabled = it }
                    ToggleMiniRow(
                        label = "Threat intel",
                        checked = intelSyncEnabled
                    ) { intelSyncEnabled = it }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SacredGeometryStatusHub(statusText: String, subText: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "sacred_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(40_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .size(280.dp)
            .padding(16.dp)
            .semantics { contentDescription = "Sacred geometry status hub: $statusText" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2.2f

            drawCircle(
                color = CyberGreen,
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = CyberGreenGlow,
                radius = radius + 6.dp.toPx(),
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )

            // Flower-of-Life style overlapping rings (Living Geometry metaphor).
            for (i in 0 until 6) {
                val angle = Math.toRadians((i * 60 + rotation).toDouble())
                val circleCenter = Offset(
                    x = (center.x + radius * 0.5f * cos(angle)).toFloat(),
                    y = (center.y + radius * 0.5f * sin(angle)).toFloat()
                )
                drawCircle(
                    color = CyberGreen.copy(alpha = 0.25f),
                    radius = radius * 0.5f,
                    center = circleCenter,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            val hexPath = Path()
            for (i in 0 until 6) {
                val angle = Math.toRadians((i * 60 + rotation).toDouble())
                val x = (center.x + radius * 0.85f * cos(angle)).toFloat()
                val y = (center.y + radius * 0.85f * sin(angle)).toFloat()
                if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
            }
            hexPath.close()
            drawPath(
                path = hexPath,
                color = CyberGreen.copy(alpha = 0.4f),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(DarkBackground.copy(alpha = 0.85f))
                .border(1.dp, CyberGreen.copy(alpha = 0.5f), CircleShape)
        ) {
            Text(
                text = statusText,
                color = CyberGreen,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subText,
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AmberScamPill(host: String, score: Int, onClick: () -> Unit) {
    val amber = Color(0xFFFFB347)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(amber.copy(alpha = 0.18f))
            .border(1.dp, amber, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics {
                contentDescription = "Scam Guard amber warning for $host"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = amber,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SCAM GUARD · AMBER",
                color = amber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$host · score $score",
                color = TextPrimary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun QuickStatusItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = label.replace('\n', ' ')
        }
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyberGreen,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 11.sp
        )
    }
}

@Composable
fun PowerUserCard(
    modifier: Modifier = Modifier,
    title: String,
    statusText: String,
    statusColor: Color,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkBackground,
                        checkedTrackColor = CyberGreen,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = CardBorder
                    )
                )
            }

            Text(
                text = statusText,
                color = statusColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            HorizontalDivider(
                color = CardBorder,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            content()
        }
    }
}

@Composable
fun MetricMiniRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 10.sp)
        Text(text = value, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ToggleMiniRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 10.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(28.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = DarkBackground,
                checkedTrackColor = CyberGreen
            )
        )
    }
}
