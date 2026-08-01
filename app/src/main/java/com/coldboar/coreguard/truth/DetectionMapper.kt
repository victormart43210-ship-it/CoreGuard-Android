package com.coldboar.coreguard.truth

import com.coldboar.coreguard.mvt.ArtifactKind
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.ThreatSeverity

// ---------------------------------------------------------------------------
// Mapper: ThreatSeverity → FindingSeverity
// ---------------------------------------------------------------------------

/**
 * Maps the Nemesis scanner's [ThreatSeverity] to the shared [FindingSeverity].
 *
 * - CRITICAL → CRITICAL (known spyware signature match)
 * - HIGH     → HIGH     (flagged file or artifact)
 * - MEDIUM   → MEDIUM   (suspicious but not confirmed indicator)
 */
fun ThreatSeverity.toFindingSeverity(): FindingSeverity = when (this) {
    ThreatSeverity.CRITICAL -> FindingSeverity.CRITICAL
    ThreatSeverity.HIGH -> FindingSeverity.HIGH
    ThreatSeverity.MEDIUM -> FindingSeverity.MEDIUM
}

// ---------------------------------------------------------------------------
// Conversion: Detection → Finding
// ---------------------------------------------------------------------------

/**
 * Converts a Nemesis scanner [Detection] to a normalized [Finding].
 *
 * IOC matches are treated as [EvidenceClass.OBSERVED] with [ConfidenceLevel.MODERATE]
 * because they reflect a direct artifact-vs-indicator comparison, but the absence
 * of root access limits full verification.
 *
 * @param timestampMs the epoch timestamp when the parent [ScanReport] was produced.
 */
fun Detection.toFinding(timestampMs: Long = System.currentTimeMillis()): Finding {
    val componentLabel = when (kind) {
        ArtifactKind.PACKAGE -> "Installed application"
        ArtifactKind.PROCESS -> "Running process"
        ArtifactKind.FILE -> "Accessible file"
        ArtifactKind.DOMAIN -> "Network domain contact"
    }
    val visibilityNote = when (kind) {
        ArtifactKind.PACKAGE ->
            "Without root access, only installed app package names are checked; internal app data is not inspected."
        ArtifactKind.PROCESS ->
            "Process visibility is limited by Android's hidepid protection; only processes in this app's session may be visible."
        ArtifactKind.FILE ->
            "Only files in app-accessible storage are checked; system and other-app files are not visible without root."
        ArtifactKind.DOMAIN ->
            "DNS-level domain matches are available only when Privacy Shield (VPN) is active."
    }
    val references = buildList {
        indicator.reference?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return Finding(
        id = "nemesis.${kind.name.lowercase()}.${artifact.replace('/', '_')}",
        title = title,
        plainSummary = detail,
        technicalDescription = "${componentLabel}: $artifact matched indicator " +
            "${indicator.malware} (${indicator.type.name.lowercase()} signature).",
        evidenceClass = EvidenceClass.OBSERVED,
        severity = severity.toFindingSeverity(),
        confidence = ConfidenceLevel.MODERATE,
        source = "NemesisScanner",
        timestampMs = timestampMs,
        affectedComponent = componentLabel,
        observedValues = listOf(artifact),
        baselineValues = emptyList(),
        whyItMatters = "This artifact matches a known indicator of compromise associated with ${indicator.malware}. " +
            "Presence does not guarantee active infection, but warrants investigation.",
        recommendedResponse = "Do not enter passwords or banking details. Remove or investigate the flagged artifact. " +
            "Consider a full forensic acquisition if the threat is high-severity.",
        verificationMethod = "Cross-reference with Amnesty International MVT or a full device backup acquisition.",
        verificationStatus = "Unverified — IOC match only; full forensic verification not possible without root.",
        threatIntelReferences = references,
        androidVisibilityLimits = visibilityNote
    )
}
