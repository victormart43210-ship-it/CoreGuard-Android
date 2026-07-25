package com.coldboar.coreguard.lore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuillaKnowledgeTest {

    @Test
    fun `matches calendar cycle themes`() {
        val fragment = QuillaKnowledge.matchFragment("how do maya calendar cycles help me read the timeline?")
        assertNotNull(fragment)
        assertEquals("calendar_cycles", fragment!!.id)
    }

    @Test
    fun `matches signal relay themes`() {
        val fragment = QuillaKnowledge.matchFragment("is this dns traffic a lunar relay style c2 echo?")
        assertNotNull(fragment)
        assertEquals("signal_relays", fragment!!.id)
    }

    @Test
    fun `answer includes disclaimer and security mapping`() {
        val answer = QuillaKnowledge.answer("Show me the recovered archives lens for IOC evidence")
        assertTrue(answer.contains("Observatory lens"))
        assertTrue(answer.contains("Security mapping:"))
        assertTrue(answer.contains(ObservatoryCodex.DISCLAIMER))
        assertTrue(answer.contains("Evidence over rumor"))
    }

    @Test
    fun `codex has six original fragments`() {
        assertEquals(6, ObservatoryCodex.fragments.size)
        assertTrue(ObservatoryCodex.fragments.all { it.title.isNotBlank() && it.body.isNotBlank() })
    }
}
