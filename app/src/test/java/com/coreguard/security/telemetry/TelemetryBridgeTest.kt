package com.coreguard.security.telemetry

import com.coldboar.coreguard.quilla.QuillaMemoryModule
import com.coldboar.coreguard.swarm.SwarmSeverity
import com.coldboar.coreguard.swarm.SwarmSignal
import com.coldboar.coreguard.swarm.SwarmSignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TelemetryBridgeTest {

    @Before
    fun setUp() {
        TelemetryDeltaFactory.resetContinuityForTests()
        TelemetryBridge.ringBuffer().clear()
    }

    @Test
    fun `onSwarmSignal stores signed ring entry and Quilla hypothesis for critical`() {
        val before = QuillaMemoryModule.hypothesisStore().all().size
        TelemetryBridge.onSwarmSignal(
            SwarmSignal(
                agentId = "memory",
                signalType = SwarmSignalType.MEMORY_HOOK_DETECTED,
                severity = SwarmSeverity.CRITICAL,
                details = "Inline hook suspected",
                metadata = mapOf("maps_hash" to "aa11")
            )
        )
        assertEquals(1, TelemetryBridge.ringBuffer().size())
        val payload = TelemetryBridge.ringBuffer().snapshot().first()
        assertEquals(TriggerEvent.MEMORY_HOOK, payload.delta.trigger)
        assertTrue(QuillaMemoryModule.hypothesisStore().all().size >= before + 1)
        assertTrue(
            QuillaMemoryModule.hypothesisStore().all().any {
                it.hypothesisType.startsWith("SIGNED_TELEMETRY_")
            }
        )
    }
}
