package com.example.bible.data.travel

import android.location.Location
import org.json.JSONArray
import org.json.JSONObject

/** Одна точка записанного GPS-трека поездки (время устройства, скорость с датчика при наличии). */
data class TravelTripTrackPoint(
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    /** м/с */
    val speedMps: Float,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("t", timestampMs)
        put("lat", latitude)
        put("lng", longitude)
        put("s", speedMps.toDouble())
    }

    companion object {
        fun fromJson(j: JSONObject): TravelTripTrackPoint? = runCatching {
            TravelTripTrackPoint(
                timestampMs = j.getLong("t"),
                latitude = j.getDouble("lat"),
                longitude = j.getDouble("lng"),
                speedMps = j.optDouble("s", 0.0).toFloat().coerceAtLeast(0f),
            )
        }.getOrNull()

        fun fromLocation(loc: Location): TravelTripTrackPoint {
            val ts = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis()
            val sp = if (loc.hasSpeed()) loc.speed.coerceAtLeast(0f) else 0f
            return TravelTripTrackPoint(
                timestampMs = ts,
                latitude = loc.latitude,
                longitude = loc.longitude,
                speedMps = sp,
            )
        }

        fun parseList(json: String): List<TravelTripTrackPoint> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        fromJson(o)?.let { add(it) }
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<TravelTripTrackPoint>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

private const val TRIP_TRACK_MERGE_MIN_DT_MS = 4_000L
private const val TRIP_TRACK_MERGE_MIN_DIST_M = 4.0
internal const val TRIP_TRACK_MAX_POINTS = 120_000
internal const val TRIP_TRACK_MAX_AGE_MS = 366L * 24L * 3600L * 1000L

/** Объединить новые точки с сохранёнными: убрать почти-дубликаты, обрезать по возрасту и числу. */
fun mergeAndPruneTripTrack(
    existing: List<TravelTripTrackPoint>,
    incoming: List<TravelTripTrackPoint>,
): List<TravelTripTrackPoint> {
    if (incoming.isEmpty()) return pruneTripTrack(existing)
    val merged = (existing + incoming).sortedBy { it.timestampMs }
    val out = ArrayList<TravelTripTrackPoint>(merged.size.coerceAtMost(existing.size + incoming.size))
    for (p in merged) {
        val last = out.lastOrNull()
        if (last != null) {
            val dt = p.timestampMs - last.timestampMs
            val d = travelDistanceMeters(
                TravelGeoPoint(last.latitude, last.longitude),
                TravelGeoPoint(p.latitude, p.longitude),
            )
            if (dt in 0 until TRIP_TRACK_MERGE_MIN_DT_MS && d < TRIP_TRACK_MERGE_MIN_DIST_M) {
                continue
            }
        }
        out.add(p)
    }
    return pruneTripTrack(out)
}

private fun pruneTripTrack(points: List<TravelTripTrackPoint>): List<TravelTripTrackPoint> {
    val cutoff = System.currentTimeMillis() - TRIP_TRACK_MAX_AGE_MS
    val ageFiltered = points.filter { it.timestampMs >= cutoff }
    return if (ageFiltered.size <= TRIP_TRACK_MAX_POINTS) {
        ageFiltered
    } else {
        ageFiltered.takeLast(TRIP_TRACK_MAX_POINTS)
    }
}

/** Пройденное расстояние по цепочке точек (м). */
fun tripTrackPathLengthMeters(points: List<TravelTripTrackPoint>): Double {
    if (points.size < 2) return 0.0
    var sum = 0.0
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        sum += travelDistanceMeters(
            TravelGeoPoint(a.latitude, a.longitude),
            TravelGeoPoint(b.latitude, b.longitude),
        )
    }
    return sum
}
