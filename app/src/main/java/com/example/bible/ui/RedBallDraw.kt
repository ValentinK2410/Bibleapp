package com.example.bible.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.example.bible.games.RedBallAabb
import com.example.bible.games.RedBallDraft
import com.example.bible.games.RedBallEnemy
import com.example.bible.games.RedBallParticle
import com.example.bible.games.RedBallPiece
import com.example.bible.games.RedBallPieceKind
import com.example.bible.games.RedBallPortalRole
import com.example.bible.games.RedBallScene
import com.example.bible.games.RedBallSim
import com.example.bible.games.RedBallSlope
import com.example.bible.games.RedBallWater
import com.example.bible.games.skins.RedBallSkinBitmaps
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

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

internal fun DrawScope.drawRedBallPlay(sim: RedBallSim, skin: RedBallSkinBitmaps? = null) {
    val scene = sim.scene
    val viewH = 620f
    val scale = size.height / viewH
    val viewW = size.width / scale
    val maxCamX = (scene.worldW - viewW).coerceAtLeast(scene.minX)
    val camX = (sim.ballX - viewW * 0.38f).coerceIn(scene.minX, maxCamX)
    val restCamY = (scene.worldH - viewH).coerceAtLeast(scene.minY)
    val camY = (sim.ballY - viewH * 0.58f).coerceIn(scene.minY - viewH * 1.5f, restCamY)
    if (skin != null) {
        drawSpritePlay(sim, camX, camY, scale, (restCamY - camY) * scale, skin)
        if (sim.submerged) drawRect(Color(0xFF15608F).copy(alpha = 0.22f))
        return
    }
    drawFairySky(camX, scale, liftPx = (restCamY - camY) * scale)
    val art = playArt(scene)
    val picture = terrainPicture(scene, art, scale)
    withTransform({
        scale(scale, scale, pivot = Offset.Zero)
        translate(-camX, -camY)
    }) {
        if (picture != null) {
            drawTerrainPicture(picture)
        } else {
            drawStaticWorld(scene, art)
        }
        for (water in scene.waters) drawWaterVolume(water, sim.anim)
        for (star in sim.stars) {
            if (!star.taken) drawCollectStar(star.x, star.y, 16f, sim.anim, star.attached)
        }
        for (magnet in sim.magnets) if (!magnet.taken) drawMagnet(magnet.x, magnet.y, sim.anim)
        for (portal in scene.portals) {
            val mood = when {
                portal.role == RedBallPortalRole.EXIT -> PortalMood.EXIT
                portal.linkId.isBlank() -> PortalMood.ASLEEP
                else -> PortalMood.OPEN
            }
            drawFairyPortal(portal.box.l, portal.box.t, portal.box.r, portal.box.b, sim.anim, mood)
        }
        drawFinishFlag(scene.door.l, scene.door.t, scene.door.r, scene.door.b, sim.anim)
        for (crate in sim.crates) drawWoodCrate(crate.x, crate.y, crate.size)
        for (cart in sim.carts) drawCartoonCart(cart.x, cart.y, cart.w, cart.h, cart.spin, null)
        for (block in sim.blocks) drawRockBoulder(block.x, block.y, block.size)
        for (enemy in sim.enemies) {
            if (enemy.alive) drawAngrySquare(enemy.x, enemy.y, enemy.size, enemy.angle)
        }
        for (particle in sim.particles) {
            if (particle.kind != RedBallParticle.BUBBLE) drawParticle(particle)
        }
        if (sim.magnetTime > 0f) drawMagnetAura(sim.ballX, sim.ballY, sim.anim)
        drawGlossyBall(
            sim.ballX,
            sim.ballY,
            RedBallScene.BALL_R,
            sim.ballAngle,
            sim.groundBelowBall(),
            sim.jellyX,
            sim.jellyY,
            faceOf(sim),
            sim.hurtBlink(),
        )
        for (particle in sim.particles) {
            if (particle.kind == RedBallParticle.BUBBLE) drawParticle(particle)
        }
    }
    if (sim.submerged) {
        drawRect(Color(0xFF15608F).copy(alpha = 0.22f))
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
    portalTime: Float = 0.8f,
    lostPortalIds: Set<Long> = emptySet(),
    skin: RedBallSkinBitmaps? = null,
) {
    val scale = editorScale(size.height, draft.cell)
    val range = editorCamRange(size.width, size.height, draft)
    val cam = camX.coerceIn(range.minX, range.maxX)
    val camDown = camY.coerceIn(range.minY, range.maxY)
    val viewW = size.width / scale
    val viewH = size.height / scale
    if (skin != null) {
        drawSpriteSky(cam, scale, -camDown * scale * 0.15f, skin)
    } else {
        drawFairySky(cam, scale, liftPx = -camDown * scale * 0.15f)
    }
    val art = if (skin == null) editorArt(draft) else null
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
        if (skin != null) {
            drawSpriteEditorWorld(draft, skin, portalTime, lostPortalIds)
        } else {
            val terrain = art ?: return@withTransform
            for (ground in terrain.grounds) drawGroundArt(ground)
            for (piece in draft.pieces) {
                if (piece.kind != RedBallPieceKind.CONCRETE) continue
                val box = piece.box(cell)
                drawConcreteBlock(box, topIsOpen(box, terrain.concreteBoxes, emptyList()))
            }
            for (slope in terrain.slopes) drawSlopeArt(slope)
            for (piece in draft.pieces) {
                val box = piece.box(cell)
                when (piece.kind) {
                    RedBallPieceKind.GROUND, RedBallPieceKind.SLOPE, RedBallPieceKind.CONCRETE -> Unit
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
                    RedBallPieceKind.WATER -> drawWaterVolume(RedBallWater(box.l, box.t, box.r, box.b), 0.4f)
                    RedBallPieceKind.CART -> {
                        val bodyH = min(box.b - box.t, 48f)
                        drawCartoonCart((box.l + box.r) / 2f, box.b - bodyH / 2f, (box.r - box.l) * 0.94f, bodyH, 0.4f, null)
                    }
                    RedBallPieceKind.DOOR -> drawFinishFlag(box.l, box.t, box.r, box.b, 0.8f)
                    RedBallPieceKind.PORTAL -> {
                        val mood = when {
                            piece.role == RedBallPortalRole.EXIT -> PortalMood.EXIT
                            piece.id in lostPortalIds -> PortalMood.LOST
                            piece.linkId.isBlank() -> PortalMood.ASLEEP
                            else -> PortalMood.OPEN
                        }
                        drawFairyPortal(box.l, box.t, box.r, box.b, portalTime, mood)
                    }
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
        }
        if (selectedId != null) {
            for (piece in draft.pieces) {
                if (piece.id != selectedId) continue
                val box = piece.box(cell)
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
            0f to Color(0xFF4EB8F5),
            0.42f to Color(0xFF8AD4FF),
            0.78f to Color(0xFFC9EFFF),
            1f to Color(0xFFE7F8FF),
        ),
    )
    val sunR = size.minDimension * 0.11f
    val sun = Offset(
        size.width * 0.24f - camX * worldToScreen * 0.04f,
        size.height * 0.16f + liftPx * 0.04f,
    )
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFFFFBE8),
            0.22f to Color(0xFFFFF3A0),
            0.4f to Color(0xFFFFE56A).copy(alpha = 0.85f),
            0.62f to Color(0xFFFFF3B0).copy(alpha = 0.28f),
            1f to Color(0xFFFFF6C2).copy(alpha = 0f),
            center = sun,
            radius = sunR * 2.8f,
        ),
        sunR * 2.8f,
        sun,
    )
    drawCircle(Color(0xFFFFF4A8), sunR * 0.92f, sun)
    drawCircle(Color(0xFFFFFBE8), sunR * 0.5f, sun)
    withTransform({ translate(0f, liftPx * 0.1f) }) {
        drawCloudBand(camX, worldToScreen, 0.1f, 0.16f, 1.15f, 0.96f)
    }
    withTransform({ translate(0f, liftPx * 0.18f) }) {
        drawCloudBand(camX, worldToScreen, 0.2f, 0.28f, 0.78f, 0.9f)
    }
    val horizon = size.height * 0.7f
    drawHills(camX, worldToScreen, 0.12f, horizon + liftPx * 0.12f - size.height * 0.05f, Color(0xFFD7F0B8), 26f, 260f)
    drawBackdropLake(camX, worldToScreen, horizon + liftPx * 0.16f - size.height * 0.01f)
    drawHills(camX, worldToScreen, 0.24f, horizon + liftPx * 0.24f, Color(0xFF9AD86A), 40f, 180f)
    val nearBase = horizon + liftPx * 0.42f + size.height * 0.04f
    drawHills(camX, worldToScreen, 0.4f, nearBase, Color(0xFF6EC84A), 34f, 150f)
    val span = size.width * 1.8f
    val shift = wrapShift(camX * worldToScreen * 0.48f, span)
    for (i in -1..6) {
        val x = i * span / 5f - shift
        val h = size.height * if (i % 2 == 0) 0.16f else 0.11f
        drawStoryTree(x, nearBase - 8f, h, i)
    }
    drawFence(size.width * 0.62f - shift * 0.2f, nearBase - 4f)
    val bird = camX * worldToScreen * 0.08f
    val birdLift = liftPx * 0.1f
    drawBird(size.width * 0.18f - bird, size.height * 0.2f + birdLift, 16f)
    drawBird(size.width * 0.28f - bird, size.height * 0.17f + birdLift, 12f)
}

private fun wrapShift(value: Float, span: Float): Float {
    if (span <= 1f) return 0f
    val m = value % span
    return if (m < 0f) m + span else m
}

private enum class BallMood { HAPPY, SURPRISED, SCARED, DEAD }

private data class BallFace(
    val mood: BallMood,
    val lookX: Float,
    val lookY: Float,
    val blink: Float,
)

private val editorFace = BallFace(BallMood.HAPPY, 0.35f, 0.05f, 0f)

private fun faceOf(sim: RedBallSim): BallFace {
    var nearest: RedBallEnemy? = null
    var nearestD = Float.MAX_VALUE
    for (enemy in sim.enemies) {
        if (!enemy.alive) continue
        val dx = enemy.x - sim.ballX
        val dy = enemy.y - sim.ballY
        val d = dx * dx + dy * dy
        if (d < nearestD) {
            nearestD = d
            nearest = enemy
        }
    }
    val foe = nearest
    val scared = foe != null && nearestD < (foe.size * 0.5f + 130f) * (foe.size * 0.5f + 130f)
    val mood = when {
        sim.dead -> BallMood.DEAD
        sim.won -> BallMood.HAPPY
        scared -> BallMood.SCARED
        !sim.grounded && (sim.ballVy < -50f || sim.ballVy > 120f) -> BallMood.SURPRISED
        else -> BallMood.HAPPY
    }
    return BallFace(mood, sim.gazeX, sim.gazeY, blinkAmount(sim.anim, mood))
}

private fun blinkAmount(time: Float, mood: BallMood): Float {
    if (mood != BallMood.HAPPY) return 0f
    val phase = time % 4.1f
    val local = when {
        phase < 0.12f -> phase / 0.12f
        phase in 0.2f..0.32f -> (phase - 0.2f) / 0.12f
        else -> return 0f
    }
    return sin(local * PI.toFloat())
}

private class StoneSpec(val x: Float, val y: Float, val rx: Float, val ry: Float, val shade: Int)

private class GroundArt(
    val l: Float,
    val t: Float,
    val r: Float,
    val b: Float,
    val bands: List<Path>,
    val edges: List<Path>,
    val shades: List<Seg>,
    val seams: List<Seg>,
    val stones: List<StoneSpec>,
)

