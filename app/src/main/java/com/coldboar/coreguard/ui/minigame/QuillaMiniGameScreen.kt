package com.coldboar.coreguard.ui.minigame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

// Teal cyber-medieval palette (local to the easter-egg game).
private val TealBackground = Color(0xFF031A1C)
private val TealPrimary = Color(0xFF00E5FF)
private val TealDark = Color(0xFF005B66)
private val GoldAccent = Color(0xFFFFD700)
private val MalwareRed = Color(0xFFFF3366)
private val GridColor = Color(0xFF003840)

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
    val x: Float,
    val gapY: Float,
    val gapHeight: Float = 240f,
    val width: Float = 70f
)

/**
 * Hidden Quilla purge mini-game — Flappy-style flight + spell shots.
 * Educational / fun only; not a security claim or detector.
 */
@Composable
fun QuillaMiniGameScreen(
    onDismiss: () -> Unit
) {
    var worldW by remember { mutableFloatStateOf(1080f) }
    var worldH by remember { mutableFloatStateOf(1920f) }

    var quillaY by remember { mutableFloatStateOf(400f) }
    var velocityY by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var shieldHp by remember { mutableIntStateOf(100) }
    var gameOver by remember { mutableStateOf(false) }

    val gravity = 0.65f
    val jumpPower = -12f
    val quillaX = 200f

    val enemies = remember { mutableStateListOf<Enemy>() }
    val spells = remember { mutableStateListOf<Spell>() }
    val pipes = remember { mutableStateListOf<PipeObstacle>() }

    fun resetRun() {
        shieldHp = 100
        score = 0
        quillaY = worldH * 0.35f
        velocityY = 0f
        enemies.clear()
        spells.clear()
        pipes.clear()
        pipes.add(PipeObstacle(x = worldW * 0.75f, gapY = worldH * 0.25f))
        pipes.add(PipeObstacle(x = worldW * 1.2f, gapY = worldH * 0.4f))
        gameOver = false
    }

    LaunchedEffect(worldW, worldH) {
        if (pipes.isEmpty() && worldW > 1f && worldH > 1f) {
            resetRun()
        }
    }

    LaunchedEffect(gameOver, worldW, worldH) {
        if (gameOver || worldW < 2f || worldH < 2f) return@LaunchedEffect

        var lastSpawn = System.currentTimeMillis()

        while (isActive) {
            withFrameNanos {
                velocityY += gravity
                quillaY += velocityY

                if (quillaY < 0f) {
                    quillaY = 0f
                    velocityY = 0f
                }
                if (quillaY > worldH) {
                    shieldHp = 0
                    gameOver = true
                    return@withFrameNanos
                }

                val now = System.currentTimeMillis()
                if (now - lastSpawn > 1800) {
                    val spawnY = Random.nextFloat() * max(1f, worldH - 120f) + 60f
                    enemies.add(
                        Enemy(
                            x = worldW + 40f,
                            y = spawnY,
                            isWorm = Random.nextBoolean()
                        )
                    )
                    lastSpawn = now
                }

                val spellIterator = spells.iterator()
                while (spellIterator.hasNext()) {
                    val s = spellIterator.next()
                    s.x += s.speed
                    if (s.x > worldW + 40f) spellIterator.remove()
                }

                for (i in pipes.indices) {
                    val p = pipes[i]
                    val newX = p.x - 4f
                    val active = if (newX < -p.width) {
                        score += 5
                        PipeObstacle(
                            x = worldW + 40f,
                            gapY = Random.nextFloat() * max(1f, worldH * 0.45f) + worldH * 0.12f,
                            gapHeight = (worldH * 0.22f).coerceIn(180f, 280f)
                        )
                    } else {
                        p.copy(x = newX)
                    }
                    pipes[i] = active

                    if (active.x < quillaX + 20f && active.x + active.width > quillaX - 20f) {
                        if (quillaY < active.gapY || quillaY > active.gapY + active.gapHeight) {
                            shieldHp -= 2
                            if (shieldHp <= 0) gameOver = true
                        }
                    }
                }

                val enemyIterator = enemies.iterator()
                while (enemyIterator.hasNext()) {
                    val enemy = enemyIterator.next()
                    enemy.x -= enemy.speed

                    val hitBySpell = spells.find { spell ->
                        val dx = spell.x - enemy.x
                        val dy = spell.y - enemy.y
                        dx * dx + dy * dy < 1600f
                    }
                    if (hitBySpell != null) {
                        spells.remove(hitBySpell)
                        enemyIterator.remove()
                        score += 10
                        continue
                    }

                    val dx = quillaX - enemy.x
                    val dy = quillaY - enemy.y
                    if (dx * dx + dy * dy < 1800f) {
                        shieldHp -= 15
                        enemyIterator.remove()
                        if (shieldHp <= 0) gameOver = true
                    } else if (enemy.x < -50f) {
                        enemyIterator.remove()
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TealBackground)
            .onSizeChanged {
                worldW = it.width.toFloat().coerceAtLeast(2f)
                worldH = it.height.toFloat().coerceAtLeast(2f)
            }
            .semantics { contentDescription = "Quilla mini-game. Tap to jump and cast." }
            .clickable(enabled = !gameOver) {
                velocityY = jumpPower
                spells.add(Spell(x = quillaX + 20f, y = quillaY))
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCyberGrid()
            pipes.forEach { drawSecurityGate(it) }
            spells.forEach {
                drawCircle(color = TealPrimary, radius = 10f, center = Offset(it.x, it.y))
            }
            enemies.forEach { drawMalware(it) }
            drawQuilla(y = quillaY, x = quillaX)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LEVEL 1-3: THE AUDIT KEEP",
                    color = TealPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "SHIELD: $shieldHp%",
                    color = if (shieldHp < 30) MalwareRed else TealPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(60.dp, 40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(4.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = TealDark, style = Stroke(2f))
                    drawCircle(color = TealPrimary, radius = 3f, center = Offset(15f, 15f))
                    drawRect(color = GoldAccent, topLeft = Offset(35f, 10f), size = Size(10f, 10f))
                }
            }

            Text(
                text = "DATA SHARDS: $score",
                color = GoldAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close mini-game",
                    tint = TealPrimary
                )
            }
        }

        if (gameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) { },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PURGE FAILED",
                        color = MalwareRed,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "FINAL SCORE: $score",
                        color = GoldAccent,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .background(TealPrimary, RoundedCornerShape(8.dp))
                            .clickable { resetRun() }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "RESTART PURGE",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .background(TealDark, RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "EXIT",
                            color = TealPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawCyberGrid() {
    val gridSize = 60f
    var x = 0f
    while (x < size.width) {
        drawLine(
            color = GridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f
        )
        x += gridSize
    }
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = GridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += gridSize
    }
}

private fun DrawScope.drawSecurityGate(gate: PipeObstacle) {
    drawRect(
        color = TealDark,
        topLeft = Offset(gate.x, 0f),
        size = Size(gate.width, gate.gapY)
    )
    drawRect(
        color = TealPrimary,
        topLeft = Offset(gate.x, 0f),
        size = Size(gate.width, gate.gapY),
        style = Stroke(3f)
    )

    val bottomY = gate.gapY + gate.gapHeight
    drawRect(
        color = TealDark,
        topLeft = Offset(gate.x, bottomY),
        size = Size(gate.width, size.height - bottomY)
    )
    drawRect(
        color = TealPrimary,
        topLeft = Offset(gate.x, bottomY),
        size = Size(gate.width, size.height - bottomY),
        style = Stroke(3f)
    )
}

private fun DrawScope.drawQuilla(y: Float, x: Float) {
    drawPath(
        path = Path().apply {
            moveTo(x - 25f, y + 30f)
            lineTo(x + 20f, y + 30f)
            lineTo(x + 15f, y - 10f)
            lineTo(x - 20f, y - 10f)
            close()
        },
        color = TealDark
    )

    drawCircle(color = Color.Black, radius = 18f, center = Offset(x, y - 15f))
    drawCircle(color = TealPrimary, radius = 4f, center = Offset(x - 5f, y - 15f))
    drawCircle(color = TealPrimary, radius = 4f, center = Offset(x + 5f, y - 15f))

    val hat = Path().apply {
        moveTo(x - 35f, y - 25f)
        lineTo(x + 35f, y - 25f)
        lineTo(x + 5f, y - 65f)
        close()
    }
    drawPath(path = hat, color = TealBackground)
    drawPath(path = hat, color = TealPrimary, style = Stroke(3f))
    drawCircle(color = GoldAccent, radius = 4f, center = Offset(x, y - 35f))

    drawLine(
        color = GoldAccent,
        start = Offset(x + 18f, y + 25f),
        end = Offset(x + 22f, y - 30f),
        strokeWidth = 4f
    )
    drawCircle(color = TealPrimary, radius = 8f, center = Offset(x + 22f, y - 35f))
}

private fun DrawScope.drawMalware(enemy: Enemy) {
    if (enemy.isWorm) {
        drawCircle(color = MalwareRed, radius = 16f, center = Offset(enemy.x, enemy.y))
        drawCircle(color = MalwareRed, radius = 12f, center = Offset(enemy.x + 18f, enemy.y + 4f))
        drawCircle(color = MalwareRed, radius = 8f, center = Offset(enemy.x + 32f, enemy.y - 2f))
    } else {
        drawCircle(
            color = MalwareRed,
            radius = enemy.size / 2,
            center = Offset(enemy.x, enemy.y)
        )
        drawCircle(
            color = MalwareRed,
            radius = enemy.size / 2 + 6f,
            center = Offset(enemy.x, enemy.y),
            style = Stroke(3f)
        )
    }
}
