package com.example.bible.games

/**
 * Физика «Красного мяча»: мяч катится, падает, толкает ящики и пружинит.
 * И встроенная горка, и свой уровень — это [RedBallScene]. Ось Y вниз.
 */
class RedBallSim(val scene: RedBallScene = RedBallScene.builtin()) {
    var ballX = 0f
    var ballY = 0f
    var ballVx = 0f
    var ballVy = 0f
    var ballAngle = 0f

    val crates: List<RedBallCrate> = scene.crates.map {
        RedBallCrate(it.x, it.y, 0f, 0f, it.size, it.x, it.y)
    }

    /** Первый ящик. Встроенный уровень один, тесты читают его отсюда. */
    val crateX: Float get() = crates.firstOrNull()?.x ?: 0f
    val crateY: Float get() = crates.firstOrNull()?.y ?: 0f

    var won = false
        private set
    var dead = false
        private set
    var grounded = false
        private set
    private var wasGrounded = false

    private val springArmed = BooleanArray(scene.springs.size) { true }
    private var pendingLose = false
    private var pendingSpring = false
    private var pendingWin = false
    private var pendingJump = false

    /** Кнопка «Прыжок» сейчас зажата: короткое касание даёт низкий прыжок, удержание — полный. */
    var jumpHeld = false
    private var jumpBuffer = 0f
    private var coyote = 0f
    private var rising = false

    /** Нажали «Прыжок». Сработает, если мяч стоит на опоре или только что с неё сошёл. */
    fun jump() {
        if (won || dead) return
        jumpBuffer = JUMP_BUFFER
    }

    fun consumeJump(): Boolean = pendingJump.also { pendingJump = false }

    fun reset() {
        ballX = scene.startX
        ballY = scene.startY
        ballVx = 0f
        ballVy = 0f
        ballAngle = 0f
        for (crate in crates) crate.reset()
        won = false
        dead = false
        grounded = false
        wasGrounded = false
        for (i in springArmed.indices) springArmed[i] = true
        pendingLose = false
        pendingSpring = false
        pendingWin = false
        pendingJump = false
        jumpHeld = false
        jumpBuffer = 0f
        coyote = 0f
        rising = false
    }

    fun consumeWin(): Boolean = pendingWin.also { pendingWin = false }

    fun consumeLose(): Boolean = pendingLose.also { pendingLose = false }

    fun consumeSpring(): Boolean = pendingSpring.also { pendingSpring = false }

    init {
        reset()
    }

    fun step(dt: Float, move: Float) {
        if (won || dead) return
        var left = dt.coerceIn(0f, 0.05f)
        while (left > 0f) {
            val h = kotlin.math.min(left, 1f / 120f)
            substep(h, move.coerceIn(-1f, 1f))
            left -= h
            if (won || dead) return
        }
    }

    private fun substep(dt: Float, move: Float) {
        coyote = if (wasGrounded) COYOTE_TIME else (coyote - dt).coerceAtLeast(0f)
        if (jumpBuffer > 0f) {
            if (coyote > 0f) {
                ballVy = minOf(ballVy, JUMP_VY)
                jumpBuffer = 0f
                coyote = 0f
                wasGrounded = false
                rising = true
                pendingJump = true
            } else {
                jumpBuffer = (jumpBuffer - dt).coerceAtLeast(0f)
            }
        }
        if (rising) {
            if (ballVy >= 0f) {
                rising = false
            } else if (!jumpHeld && ballVy < JUMP_CUT_VY) {
                ballVy = JUMP_CUT_VY
                rising = false
            }
        }
        ballVy += GRAVITY * dt
        for (crate in crates) crate.vy += GRAVITY * dt
        val accel = if (wasGrounded) RUN_ACCEL else AIR_ACCEL
        ballVx += move * accel * dt
        ballVx = ballVx.coerceIn(-MAX_RUN, MAX_RUN)
        if (move == 0f && wasGrounded) {
            ballVx *= (1f - GROUND_DRAG * dt).coerceAtLeast(0f)
        }
        ballVx *= (1f - AIR_DRAG * dt).coerceAtLeast(0f)
        grounded = false
        for (crate in crates) crate.onGround = false

        ballX += ballVx * dt
        ballY += ballVy * dt
        for (crate in crates) {
            crate.x += crate.vx * dt
            crate.y += crate.vy * dt
        }

        repeat(4) {
            resolveBallAgainstSolids()
            resolveBallAgainstCrates()
            resolveCratesAgainstSolids()
            if (!scene.builtin) {
                resolveCratesAgainstSlopes()
                resolveCratesAgainstEachOther()
            }
        }
        for (crate in crates) {
            if (crate.onGround) crate.vx *= (1f - CRATE_DRAG * dt).coerceAtLeast(0f)
        }
        ballAngle += ballVx / RedBallScene.BALL_R * dt
        wasGrounded = grounded

        applySprings()

        if (ballY > scene.voidY || hitsSpike()) {
            dead = true
            pendingLose = true
            ballVx = 0f
            ballVy = 0f
            return
        }
        if (overlapsDoor()) {
            won = true
            pendingWin = true
            ballVx = 0f
            ballVy = 0f
        }
    }

