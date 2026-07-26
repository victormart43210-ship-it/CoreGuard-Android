package com.coreguard.security.telemetry

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Client-side risk severity for a telemetry delta. */
enum class RiskSeverity { LOW, MEDIUM, HIGH, CRITICAL }

/** Why a [TelemetryDelta] was emitted. */
enum class TriggerEvent {
    HEARTBEAT,
    FRIDA_DETECTED,
    ROOT_STATE_CHANGE,
    DEBUGGER_ATTACHED,
    MEMORY_HOOK
}

/**
 * Compact continuity-preserving telemetry delta for Quilla / swarm analysis.
 *
 * Hashes link state N-1 → N so consumers can detect dropped or reordered frames.
 * This payload is built on-device; export/upload is opt-in and not automatic.
 */
data class TelemetryDelta(
    val deltaId: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val previousStateHash: String,
    val currentStateHash: String,
    val trigger: TriggerEvent,
    val severity: RiskSeverity,
    val detectedAnomalies: Map<String, String> = emptyMap(),
    val environmentHashes: List<String> = emptyList()
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("deltaId", deltaId)
        put("timestampMs", timestampMs)
        put("previousStateHash", previousStateHash)
        put("currentStateHash", currentStateHash)
        put("trigger", trigger.name)
        put("severity", severity.name)
        put("detectedAnomalies", JSONObject(detectedAnomalies))
        put("environmentHashes", JSONArray(environmentHashes))
    }

    fun toCanonicalJson(): String = toJsonObject().toString()

    companion object {
        fun fromJson(json: String): TelemetryDelta? {
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val trigger = runCatching { TriggerEvent.valueOf(obj.optString("trigger")) }.getOrNull()
                ?: return null
            val severity = runCatching { RiskSeverity.valueOf(obj.optString("severity")) }.getOrNull()
                ?: return null
            val anomaliesObj = obj.optJSONObject("detectedAnomalies") ?: JSONObject()
            val anomalies = buildMap {
                val keys = anomaliesObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, anomaliesObj.optString(k))
                }
            }
            val envArr = obj.optJSONArray("environmentHashes") ?: JSONArray()
            val env = buildList {
                for (i in 0 until envArr.length()) add(envArr.optString(i))
            }
            return TelemetryDelta(
                deltaId = obj.optString("deltaId").ifBlank { UUID.randomUUID().toString() },
                timestampMs = obj.optLong("timestampMs", System.currentTimeMillis()),
                previousStateHash = obj.optString("previousStateHash"),
                currentStateHash = obj.optString("currentStateHash"),
                trigger = trigger,
                severity = severity,
                detectedAnomalies = anomalies,
                environmentHashes = env
            )
        }
    }
}

/**
 * Keystore-signed telemetry envelope.
 *
 * [signatureHex] covers the canonical JSON of [delta] (SHA256withECDSA).
 */
data class SignedTelemetryPayload(
    val delta: TelemetryDelta,
    val deviceIdHash: String,
    val signatureHex: String
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("delta", delta.toJsonObject())
        put("deviceIdHash", deviceIdHash)
        put("signatureHex", signatureHex)
    }

    fun toCanonicalJson(): String = toJsonObject().toString()
}