private class Seg(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

private class SlopeArt(
    val soil: Path,
    val band: Path,
    val edge: Path,
    val outline: Path,
    val base0: Offset,
    val base1: Offset,
    val minY: Float,
    val maxY: Float,
    val stones: List<StoneSpec>,
    val strokeUnderside: Boolean,
)

private class TerrainArt(
    val key: Long,
    val grounds: List<GroundArt>,
    val slopes: List<SlopeArt>,
    val concreteBoxes: List<RedBallAabb>,
)

private class TerrainPicture(
    val key: Long,
    val ppm: Float,
    val originX: Float,
    val originY: Float,
    val worldW: Float,
    val worldH: Float,
    val image: ImageBitmap,
)

private var playCache: TerrainArt? = null
private var editorCache: TerrainArt? = null
private var pictureCache: TerrainPicture? = null
private var pictureFailedKey: Long? = null

/**
 * Контуры земли и горок собираются один раз на геометрию уровня.
 * В игре статичный слой ещё и растеризуется в одну картинку: камера её только сдвигает.
 */
private fun playArt(scene: RedBallScene): TerrainArt {
    val key = sceneStaticKey(scene)
    val cached = playCache
    if (cached != null && cached.key == key) return cached
    val grounds = ArrayList<GroundArt>(scene.solids.size)
    for (solid in scene.solids) {
        if (solid.r <= scene.minX + 0.5f || solid.l >= scene.worldW - 0.5f) continue
        grounds += buildGroundArt(solid, scene.solids, scene.slopes)
    }
    val slopes = scene.slopes.map { buildSlopeArt(it.x0, it.y0, it.x1, it.y1, scene.solids) }
    return TerrainArt(key, grounds, slopes, scene.concrete).also { playCache = it }
}

private fun editorArt(draft: RedBallDraft): TerrainArt {
    val key = draftStaticKey(draft)
    val cached = editorCache
    if (cached != null && cached.key == key) return cached
    val cell = draft.cell
    val groundPieces = draft.pieces.filter { it.kind == RedBallPieceKind.GROUND }
    val slopePieces = draft.pieces.filter { it.kind == RedBallPieceKind.SLOPE }
    val concreteBoxes = draft.pieces.filter { it.kind == RedBallPieceKind.CONCRETE }.map { it.box(cell) }
    val groundBoxes = groundPieces.map { it.box(cell) }
    val slopeLines = slopePieces.map { it.slopeLine(cell) }
    val grounds = groundPieces.map { piece ->
        val box = piece.box(cell)
        buildGroundArt(box, groundBoxes, slopeLines)
    }
    val slopes = slopeLines.map { buildSlopeArt(it.x0, it.y0, it.x1, it.y1, groundBoxes) }
    return TerrainArt(key, grounds, slopes, concreteBoxes).also { editorCache = it }
}

private fun sceneStaticKey(scene: RedBallScene): Long {
    var h = -3750763034362895579L
    fun mix(v: Int) {
        h = h xor v.toLong()
        h *= 1099511628211L
    }
    fun mixF(v: Float) = mix(v.toBits())
    mixF(scene.minX)
    mixF(scene.minY)
    mixF(scene.worldW)
    mixF(scene.worldH)
    mixF(scene.voidY)
    for (s in scene.solids) {
        mixF(s.l); mixF(s.t); mixF(s.r); mixF(s.b)
    }
    for (s in scene.slopes) {
        mixF(s.x0); mixF(s.y0); mixF(s.x1); mixF(s.y1)
    }
    for (s in scene.concrete) {
        mixF(s.l); mixF(s.t); mixF(s.r); mixF(s.b)
    }
    for (s in scene.ladders) {
        mixF(s.l); mixF(s.t); mixF(s.r); mixF(s.b)
    }
    for (s in scene.spikes) {
        mixF(s.x); mixF(s.top); mixF(s.w); mixF(s.h)
    }
    for (s in scene.springs) {
        mixF(s.l); mixF(s.t); mixF(s.r); mixF(s.b)
    }
    return h
}

private fun draftStaticKey(draft: RedBallDraft): Long {
    var h = 1469598103934665603L
    fun mix(v: Int) {
        h = h xor v.toLong()
        h *= 1099511628211L
    }
    mix(draft.cell.toBits())
    for (piece in draft.pieces) {
        if (
            piece.kind != RedBallPieceKind.GROUND &&
            piece.kind != RedBallPieceKind.SLOPE &&
            piece.kind != RedBallPieceKind.CONCRETE
        ) {
            continue
        }
        mix(piece.kind.ordinal)
        mix(piece.c)
        mix(piece.r)
        mix(piece.w)
        mix(piece.h)
        mix(if (piece.flip) 1 else 0)
    }
    return h
}

private fun terrainPicture(scene: RedBallScene, art: TerrainArt, screenScale: Float): TerrainPicture? {
    if (pictureFailedKey == art.key) return null
    val pad = 42f
    val originX = scene.minX - pad
    val originY = scene.minY - pad
    val worldW = (scene.worldW + pad) - originX
    val worldH = (scene.worldH + pad) - originY
    if (worldW < 8f || worldH < 8f) return null
    val maxPixels = 4_800_000
    val wanted = screenScale.coerceIn(1f, 2f)
    val ppm = if (worldW * worldH * wanted * wanted > maxPixels) {
        sqrt(maxPixels / (worldW * worldH))
    } else {
        wanted
    }
    if (ppm < 0.85f) return null
    val cached = pictureCache
    if (cached != null && cached.key == art.key && abs(cached.ppm - ppm) < 0.08f) return cached
    val bw = (worldW * ppm).toInt().coerceIn(1, 8192)
    val bh = (worldH * ppm).toInt().coerceIn(1, 8192)
    return try {
        val bitmap = ImageBitmap(bw, bh, ImageBitmapConfig.Argb8888)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(bitmap),
            Size(bw.toFloat(), bh.toFloat()),
        ) {
            val sx = bw / worldW
            val sy = bh / worldH
            withTransform({
                scale(sx, sy, Offset.Zero)
                translate(-originX, -originY)
            }) {
                drawStaticWorld(scene, art)
            }
        }
        val created = TerrainPicture(art.key, ppm, originX, originY, worldW, worldH, bitmap)
        val previous = pictureCache
        pictureCache = created
        if (previous != null) {
            runCatching { previous.image.asAndroidBitmap().recycle() }
        }
        created
    } catch (_: Throwable) {
        pictureFailedKey = art.key
        null
    }
}

private fun DrawScope.drawTerrainPicture(picture: TerrainPicture) {
    val image = picture.image
    withTransform({
        translate(picture.originX, picture.originY)
        scale(picture.worldW / image.width, picture.worldH / image.height, Offset.Zero)
    }) {
        drawImage(image, filterQuality = FilterQuality.Medium)
    }
}

private fun DrawScope.drawStaticWorld(scene: RedBallScene, art: TerrainArt) {
    drawVoid(scene)
    for (ground in art.grounds) drawGroundArt(ground)
    for (block in scene.concrete) {
        drawConcreteBlock(block, topIsOpen(block, scene.concrete, emptyList()))
    }
    for (slope in art.slopes) drawSlopeArt(slope)
    for (ladder in scene.ladders) drawLadder(ladder)
    for (spike in scene.spikes) drawIronSpike(spike.x, spike.top, spike.w, spike.h)
    for (spring in scene.springs) drawFairySpring(spring.l, spring.t, spring.r, spring.b)
}

private val soilStroke = Stroke(5.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val grassStroke = Stroke(3.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val stoneFills = arrayOf(
    Color(0xFF5A3824),
    Color(0xFF6E4A30),
    Color(0xFF3E2818),
    Color(0xFF7A5640),
)

private fun buildGroundArt(box: RedBallAabb, solids: List<RedBallAabb>, slopes: List<RedBallSlope>): GroundArt {
    val w = box.r - box.l
    val h = box.b - box.t
    if (w <= 1f || h <= 1f) {
        return GroundArt(box.l, box.t, box.r, box.b, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
    val grassBits = grassIntervals(box, solids, slopes).filter { it.second - it.first >= 18f }
    val soilTop = if (grassBits.isNotEmpty()) box.t + 24f else box.t + 10f
    val stones = stonesInRect(box.l, soilTop, box.r, box.b)
    val seams = ArrayList<Seg>(4)
    seams += verticalSeams(box.l, box.t, box.b, solids, slopes, box, leftSide = true)
    seams += verticalSeams(box.r, box.t, box.b, solids, slopes, box, leftSide = false)
    seams += horizontalSeams(box.b, box.l, box.r, solids, box, alongTop = false)
    if (grassBits.isEmpty()) seams += horizontalSeams(box.t, box.l, box.r, solids, box, alongTop = true)
    val bands = ArrayList<Path>(grassBits.size)
    val edges = ArrayList<Path>(grassBits.size)
    val shades = ArrayList<Seg>(grassBits.size)
    val depth = min(GRASS_DEPTH, h * 0.45f).coerceAtLeast(8f)
    for ((a, b) in grassBits) {
        val pts = edgePoints(a, box.t, b, box.t)
        val down = bladeDown(a, box.t, b, box.t)
        bands += bandPath(pts, down, depth)
        edges += edgePath(pts)
        shades += Seg(a + 2f, box.t + depth, b - 2f, box.t + depth)
    }
    return GroundArt(box.l, box.t, box.r, box.b, bands, edges, shades, seams, stones)
}

internal fun grassIntervals(
    box: RedBallAabb,
    solids: List<RedBallAabb>,
    slopes: List<RedBallSlope>,
): List<Pair<Float, Float>> {
    val covered = ArrayList<Pair<Float, Float>>()
    for (other in solids) {
        if (other === box) continue
        if (other.t < box.t - 0.5f && other.b >= box.t - 0.5f) {
            val a = max(other.l, box.l)
            val b = min(other.r, box.r)
            if (b - a > 1f) covered += a to b
        }
    }
    for (slope in slopes) {
        val top = min(slope.y0, slope.y1)
        val bot = max(slope.y0, slope.y1)
        if (box.t > bot + 4f || box.t < top - 4f) continue
        val a = max(min(slope.x0, slope.x1), box.l)
        val b = min(max(slope.x0, slope.x1), box.r)
        if (b - a > 1f) covered += a to b
    }
    return subtractSpan(box.l, box.r, covered)
}

private const val GRASS_DEPTH = 16f

private fun verticalSeams(
    x: Float,
    top: Float,
    bottom: Float,
    solids: List<RedBallAabb>,
    slopes: List<RedBallSlope>,
    self: RedBallAabb,
    leftSide: Boolean,
): List<Seg> {
    val covered = ArrayList<Pair<Float, Float>>()
    for (other in solids) {
        if (other === self) continue
        val touch = if (leftSide) abs(other.r - x) < 1.6f else abs(other.l - x) < 1.6f
        if (!touch) continue
        val a = max(other.t, top)
        val b = min(other.b, bottom)
        if (b - a > 1f) covered += a to b
    }
    for (slope in slopes) {
        val legX = if (slope.y0 <= slope.y1) slope.x0 else slope.x1
        if (abs(legX - x) > 3f) continue
        val a = max(min(slope.y0, slope.y1), top)
        val b = min(max(slope.y0, slope.y1), bottom)
        if (b - a > 1f) covered += a to b
    }
    return subtractSpan(top, bottom, covered).map { Seg(x, it.first, x, it.second) }
}

private fun horizontalSeams(
    y: Float,
    left: Float,
    right: Float,
    solids: List<RedBallAabb>,
    self: RedBallAabb,
    alongTop: Boolean,
): List<Seg> {
    val covered = ArrayList<Pair<Float, Float>>()
    for (other in solids) {
        if (other === self) continue
        val touch = if (alongTop) abs(other.b - y) < 1.6f else abs(other.t - y) < 1.6f
        if (!touch) continue
        val a = max(other.l, left)
        val b = min(other.r, right)
        if (b - a > 1f) covered += a to b
    }
    return subtractSpan(left, right, covered).map { Seg(it.first, y, it.second, y) }
}

private fun subtractSpan(start: Float, end: Float, cuts: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    if (end - start < 1.5f) return emptyList()
    val sorted = cuts.sortedBy { it.first }
    val out = ArrayList<Pair<Float, Float>>()
    var cursor = start
    for ((a, b) in sorted) {
        val from = max(a, start)
        val to = min(b, end)
        if (to <= from) continue
        if (from - cursor > 1.5f) out += cursor to from
        if (to > cursor) cursor = to
    }
    if (end - cursor > 1.5f) out += cursor to end
    return out
}

private fun slopeUndersideExposed(corner: Offset, solids: List<RedBallAabb>): Boolean {
    for (solid in solids) {
        if (corner.x >= solid.l - 8f && corner.x <= solid.r + 8f && corner.y >= solid.t - 8f && corner.y <= solid.b + 8f) {
            return false
        }
    }
    return true
}

private fun buildSlopeArt(x0: Float, y0: Float, x1: Float, y1: Float, solids: List<RedBallAabb>): SlopeArt {
    val tuck = 5f
    val third = if (y0 <= y1) Offset(x0 - tuck, y1 + tuck) else Offset(x1 + tuck, y0 + tuck)
    val low = if (y0 <= y1) Offset(x1, y1 + tuck) else Offset(x0, y0 + tuck)
    val soil = Path().apply {
        moveTo(x0, y0)
        lineTo(x1, y1)
        lineTo(low.x, low.y)
        lineTo(third.x, third.y)
        close()
    }
    val corner = if (y0 <= y1) Offset(x0, y1) else Offset(x1, y0)
    val pts = edgePoints(x0, y0, x1, y1)
    val down = bladeDown(x0, y0, x1, y1)
    val depth = 15f
    val band = bandPath(pts, down, depth)
    val edge = edgePath(pts)
    val outline = Path().apply {
        moveTo(x0, y0)
        lineTo(corner.x, corner.y)
        lineTo(x1, y1)
    }
    val minX = min(x0, x1)
    val maxX = max(x0, x1)
    val minY = min(min(y0, y1), third.y)
    val maxY = max(max(y0, y1), third.y)
    val raw = stonesInRect(minX, minY, maxX, maxY, minCount = 1)
    val stones = raw.filter { stone ->
        pointInTri(stone.x, stone.y, x0, y0, x1, y1, corner.x, corner.y) &&
            distToSeg(stone.x, stone.y, x0, y0, x1, y1) > stone.ry + 8f
    }
    val exposed = slopeUndersideExposed(corner, solids)
    return SlopeArt(
        soil = soil,
        band = band,
        edge = edge,
        outline = outline,
        base0 = Offset(x0 + down.x * depth, y0 + down.y * depth),
        base1 = Offset(x1 + down.x * depth, y1 + down.y * depth),
        minY = minY,
        maxY = maxY,
        stones = stones,
        strokeUnderside = exposed,
    )
}

private fun DrawScope.drawGroundArt(art: GroundArt) {
    val w = art.r - art.l
    val h = art.b - art.t
    if (w <= 1f || h <= 1f) return
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFFB07848), Color(0xFF8B5430), Color(0xFF754628)),
            startY = art.t,
            endY = art.b,
        ),
        Offset(art.l, art.t),
        Size(w, h),
    )
    for (stone in art.stones) drawStone(stone)
    val grass = Brush.verticalGradient(
        listOf(Color(0xFFDFFF78), Color(0xFF9BE437), Color(0xFF5CB51C)),
        startY = art.t - 22f,
        endY = art.t + 22f,
    )
    for (band in art.bands) drawPath(band, grass)
    for (shade in art.shades) {
        drawLine(Color(0xFF3E8C16), Offset(shade.x0, shade.y0), Offset(shade.x1, shade.y1), 4.2f, StrokeCap.Round)
    }
    for (seg in art.seams) {
        drawLine(Color(0xFF3A2416), Offset(seg.x0, seg.y0), Offset(seg.x1, seg.y1), 5.2f, StrokeCap.Round)
    }
    for (edge in art.edges) drawPath(edge, Color(0xFF24580F), style = grassStroke)
}

