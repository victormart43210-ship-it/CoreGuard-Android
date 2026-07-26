package com.coldboar.coreguard.elite

import android.content.Context

/**
 * Module-pattern façade for **CoreGuard Elite** zero-trust surfaces.
 *
 * UI, Quilla chips, and Application boot should call **this** object — not
 * [DynamicThreatEngine], [ForensicJournal], or [ScamGuardEngine] internals —
 * so a future `:feature:elite` Gradle module can extract without rewriting
 * Compose screens (see `docs/MODULE_ARCHITECTURE.md`).
 *
 * ## What this module owns
 *
 * | Concern | Hidden behind façade | Public entry |
 * |---------|----------------------|--------------|
 * | Dynamic Threat Score | Weighted correlator + Quilla quantum blend | [evaluateThreatScore] |
 * | Scam Guard | URL heuristics + optional notification parse | [inspectScamText] / [scoreScamUrl] |
 * | Forensic Journal | SHA-256 chain + StrongBox encrypt-at-rest | [journalEntries] / [exportJournalJson] |
 * | Elite threat Counter | Redux store for DTS + amber scam count | [threatCounter] |
 *
 * ## Redux Counter (UI separation)
 *
 * [threatCounter] is a Redux-style store. The Elite Home dashboard
 * ([com.coldboar.coreguard.ui.dashboard.EliteDashboardScreen]) and any Counter
 * chip must **subscribe + dispatch only** — never own `dtsScore` / amber ints
 * as business source of truth. Engines feed the store through this façade after
 * evaluations so side effects (journal append) stay out of Compose.
 *
 * ## Honesty
 *
 * DTS is an on-device classical correlator (Quilla quantum-*inspired*), not a
 * cloud LLM or NPU small language model. Scam Guard needs user-granted
 * Notification access for live interception. Overlay kill of third-party windows
 * is outside Play-app privilege — Matrix audits and journals only.
 */
object EliteModule {

    /**
     * Process-wide Redux-style Elite threat Counter.
     * Single source of truth for DTS band/score and Scam amber count on Home.
     */
    val threatCounter: EliteThreatCounterStore = EliteThreatCounterStore()

    /**
     * Run the on-device Dynamic Threat Score correlator and **dispatch** the
     * result into [threatCounter]. Journal side effects remain inside
     * [DynamicThreatEngine] (band-transition only).
     *
     * Prefer this over calling [DynamicThreatEngine.evaluate] from UI.
     */
    fun evaluateThreatScore(context: Context): DynamicThreatEngine.ThreatScore {
        val score = DynamicThreatEngine.evaluate(context)
        threatCounter.dispatch(
            EliteThreatCounterStore.Action.ThreatScoreUpdated(
                score = score.score,
                band = score.band,
                summary = score.summary
            )
        )
        return score
    }

    /**
     * Inspect free-form notification / SMS-style text for phishing URLs.
     * Publishes into Scam Guard memory and, when amber+, into [threatCounter].
     */
    fun inspectScamText(
        context: Context,
        text: String,
        source: String = "notification"
    ): ScamGuardEngine.Finding? {
        val finding = ScamGuardEngine.inspectNotificationText(context, text, source)
        finding?.let { onScamFinding(it) }
        return finding
    }

    /**
     * Score a single URL or paste blob (manual path on Scam Guard screen).
     * Does not require Notification Listener permission — same Counter bridge
     * as [inspectScamText].
     */
    fun scoreScamUrl(
        context: Context,
        url: String,
        source: String = "manual"
    ): ScamGuardEngine.Finding? = inspectScamText(context, url, source)

    /**
     * Append a forensic event through the module boundary (screens should not
     * import [ForensicJournal] when a façade method exists).
     */
    fun appendJournal(
        context: Context,
        kind: ForensicJournal.EventKind,
        packageName: String?,
        details: String,
        metadata: Map<String, String> = emptyMap()
    ): ForensicJournal.Entry =
        ForensicJournal.append(context, kind, packageName, details, metadata)

    /**
     * Bridge for [ScamGuardNotificationListener] after local parse.
     * Safe to call from a binder thread — store dispatch is synchronized.
     */
    fun onScamFinding(finding: ScamGuardEngine.Finding) {
        threatCounter.dispatch(
            EliteThreatCounterStore.Action.ScamFindingObserved(
                host = finding.host,
                score = finding.score
            )
        )
    }

    /** Snapshot of journal entries (newest-last from engine; UI may reverse). */
    fun journalEntries(context: Context): List<ForensicJournal.Entry> =
        ForensicJournal.all(context)

    /** Verify SHA-256 chain integrity for the on-device journal. */
    fun verifyJournalChain(context: Context): Boolean =
        ForensicJournal.verifyChain(context)

    /** Export plaintext JSON for share-sheet / IT handoff. */
    fun exportJournalJson(context: Context): String =
        ForensicJournal.exportJson(context)

    /** Export CSV for spreadsheet analysts. */
    fun exportJournalCsv(context: Context): String =
        ForensicJournal.exportCsv(context)

    /** User-initiated wipe of the on-device forensic journal. */
    fun clearJournal(context: Context) {
        ForensicJournal.clear(context)
    }

    /** UI / tests: reset Elite Counter without clearing the journal file. */
    fun resetThreatCounter() {
        threatCounter.dispatch(EliteThreatCounterStore.Action.Reset)
    }

    /** Latest Scam Guard finding from the engine ring (may be below amber). */
    fun latestScamFinding(): ScamGuardEngine.Finding? =
        ScamGuardEngine.latestFinding()

    /** Recent Scam Guard findings (newest first). */
    fun recentScamFindings(): List<ScamGuardEngine.Finding> =
        ScamGuardEngine.recentFindings()
}
