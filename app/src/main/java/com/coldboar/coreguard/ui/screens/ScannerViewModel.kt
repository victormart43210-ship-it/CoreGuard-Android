package com.coldboar.coreguard.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ScanProgressListener
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanStage
import com.coldboar.coreguard.mvt.ScannerModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------

/** Phase of the scanner lifecycle reflected in the UI. */
enum class ScanPhase {
    /** No scan has been run yet in this session. */
    IDLE,

    /** A scan is currently in progress. */
    SCANNING,

    /** Scan completed normally with a valid report. */
    COMPLETE,

    /**
     * Scan was cancelled by the user.
     *
     * No score or verdict is displayed for a cancelled scan — results are
     * incomplete. The prior completed scan (if any) may still be displayed.
     */
    CANCELLED,

    /** Scan failed with an error. */
    ERROR
}

/**
 * Immutable snapshot of all scanner-screen state.
 *
 * The [ScannerScreen] renders exclusively from this state; no Compose-local
 * mutable vars track scan lifecycle.
 */
data class ScannerUiState(
    val phase: ScanPhase = ScanPhase.IDLE,

    // Progress — only meaningful when phase == SCANNING
    /** Current [ScanStage], null when not scanning. */
    val currentStage: ScanStage? = null,
    /** Per-stage progress 0.0–1.0 as reported by the engine. */
    val stageProgress: Float = 0f,
    /** Human-readable stage label for display (not an engine guarantee). */
    val stageLabel: String = "",
    /** Overall estimated progress 0.0–1.0 based on stage ordering. */
    val overallProgress: Float = 0f,

    // Results
    /** Present only when phase == COMPLETE. Never populated for CANCELLED/ERROR scans. */
    val completedReport: ScanReport? = null,

    /** Most recent scan from history (shown as fallback for IDLE/CANCELLED states). */
    val lastHistoryRecord: ScanHistoryStore.ScanRecord? = null,

    /** Error message when phase == ERROR. */
    val errorMessage: String? = null,

    // IOC refresh
    val isRefreshing: Boolean = false,
    val refreshMessage: String? = null,

    // Premium upsell
    val showUpsell: Boolean = false
)

// ---------------------------------------------------------------------------
// ViewModel
// -----------------------------------------------------------------------

/**
 * ViewModel for the Scanner screen.
 *
 * Progress is driven by real engine-emitted [ScanStage] checkpoints, not a
 * time-animated fake loop.  Cancellation is supported: if the user cancels,
 * [ScanPhase.CANCELLED] is set, no score or verdict is recorded, and the
 * latest prior history record is shown instead.
 *
 * Uses manual constructor injection (no Hilt).
 * TODO (Phase 2+): Replace [Factory] with @HiltViewModel once Hilt is added.
 */
class ScannerViewModel(
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    /** Active scan coroutine job — may be cancelled by [cancelScan]. */
    private var scanJob: Job? = null

    private val stageOrder = ScanStage.entries.toList()
    private val stageLabels = mapOf(
        ScanStage.LOADING_INDICATORS to "Loading threat indicators",
        ScanStage.SCANNING_PACKAGES to "Enumerating installed packages",
        ScanStage.SCANNING_PROCESSES to "Reading process signals",
        ScanStage.SCANNING_FILES to "Scanning file storage",
        ScanStage.COMPOSING_VERDICT to "Composing verdict"
    )

    init {
        // Load prior history for IDLE state display.
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) {
                ScannerModule.loadHistory(appContext).firstOrNull()
            }
            _uiState.update { current ->
                // Only update if no scan report is already in memory.
                val existingReport = ScannerModule.latestReport()
                when {
                    existingReport != null -> current.copy(
                        phase = ScanPhase.COMPLETE,
                        completedReport = existingReport
                    )
                    history != null -> current.copy(lastHistoryRecord = history)
                    else -> current
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Public actions
    // -----------------------------------------------------------------------

    /** Starts a scan. No-op if a scan is already in progress. */
    fun startScan() {
        if (_uiState.value.phase == ScanPhase.SCANNING) return

        _uiState.update { current ->
            current.copy(
                phase = ScanPhase.SCANNING,
                currentStage = null,
                stageProgress = 0f,
                stageLabel = "Starting…",
                overallProgress = 0f,
                completedReport = null,
                errorMessage = null,
                showUpsell = false
            )
        }

        val listener = object : ScanProgressListener {
            override fun onStage(stage: ScanStage, progress: Float) {
                val stageIdx = stageOrder.indexOf(stage)
                val overall = if (stageOrder.isEmpty()) 0f else {
                    (stageIdx + progress) / stageOrder.size
                }
                _uiState.update { current ->
                    current.copy(
                        currentStage = stage,
                        stageProgress = progress,
                        stageLabel = stageLabels[stage] ?: stage.name,
                        overallProgress = overall.coerceIn(0f, 1f)
                    )
                }
            }
        }

        scanJob = viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    ScannerModule.scanDevice(appContext, listener)
                }
                withContext(Dispatchers.IO) {
                    ScannerModule.recordHistory(appContext, report)
                }
                _uiState.update { current ->
                    current.copy(
                        phase = ScanPhase.COMPLETE,
                        completedReport = report,
                        overallProgress = 1f,
                        stageLabel = "Scan complete"
                    )
                }
            } catch (e: CancellationException) {
                // User cancelled — do NOT record a score or verdict.
                _uiState.update { current ->
                    current.copy(
                        phase = ScanPhase.CANCELLED,
                        completedReport = null,
                        stageLabel = "Scan cancelled",
                        overallProgress = 0f
                    )
                }
                // Re-throw so the coroutine is properly cancelled.
                throw e
            } catch (e: Throwable) {
                _uiState.update { current ->
                    current.copy(
                        phase = ScanPhase.ERROR,
                        errorMessage = "Scan couldn't finish: ${e.message ?: "unknown error"}. Try again.",
                        stageLabel = ""
                    )
                }
            }
        }
    }

    /**
     * Cancels the active scan.
     *
     * Sets [ScanPhase.CANCELLED]. No score or verdict is displayed; the prior
     * completed scan from history may still be shown.
     */
    fun cancelScan() {
        scanJob?.cancel()
        // State is updated inside the CancellationException handler above.
        // If the job was already done, force state just in case.
        if (_uiState.value.phase == ScanPhase.SCANNING) {
            _uiState.update { current ->
                current.copy(
                    phase = ScanPhase.CANCELLED,
                    completedReport = null,
                    stageLabel = "Scan cancelled",
                    overallProgress = 0f
                )
            }
        }
    }

    fun setRefreshState(isRefreshing: Boolean, message: String?) {
        _uiState.update { it.copy(isRefreshing = isRefreshing, refreshMessage = message) }
    }

    fun setShowUpsell(show: Boolean) {
        _uiState.update { it.copy(showUpsell = show) }
    }

    fun dismissError() {
        _uiState.update { it.copy(phase = ScanPhase.IDLE, errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }

    // -----------------------------------------------------------------------
    // Factory (manual DI — replace with @HiltViewModel in Phase 2+)
    // -----------------------------------------------------------------------

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == ScannerViewModel::class.java) {
                "Factory only creates ScannerViewModel"
            }
            return ScannerViewModel(context.applicationContext) as T
        }
    }
}
