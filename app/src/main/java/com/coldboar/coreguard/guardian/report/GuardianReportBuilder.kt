package com.coldboar.coreguard.guardian.report

import com.coldboar.coreguard.guardian.EvidenceClass
import com.coldboar.coreguard.guardian.GuardianReport
import com.coldboar.coreguard.guardian.GuardianState
import com.coldboar.coreguard.guardian.SecurityFinding
import java.util.UUID

/**
 * Redacted Guardian reports (Blueprint §16). Never includes secrets or message contents.
 */
object GuardianReportBuilder {

    private const val DISCLAIMER =
        "CoreGuard observes what Android allows it to observe. " +
            "Inferred and simulated results are not confirmed compromise. " +
            "This report is not a guarantee of safety."

    fun build(
        findings: List<SecurityFinding>,
        state: GuardianState,
        versionName: String,
        includePackageNames: Boolean,
        includeDeviceModel: Boolean
    ): GuardianReport =
        GuardianReport(
            id = "rpt-${UUID.randomUUID()}",
            createdAtEpochMillis = System.currentTimeMillis(),
            coreGuardVersion = versionName,
            findings = findings,
            guardianState = state,
            disclaimer = DISCLAIMER,
            includePackageNames = includePackageNames,
            includeDeviceModel = includeDeviceModel
        )

    fun toShareText(report: GuardianReport, deviceModel: String?): String = buildString {
        appendLine("CoreGuard Guardian Report")
        appendLine("id=${report.id}")
        appendLine("created=${report.createdAtEpochMillis}")
        appendLine("version=${report.coreGuardVersion}")
        appendLine("pulse=${report.guardianState.userLabel}")
        if (report.includeDeviceModel) {
            appendLine("device=${deviceModel ?: "unavailable"}")
        }
        appendLine()
        appendLine("Evidence-class legend: " + EvidenceClass.entries.joinToString { it.userLabel })
        appendLine("Severity legend: Protected · Informational · Review Suggested · Elevated Concern · High Confidence Risk")
        appendLine()
        report.findings.forEach { f ->
            appendLine("## ${f.title}")
            appendLine("severity=${f.severity.userLabel} confidence=${f.confidence.userLabel}")
            appendLine("evidenceClass=${f.primaryEvidenceClass.userLabel}")
            appendLine(f.plainLanguageSummary)
            appendLine("why: ${f.whyItMatters}")
            appendLine("benign: ${f.possibleBenignCauses.joinToString("; ")}")
            f.evidence.forEach { e ->
                appendLine("- [${e.evidenceClass.userLabel}] ${e.summary}")
            }
            appendLine()
        }
        appendLine(report.disclaimer)
    }
}
