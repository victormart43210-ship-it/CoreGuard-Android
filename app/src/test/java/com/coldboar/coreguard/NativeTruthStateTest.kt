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
