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

data class RedBallSpike(
    val x: Float,
    val top: Float,
    val w: Float,
    val h: Float,
    /** Пустая группа всегда колется. Иначе шипы пропадают, когда сценарий очищает группу. */
    val group: String = "",
)

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

/**
 * Прямоугольник воды. [t] — поверхность, [b] — дно. Ось Y вниз.
 * В отличие от ямы под [RedBallScene.voidY], здесь можно плыть и нырять.
 */
data class RedBallWater(
    val l: Float,
    val t: Float,
    val r: Float,
    val b: Float,
)

/** Тележка. [x] и [y] — центр кузова, низ стоит на земле, если её так поставили. */
data class RedBallCartSpawn(
    val x: Float,
    val y: Float,
    val w: Float = 118f,
    val h: Float = 46f,
)

/**
 * Точка, где мяч появляется после ямы или потери сердца.
 * [x] и [y] — центр мяча, как [RedBallScene.startX].
 * Сердца при этом не восполняются.
 */
data class RedBallCheckpoint(
    val x: Float,
    val y: Float,
)

data class RedBallEnemySpawn(
    val x: Float,
    val y: Float,
    val size: Float,
    val left: Float,
    val right: Float,
    val gravity: Boolean = true,
    val friction: Float = RED_BALL_FRICTION,
    val mass: Float = 1f,
    /** Сколько прыжков сверху нужно, чтобы квадрат исчез. У короля больше одного. */
    val hits: Int = 1,
    /** Спит, пока сценарий не разбудит: не едет и не ранит. */
    val asleep: Boolean = false,
    /** Пока спит, рисуется ящиком, а не квадратом. */
    val disguised: Boolean = false,
    /** Имя события, которое вспыхивает, когда жизни квадрата кончились. */
    val defeatTrigger: String = "",
)

/** Вход уводит мяч в другую сцену. Выход только принимает: в него зайти нельзя. */
enum class RedBallPortalRole {
    ENTRY,
    EXIT,
}

/**
 * Главная сцена видна в общем списке уровней.
 * Вложенная живёт внутри ровно одной главной и в общий список не попадает.
 */
enum class RedBallSceneKind {
    MAIN,
    NESTED,
}

/**
 * Проход между сценами. Пустой [linkId] у входа — портал спит.
 * [exitId] — имя выхода в целевой сцене; пустое имя или пропавший выход ставят мяч на старт.
 * [spawnC] и [spawnR] — старый способ указать клетку. Новые порталы ими не пользуются.
 */
