package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.quilla.QuillaInsight
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold

@Composable
fun QuillaInsightCard(
    card: QuillaInsight.Card,
    onAction: (QuillaInsight.Action) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ElectricTeal.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = card.eyebrow.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = RestrainedGold,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                color = ElectricTeal,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = card.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )
            if (card.primaryCta != null || card.secondaryCta != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    card.primaryCta?.let { label ->
                        Button(
                            onClick = { onAction(card.primaryAction) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal)
                        ) {
                            Text(label, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    card.secondaryCta?.let { label ->
                        val action = card.secondaryAction ?: return@let
                        OutlinedButton(
                            onClick = { onAction(action) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(label, color = ElectricTeal)
                        }
                    }
                }
            }
        }
    }
}
