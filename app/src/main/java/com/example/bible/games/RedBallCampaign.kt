package com.example.bible.games

import android.content.Context
import com.example.bible.games.skins.RedBallSkinId

/**
 * Десять готовых уровней. Следующий открывается, когда мяч доехал до флага.
 * Свободная горка и редактор живут отдельно и этот список не заменяют.
 */
object RedBallCampaign {
    const val COUNT = 10

    val levels: List<RedBallCampaignLevel> = listOf(
        levelHills(),
        levelFakeFlag(),
        levelFriend(),
        levelDarkForest(),
        levelPortal(),
        levelCartChase(),
        levelCollapseWater(),
        levelFlood(),
        levelFlee(),
        levelBoss(),
    )

    fun byIndex(index: Int): RedBallCampaignLevel? = levels.firstOrNull { it.index == index }

    fun byId(id: String): RedBallCampaignLevel? = levels.firstOrNull { it.id == id || it.scene.id == id }

    /** Сцена, если портал ведёт в уровень кампании, а не в сохранение редактора. */
    fun sceneByLink(linkId: String): RedBallScene? = byId(linkId)?.scene

    fun isCampaignId(id: String): Boolean = byId(id) != null
}

data class RedBallCampaignLevel(
    val index: Int,
    val id: String,
    val title: String,
    val chapter: String,
    val skin: RedBallSkinId,
    val intro: String,
    val twist: String,
    val scene: RedBallScene,
)

/**
 * Память кампании без Android: тесты подставляют карту, игра — [PrefsCampaignMemory].
 * Звёзды уровня хранятся строкой. Нет ключа — уровень ещё не пройден.
 */
interface RedBallCampaignMemory {
    fun getString(key: String, fallback: String?): String?
    fun putString(key: String, value: String)
}

class MapCampaignMemory : RedBallCampaignMemory {
    private val values = HashMap<String, String>()

    override fun getString(key: String, fallback: String?): String? = if (key in values) values[key] else fallback

    override fun putString(key: String, value: String) {
        values[key] = value
    }
}

class PrefsCampaignMemory(context: Context) : RedBallCampaignMemory {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun getString(key: String, fallback: String?): String? = prefs.getString(key, fallback)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    private companion object {
        const val PREFS = "redball_campaign"
    }
}

/**
 * Уровень 1 открыт сразу. Уровень N+1 открыт, только если пройдены все предыдущие.
 * Повторный заход оставляет лучшие звёзды и не закрывает уже открытое.
 */
class RedBallCampaignProgress(private val memory: RedBallCampaignMemory) {
    fun isCleared(level: Int): Boolean = stored(level) >= 0

    fun bestStars(level: Int): Int = stored(level).coerceAtLeast(0)

    fun isUnlocked(level: Int): Boolean {
        if (level !in 1..RedBallCampaign.COUNT) return false
        for (previous in 1 until level) if (!isCleared(previous)) return false
        return true
    }

    fun record(level: Int, stars: Int) {
        if (level !in 1..RedBallCampaign.COUNT) return
        val next = stars.coerceIn(0, 3)
        val prev = stored(level)
        if (prev >= next) return
        memory.putString(key(level), next.toString())
    }

    private fun stored(level: Int): Int {
        if (level !in 1..RedBallCampaign.COUNT) return -1
        return memory.getString(key(level), null)?.toIntOrNull() ?: -1
    }

    private fun key(level: Int) = "stars_$level"
}

/**
 * Глава «Водный мир» собирается в соседнем PR.
 * TODO: когда в [RedBallSkinId] появится storageKey "water_world",
 * уровни 7 и 8 возьмут её сами. Пока ключа нет — рисуется классика.
 */
internal val campaignWaterSkin: RedBallSkinId =
    RedBallSkinId.entries.firstOrNull { it.storageKey == "water_world" } ?: RedBallSkinId.CLASSIC

private const val R = RedBallScene.BALL_R

