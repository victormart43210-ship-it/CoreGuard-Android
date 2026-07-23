package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpuUsageCalculatorTest {

    @Test
    fun `parseSnapshot returns total and idle ticks for valid cpu line`() {
        val snapshot = CpuUsageCalculator.parseSnapshot(
            """
            cpu  100 20 30 400 50 10 5 0 0 0
            cpu0 50 10 15 200 25 5 2 0 0 0
            """.trimIndent()
        )

        assertEquals(CpuUsageCalculator.CpuSnapshot(615L, 450L), snapshot)
    }

    @Test
    fun `parseSnapshot returns null when aggregate cpu line missing`() {
        val snapshot = CpuUsageCalculator.parseSnapshot("cpu0 1 2 3 4 5 6 7 8 9 10")

        assertNull(snapshot)
    }

    @Test
    fun `parseSnapshot returns null for malformed values`() {
        val snapshot = CpuUsageCalculator.parseSnapshot("cpu  1 2 three 4 5")

        assertNull(snapshot)
    }

    @Test
    fun `getUsagePercent returns null for first sample`() {
        CpuUsageCalculator.reset()

        val usage = CpuUsageCalculator.getUsagePercent {
            "cpu  100 20 30 400 50 10 5 0 0 0"
        }

        assertNull(usage)
    }

    @Test
    fun `getUsagePercent calculates percent from consecutive samples`() {
        CpuUsageCalculator.reset()
        CpuUsageCalculator.getUsagePercent {
            "cpu  100 20 30 400 50 10 5 0 0 0"
        }

        val usage = CpuUsageCalculator.getUsagePercent {
            "cpu  130 30 50 420 60 20 10 0 0 0"
        }

        assertEquals(71, usage)
    }

    @Test
    fun `getUsagePercent resets after invalid sample`() {
        CpuUsageCalculator.reset()
        CpuUsageCalculator.getUsagePercent {
            "cpu  100 20 30 400 50 10 5 0 0 0"
        }

        val afterInvalid = CpuUsageCalculator.getUsagePercent { "not-a-cpu-line" }
        val afterReset = CpuUsageCalculator.getUsagePercent {
            "cpu  130 30 50 420 60 20 10 0 0 0"
        }

        assertNull(afterInvalid)
        assertNull(afterReset)
    }
}
