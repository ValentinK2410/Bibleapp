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
import com.example.bible.games.RedBallPiece
import com.example.bible.games.RedBallPieceKind
import com.example.bible.games.RedBallScene
import com.example.bible.games.RedBallSim
import com.example.bible.games.RedBallSlope
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal fun editorMaxCam(widthPx: Float, heightPx: Float, draft: RedBallDraft): Float {
    if (heightPx <= 1f) return 0f
    val scale = heightPx / (draft.rows * draft.cell)
    val viewW = widthPx / scale
    return (draft.cols * draft.cell - viewW).coerceAtLeast(0f)
}

internal fun DrawScope.drawRedBallPlay(sim: RedBallSim) {
    val scene = sim.scene
    val viewH = 620f
    val scale = size.height / viewH
    val viewW = size.width / scale
    val camX = (sim.ballX - viewW * 0.38f).coerceIn(0f, (scene.worldW - viewW).coerceAtLeast(0f))
    // Камера следит за мячом и по вертикали: при прыжке мир (и фон) смещается.
    val restCamY = (scene.worldH - viewH).coerceAtLeast(0f)
    val camY = (sim.ballY - viewH * 0.58f).coerceIn(-viewH * 1.5f, restCamY)
    drawFairySky(camX, scale, liftPx = (restCamY - camY) * scale)
    withTransform({
        scale(scale, scale, pivot = Offset.Zero)
        translate(-camX, -camY)
    }) {
        drawVoid(scene)
        drawSolids(scene.solids, scene.slopes, scene.worldW)
        for (slope in scene.slopes) drawSlopeBank(slope.x0, slope.y0, slope.x1, slope.y1)
        for (spike in scene.spikes) drawIronSpike(spike.x, spike.top, spike.w, spike.h)
        for (spring in scene.springs) drawFairySpring(spring.l, spring.t, spring.r, spring.b)
        drawFairyDoor(scene.door.l, scene.door.t, scene.door.r, scene.door.b)
        for (crate in sim.crates) drawWoodCrate(crate.x, crate.y, crate.size)
        drawGlossyBall(sim.ballX, sim.ballY, RedBallScene.BALL_R, sim.ballAngle, sim.groundBelowBall())
    }
}