private fun DrawScope.drawSlopeArt(art: SlopeArt) {
    drawPath(
        art.soil,
        Brush.verticalGradient(
            listOf(Color(0xFFC48A52), Color(0xFF8D5A32), Color(0xFF5C381E)),
            startY = art.minY,
            endY = art.maxY,
        ),
    )
    for (stone in art.stones) drawStone(stone)
    drawPath(
        art.band,
        Brush.verticalGradient(
            listOf(Color(0xFFDFFF78), Color(0xFF8ED63A), Color(0xFF4FA818)),
            startY = art.minY - 18f,
            endY = art.maxY,
        ),
    )
    drawLine(Color(0xFF3E8C16), art.base0, art.base1, 4f, StrokeCap.Round)
    if (art.strokeUnderside) drawPath(art.outline, Color(0xFF3A2416), style = soilStroke)
    drawPath(art.edge, Color(0xFF24580F), style = grassStroke)
}

private fun DrawScope.drawStone(stone: StoneSpec) {
    val fill = stoneFills[stone.shade and 3]
    drawOval(
        Color(0xFF2A1A10),
        Offset(stone.x - stone.rx, stone.y - stone.ry),
        Size(stone.rx * 2f, stone.ry * 2f),
    )
    drawOval(
        fill,
        Offset(stone.x - stone.rx * 0.84f, stone.y - stone.ry * 0.82f),
        Size(stone.rx * 1.68f, stone.ry * 1.64f),
    )
    drawOval(
        Color.White.copy(alpha = 0.16f),
        Offset(stone.x - stone.rx * 0.45f, stone.y - stone.ry * 0.62f),
        Size(stone.rx * 0.55f, stone.ry * 0.36f),
    )
}

private fun edgePoints(x0: Float, y0: Float, x1: Float, y1: Float): List<Offset> {
    if (abs(y1 - y0) < 1.5f && abs(x1 - x0) > 8f) return horizontalTeeth(x0, y0, x1)
    val dx = x1 - x0
    val dy = y1 - y0
    val len = hypot(dx, dy).coerceAtLeast(1f)
    val normal = bladeUp(x0, y0, x1, y1)
    val step = 17f
    val n = (len / step).toInt().coerceIn(2, 72)
    val pts = ArrayList<Offset>(n * 2 + 2)
    pts.add(Offset(x0, y0))
    val seed = (x0 * 0.17f + y0 * 0.09f).toInt()
    for (i in 0 until n) {
        val tm = (i + 0.5f) / n
        val h = bladeHeight(seed + i)
        pts.add(Offset(x0 + dx * tm + normal.x * h, y0 + dy * tm + normal.y * h))
        if (i < n - 1) {
            val t1 = (i + 1) / n.toFloat()
            pts.add(Offset(x0 + dx * t1, y0 + dy * t1))
        }
    }
    pts.add(Offset(x1, y1))
    return pts
}

/** Зубцы травы сидят на общей сетке, чтобы соседние блоки земли сходились без шва. */
private fun horizontalTeeth(x0: Float, y: Float, x1: Float): List<Offset> {
    val left = min(x0, x1)
    val right = max(x0, x1)
    val step = 17f
    val pts = ArrayList<Offset>()
    fun add(x: Float, peak: Boolean) {
        val h = if (peak) bladeHeight(kotlin.math.floor(x / step).toInt()) else 0f
        pts.add(Offset(x, y - h))
    }
    if (x0 <= x1) {
        add(left, false)
        var peak = kotlin.math.ceil((left + step * 0.5f) / step) * step - step * 0.5f
        if (peak < left + 1f) peak += step
        while (peak < right - 1f) {
            add(peak, true)
            val base = peak + step * 0.5f
            if (base < right - 1f) add(base, false)
            peak += step
        }
        add(right, false)
    } else {
        add(right, false)
        var peak = kotlin.math.floor((right - step * 0.5f) / step) * step + step * 0.5f
        if (peak > right - 1f) peak -= step
        while (peak > left + 1f) {
            add(peak, true)
            val base = peak - step * 0.5f
            if (base > left + 1f) add(base, false)
            peak -= step
        }
        add(left, false)
    }
    return pts
}

private fun bladeUp(x0: Float, y0: Float, x1: Float, y1: Float): Offset {
    val dx = x1 - x0
    val dy = y1 - y0
    val len = hypot(dx, dy).coerceAtLeast(1f)
    val tx = dx / len
    val ty = dy / len
    var nx = -ty
    var ny = tx
    if (ny > 0.02f) {
        nx = -nx
        ny = -ny
    }
    nx *= 0.3f
    ny = ny * 0.3f - 0.7f
    val nl = hypot(nx, ny).coerceAtLeast(0.001f)
    return Offset(nx / nl, ny / nl)
}

private fun bladeDown(x0: Float, y0: Float, x1: Float, y1: Float): Offset {
    val up = bladeUp(x0, y0, x1, y1)
    return Offset(-up.x, -up.y)
}

private fun bladeHeight(i: Int): Float = when (Math.floorMod(i, 5)) {
    0 -> 16f
    1 -> 8f
    2 -> 13f
    3 -> 7f
    else -> 11f
}

private fun bandPath(pts: List<Offset>, down: Offset, depth: Float): Path {
    val path = Path()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
    val last = pts.last()
    path.lineTo(last.x + down.x * depth, last.y + down.y * depth)
    path.lineTo(pts[0].x + down.x * depth, pts[0].y + down.y * depth)
    path.close()
    return path
}

private fun edgePath(pts: List<Offset>): Path {
    val path = Path()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
    return path
}

private fun stonesInRect(l: Float, t: Float, r: Float, b: Float, minCount: Int = 2): List<StoneSpec> {
    val w = r - l
    val h = b - t
    if (w < 18f || h < 16f) return emptyList()
    val target = ((w * h) / 16000f).toInt().coerceIn(minCount, 8)
    val cols = sqrt(target * (w / h).coerceAtLeast(0.45f)).toInt().coerceAtLeast(1)
    val rows = (target / cols).coerceAtLeast(1)
    val out = ArrayList<StoneSpec>(cols * rows)
    val seedX = l.toInt()
    val seedY = t.toInt()
    for (iy in 0 until rows) {
        for (ix in 0 until cols) {
            val jx = unitHash(seedX + ix * 13, seedY + iy * 29)
            val jy = unitHash(seedX + ix * 7 + 3, seedY + iy * 11 + 5)
            val big = unitHash(seedX + ix + 19, seedY + iy + 4) > 0.72f
            val rx = if (big) 16f + unitHash(ix + 2, iy + seedY) * 14f else 8f + unitHash(ix + 6, iy + 8) * 7f
            val ry = rx * (0.62f + unitHash(ix + 4, iy + 15) * 0.28f)
            val x = l + w * ((ix + 0.28f + jx * 0.5f) / cols)
            val y = t + h * ((iy + 0.3f + jy * 0.45f) / rows)
            if (x - rx < l + 4f || x + rx > r - 4f || y - ry < t + 2f || y + ry > b - 4f) continue
            val shade = (unitHash(ix + 8, iy + 3) * 4f).toInt().coerceIn(0, 3)
            out.add(StoneSpec(x, y, rx, ry, shade))
        }
    }
    return out
}

private fun unitHash(a: Int, b: Int): Float {
    var x = a * 73856093 xor b * 19349663
    x = x xor (x ushr 13)
    x *= 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFF) / 65535f
}

