package com.example.bible.ui

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
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.bible.games.RedBallAabb
import com.example.bible.games.RedBallDraft
import com.example.bible.games.RedBallParticle
import com.example.bible.games.RedBallPiece
import com.example.bible.games.RedBallPieceKind
import com.example.bible.games.RedBallScene
import com.example.bible.games.RedBallSim
import com.example.bible.games.RedBallSlope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal const val EDITOR_VIEW_ROWS = 14f

internal fun editorScale(heightPx: Float, cell: Float): Float {
    if (heightPx <= 1f || cell <= 1f) return 1f
    return heightPx / (EDITOR_VIEW_ROWS * cell)
}

internal data class EditorCamRange(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
)

/** Камеру можно увести за край нарисованного: туда и ставят новые клетки, мир растёт. */
internal fun editorCamRange(widthPx: Float, heightPx: Float, draft: RedBallDraft): EditorCamRange {
    val cell = draft.cell
    val scale = editorScale(heightPx, cell)
    val viewW = if (scale <= 0f) cell else widthPx / scale
    val viewH = if (scale <= 0f) EDITOR_VIEW_ROWS * cell else heightPx / scale
    val margin = cell * 8f
    var left = 0f
    var top = 0f
    var right = draft.cols * cell
    var bottom = draft.rows * cell
    if (draft.pieces.isNotEmpty()) {
        left = draft.pieces.minOf { it.c * cell }
        top = draft.pieces.minOf { it.r * cell }
        right = draft.pieces.maxOf { (it.c + it.w) * cell }
        bottom = draft.pieces.maxOf { (it.r + it.h) * cell }
    }
    val minX = left - margin
    val minY = top - margin
    return EditorCamRange(
        minX = minX,
        maxX = (right + margin - viewW).coerceAtLeast(minX),
        minY = minY,
        maxY = (bottom + margin - viewH).coerceAtLeast(minY),
    )
}

internal fun DrawScope.drawRedBallPlay(sim: RedBallSim) {
    val scene = sim.scene
    val viewH = 620f
    val scale = size.height / viewH
    val viewW = size.width / scale
    val maxCamX = (scene.worldW - viewW).coerceAtLeast(scene.minX)
    val camX = (sim.ballX - viewW * 0.38f).coerceIn(scene.minX, maxCamX)
    // Камера следит за мячом и по вертикали: при прыжке мир (и фон) смещается.
    val restCamY = (scene.worldH - viewH).coerceAtLeast(scene.minY)
    val camY = (sim.ballY - viewH * 0.58f).coerceIn(scene.minY - viewH * 1.5f, restCamY)
    drawFairySky(camX, scale, liftPx = (restCamY - camY) * scale)
    withTransform({
        scale(scale, scale, pivot = Offset.Zero)
        translate(-camX, -camY)
    }) {
        drawVoid(scene)
        drawSolids(scene.solids, scene.slopes, scene.minX, scene.worldW)
        for (block in scene.concrete) {
            drawConcreteBlock(block, topIsOpen(block, scene.concrete, emptyList()))
        }
        for (slope in scene.slopes) drawSlopeBank(slope.x0, slope.y0, slope.x1, slope.y1)
        for (ladder in scene.ladders) drawLadder(ladder)
        for (spike in scene.spikes) drawIronSpike(spike.x, spike.top, spike.w, spike.h)
        for (spring in scene.springs) drawFairySpring(spring.l, spring.t, spring.r, spring.b)
        for (star in sim.stars) {
            if (!star.taken) drawCollectStar(star.x, star.y, 16f, sim.anim, star.attached)
        }
        for (magnet in sim.magnets) if (!magnet.taken) drawMagnet(magnet.x, magnet.y, sim.anim)
        drawFairyDoor(scene.door.l, scene.door.t, scene.door.r, scene.door.b)
        for (crate in sim.crates) drawWoodCrate(crate.x, crate.y, crate.size)
        for (block in sim.blocks) drawConcreteCrate(block.x, block.y, block.size)
        for (enemy in sim.enemies) {
            if (enemy.alive) drawAngrySquare(enemy.x, enemy.y, enemy.size, enemy.angle)
        }
        for (particle in sim.particles) drawParticle(particle)
        if (sim.magnetTime > 0f) drawMagnetAura(sim.ballX, sim.ballY, sim.anim)
        drawGlossyBall(
            sim.ballX,
            sim.ballY,
            RedBallScene.BALL_R,
            sim.ballAngle,
            sim.groundBelowBall(),
            sim.jellyX,
            sim.jellyY,
        )
    }
}

