package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the security check evaluators.
 *
 * All evaluators accept injectable lambdas/parameters so they can be tested
 * on the JVM without Android framework calls.
 */
class SecurityCheckEvaluatorTest {

    // -----------------------------------------------------------------------
    // DebuggerCheckEvaluator
    // -----------------------------------------------------------------------

    @Test
    fun `DebuggerCheckEvaluator returns PASS when no debugger`() {
        val evaluator = DebuggerCheckEvaluator(isDebuggerConnected = { false })
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
        assertEquals("debugger", result.id)
    }

    @Test
    fun `DebuggerCheckEvaluator returns FAIL when debugger attached`() {
        val evaluator = DebuggerCheckEvaluator(isDebuggerConnected = { true })
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.FAIL, result.state)
    }

    @Test
    fun `DebuggerCheckEvaluator explanation is non-empty`() {
        val result = DebuggerCheckEvaluator(isDebuggerConnected = { false }).evaluate()
        assertTrue(result.explanation.isNotEmpty())
    }

    // -----------------------------------------------------------------------
    // EmulatorCheckEvaluator
    // -----------------------------------------------------------------------

    @Test
    fun `EmulatorCheckEvaluator returns PASS on real device fingerprint`() {
        val evaluator = EmulatorCheckEvaluator(
            fingerprint = "samsung/SM-G991B/SM-G991B:11/RP1A.200720.012/G991BXXU3CUL1:user/release-keys",
            model = "SM-G991B",
            manufacturer = "samsung",
            brand = "samsung",
            hardware = "exynos2100",
            product = "SM-G991B"
        )
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
    }

    @Test
    fun `EmulatorCheckEvaluator returns WARN on generic fingerprint`() {
        val evaluator = EmulatorCheckEvaluator(
            fingerprint = "generic/sdk_gphone64_arm64/generic_arm64:12/SE1A.211212.001/7941916:userdebug/test-keys",
            model = "sdk_gphone64_arm64",
            manufacturer = "Google",
            brand = "google",
            hardware = "ranchu",
            product = "sdk_gphone64_arm64"
        )
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.WARN, result.state)
    }

    @Test
    fun `EmulatorCheckEvaluator detects goldfish hardware`() {
        val evaluator = EmulatorCheckEvaluator(
            fingerprint = "Android/sdk_x86/generic_x86:9/PSR1.180720.012/5124027:userdebug/test-keys",
            model = "Android SDK built for x86",
            manufacturer = "unknown",
            brand = "Android",
            hardware = "goldfish",
            product = "sdk_x86"
        )
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.WARN, result.state)
    }

    // -----------------------------------------------------------------------
    // RootCheckEvaluator
    // -----------------------------------------------------------------------

    @Test
    fun `RootCheckEvaluator returns PASS when no su found and no test-keys`() {
        val evaluator = RootCheckEvaluator(
            suPaths = listOf("/system/xbin/su"),
            buildTags = "release-keys",
            fileExistsCheck = { false }
        )
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
    }

    @Test
    fun `RootCheckEvaluator returns FAIL when su binary found`() {
        val evaluator = RootCheckEvaluator(
            suPaths = listOf("/system/xbin/su"),
            buildTags = "release-keys",
            fileExistsCheck = { path -> path == "/system/xbin/su" }
        )
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.FAIL, result.state)
    }

    @Test
    fun `RootCheckEvaluator returns FAIL on test-keys build tag`() {
        val evaluator = RootCheckEvaluator(
            suPaths = listOf("/system/xbin/su"),
            buildTags = "test-keys",
            fileExistsCheck = { false }
        )
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.FAIL, result.state)
    }

    // -----------------------------------------------------------------------
    // BuildTypeCheckEvaluator
    // -----------------------------------------------------------------------

    @Test
    fun `BuildTypeCheckEvaluator returns PASS for release build`() {
        val evaluator = BuildTypeCheckEvaluator(isDebugBuild = false)
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
        assertEquals("build_type", result.id)
    }

    @Test
    fun `BuildTypeCheckEvaluator returns WARN for debug build`() {
        val evaluator = BuildTypeCheckEvaluator(isDebugBuild = true)
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.WARN, result.state)
    }

    // -----------------------------------------------------------------------
    // SignatureCheckEvaluator
    // -----------------------------------------------------------------------

    @Test
    fun `SignatureCheckEvaluator returns WARN when expected hash is empty`() {
        val evaluator = SignatureCheckEvaluator(
            actualSha256 = { "AA:BB:CC" },
            expectedSha256 = ""
        )
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.WARN, result.state)
        assertEquals("signature", result.id)
    }

    @Test
    fun `SignatureCheckEvaluator returns PASS when hashes match`() {
        val hash = "AB:CD:EF:12:34:56"
        val evaluator = SignatureCheckEvaluator(
            actualSha256 = { hash },
            expectedSha256 = hash
        )
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
    }

    @Test
    fun `SignatureCheckEvaluator is case-insensitive on hash comparison`() {
        val evaluator = SignatureCheckEvaluator(
            actualSha256 = { "ab:cd:ef" },
            expectedSha256 = "AB:CD:EF"
        )
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
    }

    @Test
    fun `SignatureCheckEvaluator returns FAIL on hash mismatch`() {
        val evaluator = SignatureCheckEvaluator(
            actualSha256 = { "AA:BB:CC" },
            expectedSha256 = "11:22:33"
        )
        val result = evaluator.evaluate()
        assertEquals(SecurityCheckState.FAIL, result.state)
    }
}
