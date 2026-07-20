package com.coldboar.coreguard

import android.app.ActivityManager
import android.content.Context

/**
 * Safe utility for reading device memory statistics.
 *
 * All public methods return null instead of throwing when data is unavailable
 * so callers can apply a safe default without try/catch at the call site.
 *
 * Pure calculation helpers ([calculateUsedBytes], [calculateUsedPercent],
 * [formatBytes]) are unit-testable on the JVM without an Android Context.
 */
object MemoryUsageCalculator {

    /**
     * Returns the total RAM in bytes as reported by [ActivityManager.MemoryInfo],
     * or null if the context or service is unavailable.
     */
    fun getTotalRamBytes(context: Context?): Long? {
        if (context == null) return null
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return null
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            if (info.totalMem > 0) info.totalMem else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the available (free) RAM in bytes, or null if unavailable.
     */
    fun getAvailableRamBytes(context: Context?): Long? {
        if (context == null) return null
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return null
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            if (info.availMem >= 0) info.availMem else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns used RAM in bytes (total minus available), or null if either value
     * is unavailable.
     */
    fun getUsedRamBytes(context: Context?): Long? {
        val total = getTotalRamBytes(context) ?: return null
        val avail = getAvailableRamBytes(context) ?: return null
        return calculateUsedBytes(total, avail)
    }

    /**
     * Pure calculation: used = total − available, clamped to ≥ 0.
     * Returns null when inputs are invalid.
     */
    fun calculateUsedBytes(totalBytes: Long, availableBytes: Long): Long? {
        if (totalBytes <= 0L || availableBytes < 0L) return null
        if (availableBytes > totalBytes) return null
        return (totalBytes - availableBytes).coerceAtLeast(0L)
    }

    /**
     * Returns a human-readable string for [bytes], e.g. "1.5 GB".
     * Returns "–" if [bytes] is null or negative.
     */
    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes < 0) return "–"
        return when {
            bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
            else -> "$bytes B"
        }
    }

    /**
     * Returns the used RAM percentage (0–100), or null if the total is
     * unavailable or zero.
     */
    fun getUsedRamPercent(context: Context?): Int? {
        val total = getTotalRamBytes(context) ?: return null
        val used = getUsedRamBytes(context) ?: return null
        return calculateUsedPercent(total, used)
    }

    /**
     * Pure calculation: used / total × 100, clamped to 0–100.
     * Returns null when inputs are invalid.
     */
    fun calculateUsedPercent(totalBytes: Long, usedBytes: Long): Int? {
        if (totalBytes <= 0L || usedBytes < 0L) return null
        if (usedBytes > totalBytes) return null
        return ((usedBytes.toDouble() / totalBytes.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 100)
    }
}