    private fun applySprings() {
        var any = false
        var armedLaunch = false
        for (i in scene.springs.indices) {
            if (overlaps(scene.springs[i])) {
                any = true
                if (ballVy > -80f && springArmed[i]) {
                    armedLaunch = true
                    springArmed[i] = false
                }
            } else {
                springArmed[i] = true
            }
        }
        if (any && ballVy > -80f) {
            ballVy = SPRING_VY
            rising = false
            if (ballVx > 0f) ballVx = kotlin.math.max(ballVx, 220f)
            if (armedLaunch) pendingSpring = true
        }
    }

    /** Ближайшая поверхность прямо под центром мяча (для тени), или null, если под мячом пусто. */
    fun groundBelowBall(): Float? {
        val x = ballX
        val from = ballY
        var best = Float.MAX_VALUE
        fun take(y: Float) {
            if (y >= from && y < best) best = y
        }
        for (s in scene.solids) if (x >= s.l && x <= s.r) take(s.t)
        for (sl in scene.slopes) {
            val lo = kotlin.math.min(sl.x0, sl.x1)
            val hi = kotlin.math.max(sl.x0, sl.x1)
            if (x < lo || x > hi || hi - lo < 1f) continue
            val t = (x - sl.x0) / (sl.x1 - sl.x0)
            take(sl.y0 + (sl.y1 - sl.y0) * t)
        }
        for (s in scene.springs) if (x >= s.l && x <= s.r) take(s.t)
        for (c in crates) {
            val half = c.size / 2f
            if (x >= c.x - half && x <= c.x + half) take(c.y - half)
        }
        return if (best == Float.MAX_VALUE) null else best
    }

    private fun resolveBallAgainstSolids() {
        for (solid in scene.solids) {
            bounceCircle(solid, RedBallScene.BALL_R)
        }
        for (slope in scene.slopes) {
            bounceSlope(slope)
        }
    }

    private fun bounceCircle(solid: RedBallAabb, radius: Float) {
        val closestX = ballX.coerceIn(solid.l, solid.r)
        val closestY = ballY.coerceIn(solid.t, solid.b)
        var nx = ballX - closestX
        var ny = ballY - closestY
        var dist2 = nx * nx + ny * ny
        if (dist2 > radius * radius) return
        if (dist2 < 1e-4f) {
            val left = ballX - solid.l
            val right = solid.r - ballX
            val top = ballY - solid.t
            val bottom = solid.b - ballY
            val min = minOf(left, right, top, bottom)
            when (min) {
                left -> { nx = -1f; ny = 0f }
                right -> { nx = 1f; ny = 0f }
                top -> { nx = 0f; ny = -1f }
                else -> { nx = 0f; ny = 1f }
            }
            dist2 = 1f
            ballX += nx * (radius + 0.5f)
            ballY += ny * (radius + 0.5f)
        } else {
            val dist = kotlin.math.sqrt(dist2)
            nx /= dist
            ny /= dist
            val pen = radius - dist
            ballX += nx * pen
            ballY += ny * pen
        }
        val vn = ballVx * nx + ballVy * ny
        if (vn < 0f) {
            val bounce = if (ny < -0.6f) 0.12f else 0.28f
            ballVx -= (1f + bounce) * vn * nx
            ballVy -= (1f + bounce) * vn * ny
        }
        if (ny < -0.45f) grounded = true
    }

    private fun bounceSlope(slope: RedBallSlope) {
        val hit = slopeHit(slope, ballX, ballY, RedBallScene.BALL_R, RedBallScene.BALL_R) ?: return
        ballX += hit.nx * hit.pen
        ballY += hit.ny * hit.pen
        val vn = ballVx * hit.nx + ballVy * hit.ny
        if (vn < 0f) {
            ballVx -= (1f + 0.05f) * vn * hit.nx
            ballVy -= (1f + 0.05f) * vn * hit.ny
        }
        if (hit.ny < -0.25f) grounded = true
    }

