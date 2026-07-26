package com.coldboar.coreguard.quilla

import android.content.Context
import android.os.Build

/**
 * Quilla Emulator Gate — host scripts boot the AVD; on-device Quilla reports
 * whether she is running inside an emulator and can smoke the quantum correlator.
 *
 * This does **not** start QEMU from inside the APK (apps cannot launch the SDK
 * emulator). Use `./scripts/quilla-emulator-tests.sh` on a workstation/CI host.
 */
object QuillaEmulatorGate {

    const val HOST_SCRIPT = "./scripts/quilla-emulator-tests.sh"
    /** Prefer lean ATD; fall back to google_apis AVD in older hosts. */
    const val AVD_NAME = "CoreGuard_ATD35"

    data class Status(
        val packageName: String,
        val isEmulator: Boolean,
        val fingerprint: String,
        val quantumSeal: String?,
        val summary: String
    )

    fun isEmulatorEnvironment(): Boolean {
        val fp = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return fp.contains("generic") ||
            fp.contains("emulator") ||
            model.contains("emulator") ||
            model.contains("sdk_gphone") ||
            product.contains("sdk") ||
            product.contains("emulator") ||
            hardware.contains("ranchu") ||
            hardware.contains("goldfish") ||
            manufacturer.contains("genymotion")
    }

    fun probe(context: Context, runQuantumSmoke: Boolean = true): Status {
        val emu = isEmulatorEnvironment()
        val quantum = if (runQuantumSmoke) {
            QuillaQuantumCorrelate.runCircuit(
                packageName = context.packageName,
                iocHit = false,
                packageIocHit = false,
                dynamicCode = false,
                root = false,
                untrustedNetwork = false,
                classicalConfidence = 0.40f
            )
        } else {
            null
        }
        val where = if (emu) "inside AVD/emulator silicon" else "on physical (or unknown) device"
        val summary = buildString {
            append("Quilla Emulator Gate · ")
            append(where)
            append(" · fingerprint=")
            append(Build.FINGERPRINT.take(48))
            if (quantum != null) {
                append(" · quantum=")
                append(quantum.seal)
            }
            append('\n')
            append("Host deadline harness: $HOST_SCRIPT (AVD $AVD_NAME). ")
            append("On-device Quilla cannot spawn the SDK emulator — the host script does.")
        }
        return Status(
            packageName = context.packageName,
            isEmulator = emu,
            fingerprint = Build.FINGERPRINT,
            quantumSeal = quantum?.seal,
            summary = summary
        )
    }
}
