package com.coldboar.coreguard.ui.screens

import android.os.Handler
import android.os.Looper
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coldboar.coreguard.BillingProvider
import com.coldboar.coreguard.EntitlementPolicy
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.IocFeedFetcher
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict
import com.coldboar.coreguard.mvt.ThreatSeverity
import com.coldboar.coreguard.truth.toFinding
import com.coldboar.coreguard.ui.components.CoreGuardCard
import com.coldboar.coreguard.ui.components.EmptyStatePanel
import com.coldboar.coreguard.ui.components.NestedSurface
import com.coldboar.coreguard.ui.components.PremiumUpsellCard
import com.coldboar.coreguard.ui.components.PrimaryTealButton
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.components.ScreenHeader
import com.coldboar.coreguard.ui.components.TruthSeal
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricCyan
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    billingProvider: BillingProvider,
    onUpgrade: () -> Unit,
    // TODO(phase2): inject via @HiltViewModel; manual factory used for Phase 1.
    scannerViewModel: ScannerViewModel = viewModel(
        factory = ScannerViewModel.Factory(LocalContext.current)
    )
) {
    val context = LocalContext.current
    val isPremium by billingProvider.premiumState.collectAsState()
    val policy = remember(isPremium) { EntitlementPolicy(billingProvider) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val feedExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(feedExecutor) {
        onDispose { feedExecutor.shutdown() }
    }

    val uiState by scannerViewModel.uiState.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    var showUpsell by remember { mutableStateOf(false) }

    // Derive display booleans from the immutable ViewModel state.
    val isScanning = uiState is ScannerUiState.Scanning
    val isCancelled = uiState is ScannerUiState.Cancelled
    val scanReport = (uiState as? ScannerUiState.Complete)?.report
    val scanError = (uiState as? ScannerUiState.Error)?.message
    val lastCompletedReport = when (val s = uiState) {
        is ScannerUiState.Cancelled -> s.lastCompletedReport
        is ScannerUiState.Error -> s.lastCompletedReport
        else -> null
    }
    // History is loaded by the ViewModel; show it only when no live report is available.
    val showEmptyState = uiState is ScannerUiState.Empty

    ScreenAtmosphere(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = "Nemesis Scanner",
            subtitle = "Looks for known spyware indicators and suspicious signs on this device. Scans stay local; optional Premium signature refresh uses HTTPS.",
            eyebrow = "Active sensor lattice"
        )

        Spacer(modifier = Modifier.height(16.dp))
        ScannerOrb(active = isScanning)
        Spacer(modifier = Modifier.height(12.dp))

        if (showEmptyState) {
            EmptyStatePanel(
                title = "No privacy check yet",
                body = "Run a quick on-device check against open spyware indicators. It usually takes a few seconds, and results stay on this device unless you opt into Premium signature refresh."
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Scan in progress: engine-emitted stages with indeterminate progress when totals are unknown.
        AnimatedVisibility(
            visible = isScanning,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                Text(
                    text = (uiState as? ScannerUiState.Scanning)?.currentStage?.label ?: "Scan in progress",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Indeterminate: real engine checkpoints not yet available.
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = ElectricTeal,
                    trackColor = Color.White.copy(alpha = 0.08f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = (uiState as? ScannerUiState.Scanning)?.currentStage?.visibilityLimitation
                        ?: "Android visibility limitations apply to scanner results.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Cancel button — always available during scanning.
                Button(
                    onClick = { scannerViewModel.cancelScan() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HighRed.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel Scan", color = Color.White)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Cancelled state: honest message, no verdict shown.
        AnimatedVisibility(visible = isCancelled) {
            CancelledScanContent(
                hasLastCompletedReport = lastCompletedReport != null,
                onRunNewScan = { scannerViewModel.startScan() }
            )
        }

        // Start scan button — hidden while scanning or cancelled (cancel button is shown instead).
        if (!isScanning && !isCancelled) {
            PrimaryTealButton(
                text = "Check My Device Now",
                enabled = !isRefreshing,
                onClick = { scannerViewModel.startScan() }
            )
        }

        @Composable
        internal fun CancelledScanContent(
            hasLastCompletedReport: Boolean,
            onRunNewScan: () -> Unit
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                CoreGuardCard(containerColor = AttentionAmber.copy(alpha = 0.10f)) {
                    Text(
                        text = "Scan cancelled",
                        style = MaterialTheme.typography.titleMedium,
                        color = AttentionAmber,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The scan was cancelled before completion. No final verdict or Integrity Index was recorded for this session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText
                    )
                    if (hasLastCompletedReport) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Showing your last completed scan below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryTealButton(
                    text = "Run New Scan",
                    enabled = true,
                    onClick = onRunNewScan
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (msg.startsWith("✓")) SafeGreen else AttentionAmber,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
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

        scanError?.let { err ->
            Spacer(modifier = Modifier.height(12.dp))
            CoreGuardCard(containerColor = HighRed.copy(alpha = 0.12f)) {
                Text(
                    text = "Check couldn't finish",
                    style = MaterialTheme.typography.titleSmall,
                    color = HighRed,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryTealButton(
                    text = "Try again",
                    onClick = { scannerViewModel.startScan() }
                )
            }
        }

        // Show the last completed report (from cancelled/error state) as a fallback.
        if (isCancelled && lastCompletedReport != null) {
            Spacer(modifier = Modifier.height(12.dp))
            ScanResultCard(lastCompletedReport, showCompletedBanner = false)
        } else if (!isCancelled && scanError == null) {
            scanReport?.let { report ->
                Spacer(modifier = Modifier.height(20.dp))
                ScanResultCard(report, showCompletedBanner = uiState is ScannerUiState.Complete)
            }
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
        Spacer(modifier = Modifier.height(6.dp))
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
        Spacer(modifier = Modifier.height(8.dp))
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
        ScanVerdict.CLEAN -> "No known indicators were observed in the data Android allowed CoreGuard to inspect."
        ScanVerdict.SUSPICIOUS -> "Review suggested: one or more signals need attention."
        ScanVerdict.INFECTED -> "High-confidence indicator match found."
    }

    CoreGuardCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        if (showCompletedBanner) {
            Text(
                text = "Scan complete",
                style = MaterialTheme.typography.labelLarge,
                color = SafeGreen,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = verdictLabel,
            style = MaterialTheme.typography.headlineSmall,
            color = verdictColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Checked ${report.scannedArtifacts} items in ${report.durationMillis} ms.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText
        )
        Text(
            text = "Observed findings: ${report.detections.size} · Inferred findings: 0 · Unavailable checks: visibility-limited by Android sandbox",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText
        )

        if (report.detections.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No known indicators were observed in the data Android allowed CoreGuard to inspect. " +
                    "This is not a guarantee of absence.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Findings", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
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
            Spacer(modifier = Modifier.height(12.dp))
            NestedSurface {
                Text(
                    text = "What to do next",
                    style = MaterialTheme.typography.titleSmall,
                    color = AttentionAmber
                )
                Spacer(modifier = Modifier.height(4.dp))
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
    // Convert to Finding to get normalized evidence class for TruthSeal.
    val finding = detection.toFinding()
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
        // TruthSeal shows evidence class with icon + label (not color alone) for a11y.
        TruthSeal(evidenceClass = finding.evidenceClass)
        Text(text = detection.detail, style = MaterialTheme.typography.bodySmall, color = MutedText)
        detection.indicator.reference?.takeIf { it.isNotBlank() }?.let { ref ->
            Text(text = ref, style = MaterialTheme.typography.bodySmall, color = MutedText)
        }
    }
}

@Composable
private fun ScannerOrb(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "scannerOrb")
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
            .height(168.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(158.dp)) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricTeal.copy(alpha = if (active) 0.26f * pulse else 0.1f),
                        Color.Transparent
                    )
                ),
                radius = radius
            )
            // Concentric instrument tracks
            listOf(0.55f, 0.72f, 0.9f).forEachIndexed { idx, factor ->
                drawCircle(
                    color = ElectricTeal.copy(alpha = 0.1f + idx * 0.06f),
                    radius = radius * factor,
                    style = Stroke(width = if (idx == 2) 1.6.dp.toPx() else 1.1.dp.toPx())
                )
            }
            val ticks = 48
            for (i in 0 until ticks) {
                val deg = Math.toRadians(i * 360.0 / ticks - 90.0 + if (active) sweep * 0.08 else 0.0)
                val c = kotlin.math.cos(deg).toFloat()
                val s = kotlin.math.sin(deg).toFloat()
                val major = i % 4 == 0
                val inner = radius * (if (major) 0.8f else 0.86f)
                val outer = radius * 0.94f
                drawLine(
                    color = ElectricTeal.copy(alpha = if (major) 0.45f * pulse else 0.18f),
                    start = Offset(center.x + c * inner, center.y + s * inner),
                    end = Offset(center.x + c * outer, center.y + s * outer),
                    strokeWidth = if (major) 2.2f else 1.1f
                )
            }
            // Crosshair
            val arm = radius * 0.18f
            drawLine(
                color = RestrainedGold.copy(alpha = 0.4f),
                start = Offset(center.x - arm, center.y),
                end = Offset(center.x + arm, center.y),
                strokeWidth = 1.4f
            )
            drawLine(
                color = RestrainedGold.copy(alpha = 0.4f),
                start = Offset(center.x, center.y - arm),
                end = Offset(center.x, center.y + arm),
                strokeWidth = 1.4f
            )
            if (active) {
                drawArc(
                    color = ElectricCyan,
                    startAngle = sweep,
                    sweepAngle = 78f,
                    useCenter = false,
                    style = Stroke(width = 3.2.dp.toPx())
                )
                drawArc(
                    color = RestrainedGold.copy(alpha = 0.65f),
                    startAngle = sweep + 90f,
                    sweepAngle = 22f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx())
                )
                // Secondary counter-sweep
                drawArc(
                    color = ElectricTeal.copy(alpha = 0.35f),
                    startAngle = -sweep,
                    sweepAngle = 36f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.55f, center.y - radius * 0.55f),
                    size = androidx.compose.ui.geometry.Size(radius * 1.1f, radius * 1.1f),
                    style = Stroke(width = 2.dp.toPx())
                )
            } else {
                drawCircle(
                    color = ElectricTeal.copy(alpha = 0.55f),
                    radius = 5.dp.toPx(),
                    center = center
                )
            }
        }
    }
}
