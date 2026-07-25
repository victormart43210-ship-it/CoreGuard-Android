package com.quilla.intelligence.sdk.engine

import com.coreguard.android.data.local.dao.QuillaLearningDao
import com.coreguard.android.data.local.entity.QuillaHypothesisEntity
import com.quilla.intelligence.sdk.intel.MultiSourceStixFetcher
import com.quilla.intelligence.sdk.model.StixIndicator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Real-time threat correlation engine that maintains a per-package sliding event window
 * and generates [QuillaHypothesisEntity] records when the accumulated confidence score
 * crosses a detection threshold.
 *
 * **Evaluation trigger:** the window is evaluated for threat hypotheses only when a
 * `NETWORK_OUTBOUND` event is ingested. RASP-only signals (DCL, ROOT) are accumulated
 * in the window but do not emit a hypothesis on their own; they contribute confidence
 * when a subsequent network event arrives.
 *
 * Two detection modes are supported, evaluated in priority order:
 *
 * 1. **STIX_THREAT_MATCH** – an outbound network connection matches a live STIX indicator
 *    loaded via [syncThreatFeeds]. Confidence starts at 0.60 with bonuses for co-occurring
 *    behavioral signals; threshold is 0.70.
 *
 * 2. **BEHAVIORAL_ANOMALY** – the accumulated score of behavioral signals (DCL, root,
 *    untrusted network) reaches the 0.75 threshold without a STIX indicator match.
 *
 * Signal confidence contributions for BEHAVIORAL_ANOMALY:
 * - Base score             → 0.40
 * - Dynamic code loading   → +0.25
 * - Root / privilege esc.  → +0.20
 * - Untrusted network AP   → +0.10
 *
 * Signal confidence contributions for STIX_THREAT_MATCH:
 * - STIX indicator match   → 0.60 (base)
 * - Dynamic code loading   → +0.20
 * - Root / privilege esc.  → +0.15
 *
 * @param dao         Persistent store for generated hypotheses.
 * @param stixFetcher Source of multi-feed STIX2 threat indicators.
 */
