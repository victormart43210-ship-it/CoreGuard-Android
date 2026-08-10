package com.coldboar.coreguard.knowledge

import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import java.util.concurrent.ConcurrentHashMap

enum class ThreatKnowledgeSource {
    ANKI,
    VIPER,
    CRAWLER
}

data class ThreatKnowledgeMatch(
    val source: ThreatKnowledgeSource,
    val hit: CyberKnowledgeBase.Hit,
    val provesCompromise: Boolean = false,
    val confidenceCap: Float = 0.60f,
    val severityCap: String = "MEDIUM"
)

interface ThreatKnowledgeRepository {
    fun mergeAnkiKnowledge(entries: Collection<CyberKnowledgeBase.Entry>)
    fun mergeViperKnowledge(entries: Collection<CyberKnowledgeBase.Entry>)
    fun mergeCrawlerKnowledge(entries: Collection<CyberKnowledgeBase.Entry>)
    fun importViperPayload(payload: String): ViperThreatIntelImporter.ImportResult
    fun search(
        query: String,
        limit: Int = Int.MAX_VALUE,
        sources: Set<ThreatKnowledgeSource> = ThreatKnowledgeSource.entries.toSet()
    ): List<ThreatKnowledgeMatch>
}

object SharedThreatKnowledgeRepository : ThreatKnowledgeRepository {
    private val sourceByEntryId = ConcurrentHashMap<String, ThreatKnowledgeSource>()

    override fun mergeAnkiKnowledge(entries: Collection<CyberKnowledgeBase.Entry>) {
        merge(entries, ThreatKnowledgeSource.ANKI)
    }

    override fun mergeViperKnowledge(entries: Collection<CyberKnowledgeBase.Entry>) {
        merge(entries, ThreatKnowledgeSource.VIPER)
    }

    override fun mergeCrawlerKnowledge(entries: Collection<CyberKnowledgeBase.Entry>) {
        if (entries.isEmpty()) return
        // Crawler entries are always context only — provesCompromise = false.
        // Confidence and severity are capped at MEDIUM by ThreatKnowledgeMatch.
        merge(entries, ThreatKnowledgeSource.CRAWLER)
    }

    override fun importViperPayload(payload: String): ViperThreatIntelImporter.ImportResult {
        val result = ViperThreatIntelImporter.importPayload(payload)
        mergeViperKnowledge(result.entries)
        return result
    }

    override fun search(
        query: String,
        limit: Int,
        sources: Set<ThreatKnowledgeSource>
    ): List<ThreatKnowledgeMatch> {
        val include = if (sources.isEmpty()) ThreatKnowledgeSource.entries.toSet() else sources
        val uncapped = limit <= 0 || limit == Int.MAX_VALUE
        val hits = CyberKnowledgeBase.search(
            query = query,
            limit = if (uncapped) Int.MAX_VALUE else (limit * 4).coerceAtLeast(limit)
        )
        val filtered = hits.mapNotNull { hit ->
            val source = sourceByEntryId[hit.entry.id] ?: ThreatKnowledgeSource.ANKI
            if (source !in include) return@mapNotNull null
            // All knowledge matches are context only — they do not prove device compromise.
            ThreatKnowledgeMatch(source = source, hit = hit, provesCompromise = false)
        }
        return if (uncapped) filtered else filtered.take(limit.coerceAtLeast(1))
    }

    private fun merge(entries: Collection<CyberKnowledgeBase.Entry>, source: ThreatKnowledgeSource) {
        if (entries.isEmpty()) return
        val sanitized = entries.filter { it.id.isNotBlank() && it.title.isNotBlank() }
        if (sanitized.isEmpty()) return
        CyberKnowledgeBase.mergeEntries(sanitized)
        sanitized.forEach { sourceByEntryId[it.id] = source }
    }

    internal fun clearForTests() {
        sourceByEntryId.clear()
    }
}
