package com.coldboar.coreguard.quilla

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuillaQuantumCorrelateTest {

    @Test
    fun `idle circuit does not collapse without evidence`() {
        val report = QuillaQuantumCorrelate.runCircuit(
            packageName = "com.example",
            iocHit = false,
            packageIocHit = false,
            dynamicCode = false,
            root = false,
            untrustedNetwork = false,
            classicalConfidence = 0.50f
        )
        assertEquals(0, report.qubitCount)
        assertFalse(report.collapsed)
        assertTrue(report.seal.contains("SUPERPOSED") || report.collapseProbability < 0.75f)
    }

    @Test
    fun `ioc plus rasp entangles and can collapse`() {
        val report = QuillaQuantumCorrelate.runCircuit(
            packageName = "com.evil",
            iocHit = true,
            packageIocHit = false,
            dynamicCode = true,
            root = false,
            untrustedNetwork = true,
            classicalConfidence = 0.90f
        )
        assertTrue(report.qubitCount >= 2)
        assertTrue(report.entangledPairs >= 1)
        assertTrue(report.gatePath.any { it.startsWith("H·") })
        assertTrue(report.gatePath.any { it.startsWith("CNOT·") || it.contains("CNOT") })
        assertTrue(report.gatePath.any { it.startsWith("M·") })
        assertTrue(report.collapsed || report.collapseProbability >= 0.70f)
        assertTrue(report.seal.contains("Q┊"))
    }

    @Test
    fun `disclaimer rejects qpu claim`() {
        assertTrue(QuillaQuantumCorrelate.DISCLAIMER.lowercase().contains("classical"))
        assertFalse(QuillaQuantumCorrelate.DISCLAIMER.lowercase().contains("we run on a qpu"))
    }

    @Test
    fun `engine stores quantum report after correlate`() {
        val store = QuillaHypothesisStore()
        val engine = QuillaCorrelationEngine(store, fetcher = { emptyList() })
        engine.loadIndicators(
            listOf(
                AmnestyIndicator("1", "DOMAIN", "evil.com", "test")
            )
        )
        engine.correlateSignals(
            packageName = "com.app",
            rasp = RaspEvent("com.app", isDynamicCodeLoaded = true, isRootDetected = false),
            network = NetworkEvent("com.app", "evil.com", isUntrustedNetwork = true, bytesTransferred = 100)
        )
        val q = engine.lastQuantumReport()
        assertTrue(q != null)
        assertTrue(q!!.qubitCount >= 1)
        assertTrue(store.all().isNotEmpty())
        assertTrue(store.all().first().evidenceJson.contains("quantumSeal"))
        assertEquals("AMNESTY_IOC_BEHAVIORAL_MATCH", store.all().first().hypothesisType)
    }

    @Test
    fun `phase rotate preserves born approx for pure rotation`() {
        val (r, i) = QuillaQuantumCorrelate.phaseRotate(1f, 0f, Math.PI / 2)
        val p = QuillaQuantumCorrelate.bornProbability(r, i)
        assertTrue(p in 0.99f..1.01f)
    }
}
