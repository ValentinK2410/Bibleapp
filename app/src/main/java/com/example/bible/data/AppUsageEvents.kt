package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * Журнал событий для статистики: открытия, копирования, дослушанные главы.
 * Хранится локально; старые записи отбрасываются.
 */
object AppUsageEvents {
    const val MAX_EVENTS = 8_000

    const val TYPE_OPEN = "open"
    const val TYPE_COPY = "copy"
    const val TYPE_LISTEN = "listen"
    const val TYPE_READ = "read"
    const val TYPE_FILE = "file"
    const val TYPE_PLAY = "play"
    const val TYPE_DOWNLOAD = "download"
    const val TYPE_DELETE = "delete"

    private val namedTypes = setOf(TYPE_FILE, TYPE_PLAY, TYPE_DOWNLOAD, TYPE_DELETE)

    data class Event(
        val timestamp: Long,
        val type: String,
        val sectionId: String,
        val detail: String = "",
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("ts", timestamp)
            put("t", type)
            put("s", sectionId)
            if (detail.isNotBlank()) put("d", detail)
        }
    }

    data class DayCount(
        val epochDay: Long,
        val count: Int,
    )

    data class TypeCount(
        val type: String,
        val label: String,
        val count: Int,
    )

    /** Один файл, песня или запись: сколько раз и когда в последний раз. */
    data class NamedItem(
        val type: String,
        val kind: String,
        val name: String,
        val count: Int,
        val lastAt: Long,
    )

    fun namedDetail(kind: String, name: String): String {
        val safeKind = kind.replace('|', ' ').trim().ifBlank { "file" }
        val safeName = name.replace('|', ' ').replace('\n', ' ').trim().ifBlank { "Без названия" }
        return "$safeKind|$safeName"
    }

    fun parseNamed(detail: String): Pair<String, String>? {
        val parts = detail.split('|', limit = 2)
        if (parts.size < 2) return null
        val kind = parts[0].trim()
        val name = parts[1].trim()
        if (kind.isEmpty() || name.isEmpty() || kind == "played") return null
        return kind to name
    }

    fun parse(json: String): List<Event> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { index ->
                val j = arr.optJSONObject(index) ?: return@mapNotNull null
                val type = j.optString("t", "").ifBlank { return@mapNotNull null }
                val section = j.optString("s", "").ifBlank { return@mapNotNull null }
                Event(
                    timestamp = j.optLong("ts", 0L),
                    type = type,
                    sectionId = section,
                    detail = j.optString("d", ""),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun toJson(events: List<Event>): String {
        val arr = JSONArray()
        events.takeLast(MAX_EVENTS).forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    fun append(events: List<Event>, event: Event): List<Event> {
        val next = events.toMutableList()
        next += event
        while (next.size > MAX_EVENTS) next.removeAt(0)
        return next
    }

    fun sectionIdsForArea(area: String): Set<String> =
        AppSectionUsage.catalog.filter { it.area == area }.map { it.id }.toSet()

    fun eventsForArea(events: List<Event>, area: String): List<Event> {
        if (area == AppSectionUsage.BIBLE_AREA) {
            val bibleIds = sectionIdsForArea(area) + setOf("reading", "coverage")
            return events.filter { it.sectionId in bibleIds || it.type == TYPE_COPY || it.type == TYPE_LISTEN || it.type == TYPE_READ }
        }
        val ids = sectionIdsForArea(area)
        return events.filter { it.sectionId in ids }
    }

    fun countsByDay(
        events: List<Event>,
        days: Int = 14,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DayCount> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        val start = today - (days - 1)
        val buckets = LongArray(days)
        for (event in events) {
            if (event.timestamp <= 0L) continue
            val day = Instant.ofEpochMilli(event.timestamp).atZone(zone).toLocalDate().toEpochDay()
            if (day in start..today) {
                buckets[(day - start).toInt()]++
            }
        }
        return (0 until days).map { offset ->
            DayCount(epochDay = start + offset, count = buckets[offset].toInt())
        }
    }

    fun countsByType(events: List<Event>): List<TypeCount> {
        val map = linkedMapOf(
            TYPE_OPEN to "Открытия",
            TYPE_READ to "Чтение",
            TYPE_LISTEN to "Озвучка",
            TYPE_COPY to "Копии",
            TYPE_FILE to "Файлы",
            TYPE_PLAY to "Песни и музыка",
            TYPE_DOWNLOAD to "Скачано",
            TYPE_DELETE to "Удалено",
        )
        return map.map { (type, label) ->
            TypeCount(type, label, events.count { it.type == type })
        }.filter { it.count > 0 }
    }

    /** Сумма реально прослушанных миллисекунд из событий `played|…|мс`. */
    fun listenedMillis(events: List<Event>): Long =
        events.sumOf { event ->
            if (event.type != TYPE_LISTEN) return@sumOf 0L
            val parts = event.detail.split('|')
            if (parts.firstOrNull() != "played") return@sumOf 0L
            parts.getOrNull(4)?.toLongOrNull() ?: 0L
        }

    fun formatListened(ms: Long): String {
        val totalSec = (ms / 1000L).toInt().coerceAtLeast(0)
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    fun typeLabel(type: String): String = when (type) {
        TYPE_OPEN -> "Открытия"
        TYPE_READ -> "Чтение"
        TYPE_LISTEN -> "Озвучка"
        TYPE_COPY -> "Копии"
        TYPE_FILE -> "Файлы"
        TYPE_PLAY -> "Песни и музыка"
        TYPE_DOWNLOAD -> "Скачано"
        TYPE_DELETE -> "Удалено"
        else -> type
    }

    fun kindLabel(kind: String): String = when (kind) {
        "song" -> "Песня"
        "music" -> "Музыка"
        "image" -> "Картинка"
        "video" -> "Видео"
        "audio" -> "Аудио"
        else -> "Файл"
    }

    /** Сводка по имени: сколько раз открыли, включили, скачали или удалили. */
    fun namedItems(events: List<Event>): List<NamedItem> {
        data class Acc(var count: Int = 0, var lastAt: Long = 0L)
        val totals = linkedMapOf<String, Acc>()
        val meta = linkedMapOf<String, Triple<String, String, String>>()
        for (event in events) {
            if (event.type !in namedTypes) continue
            val parsed = parseNamed(event.detail) ?: continue
            val key = "${event.type}|${parsed.first}|${parsed.second}"
            val acc = totals.getOrPut(key) { Acc() }
            acc.count++
            if (event.timestamp >= acc.lastAt) acc.lastAt = event.timestamp
            meta[key] = Triple(event.type, parsed.first, parsed.second)
        }
        return totals.map { (key, acc) ->
            val (type, kind, name) = meta.getValue(key)
            NamedItem(type, kind, name, acc.count, acc.lastAt)
        }.sortedWith(compareByDescending<NamedItem> { it.count }.thenByDescending { it.lastAt })
    }

    fun recentNamed(events: List<Event>, limit: Int = 40): List<Event> =
        events.asReversed().filter { event ->
            event.type in namedTypes && parseNamed(event.detail) != null
        }.take(limit)
}
