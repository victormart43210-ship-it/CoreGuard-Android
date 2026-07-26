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
import com.coldboar.coreguard.elite.DynamicThreatEngine
import com.coldboar.coreguard.elite.EliteModule
import com.coldboar.coreguard.elite.EliteThreatCounterStore
import com.coldboar.coreguard.ui.redux.rememberEliteThreatCounterState
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold

/**
 * Presentation-only Elite **threat Counter** composable.
 *
 * ## Redux separation (required)
 *
 * Mirrors the Swarm Counter contract: this file is dumb UI. Business state
 * (DTS score/band, Scam amber count) lives in [EliteThreatCounterStore], owned
 * by [EliteModule]. Subscription goes through
 * [rememberEliteThreatCounterState] so screens do not open DisposableEffect
 * stores inline.
 *
 * | Layer | Responsibility | This file? |
 * |-------|----------------|------------|
 * | [EliteThreatCounterStore] | State + pure reducer + dispatch | No |
 * | [EliteModule] | Module façade; evaluate DTS / reset Counter | Called only |
 * | [rememberEliteThreatCounterState] | Store → Compose State bridge | Used |
 * | This composable | Paint + forward Reset taps | Yes |
 *
 * ## Honesty copy
 *
 * DTS is an on-device correlator (Quilla quantum-*inspired*), not cloud AI.
 * The subtitle states that so demos cannot be misread as an NPU LLM.
 *
 * @param store Injectable for previews / tests; production uses [EliteModule.threatCounter].
 * @param onReset UI → module reset (override when injecting a private store).
 */
@Composable
fun EliteThreatCounter(
    modifier: Modifier = Modifier,
    store: EliteThreatCounterStore = EliteModule.threatCounter,
    onReset: () -> Unit = { EliteModule.resetThreatCounter() }
) {
    // -------------------------------------------------------------------------
    // Redux mirror: State is read-only. Never assign dtsScore locally.
    // -------------------------------------------------------------------------
    val state by rememberEliteThreatCounterState(store)

    // Color encodes band pressure from store fields only (no extra thresholds).
    val scoreColor = when (state.dtsBand) {
        DynamicThreatEngine.Band.CRITICAL -> HighRed
        DynamicThreatEngine.Band.ELEVATED -> RestrainedGold
        DynamicThreatEngine.Band.WATCH -> RestrainedGold
        DynamicThreatEngine.Band.CLEAR -> ElectricTeal
    }

    CoreGuardCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(4.dp)) {
            Text(
                text = "Elite threat Counter",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricTeal,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Redux store · UI resets via EliteModule · engines feed DTS / Scam amber",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Big DTS numeral — rendered from store state, never a local counter.
            Text(
                text = state.dtsScore.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = scoreColor,
                modifier = Modifier.semantics {
                    contentDescription =
                        "Dynamic Threat Score ${state.dtsScore}, band ${state.dtsBand.name}"
                }
            )
            Text(
                text = buildString {
                    append("band=")
                    append(state.dtsBand.name)
                    append(" · scamAmber=")
                    append(state.scamAmberCount)
                    state.lastScamHost?.let { host ->
                        append(" · last=")
                        append(host)
                        append(" (")
                        append(state.lastScamScore)
                        append(")")
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MutedText
            )
            state.dtsSummary?.let { summary ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "DTS is an on-device correlator — not cloud AI.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // -------------------------------------------------------------
                // Dispatch only through EliteModule — UI does not import Action
                // sealed classes, proving the Counter is Redux-separated.
                // -------------------------------------------------------------
                OutlinedButton(onClick = onReset) {
                    Text("Reset Counter")
                }
            }
        }
    }
}
