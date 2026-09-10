package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Заметка к моменту видео: пауза → записать мысль. */
data class VideoThought(
    val id: String = UUID.randomUUID().toString(),
    val positionMs: Int,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("ms", positionMs)
        put("t", text)
        put("at", createdAt)
    }

    companion object {
        fun fromJson(j: JSONObject): VideoThought = VideoThought(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            positionMs = j.optInt("ms", 0).coerceAtLeast(0),
            text = j.optString("t", ""),
            createdAt = j.optLong("at", 0L),
        )

        fun parseMap(json: String): Map<String, List<VideoThought>> {
            if (json.isBlank()) return emptyMap()
            return try {
                val root = JSONObject(json)
                buildMap {
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val videoId = keys.next()
                        val arr = root.optJSONArray(videoId) ?: continue
                        val list = (0 until arr.length()).mapNotNull { i ->
                            val o = arr.optJSONObject(i) ?: return@mapNotNull null
                            fromJson(o).takeIf { it.text.isNotBlank() }
                        }.sortedBy { it.positionMs }
                        if (list.isNotEmpty()) put(videoId, list)
                    }
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }

        fun toJsonMap(map: Map<String, List<VideoThought>>): String {
            val root = JSONObject()
            map.forEach { (videoId, thoughts) ->
                if (thoughts.isEmpty()) return@forEach
                val arr = JSONArray()
                thoughts.sortedBy { it.positionMs }.forEach { arr.put(it.toJson()) }
                root.put(videoId, arr)
            }
            return root.toString()
        }
    }
}
