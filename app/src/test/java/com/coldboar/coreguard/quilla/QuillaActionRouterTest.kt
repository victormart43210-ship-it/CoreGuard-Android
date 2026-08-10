package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.ui.navigation.CoreGuardRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuillaActionRouterTest {

    @Test
    fun `navigable actions become Navigate when host can navigate`() {
        assertEquals(
            QuillaActionOutcome.Navigate(CoreGuardRoute.Scanner.route),
            QuillaActionRouter.resolve(QuillaActionSuggestion.RUN_SCAN, canNavigate = true)
        )
        assertEquals(
            QuillaActionOutcome.Navigate(CoreGuardRoute.Shield.route),
            QuillaActionRouter.resolve(QuillaActionSuggestion.OPEN_SHIELD, canNavigate = true)
        )
        assertEquals(
            QuillaActionOutcome.Navigate(CoreGuardRoute.Timeline.route),
            QuillaActionRouter.resolve(QuillaActionSuggestion.OPEN_TIMELINE, canNavigate = true)
        )
    }

    @Test
    fun `navigable actions fall back to prompts when host cannot navigate`() {
        assertEquals(
            QuillaActionOutcome.AskPrompt(QuillaActionRouter.FALLBACK_SCAN_PROMPT),
            QuillaActionRouter.resolve(QuillaActionSuggestion.RUN_SCAN, canNavigate = false)
        )
        assertEquals(
            QuillaActionOutcome.AskPrompt(QuillaActionRouter.FALLBACK_SHIELD_PROMPT),
            QuillaActionRouter.resolve(QuillaActionSuggestion.OPEN_SHIELD, canNavigate = false)
        )
        assertEquals(
            QuillaActionOutcome.AskPrompt(QuillaActionRouter.FALLBACK_TIMELINE_PROMPT),
            QuillaActionRouter.resolve(QuillaActionSuggestion.OPEN_TIMELINE, canNavigate = false)
        )
    }

    @Test
    fun `sync intel is always a prompt and never a silent success route`() {
        val withNav = QuillaActionRouter.resolve(
            QuillaActionSuggestion.SYNC_INTEL,
            canNavigate = true
        )
        val withoutNav = QuillaActionRouter.resolve(
            QuillaActionSuggestion.SYNC_INTEL,
            canNavigate = false
        )
        assertEquals(QuillaActionOutcome.AskPrompt(QuillaActionRouter.SYNC_INTEL_PROMPT), withNav)
        assertEquals(withNav, withoutNav)
        assertTrue(withNav !is QuillaActionOutcome.Navigate)
    }

    @Test
    fun `unknown action id is ignored`() {
        assertEquals(
            QuillaActionOutcome.Ignored,
            QuillaActionRouter.resolve("launch_missile", canNavigate = true)
        )
    }

    @Test
    fun `fallback prompts do not claim silent execution`() {
        listOf(
            QuillaActionRouter.FALLBACK_SCAN_PROMPT,
            QuillaActionRouter.FALLBACK_SHIELD_PROMPT,
            QuillaActionRouter.FALLBACK_TIMELINE_PROMPT,
            QuillaActionRouter.SYNC_INTEL_PROMPT
        ).forEach { prompt ->
            assertTrue(prompt.isNotBlank())
            assertTrue(
                "Prompt should ask how/sync, not claim done: $prompt",
                prompt.startsWith("how ") || prompt.startsWith("sync ")
            )
        }
    }
}
