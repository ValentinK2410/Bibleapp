package com.example.bible

import com.example.bible.games.RED_BALL_FRICTION
import com.example.bible.games.RedBallAabb
import com.example.bible.games.RedBallCrateSpawn
import com.example.bible.games.RedBallDraft
import com.example.bible.games.RedBallEnemySpawn
import com.example.bible.games.RedBallLevelStore
import com.example.bible.games.RedBallMagnetSpawn
import com.example.bible.games.RedBallPiece
import com.example.bible.games.RedBallPieceKind
import com.example.bible.games.RedBallPortal
import com.example.bible.games.RedBallPortalRole
import com.example.bible.games.RedBallScene
import com.example.bible.games.RedBallSceneKind
import com.example.bible.games.RedBallSim
import com.example.bible.games.RedBallSpike
import com.example.bible.games.allowedPortalTargetIds
import com.example.bible.games.RedBallStarSpawn
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
        var hurt = false
        var heartsWhenHurt = RedBallSim.MAX_HEARTS
        repeat(60 * 4) {
            val before = doomed.hearts
            doomed.step(1f / 60f, 1f)
            if (!hurt && doomed.hearts < before) {
                hurt = true
                heartsWhenHurt = doomed.hearts
            }
        }
        assertTrue("шипы не сработали, x=${doomed.ballX} y=${doomed.ballY}", hurt)
        assertEquals("шипы сняли не одно сердце", RedBallSim.MAX_HEARTS - 1, heartsWhenHurt)
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

    @Test
    fun stompKillsEnemyAndSideContactCostsOneHeart() {
        val scene = arena(enemies = listOf(RedBallEnemySpawn(420f, 460f, 80f, 280f, 560f)))
        val stomp = RedBallSim(scene)
        val enemy = stomp.enemies.single()
        stomp.ballX = enemy.x
        stomp.ballY = enemy.y - 52f
        stomp.ballVy = 420f
        stomp.step(1f / 60f, 0f)
        assertFalse("враг пережил прыжок сверху", enemy.alive)
        assertFalse("мяч погиб от своего прыжка", stomp.dead)
        assertEquals(RedBallSim.MAX_HEARTS, stomp.hearts)
        assertEquals(0f, stomp.invuln, 0.001f)
        assertTrue(stomp.consumeStomp())

        val side = RedBallSim(scene)
        val walker = side.enemies.single()
        side.ballX = walker.x - walker.size / 2f - 8f
        side.ballY = walker.y
        side.ballVy = 0f
        side.step(1f / 60f, 0f)
        assertFalse("боковой удар не должен забирать все сердца", side.dead)
        assertEquals(RedBallSim.MAX_HEARTS - 1, side.hearts)
        assertTrue("после удара нет неуязвимости", side.invuln > 0f)
        assertTrue("враг исчез от касания сбоку", walker.alive)
        assertTrue(side.consumeHurt())
        assertFalse(side.consumeLose())

        side.ballX = walker.x
        side.ballY = walker.y - 52f
        side.ballVy = 420f
        side.step(1f / 60f, 0f)
        assertFalse("враг пережил прыжок во время мигания", walker.alive)
        assertEquals("прыжок сверху снял сердце", RedBallSim.MAX_HEARTS - 1, side.hearts)
    }

    @Test
    fun enemyTouchCostsOneHeartThenAnotherOnlyAfterInvuln() {
        val scene = arena(enemies = listOf(RedBallEnemySpawn(420f, 460f, 80f, 280f, 560f)))
        val sim = RedBallSim(scene)
        val blinks = HashSet<Float>()
        repeat((1.5f * 60f).toInt()) {
            pinOntoEnemy(sim)
            sim.step(1f / 60f, 0f)
            blinks += sim.hurtBlink()
        }
        assertEquals("за 1.5 с касания должно пропасть ровно одно сердце", 2, sim.hearts)
        assertFalse(sim.dead)
        assertTrue("враг не должен умереть от бокового касания", sim.enemies.single().alive)
        assertTrue("неуязвимость кончилась раньше 1.5 с", sim.invuln > 0.3f)
        assertTrue("мигание не включается", blinks.contains(1f))
        assertTrue("мигание не гаснет", blinks.contains(RedBallSim.HURT_BLINK_ALPHA))

        repeat((2.5f * 60f).toInt()) {
            parkOnStart(sim)
            sim.step(1f / 60f, 0f)
        }
        assertEquals("за ожидание в стороне снялось ещё сердце", 2, sim.hearts)
        assertEquals(0f, sim.invuln, 0.001f)
        assertEquals(1f, sim.hurtBlink(), 0.001f)

        pinOntoEnemy(sim)
        sim.step(1f / 60f, 0f)
        assertEquals("повторное касание после 2 с не сняло сердце", 1, sim.hearts)
        assertFalse(sim.dead)
        assertTrue(sim.invuln > RedBallSim.INVULN_TIME - 0.05f)
    }

    @Test
    fun spikeAndPitShareTheInvulnWindow() {
        val spiked = arena(spikes = listOf(RedBallSpike(200f, 400f, 120f, 120f)))
        val onSpikes = RedBallSim(spiked)
        repeat((1.5f * 60f).toInt()) {
            onSpikes.ballX = 200f
            onSpikes.ballY = 472f
            onSpikes.ballVx = 0f
            onSpikes.ballVy = 0f
            onSpikes.step(1f / 60f, 0f)
        }
        assertEquals(2, onSpikes.hearts)
        assertFalse(onSpikes.dead)
        assertTrue(onSpikes.invuln > 0.3f)

        val pit = RedBallSim(arena())
        repeat((1.5f * 60f).toInt()) {
            pit.ballX = 400f
            pit.ballY = pit.scene.voidY + 40f
            pit.ballVx = 0f
            pit.ballVy = 40f
            pit.step(1f / 60f, 0f)
        }
        assertEquals("яма сняла не одно сердце", 2, pit.hearts)
        assertFalse(pit.dead)
        assertEquals(pit.scene.startX, pit.ballX, 0.1f)
        assertEquals(pit.scene.startY, pit.ballY, 0.1f)
        repeat(60 * 3) {
            parkOnStart(pit)
            pit.step(1f / 60f, 0f)
        }
        assertEquals(0f, pit.invuln, 0.001f)
        pit.ballY = pit.scene.voidY + 40f
        pit.step(1f / 60f, 0f)
        assertEquals("вторая яма после неуязвимости не сняла сердце", 1, pit.hearts)
        assertEquals(pit.scene.startX, pit.ballX, 0.1f)
    }

    @Test
    fun magnetPullsNearbyStarsAndLeavesFarOnes() {
        val radius = RedBallSim.MAGNET_RADIUS
        val home = 80f
        val scene = arena(
            magnets = listOf(RedBallMagnetSpawn(home, 472f)),
            stars = listOf(
                RedBallStarSpawn(home + radius * 0.55f, 460f),
                RedBallStarSpawn(home + radius * 3f, 460f),
            ),
        )
        val sim = RedBallSim(scene)
        val near = sim.stars[0]
        val far = sim.stars[1]
        val farX = far.x
        val nearStart = kotlin.math.abs(near.x - sim.ballX)
        repeat(40) { sim.step(1f / 60f, 0f) }
        assertTrue("магнит не подобрался", sim.magnetTime > 0f)
        assertTrue(
            "ближняя звезда не полетела к мячу: x=${near.x} мяч=${sim.ballX}",
            near.attached || near.taken || kotlin.math.abs(near.x - sim.ballX) < nearStart - 40f,
        )
        assertFalse(far.attached)
        assertFalse(far.taken)
        assertEquals(farX, far.x, 0.01f)
    }

    @Test
    fun gravityOffObjectStaysPut() {
        val scene = arena(
            crates = listOf(RedBallCrateSpawn(360f, 180f, 80f, gravity = false, movable = true)),
        )
        val sim = RedBallSim(scene)
        val y = sim.crateY
        val x = sim.crateX
        repeat(120) { sim.step(1f / 60f, 0f) }
        assertEquals(x, sim.crateX, 0.5f)
        assertEquals(y, sim.crateY, 0.5f)
        assertFalse(sim.dead)
    }

    @Test
    fun pushableObjectMovesAndStaticOneDoesNot() {
        val pushable = RedBallSim(
            arena(crates = listOf(RedBallCrateSpawn(300f, 460f, 80f, gravity = true, movable = true))),
        )
        val start = pushable.crateX
        repeat(60 * 3) { pushable.step(1f / 60f, 1f) }
        assertTrue("толкаемый ящик не сдвинулся: ${pushable.crateX}", pushable.crateX > start + 24f)

        val fixed = RedBallSim(
            arena(crates = listOf(RedBallCrateSpawn(300f, 460f, 80f, gravity = true, movable = false, mass = 4f))),
        )
        val fixedX = fixed.crateX
        repeat(60 * 3) { fixed.step(1f / 60f, 1f) }
        assertEquals(fixedX, fixed.crateX, 1f)
        assertFalse(fixed.dead)
    }

    @Test
    fun frictionChangesHowFarTheBallCoasts() {
        fun coast(friction: Float): Float {
            val sim = RedBallSim(arena(friction = friction))
            repeat(40) { sim.step(1f / 60f, 0f) }
            sim.ballVx = 320f
            val x = sim.ballX
            repeat(50) { sim.step(1f / 60f, 0f) }
            return sim.ballX - x
        }
        val icy = coast(0f)
        val rough = coast(1f)
        assertTrue("лёд $icy не дальше шершавого $rough", icy > rough + 30f)
    }

    @Test
    fun ladderClimbKeepsGoingHigherThanAJump() {
        val scene = arena(ladders = listOf(RedBallAabb(40f, 120f, 90f, 500f)))
        val sim = RedBallSim(scene)
        repeat(20) { sim.step(1f / 60f, 0f) }
        val y = sim.ballY
        sim.jumpHeld = true
        sim.jump()
        repeat(50) { sim.step(1f / 60f, 0f) }
        assertFalse(sim.dead)
        assertTrue("лестница не подняла мяч: ${y - sim.ballY}", y - sim.ballY > 170f)
    }

    @Test
    fun levelBoundsFollowWhatWasDrawn() {
        val draft = RedBallDraft(
            id = "wide",
            name = "Широкий",
            cols = 20,
            rows = 12,
            cell = 40f,
            originY = 100f,
            pieces = listOf(
                RedBallPiece(1, RedBallPieceKind.GROUND, -8, 10, 2, 2, false),
                RedBallPiece(2, RedBallPieceKind.GROUND, 48, 10, 2, 2, false),
                RedBallPiece(3, RedBallPieceKind.START, -7, 9, 1, 1, false),
                RedBallPiece(4, RedBallPieceKind.DOOR, 48, 6, 2, 4, false),
                RedBallPiece(5, RedBallPieceKind.STAR, 55, -3, 1, 1, false),
            ),
            nextId = 6,
        )
        val scene = draft.toScene()
        assertTrue("мир не ушёл влево: ${scene.minX}", scene.minX < -40f)
        assertTrue("мир не вырос вправо: ${scene.worldW}", scene.worldW > draft.cols * draft.cell)
        assertTrue(scene.minY < draft.originY)
        assertEquals(1, scene.stars.size)
        assertTrue(scene.solids.any { it.l < 0f })
        assertTrue(scene.solids.none { it.r <= 0.5f && it.b - it.t > 400f })
        val again = RedBallDraft.fromJson(draft.toJson())
        assertNotNull(again)
        assertTrue(again!!.pieces.any { it.kind == RedBallPieceKind.STAR && it.c > draft.cols && it.r < 0 })
        val sim = RedBallSim(scene)
        repeat(50) { sim.step(1f / 60f, 0f) }
        assertFalse(sim.dead)
        assertTrue("мяч вытолкнуло из отрицательных координат: x=${sim.ballX}", sim.ballX < -20f)
    }

    @Test
    fun oldLevelJsonKeepsDefaultsAndStillLoads() {
        val old = """
            {"version":1,"id":"legacy-level","name":"Старый","cols":20,"rows":12,"cell":40,"originY":100,"nextId":5,"updatedAt":7,
            "pieces":[
              {"id":1,"kind":"ground","c":0,"r":10,"w":2,"h":2,"flip":false},
              {"id":2,"kind":"ground","c":2,"r":10,"w":2,"h":2,"flip":false},
              {"id":3,"kind":"ground","c":4,"r":10,"w":2,"h":2,"flip":false},
              {"id":4,"kind":"ground","c":6,"r":10,"w":2,"h":2,"flip":false},
              {"id":5,"kind":"ground","c":8,"r":10,"w":2,"h":2,"flip":false},
              {"id":6,"kind":"ground","c":10,"r":10,"w":2,"h":2,"flip":false},
              {"id":7,"kind":"ground","c":12,"r":10,"w":2,"h":2,"flip":false},
              {"id":8,"kind":"ground","c":14,"r":10,"w":2,"h":2,"flip":false},
              {"id":9,"kind":"ground","c":16,"r":10,"w":2,"h":2,"flip":false},
              {"id":10,"kind":"ground","c":18,"r":10,"w":2,"h":2,"flip":false},
              {"id":11,"kind":"crate","c":6,"r":8,"w":2,"h":2,"flip":false},
              {"id":12,"kind":"start","c":1,"r":9,"w":1,"h":1,"flip":false},
              {"id":13,"kind":"door","c":16,"r":6,"w":2,"h":4,"flip":false},
              {"id":14,"kind":"portal","c":3,"r":3,"w":1,"h":1,"flip":false},
              {"id":15,"kind":"lantern","c":4,"r":4,"w":1,"h":1,"flip":false}
            ]}
        """.trimIndent()
        val parsed = RedBallDraft.fromJson(old)
        assertNotNull(parsed)
        val crate = parsed!!.pieces.first { it.kind == RedBallPieceKind.CRATE }
        assertTrue(crate.gravity)
        assertTrue(crate.movable)
        assertEquals(RED_BALL_FRICTION, crate.friction, 0.0001f)
        assertEquals(1f, crate.mass, 0.0001f)
        assertEquals(6, crate.c)
        assertNull(parsed.playBlockReason())
        val legacyPortal = parsed.pieces.single { it.kind == RedBallPieceKind.PORTAL }
        assertEquals("", legacyPortal.linkId)
        assertNull(legacyPortal.spawnC)
        assertNull(legacyPortal.spawnR)
        assertEquals(RedBallPortalRole.ENTRY, legacyPortal.role)
        assertEquals("", legacyPortal.portalName)
        assertEquals("", legacyPortal.exitId)
        assertEquals(RedBallSceneKind.MAIN, parsed.sceneKind)
        assertEquals("", parsed.parentId)
        assertEquals("", parsed.toScene().portals.single().linkId)
        assertTrue(parsed.pieces.none { it.id == 15L })
        val sim = RedBallSim(parsed.toScene())
        val x = sim.crateX
        repeat(60 * 3) { sim.step(1f / 60f, 1f) }
        assertTrue("старый ящик не толкается: ${sim.crateX}", sim.crateX > x + 15f)

        val tuned = parsed.withPiece(crate.id) {
            it.copy(gravity = false, movable = false, friction = 0.15f, mass = 2.5f)
        }
        val round = RedBallDraft.fromJson(tuned.toJson())
        val saved = round!!.pieces.first { it.id == crate.id }
        assertFalse(saved.gravity)
        assertFalse(saved.movable)
        assertEquals(0.15f, saved.friction, 0.0001f)
        assertEquals(2.5f, saved.mass, 0.0001f)
    }

    @Test
    fun portalTouchFiresOnceAndBlankLinkStaysQuiet() {
        val gate = RedBallPortal(RedBallAabb(400f, 400f, 500f, 530f), "next-scene", spawnC = 4, spawnR = 9)
        val sim = RedBallSim(arena(portals = listOf(gate)))
        sim.ballX = 450f
        sim.ballY = 472f
        sim.ballVx = 0f
        sim.ballVy = 0f
        sim.step(1f / 60f, 0f)
        val hit = sim.consumePortal()
        assertEquals("next-scene", hit?.linkId)
        assertEquals(4, hit?.spawnC)
        assertEquals(9, hit?.spawnR)
        assertFalse(sim.won)
        assertFalse(sim.dead)
        assertNull(sim.consumePortal())
        val frozen = sim.ballX
        sim.step(1f / 60f, 1f)
        assertEquals(frozen, sim.ballX, 0.01f)

        val asleep = RedBallSim(arena(portals = listOf(gate.copy(linkId = ""))))
        asleep.ballX = 450f
        asleep.ballY = 472f
        repeat(20) { asleep.step(1f / 60f, 0f) }
        assertNull(asleep.consumePortal())
        assertFalse(asleep.dead)
    }

    @Test
    fun portalOnThePathLeavesBeforeTheDoor() {
        val draft = flatDraft(
            RedBallPiece(100, RedBallPieceKind.PORTAL, 8, 7, 2, 3, false, linkId = "next"),
        )
        val sim = RedBallSim(draft.toScene())
        var hit: RedBallPortal? = null
        repeat(60 * 8) {
            if (hit != null) return@repeat
            sim.step(1f / 60f, 1f)
            hit = sim.consumePortal()
        }
        assertEquals("next", hit?.linkId)
        assertFalse(sim.won)
        assertFalse(sim.dead)
    }

    @Test
    fun portalArrivalDoesNotBounceStraightBack() {
        val back = RedBallPortal(RedBallAabb(400f, 400f, 500f, 530f), "back")
        val forth = RedBallPortal(RedBallAabb(800f, 400f, 900f, 530f), "forth")
        val sim = RedBallSim(arena(portals = listOf(back, forth)))
        sim.ballX = 450f
        sim.ballY = 472f
        sim.ballVx = 0f
        sim.ballVy = 0f
        sim.step(1f / 60f, 0f)
        assertEquals("back", sim.consumePortal()?.linkId)

        sim.arrive(850f, 472f)
        repeat(80) { sim.step(1f / 60f, 0f) }
        assertNull(sim.consumePortal())
        assertFalse(sim.dead)

        sim.ballX = 180f
        sim.ballY = 472f
        sim.ballVx = 0f
        sim.ballVy = 0f
        repeat(30) { sim.step(1f / 60f, 0f) }
        assertNull(sim.consumePortal())

        sim.ballX = 850f
        sim.ballY = 472f
        sim.ballVx = 0f
        sim.ballVy = 0f
        sim.step(1f / 60f, 0f)
        assertEquals("forth", sim.consumePortal()?.linkId)
    }

    @Test
    fun portalFieldsRoundTripAndMissingTargetGoesQuiet() {
        val piece = RedBallPiece(
            id = 50,
            kind = RedBallPieceKind.PORTAL,
            c = 8,
            r = 7,
            w = 2,
            h = 3,
            linkId = "level-b",
            spawnC = 4,
            spawnR = 9,
        )
        val draft = flatDraft(piece)
        val scene = draft.toScene()
        val portal = scene.portals.single()
        assertEquals("level-b", portal.linkId)
        assertEquals(4, portal.spawnC)
        assertEquals(320f, portal.box.l, 0.01f)
        assertEquals(380f, portal.box.t, 0.01f)
        assertEquals(500f, portal.box.b, 0.01f)
        assertEquals(draft.pieces, RedBallDraft.fromJson(draft.toJson())!!.pieces)

        val moved = draft.withMoved(50, 10, 6)
        val movedPortal = moved.pieces.first { it.id == 50L }
        assertEquals("level-b", movedPortal.linkId)
        assertEquals(4, movedPortal.spawnC)
        assertEquals(10, movedPortal.c)

        val bare = flatDraft(RedBallPiece(51, RedBallPieceKind.PORTAL, 8, 7, 2, 3, false))
        val stored = RedBallDraft.fromJson(bare.toJson())!!.pieces.first { it.kind == RedBallPieceKind.PORTAL }
        assertEquals("", stored.linkId)
        assertNull(stored.spawnC)
        assertNull(stored.spawnR)

        assertEquals("", scene.withKnownPortals(setOf(RedBallScene.BUILTIN_ID)).portals.single().linkId)
        assertEquals("level-b", scene.withKnownPortals(setOf("level-b")).portals.single().linkId)
        assertEquals(
            "Выбери сцену у портала, иначе он спит",
            bare.portalWarning(setOf(RedBallScene.BUILTIN_ID, bare.id)),
        )
        assertEquals(
            "Портал ведёт в удалённую сцену — пока он спит",
            draft.portalWarning(setOf(RedBallScene.BUILTIN_ID, draft.id)),
        )
        assertNull(draft.portalWarning(setOf(RedBallScene.BUILTIN_ID, draft.id, "level-b")))

        val (x, y) = portal.arrivalIn(scene, draft)
        assertEquals(4.5f * draft.cell, x, 0.01f)
        assertEquals(draft.originY + 10f * draft.cell - RedBallScene.BALL_R, y, 0.01f)
        val atStart = portal.copy(spawnC = null, spawnR = null).arrivalIn(scene, null)
        assertEquals(scene.startX, atStart.first, 0.01f)
        assertEquals(scene.startY, atStart.second, 0.01f)
    }

    @Test
    fun sceneHierarchyFiltersPickerAndPortalTargets() {
        val dir = Files.createTempDirectory("redball-family").toFile()
        val store = RedBallLevelStore(dir)
        val mainA = RedBallDraft.fresh("Дом").copy(id = "main-a", updatedAt = 10L)
        val nested = RedBallDraft.freshNested("main-a", "Чердак").copy(id = "nest-a", updatedAt = 20L)
        val mainB = RedBallDraft.fresh("Сад").copy(id = "main-b", updatedAt = 30L)
        val foreign = RedBallDraft.freshNested("main-b", "Чулан").copy(id = "nest-b", updatedAt = 40L)
        store.save(mainA)
        store.save(nested)
        store.save(mainB)
        store.save(foreign)

        assertEquals(listOf("main-b", "main-a"), store.list().map { it.id })
        val loadedNest = store.load("nest-a")
        assertEquals(RedBallSceneKind.NESTED, loadedNest?.sceneKind)
        assertEquals("main-a", loadedNest?.parentId)
        val round = RedBallDraft.fromJson(nested.toJson())
        assertEquals(RedBallSceneKind.NESTED, round?.sceneKind)
        assertEquals("main-a", round?.parentId)

        val catalog = store.listAll()
        val fromMain = allowedPortalTargetIds("main-a", RedBallSceneKind.MAIN, "", catalog)
        assertTrue(fromMain.contains("nest-a"))
        assertTrue(fromMain.contains("main-b"))
        assertTrue(fromMain.contains(RedBallScene.BUILTIN_ID))
        assertFalse(fromMain.contains("nest-b"))
        val fromNest = allowedPortalTargetIds("nest-a", RedBallSceneKind.NESTED, "main-a", catalog)
        assertTrue(fromNest.contains("main-a"))
        assertTrue(fromNest.contains("nest-a"))
        assertTrue(fromNest.contains("main-b"))
        assertFalse(fromNest.contains("nest-b"))

        java.io.File(dir, "legacy-main.json").writeText(
            """{"version":1,"id":"legacy-main","name":"Старый дом","cols":20,"rows":12,"cell":40,"originY":100,"nextId":2,"updatedAt":8,"pieces":[]}""",
        )
        assertEquals(RedBallSceneKind.MAIN, store.load("legacy-main")?.sceneKind)
        assertTrue(store.list().any { it.id == "legacy-main" && it.sceneKind == RedBallSceneKind.MAIN })

        val orphan = RedBallDraft.fresh("Сирота").copy(
            id = "orphan",
            sceneKind = RedBallSceneKind.NESTED,
            parentId = "missing",
            updatedAt = 4L,
        )
        java.io.File(dir, "orphan.json").writeText(orphan.toJson())
        assertEquals(RedBallSceneKind.MAIN, store.load("orphan")?.sceneKind)
        assertEquals("", store.load("orphan")?.parentId)
        assertTrue(store.list().any { it.id == "orphan" })

        store.delete("main-a")
        assertNull(store.load("main-a"))
        assertNull(store.load("nest-a"))
        assertNotNull(store.load("main-b"))
        assertNotNull(store.load("nest-b"))
        assertFalse(store.list().any { it.id == "nest-b" })
    }

    @Test
    fun entryLandsOnNamedExitAndExitNeverTriggers() {
        val exit = RedBallPiece(
            id = 80,
            kind = RedBallPieceKind.PORTAL,
            c = 12,
            r = 7,
            w = 2,
            h = 3,
            portalName = "У двери",
            role = RedBallPortalRole.EXIT,
        )
        val entry = RedBallPiece(
            id = 81,
            kind = RedBallPieceKind.PORTAL,
            c = 4,
            r = 7,
            w = 2,
            h = 3,
            portalName = "В подвал",
            role = RedBallPortalRole.ENTRY,
            linkId = "flat",
            exitId = "У двери",
        )
        val base = flatDraft()
        val draft = base.copy(pieces = base.pieces + exit + entry)
        val scene = draft.toScene()
        val gate = scene.portals.first { it.role == RedBallPortalRole.ENTRY }
        val spot = draft.portalExitSpot("У двери")
        assertNotNull(spot)
        val (x, y) = gate.arrivalIn(scene, draft)
        assertEquals(spot!!.first, x, 0.01f)
        assertEquals(spot.second, y, 0.01f)
        assertEquals("У двери", gate.exitId)

        val renamed = gate.copy(exitId = "Пропал", spawnC = 4, spawnR = 9)
        val fallback = renamed.arrivalIn(scene, draft)
        assertEquals(scene.startX, fallback.first, 0.01f)
        assertEquals(scene.startY, fallback.second, 0.01f)
        val broken = draft.copy(pieces = draft.pieces.map { piece ->
            if (piece.id == 81L) piece.copy(exitId = "Пропал") else piece
        })
        assertEquals(
            "Выход «Пропал» пропал — мяч появится на старте",
            broken.portalWarning(
                setOf(RedBallScene.BUILTIN_ID, broken.id),
                mapOf(broken.id to setOf("У двери")),
            ),
        )

        val quiet = RedBallSim(scene)
        val exitBox = scene.portals.first { it.role == RedBallPortalRole.EXIT }.box
        quiet.ballX = (exitBox.l + exitBox.r) / 2f
        quiet.ballY = (exitBox.t + exitBox.b) / 2f
        quiet.ballVx = 0f
        quiet.ballVy = 0f
        val before = quiet.ballX
        repeat(25) { quiet.step(1f / 60f, 1f) }
        assertNull(quiet.consumePortal())
        assertFalse(quiet.dead)
        assertTrue("выход заморозил мяч: x=${quiet.ballX}", quiet.ballX > before + 1f)

        val sim = RedBallSim(scene)
        sim.ballX = (gate.box.l + gate.box.r) / 2f
        sim.ballY = (gate.box.t + gate.box.b) / 2f
        sim.ballVx = 0f
        sim.ballVy = 0f
        sim.step(1f / 60f, 0f)
        val hit = sim.consumePortal()
        assertEquals("flat", hit?.linkId)
        assertEquals("У двери", hit?.exitId)
        assertEquals(RedBallPortalRole.ENTRY, hit?.role)

        val arrived = RedBallSim(scene)
        arrived.arrive(x, y)
        repeat(40) { arrived.step(1f / 60f, 1f) }
        assertNull(arrived.consumePortal())
        assertFalse(arrived.dead)
        assertTrue(arrived.ballX > x + 1f)

        val linkedExit = RedBallPortal(
            RedBallAabb(400f, 400f, 500f, 530f),
            linkId = "should-not-fire",
            portalName = "Выход 1",
            role = RedBallPortalRole.EXIT,
        )
        val blocked = RedBallSim(arena(portals = listOf(linkedExit)))
        blocked.ballX = 450f
        blocked.ballY = 465f
        val startX = blocked.ballX
        repeat(20) { blocked.step(1f / 60f, 1f) }
        assertNull(blocked.consumePortal())
        assertTrue(blocked.ballX > startX)

        val named = flatDraft()
            .withPlaced(RedBallPieceKind.PORTAL, 2, 2, false)
            .let { placed -> placed.withPlaced(RedBallPieceKind.PORTAL, 8, 2, false, setOf("Вход 2")) }
        val portalNames = named.pieces.filter { it.kind == RedBallPieceKind.PORTAL }.map { it.portalName }.sorted()
        assertEquals(listOf("Вход 1", "Вход 3"), portalNames)
        val duplicated = named.copy(
            pieces = named.pieces.map { piece ->
                if (piece.kind == RedBallPieceKind.PORTAL) piece.copy(portalName = "Вход 1") else piece
            },
        )
        assertEquals(
            "Имена порталов в одной семье должны быть разными",
            duplicated.portalWarning(setOf(RedBallScene.BUILTIN_ID, duplicated.id)),
        )
    }

    @Test
    fun exitOnThePathDoesNotStealTheDoor() {
        val draft = flatDraft(
            RedBallPiece(
                100,
                RedBallPieceKind.PORTAL,
                8,
                7,
                2,
                3,
                false,
                portalName = "Мимо",
                role = RedBallPortalRole.EXIT,
                linkId = "next",
            ),
        )
        val sim = RedBallSim(draft.toScene())
        repeat(60 * 8) { sim.step(1f / 60f, 1f) }
        assertNull(sim.consumePortal())
        assertTrue(sim.won)
        assertFalse(sim.dead)
    }

    private fun pinOntoEnemy(sim: RedBallSim) {
        val enemy = sim.enemies.single()
        sim.ballX = enemy.x - enemy.size * 0.2f
        sim.ballY = enemy.y
        sim.ballVx = 0f
        sim.ballVy = 0f
    }

    private fun parkOnStart(sim: RedBallSim) {
        sim.ballX = sim.scene.startX
        sim.ballY = sim.scene.startY
        sim.ballVx = 0f
        sim.ballVy = 0f
    }

    private fun arena(
        friction: Float = RED_BALL_FRICTION,
        crates: List<RedBallCrateSpawn> = emptyList(),
        ladders: List<RedBallAabb> = emptyList(),
        stars: List<RedBallStarSpawn> = emptyList(),
        magnets: List<RedBallMagnetSpawn> = emptyList(),
        enemies: List<RedBallEnemySpawn> = emptyList(),
        portals: List<RedBallPortal> = emptyList(),
        spikes: List<RedBallSpike> = emptyList(),
    ): RedBallScene {
        return RedBallScene(
            id = "arena",
            name = "Площадка",
            builtin = false,
            worldW = 1800f,
            worldH = 820f,
            voidY = 760f,
            startX = 80f,
            startY = 472f,
            solids = listOf(RedBallAabb(0f, 500f, 1700f, 760f, friction)),
            slopes = emptyList(),
            spikes = spikes,
            springs = emptyList(),
            door = RedBallAabb(1500f, 340f, 1600f, 500f),
            crates = crates,
            ladders = ladders,
            stars = stars,
            magnets = magnets,
            enemies = enemies,
            portals = portals,
        )
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
