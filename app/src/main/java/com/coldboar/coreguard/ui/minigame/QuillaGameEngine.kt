package com.coldboar.coreguard.ui.minigame

import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

internal data class Enemy(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var prevX: Float = x,
    var prevY: Float = y,
    val size: Float = 36f,
    val speed: Float = 2.8f,
    val isWorm: Boolean = false,
    val knowledgeId: String = "",
    val label: String = "",
    val tip: String = "",
    val angel: String = ""
)

internal data class Spell(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var prevX: Float = x,
    var prevY: Float = y,
    val speed: Float = 16f
)

internal data class PipeObstacle(
    var x: Float,
    val gapY: Float,
    val gapHeight: Float = 240f,
    val width: Float = 64f,
    var prevX: Float = x,
    /** One-shot gate damage so overlap does not melt shield every frame. */
    var damaged: Boolean = false,
    var scored: Boolean = false,
    val knowledgeId: String = "",
    val label: String = "",
    val tip: String = "",
    val angel: String = ""
)

internal data class PurgeToast(
    val title: String,
    val tip: String,
    val kind: Kind
) {
    enum class Kind { THREAT, GATE }
}

/**
 * Non-Compose physics for the Quilla mini-game.
 *
 * Use [beginFrame] → [fixedTick] (fixed dt) → read [alpha] / lerp helpers for smooth draws.
 */
