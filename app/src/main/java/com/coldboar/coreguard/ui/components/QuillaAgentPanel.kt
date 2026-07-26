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
import com.coldboar.coreguard.quilla.QuillaAgentAnswer
import com.coldboar.coreguard.quilla.QuillaMemoryFactory
import com.coldboar.coreguard.quilla.QuillaModule
import com.coldboar.coreguard.quilla.UltimateQuillaAgent
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeAssets
import com.coldboar.coreguard.quilla.knowledge.QuillaReadyTopics
import com.coldboar.coreguard.ui.navigation.QuillaActionRouter
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val QUILLA_RESPONSE_DELAY_MS = 500L

/**
 * Shared Ultimate Quilla panel: Brain / Memory / Research / Knowledge / Actions / Tools.
 *
 * Navigation callbacks should be wired by the host screen. Missing callbacks are
 * no-ops for nav destinations; threat-intel sync stays in-panel.
 */
@Composable
fun QuillaAgentPanel(
    modifier: Modifier = Modifier,
    isPremium: Boolean = false,
    onRunScan: (() -> Unit)? = null,
    onOpenShield: (() -> Unit)? = null,
    onOpenTimeline: (() -> Unit)? = null,
    onUpgradePremium: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<QuillaAgentAnswer?>(null) }
    var isAsking by remember { mutableStateOf(false) }
    var pendingPrompt by remember { mutableStateOf<String?>(null) }
    val hasQuestion = question.isNotBlank()

    fun buildAgent(): UltimateQuillaAgent = UltimateQuillaAgent(
        memoryProvider = { QuillaMemoryFactory.memorySnapshot(context) },
        researchProvider = { QuillaMemoryFactory.cachedResearch() },
        isPremiumProvider = { isPremium }
    )

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            CyberKnowledgeAssets.ensureLoaded(context)
        }
        answer = buildAgent().answer("what can you do")
    }

    LaunchedEffect(pendingPrompt) {
        val prompt = pendingPrompt ?: return@LaunchedEffect
        isAsking = true
        delay(QUILLA_RESPONSE_DELAY_MS)
        val result = withContext(Dispatchers.IO) {
            CyberKnowledgeAssets.ensureLoaded(context)
            val wantsResearch = prompt.lowercase().let {
                it.contains("research") || it.contains("intel") || it.contains("stix") ||
                    it.contains("amnesty") || it.contains("sync") ||
                    (it.contains("ioc") && it.contains("sync"))
            }
            if (wantsResearch) {
                QuillaMemoryFactory.syncResearch()
            }
            buildAgent().answer(prompt)
        }
        answer = result
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
                text = "Ultimate Quilla Agent",
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
                text = "On-device cyber codex: OWASP · MITRE ATT&CK Mobile · pentest · IR",
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

            ModuleChipRow(answer)

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
                label = { Text("Ask Ultimate Quilla…") },
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
                Text(if (isAsking) "Consulting all modules…" else "Ask Quilla")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = answer?.text ?: "Ask Quilla a question to begin.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )

            val pitch = answer?.premiumPitch
            if (answer?.suggestPremium == true && !pitch.isNullOrBlank() && onUpgradePremium != null) {
                Spacer(modifier = Modifier.height(12.dp))
                PremiumUpsellCard(
                    title = "Quilla recommends Premium",
                    body = pitch,
                    onUpgrade = onUpgradePremium
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
                            QuillaActionRouter.dispatchSuggestion(
                                actionId = action.id,
                                onScanner = onRunScan,
                                onShield = onOpenShield,
                                onTimeline = onOpenTimeline,
                                onSyncIntel = {
                                    pendingPrompt = "sync threat intel research"
                                }
                            )
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
private fun ModuleChipRow(answer: QuillaAgentAnswer?) {
    val used = answer?.modulesUsed?.toSet().orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        QuillaModule.entries.forEach { module ->
            val active = module in used || answer == null
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
