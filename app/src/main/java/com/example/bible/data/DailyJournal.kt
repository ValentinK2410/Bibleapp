package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class JournalMood(val emoji: String, val labelRu: String) {
    GREAT("😊", "Отлично"),
    CALM("😌", "Спокойно"),
    TIRED("😴", "Усталость"),
    HARD("😔", "Трудно"),
    GRATEFUL("🙏", "Благодарность"),
    ;

    fun toJson(): String = name

    companion object {
        fun fromJson(s: String?): JournalMood? =
            entries.find { it.name.equals(s?.trim(), ignoreCase = true) }
    }
}

data class JournalCheckItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val done: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("t", text)
        if (done) put("d", true)
    }

    companion object {
        fun fromJson(j: JSONObject): JournalCheckItem = JournalCheckItem(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            text = j.optString("t", ""),
            done = j.optBoolean("d", false),
        )
    }
}

/** Запись ежедневника (день + текст, задачи, настроение, стих). */
data class DailyJournalEntry(
    val id: String = UUID.randomUUID().toString(),
    /** yyyy-MM-dd */
    val dayKey: String = todayKey(),
    val title: String = "",
    val body: String = "",
    val mood: JournalMood? = null,
    val tags: List<String> = emptyList(),
    val checkItems: List<JournalCheckItem> = emptyList(),
    val verseBookId: String? = null,
    val verseChapter: Int? = null,
    val verseVerse: Int? = null,
    val verseLabel: String? = null,
    val pinned: Boolean = false,
    /** Час 0–23, если задача привязана ко времени. */
    val hour: Int? = null,
    val minute: Int? = null,
    val allDay: Boolean = false,
    val endDayKey: String? = null,
    val endHour: Int? = null,
    val endMinute: Int? = null,
    /** none, daily, weekly, monthly, yearly */
    val repeat: String = "none",
    val reminderOn: Boolean = true,
    val reminderMinutes: Int = 10,
    val location: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun verseRefOrNull(): VerseRef? {
        val b = verseBookId ?: return null
        val c = verseChapter ?: return null
        val v = verseVerse ?: return null
        return try {
            VerseRef(TranslationId.SYNODAL, b, c, v)
        } catch (_: Exception) {
            null
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("day", dayKey)
        put("title", title)
        put("body", body)
        mood?.let { put("mood", it.toJson()) }
        if (tags.isNotEmpty()) {
            put("tags", JSONArray().apply { tags.forEach { put(it) } })
        }
        if (checkItems.isNotEmpty()) {
            put("checks", JSONArray().apply { checkItems.forEach { put(it.toJson()) } })
        }
        verseBookId?.let { put("vb", it) }
        verseChapter?.let { put("vc", it) }
        verseVerse?.let { put("vv", it) }
        verseLabel?.let { put("vl", it) }
        if (pinned) put("pin", true)
        hour?.let { put("hh", it) }
        minute?.let { put("mm", it) }
        if (allDay) put("ad", true)
        endDayKey?.let { put("ed", it) }
        endHour?.let { put("eh", it) }
        endMinute?.let { put("em", it) }
        if (repeat != "none") put("rp", repeat)
        put("ro", reminderOn)
        put("rm", reminderMinutes)
        if (location.isNotBlank()) put("loc", location)
        put("ca", createdAt)
        put("ua", updatedAt)
    }

    companion object {
        private val dayFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        fun todayKey(): String = LocalDate.now().format(dayFmt)

        fun fromJson(j: JSONObject): DailyJournalEntry {
            val tags = if (j.has("tags")) {
                val arr = j.getJSONArray("tags")
                (0 until arr.length()).map { arr.getString(it).trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
            val checks = if (j.has("checks")) {
                val arr = j.getJSONArray("checks")
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { JournalCheckItem.fromJson(it) }
                }
            } else {
                emptyList()
            }
            return DailyJournalEntry(
                id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
                dayKey = j.optString("day", todayKey()),
                title = j.optString("title", ""),
                body = j.optString("body", ""),
                mood = JournalMood.fromJson(j.optString("mood", null)),
                tags = tags,
                checkItems = checks,
                verseBookId = j.optString("vb").takeIf { it.isNotBlank() },
                verseChapter = if (j.has("vc")) j.optInt("vc") else null,
                verseVerse = if (j.has("vv")) j.optInt("vv") else null,
                verseLabel = j.optString("vl").takeIf { it.isNotBlank() },
                pinned = j.optBoolean("pin", false),
                hour = if (j.has("hh")) j.optInt("hh").coerceIn(0, 23) else null,
                minute = if (j.has("mm")) j.optInt("mm").coerceIn(0, 59) else null,
                allDay = j.optBoolean("ad", !j.has("hh")),
                endDayKey = j.optString("ed").takeIf { it.isNotBlank() },
                endHour = if (j.has("eh")) j.optInt("eh").coerceIn(0, 23) else null,
                endMinute = if (j.has("em")) j.optInt("em").coerceIn(0, 59) else null,
                repeat = j.optString("rp", "none").ifBlank { "none" },
                reminderOn = j.optBoolean("ro", true),
                reminderMinutes = j.optInt("rm", 10),
                location = j.optString("loc", ""),
                createdAt = j.optLong("ca", 0L),
                updatedAt = j.optLong("ua", 0L),
            )
        }

        fun parseList(json: String): List<DailyJournalEntry> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<DailyJournalEntry>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}
