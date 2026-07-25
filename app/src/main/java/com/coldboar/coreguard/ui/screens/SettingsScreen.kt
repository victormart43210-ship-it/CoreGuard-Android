package com.coldboar.coreguard.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.BuildConfig
import com.coldboar.coreguard.DemoBillingProvider
import com.coldboar.coreguard.PaywallActivity
import com.coldboar.coreguard.PurchaseResult
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val billing = remember { DemoBillingProvider() }
    var purchaseStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(Modifier.height(20.dp))

        // Subscription section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("CoreGuard Premium", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Unlock advanced monitoring, threat export, and priority support.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "⚠️ DEMO BUILD: No real payment is processed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                purchaseStatus?.let { status ->
                    Spacer(Modifier.height(4.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall, color = ElectricTeal)
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        billing.launchPurchaseFlow("coreguard_premium_monthly") { result ->
                            purchaseStatus = when (result) {
                                is PurchaseResult.Success -> "✅ Demo purchase simulated. No real payment was made."
                                is PurchaseResult.Cancelled -> "Purchase cancelled."
                                is PurchaseResult.Error -> "Error: ${result.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RestrainedGold)
                ) {
                    Text("Subscribe (Demo)", color = Color.Black)
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { context.startActivity(Intent(context, PaywallActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Upgrade Screen")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // About section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium)
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
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MutedText)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
