package com.coldboar.coreguard.guardian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Truth Seal label contract — non-color-only, honest wording. */
class EvidenceClassLabelTest {

    @Test
    fun userLabelsMatchBlueprint() {
        assertEquals("Observed", EvidenceClass.OBSERVED.userLabel)
        assertEquals("Inferred", EvidenceClass.INFERRED.userLabel)
        assertEquals("Simulation", EvidenceClass.SIMULATED.userLabel)
        assertEquals("Unavailable", EvidenceClass.UNAVAILABLE.userLabel)
        assertEquals("User reported", EvidenceClass.USER_REPORTED.userLabel)
    }

    @Test
    fun calmSeverityLabelsAvoidDramaWords() {
        Severity.entries.forEach { sev ->
            val label = sev.userLabel.lowercase()
            assertFalse(label.contains("hacked"))
            assertFalse(label.contains("pegasus"))
            assertFalse(label.contains("spyware confirmed"))
        }
    }
}
