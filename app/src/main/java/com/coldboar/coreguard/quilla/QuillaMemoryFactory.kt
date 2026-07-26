package com.coldboar.coreguard.quilla

import android.content.Context
import com.coldboar.coreguard.SecurityCheckRunner
import com.coldboar.coreguard.defense.AngelicDefenseBlessings
import com.coldboar.coreguard.elite.EliteModule
import com.coldboar.coreguard.elite.ForensicJournal
import com.coldboar.coreguard.mvt.IocRepository
import com.coldboar.coreguard.mvt.LastScan
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict
import com.coldboar.coreguard.mvt.ShieldState
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeAssets
import com.coldboar.coreguard.swarm.SwarmModule
import com.coldboar.coreguard.swarm.SwarmSeverity
import com.coldboar.coreguard.swarm.SwarmSignal
import com.coldboar.coreguard.swarm.SwarmSignalType
import com.coreguard.security.telemetry.RiskSeverity
import com.coreguard.security.telemetry.TelemetryBridge
import java.util.concurrent.atomic.AtomicReference

/**
 * Builds [QuillaMemorySnapshot] / [QuillaResearchSnapshot] from live CoreGuard state.
 *
 * Threat-intel honesty:
 * - Research sync uses [QuillaIntelNetwork] (Amnesty/MVT STIX + CISA/MISP/Malpedia web intel)
 *   then runs [QuillaInfinityTrainer] to harden angels + swarm (uncapped teaching).
 * - Nemesis scan completion is bridged via [onScanCompleted] into hypotheses, choir,
 *   Elite DTS, and (on hits) Forensic Journal + swarm — evidence only, no lore inventing.
 * - On-device MVT IOCs from [IocRepository] are also merged for correlation.
 * - Neither path writes Scanner signatures for free users; Premium Nemesis
 *   refresh remains [com.coldboar.coreguard.mvt.IocFeedFetcher].
 */
object QuillaMemoryFactory {

    private val sharedStore = QuillaHypothesisStore()
    private val sharedCorrelation = QuillaCorrelationEngine(sharedStore)
    private var cachedResearch = QuillaResearchSnapshot()
    private val lastScanBridge = AtomicReference<QuillaScanBridgeResult?>(null)

    @Volatile
    private var localIntelLoaded = false

    fun hypothesisStore(): QuillaHypothesisStore = sharedStore

    fun correlationEngine(): QuillaCorrelationEngine = sharedCorrelation

    /** Most recent Nemesis→Quilla/choir bridge result (process-local). */
    fun lastScanBridge(): QuillaScanBridgeResult? = lastScanBridge.get()

    /** Call after Premium Nemesis feed write so Quilla re-reads inventory. */
    fun invalidateLocalIntel() {
        localIntelLoaded = false
    }

    /**
     * Lazily merges on-device MVT/Nemesis IOCs into the Quilla correlator.
     * Safe to call from scan / shield paths; no network I/O.
     */
    fun ensureLocalIntel(context: Context) {
        if (localIntelLoaded) return
        synchronized(this) {
            if (localIntelLoaded) return
            val onDevice = QuillaIocBridge.fromMvtIndicators(IocRepository.indicators(context))
            sharedCorrelation.mergeIndicators(onDevice)
            localIntelLoaded = true
        }
    }

