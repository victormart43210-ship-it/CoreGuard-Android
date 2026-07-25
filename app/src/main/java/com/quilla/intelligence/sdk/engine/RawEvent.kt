package com.quilla.intelligence.sdk.engine

/**
 * A raw telemetry event ingested by [SlidingWindowCorrelationEngine].
 *
 * @param packageName Android package that produced the signal.
 * @param type        Signal category. Recognised values:
 *                    - `"RASP_DCL"` – dynamic code loading detected.
 *                    - `"RASP_ROOT"` – root / privilege-escalation environment.
 *                    - `"NETWORK_OUTBOUND"` – outbound network connection observed.
 * @param detail      Signal-specific detail string. For `NETWORK_OUTBOUND` events
 *                    the format is `"DEST:<domain-or-ip>[,UNTRUSTED_AP]"`.
 * @param timestamp   Epoch-millisecond creation time; defaults to [System.currentTimeMillis].
 */
data class RawEvent(
    val packageName: String,
    val type: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
)
