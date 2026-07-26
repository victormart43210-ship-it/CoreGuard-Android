package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.swarm.SwarmAlertCounterStore
import com.coldboar.coreguard.swarm.SwarmModule
import com.coldboar.coreguard.swarm.SwarmSeverity
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold

/**
 * Presentation-only Swarm alert **Counter**.
 *
 * Intentionally Redux-separated from business logic:
 * - Reads state via [SwarmModule.alertCounter] subscription
 * - Mutates only by `dispatch(Action)` (Increment / Reset)
 * - Contains **no** agent registration, IOC matching, or native RASP calls
 *
 * This is the Android analogue of “use Redux to separate UI from the Counter
 * component” — without pulling a JS Redux runtime into the APK.
 */
@Composable
fun SwarmAlertCounter(
    modifier: Modifier = Modifier,
    store: SwarmAlertCounterStore = SwarmModule.alertCounter
) {
    // Local Compose mirror of the Redux store — never the source of truth.
    var state by remember {
        mutableStateOf(store.getState())
    }

    DisposableEffect(store) {
        val unsubscribe = store.subscribe { next -> state = next }
        onDispose { unsubscribe() }
    }

    val countColor = when {
        state.criticalCount > 0 -> HighRed
        state.count > 0 -> RestrainedGold
        else -> ElectricTeal
    }

    CoreGuardCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(4.dp)) {
            Text(
                text = "Swarm alert Counter",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricTeal,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Redux-style store · UI dispatches only · agents feed via SwarmModule",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.count.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = countColor,
                modifier = Modifier.semantics {
                    contentDescription = "Swarm alert count ${state.count}"
                }
            )
            Text(
                text = buildString {
                    append("critical=")
                    append(state.criticalCount)
                    append(" · agents=")
                    append(SwarmModule.agentCount())
                    state.lastSeverity?.let {
                        append(" · last=")
                        append(it.name)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MutedText
            )
            state.lastDetails?.let { details ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.lastSeverity == SwarmSeverity.CRITICAL) HighRed else MutedText
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Demo increment — proves UI never touches the integer directly.
                OutlinedButton(
                    onClick = {
                        store.dispatch(SwarmAlertCounterStore.Action.Increment)
                    }
                ) {
                    Text("Dispatch +1")
                }
                OutlinedButton(
                    onClick = {
                        store.dispatch(SwarmAlertCounterStore.Action.Reset)
                    }
                ) {
                    Text("Reset")
                }
            }
        }
    }
}