private class Stage(val title: String) {
    private val solids = ArrayList<RedBallAabb>()
    private val slopes = ArrayList<RedBallSlope>()
    private val spikes = ArrayList<RedBallSpike>()
    private val springs = ArrayList<RedBallAabb>()
    private val crates = ArrayList<RedBallCrateSpawn>()
    private val ladders = ArrayList<RedBallAabb>()
    private val stars = ArrayList<RedBallStarSpawn>()
    private val magnets = ArrayList<RedBallMagnetSpawn>()
    private val enemies = ArrayList<RedBallEnemySpawn>()
    private val waters = ArrayList<RedBallWater>()
    private val carts = ArrayList<RedBallCartSpawn>()
    private var door = RedBallAabb(0f, 0f, 40f, 40f)
    private var startX = 120f
    private var startY = 400f

    fun ground(l: Float, r: Float, top: Float) {
        solids += RedBallAabb(l, top, r, top + 72f)
    }

    fun slope(x0: Float, y0: Float, x1: Float, y1: Float) {
        slopes += RedBallSlope(x0, y0, x1, y1)
    }

    fun spikes(l: Float, r: Float, floor: Float, group: String = "") {
        var x = l
        while (x < r - 8f) {
            spikes += RedBallSpike(x + 18f, floor - 54f, 36f, 54f, group)
            x += 40f
        }
    }

    fun spring(x: Float, floor: Float) {
        springs += RedBallAabb(x, floor - 36f, x + 92f, floor)
    }

    fun crate(x: Float, floor: Float, size: Float = 76f) {
        crates += RedBallCrateSpawn(x, floor - size / 2f, size)
    }

    fun ladder(x: Float, top: Float, bottom: Float) {
        ladders += RedBallAabb(x, top, x + 44f, bottom)
    }

    fun star(x: Float, y: Float) {
        stars += RedBallStarSpawn(x, y)
    }

    fun starOn(x: Float, floor: Float) {
        star(x, floor - 58f)
    }

    fun magnet(x: Float, y: Float) {
        magnets += RedBallMagnetSpawn(x, y)
    }

    fun water(l: Float, t: Float, r: Float, b: Float) {
        waters += RedBallWater(l, t, r, b)
    }

    fun cart(x: Float, floor: Float) {
        carts += RedBallCartSpawn(x, floor - 23f, 124f, 46f)
    }

    fun enemy(
        x: Float,
        floor: Float,
        size: Float,
        left: Float,
        right: Float,
        hits: Int = 1,
        asleep: Boolean = false,
        disguised: Boolean = false,
        defeat: String = "",
    ) {
        enemies += RedBallEnemySpawn(
            x = x,
            y = floor - size / 2f,
            size = size,
            left = left,
            right = right,
            hits = hits,
            asleep = asleep,
            disguised = disguised,
            defeatTrigger = defeat,
        )
    }

    fun door(x: Float, floor: Float) {
        door = RedBallAabb(x, floor - 176f, x + 88f, floor)
    }

    fun start(x: Float, floor: Float) {
        startX = x
        startY = floor - R
    }

    fun bake(id: String, script: RedBallScript): RedBallScene {
        var minX = startX
        var maxX = startX
        var maxY = startY
        fun take(l: Float, t: Float, r: Float, b: Float) {
            if (l < minX) minX = l
            if (r > maxX) maxX = r
            if (b > maxY) maxY = b
        }
        for (box in solids) take(box.l, box.t, box.r, box.b)
        for (box in script.platforms) take(box.box.l, box.box.t, box.box.r, box.box.b)
        for (box in script.bridges) take(box.box.l, box.box.t, box.box.r, box.box.b)
        for (pool in waters) take(pool.l, pool.t, pool.r, pool.b)
        for (pool in script.floods) take(pool.water.l, pool.riseTo, pool.water.r, pool.water.b)
        take(door.l, door.t, door.r, door.b)
        val voidY = maxY + 240f
        return RedBallScene(
            id = id,
            name = title,
            builtin = false,
            worldW = maxX + 200f,
            worldH = voidY + 120f,
            voidY = voidY,
            startX = startX,
            startY = startY,
            solids = solids,
            slopes = slopes,
            spikes = spikes,
            springs = springs,
            door = door,
            crates = crates,
            minX = minX - 120f,
            minY = -220f,
            ladders = ladders,
            stars = stars,
            magnets = magnets,
            enemies = enemies,
            waters = waters,
            carts = carts,
            script = script,
        )
    }
}

