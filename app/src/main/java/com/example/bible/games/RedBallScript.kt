package com.example.bible.games

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Как нарисовать зону, которая сама по себе не предмет редактора. */
enum class RedBallMark {
    NONE,
    PORTAL,
    FRIEND,
    SWITCH,
}

/**
 * Прямоугольник, в который мяч заезжает один раз.
 * Дальше срабатывают [RedBallAct] с тем же [id].
 */
data class RedBallTrigger(
    val id: String,
    val box: RedBallAabb,
    val mark: RedBallMark = RedBallMark.NONE,
)

enum class RedBallActKind {
    COLLAPSE,
    RAISE_WATER,
    DARKEN,
    CHASE,
    BRIDGE,
    REVEAL_DOOR,
    HIDE_DECOY,
    WAKE,
    GRAVITY,
    STORY,
    CLEAR_SPIKES,
    TELEPORT,
}

/** Одно действие сценария. [target] — id площадки, воды, моста, шипов или индекс врага. */
data class RedBallAct(
    val triggerId: String,
    val kind: RedBallActKind,
    val target: String = "",
    val value: Float = 0f,
    val x: Float = 0f,
    val y: Float = 0f,
    val text: String = "",
)

/** Пол, который после триггера трясётся и проваливается. Пока стоит — это твёрдое тело. */
data class RedBallPlatform(
    val id: String,
    val box: RedBallAabb,
    val delay: Float = 0.42f,
)

/**
 * Вода, которой нет в [RedBallScene.waters], пока уровень не включил подъём.
 * [riseTo] — новая поверхность. Ось Y вниз, поэтому меньшее число — выше.
 */
data class RedBallFlood(
    val id: String,
    val water: RedBallWater,
    val riseTo: Float,
    val speed: Float = 52f,
)

/** Круглый камень или король, который после триггера катится до [stopX] и там встаёт. */
data class RedBallChaser(
    val id: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val stopX: Float,
    val boss: Boolean = false,
    /** Пока триггер не сработал, фигуру не рисуют: она «появляется». */
    val wait: Boolean = false,
)

/** Помощь друга: твёрдый мост, которого не было, пока не сработал триггер. */
data class RedBallBridge(
    val id: String,
    val box: RedBallAabb,
)

/** Флаг, который выглядит как дверь, но победу не даёт. Касание включает [triggerId]. */
data class RedBallDecoy(
    val id: String,
    val box: RedBallAabb,
    val triggerId: String,
)

/**
 * Надстройка над [RedBallScene] для поворотов сюжета.
 * Редактор и встроенная горка оставляют [NONE]: физика от этого не меняется.
 */
data class RedBallScript(
    val triggers: List<RedBallTrigger> = emptyList(),
    val acts: List<RedBallAct> = emptyList(),
    val platforms: List<RedBallPlatform> = emptyList(),
    val floods: List<RedBallFlood> = emptyList(),
    val chasers: List<RedBallChaser> = emptyList(),
    val bridges: List<RedBallBridge> = emptyList(),
    val decoys: List<RedBallDecoy> = emptyList(),
    val doorHidden: Boolean = false,
    val gravity: Float = 1f,
    val darkness: Float = 0f,
) {
    companion object {
        val NONE = RedBallScript()
    }
}

/** Чем кончился набор уже сыгранных триггеров. Проверка пути читает это без таймеров. */
internal data class RedBallEffect(
    val gonePlatforms: Set<String>,
    val bridges: Set<String>,
    val waterTop: Map<String, Float>,
    val doorVisible: Boolean,
    val clearSpikes: Set<String>,
    val darkness: Float,
    val gravity: Float,
    val hiddenDecoys: Set<String>,
    val chasing: Set<String>,
    val awake: Set<Int>,
    val teleport: Pair<Float, Float>?,
    val story: String?,
)

