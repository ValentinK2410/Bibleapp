package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class UserMediaPlaylistKind {
    VIDEO,
    AUDIO,
}

/** Цветовая тема карточки плейлиста, если своей обложки ещё нет. */
enum class PlaylistLook(val id: String, val titleRu: String, val startArgb: Long, val endArgb: Long) {
    INK("ink", "Чернила", 0xFF1B2430, 0xFF4A628A),
    PARCHMENT("parchment", "Пергамент", 0xFF5C4033, 0xFFC4A574),
    FOREST("forest", "Лес", 0xFF14352A, 0xFF3D7A5A),
    ROSE("rose", "Роза", 0xFF4A2030, 0xFFC45C78),
    OCEAN("ocean", "Океан", 0xFF12344A, 0xFF3A8BB5),
    GOLD("gold", "Золото", 0xFF3D2E10, 0xFFC9A227),
    ;

    companion object {
        fun fromId(raw: String?): PlaylistLook =
            entries.find { it.id == raw } ?: INK
    }
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
    /** Файл обложки в [MediaCatalogPaths.PLAYLIST_COVERS]; пусто — рисуем тему [lookId]. */
    val coverFileName: String = "",
    val lookId: String = PlaylistLook.INK.id,
    val subtitle: String = "",
) {
    val look: PlaylistLook get() = PlaylistLook.fromId(lookId)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("kind", kind.toJsonKey())
        put("items", JSONArray().apply { itemIds.forEach { put(it) } })
        put("at", createdAt)
        put("upd", updatedAt)
        if (coverFileName.isNotBlank()) put("cover", coverFileName)
        if (lookId.isNotBlank() && lookId != PlaylistLook.INK.id) put("look", lookId)
        if (subtitle.isNotBlank()) put("sub", subtitle)
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
                coverFileName = j.optString("cover").trim(),
                lookId = j.optString("look").ifBlank { PlaylistLook.INK.id },
                subtitle = j.optString("sub").trim(),
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
