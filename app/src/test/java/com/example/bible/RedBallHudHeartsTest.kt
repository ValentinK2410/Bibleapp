package com.example.bible

import com.example.bible.games.HUD_HEART_FADE_MS
import com.example.bible.games.RedBallAabb
import com.example.bible.games.RedBallScene
import com.example.bible.games.RedBallSim
import com.example.bible.games.RedBallSpike
import com.example.bible.games.hudHeartPop
import com.example.bible.games.hudHeartsAfterLoss
import com.example.bible.games.visibleHudHeartCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedBallHudHeartsTest {

    @Test
    fun visibleCountDropsLostHeartsAndClamps() {
        assertEquals(RedBallSim.MAX_HEARTS, visibleHudHeartCount(RedBallSim.MAX_HEARTS))
        assertEquals(2, visibleHudHeartCount(2))
        assertEquals(1, visibleHudHeartCount(1))
        assertEquals(0, visibleHudHeartCount(0))
        assertEquals(0, visibleHudHeartCount(-4))
        assertEquals(RedBallSim.MAX_HEARTS, visibleHudHeartCount(99))
        assertEquals(0, visibleHudHeartCount(2, maxHearts = 0))
        assertEquals(1, visibleHudHeartCount(2, maxHearts = 1))
    }

    @Test
    fun lossFadesTheRightmostHeartAndRestartClearsIt() {
        val one = hudHeartsAfterLoss(3, 2)
        assertEquals(2, one.shown)
        assertEquals(listOf(2), one.fading)

        val next = hudHeartsAfterLoss(one.shown, 1)
        assertEquals(1, next.shown)
        assertEquals(listOf(1), next.fading)

        val all = hudHeartsAfterLoss(RedBallSim.MAX_HEARTS, 0)
        assertEquals(0, all.shown)
        assertEquals(listOf(0, 1, 2), all.fading)

        val same = hudHeartsAfterLoss(2, 2)
        assertEquals(2, same.shown)
        assertTrue(same.fading.isEmpty())

        val restart = hudHeartsAfterLoss(0, RedBallSim.MAX_HEARTS)
        assertEquals(RedBallSim.MAX_HEARTS, restart.shown)
        assertTrue(restart.fading.isEmpty())
    }

    @Test
    fun lostHeartPopsUpAndFadesOutIn250ms() {
        assertEquals(250, HUD_HEART_FADE_MS)
        val start = hudHeartPop(0f)
        assertEquals(1f, start.scale, 0.001f)
        assertEquals(1f, start.alpha, 0.001f)
        val mid = hudHeartPop(0.5f)
        assertEquals(1.2f, mid.scale, 0.001f)
        assertEquals(0.5f, mid.alpha, 0.001f)
        val end = hudHeartPop(1f)
        assertEquals(1.4f, end.scale, 0.001f)
        assertEquals(0f, end.alpha, 0.001f)
        val over = hudHeartPop(3f)
        assertEquals(end.scale, over.scale, 0.001f)
        assertEquals(0f, over.alpha, 0.001f)
        val before = hudHeartPop(-1f)
        assertEquals(start.scale, before.scale, 0.001f)
        assertEquals(1f, before.alpha, 0.001f)
    }

    @Test
    fun spikeRemovesOneVisibleHeartAndResetBringsThemBack() {
        val scene = RedBallScene(
            id = "hud",
            name = "Сердца",
            builtin = false,
            worldW = 800f,
            worldH = 600f,
            voidY = 560f,
            startX = 80f,
            startY = 200f,
            solids = listOf(RedBallAabb(0f, 400f, 800f, 560f)),
            slopes = emptyList(),
            spikes = listOf(RedBallSpike(80f, 340f, 80f, 60f)),
            springs = emptyList(),
            door = RedBallAabb(700f, 200f, 760f, 400f),
            crates = emptyList(),
        )
        val sim = RedBallSim(scene)
        assertEquals(RedBallSim.MAX_HEARTS, visibleHudHeartCount(sim.hearts))
        repeat(5) {
            sim.ballX = 80f
            sim.ballY = 372f
            sim.ballVx = 0f
            sim.ballVy = 0f
            sim.step(1f / 60f, 0f)
        }
        assertEquals("шипы сняли не одно сердце", RedBallSim.MAX_HEARTS - 1, sim.hearts)
        val loss = hudHeartsAfterLoss(RedBallSim.MAX_HEARTS, sim.hearts)
        assertEquals(2, visibleHudHeartCount(sim.hearts))
        assertEquals(listOf(2), loss.fading)
        sim.reset()
        assertEquals(RedBallSim.MAX_HEARTS, sim.hearts)
        assertEquals(RedBallSim.MAX_HEARTS, visibleHudHeartCount(sim.hearts))
        val back = hudHeartsAfterLoss(loss.shown, sim.hearts)
        assertEquals(RedBallSim.MAX_HEARTS, back.shown)
        assertTrue(back.fading.isEmpty())
    }
}
