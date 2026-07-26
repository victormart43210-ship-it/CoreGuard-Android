package com.coldboar.coreguard.swarm

/**
 * Contract for a single-purpose swarm monitoring agent.
 *
 * ## Module pattern
 * Concrete agents (`MemoryIntegrityAgent`, `NetworkMonitorAgent`,
 * `ProcessLineageAgent`) are **internals**. Screens and Quilla should go through
 * [SwarmModule] — not construct agents or talk to [SwarmCoordinator] directly —
 * so `:feature:swarm` can be extracted later (`docs/MODULE_ARCHITECTURE.md`).
 *
 * ## Lifecycle
 * Each agent runs a focused, lightweight monitoring loop and emits [SwarmSignal]s
 * to the [SwarmCoordinator] when it detects an anomaly. Agents must also respond
 * to coordinator directives delivered via [onCoordinatorDirective] (peer handoff).
 *
 * ## On-device role (not an LLM swarm)
 * On-device agents are **not** LLM workers. They perform background / offline
 * analysis and signal handoff only. Microsecond-path RASP remains in native
 * C++ — see `docs/SWARM_ARCHITECTURE.md` for the use-case matrix
 * (CI yes · server TI yes · real-time RASP = native, not LLM swarm).
 *
 * ## Design principles
 * - Single-purpose: one agent, one concern.
 * - Injectable signals: raw inputs via constructor lambdas → JVM unit-testable.
 * - Stateless between polls: no unbounded accumulation.
 * - No cloud model calls on the monitoring loop.
 * - Poll in seconds; never busy-loop the UI thread.
 */
interface SwarmAgent {

    /** Stable, unique identifier for this agent (e.g. "network-monitor"). */
    val agentId: String

    /** Human-readable display name shown in diagnostics. */
    val name: String

    /**
     * Starts the agent's monitoring loop. The [coordinator] reference is used
     * to emit signals and receive routing context.
     *
     * Implementations must be non-blocking: long-running monitoring work
     * must be dispatched to a background executor or coroutine.
     */
    fun start(coordinator: SwarmCoordinator)

    /**
     * Stops the agent's monitoring loop and releases any resources it holds.
     * Must be idempotent.
     */
    fun stop()

    /**
     * Receives a directive from the [SwarmCoordinator] in response to a high-severity
     * signal originating from another agent. The agent may choose to adjust its
     * monitoring sensitivity, isolate connections, or escalate checks.
     *
     * @param directive A [SwarmSignal] emitted by another agent that triggered the handoff.
     */
    fun onCoordinatorDirective(directive: SwarmSignal)

    /**
     * Optional latest signal for diagnostics / UI mirrors. Default null.
     */
    fun getLatestSignal(): SwarmSignal? = null
}