private fun act(
    id: String,
    kind: RedBallActKind,
    target: String = "",
    value: Float = 0f,
    x: Float = 0f,
    y: Float = 0f,
    text: String = "",
) = RedBallAct(id, kind, target, value, x, y, text)

private fun campaignLevel(
    index: Int,
    title: String,
    chapter: String,
    skin: RedBallSkinId,
    intro: String,
    twist: String,
    scene: RedBallScene,
) = RedBallCampaignLevel(
    index = index,
    id = "campaign-$index",
    title = title,
    chapter = chapter,
    skin = skin,
    intro = intro,
    twist = twist,
    scene = scene.copy(id = "campaign-$index", name = title),
)

/** 1. Холмы. Учит катиться и прыгать. Милый кубик оказывается врагом. */
private fun levelHills(): RedBallCampaignLevel {
    val twist = "Этот кубик улыбался... а теперь гонится!"
    val stage = Stage("Первый холм")
    stage.ground(-40f, 460f, 760f)
    stage.ground(560f, 1580f, 760f)
    stage.start(130f, 760f)
    stage.door(1420f, 760f)
    stage.crate(250f, 760f)
    stage.starOn(170f, 760f)
    stage.starOn(700f, 760f)
    stage.starOn(1040f, 760f)
    stage.enemy(
        x = 1180f,
        floor = 760f,
        size = 72f,
        left = 980f,
        right = 1360f,
        asleep = true,
        disguised = true,
    )
    val scene = stage.bake(
        "campaign-1",
        RedBallScript(
            triggers = listOf(RedBallTrigger("wake", RedBallAabb(820f, 600f, 980f, 770f))),
            acts = listOf(
                act("wake", RedBallActKind.WAKE, "0"),
                act("wake", RedBallActKind.STORY, text = twist),
            ),
        ),
    )
    return campaignLevel(
        1,
        "Первый холм",
        "Зелёные холмы",
        RedBallSkinId.GREEN_HILLS,
        "Злые квадраты решили сделать весь мир квадратным. Красный мяч покатился через холмы, чтобы их остановить.",
        twist,
        scene,
    )
}

/** 2. Пружина. Флаг на полпути нарисован. */
private fun levelFakeFlag(): RedBallCampaignLevel {
    val twist = "Это не настоящий флаг. Квадраты его нарисовали."
    val stage = Stage("Чужой флаг")
    stage.ground(-40f, 980f, 760f)
    stage.ground(1060f, 1760f, 480f)
    stage.start(120f, 760f)
    stage.spring(820f, 760f)
    stage.door(1560f, 480f)
    stage.starOn(280f, 760f)
    stage.starOn(1240f, 480f)
    stage.starOn(1480f, 480f)
    val scene = stage.bake(
        "campaign-2",
        RedBallScript(
            decoys = listOf(RedBallDecoy("fake", RedBallAabb(520f, 584f, 608f, 760f), "fake")),
            acts = listOf(
                act("fake", RedBallActKind.HIDE_DECOY, "fake"),
                act("fake", RedBallActKind.STORY, text = twist),
            ),
        ),
    )
    return campaignLevel(
        2,
        "Чужой флаг",
        "Зелёные холмы",
        RedBallSkinId.GREEN_HILLS,
        "На холме уже виден флаг. Кажется, день закончен? Сначала докатись и посмотри вблизи.",
        twist,
        scene,
    )
}

