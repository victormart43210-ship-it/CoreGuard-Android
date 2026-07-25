package com.coldboar.coreguard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.DeviceScanner
import com.coldboar.coreguard.mvt.LastScan
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict
import com.coldboar.coreguard.mvt.ThreatSeverity
import com.coldboar.coreguard.ui.components.CoreGuardCard
import com.coldboar.coreguard.ui.components.NestedSurface
import com.coldboar.coreguard.ui.components.PrimaryTealButton
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.components.ScreenHeader
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScannerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var scanReport by remember { mutableStateOf(LastScan.report) }
    var lastHistory by remember { mutableStateOf<ScanHistoryStore.ScanRecord?>(null) }

    LaunchedEffect(Unit) {
        if (scanReport == null) {
            lastHistory = withContext(Dispatchers.IO) {
                ScanHistoryStore.load(context).firstOrNull()
            }
        }
    }

    ScreenAtmosphere(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = "Nemesis Scanner",
            subtitle = "On-device privacy integrity check against open threat-intelligence signatures."
        )

        Spacer(Modifier.height(20.dp))

        AnimatedVisibility(
            visible = isScanning,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = ElectricTeal)
                Text(
                    text = "Scanning…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        PrimaryTealButton(
            text = if (isScanning) "Scanning…" else "Run Privacy Check",
            enabled = !isScanning,
            onClick = {
                isScanning = true
                scanError = null
                scope.launch {
                    try {
                        val report = withContext(Dispatchers.IO) {
                            val result = DeviceScanner.scan(context)
                            ScanHistoryStore.append(context, result)
                            result
                        }
                        LastScan.report = report
                        scanReport = report
                        lastHistory = null
                    } catch (t: Throwable) {
                        scanError = t.message ?: "Scan failed"
                    } finally {
                        isScanning = false
                    }
                }
            }
        )

        scanError?.let { err ->
            Spacer(Modifier.height(12.dp))
            Text(text = err, style = MaterialTheme.typography.bodySmall, color = HighRed)
        }

        scanReport?.let { report ->
            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(visible = true, enter = fadeIn()) {
                ScanResultCard(report)
            }
        } ?: lastHistory?.let { record ->
            Spacer(modifier.height(20.dp))
            LastScanSummaryCard(record)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Privacy signatures sourced from the Amnesty International Security Lab / mvt-project. " +
                "CoreGuard is an independent project and is not affiliated with Amnesty International.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText
        )
    }
}

@Composable
private fun LastScanSummaryCard(record: ScanHistoryStore.ScanRecord) {
    val verdictColor = when (record.verdict) {
        ScanVerdict.CLEAN -> SafeGreen
        ScanVerdict.SUSPICIOUS -> AttentionAmber
        ScanVerdict.INFECTED -> HighRed
    }
    CoreGuardCard {
        Text(
            text = "Last scan on this device",
            style = MaterialTheme.typography.titleMedium,
            color = MutedText
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = record.verdict.name.replace('_', ' '),
            style = MaterialTheme.typography.headlineSmall,
            color = verdictColor
        )
        Text(
            text = "${record.scannedArtifacts} items · ${record.indicatorCount} signatures · " +
                "${record.detectionCount} findings · ${record.durationMillis} ms",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Run a new privacy check to refresh full details.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText
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

    CoreGuardCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = verdictLabel,
            style = MaterialTheme.typography.headlineSmall,
            color = verdictColor
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${report.scannedArtifacts} items checked · " +
                "${report.indicatorCount} signatures · " +
                "${report.durationMillis} ms",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText
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
            Text(text = "Findings", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            report.detections
                .sortedBy { it.severity.ordinal }
                .forEachIndexed { index, detection ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MutedText.copy(alpha = 0.2f)
                        )
                    }
                    DetectionRow(detection)
                }
        }
    }
}

@Composable
private fun DetectionRow(detection: Detection) {
    val severityColor = when (detection.severity) {
        ThreatSeverity.CRITICAL -> HighRed
        ThreatSeverity.HIGH -> AttentionAmber
        ThreatSeverity.MEDIUM -> RestrainedGold
    }
    NestedSurface {
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
        Text(text = detection.detail, style = MaterialTheme.typography.bodySmall, color = MutedText)
        detection.indicator.reference?.takeIf { it.isNotBlank() }?.let { ref ->
            Text(text = ref, style = MaterialTheme.typography.bodySmall, color = MutedText)
        }
    }
}
