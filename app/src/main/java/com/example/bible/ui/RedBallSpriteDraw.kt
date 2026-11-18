package com.example.bible.ui

import android.graphics.Matrix as AndroidMatrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.bible.games.RedBallAabb
import com.example.bible.games.RedBallDraft
import com.example.bible.games.RedBallPiece
import com.example.bible.games.RedBallPieceKind
import com.example.bible.games.RedBallPortalRole
import com.example.bible.games.RedBallScene
import com.example.bible.games.RedBallSim
import com.example.bible.games.RedBallSlope
import com.example.bible.games.skins.RedBallSkinBitmaps
import com.example.bible.games.skins.RedBallSkinSlot
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SOIL_TILE = 128f
private const val CONCRETE_TILE = 112f
private const val GRASS_TILE_W = 144f
private const val GRASS_CAP_FRACTION = 0.25f
private const val GRASS_LIP = 26f
private const val GRASS_EDGE_H = 34f
private const val WATER_BODY_TILE = 160f
private const val WATER_SURFACE_H = 34f
private const val WATER_SURFACE_W = WATER_SURFACE_H * 4f
private const val WATER_BODY_ALPHA = 0.85f
/** Мировые единицы в секунду: полоска волн уезжает вбок, тело воды стоит. */
private const val WATER_SCROLL = 26f
/** Сколько держать сжатую пружину после того, как мяч уже отлип. */
private const val SPRING_SQUASH_HOLD = 0.13f

/** Крупный квадрат рисуется рогатым боссом. Обычный враг редактора — 80. */
private const val BOSS_SIDE = 112f

private val asleepFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToSaturation(0.15f) },
)

private enum class SpritePortalMood { OPEN, EXIT, ASLEEP, LOST }

/**
 * Кадр игры в спрайтовом скине. Небо — в пикселях экрана, мир — в координатах уровня.
 * Желе, кувырок врага, искры взрыва и цвета порталов остаются.
 * Шипы, пружина и вода ямы — тоже спрайты главы. Классика их не вызывает.
 */
internal fun DrawScope.drawSpritePlay(
    sim: RedBallSim,
    camX: Float,
    camY: Float,
    scale: Float,
    liftPx: Float,
    skin: RedBallSkinBitmaps,
) {
    val scene = sim.scene
    drawSpriteSky(camX, scale, liftPx, skin)
    withTransform({
        scale(scale, scale, pivot = Offset.Zero)
        translate(-camX, -camY)
    }) {
        drawSpriteWater(scene, sim.anim, skin)
        val grounds = ArrayList<RedBallAabb>(scene.solids.size)
        for (solid in scene.solids) {
            if (solid.r <= scene.minX + 0.5f || solid.l >= scene.worldW - 0.5f) continue
            grounds += solid
        }
        drawSpriteTerrain(grounds, scene.slopes, scene.concrete, skin)
        for (ladder in scene.ladders) drawSpriteLadder(ladder, skin)
        for (spike in scene.spikes) drawSpriteSpikes(spike.x, spike.top, spike.w, spike.h, skin)
        for ((index, spring) in scene.springs.withIndex()) {
            drawSpriteSpring(
                spring.l,
                spring.t,
                spring.r,
                spring.b,
                SpringSquash.showCompressed(sim, index),
                skin,
            )
        }
        for (star in sim.stars) {
            if (!star.taken) drawSpriteStar(star.x, star.y, sim.anim, star.attached, skin)
        }
        for (magnet in sim.magnets) {
            if (!magnet.taken) drawSpriteMagnet(magnet.x, magnet.y, sim.anim, skin)
        }
        for (portal in scene.portals) {
            val mood = when {
                portal.role == RedBallPortalRole.EXIT -> SpritePortalMood.EXIT
                portal.linkId.isBlank() -> SpritePortalMood.ASLEEP
                else -> SpritePortalMood.OPEN
            }
            val box = portal.box
            drawSpritePortal(box.l, box.t, box.r, box.b, sim.anim, mood, skin)
        }
        val door = scene.door
        drawSpriteFlag(door.l, door.t, door.r, door.b, sim.anim, skin)
        for (crate in sim.crates) drawSpriteCrate(crate.x, crate.y, crate.size, skin)
        for (block in sim.blocks) drawSpriteBoulder(block.x, block.y, block.size, skin)
        for (enemy in sim.enemies) {
            if (enemy.alive) drawSpriteEnemy(enemy.x, enemy.y, enemy.size, enemy.angle, skin)
        }
        for (particle in sim.particles) drawParticle(particle)
        if (sim.magnetTime > 0f) drawMagnetAura(sim.ballX, sim.ballY, sim.anim)
        drawSpriteHero(sim, skin)
    }
}

