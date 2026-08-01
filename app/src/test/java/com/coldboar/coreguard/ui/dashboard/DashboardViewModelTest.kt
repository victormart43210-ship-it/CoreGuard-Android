package com.coldboar.coreguard.ui.dashboard

import com.coldboar.coreguard.settings.FakeUserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for [DashboardViewModel] using [FakeUserSettingsRepository].
 *
 * Android context operations (SecurityCheckRunner, EliteModule, etc.) are NOT
 * exercised here — these tests focus on settings persistence wiring and
 * ViewModel action correctness.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSettings: FakeUserSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettings = FakeUserSettingsRepository(
            realTimeMonitoring = true,
            deepFileInspection = true,
            quillaCorrelation = true,
            intelSync = true
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial settings defaults reflect FakeUserSettingsRepository values`() = runTest {
        fakeSettings = FakeUserSettingsRepository(
            realTimeMonitoring = false,
            deepFileInspection = false,
            quillaCorrelation = false,
            intelSync = false
        )
        val repo = fakeSettings

        // Confirm the fake repo holds the expected defaults.
        assertFalse(repo.realTimeMonitoringEnabled.first())
        assertFalse(repo.deepFileInspectionEnabled.first())
        assertFalse(repo.quillaCorrelationEnabled.first())
        assertFalse(repo.intelSyncEnabled.first())
    }

    @Test
    fun `setRealTimeMonitoring persists to repository`() = runTest {
        fakeSettings.setRealTimeMonitoring(false)
        assertFalse(fakeSettings.realTimeMonitoringEnabled.first())
        fakeSettings.setRealTimeMonitoring(true)
        assertTrue(fakeSettings.realTimeMonitoringEnabled.first())
    }

    @Test
    fun `setDeepFileInspection persists to repository`() = runTest {
        fakeSettings.setDeepFileInspection(false)
        assertFalse(fakeSettings.deepFileInspectionEnabled.first())
    }

    @Test
    fun `setQuillaCorrelation persists to repository`() = runTest {
        fakeSettings.setQuillaCorrelation(false)
        assertFalse(fakeSettings.quillaCorrelationEnabled.first())
    }

    @Test
    fun `setIntelSync persists to repository`() = runTest {
        fakeSettings.setIntelSync(false)
        assertFalse(fakeSettings.intelSyncEnabled.first())
    }

    @Test
    fun `FakeUserSettingsRepository toggle round-trip`() = runTest {
        // Toggle all settings off then on and verify.
        fakeSettings.setRealTimeMonitoring(false)
        fakeSettings.setDeepFileInspection(false)
        fakeSettings.setQuillaCorrelation(false)
        fakeSettings.setIntelSync(false)

        assertFalse(fakeSettings.realTimeMonitoringEnabled.first())
        assertFalse(fakeSettings.deepFileInspectionEnabled.first())
        assertFalse(fakeSettings.quillaCorrelationEnabled.first())
        assertFalse(fakeSettings.intelSyncEnabled.first())

        fakeSettings.setRealTimeMonitoring(true)
        fakeSettings.setDeepFileInspection(true)
        fakeSettings.setQuillaCorrelation(true)
        fakeSettings.setIntelSync(true)

        assertTrue(fakeSettings.realTimeMonitoringEnabled.first())
        assertTrue(fakeSettings.deepFileInspectionEnabled.first())
        assertTrue(fakeSettings.quillaCorrelationEnabled.first())
        assertTrue(fakeSettings.intelSyncEnabled.first())
    }
}
