package com.example.bible.games

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Ось Y направлена вниз, как на экране. Прямоугольник столкновения. */
data class RedBallAabb(val l: Float, val t: Float, val r: Float, val b: Float)

/** Отрезок поверхности горки. Нормаль при расчёте всегда смотрит вверх. */
data class RedBallSlope(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

data class RedBallSpike(val x: Float, val top: Float, val w: Float, val h: Float)

data class RedBallCrateSpawn(val x: Float, val y: Float, val size: Float)

/**
 * Готовая сцена для [RedBallSim]: и встроенная горка, и уровень из редактора
 * приходят сюда одним и тем же набором фигур.
 */
data class RedBallScene(
    val id: String,
    val name: String,
    val builtin: Boolean,
    val worldW: Float,
    val worldH: Float,
    val voidY: Float,
    val startX: Float,
    val startY: Float,
    val solids: List<RedBallAabb>,
    val slopes: List<RedBallSlope>,
    val spikes: List<RedBallSpike>,
    val springs: List<RedBallAabb>,
    val door: RedBallAabb,
    val crates: List<RedBallCrateSpawn>,
) {
    companion object {
        const val BALL_R = 28f

        /** Та самая горка, с которой игра начиналась: числа не сдвинуты. */
        fun builtin(): RedBallScene {
            val floor = 760f
            val ledge = 430f
            val crate = 96f
            return RedBallScene(
                id = "builtin",
                name = "Горка",
                builtin = true,
                worldW = 2680f,
                worldH = 1000f,
                voidY = 960f,
                startX = 170f,
                startY = 640f - BALL_R,
                solids = listOf(
                    RedBallAabb(0f, 640f, 470f, 980f),
                    RedBallAabb(470f, floor, 820f, 980f),
                    RedBallAabb(820f, floor, 1280f, 980f),
                    RedBallAabb(1280f, 860f, 1400f, 980f),
                    RedBallAabb(1400f, floor, 1860f, 980f),
                    RedBallAabb(1860f, ledge, 2620f, 980f),
                    RedBallAabb(-80f, 0f, 0f, 980f),
                    RedBallAabb(2620f, 0f, 2700f, 980f),
                ),
                slopes = listOf(RedBallSlope(470f, 640f, 820f, floor)),
                spikes = listOf(
                    RedBallSpike(1310f, 800f, 36f, 60f),
                    RedBallSpike(1350f, 800f, 36f, 60f),
                    RedBallSpike(1390f, 800f, 36f, 60f),
                ),
                springs = listOf(RedBallAabb(1660f, 724f, 1750f, floor)),
                door = RedBallAabb(2380f, 250f, 2480f, ledge),
                crates = listOf(RedBallCrateSpawn(1140f, floor - crate / 2f, crate)),
            )
        }
    }
}

enum class RedBallPieceKind(val w: Int, val h: Int) {
    GROUND(2, 2),
    SLOPE(4, 2),
    CRATE(2, 2),
    SPRING(2, 1),
    SPIKE(1, 2),
    START(1, 1),
    DOOR(2, 4),
}

data class RedBallPiece(
    val id: Long,
    val kind: RedBallPieceKind,
    val c: Int,
    val r: Int,
    val w: Int,
    val h: Int,
    val flip: Boolean = false,
)

/**
 * Уровень в клетках. Редактор правит его, [toScene] собирает физическую сцену.
 * Клетка 40: ящик 80, горка 160×80, дверь 80×160.
 */
data class RedBallDraft(
    val id: String,
    val name: String,
    val cols: Int = 40,
    val rows: Int = 14,
    val cell: Float = 40f,
    val originY: Float = 220f,
    val pieces: List<RedBallPiece> = emptyList(),
    val nextId: Long = 1L,
    val updatedAt: Long = 0L,
) {
    fun playBlockReason(): String? {
        val starts = pieces.count { it.kind == RedBallPieceKind.START }
        val doors = pieces.count { it.kind == RedBallPieceKind.DOOR }
        return when {
            starts == 1 && doors == 1 -> null
            starts != 1 && doors != 1 -> "Нужны один старт мяча и одна дверь"
            starts != 1 -> "Поставь один старт — откуда катится мяч"
            else -> "Поставь одну дверь — куда нужно докатиться"
        }
    }

    fun cellOf(x: Float, y: Float): Pair<Int, Int> {
        return floor(x / cell).toInt() to floor(y / cell).toInt()
    }

    fun hitPiece(x: Float, y: Float): RedBallPiece? {
        for (kind in HIT_ORDER) {
            val found = pieces.lastOrNull { it.kind == kind && it.hits(x, y, cell) }
            if (found != null) return found
        }
        return null
    }

    fun withPlaced(kind: RedBallPieceKind, c: Int, r: Int, flip: Boolean): RedBallDraft {
        if (pieces.size >= MAX_PIECES && kind != RedBallPieceKind.START && kind != RedBallPieceKind.DOOR) {
            return this
        }
        val (cc, rr) = kind.anchor(c, r, cols, rows)
        val slopeFlip = flip && kind == RedBallPieceKind.SLOPE
        if (kind == RedBallPieceKind.SLOPE) {
            if (pieces.any { it.kind == kind && it.c == cc && it.r == rr && it.flip == slopeFlip }) return this
            val next = pieces.filter { !(it.kind == kind && it.c == cc && it.r == rr) }
            return copy(
                pieces = next + RedBallPiece(nextId, kind, cc, rr, kind.w, kind.h, slopeFlip),
                nextId = nextId + 1,
            )
        }
        if (pieces.any { it.kind == kind && it.c == cc && it.r == rr && it.flip == slopeFlip }) return this
        var next = pieces
        if (kind == RedBallPieceKind.START || kind == RedBallPieceKind.DOOR) {
            next = next.filter { it.kind != kind }
        }
        return copy(
            pieces = next + RedBallPiece(nextId, kind, cc, rr, kind.w, kind.h, slopeFlip),
            nextId = nextId + 1,
        )
    }

    fun withMoved(id: Long, c: Int, r: Int): RedBallDraft {
        val piece = pieces.firstOrNull { it.id == id } ?: return this
        val (cc, rr) = piece.kind.anchor(c, r, cols, rows)
        if (piece.c == cc && piece.r == rr) return this
        return copy(pieces = pieces.map { if (it.id == id) it.copy(c = cc, r = rr) else it })
    }

    fun withErasedAt(x: Float, y: Float): RedBallDraft {
        val hit = hitPiece(x, y) ?: return this
        return copy(pieces = pieces.filter { it.id != hit.id })
    }

    fun toScene(): RedBallScene {
        val grounds = mutableListOf<RedBallAabb>()
        val slopes = mutableListOf<RedBallSlope>()
        val spikes = mutableListOf<RedBallSpike>()
        val springs = mutableListOf<RedBallAabb>()
        val crates = mutableListOf<RedBallCrateSpawn>()
        var startX = cell * 2f
        var startY = originY + cell
        var door = RedBallAabb(worldW - cell * 4f, originY, worldW - cell * 2f, originY + cell * 4f)
        for (piece in pieces) {
            val box = aabb(piece)
            when (piece.kind) {
                RedBallPieceKind.GROUND -> grounds += box
                RedBallPieceKind.SLOPE -> {
                    slopes += if (!piece.flip) {
                        RedBallSlope(box.l, box.t, box.r, box.b)
                    } else {
                        RedBallSlope(box.l, box.b, box.r, box.t)
                    }
                }
                RedBallPieceKind.CRATE -> {
                    val size = min(piece.w, piece.h) * cell
                    crates += RedBallCrateSpawn(
                        x = (box.l + box.r) / 2f,
                        y = (box.t + box.b) / 2f,
                        size = size,
                    )
                }
                RedBallPieceKind.SPRING -> springs += box
                RedBallPieceKind.SPIKE -> spikes += RedBallSpike(
                    x = (box.l + box.r) / 2f,
                    top = box.t,
                    w = box.r - box.l,
                    h = box.b - box.t,
                )
                RedBallPieceKind.START -> {
                    startX = (box.l + box.r) / 2f
                    startY = box.b - RedBallScene.BALL_R
                }
                RedBallPieceKind.DOOR -> door = box
            }
        }
        val gridBottom = originY + rows * cell
        val worldH = gridBottom + 180f
        val solids = mergeAabbs(grounds).toMutableList()
        solids += RedBallAabb(-80f, 0f, 0f, worldH)
        solids += RedBallAabb(worldW, 0f, worldW + 80f, worldH)
        return RedBallScene(
            id = id,
            name = name.ifBlank { "Без имени" },
            builtin = false,
            worldW = worldW,
            worldH = worldH,
            voidY = gridBottom + 120f,
            startX = startX,
            startY = startY,
            solids = solids,
            slopes = slopes,
            spikes = spikes,
            springs = springs,
            door = door,
            crates = crates,
        )
    }

    val worldW: Float get() = cols * cell

    fun toJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("id", id)
        root.put("name", name)
        root.put("cols", cols)
        root.put("rows", rows)
        root.put("cell", cell.toDouble())
        root.put("originY", originY.toDouble())
        root.put("nextId", nextId)
        root.put("updatedAt", updatedAt)
        val array = JSONArray()
        for (piece in pieces) {
            array.put(JSONObject().apply {
                put("id", piece.id)
                put("kind", piece.kind.name.lowercase())
                put("c", piece.c)
                put("r", piece.r)
                put("w", piece.w)
                put("h", piece.h)
                put("flip", piece.flip)
            })
        }
        root.put("pieces", array)
        return root.toString()
    }

    private fun aabb(piece: RedBallPiece) = RedBallAabb(
        l = piece.c * cell,
        t = originY + piece.r * cell,
        r = (piece.c + piece.w) * cell,
        b = originY + (piece.r + piece.h) * cell,
    )

    companion object {
        const val MAX_PIECES = 480

        private val HIT_ORDER = listOf(
            RedBallPieceKind.CRATE,
            RedBallPieceKind.DOOR,
            RedBallPieceKind.SPRING,
            RedBallPieceKind.SPIKE,
            RedBallPieceKind.START,
            RedBallPieceKind.SLOPE,
            RedBallPieceKind.GROUND,
        )

        fun fresh(name: String = "Мой уровень"): RedBallDraft {
            val pieces = mutableListOf<RedBallPiece>()
            var id = 1L
            fun add(kind: RedBallPieceKind, c: Int, r: Int, flip: Boolean = false) {
                pieces += RedBallPiece(id++, kind, c, r, kind.w, kind.h, flip)
            }
            for (c in 0 until 40 step 2) add(RedBallPieceKind.GROUND, c, 12)
            for (c in 0 until 12 step 2) add(RedBallPieceKind.GROUND, c, 10)
            add(RedBallPieceKind.SLOPE, 12, 10, flip = false)
            add(RedBallPieceKind.CRATE, 2, 8)
            add(RedBallPieceKind.SPRING, 4, 9)
            add(RedBallPieceKind.START, 8, 9)
            add(RedBallPieceKind.DOOR, 34, 8)
            return RedBallDraft(
                id = UUID.randomUUID().toString(),
                name = name,
                cols = 40,
                rows = 14,
                cell = 40f,
                originY = 220f,
                pieces = pieces,
                nextId = id,
            )
        }

        fun fromJson(text: String): RedBallDraft? = try {
            parse(JSONObject(text))
        } catch (_: Exception) {
            null
        }

        private fun parse(root: JSONObject): RedBallDraft {
            val cols = root.optInt("cols", 40).coerceIn(8, 80)
            val rows = root.optInt("rows", 14).coerceIn(8, 40)
            val cell = root.optDouble("cell", 40.0).toFloat().coerceIn(16f, 80f)
            val originY = root.optDouble("originY", 220.0).toFloat()
            val array = root.optJSONArray("pieces") ?: JSONArray()
            val pieces = mutableListOf<RedBallPiece>()
            var maxId = 0L
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val kind = kindOf(item.optString("kind")) ?: continue
                val w = kind.w
                val h = kind.h
                if (w > cols || h > rows) continue
                val c = item.optInt("c").coerceIn(0, cols - w)
                val r = item.optInt("r").coerceIn(0, rows - h)
                val id = item.optLong("id", pieces.size + 1L)
                if (id > maxId) maxId = id
                val flip = item.optBoolean("flip") && kind == RedBallPieceKind.SLOPE
                if (kind == RedBallPieceKind.START || kind == RedBallPieceKind.DOOR) {
                    pieces.removeAll { it.kind == kind }
                }
                pieces += RedBallPiece(id, kind, c, r, w, h, flip)
            }
            val nextId = root.optLong("nextId", maxId + 1L).coerceAtLeast(maxId + 1L)
            return RedBallDraft(
                id = root.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = root.optString("name").ifBlank { "Без имени" }.take(32),
                cols = cols,
                rows = rows,
                cell = cell,
                originY = originY,
                pieces = pieces,
                nextId = nextId,
                updatedAt = root.optLong("updatedAt"),
            )
        }

        private fun kindOf(raw: String): RedBallPieceKind? = when (raw.lowercase()) {
            "ground" -> RedBallPieceKind.GROUND
            "slope" -> RedBallPieceKind.SLOPE
            "crate" -> RedBallPieceKind.CRATE
            "spring" -> RedBallPieceKind.SPRING
            "spike" -> RedBallPieceKind.SPIKE
            "start" -> RedBallPieceKind.START
            "door" -> RedBallPieceKind.DOOR
            else -> null
        }
    }
}

