package com.coldboar.coreguard.swarm

import android.os.Build
import com.coldboar.coreguard.NativeTamperGuard
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Swarm agent that monitors the process lineage and runtime environment for
 * privilege-escalation and dynamic instrumentation indicators.
 *
 * Monitors:
 *  - [NativeTamperGuard.tracerPid]       — native debugger / tracer attachment.
 *  - [NativeTamperGuard.rootMountEntry]  — systemless-root mount points (Magisk/KernelSU).
 *  - [NativeTamperGuard.fridaPortOpen]   — Frida server listening on a known port.
 *  - Build property heuristics           — test-keys build tag, emulator fingerprint.
 *
 * @param pollIntervalMs     Sampling period for the monitoring loop.
 * @param tracerPid          Lambda returning the process's TracerPid from /proc/self/status.
 * @param rootMountEntry     Lambda returning the first suspicious mount entry, or "".
 * @param fridaPortOpen      Lambda returning true if a Frida server port is open.
 * @param buildTags          [Build.TAGS] value (injectable for tests).
 * @param buildFingerprint   [Build.FINGERPRINT] value (injectable for tests).
 * @param executor           Scheduler used for the polling loop (injectable for tests).
 */
class ProcessLineageAgent(
    override val agentId: String = "process-lineage",
    override val name: String = "Process Lineage Agent",
    private val pollIntervalMs: Long = 8_000L,
    private val tracerPid: () -> Int = { NativeTamperGuard.tracerPid() },
    private val rootMountEntry: () -> String = { NativeTamperGuard.rootMountEntry() },
    private val fridaPortOpen: () -> Boolean = { NativeTamperGuard.fridaPortOpen() },
    private val buildTags: String = Build.TAGS ?: "",
    private val buildFingerprint: String = Build.FINGERPRINT,
    private val executor: ScheduledExecutorService = defaultExecutor(),
) : SwarmAgent {

    companion object {
        private fun defaultExecutor(): ScheduledExecutorService =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "coreguard-process-agent").apply { isDaemon = true }
            }
    }

    @Volatile private var coordinator: SwarmCoordinator? = null
    @Volatile private var latestSignal: SwarmSignal? = null
    @Volatile private var scheduledTask: ScheduledFuture<*>? = null

    // Guards rescheduling so concurrent onCoordinatorDirective calls don't create duplicate tasks.
    private val scheduleLock = Any()
    private val highAlert = AtomicBoolean(false)

    override fun start(coordinator: SwarmCoordinator) {
        this.coordinator = coordinator
        scheduledTask = executor.scheduleWithFixedDelay(
            ::poll, 0L, pollIntervalMs, TimeUnit.MILLISECONDS
        )
    }

    override fun stop() {
        scheduledTask?.cancel(false)
        scheduledTask = null
        coordinator = null
    }

    override fun onCoordinatorDirective(directive: SwarmSignal) {
        // Increase poll frequency when a critical event arrives from a peer agent.
        // Use scheduleLock + AtomicBoolean to prevent duplicate tasks on concurrent calls.
        if (directive.severity == SwarmSeverity.CRITICAL) {
            synchronized(scheduleLock) {
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

    private fun evaluate(): SwarmSignal {
        // Priority 1: active external tracer (debugger attached)
        val pid = tracerPid()
        if (pid > 0) {
            return SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.PROCESS_ANOMALY,
                severity = SwarmSeverity.CRITICAL,
                details = "External debugger/tracer attached (TracerPid=$pid). Active instrumentation likely.",
                metadata = mapOf("tracer_pid" to pid.toString()),
            )
        }

        // Priority 2: systemless-root mounts (Magisk / KernelSU)
        val mount = rootMountEntry()
        if (mount.isNotEmpty()) {
            return SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.PRIVILEGE_ESCALATION,
                severity = SwarmSeverity.CRITICAL,
                details = "Systemless-root mount detected: ${mount.take(120)}",
                metadata = mapOf("mount_entry" to mount),
            )
        }

        // Priority 3: Frida server port open
        if (fridaPortOpen()) {
            return SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.PROCESS_ANOMALY,
                severity = SwarmSeverity.WARN,
                details = "Frida instrumentation server port is open on a known loopback address.",
            )
        }

        // Priority 4: test-keys or emulator environment (lower severity; informational)
        val isTestKeys = buildTags.contains("test-keys")
        val isEmulator = buildFingerprint.startsWith("generic") ||
            buildFingerprint.contains("emulator", ignoreCase = true)

        if (isTestKeys || isEmulator) {
            val reason = buildList {
                if (isTestKeys) add("test-keys build tag")
                if (isEmulator) add("emulator fingerprint")
            }.joinToString(", ")
            return SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.PROCESS_ANOMALY,
                severity = SwarmSeverity.WARN,
                details = "Suspicious process environment: $reason",
            )
        }

        return SwarmSignal(
            agentId = agentId,
            signalType = SwarmSignalType.TELEMETRY,
            severity = SwarmSeverity.INFO,
            details = "Process lineage clean. No tracer, root mounts, or Frida server detected.",
        )
    }
}
