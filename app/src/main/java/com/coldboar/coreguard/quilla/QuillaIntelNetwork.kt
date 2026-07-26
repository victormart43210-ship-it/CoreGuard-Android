package com.coldboar.coreguard.quilla

import android.content.Context
import android.util.Log
import com.coldboar.coreguard.CoreGuardApplication
import com.coldboar.coreguard.mvt.IocRepository
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import com.quilla.intelligence.sdk.engine.SlidingWindowCorrelationEngine
import com.quilla.intelligence.sdk.intel.MultiSourceStixFetcher
import com.quilla.intelligence.sdk.intel.PublicMultiSourceStixFetcher
import com.quilla.intelligence.sdk.model.StixIndicator

/**
 * Quilla Intelligence Network — orchestrates public web threat intel into the
 * on-device correlator, sliding-window engine, and Cyber Codex.
 *
 * Sources (HTTPS, optional):
 * - Amnesty Tech / MVT campaign STIX2 IOC bundles
 * - Open stalkerware STIX2
 * - CISA Known Exploited Vulnerabilities (Android/mobile filter)
 * - MISP Android malware galaxy (defensive family briefs)
 *
 * Honesty:
 * - Not a live continuous feed; each sync is an optional pull.
 * - Does **not** write Nemesis Scanner signatures ([com.coldboar.coreguard.mvt.IocFeedFetcher]).
 * - Content is defensive education / detection correlation only.
 */
object QuillaIntelNetwork {

    private const val TAG = "QuillaIntelNetwork"

    @Volatile
    private var slidingEngine: SlidingWindowCorrelationEngine? = null

    @Volatile
    private var lastSync = QuillaIntelNetworkSnapshot()

    fun lastSnapshot(): QuillaIntelNetworkSnapshot = lastSync

    fun slidingWindowEngine(): SlidingWindowCorrelationEngine? = slidingEngine

    /**
     * Full network sync. Call from a background dispatcher.
     *
     * @param stixFetcher injectable for tests; defaults to [PublicMultiSourceStixFetcher].
     */
    fun syncAll(
        context: Context,
        stixFetcher: MultiSourceStixFetcher = PublicMultiSourceStixFetcher(),
        webFetcher: () -> QuillaWebSecurityIntelFetcher.WebIntelResult =
            QuillaWebSecurityIntelFetcher::fetchDefensiveKnowledge
    ): QuillaIntelNetworkSnapshot {
        val feedNotes = mutableListOf<String>()
        var stixCount = 0
        var stixFailed = false
        var knowledgeCount = 0
        var knowledgeFailed = false

        val stixResult = runCatching { stixFetcher.fetchAllSources() }
        val stix = if (stixResult.isSuccess) {
            val list = stixResult.getOrDefault(emptyList())
            stixCount = list.size
            feedNotes += "STIX multi-source: ${list.size} indicators"
            list
        } else {
            stixFailed = true
            feedNotes += "STIX multi-source failed: ${stixResult.exceptionOrNull()?.message}"
            emptyList()
        }

        val onDevice = runCatching {
            QuillaIocBridge.fromMvtIndicators(IocRepository.indicators(context))
        }.getOrDefault(emptyList())
        feedNotes += "On-device MVT inventory: ${onDevice.size}"

        val fromStix = stix.map { it.toAmnestyIndicator() }
        val merged = QuillaIocBridge.mergeUnique(fromStix, onDevice)
        QuillaMemoryFactory.correlationEngine().loadIndicators(merged)

        // Warm / sync sliding-window engine when Room is available.
        runCatching {
            val app = CoreGuardApplication.get()
            if (app != null) {
                val engine = slidingEngine ?: SlidingWindowCorrelationEngine(
                    app.quillaDatabase.quillaLearningDao(),
                    stixFetcher
                ).also { slidingEngine = it }
                engine.syncThreatFeeds()
                feedNotes += "Sliding-window STIX sync: ${stixCount}"
            }
        }.onFailure {
            Log.w(TAG, "Sliding-window sync skipped: ${it.message}")
        }

        val web = runCatching { webFetcher() }
        if (web.isSuccess) {
            val result = web.getOrThrow()
            if (result.entries.isNotEmpty()) {
                CyberKnowledgeBase.mergeEntries(result.entries)
                knowledgeCount = result.entries.size
            }
            feedNotes += result.sourcesOk
            if (result.sourcesFailed.isNotEmpty()) {
                feedNotes += result.sourcesFailed.map { "failed:$it" }
                // Partial web failure is not a total research failure when STIX worked.
                if (result.sourcesOk.isEmpty()) knowledgeFailed = true
            }
        } else {
            knowledgeFailed = true
            feedNotes += "Web security intel failed: ${web.exceptionOrNull()?.message}"
        }

        // Infinity: harden angel choir + swarm on the merged malware/vuln corpus (uncapped).
        QuillaInfinityTrainer.restoreLite(context)
        val training = QuillaInfinityTrainer.trainFromCodex(
            context = context,
            network = QuillaIntelNetworkSnapshot(
                stixIndicatorCount = stixCount,
                mergedCorrelatorCount = merged.size,
                onDeviceMvtCount = onDevice.size,
                webKnowledgeCount = knowledgeCount,
                feedNotes = feedNotes.toList(),
                synced = true,
                syncFailed = false,
                sourceLabel = "Quilla Intel Network"
            ),
            correlatorIndicatorCount = merged.size
        )
        feedNotes += training.summaryLine()

        val snapshot = QuillaIntelNetworkSnapshot(
            stixIndicatorCount = stixCount,
            mergedCorrelatorCount = merged.size,
            onDeviceMvtCount = onDevice.size,
            webKnowledgeCount = knowledgeCount,
            feedNotes = feedNotes.toList(),
            synced = !stixFailed || stixCount > 0 || knowledgeCount > 0,
            syncFailed = stixFailed && knowledgeFailed && merged.isEmpty(),
            sourceLabel = "Quilla Infinity Intel (Amnesty/MVT STIX · CISA KEV · MISP/Malpedia · on-device IOCs)",
            infinityGeneration = training.generation,
            infinityMalwareStudied = training.malwareEntriesStudied,
            infinityVulnStudied = training.vulnerabilityEntriesStudied,
            infinityCodexDepth = training.totalCodexEntries
        )
        lastSync = snapshot
        return snapshot
    }

    private fun StixIndicator.toAmnestyIndicator(): AmnestyIndicator =
        AmnestyIndicator(
            id = id,
            indicatorType = indicatorType,
            patternValue = patternValue,
            description = "$description [$sourceFeed]"
        )
}

data class QuillaIntelNetworkSnapshot(
    val stixIndicatorCount: Int = 0,
    val mergedCorrelatorCount: Int = 0,
    val onDeviceMvtCount: Int = 0,
    val webKnowledgeCount: Int = 0,
    val feedNotes: List<String> = emptyList(),
    val synced: Boolean = false,
    val syncFailed: Boolean = false,
    val sourceLabel: String = "Quilla Intel Network",
    val infinityGeneration: Int = 0,
    val infinityMalwareStudied: Int = 0,
    val infinityVulnStudied: Int = 0,
    val infinityCodexDepth: Int = 0
)
