package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.DeviceScanner
import com.coldboar.coreguard.mvt.LastScan
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict
import com.coldboar.coreguard.mvt.ThreatSeverity
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricCyan
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.SafeGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScannerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var scanReport by remember { mutableStateOf(LastScan.report) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Nemesis Scanner",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "A privacy integrity check for your device — defending your right to private communication.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(20.dp))

        if (isScanning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = ElectricCyan)
                Text(
                    text = "  Scanning…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }

        Button(
            onClick = {
                isScanning = true
                scope.launch {
                    val report = withContext(Dispatchers.IO) { DeviceScanner.scan(context) }
                    LastScan.report = report
                    scanReport = report
                    isScanning = false
                }
            },
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal)
        ) {
            Text("Run Privacy Check", color = Color.Black)
        }

        scanReport?.let { report ->
            Spacer(Modifier.height(20.dp))
            ScanResultCard(report)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Privacy signatures sourced from the Amnesty International Security Lab / mvt-project. " +
                "CoreGuard is an independent project and is not affiliated with Amnesty International.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ScanResultCard(report: ScanReport) {
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = verdictLabel,
                style = MaterialTheme.typography.titleLarge,
                color = verdictColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${report.scannedArtifacts} items checked · " +
                    "${report.indicatorCount} signatures · " +
                    "${report.durationMillis} ms",
                style = MaterialTheme.typography.bodySmall
            )

            if (report.detections.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Nothing flagged on this device. A clean result is reassuring but " +
                        "not a guarantee — a full off-device analysis is more thorough.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Findings",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                report.detections
                    .sortedBy { it.severity.ordinal }
                    .forEach { detection ->
                        DetectionRow(detection)
                        Spacer(Modifier.height(8.dp))
                    }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = detection.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = detection.severity.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = severityColor
                )
            }
            Text(text = detection.detail, style = MaterialTheme.typography.bodySmall)
            if (!detection.indicator.reference.isNullOrBlank()) {
                Text(
                    text = detection.indicator.reference!!,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
