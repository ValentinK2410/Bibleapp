package com.example.bible.games

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Проверка данных уровня без проигрыша всей физики.
 * Смотрит, что старт не замурован, и что по клеткам хода, прыжка,
 * лестницы, пружины, воды и уже сыгранных триггеров можно дойти до флага.
 */
internal object RedBallReach {
    private const val R = RedBallScene.BALL_R

    fun check(scene: RedBallScene): String? {
        scriptProblems(scene)?.let { return it }
        spawnProblems(scene)?.let { return it }
        val ids = triggerIds(scene)
        if (ids.size > 16) return "${scene.name}: слишком много триггеров"
        val reached = reachedPoints(scene, ids)
        if (reached == null) {
            val samples = buildSamples(scene, 0, ids)
            val doorish = samples.count { insideDoor(scene, it.x, it.y) }
            return "${scene.name}: флаг недостижим (точек ${samples.size}, в двери $doorish)"
        }
        starProblems(scene, reached)?.let { return it }
        checkpointProblems(scene, reached)?.let { return it }
        return null
    }

    private fun scriptProblems(scene: RedBallScene): String? {
        val script = scene.script
        if (script.triggers.map { it.id }.let { it.size != it.toSet().size }) {
            return "${scene.name}: одинаковые id триггеров"
        }
        val platformIds = script.platforms.map { it.id }
        if (platformIds.size != platformIds.toSet().size) return "${scene.name}: одинаковые площадки"
        val known = triggerIds(scene).toSet()
        for (act in script.acts) {
            if (act.triggerId !in known) return "${scene.name}: нет триггера «${act.triggerId}»"
            val missing = when (act.kind) {
                RedBallActKind.COLLAPSE -> script.platforms.none { it.id == act.target }
                RedBallActKind.RAISE_WATER -> script.floods.none { it.id == act.target }
                RedBallActKind.CHASE -> script.chasers.none { it.id == act.target }
                RedBallActKind.BRIDGE -> script.bridges.none { it.id == act.target }
                RedBallActKind.HIDE_DECOY -> script.decoys.none { it.id == act.target }
                RedBallActKind.WAKE -> act.target.toIntOrNull()?.let { it !in scene.enemies.indices } ?: true
                RedBallActKind.CLEAR_SPIKES -> scene.spikes.none { it.group == act.target }
                RedBallActKind.GRAVITY -> act.value !in 0.4f..2.6f
                RedBallActKind.DARKEN -> act.value !in 0f..0.9f
                RedBallActKind.STORY -> act.text.isBlank()
                RedBallActKind.TELEPORT -> false
                RedBallActKind.REVEAL_DOOR -> false
            }
            if (missing) return "${scene.name}: плохое действие ${act.kind} «${act.target}»"
        }
        for (flood in script.floods) {
            if (flood.speed <= 0f) return "${scene.name}: вода «${flood.id}» не движется"
        }
        return null
    }

    private fun spawnProblems(scene: RedBallScene): String? {
        val solids = scene.solidsWhen(emptySet())
        if (scene.startY >= scene.voidY - 4f) return "${scene.name}: старт уже в яме"
        if (deep(scene.startX, scene.startY, solids, R, rest = true)) {
            return "${scene.name}: старт внутри стены"
        }
        if (hurts(scene, scene.effect(emptySet()), scene.startX, scene.startY)) {
            return "${scene.name}: старт на шипах"
        }
        val door = scene.door
        if (door.r - door.l < 20f || door.b - door.t < R + 8f) return "${scene.name}: дверь слишком маленькая"
        for (star in scene.stars) {
            if (deep(star.x, star.y, solids, 10f, rest = false)) {
                return "${scene.name}: звезда внутри стены"
            }
        }
        return null
    }

    private fun starProblems(scene: RedBallScene, reached: List<Pt>): String? {
        val magnet = scene.magnets.any { magnet ->
            reached.any { dist(it.x, it.y, magnet.x, magnet.y) <= R + 20f }
        }
        for (star in scene.stars) {
            val near = reached.any { dist(it.x, it.y, star.x, star.y) <= 52f }
            val pulled = magnet && reached.any { dist(it.x, it.y, star.x, star.y) <= 220f }
            if (!near && !pulled) return "${scene.name}: звезда (${star.x.toInt()}, ${star.y.toInt()}) не по пути"
        }
        return null
    }

