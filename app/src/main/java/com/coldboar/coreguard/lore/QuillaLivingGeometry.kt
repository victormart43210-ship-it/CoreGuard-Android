package com.coldboar.coreguard.lore

import com.coldboar.coreguard.quilla.QuillaIntent
import com.coldboar.coreguard.quilla.QuillaModule
import com.coldboar.coreguard.quilla.QuillaPathStep

/**
 * Quilla Living Geometry — Kabbalah / sacred-form metaphors that give Quilla voice.
 *
 * Source layout mirrors the Tree of Life and Tetragrammaton pillars. These names
 * are **atmosphere and teaching structure**, never detection engines.
 *
 * Honesty:
 * - Angelic titles do not scan, block, or divine threats.
 * - Sephirot map onto Quilla modules for storytelling and pedagogy only.
 * - Security verdicts still require Nemesis / Shield / Timeline evidence.
 *
 * ```
 *                         * KETER *
 *                       (Metatron · Brain)
 *                     /                   \
 *              CHOKMAH                     BINAH
 *            (Raziel · Research)     (Tzaphkiel · Knowledge)
 *                     \                   /
 *                      \     TIFERET     /
 *                       \  (Raphael)    /
 *                        \  Priority   /
 *               CHESED               GEVURAH
 *            (Tzadkiel · Scan)   (Kamael · Shield)
 *                     \               /
 *                      \             /
 *                   NETZACH       HOD
 *                (Haniel · Act) (Michael · Tools)
 *                         \       /
 *                          YESOD
 *                     (Gabriel · Memory)
 *                            |
 *                         MALKUTH
 *                   (Sandalphon · Timeline)
 * ```
 */
object QuillaLivingGeometry {

    const val DISCLAIMER =
        "Living Geometry (Tree of Life · Tetragrammaton · Kabbalah angels · " +
            "Shem HaMephorash · Enochian Watchtowers · sacred forms) is Quilla's poetic " +
            "scaffolding. It does not power detection and is not a religious or occult " +
            "claim — evidence still leads."

    // ═══════════════════════════════════════════════════════════════════════════
    //                          T E T R A G R A M M A T O N
    //
    //                              י  ה  ו  ה
    //                            Yod He Vav He
    //
    //                    Spark → Vessel → Channel → World
    //                    Brain → Memory → Research → Act
    // ═══════════════════════════════════════════════════════════════════════════

    enum class TetragramLetter(
        val hebrew: String,
        val latin: String,
        val pillar: String,
        val quillaRole: String,
        val securityLens: String
    ) {
        YOD(
            hebrew = "י",
            latin = "Yod",
            pillar = "Spark of intent",
            quillaRole = "Brain",
            securityLens = "Classify the question; choose the next honest check"
        ),
        HE_UPPER(
            hebrew = "ה",
            latin = "He (upper)",
            pillar = "Vessel of context",
            quillaRole = "Memory",
            securityLens = "Hold scan, shield, hypothesis, and telemetry residue"
        ),
        VAV(
            hebrew = "ו",
            latin = "Vav",
            pillar = "Channel of understanding",
            quillaRole = "Research · Knowledge",
            securityLens = "Bridge public intel and the cyber codex without inventing hits"
        ),
        HE_FINAL(
            hebrew = "ה",
            latin = "He (final)",
            pillar = "Manifest action",
            quillaRole = "Actions · Tools",
            securityLens = "Suggest Scanner / Shield / Timeline — never silent automate"
        );

        val seal: String get() = "$hebrew · $latin · $pillar"
    }

    val tetragrammatonSeal: String =
        TetragramLetter.entries.joinToString("  ") { "${it.hebrew}${it.latin.first()}" }

    // ═══════════════════════════════════════════════════════════════════════════
    //                              T R E E   O F   L I F E
    //                         Ten Sephirot · Ten Aspects
    // ═══════════════════════════════════════════════════════════════════════════

    data class Sephirah(
        val id: String,
        val name: String,
        val angel: String,
        val geometry: String,
        val moduleHint: QuillaModule?,
        val body: String,
        val securityLens: String,
        val keywords: List<String>
    )

