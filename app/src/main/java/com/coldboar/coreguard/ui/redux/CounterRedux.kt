package com.coldboar.coreguard.ui.redux

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.coldboar.coreguard.elite.EliteModule
import com.coldboar.coreguard.elite.EliteThreatCounterStore
import com.coldboar.coreguard.elite.EliteThreatCounterStore.EliteThreatCounterState
import com.coldboar.coreguard.swarm.SwarmAlertCounterStore
import com.coldboar.coreguard.swarm.SwarmAlertCounterStore.SwarmAlertCounterState
import com.coldboar.coreguard.swarm.SwarmModule

/**
 * # Counter Redux bridge for Compose
 *
 * Product ask: **use Redux to separate the UI code from the Counter component**.
 *
 * Android ships no React-Redux runtime. CoreGuard uses tiny unidirectional stores
 * ([SwarmAlertCounterStore], [EliteThreatCounterStore]) owned by module façades
 * ([SwarmModule], [EliteModule]). This file is the **only** place Compose should
 * subscribe to those stores.
 *
 * ## Layering (do not collapse these)
 *
 * 1. **Compose UI** (Counter components / Home dashboard)
 *    - paints [State]
 *    - forwards taps via module façades (never sealed `Action` types)
 * 2. **Module façade** ([SwarmModule] / [EliteModule])
 *    - owns the Store instance
 *    - dispatches from engines and demo buttons
 * 3. **Redux store** (pure `reduce` + synchronized notify)
 *    - single source of truth for Counter integers / DTS fields
 *
 * ## What belongs here
 *
 * - `remember*AsState` subscription helpers (mirror store → Compose [State])
 * - Documentation of the Redux contract for UI authors
 *
 * ## What must never live in Counter composables
 *
 * - Agent registration / coordinator wiring
 * - DTS engine evaluation, Scam Guard parsing, journal I/O
 * - Native RASP / Frida probes
 * - Direct `count++` or calling `Store.reduce` from UI
 *
 * Local Compose `mutableStateOf` inside these helpers is a **mirror** for
 * recomposition only — the store remains the single source of truth.
 */

/**
 * Subscribe to the Swarm alert Counter as Compose [State].
 *
 * The returned [State] updates whenever [SwarmAlertCounterStore.dispatch]
 * commits a new snapshot. Callers must not mutate the state object.
 *
 * @param store Injectable for previews/tests; production uses [SwarmModule.alertCounter].
 */
@Composable
fun rememberSwarmAlertCounterState(
    store: SwarmAlertCounterStore = SwarmModule.alertCounter
): State<SwarmAlertCounterState> {
    // Seed from the store so the first frame paints without waiting for notify.
    val state = remember(store) { mutableStateOf(store.getState()) }

    // Unsubscribe when leaving composition — agents may keep dispatching.
    DisposableEffect(store) {
        val unsubscribe = store.subscribe { next -> state.value = next }
        onDispose { unsubscribe() }
    }

    return state
}

/**
 * Subscribe to the Elite threat Counter (DTS + Scam amber) as Compose [State].
 *
 * Engines feed this store only through [EliteModule.evaluateThreatScore] /
 * [EliteModule.inspectScamText]. UI may call [EliteModule.resetThreatCounter]
 * for demos — never [EliteThreatCounterStore.Action] types directly.
 *
 * @param store Injectable for previews/tests; production uses [EliteModule.threatCounter].
 */
@Composable
fun rememberEliteThreatCounterState(
    store: EliteThreatCounterStore = EliteModule.threatCounter
): State<EliteThreatCounterState> {
    val state = remember(store) { mutableStateOf(store.getState()) }

    DisposableEffect(store) {
        val unsubscribe = store.subscribe { next -> state.value = next }
        onDispose { unsubscribe() }
    }

    return state
}
