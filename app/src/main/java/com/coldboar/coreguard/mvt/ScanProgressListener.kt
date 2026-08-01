package com.coldboar.coreguard.mvt

/**
 * Stages of a Nemesis scanner run, in execution order.
 *
 * The UI can use these checkpoints to display honest, engine-driven progress
 * rather than a time-animated fake loop.
 */
enum class ScanStage {
    /** Loading / verifying the IOC dataset. */
    LOADING_INDICATORS,

    /** Enumerating installed application packages. */
    SCANNING_PACKAGES,

    /** Reading process names from /proc (best-effort). */
    SCANNING_PROCESSES,

    /** Walking app-accessible file storage paths. */
    SCANNING_FILES,

    /** Composing the final verdict from all detections. */
    COMPOSING_VERDICT
}

/**
 * Callback interface for receiving scanner progress events.
 *
 * Called from the scanner thread; implementations must be thread-safe and
 * avoid blocking (post to Main if UI updates are needed).
 */
interface ScanProgressListener {
    /**
     * Called when the scanner enters [stage].
     *
     * @param stage    The current stage.
     * @param progress A rough 0.0–1.0 progress estimate for the current stage
     *                 (0.0 = started, 1.0 = stage complete).
     */
    fun onStage(stage: ScanStage, progress: Float)
}
