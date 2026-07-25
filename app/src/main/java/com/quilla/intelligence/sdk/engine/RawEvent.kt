package com.quilla.intelligence.sdk.engine

/**
 * A raw telemetry event emitted by RASP or Network Shield for a monitored package.
 *
 * @param packageName Android package name of the app being monitored.
 * @param type        Event category, e.g. "RASP_DCL", "RASP_ROOT", "NETWORK_OUTBOUND".
 * @param detail      Free-text payload; for NETWORK_OUTBOUND use "DEST:<domain>,<flags>".
 * @param timestamp   Event epoch-millisecond timestamp; defaults to the current wall-clock time.
 */
data class RawEvent(
    val packageName: String,
    val type: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
)
