package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "CoreGuard Privacy Policy",
                style = MaterialTheme.typography.headlineSmall,
                color = ElectricTeal,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Effective Date: July 2026",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )

            Spacer(Modifier.height(20.dp))

            PolicySection(
                title = "Our Commitment",
                body = "CoreGuard is designed to protect your privacy, not exploit it. " +
                    "We do not sell personal data or show ads. " +
                    "Security scans and local reports stay on your device; optional threat-intel " +
                    "downloads and Privacy Shield DNS forwarding are described below."
            )

            PolicySection(
                title = "Data We Do Not Collect",
                body = "CoreGuard does not collect:\n" +
                    "• Your name, email, or contact information\n" +
                    "• Device identifiers or advertising IDs\n" +
                    "• Location data\n" +
                    "• Browsing or app usage history\n" +
                    "• Biometric data\n" +
                    "• Financial information beyond purchase receipts processed by Google Play"
            )

            PolicySection(
                title = "On-Device Processing",
                body = "All security scans, threat analysis, and compliance scoring run locally on your device. " +
                    "Scan results are stored only in your device's private app storage and are never transmitted off-device."
            )

            PolicySection(
                title = "Threat Signature Updates",
                body = "CoreGuard may optionally fetch threat signatures (IOC lists) from the " +
                    "Amnesty International Security Lab and other open-source threat intelligence feeds. " +
                    "These requests are anonymous — no identifying information is sent."
            )

            PolicySection(
                title = "Privacy Shield (VPN)",
                body = "Privacy Shield uses Android's local VPN API as a DNS filter against known " +
                    "surveillance / tracker domains. Blocked domains receive an NXDOMAIN-style response " +
                    "on-device. Allowed DNS queries are forwarded to your device's system DNS resolver, " +
                    "or to a public resolver fallback (8.8.8.8) if none is available. " +
                    "CoreGuard does not inspect or store the contents of your other app traffic, and " +
                    "does not route all traffic through CoreGuard servers."
            )

            PolicySection(
                title = "Permissions Explained",
                body = "• INTERNET — Required only to fetch optional threat signature updates and for the on-device VPN to forward allowed DNS queries.\n" +
                    "• FOREGROUND_SERVICE — Required to run the Privacy Shield VPN as a persistent background service.\n" +
                    "• POST_NOTIFICATIONS — Required to show the VPN status notification.\n" +
                    "• QUERY_ALL_PACKAGES — Required to check installed apps against spyware package indicators on your device. This data never leaves your device."
            )

            PolicySection(
                title = "Subscriptions & Billing",
                body = "Premium subscriptions are processed entirely by Google Play. " +
                    "CoreGuard does not receive or store your payment details. " +
                    "Purchase receipts are verified through Google's secure infrastructure."
            )

            PolicySection(
                title = "Children's Privacy",
                body = "CoreGuard is not directed at children under 13. " +
                    "We do not knowingly collect data from children."
            )

            PolicySection(
                title = "Changes to This Policy",
                body = "We may update this policy as CoreGuard evolves. " +
                    "Significant changes will be communicated through an in-app notice. " +
                    "The effective date at the top of this page reflects the most recent revision."
            )

            PolicySection(
                title = "Contact",
                body = "Questions or concerns? Reach us through the CoreGuard GitHub repository or the " +
                    "support link on our Play Store listing."
            )

            Spacer(Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    text = "CoreGuard is an independent project. Privacy signatures are sourced from the " +
                        "Amnesty International Security Lab / mvt-project. CoreGuard is not affiliated with " +
                        "Amnesty International.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = ElectricTeal,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium
    )
}
