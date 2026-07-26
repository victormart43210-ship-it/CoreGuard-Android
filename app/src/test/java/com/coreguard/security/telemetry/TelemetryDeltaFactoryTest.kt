package com.coreguard.security.telemetry

import com.coldboar.coreguard.swarm.SwarmSeverity
import com.coldboar.coreguard.swarm.SwarmSignal
import com.coldboar.coreguard.swarm.SwarmSignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TelemetryDeltaFactoryTest {

    @Before
    fun setUp() {
        TelemetryDeltaFactory.resetContinuityForTests()
    }

    @Test
    fun `next advances continuity hashes`() {
        val first = TelemetryDeltaFactory.next(
            trigger = TriggerEvent.HEARTBEAT,
            severity = RiskSeverity.LOW
        )
        val second = TelemetryDeltaFactory.next(
            trigger = TriggerEvent.FRIDA_DETECTED,
            severity = RiskSeverity.CRITICAL,
            anomalies = mapOf("frida_port" to "27042")
        )
        assertEquals(TelemetryDeltaFactory.ZERO_HASH, first.previousStateHash)
        assertEquals(first.currentStateHash, second.previousStateHash)
        assertNotEquals(first.currentStateHash, second.currentStateHash)
        assertEquals(64, second.currentStateHash.length)
    }

    @Test
    fun `fromSwarmSignal maps Frida critical signal`() {
        val signal = SwarmSignal(
            agentId = "process-lineage",
            signalType = SwarmSignalType.PROCESS_ANOMALY,
            severity = SwarmSeverity.CRITICAL,
            details = "Frida instrumentation server port is open",
            metadata = mapOf("frida_port" to "27042")
        )
        val delta = TelemetryDeltaFactory.fromSwarmSignal(signal)
        assertNotNull(delta)
        assertEquals(TriggerEvent.FRIDA_DETECTED, delta!!.trigger)
        assertEquals(RiskSeverity.CRITICAL, delta.severity)
        assertEquals("27042", delta.detectedAnomalies["frida_port"])
    }

    @Test
    fun `fromSwarmSignal ignores low network noise`() {
        val signal = SwarmSignal(
            agentId = "network",
            signalType = SwarmSignalType.NETWORK_SUSPICIOUS,
            severity = SwarmSeverity.INFO,
            details = "benign"
        )
        assertNull(TelemetryDeltaFactory.fromSwarmSignal(signal))
    }

    @Test
    fun `round trip JSON preserves fields`() {
        val delta = TelemetryDeltaFactory.next(
            trigger = TriggerEvent.MEMORY_HOOK,
            severity = RiskSeverity.HIGH,
            anomalies = mapOf("lib" to "frida-gadget.so"),
            environmentHashes = listOf("deadbeef")
        )
        val parsed = TelemetryDelta.fromJson(delta.toCanonicalJson())
        assertNotNull(parsed)
        assertEquals(delta.trigger, parsed!!.trigger)
        assertEquals(delta.severity, parsed.severity)
        assertEquals(delta.currentStateHash, parsed.currentStateHash)
        assertEquals("frida-gadget.so", parsed.detectedAnomalies["lib"])
    }

    @Test
    fun `signer injectable path produces hex signature`() {
        val delta = TelemetryDeltaFactory.next(TriggerEvent.HEARTBEAT, RiskSeverity.LOW)
        val signer = TelemetrySigner(signBytes = { data ->
            ByteArray(8) { i -> (data[i % data.size].toInt() xor i).toByte() }
        })
        val signed = signer.buildAndSign(delta, deviceIdHash = "devhash")
        assertEquals("devhash", signed.deviceIdHash)
        assertEquals(16, signed.signatureHex.length)
        assertTrue(signed.signatureHex.matches(Regex("[0-9a-f]+")))
        assertTrue(signed.toCanonicalJson().contains("signatureHex"))
    }
}
