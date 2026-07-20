package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MemoryUsageCalculator].
 *
 * All tests run on the JVM without an Android context so we verify the
 * null-safe and pure-utility methods only.
 */
class MemoryUsageCalculatorTest {

    // -----------------------------------------------------------------------
    // formatBytes
    // -----------------------------------------------------------------------

    @Test
    fun `formatBytes returns dash for null`() {
        assertEquals("–", MemoryUsageCalculator.formatBytes(null))
    }

    @Test
    fun `formatBytes returns dash for negative`() {
        assertEquals("–", MemoryUsageCalculator.formatBytes(-1L))
    }

    @Test
    fun `formatBytes returns bytes for small values`() {
        assertEquals("512 B", MemoryUsageCalculator.formatBytes(512L))
    }

    @Test
    fun `formatBytes returns KB for kilobyte range`() {
        val result = MemoryUsageCalculator.formatBytes(2048L)
        assertTrue("Expected KB suffix, got: $result", result.endsWith("KB"))
    }

    @Test
    fun `formatBytes returns MB for megabyte range`() {
        val result = MemoryUsageCalculator.formatBytes(5 * 1_048_576L)
        assertTrue("Expected MB suffix, got: $result", result.endsWith("MB"))
    }

    @Test
    fun `formatBytes returns GB for gigabyte range`() {
        val result = MemoryUsageCalculator.formatBytes(2 * 1_073_741_824L)
        assertTrue("Expected GB suffix, got: $result", result.endsWith("GB"))
    }

    @Test
    fun `formatBytes returns zero bytes for zero value`() {
        assertEquals("0 B", MemoryUsageCalculator.formatBytes(0L))
    }

    // -----------------------------------------------------------------------
    // getTotalRamBytes / getAvailableRamBytes / getUsedRamBytes – null context
    // -----------------------------------------------------------------------

    @Test
    fun `getTotalRamBytes returns null when context is null`() {
        assertNull(MemoryUsageCalculator.getTotalRamBytes(null))
    }

    @Test
    fun `getAvailableRamBytes returns null when context is null`() {
        assertNull(MemoryUsageCalculator.getAvailableRamBytes(null))
    }

    @Test
    fun `getUsedRamBytes returns null when context is null`() {
        assertNull(MemoryUsageCalculator.getUsedRamBytes(null))
    }

    @Test
    fun `getUsedRamPercent returns null when context is null`() {
        assertNull(MemoryUsageCalculator.getUsedRamPercent(null))
    }
}
