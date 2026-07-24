package com.coldboar.coreguard

import android.util.Log

/**
 * Kotlin bridge to the native `libtamperguard.so` anti-tamper primitives.
 *
 * The heavy lifting lives in C++ (see `src/main/cpp/tamperguard.cpp`) because
 * native code is much harder to trace and hook than Kotlin/Java. This object is
 * a thin, defensive wrapper: if the native library fails to load (or a specific
 * symbol is missing) every accessor degrades to a benign default so the app
 * never crashes because of the security layer itself.
 *
 * Loading the library triggers `JNI_OnLoad`, which installs the `ptrace`
 * anti-debug guard and captures the code-integrity baseline as early as
 * possible, so [ensureLoaded] should be called from `Application.onCreate`.
 */
object NativeTamperGuard {

    private const val TAG = "TamperGuard"

    /** True once the native library has been successfully loaded. */
    @Volatile
    var isAvailable: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("tamperguard")
            isAvailable = true
        } catch (t: Throwable) {
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

    /** TracerPid from `/proc/self/status`; 0 when no debugger/tracer attached. */
    fun tracerPid(): Int = safe(0) { nativeTracerPid() }

    /** Whether the early `ptrace(PTRACE_TRACEME)` anti-debug guard is engaged. */
    fun ptraceProtected(): Boolean = safe(false) { nativePtraceProtected() }

    /** True when a Frida server/gadget is listening on a known loopback port. */
    fun fridaPortOpen(): Boolean = safe(false) { nativeFridaPortOpen() }

    /** Name of a Frida-injected thread in this process, or empty string. */
    fun suspiciousFridaThread(): String = safe("") { nativeScanThreadsForFrida() }

    /** Path of a hooking-framework library mapped into this process, or "". */
    fun hookedLibraryPath(): String = safe("") { nativeScanMapsForHooks() }

    /** A Magisk/KernelSU style root mount entry, or empty string. */
    fun rootMountEntry(): String = safe("") { nativeScanMountsForRoot() }

    /** False only when the executable code segment differs from its baseline. */
    fun textIntact(): Boolean = safe(true) { nativeTextIntact() }

    /** Whether a code-integrity baseline was captured at load time. */
    fun baselineReady(): Boolean = safe(false) { nativeBaselineReady() }

    private inline fun <T> safe(fallback: T, block: () -> T): T =
        if (!isAvailable) fallback else try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "Native call failed, using fallback: ${t.message}")
            fallback
        }

    private external fun nativeTracerPid(): Int
    private external fun nativePtraceProtected(): Boolean
    private external fun nativeFridaPortOpen(): Boolean
    private external fun nativeScanThreadsForFrida(): String
    private external fun nativeScanMapsForHooks(): String
    private external fun nativeScanMountsForRoot(): String
    private external fun nativeTextIntact(): Boolean
    private external fun nativeBaselineReady(): Boolean
}
