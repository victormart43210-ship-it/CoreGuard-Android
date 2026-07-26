package com.coldboar.coreguard.ui.minigame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

// Cyber-fantasy pixel palette (Audit Keep / Quilla reference look).
private val CxVoid = Color(0xFF0B0B18)
private val CxDeep = Color(0xFF12122A)
private val CxPanel = Color(0xFF1A1A34)
private val CxPanelEdge = Color(0xFF2E2E55)
private val CxTeal = Color(0xFF2EE6D6)
private val CxTealDim = Color(0xFF149B96)
private val CxTealGlow = Color(0xFF7CFFF2)
private val CxCyan = Color(0xFF39F3FF)
private val CxGold = Color(0xFFE7C35A)
private val CxDanger = Color(0xFFFF4D6D)
private val CxWorm = Color(0xFF9B5CFF)
private val CxWormDark = Color(0xFF5A2E99)
private val CxOrb = Color(0xFF2BC4A8)
private val CxOrbDark = Color(0xFF167A6A)
private val CxCircuit = Color(0xFF1F6F75)
private val CxWhite = Color(0xFFE8F7FF)
private val CxShadow = Color(0x88000000)

/**
 * Hidden Quilla purge mini-game — Flappy-style flight + spell shots.
 * Educational / fun only; not a security claim or detector.
 *
 * Visual direction: cyber-fantasy pixel Audit Keep (hooded Quilla, circuit
 * vault walls, data-shard HUD). Homage styling only.
 */
