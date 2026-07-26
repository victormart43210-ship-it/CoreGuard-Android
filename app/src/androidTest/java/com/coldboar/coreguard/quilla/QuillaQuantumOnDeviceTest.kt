package com.coldboar.coreguard.quilla

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-emulator Quilla Quantum Correlate + Emulator Gate checks.
 * Classical circuit only — proves silicon path works on the AVD.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class QuillaQuantumOnDeviceTest {

    @Test
    fun quantumCircuitRunsOnDeviceJvm() {
        val report = QuillaQuantumCorrelate.runCircuit(
            packageName = "com.coldboar.coreguard.debug",
            iocHit = true,
            packageIocHit = false,
            dynamicCode = true,
            root = false,
            untrustedNetwork = true,
            classicalConfidence = 0.85f
        )
        assertTrue(report.qubitCount >= 2)
        assertTrue(report.gatePath.any { it.startsWith("H·") })
        assertTrue(report.gatePath.any { it.startsWith("M·") })
        assertTrue(report.seal.startsWith("Q┊"))
        assertTrue(QuillaQuantumCorrelate.DISCLAIMER.lowercase().contains("classical"))
    }

    @Test
    fun emulatorGateReportsEnvironment() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val status = QuillaEmulatorGate.probe(ctx)
        assertTrue(status.packageName.endsWith(".debug") || status.packageName == "com.coldboar.coreguard")
        // On CI/AVD we expect emulator; on physical device this still must not crash.
        assertFalse(status.summary.isBlank())
        assertTrue(status.summary.contains("Quilla Emulator Gate"))
    }
}
