package com.coldboar.coreguard

import android.app.Application
import android.util.Log
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeAssets
import com.coldboar.coreguard.swarm.MemoryIntegrityAgent
import com.coldboar.coreguard.swarm.NetworkMonitorAgent
import com.coldboar.coreguard.swarm.ProcessLineageAgent
import com.coldboar.coreguard.swarm.SwarmCoordinator
import com.coreguard.android.data.local.QuillaDatabase
import com.coreguard.security.telemetry.TelemetryBridge
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

    /**
     * Shared Google Play Billing provider for the whole process.
     * Activities must call [PlayBillingProvider.attach] / [PlayBillingProvider.detach]
     * around their lifecycle; purchase UI requires an attached Activity.
     */
    val billingProvider: PlayBillingProvider by lazy { PlayBillingProvider(this) }

    /** Room database for Quilla Intelligence threat hypotheses. */
    val quillaDatabase: QuillaDatabase by lazy { QuillaDatabase.getInstance(this) }

    /**
     * Michael's swarm — on-device RASP peer agents (Frida/hooks/network/process).
     * Started once after native tamper baseline; never claims supernatural detection.
     */
    val swarmCoordinator: SwarmCoordinator by lazy { SwarmCoordinator() }

    override fun onCreate() {
        super.onCreate()
        instance.set(this)

        val instrumented = isUnderInstrumentation()
        if (instrumented) {
            Log.i(TAG, "Instrumented test process — deferred warm-up (Quilla Emulator Gate)")
        }

        // Never block Application.onCreate on software AVDs (TCG/no-KVM ANRs).
        // Native ptrace baseline + billing warm on a daemon thread.
        Thread {
            if (!instrumented) {
                try {
                    NativeTamperGuard.ensureLoaded()
                } catch (t: Throwable) {
                    Log.w(TAG, "Native tamper load failed: ${t.message}")
                }
                try {
                    billingProvider
                } catch (t: Throwable) {
                    Log.w(TAG, "Billing warm-up failed: ${t.message}")
                }
            } else {
                Log.i(TAG, "Skipping native/billing preload under instrumentation")
            }
            try {
                val token = keyManager.encrypt("coreguard".toByteArray())
                keyManager.decrypt(token)
                Log.i(TAG, "Master key ready (level=${keyManager.securityLevel})")
            } catch (t: Throwable) {
                Log.w(TAG, "Key provisioning failed: ${t.message}")
            }
            if (instrumented) {
                Log.i(TAG, "Skipping knowledge/swarm/telemetry preload under instrumentation")
                return@Thread
            }
            try {
                CyberKnowledgeAssets.ensureLoaded(this@CoreGuardApplication)
            } catch (t: Throwable) {
                Log.w(TAG, "Quilla knowledge preload failed: ${t.message}")
            }
            try {
                // Open Room so hypothesis writes from the correlation engine do not
                // pay first-open latency on the UI path.
                quillaDatabase.quillaLearningDao()
            } catch (t: Throwable) {
                Log.w(TAG, "Quilla database warm-up failed: ${t.message}")
            }
            try {
                TelemetryBridge.init(this@CoreGuardApplication)
                TelemetryBridge.emitHeartbeat(mapOf("boot" to "warm"))
            } catch (t: Throwable) {
                Log.w(TAG, "Telemetry bridge init failed: ${t.message}")
            }
            try {
                // Michael (Hod) — register swarm agents once for collaborative RASP watch.
                val swarm = swarmCoordinator
                swarm.register(MemoryIntegrityAgent())
                swarm.register(NetworkMonitorAgent())
                swarm.register(ProcessLineageAgent())
                Log.i(TAG, "Angelic swarm registered (Michael · memory/network/process)")
            } catch (t: Throwable) {
                Log.w(TAG, "Swarm registration failed: ${t.message}")
            }
        }.apply { isDaemon = true }.start()
    }

    companion object {
        private const val TAG = "CoreGuard"
        private val instance = AtomicReference<CoreGuardApplication?>()

        /**
         * True when a non-default Instrumentation is attached (AndroidJUnitRunner).
         * Prefer ActivityThread over Class.forName — test APK classes may not be
         * visible yet during early Application.onCreate on slow emulators.
         */
        fun isUnderInstrumentation(): Boolean {
            return try {
                val atClass = Class.forName("android.app.ActivityThread")
                val current = atClass.getMethod("currentActivityThread").invoke(null) ?: return false
                val instr = atClass.getMethod("getInstrumentation").invoke(current) ?: return false
                instr.javaClass.name != "android.app.Instrumentation"
            } catch (_: Throwable) {
                try {
                    Class.forName("androidx.test.platform.app.InstrumentationRegistry")
                    true
                } catch (_: ClassNotFoundException) {
                    false
                }
            }
        }

        /** The running application instance, if available. */
        fun get(): CoreGuardApplication? = instance.get()

        /** Non-null application instance; throws if accessed before [onCreate]. */
        fun require(): CoreGuardApplication =
            checkNotNull(instance.get()) { "CoreGuardApplication not initialized" }
    }
}
