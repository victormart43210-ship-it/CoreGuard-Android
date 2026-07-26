package com.coreguard.security.telemetry

import com.coldboar.coreguard.swarm.SwarmSeverity
import com.coldboar.coreguard.swarm.SwarmSignal
import com.coldboar.coreguard.swarm.SwarmSignalType
import java.security.MessageDigest

/**
 * Builds continuity-linked [TelemetryDelta] frames from on-device swarm / RASP signals.
 */
object TelemetryDeltaFactory {

    /** 64-char hex zero used as the genesis previous-state hash. */
    const val ZERO_HASH =
        "0000000000000000000000000000000000000000000000000000000000000000"

    @Volatile
    private var lastStateHash: String = ZERO_HASH

    fun resetContinuityForTests() {
        lastStateHash = ZERO_HASH
    }

    fun previousHash(): String = lastStateHash

    /**
     * Creates the next delta, advancing the continuity chain.
     */
    @Synchronized
    fun next(
        trigger: TriggerEvent,
        severity: RiskSeverity,
        anomalies: Map<String, String> = emptyMap(),
        environmentHashes: List<String> = emptyList(),
        timestampMs: Long = System.currentTimeMillis()
    ): TelemetryDelta {
        val previous = lastStateHash
        val current = hashState(previous, trigger, severity, anomalies, environmentHashes, timestampMs)
        val delta = TelemetryDelta(
            timestampMs = timestampMs,
            previousStateHash = previous,
            currentStateHash = current,
            trigger = trigger,
            severity = severity,
            detectedAnomalies = anomalies,
            environmentHashes = environmentHashes
        )
        lastStateHash = current
        return delta
    }

    /** Maps a [SwarmSignal] into a telemetry frame when severity warrants it. */
    fun fromSwarmSignal(signal: SwarmSignal): TelemetryDelta? {
        val trigger = when (signal.signalType) {
            SwarmSignalType.HOOK_LIBRARY_MAPPED,
            SwarmSignalType.MEMORY_HOOK_DETECTED -> TriggerEvent.MEMORY_HOOK
            SwarmSignalType.PRIVILEGE_ESCALATION -> TriggerEvent.ROOT_STATE_CHANGE
            SwarmSignalType.PROCESS_ANOMALY ->
                if (signal.details.contains("Frida", ignoreCase = true) ||
                    signal.metadata.keys.any { it.contains("frida", ignoreCase = true) }
                ) {
                    TriggerEvent.FRIDA_DETECTED
                } else {
                    TriggerEvent.DEBUGGER_ATTACHED
                }
            SwarmSignalType.TELEMETRY -> TriggerEvent.HEARTBEAT
            else -> return null
        }
        if (signal.severity < SwarmSeverity.WARN && trigger != TriggerEvent.HEARTBEAT) {
            return null
        }
        val severity = when (signal.severity) {
            SwarmSeverity.INFO -> RiskSeverity.LOW
            SwarmSeverity.WARN -> RiskSeverity.MEDIUM
            SwarmSeverity.CRITICAL -> RiskSeverity.CRITICAL
        }
        val anomalies = linkedMapOf(
            "agentId" to signal.agentId,
            "signalType" to signal.signalType.name,
            "details" to signal.details.take(240)
        ) + signal.metadata
        return next(
            trigger = trigger,
            severity = severity,
            anomalies = anomalies,
            environmentHashes = signal.metadata["maps_hash"]?.let { listOf(it) }.orEmpty(),
            timestampMs = signal.timestamp
        )
    }

    fun hashState(
        previous: String,
        trigger: TriggerEvent,
        severity: RiskSeverity,
        anomalies: Map<String, String>,
        environmentHashes: List<String>,
        timestampMs: Long
    ): String {
        val material = buildString {
            append(previous)
            append('|')
            append(trigger.name)
            append('|')
            append(severity.name)
            append('|')
            append(timestampMs)
            append('|')
            anomalies.toSortedMap().forEach { (k, v) ->
                append(k)
                append('=')
                append(v)
                append(';')
            }
            environmentHashes.sorted().forEach {
                append(it)
                append(';')
            }
        }
        return sha256Hex(material)
    }

    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
