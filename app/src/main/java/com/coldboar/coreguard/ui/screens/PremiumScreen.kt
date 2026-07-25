package com.coldboar.coreguard.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.DemoBillingProvider
import com.coldboar.coreguard.PaywallActivity
import com.coldboar.coreguard.PurchaseResult
import com.coldboar.coreguard.ui.components.CardSpacer
import com.coldboar.coreguard.ui.components.SectionHeader
import com.coldboar.coreguard.ui.components.StatusCard
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.RestrainedGold

/**
 * Premium / paywall screen.
 *
 * Lists premium features, provides a demo purchase flow, and offers a link
 * to the legacy [PaywallActivity] for reference.
 */
@Composable
fun PremiumScreen() {
    val context = LocalContext.current
    val billing = remember { DemoBillingProvider() }
    var purchaseStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionHeader(
            title = "Premium",
            subtitle = "Unlock the full CoreGuard feature set"
        )

        Spacer(Modifier.height(20.dp))

        StatusCard {
            Text("What's included", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val features = listOf(
                "✦  Advanced threat monitoring and alerts",
                "✦  Export scan reports (JSON / CSV)",
                "✦  Priority IOC signature updates",
                "✦  Timeline history — unlimited entries",
                "✦  Priority in-app support"
            )
            features.forEach { feature ->
                Text(
                    text = feature,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        CardSpacer()

        StatusCard {
            Text("CoreGuard Premium", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "⚠️  DEMO BUILD — no real payment is processed.",
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
                            is PurchaseResult.Success ->
                                "✅ Demo purchase simulated. No real payment was made."
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

            Button(
                onClick = { context.startActivity(Intent(context, PaywallActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("View Upgrade Screen", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
