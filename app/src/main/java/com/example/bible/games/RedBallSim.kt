package com.example.bible.games

/**
 * Один уровень физической аркады: мяч катится, падает, толкает ящик и пружинит.
 * Ось Y направлена вниз, как на экране.
 */
class RedBallSim {
    var ballX = 0f
    var ballY = 0f
    var ballVx = 0f
    var ballVy = 0f
    var ballAngle = 0f

    var crateX = 0f
    var crateY = 0f
    var crateVx = 0f
    var crateVy = 0f

    var won = false
        private set
    var dead = false
        private set
    var grounded = false
        private set
    private var wasGrounded = false

    private var springArmed = true
    private var pendingLose = false
    private var pendingSpring = false
    private var pendingWin = false
    private var crateOnGround = false

    fun reset() {
        ballX = START_X
        ballY = START_Y
        ballVx = 0f
        ballVy = 0f
        ballAngle = 0f
        crateX = CRATE_START_X
        crateY = CRATE_START_Y
        crateVx = 0f
        crateVy = 0f
        won = false
        dead = false
        grounded = false
        wasGrounded = false
        springArmed = true
        pendingLose = false
        pendingSpring = false
        pendingWin = false
        crateOnGround = false
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
        ballVy += GRAVITY * dt
        crateVy += GRAVITY * dt
        val accel = if (wasGrounded) RUN_ACCEL else AIR_ACCEL
        ballVx += move * accel * dt
        ballVx = ballVx.coerceIn(-MAX_RUN, MAX_RUN)
        if (move == 0f && wasGrounded) {
            ballVx *= (1f - GROUND_DRAG * dt).coerceAtLeast(0f)
        }
        ballVx *= (1f - AIR_DRAG * dt).coerceAtLeast(0f)
        grounded = false
        crateOnGround = false

        ballX += ballVx * dt
        ballY += ballVy * dt
        crateX += crateVx * dt
        crateY += crateVy * dt

        repeat(4) {
            resolveBallAgainstSolids()
            resolveBallAgainstCrate()
            resolveCrateAgainstSolids()
        }
        if (crateOnGround) {
            crateVx *= (1f - CRATE_DRAG * dt).coerceAtLeast(0f)
        }
        ballAngle += ballVx / BALL_R * dt
        wasGrounded = grounded

        if (overlapsSpring() && ballVy > -80f) {
            ballVy = SPRING_VY
            if (ballVx > 0f) ballVx = kotlin.math.max(ballVx, 220f)
            if (springArmed) {
                pendingSpring = true
                springArmed = false
            }
        } else if (!overlapsSpring()) {
            springArmed = true
        }

        if (ballY > VOID_Y || hitsSpike()) {
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

    private fun resolveBallAgainstSolids() {
        for (solid in SOLIDS) {
            bounceCircle(solid, BALL_R, onBall = true)
        }
        for (slope in SLOPES) {
            bounceSlope(slope)
        }
    }

    private fun bounceCircle(solid: Aabb, radius: Float, onBall: Boolean) {
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
        if (onBall && ny < -0.45f) grounded = true
    }

    private fun bounceSlope(slope: Slope) {
        val dx = slope.x1 - slope.x0
        val dy = slope.y1 - slope.y0
        val len2 = dx * dx + dy * dy
        if (len2 < 1f) return
        val t = (((ballX - slope.x0) * dx + (ballY - slope.y0) * dy) / len2).coerceIn(0f, 1f)
        val px = slope.x0 + dx * t
        val py = slope.y0 + dy * t
        var nx = -dy
        var ny = dx
        if (ny > 0f) {
            nx = -nx
            ny = -ny
        }
        val nLen = kotlin.math.sqrt(nx * nx + ny * ny)
        nx /= nLen
        ny /= nLen
        val ox = ballX - px
        val oy = ballY - py
        val side = ox * nx + oy * ny
        if (side >= BALL_R || side < -BALL_R) return
        val pen = BALL_R - side
        ballX += nx * pen
        ballY += ny * pen
        val vn = ballVx * nx + ballVy * ny
        if (vn < 0f) {
            ballVx -= (1f + 0.05f) * vn * nx
            ballVy -= (1f + 0.05f) * vn * ny
        }
        if (ny < -0.25f) grounded = true
    }

    private fun resolveBallAgainstCrate() {
        val solid = crateAabb()
        val closestX = ballX.coerceIn(solid.l, solid.r)
        val closestY = ballY.coerceIn(solid.t, solid.b)
        var nx = ballX - closestX
        var ny = ballY - closestY
        val dist2 = nx * nx + ny * ny
        val radius = BALL_R
        if (dist2 > radius * radius && dist2 > 1e-4f) return
        if (dist2 < 1e-4f) {
            nx = if (ballX < crateX) -1f else 1f
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
        crateX -= nx * pen * (invCrate / inv)
        crateY -= ny * pen * (invCrate / inv)
        val rvx = ballVx - crateVx
        val rvy = ballVy - crateVy
        val vn = rvx * nx + rvy * ny
        if (vn < 0f) {
            val j = -(1.02f) * vn / inv
            ballVx += j * invBall * nx
            ballVy += j * invBall * ny
            crateVx -= j * invCrate * nx
            crateVy -= j * invCrate * ny
        }
        if (ny < -0.45f) grounded = true
    }

    private fun resolveCrateAgainstSolids() {
        for (solid in SOLIDS) {
            val box = crateAabb()
            if (box.r <= solid.l || box.l >= solid.r || box.b <= solid.t || box.t >= solid.b) continue
            val penL = box.r - solid.l
            val penR = solid.r - box.l
            val penT = box.b - solid.t
            val penB = solid.b - box.t
            val min = minOf(penL, penR, penT, penB)
            when (min) {
                penL -> {
                    crateX -= penL
                    if (crateVx > 0f) crateVx = 0f
                }
                penR -> {
                    crateX += penR
                    if (crateVx < 0f) crateVx = 0f
                }
                penT -> {
                    crateY -= penT
                    if (crateVy > 0f) crateVy *= -0.05f
                    crateOnGround = true
                }
                else -> {
                    crateY += penB
                    if (crateVy < 0f) crateVy = 0f
                }
            }
        }
    }

    private fun crateAabb(): Aabb {
        val h = CRATE / 2f
        return Aabb(crateX - h, crateY - h, crateX + h, crateY + h)
    }

    private fun overlapsSpring(): Boolean {
        val s = SPRING
        return ballX + BALL_R > s.l && ballX - BALL_R < s.r &&
            ballY + BALL_R > s.t && ballY - BALL_R < s.b
    }

    private fun overlapsDoor(): Boolean {
        val d = DOOR
        return ballX > d.l && ballX < d.r && ballY > d.t && ballY < d.b
    }

    private fun hitsSpike(): Boolean {
        for (spike in SPIKES) {
            if (kotlin.math.abs(ballX - spike.x) > spike.w / 2f + BALL_R * 0.4f) continue
            if (ballY + BALL_R < spike.top || ballY - BALL_R > spike.top + spike.h) continue
            val depth = ((ballY - spike.top) / spike.h).coerceIn(0f, 1f)
            val half = spike.w / 2f * depth
            if (kotlin.math.abs(ballX - spike.x) < half + BALL_R * 0.45f) return true
        }
        return false
    }

    data class Aabb(val l: Float, val t: Float, val r: Float, val b: Float)
    data class Slope(val x0: Float, val y0: Float, val x1: Float, val y1: Float)
    data class Spike(val x: Float, val top: Float, val w: Float, val h: Float)

    companion object {
        const val WORLD_W = 2680f
        const val WORLD_H = 1000f
        const val BALL_R = 28f
        const val CRATE = 96f
        const val GRAVITY = 2400f
        const val FLOOR = 760f
        const val LEDGE = 430f

        private const val RUN_ACCEL = 1700f
        private const val AIR_ACCEL = 980f
        private const val MAX_RUN = 460f
        private const val GROUND_DRAG = 3.2f
        private const val AIR_DRAG = 0.15f
        private const val CRATE_DRAG = 2.2f
        private const val SPRING_VY = -1380f
        private const val VOID_Y = 960f
        private const val START_X = 170f
        private const val START_Y = 640f - BALL_R
        private const val CRATE_START_X = 1140f
        private const val CRATE_START_Y = FLOOR - CRATE / 2f

        val SOLIDS = listOf(
            Aabb(0f, 640f, 470f, 980f),
            Aabb(470f, FLOOR, 820f, 980f),
            Aabb(820f, FLOOR, 1280f, 980f),
            Aabb(1280f, 860f, 1400f, 980f),
            Aabb(1400f, FLOOR, 1860f, 980f),
            Aabb(1860f, LEDGE, 2620f, 980f),
            Aabb(-80f, 0f, 0f, 980f),
            Aabb(2620f, 0f, 2700f, 980f),
        )
        val SLOPES = listOf(Slope(470f, 640f, 820f, FLOOR))
        val SPIKES = listOf(
            Spike(1310f, 800f, 36f, 60f),
            Spike(1350f, 800f, 36f, 60f),
            Spike(1390f, 800f, 36f, 60f),
        )
        val SPRING = Aabb(1660f, 724f, 1750f, FLOOR)
        val DOOR = Aabb(2380f, 250f, 2480f, LEDGE)
    }
}
