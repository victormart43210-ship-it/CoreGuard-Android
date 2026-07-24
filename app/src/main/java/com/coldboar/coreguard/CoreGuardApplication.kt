package com.coldboar.coreguard

import android.app.Application
import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * Application entry point.
 *
 * Loads the native anti-tamper library as early as possible so that its
 * `JNI_OnLoad` installs the `ptrace` anti-debug guard and captures the code
 * integrity baseline before any attacker-controlled code runs. Also provisions
 * the hardware-backed master key off the main thread.
 */
class CoreGuardApplication : Application() {

    /** Lazily provisioned; exposed so security checks can report its backing. */
    val keyManager: HardwareKeyManager by lazy { HardwareKeyManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance.set(this)

        // Triggers System.loadLibrary + JNI_OnLoad (ptrace guard, baseline).
        NativeTamperGuard.ensureLoaded()

        // Provision the hardware key without blocking the main thread. A tiny
        // round-trip confirms the key is usable and records its security level.
        Thread {
            try {
                val token = keyManager.encrypt("coreguard".toByteArray())
                keyManager.decrypt(token)
                Log.i(TAG, "Master key ready (level=${keyManager.securityLevel})")
            } catch (t: Throwable) {
                Log.w(TAG, "Key provisioning failed: ${t.message}")
            }
        }.apply { isDaemon = true }.start()
    }

    companion object {
        private const val TAG = "CoreGuard"
        private val instance = AtomicReference<CoreGuardApplication?>()

        /** The running application instance, if available. */
        fun get(): CoreGuardApplication? = instance.get()
    }
}
