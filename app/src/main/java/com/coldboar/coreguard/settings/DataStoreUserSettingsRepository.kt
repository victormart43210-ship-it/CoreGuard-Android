package com.coldboar.coreguard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore singleton scoped to the application context. */
private val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "coreguard_user_settings"
)

/**
 * Preferences DataStore backed implementation of [UserSettingsRepository].
 *
 * Defaults: all toggles start enabled so behaviour on first launch matches the
 * pre-Phase-1 in-memory defaults, preserving user expectations.
 */
class DataStoreUserSettingsRepository(context: Context) : UserSettingsRepository {

    private val store = context.applicationContext.userSettingsDataStore

    override val realTimeMonitoringEnabled: Flow<Boolean> =
        store.data.map { prefs -> prefs[Keys.REAL_TIME_MONITORING] ?: true }

    override val deepFileInspectionEnabled: Flow<Boolean> =
        store.data.map { prefs -> prefs[Keys.DEEP_FILE_INSPECTION] ?: true }

    override val quillaCorrelationEnabled: Flow<Boolean> =
        store.data.map { prefs -> prefs[Keys.QUILLA_CORRELATION] ?: true }

    override val intelSyncEnabled: Flow<Boolean> =
        store.data.map { prefs -> prefs[Keys.INTEL_SYNC] ?: true }

    override suspend fun setRealTimeMonitoring(enabled: Boolean) {
        store.edit { prefs -> prefs[Keys.REAL_TIME_MONITORING] = enabled }
    }

    override suspend fun setDeepFileInspection(enabled: Boolean) {
        store.edit { prefs -> prefs[Keys.DEEP_FILE_INSPECTION] = enabled }
    }

    override suspend fun setQuillaCorrelation(enabled: Boolean) {
        store.edit { prefs -> prefs[Keys.QUILLA_CORRELATION] = enabled }
    }

    override suspend fun setIntelSync(enabled: Boolean) {
        store.edit { prefs -> prefs[Keys.INTEL_SYNC] = enabled }
    }

    private object Keys {
        val REAL_TIME_MONITORING = booleanPreferencesKey("real_time_monitoring_enabled")
        val DEEP_FILE_INSPECTION = booleanPreferencesKey("deep_file_inspection_enabled")
        val QUILLA_CORRELATION = booleanPreferencesKey("quilla_correlation_enabled")
        val INTEL_SYNC = booleanPreferencesKey("intel_sync_enabled")
    }
}
