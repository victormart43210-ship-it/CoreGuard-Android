package com.coldboar.coreguard.quilla

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Sliding-window threat correlation engine.
 *
 * Ingests [RawEvent] telemetry and correlates events that fall within a configurable
 * time window against a live [StixIndicator] list. A [QuillaHypothesis] is produced
 * and persisted to [store] when the accumulated confidence score reaches
 * [ACTIVATION_THRESHOLD].
 *
 * Results are also emitted on [hypothesisFlow] so callers can react without polling
 * the store.
 *
 * Thread-safety: [syncThreatIntelligence] is synchronous and must be called on a
 * background thread. [ingest] is a suspend function safe to call from any coroutine.
 *
 * @param store          Where generated [QuillaHypothesis] records are persisted.
 * @param fetcher        Source of multi-feed STIX threat intelligence; defaults to
 *                       [MultiSourceStixFetcher.Default].
 * @param windowMs       Sliding-window duration in milliseconds; defaults to 5 minutes.
 */
class SlidingWindowCorrelationEngine(
    private val store: QuillaHypothesisStore,
    private val fetcher: MultiSourceStixFetcher = MultiSourceStixFetcher.Default,
    private val windowMs: Long = 5L * 60 * 1000
) {

    companion object {
        /** Minimum confidence score required to persist a [QuillaHypothesis]. */
        const val ACTIVATION_THRESHOLD = 0.75f

        // Signal score contributions
        private const val SCORE_IOC_MATCH = 0.40f
        private const val SCORE_DYNAMIC_CODE = 0.25f
        private const val SCORE_ROOT = 0.20f
        private const val SCORE_UNTRUSTED_NET = 0.10f
        private const val SCORE_BASE = 0.50f
    }

    // Per-package rolling event cache, pruned on every ingest call.
    private val eventCache = ConcurrentHashMap<String, MutableList<RawEvent>>()
    private val cacheMutex = Mutex()

    // Active STIX indicator list updated by syncThreatIntelligence().
    private val indicatorLock = Any()
    private val activeIndicators = mutableListOf<StixIndicator>()

    // Hot flow through which generated hypotheses are broadcast.
    private val _hypothesisFlow = MutableSharedFlow<QuillaHypothesis>(extraBufferCapacity = 64)

    /**
     * Hot [SharedFlow] that emits each [QuillaHypothesis] as it is created.
     *
     * Subscribe before calling [ingest] to avoid missing early emissions.
     */
    val hypothesisFlow: SharedFlow<QuillaHypothesis> = _hypothesisFlow.asSharedFlow()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Replaces the active indicator list with freshly fetched records from all
     * configured STIX feeds. Must be called on a background thread.
     */
    fun syncThreatIntelligence() {
        val fetched = fetcher.fetchAllSources()
        synchronized(indicatorLock) {
            activeIndicators.clear()
            activeIndicators.addAll(fetched)
        }
    }

    /**
     * Loads a pre-built indicator list, bypassing the network fetcher.
     * Intended for offline scenarios and unit tests.
     */
    fun loadIndicators(indicators: List<StixIndicator>) {
        synchronized(indicatorLock) {
            activeIndicators.clear()
            activeIndicators.addAll(indicators)
        }
    }

    /**
     * Ingests a single [RawEvent], prunes stale events outside the sliding window,
     * then runs correlation for the affected package.
     *
     * If the resulting confidence score meets [ACTIVATION_THRESHOLD] a
     * [QuillaHypothesis] is persisted to [store] and emitted on [hypothesisFlow].
     */
    suspend fun ingest(event: RawEvent) {
        val now = System.currentTimeMillis()
        cacheMutex.withLock {
            val events = eventCache.getOrPut(event.packageName) { mutableListOf() }
            events.add(event)
            events.removeAll { now - it.timestamp > windowMs }
        }
        correlateWindow(event.packageName, now)
    }

    /**
     * Returns a snapshot of all events currently held in the sliding window for
     * [packageName], or an empty list if no events are cached.
     */
    suspend fun windowSnapshot(packageName: String): List<RawEvent> =
        cacheMutex.withLock { eventCache[packageName]?.toList() ?: emptyList() }

    /**
     * Clears all cached events. Useful for resetting state in tests or on sign-out.
     */
    suspend fun clearCache() {
        cacheMutex.withLock { eventCache.clear() }
    }

    // -------------------------------------------------------------------------
    // Internal correlation logic
    // -------------------------------------------------------------------------

    private suspend fun correlateWindow(packageName: String, now: Long) {
        val windowEvents = cacheMutex.withLock {
            eventCache[packageName]?.toList() ?: return
        }
        val indicators = synchronized(indicatorLock) { activeIndicators.toList() }

        var score = SCORE_BASE
        val reasons = mutableListOf<String>()
        var matchedIoc: StixIndicator? = null
        var bytesTransferred = 0L
        var untrustedNetwork = false
        val raspSignals = mutableListOf<String>()

        for (event in windowEvents) {
            when (event.type) {
                "RASP_DCL" -> {
                    score += SCORE_DYNAMIC_CODE
                    val signal = "Dynamic Code Loading detected: ${event.detail}"
                    reasons.add(signal)
                    raspSignals.add(signal)
                }
                "RASP_ROOT" -> {
                    score += SCORE_ROOT
                    val signal = "Root/privilege escalation detected: ${event.detail}"
                    reasons.add(signal)
                    raspSignals.add(signal)
                }
                "NETWORK_OUTBOUND" -> {
                    // Attempt to match the destination against active IOCs.
                    val ioc = indicators.find {
                        it.patternValue.equals(event.detail, ignoreCase = true)
                    }
                    if (ioc != null && matchedIoc == null) {
                        score += SCORE_IOC_MATCH
                        reasons.add(
                            "Matched IOC [${ioc.sourceFeed}] ${ioc.indicatorType}: ${ioc.patternValue}"
                        )
                        matchedIoc = ioc
                    }
                    // Detail format: "<destination>|<bytes>" — bytes are optional.
                    val parts = event.detail.split("|")
                    if (parts.size >= 2) {
                        bytesTransferred += parts[1].toLongOrNull() ?: 0L
                    }
                }
                "NETWORK_UNTRUSTED" -> {
                    if (!untrustedNetwork) {
                        score += SCORE_UNTRUSTED_NET
                        reasons.add("Untrusted network active: ${event.detail}")
                        untrustedNetwork = true
                    }
                }
            }
        }

        if (score < ACTIVATION_THRESHOLD) return

        val payload = EvidencePayload(
            packageName = packageName,
            matchedIoc = matchedIoc?.patternValue,
            iocSource = matchedIoc?.sourceFeed,
            raspSignals = raspSignals,
            bytesTransferred = bytesTransferred,
            untrustedNetwork = untrustedNetwork,
            timestamp = now
        )

        val evidenceJson = buildEvidenceJson(payload, score, reasons)

        val hypothesis = QuillaHypothesis(
            id = UUID.randomUUID().toString(),
            hypothesisType = "SLIDING_WINDOW_BEHAVIORAL_MATCH",
            summary = "Package $packageName triggered sliding-window correlation rule " +
                "with ${windowEvents.size} event(s) in the ${windowMs / 60_000}min window.",
            evidenceJson = evidenceJson,
            confidence = score.coerceAtMost(1.0f),
            status = "ACTIVE"
        )

        store.upsert(hypothesis)
        _hypothesisFlow.tryEmit(hypothesis)
    }

    private fun buildEvidenceJson(
        payload: EvidencePayload,
        score: Float,
        reasons: List<String>
    ): String = JSONObject().apply {
        put("packageName", payload.packageName)
        put("confidence", score)
        put("reasons", JSONArray(reasons))
        put("matchedIoc", payload.matchedIoc)
        put("iocSource", payload.iocSource)
        put("raspSignals", JSONArray(payload.raspSignals))
        put("bytesTransferred", payload.bytesTransferred)
        put("untrustedNetwork", payload.untrustedNetwork)
        put("timestamp", payload.timestamp)
    }.toString()
}
