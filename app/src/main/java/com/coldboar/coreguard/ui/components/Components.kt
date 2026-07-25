package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.SafeGreen

/** A consistently-styled card container used across all screens. */
@Composable
fun StatusCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            content()
        }
    }
}

/** A top-level heading with an optional subtitle. */
@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.semantics { heading() }
    )
    if (subtitle != null) {
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Renders a single [SecurityCheckResult] row with an icon, label, and explanation. */
@Composable
fun SecurityCheckRow(result: SecurityCheckResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val icon = when (result.state) {
            SecurityCheckState.PASS -> "✅"
            SecurityCheckState.WARN -> "⚠️"
            SecurityCheckState.FAIL -> "❌"
        }
        Text(
            text = "$icon ${result.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = result.state.toColor(),
            modifier = Modifier.weight(1f)
        )
    }
    Text(
        text = result.explanation,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 28.dp, bottom = 4.dp)
    )
}

/** A compact stat row (label + value) used in performance and about cards. */
@Composable
fun StatRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

/** Converts a [SecurityCheckState] to its representative UI colour. */
fun SecurityCheckState.toColor(): Color = when (this) {
    SecurityCheckState.PASS -> SafeGreen
    SecurityCheckState.WARN -> AttentionAmber
    SecurityCheckState.FAIL -> HighRed
}

/** Spacer shorthand used between cards. */
@Composable
fun CardSpacer() = Spacer(Modifier.height(16.dp))
