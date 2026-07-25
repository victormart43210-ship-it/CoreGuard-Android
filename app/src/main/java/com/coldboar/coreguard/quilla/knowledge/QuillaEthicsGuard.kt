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
        "break into",
        "steal password",
        "steal passwords",
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
        "install spyware on"
    )

    private val defensiveSignals = listOf(
        "defend", "defense", "detect", "detection", "prevent", "prevention",
        "secure", "security", "harden", "hardening", "protect", "protection",
        "masvs", "mitre", "owasp", "incident", "response", "blue team",
        "my device", "my phone", "coreguard", "triage", "authorize", "authorized",
        "pentest methodology", "rules of engagement"
    )

    fun shouldRefuse(prompt: String): Boolean {
        val p = prompt.lowercase()
        if (p.isBlank()) return false
        val offensive = offensiveSignals.any { p.contains(it) }
        if (!offensive) return false
        val defensive = defensiveSignals.any { p.contains(it) }
        return !defensive
    }

    fun refusalMessage(): String =
        "I won't help attack people or systems without authorization.\n\n" +
            "I *will* teach defensive cybersecurity: OWASP MASVS, MITRE ATT&CK Mobile, " +
            "authorized pentest methodology, Android hardening, and incident response — " +
            "and I'll apply those ideas to *your* CoreGuard evidence (scan, shield, compliance).\n\n" +
            "Try: \"explain MASVS-NETWORK\", \"what is T1636\", \"mobile incident triage\", " +
            "or \"android permission hygiene\"."
}
