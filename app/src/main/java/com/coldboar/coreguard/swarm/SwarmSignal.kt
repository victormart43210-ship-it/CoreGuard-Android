package com.coldboar.coreguard.swarm

/**
 * Severity level of a [SwarmSignal].
 *
 * - [INFO]     — diagnostic telemetry; no action required.
 * - [WARN]     — elevated risk indicator; other agents should increase vigilance.
 * - [CRITICAL] — high-confidence threat; triggers handoff logic in [SwarmCoordinator].
 */
enum class SwarmSeverity { INFO, WARN, CRITICAL }

/**
 * Signal type emitted by a specialist swarm agent. Used by [SwarmCoordinator]
 * to route handoff decisions between agents.
 */
enum class SwarmSignalType {
    /** Native code segment checksum mismatch — possible inline hook. */
    MEMORY_HOOK_DETECTED,

    /** Frida / Xposed / Substrate library mapped into process. */
    HOOK_LIBRARY_MAPPED,

    /** Network connection to a suspicious or known-malicious destination. */
    NETWORK_SUSPICIOUS,

    /** Active connection forcibly isolated by the swarm coordinator. */
    NETWORK_CONNECTION_ISOLATED,

    /** Process tree anomaly: unexpected parent or spawn chain. */
    PROCESS_ANOMALY,

    /** Root privilege escalation indicator in process environment. */
    PRIVILEGE_ESCALATION,

    /** General diagnostic telemetry. */
    TELEMETRY,
}

/**
 * An immutable event emitted by a [SwarmAgent] and routed by [SwarmCoordinator].
 *
 * @param agentId    Stable identifier of the originating agent.
 * @param signalType Semantic classification of the event.
 * @param severity   Risk level; [SwarmSeverity.CRITICAL] triggers coordinator handoffs.
 * @param details    Human-readable description of the observation.
 * @param timestamp  Epoch-milliseconds when the signal was captured.
 * @param metadata   Optional agent-specific key-value pairs for enrichment.
 */
data class SwarmSignal(
    val agentId: String,
    val signalType: SwarmSignalType,
    val severity: SwarmSeverity,
    val details: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
)
