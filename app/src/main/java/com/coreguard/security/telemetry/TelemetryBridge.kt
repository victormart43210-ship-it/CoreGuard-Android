package com.coreguard.security.telemetry

import android.content.Context
import android.util.Log
import com.coldboar.coreguard.quilla.QuillaHypothesis
import com.coldboar.coreguard.quilla.QuillaMemoryFactory
import com.coldboar.coreguard.swarm.SwarmSignal
import org.json.JSONObject
import java.util.UUID

/**
 * Bridges on-device swarm signals into signed telemetry + Quilla hypotheses.
 *
 * Privacy: payloads stay in [TelemetryRingBuffer] unless a future opt-in exporter
 * is explicitly enabled. No cloud LLM is invoked from this path.
 */
object TelemetryBridge {

    private const val TAG = "TelemetryBridge"

    private val ring = TelemetryRingBuffer()

    @Volatile
    private var signer: TelemetrySigner? = null

    @Volatile
    private var deviceIdHash: String = "anonymous"

    fun ringBuffer(): TelemetryRingBuffer = ring

    fun init(context: Context, deviceIdHash: String = hashDeviceHint(context)) {
        this.deviceIdHash = deviceIdHash
        this.signer = TelemetrySigner(context.applicationContext)
    }

    fun onSwarmSignal(signal: SwarmSignal) {
        val delta = TelemetryDeltaFactory.fromSwarmSignal(signal) ?: return
        val activeSigner = signer
        val payload = if (activeSigner != null) {
            runCatching { activeSigner.buildAndSign(delta, deviceIdHash) }
                .onFailure { Log.w(TAG, "Telemetry sign failed: ${it.message}") }
                .getOrElse {
                    SignedTelemetryPayload(delta, deviceIdHash, signatureHex = "")
                }
        } else {
            SignedTelemetryPayload(delta, deviceIdHash, signatureHex = "")
        }
        ring.append(payload)
        recordQuillaHypothesis(delta)
    }

    fun emitHeartbeat(anomalies: Map<String, String> = emptyMap()) {
        val delta = TelemetryDeltaFactory.next(
            trigger = TriggerEvent.HEARTBEAT,
            severity = RiskSeverity.LOW,
            anomalies = anomalies
        )
        val activeSigner = signer
        val payload = if (activeSigner != null) {
            runCatching { activeSigner.buildAndSign(delta, deviceIdHash) }.getOrElse {
                SignedTelemetryPayload(delta, deviceIdHash, "")
            }
        } else {
            SignedTelemetryPayload(delta, deviceIdHash, "")
        }
        ring.append(payload)
    }

    private fun recordQuillaHypothesis(delta: TelemetryDelta) {
        if (delta.severity < RiskSeverity.HIGH) return
        val evidence = JSONObject().apply {
            put("deltaId", delta.deltaId)
            put("trigger", delta.trigger.name)
            put("severity", delta.severity.name)
            put("currentStateHash", delta.currentStateHash)
            put("anomalies", JSONObject(delta.detectedAnomalies))
        }.toString()
        QuillaMemoryFactory.hypothesisStore().upsert(
            QuillaHypothesis(
                id = UUID.randomUUID().toString(),
                hypothesisType = "SIGNED_TELEMETRY_${delta.trigger.name}",
                summary = "Signed telemetry ${delta.trigger.name} at ${delta.severity.name}",
                evidenceJson = evidence,
                confidence = when (delta.severity) {
                    RiskSeverity.CRITICAL -> 0.92f
                    RiskSeverity.HIGH -> 0.82f
                    else -> 0.70f
                },
                status = "ACTIVE"
            )
        )
    }

    private fun hashDeviceHint(context: Context): String {
        val raw = context.packageName + "|" + android.os.Build.FINGERPRINT
        return TelemetryDeltaFactory.sha256Hex(raw).take(32)
    }
}
