package com.coldboar.coreguard.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.elite.ScamGuardEngine
import com.coldboar.coreguard.ui.components.CoreGuardCard
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.components.SubScreenTopBar
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.SafeGreen

@Composable
fun ScamGuardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var last by remember { mutableStateOf(ScamGuardEngine.latestFinding()) }
    val recent = remember { mutableStateOf(ScamGuardEngine.recentFindings()) }

    ScreenAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SubScreenTopBar(
            title = "Scam Guard",
            subtitle = "On-device URL / smishing heuristics · Quilla-assisted",
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Enable Notification access for CoreGuard to inspect incoming message " +
                "notifications locally. No cloud upload. Paste a suspicious link anytime.",
            color = MutedText,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        ) { Text("Open notification access settings") }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Paste URL or message text") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                last = ScamGuardEngine.inspectNotificationText(context, input, source = "manual")
                    ?: ScamGuardEngine.scoreUrl(context, input, source = "manual")
                recent.value = ScamGuardEngine.recentFindings()
            }
        ) { Text("Analyze with Scam Guard") }

        last?.let { f ->
            Spacer(modifier = Modifier.height(16.dp))
            CoreGuardCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (f.score >= 50) "AMBER WARNING" else "WATCH",
                        color = if (f.score >= 50) AttentionAmber else ElectricTeal,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(f.host, color = HighRed, style = MaterialTheme.typography.bodyLarge)
                    Text("Score ${f.score}/100", color = MutedText)
                    f.reasons.forEach { Text("• $it", color = MutedText, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Recent findings", color = ElectricTeal, style = MaterialTheme.typography.titleSmall)
        recent.value.take(10).forEach { f ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${f.score} · ${f.host}",
                color = if (f.score >= 50) AttentionAmber else SafeGreen,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
