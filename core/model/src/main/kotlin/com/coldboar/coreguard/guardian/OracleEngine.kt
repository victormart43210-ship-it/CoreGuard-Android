package com.coldboar.coreguard.guardian

/**
 * Turns raw detector output into calm SecurityFinding explanations (Blueprint §6).
 * Deterministic — no generative model.
 */
interface OracleEngine {
    fun explain(signal: RawSecuritySignal): SecurityFinding
}

/**
 * Adapter input from existing [com.coldboar.coreguard.SecurityCheckResult]-style detectors.
 */
data class RawSecuritySignal(
    val checkId: String,
    val displayName: String,
    val stateName: String,
    val explanation: String,
    val collectedAtEpochMillis: Long = System.currentTimeMillis()
)

data class ExplanationRule(
    val checkId: String,
    val category: FindingCategory,
    val evidenceClass: EvidenceClass,
    val passSeverity: Severity = Severity.PROTECTED,
    val warnSeverity: Severity = Severity.REVIEW_SUGGESTED,
    val failSeverity: Severity = Severity.ELEVATED_CONCERN,
    val passConfidence: Confidence = Confidence.MEDIUM,
    val warnConfidence: Confidence = Confidence.MEDIUM,
    val failConfidence: Confidence = Confidence.MEDIUM,
    val whyItMatters: String,
    val benignCauses: List<String>,
    val primaryActionLabel: String = "Review evidence",
    val primaryActionType: ActionType = ActionType.REVIEW_EVIDENCE
)

/**
 * Catalog of explanation rules for CoreGuard's standard security checks.
 */
object OracleRules {

    private fun rule(
        checkId: String,
        category: FindingCategory,
        evidenceClass: EvidenceClass,
        why: String,
        benign: List<String>,
        failSeverity: Severity = Severity.ELEVATED_CONCERN,
        failConfidence: Confidence = Confidence.MEDIUM,
        warnSeverity: Severity = Severity.REVIEW_SUGGESTED
    ) = ExplanationRule(
        checkId = checkId,
        category = category,
        evidenceClass = evidenceClass,
        failSeverity = failSeverity,
        failConfidence = failConfidence,
        warnSeverity = warnSeverity,
        whyItMatters = why,
        benignCauses = benign
    )

    val catalog: Map<String, ExplanationRule> = listOf(
        rule(
            "debugger", FindingCategory.DEBUGGING, EvidenceClass.OBSERVED,
            "A debugger connection can let another process inspect this app.",
            listOf("You are developing or testing with Android Studio attached.")
        ),
        rule(
            "native_debugger", FindingCategory.DEBUGGING, EvidenceClass.OBSERVED,
            "Native tracer signals can indicate debugging or instrumentation.",
            listOf("A legitimate debugging session", "Some accessibility tooling")
        ),
        rule(
            "frida", FindingCategory.DEVICE_INTEGRITY, EvidenceClass.INFERRED,
            "Instrumentation frameworks are sometimes used to hook apps.",
            listOf("Security research tools you installed", "False positives on unusual builds"),
            failSeverity = Severity.ELEVATED_CONCERN,
            failConfidence = Confidence.MEDIUM
        ),
        rule(
            "hook_maps", FindingCategory.DEVICE_INTEGRITY, EvidenceClass.INFERRED,
            "Hooked libraries can alter app behavior.",
            listOf("Xposed-style frameworks you chose to install")
        ),
        rule(
            "memory_integrity", FindingCategory.DEVICE_INTEGRITY, EvidenceClass.INFERRED,
            "Unexpected memory or text-segment changes may indicate tampering.",
            listOf("OS updates mid-session", "Aggressive memory optimizers")
        ),
        rule(
            "emulator", FindingCategory.OPERATING_SYSTEM, EvidenceClass.OBSERVED,
            "Emulators are common for development and may lack device protections.",
            listOf("You are running CoreGuard inside an emulator by choice")
        ),
        rule(
            "root", FindingCategory.ROOT_INDICATOR, EvidenceClass.INFERRED,
            "Root or system-modification indicators can expand app privileges.",
            listOf("Intentional rooted device", "Custom ROM with su binaries"),
            failSeverity = Severity.ELEVATED_CONCERN
        ),
        rule(
            "mount_integrity", FindingCategory.DEVICE_INTEGRITY, EvidenceClass.INFERRED,
            "Unusual mount points can appear on modified systems.",
            listOf("Custom recovery", "Multi-user or work-profile mounts")
        ),
        rule(
            "build_type", FindingCategory.OPERATING_SYSTEM, EvidenceClass.OBSERVED,
            "Debug builds are not intended for production use.",
            listOf("You installed a debug APK from CI or Android Studio")
        ),
        rule(
            "signature", FindingCategory.SIGNATURE, EvidenceClass.OBSERVED,
            "Signing identity mismatch means this install may not be the official build.",
            listOf("A fork you built yourself", "A sideloaded unofficial package"),
            failSeverity = Severity.HIGH_CONFIDENCE_RISK,
            failConfidence = Confidence.VERIFIED
        ),
        rule(
            "strongbox", FindingCategory.DEVICE_INTEGRITY, EvidenceClass.OBSERVED,
            "Hardware-backed keys strengthen local secret storage when available.",
            listOf("Older devices without StrongBox", "Emulators without TEE")
        ),
        rule(
            "process_lineage", FindingCategory.DEVICE_INTEGRITY, EvidenceClass.INFERRED,
            "Unexpected process parents can appear with instrumentation.",
            listOf("OEM process managers", "Accessibility services")
        ),
        rule(
            "spyware_scan", FindingCategory.PRIVACY, EvidenceClass.INFERRED,
            "IOC matches are indicators, not confirmation of spyware presence.",
            listOf("Benign apps sharing artifact names", "Stale IOC lists"),
            failSeverity = Severity.ELEVATED_CONCERN,
            failConfidence = Confidence.MEDIUM,
            warnSeverity = Severity.REVIEW_SUGGESTED
        ),
        rule(
            "overlay_abuse", FindingCategory.PRIVACY, EvidenceClass.INFERRED,
            "Overlay permission can be abused for UI redressing.",
            listOf("Chat heads", "Screen dimmers you installed")
        ),
        rule(
            "accessibility_abuse", FindingCategory.ACCESSIBILITY, EvidenceClass.OBSERVED,
            "Accessibility services can read screen content when enabled.",
            listOf("Password managers", "Screen readers you enabled")
        ),
        rule(
            "sideload_risk", FindingCategory.PACKAGE_CHANGE, EvidenceClass.INFERRED,
            "Unknown sources increase the chance of unofficial packages.",
            listOf("You sideload apps intentionally", "Enterprise MDM installs")
        )
    ).associateBy { it.checkId }

