package com.example.bible.data

/**
 * По текущей позиции трека и отсортированным меткам возвращает диапазон стихов для подсветки.
 */
fun verseRangeForTimemarkPosition(positionMs: Long, cues: List<TimemarkCue>): IntRange? {
    if (cues.isEmpty()) return null
    val sorted = cues.sortedBy { it.timeMs }
    val active = sorted.lastOrNull { it.timeMs <= positionMs } ?: return null
    val end = active.verseEnd ?: active.verseStart
    return active.verseStart..end
}

fun activeTimemarkCue(positionMs: Long, cues: List<TimemarkCue>): TimemarkCue? {
    if (cues.isEmpty()) return null
    val sorted = cues.sortedBy { it.timeMs }
    return sorted.lastOrNull { it.timeMs <= positionMs }
}

fun formatTimemarkTimeMs(ms: Long): String {
    val s = (ms / 1000).toInt()
    val m = s / 60
    val r = s % 60
    return "%d:%02d".format(m, r)
}
