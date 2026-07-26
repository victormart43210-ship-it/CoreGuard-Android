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
import com.coldboar.coreguard.elite.EliteModule
import com.coldboar.coreguard.elite.ScamGuardEngine
import com.coldboar.coreguard.ui.components.CoreGuardCard
import com.coldboar.coreguard.ui.components.EmptyStatePanel
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.components.SubScreenTopBar
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.SafeGreen

/**
 * Scam Guard screen — presentation only.
 *
 * Analysis goes through [EliteModule] so the Redux Elite threat Counter and
 * Forensic Journal stay behind the module boundary (no engine imports for I/O).
 */
@Composable
fun ScamGuardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var last by remember { mutableStateOf(EliteModule.latestScamFinding()) }
    val recent = remember { mutableStateOf(EliteModule.recentScamFindings()) }

    ScreenAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SubScreenTopBar(
            title = "Scam Guard",
            subtitle = "On-device URL / smishing heuristics · via EliteModule",
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Before enabling Notification access, know what CoreGuard does:\n" +
                "• Reads notification title/text locally to extract URLs only\n" +
                "• Scoring is heuristic (IOC + URL patterns) — not guaranteed detection\n" +
                "• Processing stays on-device; nothing is uploaded\n" +
                "• Forensic journal may store host, score, and reason codes — not full message bodies\n" +
                "• Clear findings anytime via Settings → Delete local security data\n" +
                "• Revoke access anytime in system Notification access settings\n" +
                "Paste a suspicious link below without granting access if you prefer.",
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
        ) { Text("I understand — open notification access settings") }

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
                // Module façade: updates engine memory + Redux Counter + journal.
                last = EliteModule.inspectScamText(context, input, source = "manual")
                    ?: EliteModule.scoreScamUrl(context, input, source = "manual")
                recent.value = EliteModule.recentScamFindings()
            }
        ) { Text("Analyze with Scam Guard") }

        last?.let { f ->
            Spacer(modifier = Modifier.height(16.dp))
            FindingCard(f)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Recent findings",
            color = ElectricTeal,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        if (recent.value.isEmpty() && last == null) {
            Spacer(modifier = Modifier.height(8.dp))
            EmptyStatePanel(
                title = "No findings yet",
                body = "Paste a suspicious link above, or enable notification access so Scam Guard " +
                    "can score URLs from alerts on-device. Heuristics are not guaranteed detection."
            )
        } else {
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
}

@Composable
private fun FindingCard(f: ScamGuardEngine.Finding) {
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
            f.reasons.forEach {
                Text("• $it", color = MutedText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
