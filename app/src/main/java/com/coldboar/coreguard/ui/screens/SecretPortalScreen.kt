package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.lore.ObservatoryCodex
import com.coldboar.coreguard.ui.theme.BackgroundDeepBlack
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SurfacePewter

/**
 * Hidden overlay revealed by the Shift+Alt+S key combination.
 * Mirrors the web "secretPortal" toggle pattern in native Android/Compose.
 *
 * Houses the Observatory Codex — original lore fragments inspired by
 * sky-watcher / recovered-archive themes, framed as security metaphors.
 */
@Composable
fun SecretPortalScreen(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeepBlack.copy(alpha = 0.97f))
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close secret portal",
                tint = MutedText
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "⬡",
                style = MaterialTheme.typography.displayMedium,
                color = ElectricTeal
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "The Vault",
                style = MaterialTheme.typography.headlineLarge,
                color = ElectricTeal,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Observatory Codex",
                style = MaterialTheme.typography.titleMedium,
                color = RestrainedGold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Recovered sky-watcher discipline for evidence-first defense. " +
                    "Fragments remix ancient-observatory themes — calendars, relays, " +
                    "hidden archives — into modern monitoring habits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            ObservatoryCodex.fragments.forEach { fragment ->
                CodexFragmentCard(fragment)
                Spacer(modifier = Modifier.height(10.dp))
            }

            Text(
                text = ObservatoryCodex.DISCLAIMER,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF3A5260),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Press Shift + Alt + S to seal the vault.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF3A5260),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CodexFragmentCard(fragment: ObservatoryCodex.Fragment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfacePewter),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = fragment.title,
                style = MaterialTheme.typography.titleMedium,
                color = RestrainedGold,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = fragment.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )
            Text(
                text = "Maps to: ${fragment.securityLens}",
                style = MaterialTheme.typography.bodySmall,
                color = ElectricTeal
            )
        }
    }
}
