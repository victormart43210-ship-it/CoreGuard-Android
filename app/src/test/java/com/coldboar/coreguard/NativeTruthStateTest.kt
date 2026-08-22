package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Truth-state specification for the native anti-tamper bridge.
 *
 * The native library is never present in a JVM unit test, so [NativeTamperGuard]
 * runs here in exactly the state it reaches on a device where
 * `libtamperguard.so` fails to load. That makes the "acquisition unavailable"
 * path directly observable without a device.
 *
 * The invariant under test: an unavailable native check must never be reported
 * as clean, safe, PASS, protected or verified.
 */
class NativeTruthStateTest {

    /**
     * Words that describe a *verified negative* result. None of them may appear
     * in the explanation of a check whose acquisition never completed.
     */
    private val cleanClaimWords = listOf(
        "clean", "safe", "protected", "no threat",
        "no hooks", "no tracer", "intact", "verified",
    )

    private fun assertNoCleanClaim(explanation: String) {
        val lower = explanation.lowercase()
        cleanClaimWords.forEach { word ->
            assertFalse(
                "Unavailable explanation must not claim \"$word\": $explanation",
                lower.contains(word),
            )
        }
    }

    // ---------------------------------------------------------------------
    // The native library cannot load on the JVM: this is the unavailable case
    // ---------------------------------------------------------------------

    @Test
    fun `native library is unavailable on the JVM`() {
        assertFalse(
            "libtamperguard.so cannot load in a JVM unit test",
            NativeTamperGuard.isAvailable,
        )
    }

    // ---------------------------------------------------------------------
    // 1. Native library unavailable -> never PASS
    // ---------------------------------------------------------------------

    @Test
    fun `debugger check is not PASS when the native library is unavailable`() {
        val result = NativeDebuggerEvaluator().evaluate()

        assertNotEquals(
            "An unreadable tracer source must not be reported as no debugger attached",
            SecurityCheckState.PASS,
            result.state,
        )
        assertNoCleanClaim(result.explanation)
    }

    @Test
    fun `frida check is not PASS when the native library is unavailable`() {
        val result = FridaDetectionEvaluator().evaluate()

        assertNotEquals(
            "An unreadable Frida source must not be reported as no instrumentation",
            SecurityCheckState.PASS,
            result.state,
        )
        assertNoCleanClaim(result.explanation)
    }

    @Test
    fun `hook check is not PASS when the native library is unavailable`() {
        val result = HookDetectionEvaluator().evaluate()

        assertNotEquals(
            "An unreadable library map must not be reported as no hooks",
            SecurityCheckState.PASS,
            result.state,
        )
        assertNoCleanClaim(result.explanation)
    }

    @Test
    fun `mount check is not PASS when the native library is unavailable`() {
        val result = MountIntegrityEvaluator().evaluate()

        assertNotEquals(
            "An unreadable mount source must not be reported as no root mount",
            SecurityCheckState.PASS,
            result.state,
        )
        assertNoCleanClaim(result.explanation)
    }

    @Test
    fun `code integrity check is not PASS when the native library is unavailable`() {
        val result = MemoryIntegrityEvaluator().evaluate()

        assertNotEquals(
            "An unavailable baseline must not be reported as intact code",
            SecurityCheckState.PASS,
            result.state,
        )
        assertNoCleanClaim(result.explanation)
    }

    // ---------------------------------------------------------------------
    // Every native-backed check in the runner must degrade the same way
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // 2-8. Each unavailable reason degrades to WARN, never PASS
    // ---------------------------------------------------------------------

    @Test
    fun `every unavailable reason degrades every native check to WARN`() {
        NativeUnavailableReason.values().forEach { reason ->
            val results = listOf(
                NativeDebuggerEvaluator(tracerPid = { unavailableAcquisition(reason) }).evaluate(),
                FridaDetectionEvaluator(
                    portOpen = { unavailableAcquisition(reason) },
                    suspiciousThread = { unavailableAcquisition(reason) },
                ).evaluate(),
                HookDetectionEvaluator(hookedLibrary = { unavailableAcquisition(reason) }).evaluate(),
                MountIntegrityEvaluator(rootMount = { unavailableAcquisition(reason) }).evaluate(),
                MemoryIntegrityEvaluator(codeIntegrity = { unavailableAcquisition(reason) }).evaluate(),
            )

            results.forEach { result ->
                assertEquals(
                    "${result.id} must be WARN for reason $reason",
                    SecurityCheckState.WARN,
                    result.state,
                )
                assertNoCleanClaim(result.explanation)
            }
        }
    }

