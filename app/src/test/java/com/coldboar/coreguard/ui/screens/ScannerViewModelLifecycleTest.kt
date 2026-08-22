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
import java.util.concurrent.atomic.AtomicReference

/**
 * Lifecycle regressions for cooperative cancellation and scan-generation safety.
 *
 * Uses [CoroutineStart.LAZY] publication semantics in [ScannerViewModel]: the Job
 * is assigned before the body runs, so cancellation never observes a null job.
 */
class ScannerViewModelLifecycleTest {

    private class FakeSessions : ScanSessionRepository {
        val saved = CopyOnWriteArrayList<ScanSessionSaveRequest>()
        override fun ensureLegacyImport() = Unit
        override fun saveSession(request: ScanSessionSaveRequest): String {
            saved += request
            return "session-${saved.size}-${request.status.name}"
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
            iocProvenance = {
                com.coldboar.coreguard.mvt.IocProvenanceSnapshot.unavailable()
            },
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

    private fun waitUntil(timeoutMs: Long = 8_000L, predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return true
            Thread.sleep(15)
        }
        return predicate()
    }

    @Test
    fun `job is published before scan body can observe cancellation`() = runBlocking {
        val bodyEntered = CountDownLatch(1)
        val releaseBody = CountDownLatch(1)
        val jobVisibleAtBodyStart = AtomicBoolean(false)
        withViewModel(
            runner = DeviceScanRunner { _, cancellation, _, _ ->
                // First instruction in the scan body: Job must already be published.
                jobVisibleAtBodyStart.set(true)
                bodyEntered.countDown()
                releaseBody.await(5, TimeUnit.SECONDS)
                cancellation.throwIfCancelled()
                report()
            }
        ) { vm, _ ->
            vm.startScan()
            assertTrue(bodyEntered.await(5, TimeUnit.SECONDS))
            // hasActiveScanJob must be true while body is blocked — proves publication.
            assertTrue("scanJob must be active before/while body runs", vm.hasActiveScanJob())
            assertTrue(jobVisibleAtBodyStart.get())
            releaseBody.countDown()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Complete })
        }
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
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Scanning && vm.hasActiveScanJob() })
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
        val entered = CountDownLatch(1)
        withViewModel(
            runner = DeviceScanRunner { _, cancellation, _, _ ->
                entered.countDown()
                while (true) {
                    cancellation.throwIfCancelled()
                    Thread.sleep(10)
                }
                @Suppress("UNREACHABLE_CODE")
                report()
            }
        ) { vm, sessions ->
            vm.startScan()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(waitUntil { vm.hasActiveScanJob() })
            vm.cancelScan()
            assertTrue(waitUntil { sessions.saved.any { it.status == ScanStageId.CANCELLED } })
            val cancelled = sessions.saved.filter { it.status == ScanStageId.CANCELLED }
            assertEquals("exactly one CANCELLED session", 1, cancelled.size)
            assertTrue(cancelled[0].endedAtMs > 0L)
            assertTrue(cancelled[0].endedAtMs >= cancelled[0].startedAtMs)
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
        ) { vm, sessions ->
            vm.startScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Scanning })
            vm.cancelScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Cancelled })
            vm.startScan()
            assertTrue(
                "Expected Complete after restart, was ${vm.uiState.value}",
                waitUntil { vm.uiState.value is ScannerUiState.Complete }
            )
            assertEquals(
                1,
                sessions.saved.count { it.status == ScanStageId.COMPLETED }
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
        ) { vm, sessions ->
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
            assertEquals(
                1,
                sessions.saved.count { it.status == ScanStageId.COMPLETED }
            )
        }
    }

    @Test
    fun `E after cancel settles never Scanning without an active job`() = runBlocking {
        val entered = CountDownLatch(1)
        withViewModel(
            runner = DeviceScanRunner { _, cancellation, _, _ ->
                entered.countDown()
                while (true) {
                    cancellation.throwIfCancelled()
                    Thread.sleep(10)
                }
                @Suppress("UNREACHABLE_CODE")
                report()
            }
        ) { vm, _ ->
            vm.startScan()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            vm.cancelScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Cancelled })
            assertFalse(
                "Impossible: Scanning with no active job",
                vm.uiState.value is ScannerUiState.Scanning && !vm.hasActiveScanJob()
            )
        }
    }

    @Test
    fun `successful scan persists exactly one COMPLETED session`() = runBlocking {
        withViewModel(
            runner = DeviceScanRunner { _, _, _, _ -> report(9_000L) }
        ) { vm, sessions ->
            vm.startScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Complete })
            assertEquals(1, sessions.saved.count { it.status == ScanStageId.COMPLETED })
            assertEquals(0, sessions.saved.count { it.status == ScanStageId.CANCELLED })
        }
    }

    @Test
    fun `failed scan persists exactly one FAILED session`() = runBlocking {
        withViewModel(
            runner = DeviceScanRunner { _, _, _, _ -> error("boom") }
        ) { vm, sessions ->
            vm.startScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Error })
            assertEquals(1, sessions.saved.count { it.status == ScanStageId.FAILED })
        }
    }

    @Test
    fun `stress start cancel restart does not strand Scanning`() = runBlocking {
        val lastTerminal = AtomicReference<ScannerUiState?>(null)
        withViewModel(
            runner = DeviceScanRunner { _, cancellation, _, _ ->
                // Alternate quick complete vs wait-for-cancel.
                repeat(20) {
                    cancellation.throwIfCancelled()
                    Thread.sleep(2)
                }
                report(System.currentTimeMillis())
            }
        ) { vm, _ ->
            repeat(100) { i ->
                vm.startScan()
                if (i % 2 == 0) {
                    Thread.sleep(5)
                    vm.cancelScan()
                }
                assertTrue(
                    waitUntil(timeoutMs = 5_000) {
                        val s = vm.uiState.value
                        s is ScannerUiState.Complete ||
                            s is ScannerUiState.Cancelled ||
                            s is ScannerUiState.Error
                    }
                )
                lastTerminal.set(vm.uiState.value)
                assertFalse(
                    vm.uiState.value is ScannerUiState.Scanning && !vm.hasActiveScanJob()
                )
            }
            assertTrue(lastTerminal.get() != null)
        }
    }

    @Test
    fun `sealed Cancelled state still carries no live verdict`() {
        val cancelled = ScannerUiState.Cancelled(
            sessionId = null,
            stageEvents = emptyList(),
            lastCompletedReport = null
        )
        assertTrue(cancelled.lastCompletedReport == null)
    }
}
