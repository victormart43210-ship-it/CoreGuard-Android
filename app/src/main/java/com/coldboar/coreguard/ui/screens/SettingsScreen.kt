package com.coldboar.coreguard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.BillingProvider
import com.coldboar.coreguard.BuildConfig
import com.coldboar.coreguard.DemoBillingProvider
import com.coldboar.coreguard.PurchaseResult
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    billingProvider: BillingProvider = remember { DemoBillingProvider() },
    onNavigateToPrivacyPolicy: () -> Unit = {}
) {
    var isPremium by remember { mutableStateOf(billingProvider.isPremium()) }
    var purchaseStatus by remember { mutableStateOf<String?>(null) }
    var quillaOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(Modifier.height(20.dp))

        // ── Premium section ─────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isPremium)
                    RestrainedGold.copy(alpha = 0.08f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = RestrainedGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "CoreGuard Premium",
                        style = MaterialTheme.typography.titleMedium,
                        color = RestrainedGold
                    )
                }

                Spacer(Modifier.height(6.dp))

                if (isPremium) {
                    Text(
                        "✓ You're Premium — thank you for choosing to protect on purpose.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RestrainedGold
                    )
                } else {
                    Text(
                        "You don't wait for a crisis to lock your door. Go Premium to fund stronger " +
                            "signatures and scanner work — and wear the badge of someone who decided.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    purchaseStatus?.let { status ->
                        Spacer(Modifier.height(4.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall, color = ElectricTeal)
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                billingProvider.launchPurchaseFlow("coreguard_premium_monthly") { result ->
                                    when (result) {
                                        is PurchaseResult.Success -> {
                                            isPremium = true
                                            purchaseStatus = "✅ You're in — welcome to Premium."
                                        }
                                        is PurchaseResult.Cancelled -> {
                                            purchaseStatus = "Your free tools stay ready whenever you are."
                                        }
                                        is PurchaseResult.Error -> {
                                            purchaseStatus = "⚠ ${result.message}"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = RestrainedGold)
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(Modifier.size(6.dp))
                            Text("Yes — Go Premium Now", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Quilla AI assistant ──────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { quillaOpen = !quillaOpen },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Quilla Intelligence",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricTeal,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (quillaOpen) "Close" else "Open",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
                if (!quillaOpen) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ask Quilla how to raise your score and protect what matters most.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        AnimatedVisibility(visible = quillaOpen) {
            QuillaPanel(modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(16.dp))

        // ── Privacy & Legal ──────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Privacy & Legal", style = MaterialTheme.typography.titleSmall, color = MutedText)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingsLink(
                    label = "Privacy Policy",
                    icon = { Icon(Icons.Filled.Policy, contentDescription = null, tint = ElectricTeal, modifier = Modifier.size(18.dp)) },
                    onClick = onNavigateToPrivacyPolicy
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    text = "CoreGuard collects no personal data. All scans run entirely on-device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── About ─────────────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("About", style = MaterialTheme.typography.titleSmall, color = MutedText)
                Spacer(Modifier.height(8.dp))
                SettingsRow(label = "Version", value = BuildConfig.VERSION_NAME)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingsRow(label = "Build type", value = if (BuildConfig.DEBUG) "Debug" else "Release")
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Privacy signatures sourced from the Amnesty International Security Lab / mvt-project. " +
                        "CoreGuard is an independent project and is not affiliated with Amnesty International.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsLink(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.size(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MutedText,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MutedText)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val QUILLA_RESPONSE_DELAY_MS = 900L

private fun quillaResponse(prompt: String): String =
    "Quilla hears you: \"$prompt\". Threat correlation focus is active."

@Composable
private fun QuillaPanel(modifier: Modifier = Modifier) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("Ask Quilla a question to begin.") }
    var isAsking by remember { mutableStateOf(false) }
    var pendingPrompt by remember { mutableStateOf<String?>(null) }
    val hasQuestion = question.isNotBlank()

    LaunchedEffect(pendingPrompt) {
        val prompt = pendingPrompt ?: return@LaunchedEffect
        isAsking = true
        answer = "Quilla is listening…"
        delay(QUILLA_RESPONSE_DELAY_MS)
        answer = quillaResponse(prompt)
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
        Column(Modifier.padding(16.dp)) {
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

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Ask Quilla about this device's security…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

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
                Text(if (isAsking) "Consulting Quilla…" else "Ask Quilla")
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = answer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