    private fun checkpointProblems(scene: RedBallScene, reached: List<Pt>): String? {
        val points = scene.checkpoints
        if (points.isEmpty()) return null
        if (points.size < 2) return "${scene.name}: мало чекпоинтов"
        val doorX = (scene.door.l + scene.door.r) / 2f
        val doorY = (scene.door.t + scene.door.b) / 2f
        for (point in points) {
            if (point.y >= scene.voidY - 8f) return "${scene.name}: чекпоинт в яме"
            if (dist(point.x, point.y, scene.startX, scene.startY) < 90f) {
                return "${scene.name}: чекпоинт на старте"
            }
            if (dist(point.x, point.y, doorX, doorY) < 90f) return "${scene.name}: чекпоинт на флаге"
            val near = reached.any { dist(it.x, it.y, point.x, point.y) <= 70f }
            if (!near) return "${scene.name}: чекпоинт (${point.x.toInt()}, ${point.y.toInt()}) не на пути"
        }
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val apart = dist(points[i].x, points[i].y, points[j].x, points[j].y)
                if (apart < 240f) return "${scene.name}: чекпоинты стоят кучно"
            }
        }
        val span = points.maxOf { it.x } - points.minOf { it.x }
        if (span < 500f) return "${scene.name}: чекпоинты не разошлись по уровню"
        return null
    }

    private data class Pt(val x: Float, val y: Float, val spring: Boolean, val water: Int)

    private fun reachedPoints(scene: RedBallScene, ids: List<String>): List<Pt>? {
        val cache = HashMap<Int, List<Pt>>()
        fun samples(mask: Int): List<Pt> = cache.getOrPut(mask) { buildSamples(scene, mask, ids) }
        val queue = ArrayDeque<Pt>()
        val masks = ArrayDeque<Int>()
        val seen = HashSet<Long>()
        val got = ArrayList<Pt>(256)
        var found = false
        fun push(x: Float, y: Float, mask: Int) {
            if (x.isNaN() || y.isNaN()) return
            val key = ((x / 10f).toInt().toLong() shl 32) xor
                ((y / 10f).toInt().toLong() shl 8) xor
                mask.toLong()
            if (!seen.add(key)) return
            queue.add(Pt(x, y, false, -1))
            masks.add(mask)
        }
        push(scene.startX, scene.startY, 0)
        var guard = 0
        while (queue.isNotEmpty()) {
            if (++guard > 120_000) return null
            val node = queue.removeFirst()
            val mask = masks.removeFirst()
            val fired = fired(ids, mask)
            val fx = scene.effect(fired)
            got += node
            if (fx.doorVisible && insideDoor(scene, node.x, node.y)) found = true
            for (zone in zones(scene)) {
                val bit = ids.indexOf(zone.first)
                if (bit < 0 || mask and (1 shl bit) != 0) continue
                if (!hits(node.x, node.y, zone.second)) continue
                val next = mask or (1 shl bit)
                val jump = scene.script.acts.firstOrNull {
                    it.triggerId == zone.first && it.kind == RedBallActKind.TELEPORT
                }
                if (jump != null) push(jump.x, jump.y, next) else push(node.x, node.y, next)
            }
            for ((index, enemy) in scene.enemies.withIndex()) {
                if (enemy.defeatTrigger.isBlank()) continue
                val bit = ids.indexOf(enemy.defeatTrigger)
                if (bit < 0 || mask and (1 shl bit) != 0) continue
                if (enemy.asleep && index !in fx.awake) continue
                if (dist(node.x, node.y, enemy.x, enemy.y) > 170f) continue
                push(node.x, node.y, mask or (1 shl bit))
            }
            val solids = scene.solidsWhen(fired)
            val gravity = fx.gravity.coerceIn(0.4f, 2.6f)
            for (candidate in samples(mask)) {
                if (!travel(node, candidate, solids, gravity)) continue
                val key = ((candidate.x / 10f).toInt().toLong() shl 32) xor
                    ((candidate.y / 10f).toInt().toLong() shl 8) xor
                    mask.toLong()
                if (!seen.add(key)) continue
                queue.add(candidate)
                masks.add(mask)
            }
        }
        return if (found) got else null
    }

    private fun zones(scene: RedBallScene): List<Pair<String, RedBallAabb>> {
        val out = ArrayList<Pair<String, RedBallAabb>>()
        for (trigger in scene.script.triggers) out += trigger.id to trigger.box
        for (decoy in scene.script.decoys) out += decoy.triggerId to decoy.box
        return out
    }

    private fun triggerIds(scene: RedBallScene): List<String> {
        val ids = LinkedHashSet<String>()
        scene.script.triggers.forEach { ids += it.id }
        scene.script.decoys.forEach { ids += it.triggerId }
        scene.enemies.forEach { if (it.defeatTrigger.isNotBlank()) ids += it.defeatTrigger }
        return ids.toList()
    }

    private fun fired(ids: List<String>, mask: Int): Set<String> {
        val set = HashSet<String>()
        for (i in ids.indices) if (mask and (1 shl i) != 0) set += ids[i]
        return set
    }

    private fun buildSamples(scene: RedBallScene, mask: Int, ids: List<String>): List<Pt> {
        val fired = fired(ids, mask)
        val fx = scene.effect(fired)
        val solids = scene.solidsWhen(fired)
        val pts = ArrayList<Pt>()
        fun ground(x: Float, y: Float) {
            if (hurts(scene, fx, x, y)) return
            if (deep(x, y, solids, R, rest = true)) return
            pts += Pt(x, y, springHere(scene, x, y), -1)
        }
        for (solid in solids) {
            val y = solid.t - R
            var x = solid.l + R
            val end = solid.r - R
            if (end < x) continue
            while (x <= end) {
                ground(x, y)
                x += 20f
            }
        }
        for (slope in scene.slopes) {
            val len = hypot(slope.x1 - slope.x0, slope.y1 - slope.y0).coerceAtLeast(1f)
            val steps = (len / 20f).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = slope.x0 + (slope.x1 - slope.x0) * t
                val y = slope.y0 + (slope.y1 - slope.y0) * t - R
                ground(x, y)
            }
        }
        for (ladder in scene.ladders) {
            val x = (ladder.l + ladder.r) / 2f
            var y = ladder.t + 12f
            while (y <= ladder.b - 8f) {
                // Лестница проходит сквозь толщу пола: точки остаются, даже если круг задевает плиту.
                pts += Pt(x, y, false, -1)
                y += 20f
            }
        }
        val waters = scene.watersWhen(fired)
        for ((index, water) in waters.withIndex()) {
            var y = water.t + R * 0.4f
            while (y < water.b - 10f) {
                var x = water.l + R
                while (x < water.r - R) {
                    if (!deep(x, y, solids, R * 0.7f, rest = false)) pts += Pt(x, y, false, index)
                    x += 36f
                }
                y += 36f
            }
        }
        if (fx.doorVisible) {
            ground((scene.door.l + scene.door.r) / 2f, scene.door.b - R)
        }
        return pts
    }

    private fun travel(a: Pt, b: Pt, solids: List<RedBallAabb>, gravity: Float): Boolean {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val adx = abs(dx)
        val ady = abs(dy)
        if (adx <= 64f && ady <= 40f) return true
        if (a.water >= 0 && a.water == b.water && adx <= 52f && ady <= 52f) return true
        if (a.water >= 0 && b.water < 0 && adx <= 76f && dy >= -140f / gravity && dy <= 40f &&
            clear(a, b, solids, 24f)
        ) {
            return true
        }
        if (b.water >= 0 && a.water < 0 && adx <= 100f && dy > -30f && dy <= 520f &&
            clear(a, b, solids, 18f)
        ) {
            return true
        }
        if (adx <= 175f && dy >= -130f / gravity && dy <= 520f &&
            clear(a, b, solids, if (dy < -12f) 90f / gravity else 22f)
        ) {
            return true
        }
        if (a.spring && adx <= 210f && dy >= -330f / gravity && dy <= 50f &&
            clear(a, b, solids, 210f / gravity)
        ) {
            return true
        }
        return false
    }

    private fun clear(a: Pt, b: Pt, solids: List<RedBallAabb>, lift: Float): Boolean {
        for (i in 1..6) {
            val t = i / 7f
            val x = a.x + (b.x - a.x) * t
            val y = a.y + (b.y - a.y) * t - lift * 4f * t * (1f - t)
            if (deep(x, y, solids, 16f, rest = false)) return false
        }
        return true
    }

    private fun springHere(scene: RedBallScene, x: Float, y: Float): Boolean {
        for (spring in scene.springs) {
            if (x < spring.l - 6f || x > spring.r + 6f) continue
            if (y + R > spring.t && y - R < spring.b + 10f) return true
        }
        return false
    }

    private fun hurts(scene: RedBallScene, fx: RedBallEffect, x: Float, y: Float): Boolean {
        val feet = y + R
        for (spike in scene.spikes) {
            if (spike.group.isNotEmpty() && spike.group in fx.clearSpikes) continue
            if (abs(x - spike.x) > spike.w * 0.5f + 10f) continue
            val base = spike.top + spike.h
            if (abs(feet - base) < 22f && feet > spike.top + 6f) return true
        }
        return false
    }

    private fun insideDoor(scene: RedBallScene, x: Float, y: Float): Boolean {
        val door = scene.door
        return x > door.l + 2f && x < door.r - 2f && y > door.t + 2f && y < door.b - 2f
    }

    private fun hits(x: Float, y: Float, box: RedBallAabb): Boolean =
        x + R > box.l && x - R < box.r && y + R > box.t && y - R < box.b

    private fun deep(x: Float, y: Float, solids: List<RedBallAabb>, radius: Float, rest: Boolean): Boolean {
        for (solid in solids) {
            val closestX = x.coerceIn(solid.l, solid.r)
            val closestY = y.coerceIn(solid.t, solid.b)
            val dx = x - closestX
            val dy = y - closestY
            val distance = hypot(dx, dy)
            val pen = radius - distance
            if (pen <= 1.2f) continue
            val standing = rest && y <= solid.t + 1.5f && closestY <= solid.t + 0.4f && pen <= 4f
            if (!standing) return true
        }
        return false
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return hypot(dx, dy)
    }
}
