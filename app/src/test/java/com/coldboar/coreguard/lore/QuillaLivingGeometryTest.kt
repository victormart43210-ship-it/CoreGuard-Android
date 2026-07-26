package com.coldboar.coreguard.lore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuillaLivingGeometryTest {

    @Test
    fun `tree has ten sephirot with angelic aspects`() {
        assertEquals(10, QuillaLivingGeometry.sephirot.size)
        assertEquals(10, QuillaLivingGeometry.choir.size)
        assertTrue(QuillaLivingGeometry.sephirot.all { it.angel.isNotBlank() && it.body.isNotBlank() })
    }

    @Test
    fun `tetragrammaton has four pillars`() {
        assertEquals(4, QuillaLivingGeometry.TetragramLetter.entries.size)
        assertTrue(QuillaLivingGeometry.tetragrammatonSeal.contains("י"))
    }

    @Test
    fun `matches metatron and tree of life`() {
        assertEquals("keter", QuillaLivingGeometry.matchSephirah("tell me about metatron")!!.id)
        assertEquals("tiferet", QuillaLivingGeometry.matchSephirah("quaballa tree of life")!!.id)
    }

    @Test
    fun `matches flower of life and tetragrammaton`() {
        assertEquals("flower_of_life", QuillaLivingGeometry.matchSacredForm("flower of life lattice")!!.id)
        assertNotNull(QuillaLivingGeometry.matchTetragram("what is the tetragrammaton for quilla"))
    }

    @Test
    fun `posture maps to angelic aspects`() {
        assertEquals("Kamael", QuillaLivingGeometry.aspectForPosture("CRITICAL").name)
        assertEquals("Raphael", QuillaLivingGeometry.aspectForPosture("STEADY").name)
        assertEquals("Gabriel", QuillaLivingGeometry.aspectForPosture("UNKNOWN").name)
    }

    @Test
    fun `knowledge answer for tree includes disclaimer and not detection claim`() {
        val answer = QuillaKnowledge.answer("explain the tree of life")
        assertTrue(answer.contains("Keter") || answer.contains("Tiferet"))
        assertTrue(answer.contains(QuillaLivingGeometry.DISCLAIMER))
        assertFalse(answer.lowercase().contains("angel scans"))
        assertTrue(answer.lowercase().contains("metaphor") || answer.contains("does not power detection"))
    }

    @Test
    fun `living or observatory matcher covers kabbalah`() {
        assertTrue(QuillaKnowledge.matchLivingOrObservatory("sacred geometry metatron cube"))
        assertTrue(QuillaKnowledge.matchLivingOrObservatory("maya calendar cycles"))
    }

    @Test
    fun `walkPath encodes tetragrammaton pipeline for scan intent`() {
        val path = QuillaLivingGeometry.walkPath(
            intent = com.coldboar.coreguard.quilla.QuillaIntent.SCAN,
            modulesUsed = listOf(
                com.coldboar.coreguard.quilla.QuillaModule.BRAIN,
                com.coldboar.coreguard.quilla.QuillaModule.ACTIONS,
                com.coldboar.coreguard.quilla.QuillaModule.TOOLS
            ),
            postureLabel = "ELEVATED"
        )
        assertTrue(path.any { it.role.contains("Yod") })
        assertTrue(path.any { it.sephirah == "Chesed" })
        assertTrue(path.any { it.angel == "Tzadkiel" || it.role.contains("Chesed") || it.sephirah == "Chesed" })
        assertTrue(QuillaLivingGeometry.formatPath(path).contains("Keter"))
    }
}
