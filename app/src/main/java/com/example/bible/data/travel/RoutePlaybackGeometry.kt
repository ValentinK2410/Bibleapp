package com.example.bible.data.travel

import android.location.Location

/** Один отрезок пути между двумя точками серии фото (по времени съёмки). */
data class RoutePlaybackSegment(
    val lat1: Double,
    val lon1: Double,
    val lat2: Double,
    val lon2: Double,
    val lengthM: Float,
)

/**
 * Полилиния для виртуального проезда и совпадения кадров с расстояниями до вершин.
 * Вершины совпадают с [sortedPoints] (отсортированы по [TravelRoutePhotoPoint.capturedAtMs]).
 */
data class RoutePlaybackPolyline(
    val segments: List<RoutePlaybackSegment>,
    val sortedPoints: List<TravelRoutePhotoPoint>,
    /** cumulativeToVertexM[i] — расстояние от начала пути до i-й точки (первая всегда 0). */
    val cumulativeToVertexM: FloatArray,
) {
    val totalLengthM: Float
        get() = cumulativeToVertexM.lastOrNull() ?: 0f
}

fun buildRoutePlaybackPolyline(points: List<TravelRoutePhotoPoint>): RoutePlaybackPolyline? {
    val sorted = points.sortedBy { it.capturedAtMs }
    if (sorted.isEmpty()) return null
    if (sorted.size == 1) {
        val p = sorted[0]
        val cum = floatArrayOf(0f)
        val seg = RoutePlaybackSegment(
            lat1 = p.latitude,
            lon1 = p.longitude,
            lat2 = p.latitude,
            lon2 = p.longitude,
            lengthM = 1f,
        )
        return RoutePlaybackPolyline(listOf(seg), sorted, cum)
    }
    val segments = ArrayList<RoutePlaybackSegment>(sorted.lastIndex)
    val cumulative = FloatArray(sorted.size)
    cumulative[0] = 0f
    var total = 0f
    for (i in 0 until sorted.lastIndex) {
        val a = sorted[i]
        val b = sorted[i + 1]
        val dist = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, dist)
        val len = dist[0].coerceAtLeast(0.5f)
        total += len
        cumulative[i + 1] = total
        segments += RoutePlaybackSegment(a.latitude, a.longitude, b.latitude, b.longitude, len)
    }
    return RoutePlaybackPolyline(segments, sorted, cumulative)
}

/** Положение на полилинии и азимут движения (по текущему сегменту). */
fun interpolateRoutePlayback(poly: RoutePlaybackPolyline, distanceM: Float): Triple<Double, Double, Float> {
    val total = poly.totalLengthM.coerceAtLeast(1f)
    var dLoop = distanceM % total
    if (dLoop < 0f) dLoop += total
    var remaining = dLoop
    for (seg in poly.segments) {
        val len = seg.lengthM.coerceAtLeast(1e-4f)
        if (remaining <= len || poly.segments.size == 1) {
            val t = (remaining / len).coerceIn(0f, 1f)
            val lat = seg.lat1 + (seg.lat2 - seg.lat1) * t
            val lon = seg.lon1 + (seg.lon2 - seg.lon1) * t
            val bear = bearingDegreesLatLon(seg.lat1, seg.lon1, seg.lat2, seg.lon2)
            return Triple(lat, lon, bear)
        }
        remaining -= len
    }
    val last = poly.segments.last()
    val bear = bearingDegreesLatLon(last.lat1, last.lon1, last.lat2, last.lon2)
    return Triple(last.lat2, last.lon2, bear)
}

/**
 * Расстояние от начала полилинии вдоль траектории до проекции [latitude],[longitude]
 * на ближайший отрезок (метры).
 */
fun nearestDistanceAlongPolyline(poly: RoutePlaybackPolyline, latitude: Double, longitude: Double): Float {
    if (poly.segments.isEmpty()) return 0f
    var cumulative = 0f
    var bestAlong = 0f
    var bestDistSq = Double.MAX_VALUE
    for (seg in poly.segments) {
        val ax = seg.lat1
        val ay = seg.lon1
        val bx = seg.lat2
        val by = seg.lon2
        val len = seg.lengthM.toDouble().coerceAtLeast(1e-6)
        val abLat = bx - ax
        val abLon = by - ay
        val apLat = latitude - ax
        val apLon = longitude - ay
        val dot = apLat * abLat + apLon * abLon
        val abSq = abLat * abLat + abLon * abLon
        val t = if (abSq < 1e-22) 0.0 else (dot / abSq).coerceIn(0.0, 1.0)
        val clat = ax + abLat * t
        val clon = ay + abLon * t
        val dArr = FloatArray(1)
        Location.distanceBetween(latitude, longitude, clat, clon, dArr)
        val d = dArr[0].toDouble()
        val dSq = d * d
        if (dSq < bestDistSq) {
            bestDistSq = dSq
            bestAlong = (cumulative + len * t).toFloat()
        }
        cumulative += seg.lengthM
    }
    val total = poly.totalLengthM.coerceAtLeast(1f)
    return bestAlong.coerceIn(0f, total)
}

/**
 * URI последнего кадра, до которого «добрались» по расстоянию по маршруту
 * (кадры берутся по порядку времени съёмки).
 */
fun routePlaybackPhotoUriAtDistance(poly: RoutePlaybackPolyline, distanceM: Float): String? {
    val pts = poly.sortedPoints
    if (pts.isEmpty()) return null
    val total = poly.totalLengthM.coerceAtLeast(1f)
    var d = distanceM % total
    if (d < 0f) d += total
    var idx = 0
    for (i in pts.indices) {
        if (poly.cumulativeToVertexM[i] <= d + 3f) idx = i
    }
    return pts[idx].photoUri
}

/** Текущее состояние виртуального проезда для карты и превью кадров. */
data class RoutePlaybackSimState(
    val latitude: Double,
    val longitude: Double,
    val bearingDeg: Float,
    val progress: Float,
    val distanceAlongMeters: Float,
    val totalPathMeters: Float,
    val currentPhotoUri: String?,
)
