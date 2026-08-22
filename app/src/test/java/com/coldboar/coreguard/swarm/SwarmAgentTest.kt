package com.coldboar.coreguard.swarm

import com.coldboar.coreguard.NativeUnavailableReason
import com.coldboar.coreguard.available
import com.coldboar.coreguard.quilla.NetworkEvent
import com.coldboar.coreguard.unavailableAcquisition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Unit tests for individual swarm agent implementations.
 *
 * All agents are constructed with injectable lambdas so the native layer is
 * never invoked and tests run on the JVM without an Android runtime.
 */
class SwarmAgentTest {

    // ─────────────────────────────────────────────── MemoryIntegrityAgent ────

    @Test
    fun `MemoryIntegrityAgent emits INFO when baseline ready and text intact`() {
        val agent = MemoryIntegrityAgent(
            executor = Executors.newSingleThreadScheduledExecutor(),
            codeIntegrity = { available(true) },
            hookedLibrary = { available("") },
            pollIntervalMs = 100L,
        )
        val coordinator = CapturingCoordinator()
        agent.start(coordinator)
        Thread.sleep(200)
        agent.stop()

        val signals = coordinator.captured
        assertTrue("Expected at least one INFO signal", signals.isNotEmpty())
        assertTrue(signals.any { it.severity == SwarmSeverity.INFO })
    }

    @Test
    fun `MemoryIntegrityAgent emits CRITICAL when text is not intact`() {
        val agent = MemoryIntegrityAgent(
            executor = Executors.newSingleThreadScheduledExecutor(),
            codeIntegrity = { available(false) },
            hookedLibrary = { available("") },
            pollIntervalMs = 100L,
        )
        val coordinator = CapturingCoordinator()
        agent.start(coordinator)
        Thread.sleep(200)
        agent.stop()

        assertTrue(coordinator.captured.any { it.signalType == SwarmSignalType.MEMORY_HOOK_DETECTED })
        assertTrue(coordinator.captured.any { it.severity == SwarmSeverity.CRITICAL })
    }

    @Test
    fun `MemoryIntegrityAgent emits CRITICAL when hook library is mapped`() {
        val agent = MemoryIntegrityAgent(
            executor = Executors.newSingleThreadScheduledExecutor(),
            codeIntegrity = { available(true) },
            hookedLibrary = { available("/data/local/tmp/frida-agent.so") },
            pollIntervalMs = 100L,
        )
        val coordinator = CapturingCoordinator()
        agent.start(coordinator)
        Thread.sleep(200)
        agent.stop()

        assertTrue(coordinator.captured.any { it.signalType == SwarmSignalType.HOOK_LIBRARY_MAPPED })
        assertTrue(coordinator.captured.any { it.severity == SwarmSeverity.CRITICAL })
    }

    @Test
    fun `MemoryIntegrityAgent emits WARN when baseline is not ready`() {
        val agent = MemoryIntegrityAgent(
            executor = Executors.newSingleThreadScheduledExecutor(),
            codeIntegrity = { unavailableAcquisition(NativeUnavailableReason.BASELINE_UNAVAILABLE) },
            hookedLibrary = { available("") },
            pollIntervalMs = 100L,
        )
        val coordinator = CapturingCoordinator()
        agent.start(coordinator)
        Thread.sleep(200)
        agent.stop()

        assertTrue(coordinator.captured.any { it.severity == SwarmSeverity.WARN })
    }

    // ─────────────────────────────────────────────── ProcessLineageAgent ─────

    @Test
    fun `ProcessLineageAgent emits CRITICAL when tracer is attached`() {
        val agent = ProcessLineageAgent(
            executor = Executors.newSingleThreadScheduledExecutor(),
            tracerPid = { available(1234) },
            rootMountEntry = { available("") },
            fridaPortOpen = { available(false) },
            buildTags = "release-keys",
            buildFingerprint = "google/sailfish/sailfish:9/PQ3A/001:user/release-keys",
            pollIntervalMs = 100L,
        )
        val coordinator = CapturingCoordinator()
        agent.start(coordinator)
        Thread.sleep(200)
        agent.stop()

        assertTrue(coordinator.captured.any { it.signalType == SwarmSignalType.PROCESS_ANOMALY && it.severity == SwarmSeverity.CRITICAL })
    }

    @Test
    fun `ProcessLineageAgent emits CRITICAL when root mount is detected`() {
        val agent = ProcessLineageAgent(
            executor = Executors.newSingleThreadScheduledExecutor(),
            tracerPid = { available(0) },
            rootMountEntry = { available("/dev/block/dm-0 /system ext4 ro") },
            fridaPortOpen = { available(false) },
            buildTags = "release-keys",
            buildFingerprint = "google/sailfish/sailfish:9/PQ3A/001:user/release-keys",
            pollIntervalMs = 100L,
        )
        val coordinator = CapturingCoordinator()
        agent.start(coordinator)
        Thread.sleep(200)
        agent.stop()

        assertTrue(coordinator.captured.any { it.signalType == SwarmSignalType.PRIVILEGE_ESCALATION })
    }

