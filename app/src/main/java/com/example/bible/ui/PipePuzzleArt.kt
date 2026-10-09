package com.example.bible.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.bible.games.pipes.PipeDir
import com.example.bible.games.pipes.PipeKind
import com.example.bible.games.pipes.PipeTile
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val BoardFrame = listOf(Color(0xFF334155), Color(0xFF1E293B))
private val TileTop = Color(0xFF475569)
private val TileBottom = Color(0xFF334155)
private val TileWetTop = Color(0xFF1E5A7A)
private val TileWetBottom = Color(0xFF15405A)
private val SteelDark = Color(0xFF3F4A5A)
private val SteelMid = Color(0xFF9AA6B8)
private val SteelLight = Color(0xFFF1F5F9)
private val DullDark = Color(0xFF4B5563)
private val DullMid = Color(0xFF8A94A3)
private val DullLight = Color(0xFFC9D1DC)
private val WaterDeep = Color(0xFF0369A1)
private val WaterLight = Color(0xFF38BDF8)
private val EmptyChannel = Color(0xFF1F2937)

/**
 * Для каждой плитки, до которой дошла вода от крана, — сторона, с которой вода в неё входит.
 * У самого крана значения нет: вода начинается в его центре.
 */
internal fun pipeWaterInlets(tiles: List<PipeTile>, gridSize: Int): Map<Int, PipeDir?> {
    val source = tiles.indexOfFirst { it.kind == PipeKind.SOURCE }
    if (source < 0) return emptyMap()
    val inlets = linkedMapOf<Int, PipeDir?>(source to null)
    val queue = ArrayDeque(listOf(source))
    while (queue.isNotEmpty()) {
        val i = queue.removeFirst()
        val r = i / gridSize
        val c = i % gridSize
        for (dir in PipeDir.entries) {
            if (!tiles[i].hasConnection(dir)) continue
            val (nr, nc) = when (dir) {
                PipeDir.N -> r - 1 to c
                PipeDir.S -> r + 1 to c
                PipeDir.E -> r to c + 1
                PipeDir.W -> r to c - 1
            }
            if (nr !in 0 until gridSize || nc !in 0 until gridSize) continue
            val j = nr * gridSize + nc
            if (j in inlets) continue
            if (!tiles[j].hasConnection(dir.opposite)) continue
            inlets[j] = dir.opposite
            queue.addLast(j)
        }
    }
    return inlets
}

