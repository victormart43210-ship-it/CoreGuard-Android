package com.coldboar.coreguard.swarm

/**
 * Redux-style unidirectional store for the swarm **alert Counter**.
 *
 * Android does not ship React-Redux; this is the idiomatic Kotlin equivalent:
 *
 * ```
 *   UI ──dispatch(Action)──► Store ──reduce──► State ──notify──► UI
 * ```
 *
 * Rules (same as Redux):
 * - **Single source of truth** — [state] is the only mutable counter data.
 * - **State is read-only to UI** — screens never mutate fields; they dispatch.
 * - **Changes via pure reducer** — [reduce] is a pure function of (state, action).
 *
 * The Counter composable ([com.coldboar.coreguard.ui.components.SwarmAlertCounter])
 * must stay presentation-only: it subscribes and dispatches; it never owns counts.
 *
 * Thread safety: [dispatch] / [getState] / [subscribe] are synchronized so agent
 * threads and the main/UI thread can share one store.
 *
 * @see SwarmModule for the module-pattern façade that owns this store.
 * @see docs/SWARM_ARCHITECTURE.md for when swarming is recommended.
 */
class SwarmAlertCounterStore(
    initial: SwarmAlertCounterState = SwarmAlertCounterState()
) {

    /**
     * Immutable counter snapshot rendered by the UI.
     *
     * @property count Total WARN+CRITICAL alerts observed since last reset.
     * @property criticalCount Subset that were CRITICAL (handoff-worthy).
     * @property lastDetails Most recent alert details string, or null if idle.
     * @property lastSeverity Severity of [lastDetails], if any.
     */
    data class SwarmAlertCounterState(
        val count: Int = 0,
        val criticalCount: Int = 0,
        val lastDetails: String? = null,
        val lastSeverity: SwarmSeverity? = null
    )

    /**
     * Actions are the only legal way to change [SwarmAlertCounterState].
     * Keep payloads small and serializable-in-spirit (no Context / Views).
     */
    sealed class Action {
        /** Increment from a routed swarm signal (WARN+). */
        data class AlertObserved(val signal: SwarmSignal) : Action()

        /** Manual +1 — useful for UI demos / unit tests without a live agent. */
        data object Increment : Action()

        /** Clear the counter (does not clear [SwarmCoordinator] alert log). */
        data object Reset : Action()
    }

    private val lock = Any()
    private var state: SwarmAlertCounterState = initial
    private val listeners = mutableListOf<(SwarmAlertCounterState) -> Unit>()

    /** Snapshot of current state (copy; safe to read on any thread). */
    fun getState(): SwarmAlertCounterState = synchronized(lock) { state }

    /**
     * Subscribe to state changes. Returns an unsubscribe handle.
     * Immediately receives the current state so UI can paint without racing.
     */
    fun subscribe(listener: (SwarmAlertCounterState) -> Unit): () -> Unit {
        synchronized(lock) {
            listeners.add(listener)
            listener(state)
        }
        return {
            synchronized(lock) { listeners.remove(listener) }
        }
    }

    /**
     * Redux dispatch: reduce → replace state → notify subscribers.
     * Never call [reduce] from UI directly.
     */
    fun dispatch(action: Action) {
        val (next, snapshot) = synchronized(lock) {
            val reduced = reduce(state, action)
            state = reduced
            reduced to listeners.toList()
        }
        snapshot.forEach { listener ->
            runCatching { listener(next) }
        }
    }

    companion object {
        /**
         * Pure reducer — no I/O, no Android APIs, no shared mutation.
         * Easy to unit-test in isolation (the Redux contract).
         */
        fun reduce(state: SwarmAlertCounterState, action: Action): SwarmAlertCounterState =
            when (action) {
                is Action.AlertObserved -> state.copy(
                    count = state.count + 1,
                    criticalCount = state.criticalCount +
                        if (action.signal.severity == SwarmSeverity.CRITICAL) 1 else 0,
                    lastDetails = action.signal.details,
                    lastSeverity = action.signal.severity
                )
                Action.Increment -> state.copy(count = state.count + 1)
                Action.Reset -> SwarmAlertCounterState()
            }
    }
}
