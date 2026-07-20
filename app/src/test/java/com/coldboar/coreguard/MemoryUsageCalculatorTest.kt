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
        assertTrue(result.startsWith("5.0"))
    }

    @Test
    fun `formatBytes returns GB for gigabyte range`() {
        val result = MemoryUsageCalculator.formatBytes(2 * 1_073_741_824L)
        assertTrue("Expected GB suffix, got: $result", result.endsWith("GB"))
        assertTrue(result.startsWith("2.0"))
    }

    @Test
    fun `formatBytes returns zero bytes for zero value`() {
        assertEquals("0 B", MemoryUsageCalculator.formatBytes(0L))
    }

    // -----------------------------------------------------------------------
    // Pure calculations
    // -----------------------------------------------------------------------

    @Test
    fun `calculateUsedBytes subtracts available from total`() {
        assertEquals(3_000L, MemoryUsageCalculator.calculateUsedBytes(10_000L, 7_000L))
    }

    @Test
    fun `calculateUsedBytes returns null for zero total`() {
        assertNull(MemoryUsageCalculator.calculateUsedBytes(0L, 0L))
    }

    @Test
    fun `calculateUsedBytes returns null when available exceeds total`() {
        assertNull(MemoryUsageCalculator.calculateUsedBytes(100L, 200L))
    }

    @Test
    fun `calculateUsedBytes returns null for negative available`() {
        assertNull(MemoryUsageCalculator.calculateUsedBytes(100L, -1L))
    }

    @Test
    fun `calculateUsedPercent returns expected percentage`() {
        assertEquals(25, MemoryUsageCalculator.calculateUsedPercent(400L, 100L))
    }

    @Test
    fun `calculateUsedPercent clamps to 0-100`() {
        assertEquals(100, MemoryUsageCalculator.calculateUsedPercent(100L, 100L))
        assertEquals(0, MemoryUsageCalculator.calculateUsedPercent(100L, 0L))
    }

    @Test
    fun `calculateUsedPercent returns null for invalid inputs`() {
        assertNull(MemoryUsageCalculator.calculateUsedPercent(0L, 0L))
        assertNull(MemoryUsageCalculator.calculateUsedPercent(100L, -1L))
        assertNull(MemoryUsageCalculator.calculateUsedPercent(100L, 200L))
    }

    // -----------------------------------------------------------------------
    // Context-based helpers – null context
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
