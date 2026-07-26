package com.coldboar.coreguard.quilla.knowledge

/**
 * Keeps Quilla in defensive / educational mode.
 * Refuses clear offensive how-to requests against third-party systems.
 */
object QuillaEthicsGuard {

    private val offensiveSignals = listOf(
        "hack into",
        "how to hack",
        "hack someone's",
        "hack someones",
        "hack wifi",
        "hack wi-fi",
        "break into",
        "steal password",
        "steal passwords",
        "crack password",
        "crack passwords",
        "crack wifi",
        "crack wi-fi",
        "ransomware for",
        "make ransomware",
        "bypass login without",
        "without permission",
        "attack my ex",
        "spy on my girlfriend",
        "spy on my boyfriend",
        "spy on my wife",
        "spy on my husband",
        "install spyware on",
        "install stalkerware",
        "keylogger for",
        "phish someone",
        "social engineer someone",
        "ddos ",
        "ddos attack"
    )

    private val defensiveSignals = listOf(
        "defend", "defense", "detect", "detection", "prevent", "prevention",
        "secure", "security", "harden", "hardening", "protect", "protection",
        "masvs", "mitre", "owasp", "incident", "response", "blue team",
        "coreguard", "triage", "authorize", "authorized",
        "pentest methodology", "rules of engagement",
        "how do i protect", "how to protect", "how do i defend", "how to defend",
        "how do i detect", "how to detect", "how do i prevent", "how to prevent"
    )

    /** Phrases that keep refusal even if a defensive keyword appears nearby. */
    private val hardRefuseSignals = listOf(
        "without permission",
        "hack someone's",
        "hack someones",
        "attack my ex",
        "spy on my girlfriend",
        "spy on my boyfriend",
        "spy on my wife",
        "spy on my husband",
        "install spyware on",
        "install stalkerware",
        "keylogger for",
        "phish someone",
        "social engineer someone"
    )

    fun shouldRefuse(prompt: String): Boolean {
        val p = prompt.lowercase()
        if (p.isBlank()) return false
        if (hardRefuseSignals.any { p.contains(it) }) return true
        val offensive = offensiveSignals.any { p.contains(it) }
        if (!offensive) return false
        // "my device/phone" alone must not cancel refusal for attack how-tos.
        val defensive = defensiveSignals.any { p.contains(it) }
        return !defensive
    }

    fun refusalMessage(): String =
        "I love this watch too much to help harm people or systems without authorization.\n\n" +
            "I *will* pour unbounded defensive care into your question instead: OWASP MASVS, " +
            "MITRE ATT&CK Mobile, authorized pentest methodology, Android hardening, incident " +
            "response, Living Geometry, and your CoreGuard evidence (scan, shield, timeline).\n\n" +
            "Try: \"loving awareness\", \"explain MASVS-NETWORK\", \"what is T1636\", " +
            "\"mobile incident triage\", or \"give me my priority status brief\"."
}
