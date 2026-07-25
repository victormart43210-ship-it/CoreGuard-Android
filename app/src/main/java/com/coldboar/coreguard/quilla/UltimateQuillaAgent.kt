package com.coldboar.coreguard.quilla

/**
 * Ultimate Quilla — a local multi-module security agent.
 *
 * Formula (adapted for CoreGuard, no ChatGPT/Claude/Zapier keys):
 * - **Brain** — intent classification + evidence-based answer composition
 * - **Memory** — last scan, timeline size, shield state, hypotheses
 * - **Research** — Amnesty / STIX indicator sync status
 * - **Actions** — suggested automations (scan, shield, timeline, intel sync)
 * - **Tools** — Nemesis Scanner, Privacy Shield, Scan Timeline
 *
 * Quilla never claims supernatural detection or silent VPN enablement.
 */
class UltimateQuillaAgent(
    private val memoryProvider: () -> QuillaMemorySnapshot,
    private val researchProvider: () -> QuillaResearchSnapshot = { QuillaResearchSnapshot() }
) {

    fun answer(prompt: String): QuillaAgentAnswer {
        val trimmed = prompt.trim()
        val intent = classify(trimmed)
        val memory = memoryProvider()
        val research = if (intent == QuillaIntent.RESEARCH || intent == QuillaIntent.CAPABILITIES) {
            researchProvider()
        } else {
            QuillaResearchSnapshot()
        }
        val modulesUsed = modulesFor(intent)
        val statuses = moduleStatuses(memory, research)
        val actions = actionsFor(intent, memory)
        val text = compose(trimmed, intent, memory, research, actions)
        return QuillaAgentAnswer(
            text = text,
            intent = intent,
            modulesUsed = modulesUsed,
            moduleStatuses = statuses,
            actions = actions
        )
    }

    fun classify(prompt: String): QuillaIntent {
        val p = prompt.lowercase()
        if (p.isBlank()) return QuillaIntent.CAPABILITIES
        return when {
            p.contains("what can you") || p.contains("capabilities") ||
                p.contains("ultimate") || p.contains("modules") ||
                p.contains("formula") || p.contains("who are you") -> QuillaIntent.CAPABILITIES
            p.contains("research") || p.contains("intel") || p.contains("stix") ||
                p.contains("amnesty") || p.contains("ioc") || p.contains("indicator") ->
                QuillaIntent.RESEARCH
            p.contains("shield") || p.contains("vpn") || p.contains("block") -> QuillaIntent.SHIELD
            p.contains("timeline") || p.contains("history") || p.contains("ledger") ->
                QuillaIntent.TIMELINE
            p.contains("scan") || p.contains("nemesis") || p.contains("pegasus") ->
                QuillaIntent.SCAN
            p.contains("safe") || p.contains("status") || p.contains("risk") ||
                p.contains("protect") || p.contains("how am i") -> QuillaIntent.STATUS
            else -> QuillaIntent.GENERAL
        }
    }

    private fun modulesFor(intent: QuillaIntent): List<QuillaModule> = when (intent) {
        QuillaIntent.CAPABILITIES -> QuillaModule.entries
        QuillaIntent.STATUS -> listOf(QuillaModule.BRAIN, QuillaModule.MEMORY, QuillaModule.TOOLS)
        QuillaIntent.SCAN -> listOf(QuillaModule.BRAIN, QuillaModule.ACTIONS, QuillaModule.TOOLS)
        QuillaIntent.SHIELD -> listOf(QuillaModule.BRAIN, QuillaModule.MEMORY, QuillaModule.ACTIONS, QuillaModule.TOOLS)
        QuillaIntent.TIMELINE -> listOf(QuillaModule.BRAIN, QuillaModule.MEMORY, QuillaModule.TOOLS)
        QuillaIntent.RESEARCH -> listOf(QuillaModule.BRAIN, QuillaModule.RESEARCH, QuillaModule.MEMORY)
        QuillaIntent.GENERAL -> listOf(QuillaModule.BRAIN, QuillaModule.MEMORY, QuillaModule.ACTIONS)
    }

    private fun moduleStatuses(
        memory: QuillaMemorySnapshot,
        research: QuillaResearchSnapshot
    ): List<QuillaModuleStatus> = listOf(
        QuillaModuleStatus(QuillaModule.BRAIN, true, "On-device reasoning ready"),
        QuillaModuleStatus(
            QuillaModule.MEMORY,
            true,
            buildString {
                append(memory.historyCount)
                append(" timeline entries")
                if (memory.lastScanVerdict != null) {
                    append(" · last=")
                    append(memory.lastScanVerdict)
                }
                if (memory.activeHypotheses.isNotEmpty()) {
                    append(" · ")
                    append(memory.activeHypotheses.size)
                    append(" hypotheses")
                }
            }
        ),
        QuillaModuleStatus(
            QuillaModule.RESEARCH,
            research.synced || research.indicatorCount > 0,
            if (research.synced || research.indicatorCount > 0) {
                "${research.indicatorCount} ${research.sourceLabel} indicators loaded"
            } else {
                "Intel idle — ask Quilla to research / sync IOCs"
            }
        ),
        QuillaModuleStatus(
            QuillaModule.ACTIONS,
            true,
            "Can suggest scan, shield, timeline, intel sync"
        ),
        QuillaModuleStatus(
            QuillaModule.TOOLS,
            true,
            buildString {
                append("Shield ")
                append(if (memory.shieldActive) "ON" else "OFF")
                append(" · blocked=")
                append(memory.shieldBlocked)
            }
        )
    )

    private fun actionsFor(intent: QuillaIntent, memory: QuillaMemorySnapshot): List<QuillaActionSuggestion> {
        val scan = QuillaActionSuggestion(
            QuillaActionSuggestion.RUN_SCAN,
            "Run Nemesis scan",
            "Collect fresh on-device evidence with the Nemesis Scanner."
        )
        val shield = QuillaActionSuggestion(
            QuillaActionSuggestion.OPEN_SHIELD,
            if (memory.shieldActive) "Manage Privacy Shield" else "Open Privacy Shield",
            "Shield start still requires Android VPN consent — Quilla will not bypass that."
        )
        val timeline = QuillaActionSuggestion(
            QuillaActionSuggestion.OPEN_TIMELINE,
            "Open Scan Timeline",
            "Review the observatory ledger of prior scans."
        )
        val intel = QuillaActionSuggestion(
            QuillaActionSuggestion.SYNC_INTEL,
            "Sync threat intel",
            "Refresh Amnesty STIX indicators used by Quilla Research."
        )
        return when (intent) {
            QuillaIntent.SCAN -> listOf(scan, timeline)
            QuillaIntent.SHIELD -> listOf(shield, scan)
            QuillaIntent.TIMELINE -> listOf(timeline, scan)
            QuillaIntent.RESEARCH -> listOf(intel, scan)
            QuillaIntent.STATUS -> listOf(scan, shield, timeline)
            QuillaIntent.CAPABILITIES, QuillaIntent.GENERAL -> listOf(scan, shield, timeline, intel)
        }
    }

    private fun compose(
        prompt: String,
        intent: QuillaIntent,
        memory: QuillaMemorySnapshot,
        research: QuillaResearchSnapshot,
        actions: List<QuillaActionSuggestion>
    ): String {
        val header = if (prompt.isBlank()) {
            "Ultimate Quilla online."
        } else {
            "Quilla hears you: \"$prompt\"."
        }
        val body = when (intent) {
            QuillaIntent.CAPABILITIES -> capabilitiesBlurb()
            QuillaIntent.STATUS -> statusBlurb(memory)
            QuillaIntent.SCAN -> scanBlurb(memory)
            QuillaIntent.SHIELD -> shieldBlurb(memory)
            QuillaIntent.TIMELINE -> timelineBlurb(memory)
            QuillaIntent.RESEARCH -> researchBlurb(research, memory)
            QuillaIntent.GENERAL -> generalBlurb(memory)
        }
        val actionLine = if (actions.isEmpty()) {
            ""
        } else {
            "\n\nSuggested actions: " + actions.joinToString(" · ") { it.label } + "."
        }
        return "$header\n\n$body$actionLine\n\n" +
            "Modules: Brain · Memory · Research · Actions · Tools — all on-device, evidence first."
    }

    private fun capabilitiesBlurb(): String =
        "I run as a full local agent stack:\n" +
            "• Brain — classify intent and decide next checks\n" +
            "• Memory — cite last scan, timeline, shield, hypotheses\n" +
            "• Research — sync Amnesty STIX threat intel\n" +
            "• Actions — propose scan / shield / timeline / intel sync\n" +
            "• Tools — Nemesis Scanner, Privacy Shield, Scan Timeline\n" +
            "I do not call ChatGPT/Claude/Zapier. I correlate CoreGuard evidence."

    private fun statusBlurb(memory: QuillaMemorySnapshot): String {
        val scanLine = if (memory.lastScanVerdict == null) {
            "No recent Nemesis scan in Memory — run a scan before claiming the device is clean."
        } else {
            "Last scan verdict: ${memory.lastScanVerdict}" +
                (memory.lastScanDetections?.let { " ($it detections)" } ?: "") +
                ". Timeline holds ${memory.historyCount} entries."
        }
        val shieldLine = if (memory.shieldActive) {
            "Privacy Shield is ON (${memory.shieldBlocked} domains blocked" +
                (memory.lastBlockedDomain?.let { ", last=$it" } ?: "") + ")."
        } else {
            "Privacy Shield is OFF — outbound spyware domains are not being sinkholed."
        }
        val hyp = if (memory.activeHypotheses.isEmpty()) {
            "No active Quilla hypotheses stored."
        } else {
            "Active hypotheses: " + memory.activeHypotheses.take(3).joinToString("; ") +
                if (memory.activeHypotheses.size > 3) "…" else "."
        }
        return "$scanLine\n$shieldLine\n$hyp\nObserve → correlate → explain before you escalate."
    }

    private fun scanBlurb(memory: QuillaMemorySnapshot): String =
        if (memory.lastScanVerdict == null) {
            "Tools → Nemesis Scanner can collect packages, processes, and file IOCs. " +
                "I will not invent a clean bill of health without that evidence."
        } else {
            "Memory still holds last verdict ${memory.lastScanVerdict}. " +
                "Run another scan if you changed apps, networks, or suspect new residue."
        }

    private fun shieldBlurb(memory: QuillaMemorySnapshot): String =
        if (memory.shieldActive) {
            "Shield tool is active with ${memory.shieldBlocked} blocks. " +
                "Manage it from the Shield screen — stopping it is a deliberate Action."
        } else {
            "Shield tool is idle. Opening Privacy Shield still requires Android VPN consent; " +
                "Quilla will not auto-enable a VPN without you."
        }

    private fun timelineBlurb(memory: QuillaMemorySnapshot): String =
        if (memory.historyCount == 0) {
            "Memory has an empty Scan Timeline. Run Nemesis once to open the ledger, " +
                "then compare later cycles against that baseline."
        } else {
            "Memory holds ${memory.historyCount} timeline entries. " +
                "Open Timeline to compare cycles — one reading is noise, a streak is signal."
        }

    private fun researchBlurb(research: QuillaResearchSnapshot, memory: QuillaMemorySnapshot): String {
        val intel = if (research.synced || research.indicatorCount > 0) {
            "Research loaded ${research.indicatorCount} indicators from ${research.sourceLabel}."
        } else {
            "Research has not synced yet (offline, blocked, or empty feed)."
        }
        val hyp = if (memory.activeHypotheses.isEmpty()) {
            "No correlated hypotheses yet — Research alone is not a verdict."
        } else {
            "Correlated hypotheses available: ${memory.activeHypotheses.size}."
        }
        return "$intel\n$hyp\nDeep analysis here means STIX + on-device correlation, not web chatbots."
    }

    private fun generalBlurb(memory: QuillaMemorySnapshot): String =
        "Brain routed this as a general security question. " +
            statusBlurb(memory) +
            "\nAsk about scan, shield, timeline, research, or my capabilities for a focused module path."
}
