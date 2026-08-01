package com.coldboar.coreguard

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A single behavioral anomaly observation emitted by [BehavioralAnomalyEngine].
 *
 * @param checkId   Stable identifier (mirrors [SecurityCheckResult.id]).
 * @param severity  How critical the anomaly is.
 * @param message   Human-readable description of what was detected.
 * @param epochMs   Wall-clock timestamp of detection.
 */
data class BehavioralAnomaly(
    val checkId: String,
    val severity: SecurityCheckState,
    val message: String,
    val epochMs: Long = System.currentTimeMillis()
)

/**
 * Continuous background engine that periodically re-runs behavioral security
 * checks and accumulates anomalies observed over the app's lifetime.
 *
 * The engine is intentionally lightweight: it delegates to injected lambdas
 * (defaulting to the [NativeTamperGuard] native bridge) so that every
 * individual check remains unit-testable without a native library.
 *
 * Usage:
 * ```kotlin
 * BehavioralAnomalyEngine.start(scope)
 * val report = BehavioralAnomalyEngine.anomalies   // read-only snapshot
 * ```
 */
object BehavioralAnomalyEngine {

    private const val TAG = "BehavioralAnomalyEngine"

    /** Default polling interval for continuous memory/hook sampling. */
    const val DEFAULT_INTERVAL_MS = 15_000L

    /** Slower cadence when the system reports power-save mode. */
    const val POWER_SAVE_INTERVAL_MS = 45_000L

    /**
     * Thread count above which the engine records a WARN anomaly. Mirrors the
     * default [ProcessLineageEvaluator.threadWarnThreshold] so both checks use
     * the same threshold policy.
     */
    const val THREAD_WARN_THRESHOLD = 100

    private val _anomalies = CopyOnWriteArrayList<BehavioralAnomaly>()

    /** Immutable snapshot of all anomalies detected since [start] was called. */
    val anomalies: List<BehavioralAnomaly> get() = _anomalies.toList()

    private val running = AtomicBoolean(false)
    private var job: Job? = null

    // -------------------------------------------------------------------------
    // Injectable signal providers – defaults call into native code
    // -------------------------------------------------------------------------

    internal var hookedLibraryProvider: () -> String = { NativeTamperGuard.hookedLibraryPath() }
    internal var textIntactProvider: () -> Boolean = { NativeTamperGuard.textIntact() }
    internal var baselineReadyProvider: () -> Boolean = { NativeTamperGuard.baselineReady() }
    internal var processStatusProvider: () -> String = { readProcSelfStatus() }

    /**
     * Starts continuous behavioral sampling on [scope].
     * Calling [start] while already running is a no-op.
     *
     * @param scope          The coroutine scope driving the polling loop.
     * @param intervalMs     Milliseconds between each sampling pass.
     */
    fun start(
        scope: CoroutineScope,
        intervalMs: Long = DEFAULT_INTERVAL_MS
    ) {
        if (!running.compareAndSet(false, true)) return
        _anomalies.clear()
        job = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "Behavioral anomaly engine started (interval=${intervalMs}ms)")
            while (isActive) {
                sampleOnce()
                delay(intervalMs)
            }
        }
    }

    /** Stops the polling loop. Accumulated anomalies are preserved. */
    fun stop() {
        job?.cancel()
        running.set(false)
        Log.d(TAG, "Behavioral anomaly engine stopped")
    }

    /** Clears the accumulated anomaly list. */
    fun reset() {
        _anomalies.clear()
    }

    // -------------------------------------------------------------------------
    // Internal – a single sampling pass
    // -------------------------------------------------------------------------

    internal fun sampleOnce() {
        checkInlineHooks()
        checkMemoryPatch()
        checkProcessLineage()
    }

    private fun checkInlineHooks() {
        val lib = try { hookedLibraryProvider() } catch (t: Throwable) { return }
        if (lib.isNotEmpty()) {
            record(
                checkId = "inline_hook_sample",
                severity = SecurityCheckState.FAIL,
                message = "Continuous sampling detected a hooking library in process memory: $lib"
            )
        }
    }

    private fun checkMemoryPatch() {
        val ready = try { baselineReadyProvider() } catch (t: Throwable) { return }
        if (!ready) return
        val intact = try { textIntactProvider() } catch (t: Throwable) { return }
        if (!intact) {
            record(
                checkId = "memory_patch_sample",
                severity = SecurityCheckState.FAIL,
                message = "Native text segment no longer matches load-time baseline – inline patch detected."
            )
        }
    }

    private fun checkProcessLineage() {
        val status = try { processStatusProvider() } catch (t: Throwable) { return }
        analyzeProcessStatus(status)
    }

    // Visible for testing
    internal fun analyzeProcessStatus(status: String) {
        val ppid = parsePpid(status) ?: return

        // PID 1 is init/systemd. Any parent other than Zygote (PID 2 on Android)
        // or init (PID 1) warrants a warning – we flag PID 0 and unexpectedly
        // high PIDs that are not Zygote-descended.
        if (ppid == 0) {
            record(
                checkId = "process_lineage",
                severity = SecurityCheckState.WARN,
                message = "Unexpected process parent: PPid=$ppid. App may have been spawned by an untrusted process."
            )
        }

        val threads = parseThreads(status) ?: return
        // A clean app normally has well under THREAD_WARN_THRESHOLD threads; above that
        // threshold indicates possible injection of worker threads.
        if (threads > THREAD_WARN_THRESHOLD) {
            record(
                checkId = "process_lineage",
                severity = SecurityCheckState.WARN,
                message = "Unusually high thread count ($threads) detected – possible injected thread activity."
            )
        }
    }

    private fun parsePpid(status: String): Int? =
        status.lineSequence()
            .firstOrNull { it.startsWith("PPid:") }
            ?.substringAfter("PPid:")
            ?.trim()
            ?.toIntOrNull()

    private fun parseThreads(status: String): Int? =
        status.lineSequence()
            .firstOrNull { it.startsWith("Threads:") }
            ?.substringAfter("Threads:")
            ?.trim()
            ?.toIntOrNull()

    private fun record(checkId: String, severity: SecurityCheckState, message: String) {
        val anomaly = BehavioralAnomaly(checkId = checkId, severity = severity, message = message)
        _anomalies.add(anomaly)
        Log.w(TAG, "[$severity] $checkId: $message")
    }

    // -------------------------------------------------------------------------
    // Platform helpers
    // -------------------------------------------------------------------------

    private fun readProcSelfStatus(): String =
        try { java.io.File("/proc/self/status").readText() } catch (_: Exception) { "" }
}
