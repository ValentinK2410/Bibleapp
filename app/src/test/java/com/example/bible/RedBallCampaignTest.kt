package com.example.bible

import com.example.bible.games.MapCampaignMemory
import com.example.bible.games.RedBallAabb
import com.example.bible.games.RedBallActKind
import com.example.bible.games.RedBallCampaign
import com.example.bible.games.RedBallCampaignProgress
import com.example.bible.games.RedBallCampaignRun
import com.example.bible.games.RedBallCheckpoint
import com.example.bible.games.RedBallChaser
import com.example.bible.games.RedBallSpike
import com.example.bible.games.cameraFrame
import com.example.bible.games.RedBallDecoy
import com.example.bible.games.RedBallEnemySpawn
import com.example.bible.games.RedBallFlood
import com.example.bible.games.RedBallPlatform
import com.example.bible.games.RedBallPortal
import com.example.bible.games.RedBallReach
import com.example.bible.games.RedBallScene
import com.example.bible.games.RedBallScript
import com.example.bible.games.RedBallSim
import com.example.bible.games.RedBallTrigger
import com.example.bible.games.RedBallWater
import com.example.bible.games.campaignWaterSkin
import com.example.bible.games.skins.RedBallSkinId
import com.example.bible.games.RedBallAct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedBallCampaignTest {

    @Test
    fun tenLevelsAreDistinctAndEachFlagIsReachable() {
        val levels = RedBallCampaign.levels
        assertEquals(10, levels.size)
        assertEquals((1..10).toList(), levels.map { it.index })
        assertEquals(10, levels.map { it.id }.toSet().size)
        assertEquals(10, levels.map { it.title }.toSet().size)
        assertEquals(10, levels.map { it.twist }.toSet().size)
        val signatures = levels.map { signature(it.scene) }
        assertEquals(signatures.joinToString("\n"), 10, signatures.toSet().size)
        for (level in levels) {
            assertEquals(level.id, level.scene.id)
            assertFalse(level.scene.builtin)
            assertTrue(level.intro.isNotBlank())
            assertTrue(level.twist.isNotBlank())
            assertTrue(level.intro != level.twist)
            assertTrue("${level.title}: мало секций", level.sections.size >= 4)
            assertTrue("${level.title}: мало звёзд", level.scene.stars.size >= 4)
            assertTrue("${level.title}: мало чекпоинтов", level.scene.checkpoints.size >= 2)
            assertTrue("${level.title}: нет горок", level.scene.slopes.isNotEmpty())
            val legacy = LEGACY_WIDTH.getValue(level.index)
            assertTrue(
                "${level.title}: ширина ${level.scene.worldW.toInt()} меньше ${legacy * 4}",
                level.scene.worldW >= legacy * 4f,
            )
            val low = level.scene.stars.minOf { it.y }
            val high = level.scene.stars.maxOf { it.y }
            assertTrue("${level.title}: нет секрета со звездой", high - low >= 160f)
            assertTrue(
                level.scene.script.acts.any { it.kind == RedBallActKind.STORY && it.text == level.twist },
            )
            assertNull(RedBallReach.check(level.scene))
            val sim = RedBallSim(level.scene)
            repeat(90) { sim.step(1f / 60f, 0f) }
            assertFalse("${level.title} мяч погиб на старте", sim.dead)
            assertFalse(sim.won)
        }
    }

    @Test
    fun levelsUnlockInOrderAndKeepTheBestStars() {
        val progress = RedBallCampaignProgress(MapCampaignMemory())
        assertTrue(progress.isUnlocked(1))
        assertFalse(progress.isUnlocked(2))
        assertFalse(progress.isCleared(1))
        progress.record(1, 2)
        assertTrue(progress.isCleared(1))
        assertEquals(2, progress.bestStars(1))
        assertTrue(progress.isUnlocked(2))
        assertFalse(progress.isUnlocked(3))
        progress.record(2, 0)
        assertTrue(progress.isCleared(2))
        assertEquals(0, progress.bestStars(2))
        assertTrue(progress.isUnlocked(3))
        progress.record(1, 1)
        assertEquals(2, progress.bestStars(1))
        progress.record(1, 3)
        assertEquals(3, progress.bestStars(1))
        for (level in 3..9) progress.record(level, 1)
        assertTrue(progress.isUnlocked(10))
        assertFalse(progress.isUnlocked(11))
        progress.record(10, 3)
        assertTrue(progress.isCleared(10))
    }

    @Test
    fun clearingALaterLevelDoesNotSkipTheLock() {
        val progress = RedBallCampaignProgress(MapCampaignMemory())
        progress.record(2, 3)
        assertTrue(progress.isCleared(2))
        assertFalse(progress.isUnlocked(2))
        assertFalse(progress.isUnlocked(3))
        assertTrue(progress.isUnlocked(1))
    }

    @Test
    fun waterLevelsWaitForTheWaterWorldSkin() {
        val hasWater = RedBallSkinId.entries.any { it.storageKey == "water_world" }
        val seventh = RedBallCampaign.levels[6].skin
        val eighth = RedBallCampaign.levels[7].skin
        assertEquals(seventh, eighth)
        assertEquals(campaignWaterSkin, seventh)
        if (hasWater) {
            assertEquals("water_world", seventh.storageKey)
        } else {
            assertEquals(RedBallSkinId.CLASSIC, seventh)
        }
    }

    @Test
    fun campaignPortalLinkResolvesToThatScene() {
        val scene = RedBallCampaign.sceneByLink("campaign-4")
        assertNotNull(scene)
        assertEquals("campaign-4", scene!!.id)
        assertNull(RedBallCampaign.sceneByLink("builtin"))
        assertNull(RedBallCampaign.sceneByLink(""))
        val portal = RedBallPortal(RedBallAabb(0f, 0f, 10f, 10f), linkId = "campaign-1")
        val arrival = portal.arrivalIn(RedBallCampaign.sceneByLink(portal.linkId)!!, null)
        val home = RedBallCampaign.byId("campaign-1")!!.scene
        assertEquals(home.startX, arrival.first)
        assertEquals(home.startY, arrival.second)
    }

    @Test
    fun collapsingPlatformDropsTheBall() {
        val scene = RedBallScene(
            id = "drop",
            name = "Обвал",
            builtin = false,
            worldW = 1000f,
            worldH = 1100f,
            voidY = 980f,
            startX = 80f,
            startY = 372f,
            solids = listOf(RedBallAabb(0f, 700f, 900f, 860f)),
            slopes = emptyList(),
            spikes = emptyList(),
            springs = emptyList(),
            door = RedBallAabb(640f, 524f, 730f, 700f),
            crates = emptyList(),
            script = RedBallScript(
                triggers = listOf(RedBallTrigger("go", RedBallAabb(150f, 220f, 280f, 410f))),
                platforms = listOf(RedBallPlatform("lid", RedBallAabb(0f, 400f, 420f, 448f), delay = 0.12f)),
                acts = listOf(RedBallAct("go", RedBallActKind.COLLAPSE, "lid")),
            ),
        )
        val sim = RedBallSim(scene)
        repeat(50) { sim.step(1f / 60f, 1f) }
        assertTrue("не доехал до люка x=${sim.ballX}", sim.ballX > 160f)
        repeat(100) { sim.step(1f / 60f, 0f) }
        assertTrue("пол не провалился y=${sim.ballY}", sim.ballY > 560f)
        assertFalse(sim.dead)
    }

    @Test
    fun waterRisesAfterTheTrigger() {
        val scene = flat(
            startX = 80f,
            doorX = 640f,
            script = RedBallScript(
                triggers = listOf(RedBallTrigger("up", RedBallAabb(240f, 300f, 420f, 520f))),
                floods = listOf(
                    RedBallFlood("pool", RedBallWater(0f, 560f, 800f, 760f), riseTo = 420f, speed = 240f),
                ),
                acts = listOf(RedBallAct("up", RedBallActKind.RAISE_WATER, "pool")),
            ),
        )
        val sim = RedBallSim(scene)
        assertEquals(560f, sim.director.waterTop("pool"))
        repeat(50) { sim.step(1f / 60f, 1f) }
        repeat(80) { sim.step(1f / 60f, 0f) }
        val top = sim.director.waterTop("pool") ?: error("нет воды")
        assertTrue("вода не поднялась: $top", top < 450f)
    }

    @Test
    fun decoyFlagDoesNotWin() {
        val scene = flat(
            startX = 80f,
            doorX = 640f,
            script = RedBallScript(
                decoys = listOf(RedBallDecoy("fake", RedBallAabb(250f, 300f, 340f, 500f), "fake")),
                acts = listOf(
                    RedBallAct("fake", RedBallActKind.HIDE_DECOY, "fake"),
                    RedBallAct("fake", RedBallActKind.STORY, text = "мимо"),
                ),
            ),
        )
        val sim = RedBallSim(scene)
        sim.ballX = 290f
        sim.ballY = 460f
        sim.step(1f / 60f, 0f)
        assertFalse(sim.won)
        assertEquals("мимо", sim.director.story)
        assertTrue(sim.director.visibleDecoys().isEmpty())
        sim.ballX = 680f
        sim.ballY = 450f
        sim.step(1f / 60f, 0f)
        assertTrue(sim.won)
    }

    @Test
    fun disguisedEnemySleepsUntilTheTrigger() {
        val scene = flat(
            startX = 80f,
            doorX = 700f,
            enemies = listOf(
                RedBallEnemySpawn(420f, 460f, 72f, 300f, 560f, asleep = true, disguised = true),
            ),
            script = RedBallScript(
                triggers = listOf(RedBallTrigger("wake", RedBallAabb(200f, 300f, 320f, 520f))),
                acts = listOf(RedBallAct("wake", RedBallActKind.WAKE, "0")),
            ),
        )
        val asleep = RedBallSim(scene)
        asleep.ballX = 420f
        asleep.ballY = 430f
        repeat(30) { asleep.step(1f / 60f, 0f) }
        assertEquals(3, asleep.hearts)
        assertTrue(asleep.enemies.single().asleep)
        assertEquals(420f, asleep.enemies.single().x)
        val waking = RedBallSim(scene)
        repeat(45) { waking.step(1f / 60f, 1f) }
        assertFalse(waking.enemies.single().asleep)
        val home = waking.enemies.single().x
        repeat(50) { waking.step(1f / 60f, 0f) }
        assertTrue("квадрат не поехал", kotlin.math.abs(waking.enemies.single().x - home) > 8f)
    }

    @Test
    fun chaserRollsStopsAndHurts() {
        val scene = flat(
            startX = 80f,
            doorX = 760f,
            script = RedBallScript(
                triggers = listOf(RedBallTrigger("go", RedBallAabb(160f, 300f, 280f, 520f))),
                chasers = listOf(RedBallChaser("rock", 40f, 446f, 80f, 220f, 360f)),
                acts = listOf(RedBallAct("go", RedBallActKind.CHASE, "rock")),
            ),
        )
        val sim = RedBallSim(scene)
        repeat(30) { sim.step(1f / 60f, 1f) }
        val before = sim.director.chasers().single().x
        repeat(70) { sim.step(1f / 60f, 0f) }
        val chaser = sim.director.chasers().single()
        assertTrue(chaser.active)
        assertTrue("камень не сдвинулся: ${chaser.x} было $before", chaser.x > before + 40f)
        repeat(180) { sim.step(1f / 60f, 0f) }
        assertEquals(360f, sim.director.chasers().single().x, 1f)
        val hit = RedBallSim(scene)
        repeat(20) { hit.step(1f / 60f, 1f) }
        val rock = hit.director.chasers().single()
        hit.ballX = rock.x
        hit.ballY = rock.y
        hit.step(1f / 60f, 0f)
        assertTrue(hit.hearts < 3)
    }

    @Test
    fun bossDefeatRevealsTheDoor() {
        val scene = flat(
            startX = 80f,
            doorX = 640f,
            enemies = listOf(
                RedBallEnemySpawn(360f, 460f, 80f, 280f, 480f, hits = 2, defeatTrigger = "down"),
            ),
            script = RedBallScript(
                doorHidden = true,
                acts = listOf(RedBallAct("down", RedBallActKind.REVEAL_DOOR)),
            ),
        )
        val sim = RedBallSim(scene)
        sim.ballX = 680f
        sim.ballY = 440f
        sim.step(1f / 60f, 0f)
        assertFalse(sim.won)
        assertFalse(sim.director.doorVisible)
        val enemy = sim.enemies.single()
        sim.ballX = enemy.x
        sim.ballY = enemy.y - enemy.size * 0.42f
        sim.ballVy = 240f
        sim.step(1f / 60f, 0f)
        assertTrue(enemy.alive)
        assertEquals(1, enemy.hitsLeft)
        sim.ballX = enemy.x
        sim.ballY = enemy.y - enemy.size * 0.42f
        sim.ballVy = 240f
        sim.step(1f / 60f, 0f)
        assertFalse(enemy.alive)
        assertTrue(sim.director.doorVisible)
        sim.ballX = 680f
        sim.ballY = 440f
        sim.step(1f / 60f, 0f)
        assertTrue(sim.won)
    }

    @Test
    fun heavierGravityJumpsLower() {
        fun peak(scale: Float): Float {
            val sim = RedBallSim(
                flat(
                    startX = 80f,
                    doorX = 400f,
                    script = RedBallScript(gravity = scale),
                ),
            )
            repeat(40) { sim.step(1f / 60f, 0f) }
            val rest = sim.ballY
            sim.jumpHeld = true
            sim.jump()
            var top = rest
            repeat(40) {
                sim.step(1f / 60f, 0f)
                top = minOf(top, sim.ballY)
            }
            return rest - top
        }
        assertTrue(peak(2f) < peak(1f) - 30f)
    }

    @Test
    fun checkpointRespawnKeepsTheHeartsYouHave() {
        val scene = RedBallScene(
            id = "cp",
            name = "Чекпоинт",
            builtin = false,
            worldW = 1600f,
            worldH = 900f,
            voidY = 800f,
            startX = 80f,
            startY = 472f,
            solids = listOf(RedBallAabb(0f, 500f, 1600f, 860f)),
            slopes = emptyList(),
            spikes = listOf(RedBallSpike(980f, 440f, 80f, 60f)),
            springs = emptyList(),
            door = RedBallAabb(1400f, 324f, 1490f, 500f),
            crates = emptyList(),
            checkpoints = listOf(
                RedBallCheckpoint(460f, 472f),
                RedBallCheckpoint(760f, 472f),
            ),
        )
        val missed = RedBallSim(scene)
        missed.ballY = scene.voidY + 40f
        missed.step(1f / 60f, 0f)
        assertEquals("яма до чекпоинта не сняла сердце", 2, missed.hearts)
        assertEquals(scene.startX, missed.ballX, 0.5f)
        assertFalse(missed.dead)

        val sim = RedBallSim(scene)
        sim.ballX = 460f
        sim.ballY = 472f
        sim.step(1f / 60f, 0f)
        assertEquals(460f, sim.spawnX, 1f)
        assertEquals(472f, sim.spawnY, 1f)
        assertEquals(RedBallSim.MAX_HEARTS, sim.hearts)
        sim.ballX = 980f
        sim.ballY = 470f
        sim.step(1f / 60f, 0f)
        assertEquals("удар не вернул на чекпоинт", 460f, sim.ballX, 1.5f)
        assertEquals(472f, sim.ballY, 1.5f)
        assertEquals(RedBallSim.MAX_HEARTS - 1, sim.hearts)
        assertFalse(sim.dead)
        repeat(60 * 3) {
            sim.ballX = 460f
            sim.ballY = 472f
            sim.step(1f / 60f, 0f)
        }
        assertEquals(0f, sim.invuln, 0.05f)
        assertEquals(RedBallSim.MAX_HEARTS - 1, sim.hearts)
        sim.ballY = scene.voidY + 40f
        sim.step(1f / 60f, 0f)
        assertEquals("яма вернула полные сердца", RedBallSim.MAX_HEARTS - 2, sim.hearts)
        assertEquals(460f, sim.ballX, 1.5f)
        assertFalse(sim.dead)

        val plain = scene.copy(checkpoints = emptyList(), id = "plain")
        val knocked = RedBallSim(plain)
        knocked.ballX = 980f
        knocked.ballY = 470f
        knocked.step(1f / 60f, 0f)
        assertEquals(RedBallSim.MAX_HEARTS - 1, knocked.hearts)
        assertTrue(
            "без чекпоинта шипы унесли мяч на старт: x=${knocked.ballX}",
            kotlin.math.abs(knocked.ballX - 980f) < 160f,
        )

        val campaign = RedBallCampaign.byIndex(1)!!.scene
        val home = campaign.checkpoints.first()
        val ball = RedBallSim(campaign)
        ball.ballX = home.x
        ball.ballY = home.y
        ball.step(1f / 60f, 0f)
        assertEquals(home.x, ball.spawnX, 1f)
        ball.ballY = campaign.voidY + 50f
        ball.step(1f / 60f, 0f)
        assertEquals(RedBallSim.MAX_HEARTS - 1, ball.hearts)
        assertEquals(home.x, ball.ballX, 1.5f)
        assertEquals(home.y, ball.ballY, 1.5f)
        assertFalse(ball.dead)
    }

    @Test
    fun flagAdvancesToTheNextLevelAndTheTenthReturnsToSelect() {
        val progress = RedBallCampaignProgress(MapCampaignMemory())
        val run = RedBallCampaignRun(progress)
        assertTrue(run.begin(1))
        assertFalse(run.begin(2))
        run.dismissIntro()
        assertEquals(RedBallCampaignRun.Phase.PLAY, run.phase)
        run.reachFlag(collected = 1, total = 5, nowMs = 0L)
        assertEquals(RedBallCampaignRun.Phase.CLEAR, run.phase)
        assertEquals(1, progress.bestStars(1))
        assertTrue(progress.isUnlocked(2))
        assertFalse(run.advance(RedBallCampaignRun.CLEAR_HOLD_MS - 1, pressed = false))
        assertEquals(1, run.index)
        assertTrue(run.advance(RedBallCampaignRun.CLEAR_HOLD_MS, pressed = false))
        assertEquals(2, run.index)
        assertEquals(RedBallCampaignRun.Phase.INTRO, run.phase)

        run.dismissIntro()
        run.reachFlag(collected = 5, total = 5, nowMs = 50L)
        assertEquals(3, progress.bestStars(2))
        assertTrue(run.advance(50L, pressed = true))
        assertEquals(3, run.index)
        assertEquals(RedBallCampaignRun.Phase.INTRO, run.phase)

        for (level in 3..10) {
            assertEquals(level, run.index)
            run.dismissIntro()
            run.reachFlag(collected = 4, total = 6, nowMs = 0L)
            if (level < 10) {
                assertEquals(RedBallCampaignRun.Phase.CLEAR, run.phase)
                assertTrue(run.advance(RedBallCampaignRun.CLEAR_HOLD_MS, pressed = false))
                assertTrue(progress.isUnlocked(level + 1))
                assertEquals(RedBallCampaignRun.Phase.INTRO, run.phase)
            }
        }
        assertEquals(RedBallCampaignRun.Phase.FINALE, run.phase)
        assertTrue(progress.isCleared(10))
        assertEquals(2, progress.bestStars(10))
        assertFalse(progress.isUnlocked(11))
        assertFalse(run.advance(RedBallCampaignRun.CLEAR_HOLD_MS - 1, pressed = false))
        assertTrue(run.advance(0L, pressed = true))
        assertEquals(RedBallCampaignRun.Phase.SELECT, run.phase)
    }

    @Test
    fun cameraKeepsTheFarFlagInsideTheWorld() {
        for (level in RedBallCampaign.levels) {
            val scene = level.scene
            val focusX = (scene.door.l + scene.door.r) / 2f
            val focusY = (scene.door.t + scene.door.b) / 2f
            val frame = scene.cameraFrame(focusX, focusY, 900f, 620f)
            val maxCam = (scene.worldW - frame.viewW).coerceAtLeast(scene.minX)
            assertTrue("${level.title}: камера x=${frame.x}", frame.x in scene.minX..maxCam)
            assertTrue(
                "${level.title}: флаг x=$focusX вне кадра ${frame.x}",
                focusX in frame.x..(frame.x + frame.viewW),
            )
            assertTrue(
                "${level.title}: флаг y=$focusY вне кадра ${frame.y}",
                focusY in frame.y..(frame.y + frame.viewH),
            )
            for (point in scene.checkpoints) {
                val shot = scene.cameraFrame(point.x, point.y, 900f, 620f)
                assertTrue(
                    "${level.title}: чекпоинт вне кадра",
                    point.x in shot.x..(shot.x + shot.viewW) && point.y in shot.y..(shot.y + shot.viewH),
                )
            }
            if (level.index == 7 || level.index == 8) {
                val pools = scene.waters.map { it.r - it.l } + scene.script.floods.map { it.water.r - it.water.l }
                assertTrue("${level.title}: короткий заплыв", pools.max() >= 2500f)
            }
            if (level.index == 10) {
                assertTrue(scene.script.doorHidden)
                assertTrue(scene.enemies.count { it.hits > 1 } >= 2)
                assertTrue(scene.script.acts.any { it.kind == RedBallActKind.GRAVITY && it.value > 1.4f })
            }
        }
    }

    /** Ширина мира до удлинения. Новый уровень должен быть хотя бы вчетверо шире. */
    private val LEGACY_WIDTH = mapOf(
        1 to 1780f,
        2 to 1960f,
        3 to 2080f,
        4 to 1520f,
        5 to 1760f,
        6 to 3060f,
        7 to 2060f,
        8 to 1240f,
        9 to 3400f,
        10 to 1960f,
    )

    private fun signature(scene: RedBallScene): String {
        val script = scene.script
        return listOf(
            scene.slopes.isNotEmpty(),
            scene.springs.isNotEmpty(),
            scene.ladders.isNotEmpty(),
            scene.carts.isNotEmpty(),
            scene.crates.isNotEmpty(),
            scene.magnets.isNotEmpty(),
            scene.waters.isNotEmpty(),
            script.floods.isNotEmpty(),
            script.platforms.isNotEmpty(),
            script.bridges.isNotEmpty(),
            script.chasers.isNotEmpty(),
            script.decoys.isNotEmpty(),
            script.doorHidden,
            script.acts.any { it.kind == RedBallActKind.DARKEN },
            script.acts.any { it.kind == RedBallActKind.TELEPORT },
            script.acts.any { it.kind == RedBallActKind.GRAVITY },
            scene.enemies.any { it.disguised },
            scene.enemies.any { it.hits > 1 },
        ).joinToString("")
    }

    private fun flat(
        startX: Float,
        doorX: Float,
        script: RedBallScript = RedBallScript.NONE,
        enemies: List<RedBallEnemySpawn> = emptyList(),
        extraFloor: RedBallAabb? = null,
    ): RedBallScene {
        val floors = ArrayList<RedBallAabb>()
        floors += RedBallAabb(0f, 500f, 900f, 860f)
        if (extraFloor != null) floors += extraFloor
        return RedBallScene(
            id = "script-lab",
            name = "Лаборатория",
            builtin = false,
            worldW = 1100f,
            worldH = 1100f,
            voidY = 980f,
            startX = startX,
            startY = 472f,
            solids = floors,
            slopes = emptyList(),
            spikes = emptyList(),
            springs = emptyList(),
            door = RedBallAabb(doorX, 324f, doorX + 90f, 500f),
            crates = emptyList(),
            enemies = enemies,
            script = script,
        )
    }
}
