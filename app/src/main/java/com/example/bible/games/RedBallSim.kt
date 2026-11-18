package com.example.bible.games

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Физика «Красного мяча»: мяч катится, падает, толкает ящики и пружинит.
 * И встроенная горка, и свой уровень — это [RedBallScene]. Ось Y вниз.
 * Столкновение мяча — всегда круг. Сжатие желе только для картинки.
 */
class RedBallSim(val scene: RedBallScene = RedBallScene.builtin()) {
    var ballX = 0f
    var ballY = 0f
    var ballVx = 0f
    var ballVy = 0f
    var ballAngle = 0f

    /** Сжатие желе: 1 — круг, по X шире при приземлении, по Y выше в прыжке. */
    var jellyX = 1f
    var jellyY = 1f
    var anim = 0f
    var magnetTime = 0f

    val crates: List<RedBallCrate> = scene.crates.map { it.toBody() }
    val blocks: List<RedBallCrate> = scene.blocks.map { it.toBody() }
    val enemies: List<RedBallEnemy> = scene.enemies.map { it.toEnemy() }
    val stars: List<RedBallStar> = scene.stars.mapIndexed { index, it ->
        RedBallStar(it.x, it.y, it.x, it.y, index * 1.3f)
    }
    val magnets: List<RedBallMagnet> = scene.magnets.map { RedBallMagnet(it.x, it.y) }
    val particles: List<RedBallParticle> get() = fx

    val starsCollected: Int get() = stars.count { it.taken || it.attached }

    /** Первый ящик. Встроенный уровень один, тесты читают его отсюда. */
    val crateX: Float get() = crates.firstOrNull()?.x ?: 0f
    val crateY: Float get() = crates.firstOrNull()?.y ?: 0f

    var won = false
        private set
    var dead = false
        private set

    /** Оставшиеся жизни. Ноль — мяч погиб. */
    var hearts = MAX_HEARTS
        private set

    /**
     * Секунды после удара, когда урон не проходит.
     * Пока значение больше нуля, мяч мигает в обоих скинах.
     */
    var invuln = 0f
        private set

    var grounded = false
        private set
    private var wasGrounded = false
    private var contactFriction = RED_BALL_FRICTION
    private var jellyV = 0f
    private var landKick = 0f

