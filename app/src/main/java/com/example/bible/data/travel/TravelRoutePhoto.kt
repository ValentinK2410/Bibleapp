package com.example.bible.data.travel

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

/** Одна точка серии фото по GPS с файлом изображения во внутреннем хранилище. */
data class TravelRoutePhotoPoint(
    val latitude: Double,
    val longitude: Double,
    /** file:// URI-как строка */
    val photoUri: String,
    val capturedAtMs: Long = System.currentTimeMillis(),
    /**
     * Азимут камеры в момент снимка (0° — север, по часовой). Для подбора кадра при возврате на точку:
     * показываем фото, снятое в похожем направлении взгляда.
     */
    val headingDeg: Float? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("lat", latitude)
        put("lng", longitude)
        put("u", photoUri)
        put("t", capturedAtMs)
        headingDeg?.takeIf { it.isFinite() }?.let { put("h", it.toDouble()) }
    }

    companion object {
        fun fromJson(j: JSONObject): TravelRoutePhotoPoint = TravelRoutePhotoPoint(
            latitude = j.getDouble("lat"),
            longitude = j.getDouble("lng"),
            photoUri = j.getString("u"),
            capturedAtMs = j.optLong("t", System.currentTimeMillis()),
            headingDeg = when {
                !j.has("h") -> null
                else -> {
                    val v = j.optDouble("h", Double.NaN)
                    if (v.isFinite()) v.toFloat() else null
                }
            },
        )
    }
}

/** Нормализация азимута в [0; 360). */
fun normalizeHeadingDeg(deg: Float): Float {
    var v = deg % 360f
    if (v < 0f) v += 360f
    return v
}

/** Минимальная разница между двумя азимутами, градусы ≤ 180. */
fun headingAngularDifferenceDeg(a: Float, b: Float): Float {
    val na = normalizeHeadingDeg(a)
    val nb = normalizeHeadingDeg(b)
    var d = abs(na - nb)
    if (d > 180f) d = 360f - d
    return d
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

/**
 * Точка серии рядом с [here]: при заданном [viewerHeadingDeg] предпочитаем кадр, снятый в том же
 * направлении взгляда; иначе — как раньше, минимальное расстояние в пределах [maxDistanceMeters].
 */
fun nearestRoutePhotoPoint(
    here: TravelGeoPoint,
    points: List<TravelRoutePhotoPoint>,
    maxDistanceMeters: Double = 40.0,
    viewerHeadingDeg: Float? = null,
): TravelRoutePhotoPoint? {
    if (points.isEmpty()) return null
    val candidates = points.mapNotNull { p ->
        val d = travelDistanceMeters(here, TravelGeoPoint(p.latitude, p.longitude))
        if (d <= maxDistanceMeters) p to d else null
    }
    if (candidates.isEmpty()) return null

    val viewer = viewerHeadingDeg
    if (viewer == null || !viewer.isFinite()) {
        return candidates.minBy { it.second }.first
    }

    val withHeading = candidates.filter { (p, _) ->
        val h = p.headingDeg
        h != null && h.isFinite()
    }
    if (withHeading.isEmpty()) {
        return candidates.minBy { it.second }.first
    }

    val norm = normalizeHeadingDeg(viewer)
    return withHeading.minWith(
        compareBy<Pair<TravelRoutePhotoPoint, Double>>(
            { headingAngularDifferenceDeg(norm, it.first.headingDeg!!) },
            { it.second },
        ),
    ).first
}
