package com.coldboar.coreguard.quilla

import android.content.Context
import com.coldboar.coreguard.mvt.IocRepository
import com.coldboar.coreguard.mvt.LastScan
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ShieldState
import com.coreguard.security.telemetry.RiskSeverity
import com.coreguard.security.telemetry.TelemetryBridge

/**
 * Builds [QuillaMemorySnapshot] / [QuillaResearchSnapshot] from live CoreGuard state.
 *
 * Threat-intel honesty:
 * - Research sync uses [QuillaIntelNetwork] (Amnesty/MVT STIX + CISA/MISP web intel).
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
        val telemetry = TelemetryBridge.ringBuffer().snapshot()
        val highTelemetry = telemetry.any {
            it.delta.severity == RiskSeverity.HIGH || it.delta.severity == RiskSeverity.CRITICAL
        }
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
            mvtIocInventoryCount = iocCount,
            correlatorIndicatorCount = sharedCorrelation.indicatorCount(),
            telemetryDeltaCount = telemetry.size,
            telemetryHighSeverity = highTelemetry
        )
    }

    fun cachedResearch(): QuillaResearchSnapshot = cachedResearch

    /**
     * Synchronous intel sync for Research module via [QuillaIntelNetwork].
     * Call from a background dispatcher.
     *
     * Honesty rules:
     * - [QuillaResearchSnapshot.synced] is true only when the network sync reports success.
     * - Failure leaves [QuillaResearchSnapshot.syncFailed] true and does not claim success.
     * - This feed feeds Quilla Research / correlation only — it does **not** refresh
     *   Nemesis Scanner signatures.
     */
    fun syncResearch(context: Context): QuillaResearchSnapshot {
        val network = QuillaIntelNetwork.syncAll(context)
        localIntelLoaded = true
        cachedResearch = QuillaResearchSnapshot(
            indicatorCount = network.mergedCorrelatorCount,
            remoteIndicatorCount = network.stixIndicatorCount,
            mvtOnDeviceCount = network.onDeviceMvtCount,
            webKnowledgeCount = network.webKnowledgeCount,
            feedNotes = network.feedNotes,
            synced = network.synced && !network.syncFailed,
            syncFailed = network.syncFailed,
            sourceLabel = network.sourceLabel
        )
        return cachedResearch
    }
}
