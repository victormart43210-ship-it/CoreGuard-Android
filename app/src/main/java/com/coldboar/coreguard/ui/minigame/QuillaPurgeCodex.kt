package com.coldboar.coreguard.ui.minigame

import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import com.coldboar.coreguard.quilla.knowledge.QuillaReadyTopics
import kotlin.random.Random

/**
 * Maps Quilla Infinity / Cyber Codex teaching into Audit Keep purge flavor.
 *
 * Educational only — tips are the same defensive notes Quilla teaches in Q&A,
 * not live detection or cloud LLM output.
 */
internal data class PurgeFlavorCard(
    val id: String,
    val title: String,
    val tip: String,
    val isWorm: Boolean,
    val angel: String,
    val shortLabel: String = title.take(28)
)

internal object QuillaPurgeCodex {

    private val WORM_TAGS = setOf(
        "dns", "c2", "network", "tracker", "domain", "spyware", "trojan",
        "malware", "stalkerware", "pegasus", "misp"
    )
    private val SPIKE_TAGS = setOf(
        "overlay", "phishing", "accessibility", "sideload", "dropper",
        "frida", "hook", "root", "instrument"
    )

    fun buildDeck(
        entries: List<CyberKnowledgeBase.Entry>,
        random: Random = Random.Default,
        maxCards: Int = 48
    ): List<PurgeFlavorCard> {
        if (entries.isEmpty()) return fallbackDeck()

        val readyIds = QuillaReadyTopics.ALL.map { it.entryId }.toSet()
        val preferred = entries.filter { it.id in readyIds || it.hasThreatSignal() }
        val pool = (if (preferred.size >= 8) preferred else entries)
            .shuffled(random)
            .take(maxCards.coerceAtLeast(8))

        return pool.map { entry ->
            PurgeFlavorCard(
                id = entry.id,
                title = entry.title.trim().ifBlank { entry.id },
                tip = tipFor(entry),
                isWorm = classifyWorm(entry),
                angel = angelFor(entry),
                shortLabel = shorten(entry.title)
            )
        }
    }

    fun levelTitle(infinityGeneration: Int, score: Int): String {
        // Reference mock uses "Level 1-3"; deeper Keep stages unlock with score.
        val stage = when {
            score >= 80 -> 6
            score >= 50 -> 5
            score >= 25 -> 4
            else -> 3
        }
        return if (infinityGeneration <= 0) {
            "Level 1-$stage: The Audit Keep"
        } else {
            "Infinity gen ${infinityGeneration.coerceAtMost(99)} · Keep $stage"
        }
    }

    fun subtitle(infinityGeneration: Int, codexDepth: Int): String {
        return if (infinityGeneration <= 0) {
            "Level 1-3: The Audit Keep"
        } else {
            "Infinity gen $infinityGeneration · $codexDepth codex"
        }
    }

    fun portraitLine(angel: String, frameHint: Int = 0): String {
        val a = angel.ifBlank { ANGELS[frameHint % ANGELS.size] }
        return "$a · Audit Keep"
    }

    fun debriefLines(purgedTitles: List<String>, infinityGeneration: Int): List<String> {
        val unique = purgedTitles.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val shards = if (unique.isEmpty()) {
            listOf("No named threats purged — keep flying.")
        } else {
            listOf("Purged: " + unique.take(4).joinToString(" · "))
        }
        val gen = if (infinityGeneration <= 0) {
            "Infinity idle — train choir from the debrief to deepen tips."
        } else {
            "Choir hardened to Infinity gen $infinityGeneration (on-device teaching)."
        }
        return shards + gen + "Tips are educational — not live Scanner detection."
    }

    fun tipFor(entry: CyberKnowledgeBase.Entry): String {
        val defense = entry.defense.trim()
        if (defense.isNotBlank()) return clipTip(defense)
        val summary = entry.summary.trim()
        if (summary.isNotBlank()) return clipTip(summary)
        return "Study ${entry.title} in Quilla — defensive care, not offense."
    }

