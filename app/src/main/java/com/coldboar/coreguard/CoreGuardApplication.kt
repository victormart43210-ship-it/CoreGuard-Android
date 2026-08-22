package com.coldboar.coreguard

import android.app.Application
import android.util.Log
import android.os.PowerManager
import com.coldboar.coreguard.BehavioralAnomalyEngine
import com.coldboar.coreguard.monitor.SecurityPulseWorker
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeAssets
import com.coldboar.coreguard.swarm.SwarmCoordinator
import com.coldboar.coreguard.swarm.SwarmModule
import com.coreguard.android.data.local.QuillaDatabase
import com.coreguard.security.telemetry.TelemetryBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicReference

/**
 * Application entry point.
 *
 * Loads the native anti-tamper library as early as possible so that its
 * `JNI_OnLoad` captures the code-integrity baseline before any
 * attacker-controlled code runs. Debugger status is observed passively via
 * `/proc/self/status` TracerPid (no self-`PTRACE_TRACEME`). Also provisions
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

    /** Application-scoped work for BAE / Elite correlators (not UI). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance.set(this)

        val instrumented = isUnderInstrumentation()
        if (instrumented) {
            Log.i(TAG, "Instrumented test process — deferred warm-up (Quilla Emulator Gate)")
        }

        // Never block Application.onCreate on software AVDs (TCG/no-KVM ANRs).
        // Native tamper baseline + billing warm on a daemon thread.
        Thread({
            try {
                if (!instrumented) {
                    NativeTamperGuard.ensureLoaded()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Native tamper load failed: ${t.message}")
            }

            if (!instrumented) {
                // Warm the billing client early so entitlement queries are ready.
                try {
                    billingProvider
                } catch (t: Throwable) {
                    Log.w(TAG, "Billing warm-up failed: ${t.message}")
                }

                // Dynamic Threat Score feeds on BAE samples (on-device only).
                // Stretch the interval in system power-save to reduce battery cost.
                try {
                    val powerSave = (getSystemService(POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
                    val interval = if (powerSave) {
                        BehavioralAnomalyEngine.POWER_SAVE_INTERVAL_MS
                    } else {
                        BehavioralAnomalyEngine.DEFAULT_INTERVAL_MS
                    }
                    BehavioralAnomalyEngine.start(appScope, intervalMs = interval)
                    Log.i(TAG, "Behavioral anomaly engine started intervalMs=$interval")
                } catch (t: Throwable) {
                    Log.w(TAG, "BAE start failed: ${t.message}")
                }

                // Hourly Guardian Score pulse via WorkManager (battery-not-low constraint).
                try {
                    SecurityPulseWorker.schedule(this@CoreGuardApplication)
                } catch (t: Throwable) {
                    Log.w(TAG, "Security pulse schedule failed: ${t.message}")
                }
            } else {
                Log.i(TAG, "Skipping billing/BAE/pulse preload under instrumentation")
            }

            try {
                val token = keyManager.encrypt("coreguard".toByteArray())
                keyManager.decrypt(token)
                Log.i(TAG, "Master key ready (level=${keyManager.securityLevel})")
            } catch (t: Throwable) {
                Log.w(TAG, "Key provisioning failed: ${t.message}")
            }

            if (!instrumented) {
                try {
                    CyberKnowledgeAssets.ensureLoaded(this@CoreGuardApplication)
                } catch (t: Throwable) {
                    Log.w(TAG, "Quilla knowledge preload failed: ${t.message}")
                }
            } else {
                Log.i(TAG, "Skipping knowledge preload under instrumentation")
            }

            try {
                // Open Room so hypothesis writes from the correlation engine do not
                // pay first-open latency on the UI path.
                quillaDatabase.quillaLearningDao()
            } catch (t: Throwable) {
                Log.w(TAG, "Quilla database warm-up failed: ${t.message}")
            }

            if (!instrumented) {
                try {
                    TelemetryBridge.init(this@CoreGuardApplication)
                    TelemetryBridge.emitHeartbeat(mapOf("boot" to "warm"))
                } catch (t: Throwable) {
                    Log.w(TAG, "Telemetry bridge init failed: ${t.message}")
                }

                try {
                    // Michael (Hod) — register swarm peers via module façade (not UI).
                    // See docs/SWARM_ARCHITECTURE.md: Kotlin swarm = background handoff;
                    // microsecond RASP stays in native TamperGuard.
                    SwarmModule.registerDefaultAgents(swarmCoordinator)
                    Log.i(TAG, "Angelic swarm registered via SwarmModule (memory/network/process)")
                } catch (t: Throwable) {
                    Log.w(TAG, "Swarm registration failed: ${t.message}")
                }
            } else {
                Log.i(TAG, "Skipping telemetry/swarm preload under instrumentation")
            }
        }, "CoreGuard-WarmUp").apply { isDaemon = true }.start()
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
