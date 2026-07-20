package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [EntitlementPolicy] and [DemoBillingProvider].
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

    // -----------------------------------------------------------------------
    // EntitlementPolicy – PREMIUM tier
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
}
