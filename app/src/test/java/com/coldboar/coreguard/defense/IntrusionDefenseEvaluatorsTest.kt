package com.coldboar.coreguard.defense

import com.coldboar.coreguard.SecurityCheckState
import org.junit.Assert.assertEquals
import org.junit.Test

class IntrusionDefenseEvaluatorsTest {

    @Test
    fun `overlay pass when none`() {
        val r = OverlayAbuseEvaluator(overlayAppCount = { 0 }).evaluate()
        assertEquals("overlay_abuse", r.id)
        assertEquals(SecurityCheckState.PASS, r.state)
    }

    @Test
    fun `overlay fail when many`() {
        val r = OverlayAbuseEvaluator(
            overlayAppCount = { 5 },
            sampleLabels = { listOf("com.evil.overlay") }
        ).evaluate()
        assertEquals(SecurityCheckState.FAIL, r.state)
        assertTrueContainsTrojanHint(r.explanation)
    }

    @Test
    fun `accessibility warn on one third party`() {
        val r = AccessibilityAbuseEvaluator(
            thirdPartyServiceCount = { 1 },
            sampleLabels = { listOf("com.helper.app") }
        ).evaluate()
        assertEquals(SecurityCheckState.WARN, r.state)
    }

    @Test
    fun `sideload fail when non-store and unknown installs`() {
        val r = SideloadRiskEvaluator(
            installedFromStore = { false },
            canInstallUnknown = { true },
            installerLabel = { "com.dropper.installer" }
        ).evaluate()
        assertEquals(SecurityCheckState.FAIL, r.state)
    }

    private fun assertTrueContainsTrojanHint(text: String) {
        org.junit.Assert.assertTrue(text.lowercase().contains("trojan") || text.lowercase().contains("overlay"))
    }
}
