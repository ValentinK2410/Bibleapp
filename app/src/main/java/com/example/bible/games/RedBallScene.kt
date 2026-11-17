package com.example.bible.games

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Трение по умолчанию: так мяч тормозил на земле до отдельных коэффициентов. */
const val RED_BALL_FRICTION = 0.45f

/** Ось Y направлена вниз, как на экране. Прямоугольник столкновения. */
data class RedBallAabb(
    val l: Float,
    val t: Float,
    val r: Float,
    val b: Float,
    val friction: Float = RED_BALL_FRICTION,
)

/** Отрезок поверхности горки. Нормаль при расчёте всегда смотрит вверх. */
data class RedBallSlope(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val friction: Float = RED_BALL_FRICTION,
)

data class RedBallSpike(val x: Float, val top: Float, val w: Float, val h: Float)

data class RedBallCrateSpawn(
    val x: Float,
    val y: Float,
    val size: Float,
    val gravity: Boolean = true,
    val movable: Boolean = true,
    val friction: Float = RED_BALL_FRICTION,
    val mass: Float = 1f,
)

data class RedBallStarSpawn(val x: Float, val y: Float)

data class RedBallMagnetSpawn(val x: Float, val y: Float)

data class RedBallEnemySpawn(
    val x: Float,
    val y: Float,
    val size: Float,
    val left: Float,
    val right: Float,
    val gravity: Boolean = true,
    val friction: Float = RED_BALL_FRICTION,
    val mass: Float = 1f,
)

/**
 * Проход в другую готовую сцену. Пустой [linkId] — портал спит.
 * [spawnC] и [spawnR] вместе задают клетку появления; иначе мяч встаёт на старт цели.
 */
data class RedBallPortal(
    val box: RedBallAabb,
    val linkId: String = "",
    val spawnC: Int? = null,
    val spawnR: Int? = null,
) {
    fun arrivalIn(scene: RedBallScene, draft: RedBallDraft?): Pair<Float, Float> {
        val column = spawnC
        val row = spawnR
        if (draft == null || column == null || row == null) return scene.startX to scene.startY
        val minX = scene.minX + RedBallScene.BALL_R
        val maxX = (scene.worldW - RedBallScene.BALL_R).coerceAtLeast(minX)
        val x = ((column + 0.5f) * draft.cell).coerceIn(minX, maxX)
        val floor = (scene.voidY - RedBallScene.BALL_R - 1f).coerceAtLeast(scene.minY)
        val y = (draft.originY + (row + 1f) * draft.cell - RedBallScene.BALL_R).coerceIn(scene.minY, floor)
        return x to y
    }
}

