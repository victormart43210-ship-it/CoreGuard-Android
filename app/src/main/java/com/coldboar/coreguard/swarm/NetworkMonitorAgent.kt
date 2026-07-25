package com.coldboar.coreguard.swarm

import com.coldboar.coreguard.quilla.NetworkEvent
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Swarm agent that monitors outbound network connections for suspicious activity.
 *
 * In normal operation the agent evaluates each [NetworkEvent] delivered via
 * [processEvent] against simple heuristics (untrusted network, excessive data
 * transfer).  When the [SwarmCoordinator] sends a directive — typically because
 * [MemoryIntegrityAgent] detected a hook — the agent enters *isolation mode*:
 * all subsequent events are marked as [SwarmSeverity.CRITICAL] regardless of
 * their individual heuristic score.
 *
 * @param suspiciousBytesThreshold  Bytes-per-event above which a transfer is flagged.
 * @param pollIntervalMs            Background polling period for any self-initiated checks.
 * @param executor                  Scheduler used for the polling loop (injectable for tests).
 */
class NetworkMonitorAgent(
    override val agentId: String = "network-monitor",
    override val name: String = "Network Monitor Agent",
    private val suspiciousBytesThreshold: Long = 1_048_576L, // 1 MiB
    private val heartbeatIntervalMs: Long = 10_000L,
    private val executor: ScheduledExecutorService = defaultExecutor(),
) : SwarmAgent {

    companion object {
        private fun defaultExecutor(): ScheduledExecutorService =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "coreguard-network-agent").apply { isDaemon = true }
            }
    }

    @Volatile private var coordinator: SwarmCoordinator? = null
    @Volatile private var latestSignal: SwarmSignal? = null
    @Volatile private var scheduledTask: ScheduledFuture<*>? = null

    /** True when a memory/hook critical event has triggered connection isolation mode. */
    private val isolationMode = AtomicBoolean(false)

    override fun start(coordinator: SwarmCoordinator) {
        this.coordinator = coordinator
        scheduledTask = executor.scheduleWithFixedDelay(
            ::heartbeat, 0L, heartbeatIntervalMs, TimeUnit.MILLISECONDS
        )
    }

    override fun stop() {
        scheduledTask?.cancel(false)
        scheduledTask = null
        coordinator = null
        isolationMode.set(false)
    }

    /**
     * Called by the [SwarmCoordinator] when a directive from another agent arrives.
     *
     * A [MEMORY_HOOK_DETECTED] or [HOOK_LIBRARY_MAPPED] critical event immediately
     * activates isolation mode so that all subsequent network events are treated as
     * suspicious — mirroring the "handoff" logic described in the swarm architecture.
     */
    override fun onCoordinatorDirective(directive: SwarmSignal) {
        if (directive.severity == SwarmSeverity.CRITICAL &&
            directive.signalType in setOf(
                SwarmSignalType.MEMORY_HOOK_DETECTED,
                SwarmSignalType.HOOK_LIBRARY_MAPPED,
                SwarmSignalType.PRIVILEGE_ESCALATION,
            )
        ) {
            isolationMode.set(true)
            val isolationSignal = SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.NETWORK_CONNECTION_ISOLATED,
                severity = SwarmSeverity.CRITICAL,
                details = "Network isolation activated in response to: ${directive.details}",
                metadata = mapOf("trigger_agent" to directive.agentId),
            )
            latestSignal = isolationSignal
            coordinator?.broadcast(isolationSignal, this)
        }
    }

    override fun getLatestSignal(): SwarmSignal? = latestSignal

    // -------------------------------------------------------------------------

    /**
     * Evaluates a single [NetworkEvent] and emits an appropriate [SwarmSignal].
     *
     * Intended to be called from the application's network-monitoring layer
     * (e.g. [com.coldboar.coreguard.mvt.GuardVpnService]) each time a connection
     * is observed.
     */
    fun processEvent(event: NetworkEvent) {
        val signal = buildSignal(event)
        latestSignal = signal
        coordinator?.broadcast(signal, this)
    }

    // -------------------------------------------------------------------------

    private fun buildSignal(event: NetworkEvent): SwarmSignal {
        if (isolationMode.get()) {
            return SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.NETWORK_CONNECTION_ISOLATED,
                severity = SwarmSeverity.CRITICAL,
                details = "Connection to ${event.destinationDomainOrIp} blocked — isolation mode active.",
                metadata = mapOf(
                    "package" to event.packageName,
                    "destination" to event.destinationDomainOrIp,
                    "bytes" to event.bytesTransferred.toString(),
                ),
            )
        }

        val isSuspicious = event.isUntrustedNetwork || event.bytesTransferred > suspiciousBytesThreshold
        return if (isSuspicious) {
            SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.NETWORK_SUSPICIOUS,
                severity = if (event.isUntrustedNetwork) SwarmSeverity.WARN else SwarmSeverity.INFO,
                details = buildString {
                    if (event.isUntrustedNetwork) append("Untrusted network. ")
                    if (event.bytesTransferred > suspiciousBytesThreshold)
                        append("Large transfer: ${event.bytesTransferred} bytes. ")
                    append("Destination: ${event.destinationDomainOrIp}")
                },
                metadata = mapOf(
                    "package" to event.packageName,
                    "destination" to event.destinationDomainOrIp,
                    "bytes" to event.bytesTransferred.toString(),
                ),
            )
        } else {
            SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.TELEMETRY,
                severity = SwarmSeverity.INFO,
                details = "Connection to ${event.destinationDomainOrIp} by ${event.packageName} looks normal.",
            )
        }
    }

    private fun heartbeat() {
        if (isolationMode.get()) {
            val signal = SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.NETWORK_CONNECTION_ISOLATED,
                severity = SwarmSeverity.WARN,
                details = "Network isolation mode is active — all new connections will be flagged.",
            )
            latestSignal = signal
            coordinator?.broadcast(signal, this)
        }
    }
}