@Composable
fun QuillaMiniGameScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val engine = remember { QuillaGameEngine() }
    val paths = remember { PixelPaths() }

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
            .background(CxVoid)
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
            drawAuditKeepBg(scrollX = engine.scrollX, frame = frame)

            engine.pipes.forEach { pipe ->
                drawCircuitVault(
                    x = engine.renderPipeX(pipe),
                    gate = pipe,
                    frame = frame
                )
            }
            // Floating data shards near pipes (cosmetic collectible sparkle)
            engine.pipes.forEachIndexed { index, pipe ->
                if (!pipe.scored) {
                    val sx = engine.renderPipeX(pipe) + pipe.width * 0.5f
                    val sy = pipe.gapY + pipe.gapHeight * 0.5f + sin((frame + index * 20) * 0.1f) * 6f
                    drawDataShard(sx, sy, frame + index * 7)
                }
            }
            engine.spells.forEach { spell ->
                drawCyanFireball(
                    x = engine.renderSpellX(spell),
                    y = engine.renderSpellY(spell),
                    frame = frame
                )
            }
            engine.enemies.forEach { enemy ->
                if (enemy.isWorm) {
                    drawPurpleWorm(
                        x = engine.renderEnemyX(enemy),
                        y = engine.renderEnemyY(enemy),
                        frame = frame
                    )
                } else {
                    drawSpikyOrb(
                        x = engine.renderEnemyX(enemy),
                        y = engine.renderEnemyY(enemy),
                        frame = frame,
                        paths = paths
                    )
                }
            }
            if (!blink) {
                drawHoodedQuilla(
                    x = QuillaGameEngine.QUILLA_X,
                    y = engine.renderQuillaY(),
                    frame = frame,
                    paths = paths
                )
            }

            // Corner portrait medallion (reference right-panel homage, phone-sized)
            drawPortraitMedallion(
                cx = size.width - 72f,
                cy = size.height - 96f,
                frame = frame,
                paths = paths
            )
        }

        AuditKeepHud(
            shield = hudShield,
            score = hudScore,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(10.dp)
        )

        if (gameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 360.dp)
                        .fillMaxWidth()
                        .background(CxPanel, RoundedCornerShape(10.dp))
                        .border(2.dp, CxTeal, RoundedCornerShape(10.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PURGE FAILED",
                        color = CxDanger,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The Audit Keep holds.\nData shards secured: $hudScore",
                        color = CxWhite,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    CyberButton("RESTART PURGE", filled = true) { resetRun() }
                    Spacer(modifier = Modifier.height(10.dp))
                    CyberButton("EXIT KEEP", filled = false, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun AuditKeepHud(
    shield: Int,
    score: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shardsProgress = (score / 50f).coerceIn(0f, 1f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Level 1-3: The Audit Keep",
            color = CxWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shield / shards bar
            Column(modifier = Modifier.weight(1.2f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(18.dp)) {
                        drawRoundRect(
                            color = CxTeal,
                            size = size,
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                        drawCircle(CxVoid, radius = size.minDimension * 0.22f, center = center)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    LinearProgressIndicator(
                        progress = { shield / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp),
                        color = if (shield < 30) CxDanger else CxCyan,
                        trackColor = CxPanelEdge,
                        strokeCap = StrokeCap.Round
                    )
                }
                Text(
                    text = "Data Shards Collected",
                    color = CxTealDim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                LinearProgressIndicator(
                    progress = { shardsProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = CxTealGlow,
                    trackColor = CxDeep,
                    strokeCap = StrokeCap.Round
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(14.dp)) {
                        drawRect(CxCyan, size = size)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$score",
                        color = CxCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "Score",
                    color = CxTealDim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Mini-map
            Box(
                modifier = Modifier
                    .size(64.dp, 40.dp)
                    .background(CxDeep, RoundedCornerShape(4.dp))
                    .border(1.dp, CxTealDim, RoundedCornerShape(4.dp))
                    .padding(4.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Rooms
                    drawRoundRect(CxPanelEdge, Offset(2f, 8f), Size(18f, 14f), CornerRadius(2f, 2f))
                    drawRoundRect(Color(0xFF4A2A6A), Offset(24f, 4f), Size(20f, 18f), CornerRadius(2f, 2f))
                    drawRoundRect(CxPanelEdge, Offset(48f, 10f), Size(14f, 12f), CornerRadius(2f, 2f))
                    // Player marker
                    drawCircle(CxTealGlow, 3.5f, Offset(34f, 12f))
                }
                Text(
                    text = "?",
                    color = CxGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(36.dp)
                    .background(CxPanel, RoundedCornerShape(6.dp))
                    .border(1.dp, CxTeal, RoundedCornerShape(6.dp))
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close mini-game", tint = CxWhite)
            }
        }
    }
}

@Composable
private fun CyberButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (filled) CxTeal else CxDeep, RoundedCornerShape(6.dp))
            .border(2.dp, CxTeal, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (filled) CxVoid else CxTealGlow,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp
        )
    }
}

private class PixelPaths {
    val robe = Path()
    val hood = Path()
    val spike = Path()
    val wing = Path()
}

private fun DrawScope.drawAuditKeepBg(scrollX: Float, frame: Int) {
    drawRect(
        brush = Brush.verticalGradient(listOf(CxVoid, CxDeep, Color(0xFF1A1030)))
    )
    // Circuit mesh
    val step = 48f
    val ox = scrollX % step
    var x = -ox
    while (x < size.width + step) {
        drawLine(CxCircuit.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), 1.2f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(CxCircuit.copy(alpha = 0.28f), Offset(0f, y), Offset(size.width, y), 1.2f)
        y += step
    }
    // Occasional node pulses
    val pulse = 0.35f + 0.25f * sin(frame * 0.08f)
    for (i in 0..6) {
        val nx = ((i * 137f + scrollX * 0.4f) % (size.width + 40f))
        val ny = 80f + (i * 97f) % (size.height - 160f)
        drawCircle(CxTeal.copy(alpha = pulse * 0.5f), 3f, Offset(nx, ny))
        drawCircle(CxTealGlow.copy(alpha = pulse), 1.5f, Offset(nx, ny))
    }
    // Floor platform band
    val floor = size.height * 0.88f
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF1C1638), Color(0xFF0E0A1C))),
        topLeft = Offset(0f, floor),
        size = Size(size.width, size.height - floor)
    )
    drawLine(CxTealDim, Offset(0f, floor), Offset(size.width, floor), 2f)
}

private fun DrawScope.drawCircuitVault(x: Float, gate: PipeObstacle, frame: Int) {
    val w = gate.width
    drawVaultBlock(x, 0f, w, gate.gapY, frame)
    val bottomY = gate.gapY + gate.gapHeight
    drawVaultBlock(x, bottomY, w, size.height - bottomY, frame + 3)
    // Neon rim at gap
    drawRect(CxTealGlow, Offset(x - 2f, gate.gapY - 5f), Size(w + 4f, 5f))
    drawRect(CxTeal, Offset(x - 2f, bottomY), Size(w + 4f, 5f))
}

private fun DrawScope.drawVaultBlock(x: Float, y: Float, w: Float, h: Float, seed: Int) {
    if (h <= 1f) return
    drawRect(
        brush = Brush.horizontalGradient(listOf(CxPanel, CxDeep, CxPanelEdge)),
        topLeft = Offset(x, y),
        size = Size(w, h)
    )
    drawRect(CxTealDim.copy(alpha = 0.7f), Offset(x, y), Size(w, h), style = Stroke(2f))
    // Pixel brick / plate seams
    var row = y + 10f
    while (row < y + h - 4f) {
        drawLine(CxCircuit.copy(alpha = 0.55f), Offset(x + 3f, row), Offset(x + w - 3f, row), 1f)
        row += 12f
    }
    // Vertical bus lines
    val bus1 = x + w * 0.3f
    val bus2 = x + w * 0.7f
    drawLine(CxTeal.copy(alpha = 0.45f), Offset(bus1, y + 4f), Offset(bus1, y + h - 4f), 2f)
    drawLine(CxCyan.copy(alpha = 0.35f), Offset(bus2, y + 4f), Offset(bus2, y + h - 4f), 1.5f)
    // Chip pads
    val padY = y + 16f + (seed % 5) * 10f
    if (padY < y + h - 12f) {
        drawRoundRect(CxTealDim, Offset(x + 8f, padY), Size(w - 16f, 8f), CornerRadius(2f, 2f))
    }
}

private fun DrawScope.drawDataShard(x: Float, y: Float, phase: Int) {
    val s = 7f + sin(phase * 0.2f) * 1.5f
    rotate(45f, Offset(x, y)) {
        drawRect(CxCyan.copy(alpha = 0.9f), Offset(x - s, y - s), Size(s * 2f, s * 2f))
        drawRect(CxTealGlow, Offset(x - s * 0.45f, y - s * 0.45f), Size(s * 0.9f, s * 0.9f))
    }
}

private fun DrawScope.drawCyanFireball(x: Float, y: Float, frame: Int) {
    val pulse = 10f + sin(frame * 0.4f) * 2f
    drawCircle(CxTeal.copy(alpha = 0.35f), pulse + 8f, Offset(x, y))
    drawCircle(CxCyan, pulse, Offset(x, y))
    drawCircle(CxWhite, pulse * 0.35f, Offset(x - 2f, y - 2f))
    // Trail
    drawCircle(CxTealGlow.copy(alpha = 0.5f), 5f, Offset(x - 14f, y))
    drawCircle(CxTeal.copy(alpha = 0.35f), 3f, Offset(x - 24f, y + 2f))
}

private fun DrawScope.drawSpikyOrb(x: Float, y: Float, frame: Int, paths: PixelPaths) {
    val rot = (frame * 4f) % 360f
    rotate(rot, Offset(x, y)) {
        paths.spike.rewind()
        for (i in 0 until 8) {
            val a = (Math.PI * 2 * i / 8).toFloat()
            val outer = 22f
            val ix = x + cos(a) * 10f
            val iy = y + sin(a) * 10f
            val ox = x + cos(a) * outer
            val oy = y + sin(a) * outer
            val a1 = a + 0.22f
            val a2 = a - 0.22f
            paths.spike.moveTo(ix, iy)
            paths.spike.lineTo(ox, oy)
            paths.spike.lineTo(x + cos(a1) * 10f, y + sin(a1) * 10f)
            paths.spike.close()
            paths.spike.moveTo(ix, iy)
            paths.spike.lineTo(ox, oy)
            paths.spike.lineTo(x + cos(a2) * 10f, y + sin(a2) * 10f)
            paths.spike.close()
        }
        drawPath(paths.spike, CxOrb)
    }
    drawCircle(
        brush = Brush.radialGradient(listOf(CxTealGlow, CxOrb, CxOrbDark), center = Offset(x, y), radius = 14f),
        radius = 13f,
        center = Offset(x, y)
    )
    // Mean face
    drawCircle(CxVoid, 2.5f, Offset(x - 4f, y - 2f))
    drawCircle(CxVoid, 2.5f, Offset(x + 4f, y - 2f))
    drawPath(
        Path().apply {
            moveTo(x - 5f, y + 5f)
            quadraticBezierTo(x, y + 9f, x + 5f, y + 5f)
        },
        CxVoid,
        style = Stroke(2f)
    )
}

private fun DrawScope.drawPurpleWorm(x: Float, y: Float, frame: Int) {
    val wobble = sin(frame * 0.2f)
    val segments = listOf(
        Offset(x, y),
        Offset(x + 14f, y + wobble * 4f),
        Offset(x + 26f, y - wobble * 3f),
        Offset(x + 38f, y + wobble * 2f)
    )
    for (i in segments.indices.reversed()) {
        val r = 12f - i * 2f
        drawCircle(
            brush = Brush.radialGradient(listOf(CxWorm, CxWormDark), center = segments[i], radius = r),
            radius = r,
            center = segments[i]
        )
        drawCircle(CxPanelEdge, r, segments[i], style = Stroke(1.5f))
    }
    // Eyes on head
    drawCircle(CxCyan, 2.5f, Offset(x - 3f, y - 3f))
    drawCircle(CxCyan, 2.5f, Offset(x + 3f, y - 3f))
    drawCircle(CxVoid, 1.2f, Offset(x - 3f, y - 3f))
    drawCircle(CxVoid, 1.2f, Offset(x + 3f, y - 3f))
}

private fun DrawScope.drawHoodedQuilla(x: Float, y: Float, frame: Int, paths: PixelPaths) {
    val bob = sin(frame * 0.14f) * 2f
    val cy = y + bob

    drawOval(CxShadow, Offset(x - 16f, cy + 28f), Size(34f, 9f))

    // Robe
    paths.robe.rewind()
    paths.robe.moveTo(x - 18f, cy + 6f)
    paths.robe.lineTo(x + 16f, cy + 6f)
    paths.robe.lineTo(x + 14f, cy + 30f)
    paths.robe.lineTo(x - 16f, cy + 30f)
    paths.robe.close()
    drawPath(
        paths.robe,
        brush = Brush.verticalGradient(listOf(CxTeal, CxTealDim, Color(0xFF0D4A4A)))
    )
    drawPath(paths.robe, CxTealGlow.copy(alpha = 0.5f), style = Stroke(1.5f))
    // Circuit trim on robe
    drawLine(CxCyan.copy(alpha = 0.7f), Offset(x - 8f, cy + 12f), Offset(x + 8f, cy + 12f), 2f)
    drawCircle(CxTealGlow, 3f, Offset(x, cy + 18f))

    // Hood / hat void face
    paths.hood.rewind()
    paths.hood.moveTo(x - 22f, cy - 6f)
    paths.hood.lineTo(x + 20f, cy - 4f)
    paths.hood.lineTo(x + 6f, cy - 42f)
    paths.hood.lineTo(x - 8f, cy - 36f)
    paths.hood.close()
    drawPath(
        paths.hood,
        brush = Brush.verticalGradient(listOf(CxTealGlow, CxTeal, CxTealDim))
    )
    drawPath(paths.hood, CxCyan.copy(alpha = 0.6f), style = Stroke(2f))
    // Eye of providence mark
    drawCircle(CxGold, 3.5f, Offset(x - 2f, cy - 22f))
    drawCircle(CxVoid, 1.6f, Offset(x - 2f, cy - 22f))

    // Face void + glowing eyes
    drawCircle(CxVoid, 13f, Offset(x, cy - 8f))
    drawCircle(CxCyan, 3.2f, Offset(x - 5f, cy - 8f))
    drawCircle(CxCyan, 3.2f, Offset(x + 5f, cy - 8f))
    drawCircle(CxTealGlow, 1.4f, Offset(x - 5f, cy - 8f))
    drawCircle(CxTealGlow, 1.4f, Offset(x + 5f, cy - 8f))

    // Staff
    drawLine(Color(0xFF6B3F1F), Offset(x + 16f, cy + 26f), Offset(x + 24f, cy - 30f), 3.5f)
    drawCircle(CxTeal, 9f, Offset(x + 25f, cy - 34f))
    drawCircle(CxCyan, 9f, Offset(x + 25f, cy - 34f), style = Stroke(2f))
    drawCircle(CxTealGlow, 3f, Offset(x + 25f, cy - 34f))
}

private fun DrawScope.drawPortraitMedallion(cx: Float, cy: Float, frame: Int, paths: PixelPaths) {
    val r = 48f
    drawCircle(CxPanel, r + 4f, Offset(cx, cy))
    drawCircle(CxTeal, r + 4f, Offset(cx, cy), style = Stroke(2.5f))
    drawCircle(
        brush = Brush.radialGradient(listOf(CxDeep, CxVoid), center = Offset(cx, cy), radius = r),
        radius = r,
        center = Offset(cx, cy)
    )
    // Mini hooded bust
    paths.hood.rewind()
    paths.hood.moveTo(cx - 26f, cy + 8f)
    paths.hood.lineTo(cx + 24f, cy + 10f)
    paths.hood.lineTo(cx + 8f, cy - 34f)
    paths.hood.lineTo(cx - 10f, cy - 28f)
    paths.hood.close()
    drawPath(paths.hood, CxTeal)
    drawCircle(CxVoid, 14f, Offset(cx, cy - 2f))
    drawCircle(CxCyan, 3.5f, Offset(cx - 5f, cy - 2f))
    drawCircle(CxCyan, 3.5f, Offset(cx + 5f, cy - 2f))
    drawCircle(CxGold, 3f, Offset(cx - 2f, cy - 16f))
    // Pulse ring
    val pulse = 0.4f + 0.3f * sin(frame * 0.1f)
    drawCircle(CxTealGlow.copy(alpha = pulse), r + 8f, Offset(cx, cy), style = Stroke(1.5f))
}