    fun forCheck(checkId: String): ExplanationRule =
        catalog[checkId] ?: ExplanationRule(
            checkId = checkId,
            category = FindingCategory.DEVICE_INTEGRITY,
            evidenceClass = EvidenceClass.INFERRED,
            whyItMatters = "This check contributed to your Guardian posture.",
            benignCauses = listOf("Benign device configuration", "Temporary OS state")
        )
}

object DeterministicOracleEngine : OracleEngine {

    const val DETECTOR_VERSION: String = "oracle-1.0.0"

    override fun explain(signal: RawSecuritySignal): SecurityFinding {
        val rule = OracleRules.forCheck(signal.checkId)
        val state = signal.stateName.uppercase()
        val severity = when (state) {
            "PASS" -> rule.passSeverity
            "WARN" -> rule.warnSeverity
            "FAIL" -> rule.failSeverity
            else -> Severity.INFORMATIONAL
        }
        val confidence = when (state) {
            "PASS" -> rule.passConfidence
            "WARN" -> rule.warnConfidence
            "FAIL" -> rule.failConfidence
            else -> Confidence.LOW
        }
        val now = signal.collectedAtEpochMillis
        val evidence = Evidence(
            id = "ev-${signal.checkId}-$now",
            evidenceClass = rule.evidenceClass,
            source = signal.checkId,
            summary = signal.explanation.ifBlank { signal.displayName },
            technicalDetail = "state=$state check=${signal.checkId}",
            collectedAtEpochMillis = now
        )
        val action = RecommendedAction(
            id = "act-${signal.checkId}",
            label = rule.primaryActionLabel,
            explanation = "Open the finding details to review evidence and next steps.",
            actionType = rule.primaryActionType,
            destination = "finding/${signal.checkId}",
            destructive = false,
            requiresConfirmation = false
        )
        val benign = rule.benignCauses.ifEmpty {
            listOf("No common benign causes are catalogued for this signal.")
        }
        return SecurityFinding(
            id = "finding-${signal.checkId}",
            category = rule.category,
            severity = severity,
            confidence = confidence,
            title = signal.displayName,
            plainLanguageSummary = when (state) {
                "PASS" -> "${signal.displayName}: no concerning indicator in this check."
                "WARN" -> "${signal.displayName}: worth a calm review. ${signal.explanation}"
                "FAIL" -> "${signal.displayName}: elevated indicator. ${signal.explanation}"
                else -> signal.explanation
            },
            whyItMatters = rule.whyItMatters,
            possibleBenignCauses = benign,
            evidence = listOf(evidence),
            recommendedActions = listOf(action),
            firstSeenEpochMillis = now,
            lastSeenEpochMillis = now,
            active = state != "PASS",
            detectorVersion = DETECTOR_VERSION,
            primaryEvidenceClass = rule.evidenceClass
        )
    }
}
