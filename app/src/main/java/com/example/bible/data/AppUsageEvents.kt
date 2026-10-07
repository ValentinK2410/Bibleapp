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
        )
        return map.map { (type, label) ->
            TypeCount(type, label, events.count { it.type == type })
        }.filter { it.count > 0 }
    }

    fun typeLabel(type: String): String = when (type) {
        TYPE_OPEN -> "Открытия"
        TYPE_READ -> "Чтение"
        TYPE_LISTEN -> "Озвучка"
        TYPE_COPY -> "Копии"
        else -> type
    }
}
