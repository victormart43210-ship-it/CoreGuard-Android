package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen

internal enum class PreviewFindingSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

internal enum class PreviewConfidence {
    CONFIRMED,
    STRONG_EVIDENCE,
    SUSPICIOUS,
    INFORMATIONAL,
    UNKNOWN
}

internal data class PreviewEvidenceFinding(
    val title: String,
    val summary: String,
    val severity: PreviewFindingSeverity,
    val confidence: PreviewConfidence,
    val recommendation: String
)

internal data class PreviewGuardianPulseState(
    val label: String,
    val guidance: String
)

internal object DebugEvidencePreviewSamples {
    val guardianPulseStates = listOf(
        PreviewGuardianPulseState("Protected", "Core checks are healthy. Keep periodic scans enabled."),
        PreviewGuardianPulseState("Observing", "No confirmed threats, but continue monitoring."),
        PreviewGuardianPulseState("Attention Needed", "Some checks need review before sensitive activity."),
        PreviewGuardianPulseState("Elevated Concern", "Multiple signals correlate and need immediate action."),
        PreviewGuardianPulseState("Critical Evidence", "High-confidence evidence warrants urgent containment steps.")
    )

    val findings = listOf(
        PreviewEvidenceFinding(
            title = "Overlay permission active on unknown app",
            summary = "App can draw over other apps, increasing phishing surface.",
            severity = PreviewFindingSeverity.HIGH,
            confidence = PreviewConfidence.STRONG_EVIDENCE,
            recommendation = "Disable overlay permission for unfamiliar apps."
        ),
        PreviewEvidenceFinding(
            title = "Accessibility service granted to untrusted package",
            summary = "Service can observe UI and input events across apps.",
            severity = PreviewFindingSeverity.CRITICAL,
            confidence = PreviewConfidence.CONFIRMED,
            recommendation = "Revoke accessibility access and uninstall if unrecognized."
        ),
        PreviewEvidenceFinding(
            title = "Unusual outbound destination pattern",
            summary = "Recent network metadata deviates from normal baseline behavior.",
            severity = PreviewFindingSeverity.MEDIUM,
            confidence = PreviewConfidence.SUSPICIOUS,
            recommendation = "Review recent installs and monitor repeat destination hits."
        ),
        PreviewEvidenceFinding(
            title = "Security patch level older than recommendation",
            summary = "Device patch level appears behind current best-practice window.",
            severity = PreviewFindingSeverity.LOW,
            confidence = PreviewConfidence.INFORMATIONAL,
            recommendation = "Apply the latest available Android security update."
        ),
        PreviewEvidenceFinding(
            title = "Installer source not yet classified",
            summary = "Installer telemetry exists but lacks a reliable trust profile.",
            severity = PreviewFindingSeverity.MEDIUM,
            confidence = PreviewConfidence.UNKNOWN,
            recommendation = "Treat as unverified source until provenance is confirmed."
        )
    )
}

@Composable
fun DebugEvidencePreviewPanel(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Debug Preview Lab",
            style = MaterialTheme.typography.titleMedium,
            color = ElectricTeal,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Debug-only sample surface for Quilla and Guardian evidence presentation.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText
        )

        CoreGuardCard {
            Text(
                text = "Guardian Pulse preview",
                style = MaterialTheme.typography.titleSmall,
                color = ElectricTeal
            )
            Spacer(modifier = Modifier.height(8.dp))
            DebugEvidencePreviewSamples.guardianPulseStates.forEach { state ->
                PulseRow(state = state)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        CoreGuardCard {
            Text(
                text = "Representative evidence findings",
                style = MaterialTheme.typography.titleSmall,
                color = ElectricTeal
            )
            Spacer(modifier = Modifier.height(8.dp))
            DebugEvidencePreviewSamples.findings.forEachIndexed { index, finding ->
                FindingCard(finding)
                if (index != DebugEvidencePreviewSamples.findings.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        CoreGuardCard {
            Text(
                text = "Sample UI states",
                style = MaterialTheme.typography.titleSmall,
                color = ElectricTeal
            )
            Spacer(modifier = Modifier.height(8.dp))
            UiStateCard("Loading", "Gathering telemetry and preparing evidence timeline…", ElectricTeal)
            Spacer(modifier = Modifier.height(8.dp))
            UiStateCard("Empty", "No findings yet. Run a scan to generate evidence cards.", MutedText)
            Spacer(modifier = Modifier.height(8.dp))
            UiStateCard("Error", "Evidence preview failed to load sample data.", AttentionAmber)
        }
    }
}

@Composable
private fun PulseRow(state: PreviewGuardianPulseState) {
    val accent = pulseColor(state.label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.label,
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = state.guidance,
                color = MutedText,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FindingCard(finding: PreviewEvidenceFinding) {
    val severityColor = severityColor(finding.severity)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, severityColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = finding.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = finding.severity.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = severityColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Confidence: ${finding.confidence.name.replace('_', ' ')}",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText
            )
            Text(
                text = finding.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
            Text(
                text = "Next: ${finding.recommendation}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun UiStateCard(title: String, message: String, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }
    }
}

private fun severityColor(severity: PreviewFindingSeverity): Color = when (severity) {
    PreviewFindingSeverity.LOW -> RestrainedGold
    PreviewFindingSeverity.MEDIUM -> AttentionAmber
    PreviewFindingSeverity.HIGH -> AttentionAmber
    PreviewFindingSeverity.CRITICAL -> HighRed
}

private fun pulseColor(label: String): Color = when (label) {
    "Protected" -> SafeGreen
    "Observing" -> ElectricTeal
    "Attention Needed" -> AttentionAmber
    "Elevated Concern" -> AttentionAmber
    "Critical Evidence" -> HighRed
    else -> MutedText
}