private fun RedBallPiece.hits(x: Float, y: Float, cell: Float): Boolean {
    if (kind == RedBallPieceKind.START) {
        val cx = (c + w / 2f) * cell
        val cy = (r + h) * cell - RedBallScene.BALL_R
        val dx = x - cx
        val dy = y - cy
        val radius = RedBallScene.BALL_R
        if (dx * dx + dy * dy <= radius * radius) return true
    }
    val l = c * cell
    val t = r * cell
    val rr = (c + w) * cell
    val bb = (r + h) * cell
    return x >= l && x < rr && y >= t && y < bb
}

private fun RedBallPieceKind.anchor(c: Int, r: Int, cols: Int, rows: Int): Pair<Int, Int> {
    val stepC = when (this) {
        RedBallPieceKind.START, RedBallPieceKind.DOOR, RedBallPieceKind.SPIKE -> 1
        else -> w
    }
    val stepR = when (this) {
        RedBallPieceKind.START, RedBallPieceKind.DOOR, RedBallPieceKind.SPRING -> 1
        else -> h
    }
    val maxC = (cols - w).coerceAtLeast(0)
    val maxR = (rows - h).coerceAtLeast(0)
    val cc = (floorDiv(c, stepC) * stepC).coerceIn(0, maxC)
    val rr = (floorDiv(r, stepR) * stepR).coerceIn(0, maxR)
    return cc to rr
}

