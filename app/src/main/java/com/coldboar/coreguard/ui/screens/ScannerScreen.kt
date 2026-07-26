package com.coldboar.coreguard.ui.screens

import android.os.Handler
import android.os.Looper
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.BillingProvider
import com.coldboar.coreguard.DemoBillingProvider
import com.coldboar.coreguard.EntitlementPolicy
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.DeviceScanner
import com.coldboar.coreguard.mvt.IocFeedFetcher
import com.coldboar.coreguard.mvt.LastScan
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict
import com.coldboar.coreguard.mvt.ThreatSeverity
import com.coldboar.coreguard.ui.components.CoreGuardCard
import com.coldboar.coreguard.ui.components.NestedSurface
import com.coldboar.coreguard.ui.components.PremiumUpsellCard
import com.coldboar.coreguard.ui.components.PrimaryTealButton
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.components.ScreenHeader
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScannerScreen(
    billingProvider: BillingProvider = remember { DemoBillingProvider() },
    onUpgrade: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPremium by billingProvider.premiumState.collectAsState()
    val policy = remember(isPremium) { EntitlementPolicy(billingProvider) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val feedExecutor = remember { Executors.newSingleThreadExecutor() }

    var isScanning by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var justCompleted by remember { mutableStateOf(false) }
    var scanReport by remember { mutableStateOf(LastScan.report) }
    var lastHistory by remember { mutableStateOf<ScanHistoryStore.ScanRecord?>(null) }
    var showUpsell by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (scanReport == null) {
            lastHistory = withContext(Dispatchers.IO) {
                ScanHistoryStore.load(context).firstOrNull()
            }
        }
    }

    val showEmptyState = !isScanning && scanReport == null && lastHistory == null && scanError == null

    ScreenAtmosphere(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = "Privacy check",
            subtitle = "Looks for known spyware indicators and suspicious signs on this device. Scans stay local; optional Premium signature refresh uses HTTPS."
        )

        Spacer(Modifier.height(20.dp))

        if (showEmptyState) {
            CoreGuardCard {
                Text(
                    text = "No scan yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricTeal
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Run a quick on-device check against open spyware indicators. It usually takes a few seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        AnimatedVisibility(
            visible = isScanning,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = ElectricTeal)
                    Text(
                        text = "Checking this device…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        PrimaryTealButton(
            text = if (isScanning) "Checking…" else "Check My Device Now",
            enabled = !isScanning && !isRefreshing,
            onClick = {
                isScanning = true
                scanError = null
                justCompleted = false
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
                        justCompleted = true
                    } catch (_: Throwable) {
                        scanError =
                            "We couldn’t finish the check. Try again, or restart the app if this keeps happening."
                    } finally {
                        isScanning = false
                    }
                }
            }
        )

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                if (!policy.canRefreshThreatSignatures()) {
                    showUpsell = true
                    refreshMessage = "Live signature refresh is a Premium unlock."
                    return@OutlinedButton
                }
                isRefreshing = true
                refreshMessage = "Pulling the newest threat signatures…"
                IocFeedFetcher.fetchAsync(context, executor = feedExecutor) { result ->
                    mainHandler.post {
                        isRefreshing = false
                        refreshMessage = when (result) {
                            is IocFeedFetcher.FetchResult.Success ->
                                "✓ ${result.indicatorsLoaded} signatures ready — run a privacy check."
                            is IocFeedFetcher.FetchResult.Failure ->
                                "Refresh failed: ${result.message}"
                        }
                    }
                }
            },
            enabled = !isScanning && !isRefreshing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (policy.canRefreshThreatSignatures()) "Refresh threat signatures"
                else "Refresh signatures (Premium)",
                color = ElectricTeal
            )
        }

        refreshMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (msg.startsWith("✓")) SafeGreen else AttentionAmber,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        if (showUpsell && !policy.isPremium()) {
            Spacer(Modifier.height(12.dp))
            PremiumUpsellCard(
                title = "Keep your intel current",
                body = "Premium unlocks live signature refresh so you can pull newer open-source IOCs before the next scan. Core scanning stays free.",
                onUpgrade = onUpgrade
            )
        }

        scanError?.let { err ->
            Spacer(Modifier.height(12.dp))
            CoreGuardCard(containerColor = HighRed.copy(alpha = 0.12f)) {
                Text(
                    text = "Check couldn’t finish",
                    style = MaterialTheme.typography.titleSmall,
                    color = HighRed
                )
                Spacer(Modifier.height(4.dp))
                Text(text = err, style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
        }

        scanReport?.let { report ->
            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(visible = true, enter = fadeIn()) {
                ScanResultCard(report, showCompletedBanner = justCompleted)
            }
        } ?: lastHistory?.let { record ->
            Spacer(Modifier.height(20.dp))
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
    val verdictLabel = when (record.verdict) {
        ScanVerdict.CLEAN -> "Looked clean"
        ScanVerdict.SUSPICIOUS -> "Possible risk"
        ScanVerdict.INFECTED -> "Threat found"
    }
    CoreGuardCard {
        Text(
            text = "Last check on this device",
            style = MaterialTheme.typography.titleMedium,
            color = MutedText
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = verdictLabel,
            style = MaterialTheme.typography.headlineSmall,
            color = verdictColor
        )
        Text(
            text = "${record.scannedArtifacts} items checked · ${record.detectionCount} findings",
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
private fun ScanResultCard(report: ScanReport, showCompletedBanner: Boolean) {
    val verdictColor = when (report.verdict) {
        ScanVerdict.CLEAN -> SafeGreen
        ScanVerdict.SUSPICIOUS -> AttentionAmber
        ScanVerdict.INFECTED -> HighRed
    }
    val verdictLabel = when (report.verdict) {
        ScanVerdict.CLEAN -> "No spyware signs found"
        ScanVerdict.SUSPICIOUS -> "Possible privacy risk"
        ScanVerdict.INFECTED -> "Privacy threat found"
    }

    CoreGuardCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        if (showCompletedBanner) {
            Text(
                text = "Scan complete",
                style = MaterialTheme.typography.labelLarge,
                color = SafeGreen,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = verdictLabel,
            style = MaterialTheme.typography.headlineSmall,
            color = verdictColor
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Checked ${report.scannedArtifacts} items in ${report.durationMillis} ms.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText
        )

        if (report.detections.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Nothing flagged on this device. A clean result is reassuring but " +
                    "not a guarantee — keep Privacy Shield on and re-check after installing new apps.",
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
            Spacer(Modifier.height(12.dp))
            NestedSurface {
                Text(
                    text = "What to do next",
                    style = MaterialTheme.typography.titleSmall,
                    color = AttentionAmber
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Don’t enter passwords or banking details until you understand the finding. " +
                        "Update your device, remove unfamiliar apps, and consider a trusted security professional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
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
    val severityLabel = when (detection.severity) {
        ThreatSeverity.CRITICAL -> "Critical"
        ThreatSeverity.HIGH -> "High"
        ThreatSeverity.MEDIUM -> "Medium"
    }
    NestedSurface {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = detection.title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = severityLabel,
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
