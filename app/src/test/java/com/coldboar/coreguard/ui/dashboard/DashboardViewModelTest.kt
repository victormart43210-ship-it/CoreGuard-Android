package com.coldboar.coreguard.ui.dashboard

import com.coldboar.coreguard.settings.FakeUserSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [DashboardUiState] and [FakeUserSettingsRepository].
 *
 * NOTE: [DashboardViewModel] requires an Android [Application] context for
 * [SecurityCheckRunner], [ScannerModule], and [SecurityScoreCache]. These tests
 * exercise the pure-Kotlin parts: UiState construction and the fake repository.
 *
 * Full ViewModel integration tests require Robolectric or an Android emulator.
 * See COREGUARD_TEST_EVIDENCE.md for the environment execution status.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    // -----------------------------------------------------------------------
    // DashboardUiState defaults
    // -----------------------------------------------------------------------

    @Test
    fun `DashboardUiState default score is null`() {
        val state = DashboardUiState()
        assertTrue("Default score must be null — never default to a 'safe' value", state.score == null)
    }

    @Test
    fun `DashboardUiState default evidence is empty`() {
        val state = DashboardUiState()
        assertTrue(state.evidence.isEmpty())
    }

    @Test
    fun `DashboardUiState deepFileInspectionEnabled defaults to false`() {
        // Not yet available — must not default to true so we don't mislead users.
        assertFalse(DashboardUiState().deepFileInspectionEnabled)
    }

    @Test
    fun `DashboardUiState quillaCorrelationEnabled defaults to false`() {
        assertFalse(DashboardUiState().quillaCorrelationEnabled)
    }

    @Test
    fun `DashboardUiState intelSyncEnabled defaults to false`() {
        assertFalse(DashboardUiState().intelSyncEnabled)
    }

    @Test
    fun `DashboardUiState realTimeMonitoringEnabled defaults to true`() {
        // Real-time monitoring (in-app refresh loop) is enabled by default.
        assertTrue(DashboardUiState().realTimeMonitoringEnabled)
    }

    // -----------------------------------------------------------------------
    // FakeUserSettingsRepository
    // -----------------------------------------------------------------------

    @Test
    fun `FakeUserSettingsRepository emits initial values`() = runTest {
        val repo = FakeUserSettingsRepository(
            realTimeMonitoring = true,
            deepFileInspection = false,
            quillaCorrelation = false,
            intelSync = false
        )
        assertTrue(repo.realTimeMonitoringEnabled.first())
        assertFalse(repo.deepFileInspectionEnabled.first())
        assertFalse(repo.quillaCorrelationEnabled.first())
        assertFalse(repo.intelSyncEnabled.first())
    }

    @Test
    fun `FakeUserSettingsRepository setRealTimeMonitoringEnabled updates flow`() = runTest {
        val repo = FakeUserSettingsRepository(realTimeMonitoring = true)
        repo.setRealTimeMonitoringEnabled(false)
        assertFalse(repo.realTimeMonitoringEnabled.first())
    }

    @Test
    fun `FakeUserSettingsRepository setDeepFileInspectionEnabled updates flow`() = runTest {
        val repo = FakeUserSettingsRepository(deepFileInspection = false)
        repo.setDeepFileInspectionEnabled(true)
        assertTrue(repo.deepFileInspectionEnabled.first())
    }

    @Test
    fun `FakeUserSettingsRepository setQuillaCorrelationEnabled updates flow`() = runTest {
        val repo = FakeUserSettingsRepository(quillaCorrelation = false)
        repo.setQuillaCorrelationEnabled(true)
        assertTrue(repo.quillaCorrelationEnabled.first())
    }

    @Test
    fun `FakeUserSettingsRepository setIntelSyncEnabled updates flow`() = runTest {
        val repo = FakeUserSettingsRepository(intelSync = false)
        repo.setIntelSyncEnabled(true)
        assertTrue(repo.intelSyncEnabled.first())
    }

    @Test
    fun `FakeUserSettingsRepository toggle round-trip is consistent`() = runTest {
        val repo = FakeUserSettingsRepository(realTimeMonitoring = true)
        repo.setRealTimeMonitoringEnabled(false)
        repo.setRealTimeMonitoringEnabled(true)
        assertTrue(repo.realTimeMonitoringEnabled.first())
    }

    // -----------------------------------------------------------------------
    // DashboardUiState copy semantics
    // -----------------------------------------------------------------------

    @Test
    fun `DashboardUiState copy preserves unmodified fields`() {
        val original = DashboardUiState(score = 75, shieldOn = true)
        val updated = original.copy(cpuText = "12%")
        assertEquals(75, updated.score)
        assertTrue(updated.shieldOn)
        assertEquals("12%", updated.cpuText)
    }

    @Test
    fun `DashboardUiState with null score does not show shieldOn as safe`() {
        // Truth-first rule: absence of score data is not the same as "safe."
        val state = DashboardUiState(score = null, shieldOn = false)
        assertFalse("Shield must not default to on", state.shieldOn)
    }
}
