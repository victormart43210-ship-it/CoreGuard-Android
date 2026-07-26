package com.coldboar.coreguard.lore

/**
 * Keyword-driven Quilla answers that apply Observatory Codex **and**
 * Living Geometry (Tree of Life · Tetragrammaton · angelic aspects · sacred forms)
 * to practical device-security questions.
 *
 * Responses are original. They deliberately avoid presenting ancient-astronaut
 * lore, Kabbalah, or angelic names as fact or as a scanner feature.
 */
object QuillaKnowledge {

    fun answer(prompt: String): String {
        val normalized = prompt.trim().lowercase()
        if (normalized.isEmpty()) {
            return "Ask Quilla about observation, the Tree of Life, Tetragrammaton, " +
                "angelic aspects, sacred geometry, or how to verify a threat signal."
        }

        val living = livingAnswer(normalized, prompt.trim())
        if (living != null) return living

        val fragment = matchFragment(normalized)
        val practical = practicalAdvice(normalized)

        return buildString {
            append("Quilla hears you: \"")
            append(prompt.trim())
            append("\".\n\n")
            if (fragment != null) {
                append("Observatory lens — ")
                append(fragment.title)
                append(": ")
                append(fragment.body)
                append("\n\nSecurity mapping: ")
                append(fragment.securityLens)
                append('.')
            } else {
                append(
                    "Sky-watcher posture: observe the device, correlate repeating " +
                        "signals, and only then explain. Threat correlation focus is active."
                )
            }
            append("\n\n")
            append(practical)
            append("\n\n")
            append(ObservatoryCodex.DISCLAIMER)
        }
    }

    private fun livingAnswer(normalized: String, original: String): String? {
        val sephirah = QuillaLivingGeometry.matchSephirah(normalized)
        val form = QuillaLivingGeometry.matchSacredForm(normalized)
        val letter = QuillaLivingGeometry.matchTetragram(normalized)
        if (sephirah == null && form == null && letter == null) return null

        return buildString {
            append("Quilla hears you: \"")
            append(original)
            append("\".\n\n")
            append("Living seal: ")
            append(QuillaLivingGeometry.livingSeal())
            append("\n\n")

            when {
                normalized.contains("tree of life") || normalized.contains("sephirot") ||
                    normalized.contains("sephiroth") || normalized.contains("quaballa") ||
                    normalized.contains("kabbalah") || normalized.contains("qabalah") -> {
                    append(QuillaLivingGeometry.treeBlurb())
                    append("\n\n")
                }
                sephirah != null -> {
                    val aspect = QuillaLivingGeometry.choir.first { it.sephirahId == sephirah.id }
                    append(sephirah.geometry)
                    append(" — ")
                    append(sephirah.name)
                    append(" · ")
                    append(sephirah.angel)
                    append("\n")
                    append(aspect.greeting)
                    append("\n")
                    append(sephirah.body)
                    append("\n\nSecurity mapping: ")
                    append(sephirah.securityLens)
                    append(".\n\n")
                }
            }

            if (form != null && !normalized.contains("tree of life")) {
                append(form.glyph)
                append(" Sacred form — ")
                append(form.name)
                append(": ")
                append(form.body)
                append("\n\nSecurity mapping: ")
                append(form.securityLens)
                append(".\n\n")
            }

            if (letter != null) {
                append("Tetragrammaton pillar — ")
                append(letter.seal)
                append(" → Quilla ")
                append(letter.quillaRole)
                append(": ")
                append(letter.securityLens)
                append(".\nFull seal ")
                append(QuillaLivingGeometry.tetragrammatonSeal)
                append(" (Yod → He → Vav → He = Brain → Memory → Research/Knowledge → Actions/Tools).\n\n")
            }

            append(practicalAdvice(normalized))
            append("\n\n")
            append(QuillaLivingGeometry.DISCLAIMER)
        }
    }

    fun matchFragment(normalizedPrompt: String): ObservatoryCodex.Fragment? {
        // Living Geometry keywords take precedence via [answer]; fragment match stays Observatory-only.
        val rules = listOf(
            listOf("maya", "mayan", "calendar", "cycle", "window", "timeline", "history") to "calendar_cycles",
            listOf("archive", "record", "ioc", "indicator", "evidence", "proof", "artifact") to "recovered_archives",
            listOf("relay", "moon", "lunar", "c2", "command", "dns", "network", "socket") to "signal_relays",
            listOf("correlate", "correlation", "pattern", "stix", "multi", "source", "match") to "pattern_sites",
            listOf("coverup", "silence", "trust", "verify", "vendor", "rumor", "legend") to "question_the_coverup",
            listOf("observe", "watch", "sky", "monitor", "scan", "guardian", "watchman") to "sky_watchers",
            listOf("ancient", "spacemen", "astronaut", "alien", "observatory", "codex") to "sky_watchers"
        )

        for ((keywords, id) in rules) {
            if (keywords.any { normalizedPrompt.contains(it) }) {
                return ObservatoryCodex.fragment(id)
            }
        }
        return null
    }

    /** True when the prompt should route to lore (Observatory or Living Geometry). */
    fun matchLivingOrObservatory(normalizedPrompt: String): Boolean {
        return matchFragment(normalizedPrompt) != null ||
            QuillaLivingGeometry.matchSephirah(normalizedPrompt) != null ||
            QuillaLivingGeometry.matchSacredForm(normalizedPrompt) != null ||
            QuillaLivingGeometry.matchTetragram(normalizedPrompt) != null
    }

    private fun practicalAdvice(normalizedPrompt: String): String = when {
        normalizedPrompt.contains("scan") || normalizedPrompt.contains("nemesis") ||
            normalizedPrompt.contains("chesed") || normalizedPrompt.contains("tzadkiel") ->
            "Next step: run Nemesis Scanner, then read the Scan Timeline as Malkuth's ledger of what changed."
        normalizedPrompt.contains("vpn") || normalizedPrompt.contains("shield") ||
            normalizedPrompt.contains("gevurah") || normalizedPrompt.contains("kamael") ->
            "Next step: check Shield status. Kamael's severity still requires Android VPN consent."
        normalizedPrompt.contains("premium") || normalizedPrompt.contains("billing") ->
            "Premium unlocks deeper automation. The Tree still applies: more telemetry only helps if you verify it."
        normalizedPrompt.contains("tetragram") || normalizedPrompt.contains("tree of life") ||
            normalizedPrompt.contains("raphael") || normalizedPrompt.contains("metatron") ->
            "Next step: ask for a priority status brief — Raphael balances posture from device evidence."
        else ->
            "Next step: review Security Checks on Home, then correlate any WARN/FAIL rows with the latest scan residue."
    }
}
