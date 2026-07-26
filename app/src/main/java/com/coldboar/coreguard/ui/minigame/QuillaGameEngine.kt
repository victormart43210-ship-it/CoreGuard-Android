package com.coldboar.coreguard.ui.minigame

import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

internal data class Enemy(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    val size: Float = 40f,
    val speed: Float = 3f,
    val isWorm: Boolean = false
)

internal data class Spell(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    val speed: Float = 14f
)

internal data class PipeObstacle(
    var x: Float,
    val gapY: Float,
    val gapHeight: Float = 240f,
    val width: Float = 70f,
    /** One-shot gate damage so overlap does not melt shield every frame. */
    var damaged: Boolean = false,
    var scored: Boolean = false
)

/**
 * Non-Compose physics for the Quilla mini-game.
 * Kept off Snapshot state so the UI can invalidate once per frame without thrashing.
 */
internal class QuillaGameEngine(
    private val random: Random = Random.Default
) {
    var worldW: Float = 1080f
    var worldH: Float = 1920f

    var quillaY: Float = 400f
    var velocityY: Float = 0f
    var score: Int = 0
    var shieldHp: Int = 100
    var gameOver: Boolean = false

    val enemies = ArrayList<Enemy>(16)
    val spells = ArrayList<Spell>(16)
    val pipes = ArrayList<PipeObstacle>(4)

    private var spawnAccumulatorMs = 0f
    private var iFramesMs = 0f

    fun reset() {
        score = 0
        shieldHp = 100
        velocityY = 0f
        quillaY = worldH * 0.45f
        enemies.clear()
        spells.clear()
        pipes.clear()
        spawnAccumulatorMs = 0f
        iFramesMs = 0f
        gameOver = false
        if (worldW > 1f && worldH > 1f) {
            val startGap = (worldH * 0.22f).coerceIn(180f, 280f)
            pipes.add(
                PipeObstacle(
                    x = worldW * 0.85f,
                    gapY = (quillaY - startGap / 2f).coerceIn(worldH * 0.1f, worldH * 0.55f),
                    gapHeight = startGap
                )
            )
            pipes.add(
                PipeObstacle(
                    x = worldW * 1.35f,
                    gapY = worldH * 0.35f,
                    gapHeight = startGap
                )
            )
        }
    }

    fun jumpAndCast(quillaX: Float = QUILLA_X) {
        if (gameOver) return
        velocityY = JUMP_POWER
        if (spells.size < MAX_SPELLS) {
            spells.add(Spell(x = quillaX + 20f, y = quillaY))
        }
    }

    /**
     * Advance simulation by [dtMs] (clamped). Returns true when HUD-facing fields changed.
     */
    fun tick(dtMs: Float): Boolean {
        if (gameOver || worldW < 2f || worldH < 2f) return false

        val dt = dtMs.coerceIn(MIN_DT_MS, MAX_DT_MS)
        val scale = dt / REF_DT_MS
        val beforeScore = score
        val beforeShield = shieldHp

        if (iFramesMs > 0f) iFramesMs = (iFramesMs - dt).coerceAtLeast(0f)

        velocityY += GRAVITY * scale
        quillaY += velocityY * scale

        if (quillaY < 0f) {
            quillaY = 0f
            velocityY = 0f
        }
        if (quillaY > worldH) {
            shieldHp = 0
            gameOver = true
            return true
        }

        spawnAccumulatorMs += dt
        if (spawnAccumulatorMs >= SPAWN_EVERY_MS && enemies.size < MAX_ENEMIES) {
            spawnAccumulatorMs = 0f
            val spawnY = random.nextFloat() * max(1f, worldH - 120f) + 60f
            enemies.add(
                Enemy(
                    x = worldW + 40f,
                    y = spawnY,
                    isWorm = random.nextBoolean()
                )
            )
        }

        var si = 0
        while (si < spells.size) {
            val s = spells[si]
            s.x += s.speed * scale
            if (s.x > worldW + 40f) spells.removeAt(si) else si++
        }

        for (i in pipes.indices) {
            val p = pipes[i]
            p.x -= PIPE_SPEED * scale
            if (p.x < -p.width) {
                pipes[i] = PipeObstacle(
                    x = worldW + 40f,
                    gapY = random.nextFloat() * max(1f, worldH * 0.45f) + worldH * 0.12f,
                    gapHeight = (worldH * 0.22f).coerceIn(180f, 280f)
                )
                continue
            }
            if (!p.scored && p.x + p.width < QUILLA_X) {
                p.scored = true
                score += 5
            }
            if (
                !p.damaged &&
                p.x < QUILLA_X + 20f &&
                p.x + p.width > QUILLA_X - 20f &&
                (quillaY < p.gapY || quillaY > p.gapY + p.gapHeight)
            ) {
                p.damaged = true
                applyDamage(PIPE_DAMAGE)
            }
        }

        var ei = 0
        while (ei < enemies.size) {
            val enemy = enemies[ei]
            enemy.x -= enemy.speed * scale

            val hitSpellIndex = spells.indexOfFirst { spell ->
                val dx = spell.x - enemy.x
                val dy = spell.y - enemy.y
                dx * dx + dy * dy < 1600f
            }
            if (hitSpellIndex >= 0) {
                spells.removeAt(hitSpellIndex)
                enemies.removeAt(ei)
                score += 10
                continue
            }

            val dx = QUILLA_X - enemy.x
            val dy = quillaY - enemy.y
            if (dx * dx + dy * dy < 1800f) {
                enemies.removeAt(ei)
                applyDamage(ENEMY_DAMAGE)
                continue
            }
            if (enemy.x < -50f) {
                enemies.removeAt(ei)
                continue
            }
            ei++
        }

        return gameOver || score != beforeScore || shieldHp != beforeShield
    }

    private fun applyDamage(amount: Int) {
        if (gameOver || iFramesMs > 0f) return
        shieldHp = (shieldHp - amount).coerceAtLeast(0)
        iFramesMs = IFRAMES_MS
        if (shieldHp <= 0) gameOver = true
    }

    companion object {
        const val QUILLA_X = 200f
        const val GRAVITY = 0.65f
        const val JUMP_POWER = -12f
        const val PIPE_SPEED = 4f
        const val REF_DT_MS = 16f
        const val MIN_DT_MS = 8f
        const val MAX_DT_MS = 33f
        const val SPAWN_EVERY_MS = 1800f
        const val MAX_ENEMIES = 8
        const val MAX_SPELLS = 6
        const val PIPE_DAMAGE = 20
        const val ENEMY_DAMAGE = 15
        const val IFRAMES_MS = 600f
    }
}
