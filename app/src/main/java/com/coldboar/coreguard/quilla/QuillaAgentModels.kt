package com.coldboar.coreguard.quilla

/**
 * Architecture modules for Ultimate Quilla, mapped from the common AI-agent
 * formula (Brain / Memory / Research / Actions / Tools) onto CoreGuard's
 * on-device security stack — no external SaaS API keys required.
 */
enum class QuillaModule(val label: String, val superpower: String) {
    BRAIN("Brain", "Reason & decide"),
    MEMORY("Memory", "Long-term device context"),
    RESEARCH("Research", "Optional Amnesty/MVT STIX"),
    KNOWLEDGE("Knowledge", "Cybersecurity codex"),
    ACTIONS("Actions", "Suggest next steps"),
    TOOLS("Tools", "Scanner · Shield · Timeline")
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
    val telemetryHighSeverity: Boolean = false
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

data class QuillaAgentAnswer(
    val text: String,
    val intent: QuillaIntent,
    val modulesUsed: List<QuillaModule>,
    val moduleStatuses: List<QuillaModuleStatus>,
    val actions: List<QuillaActionSuggestion>,
    val followUps: List<QuillaFollowUp> = emptyList(),
    val postureLabel: String? = null,
    val postureScore: Int? = null
)
