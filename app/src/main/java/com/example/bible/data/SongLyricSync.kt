package com.example.bible.data

/** Старые метки без пути относятся к первой фонограмме. */
fun SongLyricCue.matchesAudio(audioPath: String, firstAudioPath: String?): Boolean {
    if (this.audioPath.isBlank()) return audioPath == firstAudioPath
    return this.audioPath == audioPath
}

fun lyricCuesForAudio(
    cues: List<SongLyricCue>,
    audioPath: String,
    firstAudioPath: String?,
): List<SongLyricCue> = cues.filter { it.matchesAudio(audioPath, firstAudioPath) }

/** Активная строка текста по позиции воспроизведения (последняя метка с timeMs ≤ position). */
fun currentLineIndexForSong(positionMs: Int, cues: List<SongLyricCue>): Int? {
    if (cues.isEmpty()) return null
    val pos = positionMs.toLong()
    val sorted = cues.sortedBy { it.timeMs }
    return sorted.lastOrNull { it.timeMs <= pos }?.lineIndex
}

fun mergeSongLyricCue(
    cues: List<SongLyricCue>,
    timeMs: Long,
    lineIndex: Int,
    audioPath: String,
    firstAudioPath: String?,
): List<SongLyricCue> {
    val kept = cues.filter { cue ->
        !(cue.lineIndex == lineIndex && cue.matchesAudio(audioPath, firstAudioPath))
    }
    return (kept + SongLyricCue(timeMs, lineIndex, audioPath)).sortedBy { it.timeMs }
}

fun retargetLyricCues(
    cues: List<SongLyricCue>,
    oldPath: String,
    newPath: String,
    wasFirst: Boolean,
): List<SongLyricCue> = cues.map { cue ->
    if (cue.audioPath == oldPath || (cue.audioPath.isBlank() && wasFirst)) cue.copy(audioPath = newPath)
    else cue
}

fun dropLyricCuesForAudio(
    cues: List<SongLyricCue>,
    path: String,
    wasFirst: Boolean,
): List<SongLyricCue> = cues.filter { cue ->
    val hit = cue.audioPath == path || (cue.audioPath.isBlank() && wasFirst)
    !hit
}
