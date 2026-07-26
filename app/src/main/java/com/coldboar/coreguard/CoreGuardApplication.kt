package com.coldboar.coreguard

import android.app.Application
import android.util.Log
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

    /** Application-scoped work for BAE / Elite correlators (not UI). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance.set(this)

        // Triggers System.loadLibrary + JNI_OnLoad (ptrace guard, baseline).
        NativeTamperGuard.ensureLoaded()

        // Warm the billing client early so entitlement queries are ready.
        billingProvider

        // Dynamic Threat Score feeds on continuous BAE samples (on-device only).
        try {
            BehavioralAnomalyEngine.start(appScope)
            Log.i(TAG, "Behavioral anomaly engine started for Elite DTS")
        } catch (t: Throwable) {
            Log.w(TAG, "BAE start failed: ${t.message}")
        }

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
                // Michael (Hod) — register swarm peers via module façade (not UI).
                // See docs/SWARM_ARCHITECTURE.md: Kotlin swarm = background handoff;
                // microsecond RASP stays in native TamperGuard.
                SwarmModule.registerDefaultAgents(swarmCoordinator)
                Log.i(TAG, "Angelic swarm registered via SwarmModule (memory/network/process)")
            } catch (t: Throwable) {
                Log.w(TAG, "Swarm registration failed: ${t.message}")
            }
        }.apply { isDaemon = true }.start()
    }

    companion object {
        private const val TAG = "CoreGuard"
        private val instance = AtomicReference<CoreGuardApplication?>()

        /** The running application instance, if available. */
        fun get(): CoreGuardApplication? = instance.get()

        /** Non-null application instance; throws if accessed before [onCreate]. */
        fun require(): CoreGuardApplication =
            checkNotNull(instance.get()) { "CoreGuardApplication not initialized" }
    }
}
