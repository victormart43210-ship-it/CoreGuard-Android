package com.coldboar.coreguard.guardian.response

import com.coldboar.coreguard.guardian.ActionType
import com.coldboar.coreguard.guardian.RecommendedAction
import com.coldboar.coreguard.guardian.ResponsePlan
import com.coldboar.coreguard.guardian.ResponseStep
import com.coldboar.coreguard.guardian.SecurityFinding
import com.coldboar.coreguard.guardian.Severity

/**
 * Ritual of Response — calm guided steps (Blueprint §13).
 * No destructive step runs automatically.
 */
object ResponsePlanFactory {

    fun forFinding(finding: SecurityFinding): ResponsePlan {
        val now = System.currentTimeMillis()
        val steps = mutableListOf<ResponseStep>()
        steps += step(
            1,
            "Review evidence",
            "Read what CoreGuard observed and its evidence class. ${finding.primaryEvidenceClass.userLabel} · ${finding.confidence.userLabel}.",
            RecommendedAction(
                id = "resp-review",
                label = "Review evidence",
                explanation = finding.plainLanguageSummary,
                actionType = ActionType.REVIEW_EVIDENCE,
                requiresConfirmation = false
            )
        )
        steps += step(
            2,
            "Export or preserve evidence",
            "Export a redacted report before changing settings or wiping local data.",
            RecommendedAction(
                id = "resp-export",
                label = "Export report",
                explanation = "Creates a local text report without secrets.",
                actionType = ActionType.EXPORT_REPORT,
                requiresConfirmation = true
            )
        )
        steps += step(
            3,
            "Identify recent changes",
            "Check Book of Changes and recently installed apps. Possible benign causes: ${finding.possibleBenignCauses.take(2).joinToString()}.",
            RecommendedAction(
                id = "resp-timeline",
                label = "Open Book of Changes",
                explanation = "Timeline of meaningful security events.",
                actionType = ActionType.READ_GUIDANCE,
                destination = "book_of_changes",
                requiresConfirmation = false
            )
        )
        if (finding.severity.ordinal >= Severity.ELEVATED_CONCERN.ordinal) {
            steps += step(
                4,
                "Open relevant Android settings",
                "CoreGuard opens Settings for you to review. It will not revoke permissions by itself.",
                finding.recommendedActions.firstOrNull() ?: RecommendedAction(
                    id = "resp-settings",
                    label = "Open settings",
                    explanation = "User-controlled Android settings.",
                    actionType = ActionType.OPEN_ANDROID_SETTINGS,
                    destination = android.provider.Settings.ACTION_SECURITY_SETTINGS,
                    requiresConfirmation = true
                )
            )
            steps += step(
                5,
                "Consider password changes from a trusted device",
                "If you believe credentials may be exposed, change important passwords from a different trusted device.",
                null,
                requiresExternal = true
            )
        }
        steps += step(
            steps.size + 1,
            "Document resolution",
            "Mark this plan complete after you have reviewed evidence and taken any user-controlled actions.",
            RecommendedAction(
                id = "resp-done",
                label = "Mark reviewed",
                explanation = "Does not delete evidence.",
                actionType = ActionType.DISMISS,
                requiresConfirmation = false
            )
        )
        return ResponsePlan(
            findingId = finding.id,
            title = "Response · ${finding.title}",
            summary = "Calm guided steps for ${finding.severity.userLabel}. " +
                "No automatic wipe, revoke, or lockout.",
            steps = steps,
            createdAtEpochMillis = now
        )
    }

    private fun step(
        order: Int,
        title: String,
        explanation: String,
        action: RecommendedAction?,
        requiresExternal: Boolean = false
    ) = ResponseStep(
        id = "step-$order",
        order = order,
        title = title,
        explanation = explanation,
        action = action,
        completed = false,
        requiresExternalTrustedDevice = requiresExternal
    )
}
