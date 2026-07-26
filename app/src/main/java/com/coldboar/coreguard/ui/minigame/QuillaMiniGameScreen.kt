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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
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

// Dragon Quest–inspired classic JRPG palette (homage look, not licensed assets).
private val DqSkyTop = Color(0xFF6EB6E8)
private val DqSkyBottom = Color(0xFFC8E7F8)
private val DqHillFar = Color(0xFF5EAF5E)
private val DqHillNear = Color(0xFF3F9B3F)
private val DqGrass = Color(0xFF2F8F2F)
private val DqGrassDark = Color(0xFF246B24)
private val DqWindowNavy = Color(0xFF0B1F6B)
private val DqWindowNavyDeep = Color(0xFF071448)
private val DqGold = Color(0xFFE6C34A)
private val DqGoldDark = Color(0xFFB8922A)
private val DqCream = Color(0xFFFFF6D8)
private val DqStone = Color(0xFF9A9080)
private val DqStoneDark = Color(0xFF6E6558)
private val DqStoneLight = Color(0xFFC4B9A6)
private val DqSlime = Color(0xFF4C9BE0)
private val DqSlimeDark = Color(0xFF2E6FAE)
private val DqSlimeShine = Color(0xFFB8E0FF)
private val DqDracky = Color(0xFF5C3D8F)
private val DqDrackyWing = Color(0xFF3A245C)
private val DqHeroBlue = Color(0xFF2F5DBB)
private val DqHeroBlueDark = Color(0xFF1E3F86)
private val DqSkin = Color(0xFFFFD2A8)
private val DqHair = Color(0xFF5A3A1E)
private val DqSpell = Color(0xFFFFF1A0)
private val DqSpellCore = Color(0xFFFFFDF2)
private val DqDanger = Color(0xFFE24B4B)
private val DqShadow = Color(0x55000000)

/**
 * Hidden Quilla purge mini-game — Flappy-style flight + spell shots.
 * Educational / fun only; not a security claim or detector.
 *
 * Visual homage to classic Dragon Quest JRPG presentation (windows, slimes,
 * castle pillars, chibi hero). Not affiliated with Square Enix.
 */
@Composable
fun QuillaMiniGameScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val engine = remember { QuillaGameEngine() }
    val paths = remember { DqPaths() }

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
            .background(DqSkyTop)
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
            drawDqWorld(scrollX = engine.scrollX)

            engine.pipes.forEach { pipe ->
                drawCastlePillar(
                    x = engine.renderPipeX(pipe),
                    gate = pipe
                )
            }
            engine.spells.forEach { spell ->
                drawDqSpell(
                    x = engine.renderSpellX(spell),
                    y = engine.renderSpellY(spell),
                    frame = frame,
                    paths = paths
                )
            }
            engine.enemies.forEach { enemy ->
                if (enemy.isWorm) {
                    drawDracky(
                        x = engine.renderEnemyX(enemy),
                        y = engine.renderEnemyY(enemy),
                        frame = frame,
                        paths = paths
                    )
                } else {
                    drawSlime(
                        x = engine.renderEnemyX(enemy),
                        y = engine.renderEnemyY(enemy),
                        bob = sin((frame + enemy.x) * 0.12f) * 3f,
                        paths = paths
                    )
                }
            }
            if (!blink) {
                drawDqHero(
                    x = QuillaGameEngine.QUILLA_X,
                    y = engine.renderQuillaY(),
                    frame = frame,
                    paths = paths
                )
            }
        }

        // Classic DQ command / status windows
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                DqWindow(
                    modifier = Modifier.weight(1.2f)
                ) {
                    Text(
                        text = "Alefgard · Floor 1-3",
                        color = DqGold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Quilla the Mage",
                        color = DqCream,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (hudShield < 30) "HP  $hudShield  !!DANGER!!" else "HP  $hudShield / 100",
                        color = if (hudShield < 30) DqDanger else DqCream,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                DqWindow(
                    modifier = Modifier.weight(0.9f)
                ) {
                    Text(
                        text = "GOLD",
                        color = DqGold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$hudScore",
                        color = DqCream,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap: Jump + Woosh",
                        color = DqCream.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Serif
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(DqWindowNavy, RoundedCornerShape(8.dp))
                        .border(2.dp, DqGold, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close mini-game",
                        tint = DqCream
                    )
                }
            }
        }

        if (gameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                DqWindow(
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 340.dp)
                        .fillMaxWidth(0.88f)
                ) {
                    Text(
                        text = "Thou art defeated…",
                        color = DqDanger,
                        fontSize = 22.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A slime feast was held.\nGOLD carried: $hudScore",
                        color = DqCream,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    DqMenuButton(
                        label = "Try again",
                        filled = true,
                        onClick = { resetRun() }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    DqMenuButton(
                        label = "Return to title",
                        filled = false,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun DqWindow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(listOf(DqWindowNavy, DqWindowNavyDeep)),
                shape = RoundedCornerShape(6.dp)
            )
            .border(3.dp, DqGold, RoundedCornerShape(6.dp))
            .border(1.dp, DqGoldDark, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            content()
        }
    }
}

@Composable
private fun DqMenuButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (filled) DqGold else DqWindowNavyDeep,
                shape = RoundedCornerShape(4.dp)
            )
            .border(2.dp, if (filled) DqGoldDark else DqGold, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "> $label",
            color = if (filled) DqWindowNavyDeep else DqCream,
            fontSize = 16.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold
        )
    }
}

