package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Заметка к видео. [positionMs] — если мысль привязана к кадру, иначе null. */
data class VideoThought(
    val id: String = UUID.randomUUID().toString(),
    val positionMs: Int? = null,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        if (positionMs != null && positionMs >= 0) put("ms", positionMs)
        put("t", text)
        put("at", createdAt)
    }

    companion object {
        fun fromJson(j: JSONObject): VideoThought {
            val ms = if (j.has("ms")) j.optInt("ms", -1).takeIf { it >= 0 } else null
            return VideoThought(
                id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
                positionMs = ms,
                text = j.optString("t", ""),
                createdAt = j.optLong("at", 0L),
            )
        }

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
                        }.sortedWith(
                            compareBy<VideoThought> { it.positionMs != null }
                                .thenBy { it.positionMs ?: 0 }
                                .thenBy { it.createdAt },
                        )
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