/** Редактор: та же земля и те же спрайты, сетка и рамка выделения остаются снаружи. */
internal fun DrawScope.drawSpriteEditorWorld(
    draft: RedBallDraft,
    skin: RedBallSkinBitmaps,
    portalTime: Float,
    lostPortalIds: Set<Long>,
) {
    val cell = draft.cell
    val grounds = ArrayList<RedBallAabb>()
    val slopes = ArrayList<RedBallSlope>()
    val concrete = ArrayList<RedBallAabb>()
    for (piece in draft.pieces) {
        when (piece.kind) {
            RedBallPieceKind.GROUND -> grounds += piece.spriteBox(cell)
            RedBallPieceKind.SLOPE -> slopes += piece.spriteSlope(cell)
            RedBallPieceKind.CONCRETE -> concrete += piece.spriteBox(cell)
            else -> Unit
        }
    }
    drawSpriteTerrain(grounds, slopes, concrete, skin)
    for (piece in draft.pieces) {
        if (
            piece.kind == RedBallPieceKind.GROUND ||
            piece.kind == RedBallPieceKind.SLOPE ||
            piece.kind == RedBallPieceKind.CONCRETE
        ) {
            continue
        }
        val box = piece.spriteBox(cell)
        when (piece.kind) {
            RedBallPieceKind.LADDER -> drawSpriteLadder(box, skin)
            RedBallPieceKind.SPIKE -> drawSpriteSpikes(
                (box.l + box.r) / 2f,
                box.t,
                box.r - box.l,
                box.b - box.t,
                skin,
            )
            RedBallPieceKind.SPRING -> drawSpriteSpring(box.l, box.t, box.r, box.b, compressed = false, skin)
            RedBallPieceKind.CRATE -> drawSpriteCrate(
                (box.l + box.r) / 2f,
                (box.t + box.b) / 2f,
                min(piece.w, piece.h) * cell,
                skin,
            )
            RedBallPieceKind.STAR -> drawSpriteStar(
                (box.l + box.r) / 2f,
                (box.t + box.b) / 2f,
                0.6f,
                stuck = false,
                skin,
            )
            RedBallPieceKind.MAGNET -> drawSpriteMagnet(
                (box.l + box.r) / 2f,
                (box.t + box.b) / 2f,
                0.4f,
                skin,
            )
            RedBallPieceKind.ENEMY -> drawSpriteEnemy(
                (box.l + box.r) / 2f,
                (box.t + box.b) / 2f,
                min(piece.w, piece.h) * cell,
                angle = 0f,
                skin,
            )
            RedBallPieceKind.DOOR -> drawSpriteFlag(box.l, box.t, box.r, box.b, 0.8f, skin)
            RedBallPieceKind.PORTAL -> {
                val mood = when {
                    piece.role == RedBallPortalRole.EXIT -> SpritePortalMood.EXIT
                    piece.id in lostPortalIds -> SpritePortalMood.LOST
                    piece.linkId.isBlank() -> SpritePortalMood.ASLEEP
                    else -> SpritePortalMood.OPEN
                }
                drawSpritePortal(box.l, box.t, box.r, box.b, portalTime, mood, skin)
            }
            RedBallPieceKind.START -> {
                val x = (box.l + box.r) / 2f
                val y = box.b - RedBallScene.BALL_R
                placeCentered(
                    skin.bitmap(RedBallSkinSlot.HERO_HAPPY),
                    x,
                    y,
                    RedBallScene.BALL_R * 2.06f,
                    RedBallScene.BALL_R * 2.06f,
                )
                drawCircle(
                    Color.White,
                    RedBallScene.BALL_R + 5f,
                    Offset(x, y),
                    style = Stroke(width = 3f),
                )
            }
            else -> Unit
        }
    }
}

