package com.coldboar.coreguard.quilla.knowledge

import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * On-device cybersecurity knowledge index for Quilla.
 *
 * Loads curated JSON corpora (OWASP MASVS, MASTG orientation, pen-test methodology,
 * incident response, Android hardening, and MITRE ATT&CK Mobile techniques).
 * Deterministic keyword ranking — no cloud LLM.
 */
object CyberKnowledgeBase {

    data class Entry(
        val id: String,
        val title: String,
        val category: String,
        val tags: Set<String>,
        val summary: String,
        val body: String,
        val defense: String,
        val references: List<String>
    )

    data class Hit(
        val entry: Entry,
        val score: Int
    )

    private val state = AtomicReference(Index(emptyList(), emptyMap(), emptyMap()))

    private data class Index(
        val entries: List<Entry>,
        val byId: Map<String, Entry>,
        val inverted: Map<String, IntArray>
    )

    fun isLoaded(): Boolean = state.get().entries.isNotEmpty()

    fun size(): Int = state.get().entries.size

    fun clear() {
        state.set(Index(emptyList(), emptyMap(), emptyMap()))
    }

    fun getById(id: String): Entry? = state.get().byId[id]

    /**
     * Parses one or more knowledge JSON documents shaped like:
     * `{ "entries": [ { id, title, category, tags, summary, body, defense?, references? } ] }`
     */
    @Synchronized
    fun loadDocuments(jsonDocuments: Iterable<String>) {
        val parsed = mutableListOf<Entry>()
        for (doc in jsonDocuments) {
            parsed += parseDocument(doc)
        }
        state.set(buildIndex(parsed))
    }

    /**
     * Merges runtime intel entries (e.g. CISA KEV / MISP galaxy) into the index.
     * Same [Entry.id] replaces the prior record; bundled corpus entries are kept.
     */
    @Synchronized
    fun mergeEntries(incoming: Collection<Entry>) {
        if (incoming.isEmpty()) return
        val byId = LinkedHashMap<String, Entry>()
        for (existing in state.get().entries) {
            byId[existing.id] = existing
        }
        for (entry in incoming) {
            if (entry.id.isBlank() || entry.title.isBlank()) continue
            byId[entry.id] = entry
        }
        state.set(buildIndex(byId.values.toList()))
    }

