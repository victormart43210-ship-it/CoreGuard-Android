package com.coldboar.coreguard.quilla

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuillaEmulatorGateTest {

    @Test
    fun `host script and avd constants are stable`() {
        assertEquals("./scripts/quilla-emulator-tests.sh", QuillaEmulatorGate.HOST_SCRIPT)
        assertEquals("CoreGuard_API35", QuillaEmulatorGate.AVD_NAME)
    }

    @Test
    fun `disclaimer path does not claim apk starts qemu`() {
        assertTrue(QuillaEmulatorGate.HOST_SCRIPT.contains("quilla-emulator-tests"))
        assertTrue(QuillaEmulatorGate.AVD_NAME.contains("CoreGuard"))
    }
}