    @Test
    fun `ProcessLineageAgent emits WARN when Frida port is open`() {
        val agent = ProcessLineageAgent(
            executor = Executors.newSingleThreadScheduledExecutor(),
            tracerPid = { available(0) },
            rootMountEntry = { available("") },
            fridaPortOpen = { available(true) },
            buildTags = "release-keys",
            buildFingerprint = "google/sailfish/sailfish:9/PQ3A/001:user/release-keys",
            pollIntervalMs = 100L,
        )
        val coordinator = CapturingCoordinator()
        agent.start(coordinator)
        Thread.sleep(200)
        agent.stop()

        assertTrue(coordinator.captured.any { it.severity == SwarmSeverity.WARN && it.signalType == SwarmSignalType.PROCESS_ANOMALY })
    }

    @Test
    fun `ProcessLineageAgent emits INFO when no threats detected`() {
        val agent = ProcessLineageAgent(
            executor = Executors.newSingleThreadScheduledExecutor(),
            tracerPid = { available(0) },
            rootMountEntry = { available("") },
            fridaPortOpen = { available(false) },
            buildTags = "release-keys",
            buildFingerprint = "google/sailfish/sailfish:9/PQ3A/001:user/release-keys",
            pollIntervalMs = 100L,
        )
        val coordinator = CapturingCoordinator()
        agent.start(coordinator)
        Thread.sleep(200)
        agent.stop()

        assertTrue(coordinator.captured.any { it.severity == SwarmSeverity.INFO })
    }

    // ─────────────────────────────────────────────── NetworkMonitorAgent ──────

    @Test
    fun `NetworkMonitorAgent emits INFO for a clean trusted event`() {
        val agent = NetworkMonitorAgent()
        val coordinator = CapturingCoordinator()
        agent.start(coordinator)

        agent.processEvent(
            NetworkEvent(
                packageName = "com.safe.app",
                destinationDomainOrIp = "api.example.com",
                isUntrustedNetwork = false,
                bytesTransferred = 1024L,
            ),
        )
        agent.stop()

        assertTrue(coordinator.captured.any { it.severity == SwarmSeverity.INFO })
    }

    @Test
    fun `NetworkMonitorAgent emits WARN for untrusted network`() {
        val agent = NetworkMonitorAgent()
        val coordinator = CapturingCoordinator()
        agent.start(coordinator)

        agent.processEvent(
            NetworkEvent(
                packageName = "com.app",
                destinationDomainOrIp = "api.example.com",
                isUntrustedNetwork = true,
                bytesTransferred = 512L,
            ),
        )
        agent.stop()

        assertTrue(coordinator.captured.any { it.severity == SwarmSeverity.WARN })
    }

    @Test
    fun `NetworkMonitorAgent enters isolation mode on MEMORY_HOOK_DETECTED directive`() {
        val agent = NetworkMonitorAgent()
        val coordinator = CapturingCoordinator()
        agent.start(coordinator)

        // Deliver a critical directive from the memory agent.
        agent.onCoordinatorDirective(
            SwarmSignal(
                agentId = "memory-integrity",
                signalType = SwarmSignalType.MEMORY_HOOK_DETECTED,
                severity = SwarmSeverity.CRITICAL,
                details = "Inline hook detected.",
            ),
        )

        // Next network event must be flagged as CRITICAL (isolation mode).
        agent.processEvent(
            NetworkEvent(
                packageName = "com.app",
                destinationDomainOrIp = "safe.example.com",
                isUntrustedNetwork = false,
                bytesTransferred = 100L,
            ),
        )
        agent.stop()

        assertTrue(
            "Expected NETWORK_CONNECTION_ISOLATED signal in isolation mode",
            coordinator.captured.any { it.signalType == SwarmSignalType.NETWORK_CONNECTION_ISOLATED && it.severity == SwarmSeverity.CRITICAL },
        )
    }

    @Test
    fun `NetworkMonitorAgent does not enter isolation mode for WARN directive`() {
        val agent = NetworkMonitorAgent()
        val coordinator = CapturingCoordinator()
        agent.start(coordinator)

        // WARN directive should not activate isolation.
        agent.onCoordinatorDirective(
            SwarmSignal(
                agentId = "process-lineage",
                signalType = SwarmSignalType.PROCESS_ANOMALY,
                severity = SwarmSeverity.WARN,
                details = "Frida port open.",
            ),
        )

        agent.processEvent(
            NetworkEvent("com.app", "safe.example.com", false, 100L),
        )
        agent.stop()

        assertTrue(coordinator.captured.none { it.signalType == SwarmSignalType.NETWORK_CONNECTION_ISOLATED })
    }

    @Test
    fun `NetworkMonitorAgent getLatestSignal returns most recent event`() {
        val agent = NetworkMonitorAgent()
        val coordinator = CapturingCoordinator()
        agent.start(coordinator)

        assertNull(agent.getLatestSignal())

        agent.processEvent(NetworkEvent("com.app", "example.com", false, 100L))
        assertNotNull(agent.getLatestSignal())

        agent.stop()
    }

    // ─────────────────────────────────────────────────────── Test helper ──────

    /**
     * A [SwarmCoordinator] that records every signal passed to [broadcast]
     * without actually routing directives (keeps tests deterministic).
     */
    private class CapturingCoordinator : SwarmCoordinator() {
        val captured = mutableListOf<SwarmSignal>()

        override fun broadcast(signal: SwarmSignal, sender: SwarmAgent) {
            captured.add(signal)
            // Still call super so alert-log logic is exercised.
            super.broadcast(signal, sender)
        }
    }
}

private fun assertNotNull(value: Any?) {
    org.junit.Assert.assertNotNull(value)
}
