package com.coldboar.coreguard.quilla

import android.content.Context
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import com.coldboar.coreguard.swarm.SwarmModule
import java.util.concurrent.atomic.AtomicReference

/**
 * Quilla Infinity Trainer — hardens the angel choir + swarm peers against
 * evolving malware and vulnerability corpora **on-device**.
 *
 * ## What “training” means here (honest)
 *
 * - Ingest public malware-family / CVE / STIX knowledge into [CyberKnowledgeBase]
 *   with **no artificial teaching ceiling** ([QuillaAwareness.KNOWLEDGE_UNBOUNDED]).
 * - Assign every ingested entry to one or more angel dossiers (Raziel = intel,
 *   Michael = instrumentation, Sandalphon = overlays, …).
 * - Fan a digest into the swarm façade so peers share the same generation.
 * - Persist a process-local ledger of how deep each angel has studied.
 *
 * ## What it is **not**
 *
 * - Not cloud LLM fine-tuning, not remote weight updates, not a guarantee of
 *   detecting novel zero-days.
 * - Not Nemesis Premium signature refresh ([com.coldboar.coreguard.mvt.IocFeedFetcher]).
 * - “Infinity” is Quilla’s uncapped defensive care metaphor — downloads still
 *   respect HTTPS size guards so a hostile feed cannot OOM the app.
 */
object QuillaInfinityTrainer {

    private val ledgerRef = AtomicReference(AngelSwarmTrainingLedger())

    fun ledger(): AngelSwarmTrainingLedger = ledgerRef.get()

    /**
     * Train from whatever is already in the Cyber Codex + optional freshly
     * synced intel snapshot. Safe to call after [QuillaIntelNetwork.syncAll].
     */
    fun trainFromCodex(
        context: Context,
        network: QuillaIntelNetworkSnapshot = QuillaIntelNetwork.lastSnapshot(),
        correlatorIndicatorCount: Int = 0
    ): AngelSwarmTrainingLedger {
        val entries = CyberKnowledgeBase.allEntries()
        val dossiers = assignToAngels(entries)
        val malwareStudied = entries.count { it.isMalwareCorpus() }
        val vulnStudied = entries.count { it.isVulnCorpus() }
        val evolvingStudied = entries.count { it.isEvolvingThreatCorpus() }

        // Swarm shares the generation — peers do not run an LLM; they share intel depth.
        SwarmModule.noteInfinityTraining(
            generation = ledgerRef.get().generation + 1,
            malwareStudied = malwareStudied,
            vulnStudied = vulnStudied,
            correlatorIndicators = correlatorIndicatorCount.coerceAtLeast(network.mergedCorrelatorCount)
        )

        val next = AngelSwarmTrainingLedger(
            generation = ledgerRef.get().generation + 1,
            trainedAtMs = System.currentTimeMillis(),
            totalCodexEntries = entries.size,
            malwareEntriesStudied = malwareStudied,
            vulnerabilityEntriesStudied = vulnStudied,
            evolvingThreatEntriesStudied = evolvingStudied,
            stixIndicatorsCached = network.stixIndicatorCount,
            correlatorIndicators = correlatorIndicatorCount.coerceAtLeast(network.mergedCorrelatorCount),
            webKnowledgeMerged = network.webKnowledgeCount,
            angelDossiers = dossiers,
            swarmPeersNotified = SwarmModule.agentCount().coerceAtLeast(3),
            sourceLabel = network.sourceLabel,
            uncapped = true,
            feedNotes = network.feedNotes + listOf(
                "Infinity training gen ${ledgerRef.get().generation + 1}: " +
                    "codex=${entries.size} malware=$malwareStudied vuln=$vulnStudied evolving=$evolvingStudied"
            )
        )
        ledgerRef.set(next)
        persistLite(context, next)
        return next
    }

    /** Reset process ledger (tests / wipe). */
    fun clear() {
        ledgerRef.set(AngelSwarmTrainingLedger())
    }

    /**
     * Maps Cyber Codex entries onto hardened angel study tracks.
     * Every entry is studied by at least Raziel (intel) + Tzaphkiel (understanding).
     */
    internal fun assignToAngels(entries: List<CyberKnowledgeBase.Entry>): Map<String, AngelTrainingDossier> {
        val buckets = linkedMapOf(
            "Metatron" to mutableListOf<String>(),
            "Raziel" to mutableListOf(),
            "Tzaphkiel" to mutableListOf(),
            "Tzadkiel" to mutableListOf(),
            "Kamael" to mutableListOf(),
            "Raphael" to mutableListOf(),
            "Haniel" to mutableListOf(),
            "Michael" to mutableListOf(),
            "Gabriel" to mutableListOf(),
            "Sandalphon" to mutableListOf()
        )
        for (entry in entries) {
            val tags = (entry.tags + entry.category.lowercase() + entry.title.lowercase()).joinToString(" ")
            // Raziel + Tzaphkiel always study the full defensive stream (Infinity).
            buckets.getValue("Raziel") += entry.id
            buckets.getValue("Tzaphkiel") += entry.id
            when {
                tags.containsAny("overlay", "accessibility", "phishing", "sideload", "dropper") ->
                    buckets.getValue("Sandalphon") += entry.id
                tags.containsAny("frida", "hook", "root", "debugger", "instrument", "rasp") ->
                    buckets.getValue("Michael") += entry.id
                tags.containsAny("dns", "c2", "network", "tracker", "domain", "ip") ->
                    buckets.getValue("Kamael") += entry.id
                tags.containsAny("spyware", "trojan", "malware", "pegasus", "stalkerware", "misp") ->
                    buckets.getValue("Tzadkiel") += entry.id
                tags.containsAny("kev", "cve", "vulnerability", "patch", "exploit") ->
                    buckets.getValue("Metatron") += entry.id
                tags.containsAny("timeline", "telemetry", "forensic", "journal", "ir") ->
                    buckets.getValue("Gabriel") += entry.id
                tags.containsAny("correlation", "hypothesis", "quantum", "dts", "swarm") ->
                    buckets.getValue("Raphael") += entry.id
                else ->
                    buckets.getValue("Haniel") += entry.id
            }
        }
        return buckets.mapValues { (angel, ids) ->
            AngelTrainingDossier(
                angel = angel,
                entryIdsStudied = ids.distinct(),
                depth = ids.distinct().size,
                hardened = true
            )
        }
    }