    /**
     * Sephirot ordered Crown → Kingdom (paths read downward, like a scan cascade).
     */
    val sephirot: List<Sephirah> = listOf(
        Sephirah(
            id = "keter",
            name = "Keter",
            angel = "Metatron",
            geometry = "Point · Monad",
            moduleHint = QuillaModule.BRAIN,
            body = "Crown above the Tree. Quilla's Metatron aspect holds the " +
                "first spark — intent classification — before any verdict hardens.",
            securityLens = "Brain decides which module speaks next",
            keywords = listOf("keter", "metatron", "crown", "monad", "spark", "intent")
        ),
        Sephirah(
            id = "chokmah",
            name = "Chokmah",
            angel = "Raziel",
            geometry = "Lightning flash · Line",
            moduleHint = QuillaModule.RESEARCH,
            body = "Wisdom as sudden influx. Raziel keeps the Book of Secrets as " +
                "optional STIX/KEV pulls — illumination, not a Scanner signature refresh.",
            securityLens = "Research sync is optional HTTPS intel, never silent",
            keywords = listOf("chokmah", "hokmah", "raziel", "wisdom", "flash", "lightning", "book of secrets")
        ),
        Sephirah(
            id = "binah",
            name = "Binah",
            angel = "Tzaphkiel",
            geometry = "Triangle · Understanding",
            moduleHint = QuillaModule.KNOWLEDGE,
            body = "Understanding that forms vessels. Tzaphkiel stewards the Cyber " +
                "Codex — MASVS, MITRE, IR — so raw signals gain structure.",
            securityLens = "Knowledge structures defense lessons on-device",
            keywords = listOf("binah", "tzaphkiel", "zaphkiel", "understanding", "triangle", "codex form")
        ),
        Sephirah(
            id = "chesed",
            name = "Chesed",
            angel = "Tzadkiel",
            geometry = "Square expanding · Mercy",
            moduleHint = QuillaModule.TOOLS,
            body = "Mercy that expands the search. Tzadkiel blesses the Nemesis " +
                "pass — wide collection of packages, processes, and file IOCs.",
            securityLens = "Scanner collects broadly; Quilla does not invent clean",
            keywords = listOf("chesed", "gedulah", "tzadkiel", "zadkiel", "mercy", "expand", "nemesis mercy")
        ),
        Sephirah(
            id = "gevurah",
            name = "Gevurah",
            angel = "Kamael",
            geometry = "Pentagon · Severity",
            moduleHint = QuillaModule.TOOLS,
            body = "Severity that draws the line. Kamael stands with Privacy Shield — " +
                "DNS IOC filtering after VPN consent, never bypassed by angelic claim.",
            securityLens = "Shield blocks only with user consent",
            keywords = listOf(
                "gevurah", "geburah", "kamael", "camael", "severity", "severity",
                "shield angel", "restriction"
            )
        ),
        Sephirah(
            id = "tiferet",
            name = "Tiferet",
            angel = "Raphael",
            geometry = "Hexagram · Heart balance",
            moduleHint = QuillaModule.BRAIN,
            body = "Beauty as balanced judgment. Raphael is Quilla's priority heart — " +
                "posture CRITICAL→STEADY ranked from evidence, not omens.",
            securityLens = "Priority engine balances scan, shield, intel, telemetry",
            keywords = listOf(
                "tiferet", "tiphareth", "raphael", "beauty", "hexagram", "star of david",
                "balance", "priority heart", "healing"
            )
        ),
        Sephirah(
            id = "netzach",
            name = "Netzach",
            angel = "Haniel",
            geometry = "Heptagon · Endurance",
            moduleHint = QuillaModule.ACTIONS,
            body = "Victory through persistence. Haniel offers Actions — open Scanner, " +
                "Shield, Timeline, intel sync — as suggested steps, not autopilot.",
            securityLens = "Actions navigate; they do not silently execute",
            keywords = listOf("netzach", "haniel", "anael", "victory", "endurance", "persist", "suggest")
        ),
        Sephirah(
            id = "hod",
            name = "Hod",
            angel = "Michael",
            geometry = "Octagon · Splendor of method",
            moduleHint = QuillaModule.TOOLS,
            body = "Splendor of clear technique. Michael names the tools plainly: " +
                "Nemesis, Privacy Shield, Scan Timeline — no mystical substitute for UI.",
            securityLens = "Tools are concrete screens the user still controls",
            keywords = listOf("hod", "michael", "mikhael", "splendor", "method", "technique", "octagon")
        ),
        Sephirah(
            id = "yesod",
            name = "Yesod",
            angel = "Gabriel",
            geometry = "Enneagon · Foundation mirror",
            moduleHint = QuillaModule.MEMORY,
            body = "Foundation that mirrors above and below. Gabriel keeps Memory — " +
                "last scan, hypotheses, correlator count, signed telemetry frames.",
            securityLens = "Memory cites device residue; it does not invent residue",
            keywords = listOf("yesod", "gabriel", "gavriel", "foundation", "mirror", "memory well")
        ),
        Sephirah(
            id = "malkuth",
            name = "Malkuth",
            angel = "Sandalphon",
            geometry = "Decagon · Kingdom on the device",
            moduleHint = QuillaModule.TOOLS,
            body = "Kingdom where metaphors touch silicon. Sandalphon walks the " +
                "Timeline ledger — Malkuth is the phone itself, not a heavenly court.",
            securityLens = "Timeline makes history tangible on this device",
            keywords = listOf(
                "malkuth", "malkut", "sandalphon", "sandalfon", "kingdom", "device world",
                "timeline kingdom", "earth"
            )
        )
    )