/**
 * Небо главы: градиент, облака и два слоя холмов с разным сдвигом.
 * [liftPx] — насколько камера поднялась, в пикселях экрана. Дальние слои едут меньше.
 */
internal fun DrawScope.drawSpriteSky(
    camX: Float,
    worldToScreen: Float,
    liftPx: Float,
    skin: RedBallSkinBitmaps,
) {
    drawSkyFill(skin.bitmap(RedBallSkinSlot.SKY))
    drawSpriteClouds(camX, worldToScreen, liftPx, skin)
    val horizon = size.height * 0.64f
    val farH = size.height * 0.26f
    val nearH = size.height * 0.30f
    val farTop = horizon - farH * 0.78f + liftPx * 0.12f
    val nearTop = horizon - nearH * 0.38f + liftPx * 0.28f
    drawParallaxStrip(
        skin.bitmap(RedBallSkinSlot.HILLS_FAR),
        camX,
        worldToScreen,
        factor = 0.14f,
        top = farTop,
        height = farH,
    )
    drawParallaxStrip(
        skin.bitmap(RedBallSkinSlot.HILLS_NEAR),
        camX,
        worldToScreen,
        factor = 0.32f,
        top = nearTop,
        height = nearH,
    )
    drawHillDecor(camX, worldToScreen, nearTop, nearH, liftPx, skin)
}

private fun DrawScope.drawSpriteTerrain(
    grounds: List<RedBallAabb>,
    slopes: List<RedBallSlope>,
    concrete: List<RedBallAabb>,
    skin: RedBallSkinBitmaps,
) {
    for (ground in grounds) drawGroundSprites(ground, grounds, slopes, skin)
    for (block in concrete) drawConcreteSprite(block, skin)
    for (slope in slopes) drawSlopeSprites(slope, skin)
}

private fun DrawScope.drawGroundSprites(
    box: RedBallAabb,
    solids: List<RedBallAabb>,
    slopes: List<RedBallSlope>,
    skin: RedBallSkinBitmaps,
) {
    val w = box.r - box.l
    val h = box.b - box.t
    if (w < 1f || h < 1f) return
    fillTiled(skin.bitmap(RedBallSkinSlot.SOIL), box.l, box.t, box.r, box.b, SOIL_TILE, SOIL_TILE)
    val spans = grassIntervals(box, solids, slopes)
    if (spans.isEmpty()) return
    val grass = skin.bitmap(RedBallSkinSlot.GRASS_TOP)
    val edge = skin.bitmap(RedBallSkinSlot.GRASS_EDGE)
    val fullH = GRASS_LIP / GRASS_CAP_FRACTION
    val edgeTop = box.t - GRASS_EDGE_H * 0.78f
    for ((a, b) in spans) {
        if (b - a < 18f) continue
        fillTiled(grass, a, box.t, b, box.t + GRASS_LIP, GRASS_TILE_W, fullH, originY = box.t)
        fillTiled(
            edge,
            a,
            edgeTop,
            b,
            edgeTop + GRASS_EDGE_H,
            GRASS_TILE_W,
            GRASS_EDGE_H,
            originY = edgeTop,
        )
    }
}

private fun DrawScope.drawConcreteSprite(box: RedBallAabb, skin: RedBallSkinBitmaps) {
    val w = box.r - box.l
    val h = box.b - box.t
    if (w < 1f || h < 1f) return
    fillTiled(
        skin.bitmap(RedBallSkinSlot.CONCRETE),
        box.l,
        box.t,
        box.r,
        box.b,
        CONCRETE_TILE,
        CONCRETE_TILE,
    )
    drawRect(
        Color(0xFF2A3036),
        Offset(box.l, box.t),
        Size(w, h),
        style = Stroke(width = 3.4f),
    )
}

