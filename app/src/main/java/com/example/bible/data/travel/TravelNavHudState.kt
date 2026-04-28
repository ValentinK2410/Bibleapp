package com.example.bible.data.travel

import com.yandex.mapkit.geometry.Point

/**
 * Состояние «навигаторского» HUD: скорость, лимит и ближайшая подсказка вдоль маршрута.
 */
data class TravelNavHudState(
    val speedKmh: Int,
    /** Текущий лимит по данным маршрута; null, если неизвестен. */
    val speedLimitKmh: Int?,
    val isOverSpeedLimit: Boolean,
    val nextRoadNote: String?,
    val nextRoadNoteDistanceM: Int?,
)

enum class TravelHazardMapKind {
    SCHOOL,
    SPEED_BUMP,
    DIRECTION_SIGN,
}

data class TravelHazardMapItem(
    val point: Point,
    val label: String,
    val kind: TravelHazardMapKind,
)
