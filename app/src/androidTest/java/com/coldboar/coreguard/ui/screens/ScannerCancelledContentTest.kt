package com.coldboar.coreguard.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScannerCancelledContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cancelledState_showsIncompleteMessageAndRunNewScanCta() {
        composeRule.setContent {
            CancelledScanContent(
                hasLastCompletedReport = false,
                onRunNewScan = {}
            )
        }

        composeRule.onNodeWithText("Scan cancelled").assertIsDisplayed()
        composeRule.onNodeWithText("Run New Scan").assertIsDisplayed()
        composeRule.onNodeWithText(
            "The scan was cancelled before it could finish. Results are incomplete — no score or verdict has been recorded for this incomplete scan."
        ).assertIsDisplayed()
    }
}