    /**
     * Connect a completed Nemesis [ScanReport] to Quilla Memory, the angelic choir,
     * Elite DTS, and (when there are hits) Forensic Journal + swarm.
     *
     * Call from [com.coldboar.coreguard.mvt.ScannerModule.scanDevice] on a background
     * thread. Does **not** run Infinity training (codex teaching ≠ scan evidence).
     */
    fun onScanCompleted(context: Context, report: ScanReport): QuillaScanBridgeResult {
        LastScan.report = report
        ensureLocalIntel(context)
        QuillaIocBridge.recordScanDetections(report, sharedStore)
        QuillaIocBridge.correlateScanArtifacts(report, sharedCorrelation)

        val dts = runCatching { EliteModule.evaluateThreatScore(context) }.getOrNull()

        var journaled = false
        var swarmNotified = false
        if (report.verdict != ScanVerdict.CLEAN || report.detections.isNotEmpty()) {
            runCatching {
                EliteModule.appendJournal(
                    context = context,
                    kind = ForensicJournal.EventKind.NEMESIS_SCAN,
                    packageName = report.detections.firstOrNull()?.artifact,
                    details = "Nemesis ${report.verdict.name}: ${report.detections.size} detection(s) " +
                        "across ${report.scannedArtifacts} artifacts · indicators=${report.indicatorCount}",
                    metadata = mapOf(
                        "verdict" to report.verdict.name,
                        "detections" to report.detections.size.toString(),
                        "scannedArtifacts" to report.scannedArtifacts.toString(),
                        "titles" to report.detections.take(5).joinToString("|") { it.title }
                    )
                )
                journaled = true
            }
        }
        if (report.detections.isNotEmpty()) {
            val severity = when (report.verdict) {
                ScanVerdict.INFECTED -> SwarmSeverity.CRITICAL
                ScanVerdict.SUSPICIOUS -> SwarmSeverity.WARN
                ScanVerdict.CLEAN -> SwarmSeverity.INFO
            }
            if (severity >= SwarmSeverity.WARN) {
                SwarmModule.onAlertRouted(
                    SwarmSignal(
                        agentId = "nemesis.scanner",
                        signalType = SwarmSignalType.PROCESS_ANOMALY,
                        severity = severity,
                        details = "Nemesis ${report.verdict.name}: ${report.detections.size} IOC hit(s) " +
                            "— Quilla choir (Tzadkiel) reviewing evidence.",
                        metadata = mapOf(
                            "verdict" to report.verdict.name,
                            "detections" to report.detections.size.toString(),
                            "source" to "nemesis"
                        )
                    )
                )
                swarmNotified = true
            }
        }

        val (_, choir) = memoryAndChoir(context)
        val tzadkiel = choir.blessings.firstOrNull { it.angel == "Tzadkiel" }
        val result = QuillaScanBridgeResult(
            verdict = report.verdict.name,
            detectionCount = report.detections.size,
            hypothesisCount = sharedStore.all().count { it.status.equals("ACTIVE", ignoreCase = true) },
            choirSeal = choir.sealLine,
            blessingsActive = choir.activeCount,
            blessingsBreached = choir.breachedCount,
            blessingsWatching = choir.watchingCount,
            tzadkielState = tzadkiel?.state?.name ?: "IDLE",
            tzadkielDetail = tzadkiel?.detail ?: "Mercy Scan idle.",
            dtsScore = dts?.score,
            dtsBand = dts?.band?.name,
            journaled = journaled,
            swarmNotified = swarmNotified
        )
        lastScanBridge.set(result)
        return result
    }

    fun memorySnapshot(context: Context): QuillaMemorySnapshot = memoryAndChoir(context).first

