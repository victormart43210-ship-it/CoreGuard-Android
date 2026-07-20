package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the native-backed anti-tamper evaluators. Each evaluator's raw
 * signals are injected, so the PASS/WARN/FAIL classification is verified on the
 * JVM without loading the native library.
 */
class TamperEvaluatorTest {

    // ----------------------------------------------------------------- Frida
    @Test
    fun `Frida clean when no port and no thread`() {
        val result = FridaDetectionEvaluator(portOpen = { false }, suspiciousThread = { "" }).evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
        assertEquals("frida", result.id)
    }

    @Test
    fun `Frida FAIL when server port open`() {
        val result = FridaDetectionEvaluator(portOpen = { true }, suspiciousThread = { "" }).evaluate()
        assertEquals(SecurityCheckState.FAIL, result.state)
        assertTrue(result.explanation.contains("port", ignoreCase = true))
    }

    @Test
    fun `Frida FAIL when injected thread present`() {
        val result = FridaDetectionEvaluator(portOpen = { false }, suspiciousThread = { "gum-js-loop" }).evaluate()
        assertEquals(SecurityCheckState.FAIL, result.state)
        assertTrue(result.explanation.contains("gum-js-loop"))
    }

    // -------------------------------------------------------- Native debugger
    @Test
    fun `Native debugger FAIL when tracer attached`() {
        val result = NativeDebuggerEvaluator(tracerPid = { 4242 }, ptraceProtected = { true }).evaluate()
        assertEquals(SecurityCheckState.FAIL, result.state)
        assertTrue(result.explanation.contains("4242"))
    }

    @Test
    fun `Native debugger PASS when guard active and no tracer`() {
        val result = NativeDebuggerEvaluator(tracerPid = { 0 }, ptraceProtected = { true }).evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
    }

    @Test
    fun `Native debugger WARN when guard not engaged`() {
        val result = NativeDebuggerEvaluator(tracerPid = { 0 }, ptraceProtected = { false }).evaluate()
        assertEquals(SecurityCheckState.WARN, result.state)
    }

    // ---------------------------------------------------------- Hook detection
    @Test
    fun `Hook detection PASS when no hook library mapped`() {
        val result = HookDetectionEvaluator(hookedLibrary = { "" }).evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
    }

    @Test
    fun `Hook detection FAIL when frida gadget mapped`() {
        val result = HookDetectionEvaluator(hookedLibrary = { "/data/local/tmp/libgadget.so" }).evaluate()
        assertEquals(SecurityCheckState.FAIL, result.state)
        assertTrue(result.explanation.contains("libgadget.so"))
    }

    // --------------------------------------------------------- Mount integrity
    @Test
    fun `Mount integrity PASS when no root mount`() {
        val result = MountIntegrityEvaluator(rootMount = { "" }).evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
    }

    @Test
    fun `Mount integrity FAIL when magisk mount present`() {
        val result = MountIntegrityEvaluator(rootMount = { "magisk /sbin/.magisk tmpfs rw" }).evaluate()
        assertEquals(SecurityCheckState.FAIL, result.state)
    }

    // -------------------------------------------------------- Memory integrity
    @Test
    fun `Memory integrity PASS when text intact`() {
        val result = MemoryIntegrityEvaluator(baselineReady = { true }, textIntact = { true }).evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
    }

    @Test
    fun `Memory integrity FAIL when text modified`() {
        val result = MemoryIntegrityEvaluator(baselineReady = { true }, textIntact = { false }).evaluate()
        assertEquals(SecurityCheckState.FAIL, result.state)
    }

    @Test
    fun `Memory integrity WARN when baseline missing`() {
        val result = MemoryIntegrityEvaluator(baselineReady = { false }, textIntact = { true }).evaluate()
        assertEquals(SecurityCheckState.WARN, result.state)
    }

    // ------------------------------------------------------------- StrongBox
    @Test
    fun `StrongBox PASS on strongbox backing`() {
        val result = StrongBoxCheckEvaluator(level = { KeySecurityLevel.STRONGBOX }).evaluate()
        assertEquals(SecurityCheckState.PASS, result.state)
    }

    @Test
    fun `StrongBox WARN on TEE backing`() {
        val result = StrongBoxCheckEvaluator(level = { KeySecurityLevel.TEE }).evaluate()
        assertEquals(SecurityCheckState.WARN, result.state)
    }

    @Test
    fun `StrongBox FAIL on software backing`() {
        val result = StrongBoxCheckEvaluator(level = { KeySecurityLevel.SOFTWARE }).evaluate()
        assertEquals(SecurityCheckState.FAIL, result.state)
    }
}