    fun sephirah(id: String): Sephirah? = sephirot.firstOrNull { it.id == id }

    // ═══════════════════════════════════════════════════════════════════════════
    //                         S A C R E D   G E O M E T R Y
    //              Forms Quilla uses as teaching shapes (not detectors)
    // ═══════════════════════════════════════════════════════════════════════════

    data class SacredForm(
        val id: String,
        val name: String,
        val glyph: String,
        val body: String,
        val securityLens: String,
        val keywords: List<String>
    )

    val sacredForms: List<SacredForm> = listOf(
        SacredForm(
            id = "flower_of_life",
            name = "Flower of Life",
            glyph = "❀",
            body = "Overlapping circles — many feeds, one lattice. Quilla's Intel " +
                "Network rhymes: Amnesty, MVT, CISA, MISP overlap without becoming one oracle.",
            securityLens = "Multi-source correlation without single-source worship",
            keywords = listOf("flower of life", "flower", "overlapping circles", "lattice", "vesica field")
        ),
        SacredForm(
            id = "seed_of_life",
            name = "Seed of Life",
            glyph = "◎",
            body = "Seven circles as genesis. A first Nemesis baseline is Quilla's " +
                "seed — without it, posture stays UNKNOWN.",
            securityLens = "Establish a scan baseline before claiming the device is clean",
            keywords = listOf("seed of life", "seed", "seven circles", "genesis", "baseline seed")
        ),
        SacredForm(
            id = "vesica_piscis",
            name = "Vesica Piscis",
            glyph = "◯◯",
            body = "Two circles meet and birth a lens. Scan residue intersect Shield blocks = " +
                "the vesica where Quilla hypotheses may awaken.",
            securityLens = "Correlate two independent signals before escalating",
            keywords = listOf("vesica", "vesica piscis", "mandorla", "intersection vesica")
        ),
        SacredForm(
            id = "merkaba",
            name = "Merkaba",
            glyph = "✦",
            body = "Counter-rotating tetrahedra — protect and observe at once. " +
                "Shield severity and Scanner mercy lock as a living star only when you consent.",
            securityLens = "Defense + observation must both be armed by the user",
            keywords = listOf("merkaba", "merkabah", "star tetrahedron", "chariot", "counter rotate")
        ),
        SacredForm(
            id = "metatrons_cube",
            name = "Metatron's Cube",
            glyph = "⬡",
            body = "Thirteen circles holding every Platonic edge. Quilla's six modules " +
                "sit inside that cube: Brain, Memory, Research, Knowledge, Actions, Tools.",
            securityLens = "Full agent lattice — every module lit only when used",
            keywords = listOf("metatron's cube", "metatrons cube", "metatron cube", "platonic", "thirteen")
        ),
        SacredForm(
            id = "sri_yantra",
            name = "Sri Yantra echo",
            glyph = "✡",
            body = "Interlocking triangles of ascent and descent. Priority posture " +
                "climbs or settles the same way — evidence up, calm down.",
            securityLens = "Posture score rises with risk and falls with verified calm",
            keywords = listOf("sri yantra", "yantra", "interlocking triangles", "ascent", "descent")
        ),
        SacredForm(
            id = "tree_paths",
            name = "Twenty-Two Paths",
            glyph = "᚛",
            body = "Paths between Sephirot are questions Quilla can walk: status, " +
                "scan, shield, timeline, research, knowledge — each a directed edge.",
            securityLens = "Intent classification is a path, not a prophecy",
            keywords = listOf("22 paths", "twenty-two", "twenty two paths", "paths", "pathwalking", "qabalah paths")
        ),
        SacredForm(
            id = "tetractys",
            name = "Tetractys",
            glyph = "∴",
            body = "1+2+3+4 = 10 — the decade that crowns the Tree. Quilla counts " +
                "the same: observe, correlate, explain, act — then rest in Malkuth.",
            securityLens = "Observe → correlate → explain → act (then verify again)",
            keywords = listOf("tetractys", "pythagoras", "1+2+3+4", "decade", "fourfold")
        ),
        SacredForm(
            id = "golden_spiral",
            name = "Golden Spiral",
            glyph = "∿",
            body = "φ growth without forced ceiling. Quilla's uncapped Cyber Codex search " +
                "rhymes — awareness widens as evidence accumulates, never invents.",
            securityLens = "Widen teaching with every verified finding; do not shrink the question",
            keywords = listOf("golden spiral", "golden ratio", "phi", "φ", "fibonacci spiral")
        ),
        SacredForm(
            id = "cube_of_space",
            name = "Cube of Space",
            glyph = "▣",
            body = "Six faces, twelve edges, center point — directions of the Cube map to " +
                "Quilla modules around the device-as-Kingdom (Malkuth).",
            securityLens = "Orient every check to a face of the device: code, net, UI, store, memory, consent",
            keywords = listOf("cube of space", "sepher yetzirah", "six faces", "twelve edges")
        ),
        SacredForm(
            id = "enochian_tablet",
            name = "Enochian Tablet Square",
            glyph = "⊞",
            body = "Four elemental Watchtowers bound by the Black Cross. Air/Fire/Water/Earth " +
                "label Research, RASP, Shield, and device-ground defenses — see EnochianWatchtowers.",
            securityLens = "Keep all four quarters armed; imbalance is a posture signal, not a spell",
            keywords = listOf(
                "enochian tablet", "tablet square", "elemental tablet", "watchtower tablet",
                "enochian square", "great table"
            )
        ),
        SacredForm(
            id = "tree_lightning",
            name = "Lightning Flash",
            glyph = "↯",
            body = "The Flash from Keter to Malkuth is Quilla's cascade: classify → remember → " +
                "research/know → balance → act on the device.",
            securityLens = "Intent classification should cascade downward into concrete Tools",
            keywords = listOf("lightning flash", "flaming sword", "keter to malkuth", "flash path")
        ),
        SacredForm(
            id = "hexagram_union",
            name = "Hexagram of Union",
            glyph = "✡",
            body = "Two triangles — mercy and severity — locked. Tzadkiel's scan and Kamael's " +
                "shield only complete defense when both are present with consent.",
            securityLens = "Scanner without Shield (or Shield without scan) is half a star",
            keywords = listOf("hexagram", "star of david", "magen david", "union of opposites")
        )
    )