private fun pointInTri(
    px: Float,
    py: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    cx: Float,
    cy: Float,
): Boolean {
    val v0x = cx - ax
    val v0y = cy - ay
    val v1x = bx - ax
    val v1y = by - ay
    val v2x = px - ax
    val v2y = py - ay
    val dot00 = v0x * v0x + v0y * v0y
    val dot01 = v0x * v1x + v0y * v1y
    val dot02 = v0x * v2x + v0y * v2y
    val dot11 = v1x * v1x + v1y * v1y
    val dot12 = v1x * v2x + v1y * v2y
    val den = dot00 * dot11 - dot01 * dot01
    if (abs(den) < 1e-3f) return false
    val inv = 1f / den
    val u = (dot11 * dot02 - dot01 * dot12) * inv
    val v = (dot00 * dot12 - dot01 * dot02) * inv
    return u >= 0f && v >= 0f && u + v <= 1f
}

private fun distToSeg(px: Float, py: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float {
    val dx = x1 - x0
    val dy = y1 - y0
    val len2 = dx * dx + dy * dy
    if (len2 < 1f) return hypot(px - x0, py - y0)
    val t = (((px - x0) * dx + (py - y0) * dy) / len2).coerceIn(0f, 1f)
    return hypot(px - (x0 + dx * t), py - (y0 + dy * t))
}

private fun topIsOpen(solid: RedBallAabb, solids: List<RedBallAabb>, slopes: List<RedBallSlope>): Boolean {
    for (other in solids) {
        if (other === solid) continue
        if (other.t < solid.t - 0.5f && other.b >= solid.t - 0.5f && other.l < solid.r - 4f && other.r > solid.l + 4f) {
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

private val waterPath = Path()
private val reedPath = Path()

/** Пруд или заводь. Поверхность чуть дышит, тело — плоский градиент. */
internal fun DrawScope.drawWaterVolume(water: RedBallWater, time: Float) {
    val w = water.r - water.l
    val h = water.b - water.t
    if (w < 2f || h < 2f) return
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xCC9AEEF6), Color(0xCC2E9BB8), Color(0xE0145670)),
            startY = water.t,
            endY = water.b,
        ),
        topLeft = Offset(water.l, water.t),
        size = Size(w, h),
    )
    val wave = waterPath
    wave.reset()
    val surface = water.t + 3f
    wave.moveTo(water.l, surface)
    var x = water.l
    var crest = true
    val bob = sin(time * 2.2f) * 1.6f
    while (x < water.r) {
        val next = min(x + 36f, water.r)
        val mid = (x + next) * 0.5f
        wave.quadraticTo(mid, surface + bob + if (crest) -6f else 8f, next, surface)
        x = next
        crest = !crest
    }
    drawPath(wave, Color(0xFFF3FEFF), style = Stroke(3.2f, cap = StrokeCap.Round))
}

/**
 * Деревянная тележка на двух колёсах. Если у главы есть PNG слота [com.example.bible.games.skins.RedBallSkinSlot.CART],
 * рисуется он, иначе эта процедура.
 */
internal fun DrawScope.drawCartoonCart(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    spin: Float,
    sprite: ImageBitmap?,
) {
    if (w < 8f || h < 8f) return
    if (sprite != null) {
        val dw = w * 1.2f
        val dh = h * 1.7f
        withTransform({
            translate(x - dw / 2f, y - h * 0.95f)
            scale(dw / sprite.width, dh / sprite.height, Offset.Zero)
        }) {
            drawImage(sprite, filterQuality = FilterQuality.Medium)
        }
        return
    }
    val bodyH = h * 0.72f
    val top = y - h * 0.42f
    val left = x - w / 2f
    drawRoundRect(Color(0xFF6B3E1E), Offset(left, top), Size(w, bodyH), CornerRadius(7f, 7f))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFE0A15A), Color(0xFFC4843A), Color(0xFF8D5524)), startY = top, endY = top + bodyH),
        Offset(left + 3f, top + 3f),
        Size((w - 6f).coerceAtLeast(1f), (bodyH - 6f).coerceAtLeast(1f)),
        CornerRadius(5f, 5f),
    )
    val plank = (w - 10f) / 3f
    for (i in 0 until 3) {
        val px = left + 5f + i * plank
        drawLine(
            Color(0xFF6B3E1E).copy(alpha = 0.55f),
            Offset(px, top + 6f),
            Offset(px, top + bodyH - 6f),
            strokeWidth = 2f,
        )
    }
    drawRoundRect(
        Color(0xFF4A2A12),
        Offset(left, top),
        Size(w, bodyH),
        CornerRadius(7f, 7f),
        style = Stroke(3f),
    )
    val wheelR = h * 0.28f
    val wy = top + bodyH - wheelR * 0.15f
    fun wheel(wx: Float) {
        drawCircle(Color(0xFF2C241C), wheelR, Offset(wx, wy))
        drawCircle(Color(0xFF8A8178), wheelR * 0.72f, Offset(wx, wy))
        rotate(Math.toDegrees(spin.toDouble()).toFloat(), Offset(wx, wy)) {
            drawLine(Color(0xFF2C241C), Offset(wx - wheelR * 0.5f, wy), Offset(wx + wheelR * 0.5f, wy), 2.2f, StrokeCap.Round)
            drawLine(Color(0xFF2C241C), Offset(wx, wy - wheelR * 0.5f), Offset(wx, wy + wheelR * 0.5f), 2.2f, StrokeCap.Round)
        }
        drawCircle(Color(0xFFF0D48A), wheelR * 0.2f, Offset(wx, wy))
    }
    wheel(x - w * 0.30f)
    wheel(x + w * 0.30f)
}

internal fun DrawScope.drawVoid(scene: RedBallScene) {
    val surface = scene.voidY - 6f
    val bottom = scene.worldH + 60f
    if (surface >= bottom) return
    val left = scene.minX - 80f
    val width = scene.worldW - left + 80f
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF8FE7F0), Color(0xFF3EAFCB), Color(0xFF1C7594), Color(0xFF12485C)),
            startY = surface,
            endY = bottom,
        ),
        topLeft = Offset(left, surface),
        size = Size(width, bottom - surface),
    )
    val right = left + width
    val wave = waterPath
    wave.reset()
    wave.moveTo(left, surface + 8f)
    var x = left
    var crest = true
    while (x < right) {
        val next = x + 42f
        wave.quadraticTo(x + 21f, surface + if (crest) -7f else 16f, next, surface + 8f)
        x = next
        crest = !crest
    }
    drawPath(wave, Color(0xFFE7FCFF), style = Stroke(4f, cap = StrokeCap.Round))
    var px = left + 24f
    var i = 0
    while (px < right && i < 48) {
        val deep = (i % 3) != 0
        val base = if (deep) surface + 34f else surface + 10f
        val h = if (deep) 22f else 30f + (i % 4) * 6f
        drawReed(px, base, h, if (deep) Color(0xFF1A6578) else Color(0xFF2E8C48))
        if (i % 4 == 1) {
            drawOval(
                Color(0xFF14556C).copy(alpha = 0.4f),
                Offset(px + 10f, surface + 28f),
                Size(18f, 10f),
            )
        }
        px += 78f + (i % 3) * 16f
        i++
    }
}

internal fun DrawScope.drawIronSpike(x: Float, top: Float, w: Float, h: Float) {
    val count = (w / 28f).toInt().coerceIn(1, 6)
    val tw = w / count
    for (i in 0 until count) {
        val cx = x - w / 2f + tw * (i + 0.5f)
        val path = scratchPath
        path.reset()
        path.moveTo(cx, top + 1f)
        path.lineTo(cx - tw * 0.46f, top + h)
        path.lineTo(cx + tw * 0.46f, top + h)
        path.close()
        drawPath(
            path,
            Brush.verticalGradient(
                listOf(Color(0xFFC5CDD4), Color(0xFF6E777F), Color(0xFF2E353B)),
                startY = top,
                endY = top + h,
            ),
        )
        val shine = scratchPathB
        shine.reset()
        shine.moveTo(cx - tw * 0.06f, top + h * 0.16f)
        shine.lineTo(cx - tw * 0.22f, top + h * 0.72f)
        shine.lineTo(cx - tw * 0.02f, top + h * 0.58f)
        shine.close()
        drawPath(shine, Color.White.copy(alpha = 0.72f))
        drawPath(path, Color(0xFF1A1E22), style = Stroke(3.2f, join = StrokeJoin.Round))
    }
}

