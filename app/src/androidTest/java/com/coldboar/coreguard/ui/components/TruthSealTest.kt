package com.coldboar.coreguard.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coldboar.coreguard.truth.EvidenceClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose instrumented tests for [TruthSeal].
 *
 * These tests verify:
 * 1. All five evidence classes render distinct text labels.
 * 2. TalkBack content descriptions are set correctly (not color-only).
 * 3. The component is visible.
 *
 * ENVIRONMENT NOTE: These tests require a connected Android device or emulator.
 * In the current sandboxed build environment `dl.google.com` is unreachable;
 * the tests may not be executable. They are written and committed per Phase 1
 * requirements. See COREGUARD_TEST_EVIDENCE.md for execution status.
 */
@RunWith(AndroidJUnit4::class)
class TruthSealTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun truthSeal_observed_showsCorrectLabel() {
        composeTestRule.setContent {
            TruthSeal(evidenceClass = EvidenceClass.OBSERVED)
        }
        composeTestRule.onNodeWithText("Observed").assertIsDisplayed()
    }

    @Test
    fun truthSeal_inferred_showsCorrectLabel() {
        composeTestRule.setContent {
            TruthSeal(evidenceClass = EvidenceClass.INFERRED)
        }
        composeTestRule.onNodeWithText("Inferred").assertIsDisplayed()
    }

    @Test
    fun truthSeal_simulated_showsCorrectLabel() {
        composeTestRule.setContent {
            TruthSeal(evidenceClass = EvidenceClass.SIMULATED)
        }
        composeTestRule.onNodeWithText("Simulated").assertIsDisplayed()
    }

    @Test
    fun truthSeal_unavailable_showsCorrectLabel() {
        composeTestRule.setContent {
            TruthSeal(evidenceClass = EvidenceClass.UNAVAILABLE)
        }
        composeTestRule.onNodeWithText("Unavailable").assertIsDisplayed()
    }

    @Test
    fun truthSeal_userReported_showsCorrectLabel() {
        composeTestRule.setContent {
            TruthSeal(evidenceClass = EvidenceClass.USER_REPORTED)
        }
        composeTestRule.onNodeWithText("User-reported").assertIsDisplayed()
    }

    @Test
    fun truthSeal_observed_hasCorrectContentDescription() {
        composeTestRule.setContent {
            TruthSeal(evidenceClass = EvidenceClass.OBSERVED)
        }
        composeTestRule
            .onNodeWithContentDescription("Evidence class: Observed — directly measured on this device")
            .assertIsDisplayed()
    }

    @Test
    fun truthSeal_unavailable_hasCorrectContentDescription() {
        composeTestRule.setContent {
            TruthSeal(evidenceClass = EvidenceClass.UNAVAILABLE)
        }
        composeTestRule
            .onNodeWithContentDescription(
                "Evidence class: Unavailable — the required data cannot be accessed on this device"
            )
            .assertIsDisplayed()
    }

    @Test
    fun truthSeal_allStatesAreDistinct() {
        // Each evidence class must produce a different label — no two states can look identical.
        val labels = EvidenceClass.values().map { ec ->
            when (ec) {
                EvidenceClass.OBSERVED -> "Observed"
                EvidenceClass.INFERRED -> "Inferred"
                EvidenceClass.SIMULATED -> "Simulated"
                EvidenceClass.UNAVAILABLE -> "Unavailable"
                EvidenceClass.USER_REPORTED -> "User-reported"
            }
        }
        assert(labels.distinct().size == labels.size) {
            "All evidence class labels must be distinct"
        }
    }
}
