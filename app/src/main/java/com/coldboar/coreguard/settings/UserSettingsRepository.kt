package com.coldboar.coreguard.settings

import kotlinx.coroutines.flow.Flow

/**
 * Persistent user settings repository.
 *
 * All toggle preferences are Flow-backed so callers can observe changes.
 * Writes are suspend functions; readers are Flows.
 *
 * Phase 1 note: backend behaviour for these toggles is NOT yet fully wired to
 * corresponding engine operations. Any switch whose backend effect is not yet
 * honoured is labelled "Not yet available" in the UI. Zero decorative toggles remain.
 */
interface UserSettingsRepository {

    /**
     * When enabled, the Guardian Score / device-metrics ticker refreshes
     * periodically while the Home screen is open.
     * Backend: SecurityCheckRunner live refresh loop (honoured in Phase 1).
     */
    val realTimeMonitoringEnabled: Flow<Boolean>

    /**
     * When enabled, the Nemesis scanner will include app-accessible file
     * paths in the IOC match pass.
     * Backend: DeviceScanner file walk (honoured in Phase 1 via ViewModel).
     */
    val deepFileInspectionEnabled: Flow<Boolean>

    /**
     * When enabled, scan detections are correlated against the Quilla
     * hypothesis store.
     * Backend: QuillaIocBridge.correlateScanArtifacts (NOT YET AVAILABLE — Phase 2+).
     */
    val quillaCorrelationEnabled: Flow<Boolean>

    /**
     * When enabled, the IOC feed refresh button is shown and the in-app
     * signature refresh is allowed.
     * Backend: IocFeedFetcher (NOT YET AVAILABLE for automatic sync — Phase 2+).
     */
    val intelSyncEnabled: Flow<Boolean>

    suspend fun setRealTimeMonitoring(enabled: Boolean)
    suspend fun setDeepFileInspection(enabled: Boolean)
    suspend fun setQuillaCorrelation(enabled: Boolean)
    suspend fun setIntelSync(enabled: Boolean)
}
