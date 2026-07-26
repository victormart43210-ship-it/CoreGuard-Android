package com.coldboar.coreguard.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.CpuUsageCalculator
import com.coldboar.coreguard.GuardianRank
import com.coldboar.coreguard.GuardianScore
import com.coldboar.coreguard.MemoryUsageCalculator
import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckRunner
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.ui.components.AtmosphereBackground
import com.coldboar.coreguard.ui.components.SecurityStatusChip
import com.coldboar.coreguard.ui.components.toStatusColor
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.BackgroundDeepBlack
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen
import com.coldboar.coreguard.ui.theme.SurfacePewter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(onNavigateToScanner: () -> Unit, onNavigateToTimeline: () -> Unit = {}) {
    val context = LocalContext.current

    var ramText by remember { mutableStateOf("–") }
    var cpuText by remember { mutableStateOf("Measuring…") }
    var securityResults by remember { mutableStateOf<List<SecurityCheckResult>>(emptyList()) }
    var scoreTarget by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        securityResults = withContext(Dispatchers.Default) { SecurityCheckRunner.run(context) }
        scoreTarget = GuardianScore.compute(securityResults).toFloat()

        CpuUsageCalculator.reset()
        while (true) {
            val usedRam = MemoryUsageCalculator.getUsedRamBytes(context)
            val totalRam = MemoryUsageCalculator.getTotalRamBytes(context)
            ramText = if (usedRam != null && totalRam != null) {
                "${MemoryUsageCalculator.formatBytes(usedRam)} / ${MemoryUsageCalculator.formatBytes(totalRam)}"
            } else "–"
            val cpu = CpuUsageCalculator.getUsagePercent()
            cpuText = if (cpu != null) "$cpu%" else "Measuring…"
            delay(2_000)
        }
    }

    AtmosphereBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CoreGuard",
                    style = MaterialTheme.typography.displayLarge,
                    color = ElectricTeal,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "On-device privacy intelligence",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText
                )

                Spacer(modifier = Modifier.height(30.dp))

                val score = scoreTarget.toInt()
                val rank = if (securityResults.isEmpty()) null else GuardianScore.rankFor(score)
                val ringColor = rankColor(rank)

                val animatedProgress by animateFloatAsState(
                    targetValue = scoreTarget / 100f,
                    animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                    label = "scoreRing"
                )

                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(196.dp)) {
                        val strokeWidth = 12.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2f
                        val topLeft = Offset(
                            (size.width - 2 * radius) / 2f,
                            (size.height - 2 * radius) / 2f
                        )
                        val arcSize = Size(2 * radius, 2 * radius)

                        drawArc(
                            color = Color.White.copy(alpha = 0.07f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        if (animatedProgress > 0f) {
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = 360f * animatedProgress,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (securityResults.isEmpty()) "–" else "$score",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp),
                            color = ringColor
                        )
                        Text(
                            text = "GUARDIAN SCORE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (rank != null) {
                    Text(
                        text = rank.name,
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                        color = ringColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rankTagline(rank),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (securityResults.isNotEmpty()) {
                val passCount = securityResults.count { it.state == SecurityCheckState.PASS }
                val warnCount = securityResults.count { it.state == SecurityCheckState.WARN }
                val failCount = securityResults.count { it.state == SecurityCheckState.FAIL }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBadge("PASS", "$passCount", SafeGreen, Modifier.weight(1f))
                    StatBadge("WARN", "$warnCount", AttentionAmber, Modifier.weight(1f))
                    StatBadge("FAIL", "$failCount", HighRed, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HealthMetric(
                    icon = Icons.Filled.Memory,
                    label = "RAM",
                    value = ramText,
                    modifier = Modifier.weight(1f)
                )
                HealthMetric(
                    icon = Icons.Filled.Speed,
                    label = "CPU",
                    value = cpuText,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (securityResults.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SurfacePewter.copy(alpha = 0.88f))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = ElectricTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Live security posture",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricTeal
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    securityResults.forEachIndexed { index, result ->
                        SecurityCheckRow(result)
                        if (index < securityResults.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 7.dp),
                                color = Color.White.copy(alpha = 0.06f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = onNavigateToScanner,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal)
            ) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = BackgroundDeepBlack,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Run Nemesis Scanner",
                    color = BackgroundDeepBlack,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onNavigateToTimeline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("View scan timeline", color = ElectricTeal)
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HealthMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfacePewter.copy(alpha = 0.88f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = ElectricTeal, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MutedText)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SecurityCheckRow(result: SecurityCheckResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(result.state.toStatusColor())
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = result.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (result.explanation.isNotBlank()) {
                    Text(
                        text = result.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        SecurityStatusChip(result.state)
    }
}

private fun rankColor(rank: GuardianRank?): Color = when (rank) {
    GuardianRank.AEGIS -> SafeGreen
    GuardianRank.WARDED -> ElectricTeal
    GuardianRank.EXPOSED -> AttentionAmber
    GuardianRank.BREACHED -> HighRed
    null -> RestrainedGold
}

private fun rankTagline(rank: GuardianRank): String = when (rank) {
    GuardianRank.AEGIS -> "Your device looks well defended."
    GuardianRank.WARDED -> "Solid posture — keep scanning."
    GuardianRank.EXPOSED -> "Attention needed on flagged checks."
    GuardianRank.BREACHED -> "High-risk signals detected — act now."
}