internal fun DrawScope.drawFairySpring(l: Float, t: Float, r: Float, b: Float) {
    val w = r - l
    val h = b - t
    if (w < 4f || h < 4f) return
    val plateH = (h * 0.18f).coerceAtLeast(4f)
    drawRoundRect(Color(0xFF2E343A), Offset(l, b - plateH), Size(w, plateH), CornerRadius(4f, 4f))
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xFFF4F7F8), Color(0xFFB7C0C6), Color(0xFF6E777E))),
        Offset(l + 2f, b - plateH + 2f),
        Size((w - 4f).coerceAtLeast(1f), (plateH - 3f).coerceAtLeast(1f)),
        CornerRadius(3f, 3f),
    )
    val topW = w * 0.76f
    drawRoundRect(Color(0xFF2E343A), Offset(l + w * 0.12f, t), Size(topW, plateH), CornerRadius(4f, 4f))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFD5DCE2))),
        Offset(l + w * 0.12f + 2f, t + 1.5f),
        Size((topW - 4f).coerceAtLeast(1f), (plateH - 3f).coerceAtLeast(1f)),
        CornerRadius(3f, 3f),
    )
    val coils = 3
    val stroke = (min(w, h) * 0.16f).coerceIn(3.5f, 7f)
    for (i in 0 until coils) {
        val y = t + h * (0.2f + 0.58f * (i + 0.5f) / coils)
        val color = if (i % 2 == 0) Color(0xFFFFD54A) else Color(0xFFF0A020)
        val arcTop = Offset(l + w * 0.14f, y - h * 0.16f)
        val arcSize = Size(w * 0.72f, h * 0.32f)
        drawArc(
            Color(0xFF5A3A10),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = arcTop,
            size = arcSize,
            style = Stroke(width = stroke + 2.6f, cap = StrokeCap.Round),
        )
        drawArc(
            color,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = arcTop,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawFinishFlag(l: Float, t: Float, r: Float, b: Float, wave: Float) {
    val w = r - l
    val h = b - t
    if (w < 4f || h < 4f) return
    val poleW = (w * 0.09f).coerceIn(4f, 11f)
    val poleX = l + w * 0.28f
    val top = t + h * 0.05f
    drawRoundRect(Color(0xFF4A2E16), Offset(poleX - 1f, top), Size(poleW + 2f, b - top), CornerRadius(3f, 3f))
    drawRoundRect(
        Brush.horizontalGradient(
            listOf(Color(0xFFF0D2A2), Color(0xFFD09A55), Color(0xFF8C5A2C)),
            startX = poleX,
            endX = poleX + poleW,
        ),
        Offset(poleX, top),
        Size(poleW, b - top),
        CornerRadius(3f, 3f),
    )
    val finial = Offset(poleX + poleW / 2f, top + 1f)
    drawCircle(Color(0xFF8A6410), poleW * 1.25f, finial)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFF6C0), Color(0xFFFFD24A), Color(0xFFE0A010)),
            center = finial - Offset(poleW * 0.3f, poleW * 0.35f),
            radius = poleW * 1.6f,
        ),
        poleW,
        finial,
    )
    val flutter = sin(wave * 3.1f) * (h * 0.025f)
    val flagLen = w * 0.7f
    val flagH = (h * 0.26f).coerceAtLeast(18f)
    val y0 = top + poleW * 1.6f
    val cloth = scratchPath
    cloth.reset()
    cloth.moveTo(poleX + poleW * 0.7f, y0)
    cloth.quadraticTo(
        poleX + flagLen * 0.55f,
        y0 - flagH * 0.08f + flutter,
        poleX + flagLen,
        y0 + flagH * 0.18f + flutter * 0.4f,
    )
    cloth.lineTo(poleX + flagLen * 0.9f, y0 + flagH * 0.55f + flutter * 0.2f)
    cloth.quadraticTo(
        poleX + flagLen * 0.4f,
        y0 + flagH * 0.95f + flutter,
        poleX + poleW * 0.7f,
        y0 + flagH * 0.82f,
    )
    cloth.close()
    drawPath(
        cloth,
        Brush.linearGradient(
            listOf(Color(0xFFFF6A6A), Color(0xFFE42334), Color(0xFFB01022)),
            start = Offset(poleX, y0),
            end = Offset(poleX + flagLen, y0 + flagH),
        ),
    )
    drawPath(cloth, Color(0xFF7A1020), style = Stroke(2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawLine(
        Color.White.copy(alpha = 0.4f),
        Offset(poleX + poleW, y0 + 4f),
        Offset(poleX + flagLen * 0.62f, y0 + flagH * 0.22f + flutter),
        2.2f,
        StrokeCap.Round,
    )
}

private enum class PortalMood { OPEN, EXIT, ASLEEP, LOST }

/** Каменная арка. Вход — фиолетовый вихрь, выход — бирюзовая арка со стрелкой вниз. */
private fun DrawScope.drawFairyPortal(
    l: Float,
    t: Float,
    r: Float,
    b: Float,
    time: Float,
    mood: PortalMood,
) {
    val w = r - l
    val h = b - t
    if (w < 4f || h < 4f) return
    val cx = (l + r) / 2f
    val cy = t + h * 0.58f
    val exit = mood == PortalMood.EXIT
    val open = mood == PortalMood.OPEN
    val arch = min(w * 0.52f, h * 0.36f)
    if (open) {
        val pulse = 0.88f + 0.12f * sin(time * 3.2f)
        drawCircle(Color(0xFFC4B5FD).copy(alpha = 0.28f), w * 0.72f * pulse, Offset(cx, cy))
    } else if (exit) {
        val pulse = 0.9f + 0.08f * sin(time * 2.4f)
        drawCircle(Color(0xFF99F6E4).copy(alpha = 0.32f), w * 0.68f * pulse, Offset(cx, cy))
    }
    val frame = Path().apply {
        moveTo(l, b)
        lineTo(l, t + arch)
        quadraticBezierTo(cx, t - arch * 0.05f, r, t + arch)
        lineTo(r, b)
        close()
    }
    val stoneTop = when {
        exit -> Color(0xFFCCFBF1)
        open -> Color(0xFFE9D5FF)
        else -> Color(0xFFE7E5E4)
    }
    val stoneMid = when {
        exit -> Color(0xFF0F766E)
        open -> Color(0xFF7C3AED)
        else -> Color(0xFFA8A29E)
    }
    val stoneBot = when {
        exit -> Color(0xFF134E4A)
        open -> Color(0xFF4C1D95)
        else -> Color(0xFF57534E)
    }
    drawPath(frame, Brush.verticalGradient(listOf(stoneTop, stoneMid, stoneBot), startY = t, endY = b))
    drawPath(
        frame,
        when {
            exit -> Color(0xFF134E4A).copy(alpha = 0.9f)
            open -> Color(0xFF3B0764).copy(alpha = 0.9f)
            else -> Color(0xFF3B0764).copy(alpha = 0.45f)
        },
        style = Stroke(w * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    val inset = w * 0.2f
    val inner = Path().apply {
        moveTo(l + inset, b - h * 0.04f)
        lineTo(l + inset, t + arch * 1.2f)
        quadraticBezierTo(cx, t + arch * 0.35f, r - inset, t + arch * 1.2f)
        lineTo(r - inset, b - h * 0.04f)
        close()
    }
    if (open) {
        drawPath(
            inner,
            Brush.radialGradient(
                listOf(Color(0xFFF5F3FF), Color(0xFF67E8F9), Color(0xFF7C3AED), Color(0xFF1E1B4B)),
                center = Offset(cx - w * 0.06f, cy - h * 0.06f),
                radius = w * 0.62f,
            ),
        )
        val spin = time * 130f
        rotate(spin, Offset(cx, cy)) {
            drawArc(
                Color.White.copy(alpha = 0.9f),
                startAngle = 18f,
                sweepAngle = 86f,
                useCenter = false,
                topLeft = Offset(cx - w * 0.22f, cy - h * 0.16f),
                size = Size(w * 0.44f, h * 0.3f),
                style = Stroke(width = (w * 0.055f).coerceIn(2.5f, 6f), cap = StrokeCap.Round),
            )
        }
        rotate(-spin * 0.65f, Offset(cx, cy)) {
            drawArc(
                Color(0xFFFDE68A).copy(alpha = 0.95f),
                startAngle = 200f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(cx - w * 0.15f, cy - h * 0.1f),
                size = Size(w * 0.3f, h * 0.2f),
                style = Stroke(width = (w * 0.04f).coerceIn(2f, 5f), cap = StrokeCap.Round),
            )
        }
        drawCircle(Color.White.copy(alpha = 0.85f), w * 0.045f, Offset(cx - w * 0.08f, cy - h * 0.04f))
        drawSparkle(cx - w * 0.02f, cy - h * 0.12f, w * 0.07f, Color.White)
        drawSparkle(cx + w * 0.12f, cy + h * 0.02f, w * 0.05f, Color(0xFFFDE68A))
    } else if (exit) {
        drawPath(
            inner,
            Brush.radialGradient(
                listOf(Color(0xFFF0FDFA), Color(0xFF5EEAD4), Color(0xFF0F766E)),
                center = Offset(cx, cy - h * 0.04f),
                radius = w * 0.55f,
            ),
        )
        val shaft = (w * 0.07f).coerceIn(3f, 7f)
        drawLine(
            Color.White,
            Offset(cx, cy - h * 0.16f),
            Offset(cx, cy + h * 0.02f),
            shaft,
            StrokeCap.Round,
        )
        val head = Path().apply {
            moveTo(cx - w * 0.16f, cy - h * 0.02f)
            lineTo(cx + w * 0.16f, cy - h * 0.02f)
            lineTo(cx, cy + h * 0.14f)
            close()
        }
        drawPath(head, Color.White)
    } else {
        drawPath(
            inner,
            Brush.verticalGradient(listOf(Color(0xFF57534E), Color(0xFF1C1917)), startY = t, endY = b),
        )
        drawLine(
            Color(0xFFD6D3D1).copy(alpha = 0.75f),
            Offset(l + inset * 1.2f, t + arch * 1.1f),
            Offset(r - inset * 1.2f, b - h * 0.12f),
            (w * 0.035f).coerceIn(2f, 4f),
            StrokeCap.Round,
        )
        drawLine(
            Color(0xFFD6D3D1).copy(alpha = 0.75f),
            Offset(r - inset * 1.2f, t + arch * 1.1f),
            Offset(l + inset * 1.2f, b - h * 0.12f),
            (w * 0.035f).coerceIn(2f, 4f),
            StrokeCap.Round,
        )
    }
    val cap = w * 0.09f
    drawCircle(stoneTop, cap, Offset(l + cap * 1.3f, b - cap))
    drawCircle(stoneTop, cap, Offset(r - cap * 1.3f, b - cap))
    if (mood == PortalMood.LOST) {
        val badge = Offset(r - w * 0.08f, t + h * 0.08f)
        drawCircle(Color(0xFFDC2626), w * 0.16f, badge)
        drawCircle(Color.White, w * 0.12f, badge)
        drawLine(
            Color(0xFFDC2626),
            badge + Offset(0f, -w * 0.05f),
            badge + Offset(0f, w * 0.02f),
            (w * 0.035f).coerceAtLeast(2f),
            StrokeCap.Round,
        )
        drawCircle(Color(0xFFDC2626), (w * 0.018f).coerceAtLeast(1.4f), badge + Offset(0f, w * 0.055f))
    }
}

private fun DrawScope.drawSparkle(cx: Float, cy: Float, s: Float, color: Color) {
    drawLine(color, Offset(cx - s, cy), Offset(cx + s, cy), s * 0.28f, StrokeCap.Round)
    drawLine(color, Offset(cx, cy - s), Offset(cx, cy + s), s * 0.28f, StrokeCap.Round)
    drawCircle(color, s * 0.22f, Offset(cx, cy))
}

private fun DrawScope.drawWoodCrate(cx: Float, cy: Float, side: Float) {
    if (side < 6f) return
    val x = cx - side / 2f
    val y = cy - side / 2f
    drawOval(
        Color(0xFF3A2414).copy(alpha = 0.22f),
        Offset(x + side * 0.08f, y + side * 0.9f),
        Size(side * 0.84f, side * 0.14f),
    )
    val radius = CornerRadius(side * 0.05f, side * 0.05f)
    drawRoundRect(Color(0xFF4A3018), Offset(x, y), Size(side, side), radius)
    val inset = side * 0.055f
    val inner = side - inset * 2f
    val planks = 4
    val gap = side * 0.028f
    val plankH = (inner - gap * (planks - 1)) / planks
    for (i in 0 until planks) {
        val py = y + inset + i * (plankH + gap)
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFFF0D7A6), Color(0xFFD7A45C), Color(0xFFC48B42)),
                startY = py,
                endY = py + plankH,
            ),
            Offset(x + inset, py),
            Size(inner, plankH),
        )
        drawLine(
            Color(0xFF8A5A30).copy(alpha = 0.35f),
            Offset(x + inset + 3f, py + plankH * 0.62f),
            Offset(x + side - inset - 3f, py + plankH * 0.62f),
            1.4f,
        )
    }
    val strapW = side * 0.13f
    fun strap(sx: Float) {
        drawRect(Color(0xFF5C3A1C), Offset(sx - 1.2f, y + inset - 1f), Size(strapW + 2.4f, inner + 2f))
        drawRect(
            Brush.horizontalGradient(
                listOf(Color(0xFFE7C48A), Color(0xFFC48A44), Color(0xFF8A5528)),
                startX = sx,
                endX = sx + strapW,
            ),
            Offset(sx, y + inset),
            Size(strapW, inner),
        )
    }
    strap(x + side * 0.16f)
    strap(x + side * 0.71f)
    val boltR = side * 0.038f
    val bolts = floatArrayOf(
        side * 0.2f, side * 0.16f,
        side * 0.8f, side * 0.16f,
        side * 0.2f, side * 0.84f,
        side * 0.8f, side * 0.84f,
        side * 0.225f, side * 0.4f,
        side * 0.225f, side * 0.62f,
        side * 0.775f, side * 0.4f,
        side * 0.775f, side * 0.62f,
    )
    var i = 0
    while (i < bolts.size) {
        drawBolt(Offset(x + bolts[i], y + bolts[i + 1]), boltR)
        i += 2
    }
    drawRoundRect(
        Color(0xFF2E1C0E),
        Offset(x, y),
        Size(side, side),
        radius,
        style = Stroke(width = side * 0.045f, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawBolt(c: Offset, r: Float) {
    drawCircle(Color(0xFF3E444A), r, c)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFF7F8F8), Color(0xFFC5CCD1), Color(0xFF7A838A)),
            center = c - Offset(r * 0.28f, r * 0.32f),
            radius = r * 1.2f,
        ),
        r * 0.78f,
        c,
    )
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.22f, c - Offset(r * 0.22f, r * 0.24f))
}