@Composable
internal fun PipePuzzleArtBoard(
    tiles: List<PipeTile>,
    gridSize: Int,
    solved: Boolean,
    gameKey: Int,
    modifier: Modifier = Modifier,
    onTileTap: (Int) -> Unit,
) {
    val inlets = remember(tiles.toList(), gridSize) { pipeWaterInlets(tiles, gridSize) }
    val flow = rememberInfiniteTransition(label = "water")
    val phase by flow.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "waterPhase",
    )
    val swirl by flow.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "swirl",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(BoardFrame))
            .padding(8.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until gridSize) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    for (col in 0 until gridSize) {
                        val index = row * gridSize + col
                        val tile = tiles.getOrNull(index) ?: return@Column
                        PipeArtTile(
                            tile = tile,
                            index = index,
                            gameKey = gameKey,
                            wet = index in inlets,
                            inlet = inlets[index],
                            solved = solved,
                            phase = phase,
                            swirl = swirl,
                            onTap = { onTileTap(index) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(2.5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PipeArtTile(
    tile: PipeTile,
    index: Int,
    gameKey: Int,
    wet: Boolean,
    inlet: PipeDir?,
    solved: Boolean,
    phase: Float,
    swirl: Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var turns by remember(index, gameKey) { mutableIntStateOf(tile.rotation) }
    LaunchedEffect(tile.rotation) {
        val diff = Math.floorMod(tile.rotation - turns, 4)
        turns += diff
    }
    val angle by animateFloatAsState(
        targetValue = turns * 90f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "pipeTurn",
    )
    val residual = angle - turns * 90f
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onTap),
    ) {
        drawTileBase(wet)
        rotate(residual) {
            drawPipes(tile, wet && residual == 0f, inlet, phase)
            when (tile.kind) {
                PipeKind.SOURCE -> drawFaucet()
                PipeKind.SINK -> drawDrain(wet, solved, swirl)
                else -> Unit
            }
        }
    }
}

private fun DrawScope.drawTileBase(wet: Boolean) {
    val r = CornerRadius(size.minDimension * 0.16f)
    drawRoundRect(
        brush = Brush.verticalGradient(
            if (wet) listOf(TileWetTop, TileWetBottom) else listOf(TileTop, TileBottom),
        ),
        cornerRadius = r,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.10f),
        topLeft = Offset(1.5f, 1.5f),
        size = Size(size.width - 3f, size.height - 3f),
        cornerRadius = r,
        style = Stroke(width = 1.5f),
    )
    val screw = size.minDimension * 0.035f
    val inset = size.minDimension * 0.11f
    listOf(
        Offset(inset, inset),
        Offset(size.width - inset, inset),
        Offset(inset, size.height - inset),
        Offset(size.width - inset, size.height - inset),
    ).forEach { c ->
        drawCircle(Color.Black.copy(alpha = 0.25f), screw * 1.25f, c + Offset(0.6f, 0.8f))
        drawCircle(Color(0xFF94A3B8), screw, c)
    }
}

private fun edge(dir: PipeDir, w: Float, h: Float): Offset = when (dir) {
    PipeDir.N -> Offset(w / 2f, 0f)
    PipeDir.E -> Offset(w, h / 2f)
    PipeDir.S -> Offset(w / 2f, h)
    PipeDir.W -> Offset(0f, h / 2f)
}

private fun DrawScope.drawPipes(tile: PipeTile, wet: Boolean, inlet: PipeDir?, phase: Float) {
    val w = size.width
    val h = size.height
    val m = min(w, h)
    val center = Offset(w / 2f, h / 2f)
    val body = m * 0.30f
    val channel = m * 0.13f
    val dirs = PipeDir.entries.filter { tile.hasConnection(it) }
    val dark = if (wet) SteelDark else DullDark
    val mid = if (wet) SteelMid else DullMid
    val light = if (wet) SteelLight else DullLight

    for (dir in dirs) {
        val end = edge(dir, w, h)
        val vertical = dir == PipeDir.N || dir == PipeDir.S
        val left = if (vertical) Offset(center.x - body / 2, min(center.y, end.y)) else Offset(min(center.x, end.x), center.y - body / 2)
        val segSize = if (vertical) Size(body, kotlin.math.abs(end.y - center.y)) else Size(kotlin.math.abs(end.x - center.x), body)
        val shine = if (vertical) {
            Brush.horizontalGradient(listOf(dark, light, mid, dark), startX = left.x, endX = left.x + body)
        } else {
            Brush.verticalGradient(listOf(dark, light, mid, dark), startY = left.y, endY = left.y + body)
        }
        drawRect(Color.Black.copy(alpha = 0.28f), topLeft = left + Offset(m * 0.02f, m * 0.025f), size = segSize)
        drawRect(shine, topLeft = left, size = segSize)
        val flangeLen = m * 0.075f
        val flangeW = body * 1.32f
        val fTopLeft = when (dir) {
            PipeDir.N -> Offset(center.x - flangeW / 2, 0f)
            PipeDir.S -> Offset(center.x - flangeW / 2, h - flangeLen)
            PipeDir.E -> Offset(w - flangeLen, center.y - flangeW / 2)
            PipeDir.W -> Offset(0f, center.y - flangeW / 2)
        }
        val fSize = if (vertical) Size(flangeW, flangeLen) else Size(flangeLen, flangeW)
        val flangeShine = if (vertical) {
            Brush.horizontalGradient(listOf(dark, light, mid, dark), startX = fTopLeft.x, endX = fTopLeft.x + flangeW)
        } else {
            Brush.verticalGradient(listOf(dark, light, mid, dark), startY = fTopLeft.y, endY = fTopLeft.y + flangeW)
        }
        drawRoundRect(flangeShine, topLeft = fTopLeft, size = fSize, cornerRadius = CornerRadius(m * 0.02f))
    }

    for (dir in dirs) {
        drawLine(
            color = if (wet) WaterDeep else EmptyChannel,
            start = center,
            end = edge(dir, w, h),
            strokeWidth = channel,
            cap = StrokeCap.Butt,
        )
    }
    if (wet) {
        val dash = m * 0.12f
        val gap = m * 0.10f
        val period = dash + gap
        for (dir in dirs) {
            val incoming = dir == inlet
            val start = if (incoming) edge(dir, w, h) else center
            val end = if (incoming) center else edge(dir, w, h)
            drawLine(
                color = WaterLight,
                start = start,
                end = end,
                strokeWidth = channel * 0.62f,
                cap = StrokeCap.Butt,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = start,
                end = end,
                strokeWidth = channel * 0.28f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), -phase * period),
            )
        }
    }

    val hub = body * 0.72f
    drawCircle(Color.Black.copy(alpha = 0.3f), hub * 1.05f, center + Offset(m * 0.02f, m * 0.025f))
    drawCircle(
        brush = Brush.radialGradient(
            listOf(light, mid, dark),
            center = center - Offset(hub * 0.35f, hub * 0.35f),
            radius = hub * 1.6f,
        ),
        radius = hub,
        center = center,
    )
    drawCircle(if (wet) WaterLight else EmptyChannel, hub * 0.45f, center)
    if (wet) drawCircle(Color.White.copy(alpha = 0.5f), hub * 0.16f, center - Offset(hub * 0.14f, hub * 0.14f))
    val boltR = hub * 0.12f
    for (k in 0 until 4) {
        val a = Math.toRadians(45.0 + k * 90.0)
        val p = center + Offset((cos(a) * hub * 0.74f).toFloat(), (sin(a) * hub * 0.74f).toFloat())
        drawCircle(dark, boltR, p)
        drawCircle(light.copy(alpha = 0.8f), boltR * 0.5f, p - Offset(boltR * 0.25f, boltR * 0.25f))
    }
}