    @Test
    fun `a clean half signal cannot pass when the other half is unavailable`() {
        // The Frida check reads two sources. One clean source plus one blind
        // source is not a verified-clean result.
        val threadBlind = FridaDetectionEvaluator(
            portOpen = { available(false) },
            suspiciousThread = { unavailableAcquisition() },
        ).evaluate()
        val portBlind = FridaDetectionEvaluator(
            portOpen = { unavailableAcquisition() },
            suspiciousThread = { available("") },
        ).evaluate()

        assertEquals(SecurityCheckState.WARN, threadBlind.state)
        assertEquals(SecurityCheckState.WARN, portBlind.state)
        assertNoCleanClaim(threadBlind.explanation)
        assertNoCleanClaim(portBlind.explanation)
    }

    // ---------------------------------------------------------------------
    // 9-10. Completed acquisitions still classify correctly
    // ---------------------------------------------------------------------

    @Test
    fun `verified clean acquisition still produces PASS`() {
        assertEquals(
            SecurityCheckState.PASS,
            NativeDebuggerEvaluator(tracerPid = { available(0) }).evaluate().state,
        )
        assertEquals(
            SecurityCheckState.PASS,
            FridaDetectionEvaluator(
                portOpen = { available(false) },
                suspiciousThread = { available("") },
            ).evaluate().state,
        )
        assertEquals(
            SecurityCheckState.PASS,
            HookDetectionEvaluator(hookedLibrary = { available("") }).evaluate().state,
        )
        assertEquals(
            SecurityCheckState.PASS,
            MountIntegrityEvaluator(rootMount = { available("") }).evaluate().state,
        )
        assertEquals(
            SecurityCheckState.PASS,
            MemoryIntegrityEvaluator(codeIntegrity = { available(true) }).evaluate().state,
        )
    }

    @Test
    fun `verified suspicious acquisition still produces FAIL`() {
        assertEquals(
            SecurityCheckState.FAIL,
            NativeDebuggerEvaluator(tracerPid = { available(4242) }).evaluate().state,
        )
        assertEquals(
            SecurityCheckState.FAIL,
            FridaDetectionEvaluator(
                portOpen = { available(true) },
                suspiciousThread = { available("") },
            ).evaluate().state,
        )
        assertEquals(
            SecurityCheckState.FAIL,
            HookDetectionEvaluator(hookedLibrary = { available("/data/local/tmp/libgadget.so") })
                .evaluate().state,
        )
        assertEquals(
            SecurityCheckState.FAIL,
            MountIntegrityEvaluator(rootMount = { available("magisk /sbin/.magisk tmpfs rw") })
                .evaluate().state,
        )
        assertEquals(
            SecurityCheckState.FAIL,
            MemoryIntegrityEvaluator(codeIntegrity = { available(false) }).evaluate().state,
        )
    }

    // ---------------------------------------------------------------------
    // Rule F: confidence may degrade automatically but never upgrade
    // ---------------------------------------------------------------------

    @Test
    fun `a failed integrity result is not upgraded by a later unavailable probe`() {
        val failed = MemoryIntegrityEvaluator(codeIntegrity = { available(false) }).evaluate()
        val thenBlind = MemoryIntegrityEvaluator(codeIntegrity = { unavailableAcquisition() }).evaluate()

        assertEquals(SecurityCheckState.FAIL, failed.state)
        // The weaker later observation must not become PASS.
        assertNotEquals(SecurityCheckState.PASS, thenBlind.state)
        assertNoCleanClaim(thenBlind.explanation)
    }

    // ---------------------------------------------------------------------
    // 12. Categorical reasons only — no raw native evidence leaks
    // ---------------------------------------------------------------------

    @Test
    fun `unavailable explanations contain no raw native evidence`() {
        NativeUnavailableReason.values().forEach { reason ->
            val text = reason.explain("Probe")
            listOf("/proc", "Exception", "at com.", ".so", "TracerPid=").forEach { leak ->
                assertFalse(
                    "Reason text must not leak \"$leak\": $text",
                    text.contains(leak),
                )
            }
        }
    }

    @Test
    fun `no native backed check reports PASS while the library is unavailable`() {
        val nativeBacked = listOf(
            NativeDebuggerEvaluator(),
            FridaDetectionEvaluator(),
            HookDetectionEvaluator(),
            MountIntegrityEvaluator(),
            MemoryIntegrityEvaluator(),
        )

        nativeBacked.forEach { evaluator ->
            val result = evaluator.evaluate()
            assertEquals(
                "${result.id} must warn instead of passing when acquisition is unavailable",
                SecurityCheckState.WARN,
                result.state,
            )
            assertNoCleanClaim(result.explanation)
            assertTrue(
                "${result.id} must say the check could not be completed",
                result.explanation.contains("could not be completed", ignoreCase = true),
            )
        }
    }
}
