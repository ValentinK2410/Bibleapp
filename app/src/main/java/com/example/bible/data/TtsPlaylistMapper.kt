package com.example.bible.data

/**
 * Куда продолжить озвучку после смены перевода.
 * [startIndex] — стих внутри [verses], с которого нужно начать.
 */
data class RemappedTtsPlaylist(
    val verses: List<BibleVerse>,
    val startIndex: Int,
)

/**
 * Переносит очередь TTS на стихи другого перевода.
 *
 * Номера, которых нет в целевой главе, пропускаются. Если якорного стиха нет,
 * берётся ближайший (при равенстве расстояния — следующий по тексту, чтобы чтение
 * шло вперёд). Если не совпал ни один номер выбранного отрывка, остаётся ближайший
 * стих, а при [continueToChapterEndIfUnmapped] — он и все стихи до конца главы.
 * Пустая глава даёт null: озвучку не с чего начинать.
 */
fun remapTtsPlaylist(
    selectedVerseNumbers: List<Int>,
    anchorVerse: Int,
    targetVerses: List<BibleVerse>,
    continueToChapterEndIfUnmapped: Boolean,
): RemappedTtsPlaylist? {
    if (targetVerses.isEmpty()) return null
    val wanted = anchorVerse.coerceAtLeast(1)
    val byNumber = LinkedHashMap<Int, BibleVerse>()
    for (verse in targetVerses) {
        byNumber.putIfAbsent(verse.number, verse)
    }
    val mapped = ArrayList<BibleVerse>()
    val seen = HashSet<Int>()
    for (number in selectedVerseNumbers) {
        val verse = byNumber[number] ?: continue
        if (seen.add(verse.number)) mapped.add(verse)
    }
    if (mapped.isNotEmpty()) {
        val start = startIndexInPlaylist(mapped, targetVerses, wanted)
        return RemappedTtsPlaylist(mapped, start.coerceIn(0, mapped.lastIndex))
    }
    val nearest = nearestVerseIndex(targetVerses, wanted)
    val tail = if (continueToChapterEndIfUnmapped) {
        targetVerses.subList(nearest, targetVerses.size).toList()
    } else {
        listOf(targetVerses[nearest])
    }
    if (tail.isEmpty()) return null
    return RemappedTtsPlaylist(tail, 0)
}

/**
 * Номер стиха подстрочника по плоскому индексу слова в главе.
 * Если индекс за пределами слов — последний стих главы.
 */
fun verseNumberForFlatInterlinearWord(verses: List<BibleVerse>, wordIndex: Int): Int {
    if (verses.isEmpty()) return 1
    var acc = 0
    for (verse in verses) {
        val count = verse.interlinearWords?.size ?: 0
        if (count > 0 && wordIndex < acc + count) return verse.number
        acc += count
    }
    return verses.last().number
}

internal fun nearestVerseIndex(verses: List<BibleVerse>, wanted: Int): Int {
    if (verses.isEmpty()) return 0
    val exact = verses.indexOfFirst { it.number == wanted }
    if (exact >= 0) return exact
    var best = 0
    var bestDist = Int.MAX_VALUE
    verses.forEachIndexed { index, verse ->
        val dist = kotlin.math.abs(verse.number - wanted)
        val bestIsBeforeAnchor = verses[best].number < wanted
        val candidateIsAtOrAfterAnchor = verse.number >= wanted
        if (dist < bestDist || (dist == bestDist && candidateIsAtOrAfterAnchor && bestIsBeforeAnchor)) {
            best = index
            bestDist = dist
        }
    }
    return best
}

private fun startIndexInPlaylist(
    playlist: List<BibleVerse>,
    chapter: List<BibleVerse>,
    anchor: Int,
): Int {
    val exact = playlist.indexOfFirst { it.number == anchor }
    if (exact >= 0) return exact
    val nearestNumber = chapter.getOrNull(nearestVerseIndex(chapter, anchor))?.number
    if (nearestNumber != null) {
        val inPlaylist = playlist.indexOfFirst { it.number == nearestNumber }
        if (inPlaylist >= 0) return inPlaylist
    }
    val forward = playlist.indexOfFirst { it.number >= anchor }
    if (forward >= 0) return forward
    return playlist.lastIndex.coerceAtLeast(0)
}
