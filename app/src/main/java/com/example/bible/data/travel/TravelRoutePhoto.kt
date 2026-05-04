package com.example.bible.data.travel

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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

private const val EARTH_RADIUS_M_ROUTE_LINE = 6371009.0

/** Азимут от точки A к B (0° — север), для участков без сохранённого heading. */
fun bearingDegreesLatLon(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val φ1 = Math.toRadians(lat1)
    val φ2 = Math.toRadians(lat2)
    val Δλ = Math.toRadians(lon2 - lon1)
    val y = sin(Δλ) * cos(φ2)
    val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
    val θ = atan2(y, x)
    return normalizeHeadingDeg(Math.toDegrees(θ).toFloat())
}

/** Смещение точки по азимуту на короткую дистанцию (метры). */
fun extrapolateLatLonMeters(latDeg: Double, lonDeg: Double, bearingDeg: Float, distanceM: Float): Pair<Double, Double> {
    if (distanceM <= 0f) return latDeg to lonDeg
    val br = Math.toRadians(bearingDeg.toDouble())
    val latRad = Math.toRadians(latDeg)
    val d = distanceM.toDouble().coerceAtMost(120.0)
    val north = d * cos(br)
    val east = d * sin(br)
    val dLat = north / EARTH_RADIUS_M_ROUTE_LINE * (180.0 / Math.PI)
    val dLon = east / (EARTH_RADIUS_M_ROUTE_LINE * cos(latRad).coerceAtLeast(1e-6)) * (180.0 / Math.PI)
    return (latDeg + dLat) to (lonDeg + dLon)
}

/** Индекс сектора по 45° (0…7): примерно С, СВ, В, ЮВ, Ю, ЮЗ, З, СЗ. */
fun headingSector8(headingDeg: Float): Int {
    val n = normalizeHeadingDeg(headingDeg)
    return (n / 45f).toInt().coerceIn(0, 7)
}

fun headingSectorColorArgb(sector: Int): Int {
    val palette = intArrayOf(
        0xFFE53935.toInt(),
        0xFFFF7043.toInt(),
        0xFFFFCA28.toInt(),
        0xFF66BB6A.toInt(),
        0xFF29B6F6.toInt(),
        0xFF7E57C2.toInt(),
        0xFFEC407A.toInt(),
        0xFF26A69A.toInt(),
    )
    return palette[sector % 8]
}

/** Сегмент полилинии для карты: цвет соответствует направлению съёмки / ходу между точками. */
data class RoutePhotoLineSegment(
    val lat1: Double,
    val lon1: Double,
    val lat2: Double,
    val lon2: Double,
    val colorArgb: Int,
)

