package com.coldboar.coreguard.ui.dashboard

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coldboar.coreguard.CpuUsageCalculator
import com.coldboar.coreguard.GuardianScore
import com.coldboar.coreguard.GuardianScoreEvidence
import com.coldboar.coreguard.MemoryUsageCalculator
import com.coldboar.coreguard.SecurityCheckRunner
import com.coldboar.coreguard.elite.EliteModule
import com.coldboar.coreguard.monitor.SecurityScoreCache
import com.coldboar.coreguard.mvt.ScannerModule
import com.coldboar.coreguard.mvt.ShieldState
import com.coldboar.coreguard.settings.DataStoreUserSettingsRepository
import com.coldboar.coreguard.settings.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Immutable snapshot of the Elite Dashboard UI state.
 *
 * All fields must represent observed or honestly-labeled values.
 * Never default score/status to a "safe" / "protected" value if it has not
 * been computed.
 */
data class DashboardUiState(
    /** Guardian Score 0–100, null until the first computation completes. */
    val score: Int? = null,
    /** Per-check evidence rows backing the score, empty until computed. */
    val evidence: List<GuardianScoreEvidence> = emptyList(),
    /** True only if the VPN-based Privacy Shield is confirmed active. */
    val shieldOn: Boolean = false,
    /** True only if a real (non-cancelled, non-error) scan has been recorded. */
    val hasScan: Boolean = false,
    val lastScanLabel: String = "No scan yet",
    val appsScanned: String = "–",
    val threatsLabel: String = "–",
    val cpuText: String = "…",
    val ramText: String = "…",
    /** Whether the in-app real-time metrics loop is running. Persisted via DataStore. */
    val realTimeMonitoringEnabled: Boolean = true,
    /**
     * NOT YET AVAILABLE: deep file inspection mode is persisted but not yet
     * honored by the scan engine. UI must show "Not yet available" on this toggle.
     */
    val deepFileInspectionEnabled: Boolean = false,
    /**
     * NOT YET AVAILABLE: Quilla correlation toggle is persisted but does not
     * yet gate Quilla behavior. UI must show "Not yet available" on this toggle.
     */
    val quillaCorrelationEnabled: Boolean = false,
    /**
     * NOT YET AVAILABLE: Intel sync toggle is persisted but does not yet
     * auto-schedule IOC refresh. UI must show "Not yet available" on this toggle.
     */
    val intelSyncEnabled: Boolean = false,
    /** True while a Guardian Score refresh is in progress. */
    val isRefreshingScore: Boolean = false
)

/**
 * ViewModel for the Elite Home/Dashboard screen.
 *
 * Owns:
 * - Settings persistence via [UserSettingsRepository] / DataStore.
 * - Guardian Score loading and periodic refresh.
 * - Shield, scan snapshot, and device metrics state.
 *
 * Note: Manual constructor injection is used (no Hilt) to keep the Phase 1
 * change scope bounded. Hilt wiring is documented as a Phase 2 follow-up.
 * TODO(phase2): convert to @HiltViewModel + @Inject constructor.
 */
