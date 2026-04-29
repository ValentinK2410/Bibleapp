package com.example.bible.data.travel

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Одна точка серии фото по GPS с файлом изображения во внутреннем хранилище. */
data class TravelRoutePhotoPoint(
    val latitude: Double,
    val longitude: Double,
    /** file:// URI-как строка */
    val photoUri: String,
    val capturedAtMs: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("lat", latitude)
        put("lng", longitude)
        put("u", photoUri)
        put("t", capturedAtMs)
    }

    companion object {
        fun fromJson(j: JSONObject): TravelRoutePhotoPoint = TravelRoutePhotoPoint(
            latitude = j.getDouble("lat"),
            longitude = j.getDouble("lng"),
            photoUri = j.getString("u"),
            capturedAtMs = j.optLong("t", System.currentTimeMillis()),
        )
    }
}

/** Серия снимков по маршруту (пользователь включил съёмку и двигался по карте). */
data class TravelRoutePhotoSession(
    val id: String = UUID.randomUUID().toString(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val points: List<TravelRoutePhotoPoint> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("createdAt", createdAtMs)
        val arr = JSONArray()
        points.forEach { arr.put(it.toJson()) }
        put("points", arr)
    }

    companion object {
        fun fromJson(j: JSONObject): TravelRoutePhotoSession {
            val arr = j.optJSONArray("points")
            val pts = mutableListOf<TravelRoutePhotoPoint>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    runCatching { pts.add(TravelRoutePhotoPoint.fromJson(o)) }
                }
            }
            return TravelRoutePhotoSession(
                id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
                createdAtMs = j.optLong("createdAt", System.currentTimeMillis()),
                points = pts,
            )
        }

        fun parseList(json: String): List<TravelRoutePhotoSession> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<TravelRoutePhotoSession>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

/** Ближайшая точка серии к текущему положению; null если вне радиуса. */
fun nearestRoutePhotoPoint(
    here: TravelGeoPoint,
    points: List<TravelRoutePhotoPoint>,
    maxDistanceMeters: Double = 40.0,
): TravelRoutePhotoPoint? {
    if (points.isEmpty()) return null
    var best: TravelRoutePhotoPoint? = null
    var bestD = Double.MAX_VALUE
    for (p in points) {
        val d = travelDistanceMeters(here, TravelGeoPoint(p.latitude, p.longitude))
        if (d < bestD && d <= maxDistanceMeters) {
            bestD = d
            best = p
        }
    }
    return best
}
