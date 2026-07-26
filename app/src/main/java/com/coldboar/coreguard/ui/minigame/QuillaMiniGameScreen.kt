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

/**
 * Hidden Quilla purge mini-game — Flappy-style flight + spell shots.
 * Educational / fun only; not a security claim or detector.
 *
 * Physics runs in [QuillaGameEngine] (non-Snapshot). Compose invalidates once
 * per frame via [frame], avoiding the ANR-prone state thrash of updating many
 * mutableState fields inside `withFrameNanos`.
 */
@Composable
fun QuillaMiniGameScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val engine = remember { QuillaGameEngine() }

    // Canvas invalidation only — not a source of physics truth.
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

    // Re-enter when Restart clears gameOver so the frame loop resumes.
    LaunchedEffect(sized, gameOver) {
        if (!sized || gameOver) return@LaunchedEffect
        if (engine.pipes.isEmpty()) resetRun()
        // Let the first Compose layout/draw finish before physics starts (avoids startup jank).
        delay(FRAME_BUDGET_MS)

        var lastFrameMs = 0L
        while (isActive && !engine.gameOver) {
            withFrameMillis { now ->
                if (engine.gameOver) return@withFrameMillis
                val dt = if (lastFrameMs == 0L) {
                    QuillaGameEngine.REF_DT_MS
                } else {
                    (now - lastFrameMs).toFloat()
                }
                lastFrameMs = now
                val hudChanged = engine.tick(dt)
                frame++
                // HUD Text is expensive to recompose on soft GPUs — only on real changes.
                if (hudChanged) syncHud()
            }
            // Cap ~30 FPS so Compose + Canvas do not starve System UI on software renderers.
            delay(FRAME_BUDGET_MS)
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
            .clickable(enabled = !gameOver) {
                engine.jumpAndCast()
                frame++
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Subscribe to frame so Canvas redraws without Snapshot lists.
            @Suppress("UNUSED_EXPRESSION")
            frame
            drawCyberGrid()
            engine.pipes.forEach { drawSecurityGate(it) }
            engine.spells.forEach {
                drawCircle(color = TealPrimary, radius = 10f, center = Offset(it.x, it.y))
            }
            engine.enemies.forEach { drawMalware(it) }
            drawQuilla(y = engine.quillaY, x = QuillaGameEngine.QUILLA_X)
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

private const val FRAME_BUDGET_MS = 33L

private fun DrawScope.drawCyberGrid() {
    // Sparse grid — soft GPUs choke on dense line meshes every frame.
    val gridSize = 120f
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
