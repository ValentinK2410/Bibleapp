package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject

/** Последние посещения аятов Корана (уникальная пара сура + аят, новые сверху). */
data class QuranReadingHistoryEntry(
    val surahNumber: Int,
    val surahNameRu: String,
    val ayahNumber: Int,
    val timestamp: Long,
    val dwellSeconds: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("s", surahNumber)
        put("n", surahNameRu)
        put("a", ayahNumber)
        put("ts", timestamp)
        if (dwellSeconds > 0) put("ds", dwellSeconds)
    }

    companion object {
        private const val MAX_ENTRIES = 500

        fun fromJson(j: JSONObject): QuranReadingHistoryEntry = QuranReadingHistoryEntry(
            surahNumber = j.getInt("s"),
            surahNameRu = j.optString("n", ""),
            ayahNumber = j.getInt("a"),
            timestamp = j.getLong("ts"),
            dwellSeconds = j.optInt("ds", 0),
        )

        fun parseList(json: String): List<QuranReadingHistoryEntry> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<QuranReadingHistoryEntry>): String {
            val arr = JSONArray()
            list.take(MAX_ENTRIES).forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

/** Хронология просмотра аятов Корана (каждое переключение / задержка). */
data class QuranReadingTraceEntry(
    val timestamp: Long,
    val surahNumber: Int,
    val surahNameRu: String,
    val ayahNumber: Int,
    val dwellSeconds: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ts", timestamp)
        put("s", surahNumber)
        put("n", surahNameRu)
        put("a", ayahNumber)
        if (dwellSeconds > 0) put("ds", dwellSeconds)
    }

    companion object {
        private const val MAX_TRACE = 20_000

        fun fromJson(j: JSONObject): QuranReadingTraceEntry = QuranReadingTraceEntry(
            timestamp = j.getLong("ts"),
            surahNumber = j.getInt("s"),
            surahNameRu = j.optString("n", ""),
            ayahNumber = j.getInt("a"),
            dwellSeconds = j.optInt("ds", 0),
        )

        fun parseList(json: String): List<QuranReadingTraceEntry> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<QuranReadingTraceEntry>): String {
            val arr = JSONArray()
            list.takeLast(MAX_TRACE).forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}
