package com.coldboar.coreguard.quilla

import android.content.Context
import com.coldboar.coreguard.mvt.ScanReport

/**
 * Compatibility alias for [QuillaMemoryModule].
 *
 * Prefer [QuillaMemoryModule] from UI and feature code — that is the
 * module-pattern façade documented in `docs/MODULE_ARCHITECTURE.md`.
 */
@Deprecated(
    message = "Use QuillaMemoryModule (module-pattern façade)",
    replaceWith = ReplaceWith(
        "QuillaMemoryModule",
        "com.coldboar.coreguard.quilla.QuillaMemoryModule"
    )
)
object QuillaMemoryFactory {

    fun hypothesisStore(): QuillaHypothesisStore = QuillaMemoryModule.hypothesisStore()

    fun correlationEngine(): QuillaCorrelationEngine = QuillaMemoryModule.correlationEngine()

    fun lastScanBridge(): QuillaScanBridgeResult? = QuillaMemoryModule.lastScanBridge()

    fun invalidateLocalIntel() = QuillaMemoryModule.invalidateLocalIntel()

    fun ensureLocalIntel(context: Context) = QuillaMemoryModule.ensureLocalIntel(context)

    fun onScanCompleted(context: Context, report: ScanReport): QuillaScanBridgeResult =
        QuillaMemoryModule.onScanCompleted(context, report)

    fun memorySnapshot(context: Context): QuillaMemorySnapshot =
        QuillaMemoryModule.memorySnapshot(context)

    fun cachedResearch(): QuillaResearchSnapshot = QuillaMemoryModule.cachedResearch()

    fun syncResearch(context: Context): QuillaResearchSnapshot =
        QuillaMemoryModule.syncResearch(context)

    fun trainInfinityLocal(context: Context): AngelSwarmTrainingLedger =
        QuillaMemoryModule.trainInfinityLocal(context)
}