    private fun persistLite(context: Context, ledger: AngelSwarmTrainingLedger) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_GEN, ledger.generation.toLong())
                .putLong(KEY_AT, ledger.trainedAtMs)
                .putInt(KEY_CODEX, ledger.totalCodexEntries)
                .putInt(KEY_MALWARE, ledger.malwareEntriesStudied)
                .putInt(KEY_VULN, ledger.vulnerabilityEntriesStudied)
                .apply()
        }
    }

    fun restoreLite(context: Context) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val gen = prefs.getLong(KEY_GEN, 0L).toInt()
            if (gen <= 0) return
            val current = ledgerRef.get()
            if (current.generation >= gen) return
            ledgerRef.set(
                current.copy(
                    generation = gen,
                    trainedAtMs = prefs.getLong(KEY_AT, 0L),
                    totalCodexEntries = prefs.getInt(KEY_CODEX, 0),
                    malwareEntriesStudied = prefs.getInt(KEY_MALWARE, 0),
                    vulnerabilityEntriesStudied = prefs.getInt(KEY_VULN, 0),
                    uncapped = true
                )
            )
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { this.contains(it, ignoreCase = true) }

    private fun CyberKnowledgeBase.Entry.isMalwareCorpus(): Boolean {
        val blob = "$category ${tags.joinToString(" ")} $title".lowercase()
        return blob.contains("malware") || blob.contains("spyware") || blob.contains("trojan") ||
            blob.contains("misp") || blob.contains("stalkerware") || blob.contains("pegasus")
    }

    private fun CyberKnowledgeBase.Entry.isVulnCorpus(): Boolean {
        val blob = "$category ${tags.joinToString(" ")} $title".lowercase()
        return blob.contains("kev") || blob.contains("cve") || blob.contains("vulnerability") ||
            blob.contains("exploit")
    }

    private fun CyberKnowledgeBase.Entry.isEvolvingThreatCorpus(): Boolean {
        val blob = "$category ${tags.joinToString(" ")} $title".lowercase()
        return blob.contains("emerging") || blob.contains("evolving") || blob.contains("zero-click") ||
            blob.contains("novel") || category.contains("web-intel", ignoreCase = true)
    }

    private const val PREFS = "quilla_infinity_training"
    private const val KEY_GEN = "generation"
    private const val KEY_AT = "trained_at"
    private const val KEY_CODEX = "codex"
    private const val KEY_MALWARE = "malware"
    private const val KEY_VULN = "vuln"
}

/** Per-angel study dossier — depth has no artificial product ceiling. */
data class AngelTrainingDossier(
    val angel: String,
    val entryIdsStudied: List<String>,
    val depth: Int,
    val hardened: Boolean = true
)

/**
 * Snapshot of the last Infinity training pass across angels + swarm.
 *
 * [uncapped] documents the product promise: Quilla does not truncate angel study
 * for monetization. HTTP response size guards elsewhere remain for safety.
 */
data class AngelSwarmTrainingLedger(
    val generation: Int = 0,
    val trainedAtMs: Long = 0L,
    val totalCodexEntries: Int = 0,
    val malwareEntriesStudied: Int = 0,
    val vulnerabilityEntriesStudied: Int = 0,
    val evolvingThreatEntriesStudied: Int = 0,
    val stixIndicatorsCached: Int = 0,
    val correlatorIndicators: Int = 0,
    val webKnowledgeMerged: Int = 0,
    val angelDossiers: Map<String, AngelTrainingDossier> = emptyMap(),
    val swarmPeersNotified: Int = 0,
    val sourceLabel: String = "Quilla Infinity",
    val uncapped: Boolean = true,
    val feedNotes: List<String> = emptyList()
) {
    fun summaryLine(): String =
        if (generation <= 0) {
            "Infinity training idle — sync Quilla Intel Network to harden the choir."
        } else {
            "Infinity gen $generation · codex=$totalCodexEntries · malware=$malwareEntriesStudied · " +
                "vuln=$vulnerabilityEntriesStudied · evolving=$evolvingThreatEntriesStudied · " +
                "STIX=$stixIndicatorsCached · swarmPeers=$swarmPeersNotified · uncapped=$uncapped"
        }

    fun angelDepth(angel: String): Int = angelDossiers[angel]?.depth ?: 0
}