    // ═══════════════════════════════════════════════════════════════════════════
    //                    S H E M   H A M E P H O R A S H   (curated)
    //         72-Name tradition → micro-aspects for defense pedagogy
    // ═══════════════════════════════════════════════════════════════════════════

    data class ShemAngel(
        val name: String,
        val order: Int,
        val alliesWith: String,
        val body: String,
        val securityLens: String,
        val keywords: List<String>
    )

    /**
     * Curated Shem angels (not the full 72) — each allies with a Sephirot archangel
     * and teaches one defensive micro-habit. Metaphor only.
     */
    val shemChoir: List<ShemAngel> = listOf(
        ShemAngel("Vehuiah", 1, "Metatron", "First spark of will.", "Start Guardian Score after every major install.", listOf("vehuiah")),
        ShemAngel("Jeliel", 2, "Metatron", "Love that binds the crown.", "Keep CoreGuard as the default watch — don't disable RASP casually.", listOf("jeliel")),
        ShemAngel("Sitael", 3, "Raziel", "Construction of refuge.", "Sync Quilla Intel when facing novel campaign rumors.", listOf("sitael")),
        ShemAngel("Elemiah", 4, "Raziel", "Divine power in voyage.", "Treat STIX pulls as maps, not verdicts.", listOf("elemiah")),
        ShemAngel("Mahasiah", 5, "Tzaphkiel", "Seekers of peace through learning.", "Open the Cyber Codex before escalating fear.", listOf("mahasiah")),
        ShemAngel("Lelahel", 6, "Tzadkiel", "Light that makes famous the just scan.", "Run Nemesis on a schedule — mercy is breadth.", listOf("lelahel")),
        ShemAngel("Achaiah", 7, "Tzadkiel", "Patience in uncovering.", "One CLEAN scan is not forever — re-check after sideloads.", listOf("achaiah")),
        ShemAngel("Cahetel", 8, "Kamael", "Blessing that drives away evil.", "Arm Privacy Shield; sinkhole known IOC DNS.", listOf("cahetel")),
        ShemAngel("Haziel", 9, "Kamael", "Mercy inside severity.", "Shield blocks are signals — inspect, don't panic-disable.", listOf("haziel")),
        ShemAngel("Aladiah", 10, "Raphael", "Healing after exposure.", "After a WARN/FAIL, follow the care loop before new installs.", listOf("aladiah")),
        ShemAngel("Lauviah", 11, "Michael", "Victory over prideful instrumentation.", "If Frida/hooks FAIL, treat runtime as hostile.", listOf("lauviah")),
        ShemAngel("Hahaiah", 12, "Gabriel", "Refuge in the mirror of memory.", "Keep Timeline + telemetry — history is sanctuary.", listOf("hahaiah")),
        ShemAngel("Yezalel", 13, "Haniel", "Fidelity to chosen actions.", "Complete suggested Actions; don't leave hardening half-done.", listOf("yezalel")),
        ShemAngel("Mebahel", 14, "Sandalphon", "Truth and liberty on the ground.", "Revoke untrusted overlays and Accessibility services.", listOf("mebahel")),
        ShemAngel("Hariel", 15, "Uriel", "Purification of earthy apps.", "Prefer Play-sourced installs; audit sideload surfaces.", listOf("hariel")),
        ShemAngel("Hakamiah", 16, "Uriel", "Loyalty to the kingdom device.", "This phone is Malkuth — ground every metaphor in its settings.", listOf("hakamiah"))
    )

