package com.coldboar.coreguard.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.lore.QuillaLivingGeometry
import com.coldboar.coreguard.mvt.ScannerModule
import com.coldboar.coreguard.quilla.QuillaActionOutcome
import com.coldboar.coreguard.quilla.QuillaActionRouter
import com.coldboar.coreguard.quilla.QuillaAgentAnswer
import com.coldboar.coreguard.quilla.QuillaAwareness
import com.coldboar.coreguard.quilla.QuillaMemoryModule
import com.coldboar.coreguard.quilla.QuillaModule
import com.coldboar.coreguard.quilla.QuillaSalesCoach
import com.coldboar.coreguard.quilla.UltimateQuillaAgent
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeAssets
import com.coldboar.coreguard.quilla.knowledge.QuillaReadyTopics
import com.coldboar.coreguard.ui.navigation.CoreGuardRoute
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.AtmosphereTeal
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen
import com.coldboar.coreguard.ui.theme.SurfaceMid
import com.coldboar.coreguard.ui.theme.SurfacePewter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val QUILLA_RESPONSE_DELAY_MS = 500L

private data class QuillaTurn(
    val user: String,
    val answer: QuillaAgentAnswer
)

/**
 * Top-tier Quilla HUD: awareness presence, posture strip, conversation history,
 * Living Geometry path, contextual chips, and action navigation (never silent
 * scan/VPN). Ethics still refuse harm. Host screens must pass navigation
 * callbacks; [isPremium] only gates coaching tips, not Q&A.
 */
