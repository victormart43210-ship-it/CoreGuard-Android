package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.lore.QuillaKnowledge
import com.coldboar.coreguard.lore.QuillaLivingGeometry
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import com.coldboar.coreguard.quilla.knowledge.QuillaEthicsGuard
import com.coldboar.coreguard.quilla.knowledge.QuillaReadyTopics

/**
 * Ultimate Quilla — a local multi-module security agent (top-tier posture lead).
 *
 * Formula (adapted for CoreGuard, no ChatGPT/Claude/Zapier keys):
 * - **Brain** — intent classification + evidence-based answer composition
 * - **Memory** — last scan, timeline size, shield state, hypotheses, telemetry
 * - **Research** — Amnesty / MVT STIX indicator sync + on-device IOC correlation
 * - **Knowledge** — on-device cyber codex (OWASP, MITRE ATT&CK Mobile, pentest, IR)
 *   plus Observatory Codex + Living Geometry metaphors (never treated as detection)
 * - **Actions** — suggested automations (scan, shield, timeline, intel sync)
 * - **Tools** — Nemesis Scanner, Privacy Shield, Scan Timeline
 * - **Priority** — [QuillaPriorityEngine] ranks posture and next moves
 *
 * Living Geometry maps Tetragrammaton / Tree of Life / angelic names onto modules
 * for voice and teaching only — not as scanners.
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
        // Cached research is always available for posture / status / capabilities.
        val research = researchProvider()
        val briefing = QuillaPriorityEngine.brief(memory, research)
        val modulesUsed = modulesFor(intent)
        val statuses = moduleStatuses(memory, research)
        val actions = actionsFor(intent, memory, briefing)
        val followUps = followUpsFor(intent, briefing, memory)
        val text = compose(trimmed, intent, memory, research, actions, briefing)
        return QuillaAgentAnswer(
            text = text,
            intent = intent,
            modulesUsed = modulesUsed,
            moduleStatuses = statuses,
            actions = actions,
            followUps = followUps,
            postureLabel = briefing.posture.label,
            postureScore = briefing.score
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
            // Capabilities: avoid matching incidental "ultimate" / "formula" in security questions.
            p.contains("what can you") || p.contains("capabilities") ||
                p.contains("who are you") || p.contains("your modules") ||
                p.contains("knowledge base") || p.contains("cyber codex") ||
                (p.contains("ultimate") && (p.contains("agent") || p.contains("quilla"))) ||
                (p.contains("formula") && (p.contains("agent") || p.contains("quilla"))) ->
                QuillaIntent.CAPABILITIES

            p.contains("research") || p.contains("stix") || p.contains("amnesty") ||
                (p.contains("intel") && (p.contains("sync") || p.contains("threat"))) ||
                (p.contains("indicator") && p.contains("sync")) ||
                (p.contains("ioc") && (p.contains("sync") || p.contains("feed") || p.contains("refresh"))) ->
                QuillaIntent.RESEARCH

            p.contains("shield") || p.contains("vpn") || p.contains("block") -> QuillaIntent.SHIELD
            p.contains("timeline") || p.contains("history") || p.contains("ledger") ->
                QuillaIntent.TIMELINE
            p.contains("nemesis") || (p.contains("scan") && !isKnowledgeHeavy(p)) ||
                (p.contains("pegasus") && p.contains("scan")) ->
                QuillaIntent.SCAN
            p.contains("priority status") || p.contains("status brief") ||
                p.contains("priority brief") || p.contains("my posture") ||
                p.contains("posture score") || p.contains("safe") || p.contains("status") ||
                p.contains("how am i") || p.contains("my risk") || p.contains("protect me") ->
                QuillaIntent.STATUS

            QuillaKnowledge.matchLivingOrObservatory(p) ||
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
            "relay", "lunar", "ancient", "codex",
            "kabbalah", "qabalah", "quaballa", "tree of life", "sephirot", "sephiroth",
            "tetragrammaton", "metatron", "raphael", "gabriel", "sandalphon",
            "flower of life", "merkaba", "sacred geometry", "living geometry"
        )
        return keys.any { p.contains(it) } || p.matches(Regex(".*\\bt\\d{4}\\b.*"))
    }

    private fun modulesFor(intent: QuillaIntent): List<QuillaModule> = when (intent) {
        QuillaIntent.CAPABILITIES -> QuillaModule.entries
        QuillaIntent.STATUS -> listOf(
            QuillaModule.BRAIN, QuillaModule.MEMORY, QuillaModule.RESEARCH, QuillaModule.TOOLS
        )
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
        QuillaModuleStatus(QuillaModule.BRAIN, true, "On-device priority reasoning ready"),
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
                if (memory.telemetryDeltaCount > 0) {
                    append(" · telemetry=")
                    append(memory.telemetryDeltaCount)
                }
            }
        ),
        QuillaModuleStatus(
            QuillaModule.RESEARCH,
            research.synced || research.indicatorCount > 0 || memory.correlatorIndicatorCount > 0,
            when {
                research.syncFailed ->
                    "Intel sync failed — still using prior cache (${research.indicatorCount} indicators)"
                research.synced && research.indicatorCount == 0 ->
                    "Synced empty campaign archive — not a Nemesis signature refresh"
                research.synced || research.indicatorCount > 0 ->
                    "${research.indicatorCount} ${research.sourceLabel} indicators cached"
                memory.correlatorIndicatorCount > 0 ->
                    "${memory.correlatorIndicatorCount} correlator IOCs loaded (local)"
                else ->
                    "Intel idle — optional STIX pull (does not refresh Scanner signatures)"
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
            "Suggests open Scanner / Shield / Timeline / optional intel sync"
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

    private fun actionsFor(
        intent: QuillaIntent,
        memory: QuillaMemorySnapshot,
        briefing: QuillaPriorityEngine.Briefing
    ): List<QuillaActionSuggestion> {
        val catalog = actionCatalog(memory)
        // Keep catalog honesty copy (no silent scan / no signature refresh); priority only ranks order.
        val fromPriority = briefing.moves.mapNotNull { move ->
            when (move.id) {
                QuillaActionSuggestion.RUN_SCAN -> catalog[QuillaActionSuggestion.RUN_SCAN]
                QuillaActionSuggestion.OPEN_SHIELD -> catalog[QuillaActionSuggestion.OPEN_SHIELD]
                QuillaActionSuggestion.OPEN_TIMELINE -> catalog[QuillaActionSuggestion.OPEN_TIMELINE]
                QuillaActionSuggestion.SYNC_INTEL -> catalog[QuillaActionSuggestion.SYNC_INTEL]
                else -> null
            }
        }
        val byIntent = when (intent) {
            QuillaIntent.SCAN -> listOfNotNull(
                catalog[QuillaActionSuggestion.RUN_SCAN],
                catalog[QuillaActionSuggestion.OPEN_TIMELINE]
            )
            QuillaIntent.SHIELD -> listOfNotNull(
                catalog[QuillaActionSuggestion.OPEN_SHIELD],
                catalog[QuillaActionSuggestion.RUN_SCAN]
            )
            QuillaIntent.TIMELINE -> listOfNotNull(
                catalog[QuillaActionSuggestion.OPEN_TIMELINE],
                catalog[QuillaActionSuggestion.RUN_SCAN]
            )
            QuillaIntent.RESEARCH -> listOfNotNull(
                catalog[QuillaActionSuggestion.SYNC_INTEL],
                catalog[QuillaActionSuggestion.RUN_SCAN]
            )
            QuillaIntent.STATUS -> fromPriority.ifEmpty {
                listOfNotNull(
                    catalog[QuillaActionSuggestion.RUN_SCAN],
                    catalog[QuillaActionSuggestion.OPEN_SHIELD],
                    catalog[QuillaActionSuggestion.OPEN_TIMELINE]
                )
            }
            QuillaIntent.KNOWLEDGE -> listOfNotNull(
                catalog[QuillaActionSuggestion.RUN_SCAN],
                catalog[QuillaActionSuggestion.OPEN_SHIELD]
            )
            QuillaIntent.ETHICS_REFUSAL -> emptyList()
            QuillaIntent.CAPABILITIES, QuillaIntent.GENERAL -> listOfNotNull(
                catalog[QuillaActionSuggestion.RUN_SCAN],
                catalog[QuillaActionSuggestion.OPEN_SHIELD],
                catalog[QuillaActionSuggestion.OPEN_TIMELINE],
                catalog[QuillaActionSuggestion.SYNC_INTEL]
            )
        }
        return (fromPriority + byIntent).distinctBy { it.id }.take(4)
    }

    private fun actionCatalog(memory: QuillaMemorySnapshot): Map<String, QuillaActionSuggestion> = mapOf(
        QuillaActionSuggestion.RUN_SCAN to QuillaActionSuggestion(
            QuillaActionSuggestion.RUN_SCAN,
            "Open Scanner",
            "Opens the Nemesis Scanner screen so you can start a scan. Quilla does not run scans silently."
        ),
        QuillaActionSuggestion.OPEN_SHIELD to QuillaActionSuggestion(
            QuillaActionSuggestion.OPEN_SHIELD,
            if (memory.shieldActive) "Manage Privacy Shield" else "Open Privacy Shield",
            "Shield start still requires Android VPN consent — Quilla will not bypass that."
        ),
        QuillaActionSuggestion.OPEN_TIMELINE to QuillaActionSuggestion(
            QuillaActionSuggestion.OPEN_TIMELINE,
            "Open Scan Timeline",
            "Review prior scan history on this device."
        ),
        QuillaActionSuggestion.SYNC_INTEL to QuillaActionSuggestion(
            QuillaActionSuggestion.SYNC_INTEL,
            "Sync Quilla Intel Network",
            "Pulls Amnesty/MVT STIX, CISA KEV, and MISP Android intel into Quilla for defensive correlation — does not refresh Nemesis Scanner signatures."
        )
    )

    private fun followUpsFor(
        intent: QuillaIntent,
        briefing: QuillaPriorityEngine.Briefing,
        memory: QuillaMemorySnapshot
    ): List<QuillaFollowUp> {
        if (intent == QuillaIntent.ETHICS_REFUSAL) return emptyList()
        val fromChips = briefing.chipPrompts.map { (label, prompt) ->
            QuillaFollowUp(label, prompt)
        }
        val extras = buildList {
            if (memory.activeHypotheses.isNotEmpty()) {
                add(QuillaFollowUp("Hypotheses", "review my quilla hypotheses and status"))
            }
            if (intent == QuillaIntent.KNOWLEDGE || intent == QuillaIntent.CAPABILITIES) {
                add(QuillaFollowUp("Status brief", "give me my priority status brief"))
                add(QuillaFollowUp("Tree of Life", "explain the tree of life"))
                add(QuillaFollowUp("Tetragrammaton", "what is the tetragrammaton for quilla"))
            }
        }
        return (fromChips + extras).distinctBy { it.prompt }.take(5)
    }

    private fun compose(
        prompt: String,
        intent: QuillaIntent,
        memory: QuillaMemorySnapshot,
        research: QuillaResearchSnapshot,
        actions: List<QuillaActionSuggestion>,
        briefing: QuillaPriorityEngine.Briefing
    ): String {
        val seal = QuillaLivingGeometry.livingSeal(briefing.posture.label)
        val header = if (prompt.isBlank()) {
            "Quilla online — priority lead engaged. Living seal $seal."
        } else {
            "Quilla hears you: \"$prompt\".\nLiving seal $seal."
        }
        val body = when (intent) {
            QuillaIntent.CAPABILITIES -> capabilitiesBlurb(briefing)
            QuillaIntent.STATUS -> statusBlurb(memory, briefing, research)
            QuillaIntent.SCAN -> scanBlurb(memory, briefing)
            QuillaIntent.SHIELD -> shieldBlurb(memory)
            QuillaIntent.TIMELINE -> timelineBlurb(memory)
            QuillaIntent.RESEARCH -> researchBlurb(research, memory)
            QuillaIntent.KNOWLEDGE -> knowledgeBlurb(prompt, memory)
            QuillaIntent.ETHICS_REFUSAL -> QuillaEthicsGuard.refusalMessage()
            QuillaIntent.GENERAL -> generalBlurb(prompt, memory, briefing, research)
        }
        val actionLine = if (actions.isEmpty()) {
            ""
        } else {
            "\n\nSuggested actions: " + actions.joinToString(" · ") { it.label } + "."
        }
        val usedResearch = intent == QuillaIntent.RESEARCH ||
            intent == QuillaIntent.STATUS ||
            research.synced ||
            research.syncFailed
        val footer = if (usedResearch) {
            "Modules: Brain · Memory · Research · Knowledge · Actions · Tools — " +
                "reasoning stays on-device; Research may use HTTPS when you sync. Evidence first."
        } else {
            "Modules: Brain · Memory · Research · Knowledge · Actions · Tools — " +
                "on-device reasoning and local evidence first."
        }
        // Lore answers already include their own "Quilla hears you" header.
        return if (body.startsWith("Quilla hears you:")) {
            "$body$actionLine\n\n$footer"
        } else {
            "$header\n\n$body$actionLine\n\n$footer"
        }
    }

    private fun capabilitiesBlurb(briefing: QuillaPriorityEngine.Briefing): String {
        val corpus = if (CyberKnowledgeBase.isLoaded()) {
            "${CyberKnowledgeBase.size()} local cyber codex entries"
        } else {
            "cyber codex loading on first open"
        }
        return "I run as a top-tier local agent stack (no cloud LLM):\n" +
            "• Brain — classify intent, rank posture, decide next checks\n" +
            "• Memory — cite last scan, timeline, shield, hypotheses, signed telemetry\n" +
            "• Research — Quilla Intel Network: optional Amnesty/MVT STIX + CISA KEV + MISP Android briefs + on-device IOC correlation (not live continuous intel; not Scanner signature refresh)\n" +
            "• Knowledge — $corpus (OWASP MASVS/MASTG, MITRE ATT&CK Mobile, pentest methodology, IR, Android hardening)\n" +
            "• Actions — suggest open Scanner / Shield / Timeline / optional intel sync\n" +
            "• Tools — Nemesis Scanner, Privacy Shield, Scan Timeline\n" +
            "Current posture: ${briefing.posture.label} (score ${briefing.score}/100). ${briefing.headline}\n" +
            "Living Geometry (metaphor): Tetragrammaton י־ה־ו־ה maps Brain→Memory→Research/Knowledge→Actions/Tools; " +
            "Tree of Life angels name my aspects — never my detectors.\n" +
            "I do not call ChatGPT/Claude/Zapier. I teach defense and cite CoreGuard evidence.\n" +
            "Ready prompts: " + QuillaReadyTopics.suggestionPrompts().joinToString(" · ") { "\"$it\"" } +
            " · \"tree of life\" · \"tetragrammaton\" · \"metatron\"."
    }

    private fun knowledgeBlurb(prompt: String, memory: QuillaMemorySnapshot): String {
        val hits = CyberKnowledgeBase.search(prompt, limit = knowledgeLimit)
        if (hits.isEmpty()) {
            if (QuillaKnowledge.matchLivingOrObservatory(prompt.lowercase())) {
                return QuillaKnowledge.answer(prompt)
            }
            return "Knowledge found no strong codex match yet. Try a ready prompt: " +
                QuillaReadyTopics.suggestionPrompts().joinToString(", ") +
                " — or ask about the Tree of Life, Tetragrammaton, Metatron, or Observatory cycles in the Secret Portal.\n" +
                statusBlurb(memory, QuillaPriorityEngine.brief(memory), QuillaResearchSnapshot())
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
                    "; shield=${if (memory.shieldActive) "ON" else "OFF"}" +
                    "; correlator=${memory.correlatorIndicatorCount}."
        }
        return "$header$articles$deviceBridge"
    }

    private fun statusBlurb(
        memory: QuillaMemorySnapshot,
        briefing: QuillaPriorityEngine.Briefing,
        research: QuillaResearchSnapshot
    ): String {
        val scanLine = if (memory.lastScanVerdict == null) {
            "No recent Nemesis scan in Memory — run a scan before claiming the device is clean."
        } else {
            val titles = memory.lastScanDetectionTitles.takeIf { it.isNotEmpty() }
                ?.joinToString("; ")
                ?.let { " Notable: $it." }
                .orEmpty()
            "Last scan verdict: ${memory.lastScanVerdict}" +
                (memory.lastScanDetections?.let { " ($it detections)" } ?: "") +
                ". Timeline holds ${memory.historyCount} entries.$titles"
        }
        val shieldLine = if (memory.shieldActive) {
            "Privacy Shield is ON (${memory.shieldBlocked} domains blocked" +
                (memory.lastBlockedDomain?.let { ", last=$it" } ?: "") + ")."
        } else {
            "Privacy Shield is OFF — DNS IOC/tracker filtering is idle until you enable it with VPN consent."
        }
        val iocLine = buildString {
            if (memory.mvtIocInventoryCount > 0) {
                append("MVT-style on-device IOC inventory: ${memory.mvtIocInventoryCount}.")
            } else {
                append("MVT-style on-device IOC inventory not loaded yet.")
            }
            if (memory.correlatorIndicatorCount > 0) {
                append(" Correlator armed with ${memory.correlatorIndicatorCount} indicators.")
            }
            if (research.synced || research.indicatorCount > 0) {
                append(" Research cache: ${research.indicatorCount} (${research.sourceLabel}).")
            }
        }
        val telemetryLine = if (memory.telemetryDeltaCount == 0) {
            "Signed telemetry ring is empty."
        } else {
            "Signed telemetry: ${memory.telemetryDeltaCount} frames" +
                if (memory.telemetryHighSeverity) " — HIGH/CRITICAL signals present." else "."
        }
        val hyp = if (memory.activeHypotheses.isEmpty()) {
            "No active Quilla hypotheses stored."
        } else {
            "Active hypotheses: " + memory.activeHypotheses.take(3).joinToString("; ") +
                if (memory.activeHypotheses.size > 3) "…" else "."
        }
        val moves = if (briefing.moves.isEmpty()) {
            "No ranked moves — maintain cadence."
        } else {
            "Priority moves:\n" + briefing.moves.mapIndexed { i, m ->
                "${i + 1}. ${m.title} — ${m.why}"
            }.joinToString("\n")
        }
        return "${briefing.headline}\n" +
            "Posture score: ${briefing.score}/100 (${briefing.posture.label}).\n" +
            "$scanLine\n$shieldLine\n$iocLine\n$telemetryLine\n$hyp\n$moves\n" +
            "Observe → correlate → explain before you escalate."
    }

    private fun scanBlurb(memory: QuillaMemorySnapshot, briefing: QuillaPriorityEngine.Briefing): String =
        if (memory.lastScanVerdict == null) {
            "Tools → Nemesis Scanner can collect packages, processes, and file IOCs. " +
                "I will not invent a clean bill of health without that evidence. " +
                briefing.headline
        } else {
            "Memory still holds last verdict ${memory.lastScanVerdict}. " +
                "Run another scan if you changed apps, networks, or suspect new residue. " +
                "Posture ${briefing.posture.label} (${briefing.score}/100)."
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
        val intel = when {
            research.syncFailed ->
                "Intel Network sync failed (network or parse error). Cached indicators: ${research.indicatorCount}" +
                    " (on-device MVT IOCs still usable: ${research.mvtOnDeviceCount})."
            research.synced && research.indicatorCount == 0 && research.webKnowledgeCount == 0 ->
                "Intel Network synced empty public feeds from ${research.sourceLabel}."
            research.synced || research.indicatorCount > 0 || research.webKnowledgeCount > 0 ->
                "Intel Network cached ${research.indicatorCount} correlator indicators + " +
                    "${research.webKnowledgeCount} web knowledge briefs from ${research.sourceLabel}" +
                    " (STIX=${research.remoteIndicatorCount}, on-device MVT=${research.mvtOnDeviceCount})."
            else ->
                "Intel Network has not synced yet. Sync is optional and uses HTTPS for public " +
                    "Amnesty/MVT STIX, CISA KEV, and MISP Android galaxy when available."
        }
        val notes = research.feedNotes.take(4).takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
            ?.let { "Feeds: $it." }
            .orEmpty()
        val hyp = if (memory.activeHypotheses.isEmpty()) {
            "No correlated hypotheses yet — a STIX/KEV pull alone is not a device verdict. " +
                "Hypotheses appear when scans, Shield blocks, or RASP signals match Amnesty/MVT IOCs."
        } else {
            "Correlated hypotheses available: ${memory.activeHypotheses.size}."
        }
        return listOf(intel, notes, hyp).filter { it.isNotBlank() }.joinToString("\n") +
            "\nThis Quilla Research feed does not refresh Nemesis Scanner signatures " +
            "(Premium signature refresh on Scanner is a separate path).\n" +
            "Ask Knowledge about emerging mobile attacks, CISA KEV CVEs, or \"what is T1636\"."
    }

    private fun generalBlurb(
        prompt: String,
        memory: QuillaMemorySnapshot,
        briefing: QuillaPriorityEngine.Briefing,
        research: QuillaResearchSnapshot
    ): String {
        val hits = CyberKnowledgeBase.search(prompt, limit = 2)
        return if (hits.isNotEmpty()) {
            knowledgeBlurb(prompt, memory)
        } else {
            "Brain routed this as a general security question.\n" +
                statusBlurb(memory, briefing, research) +
                "\nAsk about scan, shield, timeline, research, MASVS, MITRE techniques, or my capabilities."
        }
    }
}
