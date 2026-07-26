package com.coldboar.coreguard.swarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SwarmCoordinator].
 *
 * Uses stub [SwarmAgent] implementations whose signals are driven directly via
 * [SwarmCoordinator.broadcast] so no background threads are involved.
 */
class SwarmCoordinatorTest {

    // -------------------------------------------------------------------------
    // Stub agent
    // -------------------------------------------------------------------------

    /** Minimal stub that records received directives. */
    private class StubAgent(
        override val agentId: String,
        override val name: String = agentId,
    ) : SwarmAgent {
        val receivedDirectives = mutableListOf<SwarmSignal>()
        var started = false
        var stopped = false
        private var latestSignal: SwarmSignal? = null

        override fun start(coordinator: SwarmCoordinator) { started = true }
        override fun stop() { stopped = true }
        override fun onCoordinatorDirective(directive: SwarmSignal) {
            receivedDirectives.add(directive)
        }
        override fun getLatestSignal(): SwarmSignal? = latestSignal

        fun emit(coordinator: SwarmCoordinator, signal: SwarmSignal) {
            latestSignal = signal
            coordinator.broadcast(signal, this)
        }
    }

    private lateinit var coordinator: SwarmCoordinator
    private lateinit var agentA: StubAgent
    private lateinit var agentB: StubAgent
    private lateinit var agentC: StubAgent

    @Before
    fun setUp() {
        // Counter is process-wide; reset so broadcast tests stay isolated.
        SwarmModule.resetAlertCounter()
        coordinator = SwarmCoordinator(maxAlerts = 10)
        agentA = StubAgent("agent-a")
        agentB = StubAgent("agent-b")
        agentC = StubAgent("agent-c")
    }

    // ─────────────────────────────────────────────────── Registration / lifecycle

    @Test
    fun `register starts agent and increments count`() {
        coordinator.register(agentA)
        assertTrue(agentA.started)
        assertEquals(1, coordinator.registeredAgentCount())
    }

    @Test
    fun `shutdown stops all agents and clears alerts`() {
        coordinator.register(agentA)
        coordinator.register(agentB)
        agentA.emit(coordinator, criticalSignal("agent-a"))
        coordinator.shutdown()
        assertTrue(agentA.stopped)
        assertTrue(agentB.stopped)
        assertEquals(0, coordinator.registeredAgentCount())
        assertTrue(coordinator.getActiveAlerts().isEmpty())
    }

    // ─────────────────────────────────────────────────────────── Broadcast routing

    @Test
    fun `WARN signal is appended to alert log`() {
        coordinator.register(agentA)
        agentA.emit(coordinator, warnSignal("agent-a"))
        assertEquals(1, coordinator.getActiveAlerts().size)
    }

    @Test
    fun `INFO signal is NOT appended to alert log`() {
        coordinator.register(agentA)
        agentA.emit(
            coordinator,
            SwarmSignal(
                agentId = "agent-a",
                signalType = SwarmSignalType.TELEMETRY,
                severity = SwarmSeverity.INFO,
                details = "All clear.",
            ),
        )
        assertTrue(coordinator.getActiveAlerts().isEmpty())
    }

    @Test
    fun `CRITICAL signal is forwarded to peer agents as a directive`() {
        coordinator.register(agentA)
        coordinator.register(agentB)
        coordinator.register(agentC)

        agentA.emit(coordinator, criticalSignal("agent-a"))

        // B and C should receive the directive; A (sender) should not.
        assertEquals(1, agentB.receivedDirectives.size)
        assertEquals(1, agentC.receivedDirectives.size)
        assertEquals(0, agentA.receivedDirectives.size)
    }

    @Test
    fun `WARN signal is NOT forwarded as a directive to peers`() {
        coordinator.register(agentA)
        coordinator.register(agentB)

        agentA.emit(coordinator, warnSignal("agent-a"))

        assertEquals(0, agentB.receivedDirectives.size)
    }

    // ─────────────────────────────────────────────────── Alert log / inspection

    @Test
    fun `getActiveAlerts returns newest first`() {
        coordinator.register(agentA)
        val first = warnSignal("agent-a", "first")
        val second = warnSignal("agent-a", "second")
        agentA.emit(coordinator, first)
        agentA.emit(coordinator, second)

        val alerts = coordinator.getActiveAlerts()
        assertEquals(2, alerts.size)
        assertEquals("second", alerts[0].details)
        assertEquals("first", alerts[1].details)
    }

    @Test
    fun `alert log is capped at maxAlerts`() {
        coordinator = SwarmCoordinator(maxAlerts = 3)
        coordinator.register(agentA)

        repeat(5) { i -> agentA.emit(coordinator, warnSignal("agent-a", "msg-$i")) }

        assertEquals(3, coordinator.getActiveAlerts().size)
    }

    @Test
    fun `getHighestThreat returns most recent CRITICAL signal`() {
        coordinator.register(agentA)
        agentA.emit(coordinator, warnSignal("agent-a"))
        assertNull(coordinator.getHighestThreat())

        val critical = criticalSignal("agent-a")
        agentA.emit(coordinator, critical)
        assertNotNull(coordinator.getHighestThreat())
        assertEquals(SwarmSeverity.CRITICAL, coordinator.getHighestThreat()!!.severity)
    }

    @Test
    fun `getHighestThreat returns null when no CRITICAL signal logged`() {
        coordinator.register(agentA)
        agentA.emit(coordinator, warnSignal("agent-a"))
        assertNull(coordinator.getHighestThreat())
    }

    // ─────────────────────────────────────────────────────────────────── Helpers

    private fun criticalSignal(agentId: String, details: String = "critical event") =
        SwarmSignal(
            agentId = agentId,
            signalType = SwarmSignalType.MEMORY_HOOK_DETECTED,
            severity = SwarmSeverity.CRITICAL,
            details = details,
        )

    private fun warnSignal(agentId: String, details: String = "warn event") =
        SwarmSignal(
            agentId = agentId,
            signalType = SwarmSignalType.NETWORK_SUSPICIOUS,
            severity = SwarmSeverity.WARN,
            details = details,
        )
}