/** 3. Горка, шипы и друг, который кладёт мост. */
private fun levelFriend(): RedBallCampaignLevel {
    val twist = "Круглый друг отодвинул шипы. Можно катиться дальше!"
    val stage = Stage("Круглый друг")
    stage.ground(-40f, 440f, 600f)
    stage.slope(440f, 600f, 760f, 760f)
    stage.ground(760f, 980f, 760f)
    stage.ground(980f, 1320f, 980f)
    stage.spikes(1000f, 1280f, 980f, "pit")
    stage.ground(1320f, 1880f, 760f)
    stage.start(120f, 600f)
    stage.door(1640f, 760f)
    stage.starOn(200f, 600f)
    stage.starOn(760f, 660f)
    stage.starOn(1500f, 760f)
    val scene = stage.bake(
        "campaign-3",
        RedBallScript(
            triggers = listOf(
                RedBallTrigger("pal", RedBallAabb(640f, 480f, 900f, 670f), RedBallMark.FRIEND),
            ),
            platforms = listOf(RedBallPlatform("nest", RedBallAabb(620f, 660f, 920f, 708f))),
            bridges = listOf(RedBallBridge("span", RedBallAabb(940f, 760f, 1360f, 808f))),
            acts = listOf(
                act("pal", RedBallActKind.BRIDGE, "span"),
                act("pal", RedBallActKind.STORY, text = twist),
            ),
        ),
    )
    return campaignLevel(
        3,
        "Круглый друг",
        "Зелёные холмы",
        RedBallSkinId.GREEN_HILLS,
        "Горка уходит вниз, а дальше шипы. Где-то рядом прячется круглый друг.",
        twist,
        scene,
    )
}

/** 4. Лес. Лестницы, потом гаснет свет. */
private fun levelDarkForest(): RedBallCampaignLevel {
    val twist = "В лесу погас свет. Иди на звёзды — они светятся."
    val stage = Stage("Ночь в лесу")
    stage.ground(-40f, 460f, 820f)
    stage.spikes(460f, 860f, 820f, "roots")
    stage.ladder(300f, 500f, 860f)
    stage.ground(220f, 820f, 540f)
    stage.ladder(700f, 240f, 580f)
    stage.ground(640f, 1320f, 280f)
    stage.start(120f, 820f)
    stage.door(1120f, 280f)
    stage.starOn(160f, 820f)
    stage.starOn(480f, 540f)
    stage.starOn(980f, 280f)
    stage.enemy(900f, 280f, 64f, 760f, 1080f)
    val scene = stage.bake(
        "campaign-4",
        RedBallScript(
            triggers = listOf(RedBallTrigger("dusk", RedBallAabb(360f, 400f, 700f, 550f))),
            acts = listOf(
                act("dusk", RedBallActKind.DARKEN, value = 0.74f),
                act("dusk", RedBallActKind.STORY, text = twist),
            ),
        ),
    )
    return campaignLevel(
        4,
        "Ночь в лесу",
        "Тёмный лес",
        RedBallSkinId.DEEP_FOREST,
        "Тропа зашла в тёмный лес. Лестницы ведут выше, к просвету между деревьями.",
        twist,
        scene,
    )
}

/** 5. Портал возвращает в изменённый низ леса. */
private fun levelPortal(): RedBallCampaignLevel {
    val twist = "Портал вернул к началу, но мост уже провалился. Внизу другой лес."
    val stage = Stage("Портал назад")
    stage.ground(-40f, 440f, 480f)
    stage.ground(1180f, 1560f, 480f)
    stage.ground(80f, 1480f, 820f)
    stage.start(140f, 480f)
    stage.door(1240f, 820f)
    stage.starOn(220f, 480f)
    stage.starOn(520f, 820f)
    stage.starOn(980f, 820f)
    stage.enemy(640f, 820f, 68f, 420f, 900f, asleep = true)
    val scene = stage.bake(
        "campaign-5",
        RedBallScript(
            doorHidden = true,
            triggers = listOf(
                RedBallTrigger("back", RedBallAabb(1240f, 280f, 1460f, 490f), RedBallMark.PORTAL),
            ),
            platforms = listOf(RedBallPlatform("span", RedBallAabb(440f, 480f, 1180f, 528f), delay = 0.2f)),
            acts = listOf(
                act("back", RedBallActKind.TELEPORT, x = 180f, y = 820f - R),
                act("back", RedBallActKind.COLLAPSE, "span"),
                act("back", RedBallActKind.REVEAL_DOOR),
                act("back", RedBallActKind.WAKE, "0"),
                act("back", RedBallActKind.STORY, text = twist),
            ),
        ),
    )
    return campaignLevel(
        5,
        "Портал назад",
        "Тёмный лес",
        RedBallSkinId.DEEP_FOREST,
        "В глубине леса мерцает портал. Он обещает дорогу вперёд.",
        twist,
        scene,
    )
}