    fun classifyWorm(entry: CyberKnowledgeBase.Entry): Boolean {
        val blob = blobOf(entry)
        val wormHits = WORM_TAGS.count { blob.contains(it) }
        val spikeHits = SPIKE_TAGS.count { blob.contains(it) }
        return when {
            wormHits > spikeHits -> true
            spikeHits > wormHits -> false
            else -> entry.id.hashCode() % 2 == 0
        }
    }

    fun angelFor(entry: CyberKnowledgeBase.Entry): String {
        val blob = blobOf(entry)
        return when {
            SPIKE_TAGS.any { blob.contains(it) && it in setOf("overlay", "phishing", "sideload", "dropper", "accessibility") } ->
                "Sandalphon"
            blob.contains("frida") || blob.contains("hook") || blob.contains("root") || blob.contains("instrument") ->
                "Michael"
            blob.contains("dns") || blob.contains("c2") || blob.contains("network") || blob.contains("tracker") ->
                "Kamael"
            blob.contains("spyware") || blob.contains("malware") || blob.contains("trojan") || blob.contains("stalkerware") ->
                "Tzadkiel"
            blob.contains("kev") || blob.contains("cve") || blob.contains("vulnerability") ->
                "Metatron"
            blob.contains("timeline") || blob.contains("forensic") || blob.contains("triage") ->
                "Gabriel"
            else -> "Raziel"
        }
    }

    fun nextCard(deck: List<PurgeFlavorCard>, index: Int): PurgeFlavorCard {
        if (deck.isEmpty()) return fallbackDeck().first()
        return deck[index.mod(deck.size)]
    }

    fun fallbackDeck(): List<PurgeFlavorCard> = listOf(
        PurgeFlavorCard(
            id = "fallback-overlay",
            title = "Overlay phishing wraith",
            tip = "Reject unexpected overlay prompts — verify the real app before tapping Allow.",
            isWorm = false,
            angel = "Sandalphon",
            shortLabel = "Overlay wraith"
        ),
        PurgeFlavorCard(
            id = "fallback-dns",
            title = "DNS C2 worm",
            tip = "Suspicious DNS beacons need network review — Quilla teaches patterns, Scanner uses signatures.",
            isWorm = true,
            angel = "Kamael",
            shortLabel = "DNS C2 worm"
        ),
        PurgeFlavorCard(
            id = "fallback-sideload",
            title = "Sideload dropper gate",
            tip = "Unknown APK installs are high risk — prefer Play or verified sources.",
            isWorm = false,
            angel = "Sandalphon",
            shortLabel = "Sideload gate"
        ),
        PurgeFlavorCard(
            id = "fallback-spyware",
            title = "Stalkerware spore",
            tip = "Hidden dual-use apps need a calm triage — Quilla’s incident checklist helps.",
            isWorm = true,
            angel = "Tzadkiel",
            shortLabel = "Stalkerware"
        )
    )

    private fun CyberKnowledgeBase.Entry.hasThreatSignal(): Boolean {
        val blob = blobOf(this)
        return WORM_TAGS.any { blob.contains(it) } ||
            SPIKE_TAGS.any { blob.contains(it) } ||
            blob.contains("kev") ||
            blob.contains("cve") ||
            blob.contains("mitre") ||
            blob.contains("masvs")
    }

    private fun blobOf(entry: CyberKnowledgeBase.Entry): String =
        "${entry.category} ${entry.tags.joinToString(" ")} ${entry.title} ${entry.id}".lowercase()

    private fun shorten(title: String): String {
        val t = title.trim()
        if (t.length <= 22) return t
        return t.take(21).trimEnd() + "…"
    }

    private fun clipTip(text: String): String {
        val oneLine = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        return if (oneLine.length <= 110) oneLine else oneLine.take(109).trimEnd() + "…"
    }

    private val ANGELS = listOf(
        "Raziel", "Sandalphon", "Michael", "Kamael", "Tzadkiel", "Metatron", "Gabriel", "Haniel"
    )
}
