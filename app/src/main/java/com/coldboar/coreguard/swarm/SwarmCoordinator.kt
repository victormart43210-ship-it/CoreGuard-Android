package com.coldboar.coreguard.swarm

/**
 * Orchestrates the on-device security agent swarm.
 *
 * This is a **lightweight, non-LLM** coordinator for background analysis and
 * peer handoff. Real-time RASP probes and execution-blocking checks stay in
 * native C++ ([com.coldboar.coreguard.NativeTamperGuard] / `tamperguard.cpp`).
 * See `docs/SWARM_ARCHITECTURE.md`.
 *
 * Responsibilities:
 *  1. **Registration** — accepts any [SwarmAgent] implementation.
 *  2. **Broadcast routing** — when an agent emits a [SwarmSignal], the coordinator
 *     forwards it to all *other* registered agents as a [SwarmAgent.onCoordinatorDirective]
 *     call if the signal's severity is [SwarmSeverity.WARN] or higher.
 *  3. **Handoff logic** — [SwarmSeverity.CRITICAL] signals trigger immediate
 *     directive propagation to every peer agent, enabling the swarm to respond
 *     to threats collaboratively (e.g. a hook detected by [MemoryIntegrityAgent]
 *     causes [NetworkMonitorAgent] to enter isolation mode).
 *  4. **Alert log** — retains the most recent [maxAlerts] signals for UI inspection.
 *
 * Thread safety: [broadcast] and [register] are synchronized so the coordinator
 * can be safely driven from multiple background agent threads simultaneously.
 *
 * @param maxAlerts Maximum number of recent alerts retained in [getActiveAlerts].
 */
class SwarmCoordinator(private val maxAlerts: Int = 50) {

    private val lock = Any()
    private val agents = mutableListOf<SwarmAgent>()
    private val alertLog = ArrayDeque<SwarmSignal>(maxAlerts + 1)

    // -------------------------------------------------------------------------
    // Agent lifecycle
    // -------------------------------------------------------------------------

    /**
     * Registers an agent and starts it. Once registered the agent will receive
     * coordinator directives and its signals will be routed to all peers.
     */
    fun register(agent: SwarmAgent) {
        synchronized(lock) { agents.add(agent) }
        agent.start(this)
    }

    /**
     * Stops all registered agents and clears internal state. Safe to call
     * multiple times.
     */
    fun shutdown() {
        val snapshot = synchronized(lock) {
            val copy = agents.toList()
            agents.clear()
            alertLog.clear()
            copy
        }
        snapshot.forEach { it.stop() }
    }

    // -------------------------------------------------------------------------
    // Signal routing
    // -------------------------------------------------------------------------

    /**
     * Called by an agent to broadcast a [SwarmSignal] to the coordinator.
     *
     * - The signal is always appended to the alert log if severity ≥ WARN.
     * - For [SwarmSeverity.CRITICAL] signals, a directive is immediately
     *   forwarded to every *other* registered agent so they can adjust their
     *   monitoring behaviour (the swarm "handoff" mechanic).
     *
     * @param signal  The event emitted by the sending agent.
     * @param sender  The agent that produced the signal (excluded from broadcasts).
     */
    fun broadcast(signal: SwarmSignal, sender: SwarmAgent) {
        if (signal.severity >= SwarmSeverity.WARN) {
            appendAlert(signal)
        }

        if (signal.severity == SwarmSeverity.CRITICAL) {
            val peers = synchronized(lock) { agents.filter { it !== sender } }
            peers.forEach { peer ->
                runCatching { peer.onCoordinatorDirective(signal) }
                // Swallow exceptions so one misbehaving agent cannot stall routing.
            }
        }
    }

    // -------------------------------------------------------------------------
    // Alert inspection
    // -------------------------------------------------------------------------

    /**
     * Returns a snapshot of the most recent alerts (severity ≥ WARN), newest first.
     */
    fun getActiveAlerts(): List<SwarmSignal> =
        synchronized(lock) { alertLog.toList().asReversed() }

    /**
     * Returns the most recent [SwarmSeverity.CRITICAL] signal, or `null` if none
     * has been logged since the coordinator was started.
     */
    fun getHighestThreat(): SwarmSignal? =
        synchronized(lock) {
            alertLog.lastOrNull { it.severity == SwarmSeverity.CRITICAL }
        }

    /**
     * Returns the number of currently registered agents.
     */
    fun registeredAgentCount(): Int = synchronized(lock) { agents.size }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun appendAlert(signal: SwarmSignal) {
        synchronized(lock) {
            alertLog.addLast(signal)
            while (alertLog.size > maxAlerts) alertLog.removeFirst()
        }
    }
}
