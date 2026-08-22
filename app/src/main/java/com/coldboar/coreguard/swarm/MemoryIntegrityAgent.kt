package com.coldboar.coreguard.swarm

import com.coldboar.coreguard.NativeAcquisition
import com.coldboar.coreguard.NativeTamperGuard
import com.coldboar.coreguard.NativeUnavailableReason
import com.coldboar.coreguard.explain
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Swarm agent that monitors the integrity of the native code segment and the
 * process address space for hook libraries.
 *
 * Monitors:
 *  - [NativeTamperGuard.codeIntegrityIntact] — inline hook detection via checksum,
 *    including whether a baseline was captured at all.
 *  - [NativeTamperGuard.hookedLibraryPath]   — Frida/Xposed/Substrate in `/proc/self/maps`.
 *
 * When a [SwarmSeverity.CRITICAL] signal is emitted the [SwarmCoordinator] will
 * propagate a directive to the [NetworkMonitorAgent] to isolate active connections.
 *
 * @param pollIntervalMs   How often (ms) the agent samples the native layer.
 * @param codeIntegrity    Lambda acquiring whether the code segment is unmodified.
 * @param hookedLibrary    Lambda acquiring the path of any mapped hook library, or "".
 * @param executor         Scheduler used for the polling loop (injectable for tests).
 */
class MemoryIntegrityAgent(
    override val agentId: String = "memory-integrity",
    override val name: String = "Memory Integrity Agent",
    private val pollIntervalMs: Long = 5_000L,
    private val codeIntegrity: () -> NativeAcquisition<Boolean> =
        { NativeTamperGuard.codeIntegrityIntact() },
    private val hookedLibrary: () -> NativeAcquisition<String> =
        { NativeTamperGuard.hookedLibraryPath() },
    private val executor: ScheduledExecutorService = defaultExecutor(),
) : SwarmAgent {

    companion object {
        private fun defaultExecutor(): ScheduledExecutorService =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "coreguard-memory-agent").apply { isDaemon = true }
            }
    }

    @Volatile private var coordinator: SwarmCoordinator? = null
    @Volatile private var latestSignal: SwarmSignal? = null
    @Volatile private var scheduledTask: ScheduledFuture<*>? = null

    // Guards the highAlert flag and task reschedule together.
    private val alertLock = Any()
    private val highAlert = AtomicBoolean(false)

    override fun start(coordinator: SwarmCoordinator) {
        this.coordinator = coordinator
        schedulePolling(pollIntervalMs)
    }

    override fun stop() {
        scheduledTask?.cancel(false)
        scheduledTask = null
        coordinator = null
    }

    override fun onCoordinatorDirective(directive: SwarmSignal) {
        // If another agent triggered a critical event, increase poll frequency.
        // Use alertLock to prevent duplicate task creation from concurrent calls.
        if (directive.severity == SwarmSeverity.CRITICAL) {
            synchronized(alertLock) {
                if (!highAlert.getAndSet(true)) {
                    scheduledTask?.cancel(false)
                    schedulePolling(pollIntervalMs / 2)
                }
            }
        }
    }

    override fun getLatestSignal(): SwarmSignal? = latestSignal

    // -------------------------------------------------------------------------

    private fun schedulePolling(intervalMs: Long) {
        scheduledTask = executor.scheduleWithFixedDelay(
            ::poll, 0L, intervalMs, TimeUnit.MILLISECONDS
        )
    }

    private fun poll() {
        val signal = evaluate()
        latestSignal = signal
        coordinator?.broadcast(signal, this)
    }

    /**
     * An unavailable acquisition yields a WARN telemetry signal that says the
     * check could not be completed. It must never yield the INFO "code segment
     * intact, no hook libraries" signal, which is a verified-clean claim.
     */
    private fun evaluate(): SwarmSignal {
        val integrity = codeIntegrity()
        if (integrity is NativeAcquisition.Unavailable) {
            return unavailableSignal(integrity.reason, "Native code-integrity check")
        }
        if (!(integrity as NativeAcquisition.Available).value) {
            return SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.MEMORY_HOOK_DETECTED,
                severity = SwarmSeverity.CRITICAL,
                details = "Native code segment checksum mismatch — an inline hook may have been applied.",
            )
        }

        val hooks = hookedLibrary()
        if (hooks is NativeAcquisition.Unavailable) {
            return unavailableSignal(hooks.reason, "Hook library check")
        }

        val lib = (hooks as NativeAcquisition.Available).value
        return if (lib.isNotEmpty()) {
            SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.HOOK_LIBRARY_MAPPED,
                severity = SwarmSeverity.CRITICAL,
                details = "Hook framework library mapped into process: $lib",
                // Raw library paths stay out of broadcast metadata; the path is
                // already in details for local diagnostics only.
                metadata = mapOf("evidence" to "hook_library_mapped"),
            )
        } else {
            SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.TELEMETRY,
                severity = SwarmSeverity.INFO,
                details = "Code segment intact. No hook libraries detected.",
            )
        }
    }

    private fun unavailableSignal(
        reason: NativeUnavailableReason,
        checkLabel: String,
    ): SwarmSignal = SwarmSignal(
        agentId = agentId,
        signalType = SwarmSignalType.TELEMETRY,
        severity = SwarmSeverity.WARN,
        details = reason.explain(checkLabel),
        metadata = mapOf("unavailable_reason" to reason.name),
    )
}