internal fun DrawScope.drawRedBallEditor(
    draft: RedBallDraft,
    camX: Float,
    camY: Float,
    hoverC: Int,
    hoverR: Int,
    hoverW: Int,
    hoverH: Int,
    showHover: Boolean,
    selectedId: Long?,
) {
    val scale = editorScale(size.height, draft.cell)
    val range = editorCamRange(size.width, size.height, draft)
    val cam = camX.coerceIn(range.minX, range.maxX)
    val camDown = camY.coerceIn(range.minY, range.maxY)
    val viewW = size.width / scale
    val viewH = size.height / scale
    drawFairySky(cam, scale, liftPx = -camDown * scale * 0.15f)
    withTransform({
        scale(scale, scale, pivot = Offset.Zero)
        translate(-cam, -camDown)
    }) {
        val cell = draft.cell
        val minC = kotlin.math.floor((cam - cell) / cell).toInt()
        val maxC = kotlin.math.ceil((cam + viewW + cell) / cell).toInt()
        val minR = kotlin.math.floor((camDown - cell) / cell).toInt()
        val maxR = kotlin.math.ceil((camDown + viewH + cell) / cell).toInt()
        val line = Color.White.copy(alpha = 0.28f)
        val bold = Color.White.copy(alpha = 0.45f)
        for (c in minC..maxC) {
            val x = c * cell
            drawLine(if (c % 4 == 0) bold else line, Offset(x, minR * cell), Offset(x, maxR * cell), if (c % 4 == 0) 2f else 1f)
        }
        for (r in minR..maxR) {
            val y = r * cell
            drawLine(if (r % 2 == 0) bold else line, Offset(minC * cell, y), Offset(maxC * cell, y), if (r % 2 == 0) 2f else 1f)
        }
        val grounds = draft.pieces.filter { it.kind == RedBallPieceKind.GROUND }.map { it.box(cell) }
        val slopes = draft.pieces.filter { it.kind == RedBallPieceKind.SLOPE }.map { it.slopeLine(cell) }
        val concretes = draft.pieces.filter { it.kind == RedBallPieceKind.CONCRETE }.map { it.box(cell) }
        for (piece in draft.pieces) {
            if (piece.kind != RedBallPieceKind.GROUND) continue
            val box = piece.box(cell)
            drawGroundBlock(box, topIsOpen(box, grounds, slopes))
        }
        for (piece in draft.pieces) {
            val box = piece.box(cell)
            when (piece.kind) {
                RedBallPieceKind.GROUND -> Unit
                RedBallPieceKind.CONCRETE -> drawConcreteBlock(box, topIsOpen(box, concretes, emptyList()))
                RedBallPieceKind.SLOPE -> {
                    val lineSlope = piece.slopeLine(cell)
                    drawSlopeBank(lineSlope.x0, lineSlope.y0, lineSlope.x1, lineSlope.y1)
                }
                RedBallPieceKind.LADDER -> drawLadder(box)
                RedBallPieceKind.SPIKE -> drawIronSpike(
                    (box.l + box.r) / 2f,
                    box.t,
                    box.r - box.l,
                    box.b - box.t,
                )
                RedBallPieceKind.SPRING -> drawFairySpring(box.l, box.t, box.r, box.b)
                RedBallPieceKind.CRATE -> drawWoodCrate((box.l + box.r) / 2f, (box.t + box.b) / 2f, min(piece.w, piece.h) * cell)
                RedBallPieceKind.STAR -> drawCollectStar((box.l + box.r) / 2f, (box.t + box.b) / 2f, 16f, 0.6f, false)
                RedBallPieceKind.MAGNET -> drawMagnet((box.l + box.r) / 2f, (box.t + box.b) / 2f, 0.4f)
                RedBallPieceKind.ENEMY -> drawAngrySquare((box.l + box.r) / 2f, (box.t + box.b) / 2f, min(piece.w, piece.h) * cell, 0.15f)
                RedBallPieceKind.DOOR -> drawFairyDoor(box.l, box.t, box.r, box.b)
                RedBallPieceKind.START -> {
                    val x = (box.l + box.r) / 2f
                    val y = box.b - RedBallScene.BALL_R
                    drawGlossyBall(x, y, RedBallScene.BALL_R, 0.4f, y + RedBallScene.BALL_R)
                    drawCircle(
                        Color.White,
                        RedBallScene.BALL_R + 5f,
                        Offset(x, y),
                        style = Stroke(width = 3f),
                    )
                }
            }
            if (piece.id == selectedId) {
                drawRoundRect(
                    Color(0xFF0369A1),
                    Offset(box.l - 3f, box.t - 3f),
                    Size(box.r - box.l + 6f, box.b - box.t + 6f),
                    CornerRadius(8f, 8f),
                    style = Stroke(width = 4f),
                )
            }
        }
        if (showHover && hoverW > 0 && hoverH > 0) {
            drawRoundRect(
                Color.White.copy(alpha = 0.55f),
                Offset(hoverC * cell, hoverR * cell),
                Size(hoverW * cell, hoverH * cell),
                CornerRadius(10f, 10f),
                style = Stroke(width = 4f),
            )
        }
    }
}

/**
 * [liftPx] — насколько камера поднялась над обычным уровнем (в пикселях экрана).
 * Дальние слои сдвигаются меньше, ближние — сильнее (параллакс по вертикали).
 */
