package com.coreguard.security.telemetry

/**
 * In-memory ring of recently signed telemetry payloads for Quilla / debug export.
 * Does **not** upload off-device.
 */
class TelemetryRingBuffer(private val capacity: Int = 32) {

    private val lock = Any()
    private val items = ArrayDeque<SignedTelemetryPayload>(capacity + 1)

    fun append(payload: SignedTelemetryPayload) {
        synchronized(lock) {
            items.addLast(payload)
            while (items.size > capacity) items.removeFirst()
        }
    }

    fun snapshot(): List<SignedTelemetryPayload> =
        synchronized(lock) { items.toList().asReversed() }

    fun clear() {
        synchronized(lock) { items.clear() }
    }

    fun size(): Int = synchronized(lock) { items.size }
}
