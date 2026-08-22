package com.coldboar.coreguard.ui.screens

import android.content.Context
import com.coldboar.coreguard.mvt.ScanCancellation
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.ScanProgressListener
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanSessionRepository
import com.coldboar.coreguard.mvt.ScanSessionSaveRequest
import com.coldboar.coreguard.mvt.ScanStageEvent
import com.coldboar.coreguard.mvt.ScanStageId
import com.coldboar.coreguard.settings.FakeUserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class ScannerViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cancelScan persists CANCELLED and reaches Cancelled ui state`() = runTest {
        val repository = CapturingScanSessionRepository()
        val viewModel = newViewModel(
            sessionRepository = repository,
            scanDevice = { _, listener, cancellation, _, _ ->
                listener.onStage(ScanStageEvent(ScanStageId.PREPARING))
                while (true) {
                    cancellation.throwIfCancelled()
                    delay(5)
                }
            }
        )

        viewModel.startScan()
        advanceUntilIdle()
        viewModel.cancelScan()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ScannerUiState.Cancelled)
        val cancelledSave = repository.saved.lastOrNull()
        assertEquals(ScanStageId.CANCELLED, cancelledSave?.status)
    }

    @Test
    fun `stale first scan cannot overwrite newer scan state`() = runTest {
        val repository = CapturingScanSessionRepository()
        var invocation = 0
        val firstReport = buildReport(111L)
        val secondReport = buildReport(222L)
        val viewModel = newViewModel(
            sessionRepository = repository,
            scanDevice = { _, listener, _, _, _ ->
                invocation += 1
                listener.onStage(ScanStageEvent(ScanStageId.PREPARING))
                if (invocation == 1) {
                    withContext(NonCancellable) { delay(100) }
                    firstReport
                } else {
                    secondReport
                }
            }
        )

        viewModel.startScan()
        viewModel.startScan()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ScannerUiState.Complete)
        val complete = state as ScannerUiState.Complete
        assertEquals(secondReport.finishedAtMillis, complete.report.finishedAtMillis)
        assertEquals(1, repository.saved.count { it.status == ScanStageId.COMPLETED })
    }

    @Test
    fun `scan can restart immediately after cancellation`() = runTest {
        val repository = CapturingScanSessionRepository()
        var invocation = 0
        val completeReport = buildReport(333L)
        val viewModel = newViewModel(
            sessionRepository = repository,
            scanDevice = { _, _, cancellation, _, _ ->
                invocation += 1
                if (invocation == 1) {
                    while (true) {
                        cancellation.throwIfCancelled()
                        delay(5)
                    }
                }
                completeReport
            }
        )

        viewModel.startScan()
        viewModel.cancelScan()
        viewModel.startScan()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ScannerUiState.Complete)
        assertEquals(ScanStageId.CANCELLED, repository.saved.first().status)
        assertEquals(ScanStageId.COMPLETED, repository.saved.last().status)
    }

    private fun newViewModel(
        sessionRepository: CapturingScanSessionRepository,
        scanDevice: suspend (
            context: Context,
            listener: ScanProgressListener,
            cancellation: ScanCancellation,
            deepFileInspectionEnabled: Boolean,
            quillaCorrelationEnabled: Boolean
        ) -> ScanReport
    ): ScannerViewModel {
        val context = mock(Context::class.java)
        return ScannerViewModel(
            appContext = context,
            settingsRepository = FakeUserSettingsRepository(
                deepFileInspection = true,
                quillaCorrelation = true
            ),
            sessionRepository = sessionRepository,
            scanDevice = scanDevice,
            recordHistory = { _, _ -> },
            latestReportProvider = { null },
            currentTimeMs = { 1_000L }
        )
    }

    private fun buildReport(finishedAt: Long): ScanReport =
        ScanReport(
            startedAtMillis = finishedAt - 10,
            finishedAtMillis = finishedAt,
            scannedPackages = 1,
            scannedProcesses = 1,
            scannedFiles = 1,
            indicatorCount = 1,
            detections = emptyList<Detection>()
        )

    private class CapturingScanSessionRepository : ScanSessionRepository {
        val saved = mutableListOf<ScanSessionSaveRequest>()
        override fun ensureLegacyImport() = Unit

        override fun saveSession(request: ScanSessionSaveRequest): String {
            saved += request
            return "session-${saved.size}"
        }
    }
}
