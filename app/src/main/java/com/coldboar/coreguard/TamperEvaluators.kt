package com.coldboar.coreguard

/**
 * Runtime anti-tamper evaluators backed by the native [NativeTamperGuard].
 *
 * Following the same design as [SecurityCheckEvaluator] implementations in this
 * module, every evaluator receives its raw signals through injectable lambdas.
 * On device those lambdas call into native code; in unit tests they return
 * fixed values, so the PASS/WARN/FAIL classification is fully testable on the
 * JVM without an Android runtime.
 *
 * Truth-state contract shared by every evaluator here:
 *
 * | Acquisition               | State |
 * |---------------------------|-------|
 * | completed, nothing found  | PASS  |
 * | completed, indicator found| FAIL  |
 * | never completed           | WARN  |
 *
 * `SecurityCheckState` has no dedicated UNAVAILABLE member, so WARN carries the
 * unavailable case, and the explanation states plainly that the result is
 * unverifiable. An unavailable probe must never be rendered as PASS: that would
 * turn "we could not look" into "we looked and it was clean".
 */

/** Label used when an acquisition failed, so wording stays factual. */
private fun unavailable(
    id: String,
    displayName: String,
    reason: NativeUnavailableReason,
    checkLabel: String,
): SecurityCheckResult = SecurityCheckResult(
    id = id,
    displayName = displayName,
    state = SecurityCheckState.WARN,
    explanation = reason.explain(checkLabel),
)

/**
 * Detects the Frida instrumentation toolkit: a listening Frida server on a
 * known loopback port, or a Frida-injected worker thread inside our process.
 */
class FridaDetectionEvaluator(
    private val portOpen: () -> NativeAcquisition<Boolean> = { NativeTamperGuard.fridaPortOpen() },
    private val suspiciousThread: () -> NativeAcquisition<String> =
        { NativeTamperGuard.suspiciousFridaThread() },
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        val threadAcquisition = suspiciousThread()
        val portAcquisition = portOpen()

        // Either source failing leaves the check unverifiable. A clean result
        // from only one half is not enough to claim no instrumentation.
        val reason = threadAcquisition.reasonOrNull() ?: portAcquisition.reasonOrNull()
        if (reason != null) {
            return unavailable("frida", "Frida Instrumentation", reason, "Frida instrumentation check")
        }

        val thread = (threadAcquisition as NativeAcquisition.Available).value
        val port = (portAcquisition as NativeAcquisition.Available).value
        val detected = port || thread.isNotEmpty()

        val explanation = when {
            port && thread.isNotEmpty() ->
                "Frida detected: server port open and injected thread \"$thread\" present."
            port ->
                "A Frida server is listening on a known instrumentation port. Dynamic tampering is likely."
            thread.isNotEmpty() ->
                "A Frida-injected thread (\"$thread\") is running inside the app process."
            else ->
                "No Frida server port or injected instrumentation threads detected."
        }

        return SecurityCheckResult(
            id = "frida",
            displayName = "Frida Instrumentation",
            state = if (detected) SecurityCheckState.FAIL else SecurityCheckState.PASS,
            explanation = explanation
        )
    }
}

/**
 * Native debugger status derived passively from `/proc/self/status`.
 *
 * A non-zero `TracerPid` means another process is actively tracing us. CoreGuard
 * deliberately does not self-attach with `PTRACE_TRACEME`: doing so designates
 * the app's parent as a tracer and would make this check report a false failure.
 */
class NativeDebuggerEvaluator(
    private val tracerPid: () -> NativeAcquisition<Int> = { NativeTamperGuard.tracerPid() }
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        return when (val acquisition = tracerPid()) {
            is NativeAcquisition.Unavailable -> unavailable(
                "native_debugger", "Native Debugger", acquisition.reason, "Native debugger check",
            )

            is NativeAcquisition.Available -> if (acquisition.value > 0) {
                SecurityCheckResult(
                    id = "native_debugger",
                    displayName = "Native Debugger",
                    state = SecurityCheckState.FAIL,
                    explanation = "A debugger/tracer (PID ${acquisition.value}) is attached to this process."
                )
            } else {
                SecurityCheckResult(
                    id = "native_debugger",
                    displayName = "Native Debugger",
                    state = SecurityCheckState.PASS,
                    explanation = "No external debugger or tracer is attached to this process."
                )
            }
        }
    }
}

/**
 * Detects hooking frameworks (Frida gadget, Xposed/LSPosed, Substrate) mapped
 * into the process address space via `/proc/self/maps`.
 */
class HookDetectionEvaluator(
    private val hookedLibrary: () -> NativeAcquisition<String> =
        { NativeTamperGuard.hookedLibraryPath() }
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        return when (val acquisition = hookedLibrary()) {
            is NativeAcquisition.Unavailable -> unavailable(
                "hook_maps", "Code Hooking", acquisition.reason, "Code hooking check",
            )

            is NativeAcquisition.Available -> {
                val lib = acquisition.value
                SecurityCheckResult(
                    id = "hook_maps",
                    displayName = "Code Hooking",
                    state = if (lib.isNotEmpty()) SecurityCheckState.FAIL else SecurityCheckState.PASS,
                    explanation = if (lib.isNotEmpty())
                        "A hooking-framework library is mapped into the process: $lib"
                    else
                        "No instrumentation or hooking libraries found in the process memory map."
                )
            }
        }
    }
}

/**
 * Inspects mount points for Magisk / KernelSU systemless-root artifacts that
 * standard su-binary checks miss.
 */
class MountIntegrityEvaluator(
    private val rootMount: () -> NativeAcquisition<String> = { NativeTamperGuard.rootMountEntry() }
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        return when (val acquisition = rootMount()) {
            is NativeAcquisition.Unavailable -> unavailable(
                "mount_integrity", "Mount Integrity", acquisition.reason, "Mount integrity check",
            )

            is NativeAcquisition.Available -> {
                val mount = acquisition.value
                SecurityCheckResult(
                    id = "mount_integrity",
                    displayName = "Mount Integrity",
                    state = if (mount.isNotEmpty()) SecurityCheckState.FAIL else SecurityCheckState.PASS,
                    explanation = if (mount.isNotEmpty())
                        "A systemless-root mount was detected: ${mount.take(120)}"
                    else
                        "No Magisk/KernelSU style mount points detected in /proc/self/mounts."
                )
            }
        }
    }
}

/**
 * Verifies the integrity of the native code segment against the checksum
 * captured at load time. A mismatch indicates an inline hook was applied to the
 * executable code after loading.
 *
 * Baseline availability and the comparison result arrive from one acquisition,
 * so a missing baseline can no longer be reported as intact code.
 */
class MemoryIntegrityEvaluator(
    private val codeIntegrity: () -> NativeAcquisition<Boolean> =
        { NativeTamperGuard.codeIntegrityIntact() }
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        return when (val acquisition = codeIntegrity()) {
            is NativeAcquisition.Unavailable -> unavailable(
                "memory_integrity", "Code Integrity", acquisition.reason, "Code integrity check",
            )

            is NativeAcquisition.Available -> if (acquisition.value) {
                SecurityCheckResult(
                    id = "memory_integrity",
                    displayName = "Code Integrity",
                    state = SecurityCheckState.PASS,
                    explanation = "Native code segment matches its load-time baseline. No inline hooks detected."
                )
            } else {
                SecurityCheckResult(
                    id = "memory_integrity",
                    displayName = "Code Integrity",
                    state = SecurityCheckState.FAIL,
                    explanation = "Native code segment was modified after loading. An inline hook may be present."
                )
            }
        }
    }
}