internal fun DrawScope.drawFairySky(camX: Float, worldToScreen: Float, liftPx: Float = 0f) {
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFFBFDBFE),
            0.28f to Color(0xFFFBCFE8),
            0.55f to Color(0xFFFDE68A),
            0.78f to Color(0xFFD9F99D),
            1f to Color(0xFF86EFAC),
        ),
    )
    val sun = Offset(size.width * 0.8f - camX * worldToScreen * 0.05f, size.height * 0.13f + liftPx * 0.04f)
    drawCircle(Color(0xFFFFF7D6).copy(alpha = 0.38f), 86f, sun)
    drawCircle(Color(0xFFFDE68A).copy(alpha = 0.9f), 48f, sun)
    drawCircle(Color(0xFFFFFBEB), 28f, sun)
    withTransform({ translate(0f, liftPx * 0.12f) }) {
        drawCloudBand(camX, worldToScreen, 0.12f, 0.18f, 1.1f, 0.95f)
    }
    withTransform({ translate(0f, liftPx * 0.2f) }) {
        drawCloudBand(camX, worldToScreen, 0.22f, 0.3f, 0.75f, 0.82f)
    }
    val horizon = size.height * 0.7f
    drawHills(camX, worldToScreen, 0.1f, horizon + liftPx * 0.1f - size.height * 0.04f, Color(0xFFDBEAFE), 22f, 240f)
    drawHills(camX, worldToScreen, 0.26f, horizon + liftPx * 0.26f, Color(0xFF93C5FD), 42f, 170f)
    drawHills(camX, worldToScreen, 0.4f, horizon + liftPx * 0.4f + size.height * 0.045f, Color(0xFF6EE7B7), 30f, 140f)
    val span = size.width * 1.8f
    val shift = wrapShift(camX * worldToScreen * 0.48f, span)
    val base = horizon + liftPx * 0.5f + size.height * 0.03f
    for (i in -1..6) {
        val x = i * span / 5f - shift
        drawStoryTree(x, base, size.height * if (i % 2 == 0) 0.15f else 0.11f)
    }
    val bird = camX * worldToScreen * 0.08f
    val birdLift = liftPx * 0.1f
    drawBird(size.width * 0.24f - bird, size.height * 0.22f + birdLift, 18f)
    drawBird(size.width * 0.33f - bird, size.height * 0.19f + birdLift, 13f)
    val moteSpan = size.width * 1.5f
    val moteShift = wrapShift(camX * worldToScreen * 0.72f, moteSpan)
    for (i in 0 until 7) {
        val x = i * moteSpan / 5f - moteShift
        val y = size.height * (0.34f + (i % 3) * 0.07f) + liftPx * 0.62f
        drawCircle(Color.White.copy(alpha = 0.45f), 2.2f + (i % 3), Offset(x, y))
    }
}

private fun wrapShift(value: Float, span: Float): Float {
    if (span <= 1f) return 0f
    val m = value % span
    return if (m < 0f) m + span else m
}

private fun DrawScope.drawVoid(scene: RedBallScene) {
    val top = scene.voidY - 50f
    if (top >= scene.worldH) return
    val left = scene.minX - 40f
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, Color(0x66221A3A), Color(0xFF1E1B4B)),
            startY = top,
            endY = scene.worldH,
        ),
        topLeft = Offset(left, top),
        size = Size(scene.worldW - left + 40f, scene.worldH - top),
    )
}

private fun DrawScope.drawSolids(
    solids: List<RedBallAabb>,
    slopes: List<RedBallSlope>,
    minX: Float,
    worldW: Float,
) {
    for (solid in solids) {
        if (solid.r <= minX + 0.5f || solid.l >= worldW - 0.5f) continue
        drawGroundBlock(solid, topIsOpen(solid, solids, slopes))
    }
}

private fun topIsOpen(solid: RedBallAabb, solids: List<RedBallAabb>, slopes: List<RedBallSlope>): Boolean {
    for (other in solids) {
        if (other === solid) continue
        if (other.t < solid.t - 0.5f && other.b > solid.t + 1f && other.l < solid.r - 4f && other.r > solid.l + 4f) {
            return false
        }
    }
    val mid = (solid.l + solid.r) * 0.5f
    for (slope in slopes) {
        val minX = min(slope.x0, slope.x1)
        val maxX = max(slope.x0, slope.x1)
        if (mid < minX + 2f || mid > maxX - 2f) continue
        val dx = slope.x1 - slope.x0
        if (abs(dx) < 1f) continue
        val t = (mid - slope.x0) / dx
        val lineY = slope.y0 + (slope.y1 - slope.y0) * t
        if (lineY < solid.t - 2f && solid.t <= max(slope.y0, slope.y1) + 2f) return false
    }
    return true
}

