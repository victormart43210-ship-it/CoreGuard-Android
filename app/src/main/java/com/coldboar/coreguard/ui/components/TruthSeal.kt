package com.coldboar.coreguard.ui.components

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
 */
@Composable
fun TruthSeal(
    evidenceClass: EvidenceClass,
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
        )
    }
}

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
}
