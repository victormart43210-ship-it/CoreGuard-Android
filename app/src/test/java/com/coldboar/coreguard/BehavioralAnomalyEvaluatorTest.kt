package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BehavioralAnomalyEngine] and [ProcessLineageEvaluator].
 * All native-signal providers are replaced with injectable fakes.
 */
class BehavioralAnomalyEvaluatorTest {

    @Before
    fun reset() {
        BehavioralAnomalyEngine.reset()
        BehavioralAnomalyEngine.hookedLibraryProvider = { available("") }
        BehavioralAnomalyEngine.codeIntegrityProvider = { available(true) }
        BehavioralAnomalyEngine.processStatusProvider = { "" }
    }

    // ------------------------------------------------------------------ Engine

    @Test
    fun `no anomalies when all checks pass`() {
        BehavioralAnomalyEngine.hookedLibraryProvider = { available("") }
        BehavioralAnomalyEngine.codeIntegrityProvider = { available(true) }
        BehavioralAnomalyEngine.processStatusProvider = { "PPid:\t1\nThreads:\t20\n" }
        BehavioralAnomalyEngine.sampleOnce()
        assertTrue(BehavioralAnomalyEngine.anomalies.isEmpty())
    }

    @Test
    fun `inline hook triggers FAIL anomaly`() {
        BehavioralAnomalyEngine.hookedLibraryProvider = { available("/data/local/tmp/frida-gadget.so") }
        BehavioralAnomalyEngine.sampleOnce()
        val anomaly = BehavioralAnomalyEngine.anomalies.find { it.checkId == "inline_hook_sample" }
        assertTrue(anomaly != null)
        assertEquals(SecurityCheckState.FAIL, anomaly!!.severity)
    }

    @Test
    fun `memory patch triggers FAIL anomaly when baseline ready`() {
        BehavioralAnomalyEngine.codeIntegrityProvider = { available(false) }
        BehavioralAnomalyEngine.sampleOnce()
        val anomaly = BehavioralAnomalyEngine.anomalies.find { it.checkId == "memory_patch_sample" }
        assertTrue(anomaly != null)
        assertEquals(SecurityCheckState.FAIL, anomaly!!.severity)
    }

    @Test
    fun `memory patch reports WARN when baseline unavailable`() {
        BehavioralAnomalyEngine.codeIntegrityProvider =
            { unavailableAcquisition(NativeUnavailableReason.BASELINE_UNAVAILABLE) }
        BehavioralAnomalyEngine.sampleOnce()
        val anomaly = BehavioralAnomalyEngine.anomalies.find { it.checkId == "memory_patch_sample" }
        assertTrue("Unavailable baseline must be reported, not silently skipped", anomaly != null)
        assertEquals(SecurityCheckState.WARN, anomaly!!.severity)
    }

    @Test
    fun `PPid 0 triggers WARN anomaly`() {
        BehavioralAnomalyEngine.analyzeProcessStatus("PPid:\t0\nThreads:\t10\n")
        val anomaly = BehavioralAnomalyEngine.anomalies.find { it.checkId == "process_lineage" }
        assertTrue(anomaly != null)
        assertEquals(SecurityCheckState.WARN, anomaly!!.severity)
    }

    @Test
    fun `high thread count triggers WARN anomaly`() {
        BehavioralAnomalyEngine.analyzeProcessStatus("PPid:\t1\nThreads:\t150\n")
        val anomaly = BehavioralAnomalyEngine.anomalies.find { it.checkId == "process_lineage" }
        assertTrue(anomaly != null)
        assertEquals(SecurityCheckState.WARN, anomaly!!.severity)
    }

    @Test
    fun `reset clears accumulated anomalies`() {
        BehavioralAnomalyEngine.hookedLibraryProvider = { available("/data/local/frida-gadget.so") }
        BehavioralAnomalyEngine.sampleOnce()
        assertTrue(BehavioralAnomalyEngine.anomalies.isNotEmpty())
        BehavioralAnomalyEngine.reset()
        assertTrue(BehavioralAnomalyEngine.anomalies.isEmpty())
    }

    // -------------------------------------------------------- ProcessLineageEvaluator

    @Test
    fun `ProcessLineage PASS with normal status`() {
        val result = ProcessLineageEvaluator(
            procStatusProvider = { "PPid:\t1\nThreads:\t25\n" }
        ).evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
        assertEquals("process_lineage", result.id)
    }

    @Test
    fun `ProcessLineage WARN when PPid is 0`() {
        val result = ProcessLineageEvaluator(
            procStatusProvider = { "PPid:\t0\nThreads:\t5\n" }
        ).evaluate()
        assertEquals(SecurityCheckState.WARN, result.state)
        assertTrue(result.explanation.contains("0"))
    }

    @Test
    fun `ProcessLineage WARN when thread count exceeds threshold`() {
        val result = ProcessLineageEvaluator(
            procStatusProvider = { "PPid:\t1\nThreads:\t200\n" },
            threadWarnThreshold = 100
        ).evaluate()
        assertEquals(SecurityCheckState.WARN, result.state)
        assertTrue(result.explanation.contains("200"))
    }

    @Test
    fun `ProcessLineage WARN when status empty`() {
        val result = ProcessLineageEvaluator(procStatusProvider = { "" }).evaluate()
        assertEquals(SecurityCheckState.WARN, result.state)
    }

    @Test
    fun `ProcessLineage PASS when thread count equals threshold`() {
        val result = ProcessLineageEvaluator(
            procStatusProvider = { "PPid:\t1\nThreads:\t100\n" },
            threadWarnThreshold = 100
        ).evaluate()
        // exactly at threshold – should still PASS (threshold is "above" not "at")
        assertEquals(SecurityCheckState.PASS, result.state)
    }
}
