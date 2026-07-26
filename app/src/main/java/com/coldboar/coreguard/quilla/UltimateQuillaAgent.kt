package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.lore.QuillaKnowledge
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import com.coldboar.coreguard.quilla.knowledge.QuillaEthicsGuard
import com.coldboar.coreguard.quilla.knowledge.QuillaReadyTopics

/**
 * Ultimate Quilla — a local multi-module security agent.
 *
 * Formula (adapted for CoreGuard, no ChatGPT/Claude/Zapier keys):
 * - **Brain** — intent classification + evidence-based answer composition
 * - **Memory** — last scan, timeline size, shield state, hypotheses
 * - **Research** — Amnesty / STIX indicator sync status
 * - **Knowledge** — on-device cyber codex (OWASP, MITRE ATT&CK Mobile, pentest, IR)
 *   plus optional Observatory Codex metaphors (never treated as detection)
 * - **Actions** — suggested automations (scan, shield, timeline, intel sync)
 * - **Tools** — Nemesis Scanner, Privacy Shield, Scan Timeline
 *
 * Quilla never claims supernatural detection or silent VPN enablement.
 */
class UltimateQuillaAgent(
    private val memoryProvider: () -> QuillaMemorySnapshot,
    private val researchProvider: () -> QuillaResearchSnapshot = { QuillaResearchSnapshot() },
    private val knowledgeLimit: Int = 3
) {

    fun answer(prompt: String): QuillaAgentAnswer {
        val trimmed = prompt.trim()
        if (QuillaEthicsGuard.shouldRefuse(trimmed)) {
            return QuillaAgentAnswer(
                text = QuillaEthicsGuard.refusalMessage(),
                intent = QuillaIntent.ETHICS_REFUSAL,
                modulesUsed = listOf(QuillaModule.BRAIN, QuillaModule.KNOWLEDGE),
                moduleStatuses = moduleStatuses(memoryProvider(), QuillaResearchSnapshot()),
                actions = emptyList()
            )
        }

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

        // Product-ready topics always route to Knowledge (MASVS-NETWORK, T1636, …).
        if (QuillaReadyTopics.resolveEntryId(prompt) != null) {
            return QuillaIntent.KNOWLEDGE
        }

        return when {
            p.contains("what can you") || p.contains("capabilities") ||
                p.contains("ultimate") || p.contains("modules") ||
                p.contains("formula") || p.contains("who are you") ||
                p.contains("knowledge base") || p.contains("cyber codex") ->
                QuillaIntent.CAPABILITIES

            p.contains("research") || p.contains("intel") || p.contains("stix") ||
                p.contains("amnesty") ||
                (p.contains("ioc") && !p.contains("mitre")) ||
                (p.contains("indicator") && p.contains("sync")) ->
                QuillaIntent.RESEARCH

            p.contains("shield") || p.contains("vpn") || p.contains("block") -> QuillaIntent.SHIELD
            p.contains("timeline") || p.contains("history") || p.contains("ledger") ->
                QuillaIntent.TIMELINE
            p.contains("nemesis") || (p.contains("scan") && !isKnowledgeHeavy(p)) ||
                (p.contains("pegasus") && p.contains("scan")) ->
                QuillaIntent.SCAN
            p.contains("safe") || p.contains("status") || p.contains("how am i") ||
                p.contains("my risk") || p.contains("protect me") -> QuillaIntent.STATUS

            QuillaKnowledge.matchFragment(p) != null ||
                isKnowledgeHeavy(p) ||
                CyberKnowledgeBase.search(p, limit = 1).isNotEmpty() ->
                QuillaIntent.KNOWLEDGE

            else -> QuillaIntent.GENERAL
        }
    }

    private fun isKnowledgeHeavy(p: String): Boolean {
        val keys = listOf(
            "owasp", "masvs", "mastg", "mitre", "att&ck", "attack technique",
            "pentest", "penetration", "red team", "blue team", "purple team",
            "incident response", "triage", "ioc ", "stix", "tls", "certificate pinning",
            "sideload", "accessibility", "hardening", "permission", "phishing",
            "smishing", "banking trojan", "supply chain", "ransomware", "zero-day",
            "zero day", "rules of engagement", "kill chain", "t16", "t14", "t15",
            "masvs-", "explain ", "what is ", "how does ", "define ",
            "observatory", "sky-watcher", "sky watcher", "maya", "calendar cycle",
            "relay", "lunar", "ancient", "codex"
        )
        return keys.any { p.contains(it) } || p.matches(Regex(".*\\bt\\d{4}\\b.*"))
    }

    private fun modulesFor(intent: QuillaIntent): List<QuillaModule> = when (intent) {
        QuillaIntent.CAPABILITIES -> QuillaModule.entries
        QuillaIntent.STATUS -> listOf(QuillaModule.BRAIN, QuillaModule.MEMORY, QuillaModule.TOOLS)
        QuillaIntent.SCAN -> listOf(QuillaModule.BRAIN, QuillaModule.ACTIONS, QuillaModule.TOOLS)
        QuillaIntent.SHIELD -> listOf(QuillaModule.BRAIN, QuillaModule.MEMORY, QuillaModule.ACTIONS, QuillaModule.TOOLS)
        QuillaIntent.TIMELINE -> listOf(QuillaModule.BRAIN, QuillaModule.MEMORY, QuillaModule.TOOLS)
        QuillaIntent.RESEARCH -> listOf(QuillaModule.BRAIN, QuillaModule.RESEARCH, QuillaModule.MEMORY)
        QuillaIntent.KNOWLEDGE -> listOf(QuillaModule.BRAIN, QuillaModule.KNOWLEDGE, QuillaModule.MEMORY)
        QuillaIntent.ETHICS_REFUSAL -> listOf(QuillaModule.BRAIN, QuillaModule.KNOWLEDGE)
        QuillaIntent.GENERAL -> listOf(
            QuillaModule.BRAIN, QuillaModule.KNOWLEDGE, QuillaModule.MEMORY, QuillaModule.ACTIONS
        )
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
            QuillaModule.KNOWLEDGE,
            CyberKnowledgeBase.isLoaded(),
            if (CyberKnowledgeBase.isLoaded()) {
                "${CyberKnowledgeBase.size()} cyber codex entries (OWASP · MITRE · IR · pentest)"
            } else {
                "Cyber codex not loaded yet"
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
            QuillaIntent.KNOWLEDGE -> listOf(scan, shield)
            QuillaIntent.ETHICS_REFUSAL -> emptyList()
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
            QuillaIntent.KNOWLEDGE -> knowledgeBlurb(prompt, memory)
            QuillaIntent.ETHICS_REFUSAL -> QuillaEthicsGuard.refusalMessage()
            QuillaIntent.GENERAL -> generalBlurb(prompt, memory)
        }
        val actionLine = if (actions.isEmpty()) {
            ""
        } else {
            "\n\nSuggested actions: " + actions.joinToString(" · ") { it.label } + "."
        }
        return "$header\n\n$body$actionLine\n\n" +
            "Modules: Brain · Memory · Research · Knowledge · Actions · Tools — on-device, evidence first."
    }

    private fun capabilitiesBlurb(): String {
        val corpus = if (CyberKnowledgeBase.isLoaded()) {
            "${CyberKnowledgeBase.size()} local cyber codex entries"
        } else {
            "cyber codex loading on first open"
        }
        return "I run as a full local agent stack:\n" +
            "• Brain — classify intent and decide next checks\n" +
            "• Memory — cite last scan, timeline, shield, hypotheses\n" +
            "• Research — sync Amnesty STIX threat intel\n" +
            "• Knowledge — $corpus (OWASP MASVS/MASTG, MITRE ATT&CK Mobile, pentest methodology, IR, Android hardening)\n" +
            "• Actions — propose scan / shield / timeline / intel sync\n" +
            "• Tools — Nemesis Scanner, Privacy Shield, Scan Timeline\n" +
            "I do not call ChatGPT/Claude/Zapier. I teach defense and correlate CoreGuard evidence.\n" +
            "Ready prompts: " + QuillaReadyTopics.suggestionPrompts().joinToString(" · ") { "\"$it\"" } + "."
    }

    private fun knowledgeBlurb(prompt: String, memory: QuillaMemorySnapshot): String {
        val hits = CyberKnowledgeBase.search(prompt, limit = knowledgeLimit)
        if (hits.isEmpty()) {
            val loreHit = QuillaKnowledge.matchFragment(prompt.lowercase())
            if (loreHit != null) {
                return QuillaKnowledge.answer(prompt)
            }
            return "Knowledge found no strong codex match yet. Try a ready prompt: " +
                QuillaReadyTopics.suggestionPrompts().joinToString(", ") +
                " — or ask about Observatory cycles / relays in the Secret Portal.\n" +
                statusBlurb(memory)
        }
        val primary = hits.first()
        val readyId = QuillaReadyTopics.resolveEntryId(prompt)
        val header = if (readyId != null && primary.entry.id == readyId) {
            "Ready topic locked — Quilla Cyber Codex:\n\n"
        } else {
            "Pulling from Quilla Cyber Codex:\n\n"
        }
        val articles = hits.joinToString("\n\n—\n\n") { CyberKnowledgeBase.formatHit(it) }
        val deviceBridge = when {
            memory.lastScanVerdict == null ->
                "\n\nDevice bridge: no recent Nemesis scan in Memory — run one to connect this lesson to evidence."
            else ->
                "\n\nDevice bridge: last scan=${memory.lastScanVerdict}" +
                    (memory.lastScanDetections?.let { " ($it detections)" } ?: "") +
                    "; shield=${if (memory.shieldActive) "ON" else "OFF"}."
        }
        return "$header$articles$deviceBridge"
    }

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
        return "$intel\n$hyp\nDeep analysis here means STIX + on-device correlation, not web chatbots. " +
            "For framework education (ATT&CK/OWASP), ask Knowledge questions like \"what is T1636\"."
    }

    private fun generalBlurb(prompt: String, memory: QuillaMemorySnapshot): String {
        val hits = CyberKnowledgeBase.search(prompt, limit = 2)
        return if (hits.isNotEmpty()) {
            knowledgeBlurb(prompt, memory)
        } else {
            "Brain routed this as a general security question.\n" +
                statusBlurb(memory) +
                "\nAsk about scan, shield, timeline, research, MASVS, MITRE techniques, or my capabilities."
        }
    }
}