private fun DrawScope.drawSlopeSprites(slope: RedBallSlope, skin: RedBallSkinBitmaps) {
    val x0 = slope.x0
    val y0 = slope.y0
    val x1 = slope.x1
    val y1 = slope.y1
    val tuck = 5f
    val third = if (y0 <= y1) Offset(x0 - tuck, y1 + tuck) else Offset(x1 + tuck, y0 + tuck)
    val low = if (y0 <= y1) Offset(x1, y1 + tuck) else Offset(x0, y0 + tuck)
    val path = Path().apply {
        moveTo(x0, y0)
        lineTo(x1, y1)
        lineTo(low.x, low.y)
        lineTo(third.x, third.y)
        close()
    }
    val minX = min(min(x0, x1), min(third.x, low.x))
    val maxX = max(max(x0, x1), max(third.x, low.x))
    val minY = min(min(y0, y1), min(third.y, low.y))
    val maxY = max(max(y0, y1), max(third.y, low.y))
    clipPath(path) {
        fillTiled(skin.bitmap(RedBallSkinSlot.SOIL), minX, minY, maxX, maxY, SOIL_TILE, SOIL_TILE)
    }
    drawSlopeGrass(x0, y0, x1, y1, skin)
}

/** Полоска травы вдоль горки. Локальная −Y смотрит в верхнюю нормаль отрезка. */
private fun DrawScope.drawSlopeGrass(x0: Float, y0: Float, x1: Float, y1: Float, skin: RedBallSkinBitmaps) {
    val dx = x1 - x0
    val dy = y1 - y0
    val len = hypot(dx, dy)
    if (len < 8f) return
    val ux = dx / len
    val uy = dy / len
    var nx = -uy
    var ny = ux
    if (ny > 0.02f) {
        nx = -nx
        ny = -ny
    }
    val theta = Math.toDegrees(atan2(nx.toDouble(), (-ny).toDouble())).toFloat()
    val grass = skin.bitmap(RedBallSkinSlot.GRASS_TOP)
    val blades = skin.bitmap(RedBallSkinSlot.GRASS_EDGE)
    val capRows = (grass.height * GRASS_CAP_FRACTION).toInt().coerceIn(1, grass.height)
    val lip = 20f
    val edge = 18f
    val tile = 64f
    var traveled = 0f
    while (traveled < len) {
        val seg = min(tile, len - traveled)
        val px = x0 + ux * traveled
        val py = y0 + uy * traveled
        withTransform({
            translate(px, py)
            rotate(theta, Offset.Zero)
        }) {
            withTransform({
                translate(0f, -lip + 3f)
                scale(seg / grass.width, lip / capRows, Offset.Zero)
            }) {
                drawImage(
                    grass,
                    srcSize = androidx.compose.ui.unit.IntSize(grass.width, capRows),
                    filterQuality = FilterQuality.Medium,
                )
            }
            withTransform({
                translate(0f, -lip - edge * 0.2f)
                scale(seg / blades.width, edge / blades.height, Offset.Zero)
            }) {
                drawImage(blades, filterQuality = FilterQuality.Medium)
            }
        }
        traveled += seg
    }
}

private fun DrawScope.drawSpriteLadder(box: RedBallAabb, skin: RedBallSkinBitmaps) {
    val w = box.r - box.l
    val h = box.b - box.t
    if (w < 4f || h < 4f) return
    val image = skin.bitmap(RedBallSkinSlot.LADDER)
    val scale = w / image.width
    val tileH = image.height * scale
    fillTiled(image, box.l, box.t, box.r, box.b, w, tileH, originX = box.l, originY = box.t)
}

private fun DrawScope.drawSpriteHero(sim: RedBallSim, skin: RedBallSkinBitmaps) {
    val r = RedBallScene.BALL_R
    val sx = sim.jellyX.coerceIn(0.62f, 1.48f)
    val sy = sim.jellyY.coerceIn(0.62f, 1.42f)
    val groundY = sim.groundBelowBall()
    if (groundY != null) {
        val gap = (groundY - (sim.ballY + r)).coerceAtLeast(0f)
        val k = (1f - gap / 360f).coerceIn(0f, 1f)
        if (k > 0.02f) {
            val w = r * 1.85f * sx * (0.45f + 0.55f * k)
            val h = r * 0.42f * (0.5f + 0.5f * k)
            drawOval(
                Color(0xFF1C3314).copy(alpha = 0.28f * k),
                Offset(sim.ballX - w / 2f, groundY - h * 0.45f),
                Size(w, h),
            )
        }
    }
    val sink = ((1f - sy) * r * 0.62f).coerceAtLeast(0f)
    val d = r * 2.06f
    placeCentered(
        skin.bitmap(heroSlot(sim)),
        sim.ballX,
        sim.ballY + sink,
        d * sx,
        d * sy,
        alpha = sim.hurtBlink(),
    )
}

