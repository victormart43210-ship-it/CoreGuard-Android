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
            pipes.clear()
            pipes.add(
                PipeObstacle(
                    x = QuillaGameEngine.QUILLA_X - 10f,
                    gapY = 10f,
                    gapHeight = 20f // quilla starts ~350 — outside gap
                )
            )
            quillaY = 400f
            velocityY = 0f
        }

        engine.tick(16f)
        assertEquals(80, engine.shieldHp)
        assertTrue(engine.pipes.first().damaged)

        engine.tick(16f)
        engine.tick(16f)
        assertEquals("i-frames + one-shot pipe damage", 80, engine.shieldHp)
    }

    @Test
    fun fallingBelowWorld_endsGame() {
        val engine = QuillaGameEngine(Random(1)).apply {
            worldW = 800f
            worldH = 600f
            reset()
            quillaY = 590f
            velocityY = 40f
        }
        engine.tick(16f)
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
}