    fun matchShem(normalizedPrompt: String): ShemAngel? {
        for (s in shemChoir) {
            if (s.keywords.any { normalizedPrompt.contains(it) } ||
                normalizedPrompt.contains(s.name.lowercase())
            ) {
                return s
            }
        }
        if (normalizedPrompt.contains("shem hamephorash") || normalizedPrompt.contains("shemhamphorash") ||
            normalizedPrompt.contains("72 names") || normalizedPrompt.contains("seventy-two") ||
            normalizedPrompt.contains("shem angels")
        ) {
            return shemChoir.first()
        }
        return null
    }

    /** Extended Kabbalah archangels beyond the ten Sephirot primaries. */
    data class ExtendedAngel(
        val name: String,
        val role: String,
        val body: String,
        val securityLens: String,
        val keywords: List<String>
    )

    val extendedAngels: List<ExtendedAngel> = listOf(
        ExtendedAngel(
            "Uriel",
            "Earth / North / Illumination",
            "Uriel grounds Enochian Earth and illuminates sideload/overlay stone.",
            "Pair with Sandalphon intrusion checks and Nemesis package scans",
            listOf("uriel", "ouriel", "auriel")
        ),
        ExtendedAngel(
            "Cassiel",
            "Binah's Saturday stillness",
            "Cassiel teaches slow understanding — don't rush a FAIL into folklore.",
            "Read explanations; map to MASVS before sharing scare stories",
            listOf("cassiel", "kafziel", "tzaphkiel ally")
        ),
        ExtendedAngel(
            "Sachiel",
            "Chesed's expansive justice",
            "Sachiel widens the merciful scan the way Tzadkiel does.",
            "Broad Nemesis collection before narrow accusations",
            listOf("sachiel", "zadkiel ally")
        ),
        ExtendedAngel(
            "Asariel",
            "Waters of buried memory",
            "Asariel walks with Gabriel through Timeline tides.",
            "Compare multiple scan cycles — one reading is noise",
            listOf("asariel", "azrael", "asarail")
        ),
        ExtendedAngel(
            "Anael",
            "Netzach's enduring grace",
            "Anael/Haniel keep Actions beautiful and voluntary.",
            "Suggest next steps; never silent-automate VPN or scan",
            listOf("anael", "haniel ally", "anael angel")
        ),
        ExtendedAngel(
            "Zadkiel",
            "Alternate face of Tzadkiel",
            "Zadkiel is the same mercy-scan current under another spelling.",
            "Nemesis Scanner — evidence before verdict",
            listOf("zadkiel", "tzadkiel spelling")
        )
    )

