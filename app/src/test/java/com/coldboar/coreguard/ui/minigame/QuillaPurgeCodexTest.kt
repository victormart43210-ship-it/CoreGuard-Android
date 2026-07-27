package com.coldboar.coreguard.ui.minigame

import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class QuillaPurgeCodexTest {

    @Before
    fun clear() {
        CyberKnowledgeBase.clear()
    }

    @Test
    fun fallbackDeck_isNonEmpty() {
        val deck = QuillaPurgeCodex.fallbackDeck()
        assertTrue(deck.size >= 4)
        assertTrue(deck.any { it.isWorm })
        assertTrue(deck.any { !it.isWorm })
    }

    @Test
    fun buildDeck_fromCodex_usesDefenseTips() {
        CyberKnowledgeBase.loadDocuments(
            listOf(
                """
                {"entries":[
                  {"id":"overlay-phish","title":"Overlay phishing","category":"threat",
                   "tags":["overlay","phishing"],"summary":"Fake UI.",
                   "body":"Attackers draw over apps.","defense":"Reject unexpected overlays."},
                  {"id":"dns-c2","title":"DNS C2 beacon","category":"network",
                   "tags":["dns","c2"],"summary":"Beaconing.",
                   "body":"Malware uses DNS.","defense":"Review odd DNS traffic."}
                ]}
                """.trimIndent()
            )
        )
        val deck = QuillaPurgeCodex.buildDeck(CyberKnowledgeBase.allEntries(), Random(1))
        assertEquals(2, deck.size)
        assertTrue(deck.any { it.id == "overlay-phish" && !it.isWorm })
        assertTrue(deck.any { it.id == "dns-c2" && it.isWorm })
        assertTrue(deck.first { it.id == "overlay-phish" }.tip.contains("overlays", ignoreCase = true))
    }

    @Test
    fun levelTitle_reflectsScoreAndGeneration() {
        assertTrue(QuillaPurgeCodex.levelTitle(0, 0).contains("Level 1-1"))
        assertTrue(QuillaPurgeCodex.levelTitle(3, 0).contains("gen 3"))
        assertTrue(QuillaPurgeCodex.levelTitle(3, 0).contains("Keep 1"))
        assertTrue(QuillaPurgeCodex.levelTitle(3, 55).contains("Keep 4"))
    }

    @Test
    fun debrief_includesHonestyLine() {
        val lines = QuillaPurgeCodex.debriefLines(listOf("Overlay phishing", "DNS C2"), 2)
        assertTrue(lines.any { it.contains("Purged:") })
        assertTrue(lines.any { it.contains("Infinity gen 2") })
        assertTrue(lines.any { it.contains("not live Scanner", ignoreCase = true) })
    }

    @Test
    fun nextCard_wrapsDeck() {
        val deck = QuillaPurgeCodex.fallbackDeck()
        assertEquals(deck[0], QuillaPurgeCodex.nextCard(deck, 0))
        assertEquals(deck[0], QuillaPurgeCodex.nextCard(deck, deck.size))
        assertFalse(QuillaPurgeCodex.nextCard(emptyList(), 0).id.isBlank())
    }

    @Test
    fun engine_notesPurgeToast_fromFlavor() {
        val engine = QuillaGameEngine(Random(9)).apply {
            worldW = 800f
            worldH = 600f
            configureFlavor(
                listOf(
                    PurgeFlavorCard(
                        id = "t1",
                        title = "Overlay phishing",
                        tip = "Reject unexpected overlays.",
                        isWorm = false,
                        angel = "Sandalphon",
                        shortLabel = "Overlay"
                    )
                )
            )
            reset()
            clearGraceForTests()
            enemies.clear()
            enemies.add(
                Enemy(
                    x = QuillaGameEngine.QUILLA_X + 10f,
                    y = 300f,
                    label = "Overlay",
                    tip = "Reject unexpected overlays.",
                    isWorm = false
                )
            )
            spells.clear()
            spells.add(Spell(x = QuillaGameEngine.QUILLA_X + 10f, y = 300f))
        }
        engine.tick(QuillaGameEngine.FIXED_DT_MS)
        assertEquals(10, engine.score)
        assertEquals("Overlay", engine.pendingToast?.title)
        assertTrue(engine.purgedTitles.contains("Overlay"))
    }
}
