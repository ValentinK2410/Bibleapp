package com.example.bible.data

import kotlin.math.max

/**
 * Примерный номер стиха для текущей позиции в одном MP3 файле главы.
 *
 * Точной разметки по времени в потоке нет — доля трека отображается пропорционально
 * длине текста каждого стиха (чем длиннее стих, тем больше ему «досталось» времени).
 * Реальное чтение диктором может заметно расходиться с этой оценкой.
 */
fun verseNumberAtChapterAudioPosition(
    verses: List<BibleVerse>,
    positionMs: Int,
    durationMs: Int,
): Int? {
    if (verses.isEmpty() || durationMs <= 0) return null
    val weights = verses.map { max(1, it.text.length) }
    val total = weights.sum().toDouble()
    if (total <= 0.0) return null
    val t = (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
    var cumulative = 0.0
    for (i in verses.indices) {
        cumulative += weights[i] / total
        if (t <= cumulative || i == verses.lastIndex) {
            return verses[i].number
        }
    }
    return verses.last().number
}
