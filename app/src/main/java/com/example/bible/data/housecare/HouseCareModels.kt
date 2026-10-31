package com.example.bible.data.housecare

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Названия дней недели: индекс 0 = понедельник. */
object WeekDays {
    val full = listOf(
        "Понедельник", "Вторник", "Среда", "Четверг",
        "Пятница", "Суббота", "Воскресенье",
    )
    val short = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    /** Воскресенье — день отдыха: уборки на него не планируют. */
    val displayed = (1..6).toList()

    fun fullLabel(day: Int): String = full.getOrElse(day - 1) { day.toString() }

    fun shortLabel(day: Int): String = short.getOrElse(day - 1) { day.toString() }
}

/**
 * Сессия уборки одного раздела: фото «до», фото «после», дата и время.
 * Уборка считается зафиксированной, когда есть фото «после».
 */
data class CleaningReport(
    val id: String = UUID.randomUUID().toString(),
    val dateMs: Long = System.currentTimeMillis(),
    val beforePhoto: String? = null,
    val afterPhoto: String? = null,
    val beforeAtMs: Long? = null,
    val afterAtMs: Long? = null,
    val durationMsOverride: Long? = null,
) {
    val isFinished: Boolean get() = !afterPhoto.isNullOrBlank()

    /** Время между «до» и «после»; ручная правка имеет приоритет. */
    val durationMs: Long
        get() = durationMsOverride ?: run {
            val start = beforeAtMs ?: return@run 0L
            val end = afterAtMs ?: return@run 0L
            if (end > start) end - start else 0L
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("dateMs", dateMs)
        put("beforePhoto", beforePhoto ?: JSONObject.NULL)
        put("afterPhoto", afterPhoto ?: JSONObject.NULL)
        put("beforeAtMs", beforeAtMs ?: JSONObject.NULL)
        put("afterAtMs", afterAtMs ?: JSONObject.NULL)
        put("durationMsOverride", durationMsOverride ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(j: JSONObject): CleaningReport = CleaningReport(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            dateMs = j.optLong("dateMs", System.currentTimeMillis()),
            beforePhoto = j.optStringOrNull("beforePhoto"),
            afterPhoto = j.optStringOrNull("afterPhoto"),
            beforeAtMs = j.optLongOrNull("beforeAtMs"),
            afterAtMs = j.optLongOrNull("afterAtMs"),
            durationMsOverride = j.optLongOrNull("durationMsOverride"),
        )
    }
}

/** Этаж дома (1 этаж, 2 этаж, подвал...). */
data class HouseFloor(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val placeId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("placeId", placeId)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(j: JSONObject): HouseFloor = HouseFloor(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = j.optString("name", ""),
            placeId = j.optString("placeId", ""),
            createdAt = j.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

/** Зона уборки, закреплённая за этажом и днём недели (детская, кухня...). */
data class CleaningZone(
    val id: String = UUID.randomUUID().toString(),
    val floorId: String,
    val dayOfWeek: Int,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("floorId", floorId)
        put("dayOfWeek", dayOfWeek)
        put("name", name)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(j: JSONObject): CleaningZone = CleaningZone(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            floorId = j.optString("floorId"),
            dayOfWeek = j.optInt("dayOfWeek", 1).coerceIn(1, 7),
            name = j.optString("name", ""),
            createdAt = j.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

/** Раздел зоны — конкретная работа: потолок, подоконник, батарея. */
data class CleaningTask(
    val id: String = UUID.randomUUID().toString(),
    val zoneId: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val order: Int = 0,
    val active: CleaningReport? = null,
    val history: List<CleaningReport> = emptyList(),
) {
    val isCleaned: Boolean get() = active?.isFinished == true

    /** Все завершённые сессии, включая текущую — источник для статистики. */
    fun finishedReports(): List<CleaningReport> =
        (listOfNotNull(active) + history).filter { it.isFinished }

    fun photos(): List<String> = (listOfNotNull(active) + history)
        .flatMap { listOfNotNull(it.beforePhoto, it.afterPhoto) }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("zoneId", zoneId)
        put("name", name)
        put("createdAt", createdAt)
        put("order", order)
        put("active", active?.toJson() ?: JSONObject.NULL)
        put("history", JSONArray().apply { history.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(j: JSONObject): CleaningTask = CleaningTask(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            zoneId = j.optString("zoneId"),
            name = j.optString("name", ""),
            createdAt = j.optLong("createdAt", System.currentTimeMillis()),
            order = j.optInt("order", 0),
            active = j.optJSONObject("active")?.let(CleaningReport::fromJson),
            history = j.optJSONArray("history").let { arr ->
                if (arr == null) emptyList() else (0 until arr.length()).map { CleaningReport.fromJson(arr.getJSONObject(it)) }
            },
        )
    }
}

/** Объект уборки из настроек: дом, квартира, гараж, дача, сарай — с адресом и числом этажей. */
data class HousePlace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String = "",
    val floorsCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("address", address)
        put("floorsCount", floorsCount)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(j: JSONObject): HousePlace = HousePlace(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = j.optString("name", ""),
            address = j.optString("address", ""),
            floorsCount = j.optInt("floorsCount", 0).coerceAtLeast(0),
            createdAt = j.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

/** Вид плитки дня недели: цвет, стиль, рамка, свечение, иконка или фото. */
data class WeekdayTileStyle(
    val day: Int,
    val colorArgb: Long? = null,
    val style: String = STYLE_FILL,
    val borderColorArgb: Long? = null,
    val borderWidthDp: Float = 0f,
    val glow: Boolean = false,
    val icon: String = "",
    val photo: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("day", day)
        put("colorArgb", colorArgb ?: JSONObject.NULL)
        put("style", style)
        put("borderColorArgb", borderColorArgb ?: JSONObject.NULL)
        put("borderWidthDp", borderWidthDp.toDouble())
        put("glow", glow)
        put("icon", icon)
        put("photo", photo ?: JSONObject.NULL)
    }

    companion object {
        const val STYLE_FILL = "fill"
        const val STYLE_OUTLINE = "outline"
        const val STYLE_SOFT = "soft"

        fun fromJson(j: JSONObject): WeekdayTileStyle = WeekdayTileStyle(
            day = j.optInt("day", 1).coerceIn(1, 7),
            colorArgb = j.optLongOrNull("colorArgb"),
            style = j.optString("style", STYLE_FILL).ifBlank { STYLE_FILL },
            borderColorArgb = j.optLongOrNull("borderColorArgb"),
            borderWidthDp = j.optFloatOrNull("borderWidthDp") ?: 0f,
            glow = j.optBoolean("glow", false),
            icon = j.optString("icon", ""),
            photo = j.optStringOrNull("photo"),
        )
    }
}

/** Бытовая привычка: «помолиться», «почистить зубы», «помыть посуду». Отмечается галочкой на каждый день. */
data class CleaningHabit(
    val id: String = UUID.randomUUID().toString(),
    val placeId: String,
    val kind: String = KIND_MORNING,
    val name: String,
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Даты в формате yyyy-MM-dd, когда привычка выполнена. */
    val doneDates: List<String> = emptyList(),
) {
    fun isDoneOn(date: String): Boolean = date in doneDates

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("placeId", placeId)
        put("kind", kind)
        put("name", name)
        put("order", order)
        put("createdAt", createdAt)
        put("doneDates", JSONArray().apply { doneDates.forEach { put(it) } })
    }

    companion object {
        const val KIND_MORNING = "morning"
        const val KIND_EVENING = "evening"

        fun fromJson(j: JSONObject): CleaningHabit = CleaningHabit(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            placeId = j.optString("placeId"),
            kind = j.optString("kind", KIND_MORNING).ifBlank { KIND_MORNING },
            name = j.optString("name", ""),
            order = j.optInt("order", 0),
            createdAt = j.optLong("createdAt", System.currentTimeMillis()),
            doneDates = j.optJSONArray("doneDates").let { arr ->
                if (arr == null) emptyList() else (0 until arr.length()).map { arr.getString(it) }
            },
        )
    }
}

/** Всё состояние раздела «Попечительная о доме». */
data class HouseCareData(
    val places: List<HousePlace> = emptyList(),
    val floors: List<HouseFloor> = emptyList(),
    val zones: List<CleaningZone> = emptyList(),
    val tasks: List<CleaningTask> = emptyList(),
    val tileStyles: List<WeekdayTileStyle> = emptyList(),
    val habits: List<CleaningHabit> = emptyList(),
) {
    fun tileStyleOf(day: Int): WeekdayTileStyle =
        tileStyles.firstOrNull { it.day == day } ?: WeekdayTileStyle(day = day)

    fun placeById(id: String): HousePlace? = places.firstOrNull { it.id == id }

    /** Этажи объекта; у этажей без объекта — «старые» данные до деления на недвижимость. */
    fun floorsOf(placeId: String): List<HouseFloor> = floors.filter { it.placeId == placeId }

    fun zonesOfPlace(placeId: String, dayOfWeek: Int): List<CleaningZone> {
        val floorIds = floorsOf(placeId).map { it.id }.toSet()
        return zones.filter { it.dayOfWeek == dayOfWeek && it.floorId in floorIds }
    }

    fun zonesOf(floorId: String, dayOfWeek: Int): List<CleaningZone> =
        zones.filter { it.floorId == floorId && it.dayOfWeek == dayOfWeek }

    fun tasksOf(zoneId: String): List<CleaningTask> =
        tasks.filter { it.zoneId == zoneId }.sortedWith(compareBy({ it.order }, { it.createdAt }))

    fun floorById(id: String): HouseFloor? = floors.firstOrNull { it.id == id }

    fun zoneById(id: String): CleaningZone? = zones.firstOrNull { it.id == id }

    fun taskById(id: String): CleaningTask? = tasks.firstOrNull { it.id == id }

    /** Раздел убран, когда каждый его раздел имеет фото «после». */
    fun isZoneCleaned(zoneId: String): Boolean {
        val zoneTasks = tasksOf(zoneId)
        return zoneTasks.isNotEmpty() && zoneTasks.all { it.isCleaned }
    }

    fun finishedReports(): List<CleaningReport> = tasks.flatMap { it.finishedReports() }

    fun habitsOf(placeId: String, kind: String): List<CleaningHabit> =
        habits.filter { it.placeId == placeId && it.kind == kind }
            .sortedWith(compareBy({ it.order }, { it.createdAt }))

    fun toJson(): JSONObject = JSONObject().apply {
        put("places", JSONArray().apply { places.forEach { put(it.toJson()) } })
        put("floors", JSONArray().apply { floors.forEach { put(it.toJson()) } })
        put("zones", JSONArray().apply { zones.forEach { put(it.toJson()) } })
        put("tasks", JSONArray().apply { tasks.forEach { put(it.toJson()) } })
        put("tileStyles", JSONArray().apply { tileStyles.forEach { put(it.toJson()) } })
        put("habits", JSONArray().apply { habits.forEach { put(it.toJson()) } })
    }

    companion object {
        val Empty = HouseCareData()

        fun parse(json: String?): HouseCareData {
            if (json.isNullOrBlank()) return Empty
            return try {
                val root = JSONObject(json)
                HouseCareData(
                    places = root.optJSONArray("places").let { arr ->
                        if (arr == null) emptyList() else (0 until arr.length()).map { HousePlace.fromJson(arr.getJSONObject(it)) }
                    },
                    floors = root.optJSONArray("floors").let { arr ->
                        if (arr == null) emptyList() else (0 until arr.length()).map { HouseFloor.fromJson(arr.getJSONObject(it)) }
                    },
                    zones = root.optJSONArray("zones").let { arr ->
                        if (arr == null) emptyList() else (0 until arr.length()).map { CleaningZone.fromJson(arr.getJSONObject(it)) }
                    },
                    tasks = root.optJSONArray("tasks").let { arr ->
                        if (arr == null) emptyList() else (0 until arr.length()).map { CleaningTask.fromJson(arr.getJSONObject(it)) }
                    },
                    tileStyles = root.optJSONArray("tileStyles").let { arr ->
                        if (arr == null) emptyList() else (0 until arr.length()).map { WeekdayTileStyle.fromJson(arr.getJSONObject(it)) }
                    },
                    habits = root.optJSONArray("habits").let { arr ->
                        if (arr == null) emptyList() else (0 until arr.length()).map { CleaningHabit.fromJson(arr.getJSONObject(it)) }
                    },
                )
            } catch (_: Exception) {
                Empty
            }
        }
    }
}

internal fun JSONObject.optStringOrNull(key: String): String? {
    if (isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

internal fun JSONObject.optLongOrNull(key: String): Long? {
    if (isNull(key)) return null
    val raw = opt(key) ?: return null
    return (raw as? Number)?.toLong() ?: raw.toString().toLongOrNull()
}

internal fun JSONObject.optFloatOrNull(key: String): Float? {
    if (isNull(key)) return null
    val raw = opt(key) ?: return null
    return (raw as? Number)?.toFloat() ?: raw.toString().toFloatOrNull()
}
