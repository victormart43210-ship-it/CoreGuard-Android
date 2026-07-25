package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.theme.BackgroundDeepBlack
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText

/**
 * Hidden overlay revealed by the Shift+Alt+S key combination.
 * Mirrors the web "secretPortal" toggle pattern in native Android/Compose.
 *
 * Displayed as a full-screen layer above all other content; dismissed via
 * the close button or by triggering the key combination a second time.
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
                contentDescription = "Close vault",
                tint = MutedText
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⬡",
                style = MaterialTheme.typography.displayLarge,
                color = ElectricTeal
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "The Vault",
                style = MaterialTheme.typography.headlineLarge,
                color = ElectricTeal
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "The ritual is complete. The vault has shifted.",
                style = MaterialTheme.typography.bodyLarge,
                color = MutedText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Press Shift + Alt + S to seal the vault.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF3A5260),
                textAlign = TextAlign.Center
            )
        }
    }
}