private fun DrawScope.drawGroundBlock(box: RedBallAabb, grass: Boolean) {
    val w = box.r - box.l
    val h = box.b - box.t
    if (w <= 0f || h <= 0f) return
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFFC4844A), Color(0xFF9A5B2A), Color(0xFF6B3A1E)),
            startY = box.t,
            endY = box.b,
        ),
        Offset(box.l, box.t),
        Size(w, h),
    )
    drawRect(Color(0xFF3F2414).copy(alpha = 0.35f), Offset(box.l, box.b - 7f), Size(w, 7f))
    var px = box.l + 16f
    var n = 0
    val limit = h.toInt().coerceAtLeast(1)
    while (px < box.r - 8f && n < 28) {
        val py = box.t + 20f + ((px.toInt() * 13) % limit) * 0.35f
        if (py < box.b - 10f) drawCircle(Color(0xFF7C4A2A), 3.2f, Offset(px, py))
        px += 58f
        n++
    }
    if (!grass) return
    drawRect(Color(0xFF15803D), Offset(box.l, box.t), Size(w, 14f))
    drawRect(Color(0xFFBBF7D0), Offset(box.l, box.t), Size(w, 4f))
    var x = box.l + 6f
    var i = 0
    while (x < box.r - 3f) {
        val blade = if (i % 2 == 0) 11f else 7f
        drawLine(
            if (i % 3 == 0) Color(0xFF4ADE80) else Color(0xFF166534),
            Offset(x, box.t + 5f),
            Offset(x + 2.5f, box.t - blade),
            2.2f,
            StrokeCap.Round,
        )
        if (i % 8 == 3) {
            drawCircle(Color(0xFFF472B6), 4.2f, Offset(x, box.t - 8f))
            drawCircle(Color(0xFFFDE68A), 1.8f, Offset(x, box.t - 8f))
        }
        x += 16f
        i++
    }
}

private fun DrawScope.drawSlopeBank(x0: Float, y0: Float, x1: Float, y1: Float) {
    val path = Path().apply {
        moveTo(x0, y0)
        lineTo(x1, y1)
        if (y0 <= y1) lineTo(x0, y1) else lineTo(x1, y0)
        close()
    }
    drawPath(path, Color(0xFF9A5B2A))
    drawLine(Color(0xFF15803D), Offset(x0, y0), Offset(x1, y1), 14f, StrokeCap.Round)
    drawLine(Color(0xFFBBF7D0), Offset(x0, y0), Offset(x1, y1), 5f, StrokeCap.Round)
    for (i in 1..4) {
        val t = i / 5f
        val x = x0 + (x1 - x0) * t
        val y = y0 + (y1 - y0) * t
        drawLine(Color(0xFF166534), Offset(x, y), Offset(x + 2f, y - 9f), 2f, StrokeCap.Round)
    }
}

private fun DrawScope.drawIronSpike(x: Float, top: Float, w: Float, h: Float) {
    val path = Path().apply {
        moveTo(x, top)
        lineTo(x - w / 2f, top + h)
        lineTo(x + w / 2f, top + h)
        close()
    }
    drawPath(path, Color(0xFF44403C))
    val shine = Path().apply {
        moveTo(x, top + h * 0.14f)
        lineTo(x - w * 0.14f, top + h * 0.7f)
        lineTo(x, top + h * 0.58f)
        close()
    }
    drawPath(shine, Color(0xFFE7E5E4))
    drawCircle(Color(0xFFEF4444), (w * 0.12f).coerceIn(3f, 8f), Offset(x, top + h * 0.18f))
}

