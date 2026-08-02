package com.coldboar.coreguard.mvt

import com.coldboar.coreguard.truth.ConfidenceLevel
import com.coldboar.coreguard.truth.Finding

data class CorrelatedFinding(
    val finding: Finding,
    val childEvidence: List<Finding>
)

fun correlateFindingsDeterministic(findings: List<Finding>): List<CorrelatedFinding> {
    return findings
        .groupBy { it.observedValues.firstOrNull() ?: it.id }
        .toSortedMap()
        .values
        .map { group ->
            val deduped = group.distinctBy { "${it.id}|${it.source}|${it.affectedComponent}" }
            val independentSources = deduped.map { "${it.source}:${it.affectedComponent}" }.distinct().size
            val primary = deduped.first()
            val correlated = if (independentSources >= 2 && primary.confidence != ConfidenceLevel.VERIFIED) {
                primary.copy(confidence = ConfidenceLevel.HIGH)
            } else {
                primary
            }
            CorrelatedFinding(
                finding = correlated,
                childEvidence = deduped
            )
        }
}