private class DqPaths {
    val body = Path()
    val hat = Path()
    val slime = Path()
    val wingL = Path()
    val wingR = Path()
    val star = Path()
    val battlement = Path()
}

private fun DrawScope.drawDqWorld(scrollX: Float) {
    // Sky wash
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(DqSkyTop, DqSkyBottom),
            startY = 0f,
            endY = size.height * 0.55f
        ),
        size = Size(size.width, size.height * 0.55f)
    )

    // Soft clouds
    val cloudShift = (scrollX * 0.15f) % (size.width + 200f)
    drawCloud(-80f + cloudShift, size.height * 0.12f, 1.1f)
    drawCloud(size.width * 0.45f - cloudShift * 0.6f, size.height * 0.08f, 0.85f)
    drawCloud(size.width * 0.8f + cloudShift * 0.3f, size.height * 0.18f, 1.0f)

    // Rolling hills
    val hillBase = size.height * 0.52f
    drawHillBand(hillBase, DqHillFar, amplitude = 28f, wavelength = 180f, scroll = scrollX * 0.25f)
    drawHillBand(hillBase + 36f, DqHillNear, amplitude = 34f, wavelength = 140f, scroll = scrollX * 0.45f)

    // Ground strip with checker tufts
    val groundY = size.height * 0.78f
    drawRect(
        brush = Brush.verticalGradient(listOf(DqGrass, DqGrassDark)),
        topLeft = Offset(0f, groundY),
        size = Size(size.width, size.height - groundY)
    )
    val tuft = 28f
    val offset = scrollX % tuft
    var x = -offset
    var toggle = false
    while (x < size.width + tuft) {
        if (toggle) {
            drawRect(
                color = DqGrassDark.copy(alpha = 0.35f),
                topLeft = Offset(x, groundY),
                size = Size(tuft, 18f)
            )
        }
        toggle = !toggle
        x += tuft
    }

    // Horizon path line (classic overworld feel)
    drawLine(
        color = Color.White.copy(alpha = 0.25f),
        start = Offset(0f, groundY),
        end = Offset(size.width, groundY),
        strokeWidth = 2f
    )
}

private fun DrawScope.drawCloud(cx: Float, cy: Float, scale: Float) {
    val r = 28f * scale
    drawCircle(Color.White.copy(alpha = 0.85f), radius = r, center = Offset(cx, cy))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = r * 0.85f, center = Offset(cx + r * 0.9f, cy + 4f))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = r * 0.75f, center = Offset(cx - r * 0.8f, cy + 6f))
}

