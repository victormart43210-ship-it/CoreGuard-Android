package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Science
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
 * A Material 3 evidence-class badge ("truth seal") that communicates how a
 * finding's evidence was produced.
 *
 * Five distinct states are represented using BOTH an icon AND a text label,
 * never by color alone, so the UI is accessible to users with color-vision
 * deficiency.
 *
 * Minimum touch target is 48 dp (applied via [Modifier.defaultMinSize]).
 * A merged semantics content description is provided for TalkBack.
 *
 * Usage:
 * ```
 * TruthSeal(evidenceClass = finding.evidenceClass)
 * ```
 */
@Composable
fun TruthSeal(
    evidenceClass: EvidenceClass,
    modifier: Modifier = Modifier
) {
    val (icon, label, tint, description) = evidenceClassMeta(evidenceClass)
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Evidence class: $description"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

private data class EvidenceClassMeta(
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val description: String
)

@Composable
private fun evidenceClassMeta(evidenceClass: EvidenceClass): EvidenceClassMeta = when (evidenceClass) {
    EvidenceClass.OBSERVED -> EvidenceClassMeta(
        icon = Icons.Filled.CheckCircle,
        label = "Observed",
        tint = MaterialTheme.colorScheme.primary,
        description = "Observed — directly measured on this device"
    )
    EvidenceClass.INFERRED -> EvidenceClassMeta(
        icon = Icons.Filled.Psychology,
        label = "Inferred",
        tint = MaterialTheme.colorScheme.secondary,
        description = "Inferred — derived from indirect signals"
    )
    EvidenceClass.SIMULATED -> EvidenceClassMeta(
        icon = Icons.Filled.Science,
        label = "Simulated",
        tint = MaterialTheme.colorScheme.tertiary,
        description = "Simulated — produced from a synthetic scenario, not a live device"
    )
    EvidenceClass.UNAVAILABLE -> EvidenceClassMeta(
        icon = Icons.Filled.Block,
        label = "Unavailable",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        description = "Unavailable — the required data cannot be accessed on this device"
    )
    EvidenceClass.USER_REPORTED -> EvidenceClassMeta(
        icon = Icons.Filled.HelpOutline,
        label = "User-reported",
        tint = MaterialTheme.colorScheme.outline,
        description = "User-reported — stated by the user; not independently verified"
    )
}