/** Спрайт уже с лицом: в воздухе удивлён, рядом с врагом испуган, иначе улыбается. */
private fun heroSlot(sim: RedBallSim): RedBallSkinSlot {
    if (sim.dead) return RedBallSkinSlot.HERO_SCARED
    if (sim.won) return RedBallSkinSlot.HERO_HAPPY
    var nearest = Float.MAX_VALUE
    var reach = 0f
    for (enemy in sim.enemies) {
        if (!enemy.alive) continue
        val dx = enemy.x - sim.ballX
        val dy = enemy.y - sim.ballY
        val dist = dx * dx + dy * dy
        if (dist < nearest) {
            nearest = dist
            reach = enemy.size * 0.5f + 130f
        }
    }
    if (reach > 0f && nearest < reach * reach) return RedBallSkinSlot.HERO_SCARED
    if (!sim.grounded && (sim.ballVy < -50f || sim.ballVy > 120f)) return RedBallSkinSlot.HERO_SURPRISED
    return RedBallSkinSlot.HERO_HAPPY
}

private fun DrawScope.drawSpriteEnemy(
    cx: Float,
    cy: Float,
    side: Float,
    angle: Float,
    skin: RedBallSkinBitmaps,
) {
    if (side < 8f) return
    drawBlobShadow(cx, cy + side * 0.42f, side * 0.84f)
    val slot = if (side >= BOSS_SIDE) RedBallSkinSlot.BOSS else RedBallSkinSlot.ENEMY_SQUARE
    val deg = Math.toDegrees(angle.toDouble()).toFloat()
    placeCentered(skin.bitmap(slot), cx, cy, side, side) {
        rotate(deg, Offset.Zero)
    }
}

private fun DrawScope.drawSpriteCrate(cx: Float, cy: Float, side: Float, skin: RedBallSkinBitmaps) {
    if (side < 6f) return
    drawBlobShadow(cx, cy + side * 0.46f, side * 0.82f)
    placeCentered(skin.bitmap(RedBallSkinSlot.CRATE), cx, cy, side, side)
}

private fun DrawScope.drawSpriteBoulder(cx: Float, cy: Float, side: Float, skin: RedBallSkinBitmaps) {
    if (side < 6f) return
    drawBlobShadow(cx, cy + side * 0.44f, side * 0.78f)
    placeCentered(skin.bitmap(RedBallSkinSlot.BOULDER), cx, cy, side, side)
}

private fun DrawScope.drawSpriteStar(
    cx: Float,
    cy: Float,
    time: Float,
    stuck: Boolean,
    skin: RedBallSkinBitmaps,
) {
    val bob = sin(time * 3.2f + cx * 0.02f) * if (stuck) 1.5f else 4f
    val pulse = 0.94f + 0.06f * sin(time * 5f + cy)
    val side = 36f * pulse
    placeCentered(skin.bitmap(RedBallSkinSlot.STAR), cx, cy + bob, side, side)
}

private fun DrawScope.drawSpriteMagnet(cx: Float, cy: Float, time: Float, skin: RedBallSkinBitmaps) {
    val bob = sin(time * 2.4f + cx) * 3f
    placeCentered(skin.bitmap(RedBallSkinSlot.MAGNET), cx, cy + bob, 40f, 40f)
}

private fun DrawScope.drawSpriteFlag(
    l: Float,
    t: Float,
    r: Float,
    b: Float,
    time: Float,
    skin: RedBallSkinBitmaps,
) {
    val bw = r - l
    val bh = b - t
    if (bw < 4f || bh < 4f) return
    val image = skin.bitmap(RedBallSkinSlot.FLAG)
    val fit = min(bw / image.width, bh / image.height)
    val w = image.width * fit
    val h = image.height * fit
    val x = (l + r) / 2f - w / 2f
    val y = b - h
    val wave = sin(time * 3.1f) * 2.2f
    withTransform({
        rotate(wave, Offset(x + w * 0.5f, b))
        translate(x, y)
        scale(fit, fit, Offset.Zero)
    }) {
        drawImage(image, filterQuality = FilterQuality.Medium)
    }
}

