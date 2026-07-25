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

    private val state = AtomicReference(Index(emptyList(), emptyMap()))

    private data class Index(
        val entries: List<Entry>,
        val inverted: Map<String, IntArray>
    )

    fun isLoaded(): Boolean = state.get().entries.isNotEmpty()

    fun size(): Int = state.get().entries.size

    fun clear() {
        state.set(Index(emptyList(), emptyMap()))
    }

    /**
     * Parses one or more knowledge JSON documents shaped like:
     * `{ "entries": [ { id, title, category, tags, summary, body, defense?, references? } ] }`
     */
    @Synchronized
    fun loadDocuments(jsonDocuments: Iterable<String>) {
        val parsed = mutableListOf<Entry>()
        for (doc in jsonDocuments) {
            if (doc.isBlank()) continue
            val root = JSONObject(doc)
            val arr = root.optJSONArray("entries") ?: continue
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
        }
        state.set(buildIndex(parsed))
    }

    fun search(query: String, limit: Int = 3): List<Hit> {
        val qTokens = tokenize(query)
        if (qTokens.isEmpty()) return emptyList()
        val index = state.get()
        if (index.entries.isEmpty()) return emptyList()

        val scores = IntArray(index.entries.size)
        for (token in qTokens) {
            val posting = index.inverted[token] ?: continue
            for (entryIdx in posting) {
                scores[entryIdx] += 4
            }
        }
        // Phrase / title boosts
        val qLower = query.lowercase(Locale.US)
        index.entries.forEachIndexed { i, entry ->
            if (entry.title.lowercase(Locale.US).contains(qLower) && qLower.length >= 4) {
                scores[i] += 8
            }
            if (entry.id.lowercase(Locale.US).contains(qLower.replace(" ", "-"))) {
                scores[i] += 6
            }
            // MITRE technique id direct hit (t1636 etc.)
            for (token in qTokens) {
                if (token.matches(Regex("t\\d{4}([a-z]|\\.\\d+)?"))) {
                    if (entry.title.lowercase(Locale.US).startsWith(token) ||
                        entry.id.contains(token.replace(".", "-"))
                    ) {
                        scores[i] += 20
                    }
                }
            }
        }

        return scores.withIndex()
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .take(limit.coerceAtLeast(1))
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
            tokens += tokenize(entry.body).take(40)
            tokens += tokenize(entry.category)
            for (token in tokens) {
                inverted.getOrPut(token) { mutableListOf() }.add(idx)
            }
        }
        return Index(
            entries = entries,
            inverted = inverted.mapValues { (_, v) -> v.toIntArray() }
        )
    }

    internal fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9.&+/\\- ]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim('-', '.', '/') }
            .filter { it.length >= 2 && it !in STOPWORDS }
    }

    private val STOPWORDS = setOf(
        "the", "and", "for", "with", "that", "this", "from", "your", "you",
        "are", "was", "were", "have", "has", "not", "but", "use", "using",
        "into", "about", "when", "what", "how", "why", "can", "does", "did",
        "a", "an", "of", "to", "in", "on", "or", "is", "it", "as", "be"
    )
}
