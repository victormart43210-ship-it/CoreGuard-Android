package com.coldboar.coreguard.hardening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHardeningGuideTest {

    @Test
    fun `guide contains backup first tip and keeps screen lock guardrail`() {
        assertNotNull(DeviceHardeningGuide.tip("backup_first"))
        val lock = DeviceHardeningGuide.tip("keep_screen_lock")
        assertNotNull(lock)
        assertTrue(lock!!.isSecurityGuardrail)
        assertEquals(DeviceHardeningGuide.Impact.SECURITY, lock.impact)
    }

    @Test
    fun `tips are non empty and uniquely identified`() {
        assertTrue(DeviceHardeningGuide.tips.size >= 6)
        val ids = DeviceHardeningGuide.tips.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        DeviceHardeningGuide.tips.forEach { tip ->
            assertTrue(tip.title.isNotBlank())
            assertTrue(tip.summary.isNotBlank())
            assertTrue(tip.steps.isNotEmpty())
        }
    }

    @Test
    fun `does not recommend auto login bypass`() {
        val joined = DeviceHardeningGuide.tips.joinToString(" ") { "${it.title} ${it.summary} ${it.steps}" }
            .lowercase()
        assertFalse(joined.contains("netplwiz"))
        assertFalse(joined.contains("skip the lock"))
        assertTrue(joined.contains("screen lock") || joined.contains("keep screen lock"))
    }

    @Test
    fun `intent mapping covers actionable deep links`() {
        assertNotNull(HardeningSettingsIntents.intentFor(DeviceHardeningGuide.SettingsDeepLink.APPS))
        assertNotNull(HardeningSettingsIntents.intentFor(DeviceHardeningGuide.SettingsDeepLink.STORAGE))
        assertNotNull(HardeningSettingsIntents.intentFor(DeviceHardeningGuide.SettingsDeepLink.SECURITY))
        assertNull(HardeningSettingsIntents.intentFor(DeviceHardeningGuide.SettingsDeepLink.NONE))
    }
}
