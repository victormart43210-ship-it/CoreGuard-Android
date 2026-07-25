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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.compliance.ComplianceReportExporter
import com.coldboar.coreguard.compliance.MasvsCategory
import com.coldboar.coreguard.compliance.MasvsCategoryScore
import com.coldboar.coreguard.compliance.MasvsComplianceReport
import com.coldboar.coreguard.compliance.MasvsComplianceScorer
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.SafeGreen
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ComplianceScreen(securityResults: List<SecurityCheckResult> = emptyList()) {
    val context = LocalContext.current
    val report = remember(securityResults) { MasvsComplianceScorer.score(securityResults) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Compliance",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "OWASP MASVS v2 scoring and attack surface overview.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText
        )

        Spacer(Modifier.height(24.dp))

        // Overall score banner
        OverallScoreBanner(report.overallScore)

        Spacer(Modifier.height(16.dp))

        // Visual attack surface map
        AttackSurfaceMap(report)

        Spacer(Modifier.height(16.dp))

        // Per-category breakdown
        report.categoryScores.forEach { catScore ->
            CategoryScoreCard(catScore)
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Export controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val exporter = ComplianceReportExporter(context)
                    val file = exporter.exportToFile(report)
                    exportMessage = if (file != null)
                        "Exported: ${file.name}"
                    else
                        "Export failed: external storage unavailable"
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal)
            ) {
                Text("Export JSON", color = Color.Black)
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
                    "MASVS Overall Score",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricTeal
                )
                Text(
                    scoreLabel(score),
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
                Text(
                    "OWASP MASVS v2 – ${MasvsCategory.entries.size} categories evaluated",
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
                "Attack Surface Map",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricTeal
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Active risk level per MASVS domain (outer ring = higher risk)",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
            Spacer(Modifier.height(12.dp))

            // Radar-style canvas visualisation
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

                // Draw concentric reference rings
                for (ring in 1..4) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        radius = maxRadius * ring / 4,
                        center = Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                    )
                }

                // Draw spokes
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

                // Draw filled polygon for scores
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

                // Draw dots at each vertex
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

            // Legend
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
                            check.state.name,
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
    score >= 80 -> "Strong compliance"
    score >= 50 -> "Partial compliance – action recommended"
    else -> "Poor compliance – immediate remediation needed"
}