/** 6. Пустыня. Тележка и валун сзади. */
private fun levelCartChase(): RedBallCampaignLevel {
    val twist = "Валун сорвался с холма! Гони тележку и не оглядывайся."
    val stage = Stage("Тележка и валун")
    stage.ground(-40f, 2860f, 760f)
    stage.start(140f, 760f)
    stage.cart(320f, 760f)
    stage.door(2580f, 760f)
    stage.starOn(860f, 760f)
    stage.starOn(1560f, 760f)
    stage.starOn(2140f, 760f)
    val scene = stage.bake(
        "campaign-6",
        RedBallScript(
            triggers = listOf(RedBallTrigger("roll", RedBallAabb(460f, 600f, 620f, 770f))),
            chasers = listOf(
                RedBallChaser("rock", 36f, 760f - 54f, 108f, 176f, 2260f),
            ),
            acts = listOf(
                act("roll", RedBallActKind.CHASE, "rock"),
                act("roll", RedBallActKind.STORY, text = twist),
            ),
        ),
    )
    return campaignLevel(
        6,
        "Тележка и валун",
        "Солнечная пустыня",
        RedBallSkinId.SUNNY_DESERT,
        "Пустыня. У дороги стоит тележка, а на горе сзади дремлет круглый камень.",
        twist,
        scene,
    )
}

/** 7. Пол проваливается в воду. Нырок и лестница наружу. */
private fun levelCollapseWater(): RedBallCampaignLevel {
    val twist = "Пол провалился — и под ним море. Ныряй и ищи лестницу."
    val stage = Stage("Провал")
    stage.ground(-40f, 500f, 640f)
    stage.water(440f, 800f, 1280f, 1040f)
    stage.ladder(1180f, 560f, 1040f)
    stage.ground(1220f, 1860f, 640f)
    stage.start(140f, 640f)
    stage.door(1620f, 640f)
    stage.starOn(220f, 640f)
    stage.star(780f, 930f)
    stage.starOn(1400f, 640f)
    val scene = stage.bake(
        "campaign-7",
        RedBallScript(
            triggers = listOf(RedBallTrigger("drop", RedBallAabb(700f, 480f, 960f, 660f))),
            platforms = listOf(RedBallPlatform("lid", RedBallAabb(500f, 640f, 1040f, 688f), delay = 0.34f)),
            acts = listOf(
                act("drop", RedBallActKind.COLLAPSE, "lid"),
                act("drop", RedBallActKind.STORY, text = twist),
            ),
        ),
    )
    return campaignLevel(
        7,
        "Провал",
        "Водный мир",
        campaignWaterSkin,
        "За пустыней слышно море. Пол над водой выглядит крепким. Почти.",
        twist,
        scene,
    )
}

/** 8. Вода поднимается к флагу, магнит собирает звёзды. */
private fun levelFlood(): RedBallCampaignLevel {
    val twist = "Квадраты открыли шлюз. Вода растёт — плыви к флагу!"
    val stage = Stage("Шлюз")
    stage.ground(40f, 340f, 760f)
    stage.slope(340f, 760f, 560f, 960f)
    stage.ground(560f, 760f, 960f)
    stage.ground(700f, 780f, 560f)
    stage.ground(680f, 1040f, 500f)
    stage.start(160f, 760f)
    stage.door(860f, 500f)
    stage.magnet(640f, 910f)
    stage.star(150f, 700f)
    stage.star(210f, 620f)
    stage.star(250f, 540f)
    val scene = stage.bake(
        "campaign-8",
        RedBallScript(
            triggers = listOf(RedBallTrigger("sluice", RedBallAabb(580f, 820f, 740f, 970f))),
            floods = listOf(
                RedBallFlood(
                    "basin",
                    RedBallWater(90f, 1000f, 690f, 1080f),
                    riseTo = 500f,
                    speed = 62f,
                ),
            ),
            acts = listOf(
                act("sluice", RedBallActKind.RAISE_WATER, "basin"),
                act("sluice", RedBallActKind.STORY, text = twist),
            ),
        ),
    )
    return campaignLevel(
        8,
        "Шлюз",
        "Водный мир",
        campaignWaterSkin,
        "Тихий бассейн внизу и флаг наверху. Магнит поможет не упустить звёзды.",
        twist,
        scene,
    )
}