    private fun resolveBallAgainstCrates() {
        for (i in crates.indices) resolveBallAgainstCrate(crates[i])
    }

    private fun resolveBallAgainstCrate(crate: RedBallCrate) {
        val solid = crateAabb(crate)
        val closestX = ballX.coerceIn(solid.l, solid.r)
        val closestY = ballY.coerceIn(solid.t, solid.b)
        var nx = ballX - closestX
        var ny = ballY - closestY
        val dist2 = nx * nx + ny * ny
        val radius = RedBallScene.BALL_R
        if (dist2 > radius * radius && dist2 > 1e-4f) return
        if (dist2 < 1e-4f) {
            nx = if (ballX < crate.x) -1f else 1f
            ny = 0f
        } else {
            val dist = kotlin.math.sqrt(dist2)
            nx /= dist
            ny /= dist
        }
        val dist = if (dist2 < 1e-4f) 0f else kotlin.math.sqrt(dist2)
        val pen = radius - dist
        val invBall = 1f
        val invCrate = 0.55f
        val inv = invBall + invCrate
        ballX += nx * pen * (invBall / inv)
        ballY += ny * pen * (invBall / inv)
        crate.x -= nx * pen * (invCrate / inv)
        crate.y -= ny * pen * (invCrate / inv)
        val rvx = ballVx - crate.vx
        val rvy = ballVy - crate.vy
        val vn = rvx * nx + rvy * ny
        if (vn < 0f) {
            val j = -(1.02f) * vn / inv
            ballVx += j * invBall * nx
            ballVy += j * invBall * ny
            crate.vx -= j * invCrate * nx
            crate.vy -= j * invCrate * ny
        }
        if (ny < -0.45f) grounded = true
    }

    private fun resolveCratesAgainstSolids() {
        for (i in crates.indices) resolveCrateAgainstSolids(crates[i])
    }

    private fun resolveCrateAgainstSolids(crate: RedBallCrate) {
        for (solid in scene.solids) {
            val box = crateAabb(crate)
            if (box.r <= solid.l || box.l >= solid.r || box.b <= solid.t || box.t >= solid.b) continue
            val penL = box.r - solid.l
            val penR = solid.r - box.l
            val penT = box.b - solid.t
            val penB = solid.b - box.t
            val min = minOf(penL, penR, penT, penB)
            when (min) {
                penL -> {
                    crate.x -= penL
                    if (crate.vx > 0f) crate.vx = 0f
                }
                penR -> {
                    crate.x += penR
                    if (crate.vx < 0f) crate.vx = 0f
                }
                penT -> {
                    crate.y -= penT
                    if (crate.vy > 0f) crate.vy *= -0.05f
                    crate.onGround = true
                }
                else -> {
                    crate.y += penB
                    if (crate.vy < 0f) crate.vy = 0f
                }
            }
        }
    }

    private fun resolveCratesAgainstSlopes() {
        for (i in crates.indices) resolveCrateAgainstSlopes(crates[i])
    }

    private fun resolveCrateAgainstSlopes(crate: RedBallCrate) {
        val radius = crate.size * 0.5f
        for (slope in scene.slopes) {
            val hit = slopeHit(slope, crate.x, crate.y, radius, radius * 0.25f) ?: continue
            crate.x += hit.nx * hit.pen
            crate.y += hit.ny * hit.pen
            val vn = crate.vx * hit.nx + crate.vy * hit.ny
            if (vn < 0f) {
                crate.vx -= vn * hit.nx
                crate.vy -= vn * hit.ny
            }
            if (hit.ny < -0.25f) crate.onGround = true
        }
    }

    /**
     * Контакт с отрезком склона.
     * На самом отрезке это односторонняя плоскость. За торцом опора только
     * в радиусе вокруг вершины: иначе прямая продолжается бесконечно, и мяч
     * едет вверх по невидимой горке либо цепляется за неё в прыжке.
     */
    private fun slopeHit(
        slope: RedBallSlope,
        x: Float,
        y: Float,
        radius: Float,
        underside: Float,
    ): SlopeHit? {
        val dx = slope.x1 - slope.x0
        val dy = slope.y1 - slope.y0
        val len2 = dx * dx + dy * dy
        if (len2 < 1f) return null
        var nx = -dy
        var ny = dx
        if (ny > 0f) {
            nx = -nx
            ny = -ny
        }
        val nLen = kotlin.math.sqrt(nx * nx + ny * ny)
        if (nLen < 1e-4f) return null
        nx /= nLen
        ny /= nLen
        val t = ((x - slope.x0) * dx + (y - slope.y0) * dy) / len2
        if (t < 0f || t > 1f) {
            val ex = if (t < 0f) slope.x0 else slope.x1
            val ey = if (t < 0f) slope.y0 else slope.y1
            var rx = x - ex
            var ry = y - ey
            val dist2 = rx * rx + ry * ry
            if (dist2 > radius * radius) return null
            val dist = kotlin.math.sqrt(dist2)
            if (dist < 1e-3f) {
                rx = nx
                ry = ny
            } else {
                rx /= dist
                ry /= dist
            }
            return SlopeHit(rx, ry, radius - dist)
        }
        val side = (x - (slope.x0 + dx * t)) * nx + (y - (slope.y0 + dy * t)) * ny
        if (side >= radius || side < -underside) return null
        return SlopeHit(nx, ny, radius - side)
    }

