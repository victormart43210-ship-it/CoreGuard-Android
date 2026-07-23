package com.coldboar.coreguard

import java.io.File
import kotlin.math.roundToInt

/**
 * Reads aggregate CPU usage from `/proc/stat`.
 *
 * The first successful sample primes the calculator and returns null because a
 * second snapshot is required to compute usage over time.
 */
object CpuUsageCalculator {

    internal data class CpuSnapshot(
        val totalTicks: Long,
        val idleTicks: Long
    )

    private var previousSnapshot: CpuSnapshot? = null

    @Synchronized
    fun getUsagePercent(procStatReader: () -> String = ::readProcStat): Int? {
        val snapshot = try {
            parseSnapshot(procStatReader())
        } catch (_: Exception) {
            null
        } ?: run {
            previousSnapshot = null
            return null
        }

        val previous = previousSnapshot
        previousSnapshot = snapshot

        if (previous == null) return null

        val totalDelta = snapshot.totalTicks - previous.totalTicks
        val idleDelta = snapshot.idleTicks - previous.idleTicks
        if (totalDelta <= 0L || idleDelta < 0L) return null

        val busyDelta = (totalDelta - idleDelta).coerceAtLeast(0L)
        return ((busyDelta.toDouble() / totalDelta.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    internal fun parseSnapshot(procStatContents: String): CpuSnapshot? {
        val cpuLine = procStatContents
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("cpu ") }
            ?: return null

        val fields = cpuLine.split(Regex("\\s+"))
        if (fields.size < 5) return null

        val values = fields.drop(1).map { it.toLongOrNull() ?: return null }
        if (values.any { it < 0L }) return null

        val totalTicks = values.sum()
        val idleTicks = values[3] + (values.getOrNull(4) ?: 0L)
        return CpuSnapshot(totalTicks = totalTicks, idleTicks = idleTicks)
    }

    @Synchronized
    internal fun reset() {
        previousSnapshot = null
    }

    private fun readProcStat(): String = File("/proc/stat").readText()
}