/** 9. Король появляется рано. От него убегают, а не дерутся. */
private fun levelFlee(): RedBallCampaignLevel {
    val twist = "Король выскочил слишком рано. Убегай — с ним не дерутся!"
    val stage = Stage("Король спешит")
    stage.ground(-40f, 1680f, 700f)
    stage.slope(1680f, 700f, 2140f, 840f)
    stage.ground(2140f, 3200f, 840f)
    stage.start(150f, 700f)
    stage.spring(1080f, 700f)
    stage.door(2920f, 840f)
    stage.starOn(760f, 700f)
    stage.starOn(1500f, 700f)
    stage.starOn(2480f, 840f)
    val scene = stage.bake(
        "campaign-9",
        RedBallScript(
            triggers = listOf(RedBallTrigger("boo", RedBallAabb(520f, 540f, 700f, 710f))),
            chasers = listOf(
                RedBallChaser(
                    id = "king",
                    x = 40f,
                    y = 700f - 66f,
                    size = 132f,
                    speed = 196f,
                    stopX = 2620f,
                    boss = true,
                    wait = true,
                ),
            ),
            acts = listOf(
                act("boo", RedBallActKind.CHASE, "king"),
                act("boo", RedBallActKind.STORY, text = twist),
            ),
        ),
    )
    return campaignLevel(
        9,
        "Король спешит",
        "Солнечная пустыня",
        RedBallSkinId.SUNNY_DESERT,
        "До логова короля квадратов осталось немного. Слишком тихо.",
        twist,
        scene,
    )
}

/** 10. Король на площади. Три прыжка, тяжелее прыжок, друг открывает дверь. */
private fun levelBoss(): RedBallCampaignLevel {
    val twist = "Король тяжелеет мир. Пружины всё ещё подбросят — прыгни на него три раза!"
    val cheer = "Кружок-друг открыл дверь. Мир снова может быть круглым."
    val stage = Stage("Площадь короля")
    stage.ground(-40f, 1760f, 760f)
    stage.start(140f, 760f)
    stage.spring(260f, 760f)
    stage.spring(1240f, 760f)
    stage.door(1540f, 760f)
    stage.spikes(1480f, 1660f, 760f, "seal")
    stage.starOn(200f, 760f)
    stage.starOn(520f, 760f)
    stage.starOn(1360f, 760f)
    stage.enemy(460f, 760f, 68f, 380f, 560f)
    stage.enemy(1080f, 760f, 68f, 980f, 1180f)
    stage.enemy(820f, 760f, 140f, 680f, 980f, hits = 3, defeat = "down")
    val scene = stage.bake(
        "campaign-10",
        RedBallScript(
            doorHidden = true,
            triggers = listOf(RedBallTrigger("heavy", RedBallAabb(640f, 560f, 1000f, 770f))),
            acts = listOf(
                act("heavy", RedBallActKind.GRAVITY, value = 1.22f),
                act("heavy", RedBallActKind.STORY, text = twist),
                act("down", RedBallActKind.REVEAL_DOOR),
                act("down", RedBallActKind.CLEAR_SPIKES, "seal"),
                act("down", RedBallActKind.STORY, text = cheer),
            ),
        ),
    )
    return campaignLevel(
        10,
        "Площадь короля",
        "Зелёные холмы",
        RedBallSkinId.GREEN_HILLS,
        "Король квадратов ждёт на площади. Дверь закрыта, пока он катается.",
        twist,
        scene,
    )
}