private fun DrawScope.drawGlossyBall(
    x: Float,
    y: Float,
    r: Float,
    angle: Float,
    groundY: Float?,
    squashX: Float = 1f,
    squashY: Float = 1f,
    face: BallFace = editorFace,
    alpha: Float = 1f,
) {
    val sx = squashX.coerceIn(0.62f, 1.48f)
    val sy = squashY.coerceIn(0.62f, 1.42f)
    if (groundY != null) {
        val gap = (groundY - (y + r)).coerceAtLeast(0f)
        val k = (1f - gap / 360f).coerceIn(0f, 1f)
        if (k > 0.02f) {
            val w = r * 1.85f * sx * (0.45f + 0.55f * k)
            val h = r * 0.42f * (0.5f + 0.5f * k)
            drawOval(
                Color(0xFF1C3314).copy(alpha = 0.28f * k),
                Offset(x - w / 2f, groundY - h * 0.45f),
                Size(w, h),
            )
        }
    }
    val sink = ((1f - sy) * r * 0.62f).coerceAtLeast(0f)
    val cy = y + sink
    val extent = r * 1.9f
    withBlink(alpha, Rect(x - extent, cy - extent, x + extent, cy + extent)) {
        withTransform({
            scale(sx, sy, pivot = Offset(x, cy))
        }) {
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFF8E8E), Color(0xFFFF3B3B), Color(0xFFE2182C), Color(0xFFB01020)),
                    center = Offset(x - r * 0.36f, cy - r * 0.42f),
                    radius = r * 1.75f,
                ),
                r,
                Offset(x, cy),
            )
            rotate(Math.toDegrees(angle.toDouble()).toFloat(), Offset(x, cy)) {
                drawArc(
                    Color(0xFF9F1230).copy(alpha = 0.45f),
                    startAngle = 28f,
                    sweepAngle = 48f,
                    useCenter = false,
                    topLeft = Offset(x - r * 0.78f, cy - r * 0.78f),
                    size = Size(r * 1.56f, r * 1.56f),
                    style = Stroke(width = r * 0.11f, cap = StrokeCap.Round),
                )
            }
            drawOval(
                Color.White.copy(alpha = 0.9f),
                Offset(x - r * 0.58f, cy - r * 0.72f),
                Size(r * 0.42f, r * 0.24f),
            )
            drawBallFace(x, cy, r, face)
            drawCircle(
                Color(0xFF3A1218),
                r,
                Offset(x, cy),
                style = Stroke(width = r * 0.1f),
            )
        }
    }
}

/** Пока мяч неуязвим, классический рисунок мигает тем же ритмом, что и спрайт скина. */
private inline fun DrawScope.withBlink(alpha: Float, bounds: Rect, block: DrawScope.() -> Unit) {
    if (alpha >= 0.99f) {
        block()
        return
    }
    val paint = Paint().apply { this.alpha = alpha.coerceIn(0f, 1f) }
    drawContext.canvas.saveLayer(bounds, paint)
    try {
        block()
    } finally {
        drawContext.canvas.restore()
    }
}

private fun DrawScope.drawBallFace(x: Float, cy: Float, r: Float, face: BallFace) {
    if (face.mood == BallMood.DEAD) {
        val eyeY = cy - r * 0.02f
        for (side in intArrayOf(-1, 1)) {
            val cx = x + side * r * 0.28f
            val s = r * 0.13f
            drawLine(Color(0xFF3A1218), Offset(cx - s, eyeY - s), Offset(cx + s, eyeY + s), r * 0.07f, StrokeCap.Round)
            drawLine(Color(0xFF3A1218), Offset(cx + s, eyeY - s), Offset(cx - s, eyeY + s), r * 0.07f, StrokeCap.Round)
        }
        drawArc(
            Color(0xFF3A1218),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(x - r * 0.22f, cy + r * 0.12f),
            size = Size(r * 0.44f, r * 0.28f),
            style = Stroke(width = r * 0.07f, cap = StrokeCap.Round),
        )
        return
    }
    val widen = when (face.mood) {
        BallMood.SCARED -> 1.14f
        BallMood.SURPRISED -> 1.08f
        else -> 1f
    }
    val eyeHalfW = r * 0.23f * widen
    val openH = r * 0.27f * if (face.mood == BallMood.SURPRISED) 1.08f else 1f
    val eyeHalfH = openH * (1f - 0.9f * face.blink)
    val eyeY = cy - r * 0.05f
    val eyeDx = r * 0.3f
    val pupilK = when (face.mood) {
        BallMood.SCARED -> 0.58f
        BallMood.SURPRISED -> 0.82f
        else -> 1f
    }
    for (side in intArrayOf(-1, 1)) {
        val ex = x + side * eyeDx
        drawOval(Color.White, Offset(ex - eyeHalfW, eyeY - eyeHalfH), Size(eyeHalfW * 2f, eyeHalfH * 2f))
        drawOval(
            Color(0xFF2A2118),
            Offset(ex - eyeHalfW, eyeY - eyeHalfH),
            Size(eyeHalfW * 2f, eyeHalfH * 2f),
            style = Stroke(width = r * 0.045f),
        )
        if (face.blink < 0.82f) {
            val px = ex + face.lookX * eyeHalfW * 0.52f
            val py = eyeY + face.lookY * eyeHalfH * 0.48f
            val pr = r * 0.105f * pupilK
            drawCircle(Color(0xFF1A120C), pr, Offset(px, py))
            drawCircle(Color.White, pr * 0.38f, Offset(px - pr * 0.32f, py - pr * 0.36f))
        }
        if (face.mood == BallMood.SCARED) {
            val outer = Offset(ex + side * eyeHalfW * 1.05f, eyeY - eyeHalfH - r * 0.08f)
            val inner = Offset(ex - side * eyeHalfW * 0.15f, eyeY - eyeHalfH + r * 0.02f)
            drawLine(Color(0xFF4A1520), outer, inner, r * 0.07f, StrokeCap.Round)
        }
    }
    when (face.mood) {
        BallMood.SURPRISED -> {
            drawOval(Color(0xFF4A1520), Offset(x - r * 0.13f, cy + r * 0.16f), Size(r * 0.26f, r * 0.3f))
            drawOval(Color(0xFF2A0A10), Offset(x - r * 0.07f, cy + r * 0.24f), Size(r * 0.14f, r * 0.14f))
        }
        BallMood.SCARED -> {
            drawOval(Color(0xFF3A1218), Offset(x - r * 0.16f, cy + r * 0.16f), Size(r * 0.32f, r * 0.26f))
            drawOval(Color.White.copy(alpha = 0.85f), Offset(x - r * 0.1f, cy + r * 0.18f), Size(r * 0.2f, r * 0.07f))
        }
        else -> {
            val smile = if (abs(face.lookX) > 0.45f) 1.12f else 1f
            drawArc(
                Color(0xFF5A1020),
                startAngle = 18f,
                sweepAngle = 144f,
                useCenter = false,
                topLeft = Offset(x - r * 0.26f * smile, cy + r * 0.08f),
                size = Size(r * 0.52f * smile, r * 0.36f),
                style = Stroke(width = r * 0.075f, cap = StrokeCap.Round),
            )
        }
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
        drawCloud(i * span / 3f - shift, y + (i % 2) * 8f, 110f * scale, alpha)
    }
}

private fun DrawScope.drawCloud(x: Float, y: Float, w: Float, alpha: Float) {
    val shade = Color(0xFFD5ECF6).copy(alpha = alpha * 0.95f)
    val top = Color.White.copy(alpha = alpha)
    drawCircle(shade, w * 0.22f, Offset(x, y + w * 0.05f))
    drawCircle(shade, w * 0.34f, Offset(x + w * 0.28f, y + w * 0.02f))
    drawCircle(shade, w * 0.2f, Offset(x + w * 0.54f, y + w * 0.06f))
    drawCircle(top, w * 0.22f, Offset(x, y))
    drawCircle(top, w * 0.36f, Offset(x + w * 0.26f, y - w * 0.08f))
    drawCircle(top, w * 0.2f, Offset(x + w * 0.52f, y + w * 0.01f))
    drawCircle(top, w * 0.14f, Offset(x + w * 0.12f, y + w * 0.04f))
}

private val hillPath = Path()
private val lakePath = Path()

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
    val path = hillPath
    path.reset()
    var x = -step * 2f - phase
    path.moveTo(x, size.height + 8f)
    path.lineTo(x, baseline)
    var index = 0
    while (x < size.width + step) {
        val next = x + step
        val hump = if (index % 2 == 0) amp else amp * 0.62f
        path.quadraticTo(x + step * 0.5f, baseline - hump, next, baseline)
        x = next
        index++
    }
    path.lineTo(x, size.height + 8f)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawBackdropLake(camX: Float, worldToScreen: Float, baseline: Float) {
    val span = size.width * 2.5f
    val shift = wrapShift(camX * worldToScreen * 0.18f, span)
    val lakeW = size.width * 0.7f
    for (i in -1..2) {
        val x = i * span - shift + size.width * 0.15f
        val path = lakePath
        path.reset()
        path.moveTo(x, baseline + 10f)
        path.cubicTo(
            x + lakeW * 0.28f,
            baseline - 16f,
            x + lakeW * 0.62f,
            baseline + 20f,
            x + lakeW,
            baseline + 2f,
        )
        path.lineTo(x + lakeW, baseline + 48f)
        path.lineTo(x, baseline + 48f)
        path.close()
        drawPath(path, Color(0xFF7ED3E4).copy(alpha = 0.92f))
        drawPath(path, Color(0xFFE5FBFF).copy(alpha = 0.8f), style = Stroke(2.5f))
        drawReed(x + lakeW * 0.2f, baseline + 6f, 36f, Color(0xFF3E9A44))
        drawReed(x + lakeW * 0.32f, baseline + 4f, 50f, Color(0xFF63C44E))
        drawReed(x + lakeW * 0.78f, baseline + 8f, 42f, Color(0xFF2E8A3C))
    }
}

private fun DrawScope.drawReed(x: Float, base: Float, h: Float, color: Color) {
    val lean = if ((x.toInt() and 1) == 0) -0.16f else 0.12f
    val top = Offset(x + h * lean, base - h)
    drawLine(color.copy(alpha = 0.35f), Offset(x + 1.5f, base), top + Offset(1.5f, 0f), 5f, StrokeCap.Round)
    drawLine(color, Offset(x, base), top, 3f, StrokeCap.Round)
    val leaf = reedPath
    leaf.reset()
    val y = base - h * 0.42f
    leaf.moveTo(x, y)
    leaf.quadraticTo(x + h * 0.42f, y - h * 0.16f, top.x, base - h * 0.75f)
    leaf.quadraticTo(x + h * 0.1f, y - h * 0.02f, x, y)
    leaf.close()
    drawPath(leaf, color)
}

private fun DrawScope.drawStoryTree(x: Float, ground: Float, h: Float, kind: Int) {
    if (h < 8f) return
    when (Math.floorMod(kind, 3)) {
        0 -> drawPine(x, ground, h)
        1 -> drawRoundTree(x, ground, h * 0.92f)
        else -> drawCypress(x, ground, h * 1.12f)
    }
}

private fun DrawScope.drawPine(x: Float, ground: Float, h: Float) {
    val trunk = h * 0.08f
    drawRoundRect(
        Color(0xFF8A5A32),
        Offset(x - trunk / 2f, ground - h * 0.22f),
        Size(trunk, h * 0.24f),
        CornerRadius(2f, 2f),
    )
    fun tier(topFrac: Float, widthFrac: Float, color: Color) {
        val path = scratchPath
        path.reset()
        val top = ground - h * topFrac
        val tw = h * widthFrac
        val th = h * 0.26f
        path.moveTo(x, top)
        path.lineTo(x - tw / 2f, top + th)
        path.lineTo(x + tw / 2f, top + th)
        path.close()
        drawPath(path, color)
        drawPath(path, Color(0xFF2C6818), style = Stroke(2.2f, join = StrokeJoin.Round))
    }
    tier(0.58f, 0.62f, Color(0xFF6FCB3E))
    tier(0.78f, 0.46f, Color(0xFF8FE052))
    tier(0.96f, 0.3f, Color(0xFFC6F56A))
}

