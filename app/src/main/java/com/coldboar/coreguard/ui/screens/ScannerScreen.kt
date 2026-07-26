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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.coldboar.coreguard.BillingProvider
import com.coldboar.coreguard.ui.rememberAppBillingProvider
import com.coldboar.coreguard.EntitlementPolicy
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.IocFeedFetcher
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict
import com.coldboar.coreguard.mvt.ScannerModule
import com.coldboar.coreguard.mvt.ThreatSeverity
import com.coldboar.coreguard.ui.components.CoreGuardCard
import com.coldboar.coreguard.ui.components.EmptyStatePanel
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
import com.coldboar.coreguard.ui.theme.ElectricCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val scanStages = listOf(
    "Enumerating installed packages",
    "Reading process signals",
    "Matching Amnesty / MVT indicators",
    "Composing privacy verdict"
)

@Composable
fun ScannerScreen(
    billingProvider: BillingProvider = rememberAppBillingProvider(),
    onUpgrade: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPremium by billingProvider.premiumState.collectAsState()
    val policy = remember(isPremium) { EntitlementPolicy(billingProvider) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val feedExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(feedExecutor) {
        onDispose { feedExecutor.shutdown() }
    }

    var isScanning by remember { mutableStateOf(false) }
    var stageIndex by remember { mutableIntStateOf(0) }
    var stageProgress by remember { mutableFloatStateOf(0f) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var justCompleted by remember { mutableStateOf(false) }
    var scanReport by remember { mutableStateOf(ScannerModule.latestReport()) }
    var lastHistory by remember { mutableStateOf<ScanHistoryStore.ScanRecord?>(null) }
    var showUpsell by remember { mutableStateOf(false) }
    var quillaChoirNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (scanReport == null) {
            lastHistory = withContext(Dispatchers.IO) {
                ScannerModule.loadHistory(context).firstOrNull()
            }
        }
    }

    val showEmptyState = !isScanning && scanReport == null && lastHistory == null && scanError == null

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
                    text = scanStages.getOrElse(stageIndex) { "Checking this device…" },
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

        PrimaryTealButton(
            text = if (isScanning) "Checking…" else "Check My Device Now",
            enabled = !isScanning && !isRefreshing,
            onClick = {
                isScanning = true
                stageIndex = 0
                stageProgress = 0f
                scanError = null
                justCompleted = false
                scope.launch {
                    try {
                        val scanJob = launch(Dispatchers.IO) {
                            // scanDevice persists history + bridges Quilla/choir/Elite/Swarm.
                            val result = ScannerModule.scanDevice(context)
                            val bridge = ScannerModule.lastQuillaBridge()
                            withContext(Dispatchers.Main) {
                                scanReport = result
                                lastHistory = null
                                justCompleted = true
                                quillaChoirNote = bridge?.scannerBlurb()
                            }
                        }
                        for (i in scanStages.indices) {
                            stageIndex = i
                            stageProgress = 0f
                            repeat(10) {
                                stageProgress = (it + 1) / 10f
                                delay(60)
                            }
                        }
                        scanJob.join()
                    } catch (_: Throwable) {
                        scanError =
                            "We couldn’t finish the check. Try again, or restart the app if this keeps happening."
                    } finally {
                        isScanning = false
                    }
                }
            }
        )

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
                    text = "Check couldn’t finish",
                    style = MaterialTheme.typography.titleSmall,
                    color = HighRed
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = err, style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
        }

        scanReport?.let { report ->
            Spacer(modifier = Modifier.height(20.dp))
            AnimatedVisibility(visible = true, enter = fadeIn()) {
                ScanResultCard(report, showCompletedBanner = justCompleted)
            }
            quillaChoirNote?.let { note ->
                Spacer(modifier = Modifier.height(12.dp))
                CoreGuardCard {
                    Text(
                        text = "Quilla · angelic choir",
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricTeal,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                        modifier = Modifier.semantics {
                            contentDescription = note
                            liveRegion = LiveRegionMode.Polite
                        }
                    )
                }
            }
        } ?: lastHistory?.let { record ->
            Spacer(modifier = Modifier.height(20.dp))
            LastScanSummaryCard(record)
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

        if (report.detections.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Nothing flagged on this device. A clean result is reassuring but " +
                    "not a guarantee — keep Privacy Shield on and re-check after installing new apps.",
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