internal fun RedBallScene.effect(fired: Set<String>): RedBallEffect {
    val gone = HashSet<String>()
    val bridges = HashSet<String>()
    val water = HashMap<String, Float>()
    for (flood in script.floods) water[flood.id] = flood.water.t
    var door = !script.doorHidden
    val spikes = HashSet<String>()
    var dark = script.darkness
    var gravity = script.gravity
    val hidden = HashSet<String>()
    val chasing = HashSet<String>()
    val awake = HashSet<Int>()
    var teleport: Pair<Float, Float>? = null
    var story: String? = null
    for (act in script.acts) {
        if (act.triggerId !in fired) continue
        when (act.kind) {
            RedBallActKind.COLLAPSE -> if (act.target.isNotBlank()) gone += act.target
            RedBallActKind.RAISE_WATER -> {
                val flood = script.floods.firstOrNull { it.id == act.target }
                if (flood != null) water[flood.id] = flood.riseTo
            }
            RedBallActKind.DARKEN -> dark = act.value
            RedBallActKind.CHASE -> if (act.target.isNotBlank()) chasing += act.target
            RedBallActKind.BRIDGE -> if (act.target.isNotBlank()) bridges += act.target
            RedBallActKind.REVEAL_DOOR -> door = true
            RedBallActKind.HIDE_DECOY -> if (act.target.isNotBlank()) hidden += act.target
            RedBallActKind.WAKE -> act.target.toIntOrNull()?.let { awake += it }
            RedBallActKind.GRAVITY -> gravity = act.value
            RedBallActKind.STORY -> if (act.text.isNotBlank()) story = act.text
            RedBallActKind.CLEAR_SPIKES -> if (act.target.isNotBlank()) spikes += act.target
            RedBallActKind.TELEPORT -> teleport = act.x to act.y
        }
    }
    return RedBallEffect(gone, bridges, water, door, spikes, dark, gravity, hidden, chasing, awake, teleport, story)
}

internal fun RedBallScene.solidsWhen(fired: Set<String>): List<RedBallAabb> {
    val fx = effect(fired)
    val out = ArrayList<RedBallAabb>(solids.size + concrete.size + script.platforms.size)
    out += solids
    out += concrete
    for (platform in script.platforms) if (platform.id !in fx.gonePlatforms) out += platform.box
    for (bridge in script.bridges) if (bridge.id in fx.bridges) out += bridge.box
    return out
}

internal fun RedBallScene.watersWhen(fired: Set<String>): List<RedBallWater> {
    val fx = effect(fired)
    val out = ArrayList<RedBallWater>(waters.size + script.floods.size)
    out += waters
    for (flood in script.floods) {
        val top = fx.waterTop[flood.id] ?: flood.water.t
        out += flood.water.copy(t = top)
    }
    return out
}

/** Где сейчас камень погони. Рисует и проверка удара. */
class RedBallChaserState(
    val id: String,
    var x: Float,
    var y: Float,
    val size: Float,
    val boss: Boolean,
    var active: Boolean,
    val waiting: Boolean,
)

/**
 * Живое состояние сценария одной попытки.
 * Таймеры обвала и подъёма воды живут здесь, а не в JSON уровня.
 */
class RedBallDirector(private val scene: RedBallScene) {
    var darkness: Float = scene.script.darkness
        private set
    var gravityScale: Float = scene.script.gravity
        private set
    var story: String? = null
        private set
    var doorVisible: Boolean = !scene.script.doorHidden
        private set

    private val spent = HashSet<String>()
    private val clearedSpikes = HashSet<String>()
    private val hiddenDecoys = HashSet<String>()
    private val awake = HashSet<Int>()
    private var teleport: Pair<Float, Float>? = null
    private val wakeNow = ArrayList<Int>(2)
    private var storyLeft = 0f

    private class Plat(val spec: RedBallPlatform) {
        var mode = 0
        var timer = 0f
        var drop = 0f
    }

    private val plats = scene.script.platforms.map { Plat(it) }
    private val bridgeOn = BooleanArray(scene.script.bridges.size)
    private class FloodRun(val spec: RedBallFlood) {
        var top = spec.water.t
        var rising = false
    }

    private val floods = scene.script.floods.map { FloodRun(it) }
    private val runners = scene.script.chasers.map {
        RedBallChaserState(it.id, it.x, it.y, it.size, it.boss, active = false, waiting = it.wait)
    }

