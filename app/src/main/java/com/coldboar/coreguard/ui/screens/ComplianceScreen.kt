package com.coldboar.coreguard.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.BillingProvider
import com.coldboar.coreguard.EntitlementPolicy
import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckRunner
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.compliance.ComplianceReportExporter
import com.coldboar.coreguard.compliance.MasvsCategory
import com.coldboar.coreguard.compliance.MasvsCategoryScore
import com.coldboar.coreguard.compliance.MasvsComplianceReport
import com.coldboar.coreguard.compliance.MasvsComplianceScorer
import com.coldboar.coreguard.ui.components.CoreGuardCard
import com.coldboar.coreguard.ui.components.PremiumUpsellCard
import com.coldboar.coreguard.ui.components.PrimaryTealButton
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.components.ScreenHeader
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.SafeGreen
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ComplianceScreen(
    billingProvider: BillingProvider,
    onUpgrade: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSupplyChain: () -> Unit,
    securityResults: List<SecurityCheckResult>? = null
) {
    val context = LocalContext.current
    val isPremium by billingProvider.premiumState.collectAsState()
    val policy = remember(isPremium) { EntitlementPolicy(billingProvider) }
    var loadedResults by remember { mutableStateOf(securityResults.orEmpty()) }
    var loading by remember { mutableStateOf(securityResults == null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(securityResults, reloadToken) {
        if (securityResults != null && reloadToken == 0) {
            loadedResults = securityResults
            loading = false
            loadError = null
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        try {
            loadedResults = SecurityCheckRunner.runConcurrent(context)
        } catch (_: Throwable) {
            loadedResults = emptyList()
            loadError = "We couldn’t run the compliance checks. Try again in a moment."
        } finally {
            loading = false
        }
    }

    val report = remember(loadedResults) { MasvsComplianceScorer.score(loadedResults) }
    val passCount = loadedResults.count { it.state == SecurityCheckState.PASS }
    val warnCount = loadedResults.count { it.state == SecurityCheckState.WARN }
    val failCount = loadedResults.count { it.state == SecurityCheckState.FAIL }
    val checksReady = !loading && loadError == null && loadedResults.isNotEmpty()

    ScreenAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "Compliance",
            subtitle = "A plain-language score of your device checks, mapped to OWASP MASVS security areas. Scores are free; JSON export is Premium."
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onNavigateToSupplyChain,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal)
        ) {
            Text("Open Supply Chain tools", color = MaterialTheme.colorScheme.onPrimary)
        }

        Spacer(Modifier.height(24.dp))

        val complianceError = loadError
        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = ElectricTeal)
            }
            Spacer(Modifier.height(16.dp))
        } else if (complianceError != null) {
            CoreGuardCard(containerColor = HighRed.copy(alpha = 0.12f)) {
                Text("Couldn’t run compliance checks", style = MaterialTheme.typography.titleMedium, color = HighRed)
                Spacer(Modifier.height(6.dp))
                Text(complianceError, style = MaterialTheme.typography.bodyMedium, color = MutedText)
                Spacer(Modifier.height(12.dp))
                PrimaryTealButton(text = "Retry", onClick = { reloadToken++ })
            }
            Spacer(Modifier.height(16.dp))
        } else if (loadedResults.isEmpty()) {
            CoreGuardCard {
                Text("No checks available yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Security checks will appear here after the device evaluators finish.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText
                )
                Spacer(Modifier.height(12.dp))
                PrimaryTealButton(text = "Retry", onClick = { reloadToken++ })
            }
            Spacer(Modifier.height(16.dp))
        } else {
            CoreGuardCard {
                Text(
                    text = "Your device passed $passCount of ${loadedResults.size} checks.",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricTeal
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        failCount > 0 -> "$failCount need immediate review. Warnings: $warnCount."
                        warnCount > 0 -> "$warnCount need a closer look below."
                        else -> "No warnings right now — keep scanning periodically."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText
                )
            }
            Spacer(Modifier.height(16.dp))
            OverallScoreBanner(report.overallScore)
            Spacer(Modifier.height(16.dp))
            AttackSurfaceMap(report)
            Spacer(Modifier.height(16.dp))
            report.categoryScores.forEach { catScore ->
                CategoryScoreCard(catScore)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }

        if (policy.canExportReport()) {
            PrimaryTealButton(
                text = "Export compliance report (JSON)",
                enabled = checksReady,
                onClick = {
                    val exporter = ComplianceReportExporter(context)
                    val file = exporter.exportToFile(report)
                    exportMessage = if (file != null) {
                        "Exported: ${file.name}"
                    } else {
                        "Export failed: external storage unavailable"
                    }
                }
            )
        } else {
            OutlinedButton(
                onClick = { onNavigateToSettings() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricTeal)
            ) {
                Text("Unlock report export in Settings")
            }
        }

        exportMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (msg.startsWith("Exported")) SafeGreen else AttentionAmber
            )
        }

        if (!policy.isPremium()) {
            Spacer(Modifier.height(12.dp))
            PremiumUpsellCard(
                title = "Export what you measured",
                body = "Viewing scores stays free. Premium unlocks Compliance JSON export for your notes or review.",
                onUpgrade = onUpgrade
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OverallScoreBanner(score: Int) {
    val color = scoreColor(score)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.headlineMedium,
                    color = color
                )
            }
            Spacer(Modifier.size(16.dp))
            Column {
                Text(
                    "Overall security score",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricTeal
                )
                Text(
                    scoreLabel(score),
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
                Text(
                    "Based on ${MasvsCategory.entries.size} mobile security areas (OWASP MASVS)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }
    }
}

@Composable
private fun AttackSurfaceMap(report: MasvsComplianceReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Security areas at a glance",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricTeal
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Farther from center means stronger coverage in that area",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
            Spacer(Modifier.height(12.dp))

            val categories = report.categoryScores
            val sliceColors = categories.map { scoreColor(it.score) }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                val cx = size.width / 2
                val cy = size.height / 2
                val maxRadius = minOf(cx, cy) * 0.88f
                val n = categories.size.coerceAtLeast(1)
                val angleStep = (2 * Math.PI / n).toFloat()

                for (ring in 1..4) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        radius = maxRadius * ring / 4,
                        center = Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                    )
                }

                for (i in 0 until n) {
                    val angle = (i * angleStep - Math.PI / 2).toFloat()
                    val endX = cx + maxRadius * cos(angle)
                    val endY = cy + maxRadius * sin(angle)
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(cx, cy),
                        end = Offset(endX, endY),
                        strokeWidth = 1f
                    )
                }

                if (n >= 3) {
                    val path = androidx.compose.ui.graphics.Path()
                    categories.forEachIndexed { i, cat ->
                        val angle = (i * angleStep - Math.PI / 2).toFloat()
                        val r = maxRadius * cat.score / 100f
                        val x = cx + r * cos(angle)
                        val y = cy + r * sin(angle)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, color = ElectricTeal.copy(alpha = 0.25f))
                    drawPath(
                        path,
                        color = ElectricTeal,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                    )
                }

                categories.forEachIndexed { i, cat ->
                    val angle = (i * angleStep - Math.PI / 2).toFloat()
                    val r = maxRadius * cat.score / 100f
                    drawCircle(
                        color = sliceColors[i],
                        radius = 5f,
                        center = Offset(cx + r * cos(angle), cy + r * sin(angle))
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            categories.forEach { cat ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(scoreColor(cat.score))
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        cat.category.label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${cat.score}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = scoreColor(cat.score)
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun CategoryScoreCard(cat: MasvsCategoryScore) {
    val color = scoreColor(cat.score)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cat.category.label, style = MaterialTheme.typography.titleSmall, color = ElectricTeal)
                Text("${cat.score}%", style = MaterialTheme.typography.titleSmall, color = color)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { cat.score / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = color.copy(alpha = 0.15f)
            )
            if (cat.checks.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                cat.checks.forEach { check ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            check.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            when (check.state) {
                                SecurityCheckState.PASS -> "OK"
                                SecurityCheckState.WARN -> "Review"
                                SecurityCheckState.FAIL -> "Risk"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (check.state) {
                                SecurityCheckState.PASS -> SafeGreen
                                SecurityCheckState.WARN -> AttentionAmber
                                SecurityCheckState.FAIL -> HighRed
                            }
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text("No checks mapped to this category yet.", style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
        }
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> SafeGreen
    score >= 50 -> AttentionAmber
    else -> HighRed
}

private fun scoreLabel(score: Int): String = when {
    score >= 80 -> "Strong coverage"
    score >= 50 -> "Partial coverage – review recommended"
    else -> "Needs work — review failing checks"
}