private fun floorDiv(a: Int, b: Int): Int {
    val q = a / b
    return if (a < 0 && a % b != 0) q - 1 else q
}

/** Склеивает соседние земляные блоки, чтобы мяч не спотыкался о швы сетки. */
internal fun mergeAabbs(input: List<RedBallAabb>): List<RedBallAabb> {
    val items = input.toMutableList()
    var changed = true
    while (changed) {
        changed = false
        var i = 0
        while (i < items.size) {
            var j = i + 1
            var merged = false
            while (j < items.size) {
                val union = tryMerge(items[i], items[j])
                if (union != null) {
                    items.removeAt(j)
                    items[i] = union
                    changed = true
                    merged = true
                    break
                }
                j++
            }
            if (!merged) i++
        }
    }
    return items
}

private fun tryMerge(a: RedBallAabb, b: RedBallAabb): RedBallAabb? {
    val eps = 0.6f
    val sameTop = abs(a.t - b.t) < eps
    val sameBot = abs(a.b - b.b) < eps
    val sameLeft = abs(a.l - b.l) < eps
    val sameRight = abs(a.r - b.r) < eps
    val overlapX = a.l <= b.r + eps && b.l <= a.r + eps
    val overlapY = a.t <= b.b + eps && b.t <= a.b + eps
    if (sameTop && sameBot && overlapX) {
        return RedBallAabb(min(a.l, b.l), a.t, max(a.r, b.r), a.b)
    }
    if (sameLeft && sameRight && overlapY) {
        return RedBallAabb(a.l, min(a.t, b.t), a.r, max(a.b, b.b))
    }
    return null
}
