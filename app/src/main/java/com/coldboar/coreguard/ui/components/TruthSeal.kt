package com.coldboar.coreguard.ui.components

<<<<<<< HEAD
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.guardian.Confidence
import com.coldboar.coreguard.guardian.EvidenceClass
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold

/**
 * Truth Seal — visible origin and certainty for a result (Blueprint §7).
 * Never relies on color alone; includes text + shape + TalkBack label.
=======
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
>>>>>>> origin/main
 */
@Composable
fun TruthSeal(
    evidenceClass: EvidenceClass,
<<<<<<< HEAD
    confidence: Confidence? = null,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    val label = buildString {
        append(evidenceClass.userLabel)
        if (confidence != null) {
            append(" · ")
            append(confidence.userLabel)
        }
    }
    val a11y = "Truth seal: $label. ${helpText(evidenceClass)}"

    Row(
        modifier = modifier
            .semantics { contentDescription = a11y }
            .clickable { showHelp = true }
            .border(
                width = 1.dp,
                color = when (evidenceClass) {
                    EvidenceClass.OBSERVED -> ElectricTeal
                    EvidenceClass.INFERRED -> RestrainedGold
                    EvidenceClass.SIMULATED -> RestrainedGold.copy(alpha = 0.7f)
                    EvidenceClass.UNAVAILABLE -> MutedText
                    EvidenceClass.USER_REPORTED -> ElectricTeal.copy(alpha = 0.7f)
                },
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Shape distinction beyond color (solid vs dotted bar).
        Canvas(modifier = Modifier.size(width = 10.dp, height = 10.dp)) {
            val stroke = when (evidenceClass) {
                EvidenceClass.INFERRED -> Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
                else -> Stroke(width = 2.dp.toPx())
            }
            drawCircle(
                brush = SolidColor(
                    when (evidenceClass) {
                        EvidenceClass.OBSERVED -> ElectricTeal
                        EvidenceClass.UNAVAILABLE -> MutedText
                        else -> RestrainedGold
                    }
                ),
                style = stroke
            )
        }
        Icon(
            imageVector = when (evidenceClass) {
                EvidenceClass.OBSERVED -> Icons.Outlined.Visibility
                EvidenceClass.INFERRED -> Icons.Outlined.Info
                EvidenceClass.SIMULATED -> Icons.Outlined.Science
                EvidenceClass.UNAVAILABLE -> Icons.Outlined.VisibilityOff
                EvidenceClass.USER_REPORTED -> Icons.Outlined.Person
            },
            contentDescription = null,
            tint = MutedText,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MutedText
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Truth Seal") },
            text = {
                Text(helpText(evidenceClass) + (confidence?.let { "\n\n${it.userLabel}." } ?: ""))
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("OK") }
            }
=======
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
>>>>>>> origin/main
        )
    }
}

<<<<<<< HEAD
private fun helpText(evidenceClass: EvidenceClass): String = when (evidenceClass) {
    EvidenceClass.OBSERVED ->
        "Observed: read directly from an Android API or OS source."
    EvidenceClass.INFERRED ->
        "Inferred: calculated from patterns. Not a confirmed intrusion."
    EvidenceClass.SIMULATED ->
        "Simulation: educational or laboratory data — not a live detection."
    EvidenceClass.UNAVAILABLE ->
        "Unavailable: Android does not expose this information to CoreGuard."
    EvidenceClass.USER_REPORTED ->
        "User reported: entered or confirmed by you."
=======
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
>>>>>>> origin/main
}
