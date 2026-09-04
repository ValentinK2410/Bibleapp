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

/** Позиция трека (мс) для начала озвучки с указанного стиха по меткам проекта. */
fun timeMsForTimemarkVerse(verseNum: Int, cues: List<TimemarkCue>): Long? {
    if (cues.isEmpty() || verseNum < 1) return null
    val sorted = cues.sortedBy { it.timeMs }
    val containing = sorted.lastOrNull { cue ->
        val end = cue.verseEnd ?: cue.verseStart
        verseNum in cue.verseStart..end
    }
    if (containing != null) return containing.timeMs
    return sorted.lastOrNull { it.verseStart <= verseNum }?.timeMs
}

/** Стартовая позиция для читалки, если есть проект таймкодов для главы. */
fun timemarkPlaybackStartMs(project: TimemarkProject?, verseNum: Int): Int? {
    if (project == null || project.cues.isEmpty()) return null
    return timeMsForTimemarkVerse(verseNum, project.cues)?.toInt()
}

/**
 * Позиция (мс), на которой нужно остановить озвучку после [endVerse] (начало следующего стиха).
 * [chapterDurationMs] — запасной вариант для последнего стиха главы.
 */
fun timemarkPlaybackStopAfterMs(
    project: TimemarkProject?,
    endVerse: Int,
    chapterDurationMs: Int? = null,
): Int? {
    if (project == null || project.cues.isEmpty()) return null
    val sorted = project.cues.sortedBy { it.timeMs }
    val nextCue = sorted.firstOrNull { it.verseStart > endVerse }
    if (nextCue != null) return nextCue.timeMs.toInt()
    return chapterDurationMs?.takeIf { it > 0 }
}

/** Оценка начала стиха без таймкодов: равномерное распределение по главе. */
fun estimatedPlaybackStartMs(
    startVerse: Int,
    chapterVerseCount: Int,
    chapterDurationMs: Int,
): Int? {
    if (startVerse <= 1) return 0.takeIf { chapterDurationMs > 0 }
    if (chapterVerseCount <= 0 || chapterDurationMs <= 0) return null
    val fraction = (startVerse - 1).toFloat() / chapterVerseCount.toFloat()
    return (chapterDurationMs * fraction).toInt().coerceIn(0, chapterDurationMs)
}

/** Оценка конца стиха без таймкодов: равномерное распределение по главе. */
fun estimatedPlaybackStopAfterMs(
    endVerse: Int,
    chapterVerseCount: Int,
    chapterDurationMs: Int,
): Int? {
    if (endVerse < 1 || chapterVerseCount <= 0 || chapterDurationMs <= 0) return null
    val nextVerse = (endVerse + 1).coerceAtMost(chapterVerseCount)
    if (nextVerse > chapterVerseCount) return chapterDurationMs
    val fraction = nextVerse.toFloat() / chapterVerseCount.toFloat()
    return (chapterDurationMs * fraction).toInt().coerceIn(0, chapterDurationMs)
}

fun formatTimemarkTimeMs(ms: Long): String {
    val s = (ms / 1000).toInt()
    val m = s / 60
    val r = s % 60
    return "%d:%02d".format(m, r)
}
