package com.coldboar.coreguard.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.coldboar.coreguard.truth.EvidenceClass
import com.coldboar.coreguard.ui.theme.CoreGuardTheme
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [TruthSeal] semantics.
 *
 * These tests verify that every [EvidenceClass] value produces a correct
 * a11y content description. The TruthSeal must describe its state in text,
 * not color-only.
 *
 * **Environment note (Phase 1)**: These tests require a Robolectric or real
 * device/emulator environment with an Android runtime. In the CI sandbox with
 * no emulator available, these tests cannot execute. The tests are written and
 * checked in as required by Phase 1 scope. The limitation is recorded in
 * COREGUARD_TEST_EVIDENCE.md.
 */
class TruthSealTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `TruthSeal OBSERVED has correct content description`() {
        composeTestRule.setContent {
            CoreGuardTheme {
                TruthSeal(evidenceClass = EvidenceClass.OBSERVED)
            }
        }
        composeTestRule
            .onNodeWithContentDescription("Evidence class: Observed", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `TruthSeal INFERRED has correct content description`() {
        composeTestRule.setContent {
            CoreGuardTheme {
                TruthSeal(evidenceClass = EvidenceClass.INFERRED)
            }
        }
        composeTestRule
            .onNodeWithContentDescription("Evidence class: Inferred", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `TruthSeal SIMULATED has correct content description`() {
        composeTestRule.setContent {
            CoreGuardTheme {
                TruthSeal(evidenceClass = EvidenceClass.SIMULATED)
            }
        }
        composeTestRule
            .onNodeWithContentDescription("Evidence class: Simulated", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `TruthSeal UNAVAILABLE has correct content description`() {
        composeTestRule.setContent {
            CoreGuardTheme {
                TruthSeal(evidenceClass = EvidenceClass.UNAVAILABLE)
            }
        }
        composeTestRule
            .onNodeWithContentDescription("Evidence class: Unavailable", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `TruthSeal USER_REPORTED has correct content description`() {
        composeTestRule.setContent {
            CoreGuardTheme {
                TruthSeal(evidenceClass = EvidenceClass.USER_REPORTED)
            }
        }
        composeTestRule
            .onNodeWithContentDescription("Evidence class: User reported", substring = true)
            .assertIsDisplayed()
    }
}
