package com.coldboar.coreguard

/**
 * Runtime anti-tamper evaluators backed by the native [NativeTamperGuard].
 *
 * Following the same design as [SecurityCheckEvaluator] implementations in this
 * module, every evaluator receives its raw signals through injectable lambdas.
 * On device those lambdas call into native code; in unit tests they return
 * fixed values, so the PASS/WARN/FAIL classification is fully testable on the
 * JVM without an Android runtime.
 */

/**
 * Detects the Frida instrumentation toolkit: a listening Frida server on a
 * known loopback port, or a Frida-injected worker thread inside our process.
 */
class FridaDetectionEvaluator(
    private val portOpen: () -> Boolean = { NativeTamperGuard.fridaPortOpen() },
    private val suspiciousThread: () -> String = { NativeTamperGuard.suspiciousFridaThread() }
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        val thread = suspiciousThread()
        val port = portOpen()
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
    private val tracerPid: () -> Int = { NativeTamperGuard.tracerPid() }
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        val pid = tracerPid()
        return if (pid > 0) {
            SecurityCheckResult(
                id = "native_debugger",
                displayName = "Native Debugger",
                state = SecurityCheckState.FAIL,
                explanation = "A debugger/tracer (PID $pid) is attached to this process."
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

/**
 * Detects hooking frameworks (Frida gadget, Xposed/LSPosed, Substrate) mapped
 * into the process address space via `/proc/self/maps`.
 */
class HookDetectionEvaluator(
    private val hookedLibrary: () -> String = { NativeTamperGuard.hookedLibraryPath() }
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        val lib = hookedLibrary()
        return SecurityCheckResult(
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

/**
 * Inspects mount points for Magisk / KernelSU systemless-root artifacts that
 * standard su-binary checks miss.
 */
class MountIntegrityEvaluator(
    private val rootMount: () -> String = { NativeTamperGuard.rootMountEntry() }
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        val mount = rootMount()
        return SecurityCheckResult(
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

/**
 * Verifies the integrity of the native code segment against the checksum
 * captured at load time. A mismatch indicates an inline hook was applied to the
 * executable code after loading.
 */
class MemoryIntegrityEvaluator(
    private val baselineReady: () -> Boolean = { NativeTamperGuard.baselineReady() },
    private val textIntact: () -> Boolean = { NativeTamperGuard.textIntact() }
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        return when {
            !baselineReady() -> SecurityCheckResult(
                id = "memory_integrity",
                displayName = "Code Integrity",
                state = SecurityCheckState.WARN,
                explanation = "Native code baseline could not be captured; integrity is unverifiable."
            )
            textIntact() -> SecurityCheckResult(
                id = "memory_integrity",
                displayName = "Code Integrity",
                state = SecurityCheckState.PASS,
                explanation = "Native code segment matches its load-time baseline. No inline hooks detected."
            )
            else -> SecurityCheckResult(
                id = "memory_integrity",
                displayName = "Code Integrity",
                state = SecurityCheckState.FAIL,
                explanation = "Native code segment was modified after loading. An inline hook may be present."
            )
        }
    }
}
