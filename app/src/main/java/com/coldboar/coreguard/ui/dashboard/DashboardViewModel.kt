package com.coldboar.coreguard.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coldboar.coreguard.GuardianScore
import com.coldboar.coreguard.GuardianScoreEvidence
import com.coldboar.coreguard.SecurityCheckRunner
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.elite.DynamicThreatEngine
import com.coldboar.coreguard.elite.EliteModule
import com.coldboar.coreguard.monitor.SecurityScoreCache
import com.coldboar.coreguard.mvt.ScannerModule
import com.coldboar.coreguard.mvt.ShieldState
import com.coldboar.coreguard.settings.DataStoreUserSettingsRepository
import com.coldboar.coreguard.settings.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Immutable snapshot of all data the Home/Dashboard screen needs to render. */
data class DashboardUiState(
    val score: Int? = null,
    val evidence: List<GuardianScoreEvidence> = emptyList(),
    val shieldOn: Boolean = false,
    val cpuText: String = "…",
    val ramText: String = "…",
    val lastScanLabel: String = "No scan yet",
    val hasScan: Boolean = false,
    val appsScanned: String = "–",
    val threatsLabel: String = "–",
    val eliteCounterState: EliteCounterSnapshot = EliteCounterSnapshot(),
    // Settings
    val realTimeMonitoringEnabled: Boolean = true,
    val deepFileInspectionEnabled: Boolean = true,
    val quillaCorrelationEnabled: Boolean = true,
    val intelSyncEnabled: Boolean = true
)

/** Mirror of elite counter values for the UI state. */
data class EliteCounterSnapshot(
    val dtsScore: Int = 0,
    val dtsBand: DynamicThreatEngine.Band = DynamicThreatEngine.Band.NOMINAL,
    val swarmAlerts: Int = 0,
    val lastScamHost: String? = null,
    val lastScamScore: Int = 0
)

/** Status constants for timeline/hypothesis entries (mirrors legacy strings). */
object DashboardStatus {
    const val ACTIVE = "ACTIVE"
    const val DISMISSED = "DISMISSED"
    const val RESOLVED = "RESOLVED"
}

/**
 * ViewModel for the Home / Elite Dashboard screen.
 *
 * Uses manual constructor injection (no Hilt). A [ViewModelProvider.Factory]
 * is provided so [viewModel()] can instantiate it from within Compose.
 *
 * TODO (Phase 2+): Replace factory with Hilt @HiltViewModel once Hilt is
 * added across the project.
 */
class DashboardViewModel(
    private val appContext: Context,
    private val settings: UserSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var liveRefreshJob: Job? = null

    init {
        // Collect settings changes into the UI state immediately.
        viewModelScope.launch {
            combine(
                settings.realTimeMonitoringEnabled,
                settings.deepFileInspectionEnabled,
                settings.quillaCorrelationEnabled,
                settings.intelSyncEnabled
            ) { rt, deep, quilla, intel ->
                _uiState.update { current ->
                    current.copy(
                        realTimeMonitoringEnabled = rt,
                        deepFileInspectionEnabled = deep,
                        quillaCorrelationEnabled = quilla,
                        intelSyncEnabled = intel
                    )
                }
            }.collect {}
        }

        // Kick off an initial load from cache + first score computation.
        viewModelScope.launch {
            loadInitialData()
            startLiveRefresh()
        }
    }

    // -----------------------------------------------------------------------
    // Public actions
    // -----------------------------------------------------------------------

    fun toggleRealTimeMonitoring(enabled: Boolean) {
        viewModelScope.launch { settings.setRealTimeMonitoring(enabled) }
        if (enabled) startLiveRefresh() else liveRefreshJob?.cancel()
    }

    fun toggleDeepFileInspection(enabled: Boolean) {
        viewModelScope.launch { settings.setDeepFileInspection(enabled) }
    }

    fun toggleQuillaCorrelation(enabled: Boolean) {
        viewModelScope.launch { settings.setQuillaCorrelation(enabled) }
    }

    fun toggleIntelSync(enabled: Boolean) {
        viewModelScope.launch { settings.setIntelSync(enabled) }
    }

    /** Forces an immediate Guardian Score refresh. */
    fun refreshScore() {
        viewModelScope.launch { doScoreRefresh() }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private suspend fun loadInitialData() {
        // Fast first paint: cached score.
        SecurityScoreCache.read(appContext)?.let { cached ->
            _uiState.update { it.copy(score = cached.score) }
        }

        // Scan snapshot.
        val report = ScannerModule.latestReport()
        if (report != null) {
            _uiState.update { current ->
                current.copy(
                    hasScan = true,
                    lastScanLabel = "Last scan: ${report.verdict.name}",
                    appsScanned = report.scannedPackages.toString(),
                    threatsLabel = report.detections.size.toString()
                )
            }
        } else {
            val history = withContext(Dispatchers.IO) {
                ScannerModule.loadHistory(appContext).firstOrNull()
            }
            if (history != null) {
                _uiState.update { current ->
                    current.copy(
                        hasScan = false,
                        lastScanLabel = "Last check (history): ${history.verdict.name}",
                        appsScanned = history.scannedArtifacts.toString(),
                        threatsLabel = history.detectionCount.toString()
                    )
                }
            }
        }

        _uiState.update { it.copy(shieldOn = ShieldState.isActive) }
        doScoreRefresh()
    }

    private suspend fun doScoreRefresh() {
        val results = withContext(Dispatchers.IO) { SecurityCheckRunner.runConcurrent(appContext) }
        val score = GuardianScore.compute(results)
        val evidence = GuardianScore.explain(results)
        SecurityScoreCache.write(
            appContext,
            score,
            GuardianScore.rankFor(score).userLabel
        )
        _uiState.update { it.copy(score = score, evidence = evidence) }
    }

    private fun startLiveRefresh() {
        liveRefreshJob?.cancel()
        liveRefreshJob = viewModelScope.launch {
            var ticks = 0
            while (true) {
                _uiState.update { current ->
                    current.copy(shieldOn = ShieldState.isActive)
                }
                ticks++
                if (ticks % 6 == 0) {
                    doScoreRefresh()
                }
                if (ticks % 15 == 0) {
                    withContext(Dispatchers.IO) { EliteModule.evaluateThreatScore(appContext) }
                }
                delay(2_000L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveRefreshJob?.cancel()
    }

    // -----------------------------------------------------------------------
    // Factory (manual DI — replace with @HiltViewModel in Phase 2+)
    // -----------------------------------------------------------------------

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == DashboardViewModel::class.java) {
                "Factory only creates DashboardViewModel"
            }
            val repo = DataStoreUserSettingsRepository(context.applicationContext)
            return DashboardViewModel(context.applicationContext, repo) as T
        }
    }
}
