package com.coldboar.coreguard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.components.CoreGuardCard
import com.coldboar.coreguard.ui.components.PrimaryTealButton
import com.coldboar.coreguard.ui.components.ExternalSecurityToolkitPanel
import com.coldboar.coreguard.ui.components.QuillaAgentPanel
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.components.SubScreenTopBar
import com.coldboar.coreguard.ui.components.SwarmAlertCounter
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText

@Composable
fun ToolsScreen(
    onBack: () -> Unit = {},
    onRunScan: () -> Unit = {},
    onOpenShield: () -> Unit = {},
    onOpenTimeline: () -> Unit = {},
    onOpenOverlayMatrix: () -> Unit = {},
    onOpenForensicJournal: () -> Unit = {},
    onOpenScamGuard: () -> Unit = {},
    isPremium: Boolean = false
) {
    var quillaOpen by remember { mutableStateOf(true) }
    var toolkitOpen by remember { mutableStateOf(false) }

    ScreenAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SubScreenTopBar(
            title = "Tools",
            subtitle = "Quick actions and Quilla — your on-device cyber force.",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(20.dp))

        CoreGuardCard {
            Text(
                "Quick actions",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricTeal,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryTealButton(text = "Run privacy check", onClick = onRunScan)
            Spacer(modifier = Modifier.height(8.dp))
            PrimaryTealButton(text = "Open Privacy Shield", onClick = onOpenShield)
            Spacer(modifier = Modifier.height(8.dp))
            PrimaryTealButton(text = "View scan history", onClick = onOpenTimeline)
            Spacer(modifier = Modifier.height(8.dp))
            PrimaryTealButton(text = "Overlay Protection Matrix", onClick = onOpenOverlayMatrix)
            Spacer(modifier = Modifier.height(8.dp))
            PrimaryTealButton(text = "Forensic Journal", onClick = onOpenForensicJournal)
            Spacer(modifier = Modifier.height(8.dp))
            PrimaryTealButton(text = "Scam Guard", onClick = onOpenScamGuard)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Redux-separated Counter: ToolsScreen never owns the integer.
        SwarmAlertCounter()

        Spacer(modifier = Modifier.height(16.dp))

        CoreGuardCard(
            modifier = Modifier.clickable { toolkitOpen = !toolkitOpen }
        ) {
            Text(
                text = "External Security Toolkit",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricTeal,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "VirusTotal, disposable mail, evidence archive, Downdetector, Fast.com, TinEye — " +
                    "browser helpers only; CoreGuard does not wrap their APIs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (toolkitOpen) "Tap to collapse" else "Tap to expand toolkit",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }

        AnimatedVisibility(visible = toolkitOpen) {
            ExternalSecurityToolkitPanel(modifier = Modifier.padding(top = 12.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        CoreGuardCard(
            modifier = Modifier.clickable { quillaOpen = !quillaOpen }
        ) {
            Text(
                text = "Quilla workspace",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricTeal,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "OWASP · MITRE ATT&CK Mobile · pentest · IR — grounded in your scan and shield evidence. Q&A stays free.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (quillaOpen) "Tap to collapse" else "Tap to expand Quilla",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }

        AnimatedVisibility(visible = quillaOpen) {
            QuillaAgentPanel(
                modifier = Modifier.padding(top = 16.dp),
                onRunScan = onRunScan,
                onOpenShield = onOpenShield,
                onOpenTimeline = onOpenTimeline,
                isPremium = isPremium
            )
        }
    }
}