private fun DrawScope.drawSpritePortal(
    l: Float,
    t: Float,
    r: Float,
    b: Float,
    time: Float,
    mood: SpritePortalMood,
    skin: RedBallSkinBitmaps,
) {
    val w = r - l
    val h = b - t
    if (w < 4f || h < 4f) return
    val cx = (l + r) / 2f
    val cy = (t + b) / 2f
    val exit = mood == SpritePortalMood.EXIT
    val glow = when (mood) {
        SpritePortalMood.OPEN -> Color(0xFF7C3AED)
        SpritePortalMood.EXIT -> Color(0xFF14B8A6)
        else -> Color(0xFF78716C)
    }
    if (mood == SpritePortalMood.OPEN || exit) {
        val pulse = 0.92f + 0.08f * sin(time * if (exit) 2.4f else 3.2f)
        drawCircle(glow.copy(alpha = if (exit) 0.30f else 0.28f), min(w, h) * 0.62f * pulse, Offset(cx, cy))
    }
    val pulse = if (mood == SpritePortalMood.OPEN) 0.98f + 0.035f * sin(time * 3.2f) else 0.98f
    val side = min(w, h) * pulse
    placeCentered(
        skin.bitmap(if (exit) RedBallSkinSlot.PORTAL_EXIT else RedBallSkinSlot.PORTAL_ENTRY),
        cx,
        cy,
        side,
        side,
        alpha = if (mood == SpritePortalMood.ASLEEP) 0.62f else 1f,
        colorFilter = if (mood == SpritePortalMood.ASLEEP) asleepFilter else null,
    )
    if (mood == SpritePortalMood.LOST) drawLostBadge(r, t, w, h)
}

private fun DrawScope.drawLostBadge(r: Float, t: Float, w: Float, h: Float) {
    val badge = Offset(r - w * 0.08f, t + h * 0.08f)
    drawCircle(Color(0xFFDC2626), w * 0.16f, badge)
    drawCircle(Color.White, w * 0.12f, badge)
    drawLine(
        Color(0xFFDC2626),
        badge + Offset(0f, -w * 0.05f),
        badge + Offset(0f, w * 0.02f),
        (w * 0.035f).coerceAtLeast(2f),
        androidx.compose.ui.graphics.StrokeCap.Round,
    )
    drawCircle(Color(0xFFDC2626), (w * 0.018f).coerceAtLeast(1.4f), badge + Offset(0f, w * 0.055f))
}

private fun DrawScope.drawSkyFill(image: ImageBitmap) {
    val h = size.height
    if (h < 1f || image.height < 1) return
    val tileW = h * image.width / image.height.toFloat()
    if (tileW < 1f) return
    val scale = h / image.height
    var x = 0f
    var guard = 0
    while (x < size.width && guard < 8) {
        withTransform({
            translate(x, 0f)
            scale(scale, scale, Offset.Zero)
        }) {
            drawImage(image, filterQuality = FilterQuality.Low)
        }
        x += tileW
        guard++
    }
}

private fun DrawScope.drawSpriteClouds(
    camX: Float,
    worldToScreen: Float,
    liftPx: Float,
    skin: RedBallSkinBitmaps,
) {
    if (size.width < 2f) return
    val slots = arrayOf(
        RedBallSkinSlot.CLOUD_A,
        RedBallSkinSlot.CLOUD_B,
        RedBallSkinSlot.CLOUD_C,
        RedBallSkinSlot.CLOUD_B,
        RedBallSkinSlot.CLOUD_A,
    )
    val span = size.width * 1.7f
    for (i in -1 until slots.size + 1) {
        val slot = slots[Math.floorMod(i, slots.size)]
        val image = skin.bitmap(slot)
        val factor = 0.05f + (Math.floorMod(i, 3)) * 0.03f
        val shift = wrapShift(camX * worldToScreen * factor, span)
        val x = (i + 0.2f) * span / slots.size - shift
        val y = size.height * (0.05f + Math.floorMod(i, 3) * 0.055f) + liftPx * factor
        val w = size.width * (0.24f + Math.floorMod(i, 2) * 0.05f)
        val h = w * image.height / image.width.toFloat()
        withTransform({
            translate(x, y)
            scale(w / image.width, h / image.height, Offset.Zero)
        }) {
            drawImage(image, filterQuality = FilterQuality.Medium)
        }
    }
}

