package com.coldboar.coreguard.mvt

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists a rolling log of [ScanReport] results in SharedPreferences.
 *
 * Stores at most [MAX_ENTRIES] scan records, dropping the oldest when full.
 * All I/O is synchronous and should be called from a background thread.
 */
object ScanHistoryStore {

    private const val PREFS_NAME = "coreguard_scan_history"
    private const val KEY_HISTORY = "history"
    private const val MAX_ENTRIES = 25

    /** A lightweight record of one completed scan. */
    data class ScanRecord(
        val timestampMs: Long,
        val verdict: ScanVerdict,
        val scannedArtifacts: Int,
        val indicatorCount: Int,
        val durationMillis: Long,
        val detectionCount: Int
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Appends [report] to the history, evicting the oldest entry if needed. */
    fun append(context: Context, report: ScanReport) {
        val record = JSONObject().apply {
            put("ts", System.currentTimeMillis())
            put("verdict", report.verdict.name)
            put("scanned", report.scannedArtifacts)
            put("indicators", report.indicatorCount)
            put("duration", report.durationMillis)
            put("detections", report.detections.size)
        }

        val prefs = prefs(context)
        val existing = try {
            JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }

        // Build new array, bounded to MAX_ENTRIES
        val updated = JSONArray()
        val start = maxOf(0, existing.length() - MAX_ENTRIES + 1)
        for (i in start until existing.length()) {
            updated.put(existing.get(i))
        }
        updated.put(record)

        prefs.edit().putString(KEY_HISTORY, updated.toString()).apply()
    }

    /** Returns all stored [ScanRecord]s, newest first. */
    fun load(context: Context): List<ScanRecord> {
        val raw = prefs(context).getString(KEY_HISTORY, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (array.length() - 1 downTo 0).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                ScanRecord(
                    timestampMs = obj.optLong("ts", 0L),
                    verdict = runCatching { ScanVerdict.valueOf(obj.optString("verdict", "CLEAN")) }
                        .getOrDefault(ScanVerdict.CLEAN),
                    scannedArtifacts = obj.optInt("scanned", 0),
                    indicatorCount = obj.optInt("indicators", 0),
                    durationMillis = obj.optLong("duration", 0L),
                    detectionCount = obj.optInt("detections", 0)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Clears all stored scan history. */
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_HISTORY).apply()
    }
}