private fun DrawScope.drawFairySpring(l: Float, t: Float, r: Float, b: Float) {
    val w = r - l
    val h = b - t
    if (w < 4f || h < 4f) return
    drawRoundRect(Color(0xFF57534E), Offset(l, b - h * 0.14f), Size(w, h * 0.14f), CornerRadius(4f, 4f))
    drawRoundRect(Color(0xFFF5F5F4), Offset(l + w * 0.14f, t), Size(w * 0.72f, h * 0.16f), CornerRadius(4f, 4f))
    val coils = 4
    for (i in 0 until coils) {
        val y = t + h * (0.18f + 0.62f * (i + 0.5f) / coils)
        drawArc(
            if (i % 2 == 0) Color(0xFFFACC15) else Color(0xFFCA8A04),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(l + w * 0.14f, y - h * 0.18f),
            size = Size(w * 0.72f, h * 0.32f),
            style = Stroke(width = (w * 0.08f).coerceIn(3f, 7f), cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawFairyDoor(l: Float, t: Float, r: Float, b: Float) {
    val w = r - l
    val h = b - t
    if (w < 4f || h < 4f) return
    val arch = min(w * 0.62f, h * 0.42f)
    val cx = (l + r) / 2f
    drawCircle(Color(0xFFFDE68A).copy(alpha = 0.5f), w * 0.9f, Offset(cx, t + arch * 0.8f))
    val frame = Path().apply {
        moveTo(l, b)
        lineTo(l, t + arch)
        quadraticBezierTo(cx, t, r, t + arch)
        lineTo(r, b)
        close()
    }
    drawPath(frame, Color(0xFF78716C))
    val inset = w * 0.16f
    val innerTop = t + arch * 0.45f
    val inner = Path().apply {
        moveTo(l + inset, b - w * 0.05f)
        lineTo(l + inset, innerTop + arch * 0.35f)
        quadraticBezierTo(cx, innerTop, r - inset, innerTop + arch * 0.35f)
        lineTo(r - inset, b - w * 0.05f)
        close()
    }
    drawPath(
        inner,
        Brush.verticalGradient(
            listOf(Color(0xFFFFF7C2), Color(0xFFF59E0B), Color(0xFFB45309)),
            startY = t,
            endY = b,
        ),
    )
    val span = r - l - inset * 2f
    for (i in 1..2) {
        val x = l + inset + span * i / 3f
        drawLine(Color(0xFF92400E).copy(alpha = 0.75f), Offset(x, innerTop + arch * 0.2f), Offset(x, b - w * 0.08f), w * 0.035f)
    }
    val knob = Offset(r - inset - w * 0.14f, t + h * 0.58f)
    drawCircle(Color(0xFFFBBF24), w * 0.07f, knob)
    drawCircle(Color(0xFF78350F), w * 0.03f, knob)
    drawSparkle(cx, t + arch * 0.35f, w * 0.14f, Color(0xFFFFFBEB))
}

private fun DrawScope.drawWoodCrate(cx: Float, cy: Float, side: Float) {
    val x = cx - side / 2f
    val y = cy - side / 2f
    drawOval(
        Color(0xFF44403C).copy(alpha = 0.22f),
        Offset(x + side * 0.1f, y + side * 0.88f),
        Size(side * 0.8f, side * 0.16f),
    )
    drawRoundRect(
        Brush.linearGradient(
            listOf(Color(0xFFF6E0B5), Color(0xFFD4A15A), Color(0xFF9A6230)),
            start = Offset(x, y),
            end = Offset(x + side, y + side),
        ),
        Offset(x, y),
        Size(side, side),
        CornerRadius(side * 0.08f, side * 0.08f),
    )
    val m = side * 0.13f
    drawRoundRect(
        Color(0xFF6B3E1E),
        Offset(x + m, y + m),
        Size(side - m * 2f, side - m * 2f),
        CornerRadius(side * 0.04f, side * 0.04f),
        style = Stroke(width = side * 0.045f),
    )
    drawLine(Color(0xFF6B3E1E), Offset(x + m, y + side * 0.4f), Offset(x + side - m, y + side * 0.4f), side * 0.04f)
    drawLine(Color(0xFF6B3E1E), Offset(x + m, y + side * 0.62f), Offset(x + side - m, y + side * 0.62f), side * 0.04f)
    drawLine(
        Color(0xFF7C4A2A),
        Offset(x + m * 1.5f, y + m * 1.4f),
        Offset(x + side - m * 1.5f, y + side - m * 1.4f),
        side * 0.03f,
    )
    val nail = side * 0.034f
    for (p in listOf(
        Offset(x + m, y + m),
        Offset(x + side - m, y + m),
        Offset(x + m, y + side - m),
        Offset(x + side - m, y + side - m),
    )) {
        drawCircle(Color(0xFF44403C), nail, p)
        drawCircle(Color(0xFFF5F5F4), nail * 0.35f, p + Offset(-nail * 0.25f, -nail * 0.25f))
    }
}

/** [groundY] — высота поверхности под мячом; тень лежит на ней, а не прилипает к мячу. */
private fun DrawScope.drawGlossyBall(
    x: Float,
    y: Float,
    r: Float,
    angle: Float,
    groundY: Float?,
    squashX: Float = 1f,
    squashY: Float = 1f,
) {
    val sx = squashX.coerceIn(0.62f, 1.48f)
    val sy = squashY.coerceIn(0.62f, 1.42f)
    if (groundY != null) {
        val gap = (groundY - (y + r)).coerceAtLeast(0f)
        val k = (1f - gap / 360f).coerceIn(0f, 1f)
        if (k > 0.02f) {
            val w = r * 1.9f * sx * (0.45f + 0.55f * k)
            val h = r * 0.46f * (0.5f + 0.5f * k)
            drawOval(
                Color(0xFF7F1D1D).copy(alpha = 0.28f * k),
                Offset(x - w / 2f, groundY - h * 0.55f),
                Size(w, h),
            )
        }
    }
    val sink = ((1f - sy) * r * 0.62f).coerceAtLeast(0f)
    val cy = y + sink
    drawCircle(Color(0xFFFB7185).copy(alpha = 0.22f), r * 1.35f, Offset(x, cy))
    withTransform({
        scale(sx, sy, pivot = Offset(x, cy))
    }) {
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFF7F7), Color(0xFFFB7185), Color(0xFFE11D48), Color(0xFF9F1239)),
                center = Offset(x - r * 0.38f, cy - r * 0.42f),
                radius = r * 1.85f,
            ),
            r,
            Offset(x, cy),
        )
        rotate(Math.toDegrees(angle.toDouble()).toFloat(), Offset(x, cy)) {
            drawArc(
                Color(0xFF881337),
                startAngle = -16f,
                sweepAngle = 34f,
                useCenter = false,
                topLeft = Offset(x - r * 0.9f, cy - r * 0.9f),
                size = Size(r * 1.8f, r * 1.8f),
                style = Stroke(width = r * 0.2f, cap = StrokeCap.Round),
            )
        }
        drawCircle(Color.White.copy(alpha = 0.95f), r * 0.16f, Offset(x - r * 0.36f, cy - r * 0.4f))
        drawCircle(Color.White.copy(alpha = 0.55f), r * 0.07f, Offset(x - r * 0.12f, cy - r * 0.18f))
        drawArc(
            Color.White.copy(alpha = 0.55f),
            startAngle = 200f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(x - r * 0.78f, cy - r * 0.78f),
            size = Size(r * 1.56f, r * 1.56f),
            style = Stroke(width = r * 0.08f, cap = StrokeCap.Round),
        )
        drawArc(
            Color.White.copy(alpha = 0.28f),
            startAngle = 28f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(x - r * 0.82f, cy - r * 0.82f),
            size = Size(r * 1.64f, r * 1.64f),
            style = Stroke(width = r * 0.07f),
        )
    }
}

