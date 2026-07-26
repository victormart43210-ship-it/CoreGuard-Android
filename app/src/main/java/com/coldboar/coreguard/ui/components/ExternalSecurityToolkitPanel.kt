package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.toolkit.ExternalSecurityToolkit
import com.coldboar.coreguard.toolkit.ExternalToolkitIntents
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen

/**
 * Browser-helper toolkit panel (rescued from conflicting PR #68).
 * Opens curated external security sites via [ExternalToolkitIntents] —
 * no third-party API keys, no silent network from CoreGuard itself.
 */
@Composable
fun ExternalSecurityToolkitPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = ExternalSecurityToolkit.SAFETY_BANNER,
                style = MaterialTheme.typography.bodySmall,
                color = AttentionAmber
            )
            ExternalSecurityToolkit.tools.forEach { tool ->
                ExternalToolCard(
                    tool = tool,
                    onOpen = { ExternalToolkitIntents.open(context, tool) }
                )
            }
        }
    }
}

@Composable
private fun ExternalToolCard(
    tool: ExternalSecurityToolkit.Tool,
    onOpen: () -> Unit
) {
    val categoryColor = when (tool.category) {
        ExternalSecurityToolkit.Category.MALWARE -> AttentionAmber
        ExternalSecurityToolkit.Category.PRIVACY -> ElectricTeal
        ExternalSecurityToolkit.Category.EVIDENCE -> RestrainedGold
        ExternalSecurityToolkit.Category.NETWORK -> SafeGreen
        ExternalSecurityToolkit.Category.OSINT -> MutedText
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = ElectricTeal,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            Text(
                text = tool.category.name,
                style = MaterialTheme.typography.labelLarge,
                color = categoryColor
            )
        }
        Text(tool.summary, style = MaterialTheme.typography.bodyMedium, color = MutedText)
        Text(
            text = "When: ${tool.whenToUse}",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText
        )
        tool.caution?.let { caution ->
            Text(
                text = "Caution: $caution",
                style = MaterialTheme.typography.bodySmall,
                color = AttentionAmber
            )
        }
        OutlinedButton(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open ${tool.host}", color = ElectricTeal)
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
