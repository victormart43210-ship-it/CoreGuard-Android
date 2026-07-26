package com.coldboar.coreguard.elite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicThreatEngineTest {

    @Test
    fun `band thresholds map score ranges`() {
        assertEquals(DynamicThreatEngine.Band.CLEAR, DynamicThreatEngine.bandFor(0))
        assertEquals(DynamicThreatEngine.Band.CLEAR, DynamicThreatEngine.bandFor(24))
        assertEquals(DynamicThreatEngine.Band.WATCH, DynamicThreatEngine.bandFor(25))
        assertEquals(DynamicThreatEngine.Band.WATCH, DynamicThreatEngine.bandFor(49))
        assertEquals(DynamicThreatEngine.Band.ELEVATED, DynamicThreatEngine.bandFor(50))
        assertEquals(DynamicThreatEngine.Band.ELEVATED, DynamicThreatEngine.bandFor(74))
        assertEquals(DynamicThreatEngine.Band.CRITICAL, DynamicThreatEngine.bandFor(75))
        assertEquals(DynamicThreatEngine.Band.CRITICAL, DynamicThreatEngine.bandFor(100))
    }

    @Test
    fun `disclaimer mentions on-device correlator not cloud LLM`() {
        assertTrue(DynamicThreatEngine.DISCLAIMER.contains("on-device", ignoreCase = true))
        assertTrue(DynamicThreatEngine.DISCLAIMER.contains("not a cloud LLM", ignoreCase = true))
    }
}