private fun DrawScope.drawCloudBand(
    camX: Float,
    worldToScreen: Float,
    factor: Float,
    yFrac: Float,
    scale: Float,
    alpha: Float,
) {
    val span = size.width * 1.7f
    if (span <= 1f) return
    val shift = wrapShift(camX * worldToScreen * factor, span)
    val y = size.height * yFrac
    for (i in -1..4) {
        drawCloud(i * span / 3f - shift, y, 96f * scale, alpha)
    }
}

private fun DrawScope.drawCloud(x: Float, y: Float, w: Float, alpha: Float) {
    val c = Color.White.copy(alpha = alpha)
    drawCircle(c, w * 0.26f, Offset(x, y))
    drawCircle(c, w * 0.36f, Offset(x + w * 0.28f, y - w * 0.08f))
    drawCircle(c, w * 0.22f, Offset(x + w * 0.52f, y + w * 0.02f))
}

private fun DrawScope.drawHills(
    camX: Float,
    worldToScreen: Float,
    factor: Float,
    baseline: Float,
    color: Color,
    amp: Float,
    step: Float,
) {
    val shift = camX * worldToScreen * factor
    val phase = wrapShift(shift, step * 2f)
    val path = Path()
    var x = -step * 2f - phase
    path.moveTo(x, size.height + 8f)
    path.lineTo(x, baseline)
    var index = 0
    while (x < size.width + step) {
        val next = x + step
        val hump = if (index % 2 == 0) amp else amp * 0.62f
        path.quadraticBezierTo(x + step * 0.5f, baseline - hump, next, baseline)
        x = next
        index++
    }
    path.lineTo(x, size.height + 8f)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawStoryTree(x: Float, ground: Float, h: Float) {
    if (h < 8f) return
    val trunk = h * 0.14f
    drawRoundRect(
        Color(0xFF7C4A2D),
        Offset(x - trunk / 2f, ground - h * 0.38f),
        Size(trunk, h * 0.4f),
        CornerRadius(3f, 3f),
    )
    drawCircle(Color(0xFF166534), h * 0.26f, Offset(x, ground - h * 0.52f))
    drawCircle(Color(0xFF15803D), h * 0.2f, Offset(x - h * 0.16f, ground - h * 0.4f))
    drawCircle(Color(0xFF22C55E), h * 0.18f, Offset(x + h * 0.14f, ground - h * 0.46f))
}

private fun DrawScope.drawBird(x: Float, y: Float, s: Float) {
    val color = Color(0xFF44403C).copy(alpha = 0.55f)
    drawArc(
        color,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(x, y),
        size = Size(s, s * 0.7f),
        style = Stroke(width = 2.4f, cap = StrokeCap.Round),
    )
    drawArc(
        color,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(x + s * 0.7f, y),
        size = Size(s, s * 0.7f),
        style = Stroke(width = 2.4f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawSparkle(cx: Float, cy: Float, s: Float, color: Color) {
    drawLine(color, Offset(cx - s, cy), Offset(cx + s, cy), s * 0.28f, StrokeCap.Round)
    drawLine(color, Offset(cx, cy - s), Offset(cx, cy + s), s * 0.28f, StrokeCap.Round)
    drawCircle(color, s * 0.22f, Offset(cx, cy))
}

private fun DrawScope.drawConcreteBlock(box: RedBallAabb, cap: Boolean) {
    val w = box.r - box.l
    val h = box.b - box.t
    if (w <= 1f || h <= 1f) return
    drawRoundRect(
        Brush.verticalGradient(
            listOf(Color(0xFFE7E5E4), Color(0xFFA8A29E), Color(0xFF78716C), Color(0xFF44403C)),
            startY = box.t,
            endY = box.b,
        ),
        Offset(box.l, box.t),
        Size(w, h),
        CornerRadius(7f, 7f),
    )
    val speck = h.toInt().coerceAtLeast(1)
    var px = box.l + 12f
    var n = 0
    while (px < box.r - 8f && n < 22) {
        val py = box.t + 16f + ((px.toInt() * 17) % speck) * 0.45f
        if (py < box.b - 8f) {
            drawCircle(Color(0xFF292524).copy(alpha = 0.28f), 2.4f, Offset(px, py))
            drawCircle(Color.White.copy(alpha = 0.4f), 1.4f, Offset(px + 5f, py + 3f))
        }
        px += 26f
        n++
    }
    var seam = box.l + 34f
    var i = 0
    while (seam < box.r - 10f && i < 10) {
        drawLine(
            Color(0xFF292524).copy(alpha = 0.22f),
            Offset(seam, box.t + 10f),
            Offset(seam - 8f, box.b - 8f),
            2f,
        )
        seam += 52f
        i++
    }
    if (cap) {
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFFF5F5F4), Color(0xFFD6D3D1))),
            Offset(box.l, box.t),
            Size(w, 9f),
        )
    }
    drawRect(Color(0xFF1C1917).copy(alpha = 0.35f), Offset(box.l, box.b - 7f), Size(w, 7f))
}

