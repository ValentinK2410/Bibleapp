package com.example.bible.ui

import com.example.bible.data.NoteScriptureLinks

/** Формат текста при копировании / отправке одного или нескольких стихов. */
object VerseShareText {

    fun format(
        bookName: String,
        chapter: Int,
        verseNumbers: Collection<Int>,
        verseTextsByNumber: Map<Int, String>,
    ): String {
        val sorted = verseNumbers.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val refPart = referenceLabel(bookName, chapter, sorted)
        if (sorted.size == 1) {
            val text = verseTextsByNumber[sorted.first()].orEmpty()
            return "$refPart\n$text"
        }
        val body = sorted.joinToString("\n\n") { n ->
            val text = verseTextsByNumber[n].orEmpty()
            if (text.isBlank()) "$n." else "$n. $text"
        }
        return "$refPart\n\n$body"
    }

    fun referenceLabel(bookName: String, chapter: Int, sorted: List<Int>): String {
        if (sorted.size == 1) return "$bookName $chapter:${sorted.first()}"
        if (isContiguous(sorted)) return "$bookName $chapter:${sorted.first()}-${sorted.last()}"
        return "$bookName $chapter:${sorted.joinToString(",")}"
    }

    /** Разбор поля диапазона (как в ссылке на озвучку): «5», «2-5», «2,4,6». */
    fun verseNumbersFromRangeSpec(
        startVerse: Int,
        rawSpec: String,
        chapterVerseCount: Int,
    ): Set<Int> {
        val maxV = chapterVerseCount.coerceAtLeast(startVerse)
        val raw = rawSpec.trim()
        val spec = when {
            raw.contains(',') || raw.contains('*') || raw.contains('-') ||
                raw.contains('–') || raw.contains('—') -> raw
            else -> {
                val end = raw.toIntOrNull() ?: startVerse
                "$startVerse-${end.coerceIn(startVerse, maxV)}"
            }
        }
        return NoteScriptureLinks.expandSegmentSpecToVerses(spec, maxV)
    }

    private fun isContiguous(sorted: List<Int>): Boolean {
        if (sorted.size <= 1) return true
        for (i in 1 until sorted.size) {
            if (sorted[i] != sorted[i - 1] + 1) return false
        }
        return true
    }
}
