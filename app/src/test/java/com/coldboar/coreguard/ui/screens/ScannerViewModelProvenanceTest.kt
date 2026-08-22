package com.coldboar.coreguard.ui.screens

import com.coldboar.coreguard.mvt.IocProvenanceClass
import com.coldboar.coreguard.mvt.IocProvenanceSnapshot
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ScannerViewModelProvenanceTest {

    private class FakeSessions : ScanSessionRepository {
        val saved = CopyOnWriteArrayList<ScanSessionSaveRequest>()
        override fun ensureLegacyImport() = Unit
        override fun saveSession(request: ScanSessionSaveRequest): String {
            saved += request
            return "s-${saved.size}"
        }
    }

    private fun report(
        provenance: IocProvenanceSnapshot = IocProvenanceSnapshot.unavailable(),
        indicatorCount: Int = 1
    ) = ScanReport(
        startedAtMillis = 1L,
        finishedAtMillis = 2L,
        scannedPackages = 1,
        scannedProcesses = 0,
        scannedFiles = 0,
        indicatorCount = indicatorCount,
        detections = emptyList(),
        iocProvenance = provenance
    )

    private fun waitUntil(timeoutMs: Long = 8_000L, predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return true
            Thread.sleep(15)
        }
        return predicate()
    }

    private fun withVm(
        sessions: FakeSessions,
        loadedAt: Long,
        provenance: IocProvenanceSnapshot,
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
            engineVersion = { "test" },
            schemaVersion = { 2 },
            iocLoadedAtMs = { loadedAt },
            iocProvenance = { provenance },
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

    private fun verifiedSnap(count: Int = 10) = IocProvenanceSnapshot(
        provenanceClass = IocProvenanceClass.VERIFIED_REMOTE,
        feedSource = "Amnesty Pegasus (NSO)",
        feedVersion = "commit=abc;sha256=def",
        feedAuthenticity = "VERIFIED_REMOTE — digest pin",
        feedLoadedAtMs = 50L,
        indicatorCount = count,
        contributingClasses = setOf(IocProvenanceClass.VERIFIED_REMOTE)
    )

    private fun bundledSnap() = IocProvenanceSnapshot(
        provenanceClass = IocProvenanceClass.BUNDLED,
        feedSource = "Bundled assets/ioc",
        feedVersion = null,
        feedAuthenticity = "BUNDLED — packaged application indicators; not remote-verified",
        feedLoadedAtMs = 50L,
        indicatorCount = 5,
        contributingClasses = setOf(IocProvenanceClass.BUNDLED)
    )

    @Test
    fun `completed verified remote persists commit and sha from scan result`() = runBlocking {
        val sessions = FakeSessions()
        val snap = verifiedSnap()
        withVm(
            sessions,
            loadedAt = 50L,
            provenance = IocProvenanceSnapshot.unavailable(), // global must be ignored
            runner = { _, _, _, _ -> report(snap) }
        ) { vm, s ->
            vm.startScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Complete })
            val saved = s.saved.single { it.status == ScanStageId.COMPLETED }
            assertTrue(saved.feedAuthenticity.startsWith("VERIFIED_REMOTE"))
            assertEquals("commit=abc;sha256=def", saved.feedVersion)
        }
    }

    @Test
    fun `completed bundled never claims verified remote`() = runBlocking {
        val sessions = FakeSessions()
        withVm(sessions, 50L, verifiedSnap(), { _, _, _, _ -> report(bundledSnap()) }) { vm, s ->
            vm.startScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Complete })
            val saved = s.saved.single()
            assertTrue(saved.feedAuthenticity.contains("BUNDLED"))
            assertNull(saved.feedVersion)
            assertFalse(saved.feedAuthenticity.contains("VERIFIED_REMOTE —"))
        }
    }

    @Test
    fun `cancelled does not inherit previous cached provenance`() = runBlocking {
        val gate = AtomicBoolean(false)
        val sessions = FakeSessions()
        withVm(
            sessions,
            loadedAt = 50L,
            provenance = verifiedSnap(), // previous cached — must not be used on cancel
            runner = DeviceScanRunner { _, cancellation, _, _ ->
                while (!gate.get()) {
                    cancellation.throwIfCancelled()
                    Thread.sleep(10)
                }
                cancellation.throwIfCancelled()
                report(verifiedSnap())
            }
        ) { vm, s ->
            vm.startScan()
            assertTrue(waitUntil { vm.hasActiveScanJob() })
            vm.cancelScan()
            gate.set(true)
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Cancelled })
            val saved = s.saved.single { it.status == ScanStageId.CANCELLED }
            assertEquals("UNAVAILABLE", saved.feedSource)
            assertTrue(saved.feedAuthenticity.contains("UNAVAILABLE"))
            assertNull(saved.feedVersion)
        }
    }

    @Test
    fun `failed does not inherit previous cached provenance`() = runBlocking {
        val sessions = FakeSessions()
        withVm(
            sessions,
            loadedAt = 50L,
            provenance = verifiedSnap(),
            runner = DeviceScanRunner { _, _, _, _ -> error("boom") }
        ) { vm, s ->
            vm.startScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Error })
            val saved = s.saved.single { it.status == ScanStageId.FAILED }
            assertTrue(saved.feedAuthenticity.contains("UNAVAILABLE"))
            assertNull(saved.feedVersion)
        }
    }

    @Test
    fun `refresh during scan does not change saved provenance`() = runBlocking {
        val sessions = FakeSessions()
        val globalProv = AtomicReference(bundledSnap())
        val scanSnap = verifiedSnap(count = 3)
        withVm(
            sessions,
            loadedAt = 50L,
            provenance = bundledSnap(),
            runner = DeviceScanRunner { _, _, _, _ ->
                // Simulate mid-scan refresh flipping the global repository.
                globalProv.set(
                    IocProvenanceSnapshot(
                        provenanceClass = IocProvenanceClass.USER_IMPORTED,
                        feedSource = "refreshed",
                        feedVersion = null,
                        feedAuthenticity = "USER_IMPORTED",
                        feedLoadedAtMs = 99L,
                        indicatorCount = 99
                    )
                )
                report(scanSnap, indicatorCount = 3)
            }
        ) { vm, s ->
            // Wire iocProvenance to mutable global — ViewModel must still use scan result.
            val job = SupervisorJob()
            val scope = CoroutineScope(job + Dispatchers.Default)
            val localSessions = FakeSessions()
            val localVm = ScannerViewModel(
                settingsRepository = FakeUserSettingsRepository(),
                sessionRepository = localSessions,
                scanRunner = DeviceScanRunner { _, _, _, _ ->
                    globalProv.set(
                        IocProvenanceSnapshot(
                            provenanceClass = IocProvenanceClass.USER_IMPORTED,
                            feedSource = "refreshed",
                            feedVersion = null,
                            feedAuthenticity = "USER_IMPORTED",
                            feedLoadedAtMs = 99L,
                            indicatorCount = 99
                        )
                    )
                    report(scanSnap, indicatorCount = 3)
                },
                recordHistory = {},
                engineVersion = { "test" },
                schemaVersion = { 2 },
                iocLoadedAtMs = { 50L },
                iocProvenance = { globalProv.get() },
                latestReport = { null },
                ioDispatcher = Dispatchers.Default,
                externalScope = scope
            )
            try {
                localVm.startScan()
                assertTrue(waitUntil { localVm.uiState.value is ScannerUiState.Complete })
                val saved = localSessions.saved.single()
                assertEquals("Amnesty Pegasus (NSO)", saved.feedSource)
                assertEquals("commit=abc;sha256=def", saved.feedVersion)
                assertFalse(saved.feedSource.contains("refreshed"))
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun `overlapping scans isolate provenance per generation`() = runBlocking {
        val sessions = FakeSessions()
        val gate1 = AtomicBoolean(false)
        val started = AtomicInteger(0)
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        val vm = ScannerViewModel(
            settingsRepository = FakeUserSettingsRepository(),
            sessionRepository = sessions,
            scanRunner = DeviceScanRunner { _, cancellation, _, _ ->
                val n = started.incrementAndGet()
                if (n == 1) {
                    while (!gate1.get()) {
                        cancellation.throwIfCancelled()
                        Thread.sleep(10)
                    }
                    cancellation.throwIfCancelled()
                    report(bundledSnap())
                } else {
                    report(verifiedSnap())
                }
            },
            recordHistory = {},
            engineVersion = { "test" },
            schemaVersion = { 2 },
            iocLoadedAtMs = { 1L },
            iocProvenance = { verifiedSnap() },
            latestReport = { null },
            ioDispatcher = Dispatchers.Default,
            externalScope = scope
        )
        try {
            vm.startScan()
            assertTrue(waitUntil { started.get() >= 1 })
            vm.startScan() // supersedes first generation
            gate1.set(true)
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Complete })
            val completed = sessions.saved.filter { it.status == ScanStageId.COMPLETED }
            assertTrue(completed.isNotEmpty())
            assertTrue(completed.last().feedAuthenticity.startsWith("VERIFIED_REMOTE"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `duplicate indicators contribute unique count via scan result`() = runBlocking {
        val sessions = FakeSessions()
        val snap = verifiedSnap(count = 2) // unique after dedup
        withVm(sessions, 50L, verifiedSnap(99), { _, _, _, _ -> report(snap, indicatorCount = 2) }) { vm, s ->
            vm.startScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Complete })
            val complete = vm.uiState.value as ScannerUiState.Complete
            assertEquals(2, complete.report.indicatorCount)
            assertEquals(2, complete.report.iocProvenance.indicatorCount)
        }
    }

    @Test
    fun `mixed provenance never stores verified feedVersion`() = runBlocking {
        val mixed = IocProvenanceSnapshot(
            provenanceClass = IocProvenanceClass.MIXED,
            feedSource = "BUNDLED+VERIFIED_REMOTE",
            feedVersion = null,
            feedAuthenticity = "MIXED — must not inherit remote-feed verification",
            feedLoadedAtMs = 50L,
            indicatorCount = 9,
            contributingClasses = setOf(IocProvenanceClass.BUNDLED, IocProvenanceClass.VERIFIED_REMOTE)
        )
        val sessions = FakeSessions()
        withVm(sessions, 50L, verifiedSnap(), { _, _, _, _ -> report(mixed) }) { vm, s ->
            vm.startScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Complete })
            val saved = s.saved.single()
            assertEquals(null, saved.feedVersion)
            assertTrue(saved.feedAuthenticity.contains("MIXED"))
        }
    }
}
