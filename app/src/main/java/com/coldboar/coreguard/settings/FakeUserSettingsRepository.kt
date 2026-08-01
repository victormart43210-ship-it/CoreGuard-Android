package com.coldboar.coreguard.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake implementation of [UserSettingsRepository] for use in unit
 * tests and JVM previews.
 *
 * Allows tests to control toggle state and observe changes without a real
 * DataStore or Android context.
 */
class FakeUserSettingsRepository(
    realTimeMonitoring: Boolean = true,
    deepFileInspection: Boolean = true,
    quillaCorrelation: Boolean = true,
    intelSync: Boolean = true
) : UserSettingsRepository {

    private val _realTimeMonitoring = MutableStateFlow(realTimeMonitoring)
    private val _deepFileInspection = MutableStateFlow(deepFileInspection)
    private val _quillaCorrelation = MutableStateFlow(quillaCorrelation)
    private val _intelSync = MutableStateFlow(intelSync)

    override val realTimeMonitoringEnabled: Flow<Boolean> = _realTimeMonitoring.asStateFlow()
    override val deepFileInspectionEnabled: Flow<Boolean> = _deepFileInspection.asStateFlow()
    override val quillaCorrelationEnabled: Flow<Boolean> = _quillaCorrelation.asStateFlow()
    override val intelSyncEnabled: Flow<Boolean> = _intelSync.asStateFlow()

    override suspend fun setRealTimeMonitoring(enabled: Boolean) {
        _realTimeMonitoring.value = enabled
    }

    override suspend fun setDeepFileInspection(enabled: Boolean) {
        _deepFileInspection.value = enabled
    }

    override suspend fun setQuillaCorrelation(enabled: Boolean) {
        _quillaCorrelation.value = enabled
    }

    override suspend fun setIntelSync(enabled: Boolean) {
        _intelSync.value = enabled
    }
}
