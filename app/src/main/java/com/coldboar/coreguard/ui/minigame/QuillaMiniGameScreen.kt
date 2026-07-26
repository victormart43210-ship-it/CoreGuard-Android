package com.coldboar.coreguard.ui.minigame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// Teal cyber-medieval palette (local to the easter-egg game).
private val TealBackground = Color(0xFF031A1C)
private val TealPrimary = Color(0xFF00E5FF)
private val TealDark = Color(0xFF005B66)
private val GoldAccent = Color(0xFFFFD700)
private val MalwareRed = Color(0xFFFF3366)
private val GridColor = Color(0xFF003840)
private val SpellGlow = Color(0xAA00E5FF)

/**
 * Hidden Quilla purge mini-game — Flappy-style flight + spell shots.
 * Educational / fun only; not a security claim or detector.
 *
 * Smooth path: fixed-timestep engine + render interpolation, vsync-paced
 * invalidation (no artificial FPS sleep), reused draw Paths.
 */
@Composable
fun QuillaMiniGameScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val engine = remember { QuillaGameEngine() }
    val robePath = remember { Path() }
    val hatPath = remember { Path() }

    var frame by remember { mutableIntStateOf(0) }
    var hudScore by remember { mutableIntStateOf(0) }
    var hudShield by remember { mutableIntStateOf(100) }
    var gameOver by remember { mutableStateOf(false) }
    var sized by remember { mutableStateOf(false) }

    fun syncHud() {
        hudScore = engine.score
        hudShield = engine.shieldHp
        gameOver = engine.gameOver
    }

    fun resetRun() {
        engine.reset()
        syncHud()
        frame++
    }

    LaunchedEffect(sized, gameOver) {
        if (!sized || gameOver) return@LaunchedEffect
        if (engine.pipes.isEmpty()) resetRun()
        delay(48)

        var lastFrameMs = 0L
        while (isActive && !engine.gameOver) {
            withFrameMillis { now ->
                if (engine.gameOver) return@withFrameMillis
                val dt = if (lastFrameMs == 0L) {
                    QuillaGameEngine.FIXED_DT_MS
                } else {
                    (now - lastFrameMs).toFloat()
                }
                lastFrameMs = now
                val hudChanged = engine.beginFrame(dt)
                frame++
                if (hudChanged) syncHud()
            }
        }
        syncHud()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TealBackground)
            .onSizeChanged {
                val w = it.width.toFloat().coerceAtLeast(2f)
                val h = it.height.toFloat().coerceAtLeast(2f)
                val changed = engine.worldW != w || engine.worldH != h
                engine.worldW = w
                engine.worldH = h
                if (changed && engine.pipes.isEmpty()) {
                    engine.reset()
                    syncHud()
                }
                sized = true
            }
            .semantics { contentDescription = "Quilla mini-game. Tap to jump and cast." }
            .pointerInput(gameOver) {
                if (gameOver) return@pointerInput
                detectTapGestures {
                    engine.jumpAndCast()
                    frame++
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            frame

            val blink = engine.invulnerable && ((frame / 3) % 2 == 0)
            drawCyberGrid(scrollX = engine.scrollX)

            engine.pipes.forEach { pipe ->
                drawSecurityGate(x = engine.renderPipeX(pipe), pipe)
            }
            engine.spells.forEach { spell ->
                val sx = engine.renderSpellX(spell)
                val sy = engine.renderSpellY(spell)
                drawCircle(color = SpellGlow, radius = 14f, center = Offset(sx, sy))
                drawCircle(color = TealPrimary, radius = 7f, center = Offset(sx, sy))
            }
            engine.enemies.forEach { enemy ->
                drawMalware(
                    enemy,
                    x = engine.renderEnemyX(enemy),
                    y = engine.renderEnemyY(enemy)
                )
            }
            if (!blink) {
                drawQuilla(
                    y = engine.renderQuillaY(),
                    x = QuillaGameEngine.QUILLA_X,
                    robePath = robePath,
                    hatPath = hatPath
                )
            }
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
                    text = "SHIELD: $hudShield%",
                    color = if (hudShield < 30) MalwareRed else TealPrimary,
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
                text = "DATA SHARDS: $hudScore",
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
                        text = "FINAL SCORE: $hudScore",
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

private fun DrawScope.drawCyberGrid(scrollX: Float) {
    val gridSize = 140f
    val offset = scrollX % gridSize
    var x = -offset
    while (x < size.width + gridSize) {
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

private fun DrawScope.drawSecurityGate(x: Float, gate: PipeObstacle) {
    drawRect(
        color = TealDark,
        topLeft = Offset(x, 0f),
        size = Size(gate.width, gate.gapY)
    )
    drawRect(
        color = TealPrimary,
        topLeft = Offset(x, 0f),
        size = Size(gate.width, gate.gapY),
        style = Stroke(2.5f)
    )

    val bottomY = gate.gapY + gate.gapHeight
    drawRect(
        color = TealDark,
        topLeft = Offset(x, bottomY),
        size = Size(gate.width, size.height - bottomY)
    )
    drawRect(
        color = TealPrimary,
        topLeft = Offset(x, bottomY),
        size = Size(gate.width, size.height - bottomY),
        style = Stroke(2.5f)
    )
}

private fun DrawScope.drawQuilla(
    y: Float,
    x: Float,
    robePath: Path,
    hatPath: Path
) {
    robePath.rewind()
    robePath.moveTo(x - 22f, y + 28f)
    robePath.lineTo(x + 18f, y + 28f)
    robePath.lineTo(x + 14f, y - 8f)
    robePath.lineTo(x - 18f, y - 8f)
    robePath.close()
    drawPath(path = robePath, color = TealDark)

    drawCircle(color = Color.Black, radius = 16f, center = Offset(x, y - 12f))
    drawCircle(color = TealPrimary, radius = 3.5f, center = Offset(x - 5f, y - 12f))
    drawCircle(color = TealPrimary, radius = 3.5f, center = Offset(x + 5f, y - 12f))

    hatPath.rewind()
    hatPath.moveTo(x - 30f, y - 22f)
    hatPath.lineTo(x + 30f, y - 22f)
    hatPath.lineTo(x + 4f, y - 58f)
    hatPath.close()
    drawPath(path = hatPath, color = TealBackground)
    drawPath(path = hatPath, color = TealPrimary, style = Stroke(2.5f))
    drawCircle(color = GoldAccent, radius = 3.5f, center = Offset(x, y - 32f))

    drawLine(
        color = GoldAccent,
        start = Offset(x + 16f, y + 22f),
        end = Offset(x + 20f, y - 26f),
        strokeWidth = 3.5f
    )
    drawCircle(color = TealPrimary, radius = 7f, center = Offset(x + 20f, y - 30f))
}

private fun DrawScope.drawMalware(enemy: Enemy, x: Float, y: Float) {
    if (enemy.isWorm) {
        drawCircle(color = MalwareRed, radius = 14f, center = Offset(x, y))
        drawCircle(color = MalwareRed, radius = 11f, center = Offset(x + 16f, y + 3f))
        drawCircle(color = MalwareRed, radius = 7f, center = Offset(x + 28f, y - 1f))
    } else {
        drawCircle(color = MalwareRed, radius = enemy.size / 2, center = Offset(x, y))
        drawCircle(
            color = MalwareRed,
            radius = enemy.size / 2 + 5f,
            center = Offset(x, y),
            style = Stroke(2.5f)
        )
    }
}
