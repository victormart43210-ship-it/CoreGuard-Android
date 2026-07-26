package com.coldboar.coreguard.ui.screens

import android.os.Handler
import android.os.Looper
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.BillingProvider
import com.coldboar.coreguard.EntitlementPolicy
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.DeviceScanner
import com.coldboar.coreguard.mvt.IocFeedFetcher
import com.coldboar.coreguard.mvt.LastScan
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict
import com.coldboar.coreguard.mvt.ThreatSeverity
import com.coldboar.coreguard.quilla.QuillaInsight
import com.coldboar.coreguard.ui.components.PremiumUpsellCard
import com.coldboar.coreguard.ui.components.QuillaInsightCard
import com.coldboar.coreguard.ui.navigation.QuillaActionRouter
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricCyan
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.SafeGreen
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScannerScreen(
    billingProvider: BillingProvider,
    onUpgrade: () -> Unit = {},
    onNavigateToShield: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToQuilla: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val policy = remember(billingProvider.isPremium()) { EntitlementPolicy(billingProvider) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val feedExecutor = remember { Executors.newSingleThreadExecutor() }

    var isScanning by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    var scanReport by remember { mutableStateOf(LastScan.report) }
    var showUpsell by remember { mutableStateOf(false) }
    var quillaCoach by remember { mutableStateOf<QuillaInsight.Card?>(null) }

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
            text = "In the next minute, know more about this phone's privacy posture — then Quilla coaches your next move.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText
        )

        Spacer(modifier = Modifier.height(20.dp))

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
                quillaCoach = null
                scope.launch {
                    val report = withContext(Dispatchers.IO) {
                        val result = DeviceScanner.scan(context)
                        ScanHistoryStore.append(context, result)
                        result
                    }
                    LastScan.report = report
                    scanReport = report
                    quillaCoach = QuillaInsight.postScanCard(report)
                    isScanning = false
                }
            },
            enabled = !isScanning && !isRefreshing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal)
        ) {
            Text("Check My Device Now", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

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
                                "✓ ${result.indicatorsLoaded} signatures ready — run Check My Device Now."
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (msg.startsWith("✓")) SafeGreen else AttentionAmber
            )
        }

        if (showUpsell && !policy.isPremium()) {
            Spacer(modifier = Modifier.height(12.dp))
            PremiumUpsellCard(
                title = "Keep your intel current",
                body = "Premium unlocks live signature refresh so you can pull newer open-source IOCs before the next scan. Core scanning stays free.",
                onUpgrade = onUpgrade
            )
        }

        scanReport?.let { report ->
            Spacer(modifier = Modifier.height(20.dp))
            ScanResultCard(report)
            val coach = quillaCoach ?: QuillaInsight.postScanCard(report)
            Spacer(modifier = Modifier.height(12.dp))
            QuillaInsightCard(
                card = coach,
                onAction = { action ->
                    QuillaActionRouter.dispatchInsight(
                        action = action,
                        onShield = onNavigateToShield,
                        onTimeline = onNavigateToTimeline,
                        onQuilla = onNavigateToQuilla
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Privacy signatures sourced from the Amnesty International Security Lab / mvt-project. " +
                "CoreGuard is an independent project and is not affiliated with Amnesty International.",
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
        ScanVerdict.CLEAN -> "LOOKING GOOD"
        ScanVerdict.SUSPICIOUS -> "NEEDS YOUR ATTENTION"
        ScanVerdict.INFECTED -> "ACT ON THIS FINDING"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = verdictLabel,
                style = MaterialTheme.typography.titleLarge,
                color = verdictColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${report.scannedArtifacts} items · ${report.indicatorCount} signatures · ${report.durationMillis} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (report.detections.isEmpty()) {
                Text(
                    text = "Nothing flagged on this pass. Encouraging — keep checking over time.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text("What we found for you", style = MaterialTheme.typography.titleSmall, color = ElectricTeal)
                Spacer(modifier = Modifier.height(8.dp))
                report.detections.forEach { detection ->
                    DetectionRow(detection)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun DetectionRow(detection: Detection) {
    val color = when (detection.severity) {
        ThreatSeverity.HIGH, ThreatSeverity.CRITICAL -> HighRed
        ThreatSeverity.MEDIUM -> AttentionAmber
    }
    Column {
        Text(detection.title, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
        Text(detection.detail, style = MaterialTheme.typography.bodySmall, color = MutedText)
    }
}
