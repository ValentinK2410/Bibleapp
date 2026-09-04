package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class UserMediaPlaylistKind {
    VIDEO,
    AUDIO,
}

/**
 * Пользовательский плейлист: группа ссылок на [BibleUserVideo.id] или [BibleUserAudio.id].
 */
data class UserMediaPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: UserMediaPlaylistKind,
    val itemIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("kind", kind.toJsonKey())
        put("items", JSONArray().apply { itemIds.forEach { put(it) } })
        put("at", createdAt)
        put("upd", updatedAt)
    }

    companion object {
        fun fromJson(j: JSONObject): UserMediaPlaylist {
            val kind = parseKind(j.optString("kind", ""))
            val items = if (j.has("items")) {
                val arr = j.getJSONArray("items")
                (0 until arr.length()).map { arr.getString(it) }
            } else {
                emptyList()
            }
            return UserMediaPlaylist(
                id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = j.optString("name", "Плейлист"),
                kind = kind,
                itemIds = items,
                createdAt = j.optLong("at", 0L),
                updatedAt = j.optLong("upd", j.optLong("at", 0L)),
            )
        }

        fun parseList(json: String): List<UserMediaPlaylist> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    try {
                        fromJson(arr.getJSONObject(i))
                    } catch (_: Exception) {
                        null
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<UserMediaPlaylist>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        private fun parseKind(s: String): UserMediaPlaylistKind =
            when (s.lowercase()) {
                "audio" -> UserMediaPlaylistKind.AUDIO
                else -> UserMediaPlaylistKind.VIDEO
            }
    }
}

private fun UserMediaPlaylistKind.toJsonKey(): String =
    when (this) {
        UserMediaPlaylistKind.VIDEO -> "video"
        UserMediaPlaylistKind.AUDIO -> "audio"
    }
