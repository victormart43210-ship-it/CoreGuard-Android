package com.coldboar.coreguard

/**
 * Result of trying to acquire one native security signal.
 *
 * The native layer can end up in three different states, and the first two must
 * never be confused with the third:
 *
 *  - the probe completed and observed nothing suspicious (`Available` with a
 *    clean value),
 *  - the probe completed and observed something suspicious (`Available` with a
 *    suspicious value),
 *  - the probe never completed at all (`Unavailable`).
 *
 * Before this type existed the bridge collapsed the third case onto a benign
 * primitive (`0`, `false`, `""`, `true`), so a device where `/proc` was
 * unreadable looked exactly like a device that had been verified clean. Callers
 * are now forced to handle [Unavailable] explicitly.
 */
sealed interface NativeAcquisition<out T> {

    /** The probe completed. [value] is real evidence and may be clean or suspicious. */
    data class Available<T>(val value: T) : NativeAcquisition<T>

    /**
     * The probe did not complete, so there is no evidence either way.
     *
     * [reason] is a closed categorical code. It deliberately carries no
     * exception text, path, or other raw native detail so it is safe to surface
     * in UI, logs and telemetry.
     */
    data class Unavailable(val reason: NativeUnavailableReason) : NativeAcquisition<Nothing>
}

/** Why a native acquisition could not be completed. */
enum class NativeUnavailableReason {
    /** `libtamperguard.so` is not loaded, so no probe can run. */
    LIBRARY_UNAVAILABLE,

    /** The JNI call itself failed or returned an unparseable contract string. */
    JNI_CALL_FAILED,

    /** The operating-system source (e.g. a `/proc` entry) could not be read. */
    SOURCE_READ_FAILED,

    /** No usable code-integrity baseline was captured, so nothing can be compared. */
    BASELINE_UNAVAILABLE,
}

/** The acquired value, or `null` when the probe never completed. */
fun <T> NativeAcquisition<T>.valueOrNull(): T? = when (this) {
    is NativeAcquisition.Available -> value
    is NativeAcquisition.Unavailable -> null
}

/** The unavailable reason, or `null` when the probe completed. */
fun <T> NativeAcquisition<T>.reasonOrNull(): NativeUnavailableReason? = when (this) {
    is NativeAcquisition.Available -> null
    is NativeAcquisition.Unavailable -> reason
}

/**
 * Factual, human-readable phrasing for an unavailable probe.
 *
 * Never claims the device is clean, safe, protected, unhooked or verified —
 * absence of evidence is not evidence of protection.
 */
fun NativeUnavailableReason.explain(checkLabel: String): String {
    val cause = when (this) {
        NativeUnavailableReason.LIBRARY_UNAVAILABLE ->
            "the native security library is not loaded on this device"
        NativeUnavailableReason.JNI_CALL_FAILED ->
            "the native call did not return a usable result"
        NativeUnavailableReason.SOURCE_READ_FAILED ->
            "Android did not expose the required system information to this app"
        NativeUnavailableReason.BASELINE_UNAVAILABLE ->
            "no load-time integrity baseline was captured"
    }
    return "$checkLabel could not be completed: $cause. This result is unverifiable."
}