/** Строит сегменты по временному порядку точек в каждой серии; одиночная точка даёт короткий штрих по heading. */
fun buildRoutePhotoDirectionSegments(sessions: List<TravelRoutePhotoSession>): List<RoutePhotoLineSegment> {
    val out = ArrayList<RoutePhotoLineSegment>(64)
    for (session in sessions) {
        val pts = session.points.sortedBy { it.capturedAtMs }
        if (pts.isEmpty()) continue
        if (pts.size == 1) {
            val p = pts[0]
            val h = p.headingDeg
            if (h != null && h.isFinite()) {
                val (lat2, lon2) = extrapolateLatLonMeters(p.latitude, p.longitude, h, 24f)
                out.add(
                    RoutePhotoLineSegment(
                        p.latitude,
                        p.longitude,
                        lat2,
                        lon2,
                        headingSectorColorArgb(headingSector8(h)),
                    ),
                )
            }
            continue
        }
        for (i in 0 until pts.lastIndex) {
            val a = pts[i]
            val b = pts[i + 1]
            val motionBearing = bearingDegreesLatLon(a.latitude, a.longitude, b.latitude, b.longitude)
            val heading = a.headingDeg?.takeIf { it.isFinite() }
            val sectorDeg = heading ?: motionBearing
            out.add(
                RoutePhotoLineSegment(
                    a.latitude,
                    a.longitude,
                    b.latitude,
                    b.longitude,
                    headingSectorColorArgb(headingSector8(sectorDeg)),
                ),
            )
        }
    }
    return out
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

private const val FOLK_BURST_MAX_HEADING_DIFF_DEG = 52f
private const val FOLK_BURST_MAX_PREVIEW_COUNT = 36

/**
 * Для превью «народной съёмки»: кадры в похожем направлении взгляда относительно пользователя,
 * от ближайших к дальним. Без совпадений по направлению — ближайшие по расстоянию.
 */
fun folkBurstFilteredPointsForViewer(
    points: List<TravelRoutePhotoPoint>,
    viewerLat: Double?,
    viewerLon: Double?,
    viewerHeadingDeg: Float?,
): List<TravelRoutePhotoPoint> {
    if (points.isEmpty()) return emptyList()
    if (viewerLat == null || viewerLon == null) {
        return points.takeLast(FOLK_BURST_MAX_PREVIEW_COUNT).asReversed()
    }
    val here = TravelGeoPoint(viewerLat, viewerLon)
    val vh = viewerHeadingDeg?.takeIf { it.isFinite() }
    val scored = points.mapNotNull { p ->
        val d = travelDistanceMeters(here, TravelGeoPoint(p.latitude, p.longitude))
        val dirOk = when {
            vh == null -> true
            p.headingDeg != null && p.headingDeg.isFinite() ->
                headingAngularDifferenceDeg(vh, p.headingDeg) <= FOLK_BURST_MAX_HEADING_DIFF_DEG
            else -> {
                val bearingToPhoto = bearingDegreesLatLon(viewerLat, viewerLon, p.latitude, p.longitude)
                headingAngularDifferenceDeg(vh, bearingToPhoto) <= FOLK_BURST_MAX_HEADING_DIFF_DEG
            }
        }
        if (dirOk) p to d else null
    }
    val ordered = if (scored.isEmpty()) {
        points.map { p ->
            p to travelDistanceMeters(here, TravelGeoPoint(p.latitude, p.longitude))
        }.sortedBy { it.second }
    } else {
        scored.sortedBy { it.second }
    }
    return ordered.map { it.first }.take(FOLK_BURST_MAX_PREVIEW_COUNT)
}

private const val SPOT_RING_MIN_DISTANCE_M = 0.0
/** Реальный GPS редко держит 1–3 м; расширяем «рядом на карте», порядок — от ближних к дальним. */
private const val SPOT_RING_MAX_DISTANCE_M = 22.0
/** Внутри этого радиуса пеленг «вы→точка» ненадёжен — достаточно геопривязки. */
private const val SPOT_NEARBY_SKIP_HEADING_M = 8.0
private const val SPOT_RING_MAX_RESULTS = 32

/**
 * Кадры рядом с текущей точкой: до [SPOT_RING_MAX_DISTANCE_M] м (включая «на месте», от 0 м),
 * с фильтром по направлению как у «народной съёмки», кроме очень близких кадров ([SPOT_NEARBY_SKIP_HEADING_M] м).
 */
fun routePhotosInSpotRingForViewer(
    points: List<TravelRoutePhotoPoint>,
    viewerLat: Double?,
    viewerLon: Double?,
    viewerHeadingDeg: Float?,
): List<TravelRoutePhotoPoint> {
    if (points.isEmpty()) return emptyList()
    if (viewerLat == null || viewerLon == null) return emptyList()
    val here = TravelGeoPoint(viewerLat, viewerLon)
    val vh = viewerHeadingDeg?.takeIf { it.isFinite() }
    val inRing = points.mapNotNull { p ->
        val d = travelDistanceMeters(here, TravelGeoPoint(p.latitude, p.longitude))
        if (d < SPOT_RING_MIN_DISTANCE_M || d > SPOT_RING_MAX_DISTANCE_M) return@mapNotNull null
        val skipHeading = d <= SPOT_NEARBY_SKIP_HEADING_M
        val dirOk = skipHeading || when {
            vh == null -> true
            p.headingDeg != null && p.headingDeg.isFinite() ->
                headingAngularDifferenceDeg(vh, p.headingDeg) <= FOLK_BURST_MAX_HEADING_DIFF_DEG
            else -> {
                val bearingToPhoto = bearingDegreesLatLon(viewerLat, viewerLon, p.latitude, p.longitude)
                headingAngularDifferenceDeg(vh, bearingToPhoto) <= FOLK_BURST_MAX_HEADING_DIFF_DEG
            }
        }
        if (!dirOk) return@mapNotNull null
        p to d
    }.sortedBy { it.second }
    return inRing.map { it.first }.take(SPOT_RING_MAX_RESULTS)
}
