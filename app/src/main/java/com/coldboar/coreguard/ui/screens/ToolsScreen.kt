package com.coldboar.coreguard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import kotlinx.coroutines.delay

private const val QUILLA_RESPONSE_DELAY_MS = 900L
private const val EYE_PATTERN_ROWS = 3
private const val EYE_PATTERN_TEXT = "👁   👁   👁   👁   👁"
private const val EYE_PATTERN_ALPHA = 0.18f
private const val QUILLA_FACE_SCALE_IDLE = 1f
private const val QUILLA_FACE_SCALE_ASKING = 1.4f
private const val QUILLA_INITIAL_PROMPT = "Ask Quilla a question to begin."
private const val QUILLA_LISTENING_MESSAGE = "Quilla is listening…"
private const val QUILLA_RESPONSE_TEMPLATE = "Quilla hears you: \"%s\". Threat correlation focus is active."

@Composable
fun ToolsScreen() {
    var quillaOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Tools",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Utilities and assistants available in CoreGuard.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { quillaOpen = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "Quilla",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricTeal,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Ask Quilla questions about threat signals and intelligence context.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!quillaOpen) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Tap to open Quilla",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
            }
        }

        AnimatedVisibility(visible = quillaOpen) {
            QuillaAssistantPanel(modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun QuillaAssistantPanel(modifier: Modifier = Modifier) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var isAsking by remember { mutableStateOf(false) }
    var pendingPrompt by remember { mutableStateOf<String?>(null) }
    val hasQuestionText = question.isNotBlank()

    LaunchedEffect(Unit) {
        answer = QUILLA_INITIAL_PROMPT
    }

    LaunchedEffect(pendingPrompt) {
        val prompt = pendingPrompt ?: return@LaunchedEffect
        isAsking = true
        answer = QUILLA_LISTENING_MESSAGE
        delay(QUILLA_RESPONSE_DELAY_MS)
        answer = QUILLA_RESPONSE_TEMPLATE.format(prompt)
        isAsking = false
        pendingPrompt = null
    }

    val faceScale by animateFloatAsState(
        targetValue = if (isAsking) QUILLA_FACE_SCALE_ASKING else QUILLA_FACE_SCALE_IDLE,
        label = "quillaFaceScale"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (isAsking) {
                Column(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(EYE_PATTERN_ALPHA),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    repeat(EYE_PATTERN_ROWS) {
                        Text(EYE_PATTERN_TEXT, style = MaterialTheme.typography.headlineLarge)
                    }
                }
            }

            Column {
                Text(
                    text = "Quilla",
                    style = MaterialTheme.typography.titleLarge,
                    color = ElectricTeal,
                    modifier = Modifier.semantics { heading() }
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "◉‿◉",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(96.dp)
                        .scale(faceScale)
                        .semantics {
                            contentDescription = if (isAsking) {
                                "Quilla is thinking"
                            } else {
                                "Quilla face"
                            }
                        }
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Ask Quilla a question") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (!hasQuestionText || isAsking) return@Button
                        val prompt = question.trim()
                        question = ""
                        pendingPrompt = prompt
                    },
                    enabled = !isAsking && hasQuestionText,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isAsking) "Consulting Quilla…" else "Ask Quilla")
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    }
                )
            }
        }
    }
}
