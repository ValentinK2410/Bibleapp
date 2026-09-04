package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Метка времени для строки текста песни (индекс строки после [String.split] по `\n`).
 */
data class SongLyricCue(
    val timeMs: Long,
    val lineIndex: Int,
)

data class SongItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String = "",
    val lyrics: String = "",
    /** Локальные аудиофайлы (несколько дорожек — выбор при воспроизведении). */
    val audioPaths: List<String> = emptyList(),
    /** Подписи дорожек (параллельно [audioPaths]; при импорте/экспорте сохраняют названия). */
    val audioLabels: List<String> = emptyList(),
    /** Прямые URL дорожек с сайта (fonki / holychords), параллельно [audioPaths]. */
    val audioSourceUrls: List<String> = emptyList(),
    val videoPath: String? = null,
    val sourceUrl: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    /** Таймкоды строк: синхрон с первым файлом из [audioPaths]. */
    val lyricCues: List<SongLyricCue> = emptyList(),
) {
    /** Первая дорожка (совместимость со старым полем `audio`). */
    val audioPath: String? get() = audioPaths.firstOrNull()

    fun hasLyricSync(): Boolean = lyricCues.isNotEmpty() && audioPaths.isNotEmpty()

    /** Название дорожки для UI: [audioLabels] или имя файла без расширения. */
    fun displayAudioLabel(index: Int): String {
        audioLabels.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { return it }
        val path = audioPaths.getOrNull(index) ?: return ""
        val file = File(path)
        return file.nameWithoutExtension.ifBlank { file.name }
    }
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        put("lyrics", lyrics)
        when {
            audioPaths.isEmpty() -> {}
            audioPaths.size == 1 -> put("audio", audioPaths[0])
            else -> {
                put("audios", JSONArray().apply { audioPaths.forEach { put(it) } })
                put("audio", audioPaths[0])
            }
        }
        if (audioLabels.isNotEmpty()) {
            put("audioLabels", JSONArray().apply { audioLabels.forEach { put(it) } })
        }
        if (audioSourceUrls.isNotEmpty()) {
            put("audioSourceUrls", JSONArray().apply { audioSourceUrls.forEach { put(it) } })
        }
        if (videoPath != null) put("video", videoPath)
        if (sourceUrl != null) put("url", sourceUrl)
        if (tags.isNotEmpty()) {
            put("tags", JSONArray().apply { tags.forEach { put(it) } })
        }
        if (lyricCues.isNotEmpty()) {
            put(
                "lyricCues",
                JSONArray().apply {
                    lyricCues.forEach { c ->
                        put(
                            JSONObject().apply {
                                put("t", c.timeMs)
                                put("i", c.lineIndex)
                            },
                        )
                    }
                },
            )
        }
        put("ca", createdAt)
    }

    companion object {
        fun fromJson(j: JSONObject): SongItem = SongItem(
            id = j.getString("id"),
            title = j.optString("title", ""),
            artist = j.optString("artist", ""),
            lyrics = j.optString("lyrics", ""),
            audioPaths = run {
                val paths = linkedSetOf<String>()
                if (j.has("audios")) {
                    val arr = j.getJSONArray("audios")
                    for (i in 0 until arr.length()) {
                        arr.optString(i)?.takeIf { it.isNotBlank() }?.let { paths.add(it) }
                    }
                }
                if (j.has("audio")) {
                    j.optString("audio").takeIf { it.isNotBlank() }?.let { paths.add(it) }
                }
                paths.toList()
            },
            audioLabels = if (j.has("audioLabels")) {
                val arr = j.getJSONArray("audioLabels")
                (0 until arr.length()).map { arr.optString(it, "") }
            } else {
                emptyList()
            },
            audioSourceUrls = if (j.has("audioSourceUrls")) {
                val arr = j.getJSONArray("audioSourceUrls")
                (0 until arr.length()).map { arr.optString(it, "") }
            } else {
                emptyList()
            },
            videoPath = if (j.has("video")) j.getString("video") else null,
            sourceUrl = if (j.has("url")) j.getString("url") else null,
            tags = if (j.has("tags")) {
                val arr = j.getJSONArray("tags")
                (0 until arr.length()).map { arr.getString(it) }
            } else emptyList(),
            createdAt = j.optLong("ca", 0L),
            lyricCues = if (j.has("lyricCues")) {
                val arr = j.getJSONArray("lyricCues")
                (0 until arr.length()).map { idx ->
                    val o = arr.getJSONObject(idx)
                    SongLyricCue(
                        timeMs = o.getLong("t"),
                        lineIndex = o.getInt("i"),
                    )
                }
            } else {
                emptyList()
            },
        )

        fun parseList(json: String): List<SongItem> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<SongItem>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}