data class RedBallPortal(
    val box: RedBallAabb,
    val linkId: String = "",
    val spawnC: Int? = null,
    val spawnR: Int? = null,
    val portalName: String = "",
    val role: RedBallPortalRole = RedBallPortalRole.ENTRY,
    val exitId: String = "",
) {
    fun arrivalIn(scene: RedBallScene, draft: RedBallDraft?): Pair<Float, Float> {
        if (exitId.isNotBlank()) {
            val spot = draft?.portalExitSpot(exitId)
            if (spot != null) return clampArrival(scene, spot.first, spot.second)
            return scene.startX to scene.startY
        }
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

private fun clampArrival(scene: RedBallScene, rawX: Float, rawY: Float): Pair<Float, Float> {
    val minX = scene.minX + RedBallScene.BALL_R
    val maxX = (scene.worldW - RedBallScene.BALL_R).coerceAtLeast(minX)
    val floor = (scene.voidY - RedBallScene.BALL_R - 1f).coerceAtLeast(scene.minY)
    return rawX.coerceIn(minX, maxX) to rawY.coerceIn(scene.minY, floor)
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
    val waters: List<RedBallWater> = emptyList(),
    val carts: List<RedBallCartSpawn> = emptyList(),
    /** Середина длинного уровня. Пустой список — яма по-прежнему возвращает на старт. */
    val checkpoints: List<RedBallCheckpoint> = emptyList(),
    /** Сценарий кампании. У горки и уровней редактора его нет. */
    val script: RedBallScript = RedBallScript.NONE,
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
                    RedBallAabb(-1100f, 0f, -980f, 980f),
                    RedBallAabb(2620f, 0f, 2700f, 980f),
                    RedBallAabb(-12f, 555f, 8f, 980f),
                    RedBallAabb(-980f, 940f, -20f, 980f),
                    RedBallAabb(-720f, 760f, -260f, 980f),
                ),
                slopes = listOf(
                    RedBallSlope(470f, 640f, 820f, floor),
                    RedBallSlope(-24f, 690f, -260f, 760f),
                ),
                spikes = listOf(
                    RedBallSpike(1310f, 800f, 36f, 60f),
                    RedBallSpike(1350f, 800f, 36f, 60f),
                    RedBallSpike(1390f, 800f, 36f, 60f),
                ),
                springs = listOf(RedBallAabb(1660f, 724f, 1750f, floor)),
                door = RedBallAabb(2380f, 250f, 2480f, ledge),
                crates = listOf(RedBallCrateSpawn(1140f, floor - crate / 2f, crate)),
                minX = -1140f,
                stars = listOf(
                    RedBallStarSpawn(-860f, 870f),
                    RedBallStarSpawn(-800f, 900f),
                    RedBallStarSpawn(-900f, 840f),
                ),
                waters = listOf(RedBallWater(-980f, 780f, -24f, 940f)),
                carts = listOf(RedBallCartSpawn(-490f, 760f - 23f, 118f, 46f)),
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
    WATER(6, 4),
    CART(3, 2),
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
    val portalName: String = "",
    val role: RedBallPortalRole = RedBallPortalRole.ENTRY,
    val exitId: String = "",
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
    val sceneKind: RedBallSceneKind = RedBallSceneKind.MAIN,
    val parentId: String = "",
) {
    /** Корень семьи: сама главная сцена или родитель вложенной. */
    fun familyRootId(): String =
        if (sceneKind == RedBallSceneKind.NESTED && parentId.isNotBlank()) parentId else id

    fun portalNames(): Set<String> = pieces.mapNotNull { piece ->
        if (piece.kind == RedBallPieceKind.PORTAL && piece.portalName.isNotBlank()) piece.portalName else null
    }.toSet()

    fun exitPortalNames(): Set<String> = pieces.mapNotNull { piece ->
        if (
            piece.kind == RedBallPieceKind.PORTAL &&
            piece.role == RedBallPortalRole.EXIT &&
            piece.portalName.isNotBlank()
        ) {
            piece.portalName
        } else {
            null
        }
    }.toSet()

    /** Точка появления у именованного выхода: низ арки, мяч стоит на полу портала. */
    fun portalExitSpot(name: String): Pair<Float, Float>? {
        if (name.isBlank()) return null
        val piece = pieces.firstOrNull {
            it.kind == RedBallPieceKind.PORTAL &&
                it.role == RedBallPortalRole.EXIT &&
                it.portalName == name
        } ?: return null
        val box = aabb(piece)
        return (box.l + box.r) / 2f to (box.b - RedBallScene.BALL_R)
    }

    /**
     * Вложенная сцена остаётся вложенной, только если родитель есть и сам главная.
     * Старые сохранения и сироты без родителя становятся главными.
     */
    fun normalizeHierarchy(parent: RedBallDraft?): RedBallDraft {
        if (sceneKind != RedBallSceneKind.NESTED || parentId.isBlank()) {
            return if (sceneKind == RedBallSceneKind.MAIN && parentId.isEmpty()) this
            else copy(sceneKind = RedBallSceneKind.MAIN, parentId = "")
        }
        if (parent == null || parent.id == id) return copy(sceneKind = RedBallSceneKind.MAIN, parentId = "")
        val parentIsMain = parent.sceneKind != RedBallSceneKind.NESTED || parent.parentId.isBlank()
        if (!parentIsMain || parent.id != parentId) return copy(sceneKind = RedBallSceneKind.MAIN, parentId = "")
        return copy(sceneKind = RedBallSceneKind.NESTED, parentId = parent.id)
    }

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

    /**
     * Предупреждение редактора.
     * [exitNames] — имена выходов по id сцены. Нет ключа — каталог ещё не прочитан, пропавший выход не ругаем.
     * [familyPortalNames] — имена порталов в других сценах той же семьи.
     */
    fun portalWarning(
        knownIds: Set<String>,
        exitNames: Map<String, Set<String>> = emptyMap(),
        familyPortalNames: Set<String> = emptySet(),
    ): String? {
        val portals = pieces.filter { it.kind == RedBallPieceKind.PORTAL }
        if (portals.isEmpty()) return null
        val named = portals.filter { it.portalName.isNotBlank() }
        val duplicate = named.groupBy { it.portalName }.any { it.value.size > 1 }
        val clash = named.any { it.portalName in familyPortalNames }
        if (duplicate || clash) return "Имена порталов в одной семье должны быть разными"
        val entries = portals.filter { it.role == RedBallPortalRole.ENTRY }
        if (entries.any { it.linkId.isNotBlank() && it.linkId !in knownIds }) {
            return "Портал ведёт в удалённую сцену — пока он спит"
        }
        if (entries.any { it.linkId.isBlank() }) {
            return "Выбери сцену у портала, иначе он спит"
        }
        val broken = entries.firstOrNull { entry ->
            entry.exitId.isNotBlank() &&
                entry.linkId.isNotBlank() &&
                entry.linkId in exitNames &&
                entry.exitId !in exitNames.getValue(entry.linkId)
        }
        if (broken != null) return "Выход «${broken.exitId}» пропал — мяч появится на старте"
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

    fun withPlaced(
        kind: RedBallPieceKind,
        c: Int,
        r: Int,
        flip: Boolean,
        reservedPortalNames: Set<String> = emptySet(),
    ): RedBallDraft {
        if (pieces.size >= MAX_PIECES && kind != RedBallPieceKind.START && kind != RedBallPieceKind.DOOR) {
            return this
        }
        val (cc, rr) = kind.snapAnchor(c, r)
        val slopeFlip = flip && kind == RedBallPieceKind.SLOPE
        val props = kind.placedProps()
        fun make(id: Long): RedBallPiece {
            val base = RedBallPiece(
                id, kind, cc, rr, kind.w, kind.h, slopeFlip,
                props.gravity, props.movable, props.friction, props.mass,
            )
            if (kind != RedBallPieceKind.PORTAL) return base
            val used = portalNames() + reservedPortalNames
            return base.copy(portalName = nextPortalName(used), role = RedBallPortalRole.ENTRY)
        }
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
        val waters = mutableListOf<RedBallWater>()
        val carts = mutableListOf<RedBallCartSpawn>()
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
                    portalName = piece.portalName,
                    role = piece.role,
                    exitId = piece.exitId,
                )
                RedBallPieceKind.WATER -> waters += RedBallWater(box.l, box.t, box.r, box.b)
                RedBallPieceKind.CART -> {
                    val bodyH = min(box.b - box.t, 48f)
                    carts += RedBallCartSpawn(
                        x = (box.l + box.r) / 2f,
                        y = box.b - bodyH / 2f,
                        w = (box.r - box.l) * 0.94f,
                        h = bodyH,
                    )
                }
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
            waters = waters,
            carts = carts,
        )
    }

    val worldW: Float get() = cols * cell

    fun toJson(): String {
        val root = JSONObject()
        root.put("version", 3)
        root.put("id", id)
        root.put("name", name)
        root.put("cols", cols)
        root.put("rows", rows)
        root.put("cell", cell.toDouble())
        root.put("originY", originY.toDouble())
        root.put("nextId", nextId)
        root.put("updatedAt", updatedAt)
        root.put("sceneKind", sceneKind.name.lowercase())
        root.put("parentId", parentId)
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
                    put("portalName", piece.portalName)
                    put("role", piece.role.name.lowercase())
                    put("exitId", piece.exitId)
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
            RedBallPieceKind.CART,
            RedBallPieceKind.STAR,
            RedBallPieceKind.MAGNET,
            RedBallPieceKind.PORTAL,
            RedBallPieceKind.DOOR,
            RedBallPieceKind.SPRING,
            RedBallPieceKind.SPIKE,
            RedBallPieceKind.LADDER,
            RedBallPieceKind.START,
            RedBallPieceKind.SLOPE,
            RedBallPieceKind.WATER,
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

        /** Новая вложенная сцена внутри уже сохранённой главной. */
        fun freshNested(parentId: String, name: String = "Вложенная"): RedBallDraft =
            fresh(name).copy(sceneKind = RedBallSceneKind.NESTED, parentId = parentId)

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
                val portalName = if (kind == RedBallPieceKind.PORTAL) {
                    normalizePortalName(item.optString("portalName"))
                } else {
                    ""
                }
                val portalRole = if (kind == RedBallPieceKind.PORTAL) {
                    portalRoleOf(item.optString("role"))
                } else {
                    RedBallPortalRole.ENTRY
                }
                val exitId = if (kind == RedBallPieceKind.PORTAL) {
                    normalizePortalName(item.optString("exitId"))
                } else {
                    ""
                }
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
                    linkId, spawn?.first, spawn?.second, portalName, portalRole, exitId,
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
                sceneKind = sceneKindOf(root.optString("sceneKind")),
                parentId = linkIdOf(root.optString("parentId")),
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
            "water" -> RedBallPieceKind.WATER
            "cart" -> RedBallPieceKind.CART
            else -> null
        }

        private fun linkIdOf(raw: String): String =
            raw.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)

        private fun sceneKindOf(raw: String): RedBallSceneKind = when (raw.lowercase()) {
            "nested" -> RedBallSceneKind.NESTED
            else -> RedBallSceneKind.MAIN
        }

        private fun portalRoleOf(raw: String): RedBallPortalRole = when (raw.lowercase()) {
            "exit" -> RedBallPortalRole.EXIT
            else -> RedBallPortalRole.ENTRY
        }
    }
}

