package com.coldboar.coreguard.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.CpuUsageCalculator
import com.coldboar.coreguard.EvidenceKind
import com.coldboar.coreguard.GuardianScore
import com.coldboar.coreguard.GuardianScoreEvidence
import com.coldboar.coreguard.MemoryUsageCalculator
import com.coldboar.coreguard.SecurityCheckRunner
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.elite.DynamicThreatEngine
import com.coldboar.coreguard.elite.EliteModule
import com.coldboar.coreguard.mvt.ScannerModule
import com.coldboar.coreguard.mvt.ShieldState
import com.coldboar.coreguard.ui.dashboard.ElitePalette.CardBackground
import com.coldboar.coreguard.ui.dashboard.ElitePalette.CardBorder
import com.coldboar.coreguard.ui.dashboard.ElitePalette.CyberGreen
import com.coldboar.coreguard.ui.dashboard.ElitePalette.CyberGreenGlow
import com.coldboar.coreguard.ui.dashboard.ElitePalette.DarkBackground
import com.coldboar.coreguard.ui.dashboard.ElitePalette.TextPrimary
import com.coldboar.coreguard.ui.dashboard.ElitePalette.TextSecondary
import com.coldboar.coreguard.ui.redux.rememberEliteThreatCounterState
import com.coldboar.coreguard.ui.redux.rememberSwarmAlertCounterState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * CG Elite Home dashboard — brand geometry + plain-language status hub.
 *
 * Layout priority (top → bottom):
 * 1. Status (Guardian Score rank in plain language)
 * 2. Next action (one primary CTA)
 * 3. Needs attention (FAIL/WARN evidence, not lore)
 * 4. Shortcuts + power-user mirrors
 *
 * Sacred geometry is **brand atmosphere only** — never presented as a sensor
 * reading or cryptographic proof.
 *
 * ## Module + Redux boundaries
 *
 * - **Swarm alerts** — [rememberSwarmAlertCounterState] (never own the int).
 * - **DTS / Scam amber Counter** — [rememberEliteThreatCounterState]; refresh
 *   DTS only through [EliteModule.evaluateThreatScore] (side effects stay in
 *   the module, not in this composable).
 * - **Toggles** below are local UI preferences only — not a cloud LLM switch.
 *
 * Counter subscription is centralized in `ui.redux` so Home stays free of
 * store `DisposableEffect` / `Action` imports (Redux UI separation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EliteDashboardScreen(
    onNavigateToScanner: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToShield: () -> Unit = {},
    onNavigateToTools: () -> Unit = {},
    onNavigateToOverlayMatrix: () -> Unit = {},
    onNavigateToForensicJournal: () -> Unit = {},
    onNavigateToScamGuard: () -> Unit = {}
) {
    val context = LocalContext.current

    // Local prefs only — do not treat these as entitlement or cloud AI flags.
    var realTimeEnabled by remember { mutableStateOf(true) }
    var deepScanEnabled by remember { mutableStateOf(true) }
    var quillaCorrelateEnabled by remember { mutableStateOf(true) }
    var intelSyncEnabled by remember { mutableStateOf(true) }

    var score by remember { mutableStateOf<Int?>(null) }
    var evidence by remember { mutableStateOf<List<GuardianScoreEvidence>>(emptyList()) }

    // -------------------------------------------------------------------------
    // Redux Counters — subscribe via ui.redux helpers (not inline store wiring).
    // eliteCounter / swarmCounter are mirrors; engines mutate the stores.
    // -------------------------------------------------------------------------
    val eliteCounter by rememberEliteThreatCounterState()
    val swarmCounter by rememberSwarmAlertCounterState()

    var cpuText by remember { mutableStateOf("…") }
    var ramText by remember { mutableStateOf("…") }
    var lastScanLabel by remember { mutableStateOf("No scan yet") }
    var hasScan by remember { mutableStateOf(false) }
    var appsScanned by remember { mutableStateOf("–") }
    var threatsLabel by remember { mutableStateOf("–") }
    var shieldOn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val results = withContext(Dispatchers.IO) { SecurityCheckRunner.run(context) }
        score = GuardianScore.compute(results)
        evidence = GuardianScore.explain(results)
        shieldOn = ShieldState.isActive

        val report = ScannerModule.latestReport()
        if (report != null) {
            hasScan = true
            lastScanLabel = "Last scan: ${report.verdict.name}"
            appsScanned = report.scannedPackages.toString()
            threatsLabel = report.detections.size.toString()
        } else {
            hasScan = false
            lastScanLabel = "No privacy check yet"
            appsScanned = "0"
            threatsLabel = "0"
        }

        // Module façade: evaluates DTS and dispatches into the Redux Counter.
        withContext(Dispatchers.IO) { EliteModule.evaluateThreatScore(context) }

        var ticks = 0
        while (true) {
            cpuText = CpuUsageCalculator.getUsagePercent()?.let { "$it%" } ?: "n/a"
            ramText = MemoryUsageCalculator.formatBytes(
                MemoryUsageCalculator.getUsedRamBytes(context)
            )
            shieldOn = ShieldState.isActive
            ticks++
            if (ticks % 15 == 0) {
                withContext(Dispatchers.IO) { EliteModule.evaluateThreatScore(context) }
            }
            delay(2_000L)
        }
    }

    // Read Counter fields from Redux mirrors — do not cache ints in local vars
    // that diverge from the store (that would re-couple UI to Counter logic).
    val dtsBand = eliteCounter.dtsBand
    val dtsScore = eliteCounter.dtsScore
    val swarmAlerts = swarmCounter.count
    val rank = score?.let { GuardianScore.rankFor(it) }
    val attention = remember(evidence) {
        evidence.filter { it.state == SecurityCheckState.FAIL || it.state == SecurityCheckState.WARN }
            .sortedByDescending {
                when (it.state) {
                    SecurityCheckState.FAIL -> 2
                    SecurityCheckState.WARN -> 1
                    SecurityCheckState.PASS -> 0
                }
            }
            .take(4)
    }

    val hubStatus = when {
        score == null -> "Checking…"
        dtsBand == DynamicThreatEngine.Band.CRITICAL -> "High risk"
        dtsBand == DynamicThreatEngine.Band.ELEVATED -> "Needs attention"
        else -> rank?.userLabel ?: "Checking…"
    }
    val hubGuidance = when {
        score == null -> "Running on-device security checks"
        dtsBand == DynamicThreatEngine.Band.CRITICAL ||
            dtsBand == DynamicThreatEngine.Band.ELEVATED ->
            "On-device threat score is elevated. Review findings below."
        else -> rank?.userGuidance ?: "Running on-device security checks"
    }
    val hubSub = buildString {
        append(lastScanLabel)
        append(" · Score ")
        append(score?.toString() ?: "–")
        append(" · DTS ")
        append(dtsScore)
    }

    val nextAction = remember(hasScan, shieldOn, attention, dtsBand) {
        resolveNextAction(
            hasScan = hasScan,
            shieldOn = shieldOn,
            attentionCount = attention.size,
            dtsBand = dtsBand
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(CyberGreenGlow)
                                .border(1.dp, CyberGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = CyberGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "CoreGuard",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .semantics { contentDescription = "CoreGuard Elite dashboard" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SacredGeometryStatusHub(
                statusText = hubStatus,
                guidance = hubGuidance,
                subText = hubSub
            )

            Text(
                text = "Geometry is brand artwork — not a live sensor reading.",
                color = TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription =
                            "Sacred geometry is brand artwork, not a live sensor reading"
                    }
            )

            NextActionCard(
                action = nextAction,
                onNavigateToScanner = onNavigateToScanner,
                onNavigateToShield = onNavigateToShield,
                onNavigateToOverlayMatrix = onNavigateToOverlayMatrix,
                onNavigateToForensicJournal = onNavigateToForensicJournal
            )

            eliteCounter.lastScamHost
                ?.takeIf { eliteCounter.lastScamScore >= 50 }
                ?.let { host ->
                    AmberScamPill(
                        host = host,
                        score = eliteCounter.lastScamScore,
                        onClick = onNavigateToScamGuard
                    )
                }

            NeedsAttentionSection(
                items = attention,
                onReviewOverlays = onNavigateToOverlayMatrix
            )

            DtsHonestyCaption(dtsScore = dtsScore, dtsBand = dtsBand)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickStatusItem(
                    icon = Icons.Filled.VerifiedUser,
                    label = if (shieldOn) "SHIELD\nON" else "SHIELD\nOFF",
                    contentDescription = if (shieldOn) {
                        "Shield on. Open Shield settings."
                    } else {
                        "Shield off. Open Shield settings."
                    },
                    onClick = onNavigateToShield
                )
                QuickStatusItem(
                    icon = Icons.Filled.FindInPage,
                    label = "SCANNED\n$appsScanned",
                    contentDescription = "Packages scanned: $appsScanned. Open scanner.",
                    onClick = onNavigateToScanner
                )
                QuickStatusItem(
                    icon = Icons.Filled.Lock,
                    label = if ((threatsLabel.toIntOrNull() ?: 0) == 0) "ZERO\nHITS" else "$threatsLabel\nHITS",
                    contentDescription = "Detection hits: $threatsLabel. Open timeline.",
                    onClick = onNavigateToTimeline
                )
                QuickStatusItem(
                    icon = Icons.Filled.CloudDone,
                    label = "SWARM\n$swarmAlerts",
                    contentDescription = "Swarm alerts: $swarmAlerts. Open tools.",
                    onClick = onNavigateToTools
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickStatusItem(
                    icon = Icons.Filled.Layers,
                    label = "OVERLAY\nMATRIX",
                    contentDescription = "Open Overlay Protection Matrix",
                    onClick = onNavigateToOverlayMatrix
                )
                QuickStatusItem(
                    icon = Icons.Filled.Lock,
                    label = "FORENSIC\nJOURNAL",
                    contentDescription = "Open Forensic Journal",
                    onClick = onNavigateToForensicJournal
                )
                QuickStatusItem(
                    icon = Icons.Filled.WarningAmber,
                    label = "SCAM\nGUARD",
                    contentDescription = "Open Scam Guard",
                    onClick = onNavigateToScamGuard
                )
                QuickStatusItem(
                    icon = Icons.Filled.Shield,
                    label = "DTS\n$dtsScore",
                    contentDescription =
                        "Dynamic Threat Score $dtsScore, band ${dtsBand.name}. Open Forensic Journal.",
                    onClick = onNavigateToForensicJournal
                )
            }

            Text(
                text = "POWER-USER TOOLS",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .semantics { heading() }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PowerUserCard(
                    modifier = Modifier.weight(1f),
                    title = "REAL-TIME MONITOR",
                    statusText = if (realTimeEnabled) "ENABLED" else "DISABLED",
                    statusColor = if (realTimeEnabled) CyberGreen else Color.Red,
                    isChecked = realTimeEnabled,
                    onCheckedChange = { realTimeEnabled = it }
                ) {
                    MetricMiniRow(label = "CPU", value = if (realTimeEnabled) cpuText else "—")
                    MetricMiniRow(label = "MEMORY", value = if (realTimeEnabled) ramText else "—")
                    MetricMiniRow(
                        label = "DTS",
                        value = if (realTimeEnabled) {
                            "$dtsScore ${dtsBand.name}"
                        } else {
                            "—"
                        }
                    )
                    MetricMiniRow(
                        label = "SHIELD",
                        value = if (shieldOn) "ARMED" else "IDLE"
                    )
                }

                PowerUserCard(
                    modifier = Modifier.weight(1f),
                    title = "DEEP FILE INSPECTION",
                    statusText = if (deepScanEnabled) "ACTIVE" else "PAUSED",
                    statusColor = if (deepScanEnabled) CyberGreen else Color.Yellow,
                    isChecked = deepScanEnabled,
                    onCheckedChange = { deepScanEnabled = it }
                ) {
                    MetricMiniRow(label = "PKGS SCANNED", value = appsScanned)
                    MetricMiniRow(label = "DETECTIONS", value = threatsLabel)
                    Spacer(modifier = Modifier.height(6.dp))
                    ToggleMiniRow(
                        label = "Quilla correlate",
                        checked = quillaCorrelateEnabled
                    ) { quillaCorrelateEnabled = it }
                    ToggleMiniRow(
                        label = "Threat intel",
                        checked = intelSyncEnabled
                    ) { intelSyncEnabled = it }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** One primary next step derived from scan / shield / evidence / DTS. */
internal data class HomeNextAction(
    val title: String,
    val body: String,
    val ctaLabel: String,
    val kind: HomeNextActionKind
)

internal enum class HomeNextActionKind {
    SCAN,
    SHIELD,
    REVIEW_FINDINGS,
    REVIEW_DTS,
    MAINTAIN
}

internal fun resolveNextAction(
    hasScan: Boolean,
    shieldOn: Boolean,
    attentionCount: Int,
    dtsBand: DynamicThreatEngine.Band
): HomeNextAction = when {
    !hasScan -> HomeNextAction(
        title = "Start with a privacy check",
        body = "Run an on-device scan to create a baseline. Nothing leaves this phone unless you opt into Premium signature refresh.",
        ctaLabel = "Check my device",
        kind = HomeNextActionKind.SCAN
    )
    attentionCount > 0 -> HomeNextAction(
        title = "Review what needs attention",
        body = "$attentionCount check(s) returned a warning or failure. Open Overlay Matrix to inspect live signals.",
        ctaLabel = "Review findings",
        kind = HomeNextActionKind.REVIEW_FINDINGS
    )
    dtsBand == DynamicThreatEngine.Band.CRITICAL ||
        dtsBand == DynamicThreatEngine.Band.ELEVATED -> HomeNextAction(
        title = "Threat score is elevated",
        body = "Dynamic Threat Score is an on-device correlator (not cloud AI). Open the journal for chained evidence.",
        ctaLabel = "Open journal",
        kind = HomeNextActionKind.REVIEW_DTS
    )
    !shieldOn -> HomeNextAction(
        title = "Shield is off",
        body = "Shield can block suspicious traffic locally. Turn it on when you want an extra network ward.",
        ctaLabel = "Open Shield",
        kind = HomeNextActionKind.SHIELD
    )
    else -> HomeNextAction(
        title = "Looks steady",
        body = "Re-run a privacy check periodically, especially after installing new apps.",
        ctaLabel = "Run another check",
        kind = HomeNextActionKind.MAINTAIN
    )
}

@Composable
private fun NextActionCard(
    action: HomeNextAction,
    onNavigateToScanner: () -> Unit,
    onNavigateToShield: () -> Unit,
    onNavigateToOverlayMatrix: () -> Unit,
    onNavigateToForensicJournal: () -> Unit
) {
    val onClick = when (action.kind) {
        HomeNextActionKind.SCAN, HomeNextActionKind.MAINTAIN -> onNavigateToScanner
        HomeNextActionKind.SHIELD -> onNavigateToShield
        HomeNextActionKind.REVIEW_FINDINGS -> onNavigateToOverlayMatrix
        HomeNextActionKind.REVIEW_DTS -> onNavigateToForensicJournal
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberGreen.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .semantics {
                contentDescription = "${action.title}. ${action.body}. Button: ${action.ctaLabel}"
            },
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "NEXT STEP",
                color = CyberGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = action.title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = action.body,
                color = TextSecondary,
                fontSize = 13.sp
            )
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberGreen,
                    contentColor = DarkBackground
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = action.ctaLabel }
            ) {
                Text(action.ctaLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NeedsAttentionSection(
    items: List<GuardianScoreEvidence>,
    onReviewOverlays: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "NEEDS ATTENTION",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.semantics { heading() }
        )
        if (items.isEmpty()) {
            Text(
                text = "No warnings or failures from the latest on-device checks.",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.semantics {
                    contentDescription = "Needs attention: no warnings or failures"
                }
            )
        } else {
            items.forEach { row ->
                EvidenceRowCard(row)
            }
            Text(
                text = "Review overlays",
                color = CyberGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onReviewOverlays)
                    .padding(vertical = 4.dp)
                    .semantics { contentDescription = "Open Overlay Protection Matrix" }
            )
        }
    }
}

@Composable
private fun EvidenceRowCard(row: GuardianScoreEvidence) {
    val stateColor = when (row.state) {
        SecurityCheckState.FAIL -> Color(0xFFFF6B6B)
        SecurityCheckState.WARN -> Color(0xFFFFB347)
        SecurityCheckState.PASS -> CyberGreen
    }
    val confidenceLabel = when (row.confidence) {
        EvidenceKind.VERIFIED -> "Verified signal"
        EvidenceKind.HEURISTIC -> "Heuristic"
        EvidenceKind.EDUCATIONAL -> "Guidance"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "${row.displayName}, ${row.state.name}. $confidenceLabel. ${row.explanation}"
            },
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = row.displayName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = row.state.name,
                    color = stateColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = confidenceLabel,
                color = TextSecondary,
                fontSize = 11.sp
            )
            Text(
                text = row.explanation,
                color = TextSecondary,
                fontSize = 12.sp
            )
            Text(
                text = row.recommendedAction,
                color = TextPrimary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DtsHonestyCaption(dtsScore: Int, dtsBand: DynamicThreatEngine.Band) {
    Text(
        text = "DTS $dtsScore (${dtsBand.name}) — on-device correlator with Quilla. Not cloud AI, not zero-day detection.",
        color = TextSecondary,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "Dynamic Threat Score $dtsScore, band ${dtsBand.name}. " +
                        "On-device correlator with Quilla. Not cloud AI."
            }
    )
}

@Composable
fun SacredGeometryStatusHub(
    statusText: String,
    guidance: String = "",
    subText: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sacred_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(40_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .size(280.dp)
            .padding(16.dp)
            .semantics {
                contentDescription = buildString {
                    append("Status: $statusText. ")
                    if (guidance.isNotBlank()) append("$guidance. ")
                    append(subText)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2.2f

            drawCircle(
                color = CyberGreen,
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = CyberGreenGlow,
                radius = radius + 6.dp.toPx(),
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )

            // Flower-of-Life style overlapping rings (brand metaphor only).
            for (i in 0 until 6) {
                val angle = Math.toRadians((i * 60 + rotation).toDouble())
                val circleCenter = Offset(
                    x = (center.x + radius * 0.5f * cos(angle)).toFloat(),
                    y = (center.y + radius * 0.5f * sin(angle)).toFloat()
                )
                drawCircle(
                    color = CyberGreen.copy(alpha = 0.25f),
                    radius = radius * 0.5f,
                    center = circleCenter,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            val hexPath = Path()
            for (i in 0 until 6) {
                val angle = Math.toRadians((i * 60 + rotation).toDouble())
                val x = (center.x + radius * 0.85f * cos(angle)).toFloat()
                val y = (center.y + radius * 0.85f * sin(angle)).toFloat()
                if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
            }
            hexPath.close()
            drawPath(
                path = hexPath,
                color = CyberGreen.copy(alpha = 0.4f),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(DarkBackground.copy(alpha = 0.85f))
                .border(1.dp, CyberGreen.copy(alpha = 0.5f), CircleShape)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = statusText,
                color = CyberGreen,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (guidance.isNotBlank()) {
                Text(
                    text = guidance,
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 12.sp,
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = subText,
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AmberScamPill(host: String, score: Int, onClick: () -> Unit) {
    val amber = Color(0xFFFFB347)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(amber.copy(alpha = 0.18f))
            .border(1.dp, amber, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics {
                contentDescription = "Scam Guard amber warning for $host, score $score"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = amber,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SCAM GUARD · AMBER",
                color = amber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$host · score $score",
                color = TextPrimary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun QuickStatusItem(
    icon: ImageVector,
    label: String,
    contentDescription: String = label.replace('\n', ' '),
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
            }
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyberGreen,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 11.sp
        )
    }
}

@Composable
fun PowerUserCard(
    modifier: Modifier = Modifier,
    title: String,
    statusText: String,
    statusColor: Color,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkBackground,
                        checkedTrackColor = CyberGreen,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = CardBorder
                    )
                )
            }

            Text(
                text = statusText,
                color = statusColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            HorizontalDivider(
                color = CardBorder,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            content()
        }
    }
}

@Composable
fun MetricMiniRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 10.sp)
        Text(text = value, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ToggleMiniRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 10.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(28.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = DarkBackground,
                checkedTrackColor = CyberGreen
            )
        )
    }
}
