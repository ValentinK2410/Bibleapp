package com.example.bible.data

enum class DeepSeekPassageScope {
    VERSE,
    RANGE,
    CHAPTER,
    BOOK,
}

object DeepSeekPassageFormatter {
    /** Запас по символам: глава почти всегда целиком, книга — пока влезает. */
    const val MAX_CHARS = 72_000

    fun versesBlock(verses: List<BibleVerse>): String =
        verses.joinToString("\n") { "${it.number}. ${it.text.trim()}" }

    fun fromMap(texts: Map<Int, String>, start: Int, end: Int): List<BibleVerse> =
        texts.entries
            .filter { it.key in start..end && it.value.isNotBlank() }
            .sortedBy { it.key }
            .map { BibleVerse(it.key, it.value) }

    fun chapterBlock(chapter: Int, verses: List<BibleVerse>): String =
        buildString {
            append("Глава $chapter\n")
            append(versesBlock(verses))
        }

    fun bookBlock(book: BibleBook): Pair<String, Boolean> {
        val chapters = book.chapters.sortedBy { it.number }
        val lastChapter = chapters.lastOrNull()?.number ?: 0
        val sb = StringBuilder()
        var truncated = false
        for (ch in chapters) {
            val full = "\n\n" + chapterBlock(ch.number, ch.verses)
            if (sb.length + full.length <= MAX_CHARS) {
                sb.append(full)
                continue
            }
            val abbr = "\n\n" + abbreviateChapter(ch)
            if (sb.length + abbr.length <= MAX_CHARS) {
                truncated = true
                sb.append(abbr)
                continue
            }
            truncated = true
            sb.append("\n\n[Текст глав ${ch.number}–$lastChapter сокращён из‑за длины книги.]")
            break
        }
        return sb.toString().trim() to truncated
    }

    private fun abbreviateChapter(ch: BibleChapter): String {
        val verses = ch.verses.sortedBy { it.number }
        if (verses.size <= 4) return chapterBlock(ch.number, verses)
        val keep = (verses.take(2) + verses.takeLast(1)).distinctBy { it.number }
        return buildString {
            append("Глава ${ch.number} (сокращённо, всего ${verses.size} стихов)\n")
            append(versesBlock(keep))
            append("\n…")
        }
    }
}
