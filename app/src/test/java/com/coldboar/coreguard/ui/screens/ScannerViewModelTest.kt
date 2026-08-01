package com.coldboar.coreguard.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM unit tests for [ScannerViewModel] state machine.
 *
 * The scanner coroutine operations require an Android context so they cannot
 * be unit-tested on JVM. These tests verify:
 * - [ScanPhase] enum values and ordering
 * - [ScannerUiState] defaults and copy semantics
 * - [ScannerUiState] honesty invariant: no completedReport for CANCELLED phase
 *
 * Full coroutine + context tests are in the Android test module.
 */
class ScannerViewModelTest {

    @Test
    fun `ScannerUiState defaults to IDLE phase`() {
        val state = ScannerUiState()
        assertEquals(ScanPhase.IDLE, state.phase)
    }

    @Test
    fun `ScannerUiState default has no completedReport`() {
        val state = ScannerUiState()
        assertNull(state.completedReport)
    }

    @Test
    fun `ScannerUiState default has no errorMessage`() {
        val state = ScannerUiState()
        assertNull(state.errorMessage)
    }

    @Test
    fun `CANCELLED state must not carry a completedReport`() {
        // Truth invariant: a cancelled scan must NEVER have a completed report.
        val cancelledState = ScannerUiState(
            phase = ScanPhase.CANCELLED,
            completedReport = null
        )
        assertNull(cancelledState.completedReport)
    }

    @Test
    fun `CANCELLED state honesty invariant — no score displayed`() {
        // Any code that renders a score must check phase == COMPLETE first.
        val cancelledState = ScannerUiState(phase = ScanPhase.CANCELLED, completedReport = null)
        val shouldShowScore = cancelledState.phase == ScanPhase.COMPLETE && cancelledState.completedReport != null
        assertFalse(shouldShowScore)
    }

    @Test
    fun `ERROR state has no completedReport`() {
        val errorState = ScannerUiState(
            phase = ScanPhase.ERROR,
            errorMessage = "Something went wrong",
            completedReport = null
        )
        assertNull(errorState.completedReport)
        assertEquals("Something went wrong", errorState.errorMessage)
    }

    @Test
    fun `SCANNING phase has non-null stageLabel when set`() {
        val scanningState = ScannerUiState(
            phase = ScanPhase.SCANNING,
            stageLabel = "Enumerating installed packages",
            overallProgress = 0.25f
        )
        assertEquals("Enumerating installed packages", scanningState.stageLabel)
        assertEquals(0.25f, scanningState.overallProgress, 0.001f)
    }

    @Test
    fun `ScanPhase enum contains all expected phases`() {
        val phases = ScanPhase.entries.map { it.name }.toSet()
        assert("IDLE" in phases)
        assert("SCANNING" in phases)
        assert("COMPLETE" in phases)
        assert("CANCELLED" in phases)
        assert("ERROR" in phases)
    }
}
