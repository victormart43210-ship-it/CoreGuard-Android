package com.coldboar.coreguard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.BillingProvider
import com.coldboar.coreguard.BuildConfig
import com.coldboar.coreguard.DemoBillingProvider
import com.coldboar.coreguard.EntitlementPolicy
import com.coldboar.coreguard.PurchaseResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import com.coldboar.coreguard.hardening.DeviceHardeningGuide
import com.coldboar.coreguard.hardening.HardeningSettingsIntents
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.SafeGreen
import com.coldboar.coreguard.ui.components.QuillaAgentPanel
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold

@Composable
fun SettingsScreen(
    billingProvider: BillingProvider = remember { DemoBillingProvider() },
    onNavigateToPrivacyPolicy: () -> Unit = {},
    onNavigateToTools: () -> Unit = {},
    onRunScan: () -> Unit = {},
    onOpenShield: () -> Unit = {},
    onOpenTimeline: () -> Unit = {}
) {
    val isPremium by billingProvider.premiumState.collectAsState()
    var purchaseStatus by remember { mutableStateOf<String?>(null) }
    var quillaOpen by remember { mutableStateOf(false) }
    var hardeningOpen by remember { mutableStateOf(false) }
    val priceLabel = billingProvider.premiumPriceLabel()
    val subscribeLabel =
        if (priceLabel.isNotBlank()) "Yes — Go Premium Now · $priceLabel" else "Yes — Go Premium Now"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = ElectricTeal,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(Modifier.height(20.dp))

        // ── Premium section ─────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isPremium)
                    RestrainedGold.copy(alpha = 0.08f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = RestrainedGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "CoreGuard Premium",
                        style = MaterialTheme.typography.titleMedium,
                        color = RestrainedGold
                    )
                }

                Spacer(Modifier.height(6.dp))

                if (isPremium) {
                    Text(
                        "You're Premium — thank you for choosing to protect on purpose.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RestrainedGold
                    )
                } else {
                    Text(
                        "Unlock live signature refresh, Compliance JSON export, a longer scan timeline, and deeper Quilla coaching. Core scan + shield stay free.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText
                    )

                    purchaseStatus?.let { status ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = ElectricTeal,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            billingProvider.launchPurchaseFlow(EntitlementPolicy.PREMIUM_PRODUCT_ID) { result ->
                                when (result) {
                                    is PurchaseResult.Success -> {
                                        purchaseStatus = "Premium unlocked — thank you!"
                                    }
                                    is PurchaseResult.Cancelled -> {
                                        purchaseStatus = "Purchase cancelled — nothing was charged."
                                    }
                                    is PurchaseResult.Error -> {
                                        purchaseStatus =
                                            "Google Play couldn’t complete the purchase. Try again later."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RestrainedGold,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(subscribeLabel, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Payment & cancellation are handled by Google Play.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Quilla AI assistant ──────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { quillaOpen = !quillaOpen },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Quilla Intelligence",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricTeal,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (quillaOpen) "Close" else "Open",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
                if (!quillaOpen) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "On-device cyber force: OWASP, MITRE ATT&CK Mobile, pentest methodology, IR, and your device evidence.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        AnimatedVisibility(visible = quillaOpen) {
            QuillaAgentPanel(
                modifier = Modifier.padding(top = 8.dp),
                onRunScan = onRunScan,
                onOpenShield = onOpenShield,
                onOpenTimeline = onOpenTimeline
            )
        }

        Spacer(Modifier.height(16.dp))


        Spacer(Modifier.height(16.dp))

        // ── Device Hardening Guide ────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { hardeningOpen = !hardeningOpen },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = ElectricTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Device Hardening",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricTeal,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (hardeningOpen) "Close" else "Open",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
                if (!hardeningOpen) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Android-safe speed, battery, storage, and lock-screen tips — " +
                            "adapted from advanced tweak checklists without Windows-only hacks.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        AnimatedVisibility(visible = hardeningOpen) {
            DeviceHardeningPanel(modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(16.dp))

        // ── Tools shortcut ───────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToTools),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Tools", style = MaterialTheme.typography.titleMedium, color = ElectricTeal)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Open the Quilla tools workspace with ready-topic coaching and action buttons.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Privacy & Legal ──────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Privacy & Legal", style = MaterialTheme.typography.titleSmall, color = MutedText)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingsLink(
                    label = "Privacy Policy",
                    icon = { Icon(Icons.Filled.Policy, contentDescription = null, tint = ElectricTeal, modifier = Modifier.size(18.dp)) },
                    onClick = onNavigateToPrivacyPolicy
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    text = "CoreGuard collects no personal data. All scans run entirely on-device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── About ─────────────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("About", style = MaterialTheme.typography.titleSmall, color = MutedText)
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

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsLink(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.size(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MutedText,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MutedText)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DeviceHardeningPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = DeviceHardeningGuide.SAFETY_BANNER,
                style = MaterialTheme.typography.bodySmall,
                color = AttentionAmber
            )
            DeviceHardeningGuide.tips.forEach { tip ->
                HardeningTipCard(
                    tip = tip,
                    onOpenSettings = {
                        HardeningSettingsIntents.open(context, tip.deepLink)
                    }
                )
            }
        }
    }
}

@Composable
private fun HardeningTipCard(
    tip: DeviceHardeningGuide.Tip,
    onOpenSettings: () -> Unit
) {
    val impactColor = when (tip.impact) {
        DeviceHardeningGuide.Impact.SPEED -> ElectricTeal
        DeviceHardeningGuide.Impact.BATTERY -> SafeGreen
        DeviceHardeningGuide.Impact.STORAGE -> RestrainedGold
        DeviceHardeningGuide.Impact.SECURITY -> AttentionAmber
        DeviceHardeningGuide.Impact.SAFETY -> MutedText
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tip.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (tip.isSecurityGuardrail) AttentionAmber else ElectricTeal,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            Text(
                text = tip.impact.name,
                style = MaterialTheme.typography.labelLarge,
                color = impactColor
            )
        }
        Text(tip.summary, style = MaterialTheme.typography.bodyMedium, color = MutedText)
        tip.steps.forEachIndexed { index, step ->
            Text(
                text = "${index + 1}. $step",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }
        if (tip.deepLink != DeviceHardeningGuide.SettingsDeepLink.NONE) {
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (tip.deepLink == DeviceHardeningGuide.SettingsDeepLink.DEVELOPER_OPTIONS_HINT)
                        "Open About phone"
                    else
                        "Open system settings",
                    color = ElectricTeal
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
