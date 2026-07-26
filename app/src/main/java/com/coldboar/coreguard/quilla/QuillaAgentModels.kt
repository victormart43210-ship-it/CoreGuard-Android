package com.coldboar.coreguard.quilla

/**
 * Architecture modules for Ultimate Quilla — Living Geometry edition.
 *
 * Each module sits on a Sephirah with an angelic aspect name and a
 * Tetragrammaton letter. Names are **runtime scaffolding** for path-walking
 * and UI voice; they do not detect threats.
 *
 * ```
 *   י Yod  → Brain (Keter · Metatron)
 *   ה He   → Memory (Yesod · Gabriel)
 *   ו Vav  → Research/Knowledge (Chokmah/Binah · Raziel/Tzaphkiel)
 *   ה He′  → Actions/Tools (Netzach/Hod · Haniel/Michael)
 * ```
 */
enum class QuillaModule(
    val label: String,
    val superpower: String,
    val sephirah: String,
    val angel: String,
    val hebrewLetter: String,
    val geometryGlyph: String
) {
    BRAIN("Brain", "Reason & decide", "Keter", "Metatron", "י", "•"),
    MEMORY("Memory", "Long-term device context", "Yesod", "Gabriel", "ה", "◎"),
    RESEARCH("Research", "Optional Amnesty/MVT STIX", "Chokmah", "Raziel", "ו", "⚡"),
    KNOWLEDGE("Knowledge", "Cybersecurity codex", "Binah", "Tzaphkiel", "ו", "△"),
    ACTIONS("Actions", "Suggest next steps", "Netzach", "Haniel", "ה", "◇"),
    TOOLS("Tools", "Scanner · Shield · Timeline", "Hod", "Michael", "ה", "⬡");

    /** Chip label used in the Quilla HUD. */
    val livingLabel: String get() = "$label · $angel"

    val pathNode: String get() = "$hebrewLetter $sephirah ($angel)"
}

enum class QuillaIntent {
    STATUS,
    SCAN,
    SHIELD,
    TIMELINE,
    RESEARCH,
    KNOWLEDGE,
    CAPABILITIES,
    ETHICS_REFUSAL,
    GENERAL
}

data class QuillaModuleStatus(
    val module: QuillaModule,
    val ready: Boolean,
    val detail: String
)

data class QuillaActionSuggestion(
    val id: String,
    val label: String,
    val description: String
) {
    companion object {
        const val RUN_SCAN = "run_scan"
        const val OPEN_SHIELD = "open_shield"
        const val OPEN_TIMELINE = "open_timeline"
        const val SYNC_INTEL = "sync_intel"
    }
}

/**
 * Snapshot of durable device context Quilla's Memory module can cite.
 */
data class QuillaMemorySnapshot(
    val lastScanVerdict: String? = null,
    val lastScanDetections: Int? = null,
    /** Short titles from the latest Nemesis detections (MVT-style matches). */
    val lastScanDetectionTitles: List<String> = emptyList(),
    val historyCount: Int = 0,
    val shieldActive: Boolean = false,
    val shieldBlocked: Int = 0,
    val lastBlockedDomain: String? = null,
    val activeHypotheses: List<String> = emptyList(),
    /** On-device MVT/Nemesis IOC inventory size available to Quilla correlation. */
    val mvtIocInventoryCount: Int = 0,
    /** Correlator IOC count currently loaded for Amnesty/MVT matching. */
    val correlatorIndicatorCount: Int = 0,
    /** Signed telemetry frames retained in the on-device ring. */
    val telemetryDeltaCount: Int = 0,
    /** True when any recent telemetry frame is HIGH/CRITICAL. */
    val telemetryHighSeverity: Boolean = false,
    /** Angelic Defense Blessings seal line (evidence-backed choir status). */
    val blessingSeal: String? = null,
    /** Short per-angel blessing lines for Quilla voice. */
    val blessingLines: List<String> = emptyList(),
    /** Count of BREACHED angelic blessings (needs human attention). */
    val blessingsBreached: Int = 0,
    /** Count of ACTIVE angelic blessings. */
    val blessingsActive: Int = 0,
    /** Last quantum-inspired circuit seal (classical simulation). */
    val quantumSeal: String? = null,
    /** Last quantum collapse probability [0,1], if a circuit ran. */
    val quantumCollapse: Float? = null,
    /** True when the last circuit measured COLLAPSED. */
    val quantumCollapsed: Boolean = false
)

/** Short follow-up prompts Quilla offers after an answer. */
data class QuillaFollowUp(
    val label: String,
    val prompt: String
)

data class QuillaResearchSnapshot(
    val indicatorCount: Int = 0,
    /** Indicators from remote Amnesty / MVT public STIX pulls (Quilla Research only). */
    val remoteIndicatorCount: Int = 0,
    /** On-device MVT-style IOCs merged from Nemesis inventory into the correlator. */
    val mvtOnDeviceCount: Int = 0,
    /** Defensive knowledge entries ingested from public web intel (CISA KEV / MISP). */
    val webKnowledgeCount: Int = 0,
    /** Human-readable feed notes from the last [QuillaIntelNetwork] sync. */
    val feedNotes: List<String> = emptyList(),
    /** True only when the last sync attempt completed without throwing. */
    val synced: Boolean = false,
    /** True when the last sync attempt failed (network/parse). Distinct from empty feed. */
    val syncFailed: Boolean = false,
    val sourceLabel: String = "Quilla Intel Network (Amnesty/MVT · CISA · MISP)"
)

/**
 * One step Quilla walked on the Tree / Tetragrammaton for this answer.
 * Pure bookkeeping — not a detection event.
 */
data class QuillaPathStep(
    val letter: String,
    val sephirah: String,
    val angel: String,
    val module: QuillaModule?,
    val role: String
)

data class QuillaAgentAnswer(
    val text: String,
    val intent: QuillaIntent,
    val modulesUsed: List<QuillaModule>,
    val moduleStatuses: List<QuillaModuleStatus>,
    val actions: List<QuillaActionSuggestion>,
    val followUps: List<QuillaFollowUp> = emptyList(),
    val postureLabel: String? = null,
    val postureScore: Int? = null,
    /** Compact י ה ו ה · Angel · Sephirah seal for this turn. */
    val livingSeal: String? = null,
    /** Angelic aspect selected from posture / intent (metaphor). */
    val aspectName: String? = null,
    /** Sephirah of the active aspect. */
    val sephirahName: String? = null,
    /** Tetragrammaton / Tree path walked while composing this answer. */
    val pathWalked: List<QuillaPathStep> = emptyList()
)