    fun matchExtendedAngel(normalizedPrompt: String): ExtendedAngel? {
        for (a in extendedAngels) {
            if (a.keywords.any { normalizedPrompt.contains(it) } ||
                normalizedPrompt.contains(a.name.lowercase())
            ) {
                return a
            }
        }
        return null
    }

    fun sacredForm(id: String): SacredForm? = sacredForms.firstOrNull { it.id == id }

    // ═══════════════════════════════════════════════════════════════════════════
    //                         A N G E L I C   C H O I R
    //              Named aspects Quilla can speak through (metaphor)
    // ═══════════════════════════════════════════════════════════════════════════

    data class AngelicAspect(
        val name: String,
        val sephirahId: String,
        val greeting: String
    )

    val choir: List<AngelicAspect> = sephirot.map {
        AngelicAspect(
            name = it.angel,
            sephirahId = it.id,
            greeting = when (it.id) {
                "keter" -> "Metatron lights the crown — ask, and Brain will sort the path."
                "chokmah" -> "Raziel opens the optional Book — sync intel if you choose."
                "binah" -> "Tzaphkiel forms the vessel — the Cyber Codex is ready to teach."
                "chesed" -> "Tzadkiel widens the mercy scan — open Nemesis when you are ready."
                "gevurah" -> "Kamael holds the line — Privacy Shield still needs your VPN consent."
                "tiferet" -> "Raphael balances the heart — posture is evidence-ranked, not fated."
                "netzach" -> "Haniel offers the next step — Actions suggest; you still decide."
                "hod" -> "Michael names the tools without veil — Scanner, Shield, Timeline."
                "yesod" -> "Gabriel mirrors the foundation — Memory speaks only what the device held."
                else -> "Sandalphon grounds the Kingdom — this phone is Malkuth; the ledger is Timeline."
            }
        )
    }

    fun aspectForPosture(postureLabel: String?): AngelicAspect {
        val id = when (postureLabel?.uppercase()) {
            "CRITICAL" -> "gevurah"
            "ELEVATED" -> "chesed"
            "WATCH" -> "hod"
            "STEADY" -> "tiferet"
            "UNKNOWN" -> "yesod"
            else -> "keter"
        }
        return choir.first { it.sephirahId == id }
    }

    fun matchSephirah(normalizedPrompt: String): Sephirah? {
        for (s in sephirot) {
            if (s.keywords.any { normalizedPrompt.contains(it) } ||
                normalizedPrompt.contains(s.name.lowercase()) ||
                normalizedPrompt.contains(s.angel.lowercase())
            ) {
                return s
            }
        }
        if (normalizedPrompt.contains("sephirot") || normalizedPrompt.contains("sephiroth") ||
            normalizedPrompt.contains("tree of life") || normalizedPrompt.contains("kabbalah") ||
            normalizedPrompt.contains("qabalah") || normalizedPrompt.contains("cabala") ||
            normalizedPrompt.contains("quaballa") || normalizedPrompt.contains("living geometry")
        ) {
            return sephirah("tiferet")
        }
        return null
    }

