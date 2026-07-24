package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.theme.MutedText

@Composable
fun TimelineScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Timeline",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Integrity timeline for scans, observations, and IOC updates.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(40.dp))

        Text(
            text = "No scan history yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MutedText
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Run a Nemesis scan to begin recording your device integrity timeline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText
        )
    }
}
