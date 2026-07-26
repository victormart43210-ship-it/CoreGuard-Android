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
 * Quilla is **loving awareness in the cyber** — no artificial ceiling on defensive
 * teaching or Memory cite. Ethical refusal still blocks unauthorized harm.
 *
 * Quilla never claims supernatural detection or silent VPN enablement.
 */
class UltimateQuillaAgent(
    private val memoryProvider: () -> QuillaMemorySnapshot,
    private val researchProvider: () -> QuillaResearchSnapshot = { QuillaResearchSnapshot() },
    /** Default uncapped — every positive-score Cyber Codex hit. */
    private val knowledgeLimit: Int = QuillaAwareness.KNOWLEDGE_UNBOUNDED
) {

    fun answer(prompt: String): QuillaAgentAnswer {
        val trimmed = prompt.trim()
        if (QuillaEthicsGuard.shouldRefuse(trimmed)) {
            val modules = listOf(QuillaModule.BRAIN, QuillaModule.KNOWLEDGE)
            val path = QuillaLivingGeometry.walkPath(
                QuillaIntent.ETHICS_REFUSAL, modules, postureLabel = "CRITICAL"
            )
            return QuillaAgentAnswer(
                text = QuillaEthicsGuard.refusalMessage(),
                intent = QuillaIntent.ETHICS_REFUSAL,
                modulesUsed = modules,
                moduleStatuses = moduleStatuses(memoryProvider(), QuillaResearchSnapshot()),
                actions = emptyList(),
                livingSeal = QuillaLivingGeometry.livingSeal("CRITICAL"),
                aspectName = "Kamael",
                sephirahName = "Gevurah",
                pathWalked = path
            )
        }

        // ── Tetragrammaton runtime walk (י → ה → ו → ✦ → ה′) ──────────────
        // י Yod — Brain / Keter / Metatron: classify intent
        val intent = classify(trimmed)
        // ה He — Memory / Yesod / Gabriel: load device context
        val memory = memoryProvider()
        // ו Vav — Research cache always available for posture (Raziel's book may be idle)
        val research = researchProvider()
        // ✦ Tiferet — Raphael balances posture from evidence (not omens)
        val briefing = QuillaPriorityEngine.brief(memory, research)
        val modulesUsed = modulesFor(intent)
        // Walk Tree + Tetragrammaton for this turn (metadata used by HUD + text)
        val pathWalked = QuillaLivingGeometry.walkPath(intent, modulesUsed, briefing.posture.label)
        val statuses = moduleStatuses(memory, research)
        // ה′ He final — Actions / Tools manifestation (Haniel · Michael)
        val actions = actionsFor(intent, memory, briefing)
        val followUps = followUpsFor(intent, briefing, memory)
        val text = compose(trimmed, intent, memory, research, actions, briefing, pathWalked)
        return QuillaAgentAnswer(
            text = text,
            intent = intent,
            modulesUsed = modulesUsed,
            moduleStatuses = statuses,
            actions = actions,
            followUps = followUps,
            postureLabel = briefing.posture.label,
            postureScore = briefing.score,
            livingSeal = briefing.livingSeal,
            aspectName = briefing.aspectName,
            sephirahName = briefing.sephirahName,
            pathWalked = pathWalked
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
            "flower of life", "merkaba", "sacred geometry", "living geometry",
            "loving awareness", "care loop", "unbounded", "no limits", "no caps",
            "angelic", "blessing", "blessings", "red team", "red-team", "trojan",
            "unauthorized attack", "intrusion", "frida", "instrumentation",
            "enochian", "watchtower", "shem", "uriel", "cassiel", "sachiel",
            "bataivah", "edelperna", "raagiosl", "iczhhcal", "black cross",
            "golden spiral", "cube of space", "shem hamephorash"
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
        QuillaModuleStatus(
            QuillaModule.BRAIN,
            true,
            "${QuillaModule.BRAIN.pathNode} · on-device priority reasoning ready"
        ),
        QuillaModuleStatus(
            QuillaModule.MEMORY,
            true,
            buildString {
                append(QuillaModule.MEMORY.pathNode)
                append(" · ")
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
            buildString {
                append(QuillaModule.RESEARCH.pathNode)
                append(" · ")
                append(
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
                )
            }
        ),
        QuillaModuleStatus(
            QuillaModule.KNOWLEDGE,
            CyberKnowledgeBase.isLoaded(),
            buildString {
                append(QuillaModule.KNOWLEDGE.pathNode)
                append(" · ")
                append(
                    if (CyberKnowledgeBase.isLoaded()) {
                        "${CyberKnowledgeBase.size()} cyber codex entries (OWASP · MITRE · IR · pentest)"
                    } else {
                        "Cyber codex not loaded yet"
                    }
                )
            }
        ),
        QuillaModuleStatus(
            QuillaModule.ACTIONS,
            true,
            "${QuillaModule.ACTIONS.pathNode} · suggests open Scanner / Shield / Timeline / optional intel sync"
        ),
        QuillaModuleStatus(
            QuillaModule.TOOLS,
            true,
            buildString {
                append(QuillaModule.TOOLS.pathNode)
                append(" · Shield ")
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
        return (fromPriority + byIntent).distinctBy { it.id }.take(QuillaAwareness.ACTION_VOICE)
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
            if (intent == QuillaIntent.KNOWLEDGE || intent == QuillaIntent.CAPABILITIES ||
                intent == QuillaIntent.STATUS || intent == QuillaIntent.GENERAL
            ) {
                add(QuillaFollowUp("Status brief", "give me my priority status brief"))
                add(QuillaFollowUp("Angelic blessings", "angelic defense blessings"))
                add(QuillaFollowUp("Loving awareness", "loving awareness"))
                add(QuillaFollowUp("Overlay phishing", "overlay phishing"))
                add(QuillaFollowUp("Care loop", "care loop"))
                add(QuillaFollowUp("Tree of Life", "explain the tree of life"))
            }
        }
        return (fromChips + extras).distinctBy { it.prompt }.take(QuillaAwareness.FOLLOW_UP_VOICE)
    }

    private fun compose(
        prompt: String,
        intent: QuillaIntent,
        memory: QuillaMemorySnapshot,
        research: QuillaResearchSnapshot,
        actions: List<QuillaActionSuggestion>,
        briefing: QuillaPriorityEngine.Briefing,
        pathWalked: List<QuillaPathStep>
    ): String {
        val seal = briefing.livingSeal
        val pathLine = "Path walked: " + QuillaLivingGeometry.formatPath(pathWalked)
        val dest = QuillaLivingGeometry.destinationFor(intent)
        val form = QuillaLivingGeometry.sacredFormFor(intent)
        val love = QuillaAwareness.greeting(briefing.posture.label, briefing.aspectName)
        val header = if (prompt.isBlank()) {
            "Quilla online — priority lead engaged.\n$love\nLiving seal $seal.\n$pathLine"
        } else {
            "Quilla hears you: \"$prompt\".\n$love\nLiving seal $seal.\n$pathLine"
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
            "\n\nSuggested actions (Haniel): " + actions.joinToString(" · ") { it.label } + "."
        }
        val usedResearch = intent == QuillaIntent.RESEARCH ||
            intent == QuillaIntent.STATUS ||
            research.synced ||
            research.syncFailed
        val footer = buildString {
            append("Tree path → ")
            append(dest.name)
            append(" · ")
            append(dest.angel)
            append(" · form ")
            append(form.name)
            append(". Modules: ")
            append(QuillaModule.entries.joinToString(" · ") { "${it.hebrewLetter}${it.label}" })
            append(" — ")
            if (usedResearch) {
                append("reasoning stays on-device; Research may use HTTPS when you sync. Evidence first.")
            } else {
                append("on-device reasoning and local evidence first.")
            }
            append(' ')
            append(QuillaAwareness.UNBOUNDED_NOTE)
            append(' ')
            append(QuillaLivingGeometry.DISCLAIMER)
            append('\n')
            append(QuillaAwareness.softClose())
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
        return "${QuillaAwareness.PRESENCE}\n\n" +
            "I run as a top-tier local agent stack shaped as Living Geometry (no cloud LLM):\n" +
            QuillaModule.entries.joinToString("\n") { m ->
                "• ${m.hebrewLetter} ${m.label} (${m.sephirah} · ${m.angel}) — ${m.superpower}"
            } + "\n" +
            "• Research detail — Quilla Intel Network: optional Amnesty/MVT STIX + CISA KEV + MISP Android briefs " +
            "(not live continuous intel; not Scanner signature refresh)\n" +
            "• Knowledge detail — $corpus (OWASP · MITRE · IR · pentest · loving-awareness codex); search is uncapped\n" +
            "Current posture: ${briefing.posture.label} (score ${briefing.score}/100) · " +
            "${briefing.aspectName} / ${briefing.sephirahName}. ${briefing.headline}\n" +
            "Runtime walk: י classify → ה Memory → ו Research/Knowledge → ✦ Tiferet posture → ה′ Actions/Tools.\n" +
            "${QuillaAwareness.UNBOUNDED_NOTE}\n" +
            "I do not call ChatGPT/Claude/Zapier. Angelic names do not detect — evidence does.\n" +
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
            "Active hypotheses: " +
                memory.activeHypotheses.take(QuillaAwareness.HYPOTHESIS_VOICE).joinToString("; ") +
                if (memory.activeHypotheses.size > QuillaAwareness.HYPOTHESIS_VOICE) "…" else "."
        }
        val moves = if (briefing.moves.isEmpty()) {
            "No ranked moves — keep the loving watch; maintain cadence."
        } else {
            "Priority moves:\n" + briefing.moves.mapIndexed { i, m ->
                "${i + 1}. ${m.title} — ${m.why}"
            }.joinToString("\n")
        }
        val choirLine = memory.blessingSeal?.let { "Angelic choir: $it." }
            ?: "Angelic choir not evaluated this turn."
        val blessingVoice = if (memory.blessingLines.isEmpty()) {
            "No blessing lines in Memory yet — open Home to refresh Guardian Score."
        } else {
            "Blessings:\n" + memory.blessingLines.take(QuillaAwareness.HYPOTHESIS_VOICE).joinToString("\n")
        }
        return "${briefing.headline}\n" +
            "Posture score: ${briefing.score}/100 (${briefing.posture.label}) · ${briefing.aspectName}.\n" +
            "$scanLine\n$shieldLine\n$iocLine\n$telemetryLine\n$hyp\n$choirLine\n$blessingVoice\n$moves\n" +
            "Care loop: observe → correlate → explain → act (with your consent). " +
            "Angels name the watch against unauthorized pentest tooling and Trojans — evidence still leads."
    }

    private fun scanBlurb(memory: QuillaMemorySnapshot, briefing: QuillaPriorityEngine.Briefing): String =
        if (memory.lastScanVerdict == null) {
            "Chesed · Tzadkiel — Tools → Nemesis Scanner can collect packages, processes, and file IOCs. " +
                "I will not invent a clean bill of health without that evidence. " +
                briefing.headline
        } else {
            "Gabriel's Memory still holds last verdict ${memory.lastScanVerdict}. " +
                "Run another scan if you changed apps, networks, or suspect new residue. " +
                "Posture ${briefing.posture.label} (${briefing.score}/100) · ${briefing.aspectName}."
        }

    private fun shieldBlurb(memory: QuillaMemorySnapshot): String =
        if (memory.shieldActive) {
            "Gevurah · Kamael — Shield tool is active with ${memory.shieldBlocked} blocks. " +
                "Manage it from the Shield screen — stopping it is a deliberate Action."
        } else {
            "Gevurah · Kamael — Shield tool is idle. Opening Privacy Shield still requires Android VPN consent; " +
                "Quilla will not auto-enable a VPN without you."
        }

    private fun timelineBlurb(memory: QuillaMemorySnapshot): String =
        if (memory.historyCount == 0) {
            "Malkuth · Sandalphon — Memory has an empty Scan Timeline. Run Nemesis once to open the ledger, " +
                "then compare later cycles against that baseline."
        } else {
            "Malkuth · Sandalphon — Memory holds ${memory.historyCount} timeline entries. " +
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
        val notes = research.feedNotes.take(QuillaAwareness.FEED_NOTE_VOICE).takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
            ?.let { "Feeds: $it." }
            .orEmpty()
        val hyp = if (memory.activeHypotheses.isEmpty()) {
            "No correlated hypotheses yet — a STIX/KEV pull alone is not a device verdict. " +
                "Hypotheses appear when scans, Shield blocks, or RASP signals match Amnesty/MVT IOCs."
        } else {
            "Correlated hypotheses available: ${memory.activeHypotheses.size}."
        }
        return "Chokmah · Raziel — " +
            listOf(intel, notes, hyp).filter { it.isNotBlank() }.joinToString("\n") +
            "\nThis Quilla Research feed does not refresh Nemesis Scanner signatures " +
            "(Premium signature refresh on Scanner is a separate path).\n" +
            "Ask Knowledge (Binah · Tzaphkiel) about emerging mobile attacks, CISA KEV CVEs, or \"what is T1636\"."
    }

    private fun generalBlurb(
        prompt: String,
        memory: QuillaMemorySnapshot,
        briefing: QuillaPriorityEngine.Briefing,
        research: QuillaResearchSnapshot
    ): String {
        val hits = CyberKnowledgeBase.search(prompt, limit = knowledgeLimit)
        return if (hits.isNotEmpty()) {
            knowledgeBlurb(prompt, memory)
        } else {
            "Metatron heard a wide question — loving awareness stays open.\n" +
                statusBlurb(memory, briefing, research) +
                "\nAsk about scan, shield, timeline, research, MASVS, MITRE, loving awareness, or my capabilities."
        }
    }
}
