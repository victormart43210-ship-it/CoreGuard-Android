package com.coldboar.coreguard.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coldboar.coreguard.mvt.ScanCancellation
import com.coldboar.coreguard.mvt.ScanProgressListener
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanSessionRepository
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

/**
 * Runs a device scan. Production wires [ScannerModule]; tests inject fakes so
 * cancellation lifecycle can be proven on the JVM without Android I/O.
 */
fun interface DeviceScanRunner {
    fun scan(
        listener: ScanProgressListener?,
        cancellation: ScanCancellation,
        deepFileInspectionEnabled: Boolean,
        quillaCorrelationEnabled: Boolean
    ): ScanReport
}

class ScannerViewModel(
    private val settingsRepository: UserSettingsRepository,
    private val sessionRepository: ScanSessionRepository,
    private val scanRunner: DeviceScanRunner,
    private val recordHistory: (ScanReport) -> Unit,
    private val engineVersion: () -> String,
    private val schemaVersion: () -> Int,
    private val iocLoadedAtMs: () -> Long,
    private val latestReport: () -> ScanReport?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val externalScope: CoroutineScope? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Empty)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private val cancelRequested = AtomicBoolean(false)

    /** Monotonic scan identity; only the current generation may publish terminal UI. */
    private val scanGeneration = AtomicLong(0L)

    private val scope: CoroutineScope
        get() = externalScope ?: viewModelScope

    init {
        sessionRepository.ensureLegacyImport()
        latestReport()?.let {
            _uiState.value = ScannerUiState.Complete(it, sessionId = "in-memory", stageEvents = emptyList())
        }
    }

    fun startScan() {
        scanJob?.cancel()
        scanJob = null
        cancelRequested.set(false)
        val generation = scanGeneration.incrementAndGet()
        val previousCompleted = (_uiState.value as? ScannerUiState.Complete)?.report
        val stageEvents = mutableListOf<ScanStageEvent>()
        _uiState.value = ScannerUiState.Scanning()

        scanJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            val listener = object : ScanProgressListener {
                override fun onStage(event: ScanStageEvent) {
                    if (!isCurrentGeneration(generation)) return
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
                val cancellation = ScanCancellation {
                    cancelRequested.get() || !isActiveForGeneration(generation)
                }
                val report = withContext(ioDispatcher) {
                    scanRunner.scan(
                        listener = listener,
                        cancellation = cancellation,
                        deepFileInspectionEnabled = deepInspection,
                        quillaCorrelationEnabled = quillaEnabled
                    )
                }
                if (!isCurrentGeneration(generation)) return@launch
                withContext(ioDispatcher) {
                    recordHistory(report)
                }
                if (!isCurrentGeneration(generation)) return@launch
                val normalizedFindings = report.detections
                    .map { it.toFinding(report.finishedAtMillis) }
                    .let { correlateFindingsDeterministic(it) }
                val sessionId = withContext(ioDispatcher) {
                    sessionRepository.saveSession(
                        ScanSessionSaveRequest(
                            status = ScanStageId.COMPLETED,
                            startedAtMs = startedAt,
                            endedAtMs = System.currentTimeMillis(),
                            scannerEngineVersion = engineVersion(),
                            schemaVersion = schemaVersion(),
                            deepInspectionEnabled = deepInspection,
                            feedSource = FEED_SOURCE,
                            feedVersion = null,
                            feedAuthenticity = FEED_AUTHENTICITY,
                            feedLoadedAtMs = iocLoadedAtMs(),
                            findings = normalizedFindings.map { it.finding },
                            stageEvents = stageEvents.toList()
                        )
                    )
                }
                if (!isCurrentGeneration(generation)) return@launch
                _uiState.value = ScannerUiState.Complete(
                    report = report,
                    sessionId = sessionId,
                    stageEvents = stageEvents.toList()
                )
            } catch (ce: CancellationException) {
                if (!isCurrentGeneration(generation)) return@launch
                val sessionId = withContext(ioDispatcher) {
                    sessionRepository.saveSession(
                        ScanSessionSaveRequest(
                            status = ScanStageId.CANCELLED,
                            startedAtMs = startedAt,
                            endedAtMs = System.currentTimeMillis(),
                            failureReason = "Cancelled by user",
                            scannerEngineVersion = engineVersion(),
                            schemaVersion = schemaVersion(),
                            deepInspectionEnabled = settingsRepository.deepFileInspectionEnabled.first(),
                            feedSource = FEED_SOURCE,
                            feedVersion = null,
                            feedAuthenticity = FEED_AUTHENTICITY,
                            feedLoadedAtMs = iocLoadedAtMs(),
                            findings = emptyList(),
                            stageEvents = stageEvents.toList()
                        )
                    )
                }
                if (!isCurrentGeneration(generation)) return@launch
                _uiState.value = ScannerUiState.Cancelled(
                    sessionId = sessionId,
                    stageEvents = stageEvents.toList(),
                    lastCompletedReport = previousCompleted
                )
            } catch (t: Throwable) {
                if (!isCurrentGeneration(generation)) return@launch
                val sessionId = withContext(ioDispatcher) {
                    sessionRepository.saveSession(
                        ScanSessionSaveRequest(
                            status = ScanStageId.FAILED,
                            startedAtMs = startedAt,
                            endedAtMs = System.currentTimeMillis(),
                            failureReason = t.message ?: "Unknown error",
                            scannerEngineVersion = engineVersion(),
                            schemaVersion = schemaVersion(),
                            deepInspectionEnabled = settingsRepository.deepFileInspectionEnabled.first(),
                            feedSource = FEED_SOURCE,
                            feedVersion = null,
                            feedAuthenticity = FEED_AUTHENTICITY,
                            feedLoadedAtMs = iocLoadedAtMs(),
                            findings = emptyList(),
                            stageEvents = stageEvents.toList()
                        )
                    )
                }
                if (!isCurrentGeneration(generation)) return@launch
                _uiState.value = ScannerUiState.Error(
                    message = "Scan failed: ${t.message ?: "unknown error"}",
                    sessionId = sessionId,
                    stageEvents = stageEvents.toList(),
                    lastCompletedReport = previousCompleted
                )
            }
        }
    }

    /**
     * Requests a cooperative stop. [DeviceScanRunner] observes [cancelRequested]
     * between scan stages and raises [CancellationException] while this coroutine
     * is still active, allowing the terminal cancellation session and UI state to
     * be persisted reliably for the current generation.
     */
    fun cancelScan() {
        cancelRequested.set(true)
    }

    /** True when a scan coroutine for the current generation is still active. */
    fun hasActiveScanJob(): Boolean = scanJob?.isActive == true

    override fun onCleared() {
        scanJob?.cancel()
    }

    fun reset() {
        _uiState.value = ScannerUiState.Empty
    }

    private fun isCurrentGeneration(generation: Long): Boolean =
        scanGeneration.get() == generation

    private fun isActiveForGeneration(generation: Long): Boolean =
        isCurrentGeneration(generation) && (scanJob?.isActive == true)

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            val context = appContext.applicationContext
            return ScannerViewModel(
                settingsRepository = DataStoreUserSettingsRepository(context),
                sessionRepository = RoomScanSessionRepository(context),
                scanRunner = DeviceScanRunner { listener, cancellation, deep, quilla ->
                    ScannerModule.scanDevice(
                        context = context,
                        listener = listener,
                        cancellation = cancellation,
                        deepFileInspectionEnabled = deep,
                        quillaCorrelationEnabled = quilla
                    )
                },
                recordHistory = { ScannerModule.recordHistory(context, it) },
                engineVersion = { ScannerModule.scannerEngineVersion() },
                schemaVersion = { ScannerModule.scanSchemaVersion() },
                iocLoadedAtMs = { ScannerModule.iocLoadedAtMs() },
                latestReport = { ScannerModule.latestReport() }
            ) as T
        }
    }

    companion object {
        /** Attribution label included in every persisted scan session. */
        const val FEED_SOURCE = "Amnesty International Security Lab / mvt-project"
        const val FEED_AUTHENTICITY = "Transport-protected but not cryptographically signed."
    }
}