/** Имя портала: буквы, цифры, пробел, дефис. Пустая строка — имя не задано. */
fun normalizePortalName(raw: String): String {
    val cleaned = buildString {
        for (ch in raw.trim()) {
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == ' ') append(ch)
        }
    }
    return cleaned.replace(Regex(" +"), " ").trim().take(24).trim()
}

/** Первое свободное «Вход N» или «Выход N». */
fun nextPortalName(used: Collection<String>, role: RedBallPortalRole = RedBallPortalRole.ENTRY): String {
    val prefix = if (role == RedBallPortalRole.EXIT) "Выход" else "Вход"
    val taken = used.toSet()
    var n = 1
    while ("$prefix $n" in taken) n++
    return "$prefix $n"
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

/**
 * Кадр камеры на длинной карте. [x] и [y] — левый верх видимого мира.
 * Мяч держится в кадре, а кадр не уезжает за [RedBallScene.minX] и [RedBallScene.worldW].
 */
data class RedBallCameraFrame(
    val x: Float,
    val y: Float,
    val viewW: Float,
    val viewH: Float,
)

fun RedBallScene.cameraFrame(focusX: Float, focusY: Float, viewW: Float, viewH: Float): RedBallCameraFrame {
    val width = viewW.coerceAtLeast(1f)
    val height = viewH.coerceAtLeast(1f)
    val minCamX = minX
    val maxCamX = (worldW - width).let { if (it < minCamX) minCamX else it }
    val camX = (focusX - width * 0.38f).coerceIn(minCamX, maxCamX)
    val minCamY = minY - height * 0.45f
    val maxCamY = (worldH - height).let { if (it < minCamY) minCamY else it }
    val camY = (focusY - height * 0.58f).coerceIn(minCamY, maxCamY)
    return RedBallCameraFrame(camX, camY, width, height)
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
