package com.coldboar.coreguard.quilla

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Quilla Correlation & Intelligence Engine.
 *
 * Matches real-time device telemetry against Amnesty / MVT-style threat
 * intelligence indicators and generates a [QuillaHypothesis] when the combined
 * confidence score reaches [ACTIVATION_THRESHOLD].
 *
 * Also runs [QuillaQuantumCorrelate] — a **classical** quantum-inspired circuit
 * (superposition → entanglement → interference → collapse) labeled with Living
 * Geometry / Enochian gate names. Not a QPU; not operative magick.
 *
 * Domain matching follows MVT conventions: exact host or parent-domain of a
 * subdomain (`evil.com` matches `c2.evil.com`).
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

    @Volatile
    private var lastQuantum: QuillaQuantumCorrelate.CircuitReport? = null

    /** Last quantum-inspired circuit report (classical simulation). */
    fun lastQuantumReport(): QuillaQuantumCorrelate.CircuitReport? = lastQuantum

    /** Count of indicators currently loaded for correlation. */
    fun indicatorCount(): Int = synchronized(indicatorLock) { activeIndicators.size }

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
     * Merges indicators into the active set without clearing existing entries.
     * Deduplicates by lower-cased [AmnestyIndicator.patternValue].
     */
    fun mergeIndicators(indicators: List<AmnestyIndicator>) {
        if (indicators.isEmpty()) return
        synchronized(indicatorLock) {
            val byKey = LinkedHashMap<String, AmnestyIndicator>()
            for (existing in activeIndicators) {
                byKey[existing.patternValue.trim().lowercase()] = existing
            }
            for (incoming in indicators) {
                val key = incoming.patternValue.trim().lowercase()
                if (key.isNotEmpty()) byKey.putIfAbsent(key, incoming)
            }
            activeIndicators.clear()
            activeIndicators.addAll(byKey.values)
        }
    }

    /**
     * Correlates weak local signals from RASP and Network Shield against the
     * active Amnesty / MVT threat intelligence indicators.
     *
     * A [QuillaHypothesis] is written to [store] when the accumulated
     * confidence score is at least [ACTIVATION_THRESHOLD].
     *
     * Signal contributions:
     * - IOC domain/IP/package match → +0.40
     * - Dynamic code loading (DCL)  → +0.25
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
        var iocHit = false
        var packageIocHit = false

        // 1. Check network traffic against Amnesty / MVT IOCs (MVT subdomain rules).
        network?.destinationDomainOrIp?.let { domain ->
            val matchingIoc = synchronized(indicatorLock) {
                findDomainOrIpMatch(domain)
            }
            if (matchingIoc != null) {
                confidenceScore += 0.40f
                iocHit = true
                evidenceList.add(
                    "Matched Amnesty/MVT IOC: ${matchingIoc.patternValue} (${matchingIoc.description})"
                )
            }
        }

        // 1b. Package id against PACKAGE indicators (MVT app:id style).
        val packageIoc = synchronized(indicatorLock) {
            findTypedMatch(packageName, "PACKAGE")
        }
        if (packageIoc != null) {
            confidenceScore += 0.40f
            packageIocHit = true
            evidenceList.add(
                "Matched Amnesty/MVT package IOC: ${packageIoc.patternValue} (${packageIoc.description})"
            )
        }

        // 2. Evaluate RASP behavioral indicators.
        val dcl = rasp?.isDynamicCodeLoaded == true
        if (dcl) {
            confidenceScore += 0.25f
            evidenceList.add("Dynamic Code Loading (DCL) detected in package $packageName")
        }

        val root = rasp?.isRootDetected == true
        if (root) {
            confidenceScore += 0.20f
            evidenceList.add("Device root/privilege escalation environment active")
        }

        val untrusted = network?.isUntrustedNetwork == true
        if (untrusted) {
            confidenceScore += 0.10f
            evidenceList.add("Active network connection on untrusted Wi-Fi/Access Point")
        }

        // 2b. Quantum-inspired classical circuit (magick-labeled gates).
        val classicalClamped = confidenceScore.coerceAtMost(1.0f)
        val quantum = QuillaQuantumCorrelate.runCircuit(
            packageName = packageName,
            iocHit = iocHit,
            packageIocHit = packageIocHit,
            dynamicCode = dcl,
            root = root,
            untrustedNetwork = untrusted,
            classicalConfidence = classicalClamped
        )
        lastQuantum = quantum
        evidenceList.add("Quantum circuit: ${quantum.seal}")
        if (quantum.entangledPairs > 0) {
            evidenceList.add(
                "Entangled evidence pairs=${quantum.entangledPairs} · interference=${"%.2f".format(quantum.interferenceGain)}"
            )
        }

        // Hypothesis confidence stays on the classical ladder when it already
        // clears the threshold (stable API / tests). Quantum blend may lift
        // borderline cases only when the circuit collapses on real evidence.
        val hardEvidence = iocHit || packageIocHit || dcl || root
        val finalConfidence = when {
            classicalClamped >= ACTIVATION_THRESHOLD -> classicalClamped
            quantum.collapsed && hardEvidence ->
                maxOf(classicalClamped, quantum.classicalBlend).coerceAtMost(1.0f)
            else -> classicalClamped
        }

        // 3. Persist hypothesis if classical or quantum collapse threshold is met.
        val shouldActivate = finalConfidence >= ACTIVATION_THRESHOLD ||
            (quantum.collapsed && hardEvidence)
        if (shouldActivate) {
            val evidenceJson = JSONObject().apply {
                put("packageName", packageName)
                put("confidence", finalConfidence)
                put("classicalConfidence", classicalClamped)
                put("quantumCollapse", quantum.collapseProbability)
                put("quantumSeal", quantum.seal)
                put("quantumGates", JSONArray(quantum.gatePath))
                put("entangledPairs", quantum.entangledPairs)
                put("reasons", JSONArray(evidenceList))
                put("bytesTransferred", network?.bytesTransferred ?: 0L)
                put("correlator", "classical+quantum_inspired")
            }.toString()

            store.upsert(
                QuillaHypothesis(
                    id = UUID.randomUUID().toString(),
                    hypothesisType = "AMNESTY_IOC_BEHAVIORAL_MATCH",
                    summary = buildString {
                        append("Package $packageName triggered high-risk correlation ")
                        append("(classical=${"%.2f".format(classicalClamped)}, ")
                        append("quantumP=${"%.2f".format(quantum.collapseProbability)}")
                        if (quantum.collapsed) append(", collapsed")
                        append(") matching threat intelligence indicators.")
                    },
                    evidenceJson = evidenceJson,
                    confidence = finalConfidence,
                    status = "ACTIVE"
                )
            )
        }
    }

    private fun findTypedMatch(value: String, type: String): AmnestyIndicator? {
        val needle = value.trim().lowercase()
        if (needle.isEmpty()) return null
        return activeIndicators.firstOrNull {
            it.indicatorType.equals(type, ignoreCase = true) &&
                it.patternValue.equals(needle, ignoreCase = true)
        }
    }

    /** MVT-style: exact host or parent domain of a subdomain; also exact IP. */
    private fun findDomainOrIpMatch(raw: String): AmnestyIndicator? {
        val host = normaliseHost(raw) ?: return null
        return activeIndicators.firstOrNull { ioc ->
            val type = ioc.indicatorType.uppercase()
            if (type != "DOMAIN" && type != "IP" && type != "GENERIC" && type != "URL") {
                return@firstOrNull false
            }
            val value = ioc.patternValue.trim().lowercase()
            if (value.isEmpty()) return@firstOrNull false
            host == value || host.endsWith(".$value") ||
                (type == "URL" && host in value)
        }
    }

    private fun normaliseHost(input: String): String? {
        var host = input.trim().lowercase()
        if (host.isEmpty()) return null
        val schemeIdx = host.indexOf("://")
        if (schemeIdx >= 0) host = host.substring(schemeIdx + 3)
        host = host.substringAfterLast('@')
        host = host.substringBefore('/').substringBefore('?')
        host = host.substringBefore(':')
        return host.ifEmpty { null }
    }
}
