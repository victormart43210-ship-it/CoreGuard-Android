package com.coldboar.coreguard.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.coldboar.coreguard.elite.ForensicJournal
import com.coldboar.coreguard.ui.components.CoreGuardCard
import com.coldboar.coreguard.ui.components.EmptyStatePanel
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.components.SubScreenTopBar
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.SafeGreen
import com.coldboar.coreguard.ui.theme.HighRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ForensicJournalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<ForensicJournal.Entry>>(emptyList()) }
    var chainOk by remember { mutableStateOf(true) }

    fun refresh() {
        // Module façade — screens avoid talking to journal storage directly.
        entries = EliteModule.journalEntries(context).asReversed()
        chainOk = EliteModule.verifyJournalChain(context)
    }

    LaunchedEffect(Unit) { refresh() }

    ScreenAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SubScreenTopBar(
            title = "Forensic Journal",
            subtitle = "Append-only · SHA-256 chain · StrongBox/TEE at rest",
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (chainOk) "Chain integrity: VALID" else "Chain integrity: BROKEN",
            color = if (chainOk) SafeGreen else HighRed,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val json = EliteModule.exportJournalJson(context)
                    shareText(context, "coreguard-forensic.json", json)
                }
            ) { Text("Export JSON") }
            OutlinedButton(
                onClick = {
                    val csv = EliteModule.exportJournalCsv(context)
                    shareText(context, "coreguard-forensic.csv", csv)
                }
            ) { Text("Export CSV") }
            OutlinedButton(onClick = { refresh() }) { Text("Refresh") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (entries.isEmpty()) {
            EmptyStatePanel(
                title = "No journal entries yet",
                body = "The Forensic Journal records Overlay Matrix findings and Dynamic Threat Score events " +
                    "in an append-only SHA-256 chain on this device. Run Overlay Matrix or wait for a " +
                    "threat-score refresh to create the first entry.",
                actionLabel = "Refresh",
                onAction = { refresh() }
            )
        }
        val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
        entries.take(40).forEach { e ->
            CoreGuardCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = e.kind.name,
                        color = ElectricTeal,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(fmt.format(Date(e.timestampMs)), color = MutedText, style = MaterialTheme.typography.labelSmall)
                    Text(e.details, color = MutedText, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "hash ${e.entryHash.take(16)}…",
                        color = MutedText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private fun shareText(context: android.content.Context, name: String, body: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, name)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(send, "Share $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
