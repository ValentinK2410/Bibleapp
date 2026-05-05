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
 * Расстояние от начала полилинии вдоль траектории до **ближайшего** места на ломаной к точке ([latitude],[longitude]).
 *
 * Для каждого сегмента ищем [t]∈[0,1], минимизирующий [Location.distanceBetween] до линейно интерполированной точки
 * на отрезке; расстояние вдоль пути — сумма длин «кусков»: от вершины сегмента до найденной точки + накопленное.
 *
 * Устаревшая проекция в «голых» градусах без учёта соотношения шагов по широте/долготе смещала точку попадания
 * после касания карты относительно реального трека.
 */
fun nearestDistanceAlongPolyline(poly: RoutePlaybackPolyline, latitude: Double, longitude: Double): Float {
    if (poly.segments.isEmpty()) return 0f
    if (poly.sortedPoints.size <= 1) return 0f

    fun distM(latP: Double, lonP: Double, latX: Double, lonX: Double): Float {
        val arr = FloatArray(1)
        Location.distanceBetween(latP, lonP, latX, lonX, arr)
        return arr[0]
    }

    fun closestPointOnSegment(seg: RoutePlaybackSegment): Pair<Double, Double> {
        fun distSqAt(t: Double): Double {
            val clat = seg.lat1 + (seg.lat2 - seg.lat1) * t
            val clon = seg.lon1 + (seg.lon2 - seg.lon1) * t
            val d = distM(latitude, longitude, clat, clon).toDouble()
            return d * d
        }
        var lo = 0.0
        var hi = 1.0
        repeat(28) {
            val m1 = lo + (hi - lo) / 3.0
            val m2 = hi - (hi - lo) / 3.0
            if (distSqAt(m1) <= distSqAt(m2)) hi = m2 else lo = m1
        }
        val t = (lo + hi) * 0.5
        val latC = seg.lat1 + (seg.lat2 - seg.lat1) * t
        val lonC = seg.lon1 + (seg.lon2 - seg.lon1) * t
        return Pair(latC, lonC)
    }

    var cumulative = 0f
    var bestAlong = 0f
    var bestDistSq = Double.MAX_VALUE
    for (seg in poly.segments) {
        val (clat, clon) = closestPointOnSegment(seg)
        val dm = distM(latitude, longitude, clat, clon).toDouble()
        val dSq = dm * dm
        val segmentPortion = distM(seg.lat1, seg.lon1, clat, clon)
        val along = cumulative + segmentPortion
        if (dSq < bestDistSq) {
            bestDistSq = dSq
            bestAlong = along
        }
        cumulative += seg.lengthM
    }
    val total = poly.totalLengthM.coerceAtLeast(1f)
    return bestAlong.coerceIn(0f, total)
}

/** Перпендикулярное расстояние от точки до ломаной (м): к проекции на трек — [nearestDistanceAlongPolyline]. */
fun distanceMetersToRoutePolyline(poly: RoutePlaybackPolyline, latitude: Double, longitude: Double): Float {
    val along = nearestDistanceAlongPolyline(poly, latitude, longitude)
    val (lat, lon, _) = interpolateRoutePlayback(poly, along)
    val buf = FloatArray(1)
    Location.distanceBetween(latitude, longitude, lat, lon, buf)
    return buf[0]
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
    /** Если false — человечек двигается, азимут задаётся симуляцией, карту поворачивает только пользователь */
    val followCameraWithWalker: Boolean = true,
)