internal class QuillaGameEngine(
    private val random: Random = Random.Default
) {
    var worldW: Float = 1080f
    var worldH: Float = 1920f

    var quillaY: Float = 400f
    var prevQuillaY: Float = 400f
    var velocityY: Float = 0f
    var score: Int = 0
    var shieldHp: Int = 100
    var gameOver: Boolean = false

    /** 0..1 blend from previous physics state → current for rendering. */
    var alpha: Float = 1f
        private set

    val enemies = ArrayList<Enemy>(16)
    val spells = ArrayList<Spell>(16)
    val pipes = ArrayList<PipeObstacle>(4)

    /** Short titles of purged threats / cleared gates (for Infinity debrief). */
    val purgedTitles = ArrayList<String>(16)

    /** Latest tip toast for the HUD ticker (UI clears after display). */
    var pendingToast: PurgeToast? = null

    private var flavorDeck: List<PurgeFlavorCard> = QuillaPurgeCodex.fallbackDeck()
    private var flavorCursor: Int = 0

    private var spawnAccumulatorMs = 0f
    private var iFramesMs = 0f
    private var graceMs = 0f
    private var timeMs = 0f
    private var accumulatorMs = 0f

    val scrollX: Float
        get() = timeMs * 0.04f

    fun configureFlavor(deck: List<PurgeFlavorCard>) {
        flavorDeck = deck.ifEmpty { QuillaPurgeCodex.fallbackDeck() }
        flavorCursor = 0
    }

    fun reset() {
        score = 0
        shieldHp = 100
        velocityY = 0f
        quillaY = worldH * 0.45f
        prevQuillaY = quillaY
        enemies.clear()
        spells.clear()
        pipes.clear()
        purgedTitles.clear()
        pendingToast = null
        flavorCursor = 0
        spawnAccumulatorMs = 0f
        iFramesMs = 0f
        graceMs = GRACE_MS
        timeMs = 0f
        accumulatorMs = 0f
        alpha = 1f
        gameOver = false
        if (worldW > 1f && worldH > 1f) {
            val startGap = (worldH * 0.28f).coerceIn(220f, 320f)
            val gapY = (quillaY - startGap / 2f).coerceIn(worldH * 0.12f, worldH * 0.5f)
            pipes.add(makePipe(worldW * 0.95f, gapY, startGap))
            pipes.add(makePipe(worldW * 1.55f, worldH * 0.32f, startGap))
        }
    }

    fun jumpAndCast(quillaX: Float = QUILLA_X) {
        if (gameOver) return
        velocityY = JUMP_POWER
        if (spells.size < MAX_SPELLS) {
            val y = renderQuillaY()
            spells.add(Spell(x = quillaX + 24f, y = y, prevX = quillaX + 24f, prevY = y))
        }
    }

    fun renderQuillaY(): Float = lerp(prevQuillaY, quillaY, alpha)

    fun renderPipeX(pipe: PipeObstacle): Float = lerp(pipe.prevX, pipe.x, alpha)

    fun renderEnemyX(enemy: Enemy): Float = lerp(enemy.prevX, enemy.x, alpha)

    fun renderEnemyY(enemy: Enemy): Float = lerp(enemy.prevY, enemy.y, alpha)

    fun renderSpellX(spell: Spell): Float = lerp(spell.prevX, spell.x, alpha)

    fun renderSpellY(spell: Spell): Float = lerp(spell.prevY, spell.y, alpha)

    val invulnerable: Boolean
        get() = iFramesMs > 0f || graceMs > 0f

    /** Test-only: skip opening grace so collision assertions are immediate. */
    internal fun clearGraceForTests() {
        graceMs = 0f
    }

    /**
     * Advance by wall-clock [frameDtMs]. Runs zero or more fixed physics steps.
     * Returns true when HUD-facing fields changed.
     */
    fun beginFrame(frameDtMs: Float): Boolean {
        if (gameOver || worldW < 2f || worldH < 2f) {
            alpha = 1f
            return false
        }

        val frameDt = frameDtMs.coerceIn(0f, MAX_FRAME_DT_MS)
        accumulatorMs = (accumulatorMs + frameDt).coerceAtMost(MAX_ACCUMULATOR_MS)

        var hudChanged = false
        while (accumulatorMs >= FIXED_DT_MS) {
            hudChanged = fixedTick(FIXED_DT_MS) || hudChanged
            accumulatorMs -= FIXED_DT_MS
            if (gameOver) {
                alpha = 1f
                return true
            }
        }
        alpha = (accumulatorMs / FIXED_DT_MS).coerceIn(0f, 1f)
        return hudChanged
    }

    /** @deprecated Prefer [beginFrame]; kept for unit tests of a single step. */
    fun tick(dtMs: Float): Boolean {
        accumulatorMs = 0f
        alpha = 1f
        return fixedTick(dtMs.coerceIn(MIN_DT_MS, MAX_DT_MS))
    }

    private fun fixedTick(dt: Float): Boolean {
        val beforeScore = score
        val beforeShield = shieldHp
        val hadToast = pendingToast != null

        // Snapshot previous positions for interpolation.
        prevQuillaY = quillaY
        for (p in pipes) p.prevX = p.x
        for (e in enemies) {
            e.prevX = e.x
            e.prevY = e.y
        }
        for (s in spells) {
            s.prevX = s.x
            s.prevY = s.y
        }

        timeMs += dt
        if (iFramesMs > 0f) iFramesMs = (iFramesMs - dt).coerceAtLeast(0f)
        if (graceMs > 0f) graceMs = (graceMs - dt).coerceAtLeast(0f)

        velocityY = (velocityY + GRAVITY).coerceIn(-MAX_UP_SPEED, MAX_FALL_SPEED)
        quillaY += velocityY

        val topPad = 48f
        val bottomPad = 48f
        if (quillaY < topPad) {
            quillaY = topPad
            if (velocityY < 0f) velocityY = 0f
        }
        if (quillaY > worldH - bottomPad) {
            shieldHp = 0
            gameOver = true
            return true
        }

        spawnAccumulatorMs += dt
        if (spawnAccumulatorMs >= SPAWN_EVERY_MS && enemies.size < MAX_ENEMIES) {
            spawnAccumulatorMs = 0f
            spawnEnemy()
        }

        var si = 0
        while (si < spells.size) {
            val s = spells[si]
            s.x += s.speed
            if (s.x > worldW + 40f) spells.removeAt(si) else si++
        }

        for (i in pipes.indices) {
            val p = pipes[i]
            p.x -= PIPE_SPEED
            if (p.x < -p.width) {
                val gapH = (worldH * 0.26f).coerceIn(200f, 300f)
                val newX = worldW + 40f
                val gapY = random.nextFloat() * max(1f, worldH * 0.42f) + worldH * 0.12f
                pipes[i] = makePipe(newX, gapY, gapH)
                continue
            }
            if (!p.scored && p.x + p.width < QUILLA_X - 10f) {
                p.scored = true
                score += 5
                notePurge(p.label, p.tip, PurgeToast.Kind.GATE)
            }
            // Hitbox uses Quilla body radius, not hat tip.
            val bodyY = quillaY
            val inX = p.x < QUILLA_X + HIT_RADIUS && p.x + p.width > QUILLA_X - HIT_RADIUS
            val inGap = bodyY > p.gapY + HIT_RADIUS && bodyY < p.gapY + p.gapHeight - HIT_RADIUS
            if (!p.damaged && inX && !inGap && graceMs <= 0f) {
                p.damaged = true
                applyDamage(PIPE_DAMAGE)
            }
        }

        var ei = 0
        while (ei < enemies.size) {
            val enemy = enemies[ei]
            enemy.x -= enemy.speed

            val hitSpellIndex = spells.indexOfFirst { spell ->
                val dx = spell.x - enemy.x
                val dy = spell.y - enemy.y
                dx * dx + dy * dy < 1400f
            }
            if (hitSpellIndex >= 0) {
                spells.removeAt(hitSpellIndex)
                notePurge(enemy.label, enemy.tip, PurgeToast.Kind.THREAT)
                enemies.removeAt(ei)
                score += 10
                continue
            }

            val dx = QUILLA_X - enemy.x
            val dy = quillaY - enemy.y
            if (dx * dx + dy * dy < 1600f && graceMs <= 0f) {
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

        return gameOver || score != beforeScore || shieldHp != beforeShield ||
            pendingToast != null && !hadToast
    }

    private fun spawnEnemy() {
        val card = nextFlavor()
        val spawnY = random.nextFloat() * max(1f, worldH - 160f) + 80f
        val x = worldW + 40f
        enemies.add(
            Enemy(
                x = x,
                y = spawnY,
                prevX = x,
                prevY = spawnY,
                isWorm = card.isWorm,
                knowledgeId = card.id,
                label = card.shortLabel,
                tip = card.tip,
                angel = card.angel
            )
        )
    }

    private fun makePipe(x: Float, gapY: Float, gapH: Float): PipeObstacle {
        val card = nextFlavor()
        return PipeObstacle(
            x = x,
            gapY = gapY,
            gapHeight = gapH,
            prevX = x,
            knowledgeId = card.id,
            label = card.shortLabel,
            tip = card.tip,
            angel = card.angel
        )
    }

    private fun nextFlavor(): PurgeFlavorCard {
        val card = QuillaPurgeCodex.nextCard(flavorDeck, flavorCursor)
        flavorCursor++
        return card
    }

    private fun notePurge(label: String, tip: String, kind: PurgeToast.Kind) {
        val title = label.ifBlank {
            if (kind == PurgeToast.Kind.GATE) "Vault gate" else "Threat"
        }
        if (title.isNotBlank() && purgedTitles.none { it.equals(title, ignoreCase = true) }) {
            purgedTitles += title
            if (purgedTitles.size > 12) purgedTitles.removeAt(0)
        }
        if (tip.isNotBlank()) {
            pendingToast = PurgeToast(title = title, tip = tip, kind = kind)
        }
    }

    private fun applyDamage(amount: Int) {
        if (gameOver || iFramesMs > 0f || graceMs > 0f) return
        shieldHp = (shieldHp - amount).coerceAtLeast(0)
        iFramesMs = IFRAMES_MS
        if (shieldHp <= 0) gameOver = true
    }

    companion object {
        const val QUILLA_X = 200f
        const val HIT_RADIUS = 22f
        const val GRAVITY = 0.42f
        const val JUMP_POWER = -9.5f
        const val MAX_FALL_SPEED = 11f
        const val MAX_UP_SPEED = 12f
        const val PIPE_SPEED = 3.2f
        const val FIXED_DT_MS = 1000f / 60f
        const val REF_DT_MS = 16f
        const val MIN_DT_MS = 8f
        const val MAX_DT_MS = 33f
        const val MAX_FRAME_DT_MS = 50f
        const val MAX_ACCUMULATOR_MS = 100f
        const val SPAWN_EVERY_MS = 2000f
        const val MAX_ENEMIES = 6
        const val MAX_SPELLS = 5
        const val PIPE_DAMAGE = 20
        const val ENEMY_DAMAGE = 15
        const val IFRAMES_MS = 700f
        const val GRACE_MS = 1400f

        fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    }
}