    private fun memoryAndChoir(
        context: Context
    ): Pair<QuillaMemorySnapshot, AngelicDefenseBlessings.ChoirReport> {
        ensureLocalIntel(context)
        val history = runCatching { ScanHistoryStore.load(context) }.getOrDefault(emptyList())
        val last = LastScan.report
        val newest = history.firstOrNull()
        val iocCount = runCatching { IocRepository.indicators(context).size }.getOrDefault(0)
        val telemetry = TelemetryBridge.ringBuffer().snapshot()
        val highTelemetry = telemetry.any {
            it.delta.severity == RiskSeverity.HIGH || it.delta.severity == RiskSeverity.CRITICAL
        }
        val base = QuillaMemorySnapshot(
            lastScanVerdict = last?.verdict?.name ?: newest?.verdict?.name,
            lastScanDetections = last?.detections?.size ?: newest?.detectionCount,
            lastScanDetectionTitles = last?.detections?.map { it.title }
                ?.take(QuillaAwareness.DETECTION_TITLE_VOICE).orEmpty(),
            historyCount = history.size,
            shieldActive = ShieldState.isActive,
            shieldBlocked = ShieldState.totalBlocked,
            lastBlockedDomain = ShieldState.lastBlockedDomain,
            activeHypotheses = sharedStore.all()
                .filter { it.status.equals("ACTIVE", ignoreCase = true) }
                .map { it.summary },
            mvtIocInventoryCount = iocCount,
            correlatorIndicatorCount = sharedCorrelation.indicatorCount(),
            telemetryDeltaCount = telemetry.size,
            telemetryHighSeverity = highTelemetry
        )
        val checks = runCatching { SecurityCheckRunner.run(context) }.getOrDefault(emptyList())
        val choir = AngelicDefenseBlessings.evaluate(checks, base, cachedResearch)
        val quantum = sharedCorrelation.lastQuantumReport()
        val memory = base.copy(
            blessingSeal = choir.sealLine,
            blessingLines = AngelicDefenseBlessings.summaryLines(choir),
            blessingsBreached = choir.breachedCount,
            blessingsActive = choir.activeCount,
            quantumSeal = quantum?.seal,
            quantumCollapse = quantum?.collapseProbability,
            quantumCollapsed = quantum?.collapsed == true
        )
        return memory to choir
    }

    fun cachedResearch(): QuillaResearchSnapshot = cachedResearch

    /**
     * Synchronous intel sync for Research module via [QuillaIntelNetwork].
     * Call from a background dispatcher.
     *
     * Honesty rules:
     * - [QuillaResearchSnapshot.synced] is true only when the network sync reports success.
     * - Failure leaves [QuillaResearchSnapshot.syncFailed] true and does not claim success.
     * - This feed feeds Quilla Research / correlation only — it does **not** refresh
     *   Nemesis Scanner signatures.
     */
    fun syncResearch(context: Context): QuillaResearchSnapshot {
        val network = QuillaIntelNetwork.syncAll(context)
        localIntelLoaded = true
        cachedResearch = QuillaResearchSnapshot(
            indicatorCount = network.mergedCorrelatorCount,
            remoteIndicatorCount = network.stixIndicatorCount,
            mvtOnDeviceCount = network.onDeviceMvtCount,
            webKnowledgeCount = network.webKnowledgeCount,
            feedNotes = network.feedNotes,
            synced = network.synced && !network.syncFailed,
            syncFailed = network.syncFailed,
            sourceLabel = network.sourceLabel,
            infinityGeneration = network.infinityGeneration,
            infinityMalwareStudied = network.infinityMalwareStudied,
            infinityVulnStudied = network.infinityVulnStudied,
            infinityCodexDepth = network.infinityCodexDepth
        )
        return cachedResearch
    }

    /**
     * Offline Infinity pass — trains angels/swarm on the bundled Cyber Codex
     * without requiring HTTPS. Useful when the user asks to "train the choir"
     * on-device with no network.
     */
    fun trainInfinityLocal(context: Context): AngelSwarmTrainingLedger {
        QuillaInfinityTrainer.restoreLite(context)
        CyberKnowledgeAssets.ensureLoaded(context)
        val training = QuillaInfinityTrainer.trainFromCodex(
            context = context,
            network = QuillaIntelNetwork.lastSnapshot(),
            correlatorIndicatorCount = cachedResearch.indicatorCount
        )
        cachedResearch = cachedResearch.copy(
            infinityGeneration = training.generation,
            infinityMalwareStudied = training.malwareEntriesStudied,
            infinityVulnStudied = training.vulnerabilityEntriesStudied,
            infinityCodexDepth = training.totalCodexEntries,
            feedNotes = (cachedResearch.feedNotes + training.summaryLine()).distinct()
        )
        return training
    }
}
