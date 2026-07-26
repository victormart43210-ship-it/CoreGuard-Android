package com.coldboar.coreguard.quilla

import android.content.Context
import com.coldboar.coreguard.mvt.LastScan
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ShieldState

/**
 * Builds [QuillaMemorySnapshot] / [QuillaResearchSnapshot] from live CoreGuard state.
 */
object QuillaMemoryFactory {

    private val sharedStore = QuillaHypothesisStore()
    private val sharedCorrelation = QuillaCorrelationEngine(sharedStore)
    private var cachedResearch = QuillaResearchSnapshot()

    fun hypothesisStore(): QuillaHypothesisStore = sharedStore

    fun correlationEngine(): QuillaCorrelationEngine = sharedCorrelation

    fun memorySnapshot(context: Context): QuillaMemorySnapshot {
        val history = runCatching { ScanHistoryStore.load(context) }.getOrDefault(emptyList())
        val last = LastScan.report
        val newest = history.firstOrNull()
        return QuillaMemorySnapshot(
            lastScanVerdict = last?.verdict?.name ?: newest?.verdict?.name,
            lastScanDetections = last?.detections?.size ?: newest?.detectionCount,
            historyCount = history.size,
            shieldActive = ShieldState.isActive,
            shieldBlocked = ShieldState.totalBlocked,
            lastBlockedDomain = ShieldState.lastBlockedDomain,
            activeHypotheses = sharedStore.all()
                .filter { it.status.equals("ACTIVE", ignoreCase = true) }
                .map { it.summary }
        )
    }

    fun cachedResearch(): QuillaResearchSnapshot = cachedResearch

    /**
     * Synchronous intel sync for Research module. Call from a background dispatcher.
     *
     * Honesty rules:
     * - [QuillaResearchSnapshot.synced] is true only when the fetch completed without error.
     * - Empty feed is success with zero indicators (not a fake "loaded" failure mask).
     * - Failure leaves [QuillaResearchSnapshot.syncFailed] true and does not claim success.
     * - This feed feeds Quilla Research only — it does **not** refresh Nemesis Scanner signatures.
     */
    fun syncResearch(): QuillaResearchSnapshot {
        val result = runCatching { AmnestyThreatIntelFetcher.fetchAmnestyIndicators() }
        return if (result.isSuccess) {
            val indicators = result.getOrDefault(emptyList())
            cachedResearch = QuillaResearchSnapshot(
                indicatorCount = indicators.size,
                synced = true,
                syncFailed = false,
                sourceLabel = "Amnesty STIX2 (campaign archive)"
            )
            if (indicators.isNotEmpty()) {
                sharedCorrelation.loadIndicators(indicators)
            }
            cachedResearch
        } else {
            cachedResearch = QuillaResearchSnapshot(
                indicatorCount = cachedResearch.indicatorCount,
                synced = false,
                syncFailed = true,
                sourceLabel = cachedResearch.sourceLabel
            )
            cachedResearch
        }
    }
}