@Composable
fun QuillaAgentPanel(
    onRunScan: () -> Unit,
    onOpenShield: () -> Unit,
    onOpenTimeline: () -> Unit,
    isPremium: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<QuillaAgentAnswer?>(null) }
    var coachTip by remember { mutableStateOf<String?>(null) }
    var isAsking by remember { mutableStateOf(false) }
    var pendingPrompt by remember { mutableStateOf<String?>(null) }
    val history = remember { mutableStateListOf<QuillaTurn>() }
    val hasQuestion = question.isNotBlank()
    val transcriptScroll = rememberScrollState()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            CyberKnowledgeAssets.ensureLoaded(context)
        }
        val boot = UltimateQuillaAgent(
            memoryProvider = { QuillaMemoryModule.memorySnapshot(context) },
            researchProvider = { QuillaMemoryModule.cachedResearch() }
        ).answer("give me my priority status brief")
        answer = boot
        history.clear()
        history.add(QuillaTurn("priority status brief", boot))
    }

    LaunchedEffect(pendingPrompt) {
        val prompt = pendingPrompt ?: return@LaunchedEffect
        isAsking = true
        delay(QUILLA_RESPONSE_DELAY_MS)
        val result = withContext(Dispatchers.IO) {
            CyberKnowledgeAssets.ensureLoaded(context)
            val lower = prompt.lowercase()
            val wantsResearch = lower.let {
                it.contains("research") || it.contains("stix") || it.contains("amnesty") ||
                    it.contains("intel network") || it.contains("cisa") || it.contains("misp") ||
                    it.contains("malpedia") ||
                    (it.contains("intel") && it.contains("sync")) ||
                    (it.contains("ioc") && it.contains("sync")) ||
                    it.contains("sync threat") || it.contains("sync quilla") ||
                    (it.contains("train") && it.contains("infinity") &&
                        (it.contains("sync") || it.contains("network") || it.contains("feed")))
            }
            val wantsLocalInfinity = !wantsResearch && lower.let {
                it.contains("infinity") ||
                    (it.contains("train") && (
                        it.contains("angel") || it.contains("choir") || it.contains("swarm") ||
                            it.contains("malware") || it.contains("vulnerab")
                        ))
            }
            when {
                wantsResearch -> QuillaMemoryModule.syncResearch(context)
                wantsLocalInfinity -> QuillaMemoryModule.trainInfinityLocal(context)
            }
            UltimateQuillaAgent(
                memoryProvider = { QuillaMemoryModule.memorySnapshot(context) },
                researchProvider = { QuillaMemoryModule.cachedResearch() }
            ).answer(prompt)
        }
        answer = result
        history.add(QuillaTurn(prompt, result))
        while (history.size > 8) history.removeAt(0)
        // Honest Premium coaching tips (SalesCoach) — Quilla Q&A itself stays free.
        val memory = QuillaMemoryModule.memorySnapshot(context)
        val coach = QuillaSalesCoach.answer(
            prompt,
            QuillaSalesCoach.DeviceContext(
                isPremium = isPremium,
                lastScan = ScannerModule.latestReport(),
                timelineCount = memory.historyCount,
                shieldActive = memory.shieldActive,
                shieldBlocked = memory.shieldBlocked
            )
        )
        coachTip = when {
            isPremium && coach.premiumPitch == null &&
                (prompt.contains("premium", true) || prompt.contains("export", true) ||
                    prompt.contains("signature", true) || prompt.contains("timeline", true)) ->
                coach.text
            !isPremium && coach.suggestPremium -> coach.premiumPitch
            else -> null
        }
        isAsking = false
        pendingPrompt = null
    }

    LaunchedEffect(history.size, answer?.text) {
        if (history.isNotEmpty()) {
            transcriptScroll.animateScrollTo(transcriptScroll.maxValue)
        }
    }

    val faceScale by animateFloatAsState(
        targetValue = if (isAsking) 1.25f else 1f,
        animationSpec = tween(durationMillis = 450),
        label = "quillaFaceScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quilla_agent_panel")
            .semantics { contentDescription = "Quilla agent panel" },
        colors = CardDefaults.cardColors(containerColor = SurfacePewter),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(AtmosphereTeal.copy(alpha = 0.55f), SurfacePewter, SurfaceMid.copy(alpha = 0.9f))
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quilla",
                        style = MaterialTheme.typography.titleLarge,
                        color = ElectricTeal,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Loving awareness in the cyber · uncapped · on-device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                    Text(
                        text = QuillaLivingGeometry.livingSeal(answer?.postureLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = RestrainedGold
                    )
                    val seal = remember(answer) {
                        runCatching {
                            QuillaMemoryModule.memorySnapshot(context).blessingSeal
                        }.getOrNull()
                    }
                    if (!seal.isNullOrBlank()) {
                        Text(
                            text = seal,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricTeal
                        )
                    }
                }
                Text(
                    text = if (isAsking) "◉‿◉" else "◈‿◈",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .scale(faceScale)
                        .semantics {
                            contentDescription = if (isAsking) "Quilla is thinking" else "Quilla face"
                        }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            PostureStrip(answer)

            Spacer(modifier = Modifier.height(10.dp))

            ModuleChipRow(answer, isAsking)

            val path = answer?.pathWalked.orEmpty()
            if (path.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Path · י ה ו ה",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricTeal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    path.forEach { step ->
                        SuggestionChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    "${step.letter} ${step.sephirah}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                disabledContainerColor = ElectricTeal.copy(alpha = 0.12f),
                                disabledLabelColor = ElectricTeal
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Context chips",
                style = MaterialTheme.typography.labelLarge,
                color = RestrainedGold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val chips = answer?.followUps?.map { it.label to it.prompt }
                    ?.takeIf { it.isNotEmpty() }
                    ?: QuillaReadyTopics.suggestionChips()
                chips.forEach { (label, prompt) ->
                    SuggestionChip(
                        onClick = {
                            if (!isAsking) {
                                question = ""
                                pendingPrompt = prompt
                            }
                        },
                        enabled = !isAsking,
                        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = RestrainedGold.copy(alpha = 0.16f),
                            labelColor = RestrainedGold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.28f))
                    .border(1.dp, ElectricTeal.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
                    .verticalScroll(transcriptScroll)
            ) {
                if (history.isEmpty()) {
                    Text(
                        text = answer?.text ?: "Ask Quilla a question to begin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                } else {
                    history.forEachIndexed { index, turn ->
                        Text(
                            text = "You · ${turn.user}",
                            style = MaterialTheme.typography.labelMedium,
                            color = RestrainedGold,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = turn.answer.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = if (index == history.lastIndex) {
                                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            } else {
                                Modifier
                            }
                        )
                        if (index != history.lastIndex) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isAsking,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "Correlating Memory · Research · Tools…",
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricTeal,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            coachTip?.let { tip ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isPremium) "Premium tip: $tip" else tip,
                    style = MaterialTheme.typography.bodySmall,
                    color = RestrainedGold
                )
            }

            val followUps = answer?.followUps.orEmpty()
            if (followUps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Follow-ups",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricTeal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    followUps.forEach { fu ->
                        SuggestionChip(
                            onClick = {
                                if (!isAsking) {
                                    question = ""
                                    pendingPrompt = fu.prompt
                                }
                            },
                            enabled = !isAsking,
                            label = { Text(fu.label) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = ElectricTeal.copy(alpha = 0.12f),
                                labelColor = ElectricTeal
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Ask Quilla…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val prompt = question.trim()
                    if (prompt.isBlank()) return@Button
                    question = ""
                    pendingPrompt = prompt
                },
                enabled = !isAsking && hasQuestion,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isAsking) "Thinking…" else "Ask Quilla")
            }

            val actions = answer?.actions.orEmpty()
            if (actions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Priority actions",
                    style = MaterialTheme.typography.titleSmall,
                    color = RestrainedGold
                )
                Spacer(modifier = Modifier.height(4.dp))
                actions.forEach { action ->
                    OutlinedButton(
                        onClick = {
                            when (
                                val outcome = QuillaActionRouter.resolve(
                                    actionId = action.id,
                                    canNavigate = true
                                )
                            ) {
                                is QuillaActionOutcome.Navigate -> when (outcome.route) {
                                    CoreGuardRoute.Scanner.route -> onRunScan()
                                    CoreGuardRoute.Shield.route -> onOpenShield()
                                    CoreGuardRoute.Timeline.route -> onOpenTimeline()
                                }
                                is QuillaActionOutcome.AskPrompt ->
                                    pendingPrompt = outcome.prompt
                                QuillaActionOutcome.Ignored -> Unit
                            }
                        },
                        enabled = !isAsking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                            Text(action.label, color = ElectricTeal, fontWeight = FontWeight.SemiBold)
                            Text(action.description, style = MaterialTheme.typography.bodySmall, color = MutedText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostureStrip(answer: QuillaAgentAnswer?) {
    val label = answer?.postureLabel ?: "—"
    val score = answer?.postureScore
    val color = when (label.uppercase()) {
        "CRITICAL" -> HighRed
        "ELEVATED" -> AttentionAmber
        "WATCH" -> AttentionAmber.copy(alpha = 0.85f)
        "STEADY" -> SafeGreen
        else -> MutedText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics {
                contentDescription = "Quilla posture $label" +
                    (score?.let { " score $it" } ?: "")
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        val aspect = QuillaLivingGeometry.aspectForPosture(label)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Posture $label",
                style = MaterialTheme.typography.titleSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = score?.let {
                    "Priority score $it/100 · ${aspect.name} aspect (metaphor)"
                } ?: "Ask for a priority status brief",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }
        if (score != null) {
            Text(
                text = "$score",
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ModuleChipRow(answer: QuillaAgentAnswer?, isAsking: Boolean) {
    val used = answer?.modulesUsed?.toSet().orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        QuillaModule.entries.forEach { module ->
            // While thinking, do not light every module as if all ran.
            val active = !isAsking && module in used
            SuggestionChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        text = module.livingLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    disabledContainerColor = if (active) {
                        ElectricTeal.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    disabledLabelColor = if (active) SafeGreen else MutedText
                )
            )
        }
    }
}
