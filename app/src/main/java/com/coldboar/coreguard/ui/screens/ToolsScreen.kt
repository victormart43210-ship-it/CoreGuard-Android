package com.coldboar.coreguard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.toolkit.ExternalSecurityToolkit
import com.coldboar.coreguard.toolkit.ExternalToolkitIntents
import com.coldboar.coreguard.ui.components.QuillaAgentPanel
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen

@Composable
fun ToolsScreen(
    onBack: () -> Unit = {},
    onRunScan: () -> Unit = {},
    onOpenShield: () -> Unit = {},
    onOpenTimeline: () -> Unit = {}
) {
    var quillaOpen by remember { mutableStateOf(true) }
    var toolkitOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Tools",
            style = MaterialTheme.typography.headlineLarge,
            color = ElectricTeal,
            modifier = Modifier
                .semantics { heading() }
                .clickable(onClick = onBack)
        )
        Text(
            text = "Quilla cyber force, on-device utilities, and curated external helpers. Tap the title to go back.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText
        )

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { quillaOpen = !quillaOpen },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ultimate Quilla",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricTeal,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Cyber force on-device: OWASP · MITRE ATT&CK Mobile · pentest methodology · incident response — plus your scan/shield evidence.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (quillaOpen) "Tap to close" else "Tap to open Quilla",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }

        AnimatedVisibility(visible = quillaOpen) {
            QuillaAgentPanel(
                modifier = Modifier.padding(top = 16.dp),
                onRunScan = onRunScan,
                onOpenShield = onOpenShield,
                onOpenTimeline = onOpenTimeline
            )
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { toolkitOpen = !toolkitOpen },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "External Security Toolkit",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricTeal,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() }
                    )
                    Text(
                        text = if (toolkitOpen) "Close" else "Open",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
                if (!toolkitOpen) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "VirusTotal, disposable mail, evidence archive, Downdetector, Fast.com, TinEye — " +
                            "browser helpers that complement Nemesis + Shield.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText
                    )
                }
            }
        }

        AnimatedVisibility(visible = toolkitOpen) {
            ExternalSecurityToolkitPanel(modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun ExternalSecurityToolkitPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = ExternalSecurityToolkit.SAFETY_BANNER,
                style = MaterialTheme.typography.bodySmall,
                color = AttentionAmber
            )
            ExternalSecurityToolkit.tools.forEach { tool ->
                ExternalToolCard(
                    tool = tool,
                    onOpen = { ExternalToolkitIntents.open(context, tool) }
                )
            }
        }
    }
}

@Composable
private fun ExternalToolCard(
    tool: ExternalSecurityToolkit.Tool,
    onOpen: () -> Unit
) {
    val categoryColor = when (tool.category) {
        ExternalSecurityToolkit.Category.MALWARE -> AttentionAmber
        ExternalSecurityToolkit.Category.PRIVACY -> ElectricTeal
        ExternalSecurityToolkit.Category.EVIDENCE -> RestrainedGold
        ExternalSecurityToolkit.Category.NETWORK -> SafeGreen
        ExternalSecurityToolkit.Category.OSINT -> MutedText
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = ElectricTeal,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            Text(
                text = tool.category.name,
                style = MaterialTheme.typography.labelLarge,
                color = categoryColor
            )
        }
        Text(tool.summary, style = MaterialTheme.typography.bodyMedium, color = MutedText)
        Text(
            text = "When: ${tool.whenToUse}",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText
        )
        tool.caution?.let { caution ->
            Text(
                text = "Caution: $caution",
                style = MaterialTheme.typography.bodySmall,
                color = AttentionAmber
            )
        }
        OutlinedButton(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open ${tool.host}", color = ElectricTeal)
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
