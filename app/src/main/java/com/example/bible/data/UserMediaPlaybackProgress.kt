package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class UserMediaKind(val code: String) {
    VIDEO("v"),
    AUDIO("a"),
    ;

    companion object {
        fun fromCode(code: String): UserMediaKind =
            entries.find { it.code == code } ?: VIDEO
    }
}

/** Сохранённый прогресс просмотра/прослушивания пользовательского медиафайла. */
data class UserMediaPlaybackProgress(
    val mediaId: String,
    val kind: UserMediaKind,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val completed: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val percent: Float
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    fun isResumable(): Boolean =
        !completed &&
            positionMs >= 2_000 &&
            (durationMs <= 0 || positionMs < durationMs * 0.92)

    fun statusLabelRu(kind: UserMediaKind = UserMediaKind.VIDEO): String = when {
        completed -> if (kind == UserMediaKind.AUDIO) "Прослушано" else "Просмотрено"
        isResumable() -> "Продолжить с ${formatMediaTimeMs(positionMs)}"
        positionMs > 0 -> "Начато · ${(percent * 100).toInt()}%"
        else -> ""
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", mediaId)
        put("k", kind.code)
        put("pos", positionMs)
        put("dur", durationMs)
        put("done", completed)
        put("at", updatedAt)
    }

    companion object {
        fun fromJson(j: JSONObject): UserMediaPlaybackProgress = UserMediaPlaybackProgress(
            mediaId = j.getString("id"),
            kind = UserMediaKind.fromCode(j.optString("k", "v")),
            positionMs = j.optLong("pos", 0L),
            durationMs = j.optLong("dur", 0L),
            completed = j.optBoolean("done", false),
            updatedAt = j.optLong("at", System.currentTimeMillis()),
        )

        fun parseMap(json: String): Map<String, UserMediaPlaybackProgress> {
            if (json.isBlank()) return emptyMap()
            return try {
                val arr = JSONArray(json)
                buildMap {
                    for (i in 0 until arr.length()) {
                        val item = fromJson(arr.getJSONObject(i))
                        put(item.mediaId, item)
                    }
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }

        fun toJsonArray(map: Map<String, UserMediaPlaybackProgress>): String {
            val arr = JSONArray()
            map.values.sortedByDescending { it.updatedAt }.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

fun formatMediaTimeMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", m, s)
    }
}

fun UserMediaPlaybackProgress?.completedLabelRu(kind: UserMediaKind): String =
    this?.statusLabelRu(kind).orEmpty()
