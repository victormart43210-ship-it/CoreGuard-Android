package com.coldboar.coreguard.quilla

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.coldboar.coreguard.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator smoke: MainActivity launches under the debug applicationId.
 * Run via `./scripts/quilla-emulator-tests.sh` or
 * `./gradlew -Pcoreguard.androidBuild=true :app:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityLaunchTest {

    @Test
    fun launchesMainActivityOnEmulatorOrDevice() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.coldboar.coreguard.debug", appContext.packageName)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.hasWindowFocus() || !activity.isFinishing)
            }
        }
    }
}
