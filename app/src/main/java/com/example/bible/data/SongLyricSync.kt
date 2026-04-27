package com.example.bible.data

/** Активная строка текста по позиции воспроизведения (последняя метка с timeMs ≤ position). */
fun currentLineIndexForSong(positionMs: Int, cues: List<SongLyricCue>): Int? {
    if (cues.isEmpty()) return null
    val pos = positionMs.toLong()
    val sorted = cues.sortedBy { it.timeMs }
    return sorted.lastOrNull { it.timeMs <= pos }?.lineIndex
}

fun mergeSongLyricCue(cues: List<SongLyricCue>, timeMs: Long, lineIndex: Int): List<SongLyricCue> {
    val without = cues.filter { it.lineIndex != lineIndex }
    return (without + SongLyricCue(timeMs, lineIndex)).sortedBy { it.timeMs }
}
