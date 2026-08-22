package com.coldboar.coreguard.quilla

import android.content.Context
import android.util.Log
import com.coldboar.coreguard.CoreGuardApplication
import com.coldboar.coreguard.knowledge.SharedThreatKnowledgeRepository
import com.coldboar.coreguard.mvt.IocRepository
import com.quilla.intelligence.sdk.engine.SlidingWindowCorrelationEngine
import com.quilla.intelligence.sdk.intel.MultiSourceStixFetcher
import com.quilla.intelligence.sdk.intel.PublicMultiSourceStixFetcher
import com.quilla.intelligence.sdk.intel.StixFetchReport
import com.quilla.intelligence.sdk.intel.StixSourceResult
import com.quilla.intelligence.sdk.model.StixIndicator

/**
 * Quilla Intelligence Network — orchestrates public web threat intel into the
 * on-device correlator, sliding-window engine, and Cyber Codex.
 *
 * Honesty:
 * - [QuillaIntelNetworkSnapshot.synced] is true only when network STIX and/or
 *   web intelligence actually verified successfully — never because local /
 *   fallback IOCs exist.
 * - Zero verified STIX sources ⇒ STIX UNAVAILABLE (not empty success).
 */
object QuillaIntelNetwork {

    private const val TAG = "QuillaIntelNetwork"

    @Volatile
    private var slidingEngine: SlidingWindowCorrelationEngine? = null

    @Volatile
    private var lastSync = QuillaIntelNetworkSnapshot()

    fun lastSnapshot(): QuillaIntelNetworkSnapshot = lastSync

    fun slidingWindowEngine(): SlidingWindowCorrelationEngine? = slidingEngine

