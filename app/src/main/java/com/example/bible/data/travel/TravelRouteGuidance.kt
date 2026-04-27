package com.example.bible.data.travel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.geometry.PolylinePosition
import com.yandex.mapkit.geometry.geo.PolylineIndex
import com.yandex.mapkit.geometry.geo.PolylineUtils
import com.yandex.mapkit.road_events.EventTag
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Озвучка манёвров, ограничений скорости и камер вдоль построенного [DrivingRoute].
 * Обновления GPS через Fused Location; привязка к полилинии через [PolylineIndex].
 */
class TravelRouteGuidanceSession(
    context: Context,
    private val route: DrivingRoute,
) {
    private val app = context.applicationContext
    private val fused = LocationServices.getFusedLocationProviderClient(app)
    private val polyline: Polyline = route.geometry
    private val polylineIndex: PolylineIndex = PolylineUtils.createPolylineIndex(polyline)

    private val routeStart = PolylinePosition(0, 0.0)

    private val maneuvers: List<TravelManeuverInfo> = TravelManeuvers.buildList(route)
    private val speedCues: List<SpeedCue> = buildSpeedCues(route, polyline)
    private val cameraCues: List<CameraCue> = buildCameraCues(route)

    private var maneuverIndex = 0
    private var announcedFarForCurrent = false
    private var announcedNearForCurrent = false
    private var maxDistanceAlong = 0.0
    private var lastSpeedLimitAnnounced: Int? = null
    private val announcedSpeedCueIndices = HashSet<Int>()
    private val announcedCameraFar = HashSet<String>()
    private val announcedCameraNear = HashSet<String>()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val raw = Point(loc.latitude, loc.longitude)
            val snapped = polylineIndex.closestPolylinePosition(
                raw,
                PolylineIndex.Priority.CLOSEST_TO_RAW_POINT,
                80.0,
            ) ?: return
            val dAlong = distanceAlong(snapped)
            if (dAlong + 35.0 >= maxDistanceAlong) {
                maxDistanceAlong = max(maxDistanceAlong, dAlong)
            }
            processManeuvers()
            processSpeedLimits()
            processCameras()
        }

        private fun processManeuvers() {
            while (maneuverIndex < maneuvers.size) {
                val cue = maneuvers[maneuverIndex]
                val dTarget = distanceAlong(cue.position)
                val remaining = dTarget - maxDistanceAlong
                if (remaining <= -25.0) {
                    maneuverIndex++
                    announcedFarForCurrent = false
                    announcedNearForCurrent = false
                    continue
                }
                if (remaining > PREVIEW_METERS) return
                if (remaining in FAR_MIN..PREVIEW_METERS && !announcedFarForCurrent) {
                    announcedFarForCurrent = true
                    val m = roundMeters(remaining.roundToInt())
                    val prefix = if (cue.isCrossing) "Внимание! " else ""
                    TravelVoicePrompter.speak(
                        app,
                        "$prefix Через $m — ${cue.shortPhrase}",
                    )
                }
                if (remaining in 1.0..NEAR_MAX && !announcedNearForCurrent) {
                    announcedNearForCurrent = true
                    val near = if (cue.isCrossing) "Перекрёсток. ${cue.fullPhrase}" else cue.fullPhrase
                    TravelVoicePrompter.speak(app, near)
                }
                return
            }
        }

        private fun processSpeedLimits() {
            for ((idx, cue) in speedCues.withIndex()) {
                if (idx in announcedSpeedCueIndices) continue
                val dCue = distanceAlong(cue.position)
                val dist = dCue - maxDistanceAlong
                if (dist in -40.0..ANNOUNCE_SPEED_METERS) {
                    announcedSpeedCueIndices.add(idx)
                    if (cue.kmh != lastSpeedLimitAnnounced) {
                        lastSpeedLimitAnnounced = cue.kmh
                        TravelVoicePrompter.speak(
                            app,
                            "Ограничение скорости ${cue.kmh} километров в час",
                        )
                    }
                }
            }
        }

        private fun processCameras() {
            for (cue in cameraCues) {
                val dCue = distanceAlong(cue.position)
                val dist = dCue - maxDistanceAlong
                if (dist in CAMERA_NEAR..CAMERA_FAR && cue.id !in announcedCameraFar) {
                    announcedCameraFar.add(cue.id)
                    TravelVoicePrompter.speak(app, cue.farPhrase)
                }
                if (dist in 1.0..<CAMERA_NEAR && cue.id !in announcedCameraNear) {
                    announcedCameraNear.add(cue.id)
                    TravelVoicePrompter.speak(app, cue.nearPhrase)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_MS,
        )
            .setMinUpdateIntervalMillis(UPDATE_MS)
            .setMaxUpdateDelayMillis(100L)
            .build()
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    fun stop() {
        fused.removeLocationUpdates(callback)
    }

    private fun distanceAlong(pos: PolylinePosition): Double =
        PolylineUtils.distanceBetweenPolylinePositions(polyline, routeStart, pos)

    private data class SpeedCue(
        val position: PolylinePosition,
        val kmh: Int,
    )

    private data class CameraCue(
        val id: String,
        val position: PolylinePosition,
        val farPhrase: String,
        val nearPhrase: String,
    )

    companion object {
        private const val PREVIEW_METERS = 320.0
        private const val FAR_MIN = 95.0
        private const val NEAR_MAX = 72.0
        private const val ANNOUNCE_SPEED_METERS = 220.0
        private const val CAMERA_FAR = 380.0
        private const val CAMERA_NEAR = 95.0
        /** 10–20 Гц, чтобы дистанция/привязка к треку шли плавно вместе с картой. */
        private const val UPDATE_MS = 50L

        private fun roundMeters(m: Int): String = when {
            m >= 950 -> "около километра"
            m >= 750 -> "около восьмисот метров"
            m >= 550 -> "около шестисот метров"
            m >= 450 -> "около пятисот метров"
            m >= 350 -> "около трёхсот метров"
            m >= 250 -> "около трёхсот метров"
            else -> "$m метров"
        }

        private fun buildSpeedCues(route: DrivingRoute, polyline: Polyline): List<SpeedCue> {
            val out = ArrayList<SpeedCue>()
            val pts = polyline.points ?: return out
            if (pts.size < 2) return out
            val limits = route.speedLimits
            if (limits.isNotEmpty()) {
                when {
                    limits.size == pts.size - 1 -> {
                        limits.forEachIndexed { i, lim ->
                            val v = lim.toInt().coerceAtLeast(0)
                            if (v > 0) {
                                out.add(SpeedCue(PolylinePosition(i, 0.0), v))
                            }
                        }
                    }
                    limits.size == pts.size -> {
                        limits.forEachIndexed { i, lim ->
                            val v = lim.toInt().coerceAtLeast(0)
                            if (v > 0) {
                                out.add(SpeedCue(PolylinePosition(max(0, i - 1), 0.0), v))
                            }
                        }
                    }
                    else -> {
                        val step = (pts.size - 1).toDouble() / limits.size.coerceAtLeast(1)
                        limits.forEachIndexed { idx, lim ->
                            val v = lim.toInt().coerceAtLeast(0)
                            if (v > 0) {
                                val seg = (idx * step).toInt().coerceIn(0, pts.size - 2)
                                out.add(SpeedCue(PolylinePosition(seg, 0.0), v))
                            }
                        }
                    }
                }
            }
            route.events?.forEach { ev ->
                val sl = ev.speedLimit ?: return@forEach
                val v = sl.toInt().coerceAtLeast(0)
                if (v <= 0) return@forEach
                val pos = ev.polylinePosition ?: return@forEach
                out.add(SpeedCue(pos, v))
            }
            return out.distinctBy { Triple(it.position.segmentIndex, (it.position.segmentPosition * 1000).roundToInt(), it.kmh) }
                .sortedBy { PolylineUtils.distanceBetweenPolylinePositions(polyline, PolylinePosition(0, 0.0), it.position) }
        }

        private fun buildCameraCues(route: DrivingRoute): List<CameraCue> {
            val tagsCamera = setOf(
                EventTag.SPEED_CONTROL,
                EventTag.MOBILE_CONTROL,
                EventTag.LANE_CONTROL,
                EventTag.ROAD_MARKING_CONTROL,
                EventTag.CROSS_ROAD_CONTROL,
                EventTag.NO_STOPPING_CONTROL,
            )
            val out = ArrayList<CameraCue>()
            route.events?.forEachIndexed { idx, ev ->
                val pos = ev.polylinePosition ?: return@forEachIndexed
                val tags = ev.tags ?: emptyList()
                if (tags.none { it in tagsCamera }) return@forEachIndexed
                val kind = when {
                    tags.contains(EventTag.SPEED_CONTROL) -> "камера контроля скорости"
                    tags.contains(EventTag.MOBILE_CONTROL) -> "мобильный контроль скорости"
                    tags.contains(EventTag.LANE_CONTROL) -> "контроль полосы"
                    else -> "дорожный контроль"
                }
                val desc = ev.descriptionText?.trim().orEmpty()
                val far = if (desc.isNotEmpty()) "Впереди $kind. $desc" else "Впереди $kind"
                val near = if (desc.isNotEmpty()) "$kind. $desc" else kind
                out.add(
                    CameraCue(
                        id = ev.eventId?.takeIf { it.isNotBlank() } ?: "ev_$idx",
                        position = pos,
                        farPhrase = far,
                        nearPhrase = near,
                    ),
                )
            }
            route.checkpoints?.forEachIndexed { idx, cp ->
                val pos = cp.position ?: return@forEachIndexed
                out.add(
                    CameraCue(
                        id = "cp_$idx",
                        position = pos,
                        farPhrase = "Впереди контрольный пункт",
                        nearPhrase = "Контрольный пункт",
                    ),
                )
            }
            return out
        }
    }
}