private fun DrawScope.drawParallaxStrip(
    image: ImageBitmap,
    camX: Float,
    worldToScreen: Float,
    factor: Float,
    top: Float,
    height: Float,
) {
    if (height < 2f || size.width < 2f || image.height < 1) return
    val scale = height / image.height
    val tileW = image.width * scale
    if (tileW < 2f) return
    val shift = wrapShift(camX * worldToScreen * factor, tileW)
    var x = -shift
    var guard = 0
    while (x < size.width && guard < 8) {
        withTransform({
            translate(x, top)
            scale(scale, scale, Offset.Zero)
        }) {
            drawImage(image, filterQuality = FilterQuality.Medium)
        }
        x += tileW
        guard++
    }
}

private fun DrawScope.drawHillDecor(
    camX: Float,
    worldToScreen: Float,
    nearTop: Float,
    nearH: Float,
    liftPx: Float,
    skin: RedBallSkinBitmaps,
) {
    if (size.width < 2f) return
    val span = size.width * 2.2f
    val shift = wrapShift(camX * worldToScreen * 0.32f, span)
    val ground = nearTop + nearH * 0.72f + liftPx * 0.02f
    for (i in -1..7) {
        val tree = i % 2 == 0
        val image = skin.bitmap(if (tree) RedBallSkinSlot.TREE else RedBallSkinSlot.BUSH)
        val h = size.height * if (tree) 0.15f else 0.09f
        val w = h * image.width / image.height.toFloat()
        val x = i * span / 6f - shift - w / 2f
        withTransform({
            translate(x, ground - h)
            scale(w / image.width, h / image.height, Offset.Zero)
        }) {
            drawImage(image, filterQuality = FilterQuality.Medium)
        }
    }
}

/**
 * Шипы кладутся в ряд по ширине предмета. В PNG два шипа, период одного — половина кадра.
 * Высота кадра равна высоте предмета, лишнее по краю обрезает сам прямоугольник.
 */
private fun DrawScope.drawSpriteSpikes(
    x: Float,
    top: Float,
    w: Float,
    h: Float,
    skin: RedBallSkinBitmaps,
) {
    if (w < 2f || h < 2f) return
    val image = skin.bitmap(RedBallSkinSlot.SPIKES)
    val periods = (w / (h * 0.5f)).roundToInt().coerceAtLeast(1)
    val tileW = w / periods * 2f
    val left = x - w / 2f
    fillTiled(image, left, top, left + w, top + h, tileW, h, originX = left, originY = top)
}

/**
 * Оба кадра пружины вписаны в один прямоугольник: низ и центр совпадают,
 * сжатый кадр короче сверху. Симулятор не сообщает длительность удара,
 * поэтому сжатие держится, пока мяч касается пружины, и ещё короткое время после.
 */
private fun DrawScope.drawSpriteSpring(
    l: Float,
    t: Float,
    r: Float,
    b: Float,
    compressed: Boolean,
    skin: RedBallSkinBitmaps,
) {
    val w = r - l
    val h = b - t
    if (w < 4f || h < 4f) return
    val slot = if (compressed) RedBallSkinSlot.SPRING_COMPRESSED else RedBallSkinSlot.SPRING_IDLE
    placeCentered(skin.bitmap(slot), (l + r) / 2f, (t + b) / 2f, w, h)
}

/** Яма: тело тайлится по всей глубине, сверху полоска волн медленно едет вбок. */
private fun DrawScope.drawSpriteWater(scene: RedBallScene, time: Float, skin: RedBallSkinBitmaps) {
    val line = scene.voidY - 6f
    val bottom = scene.worldH + 60f
    if (line >= bottom) return
    val left = scene.minX - 80f
    val right = scene.worldW + 80f
    fillTiled(
        skin.bitmap(RedBallSkinSlot.WATER_BODY),
        left,
        line,
        right,
        bottom,
        WATER_BODY_TILE,
        WATER_BODY_TILE,
        alpha = WATER_BODY_ALPHA,
    )
    val overlap = WATER_SURFACE_H * 0.42f
    val stripTop = line - (WATER_SURFACE_H - overlap)
    val shift = wrapShift(time * WATER_SCROLL, WATER_SURFACE_W)
    fillTiled(
        skin.bitmap(RedBallSkinSlot.WATER_SURFACE),
        left,
        stripTop,
        right,
        stripTop + WATER_SURFACE_H,
        WATER_SURFACE_W,
        WATER_SURFACE_H,
        originX = left - shift,
        originY = stripTop,
    )
}

