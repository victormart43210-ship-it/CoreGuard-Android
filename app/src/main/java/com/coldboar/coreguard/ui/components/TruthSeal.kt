package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.truth.EvidenceClass

/**
 * Material 3 badge that communicates the evidence class of a finding.
 *
 * Each of the five states is represented by a unique **icon + text label**
 * (never color-only) so the UI is accessible to users with color vision
 * deficiencies.
 *
 * Minimum touch target: 48 dp × 48 dp (WCAG 2.5.5 / Material 3 guidance).
 * Content descriptions are set for TalkBack.
 *
 * @param evidenceClass The [EvidenceClass] to render.
 * @param modifier      Optional outer modifier.
 * @param compact       When true, shows only the icon (for tight rows);
 *                      the text label is still included in the accessibility
 *                      description.
 */
@Composable
fun TruthSeal(
    evidenceClass: EvidenceClass,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val config = evidenceClass.sealConfig()
    val accessibilityLabel = "Evidence class: ${config.label}. ${config.description}"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 4.dp)
            .semantics { contentDescription = accessibilityLabel }
    ) {
        Icon(
            imageVector = config.icon,
            contentDescription = null, // Provided at Row level
            tint = config.tint,
            modifier = Modifier.size(20.dp)
        )
        if (!compact) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = config.label,
                style = MaterialTheme.typography.labelSmall,
                color = config.tint
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Internal configuration
// ---------------------------------------------------------------------------

private data class SealConfig(
    val icon: ImageVector,
    val label: String,
    val description: String,
    val tint: Color
)

@Composable
private fun EvidenceClass.sealConfig(): SealConfig = when (this) {
    EvidenceClass.OBSERVED -> SealConfig(
        icon = Icons.Filled.CheckCircle,
        label = "Observed",
        description = "Directly observed via OS API or verifiable attestation.",
        tint = MaterialTheme.colorScheme.primary
    )
    EvidenceClass.INFERRED -> SealConfig(
        icon = Icons.Filled.Psychology,
        label = "Inferred",
        description = "Derived from heuristics or behavioural patterns, not directly seen.",
        tint = MaterialTheme.colorScheme.tertiary
    )
    EvidenceClass.SIMULATED -> SealConfig(
        icon = Icons.Filled.Science,
        label = "Simulated",
        description = "Produced by a local simulation, lab fixture, or test dataset.",
        tint = MaterialTheme.colorScheme.secondary
    )
    EvidenceClass.UNAVAILABLE -> SealConfig(
        icon = Icons.Filled.VisibilityOff,
        label = "Unavailable",
        description = "Data was requested but the OS returned no value or access was denied.",
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )
    EvidenceClass.USER_REPORTED -> SealConfig(
        icon = Icons.Filled.HelpOutline,
        label = "User reported",
        description = "Explicitly supplied by the user — not verified by an on-device sensor.",
        tint = MaterialTheme.colorScheme.secondary
    )
}
