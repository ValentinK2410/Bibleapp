package com.example.bible.data.travel

import kotlin.math.abs
import org.json.JSONObject

/** Положение другого пользователя на карте (опрос JSON или ручной ввод). */
data class FriendPeerLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String?,
    /** Время последнего известного обновления точки (мс с epoch). */
    val updatedAtMs: Long,
)

fun parseFriendPeerLocationJson(jsonText: String): FriendPeerLocation? {
    val trimmed = jsonText.trim()
    if (trimmed.isEmpty()) return null
    return try {
        val j = JSONObject(trimmed)
        val lat = when {
            j.has("latitude") -> j.optDouble("latitude", Double.NaN)
            j.has("lat") -> j.optDouble("lat", Double.NaN)
            else -> Double.NaN
        }
        val lon = when {
            j.has("longitude") -> j.optDouble("longitude", Double.NaN)
            j.has("lng") -> j.optDouble("lng", Double.NaN)
            j.has("lon") -> j.optDouble("lon", Double.NaN)
            else -> Double.NaN
        }
        if (!lat.isFinite() || !lon.isFinite()) return null
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return null
        val label = j.optString("label", "").trim().takeIf { it.isNotEmpty() }
            ?: j.optString("name", "").trim().takeIf { it.isNotEmpty() }
        val updated = when {
            j.has("updatedAtMs") -> j.optLong("updatedAtMs", System.currentTimeMillis())
            j.has("t") -> j.optLong("t", System.currentTimeMillis())
            else -> System.currentTimeMillis()
        }
        FriendPeerLocation(
            latitude = lat,
            longitude = lon,
            label = label,
            updatedAtMs = updated,
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * Разбор строки «55.755826, 37.617299» или «55.755826 37.617299».
 */
fun parseLatLonManualLine(line: String): Pair<Double, Double>? {
    val s = line.trim()
    if (s.isEmpty()) return null
    val parts = s.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
    if (parts.size < 2) return null
    val a = parts[0].toDoubleOrNull() ?: return null
    val b = parts[1].toDoubleOrNull() ?: return null
    val lat: Double
    val lon: Double
    if (abs(a) <= 90.0 && abs(b) <= 180.0) {
        lat = a
        lon = b
    } else {
        return null
    }
    if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return null
    return lat to lon
}
