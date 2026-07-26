package com.coldboar.coreguard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.DeviceScanner
import com.coldboar.coreguard.mvt.LastScan
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict
import com.coldboar.coreguard.mvt.ThreatSeverity
import com.coldboar.coreguard.ui.components.AtmosphereBackground
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.BackgroundDeepBlack
import com.coldboar.coreguard.ui.theme.ElectricCyan
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.SafeGreen
import com.coldboar.coreguard.ui.theme.SurfacePewter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val scanStages = listOf(
    "Enumerating installed packages",
    "Reading process signals",
    "Matching Amnesty / MVT indicators",
    "Composing privacy verdict"
)

@Composable
fun ScannerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var stageIndex by remember { mutableIntStateOf(0) }
    var stageProgress by remember { mutableFloatStateOf(0f) }
    var scanReport by remember { mutableStateOf(LastScan.report) }

    AtmosphereBackground(accent = ElectricCyan) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "Nemesis",
                style = MaterialTheme.typography.displayLarge,
                color = ElectricTeal,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Scanner",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "A private, on-device integrity check for your right to communicate without surveillance.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )

            Spacer(modifier = Modifier.height(28.dp))

            ScanOrb(active = isScanning)

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(
                visible = isScanning,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Text(
                        text = scanStages.getOrElse(stageIndex) { "Scanning…" },
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { stageProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = ElectricTeal,
                        trackColor = Color.White.copy(alpha = 0.08f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Stage ${stageIndex + 1} of ${scanStages.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            Button(
                onClick = {
                    isScanning = true
                    stageIndex = 0
                    stageProgress = 0f
                    scope.launch {
                        val scanJob = launch(Dispatchers.IO) {
                            val result = DeviceScanner.scan(context)
                            ScanHistoryStore.append(context, result)
                            withContext(Dispatchers.Main) {
                                LastScan.report = result
                                scanReport = result
                            }
                        }
                        for (i in scanStages.indices) {
                            stageIndex = i
                            stageProgress = 0f
                            repeat(12) {
                                stageProgress = (it + 1) / 12f
                                delay(70)
                            }
                        }
                        scanJob.join()
                        isScanning = false
                    }
                },
                enabled = !isScanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal)
            ) {
                Text(
                    if (isScanning) "Scanning…" else "Run privacy check",
                    color = BackgroundDeepBlack,
                    fontWeight = FontWeight.Bold
                )
            }

            scanReport?.let { report ->
                Spacer(modifier = Modifier.height(22.dp))
                ScanResultPanel(report)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Privacy signatures sourced from the Amnesty International Security Lab / mvt-project. " +
                    "CoreGuard is an independent project and is not affiliated with Amnesty International.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }
    }
}

@Composable
private fun ScanOrb(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "scanOrb")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricTeal.copy(alpha = if (active) 0.22f * pulse else 0.08f),
                        Color.Transparent
                    )
                ),
                radius = radius
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = radius * 0.72f,
                style = Stroke(width = 2.dp.toPx())
            )
            if (active) {
                drawArc(
                    color = ElectricCyan,
                    startAngle = sweep,
                    sweepAngle = 70f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx())
                )
            } else {
                drawCircle(
                    color = ElectricTeal.copy(alpha = 0.45f),
                    radius = 6.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 2f)
                )
            }
        }
    }
}

@Composable
private fun ScanResultPanel(report: ScanReport) {
    val verdictColor = when (report.verdict) {
        ScanVerdict.CLEAN -> SafeGreen
        ScanVerdict.SUSPICIOUS -> AttentionAmber
        ScanVerdict.INFECTED -> HighRed
    }
    val verdictLabel = when (report.verdict) {
        ScanVerdict.CLEAN -> "PRIVACY INTACT"
        ScanVerdict.SUSPICIOUS -> "POSSIBLE PRIVACY RISK"
        ScanVerdict.INFECTED -> "PRIVACY THREAT FOUND"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfacePewter.copy(alpha = 0.9f))
            .padding(18.dp)
    ) {
        Text(
            text = verdictLabel,
            style = MaterialTheme.typography.headlineSmall,
            color = verdictColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${report.scannedArtifacts} items checked · " +
                "${report.indicatorCount} signatures · " +
                "${report.durationMillis} ms",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText
        )

        if (report.detections.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Nothing flagged on this device. A clean result is reassuring but " +
                    "not a guarantee — a full off-device analysis is more thorough.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )
        } else {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Findings",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricTeal
            )
            Spacer(modifier = Modifier.height(8.dp))
            report.detections
                .sortedBy { it.severity.ordinal }
                .forEach { detection ->
                    DetectionRow(detection)
                    Spacer(modifier = Modifier.height(8.dp))
                }
        }
    }
}

@Composable
private fun DetectionRow(detection: Detection) {
    val severityColor = when (detection.severity) {
        ThreatSeverity.CRITICAL -> HighRed
        ThreatSeverity.HIGH -> AttentionAmber
        ThreatSeverity.MEDIUM -> AttentionAmber
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundDeepBlack.copy(alpha = 0.45f))
            .padding(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = detection.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = detection.severity.name,
                style = MaterialTheme.typography.labelMedium,
                color = severityColor
            )
        }
        Text(text = detection.detail, style = MaterialTheme.typography.bodySmall, color = MutedText)
        detection.indicator.reference?.takeIf { it.isNotBlank() }?.let { ref ->
            Text(text = ref, style = MaterialTheme.typography.bodySmall, color = MutedText)
        }
    }
}