/**
 * Пока мяч лежит на пружине, кадр сжатый. После отрыва ещё [SPRING_SQUASH_HOLD] секунд.
 * Часы сбрасываются, когда уровень перезапустили или сцена сменилась.
 */
private object SpringSquash {
    private var sceneKey = ""
    private var lastAnim = 0f
    private var until = FloatArray(0)

    fun showCompressed(sim: RedBallSim, index: Int): Boolean {
        val springs = sim.scene.springs
        if (index !in springs.indices) return false
        val first = springs.first()
        val last = springs.last()
        val key = sim.scene.id + ":" + springs.size + ":" +
            first.l.toBits() + ":" + first.t.toBits() + ":" +
            last.r.toBits() + ":" + last.b.toBits()
        val restarted = sim.anim + 0.0001f < lastAnim
        if (key != sceneKey || restarted || until.size != springs.size) {
            sceneKey = key
            until = FloatArray(springs.size)
        }
        lastAnim = sim.anim
        if (ballPressesSpring(sim, springs[index])) {
            until[index] = sim.anim + SPRING_SQUASH_HOLD
        }
        return sim.anim < until[index]
    }
}

private fun ballPressesSpring(sim: RedBallSim, spring: RedBallAabb): Boolean {
    val radius = RedBallScene.BALL_R
    return sim.ballX + radius > spring.l && sim.ballX - radius < spring.r &&
        sim.ballY + radius > spring.t && sim.ballY - radius < spring.b
}

/**
 * Повтор PNG. Матрица шейдера увеличивает тайл до [tileW]×[tileH] мировых единиц
 * и ставит пиксель (0, 0) картинки в ([originX], [originY]), чтобы соседние блоки
 * сходились без шва. Шейдер создаётся на один прямоугольник и не делится между вызовами.
 */
private fun DrawScope.fillTiled(
    image: ImageBitmap,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    tileW: Float,
    tileH: Float,
    originX: Float = 0f,
    originY: Float = 0f,
    alpha: Float = 1f,
) {
    if (right - left < 1f || bottom - top < 1f || tileW < 1f || tileH < 1f) return
    if (image.width < 1 || image.height < 1) return
    val shader = ImageShader(image, TileMode.Repeated, TileMode.Repeated)
    val matrix = AndroidMatrix()
    matrix.setScale(tileW / image.width, tileH / image.height)
    matrix.postTranslate(originX, originY)
    shader.setLocalMatrix(matrix)
    drawRect(
        ShaderBrush(shader),
        Offset(left, top),
        Size(right - left, bottom - top),
        alpha = alpha,
    )
}

private fun DrawScope.placeCentered(
    image: ImageBitmap,
    cx: Float,
    cy: Float,
    width: Float,
    height: Float,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    around: DrawTransform.() -> Unit = {},
) {
    if (width < 1f || height < 1f || image.width < 1 || image.height < 1) return
    withTransform({
        translate(cx, cy)
        around()
        translate(-width / 2f, -height / 2f)
        scale(width / image.width, height / image.height, Offset.Zero)
    }) {
        drawImage(
            image,
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = FilterQuality.Medium,
        )
    }
}

private fun DrawScope.drawBlobShadow(cx: Float, cy: Float, w: Float) {
    drawOval(
        Color(0xFF1C3314).copy(alpha = 0.22f),
        Offset(cx - w / 2f, cy),
        Size(w, w * 0.18f),
    )
}

private fun wrapShift(value: Float, span: Float): Float {
    if (span <= 1f) return 0f
    val m = value % span
    return if (m < 0f) m + span else m
}

private fun RedBallPiece.spriteBox(cell: Float) = RedBallAabb(
    c * cell,
    r * cell,
    (c + w) * cell,
    (r + h) * cell,
)

private fun RedBallPiece.spriteSlope(cell: Float): RedBallSlope {
    val box = spriteBox(cell)
    return if (!flip) RedBallSlope(box.l, box.t, box.r, box.b) else RedBallSlope(box.l, box.b, box.r, box.t)
}
