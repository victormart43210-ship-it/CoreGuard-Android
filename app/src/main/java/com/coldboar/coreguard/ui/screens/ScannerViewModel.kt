package com.coldboar.coreguard.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coldboar.coreguard.mvt.ScanCancellation
import com.coldboar.coreguard.mvt.ScanProgressListener
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanSessionSaveRequest
import com.coldboar.coreguard.mvt.ScanSessionRepository
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
    private val sessionRepository: ScanSessionRepository,
    private val scanDevice: suspend (
        context: Context,
        listener: ScanProgressListener,
        cancellation: ScanCancellation,
        deepFileInspectionEnabled: Boolean,
        quillaCorrelationEnabled: Boolean
    ) -> ScanReport = { context, listener, cancellation, deepInspection, quillaEnabled ->
        withContext(Dispatchers.IO) {
            ScannerModule.scanDevice(
                context = context,
                listener = listener,
                cancellation = cancellation,
                deepFileInspectionEnabled = deepInspection,
                quillaCorrelationEnabled = quillaEnabled
            )
        }
    },
    private val recordHistory: suspend (Context, ScanReport) -> Unit = { context, report ->
        withContext(Dispatchers.IO) { ScannerModule.recordHistory(context, report) }
    },
    private val latestReportProvider: () -> ScanReport? = { ScannerModule.latestReport() },
    private val currentTimeMs: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Empty)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private val cancelRequested = AtomicBoolean(false)
    private val scanTokenCounter = AtomicLong(0L)
    @Volatile private var activeScanToken: Long = 0L
    @Volatile private var activeCancelRequest: AtomicBoolean? = null

    init {
        sessionRepository.ensureLegacyImport()
        latestReportProvider()?.let {
            _uiState.value = ScannerUiState.Complete(it, sessionId = "in-memory", stageEvents = emptyList())
        }
    }

    fun startScan() {
        activeCancelRequest?.set(true)
        scanJob?.cancel()

        val scanToken = scanTokenCounter.incrementAndGet()
        activeScanToken = scanToken
        val localCancelRequested = AtomicBoolean(false)
        activeCancelRequest = localCancelRequested
        cancelRequested.set(false)
        val previousCompleted = (_uiState.value as? ScannerUiState.Complete)?.report
        val stageEvents = mutableListOf<ScanStageEvent>()
        _uiState.value = ScannerUiState.Scanning()

        scanJob = viewModelScope.launch {
            val startedAt = currentTimeMs()
            var deepInspection: Boolean? = null
            var quillaEnabled: Boolean? = null
            var cancellationPersisted = false
            var cancellationSessionId: String? = null
            val listener = object : ScanProgressListener {
                override fun onStage(event: ScanStageEvent) {
                    if (!isCurrentScan(scanToken)) return
                    stageEvents += event
                    _uiState.value = ScannerUiState.Scanning(
                        currentStage = event,
                        allStages = stageEvents.toList()
                    )
                }
            }
            try {
                deepInspection = settingsRepository.deepFileInspectionEnabled.first()
                quillaEnabled = settingsRepository.quillaCorrelationEnabled.first()
                val deepInspectionEnabled = deepInspection
                val quillaCorrelationEnabled = quillaEnabled
                val cancellation = ScanCancellation {
                    cancelRequested.get() || localCancelRequested.get() || !isCurrentScan(scanToken)
                }
                val report = scanDevice(
                    appContext,
                    listener,
                    cancellation,
                    deepInspectionEnabled ?: false,
                    quillaCorrelationEnabled ?: false
                )
                if (!isCurrentScan(scanToken)) return@launch
                recordHistory(appContext, report)
                val normalizedFindings = report.detections
                    .map { it.toFinding(report.finishedAtMillis) }
                    .let { correlateFindingsDeterministic(it) }
                val sessionId = withContext(Dispatchers.IO) {
                    sessionRepository.saveSession(
                        ScanSessionSaveRequest(
                            status = ScanStageId.COMPLETED,
                            startedAtMs = startedAt,
                            endedAtMs = currentTimeMs(),
                            scannerEngineVersion = ScannerModule.scannerEngineVersion(),
                            schemaVersion = ScannerModule.scanSchemaVersion(),
                            deepInspectionEnabled = deepInspectionEnabled ?: false,
                            feedSource = FEED_SOURCE,
                            feedVersion = null,
                            feedAuthenticity = FEED_AUTHENTICITY,
                            feedLoadedAtMs = ScannerModule.iocLoadedAtMs(),
                            findings = normalizedFindings.map { it.finding },
                            stageEvents = stageEvents.toList()
                        )
                    )
                }
                if (!isCurrentScan(scanToken)) return@launch
                _uiState.value = ScannerUiState.Complete(
                    report = report,
                    sessionId = sessionId,
                    stageEvents = stageEvents.toList()
                )
            } catch (ce: CancellationException) {
                val deepInspectionEnabled = deepInspection ?: withContext(NonCancellable) {
                    settingsRepository.deepFileInspectionEnabled.first()
                }
                val sessionId = withContext(NonCancellable + Dispatchers.IO) {
                    sessionRepository.saveSession(
                        ScanSessionSaveRequest(
                            status = ScanStageId.CANCELLED,
                            startedAtMs = startedAt,
                            endedAtMs = currentTimeMs(),
                            failureReason = "Cancelled by user",
                            scannerEngineVersion = ScannerModule.scannerEngineVersion(),
                            schemaVersion = ScannerModule.scanSchemaVersion(),
                            deepInspectionEnabled = deepInspectionEnabled,
                            feedSource = FEED_SOURCE,
                            feedVersion = null,
                            feedAuthenticity = FEED_AUTHENTICITY,
                            feedLoadedAtMs = ScannerModule.iocLoadedAtMs(),
                            findings = emptyList(),
                            stageEvents = stageEvents.toList()
                        )
                    )
                }
                cancellationPersisted = true
                cancellationSessionId = sessionId
                if (isCurrentScan(scanToken)) {
                    _uiState.value = ScannerUiState.Cancelled(
                        sessionId = sessionId,
                        stageEvents = stageEvents.toList(),
                        lastCompletedReport = previousCompleted
                    )
                }
            } catch (t: Throwable) {
                val sessionId = withContext(NonCancellable + Dispatchers.IO) {
                    sessionRepository.saveSession(
                        ScanSessionSaveRequest(
                            status = ScanStageId.FAILED,
                            startedAtMs = startedAt,
                            endedAtMs = currentTimeMs(),
                            failureReason = t.message ?: "Unknown error",
                            scannerEngineVersion = ScannerModule.scannerEngineVersion(),
                            schemaVersion = ScannerModule.scanSchemaVersion(),
                            deepInspectionEnabled = deepInspection ?: settingsRepository.deepFileInspectionEnabled.first(),
                            feedSource = FEED_SOURCE,
                            feedVersion = null,
                            feedAuthenticity = FEED_AUTHENTICITY,
                            feedLoadedAtMs = ScannerModule.iocLoadedAtMs(),
                            findings = emptyList(),
                            stageEvents = stageEvents.toList()
                        )
                    )
                }
                if (isCurrentScan(scanToken)) {
                    _uiState.value = ScannerUiState.Error(
                        message = "Scan failed: ${t.message ?: "unknown error"}",
                        sessionId = sessionId,
                        stageEvents = stageEvents.toList(),
                        lastCompletedReport = previousCompleted
                    )
                }
            } finally {
                if (isCurrentScan(scanToken)) {
                    if (_uiState.value is ScannerUiState.Scanning && localCancelRequested.get()) {
                        if (!cancellationPersisted) {
                            val deepInspectionEnabled = deepInspection ?: withContext(NonCancellable) {
                                settingsRepository.deepFileInspectionEnabled.first()
                            }
                            cancellationSessionId = withContext(NonCancellable + Dispatchers.IO) {
                                sessionRepository.saveSession(
                                    ScanSessionSaveRequest(
                                        status = ScanStageId.CANCELLED,
                                        startedAtMs = startedAt,
                                        endedAtMs = currentTimeMs(),
                                        failureReason = "Cancelled by user",
                                        scannerEngineVersion = ScannerModule.scannerEngineVersion(),
                                        schemaVersion = ScannerModule.scanSchemaVersion(),
                                        deepInspectionEnabled = deepInspectionEnabled,
                                        feedSource = FEED_SOURCE,
                                        feedVersion = null,
                                        feedAuthenticity = FEED_AUTHENTICITY,
                                        feedLoadedAtMs = ScannerModule.iocLoadedAtMs(),
                                        findings = emptyList(),
                                        stageEvents = stageEvents.toList()
                                    )
                                )
                            }
                            cancellationPersisted = true
                        }
                        _uiState.value = ScannerUiState.Cancelled(
                            sessionId = cancellationSessionId,
                            stageEvents = stageEvents.toList(),
                            lastCompletedReport = previousCompleted
                        )
                    }
                    scanJob = null
                    activeCancelRequest = null
                    cancelRequested.set(false)
                }
            }
        }
    }

    fun cancelScan() {
        cancelRequested.set(true)
        activeCancelRequest?.set(true)
        scanJob?.cancel()
    }

    override fun onCleared() {
        activeCancelRequest?.set(true)
        scanJob?.cancel()
        scanJob = null
    }

    fun reset() {
        _uiState.value = ScannerUiState.Empty
    }

    private fun isCurrentScan(scanToken: Long): Boolean = activeScanToken == scanToken

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

    companion object {
        /** Attribution label included in every persisted scan session. */
        const val FEED_SOURCE = "Amnesty International Security Lab / mvt-project"
        const val FEED_AUTHENTICITY = "Transport-protected but not cryptographically signed."
    }
}
