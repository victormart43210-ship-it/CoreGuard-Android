package com.coldboar.coreguard.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coldboar.coreguard.mvt.ScanCancellation
import com.coldboar.coreguard.mvt.ScanProgressListener
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanSessionSaveRequest
import com.coldboar.coreguard.mvt.ScanStageEvent
import com.coldboar.coreguard.mvt.ScanStageId
import com.coldboar.coreguard.mvt.ScannerModule
import com.coldboar.coreguard.mvt.RoomScanSessionRepository
import com.coldboar.coreguard.mvt.correlateFindingsDeterministic
import com.coldboar.coreguard.settings.DataStoreUserSettingsRepository
import com.coldboar.coreguard.settings.UserSettingsRepository
import com.coldboar.coreguard.truth.toFinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ScannerUiState {
    object Empty : ScannerUiState()
    data class Scanning(
        val currentStage: ScanStageEvent? = null,
        val allStages: List<ScanStageEvent> = emptyList()
    ) : ScannerUiState()
    data class Complete(
        val report: ScanReport,
        val sessionId: String,
        val stageEvents: List<ScanStageEvent>
    ) : ScannerUiState()
    data class Cancelled(
        val sessionId: String?,
        val stageEvents: List<ScanStageEvent>,
        val lastCompletedReport: ScanReport? = null
    ) : ScannerUiState()
    data class Error(
        val message: String,
        val sessionId: String?,
        val stageEvents: List<ScanStageEvent>,
        val lastCompletedReport: ScanReport? = null
    ) : ScannerUiState()
}

class ScannerViewModel(
    private val appContext: Context,
    private val settingsRepository: UserSettingsRepository,
    private val sessionRepository: RoomScanSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Empty)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var cancelRequested = false

    init {
        sessionRepository.ensureLegacyImport()
        ScannerModule.latestReport()?.let {
            _uiState.value = ScannerUiState.Complete(it, sessionId = "in-memory", stageEvents = emptyList())
        }
    }

    fun startScan() {
        scanJob?.cancel()
        scanJob = null
        cancelRequested = false
        val previousCompleted = (_uiState.value as? ScannerUiState.Complete)?.report
        val stageEvents = mutableListOf<ScanStageEvent>()
        _uiState.value = ScannerUiState.Scanning()

        scanJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val listener = object : ScanProgressListener {
                override fun onStage(event: ScanStageEvent) {
                    stageEvents += event
                    _uiState.value = ScannerUiState.Scanning(
                        currentStage = event,
                        allStages = stageEvents.toList()
                    )
                }
            }
            try {
                val deepInspection = settingsRepository.deepFileInspectionEnabled.first()
                val quillaEnabled = settingsRepository.quillaCorrelationEnabled.first()
                val cancellation = ScanCancellation { cancelRequested || !isActive() }
                val report = withContext(Dispatchers.IO) {
                    ScannerModule.scanDevice(
                        context = appContext,
                        listener = listener,
                        cancellation = cancellation,
                        deepFileInspectionEnabled = deepInspection,
                        quillaCorrelationEnabled = quillaEnabled
                    )
                }
                withContext(Dispatchers.IO) {
                    ScannerModule.recordHistory(appContext, report)
                }
                val normalizedFindings = report.detections
                    .map { it.toFinding(report.finishedAtMillis) }
                    .let { correlateFindingsDeterministic(it) }
                val sessionId = withContext(Dispatchers.IO) {
                    sessionRepository.saveSession(
                        ScanSessionSaveRequest(
                            status = ScanStageId.COMPLETED,
                            startedAtMs = startedAt,
                            endedAtMs = System.currentTimeMillis(),
                            scannerEngineVersion = ScannerModule.scannerEngineVersion(),
                            schemaVersion = ScannerModule.scanSchemaVersion(),
                            deepInspectionEnabled = deepInspection,
                            feedSource = "Amnesty International Security Lab / mvt-project",
                            feedVersion = null,
                            feedAuthenticity = "Transport-protected but not cryptographically signed.",
                            feedLoadedAtMs = ScannerModule.iocLoadedAtMs(),
                            findings = normalizedFindings.map { it.finding },
                            stageEvents = stageEvents.toList()
                        )
                    )
                }
                _uiState.value = ScannerUiState.Complete(
                    report = report,
                    sessionId = sessionId,
                    stageEvents = stageEvents.toList()
                )
            } catch (ce: CancellationException) {
                val sessionId = withContext(Dispatchers.IO) {
                    sessionRepository.saveSession(
                        ScanSessionSaveRequest(
                            status = ScanStageId.CANCELLED,
                            startedAtMs = startedAt,
                            endedAtMs = System.currentTimeMillis(),
                            failureReason = "Cancelled by user",
                            scannerEngineVersion = ScannerModule.scannerEngineVersion(),
                            schemaVersion = ScannerModule.scanSchemaVersion(),
                            deepInspectionEnabled = settingsRepository.deepFileInspectionEnabled.first(),
                            feedSource = "Amnesty International Security Lab / mvt-project",
                            feedVersion = null,
                            feedAuthenticity = "Transport-protected but not cryptographically signed.",
                            feedLoadedAtMs = ScannerModule.iocLoadedAtMs(),
                            findings = emptyList(),
                            stageEvents = stageEvents.toList()
                        )
                    )
                }
                _uiState.value = ScannerUiState.Cancelled(
                    sessionId = sessionId,
                    stageEvents = stageEvents.toList(),
                    lastCompletedReport = previousCompleted
                )
            } catch (t: Throwable) {
                val sessionId = withContext(Dispatchers.IO) {
                    sessionRepository.saveSession(
                        ScanSessionSaveRequest(
                            status = ScanStageId.FAILED,
                            startedAtMs = startedAt,
                            endedAtMs = System.currentTimeMillis(),
                            failureReason = t.message ?: "Unknown error",
                            scannerEngineVersion = ScannerModule.scannerEngineVersion(),
                            schemaVersion = ScannerModule.scanSchemaVersion(),
                            deepInspectionEnabled = settingsRepository.deepFileInspectionEnabled.first(),
                            feedSource = "Amnesty International Security Lab / mvt-project",
                            feedVersion = null,
                            feedAuthenticity = "Transport-protected but not cryptographically signed.",
                            feedLoadedAtMs = ScannerModule.iocLoadedAtMs(),
                            findings = emptyList(),
                            stageEvents = stageEvents.toList()
                        )
                    )
                }
                _uiState.value = ScannerUiState.Error(
                    message = "Scan failed: ${t.message ?: "unknown error"}",
                    sessionId = sessionId,
                    stageEvents = stageEvents.toList(),
                    lastCompletedReport = previousCompleted
                )
            }
        }
    }

    fun cancelScan() {
        cancelRequested = true
        scanJob?.cancel()
        scanJob = null
    }

    override fun onCleared() {
        scanJob?.cancel()
    }

    fun reset() {
        _uiState.value = ScannerUiState.Empty
    }

    private fun isActive(): Boolean = scanJob?.isActive == true

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            val context = appContext.applicationContext
            return ScannerViewModel(
                appContext = context,
                settingsRepository = DataStoreUserSettingsRepository(context),
                sessionRepository = RoomScanSessionRepository(context)
            ) as T
        }
    }
}