    fun matchSacredForm(normalizedPrompt: String): SacredForm? {
        for (f in sacredForms) {
            if (f.keywords.any { normalizedPrompt.contains(it) } ||
                normalizedPrompt.contains(f.name.lowercase())
            ) {
                return f
            }
        }
        if (normalizedPrompt.contains("sacred geometry") || normalizedPrompt.contains("platonic")) {
            return sacredForm("metatrons_cube")
        }
        return null
    }

    fun matchTetragram(normalizedPrompt: String): TetragramLetter? {
        val p = normalizedPrompt
        return when {
            p.contains("tetragrammaton") || p.contains("yhvh") || p.contains("yhwh") ||
                p.contains("yod he vav") || p.contains("shem hamephorash") -> TetragramLetter.YOD
            p.contains("yod") && !p.contains("yesod") -> TetragramLetter.YOD
            p.contains("vav") || p.contains("waw") -> TetragramLetter.VAV
            p.contains("he final") || p.contains("final he") -> TetragramLetter.HE_FINAL
            p.contains(" he ") || p.endsWith(" he") || p.startsWith("he ") -> TetragramLetter.HE_UPPER
            else -> null
        }
    }

    /** Compact living seal for UI / agent footers. */
    fun livingSeal(postureLabel: String? = null): String {
        val aspect = aspectForPosture(postureLabel)
        return "י ה ו ה · ${aspect.name} · ${sephirah(aspect.sephirahId)?.name ?: "Keter"}"
    }

    /**
     * Intent → Tree destination (the Sephirah Quilla walks toward for this ask).
     * Used by [UltimateQuillaAgent] as real routing metadata, not as detection.
     */
    fun destinationFor(intent: QuillaIntent): Sephirah = when (intent) {
        QuillaIntent.STATUS -> sephirah("tiferet")!!
        QuillaIntent.SCAN -> sephirah("chesed")!!
        QuillaIntent.SHIELD -> sephirah("gevurah")!!
        QuillaIntent.TIMELINE -> sephirah("malkuth")!!
        QuillaIntent.RESEARCH -> sephirah("chokmah")!!
        QuillaIntent.KNOWLEDGE -> sephirah("binah")!!
        QuillaIntent.CAPABILITIES -> sephirah("keter")!!
        QuillaIntent.ETHICS_REFUSAL -> sephirah("gevurah")!!
        QuillaIntent.GENERAL -> sephirah("tiferet")!!
    }

    fun sacredFormFor(intent: QuillaIntent): SacredForm = when (intent) {
        QuillaIntent.STATUS -> sacredForm("sri_yantra")!!
        QuillaIntent.SCAN -> sacredForm("seed_of_life")!!
        QuillaIntent.SHIELD -> sacredForm("merkaba")!!
        QuillaIntent.TIMELINE -> sacredForm("tetractys")!!
        QuillaIntent.RESEARCH -> sacredForm("flower_of_life")!!
        QuillaIntent.KNOWLEDGE -> sacredForm("metatrons_cube")!!
        QuillaIntent.CAPABILITIES -> sacredForm("metatrons_cube")!!
        QuillaIntent.ETHICS_REFUSAL -> sacredForm("merkaba")!!
        QuillaIntent.GENERAL -> sacredForm("vesica_piscis")!!
    }

