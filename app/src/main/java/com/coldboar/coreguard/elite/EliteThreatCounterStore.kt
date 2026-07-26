package com.coldboar.coreguard.elite

/**
 * Redux-style unidirectional store for the Elite **threat Counter**.
 *
 * This is the Kotlin/Android analogue of separating Counter UI from business
 * state with Redux (without shipping a JS Redux runtime in the APK):
 *
 * ```
 *   Engines / Module ──dispatch(Action)──► Store ──reduce──► State ──notify──► UI
 *   UI ──dispatch(Reset / demo)──► Store   (never mutates ints directly)
 * ```
 *
 * ## Redux rules enforced here
 *
 * 1. **Single source of truth** — [EliteThreatCounterState] is the only Counter
 *    data the Elite Home dashboard should render for DTS / Scam amber chips.
 * 2. **State is read-only to UI** — Compose never writes `dtsScore = …`; it
 *    dispatches or lets [EliteModule] feed evaluations.
 * 3. **Pure reducer** — [reduce] has no I/O, no Android Context, no journal
 *    writes. Side effects stay in engines / [EliteModule].
 *
 * Thread safety: [dispatch] / [getState] / [subscribe] are synchronized so the
 * BAE / notification listener threads and the main thread can share one store.
 *
 * @see EliteModule module-pattern façade that owns this store
 * @see com.coldboar.coreguard.swarm.SwarmAlertCounterStore sibling Counter for swarm
 */
class EliteThreatCounterStore(
    initial: EliteThreatCounterState = EliteThreatCounterState()
) {

    /**
     * Immutable Elite Counter snapshot.
     *
     * @property dtsScore Dynamic Threat Score 0–100 (correlator output).
     * @property dtsBand Risk band derived from [dtsScore].
     * @property dtsSummary Short human line for hub subtitle / journal context.
     * @property scamAmberCount How many Scam Guard findings scored ≥ amber.
     * @property lastScamHost Host of the most recent amber-worthy finding.
     * @property lastScamScore Score of that finding (0 if none).
     */
    data class EliteThreatCounterState(
        val dtsScore: Int = 0,
        val dtsBand: DynamicThreatEngine.Band = DynamicThreatEngine.Band.CLEAR,
        val dtsSummary: String? = null,
        val scamAmberCount: Int = 0,
        val lastScamHost: String? = null,
        val lastScamScore: Int = 0
    )

    /**
     * Actions are the only legal mutations. Keep payloads free of Views / Context
     * so reducers stay JVM-unit-testable.
     */
    sealed class Action {
        /** Replace DTS fields from a fresh [DynamicThreatEngine] evaluation. */
        data class ThreatScoreUpdated(
            val score: Int,
            val band: DynamicThreatEngine.Band,
            val summary: String
        ) : Action()

        /** Increment amber Counter when Scam Guard publishes a high-score finding. */
        data class ScamFindingObserved(
            val host: String,
            val score: Int
        ) : Action()

        /** Clear Counter fields (does not wipe the Forensic Journal file). */
        data object Reset : Action()
    }

    private val lock = Any()
    private var state: EliteThreatCounterState = initial
    private val listeners = mutableListOf<(EliteThreatCounterState) -> Unit>()

    /** Thread-safe snapshot for one-shot reads (e.g. before subscribe paints). */
    fun getState(): EliteThreatCounterState = synchronized(lock) { state }

    /**
     * Subscribe to Counter changes. Invokes [listener] immediately with the
     * current state, then on every successful [dispatch].
     *
     * @return unsubscribe handle — Compose should call this from `onDispose`.
     */
    fun subscribe(listener: (EliteThreatCounterState) -> Unit): () -> Unit {
        synchronized(lock) {
            listeners.add(listener)
            listener(state)
        }
        return {
            synchronized(lock) { listeners.remove(listener) }
        }
    }

    /**
     * Redux dispatch: reduce → commit → notify.
     * UI and engines must never call [reduce] themselves.
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
         * Pure reducer — the Redux contract under test in
         * [com.coldboar.coreguard.elite.EliteThreatCounterStoreTest].
         */
        fun reduce(
            state: EliteThreatCounterState,
            action: Action
        ): EliteThreatCounterState =
            when (action) {
                is Action.ThreatScoreUpdated -> state.copy(
                    dtsScore = action.score.coerceIn(0, 100),
                    dtsBand = action.band,
                    dtsSummary = action.summary
                )
                is Action.ScamFindingObserved -> {
                    // Only amber+ findings bump the Counter (matches dashboard pill).
                    if (action.score < 50) {
                        state
                    } else {
                        state.copy(
                            scamAmberCount = state.scamAmberCount + 1,
                            lastScamHost = action.host,
                            lastScamScore = action.score
                        )
                    }
                }
                Action.Reset -> EliteThreatCounterState()
            }
    }
}
