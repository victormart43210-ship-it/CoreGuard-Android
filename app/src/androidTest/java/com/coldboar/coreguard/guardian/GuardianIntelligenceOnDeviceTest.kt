package com.coldboar.coreguard.guardian

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke for Guardian Intelligence façade.
 * Run via quilla-emulator-tests or connectedDebugAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class GuardianIntelligenceOnDeviceTest {

    @Test
    fun refreshIntelligenceProducesFindingsAndPulse() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.coldboar.coreguard.debug", context.packageName)

        val snap = GuardianModule.refreshIntelligence(context)
        assertTrue(snap.findings.isNotEmpty())
        assertNotNull(snap.state)
        assertTrue(snap.hardening.isNotEmpty())
        assertNotNull(snap.verification.packageName)
        assertTrue(GuardianModule.bookOfChanges(context).chainValid())
    }
}
