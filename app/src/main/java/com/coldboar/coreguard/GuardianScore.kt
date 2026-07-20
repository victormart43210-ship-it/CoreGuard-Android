package com.coldboar.coreguard

/**
 * Guardian rank derived from the numeric score. Ranks borrow the language of
 * ancient protection: AEGIS (the shield of Zeus) down to BREACHED.
 */
enum class GuardianRank {
    /** 90–100: every ward holds. */
    AEGIS,

    /** 65–89: minor weaknesses, still protected. */
    WARDED,

    /** 35–64: meaningful gaps in protection. */
    EXPOSED,

    /** 0–34: high-risk indicators present. */
    BREACHED
}

/**
 * Computes a 0–100 Guardian Score from a set of security check results.
 *
 * Scoring: each check contributes an equal share of 100 points.
 * PASS earns the full share, WARN earns half, FAIL earns nothing.
 * An empty result list scores 0 (nothing verified means nothing earned).
 */
object GuardianScore {

    fun compute(results: List<SecurityCheckResult>): Int {
        if (results.isEmpty()) return 0
        val earned = results.sumOf { result ->
            when (result.state) {
                SecurityCheckState.PASS -> 1.0
                SecurityCheckState.WARN -> 0.5
                SecurityCheckState.FAIL -> 0.0
            }
        }
        return Math.round(earned / results.size * 100).toInt()
    }

    fun rankFor(score: Int): GuardianRank = when {
        score >= 90 -> GuardianRank.AEGIS
        score >= 65 -> GuardianRank.WARDED
        score >= 35 -> GuardianRank.EXPOSED
        else -> GuardianRank.BREACHED
    }
}
