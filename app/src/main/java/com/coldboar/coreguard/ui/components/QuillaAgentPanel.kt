package com.coldboar.coreguard.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.quilla.QuillaActionSuggestion
import com.coldboar.coreguard.quilla.QuillaAgentAnswer
import com.coldboar.coreguard.quilla.QuillaMemoryFactory
import com.coldboar.coreguard.quilla.QuillaModule
import com.coldboar.coreguard.quilla.QuillaSalesCoach
import com.coldboar.coreguard.quilla.UltimateQuillaAgent
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeAssets
import com.coldboar.coreguard.quilla.knowledge.QuillaReadyTopics
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val QUILLA_RESPONSE_DELAY_MS = 500L

/**
 * Shared Quilla panel: Brain / Memory / Research / Knowledge / Actions / Tools.
 *
 * Basic Q&A stays free. Optional Research sync may use HTTPS.
 * Action buttons navigate to tools — they do not silently execute scans or VPN.
 */
@Composable
fun QuillaAgentPanel(
    modifier: Modifier = Modifier,
    onRunScan: (() -> Unit)? = null,
    onOpenShield: (() -> Unit)? = null,
    onOpenTimeline: (() -> Unit)? = null,
    isPremium: Boolean = false
) {
    val context = LocalContext.current
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<QuillaAgentAnswer?>(null) }
    var coachTip by remember { mutableStateOf<String?>(null) }
    var isAsking by remember { mutableStateOf(false) }
    var pendingPrompt by remember { mutableStateOf<String?>(null) }
    val hasQuestion = question.isNotBlank()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            CyberKnowledgeAssets.ensureLoaded(context)
        }
        answer = UltimateQuillaAgent(
            memoryProvider = { QuillaMemoryFactory.memorySnapshot(context) },
            researchProvider = { QuillaMemoryFactory.cachedResearch() }
        ).answer("what can you do")
    }

    LaunchedEffect(pendingPrompt) {
        val prompt = pendingPrompt ?: return@LaunchedEffect
        isAsking = true
        delay(QUILLA_RESPONSE_DELAY_MS)
        val result = withContext(Dispatchers.IO) {
            CyberKnowledgeAssets.ensureLoaded(context)
            val wantsResearch = prompt.lowercase().let {
                it.contains("research") || it.contains("stix") || it.contains("amnesty") ||
                    (it.contains("intel") && it.contains("sync")) ||
                    (it.contains("ioc") && it.contains("sync")) ||
                    it.contains("sync threat") || it.contains("sync quilla")
            }
            if (wantsResearch) {
                QuillaMemoryFactory.syncResearch()
            }
            UltimateQuillaAgent(
                memoryProvider = { QuillaMemoryFactory.memorySnapshot(context) },
                researchProvider = { QuillaMemoryFactory.cachedResearch() }
            ).answer(prompt)
        }
        answer = result
        // Honest Premium coaching tips (SalesCoach) — Quilla Q&A itself stays free.
        val coach = QuillaSalesCoach.answer(
            prompt,
            QuillaSalesCoach.DeviceContext(
                isPremium = isPremium,
                timelineCount = QuillaMemoryFactory.memorySnapshot(context).historyCount,
                shieldActive = QuillaMemoryFactory.memorySnapshot(context).shieldActive,
                shieldBlocked = QuillaMemoryFactory.memorySnapshot(context).shieldBlocked
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

    val faceScale by animateFloatAsState(
        targetValue = if (isAsking) 1.3f else 1f,
        animationSpec = tween(durationMillis = 450),
        label = "quillaFaceScale"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Quilla",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricTeal,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Brain · Memory · Research · Knowledge · Actions · Tools",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
            Text(
                text = "On-device cyber codex + local evidence. Optional Research sync uses HTTPS.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "◉‿◉",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .scale(faceScale)
                    .semantics {
                        contentDescription = if (isAsking) "Quilla is thinking" else "Quilla face"
                    }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ModuleChipRow(answer, isAsking)

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Ready topics",
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
                QuillaReadyTopics.suggestionChips().forEach { (label, prompt) ->
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = answer?.text ?: "Ask Quilla a question to begin.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )

            coachTip?.let { tip ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isPremium) "Premium tip: $tip" else tip,
                    style = MaterialTheme.typography.bodySmall,
                    color = RestrainedGold
                )
            }

            val actions = answer?.actions.orEmpty()
            if (actions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Actions",
                    style = MaterialTheme.typography.titleSmall,
                    color = RestrainedGold
                )
                Spacer(modifier = Modifier.height(4.dp))
                actions.forEach { action ->
                    OutlinedButton(
                        onClick = {
                            when (action.id) {
                                QuillaActionSuggestion.RUN_SCAN -> {
                                    if (onRunScan != null) onRunScan()
                                    else pendingPrompt = "how do I run a nemesis scan"
                                }
                                QuillaActionSuggestion.OPEN_SHIELD -> {
                                    if (onOpenShield != null) onOpenShield()
                                    else pendingPrompt = "how do I open privacy shield"
                                }
                                QuillaActionSuggestion.OPEN_TIMELINE -> {
                                    if (onOpenTimeline != null) onOpenTimeline()
                                    else pendingPrompt = "how do I open scan timeline"
                                }
                                QuillaActionSuggestion.SYNC_INTEL ->
                                    pendingPrompt = "sync quilla research intel"
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
                        text = "${module.label}: ${module.superpower}",
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

