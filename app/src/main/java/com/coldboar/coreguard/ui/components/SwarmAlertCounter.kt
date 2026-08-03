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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.swarm.SwarmAlertCounterStore
import com.coldboar.coreguard.swarm.SwarmModule
import com.coldboar.coreguard.swarm.SwarmSeverity
import com.coldboar.coreguard.ui.redux.rememberSwarmAlertCounterState
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold

/**
 * Presentation-only Swarm alert **Counter** composable.
 *
 * ## Redux separation (required)
 *
 * This file is intentionally dumb UI. It implements the product ask
 * “use Redux to separate the UI code from the Counter component”:
 *
 * | Layer | Responsibility | This file? |
 * |-------|----------------|------------|
 * | [SwarmAlertCounterStore] | State + pure reducer + dispatch | No |
 * | [SwarmModule] | Module façade; UI-facing increment/reset | Called only |
 * | [rememberSwarmAlertCounterState] | Store → Compose State bridge | Used |
 * | This composable | Paint + forward button taps | Yes |
 *
 * Local Compose state from [rememberSwarmAlertCounterState] is a **mirror** of
 * the Redux store for recomposition. It is never the source of truth: agents
 * and [SwarmModule] own mutations.
 *
 * ## What must never live here
 *
 * - Agent registration / [com.coldboar.coreguard.swarm.SwarmCoordinator] calls
 * - Native RASP / Frida probes
 * - IOC matching or journal I/O
 * - Direct `count++` or reducer invocation
 * - Importing [SwarmAlertCounterStore.Action] sealed types
 *
 * Android does not ship React-Redux; [SwarmAlertCounterStore] is the idiomatic
 * unidirectional stand-in inside the APK. See also [EliteThreatCounter] for the
 * Elite DTS / Scam amber Counter with the same contract.
 *
 * @param store Injectable for previews / tests; production uses [SwarmModule.alertCounter].
 * @param agentCount Peer count from the module façade (injected so this file does
 *   not reach into coordinator internals). Defaults to [SwarmModule.agentCount].
 * @param onIncrement UI → module dispatch (override when injecting a private store).
 * @param onReset UI → module reset (override when injecting a private store).
 */
@Composable
fun SwarmAlertCounter(
    modifier: Modifier = Modifier,
    store: SwarmAlertCounterStore = SwarmModule.alertCounter,
    agentCount: Int = SwarmModule.agentCount(),
    onIncrement: () -> Unit = { SwarmModule.incrementAlertCounter() },
    onReset: () -> Unit = { SwarmModule.resetAlertCounter() }
) {
    // -------------------------------------------------------------------------
    // Subscription lives in ui.redux — this composable only reads State.
    // That keeps Counter presentation free of DisposableEffect / store wiring.
    // -------------------------------------------------------------------------
    val state by rememberSwarmAlertCounterState(store)

    // Color encodes severity pressure without embedding business thresholds
    // beyond what the store already exposed (criticalCount / count).
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
                text = "Redux store · UI dispatches via SwarmModule · agents feed alerts",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
            Spacer(modifier = Modifier.height(12.dp))

            // The big numeral — rendered from store state, never a local counter.
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
                    // Injected peer count — not read from coordinator here.
                    append(agentCount)
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
                // -------------------------------------------------------------
                // Dispatch only through SwarmModule — UI does not import Action
                // sealed classes, proving the Counter is Redux-separated.
                // -------------------------------------------------------------
                OutlinedButton(onClick = onIncrement) {
                    Text("Dispatch +1")
                }
                OutlinedButton(onClick = onReset) {
                    Text("Reset")
                }
            }
        }
    }
}
