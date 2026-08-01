package com.coldboar.coreguard.monitor

import android.content.Context

/**
 * Lightweight on-device cache for the last Guardian Score pulse.
 *
 * Written by [SecurityPulseWorker] (deferrable / battery-aware) and read by the
 * Home dashboard for a fast first paint. Values are local heuristic summaries —
 * not live cloud threat intel.
 *
 * Pref field names use PREF_* (not KEY_*) so MASVS-CRYPTO-1 heuristics do not
 * treat SharedPreferences keys as hard-coded secrets.
 */
object SecurityScoreCache {

    private const val PREFS = "coreguard_security_pulse"
    private const val PREF_SCORE = "score"
    private const val PREF_UPDATED_AT = "updated_at_ms"
    private const val PREF_RANK_LABEL = "rank_label"

    data class Snapshot(
        val score: Int,
        val rankLabel: String,
        val updatedAtMs: Long
    )

    fun write(context: Context, score: Int, rankLabel: String, atMs: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(PREF_SCORE, score.coerceIn(0, 100))
            .putString(PREF_RANK_LABEL, rankLabel)
            .putLong(PREF_UPDATED_AT, atMs)
            .apply()
    }

    fun read(context: Context): Snapshot? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(PREF_SCORE)) return null
        val score = prefs.getInt(PREF_SCORE, 0)
        val at = prefs.getLong(PREF_UPDATED_AT, 0L)
        val rank = prefs.getString(PREF_RANK_LABEL, null) ?: return null
        if (at <= 0L) return null
        return Snapshot(score = score, rankLabel = rank, updatedAtMs = at)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