/**
 * Готовая сцена для [RedBallSim]: и встроенная горка, и уровень из редактора
 * приходят сюда одним и тем же набором фигур.
 *
 * [minX]/[minY] — левый верх мира, [worldW]/[worldH] — правый низ.
 * У встроенной горки левый верх нулевой, поэтому старые числа на месте.
 * Свой уровень тянется за нарисованным, без боковых стен.
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
    val minX: Float = 0f,
    val minY: Float = 0f,
    val concrete: List<RedBallAabb> = emptyList(),
    val blocks: List<RedBallCrateSpawn> = emptyList(),
    val ladders: List<RedBallAabb> = emptyList(),
    val stars: List<RedBallStarSpawn> = emptyList(),
    val magnets: List<RedBallMagnetSpawn> = emptyList(),
    val enemies: List<RedBallEnemySpawn> = emptyList(),
    val portals: List<RedBallPortal> = emptyList(),
) {
    /** Порталы с неизвестной целью засыпают: мяч проходит сквозь них. */
    fun withKnownPortals(knownIds: Set<String>): RedBallScene {
        if (portals.none { it.linkId.isNotBlank() && it.linkId !in knownIds }) return this
        return copy(
            portals = portals.map { portal ->
                if (portal.linkId.isNotBlank() && portal.linkId !in knownIds) portal.copy(linkId = "") else portal
            },
        )
    }

    companion object {
        const val BUILTIN_ID = "builtin"
        const val BALL_R = 28f

        /** Та самая горка, с которой игра начиналась: числа не сдвинуты. */
        fun builtin(): RedBallScene {
            val floor = 760f
            val ledge = 430f
            val crate = 96f
            return RedBallScene(
                id = BUILTIN_ID,
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
    CONCRETE(2, 2),
    SLOPE(4, 2),
    LADDER(1, 4),
    CRATE(2, 2),
    SPRING(2, 1),
    SPIKE(1, 2),
    STAR(1, 1),
    MAGNET(1, 1),
    ENEMY(2, 2),
    START(1, 1),
    DOOR(2, 4),
    PORTAL(2, 3),
}

data class RedBallPiece(
    val id: Long,
    val kind: RedBallPieceKind,
    val c: Int,
    val r: Int,
    val w: Int,
    val h: Int,
    val flip: Boolean = false,
    val gravity: Boolean = true,
    val movable: Boolean = true,
    val friction: Float = RED_BALL_FRICTION,
    val mass: Float = 1f,
    val linkId: String = "",
    val spawnC: Int? = null,
    val spawnR: Int? = null,
)

/** Какие поля физики имеют смысл у предмета. */
internal enum class RedBallTune {
    NONE,
    FRICTION,
    FULL,
}

internal fun RedBallPieceKind.tune(): RedBallTune = when (this) {
    RedBallPieceKind.CRATE, RedBallPieceKind.CONCRETE, RedBallPieceKind.ENEMY -> RedBallTune.FULL
    RedBallPieceKind.GROUND, RedBallPieceKind.SLOPE -> RedBallTune.FRICTION
    else -> RedBallTune.NONE
}

internal data class RedBallPlacedProps(
    val gravity: Boolean,
    val movable: Boolean,
    val friction: Float,
    val mass: Float,
)

/** Значения, с которыми предмет появляется из палитры. Старые сохранения их не получают. */
internal fun RedBallPieceKind.placedProps(): RedBallPlacedProps = when (this) {
    RedBallPieceKind.CONCRETE -> RedBallPlacedProps(false, false, 0.8f, 3.4f)
    RedBallPieceKind.CRATE -> RedBallPlacedProps(true, true, RED_BALL_FRICTION, 1f)
    RedBallPieceKind.ENEMY -> RedBallPlacedProps(true, false, 0.6f, 1.5f)
    RedBallPieceKind.GROUND, RedBallPieceKind.SLOPE -> RedBallPlacedProps(false, false, RED_BALL_FRICTION, 1f)
    else -> RedBallPlacedProps(false, false, RED_BALL_FRICTION, 1f)
}

/**
 * Уровень в клетках. Редактор правит его, [toScene] собирает физическую сцену.
 * Клетка 40: ящик 80, горка 160×80, дверь 80×160.
 * Клетки не заперты в cols×rows: поле растёт туда, куда ставят предметы.
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

    /** Предупреждение редактора: портал без цели или с удалённой сценой не срабатывает. */
    fun portalWarning(knownIds: Set<String>): String? {
        val portals = pieces.filter { it.kind == RedBallPieceKind.PORTAL }
        if (portals.isEmpty()) return null
        if (portals.any { it.linkId.isNotBlank() && it.linkId !in knownIds }) {
            return "Портал ведёт в удалённую сцену — пока он спит"
        }
        if (portals.any { it.linkId.isBlank() }) {
            return "Выбери сцену у портала, иначе он спит"
        }
        return null
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
        val (cc, rr) = kind.snapAnchor(c, r)
        val slopeFlip = flip && kind == RedBallPieceKind.SLOPE
        val props = kind.placedProps()
        fun make(id: Long) = RedBallPiece(
            id, kind, cc, rr, kind.w, kind.h, slopeFlip,
            props.gravity, props.movable, props.friction, props.mass,
        )
        if (kind == RedBallPieceKind.SLOPE) {
            if (pieces.any { it.kind == kind && it.c == cc && it.r == rr && it.flip == slopeFlip }) return this
            val next = pieces.filter { !(it.kind == kind && it.c == cc && it.r == rr) }
            return copy(pieces = next + make(nextId), nextId = nextId + 1)
        }
        if (pieces.any { it.kind == kind && it.c == cc && it.r == rr && it.flip == slopeFlip }) return this
        var next = pieces
        if (kind == RedBallPieceKind.START || kind == RedBallPieceKind.DOOR) {
            next = next.filter { it.kind != kind }
        }
        return copy(pieces = next + make(nextId), nextId = nextId + 1)
    }

    fun withMoved(id: Long, c: Int, r: Int): RedBallDraft {
        val piece = pieces.firstOrNull { it.id == id } ?: return this
        val (cc, rr) = piece.kind.snapAnchor(c, r)
        if (piece.c == cc && piece.r == rr) return this
        return copy(pieces = pieces.map { if (it.id == id) it.copy(c = cc, r = rr) else it })
    }

    fun withPiece(id: Long, transform: (RedBallPiece) -> RedBallPiece): RedBallDraft {
        val piece = pieces.firstOrNull { it.id == id } ?: return this
        val updated = transform(piece)
        if (updated == piece) return this
        return copy(pieces = pieces.map { if (it.id == id) updated else it })
    }

    fun withErasedAt(x: Float, y: Float): RedBallDraft {
        val hit = hitPiece(x, y) ?: return this
        return copy(pieces = pieces.filter { it.id != hit.id })
    }

    fun toScene(): RedBallScene {
        val grounds = mutableListOf<RedBallAabb>()
        val concretes = mutableListOf<RedBallAabb>()
        val slopes = mutableListOf<RedBallSlope>()
        val spikes = mutableListOf<RedBallSpike>()
        val springs = mutableListOf<RedBallAabb>()
        val ladders = mutableListOf<RedBallAabb>()
        val crates = mutableListOf<RedBallCrateSpawn>()
        val blocks = mutableListOf<RedBallCrateSpawn>()
        val stars = mutableListOf<RedBallStarSpawn>()
        val magnets = mutableListOf<RedBallMagnetSpawn>()
        val enemies = mutableListOf<RedBallEnemySpawn>()
        val portals = mutableListOf<RedBallPortal>()
        var startX = cell * 2f
        var startY = originY + cell
        var door: RedBallAabb? = null
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (piece in pieces) {
            val box = aabb(piece)
            minX = min(minX, box.l)
            minY = min(minY, box.t)
            maxX = max(maxX, box.r)
            maxY = max(maxY, box.b)
            when (piece.kind) {
                RedBallPieceKind.GROUND -> grounds += box
                RedBallPieceKind.CONCRETE -> {
                    if (piece.gravity || piece.movable) blocks += box.asBody(piece)
                    else concretes += box
                }
                RedBallPieceKind.SLOPE -> {
                    slopes += if (!piece.flip) {
                        RedBallSlope(box.l, box.t, box.r, box.b, piece.friction)
                    } else {
                        RedBallSlope(box.l, box.b, box.r, box.t, piece.friction)
                    }
                }
                RedBallPieceKind.LADDER -> ladders += box
                RedBallPieceKind.CRATE -> crates += box.asBody(piece)
                RedBallPieceKind.SPRING -> springs += box
                RedBallPieceKind.SPIKE -> spikes += RedBallSpike(
                    x = (box.l + box.r) / 2f,
                    top = box.t,
                    w = box.r - box.l,
                    h = box.b - box.t,
                )
                RedBallPieceKind.STAR -> stars += RedBallStarSpawn((box.l + box.r) / 2f, (box.t + box.b) / 2f)
                RedBallPieceKind.MAGNET -> magnets += RedBallMagnetSpawn((box.l + box.r) / 2f, (box.t + box.b) / 2f)
                RedBallPieceKind.ENEMY -> {
                    val cx = (box.l + box.r) / 2f
                    val cy = (box.t + box.b) / 2f
                    val size = min(piece.w, piece.h) * cell
                    val reach = cell * 3.5f
                    enemies += RedBallEnemySpawn(
                        x = cx,
                        y = cy,
                        size = size,
                        left = cx - reach,
                        right = cx + reach,
                        gravity = piece.gravity,
                        friction = piece.friction,
                        mass = piece.mass,
                    )
                }
                RedBallPieceKind.START -> {
                    startX = (box.l + box.r) / 2f
                    startY = box.b - RedBallScene.BALL_R
                }
                RedBallPieceKind.DOOR -> door = box
                RedBallPieceKind.PORTAL -> portals += RedBallPortal(
                    box = box,
                    linkId = piece.linkId,
                    spawnC = piece.spawnC,
                    spawnR = piece.spawnR,
                )
            }
        }
        if (pieces.isEmpty()) {
            minX = 0f
            minY = originY
            maxX = cols * cell
            maxY = originY + rows * cell
        }
        val padX = cell * 2.5f
        val padTop = cell * 4f
        val voidY = maxY + cell * 3f
        val fallbackDoor = RedBallAabb(
            maxX - cell * 4f,
            maxY - cell * 6f,
            maxX - cell * 2f,
            maxY - cell * 2f,
            RED_BALL_FRICTION,
        )
        return RedBallScene(
            id = id,
            name = name.ifBlank { "Без имени" },
            builtin = false,
            worldW = maxX + padX,
            worldH = voidY + cell * 4f,
            voidY = voidY,
            startX = startX,
            startY = startY,
            solids = mergeAabbs(grounds),
            slopes = slopes,
            spikes = spikes,
            springs = springs,
            door = door ?: fallbackDoor,
            crates = crates,
            minX = minX - padX,
            minY = minY - padTop,
            concrete = mergeAabbs(concretes),
            blocks = blocks,
            ladders = ladders,
            stars = stars,
            magnets = magnets,
            enemies = enemies,
            portals = portals,
        )
    }

    val worldW: Float get() = cols * cell

    fun toJson(): String {
        val root = JSONObject()
        root.put("version", 2)
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
                put("gravity", piece.gravity)
                put("movable", piece.movable)
                put("friction", piece.friction.toDouble())
                put("mass", piece.mass.toDouble())
                if (piece.kind == RedBallPieceKind.PORTAL) {
                    put("linkId", piece.linkId)
                    val column = piece.spawnC
                    val row = piece.spawnR
                    if (column != null && row != null) {
                        put("spawnC", column)
                        put("spawnR", row)
                    }
                }
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
        friction = piece.friction,
    )

    private fun RedBallAabb.asBody(piece: RedBallPiece): RedBallCrateSpawn {
        val size = min(piece.w, piece.h) * cell
        return RedBallCrateSpawn(
            x = (l + r) / 2f,
            y = (t + b) / 2f,
            size = size,
            gravity = piece.gravity,
            movable = piece.movable,
            friction = piece.friction,
            mass = piece.mass,
        )
    }

    companion object {
        const val MAX_PIECES = 900
        const val CELL_LIMIT = 480

        private val HIT_ORDER = listOf(
            RedBallPieceKind.ENEMY,
            RedBallPieceKind.CRATE,
            RedBallPieceKind.STAR,
            RedBallPieceKind.MAGNET,
            RedBallPieceKind.PORTAL,
            RedBallPieceKind.DOOR,
            RedBallPieceKind.SPRING,
            RedBallPieceKind.SPIKE,
            RedBallPieceKind.LADDER,
            RedBallPieceKind.START,
            RedBallPieceKind.SLOPE,
            RedBallPieceKind.CONCRETE,
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
            val version = root.optInt("version", 1)
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
                val rawC = item.optInt("c")
                val rawR = item.optInt("r")
                val (c, r) = if (version < 2) {
                    if (w > cols || h > rows) continue
                    rawC.coerceIn(0, cols - w) to rawR.coerceIn(0, rows - h)
                } else {
                    if (rawC !in -CELL_LIMIT..CELL_LIMIT || rawR !in -CELL_LIMIT..CELL_LIMIT) continue
                    rawC to rawR
                }
                val id = item.optLong("id", pieces.size + 1L)
                if (id > maxId) maxId = id
                val flip = item.optBoolean("flip") && kind == RedBallPieceKind.SLOPE
                val gravity = if (item.has("gravity")) item.optBoolean("gravity") else true
                val movable = if (item.has("movable")) item.optBoolean("movable") else true
                val friction = if (item.has("friction")) {
                    item.optDouble("friction").toFloat().coerceIn(0f, 1f)
                } else {
                    RED_BALL_FRICTION
                }
                val mass = if (item.has("mass")) {
                    item.optDouble("mass").toFloat().coerceIn(0.2f, 8f)
                } else {
                    1f
                }
                val linkId = if (kind == RedBallPieceKind.PORTAL) linkIdOf(item.optString("linkId")) else ""
                val spawn = if (kind == RedBallPieceKind.PORTAL && item.has("spawnC") && item.has("spawnR")) {
                    val column = item.optInt("spawnC")
                    val row = item.optInt("spawnR")
                    if (column in -CELL_LIMIT..CELL_LIMIT && row in -CELL_LIMIT..CELL_LIMIT) column to row else null
                } else {
                    null
                }
                if (kind == RedBallPieceKind.START || kind == RedBallPieceKind.DOOR) {
                    pieces.removeAll { it.kind == kind }
                }
                pieces += RedBallPiece(
                    id, kind, c, r, w, h, flip, gravity, movable, friction, mass,
                    linkId, spawn?.first, spawn?.second,
                )
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
            "concrete" -> RedBallPieceKind.CONCRETE
            "slope" -> RedBallPieceKind.SLOPE
            "ladder" -> RedBallPieceKind.LADDER
            "crate" -> RedBallPieceKind.CRATE
            "spring" -> RedBallPieceKind.SPRING
            "spike" -> RedBallPieceKind.SPIKE
            "star" -> RedBallPieceKind.STAR
            "magnet" -> RedBallPieceKind.MAGNET
            "enemy" -> RedBallPieceKind.ENEMY
            "start" -> RedBallPieceKind.START
            "door" -> RedBallPieceKind.DOOR
            "portal" -> RedBallPieceKind.PORTAL
            else -> null
        }

        private fun linkIdOf(raw: String): String =
            raw.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
    }
}

internal fun RedBallPieceKind.snapAnchor(c: Int, r: Int): Pair<Int, Int> {
    val stepC = when (this) {
        RedBallPieceKind.START, RedBallPieceKind.DOOR, RedBallPieceKind.PORTAL,
        RedBallPieceKind.SPIKE, RedBallPieceKind.LADDER, RedBallPieceKind.STAR,
        RedBallPieceKind.MAGNET,
        -> 1
        else -> w
    }
    val stepR = when (this) {
        RedBallPieceKind.START, RedBallPieceKind.DOOR, RedBallPieceKind.PORTAL,
        RedBallPieceKind.SPRING, RedBallPieceKind.LADDER, RedBallPieceKind.STAR,
        RedBallPieceKind.MAGNET,
        -> 1
        else -> h
    }
    val limit = RedBallDraft.CELL_LIMIT
    val cc = (floorDiv(c, stepC) * stepC).coerceIn(-limit, limit - w)
    val rr = (floorDiv(r, stepR) * stepR).coerceIn(-limit, limit - h)
    return cc to rr
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
    if (abs(a.friction - b.friction) > 0.02f) return null
    val eps = 0.6f
    val sameTop = abs(a.t - b.t) < eps
    val sameBot = abs(a.b - b.b) < eps
    val sameLeft = abs(a.l - b.l) < eps
    val sameRight = abs(a.r - b.r) < eps
    val overlapX = a.l <= b.r + eps && b.l <= a.r + eps
    val overlapY = a.t <= b.b + eps && b.t <= a.b + eps
    if (sameTop && sameBot && overlapX) {
        return RedBallAabb(min(a.l, b.l), a.t, max(a.r, b.r), a.b, a.friction)
    }
    if (sameLeft && sameRight && overlapY) {
        return RedBallAabb(a.l, min(a.t, b.t), a.r, max(a.b, b.b), a.friction)
    }
    return null
}
