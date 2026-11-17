package com.example.bible

import com.example.bible.games.RedBallDraft
import com.example.bible.games.RedBallLevelStore
import com.example.bible.games.RedBallPiece
import com.example.bible.games.RedBallPieceKind
import com.example.bible.games.RedBallScene
import com.example.bible.games.RedBallSim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

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
    fun jumpLiftsTheBallAndLandsBack() {
        val sim = RedBallSim()
        repeat(90) { sim.step(1f / 60f, 0f) }
        val restY = sim.ballY
        sim.jumpHeld = true
        sim.jump()
        var top = restY
        repeat(30) {
            sim.step(1f / 60f, 0f)
            top = minOf(top, sim.ballY)
        }
        sim.jumpHeld = false
        assertTrue("прыжок слишком низкий: ${restY - top}", restY - top > 100f)
        repeat(90) { sim.step(1f / 60f, 0f) }
        assertFalse(sim.dead)
        assertTrue("мяч не приземлился: y=${sim.ballY}", kotlin.math.abs(sim.ballY - restY) < 4f)
    }

    @Test
    fun shortTapGivesALowerJump() {
        fun peak(hold: Boolean): Float {
            val sim = RedBallSim()
            repeat(90) { sim.step(1f / 60f, 0f) }
            val restY = sim.ballY
            sim.jumpHeld = true
            sim.jump()
            sim.step(1f / 60f, 0f)
            sim.jumpHeld = hold
            var top = restY
            repeat(40) {
                sim.step(1f / 60f, 0f)
                top = minOf(top, sim.ballY)
            }
            return restY - top
        }
        assertTrue(peak(hold = false) < peak(hold = true) - 40f)
    }

    @Test
    fun noJumpInMidAir() {
        val sim = RedBallSim()
        repeat(90) { sim.step(1f / 60f, 0f) }
        sim.jumpHeld = true
        sim.jump()
        repeat(12) { sim.step(1f / 60f, 0f) }
        val vyBefore = sim.ballVy
        sim.jump()
        sim.step(1f / 60f, 0f)
        assertTrue("второй прыжок в воздухе", sim.ballVy > vyBefore)
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

    @Test
    fun builtinSceneKeepsTheOriginalGeometry() {
        val scene = RedBallScene.builtin()
        assertEquals(2680f, scene.worldW)
        assertEquals(170f, scene.startX)
        assertEquals(640f - 28f, scene.startY)
        assertEquals(1140f, scene.crates.single().x)
        assertEquals(96f, scene.crates.single().size)
        assertEquals(1660f, scene.springs.single().l)
        assertEquals(2380f, scene.door.l)
        assertEquals(3, scene.spikes.size)
    }

    @Test
    fun freshLevelRestsThenReachesTheDoor() {
        val sim = RedBallSim(RedBallDraft.fresh("Проба").toScene())
        repeat(90) { sim.step(1f / 60f, 0f) }
        assertFalse(sim.dead)
        assertTrue("мяч уехал с полки: x=${sim.ballX} y=${sim.ballY}", kotlin.math.abs(sim.ballY - 592f) < 3f)
        repeat(60 * 15) { sim.step(1f / 60f, 1f) }
        assertFalse("мяч погиб, x=${sim.ballX} y=${sim.ballY}", sim.dead)
        assertTrue("дверь не достигнута, x=${sim.ballX} y=${sim.ballY}", sim.won)
    }

    @Test
    fun customCrateCanBePushedAndSpikesHurt() {
        val draft = flatDraft(
            RedBallPiece(100, RedBallPieceKind.CRATE, 6, 8, 2, 2, false),
        )
        val sim = RedBallSim(draft.toScene())
        val start = sim.crateX
        repeat(60 * 4) { sim.step(1f / 60f, 1f) }
        assertTrue("ящик не сдвинулся: ${sim.crateX}", sim.crateX > start + 20f)

        val spiked = flatDraft(
            RedBallPiece(100, RedBallPieceKind.SPIKE, 6, 8, 1, 2, false),
        )
        val doomed = RedBallSim(spiked.toScene())
        repeat(60 * 4) { doomed.step(1f / 60f, 1f) }
        assertTrue("шипы не сработали, x=${doomed.ballX} y=${doomed.ballY}", doomed.dead)
    }

    @Test
    fun editorRulesAndJsonRoundTrip() {
        val fresh = RedBallDraft.fresh("Горка у дома")
        assertNull(fresh.playBlockReason())
        val noDoor = fresh.copy(pieces = fresh.pieces.filter { it.kind != RedBallPieceKind.DOOR })
        assertNotNull(noDoor.playBlockReason())
        val parsed = RedBallDraft.fromJson(fresh.toJson())
        assertNotNull(parsed)
        assertEquals(fresh.name, parsed!!.name)
        assertEquals(fresh.pieces, parsed.pieces)
        val scene = fresh.toScene()
        val grounds = scene.solids.filter { it.l >= 0f && it.r <= scene.worldW + 0.1f }
        assertEquals(2, grounds.size)
        val gapped = flatDraft().copy(
            pieces = listOf(
                RedBallPiece(1, RedBallPieceKind.GROUND, 0, 10, 2, 2, false),
                RedBallPiece(2, RedBallPieceKind.GROUND, 6, 10, 2, 2, false),
                RedBallPiece(3, RedBallPieceKind.START, 1, 9, 1, 1, false),
                RedBallPiece(4, RedBallPieceKind.DOOR, 16, 6, 2, 4, false),
            ),
        )
        val gapSolids = gapped.toScene().solids.filter { it.l >= 0f && it.r <= gapped.worldW + 0.1f }
        assertEquals(2, gapSolids.size)
    }

    @Test
    fun ballPastTheSlopeEndFallsOntoThePlatform() {
        val sim = RedBallSim()
        // x=400 левее кромки склона (470): точка на продолжении прямой,
        // но уже над плоской верхней площадкой, а не на самом отрезке.
        placeBallOnSlopeLine(sim, 400f)
        val startY = sim.ballY
        assertTrue("старт должен быть выше площадки, y=$startY", startY < 600f)
        repeat(90) { sim.step(1f / 60f, 0f) }
        assertFalse("мяч погиб, x=${sim.ballX} y=${sim.ballY}", sim.dead)
        assertTrue(
            "мяч уехал по невидимому склону: x=${sim.ballX} y=${sim.ballY}",
            sim.ballX > 300f && sim.ballX < 450f,
        )
        assertTrue(
            "мяч не спустился на площадку: start=$startY y=${sim.ballY}",
            sim.ballY > startY + 10f && sim.ballY > 600f && sim.ballY < 645f,
        )
    }

    @Test
    fun rollingUpTheSlopeComesDownAfterTheCrest() {
        val sim = RedBallSim()
        placeBallOnSlopeLine(sim, 700f)
        sim.ballVx = -80f
        var passedCrest = false
        var minYAfterCrest = Float.POSITIVE_INFINITY
        repeat(60 * 4) {
            sim.step(1f / 60f, -1f)
            if (sim.ballX < 440f) {
                passedCrest = true
                if (sim.ballY < minYAfterCrest) minYAfterCrest = sim.ballY
            }
        }
        assertFalse("мяч погиб, x=${sim.ballX} y=${sim.ballY}", sim.dead)
        assertTrue("мяч не пересёк кромку склона, x=${sim.ballX}", passedCrest)
        assertTrue(
            "за кромкой мяч продолжил ехать вверх: minY=$minYAfterCrest x=${sim.ballX}",
            minYAfterCrest > 590f,
        )
        assertTrue(
            "после склона мяч не оказался на верхней площадке: x=${sim.ballX} y=${sim.ballY}",
            sim.ballX < 470f && sim.ballY > 600f && sim.ballY < 645f,
        )
    }

    /** Центр мяча на бесконечной прямой склона в точке с заданным x (в том числе за торцом). */
    private fun placeBallOnSlopeLine(sim: RedBallSim, surfaceX: Float) {
        val slope = sim.scene.slopes.first()
        val dx = slope.x1 - slope.x0
        val dy = slope.y1 - slope.y0
        val along = (surfaceX - slope.x0) / dx
        val sx = slope.x0 + dx * along
        val sy = slope.y0 + dy * along
        var nx = -dy
        var ny = dx
        if (ny > 0f) {
            nx = -nx
            ny = -ny
        }
        val nLen = kotlin.math.sqrt(nx * nx + ny * ny)
        sim.ballX = sx + nx / nLen * RedBallScene.BALL_R
        sim.ballY = sy + ny / nLen * RedBallScene.BALL_R
        sim.ballVx = 0f
        sim.ballVy = 0f
    }

    @Test
    fun levelsSurviveAFileRoundTrip() {
        val dir = Files.createTempDirectory("redball-levels").toFile()
        val store = RedBallLevelStore(dir)
        val draft = RedBallDraft.fresh("Моя горка").copy(updatedAt = 42L)
        store.save(draft)
        val loaded = store.load(draft.id)
        assertEquals("Моя горка", loaded?.name)
        assertEquals(draft.pieces, loaded?.pieces)
        store.rename(draft.id, "Новое имя")
        assertEquals("Новое имя", store.list().single().name)
        store.delete(draft.id)
        assertTrue(store.list().isEmpty())
    }

    private fun flatDraft(extra: RedBallPiece? = null): RedBallDraft {
        val pieces = mutableListOf<RedBallPiece>()
        var id = 1L
        for (c in 0 until 20 step 2) {
            pieces += RedBallPiece(id++, RedBallPieceKind.GROUND, c, 10, 2, 2, false)
        }
        pieces += RedBallPiece(id++, RedBallPieceKind.START, 1, 9, 1, 1, false)
        pieces += RedBallPiece(id, RedBallPieceKind.DOOR, 16, 6, 2, 4, false)
        if (extra != null) pieces += extra
        return RedBallDraft(
            id = "flat",
            name = "Плоский",
            cols = 20,
            rows = 12,
            cell = 40f,
            originY = 100f,
            pieces = pieces,
            nextId = id + 2,
        )
    }
}
