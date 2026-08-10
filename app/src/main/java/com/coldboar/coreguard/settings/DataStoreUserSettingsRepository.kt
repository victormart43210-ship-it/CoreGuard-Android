package com.coldboar.coreguard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "coreguard_user_settings"
)

/**
 * Preferences DataStore–backed implementation of [UserSettingsRepository].
 *
 * Persists user preferences across app restarts. Each setting has a documented
 * default and a note about whether the backend actually honors the value yet.
 *
 * All I/O is handled by the DataStore library; callers do not need to manage
 * threading.
 */
class DataStoreUserSettingsRepository(private val context: Context) : UserSettingsRepository {

    private object Keys {
        val REAL_TIME_MONITORING = booleanPreferencesKey("real_time_monitoring_enabled")
        val DEEP_FILE_INSPECTION = booleanPreferencesKey("deep_file_inspection_enabled")
        val QUILLA_CORRELATION = booleanPreferencesKey("quilla_correlation_enabled")
        val INTEL_SYNC = booleanPreferencesKey("intel_sync_enabled")
    }

    override val realTimeMonitoringEnabled: Flow<Boolean> =
        context.userSettingsDataStore.data.map { prefs ->
            prefs[Keys.REAL_TIME_MONITORING] ?: true
        }

    override val deepFileInspectionEnabled: Flow<Boolean> =
        context.userSettingsDataStore.data.map { prefs ->
            prefs[Keys.DEEP_FILE_INSPECTION] ?: true
        }

    override val quillaCorrelationEnabled: Flow<Boolean> =
        context.userSettingsDataStore.data.map { prefs ->
            prefs[Keys.QUILLA_CORRELATION] ?: true
        }

    override val intelSyncEnabled: Flow<Boolean> =
        context.userSettingsDataStore.data.map { prefs ->
            prefs[Keys.INTEL_SYNC] ?: true
        }

    override suspend fun setRealTimeMonitoringEnabled(enabled: Boolean) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[Keys.REAL_TIME_MONITORING] = enabled
        }
    }

    override suspend fun setDeepFileInspectionEnabled(enabled: Boolean) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[Keys.DEEP_FILE_INSPECTION] = enabled
        }
    }

    override suspend fun setQuillaCorrelationEnabled(enabled: Boolean) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[Keys.QUILLA_CORRELATION] = enabled
        }
    }

    override suspend fun setIntelSyncEnabled(enabled: Boolean) {
        context.userSettingsDataStore.edit { prefs ->
            prefs[Keys.INTEL_SYNC] = enabled
        }
    }
}