private fun DrawScope.drawHillBand(
    baseY: Float,
    color: Color,
    amplitude: Float,
    wavelength: Float,
    scroll: Float
) {
    val path = Path()
    path.moveTo(0f, size.height)
    path.lineTo(0f, baseY)
    var x = 0f
    while (x <= size.width) {
        val y = baseY + sin((x + scroll) / wavelength * Math.PI.toFloat() * 2f) * amplitude
        path.lineTo(x, y)
        x += 12f
    }
    path.lineTo(size.width, size.height)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawCastlePillar(
    x: Float,
    gate: PipeObstacle
) {
    val w = gate.width
    // Top tower
    drawStoneBlock(x, 0f, w, gate.gapY)
    drawBattlements(x, gate.gapY - 16f, w)

    // Bottom tower
    val bottomY = gate.gapY + gate.gapHeight
    drawStoneBlock(x, bottomY, w, size.height - bottomY)
    drawBattlements(x, bottomY, w)

    // Gold trim rim at gap edges
    drawRect(DqGold, topLeft = Offset(x - 2f, gate.gapY - 6f), size = Size(w + 4f, 6f))
    drawRect(DqGoldDark, topLeft = Offset(x - 2f, bottomY), size = Size(w + 4f, 6f))
}

private fun DrawScope.drawStoneBlock(x: Float, y: Float, w: Float, h: Float) {
    if (h <= 0f) return
    drawRect(
        brush = Brush.horizontalGradient(listOf(DqStoneLight, DqStone, DqStoneDark)),
        topLeft = Offset(x, y),
        size = Size(w, h)
    )
    drawRect(
        color = DqStoneDark,
        topLeft = Offset(x, y),
        size = Size(w, h),
        style = Stroke(3f)
    )
    // Brick courses
    var row = y + 12f
    var stagger = false
    while (row < y + h - 4f) {
        drawLine(
            color = DqStoneDark.copy(alpha = 0.55f),
            start = Offset(x + 2f, row),
            end = Offset(x + w - 2f, row),
            strokeWidth = 1.5f
        )
        val brickW = w / 2.2f
        val x0 = x + if (stagger) brickW * 0.5f else 0f
        var cx = x0
        while (cx < x + w - 6f) {
            drawLine(
                color = DqStoneDark.copy(alpha = 0.4f),
                start = Offset(cx, row - 12f),
                end = Offset(cx, row),
                strokeWidth = 1.5f
            )
            cx += brickW
        }
        stagger = !stagger
        row += 14f
    }
}

private fun DrawScope.drawBattlements(x: Float, y: Float, w: Float) {
    val merlon = w / 4.5f
    var mx = x
    var i = 0
    while (mx < x + w - 2f) {
        if (i % 2 == 0) {
            drawRect(
                color = DqStoneLight,
                topLeft = Offset(mx, y - 10f),
                size = Size(merlon * 0.85f, 12f)
            )
            drawRect(
                color = DqStoneDark,
                topLeft = Offset(mx, y - 10f),
                size = Size(merlon * 0.85f, 12f),
                style = Stroke(1.5f)
            )
        }
        mx += merlon
        i++
    }
}

private fun DrawScope.drawSlime(
    x: Float,
    y: Float,
    bob: Float,
    paths: DqPaths
) {
    val cy = y + bob
    // Soft shadow
    drawOval(
        color = DqShadow,
        topLeft = Offset(x - 22f, cy + 18f),
        size = Size(44f, 12f)
    )
    paths.slime.rewind()
    // Teardrop slime silhouette
    paths.slime.moveTo(x, cy - 26f)
    paths.slime.cubicTo(x + 28f, cy - 22f, x + 30f, cy + 10f, x + 18f, cy + 20f)
    paths.slime.quadraticBezierTo(x, cy + 28f, x - 18f, cy + 20f)
    paths.slime.cubicTo(x - 30f, cy + 10f, x - 28f, cy - 22f, x, cy - 26f)
    paths.slime.close()
    drawPath(
        path = paths.slime,
        brush = Brush.verticalGradient(listOf(DqSlimeShine, DqSlime, DqSlimeDark))
    )
    drawPath(path = paths.slime, color = DqSlimeDark, style = Stroke(3f))
    // Face
    drawCircle(Color.White, radius = 5f, center = Offset(x - 7f, cy - 2f))
    drawCircle(Color.White, radius = 5f, center = Offset(x + 7f, cy - 2f))
    drawCircle(Color.Black, radius = 2.2f, center = Offset(x - 6f, cy - 1f))
    drawCircle(Color.Black, radius = 2.2f, center = Offset(x + 8f, cy - 1f))
    // Smile
    val smile = Path().apply {
        moveTo(x - 8f, cy + 8f)
        quadraticBezierTo(x, cy + 14f, x + 8f, cy + 8f)
    }
    drawPath(smile, Color.Black, style = Stroke(2.2f))
    // Antenna sparkle
    drawCircle(DqGold, radius = 3f, center = Offset(x, cy - 30f))
}

private fun DrawScope.drawDracky(
    x: Float,
    y: Float,
    frame: Int,
    paths: DqPaths
) {
    val flap = sin(frame * 0.25f) * 12f
    paths.wingL.rewind()
    paths.wingL.moveTo(x, y)
    paths.wingL.quadraticBezierTo(x - 34f, y - 10f - flap, x - 40f, y + 8f)
    paths.wingL.quadraticBezierTo(x - 20f, y + 6f, x, y)
    paths.wingL.close()
    paths.wingR.rewind()
    paths.wingR.moveTo(x, y)
    paths.wingR.quadraticBezierTo(x + 34f, y - 10f - flap, x + 40f, y + 8f)
    paths.wingR.quadraticBezierTo(x + 20f, y + 6f, x, y)
    paths.wingR.close()
    drawPath(paths.wingL, DqDrackyWing)
    drawPath(paths.wingR, DqDrackyWing)
    drawCircle(
        brush = Brush.verticalGradient(listOf(DqDracky, Color(0xFF2A1744))),
        radius = 16f,
        center = Offset(x, y)
    )
    drawCircle(Color.White, 4f, Offset(x - 5f, y - 2f))
    drawCircle(Color.White, 4f, Offset(x + 5f, y - 2f))
    drawCircle(Color.Black, 2f, Offset(x - 4f, y - 1f))
    drawCircle(Color.Black, 2f, Offset(x + 6f, y - 1f))
    drawCircle(DqDanger, 2.5f, Offset(x, y + 6f))
}

private fun DrawScope.drawDqSpell(
    x: Float,
    y: Float,
    frame: Int,
    paths: DqPaths
) {
    val rot = (frame * 8f) % 360f
    rotate(rot, Offset(x, y)) {
        paths.star.rewind()
        val spikes = 4
        for (i in 0 until spikes * 2) {
            val angle = Math.PI.toFloat() * i / spikes - Math.PI.toFloat() / 2f
            val radius = if (i % 2 == 0) 16f else 7f
            val px = x + cos(angle) * radius
            val py = y + sin(angle) * radius
            if (i == 0) paths.star.moveTo(px, py) else paths.star.lineTo(px, py)
        }
        paths.star.close()
        drawPath(paths.star, DqSpell)
        drawPath(paths.star, DqGoldDark, style = Stroke(2f))
    }
    drawCircle(DqSpellCore, radius = 5f, center = Offset(x, y))
}

private fun DrawScope.drawDqHero(
    x: Float,
    y: Float,
    frame: Int,
    paths: DqPaths
) {
    val bob = sin(frame * 0.15f) * 2f
    val cy = y + bob

    // Shadow
    drawOval(
        color = DqShadow,
        topLeft = Offset(x - 18f, cy + 26f),
        size = Size(36f, 10f)
    )

    // Cape
    paths.body.rewind()
    paths.body.moveTo(x - 6f, cy - 4f)
    paths.body.lineTo(x - 28f, cy + 18f)
    paths.body.quadraticBezierTo(x - 10f, cy + 10f, x - 4f, cy + 8f)
    paths.body.close()
    drawPath(paths.body, Color(0xFF9B2D2D))

    // Tunic
    paths.body.rewind()
    paths.body.moveTo(x - 16f, cy + 4f)
    paths.body.lineTo(x + 16f, cy + 4f)
    paths.body.lineTo(x + 12f, cy + 28f)
    paths.body.lineTo(x - 12f, cy + 28f)
    paths.body.close()
    drawPath(
        paths.body,
        brush = Brush.verticalGradient(listOf(DqHeroBlue, DqHeroBlueDark))
    )
    drawPath(paths.body, DqWindowNavyDeep, style = Stroke(2f))

    // Belt
    drawRect(DqGold, topLeft = Offset(x - 14f, cy + 12f), size = Size(28f, 5f))

    // Head
    drawCircle(DqSkin, radius = 15f, center = Offset(x, cy - 10f))
    drawCircle(DqWindowNavyDeep.copy(alpha = 0.15f), radius = 15f, center = Offset(x, cy - 10f), style = Stroke(1.5f))

    // Spiky DQ hair
    paths.hat.rewind()
    paths.hat.moveTo(x - 16f, cy - 12f)
    paths.hat.lineTo(x - 10f, cy - 28f)
    paths.hat.lineTo(x - 2f, cy - 16f)
    paths.hat.lineTo(x + 4f, cy - 32f)
    paths.hat.lineTo(x + 10f, cy - 14f)
    paths.hat.lineTo(x + 18f, cy - 26f)
    paths.hat.lineTo(x + 16f, cy - 10f)
    paths.hat.close()
    drawPath(paths.hat, DqHair)
    drawPath(paths.hat, Color.Black.copy(alpha = 0.35f), style = Stroke(1.5f))

    // Eyes
    drawCircle(Color.White, 3.5f, Offset(x - 5f, cy - 10f))
    drawCircle(Color.White, 3.5f, Offset(x + 5f, cy - 10f))
    drawCircle(Color.Black, 1.8f, Offset(x - 4.5f, cy - 9.5f))
    drawCircle(Color.Black, 1.8f, Offset(x + 5.5f, cy - 9.5f))
    // Smile
    drawPath(
        Path().apply {
            moveTo(x - 4f, cy - 3f)
            quadraticBezierTo(x, cy + 1f, x + 4f, cy - 3f)
        },
        Color(0xFF8B4513),
        style = Stroke(1.8f)
    )

    // Mage hat (Quilla flair on DQ hero)
    paths.hat.rewind()
    paths.hat.moveTo(x - 20f, cy - 18f)
    paths.hat.lineTo(x + 18f, cy - 16f)
    paths.hat.lineTo(x + 2f, cy - 48f)
    paths.hat.close()
    drawPath(paths.hat, DqHeroBlue)
    drawPath(paths.hat, DqGold, style = Stroke(2.2f))
    drawCircle(DqGold, 3f, Offset(x + 1f, cy - 28f))

    // Staff
    drawLine(
        color = Color(0xFF8B5A2B),
        start = Offset(x + 18f, cy + 24f),
        end = Offset(x + 26f, cy - 34f),
        strokeWidth = 4f
    )
    drawCircle(DqSpell, radius = 8f, center = Offset(x + 27f, cy - 38f))
    drawCircle(DqGold, radius = 8f, center = Offset(x + 27f, cy - 38f), style = Stroke(2f))
    drawCircle(DqSpellCore, radius = 3f, center = Offset(x + 27f, cy - 38f))

    // Tiny shield badge
    drawRoundRect(
        color = DqGold,
        topLeft = Offset(x - 22f, cy + 6f),
        size = Size(12f, 14f),
        cornerRadius = CornerRadius(3f, 3f)
    )
    drawCircle(DqHeroBlue, 3f, Offset(x - 16f, cy + 12f))
}
