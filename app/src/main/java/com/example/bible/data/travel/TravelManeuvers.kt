package com.example.bible.data.travel

import com.yandex.mapkit.directions.driving.Action
import com.yandex.mapkit.directions.driving.Annotation
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.geometry.PolylinePosition
import com.yandex.mapkit.geometry.geo.PolylineUtils
import kotlin.math.abs

/**
 * Манёвры (повороты) и важные участки (перекрёстки) вдоль [DrivingRoute] для карты и озвучки.
 */
data class TravelManeuverInfo(
    val position: PolylinePosition,
    val shortPhrase: String,
    val fullPhrase: String,
    val isFinish: Boolean,
    /** Пешеходный/транспортный перекрёсток по подписи, не обязательно поворот. */
    val isCrossing: Boolean = false,
)

object TravelManeuvers {

    /**
     * Точки манёвров в порядке движения: повороты, развилки, прямые участки-перекрёстки, финиш.
     */
    fun buildList(route: DrivingRoute): List<TravelManeuverInfo> {
        val out = ArrayList<TravelManeuverInfo>()
        val seenAlong = ArrayList<Double>()

        fun alreadyNear(dist: Double): Boolean = seenAlong.any { abs(it - dist) < 12.0 }

        fun addFromSection(
            end: PolylinePosition,
            action: Action,
            ann: Annotation,
            dist: Double,
        ) {
            if (alreadyNear(dist)) return
            if (action == Action.UNKNOWN) return
            if (action == Action.STRAIGHT) return
            if (action == Action.WAYPOINT) return
            when (action) {
                Action.FINISH -> {
                    seenAlong.add(dist)
                    out.add(
                        TravelManeuverInfo(
                            position = end,
                            shortPhrase = shortFromAction(action),
                            fullPhrase = phraseFromAnnotation(ann, action),
                            isFinish = true,
                        ),
                    )
                }
                else -> {
                    seenAlong.add(dist)
                    out.add(
                        TravelManeuverInfo(
                            position = end,
                            shortPhrase = shortFromAction(action),
                            fullPhrase = phraseFromAnnotation(ann, action),
                            isFinish = false,
                        ),
                    )
                }
            }
        }

        for (section in route.sections.orEmpty()) {
                val meta = section.metadata ?: continue
                val ann = meta.annotation ?: continue
                val geom = section.geometry ?: continue
                val end = geom.end ?: continue
                val action = ann.action ?: Action.UNKNOWN
                val dStart = PolylinePosition(0, 0.0)
                val dist = PolylineUtils.distanceBetweenPolylinePositions(
                    route.geometry,
                    dStart,
                    end,
                )
                addFromSection(end, action, ann, dist)
        }

        // Перекрёстки и оживлённые участки: явные подсказки в тексте, даже если вектор = STRAIGHT
        for (section in route.sections.orEmpty()) {
                val meta = section.metadata ?: continue
                val ann = meta.annotation ?: continue
                val geom = section.geometry ?: continue
                val end = geom.end ?: continue
                val text = ann.descriptionText?.trim().orEmpty()
                val t = text.lowercase()
                val crossing = when {
                    t.contains("перекр") -> true
                    t.contains("пересеч") && (t.contains("ул") || t.contains("шоссе") || t.contains("дорог")) -> true
                    t.contains("светофор") -> true
                    t.contains("круг") && t.contains("движ") -> true
                    else -> false
                }
                if (!crossing) continue
                val dStart = PolylinePosition(0, 0.0)
                val dist = PolylineUtils.distanceBetweenPolylinePositions(
                    route.geometry,
                    dStart,
                    end,
                )
                if (alreadyNear(dist)) continue
                seenAlong.add(dist)
                val full = (ann.descriptionText?.trim() ?: "").ifEmpty { CROSSING_FULL }
            out.add(
                TravelManeuverInfo(
                    position = end,
                    shortPhrase = CROSSING_SHORT,
                    fullPhrase = full,
                    isFinish = false,
                    isCrossing = true,
                ),
            )
        }

        // Финиш, если в первом проходе не попал (иногда отдельная секция)
        val withFinish = out.any { it.isFinish }
        if (!withFinish) {
            val poly = route.geometry
            val pts = poly.points
            if (pts.size >= 2) {
                val lastSeg = pts.size - 2
                val end = PolylinePosition(lastSeg, 1.0)
                val dStart = PolylinePosition(0, 0.0)
                val dist = PolylineUtils.distanceBetweenPolylinePositions(poly, dStart, end)
                if (!alreadyNear(dist)) {
                    out.add(
                        TravelManeuverInfo(
                            position = end,
                            shortPhrase = "финиш",
                            fullPhrase = "Вы прибыли",
                            isFinish = true,
                        ),
                    )
                }
            }
        }

        return out.sortedBy {
            PolylineUtils.distanceBetweenPolylinePositions(
                route.geometry,
                PolylinePosition(0, 0.0),
                it.position,
            )
        }
    }

    fun pointOnRoute(polyline: Polyline, pos: PolylinePosition) =
        PolylineUtils.pointByPolylinePosition(polyline, pos)

    private const val CROSSING_SHORT = "перекрёсток"
    private const val CROSSING_FULL = "Перекрёсток"

    private fun phraseFromAnnotation(ann: Annotation, action: Action): String {
        val t = ann.descriptionText?.trim().orEmpty()
        if (t.isNotEmpty()) return t
        return shortFromAction(action)
    }

    private fun shortFromAction(action: Action): String = when (action) {
        Action.SLIGHT_LEFT -> "держитесь левее"
        Action.SLIGHT_RIGHT -> "держитесь правее"
        Action.LEFT -> "поверните налево"
        Action.RIGHT -> "поверните направо"
        Action.HARD_LEFT -> "резко налево"
        Action.HARD_RIGHT -> "резко направо"
        Action.FORK_LEFT -> "на развилке налево"
        Action.FORK_RIGHT -> "на развилке направо"
        Action.UTURN_LEFT -> "разворот налево"
        Action.UTURN_RIGHT -> "разворот направо"
        Action.ENTER_ROUNDABOUT -> "въезд на кольцо"
        Action.LEAVE_ROUNDABOUT -> "съезд с кольца"
        Action.BOARD_FERRY -> "паром"
        Action.LEAVE_FERRY -> "с парома"
        Action.EXIT_LEFT -> "съезд налево"
        Action.EXIT_RIGHT -> "съезд направо"
        Action.FINISH -> "конечная точка"
        else -> "манёвр"
    }
}
