package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for RestrictedMode policy (prototype-grade local gates).
 */
class RestrictedModeTest {

    private fun result(
        id: String,
        state: SecurityCheckState,
        explanation: String = "test"
    ) = SecurityCheckResult(
        id = id,
        displayName = id,
        state = state,
        explanation = explanation
    )

    @Test
    fun `no FAIL keeps restricted mode off`() {
        val decision = RestrictedMode.evaluate(
            listOf(
                result("debugger", SecurityCheckState.PASS),
                result("emulator", SecurityCheckState.WARN),
            )
        )
        assertFalse(decision.active)
        assertTrue(decision.reasons.isEmpty())
        assertEquals("Restricted mode off", decision.summary)
    }

    @Test
    fun `any FAIL activates restricted mode with reasons`() {
        val decision = RestrictedMode.evaluate(
            listOf(
                result("debugger", SecurityCheckState.PASS),
                result("root", SecurityCheckState.FAIL, "su found"),
            )
        )
        assertTrue(decision.active)
        assertEquals(1, decision.reasons.size)
        assertTrue(decision.summary.contains("Restricted mode on"))
        assertTrue(decision.reasons[0].contains("root"))
    }

    @Test
    fun `multiple FAILs collect all reasons`() {
        val decision = RestrictedMode.evaluate(
            listOf(
                result("root", SecurityCheckState.FAIL, "su"),
                result("debugger", SecurityCheckState.FAIL, "jdwp"),
                result("emulator", SecurityCheckState.WARN),
            )
        )
        assertTrue(decision.active)
        assertEquals(2, decision.reasons.size)
    }

    @Test
    fun `empty results keep restricted mode off`() {
        assertFalse(RestrictedMode.evaluate(emptyList()).active)
    }

    @Test
    fun `export report requires premium and not restricted`() {
        assertTrue(RestrictedMode.canExportReport(isPremium = true, restricted = false))
        assertFalse(RestrictedMode.canExportReport(isPremium = true, restricted = true))
        assertFalse(RestrictedMode.canExportReport(isPremium = false, restricted = false))
        assertFalse(RestrictedMode.canExportReport(isPremium = false, restricted = true))
    }

    @Test
    fun `advanced monitoring requires premium and not restricted`() {
        assertTrue(RestrictedMode.canAccessAdvancedMonitoring(isPremium = true, restricted = false))
        assertFalse(RestrictedMode.canAccessAdvancedMonitoring(isPremium = true, restricted = true))
        assertFalse(RestrictedMode.canAccessAdvancedMonitoring(isPremium = false, restricted = false))
    }

    @Test
    fun `paywall blocked while restricted`() {
        assertTrue(RestrictedMode.canLaunchPaywall(restricted = false))
        assertFalse(RestrictedMode.canLaunchPaywall(restricted = true))
    }

    @Test
    fun `EntitlementPolicy honors restrictedActive override`() {
        val premium = DemoBillingProvider(startAsPremium = true)
        val open = EntitlementPolicy(premium, restrictedActive = false)
        val locked = EntitlementPolicy(premium, restrictedActive = true)

        assertTrue(open.canExportReport())
        assertTrue(open.canAccessAdvancedMonitoring())
        assertTrue(open.canLaunchPaywall())

        assertFalse(locked.canExportReport())
        assertFalse(locked.canAccessAdvancedMonitoring())
        assertFalse(locked.canLaunchPaywall())
        assertEquals(EntitlementTier.PREMIUM, locked.currentTier())
    }
}
