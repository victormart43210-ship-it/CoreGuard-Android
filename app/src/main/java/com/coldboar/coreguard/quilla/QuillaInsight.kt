package com.coldboar.coreguard.quilla

import android.content.Context
import com.coldboar.coreguard.mvt.LastScan
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict
import com.coldboar.coreguard.mvt.ShieldState
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeAssets
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase

/**
 * Short, buyer-facing Quilla blurbs for Home / Scanner / Shield —
 * powered by the same on-device agent stack (no cloud LLM).
 */
object QuillaInsight {

    data class Card(
        val eyebrow: String = "Quilla",
        val title: String,
        val body: String,
        val primaryCta: String? = null,
        val primaryAction: Action = Action.ASK_QUILLA,
        val secondaryCta: String? = null,
        val secondaryAction: Action? = null
    )

    enum class Action {
        RUN_SCAN,
        OPEN_SHIELD,
        OPEN_TIMELINE,
        ASK_QUILLA,
        OPEN_SETTINGS
    }

    fun ensureReady(context: Context) {
        CyberKnowledgeAssets.ensureLoaded(context)
    }

    fun homeCard(context: Context): Card {
        ensureReady(context)
        val memory = QuillaMemoryFactory.memorySnapshot(context)
        val agent = UltimateQuillaAgent(
            memoryProvider = { memory },
            researchProvider = { QuillaMemoryFactory.cachedResearch() }
        )
        val answer = agent.answer("am I safe right now?")
        val body = answer.text
            .lineSequence()
            .dropWhile { it.startsWith("Quilla hears") || it.isBlank() }
            .take(4)
            .joinToString("\n")
            .ifBlank { answer.text }
            .take(420)

        return when {
            memory.lastScanVerdict == null -> Card(
                title = "I haven’t checked this phone yet",
                body = "Run a Nemesis privacy check in about a minute — then I’ll coach from real evidence, not guesses.",
                primaryCta = "Check My Device Now",
                primaryAction = Action.RUN_SCAN,
                secondaryCta = "Ask Quilla",
                secondaryAction = Action.ASK_QUILLA
            )
            memory.lastScanVerdict == ScanVerdict.INFECTED.name ||
                memory.lastScanVerdict == ScanVerdict.SUSPICIOUS.name -> Card(
                title = "I see something you should review",
                body = body,
                primaryCta = "Open Scanner",
                primaryAction = Action.RUN_SCAN,
                secondaryCta = if (!memory.shieldActive) "Turn On Shield" else "Ask Quilla",
                secondaryAction = if (!memory.shieldActive) Action.OPEN_SHIELD else Action.ASK_QUILLA
            )
            !memory.shieldActive -> Card(
                title = "Looking good — keep the edge",
                body = "Last scan looked clean. Privacy Shield is off — on untrusted Wi‑Fi I’d turn it on next.",
                primaryCta = "Enable Shield",
                primaryAction = Action.OPEN_SHIELD,
                secondaryCta = "Ask Quilla",
                secondaryAction = Action.ASK_QUILLA
            )
            else -> Card(
                title = "Your observatory is live",
                body = "Last scan looked clean and Shield is on (${memory.shieldBlocked} blocks). Ask me about MASVS, ATT&CK, or your next move.",
                primaryCta = "Ask Quilla",
                primaryAction = Action.ASK_QUILLA,
                secondaryCta = "View Timeline",
                secondaryAction = Action.OPEN_TIMELINE
            )
        }
    }

    fun postScanCard(report: ScanReport, shieldActive: Boolean = ShieldState.isActive): Card {
        val detectionHint = report.detections.firstOrNull()?.let {
            " Top finding: ${it.title}."
        }.orEmpty()
        return when (report.verdict) {
            ScanVerdict.CLEAN -> Card(
                title = "Quilla: encouraging pass",
                body = "Nothing flagged this round.$detectionHint " +
                    if (shieldActive) {
                        "Shield is already helping. Re-check after new installs."
                    } else {
                        "Turn on Privacy Shield on public Wi‑Fi for a cheap extra layer."
                    },
                primaryCta = if (shieldActive) "Ask Quilla" else "Enable Shield",
                primaryAction = if (shieldActive) Action.ASK_QUILLA else Action.OPEN_SHIELD,
                secondaryCta = "View Timeline",
                secondaryAction = Action.OPEN_TIMELINE
            )
            ScanVerdict.SUSPICIOUS -> Card(
                title = "Quilla: needs your eyes",
                body = "Findings look SUSPICIOUS — review each item below.$detectionHint " +
                    "Don’t panic; verify, then harden.",
                primaryCta = if (!shieldActive) "Enable Shield" else "Ask Quilla",
                primaryAction = if (!shieldActive) Action.OPEN_SHIELD else Action.ASK_QUILLA,
                secondaryCta = "Ask Quilla",
                secondaryAction = Action.ASK_QUILLA
            )
            ScanVerdict.INFECTED -> Card(
                title = "Quilla: act on this finding",
                body = "Privacy-threat indicators matched.$detectionHint " +
                    "Open the list, remove unknown apps, enable Shield, and rotate critical accounts from a clean device.",
                primaryCta = "Enable Shield",
                primaryAction = Action.OPEN_SHIELD,
                secondaryCta = "Ask Quilla what to do",
                secondaryAction = Action.ASK_QUILLA
            )
        }
    }

    fun shieldCard(context: Context): Card? {
        ensureReady(context)
        val memory = QuillaMemoryFactory.memorySnapshot(context)
        val last = LastScan.report
        return when {
            !memory.shieldActive &&
                (last?.verdict == ScanVerdict.SUSPICIOUS || last?.verdict == ScanVerdict.INFECTED) -> Card(
                title = "Quilla recommends Shield",
                body = "Your last scan wasn’t clean, but Privacy Shield is off. Enabling it sinkholes known-bad DNS names while you investigate.",
                primaryCta = null, // screen already has the toggle
                primaryAction = Action.OPEN_SHIELD
            )
            memory.shieldActive && memory.shieldBlocked > 0 -> Card(
                title = "Quilla saw ${memory.shieldBlocked} blocks",
                body = buildString {
                    append("Shield is working.")
                    memory.lastBlockedDomain?.let { append(" Last blocked: $it.") }
                    append(" Want a fresh privacy check against those signals?")
                },
                primaryCta = "Run Privacy Check",
                primaryAction = Action.RUN_SCAN,
                secondaryCta = "Ask Quilla",
                secondaryAction = Action.ASK_QUILLA
            )
            memory.shieldActive -> Card(
                title = "Quilla: Shield standing watch",
                body = "DNS filtering is on. Ask me how Shield differs from a full VPN, or about MASVS-NETWORK.",
                primaryCta = "Ask Quilla",
                primaryAction = Action.ASK_QUILLA
            )
            else -> Card(
                title = "Quilla: cheap insurance on hostile Wi‑Fi",
                body = "Shield is a local DNS sinkhole — not magic spyware removal. Flip it on when you don’t trust the network.",
                primaryCta = "Ask Quilla",
                primaryAction = Action.ASK_QUILLA
            )
        }
    }

    fun knowledgeTeaser(query: String): String? {
        if (!CyberKnowledgeBase.isLoaded()) return null
        val hit = CyberKnowledgeBase.search(query, limit = 1).firstOrNull() ?: return null
        return "${hit.entry.title}: ${hit.entry.summary}"
    }
}
