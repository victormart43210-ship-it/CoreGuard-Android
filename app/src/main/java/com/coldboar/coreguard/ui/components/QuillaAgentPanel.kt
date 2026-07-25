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
import com.coldboar.coreguard.quilla.UltimateQuillaAgent
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val QUILLA_RESPONSE_DELAY_MS = 500L

/**
 * Shared Ultimate Quilla panel: Brain / Memory / Research / Actions / Tools.
 */
@Composable
fun QuillaAgentPanel(
    modifier: Modifier = Modifier,
    onRunScan: (() -> Unit)? = null,
    onOpenShield: (() -> Unit)? = null,
    onOpenTimeline: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val agent = remember {
        UltimateQuillaAgent(
            memoryProvider = { QuillaMemoryFactory.memorySnapshot(context) },
            researchProvider = { QuillaMemoryFactory.cachedResearch() }
        )
    }

    var question by remember { mutableStateOf("") }
    var answer by remember {
        mutableStateOf<QuillaAgentAnswer?>(
            agent.answer("what can you do")
        )
    }
    var isAsking by remember { mutableStateOf(false) }
    var pendingPrompt by remember { mutableStateOf<String?>(null) }
    val hasQuestion = question.isNotBlank()

    LaunchedEffect(pendingPrompt) {
        val prompt = pendingPrompt ?: return@LaunchedEffect
        isAsking = true
        delay(QUILLA_RESPONSE_DELAY_MS)
        val result = withContext(Dispatchers.IO) {
            val wantsResearch = prompt.lowercase().let {
                it.contains("research") || it.contains("intel") || it.contains("stix") ||
                    it.contains("amnesty") || it.contains("sync") || it.contains("ioc")
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
                text = "Brain · Memory · Research · Actions · Tools",
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
                                QuillaActionSuggestion.RUN_SCAN -> onRunScan?.invoke()
                                    ?: run { pendingPrompt = "run a nemesis scan" }
                                QuillaActionSuggestion.OPEN_SHIELD -> onOpenShield?.invoke()
                                    ?: run { pendingPrompt = "open privacy shield" }
                                QuillaActionSuggestion.OPEN_TIMELINE -> onOpenTimeline?.invoke()
                                    ?: run { pendingPrompt = "open scan timeline" }
                                QuillaActionSuggestion.SYNC_INTEL ->
                                    pendingPrompt = "sync threat intel research"
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
