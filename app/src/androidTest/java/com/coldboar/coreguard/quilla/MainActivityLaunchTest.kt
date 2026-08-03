package com.coldboar.coreguard.quilla

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.coldboar.coreguard.CoreGuardApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator smoke without forcing a full Compose ActivityScenario (software
 * GPU AVDs often ANR on first frame). Full UI launch is covered by
 * `scripts/smoke-adb.sh` after the harness installs the APK.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityLaunchTest {

    @Test
    fun debugPackageAndApplicationReadyOnEmulator() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.coldboar.coreguard.debug", appContext.packageName)
        val app = appContext.applicationContext
        assertTrue(
            "Expected CoreGuardApplication, got ${app.javaClass.name}",
            app is CoreGuardApplication
        )
        assertTrue(CoreGuardApplication.isUnderInstrumentation())
    }
}
