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

class ScannerViewModelProvenanceTest {

    private class FakeSessions : ScanSessionRepository {
        val saved = CopyOnWriteArrayList<ScanSessionSaveRequest>()
        override fun ensureLegacyImport() = Unit
        override fun saveSession(request: ScanSessionSaveRequest): String {
            saved += request
            return "s-${saved.size}"
        }
    }

    private fun report() = ScanReport(
        startedAtMillis = 1L,
        finishedAtMillis = 2L,
        scannedPackages = 1,
        scannedProcesses = 0,
        scannedFiles = 0,
        indicatorCount = 1,
        detections = emptyList()
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

    private fun verifiedSnap() = IocProvenanceSnapshot(
        provenanceClass = IocProvenanceClass.VERIFIED_REMOTE,
        feedSource = "Amnesty Pegasus (NSO)",
        feedVersion = "commit=abc;sha256=def",
        feedAuthenticity = "VERIFIED_REMOTE — digest pin",
        feedLoadedAtMs = 50L,
        indicatorCount = 10,
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
    fun `completed verified remote persists commit and sha`() = runBlocking {
        val sessions = FakeSessions()
        withVm(sessions, loadedAt = 50L, provenance = verifiedSnap(), runner = { _, _, _, _ -> report() }) { vm, s ->
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
        withVm(sessions, 50L, bundledSnap(), { _, _, _, _ -> report() }) { vm, s ->
            vm.startScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Complete })
            val saved = s.saved.single()
            assertTrue(saved.feedAuthenticity.contains("BUNDLED"))
            assertNull(saved.feedVersion)
            assertFalse(saved.feedAuthenticity.contains("VERIFIED_REMOTE —"))
        }
    }

    @Test
    fun `cancelled without loaded IOC snapshot is UNAVAILABLE`() = runBlocking {
        val gate = AtomicBoolean(false)
        val sessions = FakeSessions()
        withVm(
            sessions,
            loadedAt = 0L,
            provenance = verifiedSnap(), // must be ignored when not loaded
            runner = DeviceScanRunner { _, cancellation, _, _ ->
                while (!gate.get()) {
                    cancellation.throwIfCancelled()
                    Thread.sleep(10)
                }
                cancellation.throwIfCancelled()
                report()
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
    fun `failed without loaded IOC snapshot is UNAVAILABLE`() = runBlocking {
        val sessions = FakeSessions()
        withVm(
            sessions,
            loadedAt = 0L,
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
    fun `cancelled with bundled snapshot keeps bundled not verified`() = runBlocking {
        val gate = AtomicBoolean(false)
        val sessions = FakeSessions()
        withVm(
            sessions,
            loadedAt = 50L,
            provenance = bundledSnap(),
            runner = DeviceScanRunner { _, cancellation, _, _ ->
                while (!gate.get()) {
                    cancellation.throwIfCancelled()
                    Thread.sleep(10)
                }
                cancellation.throwIfCancelled()
                report()
            }
        ) { vm, s ->
            vm.startScan()
            assertTrue(waitUntil { vm.hasActiveScanJob() })
            vm.cancelScan()
            gate.set(true)
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Cancelled })
            val saved = s.saved.single { it.status == ScanStageId.CANCELLED }
            assertTrue(saved.feedAuthenticity.contains("BUNDLED"))
            assertNull(saved.feedVersion)
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
        withVm(sessions, 50L, mixed, { _, _, _, _ -> report() }) { vm, s ->
            vm.startScan()
            assertTrue(waitUntil { vm.uiState.value is ScannerUiState.Complete })
            val saved = s.saved.single()
            assertEquals(null, saved.feedVersion)
            assertTrue(saved.feedAuthenticity.contains("MIXED"))
        }
    }
}
