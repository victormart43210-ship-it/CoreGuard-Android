package com.coldboar.coreguard.mvt

/**
 * Stages of the Nemesis scan in execution order.
 *
 * These stages correspond to the three artifact categories the scanner checks:
 * installed packages, running processes, and accessible files.
 */
enum class ScanStage {
    /** Enumerating and matching installed application package names. */
    PACKAGES,
    /** Reading and matching visible process names (best-effort, limited by hidepid). */
    PROCESSES,
    /** Walking and matching accessible file paths in app-accessible storage. */
    FILES,
    /** Finalizing results and persisting the scan record. */
    FINALIZING
}

/**
 * Callback interface for receiving real-time progress from the Nemesis scanner.
 *
 * Implementations must be thread-safe; callbacks may be invoked from a
 * background thread.
 *
 * Progress values are in the range [0.0, 1.0] and represent completion within
 * the current stage (not overall scan progress).
 */
interface ScanProgressListener {
    /**
     * Called when the scanner moves to [stage] with progress within that stage.
     *
     * @param stage    the current scan stage.
     * @param progress fraction complete within this stage (0.0–1.0).
     */
    fun onStage(stage: ScanStage, progress: Float)
}