private fun DrawScope.drawConcreteCrate(cx: Float, cy: Float, side: Float) {
    drawOval(
        Color(0xFF292524).copy(alpha = 0.22f),
        Offset(cx - side * 0.38f, cy + side * 0.38f),
        Size(side * 0.76f, side * 0.16f),
    )
    val half = side / 2f
    drawConcreteBlock(RedBallAabb(cx - half, cy - half, cx + half, cy + half), true)
}

private fun DrawScope.drawLadder(box: RedBallAabb) {
    val w = box.r - box.l
    val h = box.b - box.t
    if (w < 4f || h < 4f) return
    val rail = (w * 0.18f).coerceIn(4f, 12f)
    val wood = Brush.verticalGradient(
        listOf(Color(0xFFF6E0B5), Color(0xFFD97706), Color(0xFF92400E)),
        startY = box.t,
        endY = box.b,
    )
    drawRoundRect(wood, Offset(box.l, box.t), Size(rail, h), CornerRadius(4f, 4f))
    drawRoundRect(wood, Offset(box.r - rail, box.t), Size(rail, h), CornerRadius(4f, 4f))
    drawRect(Color.White.copy(alpha = 0.35f), Offset(box.l + 1f, box.t), Size(rail * 0.28f, h))
    val rungH = (h * 0.055f).coerceIn(3f, 8f)
    var y = box.t + h * 0.14f
    val step = (w * 0.9f).coerceIn(16f, 30f)
    while (y < box.b - 8f) {
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFFFDE68A), Color(0xFFB45309), Color(0xFF78350F))),
            Offset(box.l + rail * 0.35f, y),
            Size(w - rail * 0.7f, rungH),
            CornerRadius(3f, 3f),
        )
        y += step
    }
}

private fun DrawScope.drawCollectStar(cx: Float, cy: Float, radius: Float, time: Float, stuck: Boolean) {
    val bob = sin(time * 3.2f + cx * 0.02f) * if (stuck) 1.5f else 4f
    val y = cy + bob
    val pulse = 0.9f + 0.1f * sin(time * 5f + cy)
    drawCircle(Color(0xFFFDE68A).copy(alpha = 0.35f), radius * 1.7f * pulse, Offset(cx, y))
    val path = starPath(cx, y, radius * pulse, radius * 0.42f * pulse)
    drawPath(
        path,
        Brush.radialGradient(
            listOf(Color(0xFFFFFBEB), Color(0xFFFBBF24), Color(0xFFD97706)),
            center = Offset(cx - radius * 0.2f, y - radius * 0.25f),
            radius = radius * 1.6f,
        ),
    )
    drawPath(path, Color(0xFF92400E), style = Stroke(width = 2f))
    drawCircle(Color.White.copy(alpha = 0.9f), radius * 0.16f, Offset(cx - radius * 0.22f, y - radius * 0.18f))
}

private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float): Path {
    val path = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = (-PI / 2.0 + i * PI / points).toFloat()
        val x = cx + cos(angle) * radius
        val y = cy + sin(angle) * radius
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun DrawScope.drawMagnet(cx: Float, cy: Float, time: Float) {
    val bob = sin(time * 2.4f + cx) * 3f
    val y = cy + bob
    drawCircle(Color(0xFF38BDF8).copy(alpha = 0.28f), 30f, Offset(cx, y))
    drawArc(
        Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFE7E5E4), Color(0xFF2563EB))),
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(cx - 16f, y - 18f),
        size = Size(32f, 32f),
        style = Stroke(width = 8f, cap = StrokeCap.Butt),
    )
    drawRoundRect(Color(0xFFEF4444), Offset(cx - 16f, y + 2f), Size(8f, 16f), CornerRadius(2f, 2f))
    drawRoundRect(Color(0xFF2563EB), Offset(cx + 8f, y + 2f), Size(8f, 16f), CornerRadius(2f, 2f))
    drawCircle(Color.White.copy(alpha = 0.7f), 3f, Offset(cx - 4f, y - 10f))
}

