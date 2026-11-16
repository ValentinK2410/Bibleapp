package com.example.bible

import com.example.bible.games.RedBallSim
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedBallSimTest {

    @Test
    fun ballRestsOnTheStartPlatform() {
        val sim = RedBallSim()
        repeat(90) { sim.step(1f / 60f, 0f) }
        assertFalse(sim.dead)
        assertTrue(sim.ballY > 590f)
        assertTrue(sim.ballY < 640f)
        assertTrue(kotlin.math.abs(sim.ballVx) < 30f)
    }

    @Test
    fun holdingRightRollsDownTheSlopeAndPushesTheCrate() {
        val sim = RedBallSim()
        val startCrate = sim.crateX
        repeat(60 * 8) { sim.step(1f / 60f, 1f) }
        assertFalse("мяч погиб на пути к ящику, x=${sim.ballX}", sim.dead)
        assertTrue("мяч не докатился: x=${sim.ballX}", sim.ballX > 900f)
        assertTrue("ящик не сдвинулся: ${sim.crateX}", sim.crateX > startCrate + 40f)
    }

    @Test
    fun springThrowsTheBallUpward() {
        val sim = RedBallSim()
        sim.ballX = 1700f
        sim.ballY = 700f
        sim.ballVx = 80f
        sim.ballVy = 40f
        sim.step(1f / 60f, 0f)
        assertTrue("пружина не подбросила: vy=${sim.ballVy}", sim.ballVy < -800f)
        assertTrue(sim.consumeSpring())
    }

    @Test
    fun rollingRightReachesTheDoor() {
        val sim = RedBallSim()
        repeat(60 * 20) { sim.step(1f / 60f, 1f) }
        assertFalse("мяч погиб, x=${sim.ballX} y=${sim.ballY} crate=${sim.crateX}", sim.dead)
        assertTrue("дверь не достигнута, x=${sim.ballX} y=${sim.ballY}", sim.won)
    }
}
