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
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.BuildTypeCheckEvaluator
import com.coldboar.coreguard.CpuUsageCalculator
import com.coldboar.coreguard.DebuggerCheckEvaluator
import com.coldboar.coreguard.EmulatorCheckEvaluator
import com.coldboar.coreguard.GuardianRank
import com.coldboar.coreguard.GuardianScore
import com.coldboar.coreguard.MemoryUsageCalculator
import com.coldboar.coreguard.RootCheckEvaluator
import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.SecurityUtils
import com.coldboar.coreguard.SignatureCheckEvaluator
import com.coldboar.coreguard.SpywareScanEvaluator
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.BackgroundDeepBlack
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen
import com.coldboar.coreguard.quilla.QuillaInsight
import com.coldboar.coreguard.ui.components.QuillaInsightCard
import com.coldboar.coreguard.ui.navigation.QuillaActionRouter
import com.coldboar.coreguard.ui.theme.SurfacePewter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onNavigateToScanner: () -> Unit,
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToShield: () -> Unit = {},
    onNavigateToQuilla: () -> Unit = {}
) {
    val context = LocalContext.current

    var ramText by remember { mutableStateOf("–") }
    var cpuText by remember { mutableStateOf("Measuring…") }
    var securityResults by remember { mutableStateOf<List<SecurityCheckResult>>(emptyList()) }
    var scoreTarget by remember { mutableFloatStateOf(0f) }
    var quillaCard by remember { mutableStateOf<QuillaInsight.Card?>(null) }

    LaunchedEffect(Unit) {
        quillaCard = withContext(Dispatchers.IO) { QuillaInsight.homeCard(context) }
        val certSha256 = withContext(Dispatchers.IO) { SecurityUtils.getAppCertSha256(context) }
        val evaluators = listOf(
            SpywareScanEvaluator(),
            DebuggerCheckEvaluator(),
            EmulatorCheckEvaluator(),
            RootCheckEvaluator(),
            BuildTypeCheckEvaluator(),
            SignatureCheckEvaluator(actualSha256 = { certSha256 })
        )
        securityResults = evaluators.map { it.evaluate() }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(BackgroundDeepBlack)
            .padding(bottom = 24.dp)
    ) {
        // ── Hero Header ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SurfacePewter,
                            BackgroundDeepBlack
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "CoreGuard",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = ElectricTeal,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Know your phone's risk — then let Quilla coach the next move",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText
                )

                Spacer(Modifier.height(28.dp))

                // ── Guardian Score Ring ─────────────────────────────────────
                val score = scoreTarget.toInt()
                val rank = if (securityResults.isEmpty()) null else GuardianScore.rankFor(score)
                val ringColor = rankColor(rank)

                val animatedProgress by animateFloatAsState(
                    targetValue = scoreTarget / 100f,
                    animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                    label = "scoreRing"
                )

                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(180.dp)) {
                        val strokeWidth = 14.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2f
                        val topLeft = Offset(
                            (size.width - 2 * radius) / 2f,
                            (size.height - 2 * radius) / 2f
                        )
                        val arcSize = Size(2 * radius, 2 * radius)

                        // Track ring
                        drawArc(
                            color = Color.White.copy(alpha = 0.08f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Score arc with glow effect
                        if (animatedProgress > 0f) {
                            // Glow layer
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        ringColor.copy(alpha = 0f),
                                        ringColor.copy(alpha = 0.4f),
                                        ringColor
                                    )
                                ),
                                startAngle = -90f,
                                sweepAngle = 360f * animatedProgress,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth * 2.2f, cap = StrokeCap.Round)
                            )
                            // Main arc
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        ringColor.copy(alpha = 0.3f),
                                        ringColor.copy(alpha = 0.7f),
                                        ringColor
                                    )
                                ),
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
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 42.sp
                            ),
                            color = ringColor
                        )
                        Text(
                            text = "GUARDIAN",
                            style = MaterialTheme.typography.bodySmall.copy(
                                letterSpacing = 3.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MutedText
                        )
                        Text(
                            text = "SCORE",
                            style = MaterialTheme.typography.bodySmall.copy(
                                letterSpacing = 3.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MutedText
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Rank badge
                if (rank != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ringColor.copy(alpha = 0.15f))
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = rank.name,
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                            color = ringColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Quick Stats ──────────────────────────────────────────────────────
        if (securityResults.isNotEmpty()) {
            val passCount = securityResults.count { it.state == SecurityCheckState.PASS }
            val warnCount = securityResults.count { it.state == SecurityCheckState.WARN }
            val failCount = securityResults.count { it.state == SecurityCheckState.FAIL }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBadge(
                    label = "PASS",
                    value = "$passCount",
                    color = SafeGreen,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    label = "WARN",
                    value = "$warnCount",
                    color = AttentionAmber,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    label = "FAIL",
                    value = "$failCount",
                    color = HighRed,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))
        }

        quillaCard?.let { card ->
            QuillaInsightCard(
                card = card,
                onAction = { action ->
                    QuillaActionRouter.dispatchInsight(
                        action = action,
                        onScanner = onNavigateToScanner,
                        onShield = onNavigateToShield,
                        onTimeline = onNavigateToTimeline,
                        onQuilla = onNavigateToQuilla
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(16.dp))
        }

        val failCount = securityResults.count { it.state == SecurityCheckState.FAIL }
        val needsAttention = securityResults.any {
            it.state == SecurityCheckState.WARN || it.state == SecurityCheckState.FAIL
        }
        if (needsAttention && quillaCard == null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = AttentionAmber.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "What to do next",
                        style = MaterialTheme.typography.titleMedium,
                        color = AttentionAmber
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (failCount > 0) {
                            "Review failed checks below, then run a privacy check. Avoid entering passwords until you understand the risk."
                        } else {
                            "Review the warnings below. A privacy check can catch spyware indicators these checks miss."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── System Health ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HealthCard(
                icon = { Icon(Icons.Filled.Memory, contentDescription = null, tint = ElectricTeal, modifier = Modifier.size(20.dp)) },
                label = "RAM",
                value = ramText,
                modifier = Modifier.weight(1f)
            )
            HealthCard(
                icon = { Icon(Icons.Filled.BatteryFull, contentDescription = null, tint = ElectricTeal, modifier = Modifier.size(20.dp)) },
                label = "CPU",
                value = cpuText,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Security Checks Detail ────────────────────────────────────────────
        if (securityResults.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfacePewter),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = ElectricTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Security Checks",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricTeal
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    securityResults.forEachIndexed { index, result ->
                        SecurityCheckRow(result)
                        if (index < securityResults.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = Color.White.copy(alpha = 0.06f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── CTA ───────────────────────────────────────────────────────────────
        Button(
            onClick = onNavigateToScanner,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal)
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = BackgroundDeepBlack, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Check My Device Now",
                color = BackgroundDeepBlack,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNavigateToTimeline,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("View Scan Timeline", color = ElectricTeal)
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNavigateToQuilla,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Ask Quilla - your on-device cyber coach", color = RestrainedGold)
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 1.sp),
                color = color.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HealthCard(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfacePewter),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
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
                    .background(result.state.toColor())
            )
            Spacer(Modifier.width(10.dp))
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
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(result.state.toColor().copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = result.state.name,
                style = MaterialTheme.typography.labelLarge,
                color = result.state.toColor()
            )
        }
    }
}

private fun SecurityCheckState.toColor(): Color = when (this) {
    SecurityCheckState.PASS -> SafeGreen
    SecurityCheckState.WARN -> AttentionAmber
    SecurityCheckState.FAIL -> HighRed
}

private fun rankColor(rank: GuardianRank?): Color = when (rank) {
    GuardianRank.AEGIS -> SafeGreen
    GuardianRank.WARDED -> ElectricTeal
    GuardianRank.EXPOSED -> AttentionAmber
    GuardianRank.BREACHED -> HighRed
    null -> RestrainedGold
}

