package com.example.bible

import com.example.bible.games.RedBallAabb
import com.example.bible.games.RedBallCartSpawn
import com.example.bible.games.RedBallGaze
import com.example.bible.games.RedBallGazeMath
import com.example.bible.games.RedBallParticle
import com.example.bible.games.RedBallScene
import com.example.bible.games.RedBallSim
import com.example.bible.games.RedBallWater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RedBallGazeCartDiveTest {

    @Test
    fun gazeFollowsRollDirectionAndSlope() {
        val right = RedBallGazeMath.target(320f, 0f, grounded = true, rising = false)
        assertTrue("вправо: x=${right.x}", right.x > 0.8f)
        assertTrue("на ровном взгляд не смотрит вниз: y=${right.y}", abs(right.y) < 0.15f)

        val left = RedBallGazeMath.target(-320f, 4f, grounded = true, rising = false)
        assertTrue("влево: x=${left.x}", left.x < -0.8f)
        assertTrue(abs(left.y) < 0.15f)

        val uphill = RedBallGazeMath.target(180f, -160f, grounded = true, rising = false)
        assertTrue("вверх по горке смотрит вправо: x=${uphill.x}", uphill.x > 0.4f)
        assertTrue("вверх по горке смотрит вверх: y=${uphill.y}", uphill.y < -0.5f)

        val downhill = RedBallGazeMath.target(180f, 160f, grounded = true, rising = false)
        assertTrue(downhill.x > 0.4f)
        assertTrue("вниз по горке смотрит вниз: y=${downhill.y}", downhill.y > 0.5f)
    }

    @Test
    fun gazeLooksUpWhileRisingAndDownWhileFalling() {
        val rising = RedBallGazeMath.target(40f, -500f, grounded = false, rising = true)
        assertTrue("прыжок смотрит вверх: y=${rising.y}", rising.y < -0.45f)

        val falling = RedBallGazeMath.target(20f, 480f, grounded = false, rising = false)
        assertTrue("падение смотрит вниз: y=${falling.y}", falling.y > 0.45f)
    }

    @Test
    fun gazeStepsTowardTheTargetWithoutSnapping() {
        val target = RedBallGazeMath.target(400f, 0f, grounded = true, rising = false)
        assertTrue(target.x > 0.9f)
        val next = RedBallGazeMath.step(RedBallGaze(0f, 0f), target, 1f / 60f)
        assertTrue("взгляд не сдвинулся: x=${next.x}", next.x > 0.04f)
        assertTrue("взгляд прыгнул к цели: x=${next.x}", next.x < target.x * 0.45f)

        var gaze = RedBallGaze(0f, 0f)
        val aim = RedBallGaze(1f, -0.6f)
        repeat(90) { gaze = RedBallGazeMath.step(gaze, aim, 1f / 60f) }
        assertTrue("за 1.5 с не дошёл по X: ${gaze.x}", gaze.x > 0.9f)
        assertTrue("за 1.5 с не дошёл по Y: ${gaze.y}", gaze.y < -0.5f)
    }

    @Test
    fun simGazeMovesSmoothlyWhileRolling() {
        val sim = RedBallSim()
        repeat(20) { sim.step(1f / 60f, 0f) }
        val before = sim.gazeX
        sim.ballVx = 400f
        sim.step(1f / 60f, 1f)
        assertTrue("симулятор не повёл взгляд: было $before стало ${sim.gazeX}", sim.gazeX > before + 0.01f)
        assertTrue("симулятор щёлкнул взгляд: ${sim.gazeX}", sim.gazeX < 0.85f)
    }

    @Test
    fun ballEntersCartRidesAndJumpExits() {
        val sim = RedBallSim(cartScene())
        sim.ballX = 420f
        sim.ballY = 430f
        sim.ballVy = 40f
        repeat(30) { sim.step(1f / 60f, 0f) }
        assertTrue("мяч не сел в тележку, x=${sim.ballX} y=${sim.ballY}", sim.ridingCart)
        val ridden = sim.ballX
        repeat(50) { sim.step(1f / 60f, 1f) }
        assertTrue(sim.ridingCart)
        assertTrue("тележка не поехала вправо: было $ridden стало ${sim.ballX}", sim.ballX > ridden + 25f)
        sim.jumpHeld = true
        sim.jump()
        sim.step(1f / 60f, 0f)
        assertFalse("прыжок не высадил из тележки", sim.ridingCart)
        assertTrue("выпрыгивание не дало скорость вверх: vy=${sim.ballVy}", sim.ballVy < -400f)
    }

    @Test
    fun breathRunsOutUnderwaterAndCostsOneHeart() {
        val sim = RedBallSim(pondScene())
        sim.ballX = 700f
        sim.ballY = 680f
        sim.ballVy = 0f
        val full = sim.breath
        repeat(45) { sim.step(1f / 60f, 0f, dive = 1f) }
        assertTrue(sim.submerged)
        assertTrue("воздух не тратится: ${sim.breath}", sim.breath < full - 0.1f)
        assertEquals(RedBallSim.MAX_HEARTS, sim.hearts)

        var guard = 0
        while (sim.hearts == RedBallSim.MAX_HEARTS && guard < 500) {
            sim.ballX = 700f
            sim.ballY = 680f
            sim.ballVy = 40f
            sim.step(1f / 60f, 0f, dive = 1f)
            guard++
        }
        assertEquals("ныряние не сняло сердце", RedBallSim.MAX_HEARTS - 1, sim.hearts)
        assertTrue(sim.invuln > 0f)
        assertFalse(sim.dead)
        assertTrue("после глотка воздуха шкала пустая", sim.breath > 0.2f)
    }

    @Test
    fun holdingDownDivesAndSurfaceRefillsBreath() {
        val sim = RedBallSim(pondScene())
        sim.ballX = 700f
        sim.ballY = 520f - RedBallScene.BALL_R
        sim.ballVy = 40f
        repeat(25) { sim.step(1f / 60f, 0f, dive = 0f) }
        assertFalse("без кнопки вниз мяч ушёл под воду, y=${sim.ballY}", sim.submerged)
        assertTrue("мяч не держится на поверхности: y=${sim.ballY}", sim.ballY < 540f)
        val afloat = sim.ballY
        repeat(50) { sim.step(1f / 60f, 0f, dive = 1f) }
        assertTrue("кнопка вниз не погрузила: y=${sim.ballY} было $afloat", sim.ballY > afloat + 24f)

        repeat(80) { sim.step(1f / 60f, 0f, dive = 1f) }
        val low = sim.breath
        sim.ballX = 140f
        sim.ballY = 472f
        sim.ballVx = 0f
        sim.ballVy = 0f
        repeat(100) { sim.step(1f / 60f, 0f, dive = 0f) }
        assertFalse(sim.submerged)
        assertTrue("на берегу воздух не вернулся: было $low стало ${sim.breath}", sim.breath > low + 0.25f)
    }

    @Test
    fun landingInWaterSplashesInsteadOfDust() {
        val sim = RedBallSim(pondScene())
        sim.ballX = 700f
        sim.ballY = 360f
        sim.ballVy = 420f
        var sawSplash = false
        var sawPuff = false
        repeat(50) {
            sim.step(1f / 60f, 0f, dive = 0f)
            val kinds = sim.particles.map { it.kind }
            if (RedBallParticle.SPLASH in kinds) sawSplash = true
            if (RedBallParticle.PUFF in kinds) sawPuff = true
        }
        assertTrue("нет всплеска", sawSplash)
        assertFalse("пыль на воде", sawPuff)
        assertFalse("после падения мяч остался под водой, y=${sim.ballY}", sim.submerged)
    }

    @Test
    fun builtinPondHasUnderwaterStarsAndACart() {
        val scene = RedBallScene.builtin()
        assertTrue(scene.carts.isNotEmpty())
        val pond = scene.waters.first()
        assertTrue("пруд мелкий: ${pond.b - pond.t}", pond.b - pond.t > 140f)
        assertTrue(
            scene.stars.any { star ->
                star.y > pond.t + 40f && star.x in pond.l..pond.r && star.y < pond.b
            },
        )
    }

    private fun cartScene(): RedBallScene {
        return RedBallScene(
            id = "cart",
            name = "Тележка",
            builtin = false,
            worldW = 1600f,
            worldH = 800f,
            voidY = 760f,
            startX = 80f,
            startY = 472f,
            solids = listOf(RedBallAabb(0f, 500f, 1500f, 760f)),
            slopes = emptyList(),
            spikes = emptyList(),
            springs = emptyList(),
            door = RedBallAabb(1400f, 340f, 1480f, 500f),
            crates = emptyList(),
            carts = listOf(RedBallCartSpawn(420f, 500f - 23f, 120f, 46f)),
        )
    }

    private fun pondScene(): RedBallScene {
        return RedBallScene(
            id = "pond",
            name = "Пруд",
            builtin = false,
            worldW = 1400f,
            worldH = 900f,
            voidY = 860f,
            startX = 120f,
            startY = 472f,
            solids = listOf(
                RedBallAabb(0f, 500f, 360f, 860f),
                RedBallAabb(360f, 760f, 1200f, 860f),
            ),
            slopes = emptyList(),
            spikes = emptyList(),
            springs = emptyList(),
            door = RedBallAabb(200f, 300f, 280f, 500f),
            crates = emptyList(),
            waters = listOf(RedBallWater(360f, 520f, 1200f, 760f)),
        )
    }
}