    fun reset() {
        darkness = scene.script.darkness
        gravityScale = scene.script.gravity.coerceIn(0.35f, 3f)
        story = null
        storyLeft = 0f
        doorVisible = !scene.script.doorHidden
        spent.clear()
        clearedSpikes.clear()
        hiddenDecoys.clear()
        awake.clear()
        teleport = null
        wakeNow.clear()
        for (plat in plats) {
            plat.mode = 0
            plat.timer = 0f
            plat.drop = 0f
        }
        for (i in bridgeOn.indices) bridgeOn[i] = false
        for (flood in floods) {
            flood.top = flood.spec.water.t
            flood.rising = false
        }
        for ((index, chaser) in scene.script.chasers.withIndex()) {
            val run = runners[index]
            run.x = chaser.x
            run.y = chaser.y
            run.active = false
        }
    }

    fun dismissStory() {
        story = null
        storyLeft = 0f
    }

    fun spikeEnabled(spike: RedBallSpike): Boolean =
        spike.group.isEmpty() || spike.group !in clearedSpikes

    fun solids(): List<RedBallAabb> {
        val out = ArrayList<RedBallAabb>(scene.solids.size + plats.size + bridgeOn.size)
        out += scene.solids
        for (plat in plats) if (plat.mode <= 1) out += plat.spec.box
        for (i in bridgeOn.indices) if (bridgeOn[i]) out += scene.script.bridges[i].box
        return out
    }

    /** Площадки и мосты, которые ещё видно. Упавшие уже уехали вниз. */
    fun visualSolids(): List<RedBallAabb> {
        val out = ArrayList<RedBallAabb>()
        val shake = sin(shakeClock * 38f) * 5f
        for (plat in plats) {
            if (plat.mode >= 3) continue
            val box = plat.spec.box
            val dx = if (plat.mode == 1) shake else 0f
            out += RedBallAabb(box.l + dx, box.t + plat.drop, box.r + dx, box.b + plat.drop, box.friction)
        }
        for (i in bridgeOn.indices) if (bridgeOn[i]) out += scene.script.bridges[i].box
        return out
    }

    fun waters(): List<RedBallWater> {
        if (floods.isEmpty()) return scene.waters
        val out = ArrayList<RedBallWater>(scene.waters.size + floods.size)
        out += scene.waters
        for (flood in floods) out += flood.spec.water.copy(t = flood.top)
        return out
    }

    fun floodWaters(): List<RedBallWater> = floods.map { it.spec.water.copy(t = it.top) }

    fun waterTop(id: String): Float? = floods.firstOrNull { it.spec.id == id }?.top

    fun visibleDecoys(): List<RedBallAabb> =
        scene.script.decoys.filter { it.id !in hiddenDecoys }.map { it.box }

    fun marks(): List<Pair<RedBallAabb, RedBallMark>> =
        scene.script.triggers.mapNotNull { trigger ->
            if (trigger.mark == RedBallMark.NONE) null else trigger.box to trigger.mark
        }

    fun chasers(): List<RedBallChaserState> = runners

    fun step(dt: Float, ballX: Float, ballY: Float, radius: Float) {
        val safe = dt.coerceIn(0f, 0.05f)
        shakeClock += safe
        pullTriggers(ballX, ballY, radius)
        tick(safe)
        if (storyLeft > 0f) {
            storyLeft -= safe
            if (storyLeft <= 0f) story = null
        }
    }

    fun drainTeleport(): Pair<Float, Float>? = teleport.also { teleport = null }

    fun drainWake(): List<Int> {
        if (wakeNow.isEmpty()) return emptyList()
        val copy = wakeNow.toList()
        wakeNow.clear()
        return copy
    }

    /** Смерть босса и другие события без прямоугольника. Повторно не играет. */
    fun fire(id: String) {
        if (id.isBlank() || !spent.add(id)) return
        for (act in scene.script.acts) {
            if (act.triggerId != id) continue
            apply(act)
        }
    }

    private var shakeClock = 0f

    private fun pullTriggers(ballX: Float, ballY: Float, radius: Float) {
        for (trigger in scene.script.triggers) {
            if (trigger.id in spent) continue
            if (circleHits(ballX, ballY, radius, trigger.box)) fire(trigger.id)
        }
        for (decoy in scene.script.decoys) {
            if (decoy.id in hiddenDecoys || decoy.triggerId in spent) continue
            if (circleHits(ballX, ballY, radius, decoy.box)) fire(decoy.triggerId)
        }
    }

