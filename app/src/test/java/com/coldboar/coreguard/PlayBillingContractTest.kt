package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests that keep Play Store product IDs and entitlement policy aligned.
 * Full BillingClient integration requires an instrumented device / Play environment.
 */
class PlayBillingContractTest {

    @Test
    fun `BillingProvider owns the single authoritative premium SKU`() {
        assertEquals("coreguard_premium_monthly", BillingProvider.PREMIUM_PRODUCT_ID)
    }

    @Test
    fun `PlayBilling and EntitlementPolicy alias the same SKU`() {
        assertEquals(BillingProvider.PREMIUM_PRODUCT_ID, PlayBillingProvider.PREMIUM_PRODUCT_ID)
        assertEquals(BillingProvider.PREMIUM_PRODUCT_ID, EntitlementPolicy.PREMIUM_PRODUCT_ID)
    }

    @Test
    fun `paywall and settings purchase flows must use the authoritative SKU`() {
        // PaywallActivity / SettingsScreen launchPurchaseFlow(BillingProvider.PREMIUM_PRODUCT_ID)
        // or the EntitlementPolicy alias — both must equal the Play Console id.
        assertEquals("coreguard_premium_monthly", EntitlementPolicy.PREMIUM_PRODUCT_ID)
    }

    @Test
    fun `export remains premium-gated`() {
        val free = EntitlementPolicy(DemoBillingProvider(startAsPremium = false))
        val premium = EntitlementPolicy(DemoBillingProvider(startAsPremium = true))
        assertFalse(free.canExportReport())
        assertTrue(premium.canExportReport())
    }

    @Test
    fun `basic Quilla recommendations flag is premium-only while free keeps core tools`() {
        val free = EntitlementPolicy(DemoBillingProvider(startAsPremium = false))
        assertTrue(free.canViewSecurityDashboard())
        assertFalse(free.canUseQuillaRecommendations())
        assertFalse(free.canRefreshThreatSignatures())
    }

    @Test
    fun `premium timeline depth matches ScanHistoryStore capacity`() {
        assertEquals(
            EntitlementPolicy.PREMIUM_TIMELINE_ENTRIES,
            com.coldboar.coreguard.mvt.ScanHistoryStore.MAX_ENTRIES
        )
    }
}