internal fun DrawScope.drawRedBallEditor(
    draft: RedBallDraft,
    camX: Float,
    hoverC: Int,
    hoverR: Int,
    hoverW: Int,
    hoverH: Int,
    showHover: Boolean,
) {
    val gridH = draft.rows * draft.cell
    val scale = size.height / gridH
    val cam = camX.coerceIn(0f, editorMaxCam(size.width, size.height, draft))
    drawFairySky(cam, scale)
    withTransform({
        scale(scale, scale, pivot = Offset.Zero)
        translate(-cam, 0f)
    }) {
        val gridW = draft.cols * draft.cell
        val line = Color.White.copy(alpha = 0.28f)
        val bold = Color.White.copy(alpha = 0.45f)
        for (c in 0..draft.cols) {
            val x = c * draft.cell
            drawLine(if (c % 4 == 0) bold else line, Offset(x, 0f), Offset(x, gridH), if (c % 4 == 0) 2f else 1f)
        }
        for (r in 0..draft.rows) {
            val y = r * draft.cell
            drawLine(if (r % 2 == 0) bold else line, Offset(0f, y), Offset(gridW, y), if (r % 2 == 0) 2f else 1f)
        }
        val grounds = draft.pieces.filter { it.kind == RedBallPieceKind.GROUND }.map { it.box(draft.cell) }
        val slopes = draft.pieces.filter { it.kind == RedBallPieceKind.SLOPE }.map { it.slopeLine(draft.cell) }
        for (piece in draft.pieces) {
            if (piece.kind != RedBallPieceKind.GROUND) continue
            val box = piece.box(draft.cell)
            drawGroundBlock(box, topIsOpen(box, grounds, slopes))
        }
        for (piece in draft.pieces) {
            val box = piece.box(draft.cell)
            when (piece.kind) {
                RedBallPieceKind.GROUND -> Unit
                RedBallPieceKind.SLOPE -> {
                    val lineSlope = piece.slopeLine(draft.cell)
                    drawSlopeBank(lineSlope.x0, lineSlope.y0, lineSlope.x1, lineSlope.y1)
                }
                RedBallPieceKind.SPIKE -> drawIronSpike(
                    (box.l + box.r) / 2f,
                    box.t,
                    box.r - box.l,
                    box.b - box.t,
                )
                RedBallPieceKind.SPRING -> drawFairySpring(box.l, box.t, box.r, box.b)
                RedBallPieceKind.CRATE -> drawWoodCrate((box.l + box.r) / 2f, (box.t + box.b) / 2f, min(piece.w, piece.h) * draft.cell)
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
        }
        if (showHover && hoverW > 0 && hoverH > 0) {
            drawRoundRect(
                Color.White.copy(alpha = 0.55f),
                Offset(hoverC * draft.cell, hoverR * draft.cell),
                Size(hoverW * draft.cell, hoverH * draft.cell),
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
    drawHills(camX, worldToScreen, 0.26f, horizon + liftPx * 0.26f, Color(0xFF93C5FD), 42f, 170f)
    drawHills(camX, worldToScreen, 0.4f, horizon + liftPx * 0.4f + size.height * 0.045f, Color(0xFF6EE7B7), 30f, 140f)
    val span = size.width * 1.8f
    val shift = if (span <= 1f) 0f else (camX * worldToScreen * 0.48f) % span
    val base = horizon + liftPx * 0.5f + size.height * 0.03f
    for (i in -1..6) {
        val x = i * span / 5f - shift
        drawStoryTree(x, base, size.height * if (i % 2 == 0) 0.15f else 0.11f)
    }
    val bird = camX * worldToScreen * 0.08f
    val birdLift = liftPx * 0.1f
    drawBird(size.width * 0.24f - bird, size.height * 0.22f + birdLift, 18f)
    drawBird(size.width * 0.33f - bird, size.height * 0.19f + birdLift, 13f)
}

private fun DrawScope.drawVoid(scene: RedBallScene) {
    val top = scene.voidY - 50f
    if (top >= scene.worldH) return
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, Color(0x66221A3A), Color(0xFF1E1B4B)),
            startY = top,
            endY = scene.worldH,
        ),
        topLeft = Offset(-40f, top),
        size = Size(scene.worldW + 80f, scene.worldH - top),
    )
}

private fun DrawScope.drawSolids(solids: List<RedBallAabb>, slopes: List<RedBallSlope>, worldW: Float) {
    for (solid in solids) {
        if (solid.r <= 0.5f || solid.l >= worldW - 0.5f) continue
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
    drawRect(Color(0xFF9A5B2A), Offset(box.l, box.t), Size(w, h))
    drawRect(Color(0xFF6B3A1E), Offset(box.l, box.b - 7f), Size(w, 7f))
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
private fun DrawScope.drawGlossyBall(x: Float, y: Float, r: Float, angle: Float, groundY: Float?) {
    if (groundY != null) {
        val gap = (groundY - (y + r)).coerceAtLeast(0f)
        val k = (1f - gap / 360f).coerceIn(0f, 1f)
        if (k > 0.02f) {
            val w = r * 1.9f * (0.45f + 0.55f * k)
            val h = r * 0.46f * (0.5f + 0.5f * k)
            drawOval(
                Color(0xFF7F1D1D).copy(alpha = 0.25f * k),
                Offset(x - w / 2f, groundY - h * 0.5f),
                Size(w, h),
            )
        }
    }
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFF7F7), Color(0xFFFB7185), Color(0xFFE11D48), Color(0xFF9F1239)),
            center = Offset(x - r * 0.38f, y - r * 0.42f),
            radius = r * 1.85f,
        ),
        r,
        Offset(x, y),
    )
    rotate(Math.toDegrees(angle.toDouble()).toFloat(), Offset(x, y)) {
        drawArc(
            Color(0xFF881337),
            startAngle = -16f,
            sweepAngle = 34f,
            useCenter = false,
            topLeft = Offset(x - r * 0.9f, y - r * 0.9f),
            size = Size(r * 1.8f, r * 1.8f),
            style = Stroke(width = r * 0.2f, cap = StrokeCap.Round),
        )
    }
    drawCircle(Color.White.copy(alpha = 0.95f), r * 0.16f, Offset(x - r * 0.36f, y - r * 0.4f))
    drawCircle(Color.White.copy(alpha = 0.5f), r * 0.07f, Offset(x - r * 0.12f, y - r * 0.18f))
    drawArc(
        Color.White.copy(alpha = 0.4f),
        startAngle = 28f,
        sweepAngle = 120f,
        useCenter = false,
        topLeft = Offset(x - r * 0.82f, y - r * 0.82f),
        size = Size(r * 1.64f, r * 1.64f),
        style = Stroke(width = r * 0.07f),
    )
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
    val shift = (camX * worldToScreen * factor) % span
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
    val phase = if (step <= 1f) 0f else shift % (step * 2f)
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
