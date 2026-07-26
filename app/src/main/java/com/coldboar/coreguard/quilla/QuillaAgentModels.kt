package com.coldboar.coreguard.quilla

/**
 * Architecture modules for Ultimate Quilla, mapped from the common AI-agent
 * formula (Brain / Memory / Research / Actions / Tools) onto CoreGuard's
 * on-device security stack — no external SaaS API keys required.
 */
enum class QuillaModule(val label: String, val superpower: String) {
    BRAIN("Brain", "Reason & decide"),
    MEMORY("Memory", "Long-term device context"),
    RESEARCH("Research", "Live threat intel"),
    KNOWLEDGE("Knowledge", "Cybersecurity codex"),
    ACTIONS("Actions", "Automate defenses"),
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
    val historyCount: Int = 0,
    val shieldActive: Boolean = false,
    val shieldBlocked: Int = 0,
    val lastBlockedDomain: String? = null,
    val activeHypotheses: List<String> = emptyList()
)

data class QuillaResearchSnapshot(
    val indicatorCount: Int = 0,
    val synced: Boolean = false,
    val sourceLabel: String = "Amnesty STIX2"
)

data class QuillaAgentAnswer(
    val text: String,
    val intent: QuillaIntent,
    val modulesUsed: List<QuillaModule>,
    val moduleStatuses: List<QuillaModuleStatus>,
    val actions: List<QuillaActionSuggestion>
)
