package com.quilla.intelligence.sdk.intel

import com.quilla.intelligence.sdk.model.StixIndicator

/**
 * Per-source outcome for a multi-feed STIX pull.
 *
 * [status] is one of: VERIFIED, UNAVAILABLE, FAILED — never SAFE/PASS/synced
 * when the source did not verify and parse successfully.
 */
data class StixSourceResult(
    val name: String,
    val url: String,
    val success: Boolean,
    val indicators: List<StixIndicator> = emptyList(),
    val failureReason: String? = null,
    val status: String
) {
    companion object {
        const val STATUS_VERIFIED = "VERIFIED"
        const val STATUS_UNAVAILABLE = "UNAVAILABLE"
        const val STATUS_FAILED = "FAILED"
    }
}

/**
 * Aggregate STIX fetch report. Zero verified sources ⇒ [allUnavailable] = true.
 */
data class StixFetchReport(
    val indicators: List<StixIndicator>,
    val sourceResults: List<StixSourceResult>,
    val verifiedSourceCount: Int,
    val failedSourceCount: Int,
    val allUnavailable: Boolean
) {
    companion object {
        fun unavailable(reason: String, configuredFeeds: Int = 0): StixFetchReport =
            StixFetchReport(
                indicators = emptyList(),
                sourceResults = listOf(
                    StixSourceResult(
                        name = "STIX",
                        url = "",
                        success = false,
                        failureReason = reason,
                        status = StixSourceResult.STATUS_UNAVAILABLE
                    )
                ),
                verifiedSourceCount = 0,
                failedSourceCount = configuredFeeds.coerceAtLeast(1),
                allUnavailable = true
            )

        /**
         * Wraps a legacy [MultiSourceStixFetcher.fetchAllSources] result.
         * An empty list cannot prove network success → UNAVAILABLE.
         */
        fun fromLegacyList(indicators: List<StixIndicator>): StixFetchReport {
            if (indicators.isEmpty()) {
                return unavailable("empty STIX result — not synchronized")
            }
            return StixFetchReport(
                indicators = indicators,
                sourceResults = listOf(
                    StixSourceResult(
                        name = "legacy-injected",
                        url = "",
                        success = true,
                        indicators = indicators,
                        status = StixSourceResult.STATUS_VERIFIED
                    )
                ),
                verifiedSourceCount = 1,
                failedSourceCount = 0,
                allUnavailable = false
            )
        }
    }
}

/**
 * Aggregates STIX 2.x threat indicators from multiple configured intelligence feeds.
 *
 * Implementations must be safe to call from a background thread. Prefer overriding
 * [fetchReport] for per-source truth; the default wraps [fetchAllSources] and treats
 * an empty list as UNAVAILABLE (not synchronized).
 */
interface MultiSourceStixFetcher {
    /**
     * Merged indicators from verified sources. Empty does **not** mean successful sync.
     */
    fun fetchAllSources(): List<StixIndicator>

    /**
     * Per-source success/failure report. Empty verified set means UNAVAILABLE.
     */
    fun fetchReport(): StixFetchReport = StixFetchReport.fromLegacyList(fetchAllSources())
}
