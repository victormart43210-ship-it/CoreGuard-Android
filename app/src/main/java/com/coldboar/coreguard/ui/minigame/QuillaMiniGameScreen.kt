package com.coldboar.coreguard.ui.minigame

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.quilla.QuillaInfinityTrainer
import com.coldboar.coreguard.quilla.QuillaMemoryFactory
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeAssets
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

// Audit Keep reference palette (cyber-fantasy pixel mock).
private val CxVoid = Color(0xFF060814)
private val CxDeep = Color(0xFF0B1224)
private val CxPanel = Color(0xFF121A32)
private val CxPanelHi = Color(0xFF1C2744)
private val CxTeal = Color(0xFF2AD9C8)
private val CxTealDim = Color(0xFF1A8F8A)
private val CxTealGlow = Color(0xFF9EFFF5)
private val CxCyan = Color(0xFF3CF0FF)
private val CxHudCyan = Color(0xFF5EEBFF)
private val CxGold = Color(0xFFE8C04A)
private val CxGoldDim = Color(0xFFB8922E)
private val CxDanger = Color(0xFFFF4B6E)
private val CxWorm = Color(0xFF2EC8B8)
private val CxWormMid = Color(0xFF1A9A90)
private val CxWormDark = Color(0xFF0E5E5A)
private val CxSpike = Color(0xFF7A7A92)
private val CxSpikeDark = Color(0xFF3E3E55)
private val CxPinkEye = Color(0xFFFF6B9A)
private val CxPinkHot = Color(0xFFFFB0C8)
private val CxCircuit = Color(0xFF1A6A72)
private val CxWhite = Color(0xFFEAF8FF)
private val CxShadow = Color(0x99000000)
private val CxRobe = Color(0xFF1FA89A)
private val CxRobeDark = Color(0xFF0D5C56)
private val CxTipBg = Color(0xCC071018)

/**
 * Hidden Quilla purge mini-game — Flappy-style flight + spell shots.
 *
 * Visual composition mirrors the Audit Keep reference: full-bleed dungeon
 * playfield, top HUD strip, and a large bottom-right Quilla portrait.
 * Quilla Infinity tips remain educational only.
 */
