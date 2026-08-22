package com.coldboar.coreguard.swarm

import com.coldboar.coreguard.NativeUnavailableReason
import com.coldboar.coreguard.available
import com.coldboar.coreguard.unavailableAcquisition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Locks down the swarm-agent side of the native truth-state contract.
 *
 * A blind sensor must produce a WARN telemetry signal that says the check could
 * not be completed. It must never produce the INFO signals ("code segment
 * intact", "process lineage clean") because those are verified-clean claims
 * that other agents and the UI act on.
 */
class NativeUnavailableSignalTest {

    private val cleanClaimWords = listOf(
        "clean", "safe", "protected", "no threat",
        "no hooks", "no tracer", "intact", "verified",
    )

    private fun assertNoCleanClaim(signal: SwarmSignal) {
        val lower = signal.details.lowercase()
        cleanClaimWords.forEach { word ->
            assertFalse(
                "Unavailable signal must not claim \"$word\": ${signal.details}",
                lower.contains(word),
            )
        }
    }

    private fun collect(block: (CapturingCoordinator) -> Unit): List<SwarmSignal> {
        val coordinator = CapturingCoordinator()
        block(coordinator)
        return coordinator.captured
    }

    // ------------------------------------------------- MemoryIntegrityAgent

    @Test
    fun `MemoryIntegrityAgent warns instead of claiming intact when integrity is unavailable`() {
        val signals = collect { coordinator ->
            val agent = MemoryIntegrityAgent(
                executor = Executors.newSingleThreadScheduledExecutor(),
                codeIntegrity = { unavailableAcquisition(NativeUnavailableReason.SOURCE_READ_FAILED) },
                hookedLibrary = { available("") },
                pollIntervalMs = 100L,
            )
            agent.start(coordinator)
            Thread.sleep(200)
            agent.stop()
        }

        assertTrue("Expected a signal", signals.isNotEmpty())
        assertTrue(signals.all { it.severity == SwarmSeverity.WARN })
        assertTrue(
            "Unavailable integrity must never be reported as INFO",
            signals.none { it.severity == SwarmSeverity.INFO },
        )
        signals.forEach(::assertNoCleanClaim)
    }

    @Test
    fun `MemoryIntegrityAgent warns when hook library source is unavailable`() {
        val signals = collect { coordinator ->
            val agent = MemoryIntegrityAgent(
                executor = Executors.newSingleThreadScheduledExecutor(),
                codeIntegrity = { available(true) },
                hookedLibrary = { unavailableAcquisition() },
                pollIntervalMs = 100L,
            )
            agent.start(coordinator)
            Thread.sleep(200)
            agent.stop()
        }

        assertTrue(signals.isNotEmpty())
        assertTrue(
            "A readable baseline plus a blind maps file is not 'no hook libraries'",
            signals.none { it.severity == SwarmSeverity.INFO },
        )
        signals.forEach(::assertNoCleanClaim)
    }

    @Test
    fun `MemoryIntegrityAgent does not put raw library paths in broadcast metadata`() {
        val signals = collect { coordinator ->
            val agent = MemoryIntegrityAgent(
                executor = Executors.newSingleThreadScheduledExecutor(),
                codeIntegrity = { available(true) },
                hookedLibrary = { available("/data/local/tmp/frida-agent.so") },
                pollIntervalMs = 100L,
            )
            agent.start(coordinator)
            Thread.sleep(200)
            agent.stop()
        }

        val leaked = signals.flatMap { it.metadata.values }.filter { it.contains("/data/local") }
        assertTrue("Raw library path leaked into metadata: $leaked", leaked.isEmpty())
    }

    // -------------------------------------------------- ProcessLineageAgent

    @Test
    fun `ProcessLineageAgent does not claim clean lineage when tracer source is unavailable`() {
        val signals = collect { coordinator ->
            val agent = ProcessLineageAgent(
                executor = Executors.newSingleThreadScheduledExecutor(),
                tracerPid = { unavailableAcquisition() },
                rootMountEntry = { available("") },
                fridaPortOpen = { available(false) },
                buildTags = "release-keys",
                buildFingerprint = "google/sailfish/sailfish:9/PQ3A/001:user/release-keys",
                pollIntervalMs = 100L,
            )
            agent.start(coordinator)
            Thread.sleep(200)
            agent.stop()
        }

        assertTrue(signals.isNotEmpty())
        assertTrue(
            "Blind tracer source must not yield the INFO clean-lineage signal",
            signals.none { it.severity == SwarmSeverity.INFO },
        )
        signals.forEach(::assertNoCleanClaim)
    }

    @Test
    fun `ProcessLineageAgent does not claim clean lineage when mount source is unavailable`() {
        val signals = collect { coordinator ->
            val agent = ProcessLineageAgent(
                executor = Executors.newSingleThreadScheduledExecutor(),
                tracerPid = { available(0) },
                rootMountEntry = { unavailableAcquisition() },
                fridaPortOpen = { available(false) },
                buildTags = "release-keys",
                buildFingerprint = "google/sailfish/sailfish:9/PQ3A/001:user/release-keys",
                pollIntervalMs = 100L,
            )
            agent.start(coordinator)
            Thread.sleep(200)
            agent.stop()
        }

        assertTrue(signals.none { it.severity == SwarmSeverity.INFO })
        signals.forEach(::assertNoCleanClaim)
    }

    @Test
    fun `ProcessLineageAgent still reports a detected tracer without leaking the pid`() {
        val signals = collect { coordinator ->
            val agent = ProcessLineageAgent(
                executor = Executors.newSingleThreadScheduledExecutor(),
                tracerPid = { available(1234) },
                rootMountEntry = { available("") },
                fridaPortOpen = { available(false) },
                buildTags = "release-keys",
                buildFingerprint = "google/sailfish/sailfish:9/PQ3A/001:user/release-keys",
                pollIntervalMs = 100L,
            )
            agent.start(coordinator)
            Thread.sleep(200)
            agent.stop()
        }

        assertTrue(
            "A completed positive probe must still escalate",
            signals.any {
                it.signalType == SwarmSignalType.PROCESS_ANOMALY &&
                    it.severity == SwarmSeverity.CRITICAL
            },
        )
        val leaked = signals.flatMap { it.metadata.values }.filter { it.contains("1234") }
        assertTrue("Raw tracer pid leaked into metadata: $leaked", leaked.isEmpty())
    }

    private class CapturingCoordinator : SwarmCoordinator() {
        val captured = mutableListOf<SwarmSignal>()

        override fun broadcast(signal: SwarmSignal, sender: SwarmAgent) {
            captured.add(signal)
            super.broadcast(signal, sender)
        }
    }
}
