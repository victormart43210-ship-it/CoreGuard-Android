package com.coldboar.coreguard.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugEvidencePreviewSamplesTest {

    @Test
    fun `guardian pulse preview includes all target states`() {
        val states = DebugEvidencePreviewSamples.guardianPulseStates.map { it.label }
        assertEquals(
            listOf(
                "Protected",
                "Observing",
                "Attention Needed",
                "Elevated Concern",
                "Critical Evidence"
            ),
            states
        )
    }

    @Test
    fun `sample findings cover severity and confidence ladders`() {
        val severities = DebugEvidencePreviewSamples.findings.map { it.severity }.toSet()
        val confidences = DebugEvidencePreviewSamples.findings.map { it.confidence }.toSet()

        assertTrue(PreviewFindingSeverity.entries.all { it in severities })
        assertTrue(PreviewConfidence.entries.all { it in confidences })
    }
}