private fun DrawScope.drawMagnetAura(x: Float, y: Float, time: Float) {
    val pulse = 0.92f + 0.08f * sin(time * 6f)
    val radius = com.example.bible.games.RedBallSim.MAGNET_RADIUS * pulse
    drawCircle(
        Color(0xFF38BDF8).copy(alpha = 0.1f),
        radius,
        Offset(x, y),
    )
    drawCircle(
        Color(0xFF7DD3FC).copy(alpha = 0.45f),
        radius,
        Offset(x, y),
        style = Stroke(width = 3f),
    )
}

private fun DrawScope.drawAngrySquare(cx: Float, cy: Float, side: Float, angle: Float) {
    val half = side / 2f
    drawOval(
        Color(0xFF7F1D1D).copy(alpha = 0.22f),
        Offset(cx - half * 0.8f, cy + half * 0.72f),
        Size(side * 0.8f, side * 0.18f),
    )
    val deg = Math.toDegrees(angle.toDouble()).toFloat()
    withTransform({ rotate(deg, Offset(cx, cy)) }) {
        drawRoundRect(
            Brush.linearGradient(
                listOf(Color(0xFFFDA4AF), Color(0xFFE11D48), Color(0xFF9F1239)),
                start = Offset(cx - half, cy - half),
                end = Offset(cx + half, cy + half),
            ),
            Offset(cx - half, cy - half),
            Size(side, side),
            CornerRadius(side * 0.16f, side * 0.16f),
        )
        drawRoundRect(
            Color(0xFF881337),
            Offset(cx - half, cy - half),
            Size(side, side),
            CornerRadius(side * 0.16f, side * 0.16f),
            style = Stroke(width = side * 0.045f),
        )
        val bolt = side * 0.06f
        for (corner in listOf(
            Offset(cx - half * 0.62f, cy - half * 0.62f),
            Offset(cx + half * 0.62f, cy - half * 0.62f),
            Offset(cx - half * 0.62f, cy + half * 0.62f),
            Offset(cx + half * 0.62f, cy + half * 0.62f),
        )) {
            drawCircle(Color(0xFF44403C), bolt, corner)
            drawCircle(Color.White.copy(alpha = 0.7f), bolt * 0.35f, corner + Offset(-bolt * 0.2f, -bolt * 0.2f))
        }
    }
    val eye = side * 0.1f
    val eyeY = cy - side * 0.08f
    drawCircle(Color(0xFF1C1917), eye, Offset(cx - side * 0.18f, eyeY))
    drawCircle(Color(0xFF1C1917), eye, Offset(cx + side * 0.18f, eyeY))
    drawCircle(Color.White, eye * 0.35f, Offset(cx - side * 0.15f, eyeY - eye * 0.3f))
    drawCircle(Color.White, eye * 0.35f, Offset(cx + side * 0.21f, eyeY - eye * 0.3f))
    drawLine(
        Color(0xFF1C1917),
        Offset(cx - side * 0.3f, cy - side * 0.26f),
        Offset(cx - side * 0.06f, cy - side * 0.18f),
        side * 0.045f,
        StrokeCap.Round,
    )
    drawLine(
        Color(0xFF1C1917),
        Offset(cx + side * 0.3f, cy - side * 0.26f),
        Offset(cx + side * 0.06f, cy - side * 0.18f),
        side * 0.045f,
        StrokeCap.Round,
    )
    drawArc(
        Color(0xFF1C1917),
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(cx - side * 0.16f, cy + side * 0.02f),
        size = Size(side * 0.32f, side * 0.22f),
        style = Stroke(width = side * 0.045f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawParticle(particle: RedBallParticle) {
    val t = (particle.life / particle.maxLife).coerceIn(0f, 1f)
    val color = when (particle.kind) {
        RedBallParticle.BOOM -> Color(0xFFF97316)
        RedBallParticle.SPARK -> Color(0xFFFDE68A)
        else -> Color(0xFFD6D3D1)
    }
    drawCircle(color.copy(alpha = 0.85f * t), particle.size * (0.6f + 0.5f * t), Offset(particle.x, particle.y))
}

private fun RedBallPiece.box(cell: Float) = RedBallAabb(
    c * cell,
    r * cell,
    (c + w) * cell,
    (r + h) * cell,
)

private fun RedBallPiece.slopeLine(cell: Float): RedBallSlope {
    val box = box(cell)
    return if (!flip) RedBallSlope(box.l, box.t, box.r, box.b) else RedBallSlope(box.l, box.b, box.r, box.t)
}
