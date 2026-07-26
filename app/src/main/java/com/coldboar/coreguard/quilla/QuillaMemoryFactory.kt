package com.coldboar.coreguard.quilla

import android.content.Context
import com.coldboar.coreguard.mvt.IocRepository
import com.coldboar.coreguard.mvt.LastScan
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ShieldState

/**
 * Builds [QuillaMemorySnapshot] / [QuillaResearchSnapshot] from live CoreGuard state.
 *
 * Threat-intel honesty:
 * - Research sync pulls public Amnesty / MVT STIX into Quilla's correlator.
 * - On-device MVT IOCs from [IocRepository] are also merged for correlation.
 * - Neither path writes Scanner signatures for free users; Premium Nemesis
 *   refresh remains [com.coldboar.coreguard.mvt.IocFeedFetcher].
 */
object QuillaMemoryFactory {

    private val sharedStore = QuillaHypothesisStore()
    private val sharedCorrelation = QuillaCorrelationEngine(sharedStore)
    private var cachedResearch = QuillaResearchSnapshot()

    @Volatile
    private var localIntelLoaded = false

    fun hypothesisStore(): QuillaHypothesisStore = sharedStore

    fun correlationEngine(): QuillaCorrelationEngine = sharedCorrelation

    /** Call after Premium Nemesis feed write so Quilla re-reads inventory. */
    fun invalidateLocalIntel() {
        localIntelLoaded = false
    }

    /**
     * Lazily merges on-device MVT/Nemesis IOCs into the Quilla correlator.
     * Safe to call from scan / shield paths; no network I/O.
     */
    fun ensureLocalIntel(context: Context) {
        if (localIntelLoaded) return
        synchronized(this) {
            if (localIntelLoaded) return
            val onDevice = QuillaIocBridge.fromMvtIndicators(IocRepository.indicators(context))
            sharedCorrelation.mergeIndicators(onDevice)
            localIntelLoaded = true
        }
    }

    fun memorySnapshot(context: Context): QuillaMemorySnapshot {
        ensureLocalIntel(context)
        val history = runCatching { ScanHistoryStore.load(context) }.getOrDefault(emptyList())
        val last = LastScan.report
        val newest = history.firstOrNull()
        val iocCount = runCatching { IocRepository.indicators(context).size }.getOrDefault(0)
        return QuillaMemorySnapshot(
            lastScanVerdict = last?.verdict?.name ?: newest?.verdict?.name,
            lastScanDetections = last?.detections?.size ?: newest?.detectionCount,
            lastScanDetectionTitles = last?.detections?.map { it.title }?.take(5).orEmpty(),
            historyCount = history.size,
            shieldActive = ShieldState.isActive,
            shieldBlocked = ShieldState.totalBlocked,
            lastBlockedDomain = ShieldState.lastBlockedDomain,
            activeHypotheses = sharedStore.all()
                .filter { it.status.equals("ACTIVE", ignoreCase = true) }
                .map { it.summary },
            mvtIocInventoryCount = iocCount
        )
    }

    fun cachedResearch(): QuillaResearchSnapshot = cachedResearch

    /**
     * Synchronous intel sync for Research module. Call from a background dispatcher.
     *
     * Honesty rules:
     * - [QuillaResearchSnapshot.synced] is true only when the remote fetch completed without error.
     * - Empty feed is success with zero remote indicators (not a fake "loaded" failure mask).
     * - Failure leaves [QuillaResearchSnapshot.syncFailed] true and does not claim success.
     * - On-device MVT IOCs are still merged into the correlator on failure when available.
     * - This feed feeds Quilla Research only — it does **not** refresh Nemesis Scanner signatures.
     */
    fun syncResearch(context: Context): QuillaResearchSnapshot {
        val onDevice = runCatching {
            QuillaIocBridge.fromMvtIndicators(IocRepository.indicators(context))
        }.getOrDefault(emptyList())

        val remoteResult = runCatching { AmnestyThreatIntelFetcher.fetchPublicResearchIndicators() }
        return if (remoteResult.isSuccess) {
            val remote = remoteResult.getOrDefault(emptyList())
            val merged = QuillaIocBridge.mergeUnique(remote, onDevice)
            sharedCorrelation.loadIndicators(merged)
            localIntelLoaded = true
            cachedResearch = QuillaResearchSnapshot(
                indicatorCount = merged.size,
                remoteIndicatorCount = remote.size,
                mvtOnDeviceCount = onDevice.size,
                synced = true,
                syncFailed = false,
                sourceLabel = "Amnesty/MVT public STIX + on-device IOCs"
            )
            cachedResearch
        } else {
            if (onDevice.isNotEmpty()) {
                sharedCorrelation.mergeIndicators(onDevice)
                localIntelLoaded = true
            }
            cachedResearch = QuillaResearchSnapshot(
                indicatorCount = maxOf(cachedResearch.indicatorCount, onDevice.size),
                remoteIndicatorCount = cachedResearch.remoteIndicatorCount,
                mvtOnDeviceCount = onDevice.size,
                synced = false,
                syncFailed = true,
                sourceLabel = cachedResearch.sourceLabel
            )
            cachedResearch
        }
    }
}