    private fun apply(act: RedBallAct) {
        when (act.kind) {
            RedBallActKind.COLLAPSE -> {
                val plat = plats.firstOrNull { it.spec.id == act.target && it.mode == 0 } ?: return
                plat.mode = 1
                plat.timer = plat.spec.delay.coerceAtLeast(0.05f)
            }
            RedBallActKind.RAISE_WATER -> {
                val flood = floods.firstOrNull { it.spec.id == act.target } ?: return
                flood.rising = true
            }
            RedBallActKind.DARKEN -> darkness = act.value.coerceIn(0f, 0.92f)
            RedBallActKind.CHASE -> {
                val run = runners.firstOrNull { it.id == act.target } ?: return
                if (!run.active) {
                    run.active = true
                    stick(run)
                }
            }
            RedBallActKind.BRIDGE -> {
                val index = scene.script.bridges.indexOfFirst { it.id == act.target }
                if (index >= 0) bridgeOn[index] = true
            }
            RedBallActKind.REVEAL_DOOR -> doorVisible = true
            RedBallActKind.HIDE_DECOY -> if (act.target.isNotBlank()) hiddenDecoys += act.target
            RedBallActKind.WAKE -> {
                val index = act.target.toIntOrNull() ?: return
                if (awake.add(index)) wakeNow += index
            }
            RedBallActKind.GRAVITY -> gravityScale = act.value.coerceIn(0.35f, 3f)
            RedBallActKind.STORY -> if (act.text.isNotBlank()) {
                story = act.text
                storyLeft = STORY_TIME
            }
            RedBallActKind.CLEAR_SPIKES -> if (act.target.isNotBlank()) clearedSpikes += act.target
            RedBallActKind.TELEPORT -> teleport = act.x to act.y
        }
    }

    private fun tick(dt: Float) {
        for (plat in plats) {
            when (plat.mode) {
                1 -> {
                    plat.timer -= dt
                    if (plat.timer <= 0f) plat.mode = 2
                }
                2 -> {
                    plat.drop += 460f * dt
                    if (plat.drop > 420f) plat.mode = 3
                }
            }
        }
        for (flood in floods) {
            if (!flood.rising) continue
            val goal = flood.spec.riseTo
            val speed = flood.spec.speed.coerceAtLeast(1f)
            if (flood.top > goal) flood.top = max(goal, flood.top - speed * dt)
            else if (flood.top < goal) flood.top = min(goal, flood.top + speed * dt)
        }
        for ((index, run) in runners.withIndex()) {
            if (!run.active) continue
            val spec = scene.script.chasers[index]
            val dir = if (spec.stopX >= spec.x) 1f else -1f
            val next = run.x + dir * spec.speed.coerceAtLeast(0f) * dt
            val arrived = if (dir > 0f) next >= spec.stopX else next <= spec.stopX
            run.x = if (arrived) spec.stopX else next
            stick(run)
        }
    }

    private fun stick(run: RedBallChaserState) {
        val feet = run.y + run.size * 0.5f
        var best = feet
        var bestDist = 220f
        fun take(top: Float) {
            val dist = abs(top - feet)
            if (dist < bestDist) {
                bestDist = dist
                best = top
            }
        }
        for (solid in solids()) {
            if (run.x < solid.l || run.x > solid.r) continue
            take(solid.t)
        }
        for (solid in scene.concrete) {
            if (run.x < solid.l || run.x > solid.r) continue
            take(solid.t)
        }
        for (slope in scene.slopes) {
            val lo = min(slope.x0, slope.x1)
            val hi = max(slope.x0, slope.x1)
            if (run.x < lo || run.x > hi || hi - lo < 1f) continue
            val t = (run.x - slope.x0) / (slope.x1 - slope.x0)
            take(slope.y0 + (slope.y1 - slope.y0) * t)
        }
        run.y = best - run.size * 0.5f
    }

    private fun circleHits(x: Float, y: Float, radius: Float, box: RedBallAabb): Boolean =
        x + radius > box.l && x - radius < box.r && y + radius > box.t && y - radius < box.b

    companion object {
        const val STORY_TIME = 6.4f
    }
}
