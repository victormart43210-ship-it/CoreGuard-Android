package com.coldboar.coreguard.ui.screens

import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanSessionRepository
import com.coldboar.coreguard.mvt.ScanSessionSaveRequest
import com.coldboar.coreguard.mvt.ScanStageId
import com.coldboar.coreguard.settings.FakeUserSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lifecycle regressions for cooperative cancellation and scan-generation safety.
 *
 * Instantiates [ScannerViewModel] with fakes so cancel → CANCELLED persistence,
 * restart-after-cancel, and Scan-A/Scan-B races are proven on the JVM.
 */
class ScannerViewModelLifecycleTest {

    private class FakeSessions : ScanSessionRepository {
        val saved = CopyOnWriteArrayList<ScanSessionSaveRequest>()
        override fun ensureLegacyImport() = Unit
        override fun saveSession(request: ScanSessionSaveRequest): String {
            saved += request
            return "session-${saved.size}"
        }
    }

    private fun report(stamp: Long = 1_000L) = ScanReport(
        startedAtMillis = stamp,
        finishedAtMillis = stamp + 10,
        scannedPackages = 1,
        scannedProcesses = 0,
        scannedFiles = 0,
        indicatorCount = 0,
        detections = emptyList()
    )

    private fun withViewModel(
        sessions: FakeSessions = FakeSessions(),
        runner: DeviceScanRunner,
        block: (ScannerViewModel, FakeSessions) -> Unit
    ) {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        val vm = ScannerViewModel(
            settingsRepository = FakeUserSettingsRepository(),
            sessionRepository = sessions,
            scanRunner = runner,
            recordHistory = {},
            engineVersion = { "test-engine" },
            schemaVersion = { 2 },
            iocLoadedAtMs = { 0L },
            latestReport = { null },
            ioDispatcher = Dispatchers.Default,
            externalScope = scope
        )
        try {
            block(vm, sessions)
        } finally {
            scope.cancel()
        }
    }

    private fun waitUntil(timeoutMs: Long = 5_000L, predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return true
            Thread.sleep(20)
        }
        return predicate()
    }

    @Test
    fun `A cancelScan yields Cancelled UI state`() = runBlocking {
        val gate = AtomicBoolean(false)
        withViewModel(
            runner = DeviceScanRunner { _, cancellation, _, _ ->
                while (!gate.get()) {
                    cancellation.throwIfCancelled()
                    Thread.sleep(10)
                }
                cancellation.throwIfCancelled()
                report()
            }
        ) { vm, _ ->
            vm.startScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Scanning })
            vm.cancelScan()
            gate.set(true)
            assertTrue(
                "Expected Cancelled, was ${vm.uiState.value}",
                waitUntil { vm.uiState.value is ScannerUiState.Cancelled }
            )
        }
    }

    @Test
    fun `B cancelled session is persisted with endedAtMs`() = runBlocking {
        val gate = AtomicBoolean(false)
        withViewModel(
            runner = DeviceScanRunner { _, cancellation, _, _ ->
                while (!gate.get()) {
                    cancellation.throwIfCancelled()
                    Thread.sleep(10)
                }
                cancellation.throwIfCancelled()
                report()
            }
        ) { vm, sessions ->
            vm.startScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Scanning })
            vm.cancelScan()
            gate.set(true)
            assertTrue(waitUntil { sessions.saved.any { it.status == ScanStageId.CANCELLED } })
            val cancelled = sessions.saved.first { it.status == ScanStageId.CANCELLED }
            assertTrue(cancelled.endedAtMs > 0L)
            assertTrue(cancelled.endedAtMs >= cancelled.startedAtMs)
        }
    }

    @Test
    fun `C restart after cancel begins a new scan`() = runBlocking {
        val phase = AtomicInteger(0)
        withViewModel(
            runner = DeviceScanRunner { _, cancellation, _, _ ->
                if (phase.getAndIncrement() == 0) {
                    while (true) {
                        cancellation.throwIfCancelled()
                        Thread.sleep(10)
                    }
                }
                report(2_000L)
            }
        ) { vm, _ ->
            vm.startScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Scanning })
            vm.cancelScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Cancelled })
            vm.startScan()
            assertTrue(
                "Expected Complete after restart, was ${vm.uiState.value}",
                waitUntil { vm.uiState.value is ScannerUiState.Complete }
            )
        }
    }

    @Test
    fun `D Scan A late cancel must not overwrite Scan B UI`() = runBlocking {
        val aEntered = CountDownLatch(1)
        val aRelease = CountDownLatch(1)
        val scanIndex = AtomicInteger(0)
        withViewModel(
            runner = DeviceScanRunner { _, cancellation, _, _ ->
                val idx = scanIndex.getAndIncrement()
                if (idx == 0) {
                    aEntered.countDown()
                    aRelease.await(5, TimeUnit.SECONDS)
                    cancellation.throwIfCancelled()
                    report(100L)
                } else {
                    report(200L)
                }
            }
        ) { vm, _ ->
            vm.startScan() // A
            assertTrue(aEntered.await(5, TimeUnit.SECONDS))
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Scanning })
            vm.cancelScan()
            vm.startScan() // B — bumps generation before A finishes
            aRelease.countDown()
            assertTrue(
                "Scan B must win terminal UI; got ${vm.uiState.value}",
                waitUntil { vm.uiState.value is ScannerUiState.Complete }
            )
            val complete = vm.uiState.value as ScannerUiState.Complete
            assertEquals(200L, complete.report.startedAtMillis)
            assertFalse(vm.uiState.value is ScannerUiState.Cancelled)
        }
    }

    @Test
    fun `E after cancel settles never Scanning without an active job`() = runBlocking {
        val gate = AtomicBoolean(false)
        withViewModel(
            runner = DeviceScanRunner { _, cancellation, _, _ ->
                while (!gate.get()) {
                    cancellation.throwIfCancelled()
                    Thread.sleep(10)
                }
                cancellation.throwIfCancelled()
                report()
            }
        ) { vm, _ ->
            vm.startScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Scanning })
            vm.cancelScan()
            gate.set(true)
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Cancelled })
            assertFalse(
                "Impossible: Scanning with no active job",
                vm.uiState.value is ScannerUiState.Scanning && !vm.hasActiveScanJob()
            )
        }
    }

    @Test
    fun `sealed Cancelled state still carries no live verdict`() {
        val cancelled = ScannerUiState.Cancelled(
            sessionId = null,
            stageEvents = emptyList(),
            lastCompletedReport = null
        )
        assertTrue(cancelled is ScannerUiState.Cancelled)
        assertTrue(cancelled.lastCompletedReport == null)
    }
}
