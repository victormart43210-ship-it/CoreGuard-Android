package com.coldboar.coreguard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.coldboar.coreguard.ui.components.QuillaAgentPanel
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText

@Composable
fun ToolsScreen(
    onBack: () -> Unit = {},
    onRunScan: () -> Unit = {},
    onOpenShield: () -> Unit = {},
    onOpenTimeline: () -> Unit = {}
) {
    var quillaOpen by remember { mutableStateOf(true) }

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
            text = "Quilla cyber force and on-device utilities. Tap the title to go back.",
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
    }
}
