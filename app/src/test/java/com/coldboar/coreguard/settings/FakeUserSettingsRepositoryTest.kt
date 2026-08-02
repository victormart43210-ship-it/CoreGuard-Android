package com.coldboar.coreguard.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeUserSettingsRepositoryTest {

    @Test
    fun `setters persist and emit updated values`() = runTest {
        val repository = FakeUserSettingsRepository(
            realTimeMonitoring = true,
            deepFileInspection = false,
            quillaCorrelation = false,
            intelSync = false
        )

        repository.setRealTimeMonitoringEnabled(false)
        repository.setDeepFileInspectionEnabled(true)
        repository.setQuillaCorrelationEnabled(true)
        repository.setIntelSyncEnabled(true)

        assertFalse(repository.realTimeMonitoringEnabled.first())
        assertTrue(repository.deepFileInspectionEnabled.first())
        assertTrue(repository.quillaCorrelationEnabled.first())
        assertTrue(repository.intelSyncEnabled.first())
    }
}