    private val springArmed = BooleanArray(scene.springs.size) { true }
    private var pendingLose = false
    private var pendingHurt = false
    private var pendingSpring = false
    private var pendingWin = false
    private var pendingJump = false
    private var pendingStomp = false
    private var pendingStar = 0
    private var pendingMagnet = false
    private val portalArmed = BooleanArray(scene.portals.size)
    private val portalAsleep = BooleanArray(scene.portals.size)
    private var portalHold = false
    private var pendingPortalIndex = -1
    private val fx = ArrayList<RedBallParticle>(80)
    private val rng = java.util.Random(1)

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
        jellyX = 1f
        jellyY = 1f
        jellyV = 0f
        landKick = 0f
        anim = 0f
        magnetTime = 0f
        for (crate in crates) crate.reset()
        for (block in blocks) block.reset()
        for (enemy in enemies) enemy.reset()
        for (star in stars) star.reset()
        for (magnet in magnets) magnet.taken = false
        fx.clear()
        won = false
        dead = false
        hearts = MAX_HEARTS
        invuln = 0f
        grounded = false
        wasGrounded = false
        contactFriction = RED_BALL_FRICTION
        for (i in springArmed.indices) springArmed[i] = true
        pendingLose = false
        pendingHurt = false
        pendingSpring = false
        pendingWin = false
        pendingJump = false
        pendingStomp = false
        pendingStar = 0
        pendingMagnet = false
        portalHold = false
        pendingPortalIndex = -1
        for (i in portalArmed.indices) {
            portalAsleep[i] = false
            val portal = scene.portals[i]
            portalArmed[i] = portal.role != RedBallPortalRole.ENTRY ||
                portal.linkId.isBlank() ||
                !overlaps(portal.box)
        }
        jumpHeld = false
        jumpBuffer = 0f
        coyote = 0f
        rising = false
    }

    fun consumeWin(): Boolean = pendingWin.also { pendingWin = false }

    fun consumeLose(): Boolean = pendingLose.also { pendingLose = false }

    /** Удар, после которого жизнь ещё осталась. */
    fun consumeHurt(): Boolean = pendingHurt.also { pendingHurt = false }

    /**
     * 1 — мяч рисуется как обычно.
     * Меньше — кадр мигания, пока действует неуязвимость.
     * Оба скина читают одно и то же значение.
     */
    fun hurtBlink(): Float {
        if (dead || invuln <= 0f) return 1f
        val dim = ((anim * HURT_BLINK_HZ).toInt() and 1) == 1
        return if (dim) HURT_BLINK_ALPHA else 1f
    }

    fun consumeSpring(): Boolean = pendingSpring.also { pendingSpring = false }

    fun consumeStomp(): Boolean = pendingStomp.also { pendingStomp = false }

    fun consumeStars(): Int = pendingStar.also { pendingStar = 0 }

    fun consumeMagnet(): Boolean = pendingMagnet.also { pendingMagnet = false }

    /** Портал, в который мяч только что въехал. Пока переход не закончен, физика стоит. */
    fun consumePortal(): RedBallPortal? {
        if (pendingPortalIndex < 0) return null
        val portal = scene.portals[pendingPortalIndex]
        pendingPortalIndex = -1
        return portal
    }

    /**
     * Появление в другой сцене. Выход сам по себе не срабатывает.
     * Вход, внутри которого мяч оказался, молчит, пока мяч из него не выйдет.
     */
    fun arrive(x: Float, y: Float) {
        ballX = x
        ballY = y
        ballVx = 0f
        ballVy = 0f
        grounded = false
        wasGrounded = false
        rising = false
        jumpHeld = false
        jumpBuffer = 0f
        coyote = 0f
        portalHold = false
        pendingPortalIndex = -1
        for (i in portalArmed.indices) portalArmed[i] = false
    }

    /** Цели больше нет: этот портал засыпает до перезапуска сцены. */
    fun dismissPortal() {
        if (pendingPortalIndex >= 0) portalAsleep[pendingPortalIndex] = true
        pendingPortalIndex = -1
        portalHold = false
        for (i in portalArmed.indices) portalArmed[i] = false
    }

    init {
        reset()
    }

    fun step(dt: Float, move: Float) {
        if (won || dead || portalHold) return
        val total = dt.coerceIn(0f, 0.05f)
        var left = total
        while (left > 0f) {
            val h = min(left, 1f / 120f)
            substep(h, move.coerceIn(-1f, 1f))
            left -= h
            if (won || dead || portalHold) break
        }
        val spent = (total - left).coerceAtLeast(0f)
        if (spent > 0f) {
            updateJelly(spent)
            updateParticles(spent)
            anim += spent
        }
    }

    private fun substep(dt: Float, move: Float) {
        if (invuln > 0f) invuln = (invuln - dt).coerceAtLeast(0f)
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
        val climbing = overlapsLadder()
        if (climbing && jumpHeld && !wasGrounded) {
            ballVy = -CLIMB_SPEED
            rising = false
            jumpBuffer = 0f
        } else if (climbing && !wasGrounded) {
            ballVy += GRAVITY * 0.2f * dt
            if (ballVy > LADDER_FALL) ballVy = LADDER_FALL
        } else {
            ballVy += GRAVITY * dt
        }
        for (crate in crates) if (crate.gravity) crate.vy += GRAVITY * dt
        for (block in blocks) if (block.gravity) block.vy += GRAVITY * dt
        for (enemy in enemies) if (enemy.alive && enemy.gravity) enemy.vy += GRAVITY * dt
        val accel = if (wasGrounded) RUN_ACCEL else AIR_ACCEL
        ballVx += move * accel * dt
        ballVx = ballVx.coerceIn(-MAX_RUN, MAX_RUN)
        if (move == 0f && wasGrounded) {
            val drag = GROUND_DRAG * frictionMul(contactFriction)
            ballVx *= (1f - drag * dt).coerceAtLeast(0f)
        }
        ballVx *= (1f - AIR_DRAG * dt).coerceAtLeast(0f)
        grounded = false
        for (crate in crates) crate.onGround = false
        for (block in blocks) block.onGround = false

        ballX += ballVx * dt
        ballY += ballVy * dt
        for (crate in crates) {
            crate.x += crate.vx * dt
            crate.y += crate.vy * dt
        }
        for (block in blocks) {
            block.x += block.vx * dt
            block.y += block.vy * dt
        }
        val fallVy = ballVy

        repeat(4) {
            resolveBallAgainstSolids()
            resolveBallAgainstCrates()
            for (block in blocks) resolveBallAgainstCrate(block)
            resolveCratesAgainstSolids()
            for (block in blocks) resolveCrateAgainstSolids(block)
            if (!scene.builtin) {
                resolveCratesAgainstSlopes()
                for (block in blocks) resolveCrateAgainstSlopes(block)
                resolveCratesAgainstEachOther()
                separateBlocks()
            }
        }
        for (crate in crates) {
            if (crate.onGround) {
                crate.vx *= (1f - CRATE_DRAG * frictionMul(crate.friction) * dt).coerceAtLeast(0f)
            } else if (!crate.gravity) {
                dampFloat(crate, dt)
            }
        }
        for (block in blocks) {
            if (block.onGround) {
                block.vx *= (1f - CRATE_DRAG * frictionMul(block.friction) * dt).coerceAtLeast(0f)
            } else if (!block.gravity) {
                dampFloat(block, dt)
            }
        }
        stepEnemies(dt)
        ballAngle += ballVx / RedBallScene.BALL_R * dt
        if (!wasGrounded && grounded && fallVy > 200f && fallVy > landKick) landKick = fallVy
        wasGrounded = grounded

        applySprings()
        touchEnemies()
        if (dead) return
        collectPickups(dt)

        if (hitsSpike()) {
            val back = if (ballVx >= 0f) -1f else 1f
            takeHit(back * 160f, -200f, respawn = false)
            if (dead) return
        }
        if (ballY > scene.voidY) {
            // Яма и вода под уровнем: одно сердце и возврат на старт.
            // Пока мигание не кончилось, в воду можно упасть снова без второго удара.
            if (invuln > 0f) respawnAtStart() else takeHit(0f, 0f, respawn = true)
            if (dead) return
        }
        if (overlapsDoor()) {
            won = true
            pendingWin = true
            ballVx = 0f
            ballVy = 0f
            return
        }
        touchPortals()
    }

    private fun touchPortals() {
        if (portalHold || scene.portals.isEmpty()) return
        for (i in scene.portals.indices) {
            val portal = scene.portals[i]
            if (portal.role != RedBallPortalRole.ENTRY || portal.linkId.isBlank() || portalAsleep[i]) continue
            val hit = overlaps(portal.box)
            if (!hit) {
                portalArmed[i] = true
                continue
            }
            if (!portalArmed[i]) continue
            portalArmed[i] = false
            portalHold = true
            pendingPortalIndex = i
            ballVx = 0f
            ballVy = 0f
            return
        }
    }

    private fun frictionMul(friction: Float): Float {
        if (friction == RED_BALL_FRICTION) return 1f
        return (friction / RED_BALL_FRICTION).coerceIn(0f, 3f)
    }

    private fun dampFloat(body: RedBallCrate, dt: Float) {
        val keep = (1f - 1.6f * dt).coerceAtLeast(0f)
        body.vx *= keep
        body.vy *= keep
    }

    private fun updateJelly(dt: Float) {
        val stretch = if (!grounded) (abs(ballVy) / 1500f).coerceIn(0f, 0.34f) else 0f
        val target = 1f + stretch
        if (landKick > 0f) {
            val kick = (landKick / 650f).coerceIn(0f, 1.15f)
            jellyV -= kick * 11f
            if (landKick > 240f) {
                puff(ballX, ballY + RedBallScene.BALL_R, RedBallParticle.DUST, 8, 110f, 0.38f, 5.5f)
            }
            landKick = 0f
        }
        jellyV += (target - jellyY) * 240f * dt
        jellyV *= (1f - 8.5f * dt).coerceIn(0f, 1f)
        jellyY = (jellyY + jellyV * dt).coerceIn(0.62f, 1.42f)
        jellyX = (1f / jellyY).coerceIn(0.66f, 1.48f)
    }

    private fun updateParticles(dt: Float) {
        var i = fx.lastIndex
        while (i >= 0) {
            val p = fx[i]
            p.life -= dt
            if (p.life <= 0f) {
                fx.removeAt(i)
            } else {
                if (p.kind != RedBallParticle.SPARK) p.vy += 980f * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
            }
            i--
        }
    }

    private fun puff(x: Float, y: Float, kind: Int, count: Int, speed: Float, life: Float, size: Float) {
        for (n in 0 until count) {
            if (fx.size >= 80) fx.removeAt(0)
            val ang = rng.nextFloat() * 6.2831855f
            val sp = speed * (0.35f + rng.nextFloat())
            val lasting = life * (0.65f + rng.nextFloat() * 0.5f)
            fx += RedBallParticle(
                x = x,
                y = y,
                vx = cos(ang) * sp,
                vy = sin(ang) * sp - speed * 0.35f,
                life = lasting,
                maxLife = lasting,
                kind = kind,
                size = size * (0.55f + rng.nextFloat()),
            )
        }
    }

    private fun stepEnemies(dt: Float) {
        for (enemy in enemies) moveEnemy(enemy, dt)
    }

    private fun moveEnemy(enemy: RedBallEnemy, dt: Float) {
        if (!enemy.alive) return
        enemy.onGround = false
        val speed = ENEMY_SPEED
        enemy.vx = enemy.dir * speed
        val prevX = enemy.x
        enemy.x += enemy.vx * dt
        enemy.y += enemy.vy * dt
        for (solid in scene.solids) resolveEnemySolid(enemy, solid)
        for (solid in scene.concrete) resolveEnemySolid(enemy, solid)
        for (crate in crates) resolveEnemySolid(enemy, crateAabb(crate))
        for (block in blocks) resolveEnemySolid(enemy, crateAabb(block))
        val radius = enemy.size * 0.5f
        for (slope in scene.slopes) {
            val hit = slopeHit(slope, enemy.x, enemy.y, radius, radius * 0.25f) ?: continue
            enemy.x += hit.nx * hit.pen
            enemy.y += hit.ny * hit.pen
            val vn = enemy.vx * hit.nx + enemy.vy * hit.ny
            if (vn < 0f) {
                enemy.vx -= vn * hit.nx
                enemy.vy -= vn * hit.ny
            }
            if (hit.ny < -0.25f) enemy.onGround = true
        }
        val moved = enemy.x - prevX
        enemy.angle += moved / enemy.size * (Math.PI.toFloat() / 2f)
        var turn = false
        val blocked = abs(moved) < abs(speed * dt) * 0.35f
        if (blocked) turn = true
        if (enemy.x > enemy.right) {
            enemy.x = enemy.right
            if (enemy.dir > 0) turn = true
        } else if (enemy.x < enemy.left) {
            enemy.x = enemy.left
            if (enemy.dir < 0) turn = true
        }
        if (enemy.gravity && enemy.onGround && !footingAhead(enemy)) {
            enemy.x = prevX
            turn = true
        }
        if (turn) enemy.dir = -enemy.dir
    }

    private fun footingAhead(enemy: RedBallEnemy): Boolean {
        val half = enemy.size * 0.5f
        val front = enemy.x + enemy.dir * (half + 8f)
        val foot = enemy.y + half
        fun covers(s: RedBallAabb): Boolean =
            front >= s.l && front <= s.r && s.t >= foot - 6f && s.t <= foot + 18f
        if (scene.solids.any { covers(it) }) return true
        if (scene.concrete.any { covers(it) }) return true
        for (crate in crates) if (covers(crateAabb(crate))) return true
        for (block in blocks) if (covers(crateAabb(block))) return true
        for (slope in scene.slopes) {
            val lo = min(slope.x0, slope.x1)
            val hi = kotlin.math.max(slope.x0, slope.x1)
            if (front < lo || front > hi || hi - lo < 1f) continue
            val t = (front - slope.x0) / (slope.x1 - slope.x0)
            val y = slope.y0 + (slope.y1 - slope.y0) * t
            if (y >= foot - 6f && y <= foot + 18f) return true
        }
        return false
    }

    private fun resolveEnemySolid(enemy: RedBallEnemy, solid: RedBallAabb) {
        val half = enemy.size / 2f
        val l = enemy.x - half
        val t = enemy.y - half
        val r = enemy.x + half
        val b = enemy.y + half
        if (r <= solid.l || l >= solid.r || b <= solid.t || t >= solid.b) return
        val penL = r - solid.l
        val penR = solid.r - l
        val penT = b - solid.t
        val penB = solid.b - t
        val least = minOf(penL, penR, penT, penB)
        when (least) {
            penL -> {
                enemy.x -= penL
                if (enemy.vx > 0f) enemy.vx = 0f
            }
            penR -> {
                enemy.x += penR
                if (enemy.vx < 0f) enemy.vx = 0f
            }
            penT -> {
                enemy.y -= penT
                if (enemy.vy > 0f) enemy.vy *= -0.02f
                enemy.onGround = true
            }
            else -> {
                enemy.y += penB
                if (enemy.vy < 0f) enemy.vy = 0f
            }
        }
    }

    private fun touchEnemies() {
        if (dead || won) return
        val radius = RedBallScene.BALL_R
        for (enemy in enemies) {
            if (!enemy.alive) continue
            val half = enemy.size / 2f
            val box = RedBallAabb(enemy.x - half, enemy.y - half, enemy.x + half, enemy.y + half)
            if (!overlaps(box)) continue
            val stomp = ballY < enemy.y - half * 0.25f && ballVy > 20f
            if (stomp) {
                enemy.alive = false
                ballVy = min(ballVy, -560f)
                ballY = min(ballY, enemy.y - half - radius - 0.8f)
                grounded = false
                rising = false
                pendingStomp = true
                puff(enemy.x, enemy.y, RedBallParticle.BOOM, 16, 280f, 0.55f, 8f)
            } else if (invuln <= 0f) {
                val away = if (ballX >= enemy.x) 1f else -1f
                ballX += away * 8f
                takeHit(away * 240f, -200f, respawn = false)
                if (dead) return
            }
        }
    }

    private fun collectPickups(dt: Float) {
        if (magnetTime <= 0f) {
            for (star in stars) {
                if (!star.attached || star.taken) continue
                star.taken = true
                star.attached = false
                pendingStar++
                puff(star.x, star.y, RedBallParticle.SPARK, 8, 160f, 0.4f, 4f)
            }
        }
        val pullRadius = MAGNET_RADIUS
        for (star in stars) {
            if (star.taken) continue
            val dx = ballX - star.x
            val dy = ballY - star.y
            val dist = hypot(dx, dy)
            val pulling = magnetTime > 0f && (star.attached || dist <= pullRadius)
            if (pulling) {
                if (!star.attached) {
                    star.attached = true
                    star.orbit = atan2(star.y - ballY, star.x - ballX)
                }
                if (dist < RedBallScene.BALL_R + 12f) {
                    star.orbit += dt * 2.4f
                    val stick = RedBallScene.BALL_R + 10f
                    star.x = ballX + cos(star.orbit) * stick
                    star.y = ballY + sin(star.orbit) * stick
                } else if (dist > 0.001f) {
                    val step = 740f * dt
                    val k = min(1f, step / dist)
                    star.x += dx * k
                    star.y += dy * k
                }
            } else if (dist <= RedBallScene.BALL_R + 14f) {
                star.taken = true
                pendingStar++
                puff(star.x, star.y, RedBallParticle.SPARK, 8, 150f, 0.4f, 4f)
            }
        }
        val grab = RedBallScene.BALL_R + 18f
        for (magnet in magnets) {
            if (magnet.taken) continue
            val dx = ballX - magnet.x
            val dy = ballY - magnet.y
            if (dx * dx + dy * dy <= grab * grab) {
                magnet.taken = true
                magnetTime = MAGNET_TIME
                pendingMagnet = true
                puff(magnet.x, magnet.y, RedBallParticle.SPARK, 10, 180f, 0.45f, 5f)
            }
        }
        if (magnetTime > 0f) magnetTime = (magnetTime - dt).coerceAtLeast(0f)
    }

    private fun separateBlocks() {
        for (i in blocks.indices) {
            val block = blocks[i]
            for (crate in crates) separateCrates(block, crate)
            for (j in i + 1 until blocks.size) separateCrates(block, blocks[j])
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
        for (s in scene.concrete) if (x >= s.l && x <= s.r) take(s.t)
        for (sl in scene.slopes) {
            val lo = min(sl.x0, sl.x1)
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
        for (c in blocks) {
            val half = c.size / 2f
            if (x >= c.x - half && x <= c.x + half) take(c.y - half)
        }
        return if (best == Float.MAX_VALUE) null else best
    }

    private fun resolveBallAgainstSolids() {
        for (solid in scene.solids) {
            bounceCircle(solid, RedBallScene.BALL_R)
        }
        for (solid in scene.concrete) {
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
            val least = minOf(left, right, top, bottom)
            when (least) {
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
        if (ny < -0.45f) {
            grounded = true
            contactFriction = solid.friction
        }
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
        if (slope.friction != RED_BALL_FRICTION && hit.ny < -0.25f) {
            val tx = -hit.ny
            val ty = hit.nx
            val vt = ballVx * tx + ballVy * ty
            val damp = ((slope.friction - RED_BALL_FRICTION) * 4f * 0.016f).coerceIn(-0.35f, 0.35f)
            ballVx -= vt * damp * tx
            ballVy -= vt * damp * ty
        }
        if (hit.ny < -0.25f) {
            grounded = true
            contactFriction = slope.friction
        }
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
        val invCrate = if (!crate.movable) 0f else 0.55f / crate.mass.coerceAtLeast(0.2f)
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
        if (ny < -0.45f) {
            grounded = true
            contactFriction = crate.friction
        }
    }

    private fun resolveCratesAgainstSolids() {
        for (i in crates.indices) resolveCrateAgainstSolids(crates[i])
    }

    private fun resolveCrateAgainstSolids(crate: RedBallCrate) {
        resolveCrateAgainstList(crate, scene.solids)
        if (scene.concrete.isNotEmpty()) resolveCrateAgainstList(crate, scene.concrete)
    }

    private fun resolveCrateAgainstList(crate: RedBallCrate, solids: List<RedBallAabb>) {
        for (solid in solids) {
            val box = crateAabb(crate)
            if (box.r <= solid.l || box.l >= solid.r || box.b <= solid.t || box.t >= solid.b) continue
            val penL = box.r - solid.l
            val penR = solid.r - box.l
            val penT = box.b - solid.t
            val penB = solid.b - box.t
            val least = minOf(penL, penR, penT, penB)
            when (least) {
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
        val overlapX = ha + hb - abs(dx)
        val overlapY = ha + hb - abs(dy)
        if (overlapX <= 0f || overlapY <= 0f) return
        val invA = if (a.movable) 1f / a.mass.coerceAtLeast(0.2f) else 0f
        val invB = if (b.movable) 1f / b.mass.coerceAtLeast(0.2f) else 0f
        val inv = invA + invB
        if (inv <= 1e-4f) return
        if (overlapX < overlapY) {
            val sign = if (dx >= 0f) 1f else -1f
            a.x -= sign * overlapX * (invA / inv)
            b.x += sign * overlapX * (invB / inv)
            if (a.movable && b.movable && (b.vx - a.vx) * sign < 0f) {
                val va = a.vx
                a.vx = b.vx
                b.vx = va
            }
        } else {
            val sign = if (dy >= 0f) 1f else -1f
            a.y -= sign * overlapY * (invA / inv)
            b.y += sign * overlapY * (invB / inv)
            if (dy > 0f && a.vy > 0f && a.movable) {
                a.vy *= -0.05f
                a.onGround = true
            } else if (dy < 0f && b.vy > 0f && b.movable) {
                b.vy *= -0.05f
                b.onGround = true
            }
        }
    }

    private fun crateAabb(crate: RedBallCrate): RedBallAabb {
        val h = crate.size / 2f
        return RedBallAabb(crate.x - h, crate.y - h, crate.x + h, crate.y + h, crate.friction)
    }

    private fun overlaps(s: RedBallAabb): Boolean {
        val radius = RedBallScene.BALL_R
        return ballX + radius > s.l && ballX - radius < s.r &&
            ballY + radius > s.t && ballY - radius < s.b
    }

    private fun overlapsLadder(): Boolean {
        if (scene.ladders.isEmpty()) return false
        for (ladder in scene.ladders) if (overlaps(ladder)) return true
        return false
    }

    private fun overlapsDoor(): Boolean {
        val d = scene.door
        return ballX > d.l && ballX < d.r && ballY > d.t && ballY < d.b
    }

    /**
     * Один удар снимает одно сердце и включает [INVULN_TIME] секунд,
     * в которые не ранят ни враг, ни шипы, ни вода, ни яма.
     * На нуле сердец мяч погибает. [respawn] — вылезти из ямы на старт.
     */
    private fun takeHit(knockVx: Float, knockVy: Float, respawn: Boolean) {
        if (dead || won || invuln > 0f) return
        hearts -= 1
        invuln = INVULN_TIME
        if (hearts <= 0) {
            hearts = 0
            dead = true
            pendingLose = true
            ballVx = 0f
            ballVy = 0f
            return
        }
        pendingHurt = true
        if (respawn) {
            respawnAtStart()
        } else {
            ballVx = knockVx
            ballVy = knockVy
        }
    }

    private fun respawnAtStart() {
        ballX = scene.startX
        ballY = scene.startY
        ballVx = 0f
        ballVy = 0f
        grounded = false
        wasGrounded = false
        rising = false
        coyote = 0f
    }

    private fun hitsSpike(): Boolean {
        val radius = RedBallScene.BALL_R
        for (spike in scene.spikes) {
            if (abs(ballX - spike.x) > spike.w / 2f + radius * 0.4f) continue
            if (ballY + radius < spike.top || ballY - radius > spike.top + spike.h) continue
            val depth = ((ballY - spike.top) / spike.h).coerceIn(0f, 1f)
            val half = spike.w / 2f * depth
            if (abs(ballX - spike.x) < half + radius * 0.45f) return true
        }
        return false
    }

    companion object {
        const val GRAVITY = 2400f
        const val MAGNET_RADIUS = 230f
        const val MAX_HEARTS = 3
        const val INVULN_TIME = 2f
        const val HURT_BLINK_HZ = 8f
        const val HURT_BLINK_ALPHA = 0.18f
        private const val MAGNET_TIME = 4.6f
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
        private const val CLIMB_SPEED = 250f
        private const val LADDER_FALL = 150f
        private const val ENEMY_SPEED = 86f
    }
}

private fun RedBallCrateSpawn.toBody() = RedBallCrate(
    x = x,
    y = y,
    vx = 0f,
    vy = 0f,
    size = size,
    homeX = x,
    homeY = y,
    gravity = gravity,
    movable = movable,
    friction = friction,
    mass = mass,
)

private fun RedBallEnemySpawn.toEnemy() = RedBallEnemy(
    x = x,
    y = y,
    size = size,
    homeX = x,
    homeY = y,
    left = left,
    right = right,
    gravity = gravity,
    friction = friction,
    mass = mass,
)

class RedBallCrate(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val size: Float,
    val homeX: Float,
    val homeY: Float,
    val gravity: Boolean = true,
    val movable: Boolean = true,
    val friction: Float = RED_BALL_FRICTION,
    val mass: Float = 1f,
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

class RedBallEnemy(
    var x: Float,
    var y: Float,
    val size: Float,
    val homeX: Float,
    val homeY: Float,
    val left: Float,
    val right: Float,
    val gravity: Boolean,
    val friction: Float,
    val mass: Float,
) {
    var vx: Float = 0f
    var vy: Float = 0f
    var dir: Int = 1
    var angle: Float = 0f
    var alive: Boolean = true
    var onGround: Boolean = false

    fun reset() {
        x = homeX
        y = homeY
        vx = 0f
        vy = 0f
        dir = 1
        angle = 0f
        alive = true
        onGround = false
    }
}

class RedBallStar(
    var x: Float,
    var y: Float,
    val homeX: Float,
    val homeY: Float,
    var orbit: Float,
) {
    var taken: Boolean = false
    var attached: Boolean = false

    fun reset() {
        x = homeX
        y = homeY
        taken = false
        attached = false
    }
}

class RedBallMagnet(
    val x: Float,
    val y: Float,
    var taken: Boolean = false,
)

class RedBallParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val kind: Int,
    val size: Float,
) {
    companion object {
        const val DUST = 0
        const val SPARK = 1
        const val BOOM = 2
    }
}
