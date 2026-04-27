package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Запись в пользовательской базе аудио для Библии.
 * Файл лежит в [MediaCatalogPaths.AUDIOS]/[fileName].
 */
data class BibleUserAudio(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val tags: List<String> = emptyList(),
    val fileName: String,
    val source: String = "gallery",
    val sourceUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("file", fileName)
        put("src", source)
        if (sourceUrl != null) put("url", sourceUrl)
        put("at", addedAt)
        if (tags.isNotEmpty()) {
            put("tags", JSONArray().apply { tags.forEach { put(it) } })
        }
    }

    companion object {
        fun fromJson(j: JSONObject): BibleUserAudio = BibleUserAudio(
            id = j.getString("id"),
            title = j.optString("title", ""),
            tags = if (j.has("tags")) {
                val arr = j.getJSONArray("tags")
                (0 until arr.length()).map { arr.getString(it).trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            },
            fileName = j.getString("file"),
            source = j.optString("src", "gallery"),
            sourceUrl = if (j.has("url")) j.getString("url") else null,
            addedAt = j.optLong("at", 0L),
        )

        fun parseList(json: String): List<BibleUserAudio> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<BibleUserAudio>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}
