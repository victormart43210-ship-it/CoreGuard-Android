package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.lore.QuillaLivingGeometry

/**
 * Top-tier Quilla posture engine — ranks device risk and next moves from local evidence.
 * Deterministic, on-device, no LLM.
 *
 * Posture maps onto Tree-of-Life aspects (Raphael/Kamael/…) for voice only —
 * scores still come from scan / shield / hypothesis / telemetry evidence.
 */
object QuillaPriorityEngine {

    enum class Posture(val label: String, val rank: Int) {
        CRITICAL("CRITICAL", 4),
        ELEVATED("ELEVATED", 3),
        WATCH("WATCH", 2),
        STEADY("STEADY", 1),
        UNKNOWN("UNKNOWN", 0)
    }

    data class PriorityMove(
        val id: String,
        val title: String,
        val why: String
    )

    data class Briefing(
        val posture: Posture,
        val score: Int,
        val headline: String,
        val moves: List<PriorityMove>,
        val chipPrompts: List<Pair<String, String>>,
        val aspectName: String,
        val sephirahName: String,
        val livingSeal: String
    )

    fun brief(memory: QuillaMemorySnapshot, research: QuillaResearchSnapshot = QuillaResearchSnapshot()): Briefing {
        var score = 40
        val moves = mutableListOf<PriorityMove>()

        when (memory.lastScanVerdict?.uppercase()) {
            "INFECTED" -> {
                score += 45
                moves += PriorityMove(
                    QuillaActionSuggestion.RUN_SCAN,
                    "Re-run Nemesis now",
                    "Last verdict was INFECTED — treat findings as active until rechecked."
                )
            }
            "SUSPICIOUS" -> {
                score += 28
                moves += PriorityMove(
                    QuillaActionSuggestion.RUN_SCAN,
                    "Review Scanner findings",
                    "SUSPICIOUS residue needs a second look before you call the device clear."
                )
            }
            "CLEAN" -> score -= 8
            null -> {
                score += 12
                moves += PriorityMove(
                    QuillaActionSuggestion.RUN_SCAN,
                    "Establish a scan baseline",
                    "No Nemesis evidence in Memory yet — status without a scan is guesswork."
                )
            }
        }

        if (!memory.shieldActive) {
            score += 10
            moves += PriorityMove(
                QuillaActionSuggestion.OPEN_SHIELD,
                "Arm Privacy Shield",
                "DNS IOC filtering is idle until VPN consent is granted."
            )
        } else if (memory.shieldBlocked > 0) {
            score += 6
            moves += PriorityMove(
                QuillaActionSuggestion.OPEN_SHIELD,
                "Inspect Shield blocks",
                "${memory.shieldBlocked} domains blocked" +
                    (memory.lastBlockedDomain?.let { " (last=$it)" } ?: "") + "."
            )
        }

        if (memory.activeHypotheses.isNotEmpty()) {
            score += 14 + (memory.activeHypotheses.size - 1).coerceAtMost(3) * 3
            moves += PriorityMove(
                "review_hypotheses",
                "Review Quilla hypotheses",
                "${memory.activeHypotheses.size} active correlation hypotheses need human judgment."
            )
        }

        if (memory.telemetryDeltaCount > 0 && memory.telemetryHighSeverity) {
            score += 16
            moves += PriorityMove(
                "telemetry",
                "Inspect signed telemetry",
                "${memory.telemetryDeltaCount} telemetry frames on-device; high-severity RASP signals present."
            )
        }

        if (!research.synced && research.indicatorCount == 0) {
            moves += PriorityMove(
                QuillaActionSuggestion.SYNC_INTEL,
                "Sync Quilla Intel Network",
                "Optional Amnesty/MVT + CISA/MISP pull sharpens correlation — not a Scanner signature refresh."
            )
        }

        if (memory.historyCount == 0) {
            moves += PriorityMove(
                QuillaActionSuggestion.OPEN_TIMELINE,
                "Open Timeline after first scan",
                "A single reading is noise; a ledger shows drift."
            )
        }

        score = score.coerceIn(0, 100)
        // No Nemesis baseline ⇒ UNKNOWN even if Shield/intel noise pushes the numeric score up.
        val posture = when {
            memory.lastScanVerdict == null -> Posture.UNKNOWN
            score >= 80 || memory.lastScanVerdict.equals("INFECTED", true) -> Posture.CRITICAL
            score >= 60 -> Posture.ELEVATED
            score >= 45 -> Posture.WATCH
            else -> Posture.STEADY
        }

        val ranked = moves.distinctBy { it.id }.take(QuillaAwareness.ACTION_VOICE)
        val aspect = QuillaLivingGeometry.aspectForPosture(posture.label)
        val seph = QuillaLivingGeometry.sephirah(aspect.sephirahId)
        val seal = QuillaLivingGeometry.livingSeal(posture.label)
        val headline = when (posture) {
            Posture.CRITICAL ->
                "Priority posture CRITICAL · ${aspect.name} (Gevurah) — act on Scanner evidence before anything else."
            Posture.ELEVATED ->
                "Priority posture ELEVATED · ${aspect.name} (Chesed) — correlate findings, then harden shield and intel."
            Posture.WATCH ->
                "Priority posture WATCH · ${aspect.name} (Hod) — baseline looks fragile; tighten one control now."
            Posture.STEADY ->
                "Priority posture STEADY · ${aspect.name} (Tiferet) — maintain Shield + periodic Nemesis cycles."
            Posture.UNKNOWN ->
                "Priority posture UNKNOWN · ${aspect.name} (Yesod) — Quilla needs a Nemesis baseline to lead."
        }

        val chips = buildList {
            add("Status brief" to "give me my priority status brief")
            when (posture) {
                Posture.CRITICAL, Posture.ELEVATED -> {
                    add("Run scan" to "please run a nemesis scan")
                    add("Overlay phishing" to "overlay phishing")
                }
                Posture.WATCH, Posture.UNKNOWN -> {
                    add("First scan" to "please run a nemesis scan")
                    add("Pentest phases" to "pentest phases")
                }
                Posture.STEADY -> {
                    add("Sync intel" to "sync quilla research intel")
                    add("T1636" to "T1636")
                }
            }
            if (!memory.shieldActive) add("Shield" to "how do I open privacy shield")
            add("MASVS-NETWORK" to "MASVS-NETWORK")
            add("Tree" to "explain the tree of life")
            add("Loving awareness" to "loving awareness")
            add("Care loop" to "care loop")
        }.distinctBy { it.second }.take(QuillaAwareness.CHIP_VOICE)

        return Briefing(
            posture = posture,
            score = score,
            headline = headline,
            moves = ranked,
            chipPrompts = chips,
            aspectName = aspect.name,
            sephirahName = seph?.name ?: "Tiferet",
            livingSeal = seal
        )
    }
}
