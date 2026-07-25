package com.coldboar.coreguard.quilla

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Quilla Correlation & Intelligence Engine.
 *
 * Matches real-time device telemetry against Amnesty International threat
 * intelligence feeds and generates a [QuillaHypothesis] when the combined
 * confidence score reaches [ACTIVATION_THRESHOLD].
 *
 * Thread-safety: [syncThreatIntelligence] and [correlateSignals] may be called
 * from any thread; the active IOC list is protected by an internal lock.
 *
 * @param store       Where generated hypotheses are persisted.
 * @param fetcher     Source of Amnesty threat intelligence; defaults to the
 *                    production [AmnestyThreatIntelFetcher] singleton.
 */
class QuillaCorrelationEngine(
    private val store: QuillaHypothesisStore,
    private val fetcher: () -> List<AmnestyIndicator> = AmnestyThreatIntelFetcher::fetchAmnestyIndicators
) {

    companion object {
        /** Minimum confidence score required to persist a [QuillaHypothesis]. */
        const val ACTIVATION_THRESHOLD = 0.75f
    }

    private val indicatorLock = Any()
    private val activeIndicators = mutableListOf<AmnestyIndicator>()

    /**
     * Replaces the active IOC list with freshly fetched indicators from the
     * configured [fetcher]. Must be called on a background thread.
     */
    fun syncThreatIntelligence() {
        val fetched = fetcher()
        synchronized(indicatorLock) {
            activeIndicators.clear()
            activeIndicators.addAll(fetched)
        }
    }

    /**
     * Loads a pre-built indicator list directly into the engine, bypassing the
     * network fetcher. Intended for testing and offline scenarios.
     */
    fun loadIndicators(indicators: List<AmnestyIndicator>) {
        synchronized(indicatorLock) {
            activeIndicators.clear()
            activeIndicators.addAll(indicators)
        }
    }

    /**
     * Correlates weak local signals from RASP and Network Shield against the
     * active Amnesty threat intelligence indicators.
     *
     * A [QuillaHypothesis] is written to [store] when the accumulated
     * confidence score is at least [ACTIVATION_THRESHOLD].
     *
     * Signal contributions:
     * - IOC domain/IP match        → +0.40
     * - Dynamic code loading (DCL) → +0.25
     * - Root / privilege escalation → +0.20
     * - Untrusted network           → +0.10
     * (Base score starts at 0.50.)
     *
     * @param packageName Observed Android package under analysis.
     * @param rasp        RASP telemetry for the package, or null if unavailable.
     * @param network     Network telemetry for the package, or null if unavailable.
     */
    fun correlateSignals(
        packageName: String,
        rasp: RaspEvent?,
        network: NetworkEvent?
    ) {
        var confidenceScore = 0.50f
        val evidenceList = mutableListOf<String>()

        // 1. Check network traffic against Amnesty IOCs.
        network?.destinationDomainOrIp?.let { domain ->
            val matchingIoc = synchronized(indicatorLock) {
                activeIndicators.find { it.patternValue.equals(domain, ignoreCase = true) }
            }
            if (matchingIoc != null) {
                confidenceScore += 0.40f
                evidenceList.add(
                    "Matched Amnesty International IOC: ${matchingIoc.patternValue} (${matchingIoc.description})"
                )
            }
        }

        // 2. Evaluate RASP behavioural indicators.
        if (rasp?.isDynamicCodeLoaded == true) {
            confidenceScore += 0.25f
            evidenceList.add("Dynamic Code Loading (DCL) detected in package $packageName")
        }

        if (rasp?.isRootDetected == true) {
            confidenceScore += 0.20f
            evidenceList.add("Device root/privilege escalation environment active")
        }

        if (network?.isUntrustedNetwork == true) {
            confidenceScore += 0.10f
            evidenceList.add("Active network connection on untrusted Wi-Fi/Access Point")
        }

        // 3. Persist hypothesis if the activation threshold is met.
        if (confidenceScore >= ACTIVATION_THRESHOLD) {
            val evidenceJson = JSONObject().apply {
                put("packageName", packageName)
                put("confidence", confidenceScore)
                put("reasons", JSONArray(evidenceList))
                put("bytesTransferred", network?.bytesTransferred ?: 0L)
            }.toString()

            store.upsert(
                QuillaHypothesis(
                    id = UUID.randomUUID().toString(),
                    hypothesisType = "AMNESTY_IOC_BEHAVIORAL_MATCH",
                    summary = "Package $packageName triggered high-risk correlation rule " +
                        "matching threat intelligence indicators.",
                    evidenceJson = evidenceJson,
                    confidence = confidenceScore.coerceAtMost(1.0f),
                    status = "ACTIVE"
                )
            )
        }
    }
}
