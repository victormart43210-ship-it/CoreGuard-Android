package com.coldboar.coreguard.guardian

/**
 * Conservative correlation rules (Blueprint §10). Does not manufacture evidence —
 * narratives describe co-occurrence only.
 */
object EvidenceConstellation {

    val RULE_PRIVILEGE_ESCALATION = CorrelationRule(
        id = "constellation.privilege_escalation.v1",
        requiredCategories = setOf(
            FindingCategory.PACKAGE_CHANGE,
            FindingCategory.ACCESSIBILITY
        ),
        optionalCategories = setOf(
            FindingCategory.APP_PERMISSION,
            FindingCategory.DEVICE_ADMIN,
            FindingCategory.PRIVACY
        ),
        timeWindowMillis = 72L * 60 * 60 * 1000,
        minimumDistinctSignals = 3,
        resultingSeverity = Severity.ELEVATED_CONCERN,
        maximumConfidence = Confidence.MEDIUM,
        explanationTemplate =
            "Several related privilege or package changes occurred close together. " +
                "This is an inferred pattern, not proof of malware."
    )

    val RULE_INTEGRITY = CorrelationRule(
        id = "constellation.coreguard_integrity.v1",
        requiredCategories = setOf(FindingCategory.SIGNATURE),
        optionalCategories = setOf(
            FindingCategory.DEBUGGING,
            FindingCategory.DEVICE_INTEGRITY
        ),
        timeWindowMillis = 24L * 60 * 60 * 1000,
        minimumDistinctSignals = 2,
        resultingSeverity = Severity.ELEVATED_CONCERN,
        maximumConfidence = Confidence.HIGH,
        explanationTemplate =
            "CoreGuard integrity-related signals appeared together. " +
                "Review installation verification before drawing conclusions."
    )

    val RULE_SIDELOAD_PRIVILEGE = CorrelationRule(
        id = "constellation.sideload_privilege.v1",
        requiredCategories = setOf(FindingCategory.PACKAGE_CHANGE),
        optionalCategories = setOf(
            FindingCategory.APP_PERMISSION,
            FindingCategory.PRIVACY,
            FindingCategory.ACCESSIBILITY
        ),
        timeWindowMillis = 48L * 60 * 60 * 1000,
        minimumDistinctSignals = 2,
        resultingSeverity = Severity.REVIEW_SUGGESTED,
        maximumConfidence = Confidence.MEDIUM,
        explanationTemplate =
            "A package change and sensitive privilege signals were seen near each other. " +
                "Review the related apps; this is not a confirmed intrusion."
    )

    val defaultRules: List<CorrelationRule> = listOf(
        RULE_PRIVILEGE_ESCALATION,
        RULE_INTEGRITY,
        RULE_SIDELOAD_PRIVILEGE
    )

    fun correlate(
        findings: List<SecurityFinding>,
        nowEpochMillis: Long = System.currentTimeMillis(),
        rules: List<CorrelationRule> = defaultRules
    ): List<CorrelatedFinding> {
        val active = findings.filter { it.active }
        return rules.mapNotNull { rule -> evaluate(rule, active, nowEpochMillis) }
    }

    private fun evaluate(
        rule: CorrelationRule,
        findings: List<SecurityFinding>,
        now: Long
    ): CorrelatedFinding? {
        val windowStart = now - rule.timeWindowMillis
        val inWindow = findings.filter { it.lastSeenEpochMillis >= windowStart }
        val requiredHits = rule.requiredCategories.mapNotNull { cat ->
            inWindow.firstOrNull { it.category == cat }
        }
        if (requiredHits.size < rule.requiredCategories.size) return null

        val optionalHits = inWindow.filter { it.category in rule.optionalCategories }
        val members = (requiredHits + optionalHits).distinctBy { it.id }
        if (members.size < rule.minimumDistinctSignals) return null

        val confidence = members
            .map { it.confidence }
            .minByOrNull { it.ordinal }
            ?.let { conf ->
                if (conf.ordinal <= rule.maximumConfidence.ordinal) conf
                else rule.maximumConfidence
            }
            ?: rule.maximumConfidence

        // Never emit VERIFIED from correlation alone.
        val capped = if (confidence == Confidence.VERIFIED) Confidence.HIGH else confidence

        var severity = rule.resultingSeverity
        if (rule.id == RULE_INTEGRITY.id) {
            val sigVerified = members.any {
                it.category == FindingCategory.SIGNATURE && it.confidence == Confidence.VERIFIED
            }
            if (sigVerified && members.size >= 2) {
                severity = Severity.HIGH_CONFIDENCE_RISK
            }
        }

        return CorrelatedFinding(
            id = "corr-${rule.id}-${members.minOf { it.id }.hashCode()}",
            ruleId = rule.id,
            memberFindingIds = members.map { it.id },
            firstSignalAtEpochMillis = members.minOf { it.firstSeenEpochMillis },
            lastSignalAtEpochMillis = members.maxOf { it.lastSeenEpochMillis },
            severity = severity,
            confidence = capped,
            narrative = rule.explanationTemplate +
                " Sources: ${members.joinToString { it.title }}."
        )
    }
}