private fun DrawScope.drawRoundTree(x: Float, ground: Float, h: Float) {
    val trunk = h * 0.1f
    drawRoundRect(
        Color(0xFF8A5A32),
        Offset(x - trunk / 2f, ground - h * 0.28f),
        Size(trunk, h * 0.3f),
        CornerRadius(2f, 2f),
    )
    fun blob(ox: Float, oy: Float, r: Float, color: Color) {
        drawCircle(Color(0xFF2C6818), r + 2.2f, Offset(x + ox, ground + oy))
        drawCircle(color, r, Offset(x + ox, ground + oy))
        drawCircle(Color(0xFFD9F98A).copy(alpha = 0.85f), r * 0.28f, Offset(x + ox - r * 0.28f, ground + oy - r * 0.32f))
    }
    blob(-h * 0.12f, -h * 0.32f, h * 0.2f, Color(0xFF7ED84A))
    blob(h * 0.14f, -h * 0.3f, h * 0.17f, Color(0xFF67C83C))
    blob(0f, -h * 0.48f, h * 0.22f, Color(0xFF98E85A))
}

private fun DrawScope.drawCypress(x: Float, ground: Float, h: Float) {
    val trunk = h * 0.06f
    drawRoundRect(
        Color(0xFF8A5A32),
        Offset(x - trunk / 2f, ground - h * 0.2f),
        Size(trunk, h * 0.2f),
        CornerRadius(2f, 2f),
    )
    val body = scratchPath
    body.reset()
    body.moveTo(x, ground - h)
    body.quadraticTo(x + h * 0.16f, ground - h * 0.55f, x + h * 0.08f, ground - h * 0.08f)
    body.lineTo(x - h * 0.08f, ground - h * 0.08f)
    body.quadraticTo(x - h * 0.16f, ground - h * 0.55f, x, ground - h)
    body.close()
    drawPath(body, Color(0xFF8FE052))
    drawPath(body, Color(0xFF2C6818), style = Stroke(2f, join = StrokeJoin.Round))
    drawOval(
        Color(0xFFD4F77A).copy(alpha = 0.8f),
        Offset(x - h * 0.05f, ground - h * 0.72f),
        Size(h * 0.06f, h * 0.16f),
    )
}

private fun DrawScope.drawFence(x: Float, ground: Float) {
    val posts = 5
    val gap = 16f
    val color = Color(0xFFC48A44)
    val edge = Color(0xFF6B4420)
    for (i in 0 until posts) {
        val px = x + i * gap
        drawRoundRect(edge, Offset(px - 0.8f, ground - 20f), Size(5.2f, 20f), CornerRadius(1.5f, 1.5f))
        drawRoundRect(color, Offset(px, ground - 19f), Size(3.6f, 18f), CornerRadius(1.2f, 1.2f))
    }
    val end = x + (posts - 1) * gap
    drawLine(edge, Offset(x, ground - 14f), Offset(end, ground - 14f), 3.4f, StrokeCap.Round)
    drawLine(color, Offset(x, ground - 14.6f), Offset(end, ground - 14.6f), 2f, StrokeCap.Round)
    drawLine(edge, Offset(x, ground - 7f), Offset(end, ground - 7f), 3.4f, StrokeCap.Round)
    drawLine(color, Offset(x, ground - 7.6f), Offset(end, ground - 7.6f), 2f, StrokeCap.Round)
}

private fun DrawScope.drawBird(x: Float, y: Float, s: Float) {
    val color = Color(0xFF3A4650).copy(alpha = 0.4f)
    drawArc(
        color,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(x, y),
        size = Size(s, s * 0.7f),
        style = Stroke(width = 2.2f, cap = StrokeCap.Round),
    )
    drawArc(
        color,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(x + s * 0.7f, y),
        size = Size(s, s * 0.7f),
        style = Stroke(width = 2.2f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawConcreteBlock(box: RedBallAabb, cap: Boolean) {
    val w = box.r - box.l
    val h = box.b - box.t
    if (w <= 1f || h <= 1f) return
    val radius = CornerRadius(min(12f, w * 0.08f), min(12f, h * 0.08f))
    drawRoundRect(
        Brush.verticalGradient(
            listOf(Color(0xFFE4E8EB), Color(0xFFC5CCD2), Color(0xFF9AA3AB), Color(0xFF727B83)),
            startY = box.t,
            endY = box.b,
        ),
        Offset(box.l, box.t),
        Size(w, h),
        radius,
    )
    val seed = box.l.toInt() * 3 + box.t.toInt()
    var n = 0
    var px = box.l + 14f
    while (px < box.r - 10f && n < 16) {
        val py = box.t + 18f + unitHash(seed + n, px.toInt()) * (h - 28f).coerceAtLeast(4f)
        if (py < box.b - 8f) {
            drawCircle(Color(0xFF4E565C).copy(alpha = 0.28f), 2.2f, Offset(px, py))
        }
        px += 28f
        n++
    }
    if (w > 36f) {
        drawLine(
            Color(0xFF4A5258).copy(alpha = 0.45f),
            Offset(box.l + 8f, box.t + h * 0.5f),
            Offset(box.r - 8f, box.t + h * 0.5f),
            2.4f,
        )
    }
    if (cap) {
        drawRoundRect(
            Color.White.copy(alpha = 0.38f),
            Offset(box.l + 5f, box.t + 4f),
            Size((w - 10f).coerceAtLeast(1f), 6f),
            CornerRadius(3f, 3f),
        )
    }
    drawRoundRect(
        Color(0xFF2E343A),
        Offset(box.l, box.t),
        Size(w, h),
        radius,
        style = Stroke(width = 4.2f, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawRockBoulder(cx: Float, cy: Float, side: Float) {
    if (side < 6f) return
    val half = side / 2f
    drawOval(
        Color(0xFF2A3034).copy(alpha = 0.22f),
        Offset(cx - side * 0.38f, cy + side * 0.38f),
        Size(side * 0.76f, side * 0.16f),
    )
    val radius = CornerRadius(side * 0.32f, side * 0.3f)
    drawRoundRect(
        Brush.radialGradient(
            listOf(Color(0xFFF2F4F5), Color(0xFFC5CCD2), Color(0xFF8E979F), Color(0xFF5C656D)),
            center = Offset(cx - side * 0.16f, cy - side * 0.18f),
            radius = side * 0.9f,
        ),
        Offset(cx - half, cy - half),
        Size(side, side),
        radius,
    )
    drawLine(
        Color(0xFF3E464C).copy(alpha = 0.75f),
        Offset(cx - side * 0.08f, cy - side * 0.02f),
        Offset(cx + side * 0.18f, cy + side * 0.2f),
        side * 0.035f,
        StrokeCap.Round,
    )
    drawLine(
        Color(0xFF3E464C).copy(alpha = 0.55f),
        Offset(cx + side * 0.02f, cy + side * 0.05f),
        Offset(cx + side * 0.16f, cy + side * 0.22f),
        side * 0.022f,
        StrokeCap.Round,
    )
    drawOval(
        Color.White.copy(alpha = 0.7f),
        Offset(cx - side * 0.28f, cy - side * 0.3f),
        Size(side * 0.26f, side * 0.14f),
    )
    drawRoundRect(
        Color(0xFF2A3036),
        Offset(cx - half, cy - half),
        Size(side, side),
        radius,
        style = Stroke(width = side * 0.055f, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawLadder(box: RedBallAabb) {
    val w = box.r - box.l
    val h = box.b - box.t
    if (w < 4f || h < 4f) return
    val rail = (w * 0.22f).coerceIn(5f, 14f)
    fun railAt(x: Float) {
        drawRoundRect(Color(0xFF4A2E16), Offset(x, box.t), Size(rail, h), CornerRadius(rail / 2f, rail / 2f))
        drawRoundRect(
            Brush.horizontalGradient(
                listOf(Color(0xFFF3D7A8), Color(0xFFD09A56), Color(0xFF8E5A2C)),
                startX = x,
                endX = x + rail,
            ),
            Offset(x + 1.4f, box.t + 1.4f),
            Size((rail - 2.8f).coerceAtLeast(1f), (h - 2.8f).coerceAtLeast(1f)),
            CornerRadius(rail / 2f, rail / 2f),
        )
    }
    railAt(box.l)
    railAt(box.r - rail)
    val rungH = (w * 0.11f).coerceIn(4f, 9f)
    var y = box.t + h * 0.12f
    val step = (h * 0.18f).coerceIn(16f, 34f)
    while (y < box.b - 8f) {
        val left = box.l + rail * 0.45f
        val rw = (w - rail * 0.9f).coerceAtLeast(4f)
        drawRoundRect(Color(0xFF5C3416), Offset(left, y), Size(rw, rungH), CornerRadius(3f, 3f))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFF6E0B4), Color(0xFFC4843C)), startY = y, endY = y + rungH),
            Offset(left, y),
            Size(rw, rungH * 0.72f),
            CornerRadius(3f, 3f),
        )
        y += step
    }
}

private fun DrawScope.drawCollectStar(cx: Float, cy: Float, radius: Float, time: Float, stuck: Boolean) {
    val bob = sin(time * 3.2f + cx * 0.02f) * if (stuck) 1.5f else 4f
    val y = cy + bob
    val pulse = 0.92f + 0.08f * sin(time * 5f + cy)
    val r = radius * 1.28f * pulse
    drawCircle(Color(0xFFFFE9A0).copy(alpha = 0.38f), r * 1.45f, Offset(cx, y))
    val path = starPath(cx, y, r, r * 0.46f)
    drawPath(
        path,
        Brush.radialGradient(
            listOf(Color(0xFFFFFBE4), Color(0xFFFFD54A), Color(0xFFF0A31A), Color(0xFFE08A10)),
            center = Offset(cx - r * 0.18f, y - r * 0.22f),
            radius = r * 1.5f,
        ),
    )
    drawPath(path, Color(0xFF8A5A10), style = Stroke(width = r * 0.09f, join = StrokeJoin.Round))
    drawCircle(Color.White.copy(alpha = 0.9f), r * 0.16f, Offset(cx - r * 0.18f, y - r * 0.22f))
    val tw = 0.45f + 0.55f * sin(time * 4.2f + cx)
    val sx = cx + r * 0.85f
    val sy = y - r * 0.75f
    drawLine(Color.White.copy(alpha = tw), Offset(sx - 5f, sy), Offset(sx + 5f, sy), 1.8f, StrokeCap.Round)
    drawLine(Color.White.copy(alpha = tw), Offset(sx, sy - 5f), Offset(sx, sy + 5f), 1.8f, StrokeCap.Round)
}

private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float): Path {
    val path = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val radius = if (i % 2 == 0) outer else inner
        val ang = (-PI / 2.0 + i * PI / points).toFloat()
        val px = cx + cos(ang) * radius
        val py = cy + sin(ang) * radius
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    return path
}

private fun DrawScope.drawMagnet(cx: Float, cy: Float, time: Float) {
    val y = cy + sin(time * 2.4f + cx) * 3f
    drawCircle(Color(0xFFFFD0D0).copy(alpha = 0.35f), 28f, Offset(cx, y - 2f))
    val shoe = scratchPath
    shoe.reset()
    shoe.moveTo(cx - 16f, y + 16f)
    shoe.lineTo(cx - 16f, y - 2f)
    shoe.quadraticTo(cx - 16f, y - 22f, cx, y - 22f)
    shoe.quadraticTo(cx + 16f, y - 22f, cx + 16f, y - 2f)
    shoe.lineTo(cx + 16f, y + 16f)
    shoe.lineTo(cx + 6f, y + 16f)
    shoe.lineTo(cx + 6f, y - 2f)
    shoe.quadraticTo(cx + 6f, y - 10f, cx, y - 10f)
    shoe.quadraticTo(cx - 6f, y - 10f, cx - 6f, y - 2f)
    shoe.lineTo(cx - 6f, y + 16f)
    shoe.close()
    drawPath(
        shoe,
        Brush.verticalGradient(
            listOf(Color(0xFFFF6A6A), Color(0xFFE11D2E), Color(0xFFB01020)),
            startY = y - 22f,
            endY = y + 8f,
        ),
    )
    drawPath(shoe, Color(0xFF6E1018), style = Stroke(2.4f, join = StrokeJoin.Round))
    drawRoundRect(Color(0xFF3E444A), Offset(cx - 18f, y + 8f), Size(14f, 12f), CornerRadius(2f, 2f))
    drawRoundRect(Color(0xFF3E444A), Offset(cx + 4f, y + 8f), Size(14f, 12f), CornerRadius(2f, 2f))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFF7F8F8), Color(0xFFC5CCD1), Color(0xFF8A939A)), startY = y + 8f, endY = y + 20f),
        Offset(cx - 16f, y + 9f),
        Size(10f, 10f),
        CornerRadius(2f, 2f),
    )
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFF7F8F8), Color(0xFFC5CCD1), Color(0xFF8A939A)), startY = y + 8f, endY = y + 20f),
        Offset(cx + 6f, y + 9f),
        Size(10f, 10f),
        CornerRadius(2f, 2f),
    )
    drawLine(Color.White.copy(alpha = 0.75f), Offset(cx - 10f, y - 14f), Offset(cx - 2f, y - 16f), 2.4f, StrokeCap.Round)
}

