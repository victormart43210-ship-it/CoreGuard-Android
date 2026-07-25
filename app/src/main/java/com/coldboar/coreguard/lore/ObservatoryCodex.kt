package com.coldboar.coreguard.lore

/**
 * Original CoreGuard "Observatory Codex" knowledge fragments.
 *
 * These are **not** quotations from any book. They remix high-level themes found
 * in 1970s sky-watcher / ancient-observatory speculation (Maya calendrics,
 * recovered archives, long-range signal relays, pattern hunting across distant
 * sites) into metaphors for modern device security.
 *
 * Important product rules:
 * - Lore is optional atmosphere for Quilla / Secret Portal.
 * - It must never be presented as a detection capability.
 * - Security conclusions still require on-device evidence.
 */
object ObservatoryCodex {

    const val DISCLAIMER =
        "Observatory Codex fragments are speculative metaphors for evidence-based " +
            "monitoring. They are not historical claims and do not power detection."

    data class Fragment(
        val id: String,
        val title: String,
        val body: String,
        /** Security practice the metaphor maps onto. */
        val securityLens: String
    )

    val fragments: List<Fragment> = listOf(
        Fragment(
            id = "sky_watchers",
            title = "Sky-Watchers Keep the Watch",
            body = "Ancient observatory cultures tracked the heavens for cycles " +
                "before they named a cause. CoreGuard borrows that discipline: " +
                "observe signals first, then interpret.",
            securityLens = "Continuous observation before verdict"
        ),
        Fragment(
            id = "calendar_cycles",
            title = "Cycles Reveal What Moments Hide",
            body = "Long calendars made rare alignments visible. Threats often " +
                "look the same: a single event is noise, a repeating window is " +
                "signal. Correlate across time before you escalate.",
            securityLens = "Sliding-window correlation"
        ),
        Fragment(
            id = "recovered_archives",
            title = "Recovered Archives Beat Rumor",
            body = "Stories of sealed tunnels and lost records remind hunters " +
                "that secondary legends are cheap. Prefer recoverable artifacts — " +
                "packages, process trees, IOC feeds — over dramatic guesses.",
            securityLens = "Evidence over rumor"
        ),
        Fragment(
            id = "signal_relays",
            title = "Distant Relays Leave Local Echoes",
            body = "Speculative lore imagined lunar relays bouncing messages " +
                "home. Modern compromise often rhymes: command channels may be " +
                "remote, but their echoes still appear on the device — DNS, " +
                "sockets, odd listeners.",
            securityLens = "Network / C2 echo hunting"
        ),
        Fragment(
            id = "pattern_sites",
            title = "Compare Distant Sites, Not One Stone",
            body = "Pattern hunters once lined up monuments across landscapes. " +
                "Quilla does the earthly version: compare STIX indicators, " +
                "behavioral anomalies, and local scan residue before claiming " +
                "a match.",
            securityLens = "Multi-source correlation"
        ),
        Fragment(
            id = "question_the_coverup",
            title = "Question Silence, Demand Proof",
            body = "Conspiracy-era books thrived on missing maps and sealed " +
                "photos. The useful habit remains: when a vendor, OS, or app " +
                "goes quiet about a capability, investigate — then insist on " +
                "reproducible evidence before you trust the story.",
            securityLens = "Verify before trusting"
        )
    )

    fun fragment(id: String): Fragment? = fragments.firstOrNull { it.id == id }
}