@Composable
fun QuillaMiniGameScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember { QuillaGameEngine() }
    val paths = remember { PixelPaths() }

    var frame by remember { mutableIntStateOf(0) }
    var hudScore by remember { mutableIntStateOf(0) }
    var hudShield by remember { mutableIntStateOf(100) }
    var gameOver by remember { mutableStateOf(false) }
    var sized by remember { mutableStateOf(false) }
    var infinityGen by remember { mutableIntStateOf(0) }
    var tipTitle by remember { mutableStateOf("") }
    var tipBody by remember { mutableStateOf("") }
    var tipVisible by remember { mutableStateOf(false) }
    var debrief by remember { mutableStateOf<List<String>>(emptyList()) }
    var trainingBusy by remember { mutableStateOf(false) }

    fun syncHud() {
        hudScore = engine.score
        hudShield = engine.shieldHp
        gameOver = engine.gameOver
        engine.pendingToast?.let { toast ->
            tipTitle = toast.title
            tipBody = toast.tip
            tipVisible = true
            engine.pendingToast = null
        }
        if (engine.gameOver) {
            debrief = QuillaPurgeCodex.debriefLines(engine.purgedTitles.toList(), infinityGen)
        }
    }

    fun resetRun() {
        engine.reset()
        tipVisible = false
        debrief = emptyList()
        syncHud()
        frame++
    }

    LaunchedEffect(Unit) {
        CyberKnowledgeAssets.ensureLoaded(context)
        QuillaInfinityTrainer.restoreLite(context)
        infinityGen = QuillaInfinityTrainer.ledger().generation
        engine.configureFlavor(QuillaPurgeCodex.buildDeck(CyberKnowledgeBase.allEntries()))
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
                if (engine.beginFrame(dt)) syncHud()
                frame++
            }
        }
        syncHud()
    }

    LaunchedEffect(tipVisible, tipTitle) {
        if (!tipVisible || gameOver) return@LaunchedEffect
        delay(3800)
        tipVisible = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CxVoid)
            .statusBarsPadding()
            .semantics { contentDescription = "Quilla mini-game. Tap to jump and cast." }
    ) {
        AuditKeepHud(
            shield = hudShield,
            score = hudScore,
            levelLine = QuillaPurgeCodex.levelTitle(infinityGen, hudScore),
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        // Full-bleed dungeon + bottom-right portrait (reference composition)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, CxTealDim, RoundedCornerShape(6.dp))
                    .background(CxDeep, RoundedCornerShape(6.dp))
                    .pointerInput(gameOver) {
                        if (gameOver) return@pointerInput
                        detectTapGestures {
                            engine.jumpAndCast()
                            frame++
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
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
                ) {
                    @Suppress("UNUSED_EXPRESSION")
                    frame
                    clipRect {
                        val blink = engine.invulnerable && ((frame / 3) % 2 == 0)
                        drawAuditKeepDungeon(scrollX = engine.scrollX, frame = frame)

                        engine.pipes.forEach { pipe ->
                            drawCircuitVault(engine.renderPipeX(pipe), pipe, frame)
                        }
                        engine.pipes.forEachIndexed { index, pipe ->
                            if (!pipe.scored) {
                                val sx = engine.renderPipeX(pipe) + pipe.width * 0.5f
                                val sy = pipe.gapY + pipe.gapHeight * 0.45f +
                                    sin((frame + index * 17) * 0.12f) * 8f
                                drawShardCrystal(sx - 16f, sy - 8f, frame)
                                drawShardCrystal(sx + 14f, sy + 16f, frame + 9)
                            }
                        }
                        engine.spells.forEach { spell ->
                            drawFirebolt(
                                engine.renderSpellX(spell),
                                engine.renderSpellY(spell),
                                frame
                            )
                        }
                        engine.enemies.forEach { enemy ->
                            if (enemy.isWorm) {
                                drawTealWorm(
                                    engine.renderEnemyX(enemy),
                                    engine.renderEnemyY(enemy),
                                    frame
                                )
                            } else {
                                drawSpikyVirus(
                                    engine.renderEnemyX(enemy),
                                    engine.renderEnemyY(enemy),
                                    frame,
                                    paths
                                )
                            }
                        }
                        if (!blink) {
                            drawPlayQuilla(
                                QuillaGameEngine.QUILLA_X,
                                engine.renderQuillaY(),
                                frame,
                                paths
                            )
                        }
                    }
                }

                // Compact Codex tip — bottom-left so portrait stays clear
                androidx.compose.animation.AnimatedVisibility(
                    visible = tipVisible && !gameOver,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, end = 130.dp, bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CxTipBg, RoundedCornerShape(6.dp))
                            .border(1.dp, CxTeal.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tipTitle,
                            color = CxGold,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tipBody,
                            color = CxWhite,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (gameOver) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .widthIn(max = 300.dp)
                                .background(CxPanel, RoundedCornerShape(8.dp))
                                .border(2.dp, CxTeal, RoundedCornerShape(8.dp))
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "PURGE FAILED",
                                color = CxDanger,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Data shards: $hudScore",
                                color = CxWhite,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            debrief.forEach { line ->
                                Text(
                                    line,
                                    color = CxTealGlow,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            PaneButton("RESTART PURGE", filled = true) { resetRun() }
                            Spacer(modifier = Modifier.height(8.dp))
                            PaneButton(
                                label = if (trainingBusy) "TRAINING…" else "TRAIN INFINITY CHOIR",
                                filled = false,
                                enabled = !trainingBusy
                            ) {
                                trainingBusy = true
                                runCatching {
                                    CyberKnowledgeAssets.ensureLoaded(context)
                                    val ledger = QuillaMemoryFactory.trainInfinityLocal(context)
                                    infinityGen = ledger.generation
                                    engine.configureFlavor(
                                        QuillaPurgeCodex.buildDeck(CyberKnowledgeBase.allEntries())
                                    )
                                    debrief = QuillaPurgeCodex.debriefLines(
                                        engine.purgedTitles.toList(),
                                        infinityGen
                                    )
                                }
                                trainingBusy = false
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            PaneButton("EXIT KEEP", filled = false, onClick = onDismiss)
                        }
                    }
                }
            }

            // Large bottom-right hero portrait (reference)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .width(148.dp)
                    .height(200.dp)
                    .border(2.dp, CxCyan.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(listOf(CxPanelHi, CxDeep, CxVoid)),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_EXPRESSION")
                    frame
                    drawPortraitBackdrop(frame)
                    drawHeroPortrait(
                        cx = size.width * 0.50f,
                        cy = size.height * 0.54f,
                        scale = (size.minDimension / 200f).coerceIn(0.9f, 1.35f),
                        frame = frame,
                        paths = paths
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditKeepHud(
    shield: Int,
    score: Int,
    levelLine: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shardFill = (score / 40f).coerceIn(0f, 1f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = levelLine.ifBlank { "Level 1-3: The Audit Keep" },
            color = CxHudCyan,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.weight(1.4f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.5f, size.height * 0.06f)
                        lineTo(size.width * 0.92f, size.height * 0.28f)
                        lineTo(size.width * 0.78f, size.height * 0.88f)
                        lineTo(size.width * 0.5f, size.height * 0.98f)
                        lineTo(size.width * 0.22f, size.height * 0.88f)
                        lineTo(size.width * 0.08f, size.height * 0.28f)
                        close()
                    }
                    drawPath(path, if (shield < 30) CxDanger else CxTeal)
                    drawPath(path, CxTealGlow, style = Stroke(1.8f))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Framed segmented shield bar (reference)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .border(1.dp, CxTealDim, RoundedCornerShape(3.dp))
                            .padding(2.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val segments = 12
                            val gap = 2.5f
                            val segW = (size.width - gap * (segments - 1)) / segments
                            val filled = ((shield / 100f) * segments).toInt().coerceIn(0, segments)
                            for (i in 0 until segments) {
                                val x = i * (segW + gap)
                                drawRoundRect(
                                    color = when {
                                        i >= filled -> CxPanelHi
                                        shield < 30 -> CxDanger
                                        else -> CxCyan
                                    },
                                    topLeft = Offset(x, 0f),
                                    size = Size(segW, size.height),
                                    cornerRadius = CornerRadius(1.5f, 1.5f)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Data Shards Collected",
                        color = CxTealDim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    ) {
                        drawRoundRect(CxPanelHi, Offset.Zero, size, CornerRadius(2f, 2f))
                        drawRoundRect(
                            Brush.horizontalGradient(listOf(CxTeal, CxTealGlow)),
                            Offset.Zero,
                            Size(size.width * shardFill, size.height),
                            CornerRadius(2f, 2f)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(18.dp)) {
                        val p = Path().apply {
                            moveTo(size.width * 0.5f, 0f)
                            lineTo(size.width, size.height * 0.35f)
                            lineTo(size.width * 0.75f, size.height)
                            lineTo(size.width * 0.25f, size.height)
                            lineTo(0f, size.height * 0.35f)
                            close()
                        }
                        drawPath(p, CxCyan)
                        drawPath(p, CxTealGlow, style = Stroke(1.5f))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$score",
                        color = CxCyan,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                Text(
                    text = "Score",
                    color = CxTealDim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }

            // Room-node mini-map (reference)
            Box(
                modifier = Modifier
                    .size(78.dp, 48.dp)
                    .background(CxDeep, RoundedCornerShape(4.dp))
                    .border(1.dp, CxTealDim, RoundedCornerShape(4.dp))
                    .padding(5.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(CxPanelHi, Offset(2f, 14f), Size(16f, 14f), CornerRadius(2f, 2f))
                    drawRoundRect(Color(0xFF3A2A6E), Offset(24f, 4f), Size(24f, 22f), CornerRadius(2f, 2f))
                    drawRoundRect(CxPanelHi, Offset(54f, 16f), Size(14f, 12f), CornerRadius(2f, 2f))
                    drawLine(CxTealDim, Offset(18f, 20f), Offset(24f, 16f), 2f)
                    drawLine(CxTealDim, Offset(48f, 16f), Offset(54f, 20f), 2f)
                    // Player marker (hood)
                    drawCircle(CxTeal, 5f, Offset(36f, 14f))
                    drawCircle(CxVoid, 2.2f, Offset(36f, 14f))
                }
                Text(
                    "?",
                    color = CxGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd)
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
private fun PaneButton(
    label: String,
    filled: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    !enabled -> CxPanelHi
                    filled -> CxTeal
                    else -> CxDeep
                },
                RoundedCornerShape(6.dp)
            )
            .border(1.5.dp, if (enabled) CxTeal else CxTealDim, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = when {
                !enabled -> CxTealDim
                filled -> CxVoid
                else -> CxTealGlow
            },
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

private class PixelPaths {
    val body = Path()
    val hood = Path()
    val spike = Path()
    val cape = Path()
}

private fun DrawScope.drawAuditKeepDungeon(scrollX: Float, frame: Int) {
    // Deep indigo fortress
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF050814), Color(0xFF0A1430), Color(0xFF0E1A28), Color(0xFF081018))
        )
    )

    // Distant vertical server pillars
    val pillarStep = 90f
    val ox = (scrollX * 0.35f) % pillarStep
    var px = -ox
    var pi = 0
    while (px < size.width + pillarStep) {
        val w = 18f + (pi % 3) * 6f
        drawRect(
            Color(0xFF0C2030).copy(alpha = 0.85f),
            Offset(px, 0f),
            Size(w, size.height)
        )
        drawLine(
            CxTeal.copy(alpha = 0.25f + 0.1f * sin(frame * 0.05f + pi)),
            Offset(px + w * 0.5f, 0f),
            Offset(px + w * 0.5f, size.height),
            2f
        )
        px += pillarStep
        pi++
    }

    // Mid-depth circuit lattice
    val step = 40f
    val gridOx = scrollX % step
    var x = -gridOx
    while (x < size.width + step) {
        drawLine(CxCircuit.copy(alpha = 0.28f), Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }

    // Floating platforms (reference dungeon shelves)
    val platforms = listOf(
        Triple(0.08f, 0.28f, 0.22f),
        Triple(0.42f, 0.48f, 0.18f),
        Triple(0.70f, 0.34f, 0.20f),
        Triple(0.18f, 0.68f, 0.28f),
        Triple(0.58f, 0.74f, 0.24f)
    )
    for ((fx, fy, fw) in platforms) {
        val scroll = (scrollX * 0.55f)
        val left = ((fx * size.width - scroll) % (size.width + 160f) + size.width + 160f) %
            (size.width + 160f) - 80f
        val top = fy * size.height
        val width = fw * size.width
        drawPlatform(left, top, width)
        // Ladder down from some platforms
        if (fy < 0.6f) {
            drawLadder(left + width * 0.55f, top + 8f, size.height * 0.18f)
        }
    }

    // Floor walkway
    val floorY = size.height * 0.88f
    drawRect(
        Brush.horizontalGradient(listOf(CxPanelHi, CxPanel, CxPanelHi)),
        Offset(0f, floorY),
        Size(size.width, size.height - floorY)
    )
    drawLine(CxTealGlow.copy(alpha = 0.55f), Offset(0f, floorY), Offset(size.width, floorY), 2.5f)
    var tile = -((scrollX * 0.8f) % 28f)
    while (tile < size.width) {
        drawLine(CxCircuit.copy(alpha = 0.5f), Offset(tile, floorY + 4f), Offset(tile, size.height), 1f)
        tile += 28f
    }

    // Soft vignette
    drawRect(
        Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = 0.4f),
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = 0.5f)
            )
        )
    )
}

private fun DrawScope.drawPlatform(x: Float, y: Float, w: Float) {
    val h = 14f
    drawRoundRect(
        Brush.verticalGradient(listOf(CxPanelHi, CxPanel)),
        Offset(x, y),
        Size(w, h),
        CornerRadius(3f, 3f)
    )
    drawRoundRect(
        CxTeal.copy(alpha = 0.85f),
        Offset(x, y),
        Size(w, h),
        CornerRadius(3f, 3f),
        style = Stroke(1.8f)
    )
    drawLine(CxTealGlow.copy(alpha = 0.7f), Offset(x + 4f, y + 3f), Offset(x + w - 4f, y + 3f), 2f)
    // Circuit traces
    var tx = x + 8f
    while (tx < x + w - 8f) {
        drawLine(CxCyan.copy(alpha = 0.35f), Offset(tx, y + 8f), Offset(tx + 10f, y + 8f), 1.2f)
        tx += 16f
    }
}

private fun DrawScope.drawLadder(x: Float, y: Float, h: Float) {
    val w = 16f
    drawLine(CxTealDim, Offset(x, y), Offset(x, y + h), 2.5f)
    drawLine(CxTealDim, Offset(x + w, y), Offset(x + w, y + h), 2.5f)
    var ry = y + 4f
    while (ry < y + h - 2f) {
        drawLine(CxTeal.copy(alpha = 0.75f), Offset(x, ry), Offset(x + w, ry), 2f)
        ry += 10f
    }
}

private fun DrawScope.drawCircuitVault(x: Float, gate: PipeObstacle, frame: Int) {
    drawVaultBlock(x, 0f, gate.width, gate.gapY, frame)
    val by = gate.gapY + gate.gapHeight
    drawVaultBlock(x, by, gate.width, size.height - by, frame + 2)
    // Glowing lips
    drawRect(CxTealGlow, Offset(x - 3f, gate.gapY - 5f), Size(gate.width + 6f, 5f))
    drawRect(CxTeal, Offset(x - 3f, by), Size(gate.width + 6f, 5f))
    val ribbon = 0.35f + 0.2f * sin(frame * 0.1f)
    drawRect(
        CxCyan.copy(alpha = ribbon * 0.18f),
        Offset(x, gate.gapY),
        Size(gate.width, gate.gapHeight)
    )
}

private fun DrawScope.drawVaultBlock(x: Float, y: Float, w: Float, h: Float, seed: Int) {
    if (h <= 1f) return
    drawRect(
        Brush.horizontalGradient(listOf(CxPanelHi, CxPanel, Color(0xFF0A1520))),
        Offset(x, y),
        Size(w, h)
    )
    drawRect(CxTeal.copy(alpha = 0.8f), Offset(x, y), Size(w, h), style = Stroke(2.2f))
    var row = y + 10f
    while (row < y + h - 6f) {
        drawLine(CxCircuit.copy(alpha = 0.7f), Offset(x + 4f, row), Offset(x + w - 4f, row), 1.2f)
        row += 11f
    }
    val bus = x + w * (0.3f + (seed % 3) * 0.18f)
    drawLine(CxCyan.copy(alpha = 0.6f), Offset(bus, y + 4f), Offset(bus, y + h - 4f), 2.2f)
    val chipY = y + 18f + (seed % 4) * 14f
    if (chipY < y + h - 16f) {
        drawRoundRect(CxTealDim, Offset(x + 7f, chipY), Size(w - 14f, 8f), CornerRadius(2f, 2f))
        drawCircle(CxTealGlow, 2.2f, Offset(x + w * 0.5f, chipY + 4f))
    }
}

private fun DrawScope.drawShardCrystal(x: Float, y: Float, phase: Int) {
    val bob = sin(phase * 0.18f) * 2.5f
    val cy = y + bob
    val s = 9f
    val p = Path().apply {
        moveTo(x, cy - s)
        lineTo(x + s * 0.7f, cy - s * 0.15f)
        lineTo(x + s * 0.45f, cy + s)
        lineTo(x - s * 0.45f, cy + s)
        lineTo(x - s * 0.7f, cy - s * 0.15f)
        close()
    }
    drawCircle(CxCyan.copy(alpha = 0.22f), s * 1.8f, Offset(x, cy))
    drawPath(p, Brush.verticalGradient(listOf(CxTealGlow, CxCyan, CxTeal)))
    drawPath(p, CxWhite.copy(alpha = 0.45f), style = Stroke(1.2f))
}

private fun DrawScope.drawFirebolt(x: Float, y: Float, frame: Int) {
    val len = 30f
    drawCircle(CxTeal.copy(alpha = 0.28f), 13f, Offset(x, y))
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color.Transparent, CxCyan, CxTealGlow, CxWhite)),
        Offset(x - len, y - 5f),
        Size(len, 10f),
        CornerRadius(5f, 5f)
    )
    drawCircle(CxWhite, 3.5f + sin(frame * 0.4f), Offset(x, y))
}

/** Purple-grey spiky virus with angry pink eyes (reference). */
private fun DrawScope.drawSpikyVirus(x: Float, y: Float, frame: Int, paths: PixelPaths) {
    rotate((frame * 2.4f) % 360f, Offset(x, y)) {
        for (i in 0 until 10) {
            val a = (Math.PI * 2 * i / 10.0).toFloat()
            paths.spike.rewind()
            paths.spike.moveTo(x + cos(a) * 8f, y + sin(a) * 8f)
            paths.spike.lineTo(x + cos(a) * 22f, y + sin(a) * 22f)
            paths.spike.lineTo(x + cos(a + 0.22f) * 8f, y + sin(a + 0.22f) * 8f)
            paths.spike.close()
            drawPath(paths.spike, CxSpike)
            drawPath(paths.spike, CxSpikeDark, style = Stroke(1f))
        }
    }
    drawCircle(
        Brush.radialGradient(listOf(CxSpike, CxSpikeDark), Offset(x, y), 13f),
        12f,
        Offset(x, y)
    )
    drawCircle(CxPinkEye, 3.2f, Offset(x - 4f, y - 2f))
    drawCircle(CxPinkEye, 3.2f, Offset(x + 4f, y - 2f))
    drawCircle(CxPinkHot, 1.3f, Offset(x - 4.5f, y - 2.5f))
    drawCircle(CxPinkHot, 1.3f, Offset(x + 3.5f, y - 2.5f))
    drawPath(
        Path().apply {
            moveTo(x - 5f, y + 5f)
            quadraticBezierTo(x, y + 9f, x + 5f, y + 5f)
        },
        CxVoid,
        style = Stroke(2f, cap = StrokeCap.Round)
    )
}

/** Teal segmented data-worm with pink eyes (reference). */
private fun DrawScope.drawTealWorm(x: Float, y: Float, frame: Int) {
    val w = sin(frame * 0.22f)
    val segs = listOf(
        Offset(x, y),
        Offset(x + 12f, y + w * 5f),
        Offset(x + 24f, y - w * 4f),
        Offset(x + 35f, y + w * 3f),
        Offset(x + 45f, y - w * 2f)
    )
    for (i in segs.indices.reversed()) {
        val r = 11f - i * 1.4f
        val fill = if (i == 0) CxWorm else if (i % 2 == 0) CxWormMid else CxWormDark
        drawCircle(fill, r, segs[i])
        drawCircle(CxTealGlow.copy(alpha = 0.35f), r, segs[i], style = Stroke(1.2f))
    }
    drawCircle(CxPinkEye, 2.6f, Offset(x - 3f, y - 3f))
    drawCircle(CxPinkEye, 2.6f, Offset(x + 4f, y - 2f))
    drawCircle(CxPinkHot, 1.1f, Offset(x - 3.4f, y - 3.4f))
    drawCircle(CxPinkHot, 1.1f, Offset(x + 3.6f, y - 2.4f))
}

private fun DrawScope.drawPlayQuilla(x: Float, y: Float, frame: Int, paths: PixelPaths) {
    val bob = sin(frame * 0.14f) * 2f
    val cy = y + bob
    drawOval(CxShadow, Offset(x - 14f, cy + 26f), Size(30f, 8f))
    drawCircle(CxTeal.copy(alpha = 0.16f), 26f, Offset(x, cy))

    paths.body.rewind()
    paths.body.moveTo(x - 15f, cy + 4f)
    paths.body.lineTo(x + 14f, cy + 4f)
    paths.body.lineTo(x + 12f, cy + 28f)
    paths.body.lineTo(x - 13f, cy + 28f)
    paths.body.close()
    drawPath(paths.body, Brush.verticalGradient(listOf(CxRobe, CxRobeDark)))
    // Circuit traces on robe
    drawLine(CxCyan.copy(alpha = 0.85f), Offset(x - 7f, cy + 12f), Offset(x + 7f, cy + 12f), 2f)
    drawLine(CxCyan.copy(alpha = 0.55f), Offset(x, cy + 12f), Offset(x, cy + 22f), 1.5f)
    drawCircle(CxGold, 4f, Offset(x, cy + 18f))
    drawCircle(CxTealGlow, 2f, Offset(x, cy + 18f))

    paths.hood.rewind()
    paths.hood.moveTo(x - 20f, cy - 4f)
    paths.hood.lineTo(x + 18f, cy - 2f)
    paths.hood.lineTo(x + 4f, cy - 40f)
    paths.hood.lineTo(x - 6f, cy - 34f)
    paths.hood.close()
    drawPath(paths.hood, Brush.verticalGradient(listOf(CxTealGlow, CxTeal, CxTealDim)))
    drawCircle(CxGold, 3f, Offset(x - 1f, cy - 20f))
    drawCircle(CxVoid, 1.4f, Offset(x - 1f, cy - 20f))

    drawCircle(CxVoid, 12f, Offset(x, cy - 6f))
    drawCircle(CxCyan, 3f, Offset(x - 4.5f, cy - 6f))
    drawCircle(CxCyan, 3f, Offset(x + 4.5f, cy - 6f))
    drawCircle(CxTealGlow, 1.3f, Offset(x - 4.5f, cy - 6f))
    drawCircle(CxTealGlow, 1.3f, Offset(x + 4.5f, cy - 6f))

    drawLine(Color(0xFF6B3F1F), Offset(x + 14f, cy + 24f), Offset(x + 22f, cy - 28f), 3.5f)
    drawCircle(CxTeal, 8f, Offset(x + 23f, cy - 32f))
    drawCircle(CxCyan, 8f, Offset(x + 23f, cy - 32f), style = Stroke(2f))
    // Stylized rune in staff orb
    drawLine(CxTealGlow, Offset(x + 20f, cy - 36f), Offset(x + 20f, cy - 28f), 1.8f)
    drawLine(CxTealGlow, Offset(x + 20f, cy - 34f), Offset(x + 26f, cy - 30f), 1.5f)
}

private fun DrawScope.drawPortraitBackdrop(frame: Int) {
    drawCircle(
        Brush.radialGradient(
            listOf(CxTeal.copy(alpha = 0.2f), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.48f),
            radius = size.minDimension * 0.55f
        ),
        radius = size.minDimension * 0.55f,
        center = Offset(size.width * 0.5f, size.height * 0.48f)
    )
    val pulse = 0.22f + 0.12f * sin(frame * 0.07f)
    drawCircle(
        CxTealGlow.copy(alpha = pulse),
        size.minDimension * 0.40f,
        Offset(size.width * 0.5f, size.height * 0.50f),
        style = Stroke(1.8f)
    )
}

private fun DrawScope.drawHeroPortrait(
    cx: Float,
    cy: Float,
    scale: Float,
    frame: Int,
    paths: PixelPaths
) {
    fun s(v: Float) = v * scale

    paths.cape.rewind()
    paths.cape.moveTo(cx - s(8f), cy - s(8f))
    paths.cape.lineTo(cx - s(62f), cy + s(85f))
    paths.cape.quadraticBezierTo(cx - s(18f), cy + s(48f), cx - s(6f), cy + s(28f))
    paths.cape.close()
    drawPath(paths.cape, Color(0xFF0A3D3A))

    paths.body.rewind()
    paths.body.moveTo(cx - s(44f), cy + s(8f))
    paths.body.lineTo(cx + s(44f), cy + s(8f))
    paths.body.lineTo(cx + s(36f), cy + s(100f))
    paths.body.lineTo(cx - s(36f), cy + s(100f))
    paths.body.close()
    drawPath(
        paths.body,
        Brush.verticalGradient(listOf(CxRobe, CxRobeDark, Color(0xFF063F3A)))
    )

    // Gold collar + shoulder plates (reference armor)
    drawRoundRect(CxGold, Offset(cx - s(34f), cy + s(6f)), Size(s(68f), s(9f)), CornerRadius(s(3f), s(3f)))
    drawCircle(CxGoldDim, s(8f), Offset(cx - s(30f), cy + s(22f)))
    drawCircle(CxGoldDim, s(8f), Offset(cx + s(30f), cy + s(22f)))
    drawCircle(CxGold, s(4f), Offset(cx - s(30f), cy + s(22f)))
    drawCircle(CxGold, s(4f), Offset(cx + s(30f), cy + s(22f)))

    // Circuit robe pattern
    drawLine(CxCyan.copy(alpha = 0.9f), Offset(cx - s(18f), cy + s(32f)), Offset(cx + s(18f), cy + s(32f)), s(2.8f))
    drawLine(CxCyan.copy(alpha = 0.7f), Offset(cx, cy + s(32f)), Offset(cx, cy + s(68f)), s(2.8f))
    drawLine(CxTealGlow.copy(alpha = 0.65f), Offset(cx - s(24f), cy + s(50f)), Offset(cx - s(6f), cy + s(50f)), s(2f))
    drawLine(CxTealGlow.copy(alpha = 0.65f), Offset(cx + s(6f), cy + s(50f)), Offset(cx + s(24f), cy + s(50f)), s(2f))
    val corePulse = 0.65f + 0.35f * sin(frame * 0.12f)
    drawCircle(CxTeal.copy(alpha = corePulse), s(11f), Offset(cx, cy + s(44f)))
    drawCircle(CxTealGlow, s(5.5f), Offset(cx, cy + s(44f)))

    drawRoundRect(CxRobeDark, Offset(cx - s(54f), cy + s(26f)), Size(s(16f), s(46f)), CornerRadius(s(5f), s(5f)))
    drawRoundRect(CxRobeDark, Offset(cx + s(38f), cy + s(26f)), Size(s(16f), s(46f)), CornerRadius(s(5f), s(5f)))
    drawRoundRect(CxGold, Offset(cx - s(54f), cy + s(66f)), Size(s(16f), s(7f)), CornerRadius(s(2f), s(2f)))
    drawRoundRect(CxGold, Offset(cx + s(38f), cy + s(66f)), Size(s(16f), s(7f)), CornerRadius(s(2f), s(2f)))

    // Hood void + pointed hat
    drawCircle(CxVoid, s(34f), Offset(cx, cy - s(16f)))
    paths.hood.rewind()
    paths.hood.moveTo(cx - s(54f), cy - s(8f))
    paths.hood.lineTo(cx + s(50f), cy - s(4f))
    paths.hood.lineTo(cx + s(22f), cy - s(50f))
    paths.hood.lineTo(cx + s(6f), cy - s(88f))
    paths.hood.lineTo(cx - s(14f), cy - s(46f))
    paths.hood.close()
    drawPath(
        paths.hood,
        Brush.verticalGradient(listOf(CxTealGlow, CxTeal, CxTealDim, CxRobeDark))
    )
    drawPath(paths.hood, CxCyan.copy(alpha = 0.5f), style = Stroke(s(2.2f)))
    drawOval(CxRobeDark.copy(alpha = 0.5f), Offset(cx - s(50f), cy - s(16f)), Size(s(96f), s(16f)))
    drawCircle(CxGold, s(7f), Offset(cx - s(3f), cy - s(38f)))
    drawCircle(CxVoid, s(3.5f), Offset(cx - s(3f), cy - s(38f)))
    drawCircle(CxTealGlow, s(1.6f), Offset(cx - s(3f), cy - s(38f)))

    val eyeGlow = 0.75f + 0.25f * sin(frame * 0.15f)
    drawCircle(CxCyan.copy(alpha = eyeGlow), s(5.5f), Offset(cx - s(11f), cy - s(14f)))
    drawCircle(CxCyan.copy(alpha = eyeGlow), s(5.5f), Offset(cx + s(11f), cy - s(14f)))
    drawCircle(CxTealGlow, s(2.2f), Offset(cx - s(11f), cy - s(14f)))
    drawCircle(CxTealGlow, s(2.2f), Offset(cx + s(11f), cy - s(14f)))

    // Staff + rune ring (reference “R” orb)
    drawLine(
        Color(0xFF7A4A22),
        Offset(cx + s(46f), cy + s(92f)),
        Offset(cx + s(62f), cy - s(64f)),
        s(4.5f),
        cap = StrokeCap.Round
    )
    val orb = Offset(cx + s(64f), cy - s(72f))
    drawCircle(CxTeal, s(14f), orb)
    drawCircle(CxCyan, s(14f), orb, style = Stroke(s(2.5f)))
    drawCircle(CxTealGlow.copy(alpha = 0.75f), s(9f), orb, style = Stroke(s(1.8f)))
    // Simple rune strokes
    drawLine(CxWhite, Offset(orb.x - s(3f), orb.y - s(6f)), Offset(orb.x - s(3f), orb.y + s(6f)), s(2f))
    drawLine(CxWhite, Offset(orb.x - s(3f), orb.y - s(4f)), Offset(orb.x + s(5f), orb.y - s(1f)), s(2f))
    drawLine(CxWhite, Offset(orb.x - s(3f), orb.y + s(1f)), Offset(orb.x + s(4f), orb.y + s(5f)), s(2f))
}
