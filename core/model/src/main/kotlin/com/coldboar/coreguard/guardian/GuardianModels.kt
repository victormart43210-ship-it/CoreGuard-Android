package com.coldboar.coreguard.guardian

/**
 * Shared Guardian Intelligence domain models (Blueprint §5+).
 * Pure JVM — no Android dependencies.
 */

data class Evidence(
    val id: String,
    val evidenceClass: EvidenceClass,
    val source: String,
    val summary: String,
    val technicalDetail: String? = null,
    val collectedAtEpochMillis: Long,
    val verifiableValue: String? = null
)

enum class ActionType {
    OPEN_ANDROID_SETTINGS,
    OPEN_APP_DETAILS,
    RUN_SCAN,
    REVIEW_EVIDENCE,
    EXPORT_REPORT,
    VERIFY_INSTALLATION,
    READ_GUIDANCE,
    DISMISS,
    NONE
}

data class RecommendedAction(
    val id: String,
    val label: String,
    val explanation: String,
    val actionType: ActionType,
    val destination: String? = null,
    val destructive: Boolean = false,
    val requiresConfirmation: Boolean = true
)

data class SecurityFinding(
    val id: String,
    val category: FindingCategory,
    val severity: Severity,
    val confidence: Confidence,
    val title: String,
    val plainLanguageSummary: String,
    val whyItMatters: String,
    val possibleBenignCauses: List<String>,
    val evidence: List<Evidence>,
    val recommendedActions: List<RecommendedAction>,
    val firstSeenEpochMillis: Long,
    val lastSeenEpochMillis: Long,
    val active: Boolean,
    val detectorVersion: String,
    /** Primary evidence class for Truth Seal (derived from evidence list). */
    val primaryEvidenceClass: EvidenceClass = evidence.firstOrNull()?.evidenceClass
        ?: EvidenceClass.UNAVAILABLE
) {
    init {
        require(evidence.isNotEmpty()) { "Every finding requires at least one Evidence item." }
    }
}

data class SecurityEvent(
    val id: String,
    val occurredAtEpochMillis: Long,
    val detectedAtEpochMillis: Long,
    val category: FindingCategory,
    val severity: Severity,
    val evidenceClass: EvidenceClass,
    val title: String,
    val explanation: String,
    val relatedPackageName: String? = null,
    val evidenceIds: List<String>,
    val sourceDetector: String,
    val eventHash: String,
    val previousEventHash: String?
)

data class CorrelationRule(
    val id: String,
    val requiredCategories: Set<FindingCategory>,
    val optionalCategories: Set<FindingCategory>,
    val timeWindowMillis: Long,
    val minimumDistinctSignals: Int,
    val resultingSeverity: Severity,
    val maximumConfidence: Confidence,
    val explanationTemplate: String,
    val version: String = "1"
)

data class CorrelatedFinding(
    val id: String,
    val ruleId: String,
    val memberFindingIds: List<String>,
    val firstSignalAtEpochMillis: Long,
    val lastSignalAtEpochMillis: Long,
    val severity: Severity,
    val confidence: Confidence,
    val narrative: String,
    val evidenceClass: EvidenceClass = EvidenceClass.INFERRED
)

data class DeviceBaseline(
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val packageNames: Set<String>,
    val trustedPackages: Set<String>,
    val securityPatchLevel: String?,
    val accessibilityServices: Set<String>,
    val deviceAdminPackages: Set<String>,
    val learningUntilEpochMillis: Long,
    val learningMode: Boolean
)

enum class HardeningStatus {
    PASSED,
    REVIEW,
    FAILED,
    UNAVAILABLE,
    MANUAL_CONFIRMATION_REQUIRED
}

data class HardeningCheck(
    val id: String,
    val title: String,
    val description: String,
    val status: HardeningStatus,
    val evidenceClass: EvidenceClass,
    val importance: Severity,
    val action: RecommendedAction?,
    val lastCheckedEpochMillis: Long
)

data class ResponseStep(
    val id: String,
    val order: Int,
    val title: String,
    val explanation: String,
    val action: RecommendedAction?,
    val completed: Boolean,
    val requiresExternalTrustedDevice: Boolean = false
)

data class ResponsePlan(
    val findingId: String,
    val title: String,
    val summary: String,
    val steps: List<ResponseStep>,
    val createdAtEpochMillis: Long
)

data class InstallationVerification(
    val packageNameMatches: Boolean,
    val signatureMatches: Boolean,
    val expectedCertificateSha256: String?,
    val installedCertificateSha256: String?,
    val installerPackage: String?,
    val buildType: String,
    val verifiedAtEpochMillis: Long,
    val evidence: List<Evidence>,
    val packageName: String,
    val versionName: String,
    val versionCode: Long
)

data class GuardianReport(
    val id: String,
    val createdAtEpochMillis: Long,
    val coreGuardVersion: String,
    val findings: List<SecurityFinding>,
    val guardianState: GuardianState,
    val disclaimer: String,
    val includePackageNames: Boolean,
    val includeDeviceModel: Boolean
)
