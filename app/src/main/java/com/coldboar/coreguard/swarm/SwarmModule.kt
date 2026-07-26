package com.coldboar.coreguard.swarm

import com.coldboar.coreguard.CoreGuardApplication

/**
 * Module-pattern façade for the on-device security swarm.
 *
 * ## Why a façade?
 *
 * UI and Quilla should call **this** object — not [SwarmCoordinator] internals,
 * agent constructors, or the alert Counter store's Action types — so
 * `:feature:swarm` can be extracted later without rewriting screens
 * (see `docs/MODULE_ARCHITECTURE.md`).
 *
 * ## Use case: is swarm recommended?
 *
 * | Use case | Recommended? | Best implementation |
 * |----------|--------------|---------------------|
 * | CI security auditing | **Yes** | Python agents + `security-swarm.yml` |
 * | Server-side TI correlation | **Yes** | Backend multi-agent routines |
 * | Real-time on-device RASP | **No LLM swarm** | Native `tamperguard.cpp` + light Kotlin peers |
 *
 * Full matrix: `docs/SWARM_ARCHITECTURE.md`.
 *
 * ## Redux Counter — UI separation contract
 *
 * [alertCounter] is a Redux-style store. Compose UI
 * ([com.coldboar.coreguard.ui.components.SwarmAlertCounter]) must:
 *
 * - **Subscribe** for rendering (local Compose state is a mirror only)
 * - **Dispatch via this façade** ([incrementAlertCounter], [resetAlertCounter])
 * - **Never** own the integer, call the reducer, or register agents
 *
 * Agent threads feed the Counter through [onAlertRouted] when severity ≥ WARN.
 */
object SwarmModule {

    /**
     * Process-wide Redux-style alert Counter (WARN+ observations).
     *
     * Prefer [incrementAlertCounter] / [resetAlertCounter] from UI so screens
     * do not depend on [SwarmAlertCounterStore.Action] sealed types.
     */
    val alertCounter: SwarmAlertCounterStore = SwarmAlertCounterStore()

    /**
     * Active coordinator from [CoreGuardApplication], or null before Application
     * [android.app.Application.onCreate] finishes.
     */
    fun coordinator(): SwarmCoordinator? =
        CoreGuardApplication.get()?.swarmCoordinator

    /** Snapshot of recent WARN+ alerts (newest first). Empty if swarm not up. */
    fun activeAlerts(): List<SwarmSignal> =
        coordinator()?.getActiveAlerts().orEmpty()

    /** Highest CRITICAL alert still in the ring, if any. */
    fun highestThreat(): SwarmSignal? =
        coordinator()?.getHighestThreat()

    /** Registered peer count (0 before boot registration). */
    fun agentCount(): Int =
        coordinator()?.registeredAgentCount() ?: 0

    /**
     * Register the default Michael-choir peers (memory / network / process).
     * Idempotent only if the caller has not already registered the same agents;
     * [CoreGuardApplication] owns the one-time boot call.
     */
    fun registerDefaultAgents(coordinator: SwarmCoordinator) {
        coordinator.register(MemoryIntegrityAgent())
        coordinator.register(NetworkMonitorAgent())
        coordinator.register(ProcessLineageAgent())
    }

    /**
     * Bridge a routed signal into the Redux Counter.
     * Called from [SwarmCoordinator] when severity ≥ WARN.
     *
     * This is the **agent → store** path. UI never constructs [SwarmSignal]
     * just to bump the Counter — use [incrementAlertCounter] for demos.
     */
    fun onAlertRouted(signal: SwarmSignal) {
        if (signal.severity >= SwarmSeverity.WARN) {
            alertCounter.dispatch(SwarmAlertCounterStore.Action.AlertObserved(signal))
        }
    }

    /**
     * UI-facing dispatch: demo / manual +1 without constructing a [SwarmSignal].
     * Keeps Action types behind the module boundary.
     */
    fun incrementAlertCounter() {
        alertCounter.dispatch(SwarmAlertCounterStore.Action.Increment)
    }

    /**
     * UI / tests: reset Counter without shutting down agents or clearing the
     * coordinator's alert ring buffer.
     */
    fun resetAlertCounter() {
        alertCounter.dispatch(SwarmAlertCounterStore.Action.Reset)
    }
}
