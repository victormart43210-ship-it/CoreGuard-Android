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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckRunner
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.compliance.ComplianceReportExporter
import com.coldboar.coreguard.compliance.MasvsCategory
import com.coldboar.coreguard.compliance.MasvsCategoryScore
import com.coldboar.coreguard.compliance.MasvsComplianceReport
import com.coldboar.coreguard.compliance.MasvsComplianceScorer
import com.coldboar.coreguard.ui.components.AtmosphereBackground
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.BackgroundDeepBlack
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.SafeGreen
import com.coldboar.coreguard.ui.theme.SurfacePewter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ComplianceScreen(securityResults: List<SecurityCheckResult>? = null) {
    val context = LocalContext.current
    var results by remember { mutableStateOf(securityResults.orEmpty()) }
    var loading by remember { mutableStateOf(securityResults == null) }

    LaunchedEffect(securityResults) {
        if (securityResults != null) {
            results = securityResults
            loading = false
        } else {
            loading = true
            results = withContext(Dispatchers.Default) { SecurityCheckRunner.run(context) }
            loading = false
        }
    }

    val report = remember(results) { MasvsComplianceScorer.score(results) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    AtmosphereBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "Compliance",
                style = MaterialTheme.typography.headlineLarge,
                color = ElectricTeal,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "OWASP MASVS v2 scoring from live on-device checks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (loading) {
                Text(
                    "Evaluating security controls…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText
                )
            } else {
                OverallScoreBanner(report.overallScore)
                Spacer(modifier = Modifier.height(16.dp))
                AttackSurfaceMap(report)
                Spacer(modifier = Modifier.height(16.dp))
                report.categoryScores.forEach { catScore ->
                    CategoryScoreCard(catScore)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val exporter = ComplianceReportExporter(context)
                        val file = exporter.exportToFile(report)
                        exportMessage = if (file != null) {
                            "Exported: ${file.name}"
                        } else {
                            "Export failed: external storage unavailable"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Export JSON", color = BackgroundDeepBlack)
                }
                exportMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (msg.startsWith("Exported")) SafeGreen else AttentionAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OverallScoreBanner(score: Int) {
    val color = scoreColor(score)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfacePewter.copy(alpha = 0.9f))
            .padding(16.dp),
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
        Spacer(modifier = Modifier.size(16.dp))
        Column {
            Text(
                "MASVS overall score",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricTeal
            )
            Text(
                scoreLabel(score),
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
            Text(
                "OWASP MASVS v2 – ${MasvsCategory.entries.size} categories",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }
    }
}

@Composable
private fun AttackSurfaceMap(report: MasvsComplianceReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfacePewter.copy(alpha = 0.9f))
            .padding(16.dp)
    ) {
        Text(
            "Attack surface map",
            style = MaterialTheme.typography.titleMedium,
            color = ElectricTeal
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Active risk level per MASVS domain (outer ring = higher score)",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText
        )
        Spacer(modifier = Modifier.height(12.dp))

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

        Spacer(modifier = Modifier.height(12.dp))

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
                Spacer(modifier = Modifier.size(8.dp))
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
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun CategoryScoreCard(cat: MasvsCategoryScore) {
    val color = scoreColor(cat.score)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfacePewter.copy(alpha = 0.88f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(cat.category.label, style = MaterialTheme.typography.titleSmall, color = ElectricTeal)
            Text("${cat.score}%", style = MaterialTheme.typography.titleSmall, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { cat.score / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
        if (cat.checks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "No checks mapped to this category yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
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
