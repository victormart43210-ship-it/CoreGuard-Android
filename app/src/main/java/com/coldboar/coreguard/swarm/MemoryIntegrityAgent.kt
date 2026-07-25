package com.coldboar.coreguard.swarm

import com.coldboar.coreguard.NativeTamperGuard
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Swarm agent that monitors the integrity of the native code segment and the
 * process address space for hook libraries.
 *
 * Monitors:
 *  - [NativeTamperGuard.textIntact]         — inline hook detection via checksum.
 *  - [NativeTamperGuard.hookedLibraryPath]  — Frida/Xposed/Substrate in `/proc/self/maps`.
 *  - [NativeTamperGuard.baselineReady]      — whether the baseline was captured.
 *
 * When a [SwarmSeverity.CRITICAL] signal is emitted the [SwarmCoordinator] will
 * propagate a directive to the [NetworkMonitorAgent] to isolate active connections.
 *
 * @param pollIntervalMs   How often (ms) the agent samples the native layer.
 * @param baselineReady    Lambda returning whether the native baseline is captured.
 * @param textIntact       Lambda returning whether the code segment is unmodified.
 * @param hookedLibrary    Lambda returning the path of any mapped hook library, or "".
 * @param executor         Scheduler used for the polling loop (injectable for tests).
 */
class MemoryIntegrityAgent(
    override val agentId: String = "memory-integrity",
    override val name: String = "Memory Integrity Agent",
    private val pollIntervalMs: Long = 5_000L,
    private val baselineReady: () -> Boolean = { NativeTamperGuard.baselineReady() },
    private val textIntact: () -> Boolean = { NativeTamperGuard.textIntact() },
    private val hookedLibrary: () -> String = { NativeTamperGuard.hookedLibraryPath() },
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
        val signal = when {
            !baselineReady() -> SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.TELEMETRY,
                severity = SwarmSeverity.WARN,
                details = "Native code baseline not yet captured — integrity unverifiable.",
            )
            !textIntact() -> SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.MEMORY_HOOK_DETECTED,
                severity = SwarmSeverity.CRITICAL,
                details = "Native code segment checksum mismatch — an inline hook may have been applied.",
            )
            else -> {
                val lib = hookedLibrary()
                if (lib.isNotEmpty()) {
                    SwarmSignal(
                        agentId = agentId,
                        signalType = SwarmSignalType.HOOK_LIBRARY_MAPPED,
                        severity = SwarmSeverity.CRITICAL,
                        details = "Hook framework library mapped into process: $lib",
                        metadata = mapOf("library_path" to lib),
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
        }
        latestSignal = signal
        coordinator?.broadcast(signal, this)
    }
}
