package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.R
import com.coldboar.coreguard.ui.components.CoreGuardCard
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.components.SubScreenTopBar
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val hostedPolicyUrl = stringResource(R.string.privacy_policy_url)
    val pagesPolicyUrl = stringResource(R.string.privacy_policy_url_pages)
    ScreenAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SubScreenTopBar(
            title = "Privacy Policy",
            subtitle = "Effective Date: July 2026",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "CoreGuard Privacy Policy",
            style = MaterialTheme.typography.headlineSmall,
            color = ElectricTeal,
            modifier = Modifier.semantics { heading() }
        )

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
            body = "Premium subscriptions are processed entirely by Google Play Billing. " +
                "CoreGuard does not receive or store your payment card details. " +
                "Entitlement is determined on-device from Play purchase state " +
                "(active acknowledged subscription). Server-side receipt verification " +
                "may be added in a future release."
        )

        PolicySection(
            title = "Hosted Privacy Policy",
            body = "Use this URL in the Google Play Console Data safety / Privacy policy fields:\n" +
                "$hostedPolicyUrl\n\n" +
                "When GitHub Pages is enabled for this repo, the same document is also at:\n" +
                pagesPolicyUrl
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

        Spacer(modifier = Modifier.height(24.dp))

        CoreGuardCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "CoreGuard is an independent project. Privacy signatures are sourced from the " +
                    "Amnesty International Security Lab / mvt-project. CoreGuard is not affiliated with " +
                    "Amnesty International.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = ElectricTeal,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium
    )
}
