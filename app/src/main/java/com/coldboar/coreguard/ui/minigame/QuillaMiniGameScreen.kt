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
import androidx.compose.foundation.layout.fillMaxHeight
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

// Reference cyber-fantasy palette (Audit Keep mock).
private val CxVoid = Color(0xFF070712)
private val CxDeep = Color(0xFF0E0E22)
private val CxPanel = Color(0xFF161632)
private val CxPanelHi = Color(0xFF22224A)
private val CxTeal = Color(0xFF2AD9C8)
private val CxTealDim = Color(0xFF178F88)
private val CxTealGlow = Color(0xFF8CFFF4)
private val CxCyan = Color(0xFF3CF0FF)
private val CxGold = Color(0xFFE8C04A)
private val CxGoldDim = Color(0xFFB8922E)
private val CxDanger = Color(0xFFFF4B6E)
private val CxWorm = Color(0xFFA86BFF)
private val CxWormDark = Color(0xFF5C2E99)
private val CxOrb = Color(0xFF2FD0B0)
private val CxOrbDark = Color(0xFF127A68)
private val CxCircuit = Color(0xFF1A6A72)
private val CxWhite = Color(0xFFEAF8FF)
private val CxShadow = Color(0x99000000)
private val CxRobe = Color(0xFF1FA89A)
private val CxRobeDark = Color(0xFF0D5C56)
private val CxTipBg = Color(0xCC0A1628)

