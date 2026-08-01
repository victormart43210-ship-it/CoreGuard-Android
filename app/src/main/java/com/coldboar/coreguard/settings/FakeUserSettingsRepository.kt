package com.coldboar.coreguard.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake implementation of [UserSettingsRepository] for use in unit
 * tests and ViewModel tests.
 *
 * State is held in [MutableStateFlow]s so tests can observe changes reactively.
 * All setter methods are synchronous (they update the flow immediately) to avoid
 * needing a real coroutine scope in tests.
 */
class FakeUserSettingsRepository(
    realTimeMonitoring: Boolean = true,
    deepFileInspection: Boolean = false,
    quillaCorrelation: Boolean = false,
    intelSync: Boolean = false
) : UserSettingsRepository {

    private val _realTimeMonitoring = MutableStateFlow(realTimeMonitoring)
    private val _deepFileInspection = MutableStateFlow(deepFileInspection)
    private val _quillaCorrelation = MutableStateFlow(quillaCorrelation)
    private val _intelSync = MutableStateFlow(intelSync)

    override val realTimeMonitoringEnabled: Flow<Boolean> = _realTimeMonitoring.asStateFlow()
    override val deepFileInspectionEnabled: Flow<Boolean> = _deepFileInspection.asStateFlow()
    override val quillaCorrelationEnabled: Flow<Boolean> = _quillaCorrelation.asStateFlow()
    override val intelSyncEnabled: Flow<Boolean> = _intelSync.asStateFlow()

    override suspend fun setRealTimeMonitoringEnabled(enabled: Boolean) {
        _realTimeMonitoring.value = enabled
    }

    override suspend fun setDeepFileInspectionEnabled(enabled: Boolean) {
        _deepFileInspection.value = enabled
    }

    override suspend fun setQuillaCorrelationEnabled(enabled: Boolean) {
        _quillaCorrelation.value = enabled
    }

    override suspend fun setIntelSyncEnabled(enabled: Boolean) {
        _intelSync.value = enabled
    }
}