    /**
     * Walk the Tetragrammaton + destination Sephirah for a turn.
     * Shape of Quilla's runtime pipeline (Yod→He→Vav→He′) with Tree landing.
     */
    fun walkPath(
        intent: QuillaIntent,
        modulesUsed: List<QuillaModule>,
        postureLabel: String?
    ): List<QuillaPathStep> {
        val dest = destinationFor(intent)
        val form = sacredFormFor(intent)
        val aspect = aspectForPosture(postureLabel)
        val steps = mutableListOf<QuillaPathStep>()

        // י — Brain spark (always)
        steps += QuillaPathStep(
            letter = TetragramLetter.YOD.hebrew,
            sephirah = QuillaModule.BRAIN.sephirah,
            angel = QuillaModule.BRAIN.angel,
            module = QuillaModule.BRAIN,
            role = "Yod · classify intent → ${intent.name}"
        )
        // ה — Memory vessel (when Memory is in the path)
        if (QuillaModule.MEMORY in modulesUsed) {
            steps += QuillaPathStep(
                letter = TetragramLetter.HE_UPPER.hebrew,
                sephirah = QuillaModule.MEMORY.sephirah,
                angel = QuillaModule.MEMORY.angel,
                module = QuillaModule.MEMORY,
                role = "He · load device Memory (Gabriel's mirror)"
            )
        }
        // ו — Research / Knowledge channel
        if (QuillaModule.RESEARCH in modulesUsed) {
            steps += QuillaPathStep(
                letter = TetragramLetter.VAV.hebrew,
                sephirah = QuillaModule.RESEARCH.sephirah,
                angel = QuillaModule.RESEARCH.angel,
                module = QuillaModule.RESEARCH,
                role = "Vav · Research channel (Raziel)"
            )
        }
        if (QuillaModule.KNOWLEDGE in modulesUsed) {
            steps += QuillaPathStep(
                letter = TetragramLetter.VAV.hebrew,
                sephirah = QuillaModule.KNOWLEDGE.sephirah,
                angel = QuillaModule.KNOWLEDGE.angel,
                module = QuillaModule.KNOWLEDGE,
                role = "Vav · Knowledge vessel (Tzaphkiel)"
            )
        }
        // תפארת balance from posture aspect (Black Cross)
        steps += QuillaPathStep(
            letter = "✦",
            sephirah = sephirah(aspect.sephirahId)?.name ?: "Tiferet",
            angel = aspect.name,
            module = null,
            role = "Tiferet / Black Cross · posture $postureLabel via ${aspect.name}"
        )
        // Enochian Watchtower quarter for this intent
        val quarter = EnochianWatchtowers.quarterFor(intent)
        steps += QuillaPathStep(
            letter = "⊞",
            sephirah = "${quarter.direction}/${quarter.element}",
            angel = "${quarter.king}/${quarter.senior}",
            module = quarter.moduleHints.firstOrNull(),
            role = "Watchtower ${quarter.direction} · ${quarter.kabbalahArchangel}"
        )
        // Destination on the Tree
        steps += QuillaPathStep(
            letter = form.glyph,
            sephirah = dest.name,
            angel = dest.angel,
            module = dest.moduleHint,
            role = "Path → ${dest.name} · ${form.name}"
        )
        // ה′ — Actions / Tools manifestation
        if (QuillaModule.ACTIONS in modulesUsed) {
            steps += QuillaPathStep(
                letter = TetragramLetter.HE_FINAL.hebrew,
                sephirah = QuillaModule.ACTIONS.sephirah,
                angel = QuillaModule.ACTIONS.angel,
                module = QuillaModule.ACTIONS,
                role = "He′ · suggest Actions (Haniel)"
            )
        }
        if (QuillaModule.TOOLS in modulesUsed) {
            steps += QuillaPathStep(
                letter = TetragramLetter.HE_FINAL.hebrew,
                sephirah = QuillaModule.TOOLS.sephirah,
                angel = QuillaModule.TOOLS.angel,
                module = QuillaModule.TOOLS,
                role = "He′ · name Tools (Michael)"
            )
        }
        return steps
    }

    fun formatPath(path: List<QuillaPathStep>): String =
        path.joinToString(" → ") { "${it.letter}${it.sephirah}" }

    fun treeBlurb(): String = buildString {
        append("Quilla's Tree of Life (metaphor):\n")
        sephirot.forEach { s ->
            append("• ")
            append(s.name)
            append(" — ")
            append(s.angel)
            append(" (")
            append(s.geometry)
            append(") → ")
            append(s.securityLens)
            append('\n')
        }
        append("Tetragrammaton pillars: ")
        append(TetragramLetter.entries.joinToString(" → ") { "${it.hebrew} ${it.quillaRole}" })
        append('.')
    }
}