    private data class SlopeHit(val nx: Float, val ny: Float, val pen: Float)

    private fun resolveCratesAgainstEachOther() {
        for (i in crates.indices) {
            for (j in i + 1 until crates.size) separateCrates(crates[i], crates[j])
        }
    }

    private fun separateCrates(a: RedBallCrate, b: RedBallCrate) {
        val ha = a.size / 2f
        val hb = b.size / 2f
        val dx = b.x - a.x
        val dy = b.y - a.y
        val overlapX = ha + hb - kotlin.math.abs(dx)
        val overlapY = ha + hb - kotlin.math.abs(dy)
        if (overlapX <= 0f || overlapY <= 0f) return
        if (overlapX < overlapY) {
            val sign = if (dx >= 0f) 1f else -1f
            val push = overlapX / 2f
            a.x -= sign * push
            b.x += sign * push
            if ((b.vx - a.vx) * sign < 0f) {
                val va = a.vx
                a.vx = b.vx
                b.vx = va
            }
        } else {
            val sign = if (dy >= 0f) 1f else -1f
            val push = overlapY / 2f
            a.y -= sign * push
            b.y += sign * push
            if (dy > 0f && a.vy > 0f) {
                a.vy *= -0.05f
                a.onGround = true
            } else if (dy < 0f && b.vy > 0f) {
                b.vy *= -0.05f
                b.onGround = true
            }
        }
    }

    private fun crateAabb(crate: RedBallCrate): RedBallAabb {
        val h = crate.size / 2f
        return RedBallAabb(crate.x - h, crate.y - h, crate.x + h, crate.y + h)
    }

    private fun overlaps(s: RedBallAabb): Boolean {
        val radius = RedBallScene.BALL_R
        return ballX + radius > s.l && ballX - radius < s.r &&
            ballY + radius > s.t && ballY - radius < s.b
    }

    private fun overlapsDoor(): Boolean {
        val d = scene.door
        return ballX > d.l && ballX < d.r && ballY > d.t && ballY < d.b
    }

    private fun hitsSpike(): Boolean {
        val radius = RedBallScene.BALL_R
        for (spike in scene.spikes) {
            if (kotlin.math.abs(ballX - spike.x) > spike.w / 2f + radius * 0.4f) continue
            if (ballY + radius < spike.top || ballY - radius > spike.top + spike.h) continue
            val depth = ((ballY - spike.top) / spike.h).coerceIn(0f, 1f)
            val half = spike.w / 2f * depth
            if (kotlin.math.abs(ballX - spike.x) < half + radius * 0.45f) return true
        }
        return false
    }

    companion object {
        const val GRAVITY = 2400f
        private const val RUN_ACCEL = 1700f
        private const val AIR_ACCEL = 980f
        private const val MAX_RUN = 460f
        private const val GROUND_DRAG = 3.2f
        private const val AIR_DRAG = 0.15f
        private const val CRATE_DRAG = 2.2f
        private const val SPRING_VY = -1380f
        /** Полный прыжок поднимает мяч примерно на 3,5 клетки редактора. */
        private const val JUMP_VY = -820f
        /** Отпустили кнопку на взлёте — прыжок обрывается ниже. */
        private const val JUMP_CUT_VY = -380f
        /** Нажатие чуть раньше приземления не теряется. */
        private const val JUMP_BUFFER = 0.12f
        /** Можно прыгнуть ещё мгновение после схода с края. */
        private const val COYOTE_TIME = 0.09f
    }
}

class RedBallCrate(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val size: Float,
    val homeX: Float,
    val homeY: Float,
) {
    var onGround: Boolean = false

    fun reset() {
        x = homeX
        y = homeY
        vx = 0f
        vy = 0f
        onGround = false
    }
}