class SlidingWindowCorrelationEngine(
    private val dao: QuillaLearningDao,
    private val stixFetcher: MultiSourceStixFetcher
) {

    companion object {
        /** Width of the sliding event window in milliseconds (5 minutes). */
        private const val WINDOW_MS = 5L * 60 * 1000

        /** Minimum confidence required to persist a BEHAVIORAL_ANOMALY hypothesis. */
        private const val BEHAVIORAL_THRESHOLD = 0.75f

        /** Minimum confidence required to persist a STIX_THREAT_MATCH hypothesis. */
        private const val STIX_THRESHOLD = 0.70f
    }

    private val mutex = Mutex()
    private val eventWindows = mutableMapOf<String, MutableList<RawEvent>>()

    /** Replaced atomically on each [syncThreatFeeds] call; reads never need a lock. */
    @Volatile
    private var activeStixIndicators: List<StixIndicator> = emptyList()

    private val _threatEvents = MutableSharedFlow<QuillaHypothesisEntity>()

    /**
     * Hot flow that emits every [QuillaHypothesisEntity] at the moment it is persisted.
     * Collectors added after an emission will not receive past values (replay = 0).
     */
    val threatEvents: SharedFlow<QuillaHypothesisEntity> = _threatEvents

    /**
     * Fetches fresh STIX indicators from all configured sources and replaces the
     * current active indicator list. Must be called from a background coroutine.
     */
    fun syncThreatFeeds() {
        val fetched = stixFetcher.fetchAllSources()
        activeStixIndicators = fetched
    }

    /**
     * Ingests a raw telemetry event, evicts stale events outside the 5-minute window,
     * and evaluates the updated window for threat hypotheses.
     *
     * A [QuillaHypothesisEntity] is persisted via [dao] and emitted on [threatEvents]
     * when a detection threshold is crossed.
     *
     * The function is marked `suspend` because [MutableSharedFlow.emit] is a
     * suspend call that back-pressures the caller when no collectors are active.
     *
     * @param event The telemetry event to ingest.
     */
    suspend fun pushEvent(event: RawEvent) {
        val eventTime = event.timestamp
        val cutoff = eventTime - WINDOW_MS

        mutex.withLock {
            val window = eventWindows.getOrPut(event.packageName) { mutableListOf() }
            window.removeAll { it.timestamp < cutoff }
            window.add(event)
        }

        // Evaluate for threat hypotheses only when a NETWORK_OUTBOUND event is received.
        // RASP-only signals (DCL, ROOT) are accumulated in the window as supporting evidence
        // but do not emit a hypothesis without an accompanying network event.
        if (event.type == "NETWORK_OUTBOUND") {
            evaluateWindow(event.packageName)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun evaluateWindow(packageName: String) {
        val window = mutex.withLock {
            eventWindows[packageName]?.toList() ?: return
        }

        // STIX_THREAT_MATCH has priority over BEHAVIORAL_ANOMALY.
        if (tryEmitStixMatch(packageName, window)) return

        tryEmitBehavioralAnomaly(packageName, window)
    }

    /**
     * Checks whether any NETWORK_OUTBOUND event in [window] targets a live STIX indicator.
     * If a match is found and the computed confidence meets [STIX_THRESHOLD], persists and
     * emits a STIX_THREAT_MATCH hypothesis and returns `true`.
     */
    private suspend fun tryEmitStixMatch(
        packageName: String,
        window: List<RawEvent>
    ): Boolean {
        val networkEvent = window.lastOrNull { it.type == "NETWORK_OUTBOUND" } ?: return false
        val dest = parseDestination(networkEvent.detail)
        val now = System.currentTimeMillis()

        val matched = activeStixIndicators.find {
            it.patternValue.equals(dest, ignoreCase = true) && it.ttlTimestamp > now
        } ?: return false

        val hasDcl = window.any { it.type == "RASP_DCL" }
        val hasRoot = window.any { it.type == "RASP_ROOT" }

        var confidence = 0.60f
        if (hasDcl) confidence += 0.20f
        if (hasRoot) confidence += 0.15f
        confidence = confidence.clampConfidence()

        if (confidence < STIX_THRESHOLD) return false

        val evidenceJson = JSONObject().apply {
            put("packageName", packageName)
            put("stixMatch", matched.patternValue)
            put("sourceFeed", matched.sourceFeed)
            put("hasDcl", hasDcl)
            put("hasRoot", hasRoot)
        }.toString()
        val hypothesis = QuillaHypothesisEntity(
            hypothesisType = "STIX_THREAT_MATCH",
            confidence = confidence,
            summary = "Package $packageName matched STIX threat indicator: ${matched.patternValue}",
            evidenceJson = evidenceJson
        )
        dao.insertHypothesis(hypothesis)
        _threatEvents.emit(hypothesis)
        return true
    }

    /**
     * Computes the behavioral anomaly confidence from signals present in [window].
     * If the score meets [BEHAVIORAL_THRESHOLD], persists and emits a BEHAVIORAL_ANOMALY
     * hypothesis.
     */
    private suspend fun tryEmitBehavioralAnomaly(packageName: String, window: List<RawEvent>) {
        val hasDcl = window.any { it.type == "RASP_DCL" }
        val hasRoot = window.any { it.type == "RASP_ROOT" }
        val hasUntrustedNet = window.any {
            it.type == "NETWORK_OUTBOUND" && it.detail.contains("UNTRUSTED_AP")
        }

        var confidence = 0.40f
        if (hasDcl) confidence += 0.25f
        if (hasRoot) confidence += 0.20f
        if (hasUntrustedNet) confidence += 0.10f

        if (confidence < BEHAVIORAL_THRESHOLD) return

        val evidenceJson = JSONObject().apply {
            put("packageName", packageName)
            put("hasDcl", hasDcl)
            put("hasRoot", hasRoot)
            put("hasUntrustedNet", hasUntrustedNet)
        }.toString()
        val hypothesis = QuillaHypothesisEntity(
            hypothesisType = "BEHAVIORAL_ANOMALY",
            confidence = confidence.clampConfidence(),
            summary = "Behavioral anomaly detected for package $packageName",
            evidenceJson = evidenceJson
        )
        dao.insertHypothesis(hypothesis)
        _threatEvents.emit(hypothesis)
    }

    /**
     * Clamps [this] confidence value to the valid [0.0, 1.0] range.
     */
    private fun Float.clampConfidence() = coerceIn(0.0f, 1.0f)

    /**
     * Extracts the destination domain or IP from a NETWORK_OUTBOUND detail string.
     * Expected format: `"DEST:<value>,<optional-flags>"`.
     */
    private fun parseDestination(detail: String): String {
        val afterDest = detail.substringAfter("DEST:", "")
        return afterDest.substringBefore(",").trim()
    }
}
