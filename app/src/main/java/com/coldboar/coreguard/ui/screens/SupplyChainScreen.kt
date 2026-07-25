package com.coldboar.coreguard.ui.screens

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import com.coldboar.coreguard.supply.SbomGenerator
import com.coldboar.coreguard.supply.SdkAuditSummary
import com.coldboar.coreguard.supply.SdkBehaviorAuditor
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.SafeGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SupplyChainScreen() {
    val context = LocalContext.current
    var packageCount by remember { mutableStateOf<Int?>(null) }
    var sdkSummaries by remember { mutableStateOf<List<SdkAuditSummary>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Parse the CycloneDX JSON to count the components array length
            val sbomJson = SbomGenerator(context).generate()
            val count = try {
                org.json.JSONObject(sbomJson).getJSONArray("components").length()
            } catch (_: Exception) { 0 }
            packageCount = count
        }
        sdkSummaries = SdkBehaviorAuditor.summaries()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Supply Chain",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "SBOM generation and third-party SDK behavior auditing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText
        )

        Spacer(Modifier.height(24.dp))

        // SBOM Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Software Bill of Materials (CycloneDX)",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricTeal
                )
                Spacer(Modifier.height(8.dp))
                if (packageCount == null) {
                    Text("Scanning installed packages…", style = MaterialTheme.typography.bodyMedium, color = MutedText)
                } else {
                    Text(
                        "Packages enumerated: $packageCount",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "SBOM available in JSON (CycloneDX 1.5). Export via the Compliance screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // SDK Behavior Auditor Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "SDK Behavior Audit",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricTeal
                )
                Spacer(Modifier.height(8.dp))
                if (sdkSummaries.isEmpty()) {
                    Text(
                        "No SDK network events recorded yet. Events are captured as SDKs make outbound requests.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText
                    )
                } else {
                    sdkSummaries.forEach { summary ->
                        SdkAuditRow(summary)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "How SDK Auditing Works",
                    style = MaterialTheme.typography.titleSmall,
                    color = ElectricTeal
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "CoreGuard intercepts outbound network calls and attributes each request to the " +
                        "originating SDK. Requests whose URLs contain device identifiers, location data, " +
                        "credentials, or biometric signals are flagged as potentially sensitive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }
    }
}

@Composable
private fun SdkAuditRow(summary: SdkAuditSummary) {
    val flagColor = when {
        summary.flagged && summary.sensitiveRequests > 5 -> HighRed
        summary.flagged -> AttentionAmber
        else -> SafeGreen
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(summary.sdkTag, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Requests: ${summary.totalRequests} · Sensitive: ${summary.sensitiveRequests}",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }
        Text(
            text = if (summary.flagged) "⚠ FLAGGED" else "✓ OK",
            style = MaterialTheme.typography.labelMedium,
            color = flagColor
        )
    }
}
