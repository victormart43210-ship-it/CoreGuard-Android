package com.coldboar.coreguard.compliance

import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckState
import kotlin.math.roundToInt

/**
 * OWASP Mobile Application Security Verification Standard (MASVS) v2 control
 * categories that CoreGuard can automatically evaluate.
 *
 * See https://mas.owasp.org/MASVS/
 */
enum class MasvsCategory(val label: String, val description: String) {
    STORAGE("MASVS-STORAGE", "Sensitive data stored securely on-device"),
    CRYPTO("MASVS-CRYPTO", "Cryptographic primitives used correctly"),
    AUTH("MASVS-AUTH", "Authentication and session management"),
    NETWORK("MASVS-NETWORK", "Secure network communications"),
    PLATFORM("MASVS-PLATFORM", "Secure use of platform APIs and features"),
    CODE("MASVS-CODE", "Code quality and tamper resistance"),
    RESILIENCE("MASVS-RESILIENCE", "Runtime environment integrity")
}

/**
 * The compliance score for a single MASVS category.
 *
 * @param category   The MASVS category.
 * @param score      0–100 score for this category (weighted average of checks).
 * @param checks     The individual check results that contributed to the score.
 */
data class MasvsCategoryScore(
    val category: MasvsCategory,
    val score: Int,
    val checks: List<SecurityCheckResult>
)

/**
 * Full MASVS compliance report produced by [MasvsComplianceScorer].
 *
 * @param categoryScores   Per-category scores.
 * @param overallScore     Weighted average across all categories.
 * @param generatedAtMs    Wall-clock timestamp of report generation.
 */
data class MasvsComplianceReport(
    val categoryScores: List<MasvsCategoryScore>,
    val overallScore: Int,
    val generatedAtMs: Long = System.currentTimeMillis()
)

/**
 * Scores an app's MASVS compliance by mapping [SecurityCheckResult] identifiers
 * to MASVS categories and computing a 0–100 score for each.
 *
 * The check-to-category mapping is intentionally conservative: only checks whose
 * coverage is unambiguous are mapped. Unknown check IDs are silently ignored so
 * new evaluators added to the app do not break existing compliance reports.
 */
object MasvsComplianceScorer {

    /**
     * Maps a [SecurityCheckResult.id] to the MASVS category it contributes to.
     * A single check may contribute to exactly one category.
     */
    private val CHECK_TO_CATEGORY: Map<String, MasvsCategory> = mapOf(
        // STORAGE
        "strongbox" to MasvsCategory.STORAGE,

        // CRYPTO
        "memory_integrity" to MasvsCategory.CRYPTO,

        // AUTH
        "play_integrity" to MasvsCategory.AUTH,
        "process_lineage" to MasvsCategory.AUTH,

        // NETWORK
        "spyware_scan" to MasvsCategory.NETWORK,

        // PLATFORM
        "emulator" to MasvsCategory.PLATFORM,
        "build_type" to MasvsCategory.PLATFORM,

        // CODE
        "signature" to MasvsCategory.CODE,

        // RESILIENCE
        "debugger" to MasvsCategory.RESILIENCE,
        "native_debugger" to MasvsCategory.RESILIENCE,
        "frida" to MasvsCategory.RESILIENCE,
        "hook_maps" to MasvsCategory.RESILIENCE,
        "mount_integrity" to MasvsCategory.RESILIENCE,
        "root" to MasvsCategory.RESILIENCE,
        "inline_hook_sample" to MasvsCategory.RESILIENCE,
        "memory_patch_sample" to MasvsCategory.RESILIENCE
    )

    /**
     * Computes a full [MasvsComplianceReport] from the given check results.
     *
     * @param results All [SecurityCheckResult] instances from the current scan.
     */
    fun score(results: List<SecurityCheckResult>): MasvsComplianceReport {
        val byCategory: Map<MasvsCategory, List<SecurityCheckResult>> =
            MasvsCategory.entries.associateWith { cat ->
                results.filter { CHECK_TO_CATEGORY[it.id] == cat }
            }

        val categoryScores = byCategory.map { (cat, checks) ->
            MasvsCategoryScore(
                category = cat,
                score = computeScore(checks),
                checks = checks
            )
        }

        // All MASVS categories are weighted equally in the overall score. MASVS v2 does not
        // prescribe category weights, so equal weighting is the most standards-aligned default.
        // Per-category scores can be used by integrators who need custom weighting.
        val overall = if (categoryScores.isEmpty()) 0
        else categoryScores.sumOf { it.score } / categoryScores.size

        return MasvsComplianceReport(
            categoryScores = categoryScores,
            overallScore = overall
        )
    }

    /** 0–100 score from a list of check results (equal weighting). */
    private fun computeScore(checks: List<SecurityCheckResult>): Int {
        if (checks.isEmpty()) return 0
        val earned = checks.sumOf { r ->
            when (r.state) {
                SecurityCheckState.PASS -> 1.0
                SecurityCheckState.WARN -> 0.5
                SecurityCheckState.FAIL -> 0.0
            }
        }
        return (earned / checks.size * 100).roundToInt()
    }
}
