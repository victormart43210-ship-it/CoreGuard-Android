package com.coldboar.coreguard.lore

import com.coldboar.coreguard.quilla.QuillaIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnochianWatchtowersTest {

    @Test
    fun `four quarters and black cross`() {
        assertEquals(4, EnochianWatchtowers.quarters.size)
        assertEquals("Raphael", EnochianWatchtowers.blackCross.angel)
        assertTrue(EnochianWatchtowers.quarters.all { it.king.isNotBlank() && it.senior.isNotBlank() })
    }

    @Test
    fun `matches enochian and king names`() {
        assertEquals("fire_south", EnochianWatchtowers.matchQuarter("edelperna fire tablet")!!.id)
        assertEquals("air_east", EnochianWatchtowers.matchQuarter("enochian watchtowers")!!.id)
    }

    @Test
    fun `intent maps to quarters`() {
        assertEquals(EnochianWatchtowers.Quarter.FIRE_SOUTH, EnochianWatchtowers.quarterFor(QuillaIntent.SCAN))
        assertEquals(EnochianWatchtowers.Quarter.WATER_WEST, EnochianWatchtowers.quarterFor(QuillaIntent.SHIELD))
        assertEquals(EnochianWatchtowers.Quarter.EARTH_NORTH, EnochianWatchtowers.quarterFor(QuillaIntent.TIMELINE))
    }

    @Test
    fun `knowledge answers enochian with disclaimer`() {
        val answer = QuillaKnowledge.answer("explain the enochian watchtowers")
        assertTrue(answer.contains("Bataivah") || answer.contains("Watchtower"))
        assertTrue(answer.contains(EnochianWatchtowers.DISCLAIMER))
        assertNotNull(QuillaKnowledge.matchLivingOrObservatory("bataivah"))
    }

    @Test
    fun `shem and uriel match living geometry`() {
        assertEquals("Lauviah", QuillaLivingGeometry.matchShem("lauviah frida")!!.name)
        assertEquals("Uriel", QuillaLivingGeometry.matchExtendedAngel("uriel earth")!!.name)
        assertTrue(QuillaLivingGeometry.sacredForms.any { it.id == "enochian_tablet" })
        assertTrue(QuillaLivingGeometry.sacredForms.size >= 12)
    }
}