class DashboardViewModel(
    application: Application,
    private val settingsRepository: UserSettingsRepository
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val realTimeMonitoringEnabled: StateFlow<Boolean> =
        settingsRepository.realTimeMonitoringEnabled
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        // Immediately apply any cached score so the screen isn't blank on first paint.
        viewModelScope.launch {
            SecurityScoreCache.read(context)?.let { cached ->
                _uiState.value = _uiState.value.copy(score = cached.score)
            }
        }

        // Observe all settings in a single coroutine to reduce overhead.
        viewModelScope.launch {
            combine(
                settingsRepository.realTimeMonitoringEnabled,
                settingsRepository.deepFileInspectionEnabled,
                settingsRepository.quillaCorrelationEnabled,
                settingsRepository.intelSyncEnabled
            ) { rtm, dfi, qc, intel ->
                _uiState.value = _uiState.value.copy(
                    realTimeMonitoringEnabled = rtm,
                    deepFileInspectionEnabled = dfi,
                    quillaCorrelationEnabled = qc,
                    intelSyncEnabled = intel
                )
            }.collect {}
        }

        // Initial data load.
        refresh()
    }

    // -------------------------------------------------------------------------
    // Action functions
    // -------------------------------------------------------------------------

    /** Triggers a full Guardian Score recomputation. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshingScore = true)
            refreshGuardianScore()
            refreshScanSnapshot()
            _uiState.value = _uiState.value.copy(
                shieldOn = ShieldState.isActive,
                isRefreshingScore = false
            )
            // Kick off DTS evaluation asynchronously.
            launch(Dispatchers.IO) { EliteModule.evaluateThreatScore(context) }
        }
    }

    /** Toggles real-time monitoring and persists the new value. */
    fun setRealTimeMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRealTimeMonitoringEnabled(enabled)
        }
    }

    /**
     * Toggle for deep file inspection — persisted but not yet honored by the engine.
     * UI should show "Not yet available" alongside this control.
     */
    fun setDeepFileInspectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDeepFileInspectionEnabled(enabled)
        }
    }

    /**
     * Toggle for Quilla correlation — persisted but Quilla currently runs
     * unconditionally. UI should show "Not yet available".
     */
    fun setQuillaCorrelationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setQuillaCorrelationEnabled(enabled)
        }
    }

    /**
     * Toggle for intel sync — persisted but auto-scheduling is not yet
     * implemented. UI should show "Not yet available".
     */
    fun setIntelSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setIntelSyncEnabled(enabled)
        }
    }

    /** Updates the CPU and RAM metrics once. Called from the real-time loop in the composable. */
    fun updateDeviceMetrics() {
        val cpu = CpuUsageCalculator.getUsagePercent()?.let { "$it%" } ?: "n/a"
        val ram = MemoryUsageCalculator.formatBytes(
            MemoryUsageCalculator.getUsedRamBytes(context)
        )
        _uiState.value = _uiState.value.copy(
            cpuText = cpu,
            ramText = ram,
            shieldOn = ShieldState.isActive
        )
    }

    /** Periodic refresh for use by the composable when real-time mode is on. */
    fun startMetricsLoop() {
        viewModelScope.launch {
            var ticks = 0
            while (isActive) {
                updateDeviceMetrics()
                ticks++
                if (ticks % 6 == 0) {
                    refreshGuardianScore()
                }
                if (ticks % 15 == 0) {
                    withContext(Dispatchers.IO) { EliteModule.evaluateThreatScore(context) }
                }
                delay(2_000L)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private suspend fun refreshGuardianScore() {
        val results = withContext(Dispatchers.IO) {
            SecurityCheckRunner.runConcurrent(context)
        }
        val computed = GuardianScore.compute(results)
        val evidence = GuardianScore.explain(results)
        SecurityScoreCache.write(context, computed, GuardianScore.rankFor(computed).userLabel)
        _uiState.value = _uiState.value.copy(score = computed, evidence = evidence)
    }

    private suspend fun refreshScanSnapshot() {
        val report = ScannerModule.latestReport()
        if (report != null) {
            _uiState.value = _uiState.value.copy(
                hasScan = true,
                lastScanLabel = "Last scan: ${report.verdict.name}",
                appsScanned = report.scannedPackages.toString(),
                threatsLabel = report.detections.size.toString()
            )
        } else {
            _uiState.value = _uiState.value.copy(
                hasScan = false,
                lastScanLabel = "No privacy check yet",
                appsScanned = "0",
                threatsLabel = "0"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Factory — use until Hilt is wired in Phase 2.
    // TODO(phase2): replace with @HiltViewModel + @Inject constructor.
    // -------------------------------------------------------------------------

    class Factory(
        private val application: Application,
        private val settingsRepository: UserSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return DashboardViewModel(application, settingsRepository) as T
        }
    }

    companion object {
        /**
         * Creates the default factory for production use.
         * Constructs [DataStoreUserSettingsRepository] with the application context.
         */
        fun defaultFactory(application: Application): Factory =
            Factory(application, DataStoreUserSettingsRepository(application))
    }
}
