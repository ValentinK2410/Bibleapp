package com.example.bible.data.travel

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
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
 * Озвучка манёвров, ограничений скорости, камер и опасных участков вдоль [DrivingRoute].
 * Обновления GPS через Fused Location; привязка к полилинии через [PolylineIndex].
 */
class TravelRouteGuidanceSession(
    context: Context,
    private val route: DrivingRoute,
    private val onHudState: ((TravelNavHudState) -> Unit)? = null,
) {
    private val app = context.applicationContext
    private val fused = LocationServices.getFusedLocationProviderClient(app)
    private val polyline: Polyline = route.geometry
    private val polylineIndex: PolylineIndex = PolylineUtils.createPolylineIndex(polyline)

    private val routeStart = PolylinePosition(0, 0.0)

    private val maneuvers: List<TravelManeuverInfo> = TravelManeuvers.buildList(route)
    private val speedCues: List<SpeedCue> = buildSpeedCues(route, polyline)
    private val cameraCues: List<CameraCue> = buildCameraCues(route)
    private val hazardCues: List<HazardCue> = buildHazardCues(route, polyline)

    private var maneuverIndex = 0
    private var announcedFarForCurrent = false
    private var announcedNearForCurrent = false
    private var maxDistanceAlong = 0.0
    private var lastSpeedLimitAnnounced: Int? = null
    private val announcedSpeedCueIndices = HashSet<Int>()
    private val announcedCameraFar = HashSet<String>()
    private val announcedCameraNear = HashSet<String>()
    private val announcedHazardFar = HashSet<String>()
    private val announcedHazardNear = HashSet<String>()
    private var prevGuidanceLoc: Location? = null

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
            val speedKmh = speedKmhFromLoc(loc)
            processManeuvers()
            processSpeedLimits()
            processCameras()
            processHazards(dAlong)
            pushHud(speedKmh, dAlong)
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

        private fun processHazards(dAlong: Double) {
            for (cue in hazardCues) {
                val dist = cue.distanceAlong - dAlong
                if (dist in HAZARD_NEAR..HAZARD_FAR && cue.id !in announcedHazardFar) {
                    announcedHazardFar.add(cue.id)
                    val m = roundMeters(dist.roundToInt().coerceAtLeast(1))
                    TravelVoicePrompter.speak(app, "Через $m — ${cue.voiceShort}")
                }
                if (dist in 1.0..<HAZARD_NEAR && cue.id !in announcedHazardNear) {
                    announcedHazardNear.add(cue.id)
                    TravelVoicePrompter.speak(app, cue.voiceNear)
                }
            }
        }

        private fun pushHud(speedKmh: Int, dAlong: Double) {
            val listener = onHudState ?: return
            val limit = currentSpeedLimitKmh(dAlong)
            val over = limit != null && speedKmh > limit + SPEED_OVER_MARGIN_KMH
            val next = hazardCues
                .filter { it.distanceAlong > dAlong + 12.0 }
                .minByOrNull { it.distanceAlong }
            val distNext = next?.let {
                (it.distanceAlong - dAlong).roundToInt().coerceAtLeast(1)
            }
            listener(
                TravelNavHudState(
                    speedKmh = speedKmh,
                    speedLimitKmh = limit,
                    isOverSpeedLimit = over,
                    nextRoadNote = next?.hudLabel,
                    nextRoadNoteDistanceM = if (next != null) distNext else null,
                ),
            )
        }

        private fun currentSpeedLimitKmh(dAlong: Double): Int? {
            var current: Int? = null
            for (cue in speedCuesSorted) {
                if (cue.distanceAlong <= dAlong + 1.5) {
                    current = cue.kmh
                } else {
                    break
                }
            }
            return current
        }
    }

    private val speedCuesSorted: List<SpeedCueIndexed> =
        speedCues.map {
            SpeedCueIndexed(
                position = it.position,
                kmh = it.kmh,
                distanceAlong = distanceAlong(it.position),
            )
        }.sortedBy { it.distanceAlong }

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

    private fun speedKmhFromLoc(loc: Location): Int {
        val mps = when {
            loc.hasSpeed() -> loc.speed.coerceAtLeast(0f)
            else -> {
                val prev = prevGuidanceLoc
                if (prev != null) {
                    val dtSec = (loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000_000f
                    if (dtSec > 0.04f) {
                        loc.distanceTo(prev) / dtSec
                    } else {
                        0f
                    }
                } else {
                    0f
                }
            }
        }
        prevGuidanceLoc = Location(loc)
        return (mps * 3.6f).roundToInt().coerceIn(0, 400)
    }

    private data class SpeedCue(
        val position: PolylinePosition,
        val kmh: Int,
    )

    private data class SpeedCueIndexed(
        val position: PolylinePosition,
        val kmh: Int,
        val distanceAlong: Double,
    )

    private data class CameraCue(
        val id: String,
        val position: PolylinePosition,
        val farPhrase: String,
        val nearPhrase: String,
    )

    private data class HazardCue(
        val id: String,
        val position: PolylinePosition,
        val distanceAlong: Double,
        val voiceShort: String,
        val voiceNear: String,
        val hudLabel: String,
    )

    companion object {
        private const val PREVIEW_METERS = 320.0
        private const val FAR_MIN = 95.0
        private const val NEAR_MAX = 72.0
        private const val ANNOUNCE_SPEED_METERS = 220.0
        private const val CAMERA_FAR = 380.0
        private const val CAMERA_NEAR = 95.0
        private const val HAZARD_FAR = 380.0
        private const val HAZARD_NEAR = 95.0
        private const val SPEED_OVER_MARGIN_KMH = 3
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

        private fun buildHazardCues(route: DrivingRoute, polyline: Polyline): List<HazardCue> {
            val start = PolylinePosition(0, 0.0)
            fun dist(pos: PolylinePosition) =
                PolylineUtils.distanceBetweenPolylinePositions(polyline, start, pos)

            val raw = ArrayList<HazardCue>()
            route.speedBumps?.forEachIndexed { i, bump ->
                val pos = bump.position ?: return@forEachIndexed
                raw.add(
                    HazardCue(
                        id = "bump_$i",
                        position = pos,
                        distanceAlong = dist(pos),
                        voiceShort = "лежачий полицейский",
                        voiceNear = "Лежачий полицейский",
                        hudLabel = "Лежачий полицейский",
                    ),
                )
            }
            route.directionSigns?.forEachIndexed { i, sign ->
                val pos = sign.position ?: return@forEachIndexed
                raw.add(
                    HazardCue(
                        id = "dirsign_$i",
                        position = pos,
                        distanceAlong = dist(pos),
                        voiceShort = "дорожный знак",
                        voiceNear = "Дорожный знак",
                        hudLabel = "Дорожный знак",
                    ),
                )
            }
            route.events?.forEachIndexed { idx, ev ->
                val pos = ev.polylinePosition ?: return@forEachIndexed
                val tags = ev.tags ?: emptyList()
                val desc = ev.descriptionText?.trim().orEmpty()
                when {
                    tags.contains(EventTag.SCHOOL) -> {
                        val label = if (desc.isNotEmpty()) desc else "Школа"
                        raw.add(
                            HazardCue(
                                id = "school_${ev.eventId}_$idx",
                                position = pos,
                                distanceAlong = dist(pos),
                                voiceShort = label.lowercase(),
                                voiceNear = label,
                                hudLabel = label,
                            ),
                        )
                    }
                    tags.contains(EventTag.PEDESTRIAN_DANGER) || tags.contains(EventTag.DANGER) -> {
                        val label = if (desc.isNotEmpty()) desc else "Опасный участок"
                        raw.add(
                            HazardCue(
                                id = "danger_${ev.eventId}_$idx",
                                position = pos,
                                distanceAlong = dist(pos),
                                voiceShort = label.lowercase(),
                                voiceNear = label,
                                hudLabel = label,
                            ),
                        )
                    }
                }
            }
            return dedupeHazards(raw.sortedBy { it.distanceAlong })
        }

        /** Сливаем объекты ближе 22 м по дистанции вдоль маршрута (одна озвучка/подпись). */
        private fun dedupeHazards(sorted: List<HazardCue>): List<HazardCue> {
            if (sorted.isEmpty()) return sorted
            val out = ArrayList<HazardCue>()
            var prev: HazardCue? = null
            for (h in sorted) {
                val p = prev
                if (p != null && h.distanceAlong - p.distanceAlong < 22.0) {
                    val merged = if (h.voiceNear.length >= p.voiceNear.length) h else p
                    out[out.lastIndex] = merged
                    prev = merged
                } else {
                    out.add(h)
                    prev = h
                }
            }
            return out
        }

        /** Точки для карты: школа, лежачий, знак. */
        fun hazardMapItems(route: DrivingRoute): List<TravelHazardMapItem> {
            val poly = route.geometry
            val items = ArrayList<TravelHazardMapItem>()
            route.speedBumps?.forEach { bump ->
                val pos = bump.position ?: return@forEach
                val pt = TravelManeuvers.pointOnRoute(poly, pos)
                items.add(
                    TravelHazardMapItem(
                        point = pt,
                        label = "Лежачий",
                        kind = TravelHazardMapKind.SPEED_BUMP,
                    ),
                )
            }
            route.directionSigns?.forEach { sign ->
                val pos = sign.position ?: return@forEach
                val pt = TravelManeuvers.pointOnRoute(poly, pos)
                items.add(
                    TravelHazardMapItem(
                        point = pt,
                        label = "Знак",
                        kind = TravelHazardMapKind.DIRECTION_SIGN,
                    ),
                )
            }
            route.events?.forEach { ev ->
                if (ev.tags?.contains(EventTag.SCHOOL) != true) return@forEach
                val pos = ev.polylinePosition ?: return@forEach
                val pt = TravelManeuvers.pointOnRoute(poly, pos)
                val label = ev.descriptionText?.trim()?.take(24)?.ifEmpty { null } ?: "Школа"
                items.add(
                    TravelHazardMapItem(
                        point = pt,
                        label = label,
                        kind = TravelHazardMapKind.SCHOOL,
                    ),
                )
            }
            return items
        }
    }
}
