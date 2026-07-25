package com.coldboar.coreguard

/**
 * Evaluates the app's process lineage by reading `/proc/self/status`.
 *
 * Checks two indicators:
 * 1. **Parent PID** – a parent PID of 0 (unresolvable) suggests the process was
 *    not spawned by the Android Zygote and may indicate process injection or an
 *    untrusted launcher.
 * 2. **Thread count** – an abnormally high number of threads (above [threadWarnThreshold])
 *    may indicate dynamic injection of worker threads by hooking frameworks.
 *
 * Both thresholds are injectable so behaviour can be verified in unit tests
 * without an Android device or `/proc` filesystem.
 */
class ProcessLineageEvaluator(
    /** Returns the raw text of `/proc/self/status` (injectable for tests). */
    private val procStatusProvider: () -> String = {
        try { java.io.File("/proc/self/status").readText() } catch (_: Exception) { "" }
    },
    /** Thread count above this value triggers a WARN. */
    private val threadWarnThreshold: Int = 100
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult {
        val status = procStatusProvider()

        val ppid = parsePpid(status)
        val threads = parseThreads(status)

        return when {
            status.isEmpty() -> SecurityCheckResult(
                id = "process_lineage",
                displayName = "Process Lineage",
                state = SecurityCheckState.WARN,
                explanation = "Unable to read /proc/self/status. Process lineage could not be verified."
            )
            ppid == 0 -> SecurityCheckResult(
                id = "process_lineage",
                displayName = "Process Lineage",
                state = SecurityCheckState.WARN,
                explanation = "Process parent PID is 0; the app may have been spawned by an untrusted process."
            )
            threads != null && threads > threadWarnThreshold -> SecurityCheckResult(
                id = "process_lineage",
                displayName = "Process Lineage",
                state = SecurityCheckState.WARN,
                explanation = "Thread count ($threads) exceeds threshold ($threadWarnThreshold). Possible injected thread activity."
            )
            else -> SecurityCheckResult(
                id = "process_lineage",
                displayName = "Process Lineage",
                state = SecurityCheckState.PASS,
                explanation = "Process lineage is clean: PPid=${ppid ?: "unknown"}, threads=${threads ?: "unknown"}."
            )
        }
    }

    private fun parsePpid(status: String): Int? =
        status.lineSequence()
            .firstOrNull { it.startsWith("PPid:") }
            ?.substringAfter("PPid:")
            ?.trim()
            ?.toIntOrNull()

    private fun parseThreads(status: String): Int? =
        status.lineSequence()
            .firstOrNull { it.startsWith("Threads:") }
            ?.substringAfter("Threads:")
            ?.trim()
            ?.toIntOrNull()
}
