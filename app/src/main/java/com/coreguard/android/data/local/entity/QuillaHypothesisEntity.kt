package com.coreguard.android.data.local.entity

/**
 * Persistent record of a correlated threat hypothesis produced by
 * [com.quilla.intelligence.sdk.engine.SlidingWindowCorrelationEngine].
 *
 * @param id             UUID assigned at creation time.
 * @param hypothesisType Classifier: "BEHAVIORAL_ANOMALY" or "STIX_THREAT_MATCH".
 * @param confidence     Combined confidence score, clamped to [0.0, 1.0].
 * @param summary        Human-readable summary containing the target package name.
 * @param evidenceJson   JSON blob with packageName, confidence, matchedDomain (if
 *                       applicable), and a reasons array.
 * @param status         Lifecycle status; "ACTIVE" when first created.
 */
data class QuillaHypothesisEntity(
    val id: String,
    val hypothesisType: String,
    val confidence: Float,
    val summary: String,
    val evidenceJson: String,
    val status: String = "ACTIVE"
)
