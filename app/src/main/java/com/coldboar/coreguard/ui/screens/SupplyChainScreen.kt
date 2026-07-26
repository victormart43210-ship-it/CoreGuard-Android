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
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.supply.SbomGenerator
import com.coldboar.coreguard.supply.SdkAuditSummary
import com.coldboar.coreguard.supply.SdkBehaviorAuditor
import com.coldboar.coreguard.ui.components.CoreGuardCard
import com.coldboar.coreguard.ui.components.EmptyStatePanel
import com.coldboar.coreguard.ui.components.LoadingLine
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.components.SubScreenTopBar
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.SafeGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SupplyChainScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var packageCount by remember { mutableStateOf<Int?>(null) }
    var sdkSummaries by remember { mutableStateOf<List<SdkAuditSummary>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val sbomJson = SbomGenerator(context).generate()
            val count = try {
                org.json.JSONObject(sbomJson).getJSONArray("components").length()
            } catch (_: Exception) {
                0
            }
            packageCount = count
        }
        sdkSummaries = SdkBehaviorAuditor.summaries()
    }

    ScreenAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SubScreenTopBar(
            title = "Supply Chain",
            subtitle = "SBOM inventory and third-party SDK behavior signals.",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        CoreGuardCard {
            Text(
                "Software Bill of Materials (CycloneDX)",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricTeal
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (packageCount == null) {
                LoadingLine("Enumerating installed packages…")
            } else {
                Text(
                    "Packages enumerated: $packageCount",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "SBOM available in JSON (CycloneDX 1.5). Export via the Compliance screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        CoreGuardCard {
            Text(
                "SDK Behavior Audit",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricTeal
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (sdkSummaries.isEmpty()) {
                EmptyStatePanel(
                    title = "Awaiting SDK signals",
                    body = "No outbound SDK network events recorded yet. Events appear as instrumented call sites observe requests.",
                    modifier = Modifier.padding(0.dp)
                )
            } else {
                sdkSummaries.forEach { summary ->
                    SdkAuditRow(summary)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        CoreGuardCard {
            Text(
                "How SDK auditing works",
                style = MaterialTheme.typography.titleSmall,
                color = ElectricTeal
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "SDK auditing records outbound request metadata that CoreGuard itself observes " +
                    "through instrumented call sites — it does not silently intercept all app traffic. " +
                    "Requests whose URLs contain device identifiers, location hints, credentials, or " +
                    "biometric signals can be flagged as potentially sensitive.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
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
    val statusLabel = when {
        summary.flagged && summary.sensitiveRequests > 5 -> "HIGH RISK"
        summary.flagged -> "FLAGGED"
        else -> "CLEAR"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(summary.sdkTag, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Requests: ${summary.totalRequests} · Sensitive: ${summary.sensitiveRequests}",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelMedium,
            color = flagColor
        )
    }
}
