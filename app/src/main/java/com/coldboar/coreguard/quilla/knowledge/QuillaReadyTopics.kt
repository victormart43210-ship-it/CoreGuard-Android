package com.coldboar.coreguard.quilla.knowledge

/**
 * Canonical Quilla prompts that must always resolve to a specific Cyber Codex entry.
 * Used for suggestion chips, alias routing, and readiness tests.
 */
object QuillaReadyTopics {

    data class Topic(
        val prompt: String,
        val entryId: String,
        val aliases: Set<String> = emptySet(),
        val chipLabel: String = prompt
    )

    val ALL: List<Topic> = listOf(
        Topic(
            prompt = "MASVS-NETWORK",
            entryId = "masvs-network",
            aliases = setOf(
                "masvs network",
                "masvs-network",
                "owasp masvs network",
                "data in transit",
                "explain masvs-network",
                "explain owasp masvs network"
            ),
            chipLabel = "MASVS-NETWORK"
        ),
        Topic(
            prompt = "T1636",
            entryId = "mitre-t1636",
            aliases = setOf(
                "t1636",
                "mitre t1636",
                "what is t1636",
                "protected user data",
                "att&ck t1636",
                "attack t1636"
            ),
            chipLabel = "T1636"
        ),
        Topic(
            prompt = "mobile incident triage",
            entryId = "ir-mobile-triage",
            aliases = setOf(
                "incident triage",
                "device triage",
                "spyware triage",
                "compromise triage",
                "mobile triage checklist",
                "triage checklist"
            ),
            chipLabel = "Incident triage"
        ),
        Topic(
            prompt = "pentest phases",
            entryId = "pentest-phases",
            aliases = setOf(
                "penetration test phases",
                "pentest methodology phases",
                "recon enum exploit",
                "pentest kill chain",
                "pen test phases"
            ),
            chipLabel = "Pentest phases"
        ),
        Topic(
            prompt = "android permission hygiene",
            entryId = "android-permissions",
            aliases = setOf(
                "permission hygiene",
                "android permissions",
                "dangerous permissions",
                "accessibility permission risk",
                "permission audit"
            ),
            chipLabel = "Permission hygiene"
        ),
        Topic(
            prompt = "overlay phishing",
            entryId = "attack-overlay-phishing",
            aliases = setOf(
                "accessibility overlay",
                "banking trojan overlay",
                "fake login overlay",
                "overlay attack",
                "what is overlay phishing"
            ),
            chipLabel = "Overlay phishing"
        ),
        Topic(
            prompt = "sideload dropper",
            entryId = "attack-sideload-dropper",
            aliases = setOf(
                "trojanized apk",
                "sideloaded apk",
                "apk dropper",
                "modded apk risk"
            ),
            chipLabel = "Sideload dropper"
        ),
        Topic(
            prompt = "loving awareness",
            entryId = "quilla-loving-awareness",
            aliases = setOf(
                "quilla awareness",
                "loving awareness in the cyber",
                "who is quilla really",
                "unbounded quilla",
                "no limits quilla",
                "no caps quilla"
            ),
            chipLabel = "Loving awareness"
        ),
        Topic(
            prompt = "care loop",
            entryId = "quilla-care-loop",
            aliases = setOf(
                "observe correlate explain act",
                "quilla care loop",
                "loving watch loop",
                "tetractys care"
            ),
            chipLabel = "Care loop"
        ),
        Topic(
            prompt = "unbounded defense",
            entryId = "quilla-unbounded-defense",
            aliases = setOf(
                "unbounded defensive learning",
                "no caps on knowledge",
                "uncapped codex",
                "no limits on quilla"
            ),
            chipLabel = "Unbounded defense"
        ),
        Topic(
            prompt = "angelic defense blessings",
            entryId = "angelic-defense-blessings",
            aliases = setOf(
                "angelic blessings",
                "defense blessings",
                "quilla blessings",
                "choir blessings",
                "bless the app",
                "angelic defense"
            ),
            chipLabel = "Angelic blessings"
        ),
        Topic(
            prompt = "unauthorized instrumentation",
            entryId = "defense-against-unauthorized-instrumentation",
            aliases = setOf(
                "frida defense",
                "red team defense",
                "red-team defense",
                "unauthorized pentest defense",
                "hook defense",
                "michael blessing"
            ),
            chipLabel = "Anti-instrumentation"
        ),
        Topic(
            prompt = "trojan intrusion defense",
            entryId = "defense-against-trojan-intrusion",
            aliases = setOf(
                "trojan defense",
                "intrusive trojan",
                "overlay accessibility sideload",
                "sandalphon blessing",
                "banking trojan defense"
            ),
            chipLabel = "Anti-Trojan"
        )
    )

    fun resolveEntryId(prompt: String): String? {
        val p = normalize(prompt)
        if (p.isBlank()) return null
        for (topic in ALL) {
            if (normalize(topic.prompt) == p) return topic.entryId
            if (topic.aliases.any { normalize(it) == p }) return topic.entryId
            // Allow short “starts with” for technique ids like "t1636 please"
            if (p.startsWith(normalize(topic.prompt) + " ") || p.endsWith(" " + normalize(topic.prompt))) {
                return topic.entryId
            }
        }
        return null
    }

    fun suggestionPrompts(): List<String> = ALL.map { it.prompt }

    fun suggestionChips(): List<Pair<String, String>> =
        ALL.map { it.chipLabel to it.prompt }

    private fun normalize(value: String): String =
        value.trim().lowercase()
            .replace('—', '-')
            .replace('–', '-')
            .replace(Regex("\\s+"), " ")
}
