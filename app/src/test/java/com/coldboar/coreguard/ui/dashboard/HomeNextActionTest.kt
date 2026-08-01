package com.coldboar.coreguard.ui.dashboard

import com.coldboar.coreguard.elite.DynamicThreatEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeNextActionTest {

    @Test
    fun `no scan prompts privacy check first`() {
        val action = resolveNextAction(
            hasScan = false,
            shieldOn = false,
            attentionCount = 2,
            dtsBand = DynamicThreatEngine.Band.CRITICAL
        )
        assertEquals(HomeNextActionKind.SCAN, action.kind)
    }

    @Test
    fun `attention findings take priority after a scan`() {
        val action = resolveNextAction(
            hasScan = true,
            shieldOn = false,
            attentionCount = 1,
            dtsBand = DynamicThreatEngine.Band.CLEAR
        )
        assertEquals(HomeNextActionKind.REVIEW_FINDINGS, action.kind)
    }

    @Test
    fun `elevated dts prompts journal when no attention rows`() {
        val action = resolveNextAction(
            hasScan = true,
            shieldOn = true,
            attentionCount = 0,
            dtsBand = DynamicThreatEngine.Band.ELEVATED
        )
        assertEquals(HomeNextActionKind.REVIEW_DTS, action.kind)
    }

    @Test
    fun `shield off is next when device looks otherwise steady`() {
        val action = resolveNextAction(
            hasScan = true,
            shieldOn = false,
            attentionCount = 0,
            dtsBand = DynamicThreatEngine.Band.CLEAR
        )
        assertEquals(HomeNextActionKind.SHIELD, action.kind)
    }

    @Test
    fun `steady device suggests another check`() {
        val action = resolveNextAction(
            hasScan = true,
            shieldOn = true,
            attentionCount = 0,
            dtsBand = DynamicThreatEngine.Band.CLEAR
        )
        assertEquals(HomeNextActionKind.MAINTAIN, action.kind)
    }
}