internal fun DrawScope.drawMagnetAura(x: Float, y: Float, time: Float) {
    val pulse = 0.92f + 0.08f * sin(time * 6f)
    val radius = RedBallSim.MAGNET_RADIUS * pulse
    drawCircle(Color(0xFFFF6B6B).copy(alpha = 0.1f), radius, Offset(x, y))
    drawCircle(
        Color(0xFFFF8A8A).copy(alpha = 0.55f),
        radius,
        Offset(x, y),
        style = Stroke(width = 3f),
    )
}

private val scratchPath = Path()
private val scratchPathB = Path()

private fun DrawScope.drawAngrySquare(cx: Float, cy: Float, side: Float, angle: Float) {
    if (side < 8f) return
    val half = side / 2f
    drawOval(
        Color(0xFF1A1E22).copy(alpha = 0.22f),
        Offset(cx - half * 0.85f, cy + half * 0.72f),
        Size(side * 0.85f, side * 0.18f),
    )
    val deg = Math.toDegrees(angle.toDouble()).toFloat()
    withTransform({ rotate(deg, Offset(cx, cy)) }) {
        val radius = CornerRadius(side * 0.2f, side * 0.2f)
        drawRoundRect(
            Brush.linearGradient(
                listOf(Color(0xFF6A7078), Color(0xFF3A3E44), Color(0xFF23262B)),
                start = Offset(cx - half, cy - half),
                end = Offset(cx + half, cy + half),
            ),
            Offset(cx - half, cy - half),
            Size(side, side),
            radius,
        )
        drawOval(
            Color.White.copy(alpha = 0.18f),
            Offset(cx - half * 0.55f, cy - half * 0.72f),
            Size(side * 0.28f, side * 0.12f),
        )
        val eyeW = side * 0.15f
        val eyeH = side * 0.12f
        val eyeY = cy - side * 0.08f
        fun eye(ex: Float, look: Float) {
            drawOval(Color(0xFFFFB703), Offset(ex - eyeW, eyeY - eyeH), Size(eyeW * 2f, eyeH * 2f))
            drawOval(
                Color(0xFF1A1208),
                Offset(ex - eyeW, eyeY - eyeH),
                Size(eyeW * 2f, eyeH * 2f),
                style = Stroke(width = side * 0.035f),
            )
            val pr = eyeH * 0.55f
            val px = ex + look * eyeW * 0.25f
            drawCircle(Color(0xFF1A1208), pr, Offset(px, eyeY + eyeH * 0.08f))
            drawCircle(Color.White, pr * 0.35f, Offset(px - pr * 0.35f, eyeY - eyeH * 0.15f))
        }
        eye(cx - side * 0.22f, -0.2f)
        eye(cx + side * 0.22f, 0.15f)
        drawLine(
            Color(0xFF14171B),
            Offset(cx - side * 0.4f, cy - side * 0.32f),
            Offset(cx - side * 0.08f, cy - side * 0.2f),
            side * 0.055f,
            StrokeCap.Round,
        )
        drawLine(
            Color(0xFF14171B),
            Offset(cx + side * 0.4f, cy - side * 0.32f),
            Offset(cx + side * 0.08f, cy - side * 0.2f),
            side * 0.055f,
            StrokeCap.Round,
        )
        val mouthW = side * 0.5f
        val mouthH = side * 0.28f
        val mouthTop = cy + side * 0.06f
        drawRoundRect(
            Color(0xFF14171B),
            Offset(cx - mouthW / 2f, mouthTop),
            Size(mouthW, mouthH),
            CornerRadius(side * 0.05f, side * 0.05f),
        )
        val teeth = 4
        val tw = mouthW / teeth
        val tooth = scratchPath
        for (i in 0 until teeth) {
            val tx = cx - mouthW / 2f + i * tw
            tooth.reset()
            tooth.moveTo(tx + tw * 0.12f, mouthTop + 1.5f)
            tooth.lineTo(tx + tw * 0.88f, mouthTop + 1.5f)
            tooth.lineTo(tx + tw * 0.5f, mouthTop + mouthH * 0.48f)
            tooth.close()
            drawPath(tooth, Color(0xFFFFFBF5))
            tooth.reset()
            tooth.moveTo(tx + tw * 0.18f, mouthTop + mouthH - 1.5f)
            tooth.lineTo(tx + tw * 0.82f, mouthTop + mouthH - 1.5f)
            tooth.lineTo(tx + tw * 0.5f, mouthTop + mouthH * 0.55f)
            tooth.close()
            drawPath(tooth, Color(0xFFF4F1EA))
        }
        drawRoundRect(
            Color(0xFF101216),
            Offset(cx - half, cy - half),
            Size(side, side),
            radius,
            style = Stroke(width = side * 0.07f, join = StrokeJoin.Round),
        )
    }
}

internal fun DrawScope.drawParticle(particle: RedBallParticle) {
    val t = (particle.life / particle.maxLife).coerceIn(0f, 1f)
    val c = Offset(particle.x, particle.y)
    val rad = particle.size * (0.55f + 0.55f * t)
    when (particle.kind) {
        RedBallParticle.BOOM -> {
            val shard = ((particle.size * 100f).toInt() and 1) == 0
            if (shard) {
                val path = scratchPathB
                path.reset()
                path.moveTo(c.x + rad, c.y)
                path.lineTo(c.x - rad * 0.45f, c.y - rad * 0.6f)
                path.lineTo(c.x - rad * 0.15f, c.y + rad * 0.55f)
                path.close()
                drawPath(path, Color(0xFF2E3238).copy(alpha = t))
                drawPath(path, Color(0xFFFFC14A).copy(alpha = t), style = Stroke(1.6f))
            } else {
                drawCircle(Color(0xFF7A2E0A).copy(alpha = t), rad, c)
                drawCircle(Color(0xFFFF8A1E).copy(alpha = t), rad * 0.78f, c)
                drawCircle(Color(0xFFFFE08A).copy(alpha = t), rad * 0.38f, c)
            }
        }
        RedBallParticle.SPARK -> {
            val path = starPath(c.x, c.y, rad * 1.3f, rad * 0.55f)
            drawPath(path, Color(0xFFFFE08A).copy(alpha = t))
            drawPath(path, Color(0xFF8A5A10).copy(alpha = t), style = Stroke(1.2f))
        }
        RedBallParticle.PUFF -> {
            drawOval(
                Color(0xFFE4CBA4).copy(alpha = 0.82f * t),
                Offset(c.x - rad * 1.45f, c.y - rad * 0.5f),
                Size(rad * 2.9f, rad * 1.05f),
            )
            drawOval(
                Color(0xFFFFF8EE).copy(alpha = 0.55f * t),
                Offset(c.x - rad * 0.8f, c.y - rad * 0.28f),
                Size(rad * 1.5f, rad * 0.5f),
            )
        }
        RedBallParticle.SPLASH -> {
            drawOval(
                Color(0xFFD7F7FF).copy(alpha = 0.9f * t),
                Offset(c.x - rad * 0.7f, c.y - rad * 1.15f),
                Size(rad * 1.4f, rad * 2.1f),
            )
            drawCircle(Color(0xFF3AA0C4).copy(alpha = 0.85f * t), rad * 0.45f, c)
        }
        RedBallParticle.BUBBLE -> {
            drawCircle(Color(0xFFEFF9FF).copy(alpha = 0.35f * t), rad, c)
            drawCircle(Color(0xFFF7FCFF).copy(alpha = 0.9f * t), rad, c, style = Stroke(1.6f))
            drawCircle(Color.White.copy(alpha = 0.8f * t), rad * 0.28f, c - Offset(rad * 0.28f, rad * 0.3f))
        }
        else -> drawCircle(Color(0xFF8A6240).copy(alpha = 0.4f * t), rad, c)
    }
}

private val emptyHudHeart = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

internal fun DrawScope.drawHudHeart(filled: Boolean, icon: ImageBitmap? = null) {
    if (icon != null) {
        val side = size
        drawImage(
            icon,
            dstSize = IntSize(side.width.toInt().coerceAtLeast(1), side.height.toInt().coerceAtLeast(1)),
            alpha = if (filled) 1f else 0.72f,
            colorFilter = if (filled) null else emptyHudHeart,
            filterQuality = FilterQuality.Medium,
        )
        return
    }
    val w = size.width
    val h = size.height
    val path = heartPath(w, h)
    drawPath(path, Color.White, style = Stroke(width = w * 0.1f, join = StrokeJoin.Round))
    if (filled) {
        drawPath(
            path,
            Brush.verticalGradient(
                listOf(Color(0xFFFF8B96), Color(0xFFE31B36), Color(0xFFB10E28)),
                startY = h * 0.12f,
                endY = h * 0.92f,
            ),
        )
        drawOval(Color.White.copy(alpha = 0.75f), Offset(w * 0.24f, h * 0.22f), Size(w * 0.16f, h * 0.11f))
    } else {
        drawPath(path, Color(0xFFE4E8EC))
    }
    drawPath(path, Color.White, style = Stroke(width = w * 0.045f, join = StrokeJoin.Round))
}

internal fun DrawScope.drawHudStarMedal(magnetOn: Boolean, icon: ImageBitmap? = null) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension / 2f
    if (icon != null) {
        drawImage(
            icon,
            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Medium,
        )
        if (magnetOn) {
            drawCircle(Color(0xFF7DD3FC), r * 0.92f, c, style = Stroke(width = r * 0.12f))
        }
        return
    }
    drawCircle(Color(0xFF8A5A12), r, c)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFF6C4), Color(0xFFFFD24A), Color(0xFFE8A317)),
            center = c - Offset(r * 0.22f, r * 0.24f),
            radius = r,
        ),
        r * 0.84f,
        c,
    )
    val star = starPath(c.x, c.y + r * 0.02f, r * 0.58f, r * 0.26f)
    drawPath(star, Color(0xFFFFF8DC))
    drawPath(star, Color(0xFFC47A10), style = Stroke(width = r * 0.07f, join = StrokeJoin.Round))
    if (magnetOn) {
        drawCircle(Color(0xFF7DD3FC), r * 0.92f, c, style = Stroke(width = r * 0.12f))
    }
}

private fun heartPath(w: Float, h: Float): Path {
    val path = Path()
    path.moveTo(w * 0.5f, h * 0.88f)
    path.cubicTo(w * 0.02f, h * 0.52f, w * 0.12f, h * 0.14f, w * 0.5f, h * 0.34f)
    path.cubicTo(w * 0.88f, h * 0.14f, w * 0.98f, h * 0.52f, w * 0.5f, h * 0.88f)
    path.close()
    return path
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
