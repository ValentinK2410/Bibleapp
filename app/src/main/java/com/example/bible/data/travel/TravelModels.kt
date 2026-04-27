package com.example.bible.data.travel

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Географическая точка (широта, долгота); без привязки к конкретному SDK карт. */
data class TravelGeoPoint(
    val latitude: Double,
    val longitude: Double,
)

enum class TravelZoneKind {
    CIRCLE,
    POLYGON;

    fun toJson(): String = name

    companion object {
        fun fromJson(s: String): TravelZoneKind =
            runCatching { valueOf(s) }.getOrDefault(CIRCLE)
    }
}

enum class TravelTriggerAction {
    NOTIFICATION_ONLY,
    BEEP,
    PLAY_SOUND,
    PLAY_VIDEO;

    fun toJson(): String = name

    companion object {
        fun fromJson(s: String): TravelTriggerAction =
            runCatching { valueOf(s) }.getOrDefault(NOTIFICATION_ONLY)
    }
}

data class TravelZone(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val kind: TravelZoneKind = TravelZoneKind.CIRCLE,
    /** Центр круга или условный центр полигона (камера карты). */
    val centerLat: Double,
    val centerLng: Double,
    /** Радиус круга, м (для полигона не используется в логике попадания). */
    val radiusMeters: Float = 150f,
    /** Вершины полигона (минимум 3). Пусто для круга. */
    val polygonPoints: List<TravelGeoPoint> = emptyList(),
    val action: TravelTriggerAction = TravelTriggerAction.NOTIFICATION_ONLY,
    /** content:// или file:// для звука/видео. */
    val mediaUri: String? = null,
    val cooldownMs: Long = 120_000L,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("kind", kind.toJson())
        put("centerLat", centerLat)
        put("centerLng", centerLng)
        put("radiusMeters", radiusMeters.toDouble())
        val arr = JSONArray()
        polygonPoints.forEach { p ->
            arr.put(JSONObject().apply { put("lat", p.latitude); put("lng", p.longitude) })
        }
        put("polygon", arr)
        put("action", action.toJson())
        put("mediaUri", mediaUri)
        put("cooldownMs", cooldownMs)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(j: JSONObject): TravelZone {
            val poly = mutableListOf<TravelGeoPoint>()
            val arr = j.optJSONArray("polygon")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    poly.add(TravelGeoPoint(o.getDouble("lat"), o.getDouble("lng")))
                }
            }
            return TravelZone(
                id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = j.optString("name", "Место"),
                enabled = j.optBoolean("enabled", true),
                kind = TravelZoneKind.fromJson(j.optString("kind", "CIRCLE")),
                centerLat = j.optDouble("centerLat", 0.0),
                centerLng = j.optDouble("centerLng", 0.0),
                radiusMeters = j.optDouble("radiusMeters", 150.0).toFloat(),
                polygonPoints = poly,
                action = TravelTriggerAction.fromJson(j.optString("action", "NOTIFICATION_ONLY")),
                mediaUri = j.optString("mediaUri").takeIf { it.isNotBlank() },
                cooldownMs = j.optLong("cooldownMs", 120_000L),
                createdAt = j.optLong("createdAt", System.currentTimeMillis()),
            )
        }

        fun parseList(json: String): List<TravelZone> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<TravelZone>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

/** Пользовательская отметка на карте путешествий («что-то произошло»). */
data class TravelMapIncident(
    val id: String = UUID.randomUUID().toString(),
    val latitude: Double,
    val longitude: Double,
    val note: String = "",
    /** file:// или content:// — звук при приближении / тапе рядом с отметкой. */
    val soundUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("latitude", latitude)
        put("longitude", longitude)
        put("note", note)
        soundUri?.let { put("soundUri", it) }
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(j: JSONObject): TravelMapIncident = TravelMapIncident(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            latitude = j.optDouble("latitude", 0.0),
            longitude = j.optDouble("longitude", 0.0),
            note = j.optString("note", ""),
            soundUri = j.optString("soundUri").takeIf { it.isNotBlank() },
            createdAt = j.optLong("createdAt", System.currentTimeMillis()),
        )

        fun parseList(json: String): List<TravelMapIncident> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<TravelMapIncident>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

/** Расстояние между точками на сфере, м (для круговых зон). */
fun travelDistanceMeters(a: TravelGeoPoint, b: TravelGeoPoint): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

/** Площадь полигона в «условных» единицах (долгота–широта), для выбора меньшей зоны при перекрытии. */
private fun travelPolygonAreaMetric(polygon: List<TravelGeoPoint>): Double {
    if (polygon.size < 3) return Double.MAX_VALUE
    var s = 0.0
    for (i in polygon.indices) {
        val j = (i + 1) % polygon.size
        s += polygon[i].longitude * polygon[j].latitude
        s -= polygon[j].longitude * polygon[i].latitude
    }
    return abs(s * 0.5)
}

/**
 * Зоны, в которые попадает точка; при нескольких совпадениях — сначала более «узкие»
 * (меньший радиус круга или меньшая площадь полигона).
 */
fun travelZonesAtPoint(zones: List<TravelZone>, point: TravelGeoPoint): List<TravelZone> {
    data class Scored(val zone: TravelZone, val metric: Double)
    val scored = mutableListOf<Scored>()
    for (z in zones) {
        when (z.kind) {
            TravelZoneKind.CIRCLE -> {
                val c = TravelGeoPoint(z.centerLat, z.centerLng)
                val d = travelDistanceMeters(c, point)
                if (d <= z.radiusMeters) {
                    scored.add(Scored(z, z.radiusMeters.toDouble()))
                }
            }
            TravelZoneKind.POLYGON -> {
                if (z.polygonPoints.size >= 3 && pointInPolygon(point, z.polygonPoints)) {
                    scored.add(Scored(z, travelPolygonAreaMetric(z.polygonPoints)))
                }
            }
        }
    }
    return scored.sortedBy { it.metric }.map { it.zone }
}

/** Попадание точки в простой полигон (без дыр), ray casting (долгота ~ x, широта ~ y). */
fun pointInPolygon(point: TravelGeoPoint, polygon: List<TravelGeoPoint>): Boolean {
    if (polygon.size < 3) return false
    val x = point.longitude
    val y = point.latitude
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val xi = polygon[i].longitude
        val yi = polygon[i].latitude
        val xj = polygon[j].longitude
        val yj = polygon[j].latitude
        val intersect = ((yi > y) != (yj > y)) &&
            (x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi)
        if (intersect) inside = !inside
        j = i
    }
    return inside
}