/**
 * Hidden Quilla purge mini-game — Flappy-style flight + spell shots.
 *
 * Wired to Quilla Infinity / Cyber Codex for threat flavor + teaching tips.
 * Homage styling only; tips are educational, not live detection.
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
    var codexDepth by remember { mutableIntStateOf(0) }
    var portraitAngel by remember { mutableStateOf("Raziel") }
    var tipTitle by remember { mutableStateOf("Quilla Infinity") }
    var tipBody by remember {
        mutableStateOf("Tap to jump & cast. Codex tips appear as you purge threats.")
    }
    var tipVisible by remember { mutableStateOf(true) }
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
        tipTitle = QuillaPurgeCodex.levelTitle(infinityGen, 0)
        tipBody = "Infinity teaching lane open — purge named threats for Codex tips."
        tipVisible = true
        debrief = emptyList()
        syncHud()
        frame++
    }

    LaunchedEffect(Unit) {
        CyberKnowledgeAssets.ensureLoaded(context)
        QuillaInfinityTrainer.restoreLite(context)
        val ledger = QuillaInfinityTrainer.ledger()
        infinityGen = ledger.generation
        codexDepth = if (ledger.totalCodexEntries > 0) {
            ledger.totalCodexEntries
        } else {
            CyberKnowledgeBase.size()
        }
        val deck = QuillaPurgeCodex.buildDeck(CyberKnowledgeBase.allEntries())
        engine.configureFlavor(deck)
        portraitAngel = deck.firstOrNull()?.angel ?: "Raziel"
        tipTitle = QuillaPurgeCodex.levelTitle(infinityGen, 0)
        tipBody = if (CyberKnowledgeBase.isLoaded()) {
            "Codex online · ${CyberKnowledgeBase.size()} entries · educational purge tips."
        } else {
            "Codex warming — using built-in Audit Keep threat cards."
        }
        tipVisible = true
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
        delay(4200)
        tipVisible = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF050510), CxVoid, Color(0xFF0A1220)))
            )
            .statusBarsPadding()
            .semantics { contentDescription = "Quilla mini-game. Tap to jump and cast." }
    ) {
        ReferenceHud(
            shield = hudShield,
            score = hudScore,
            levelLine = QuillaPurgeCodex.levelTitle(infinityGen, hudScore),
            subtitle = QuillaPurgeCodex.subtitle(infinityGen, codexDepth),
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Playfield pane
            Box(
                modifier = Modifier
                    .weight(1.55f)
                    .fillMaxHeight()
                    .border(2.dp, CxTealDim, RoundedCornerShape(8.dp))
                    .background(CxDeep, RoundedCornerShape(8.dp))
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
                        drawKeepPlayfield(scrollX = engine.scrollX, frame = frame)

                        engine.pipes.forEach { pipe ->
                            drawCircuitColumn(engine.renderPipeX(pipe), pipe, frame)
                        }
                        engine.pipes.forEachIndexed { index, pipe ->
                            if (!pipe.scored) {
                                val sx = engine.renderPipeX(pipe) + pipe.width * 0.5f
                                val sy = pipe.gapY + pipe.gapHeight * 0.45f +
                                    sin((frame + index * 17) * 0.12f) * 8f
                                drawShardCube(sx - 18f, sy - 10f, frame)
                                drawShardCube(sx + 16f, sy + 14f, frame + 9)
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
                                drawMalwareWorm(
                                    engine.renderEnemyX(enemy),
                                    engine.renderEnemyY(enemy),
                                    frame
                                )
                            } else {
                                drawSpikyMalware(
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
                        // Soft scanlines for CRT-ish polish
                        var sy = 0f
                        while (sy < size.height) {
                            drawLine(
                                Color.White.copy(alpha = 0.03f),
                                Offset(0f, sy),
                                Offset(size.width, sy),
                                1f
                            )
                            sy += 3f
                        }
                    }
                }

                // Floating Codex tip ticker (fully qualified: avoid RowScope receiver)
                androidx.compose.animation.AnimatedVisibility(
                    visible = tipVisible && !gameOver,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CxTipBg, RoundedCornerShape(8.dp))
                            .border(1.dp, CxTeal.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = tipTitle,
                            color = CxGold,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tipBody,
                            color = CxWhite,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 3,
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
                                    codexDepth = ledger.totalCodexEntries
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

            // Large portrait pane (reference right panel)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(2.dp, CxTeal, RoundedCornerShape(8.dp))
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
                        cx = size.width * 0.52f,
                        cy = size.height * 0.52f,
                        scale = (size.minDimension / 220f).coerceIn(0.85f, 1.55f),
                        frame = frame,
                        paths = paths
                    )
                }
                Text(
                    text = "QUILLA",
                    color = CxTealGlow,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                )
                Text(
                    text = "Infinity Intelligence",
                    color = CxGoldDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 26.dp)
                )
                Text(
                    text = QuillaPurgeCodex.portraitLine(portraitAngel, hudScore),
                    color = CxTealDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun ReferenceHud(
    shield: Int,
    score: Int,
    levelLine: String,
    subtitle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shardFill = (score / 40f).coerceIn(0f, 1f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = levelLine,
            color = CxWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = CxTealDim,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.weight(1.35f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(22.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.5f, size.height * 0.08f)
                        lineTo(size.width * 0.92f, size.height * 0.28f)
                        lineTo(size.width * 0.78f, size.height * 0.88f)
                        lineTo(size.width * 0.5f, size.height * 0.98f)
                        lineTo(size.width * 0.22f, size.height * 0.88f)
                        lineTo(size.width * 0.08f, size.height * 0.28f)
                        close()
                    }
                    drawPath(path, if (shield < 30) CxDanger else CxTeal)
                    drawPath(path, CxTealGlow, style = Stroke(1.5f))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                    ) {
                        val segments = 10
                        val gap = 3f
                        val segW = (size.width - gap * (segments - 1)) / segments
                        val filled = ((shield / 100f) * segments).toInt().coerceIn(0, segments)
                        for (i in 0 until segments) {
                            val x = i * (segW + gap)
                            val on = i < filled
                            drawRoundRect(
                                color = when {
                                    !on -> CxPanelHi
                                    shield < 30 -> CxDanger
                                    else -> CxCyan
                                },
                                topLeft = Offset(x, 0f),
                                size = Size(segW, size.height),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                        }
                    }
                    Text(
                        text = "Data Shards Collected",
                        color = CxTealDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
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
                    Canvas(modifier = Modifier.size(16.dp)) {
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
                        fontSize = 20.sp,
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

            Box(
                modifier = Modifier
                    .size(72.dp, 44.dp)
                    .background(CxDeep, RoundedCornerShape(4.dp))
                    .border(1.dp, CxTealDim, RoundedCornerShape(4.dp))
                    .padding(5.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(CxPanelHi, Offset(0f, 10f), Size(16f, 14f), CornerRadius(2f, 2f))
                    drawRoundRect(Color(0xFF4A2A6E), Offset(20f, 4f), Size(22f, 20f), CornerRadius(2f, 2f))
                    drawRoundRect(CxPanelHi, Offset(46f, 12f), Size(14f, 12f), CornerRadius(2f, 2f))
                    drawLine(CxTealDim, Offset(16f, 16f), Offset(20f, 14f), 2f)
                    drawLine(CxTealDim, Offset(42f, 14f), Offset(46f, 16f), 2f)
                    drawCircle(CxTealGlow, 3.5f, Offset(31f, 13f))
                }
                Text(
                    "?",
                    color = CxGold,
                    fontSize = 11.sp,
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

private fun DrawScope.drawKeepPlayfield(scrollX: Float, frame: Int) {
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF050518), CxDeep, Color(0xFF14102A), Color(0xFF0C1A22))
        )
    )
    val step = 36f
    val ox = scrollX % step
    var x = -ox
    while (x < size.width + step) {
        drawLine(CxCircuit.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, size.height), 1.2f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(CxCircuit.copy(alpha = 0.3f), Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
    for (i in 0..10) {
        val gx = (i * 73f + scrollX * 0.5f) % (size.width + 20f)
        val gy = 40f + (i * 89f) % (size.height - 80f)
        val pulse = 0.35f + 0.25f * sin(frame * 0.08f + i)
        drawCircle(CxTeal.copy(alpha = pulse), 2.5f, Offset(gx, gy))
        drawLine(
            CxTealDim.copy(alpha = 0.5f),
            Offset(gx, gy),
            Offset(gx + 16f, gy),
            1.5f
        )
        drawCircle(CxCyan.copy(alpha = 0.4f), 1.5f, Offset(gx + 16f, gy))
    }
    val platY = size.height * 0.72f
    drawRect(
        Brush.horizontalGradient(listOf(CxPanelHi.copy(alpha = 0.2f), CxPanelHi.copy(alpha = 0.65f))),
        Offset(0f, platY),
        Size(size.width, 6f)
    )
    for (i in 0..4) {
        val lx = size.width * 0.15f + i * 18f
        drawRect(CxTealDim.copy(alpha = 0.35f), Offset(lx, platY - 50f), Size(4f, 50f))
    }
    // Soft vignette
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.45f))
        )
    )
}

private fun DrawScope.drawCircuitColumn(x: Float, gate: PipeObstacle, frame: Int) {
    drawColumnBlock(x, 0f, gate.width, gate.gapY, frame)
    val by = gate.gapY + gate.gapHeight
    drawColumnBlock(x, by, gate.width, size.height - by, frame + 2)
    drawRect(CxTealGlow, Offset(x - 2f, gate.gapY - 4f), Size(gate.width + 4f, 4f))
    drawRect(CxTeal, Offset(x - 2f, by), Size(gate.width + 4f, 4f))
    // Gate energy ribbon
    val ribbon = 0.45f + 0.25f * sin(frame * 0.1f)
    drawRect(
        CxCyan.copy(alpha = ribbon * 0.25f),
        Offset(x, gate.gapY),
        Size(gate.width, gate.gapHeight)
    )
}

private fun DrawScope.drawColumnBlock(x: Float, y: Float, w: Float, h: Float, seed: Int) {
    if (h <= 1f) return
    drawRect(
        Brush.horizontalGradient(listOf(CxPanelHi, CxPanel, CxDeep)),
        Offset(x, y),
        Size(w, h)
    )
    drawRect(CxTealDim.copy(alpha = 0.75f), Offset(x, y), Size(w, h), style = Stroke(2f))
    var row = y + 8f
    while (row < y + h - 4f) {
        drawLine(CxCircuit.copy(alpha = 0.65f), Offset(x + 3f, row), Offset(x + w - 3f, row), 1f)
        row += 10f
    }
    val bus = x + w * (0.25f + (seed % 3) * 0.2f)
    drawLine(CxCyan.copy(alpha = 0.55f), Offset(bus, y + 3f), Offset(bus, y + h - 3f), 2f)
    val chipY = y + 20f + (seed % 4) * 12f
    if (chipY < y + h - 14f) {
        drawRoundRect(CxTealDim, Offset(x + 6f, chipY), Size(w - 12f, 7f), CornerRadius(2f, 2f))
        drawCircle(CxTealGlow, 2f, Offset(x + w * 0.5f, chipY + 3.5f))
    }
}

private fun DrawScope.drawShardCube(x: Float, y: Float, phase: Int) {
    val s = 8f + sin(phase * 0.18f) * 1.2f
    rotate(45f, Offset(x, y)) {
        drawRect(CxCyan.copy(alpha = 0.95f), Offset(x - s, y - s), Size(s * 2, s * 2))
        drawRect(CxTealGlow, Offset(x - s * 0.4f, y - s * 0.4f), Size(s * 0.8f, s * 0.8f))
        drawRect(CxWhite.copy(alpha = 0.35f), Offset(x - s, y - s), Size(s * 2, s * 2), style = Stroke(1f))
    }
    drawCircle(CxCyan.copy(alpha = 0.2f), s * 2.2f, Offset(x, y))
}

private fun DrawScope.drawFirebolt(x: Float, y: Float, frame: Int) {
    val len = 28f
    drawCircle(CxTeal.copy(alpha = 0.3f), 12f, Offset(x, y))
    drawCircle(CxCyan.copy(alpha = 0.15f), 18f, Offset(x - 8f, y))
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color.Transparent, CxCyan, CxTealGlow)),
        Offset(x - len, y - 5f),
        Size(len, 10f),
        CornerRadius(5f, 5f)
    )
    drawCircle(CxWhite, 4f + sin(frame * 0.4f), Offset(x, y))
}

private fun DrawScope.drawSpikyMalware(x: Float, y: Float, frame: Int, paths: PixelPaths) {
    rotate((frame * 3f) % 360f, Offset(x, y)) {
        for (i in 0 until 8) {
            val a = (Math.PI * 2 * i / 8.0).toFloat()
            paths.spike.rewind()
            paths.spike.moveTo(x + cos(a) * 9f, y + sin(a) * 9f)
            paths.spike.lineTo(x + cos(a) * 24f, y + sin(a) * 24f)
            paths.spike.lineTo(x + cos(a + 0.28f) * 9f, y + sin(a + 0.28f) * 9f)
            paths.spike.close()
            drawPath(paths.spike, CxOrb)
        }
    }
    drawCircle(
        Brush.radialGradient(listOf(CxTealGlow, CxOrb, CxOrbDark), Offset(x, y), 14f),
        13f,
        Offset(x, y)
    )
    drawCircle(CxVoid, 2.4f, Offset(x - 4f, y - 2f))
    drawCircle(CxVoid, 2.4f, Offset(x + 4f, y - 2f))
    drawPath(
        Path().apply {
            moveTo(x - 6f, y + 5f)
            lineTo(x - 2f, y + 8f)
            lineTo(x + 2f, y + 5f)
            lineTo(x + 6f, y + 8f)
        },
        CxVoid,
        style = Stroke(2f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawMalwareWorm(x: Float, y: Float, frame: Int) {
    val w = sin(frame * 0.22f)
    val segs = listOf(
        Offset(x, y),
        Offset(x + 13f, y + w * 5f),
        Offset(x + 25f, y - w * 4f),
        Offset(x + 36f, y + w * 3f),
        Offset(x + 46f, y - w * 2f)
    )
    for (i in segs.indices.reversed()) {
        val r = 11f - i * 1.5f
        drawCircle(
            Brush.radialGradient(listOf(CxWorm, CxWormDark), segs[i], r),
            r,
            segs[i]
        )
        drawCircle(CxPanelHi, r, segs[i], style = Stroke(1.2f))
    }
    drawCircle(CxCyan, 2.4f, Offset(x - 3f, y - 3f))
    drawCircle(CxCyan, 2.4f, Offset(x + 4f, y - 2f))
    drawCircle(CxVoid, 1.1f, Offset(x - 3f, y - 3f))
    drawCircle(CxVoid, 1.1f, Offset(x + 4f, y - 2f))
}

private fun DrawScope.drawPlayQuilla(x: Float, y: Float, frame: Int, paths: PixelPaths) {
    val bob = sin(frame * 0.14f) * 2f
    val cy = y + bob
    drawOval(CxShadow, Offset(x - 14f, cy + 26f), Size(30f, 8f))
    drawCircle(CxTeal.copy(alpha = 0.18f), 28f, Offset(x, cy))

    paths.body.rewind()
    paths.body.moveTo(x - 15f, cy + 4f)
    paths.body.lineTo(x + 14f, cy + 4f)
    paths.body.lineTo(x + 12f, cy + 28f)
    paths.body.lineTo(x - 13f, cy + 28f)
    paths.body.close()
    drawPath(paths.body, Brush.verticalGradient(listOf(CxRobe, CxRobeDark)))
    drawLine(CxCyan.copy(alpha = 0.8f), Offset(x - 7f, cy + 12f), Offset(x + 7f, cy + 12f), 2f)
    drawCircle(CxTealGlow, 3f, Offset(x, cy + 18f))

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
    drawCircle(CxTealGlow, 2.5f, Offset(x + 23f, cy - 32f))
}

private fun DrawScope.drawPortraitBackdrop(frame: Int) {
    drawCircle(
        Brush.radialGradient(
            listOf(CxTeal.copy(alpha = 0.22f), CxGold.copy(alpha = 0.06f), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.45f),
            radius = size.minDimension * 0.58f
        ),
        radius = size.minDimension * 0.58f,
        center = Offset(size.width * 0.5f, size.height * 0.45f)
    )
    val pulse = 0.25f + 0.15f * sin(frame * 0.07f)
    drawCircle(
        CxTealGlow.copy(alpha = pulse),
        size.minDimension * 0.42f,
        Offset(size.width * 0.5f, size.height * 0.48f),
        style = Stroke(2f)
    )
    drawCircle(
        CxGold.copy(alpha = pulse * 0.45f),
        size.minDimension * 0.48f,
        Offset(size.width * 0.5f, size.height * 0.48f),
        style = Stroke(1.2f)
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
    paths.cape.moveTo(cx - s(10f), cy - s(10f))
    paths.cape.lineTo(cx - s(70f), cy + s(90f))
    paths.cape.quadraticBezierTo(cx - s(20f), cy + s(50f), cx - s(8f), cy + s(30f))
    paths.cape.close()
    drawPath(paths.cape, Color(0xFF0A3D3A))

    paths.body.rewind()
    paths.body.moveTo(cx - s(48f), cy + s(10f))
    paths.body.lineTo(cx + s(48f), cy + s(10f))
    paths.body.lineTo(cx + s(40f), cy + s(110f))
    paths.body.lineTo(cx - s(40f), cy + s(110f))
    paths.body.close()
    drawPath(
        paths.body,
        Brush.verticalGradient(listOf(CxRobe, CxRobeDark, Color(0xFF063F3A)))
    )
    drawRoundRect(
        CxGold,
        Offset(cx - s(36f), cy + s(8f)),
        Size(s(72f), s(10f)),
        CornerRadius(s(3f), s(3f))
    )
    drawRoundRect(
        CxGoldDim,
        Offset(cx - s(36f), cy + s(8f)),
        Size(s(72f), s(10f)),
        CornerRadius(s(3f), s(3f)),
        style = Stroke(s(1.5f))
    )
    drawLine(CxCyan.copy(alpha = 0.85f), Offset(cx - s(20f), cy + s(35f)), Offset(cx + s(20f), cy + s(35f)), s(3f))
    drawLine(CxCyan.copy(alpha = 0.65f), Offset(cx, cy + s(35f)), Offset(cx, cy + s(70f)), s(3f))
    drawLine(CxTealGlow.copy(alpha = 0.7f), Offset(cx - s(28f), cy + s(55f)), Offset(cx - s(8f), cy + s(55f)), s(2f))
    drawLine(CxTealGlow.copy(alpha = 0.7f), Offset(cx + s(8f), cy + s(55f)), Offset(cx + s(28f), cy + s(55f)), s(2f))
    val corePulse = 0.65f + 0.35f * sin(frame * 0.12f)
    drawCircle(CxTeal.copy(alpha = corePulse), s(12f), Offset(cx, cy + s(48f)))
    drawCircle(CxTealGlow, s(6f), Offset(cx, cy + s(48f)))
    drawCircle(CxWhite.copy(alpha = 0.8f), s(2.5f), Offset(cx - s(2f), cy + s(46f)))

    drawRoundRect(CxRobeDark, Offset(cx - s(58f), cy + s(28f)), Size(s(18f), s(50f)), CornerRadius(s(6f), s(6f)))
    drawRoundRect(CxRobeDark, Offset(cx + s(40f), cy + s(28f)), Size(s(18f), s(50f)), CornerRadius(s(6f), s(6f)))
    drawRoundRect(CxGold, Offset(cx - s(58f), cy + s(70f)), Size(s(18f), s(8f)), CornerRadius(s(2f), s(2f)))
    drawRoundRect(CxGold, Offset(cx + s(40f), cy + s(70f)), Size(s(18f), s(8f)), CornerRadius(s(2f), s(2f)))

    drawCircle(CxVoid, s(38f), Offset(cx, cy - s(18f)))
    paths.hood.rewind()
    paths.hood.moveTo(cx - s(60f), cy - s(10f))
    paths.hood.lineTo(cx + s(55f), cy - s(6f))
    paths.hood.lineTo(cx + s(25f), cy - s(55f))
    paths.hood.lineTo(cx + s(8f), cy - s(95f))
    paths.hood.lineTo(cx - s(15f), cy - s(50f))
    paths.hood.close()
    drawPath(
        paths.hood,
        Brush.verticalGradient(listOf(CxTealGlow, CxTeal, CxTealDim, CxRobeDark))
    )
    drawPath(paths.hood, CxCyan.copy(alpha = 0.55f), style = Stroke(s(2.5f)))
    drawOval(CxRobeDark.copy(alpha = 0.55f), Offset(cx - s(55f), cy - s(18f)), Size(s(105f), s(18f)))
    drawCircle(CxGold, s(8f), Offset(cx - s(4f), cy - s(42f)))
    drawCircle(CxVoid, s(4f), Offset(cx - s(4f), cy - s(42f)))
    drawCircle(CxTealGlow, s(1.8f), Offset(cx - s(4f), cy - s(42f)))
    drawLine(CxGoldDim, Offset(cx + s(10f), cy - s(60f)), Offset(cx + s(18f), cy - s(70f)), s(2f))
    drawLine(CxGoldDim, Offset(cx - s(18f), cy - s(55f)), Offset(cx - s(10f), cy - s(65f)), s(2f))

    val eyeGlow = 0.75f + 0.25f * sin(frame * 0.15f)
    drawCircle(CxCyan.copy(alpha = eyeGlow), s(6f), Offset(cx - s(12f), cy - s(16f)))
    drawCircle(CxCyan.copy(alpha = eyeGlow), s(6f), Offset(cx + s(12f), cy - s(16f)))
    drawCircle(CxTealGlow, s(2.5f), Offset(cx - s(12f), cy - s(16f)))
    drawCircle(CxTealGlow, s(2.5f), Offset(cx + s(12f), cy - s(16f)))

    drawLine(
        Color(0xFF7A4A22),
        Offset(cx + s(50f), cy + s(100f)),
        Offset(cx + s(68f), cy - s(70f)),
        s(5f),
        cap = StrokeCap.Round
    )
    drawCircle(CxTeal, s(16f), Offset(cx + s(70f), cy - s(78f)))
    drawCircle(CxCyan, s(16f), Offset(cx + s(70f), cy - s(78f)), style = Stroke(s(3f)))
    drawCircle(CxTealGlow.copy(alpha = 0.8f), s(10f), Offset(cx + s(70f), cy - s(78f)), style = Stroke(s(2f)))
    drawCircle(CxWhite, s(3f), Offset(cx + s(70f), cy - s(78f)))
}
