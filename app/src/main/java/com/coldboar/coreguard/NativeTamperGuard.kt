package com.coldboar.coreguard

import android.util.Log

/**
 * Kotlin bridge to the native `libtamperguard.so` anti-tamper primitives.
 *
 * The heavy lifting lives in C++ (see `src/main/cpp/tamperguard.cpp`) because
 * native code is much harder to trace and hook than Kotlin/Java.
 *
 * Every accessor returns a [NativeAcquisition] rather than a bare primitive.
 * That is a security requirement, not a style choice: a benign primitive cannot
 * distinguish "probe completed and found nothing" from "probe never ran", and
 * treating the second as the first silently manufactures a clean verdict on any
 * device where the library is missing or `/proc` is unreadable.
 *
 * Each probe is a single JNI call that carries its own status, so a caller can
 * never observe a status from one acquisition and evidence from another.
 *
 * Loading the library triggers `JNI_OnLoad`, which captures the code-integrity
 * baseline as early as possible. Debugger status is derived passively from
 * `/proc/self/status` so the app never creates its own tracer false positive.
 * [ensureLoaded] should be called from `Application.onCreate`.
 */
object NativeTamperGuard {

    private const val TAG = "TamperGuard"

    /** Separator between the status tag and the payload in the JNI contract. */
    private const val SEPARATOR = '|'
    private const val TAG_OK = "OK"
    private const val TAG_UNAVAILABLE = "UNAVAILABLE"

    /** True once the native library has been successfully loaded. */
    @Volatile
    var isAvailable: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("tamperguard")
            isAvailable = true
        } catch (t: Throwable) {
            // Message is intentionally not propagated into the public contract.
            Log.w(TAG, "Native tamper guard unavailable: ${t.message}")
            isAvailable = false
        }
    }

    /**
     * No-op accessor whose only purpose is to guarantee the object (and thus the
     * native library and its `JNI_OnLoad`) has been initialised. Safe to call
     * repeatedly.
     */
    fun ensureLoaded(): Boolean = isAvailable

    /**
     * TracerPid from `/proc/self/status`. `0` means no debugger/tracer was
     * observed; an unreadable status file is [NativeAcquisition.Unavailable],
     * never `0`.
     */
    fun tracerPid(): NativeAcquisition<Int> =
        acquire({ nativeTracerProbe() }) { it.toIntOrNull()?.coerceAtLeast(0) }

    /** Whether a Frida server/gadget is listening on a known loopback port. */
    fun fridaPortOpen(): NativeAcquisition<Boolean> =
        acquire({ nativeFridaPortProbe() }, ::parseBoolean)

    /** Name of a Frida-injected thread in this process, or an empty string. */
    fun suspiciousFridaThread(): NativeAcquisition<String> =
        acquire({ nativeFridaThreadProbe() }) { it }

    /** Path of a hooking-framework library mapped into this process, or "". */
    fun hookedLibraryPath(): NativeAcquisition<String> =
        acquire({ nativeHookedLibraryProbe() }) { it }

    /** A Magisk/KernelSU style root mount entry, or an empty string. */
    fun rootMountEntry(): NativeAcquisition<String> =
        acquire({ nativeRootMountProbe() }) { it }

    /**
     * Whether the executable code segment still matches its load-time baseline.
     *
     * This single probe replaces the previous `baselineReady()` + `textIntact()`
     * pair. Two calls could observe two different states, and a missing baseline
     * used to be reported as intact code.
     */
    fun codeIntegrityIntact(): NativeAcquisition<Boolean> =
        acquire({ nativeCodeIntegrityProbe() }, ::parseBoolean)

    // -------------------------------------------------------------------------
    // Contract decoding
    // -------------------------------------------------------------------------

    private fun parseBoolean(payload: String): Boolean? = when (payload) {
        "1" -> true
        "0" -> false
        else -> null
    }

    /**
     * Runs one native probe and decodes its `OK|payload` /
     * `UNAVAILABLE|REASON` contract string.
     *
     * A library that is not loaded, a throwing JNI call, and a malformed
     * contract string are all distinct unavailable reasons; none of them can
     * produce an [NativeAcquisition.Available] result.
     */
    private inline fun <T> acquire(
        probe: () -> String,
        parse: (String) -> T?,
    ): NativeAcquisition<T> {
        if (!isAvailable) {
            return NativeAcquisition.Unavailable(NativeUnavailableReason.LIBRARY_UNAVAILABLE)
        }

        val raw = try {
            probe()
        } catch (t: Throwable) {
            Log.w(TAG, "Native probe failed: ${t.javaClass.simpleName}")
            return NativeAcquisition.Unavailable(NativeUnavailableReason.JNI_CALL_FAILED)
        }

        val tag = raw.substringBefore(SEPARATOR)
        val payload = raw.substringAfter(SEPARATOR, missingDelimiterValue = "")

        return when (tag) {
            TAG_OK -> parse(payload)
                ?.let { NativeAcquisition.Available(it) }
                ?: NativeAcquisition.Unavailable(NativeUnavailableReason.JNI_CALL_FAILED)

            TAG_UNAVAILABLE -> NativeAcquisition.Unavailable(decodeReason(payload))

            else -> NativeAcquisition.Unavailable(NativeUnavailableReason.JNI_CALL_FAILED)
        }
    }

    private fun decodeReason(payload: String): NativeUnavailableReason = when (payload) {
        "SOURCE_READ_FAILED" -> NativeUnavailableReason.SOURCE_READ_FAILED
        "BASELINE_UNAVAILABLE" -> NativeUnavailableReason.BASELINE_UNAVAILABLE
        else -> NativeUnavailableReason.JNI_CALL_FAILED
    }

    // -------------------------------------------------------------------------
    // Native contract: each probe returns "OK|<payload>" or "UNAVAILABLE|<REASON>"
    // -------------------------------------------------------------------------

    private external fun nativeTracerProbe(): String
    private external fun nativeFridaPortProbe(): String
    private external fun nativeFridaThreadProbe(): String
    private external fun nativeHookedLibraryProbe(): String
    private external fun nativeRootMountProbe(): String
    private external fun nativeCodeIntegrityProbe(): String
}
