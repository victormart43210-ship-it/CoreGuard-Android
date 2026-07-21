package com.coldboar.coreguard.mvt

/** Severity of a single detection. */
enum class ThreatSeverity { CRITICAL, HIGH, MEDIUM }

/** Overall verdict of a forensic scan. */
enum class ScanVerdict { CLEAN, SUSPICIOUS, INFECTED }

/** The kind of artifact that produced a detection. */
enum class ArtifactKind { PACKAGE, PROCESS, FILE, DOMAIN }

/** A single confirmed match between an on-device artifact and an [Indicator]. */
data class Detection(
    val kind: ArtifactKind,
    val artifact: String,
    val indicator: Indicator,
    val severity: ThreatSeverity
) {
    val title: String
        get() = when (kind) {
            ArtifactKind.PACKAGE -> "Malicious app installed"
            ArtifactKind.PROCESS -> "Spyware process running"
            ArtifactKind.FILE -> "Malicious file present"
            ArtifactKind.DOMAIN -> "Contact with malicious infrastructure"
        }

    val detail: String
        get() = "$artifact → ${indicator.malware} (${indicator.type.name.lowercase()} IOC)"
}

/**
 * The result of a forensic scan.
 */
data class ScanReport(
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val scannedPackages: Int,
    val scannedProcesses: Int,
    val scannedFiles: Int,
    val indicatorCount: Int,
    val detections: List<Detection>
) {
    val verdict: ScanVerdict = PegasusScanner.classify(detections)
    val durationMillis: Long get() = (finishedAtMillis - startedAtMillis).coerceAtLeast(0)
    val scannedArtifacts: Int get() = scannedPackages + scannedProcesses + scannedFiles
}

/**
 * A "Pegasus scanner" inspired by Amnesty International's Mobile Verification
 * Toolkit (MVT): it enumerates on-device artifacts and matches them against a
 * set of mercenary-spyware Indicators of Compromise.
 *
 * On a non-rooted Android device an in-app scanner has far less visibility than
 * MVT running against a full forensic acquisition, so this focuses on what is
 * reliably observable without root: installed application packages, this
 * process's own thread/process names, and files in app-accessible storage.
 *
 * All inputs are injected so the matching + classification logic is unit
 * testable without an Android runtime.
 */
class PegasusScanner(
    private val matcher: IocMatcher,
    private val installedPackages: () -> List<String>,
    private val runningProcesses: () -> List<String> = { emptyList() },
    private val accessibleFiles: () -> List<String> = { emptyList() },
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun scan(): ScanReport {
        val started = clock()
        val detections = mutableListOf<Detection>()

        val packages = installedPackages()
        packages.forEach { pkg ->
            matcher.matchPackage(pkg)?.let {
                detections += Detection(ArtifactKind.PACKAGE, pkg, it, ThreatSeverity.CRITICAL)
            }
        }

        val processes = runningProcesses()
        processes.forEach { proc ->
            matcher.matchProcess(proc)?.let {
                detections += Detection(ArtifactKind.PROCESS, proc, it, ThreatSeverity.CRITICAL)
            }
        }

        val files = accessibleFiles()
        files.forEach { path ->
            matcher.matchFilePath(path)?.let {
                detections += Detection(ArtifactKind.FILE, path, it, ThreatSeverity.HIGH)
            }
        }

        return ScanReport(
            startedAtMillis = started,
            finishedAtMillis = clock(),
            scannedPackages = packages.size,
            scannedProcesses = processes.size,
            scannedFiles = files.size,
            indicatorCount = matcher.size,
            detections = detections.distinct()
        )
    }

    companion object {
        /** Derives the overall verdict from the set of detections. */
        fun classify(detections: List<Detection>): ScanVerdict = when {
            detections.any { it.severity == ThreatSeverity.CRITICAL } -> ScanVerdict.INFECTED
            detections.isNotEmpty() -> ScanVerdict.SUSPICIOUS
            else -> ScanVerdict.CLEAN
        }
    }
}
