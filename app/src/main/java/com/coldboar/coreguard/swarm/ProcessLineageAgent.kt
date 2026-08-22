package com.coldboar.coreguard.swarm

import android.os.Build
import com.coldboar.coreguard.NativeAcquisition
import com.coldboar.coreguard.NativeTamperGuard
import com.coldboar.coreguard.explain
import com.coldboar.coreguard.reasonOrNull
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
 * @param tracerPid          Lambda acquiring the process's TracerPid from /proc/self/status.
 * @param rootMountEntry     Lambda acquiring the first suspicious mount entry, or "".
 * @param fridaPortOpen      Lambda acquiring whether a Frida server port is open.
 * @param buildTags          [Build.TAGS] value (injectable for tests).
 * @param buildFingerprint   [Build.FINGERPRINT] value (injectable for tests).
 * @param executor           Scheduler used for the polling loop (injectable for tests).
 */
class ProcessLineageAgent(
    override val agentId: String = "process-lineage",
    override val name: String = "Process Lineage Agent",
    private val pollIntervalMs: Long = 8_000L,
    private val tracerPid: () -> NativeAcquisition<Int> = { NativeTamperGuard.tracerPid() },
    private val rootMountEntry: () -> NativeAcquisition<String> =
        { NativeTamperGuard.rootMountEntry() },
    private val fridaPortOpen: () -> NativeAcquisition<Boolean> =
        { NativeTamperGuard.fridaPortOpen() },
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
        // Priority 1: active tracer (debugger attached)
        val tracer = tracerPid()
        if (tracer is NativeAcquisition.Available && tracer.value > 0) {
            return SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.PROCESS_ANOMALY,
                severity = SwarmSeverity.CRITICAL,
                details = "Native debugger/tracer attached (TracerPid=${tracer.value}). Active instrumentation likely.",
                // Raw tracer PID stays out of broadcast metadata.
                metadata = mapOf("evidence" to "tracer_attached"),
            )
        }

        // Priority 2: systemless-root mounts (Magisk / KernelSU)
        val mount = rootMountEntry()
        if (mount is NativeAcquisition.Available && mount.value.isNotEmpty()) {
            return SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.PRIVILEGE_ESCALATION,
                severity = SwarmSeverity.CRITICAL,
                details = "Systemless-root mount detected: ${mount.value.take(120)}",
                // Raw mount entry stays out of broadcast metadata.
                metadata = mapOf("evidence" to "root_mount_detected"),
            )
        }

        // Priority 3: Frida server port open
        val frida = fridaPortOpen()
        if (frida is NativeAcquisition.Available && frida.value) {
            return SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.PROCESS_ANOMALY,
                severity = SwarmSeverity.WARN,
                details = "Frida instrumentation server port is open on a known loopback address.",
            )
        }

        // No positive indicator was observed. Before reporting a clean lineage,
        // require that all three native sources actually completed: otherwise
        // "nothing detected" is only "nothing observable".
        val unavailable = listOf(
            tracer.reasonOrNull() to "Native tracer check",
            mount.reasonOrNull() to "Root mount check",
            frida.reasonOrNull() to "Frida port check",
        ).firstOrNull { it.first != null }

        if (unavailable != null) {
            val (reason, label) = unavailable
            return SwarmSignal(
                agentId = agentId,
                signalType = SwarmSignalType.TELEMETRY,
                severity = SwarmSeverity.WARN,
                details = reason!!.explain(label),
                metadata = mapOf("unavailable_reason" to reason.name),
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
