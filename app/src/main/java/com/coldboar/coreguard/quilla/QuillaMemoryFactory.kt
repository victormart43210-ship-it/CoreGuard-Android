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
    private var cachedResearch = QuillaResearchSnapshot()

    fun hypothesisStore(): QuillaHypothesisStore = sharedStore

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
     */
    fun syncResearch(): QuillaResearchSnapshot {
        val indicators = runCatching { AmnestyThreatIntelFetcher.fetchAmnestyIndicators() }
            .getOrDefault(emptyList())
        cachedResearch = QuillaResearchSnapshot(
            indicatorCount = indicators.size,
            synced = true,
            sourceLabel = "Amnesty STIX2"
        )
        // Keep correlation engine warm with the same feed when available.
        if (indicators.isNotEmpty()) {
            QuillaCorrelationEngine(sharedStore).loadIndicators(indicators)
        }
        return cachedResearch
    }
}