    fun syncAll(
        context: Context,
        stixFetcher: MultiSourceStixFetcher = PublicMultiSourceStixFetcher(),
        webFetcher: () -> QuillaWebSecurityIntelFetcher.WebIntelResult =
            QuillaWebSecurityIntelFetcher::fetchDefensiveKnowledge,
        curatedIntelResult: QuillaCuratedIntelFetcher.FetchResult? = null,
    ): QuillaIntelNetworkSnapshot {
        val feedNotes = mutableListOf<String>()

        val stixReport = runCatching { stixFetcher.fetchReport() }
            .getOrElse { e ->
                StixFetchReport.unavailable(e.message ?: "STIX fetch threw")
            }
        val stix = stixReport.indicators
        val stixCount = stix.size
        val stixVerifiedOk = stixReport.verifiedSourceCount > 0 && !stixReport.allUnavailable
        for (source in stixReport.sourceResults) {
            if (source.success) {
                feedNotes += "STIX OK ${source.name}: ${source.indicators.size} indicators (${source.status})"
            } else {
                feedNotes += "STIX ${source.status} ${source.name}: ${source.failureReason ?: "failed"}"
            }
        }
        if (!stixVerifiedOk) {
            feedNotes += "STIX: UNAVAILABLE — zero verified configured feeds"
        }

        val onDevice = runCatching {
            QuillaIocBridge.fromMvtIndicators(IocRepository.indicators(context))
        }.getOrDefault(emptyList())
        feedNotes += "On-device MVT inventory: ${onDevice.size} (local; not network-sync proof)"

        val fromStix = stix.map { it.toAmnestyIndicator() }
        val merged = QuillaIocBridge.mergeUnique(fromStix, onDevice)
        QuillaMemoryModule.correlationEngine().loadIndicators(merged)

        runCatching {
            val app = CoreGuardApplication.get()
            if (app != null) {
                val engine = slidingEngine ?: SlidingWindowCorrelationEngine(
                    app.quillaDatabase.quillaLearningDao(),
                    stixFetcher
                ).also { slidingEngine = it }
                engine.syncThreatFeeds()
                feedNotes += "Sliding-window STIX sync attempted (verifiedSources=${stixReport.verifiedSourceCount})"
            }
        }.onFailure {
            Log.w(TAG, "Sliding-window sync skipped: ${it.message}")
        }

        var knowledgeCount = 0
        var knowledgeOk = false
        val web = runCatching { webFetcher() }
        if (web.isSuccess) {
            val result = web.getOrThrow()
            if (result.entries.isNotEmpty()) {
                SharedThreatKnowledgeRepository.mergeAnkiKnowledge(result.entries)
                knowledgeCount = result.entries.size
            }
            feedNotes += result.sourcesOk
            if (result.sourcesFailed.isNotEmpty()) {
                feedNotes += result.sourcesFailed.map { "failed:$it" }
            }
            knowledgeOk = result.sourcesOk.isNotEmpty()
            if (result.sourcesFailed.any {
                    it.contains("digest", ignoreCase = true) ||
                        it.contains("SHA-256", ignoreCase = true) ||
                        it.contains("integrity", ignoreCase = true) ||
                        it.contains("UNAVAILABLE", ignoreCase = true)
                }
            ) {
                feedNotes += "Web intel digest drift ⇒ UNAVAILABLE (pin refresh required; never auto-learn)"
            }
        } else {
            feedNotes += "Web security intel failed: ${web.exceptionOrNull()?.message}"
        }

        var crawlerEntryCount = 0
        var crawlerSigValid = false
        var crawlerSourceLabel = ""
        val crawlerWarnings = mutableListOf<String>()
        if (curatedIntelResult != null) {
            if (curatedIntelResult.signatureValid && curatedIntelResult.entries.isNotEmpty()) {
                SharedThreatKnowledgeRepository.mergeCrawlerKnowledge(curatedIntelResult.entries)
                crawlerEntryCount = curatedIntelResult.entries.size
                crawlerSigValid = true
                crawlerSourceLabel = curatedIntelResult.sourceLabel
                feedNotes += "Curated crawler bundle: ${crawlerEntryCount} entries"
            } else {
                feedNotes += "Curated crawler bundle: skipped (${curatedIntelResult.failureReason.take(80)})"
            }
            crawlerWarnings += curatedIntelResult.warnings
        }

        // Network sync truth: local/fallback IOCs never prove synchronized.
        val synced = stixVerifiedOk || knowledgeOk
        val syncFailed = !synced

        val snapshot = QuillaIntelNetworkSnapshot(
            stixIndicatorCount = stixCount,
            mergedCorrelatorCount = merged.size,
            onDeviceMvtCount = onDevice.size,
            webKnowledgeCount = knowledgeCount,
            feedNotes = feedNotes.toList(),
            synced = synced,
            syncFailed = syncFailed,
            sourceLabel = when {
                synced && stixVerifiedOk && knowledgeOk ->
                    "Quilla Intel Network (STIX verified + web intel)"
                synced && stixVerifiedOk ->
                    "Quilla Intel Network (STIX verified)"
                synced && knowledgeOk ->
                    "Quilla Intel Network (web intel only; STIX UNAVAILABLE)"
                else ->
                    "Quilla Intel Network (UNAVAILABLE — sync failed)"
            },
            stixStatus = if (stixVerifiedOk) StixSourceResult.STATUS_VERIFIED else StixSourceResult.STATUS_UNAVAILABLE,
            stixVerifiedSourceCount = stixReport.verifiedSourceCount,
            stixFailedSourceCount = stixReport.failedSourceCount,
            crawlerEntryCount = crawlerEntryCount,
            crawlerSignatureValid = crawlerSigValid,
            crawlerSourceLabel = crawlerSourceLabel,
            crawlerWarnings = crawlerWarnings.toList(),
        )

        QuillaInfinityTrainer.restoreLite(context)
        val training = QuillaInfinityTrainer.trainFromCodex(
            context = context,
            network = snapshot,
            correlatorIndicatorCount = merged.size
        )
        val finalNotes = feedNotes.toMutableList().also { it += training.summaryLine() }
        val finalSnapshot = snapshot.copy(
            feedNotes = finalNotes.toList(),
            infinityGeneration = training.generation,
            infinityMalwareStudied = training.malwareEntriesStudied,
            infinityVulnStudied = training.vulnerabilityEntriesStudied,
            infinityCodexDepth = training.totalCodexEntries,
        )
        lastSync = finalSnapshot
        return finalSnapshot
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
    val stixStatus: String = StixSourceResult.STATUS_UNAVAILABLE,
    val stixVerifiedSourceCount: Int = 0,
    val stixFailedSourceCount: Int = 0,
    val infinityGeneration: Int = 0,
    val infinityMalwareStudied: Int = 0,
    val infinityVulnStudied: Int = 0,
    val infinityCodexDepth: Int = 0,
    val crawlerEntryCount: Int = 0,
    val crawlerSignatureValid: Boolean = false,
    val crawlerSourceLabel: String = "",
    val crawlerWarnings: List<String> = emptyList(),
)
