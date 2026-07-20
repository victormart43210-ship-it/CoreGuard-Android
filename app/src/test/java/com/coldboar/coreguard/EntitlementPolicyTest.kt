package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [EntitlementPolicy], [DemoBillingProvider], [PlayBillingProvider],
 * and [SubscriptionManager].
 *
 * Tests run on JVM with no network, no secrets, and no external services.
 */
class EntitlementPolicyTest {

    // -----------------------------------------------------------------------
    // DemoBillingProvider
    // -----------------------------------------------------------------------

    @Test
    fun `DemoBillingProvider starts as non-premium by default`() {
        val provider = DemoBillingProvider()
        assertFalse(provider.isPremium())
    }

    @Test
    fun `DemoBillingProvider can start as premium`() {
        val provider = DemoBillingProvider(startAsPremium = true)
        assertTrue(provider.isPremium())
    }

    @Test
    fun `DemoBillingProvider launchPurchaseFlow returns Success and sets premium`() {
        val provider = DemoBillingProvider()
        var result: PurchaseResult? = null
        provider.launchPurchaseFlow("test_product") { result = it }

        assertTrue(result is PurchaseResult.Success)
        assertTrue(provider.isPremium())
    }

    @Test
    fun `DemoBillingProvider reset returns to initial state`() {
        val provider = DemoBillingProvider(startAsPremium = false)
        provider.launchPurchaseFlow("test_product") {}
        assertTrue(provider.isPremium())
        provider.reset()
        assertFalse(provider.isPremium())
    }

    // -----------------------------------------------------------------------
    // PlayBillingProvider placeholder — must not grant premium
    // -----------------------------------------------------------------------

    @Test
    fun `PlayBillingProvider never reports premium before integration`() {
        val provider = PlayBillingProvider()
        assertFalse(provider.isPremium())
    }

    @Test
    fun `PlayBillingProvider purchase flow returns Error not Success`() {
        val provider = PlayBillingProvider()
        var result: PurchaseResult? = null
        provider.launchPurchaseFlow("coreguard_premium_monthly") { result = it }

        assertTrue(result is PurchaseResult.Error)
        assertFalse(provider.isPremium())
    }

    // -----------------------------------------------------------------------
    // EntitlementPolicy – FREE tier
    // -----------------------------------------------------------------------

    @Test
    fun `EntitlementPolicy returns FREE when not premium`() {
        val policy = EntitlementPolicy(DemoBillingProvider(startAsPremium = false))
        assertEquals(EntitlementTier.FREE, policy.currentTier())
    }

    @Test
    fun `EntitlementPolicy canViewSecurityDashboard is true for FREE tier`() {
        val policy = EntitlementPolicy(DemoBillingProvider(startAsPremium = false))
        assertTrue(policy.canViewSecurityDashboard())
    }

    @Test
    fun `EntitlementPolicy canExportReport is false for FREE tier`() {
        val policy = EntitlementPolicy(DemoBillingProvider(startAsPremium = false))
        assertFalse(policy.canExportReport())
    }

    @Test
    fun `EntitlementPolicy canAccessAdvancedMonitoring is false for FREE tier`() {
        val policy = EntitlementPolicy(DemoBillingProvider(startAsPremium = false))
        assertFalse(policy.canAccessAdvancedMonitoring())
    }

    @Test
    fun `EntitlementPolicy labels demo free source honestly`() {
        val policy = EntitlementPolicy(DemoBillingProvider(startAsPremium = false))
        assertEquals("demo_free", policy.entitlementSourceLabel())
    }

    // -----------------------------------------------------------------------
    // EntitlementPolicy – PREMIUM tier (demo)
    // -----------------------------------------------------------------------

    @Test
    fun `EntitlementPolicy returns PREMIUM when premium`() {
        val policy = EntitlementPolicy(DemoBillingProvider(startAsPremium = true))
        assertEquals(EntitlementTier.PREMIUM, policy.currentTier())
    }

    @Test
    fun `EntitlementPolicy canViewSecurityDashboard is true for PREMIUM tier`() {
        val policy = EntitlementPolicy(DemoBillingProvider(startAsPremium = true))
        assertTrue(policy.canViewSecurityDashboard())
    }

    @Test
    fun `EntitlementPolicy canExportReport is true for PREMIUM tier`() {
        val policy = EntitlementPolicy(DemoBillingProvider(startAsPremium = true))
        assertTrue(policy.canExportReport())
    }

    @Test
    fun `EntitlementPolicy canAccessAdvancedMonitoring is true for PREMIUM tier`() {
        val policy = EntitlementPolicy(DemoBillingProvider(startAsPremium = true))
        assertTrue(policy.canAccessAdvancedMonitoring())
    }

    @Test
    fun `EntitlementPolicy labels demo premium source honestly`() {
        val policy = EntitlementPolicy(DemoBillingProvider(startAsPremium = true))
        assertEquals("demo_premium", policy.entitlementSourceLabel())
    }

    @Test
    fun `EntitlementPolicy labels PlayBillingProvider as play_free when not premium`() {
        val policy = EntitlementPolicy(PlayBillingProvider())
        assertEquals("play_free", policy.entitlementSourceLabel())
    }

    // -----------------------------------------------------------------------
    // SubscriptionManager paywall guard
    // -----------------------------------------------------------------------

    @Test
    fun `SubscriptionManager isPremium mirrors billing provider`() {
        val billing = DemoBillingProvider(startAsPremium = false)
        val sm = SubscriptionManager(billing)
        assertFalse(sm.isPremium())

        billing.launchPurchaseFlow("test") {}
        assertTrue(sm.isPremium())
    }

    @Test
    fun `SubscriptionManager prevents duplicate paywall launches`() {
        var launchCount = 0
        val sm = SubscriptionManager(DemoBillingProvider())

        assertTrue(sm.launchPaywallIfNotShowing { launchCount++ })
        assertFalse(sm.launchPaywallIfNotShowing { launchCount++ })
        assertEquals(1, launchCount)
        assertTrue(sm.isPaywallVisible())

        sm.onPaywallDismissed()
        assertFalse(sm.isPaywallVisible())
        assertTrue(sm.launchPaywallIfNotShowing { launchCount++ })
        assertEquals(2, launchCount)
    }
}