    internal fun parseDocument(doc: String): List<Entry> {
        if (doc.isBlank()) return emptyList()
        val root = runCatching { JSONObject(doc) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("entries") ?: return emptyList()
        val parsed = mutableListOf<Entry>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id").trim()
            val title = obj.optString("title").trim()
            if (id.isEmpty() || title.isEmpty()) continue
            val tagsArr = obj.optJSONArray("tags")
            val tags = buildSet {
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) {
                        val tag = tagsArr.optString(t).trim().lowercase(Locale.US)
                        if (tag.isNotEmpty()) add(tag)
                    }
                }
                add(obj.optString("category").trim().lowercase(Locale.US))
                tokenize(title).forEach { add(it) }
            }.filter { it.isNotBlank() }.toSet()
            val refsArr = obj.optJSONArray("references")
            val refs = buildList {
                if (refsArr != null) {
                    for (r in 0 until refsArr.length()) {
                        val ref = refsArr.optString(r).trim()
                        if (ref.isNotEmpty()) add(ref)
                    }
                }
            }
            parsed += Entry(
                id = id,
                title = title,
                category = obj.optString("category").ifBlank { "general" },
                tags = tags,
                summary = obj.optString("summary").trim(),
                body = obj.optString("body").trim(),
                defense = obj.optString("defense").trim(),
                references = refs
            )
        }
        return parsed
    }

    /**
     * Search the Cyber Codex.
     *
     * @param limit max hits to return. Use [Int.MAX_VALUE] or `<= 0` for **uncapped**
     *   (every positive-score hit). Quilla defaults open — loving awareness has no
     *   artificial teaching ceiling.
     */
    fun search(query: String, limit: Int = Int.MAX_VALUE): List<Hit> {
        val index = state.get()
        if (index.entries.isEmpty()) return emptyList()
        val uncapped = limit <= 0 || limit == Int.MAX_VALUE

        // Canonical ready topics always win (exact product promises).
        QuillaReadyTopics.resolveEntryId(query)?.let { readyId ->
            val ready = index.byId[readyId]
            if (ready != null) {
                val rest = rankedHits(query, index, excludeId = readyId).let { hits ->
                    if (uncapped) hits else hits.take((limit - 1).coerceAtLeast(0))
                }
                return listOf(Hit(ready, 1_000)) + rest
            }
        }

        val ranked = rankedHits(query, index, excludeId = null)
        return if (uncapped) ranked else ranked.take(limit.coerceAtLeast(1))
    }

    private fun rankedHits(query: String, index: Index, excludeId: String?): List<Hit> {
        val qTokens = tokenize(query)
        if (qTokens.isEmpty()) return emptyList()

        val scores = IntArray(index.entries.size)
        for (token in qTokens) {
            val posting = index.inverted[token] ?: continue
            for (entryIdx in posting) {
                scores[entryIdx] += 4
            }
        }

        val qLower = query.trim().lowercase(Locale.US)
        val mitreBare = Regex("^t\\d{4}$", RegexOption.IGNORE_CASE).matches(qLower)
        index.entries.forEachIndexed { i, entry ->
            if (excludeId != null && entry.id == excludeId) {
                scores[i] = Int.MIN_VALUE / 4
                return@forEachIndexed
            }
            if (entry.title.lowercase(Locale.US).contains(qLower) && qLower.length >= 4) {
                scores[i] += 8
            }
            val idNeedle = qLower.replace(' ', '-')
            if (entry.id.lowercase(Locale.US).contains(idNeedle)) {
                scores[i] += 6
            }
            for (token in qTokens) {
                if (token.matches(Regex("t\\d{4}(\\.\\d+)?"))) {
                    val titleLower = entry.title.lowercase(Locale.US)
                    if (titleLower.startsWith(token)) {
                        scores[i] += 20
                    }
                    if (entry.id.contains(token.replace(".", "-"))) {
                        scores[i] += 12
                    }
                    // Prefer parent technique when user asks bare T1636 (not T1636.004).
                    if (mitreBare && entry.id == "mitre-$token") {
                        scores[i] += 40
                    }
                    if (mitreBare && entry.id.startsWith("mitre-$token-")) {
                        scores[i] -= 10
                    }
                }
            }
        }

        return scores.withIndex()
            .filter { it.value > 0 }
            .sortedWith(
                compareByDescending<IndexedValue<Int>> { it.value }
                    .thenBy { index.entries[it.index].id }
            )
            .map { Hit(index.entries[it.index], it.value) }
    }

    fun formatHit(hit: Hit): String {
        val e = hit.entry
        return buildString {
            append("📚 ")
            append(e.title)
            append("\n")
            if (e.summary.isNotBlank()) {
                append(e.summary)
                append("\n")
            }
            append(e.body)
            if (e.defense.isNotBlank()) {
                append("\n\nDefense: ")
                append(e.defense)
            }
            if (e.references.isNotEmpty()) {
                append("\nRef: ")
                append(e.references.first())
            }
        }
    }

    private fun buildIndex(entries: List<Entry>): Index {
        val inverted = HashMap<String, MutableList<Int>>()
        entries.forEachIndexed { idx, entry ->
            val tokens = LinkedHashSet<String>()
            tokens += entry.tags
            tokens += tokenize(entry.title)
            tokens += tokenize(entry.summary)
            // Index the full body — no artificial token ceiling on awareness.
            tokens += tokenize(entry.body)
            tokens += tokenize(entry.category)
            tokens += entry.id.lowercase(Locale.US)
            for (token in tokens) {
                inverted.getOrPut(token) { mutableListOf() }.add(idx)
            }
        }
        return Index(
            entries = entries,
            byId = entries.associateBy { it.id },
            inverted = inverted.mapValues { (_, v) -> v.toIntArray() }
        )
    }

    internal fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val cleaned = text.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9.&+/\\- ]"), " ")
        val raw = cleaned.split(Regex("\\s+"))
            .map { it.trim('-', '.', '/') }
            .filter { it.length >= 2 && it !in STOPWORDS }
        val out = LinkedHashSet<String>()
        for (token in raw) {
            out += token
            if (token.contains('-')) {
                token.split('-').filter { it.length >= 2 && it !in STOPWORDS }.forEach { out += it }
            }
            if (token.contains('.')) {
                // t1636.004 → t1636, 004 kept only if long enough
                token.split('.').filter { it.length >= 2 && it !in STOPWORDS }.forEach { out += it }
            }
        }
        return out.toList()
    }

    private val STOPWORDS = setOf(
        "the", "and", "for", "with", "that", "this", "from", "your", "you",
        "are", "was", "were", "have", "has", "not", "but", "use", "using",
        "into", "about", "when", "what", "how", "why", "can", "does", "did",
        "a", "an", "of", "to", "in", "on", "or", "is", "it", "as", "be"
    )
}
