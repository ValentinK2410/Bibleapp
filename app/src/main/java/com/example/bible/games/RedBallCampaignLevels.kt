package com.example.bible.games

import com.example.bible.games.skins.RedBallSkinId

/**
 * Длинные уровни сказки. Каждый в несколько раз шире прежнего:
 * холмы, прыжки, головоломка, враги, след звёзд, секрет и чекпоинты.
 * Поворот сюжета остаётся, просто он больше не случается через два экрана.
 */

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
    private val checkpoints = ArrayList<RedBallCheckpoint>()
    private var door = RedBallAabb(0f, 0f, 40f, 40f)
    private var startX = 120f
    private var startY = 400f

    fun ground(l: Float, r: Float, top: Float) {
        solids += RedBallAabb(l, top, r, top + 72f)
    }

    fun solid(l: Float, t: Float, r: Float, b: Float) {
        solids += RedBallAabb(l, t, r, b)
    }

    fun ledge(l: Float, r: Float, top: Float) {
        solids += RedBallAabb(l, top, r, top + 36f)
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

    fun checkpoint(x: Float, floor: Float) {
        checkpoints += RedBallCheckpoint(x, floor - R)
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
    ): Int {
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
        return enemies.lastIndex
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
        var minY = startY
        var maxY = startY
        fun take(l: Float, t: Float, r: Float, b: Float) {
            if (l < minX) minX = l
            if (r > maxX) maxX = r
            if (t < minY) minY = t
            if (b > maxY) maxY = b
        }
        for (box in solids) take(box.l, box.t, box.r, box.b)
        for (line in slopes) {
            take(line.x0, line.y0, line.x0, line.y0)
            take(line.x1, line.y1, line.x1, line.y1)
        }
        for (box in script.platforms) take(box.box.l, box.box.t, box.box.r, box.box.b)
        for (box in script.bridges) take(box.box.l, box.box.t, box.box.r, box.box.b)
        for (pool in waters) take(pool.l, pool.t, pool.r, pool.b)
        for (pool in script.floods) take(pool.water.l, pool.riseTo, pool.water.r, pool.water.b)
        for (star in stars) take(star.x - 16f, star.y - 16f, star.x + 16f, star.y + 16f)
        for (point in checkpoints) take(point.x, point.y, point.x, point.y)
        for (enemy in enemies) take(enemy.x - enemy.size, enemy.y - enemy.size, enemy.x + enemy.size, enemy.y + enemy.size)
        for (chaser in script.chasers) take(chaser.x, chaser.y, chaser.stopX, chaser.y + chaser.size)
        take(door.l, door.t, door.r, door.b)
        val voidY = maxY + 320f
        return RedBallScene(
            id = id,
            name = title,
            builtin = false,
            worldW = maxX + 240f,
            worldH = voidY + 200f,
            voidY = voidY,
            startX = startX,
            startY = startY,
            solids = solids,
            slopes = slopes,
            spikes = spikes,
            springs = springs,
            door = door,
            crates = crates,
            minX = minX - 180f,
            minY = minY - 260f,
            ladders = ladders,
            stars = stars,
            magnets = magnets,
            enemies = enemies,
            waters = waters,
            carts = carts,
            checkpoints = checkpoints,
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

private class Course(title: String) {
    private val stage = Stage(title)
    val sections = ArrayList<String>()
    private val triggers = ArrayList<RedBallTrigger>()
    private val acts = ArrayList<RedBallAct>()
    private val platforms = ArrayList<RedBallPlatform>()
    private val floods = ArrayList<RedBallFlood>()
    private val chasers = ArrayList<RedBallChaser>()
    private val bridges = ArrayList<RedBallBridge>()
    private val decoys = ArrayList<RedBallDecoy>()
    var doorHidden = false
    var x = 300f
    var y = 760f
    private var serial = 0

    fun section(name: String) {
        sections += name
    }

    fun begin(floor: Float) {
        y = floor
        x = 320f
        stage.ground(-40f, 320f, y)
        stage.start(140f, y)
    }

    fun ground(len: Float) {
        stage.ground(x, x + len, y)
        x += len
    }

    fun gap(w: Float = 92f) {
        x += w
    }

    fun slope(to: Float, run: Float = 360f) {
        stage.slope(x, y, x + run, to)
        x += run
        y = to
    }

    fun star(back: Float = 120f) {
        stage.starOn(x - back, y)
    }

    fun save(back: Float = 160f) {
        stage.checkpoint(x - back, y)
    }

    fun crate(back: Float = 220f) {
        stage.crate(x - back, y)
    }

    fun foe(
        back: Float = 200f,
        range: Float = 70f,
        hits: Int = 1,
        asleep: Boolean = false,
        disguised: Boolean = false,
        defeat: String = "",
        size: Float = 68f,
    ): Int = foeAt(x - back, y, range, hits, asleep, disguised, defeat, size)

    fun foeAt(
        at: Float,
        floor: Float,
        range: Float = 70f,
        hits: Int = 1,
        asleep: Boolean = false,
        disguised: Boolean = false,
        defeat: String = "",
        size: Float = 68f,
    ): Int = stage.enemy(at, floor, size, at - range, at + range, hits, asleep, disguised, defeat)

    fun cave() {
        val left = x
        val span = 100f
        x += span
        val caveY = y + 250f
        stage.ground(left - 40f, x + 20f, caveY)
        stage.ladder(x - 46f, y, caveY + 8f)
        stage.starOn(left + 28f, caveY)
    }

    fun hazard() {
        val span = 96f
        val pit = y + 210f
        stage.ground(x - 6f, x + span + 6f, pit)
        stage.spikes(x, x + span, pit)
        x += span
    }

    fun gate(span: Float = 230f) {
        val id = "sw${serial++}"
        val tx = x - 90f
        triggers += RedBallTrigger(
            id,
            RedBallAabb(tx - 36f, y - 160f, tx + 36f, y + 12f),
            RedBallMark.SWITCH,
        )
        val left = x
        x += span
        bridges += RedBallBridge(id, RedBallAabb(left - 8f, y, x + 8f, y + 46f))
        acts += act(id, RedBallActKind.BRIDGE, id)
    }

    fun steps(n: Int) {
        x += 74f
        var hy = y
        repeat(n) { i ->
            if (i > 0) hy -= 76f
            stage.ledge(x, x + 146f, hy)
            if (i == n - 1) stage.starOn(x + 72f, hy)
            x += 146f
            if (i < n - 1) x += 86f
        }
        y = hy
    }

    fun bounce(up: Float = 120f) {
        stage.spring(x - 140f, y)
        x += 70f
        y -= up
        stage.ground(x, x + 300f, y)
        x += 300f
    }

    fun climb(height: Float = 210f) {
        val top = y - height
        stage.ladder(x - 28f, top, y + 30f)
        y = top
        stage.ground(x, x + 340f, y)
        x += 340f
    }

    fun islands(n: Int, len: Float = 210f) {
        repeat(n) { i ->
            stage.ground(x, x + len, y)
            if (i == n / 2) stage.starOn(x + len * 0.5f, y)
            x += len
            if (i < n - 1) x += 92f
        }
    }

    fun zone(id: String, atX: Float, floor: Float, mark: RedBallMark = RedBallMark.NONE) {
        triggers += RedBallTrigger(id, RedBallAabb(atX - 46f, floor - 180f, atX + 46f, floor + 14f), mark)
    }

    fun on(
        id: String,
        kind: RedBallActKind,
        target: String = "",
        value: Float = 0f,
        px: Float = 0f,
        py: Float = 0f,
        text: String = "",
    ) {
        acts += act(id, kind, target, value, px, py, text)
    }

    fun wakeBehind(distance: Float, index: Int, text: String) {
        val id = "wk${serial++}"
        zone(id, x - distance, y)
        on(id, RedBallActKind.WAKE, index.toString())
        on(id, RedBallActKind.STORY, text = text)
    }

    fun fakeFlag(back: Float, text: String) {
        val dx = x - back
        decoys += RedBallDecoy("fake", RedBallAabb(dx, y - 176f, dx + 88f, y), "fake")
        on("fake", RedBallActKind.HIDE_DECOY, "fake")
        on("fake", RedBallActKind.STORY, text = text)
    }

    fun friendPit(text: String) {
        val span = 280f
        zone("pal", x - 180f, y, RedBallMark.FRIEND)
        platforms += RedBallPlatform("nest", RedBallAabb(x - 420f, y - 150f, x - 250f, y - 108f))
        val pit = y + 190f
        stage.ground(x, x + span, pit)
        stage.spikes(x + 12f, x + span - 12f, pit, "pit")
        bridges += RedBallBridge("span", RedBallAabb(x - 12f, y, x + span + 12f, y + 46f))
        on("pal", RedBallActKind.BRIDGE, "span")
        on("pal", RedBallActKind.STORY, text = text)
        x += span
    }

    fun shortcut(fromX: Float, fromFloor: Float, toX: Float, toFloor: Float) {
        val id = "pt${serial++}"
        zone(id, fromX, fromFloor, RedBallMark.PORTAL)
        on(id, RedBallActKind.TELEPORT, px = toX, py = toFloor - R)
    }

    fun finish() {
        stage.ground(x, x + 300f, y)
        stage.door(x + 110f, y)
        x += 300f
    }

    fun sealedDoor() {
        stage.ground(x, x + 340f, y)
        stage.spikes(x + 30f, x + 130f, y, "seal")
        stage.door(x + 170f, y)
        x += 340f
    }

    fun bake(id: String): RedBallScene {
        val script = RedBallScript(
            triggers = triggers,
            acts = acts,
            platforms = platforms,
            floods = floods,
            chasers = chasers,
            bridges = bridges,
            decoys = decoys,
            doorHidden = doorHidden,
        )
        return stage.bake(id, script)
    }

    fun chaser(
        id: String,
        fromX: Float,
        stopX: Float,
        speed: Float,
        floor: Float,
        size: Float = 108f,
        boss: Boolean = false,
        wait: Boolean = false,
    ) {
        chasers += RedBallChaser(id, fromX, floor - size / 2f, size, speed, stopX, boss, wait)
    }

    fun flood(id: String, water: RedBallWater, riseTo: Float, speed: Float) {
        floods += RedBallFlood(id, water, riseTo, speed)
    }

    fun platform(id: String, box: RedBallAabb, delay: Float) {
        platforms += RedBallPlatform(id, box, delay)
    }

    fun rawGround(l: Float, r: Float, top: Float) = stage.ground(l, r, top)
    fun rawSolid(l: Float, t: Float, r: Float, b: Float) = stage.solid(l, t, r, b)
    fun rawWater(l: Float, t: Float, r: Float, b: Float) = stage.water(l, t, r, b)
    fun rawStar(sx: Float, floor: Float) = stage.starOn(sx, floor)
    fun rawStarAt(sx: Float, sy: Float) = stage.star(sx, sy)
    fun rawSave(sx: Float, floor: Float) = stage.checkpoint(sx, floor)
    fun rawLadder(sx: Float, top: Float, bottom: Float) = stage.ladder(sx, top, bottom)
    fun rawSpring(sx: Float, floor: Float) = stage.spring(sx, floor)
    fun rawMagnet(sx: Float, sy: Float) = stage.magnet(sx, sy)
    fun rawCart(sx: Float, floor: Float) = stage.cart(sx, floor)
    fun rawSpikes(l: Float, r: Float, floor: Float, group: String = "") = stage.spikes(l, r, floor, group)
}

private fun courseLevel(
    index: Int,
    title: String,
    chapter: String,
    skin: RedBallSkinId,
    intro: String,
    twist: String,
    build: Course.() -> Unit,
): RedBallCampaignLevel {
    val course = Course(title)
    course.build()
    return RedBallCampaignLevel(
        index = index,
        id = "campaign-$index",
        title = title,
        chapter = chapter,
        skin = skin,
        intro = intro,
        twist = twist,
        sections = course.sections.toList(),
        scene = course.bake("campaign-$index"),
    )
}

/** 1. Холмы. Милый кубик оказывается врагом. */
internal fun levelHills() = courseLevel(
    1,
    "Первый холм",
    "Зелёные холмы",
    RedBallSkinId.GREEN_HILLS,
    "Злые квадраты решили сделать весь мир квадратным. Холмы длинные: катись, прыгай и не верь каждому ящику.",
    "Этот кубик улыбался... а теперь гонится!",
) {
    val twist = "Этот кубик улыбался... а теперь гонится!"
    begin(760f)
    section("Луг и ящик")
    ground(980f)
    crate(420f)
    star(560f)
    section("Холмы")
    slope(600f, 400f)
    ground(420f)
    star(180f)
    slope(880f, 460f)
    ground(520f)
    star(200f)
    slope(740f, 380f)
    ground(640f)
    section("Рычаг")
    save(180f)
    gate()
    ground(720f)
    star(240f)
    section("Пещера")
    cave()
    ground(560f)
    section("Проснувшийся кубик")
    ground(1100f)
    val cube = foe(back = 280f, range = 110f, asleep = true, disguised = true, size = 72f)
    wakeBehind(640f, cube, twist)
    ground(520f)
    foe(back = 200f, range = 60f)
    section("Пружина к флагу")
    ground(480f)
    save(170f)
    bounce(120f)
    star(140f)
    ground(860f)
    steps(2)
    ground(420f)
    finish()
}

/** 2. Нарисованный флаг. Настоящая дверь далеко вверху. */
internal fun levelFakeFlag() = courseLevel(
    2,
    "Чужой флаг",
    "Зелёные холмы",
    RedBallSkinId.GREEN_HILLS,
    "На холме уже виден флаг. До него ещё долго, и не всякая дверь настоящая.",
    "Это не настоящий флаг. Квадраты его нарисовали.",
) {
    val twist = "Это не настоящий флаг. Квадраты его нарисовали."
    begin(760f)
    section("Дорога к нарисованному флагу")
    ground(1500f)
    star(700f)
    crate(400f)
    fakeFlag(520f, twist)
    slope(640f, 400f)
    ground(480f)
    section("Настоящий подъём")
    bounce(130f)
    star(150f)
    ground(700f)
    steps(2)
    ground(540f)
    section("Рычаг и ящик")
    save(180f)
    crate(260f)
    gate(240f)
    ground(800f)
    star(300f)
    section("Секрет под горкой")
    cave()
    ground(640f)
    section("Квадраты на высоте")
    foe(back = 220f)
    ground(500f)
    hazard()
    ground(420f)
    foe(back = 180f)
    save(160f)
    section("Настоящая дверь")
    ground(900f)
    star(400f)
    ground(700f)
    finish()
}

/** 3. Друг кладёт мост через шипы. Дальше острова. */
internal fun levelFriend() = courseLevel(
    3,
    "Круглый друг",
    "Зелёные холмы",
    RedBallSkinId.GREEN_HILLS,
    "Горка уходит вниз, а дальше шипы. Где-то рядом прячется круглый друг — и это только начало дороги.",
    "Круглый друг отодвинул шипы. Можно катиться дальше!",
) {
    val twist = "Круглый друг отодвинул шипы. Можно катиться дальше!"
    begin(600f)
    section("Горка")
    ground(900f)
    star(400f)
    crate(250f)
    slope(860f, 480f)
    ground(700f)
    star(220f)
    section("Мост друга")
    ground(640f)
    save(180f)
    friendPit(twist)
    ground(520f)
    section("Острова")
    gap(92f)
    islands(5, 230f)
    ground(400f)
    foe(back = 180f)
    section("Секрет")
    cave()
    ground(700f)
    section("Подъём")
    climb(200f)
    star(160f)
    ground(640f)
    save(170f)
    hazard()
    ground(480f)
    foe(back = 200f)
    section("Флаг за холмом")
    slope(700f, 360f)
    ground(1100f)
    star(400f)
    ground(800f)
    finish()
}

/** 4. Лес гаснет. Дорогу держат звёзды, лестницы и короткий портал. */
internal fun levelDarkForest() = courseLevel(
    4,
    "Ночь в лесу",
    "Тёмный лес",
    RedBallSkinId.DEEP_FOREST,
    "Тропа зашла в тёмный лес. Лестницы ведут выше, а когда погаснет свет, смотри на звёзды.",
    "В лесу погас свет. Иди на звёзды — они светятся.",
) {
    val twist = "В лесу погас свет. Иди на звёзды — они светятся."
    begin(860f)
    section("Тропа")
    ground(780f)
    star(360f)
    crate(240f)
    slope(700f, 420f)
    ground(520f)
    foe(back = 220f)
    section("Лестницы")
    climb(220f)
    star(150f)
    ground(480f)
    climb(180f)
    ground(420f)
    section("Темнота")
    ground(700f)
    val dusk = x - 200f
    val floor = y
    zone("dusk", dusk, floor)
    on("dusk", RedBallActKind.DARKEN, value = 0.74f)
    on("dusk", RedBallActKind.STORY, text = twist)
    star(180f)
    ground(600f)
    save(160f)
    section("Портал-срез")
    val from = x - 80f
    hazard()
    ground(520f)
    hazard()
    ground(480f)
    shortcut(from, floor, x - 200f, y)
    star(160f)
    section("Секрет в кроне")
    cave()
    ground(640f)
    foe(back = 240f)
    save(180f)
    section("Просвет")
    ground(680f)
    star(280f)
    steps(2)
    ground(500f)
    finish()
}

/** 5. Портал возвращает вниз, мост проваливается, дальше другой лес. */
internal fun levelPortal() = courseLevel(
    5,
    "Портал назад",
    "Тёмный лес",
    RedBallSkinId.DEEP_FOREST,
    "В глубине леса мерцает портал. Он обещает дорогу вперёд — и обманывает только наполовину.",
    "Портал вернул к началу, но мост уже провалился. Внизу другой лес.",
) {
    val twist = "Портал вернул к началу, но мост уже провалился. Внизу другой лес."
    doorHidden = true
    begin(460f)
    section("Верхний лес")
    ground(900f)
    star(400f)
    crate(280f)
    slope(640f, 420f)
    ground(780f)
    star(240f)
    save(200f)
    section("Мост")
    val spanL = x
    val upper = y
    platform("span", RedBallAabb(spanL, upper, spanL + 900f, upper + 48f), 0.2f)
    x += 900f
    ground(480f)
    star(160f)
    val fromX = x - 150f
    section("Портал")
    rawSolid(x, upper - 380f, x + 80f, upper + 80f)
    val landFloor = 980f
    val landX = 780f
    x = 600f
    y = landFloor
    ground(1000f)
    rawSave(860f, landFloor)
    section("Другой лес")
    ground(1800f)
    star(500f)
    val sleeper = foe(back = 420f, range = 90f, asleep = true)
    cave()
    ground(1500f)
    star(400f)
    save(220f)
    foe(back = 240f)
    ground(900f)
    hazard()
    ground(700f)
    steps(2)
    ground(800f)
    star(300f)
    ground(900f)
    finish()
    val id = "back"
    zone(id, fromX, upper, RedBallMark.PORTAL)
    on(id, RedBallActKind.TELEPORT, px = landX, py = landFloor - R)
    on(id, RedBallActKind.COLLAPSE, "span")
    on(id, RedBallActKind.REVEAL_DOOR)
    on(id, RedBallActKind.WAKE, sleeper.toString())
    on(id, RedBallActKind.STORY, text = twist)
}

/** 6. Пустыня, тележка и валун на длинной прямой. */
internal fun levelCartChase() = courseLevel(
    6,
    "Тележка и валун",
    "Солнечная пустыня",
    RedBallSkinId.SUNNY_DESERT,
    "Пустыня широкая. Сначала дюны, потом тележка, и только после камня можно выдохнуть.",
    "Валун сорвался с холма! Гони тележку и не оглядывайся.",
) {
    val twist = "Валун сорвался с холма! Гони тележку и не оглядывайся."
    begin(760f)
    section("Дюны")
    ground(700f)
    star(300f)
    slope(640f, 420f)
    ground(480f)
    slope(820f, 480f)
    ground(560f)
    crate(200f)
    save(160f)
    section("Тележка и валун")
    val chase = x
    val floor = y
    ground(2800f)
    star(1400f)
    ground(2800f)
    star(1200f)
    ground(2800f)
    star(1000f)
    ground(2200f)
    val stop = x - 280f
    rawCart(chase + 240f, floor)
    chaser("rock", chase - 120f, stop, 190f, floor, 112f)
    zone("roll", chase + 460f, floor)
    on("roll", RedBallActKind.CHASE, "rock")
    on("roll", RedBallActKind.STORY, text = twist)
    rawSave(chase + 900f, floor)
    rawSave(chase + 5200f, floor)
    section("После камня")
    ground(520f)
    save(180f)
    gate(240f)
    ground(640f)
    rawSpring(x - 200f, y)
    section("Секрет")
    cave()
    ground(500f)
    foe(back = 200f)
    section("Флаг")
    ground(800f)
    star(300f)
    ground(600f)
    finish()
}

/** 7. Пол проваливается в длинное море. */
internal fun levelCollapseWater() = courseLevel(
    7,
    "Провал",
    "Водный мир",
    campaignWaterSkin,
    "За пустыней море. Пол над водой выглядит крепким — и нырять придётся долго.",
    "Пол провалился — и под ним море. Ныряй и ищи лестницу.",
) {
    val twist = "Пол провалился — и под ним море. Ныряй и ищи лестницу."
    begin(700f)
    section("Берег")
    ground(980f)
    star(450f)
    crate(300f)
    slope(820f, 400f)
    ground(720f)
    save(180f)
    val from = x - 100f
    val beach = y
    ground(360f)
    shortcut(from, beach, x - 140f, y)
    section("Провал")
    val lip = x
    val floor = y
    zone("drop", lip + 40f, floor)
    on("drop", RedBallActKind.COLLAPSE, "lid")
    on("drop", RedBallActKind.STORY, text = twist)
    platform("lid", RedBallAabb(lip, floor, lip + 480f, floor + 48f), 0.22f)
    x = lip + 480f
    val surface = floor + 42f
    val seaEnd = lip + 5200f
    section("Длинный нырок")
    rawWater(lip - 16f, surface, seaEnd, surface + 360f)
    var tunnel = lip + 780f
    while (tunnel < seaEnd - 700f) {
        rawSolid(tunnel, surface - 22f, tunnel + 430f, surface + 108f)
        tunnel += 430f + 300f
    }
    val raft = lip + 1280f
    rawGround(raft, raft + 180f, surface - 8f)
    rawLadder(raft - 20f, surface - 8f, surface + 250f)
    rawSave(raft + 90f, surface - 8f)
    rawStar(raft + 130f, surface - 8f)
    section("Причал")
    x = seaEnd
    y = floor
    ground(640f)
    star(220f)
    save(160f)
    section("Секретная бухта")
    cave()
    ground(560f)
    foe(back = 200f)
    section("Лестница к флагу")
    climb(180f)
    star(140f)
    ground(700f)
    finish()
}

/** 8. Вода поднимается и несёт через длинный залив. */
internal fun levelFlood() = courseLevel(
    8,
    "Шлюз",
    "Водный мир",
    campaignWaterSkin,
    "Тихий бассейн внизу и флаг высоко. Когда откроется шлюз, плыть придётся через весь залив.",
    "Квадраты открыли шлюз. Вода растёт — плыви к флагу!",
) {
    val twist = "Квадраты открыли шлюз. Вода растёт — плыви к флагу!"
    begin(760f)
    section("Спуск")
    ground(640f)
    star(280f)
    crate(200f)
    slope(980f, 520f)
    ground(700f)
    rawMagnet(x - 240f, y - 80f)
    rawStarAt(x - 240f, y - 200f)
    save(180f)
    section("Секретная ниша")
    cave()
    ground(420f)
    foe(back = 160f)
    section("Шлюз")
    ground(480f)
    val basinL = 420f
    val basinR = x + 2700f
    val low = 1120f
    val rise = 620f
    rawWater(basinL, low, basinR, low + 220f)
    flood("basin", RedBallWater(basinL, low, basinR, low + 220f), rise, 70f)
    zone("sluice", x - 160f, y)
    on("sluice", RedBallActKind.RAISE_WATER, "basin")
    on("sluice", RedBallActKind.STORY, text = twist)
    section("Длинный залив")
    var slab = x + 360f
    while (slab < basinR - 800f) {
        rawSolid(slab, rise - 18f, slab + 460f, rise + 100f)
        slab += 460f + 260f
    }
    val pocket = x + 900f
    rawGround(pocket, pocket + 170f, rise - 10f)
    rawLadder(pocket - 18f, rise - 10f, low - 20f)
    rawSave(pocket + 80f, rise - 10f)
    rawStar(pocket + 120f, rise - 10f)
    section("Плотина")
    x = basinR - 40f
    y = 640f
    ground(520f)
    star(180f)
    save(150f)
    ground(480f)
    finish()
}

/** 9. Король выскакивает рано. От него убегают через всю пустыню. */
internal fun levelFlee() = courseLevel(
    9,
    "Король спешит",
    "Солнечная пустыня",
    RedBallSkinId.SUNNY_DESERT,
    "До логова короля ещё далеко. Слишком тихо — и тишина кончается рано.",
    "Король выскочил слишком рано. Убегай — с ним не дерутся!",
) {
    val twist = "Король выскочил слишком рано. Убегай — с ним не дерутся!"
    begin(700f)
    section("Тишина")
    ground(900f)
    star(400f)
    crate(280f)
    slope(820f, 420f)
    ground(640f)
    save(170f)
    section("Король")
    val chase = x
    val floor = y
    rawSpring(chase + 360f, floor)
    repeat(18) { i ->
        ground(460f)
        if (i == 5 || i == 11) star(180f)
        if (i == 8) save(200f)
        if (i < 17) gap(90f)
    }
    val stop = x - 200f
    chaser("king", chase - 160f, stop, 205f, floor, 128f, boss = true, wait = true)
    zone("boo", chase + 280f, floor)
    on("boo", RedBallActKind.CHASE, "king")
    on("boo", RedBallActKind.STORY, text = twist)
    section("После погони")
    ground(560f)
    save(180f)
    gate(230f)
    ground(480f)
    section("Секрет")
    cave()
    ground(420f)
    foe(back = 180f)
    section("Флаг")
    bounce(110f)
    star(140f)
    ground(800f)
    finish()
}

/** 10. Длинный подход и две фазы короля. Дверь открывает друг. */
internal fun levelBoss() = courseLevel(
    10,
    "Площадь короля",
    "Зелёные холмы",
    RedBallSkinId.GREEN_HILLS,
    "Король квадратов ждёт на площади. Сначала до неё ещё нужно доехать, и одного прыжка ему мало.",
    "Король тяжелеет мир. Пружины всё ещё подбросят — прыгни на него три раза!",
) {
    val twist = "Король тяжелеет мир. Пружины всё ещё подбросят — прыгни на него три раза!"
    val cheer = "Кружок-друг открыл дверь. Мир снова может быть круглым."
    doorHidden = true
    begin(760f)
    section("Подход")
    ground(1100f)
    star(500f)
    crate(320f)
    slope(640f, 400f)
    ground(520f)
    bounce(120f)
    star(140f)
    ground(700f)
    val from = x - 120f
    val floorNow = y
    ground(640f)
    shortcut(from, floorNow, x - 180f, y)
    section("Секрет перед площадью")
    cave()
    ground(600f)
    foe(back = 220f)
    save(180f)
    steps(2)
    ground(480f)
    section("Площадь")
    val arena = x
    ground(2400f)
    val floor = y
    val boss1 = foeAt(arena + 780f, floor, 50f, hits = 2, asleep = true, defeat = "phase2", size = 120f)
    val boss2 = foeAt(arena + 1500f, floor, 50f, hits = 3, asleep = true, defeat = "down", size = 136f)
    foeAt(arena + 420f, floor, 50f)
    rawSave(arena + 260f, floor)
    rawSpring(arena + 980f, floor)
    rawSpring(arena + 1700f, floor)
    zone("arena", arena + 140f, floor)
    on("arena", RedBallActKind.GRAVITY, value = 1.18f)
    on("arena", RedBallActKind.WAKE, boss1.toString())
    on("arena", RedBallActKind.STORY, text = twist)
    on("phase2", RedBallActKind.GRAVITY, value = 1.52f)
    on("phase2", RedBallActKind.WAKE, boss2.toString())
    on("phase2", RedBallActKind.STORY, text = "Король зовёт стражу. Мир стал ещё тяжелее.")
    on("down", RedBallActKind.REVEAL_DOOR)
    on("down", RedBallActKind.CLEAR_SPIKES, "seal")
    on("down", RedBallActKind.GRAVITY, value = 1f)
    on("down", RedBallActKind.STORY, text = cheer)
    section("Дверь друга")
    sealedDoor()
}
