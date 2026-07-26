package com.coldboar.coreguard.lore

/**
 * Keyword-driven Quilla answers that apply Observatory Codex themes to
 * practical device-security questions.
 *
 * Responses are original. They deliberately avoid presenting ancient-astronaut
 * lore as fact or as a scanner feature.
 */
object QuillaKnowledge {

    fun answer(prompt: String): String {
        val normalized = prompt.trim().lowercase()
        if (normalized.isEmpty()) {
            return "Ask Quilla about observation, cycles, archives, relays, or how to verify a threat signal."
        }

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

    fun matchFragment(normalizedPrompt: String): ObservatoryCodex.Fragment? {
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

    private fun practicalAdvice(normalizedPrompt: String): String = when {
        normalizedPrompt.contains("scan") || normalizedPrompt.contains("nemesis") ->
            "Next step: run Nemesis Scanner, then read the Scan Timeline as an observatory log of what changed."
        normalizedPrompt.contains("vpn") || normalizedPrompt.contains("shield") ->
            "Next step: check Shield status. Blocking is a response action — keep observing whether the echo returns."
        normalizedPrompt.contains("premium") || normalizedPrompt.contains("billing") ->
            "Premium unlocks deeper automation. The Codex still applies: more telemetry only helps if you verify it."
        else ->
            "Next step: review Security Checks on Home, then correlate any WARN/FAIL rows with the latest scan residue."
    }
}
