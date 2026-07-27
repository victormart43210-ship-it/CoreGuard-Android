package com.coldboar.coreguard.ui.minigame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuillaGameEngineTest {

    @Test
    fun pipeOverlap_damagesOnce_notEveryFrame() {
        val engine = QuillaGameEngine(Random(0)).apply {
            worldW = 1000f
            worldH = 1000f
            reset()
            clearGraceForTests()
            pipes.clear()
            pipes.add(
                PipeObstacle(
                    x = QuillaGameEngine.QUILLA_X - 10f,
                    gapY = 10f,
                    gapHeight = 20f // body at 400 — outside gap
                )
            )
            quillaY = 400f
            prevQuillaY = 400f
            velocityY = 0f
        }

        engine.tick(QuillaGameEngine.FIXED_DT_MS)
        assertEquals(80, engine.shieldHp)
        assertTrue(engine.pipes.first().damaged)

        engine.tick(QuillaGameEngine.FIXED_DT_MS)
        engine.tick(QuillaGameEngine.FIXED_DT_MS)
        assertEquals("i-frames + one-shot pipe damage", 80, engine.shieldHp)
    }

    @Test
    fun fallingBelowWorld_endsGame() {
        val engine = QuillaGameEngine(Random(1)).apply {
            worldW = 800f
            worldH = 600f
            reset()
            clearGraceForTests()
            quillaY = 560f
            prevQuillaY = 560f
            velocityY = 40f
        }
        engine.tick(QuillaGameEngine.FIXED_DT_MS)
        assertTrue(engine.gameOver)
        assertEquals(0, engine.shieldHp)
    }

    @Test
    fun jumpAndCast_addsSpell_andCaps() {
        val engine = QuillaGameEngine(Random(2)).apply {
            worldW = 800f
            worldH = 600f
            reset()
        }
        repeat(QuillaGameEngine.MAX_SPELLS + 3) {
            engine.jumpAndCast()
        }
        assertEquals(QuillaGameEngine.MAX_SPELLS, engine.spells.size)
        assertFalse(engine.gameOver)
    }

    @Test
    fun beginFrame_interpolatesBetweenFixedSteps() {
        val engine = QuillaGameEngine(Random(3)).apply {
            worldW = 800f
            worldH = 600f
            reset()
            clearGraceForTests()
            pipes.clear()
            enemies.clear()
            velocityY = 0f
            quillaY = 300f
            prevQuillaY = 300f
        }
        // Half of a fixed step → alpha mid-blend, position not fully advanced twice.
        engine.beginFrame(QuillaGameEngine.FIXED_DT_MS * 0.5f)
        assertTrue(engine.alpha in 0.4f..0.6f)
        val midY = engine.renderQuillaY()
        assertTrue(midY >= 300f)
        engine.beginFrame(QuillaGameEngine.FIXED_DT_MS * 0.5f)
        assertTrue(engine.renderQuillaY() > midY - 0.01f)
    }
}
