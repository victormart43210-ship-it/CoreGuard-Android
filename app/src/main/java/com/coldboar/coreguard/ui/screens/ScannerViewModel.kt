package com.coldboar.coreguard.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScannerModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The UI state for the scanner screen. Each state is mutually exclusive; the
 * UI should render exactly one view for the current state.
 *
 * Truth-first rules:
 * - [Cancelled] must never display a score or verdict — results are incomplete.
 * - [Complete] includes the full [ScanReport]; the UI must not summarize a
 *   cancelled scan as [Complete].
 * - [Error] must show the actual failure message, not a fake clean result.
 */
sealed class ScannerUiState {
    /** No scan has been run yet (or state has been cleared). */
    object Empty : ScannerUiState()

    /** A scan is currently in progress. [progressLabel] is shown to the user. */
    data class Scanning(
        val progressLabel: String = "Scan in progress…"
    ) : ScannerUiState()

    /** A scan completed successfully. */
    data class Complete(val report: ScanReport) : ScannerUiState()

    /**
     * The scan was cancelled by the user before it finished.
     * No score or verdict may be derived from this state.
     * [lastCompletedReport] is the most recent successfully finished scan,
     * if available — shown so the user always has access to their last real results.
     */
    data class Cancelled(
        val lastCompletedReport: ScanReport? = null
    ) : ScannerUiState()

    /** The scan failed with an error. */
    data class Error(
        val message: String,
        val lastCompletedReport: ScanReport? = null
    ) : ScannerUiState()
}

/**
 * ViewModel for the [ScannerScreen].
 *
 * Owns the scan lifecycle: starting, cancelling, and persisting results.
 * The ViewModel survives configuration changes; the scan coroutine is tied to
 * [viewModelScope] and is cancelled when the ViewModel is cleared.
 *
 * Note: Manual constructor injection is used here because Hilt was not added to
 * the project in Phase 1 to avoid a large cross-screen migration. Hilt wiring
 * is documented as a Phase 2 follow-up (see COREGUARD_BLOCKERS.md).
 */
class ScannerViewModel(
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Empty)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        // Restore the latest completed scan report so the screen is not blank
        // on first load if a scan was run in a previous session.
        val latest = ScannerModule.latestReport()
        if (latest != null) {
            _uiState.value = ScannerUiState.Complete(latest)
        } else {
            viewModelScope.launch {
                val history = withContext(Dispatchers.IO) {
                    ScannerModule.loadHistory(appContext).firstOrNull()
                }
                if (_uiState.value is ScannerUiState.Empty && history != null) {
                    // History exists but the in-memory report is gone (e.g. process restart).
                    // Keep state Empty but record that history is available.
                    // The screen will load history separately if needed.
                }
            }
        }
    }

    /**
     * Starts a new scan. If a scan is already in progress, this is a no-op.
     *
     * Progress is labeled "Estimated progress — scan in progress" because the
     * engine does not yet emit real stage checkpoints. When real checkpoint
     * callbacks are wired in a future phase, this label should be updated.
     */
    fun startScan() {
        if (scanJob?.isActive == true) return
        val previousCompleted = (_uiState.value as? ScannerUiState.Complete)?.report

        _uiState.value = ScannerUiState.Scanning(
            progressLabel = "Estimated progress — scan in progress"
        )

        scanJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ScannerModule.scanDevice(appContext)
                }
                withContext(Dispatchers.IO) {
                    ScannerModule.recordHistory(appContext, result)
                }
                _uiState.value = ScannerUiState.Complete(result)
            } catch (ce: CancellationException) {
                // Scan was cancelled — do NOT record a score or verdict.
                _uiState.value = ScannerUiState.Cancelled(lastCompletedReport = previousCompleted)
            } catch (t: Throwable) {
                _uiState.value = ScannerUiState.Error(
                    message = "Scan couldn't finish: ${t.message ?: "unknown error"}. " +
                        "Try again, or restart the app if this keeps happening.",
                    lastCompletedReport = previousCompleted
                )
            }
        }
    }

    /**
     * Cancels the in-progress scan.
     *
     * Per truth-first rules: after cancellation no score or verdict is recorded.
     * The UI must show a "Scan cancelled — results are incomplete" message and
     * must NOT display the previous or partial results as current.
     */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }

    /**
     * Clears the current state back to [ScannerUiState.Empty].
     * Useful after dismissing an error or cancelled state.
     */
    fun reset() {
        _uiState.value = ScannerUiState.Empty
    }

    // -------------------------------------------------------------------------
    // Factory — use until Hilt is wired in Phase 2.
    // TODO(phase2): replace with @HiltViewModel + @Inject constructor.
    // -------------------------------------------------------------------------

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return ScannerViewModel(appContext.applicationContext) as T
        }
    }
}
