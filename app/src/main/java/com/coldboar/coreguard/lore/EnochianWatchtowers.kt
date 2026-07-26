package com.coldboar.coreguard.lore

import com.coldboar.coreguard.quilla.QuillaIntent
import com.coldboar.coreguard.quilla.QuillaModule

/**
 * Enochian Watchtower lattice for Quilla — Dee/Kelly-inspired **metaphors**
 * shaped as code so the app's defensive stack reads like a living tablet.
 *
 * ```
 *                    ✦ BLACK CROSS (Raphael balance) ✦
 *         AIR/EAST              FIRE/SOUTH
 *      (Bataivah · Ave)      (Edelperna · Habioro)
 *        Intel / signals        RASP / Frida line
 *                 \                /
 *                  \   TABLET    /
 *                  /   SQUARE   \
 *                 /                \
 *      WATER/WEST              EARTH/NORTH
 *   (Raagiosl · Hraoli)     (Iczhhcal · Mordial)
 *     Shield / DNS flow       Device / Trojans
 * ```
 *
 * Honesty: Enochian names do **not** cast wards, scan packets, or detect
 * Trojans. They label evidence-backed modules the same way Sephirot do.
 */
object EnochianWatchtowers {

    const val DISCLAIMER =
        "Enochian Watchtowers and tablet names are Quilla's poetic lattice — " +
            "inspired by historical angelic vocabularies, not operative magick. " +
            "They do not power detection; CoreGuard evidence still leads."

    enum class Quarter(
        val id: String,
        val element: String,
        val direction: String,
        val king: String,
        val senior: String,
        val kabbalahArchangel: String,
        val geometry: String,
        val quillaFocus: String,
        val securityLens: String,
        val moduleHints: List<QuillaModule>,
        val keywords: List<String>
    ) {
        AIR_EAST(
            id = "air_east",
            element = "Air",
            direction = "East",
            king = "Bataivah",
            senior = "Ave",
            kabbalahArchangel = "Raphael",
            geometry = "Yellow square · sword of breath",
            quillaFocus = "Signals, STIX breath, intel winds",
            securityLens = "Research sync + network/C2 echo correlation (optional HTTPS)",
            moduleHints = listOf(QuillaModule.RESEARCH, QuillaModule.BRAIN),
            keywords = listOf(
                "enochian air", "watchtower east", "bataivah", "ave", "air tablet",
                "eastern watchtower", "air quarter"
            )
        ),
        FIRE_SOUTH(
            id = "fire_south",
            element = "Fire",
            direction = "South",
            king = "Edelperna",
            senior = "Habioro",
            kabbalahArchangel = "Michael",
            geometry = "Red square · flaming sword",
            quillaFocus = "RASP fire against unauthorized instrumentation",
            securityLens = "Frida, hooks, memory integrity, debugger, swarm handoff",
            moduleHints = listOf(QuillaModule.TOOLS, QuillaModule.BRAIN),
            keywords = listOf(
                "enochian fire", "watchtower south", "edelperna", "habioro", "fire tablet",
                "southern watchtower", "fire quarter"
            )
        ),
        WATER_WEST(
            id = "water_west",
            element = "Water",
            direction = "West",
            king = "Raagiosl",
            senior = "Hraoli",
            kabbalahArchangel = "Gabriel",
            geometry = "Blue square · cup of flow",
            quillaFocus = "DNS tides, Shield sinkholes, memory wells",
            securityLens = "Privacy Shield VPN consent + Memory/telemetry mirroring",
            moduleHints = listOf(QuillaModule.MEMORY, QuillaModule.TOOLS),
            keywords = listOf(
                "enochian water", "watchtower west", "raagiosl", "hraoli", "water tablet",
                "western watchtower", "water quarter"
            )
        ),
        EARTH_NORTH(
            id = "earth_north",
            element = "Earth",
            direction = "North",
            king = "Iczhhcal",
            senior = "Mordial",
            kabbalahArchangel = "Uriel",
            geometry = "Black/green square · pantacle of ground",
            quillaFocus = "Device kingdom — packages, overlays, sideload stone",
            securityLens = "Nemesis IOC scan + Sandalphon intrusion surfaces (overlay/a11y/sideload)",
            moduleHints = listOf(QuillaModule.TOOLS, QuillaModule.ACTIONS),
            keywords = listOf(
                "enochian earth", "watchtower north", "iczhhcal", "mordial", "earth tablet",
                "northern watchtower", "earth quarter", "uriel"
            )
        );

        val seal: String get() = "$king · $senior · $kabbalahArchangel ($element/$direction)"
    }

    /** Black Cross — the balancing bar between elemental tablets (Tiferet / Raphael). */
    data class BlackCross(
        val title: String = "Black Cross",
        val angel: String = "Raphael",
        val geometry: String = "Equal-armed cross binding four tablets",
        val securityLens: String = "Priority posture balances all four quarters from evidence"
    )

    val blackCross = BlackCross()

    val quarters: List<Quarter> = Quarter.entries

    fun quarter(id: String): Quarter? = quarters.firstOrNull { it.id == id }

    fun matchQuarter(normalizedPrompt: String): Quarter? {
        for (q in quarters) {
            if (q.keywords.any { normalizedPrompt.contains(it) } ||
                normalizedPrompt.contains(q.king.lowercase()) ||
                normalizedPrompt.contains(q.senior.lowercase()) ||
                normalizedPrompt.contains(q.element.lowercase() + " watchtower") ||
                normalizedPrompt.contains(q.direction.lowercase() + " watchtower")
            ) {
                return q
            }
        }
        if (normalizedPrompt.contains("enochian") || normalizedPrompt.contains("watchtower") ||
            normalizedPrompt.contains("watchtowers") || normalizedPrompt.contains("elemental tablet") ||
            normalizedPrompt.contains("black cross")
        ) {
            return Quarter.AIR_EAST
        }
        return null
    }

    fun quarterFor(intent: QuillaIntent): Quarter = when (intent) {
        QuillaIntent.RESEARCH, QuillaIntent.KNOWLEDGE, QuillaIntent.CAPABILITIES -> Quarter.AIR_EAST
        QuillaIntent.SCAN, QuillaIntent.ETHICS_REFUSAL -> Quarter.FIRE_SOUTH
        QuillaIntent.SHIELD, QuillaIntent.STATUS -> Quarter.WATER_WEST
        QuillaIntent.TIMELINE, QuillaIntent.GENERAL -> Quarter.EARTH_NORTH
    }

    fun tabletBlurb(): String = buildString {
        append("Enochian Watchtower lattice (metaphor):\n")
        append("• Black Cross — ${blackCross.angel}: ${blackCross.securityLens}\n")
        quarters.forEach { q ->
            append("• ")
            append(q.direction)
            append('/')
            append(q.element)
            append(" — King ")
            append(q.king)
            append(" · Senior ")
            append(q.senior)
            append(" · ")
            append(q.kabbalahArchangel)
            append(" → ")
            append(q.securityLens)
            append('\n')
        }
        append(DISCLAIMER)
    }

    fun livingSeal(intent: QuillaIntent? = null): String {
        val q = intent?.let { quarterFor(it) } ?: Quarter.AIR_EAST
        return "Watchtower ${q.direction} · ${q.king}/${q.senior} · ${q.kabbalahArchangel}"
    }
}
