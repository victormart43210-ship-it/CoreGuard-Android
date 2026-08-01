package com.coldboar.coreguard.settings

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for user-configurable settings that control security
 * and monitoring behavior.
 *
 * All getters return [Flow] so composables and ViewModels can react to changes.
 * All setters are suspending functions.
 *
 * Truth-first rule: a setting being stored here does NOT imply the underlying
 * behavior is fully implemented. See [DataStoreUserSettingsRepository] for
 * which features are currently honored and which are marked "not yet available."
 */
interface UserSettingsRepository {

    /**
     * Enables the in-app real-time metrics refresh loop (CPU/RAM update cycle
     * and periodic Guardian Score re-computation while the app is in the foreground).
     *
     * Note: this does NOT enable a background monitoring service; that requires
     * additional permissions and is a future phase deliverable.
     */
    val realTimeMonitoringEnabled: Flow<Boolean>

    /**
     * Deep file inspection mode.
     *
     * **NOT YET AVAILABLE**: the backend engine does not yet perform deeper
     * file scanning beyond app-accessible storage. Storing the preference is
     * implemented; honoring it in the engine is a future phase deliverable.
     */
    val deepFileInspectionEnabled: Flow<Boolean>

    /**
     * Quilla cross-source correlation mode.
     *
     * **NOT YET AVAILABLE**: Quilla correlation runs unconditionally in the
     * current implementation. This toggle does not yet gate that behavior.
     */
    val quillaCorrelationEnabled: Flow<Boolean>

    /**
     * Intel sync / live IOC feed pull.
     *
     * **NOT YET AVAILABLE**: IOC refresh is currently triggered explicitly by
     * the user in the Scanner screen; this toggle does not yet auto-schedule it.
     */
    val intelSyncEnabled: Flow<Boolean>

    suspend fun setRealTimeMonitoringEnabled(enabled: Boolean)
    suspend fun setDeepFileInspectionEnabled(enabled: Boolean)
    suspend fun setQuillaCorrelationEnabled(enabled: Boolean)
    suspend fun setIntelSyncEnabled(enabled: Boolean)
}
