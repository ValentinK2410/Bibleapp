package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SongListSort(val id: String, val titleRu: String) {
    MANUAL("manual", "Свой порядок"),
    TITLE_AZ("az", "По названию А–Я"),
    TITLE_ZA("za", "По названию Я–А"),
    NEWEST("new", "Сначала новые"),
    ;

    companion object {
        fun fromId(raw: String?): SongListSort =
            entries.find { it.id == raw } ?: MANUAL
    }
}

/**
 * Список песен в разделе «Песнопение»: постоянный или временный (например, на служение).
 * [songRefs] — [SongItem.id] или `pv:номер` гимна из «Песнь возрождения».
 */
data class UserSongPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songRefs: List<String> = emptyList(),
    val temporary: Boolean = false,
    val lookId: String = PlaylistLook.INK.id,
    val subtitle: String = "",
    val sort: SongListSort = SongListSort.MANUAL,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val look: PlaylistLook get() = PlaylistLook.fromId(lookId)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("refs", JSONArray().apply { songRefs.forEach { put(it) } })
        if (temporary) put("tmp", true)
        if (lookId.isNotBlank() && lookId != PlaylistLook.INK.id) put("look", lookId)
        if (subtitle.isNotBlank()) put("sub", subtitle)
        if (sort != SongListSort.MANUAL) put("sort", sort.id)
        put("at", createdAt)
        put("upd", updatedAt)
    }

    companion object {
        const val PV_PREFIX = "pv:"

        fun pvRef(number: Int): String = "$PV_PREFIX$number"

        fun isPvRef(ref: String): Boolean = ref.startsWith(PV_PREFIX)

        fun pvNumber(ref: String): Int? =
            if (isPvRef(ref)) ref.removePrefix(PV_PREFIX).toIntOrNull() else null

        fun fromJson(j: JSONObject): UserSongPlaylist {
            val refs = if (j.has("refs")) {
                val arr = j.getJSONArray("refs")
                (0 until arr.length()).map { arr.getString(it) }
            } else {
                emptyList()
            }
            return UserSongPlaylist(
                id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = j.optString("name", "Список"),
                songRefs = refs,
                temporary = j.optBoolean("tmp", false),
                lookId = j.optString("look").ifBlank { PlaylistLook.INK.id },
                subtitle = j.optString("sub").trim(),
                sort = SongListSort.fromId(j.optString("sort")),
                createdAt = j.optLong("at", 0L),
                updatedAt = j.optLong("upd", j.optLong("at", 0L)),
            )
        }

        fun parseList(json: String): List<UserSongPlaylist> {
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

        fun toJsonArray(list: List<UserSongPlaylist>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}
