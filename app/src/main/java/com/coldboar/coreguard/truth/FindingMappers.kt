package com.coldboar.coreguard.truth

import com.coldboar.coreguard.mvt.ArtifactKind
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.ThreatSeverity

/**
 * App-module mapper functions from legacy MVT/Nemesis types to the canonical
 * [Finding] truth model.
 *
 * These live here (not in :core:model) because [Detection] and [ThreatSeverity]
 * are defined in the :app module and :core:model cannot take a dependency on :app.
 */

/** Maps [ThreatSeverity] to the canonical [FindingSeverity]. */
fun ThreatSeverity.toFindingSeverity(): FindingSeverity = when (this) {
    ThreatSeverity.CRITICAL -> FindingSeverity.CRITICAL
    ThreatSeverity.HIGH -> FindingSeverity.HIGH
    ThreatSeverity.MEDIUM -> FindingSeverity.MEDIUM
}

/**
 * Converts a [Detection] from the Nemesis scanner to a canonical [Finding].
 *
 * Android visibility limits: on a non-rooted device the scanner can only
 * observe installed packages, this app's process namespace, and files in
 * app-accessible storage directories.
 */
fun Detection.toFinding(timestampMs: Long = System.currentTimeMillis()): Finding {
    val visibilityNote = when (kind) {
        ArtifactKind.PACKAGE ->
            "Package list visible on non-rooted devices; installation timestamp not available."
        ArtifactKind.PROCESS ->
            "Process enumeration limited to this app's own process namespace without root."
        ArtifactKind.FILE ->
            "File scan limited to app-accessible storage (filesDir, getExternalFilesDir)."
        ArtifactKind.DOMAIN ->
            "DNS contacts observable only via VPN sinkhole (Shield must be active)."
    }
    return Finding(
        id = "nemesis:${kind.name.lowercase()}:$artifact",
        title = title,
        plainSummary = detail,
        technicalDescription = buildString {
            append("Artifact: $artifact")
            append(" | Indicator type: ${indicator.type.name}")
            append(" | Malware family: ${indicator.malware}")
            indicator.reference?.takeIf { it.isNotBlank() }?.let { append(" | Ref: $it") }
        },
        evidenceClass = EvidenceClass.OBSERVED,
        severity = severity.toFindingSeverity(),
        confidence = ConfidenceLevel.MODERATE,
        source = "NemesisScanner",
        timestampMs = timestampMs,
        affectedComponent = artifact,
        observedValues = listOf(artifact),
        baselineValues = emptyList(),
        whyItMatters = "This artifact matches a known spyware indicator from the Amnesty International Security Lab IOC dataset.",
        recommendedResponse = "Do not enter passwords or sensitive data. Remove the flagged app if possible and consult a trusted security professional.",
        verificationMethod = "Cross-reference $artifact against the mvt-project IOC list at https://github.com/mvt-project/mvt-indicators",
        verificationStatus = "Matched against loaded IOC dataset; not independently confirmed.",
        threatIntelReferences = listOfNotNull(indicator.reference?.takeIf { it.isNotBlank() }),
        androidVisibilityLimits = visibilityNote
    )
}
