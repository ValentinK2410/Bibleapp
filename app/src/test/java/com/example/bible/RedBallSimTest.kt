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