private fun DrawScope.drawFaucet() {
    val m = size.minDimension
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = m * 0.24f
    drawCircle(Color.Black.copy(alpha = 0.3f), r * 1.02f, c + Offset(m * 0.02f, m * 0.03f))
    drawCircle(Color(0xFFDC2626), r, c, style = Stroke(width = m * 0.07f))
    for (k in 0 until 4) {
        val a = Math.toRadians(k * 90.0 + 45.0)
        drawLine(
            Color(0xFFEF4444),
            c,
            c + Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat()),
            strokeWidth = m * 0.055f,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFFFCA5A5), Color(0xFFB91C1C)), center = c - Offset(r * 0.2f, r * 0.2f), radius = r * 0.5f),
        radius = r * 0.32f,
        center = c,
    )
    drawCircle(Color.White.copy(alpha = 0.7f), r * 0.09f, c - Offset(r * 0.12f, r * 0.12f))
}

private fun DrawScope.drawDrain(wet: Boolean, solved: Boolean, swirl: Float) {
    val m = size.minDimension
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = m * 0.25f
    drawCircle(Color.Black.copy(alpha = 0.3f), r * 1.05f, c + Offset(m * 0.02f, m * 0.03f))
    drawCircle(
        brush = Brush.radialGradient(listOf(SteelLight, SteelMid, SteelDark), center = c - Offset(r * 0.3f, r * 0.3f), radius = r * 1.5f),
        radius = r,
        center = c,
    )
    drawCircle(if (wet) WaterDeep else Color(0xFF0F172A), r * 0.74f, c)
    if (solved) {
        rotate(swirl, pivot = c) {
            for (k in 0 until 3) {
                drawArc(
                    color = WaterLight.copy(alpha = 0.85f),
                    startAngle = k * 120f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = c - Offset(r * 0.55f, r * 0.55f),
                    size = Size(r * 1.1f, r * 1.1f),
                    style = Stroke(width = m * 0.035f, cap = StrokeCap.Round),
                )
            }
        }
    }
    val bar = m * 0.025f
    for (k in -1..1) {
        val y = c.y + k * r * 0.32f
        drawLine(SteelMid, Offset(c.x - r * 0.6f, y), Offset(c.x + r * 0.6f, y), strokeWidth = bar, cap = StrokeCap.Round)
    }
}


/** Мини-картинка для плитки «Водопровод» на экране игр: две полоски по три клетки. */
@Composable
internal fun PipePreviewArt(modifier: Modifier = Modifier) {
    val layout = listOf(
        listOf(PipeTile(PipeKind.SOURCE, 1), PipeTile(PipeKind.STRAIGHT, 1), PipeTile(PipeKind.CORNER, 2)),
        listOf(PipeTile(PipeKind.SINK, 1), PipeTile(PipeKind.STRAIGHT, 1), PipeTile(PipeKind.CORNER, 3)),
    )
    Column(modifier) {
        layout.forEach { row ->
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                row.forEach { tile ->
                    Canvas(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(1.5.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    ) {
                        drawTileBase(true)
                        drawPipes(tile, true, null, 0.3f)
                        when (tile.kind) {
                            PipeKind.SOURCE -> drawFaucet()
                            PipeKind.SINK -> drawDrain(wet = true, solved = false, swirl = 0f)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}
