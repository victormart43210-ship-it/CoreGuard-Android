package com.coldboar.coreguard.swarm

/**
 * Contract for a single-purpose swarm monitoring agent.
 *
 * Each agent runs a focused, lightweight monitoring loop and emits [SwarmSignal]s
 * to the [SwarmCoordinator] when it detects an anomaly.  Agents must also respond
 * to coordinator directives delivered via [onCoordinatorDirective].
 *
 * On-device agents are **not** LLM workers. They perform background / offline
 * analysis and signal handoff only. Microsecond-path RASP remains in native
 * C++ — see `docs/SWARM_ARCHITECTURE.md`.
 *
 * Design principles:
 *  - Single-purpose: one agent, one concern.
 *  - Injectable signals: all raw inputs are injected via constructor lambdas so
 *    agents are fully unit-testable on the JVM without an Android device.
 *  - Stateless between polls: agents must not accumulate unbounded state.
 *  - No cloud model calls on the monitoring loop.
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
     * @param directive  A [SwarmSignal] emitted by another agent that triggered the handoff.
     */
    fun onCoordinatorDirective(directive: SwarmSignal)

    /**
     * Returns the most recent signal this agent has emitted, or `null` if the
     * agent has not yet produced any output since [start] was called.
     */
    fun getLatestSignal(): SwarmSignal?
}
