package com.quilla.intelligence.sdk.engine

import com.coreguard.android.data.local.dao.QuillaLearningDao
import com.coreguard.android.data.local.entity.QuillaHypothesisEntity
import com.quilla.intelligence.sdk.intel.MultiSourceStixFetcher
import com.quilla.intelligence.sdk.model.StixIndicator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Sliding-window correlation engine that accumulates [RawEvent] signals per
 * package over a 5-minute window and generates a [QuillaHypothesisEntity] when
 * the combined confidence score reaches [CONFIDENCE_THRESHOLD].
 *
 * Two hypothesis types are produced:
 * - **BEHAVIORAL_ANOMALY** – behavioural signals (DCL + root + untrusted network)
 *   accumulate to ≥ 0.75.
 * - **STIX_THREAT_MATCH** – an outbound destination matches a loaded STIX
 *   indicator; the combined score also reaches [CONFIDENCE_THRESHOLD].
 *
 * Confidence contributions:
 * - `RASP_DCL`          → +0.40
 * - `RASP_ROOT`         → +0.20
 * - STIX indicator match → +0.35 (replaces the untrusted-AP score)
 * - Untrusted AP only   → +0.15
 *
 * Thread-safety: [syncThreatFeeds] and [pushEvent] may be called concurrently;
 * the indicator list and event windows are protected by separate locks.
 *
 * @param dao         Persistence target for generated hypotheses.
 * @param stixFetcher Source of STIX2 threat-intelligence indicators.
 */
class SlidingWindowCorrelationEngine(
    private val dao: QuillaLearningDao,
    private val stixFetcher: MultiSourceStixFetcher
) {

    companion object {
        /** Events older than this are evicted from the sliding window. */
        private const val WINDOW_MS = 5L * 60L * 1000L

        /** Minimum confidence required to persist a hypothesis. */
        const val CONFIDENCE_THRESHOLD = 0.75f

        private const val SCORE_DCL = 0.40f
        private const val SCORE_ROOT = 0.20f
        private const val SCORE_STIX_MATCH = 0.35f
        private const val SCORE_UNTRUSTED_AP = 0.15f
    }

    // ── Observable output ────────────────────────────────────────────────────

    // extraBufferCapacity = 1 lets emit() complete immediately when there are
    // no active collectors (fire-and-forget semantics).  Hypotheses emitted
    // while no collector is subscribed are intentionally dropped; observers that
    // need historical values should query the DAO directly.
    private val _threatEvents =
        MutableSharedFlow<QuillaHypothesisEntity>(extraBufferCapacity = 1)

    /** Emits each [QuillaHypothesisEntity] as it is generated. */
    val threatEvents: Flow<QuillaHypothesisEntity> = _threatEvents

    // ── Internal state ───────────────────────────────────────────────────────

    private val indicatorLock = Any()
    private val activeIndicators = mutableListOf<StixIndicator>()

    private val windowLock = Any()
    private val eventsByPackage = mutableMapOf<String, MutableList<RawEvent>>()

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Replaces the active STIX indicator list with freshly fetched, non-expired
     * indicators from [stixFetcher]. Should be called on a background thread.
     */
    fun syncThreatFeeds() {
        val now = System.currentTimeMillis()
        val fetched = stixFetcher.fetchAllSources()
        synchronized(indicatorLock) {
            activeIndicators.clear()
            activeIndicators.addAll(fetched.filter { it.ttlTimestamp > now })
        }
    }

    /**
     * Ingests [event] into the sliding window for its package and re-evaluates
     * whether the confidence threshold has been crossed.
     *
     * Expired events (older than 5 minutes) are evicted before evaluation.
     * When a hypothesis is generated, the window for that package is cleared so
     * that subsequent events form a fresh accumulation window.
     *
     * The function is marked `suspend` because [MutableSharedFlow.emit] is a
     * suspend call that may back-pressure the caller when the internal buffer is
     * full.
     */
    suspend fun pushEvent(event: RawEvent) {
        val now = System.currentTimeMillis()
        val windowEvents = synchronized(windowLock) {
            val list = eventsByPackage.getOrPut(event.packageName) { mutableListOf() }
            list.add(event)
            list.removeAll { now - it.timestamp > WINDOW_MS }
            list.toList()
        }
        evaluateWindow(event.packageName, windowEvents, now)
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private suspend fun evaluateWindow(
        packageName: String,
        events: List<RawEvent>,
        now: Long
    ) {
        val hasDcl = events.any { it.type == "RASP_DCL" }
        val hasRoot = events.any { it.type == "RASP_ROOT" }
        val networkEvents = events.filter { it.type == "NETWORK_OUTBOUND" }

        // Check for an active STIX match among network events.
        var stixMatch: StixIndicator? = null
        var matchedDomain: String? = null
        for (ne in networkEvents) {
            val dest = parseDestination(ne.detail) ?: continue
            val match = synchronized(indicatorLock) {
                activeIndicators.find { it.patternValue.equals(dest, ignoreCase = true) }
            }
            if (match != null) {
                stixMatch = match
                matchedDomain = dest
                break
            }
        }

        var confidence = 0f
        val evidence = mutableListOf<String>()
        val hypothesisType: String

        if (hasDcl) {
            confidence += SCORE_DCL
            evidence.add("Dynamic Code Loading (RASP_DCL) detected in $packageName")
        }
        if (hasRoot) {
            confidence += SCORE_ROOT
            evidence.add("Root/privilege escalation (RASP_ROOT) detected")
        }

        if (stixMatch != null) {
            hypothesisType = "STIX_THREAT_MATCH"
            confidence += SCORE_STIX_MATCH
            evidence.add(
                "STIX indicator match: $matchedDomain " +
                    "(${stixMatch.sourceFeed}: ${stixMatch.description})"
            )
        } else {
            hypothesisType = "BEHAVIORAL_ANOMALY"
            if (networkEvents.any { it.detail.contains("UNTRUSTED_AP") }) {
                confidence += SCORE_UNTRUSTED_AP
                evidence.add("Outbound connection on untrusted access point")
            }
        }

        if (confidence >= CONFIDENCE_THRESHOLD) {
            val entity = QuillaHypothesisEntity(
                id = UUID.randomUUID().toString(),
                hypothesisType = hypothesisType,
                confidence = confidence.coerceAtMost(1.0f),
                summary = "Package $packageName triggered $hypothesisType correlation rule",
                evidenceJson = buildEvidenceJson(packageName, confidence, evidence, matchedDomain)
            )
            dao.upsertHypothesis(entity)
            _threatEvents.emit(entity)
            // Clear the package window so further events accumulate fresh.
            synchronized(windowLock) { eventsByPackage.remove(packageName) }
        }
    }

    /**
     * Extracts the destination host/IP from a NETWORK_OUTBOUND detail string
     * of the form `"DEST:<value>[,...]"`. Returns `null` if not found.
     */
    private fun parseDestination(detail: String): String? =
        detail.split(",")
            .find { it.startsWith("DEST:") }
            ?.removePrefix("DEST:")
            ?.trim()

    private fun buildEvidenceJson(
        packageName: String,
        confidence: Float,
        evidence: List<String>,
        matchedDomain: String?
    ): String = JSONObject().apply {
        put("packageName", packageName)
        put("confidence", confidence)
        if (matchedDomain != null) put("matchedDomain", matchedDomain)
        put("reasons", JSONArray(evidence))
    }.toString()
}
