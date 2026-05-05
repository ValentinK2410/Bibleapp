package com.example.bible.data.travel

/** Положение «точки» во время ускоренного просмотра сохранённого GPS-трека. */
data class TripHistoryReplayPose(
    val latitude: Double,
    val longitude: Double,
    val bearingDeg: Float,
    /** 0..1 вдоль выбранного временного интервала трека */
    val progress: Float,
)

/** Шкала скорости для расцветки трека и легенды: 1 км/ч … 250 км/ч. */
const val TRIP_TRACK_COLOR_SCALE_MIN_KMH = 1f
const val TRIP_TRACK_COLOR_SCALE_MAX_KMH = 250f

/** Оценка скорости на участке [a]→[b] для раскраски линии. */
fun tripTrackSegmentSpeedKmhForDisplay(a: TravelTripTrackPoint, b: TravelTripTrackPoint): Float {
    val dtMs = (b.timestampMs - a.timestampMs).coerceAtLeast(250L).toDouble()
    val dtSec = (dtMs / 1000.0).coerceAtLeast(1e-3)
    val distM = travelDistanceMeters(
        TravelGeoPoint(a.latitude, a.longitude),
        TravelGeoPoint(b.latitude, b.longitude),
    )
    val fromDelta = ((distM / dtSec) * 3.6).toFloat()
    val sensorKmh = (a.speedMps + b.speedMps) / 2f * 3.6f
    val v = kotlin.math.max(fromDelta, sensorKmh)
    return v.coerceIn(TRIP_TRACK_COLOR_SCALE_MIN_KMH, TRIP_TRACK_COLOR_SCALE_MAX_KMH)
}

/**
 * Цвет участка по скорости: медленно — краснее, быстрее — синее ([TRIP_TRACK_COLOR_SCALE_MIN_KMH] … [TRIP_TRACK_COLOR_SCALE_MAX_KMH] км/ч).
 */
fun tripTrackSpeedKmhToArgb(kmh: Float): Int {
    val span = TRIP_TRACK_COLOR_SCALE_MAX_KMH - TRIP_TRACK_COLOR_SCALE_MIN_KMH
    val t = ((kmh - TRIP_TRACK_COLOR_SCALE_MIN_KMH) / span).coerceIn(0f, 1f)
    val rSlow = 255
    val gSlow = 23
    val bSlow = 23
    val rFast = 41
    val gFast = 121
    val bFast = 255
    fun lerp(a: Int, b: Int, tt: Float) = (a + (b - a) * tt).toInt().coerceIn(0, 255)
    val r = lerp(rSlow, rFast, t)
    val g = lerp(gSlow, gFast, t)
    val b = lerp(bSlow, bFast, t)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Положение на треке по времени от начала: [elapsedTimelineMs] зажато в интервал между первой и последней точкой следа.
 */
fun interpolateTripHistoryReplayPose(track: List<TravelTripTrackPoint>, elapsedTimelineMs: Long): TripHistoryReplayPose? {
    val ordered = track.sortedBy { it.timestampMs }
    if (ordered.size < 2) return ordered.singleOrNull()?.let {
        TripHistoryReplayPose(it.latitude, it.longitude, 0f, 1f)
    }
    val tStart = ordered.first().timestampMs
    val tEnd = ordered.last().timestampMs
    val spanMs = (tEnd - tStart).coerceAtLeast(1L)
    val cappedElapsed = elapsedTimelineMs.coerceIn(0L, spanMs)
    val targetTs = tStart + cappedElapsed
    val prog = cappedElapsed.toFloat() / spanMs.toFloat()

    val iLast = ordered.lastIndex
    var idx = 0
    while (idx < iLast && ordered[idx + 1].timestampMs < targetTs) {
        idx++
    }
    val a = ordered[idx]
    val b = ordered[idx + 1]

    val segDt = (b.timestampMs - a.timestampMs).coerceAtLeast(1L)
    val uFloat = ((targetTs - a.timestampMs).toDouble() / segDt.toDouble()).coerceIn(0.0, 1.0)
    val lat = a.latitude + uFloat * (b.latitude - a.latitude)
    val lon = a.longitude + uFloat * (b.longitude - a.longitude)
    val bearing = bearingDegreesLatLon(a.latitude, a.longitude, b.latitude, b.longitude)
    return TripHistoryReplayPose(latitude = lat, longitude = lon, bearingDeg = bearing, progress = prog)
}
