package com.example.bible.data.genealogy

import com.example.bible.data.BibleBook
import com.example.bible.data.BibleCanon
import com.example.bible.data.BibleLibrary
import com.example.bible.data.TranslationId

/**
 * Разбор строк вида «Быт 5:1–5», «1 Цар 1–16», «Мф 1:16» и загрузка текста из выбранного перевода.
 */
object GenealogyScriptureParser {

    private val bookPrefixes: List<Pair<String, String>> by lazy {
        val m = linkedMapOf<String, String>()
        fun add(prefix: String, id: String) {
            val p = prefix.trim()
            if (p.isNotEmpty() && p !in m) m[p] = id
            val noSpace = p.replace(" ", "")
            if (noSpace != p && noSpace.isNotEmpty() && noSpace !in m) m[noSpace] = id
        }
        for (b in BibleCanon.allBooks) {
            add(b.abbrRu, b.id)
            add(b.nameRu, b.id)
        }
        // Частые сокращения в подстрочных ссылках
        add("Мф", "matthew")
        add("Мк", "mark")
        add("Лк", "luke")
        add("Ин", "john")
        add("1 Цар", "1_samuel")
        add("2 Цар", "2_samuel")
        add("3 Цар", "1_kings")
        add("4 Цар", "2_kings")
        add("1 Пар", "1_chronicles")
        add("2 Пар", "2_chronicles")
        m.entries.sortedByDescending { it.key.length }.map { it.toPair() }
    }

    data class BookMatch(
        val bookId: String,
        val rest: String,
        /** Префикс книги как в ссылке (для «; 21» → «Быт 21»). */
        val bookPrefix: String,
    )

    /** Сегменты ссылки (после разбиения по «;» и «·»). */
    fun splitRefSegments(line: String): List<String> {
        val raw = line.split(';', '·', '•').map { it.trim() }.filter { it.isNotEmpty() }
        if (raw.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        var pendingBookPrefix: String? = null
        for (seg in raw) {
            val matched = matchBook(seg)
            if (matched != null) {
                out.add(seg)
                pendingBookPrefix = matched.bookPrefix
            } else if (pendingBookPrefix != null && isContinuationSegment(seg)) {
                out.add("$pendingBookPrefix $seg")
            } else {
                out.add(seg)
            }
        }
        return out
    }

    private fun isContinuationSegment(s: String): Boolean {
        val t = s.trim().replace('–', '-')
        return t.matches(Regex("^\\d+.*")) || t.matches(Regex("^\\d+\\s*-\\s*\\d+.*"))
    }

    fun matchBook(segment: String): BookMatch? {
        val t = segment.trim()
        for ((prefix, bookId) in bookPrefixes) {
            val rest = stripPrefixIgnoringSpaces(t, prefix)
            if (rest != null) return BookMatch(bookId, rest, prefix.trim())
        }
        return null
    }

    /** @return bookId to remainder (глава/стихи) или null */
    fun matchBookPrefix(segment: String): Pair<String, String>? =
        matchBook(segment)?.let { it.bookId to it.rest }

    /** Совпадение префикса книги, пробелы в префиксе и в тексте игнорируются. */
    private fun stripPrefixIgnoringSpaces(text: String, prefix: String): String? {
        var ti = 0
        var pi = 0
        while (pi < prefix.length && ti < text.length) {
            if (text[ti].isWhitespace()) {
                ti++
                continue
            }
            if (prefix[pi].isWhitespace()) {
                pi++
                continue
            }
            if (!text[ti].equals(prefix[pi], ignoreCase = true)) return null
            ti++
            pi++
        }
        while (pi < prefix.length && prefix[pi].isWhitespace()) pi++
        if (pi < prefix.length) return null
        return text.substring(ti).trim()
    }

    sealed class ParsedNumbers {
        data class WholeChapter(val chapter: Int) : ParsedNumbers()
        data class ChapterRange(val from: Int, val to: Int) : ParsedNumbers()
        data class Verses(val chapter: Int, val from: Int, val to: Int) : ParsedNumbers()
    }

    fun parseNumbers(rest: String): ParsedNumbers? {
        val t = rest.trim().replace('–', '-').replace('—', '-')
        if (t.isEmpty()) return null
        val colon = t.indexOf(':')
        if (colon >= 0) {
            val ch = t.substring(0, colon).trim().toIntOrNull() ?: return null
            val after = t.substring(colon + 1).trim()
            val dash = after.indexOf('-')
            if (dash >= 0) {
                val v1 = after.substring(0, dash).trim().toIntOrNull() ?: return null
                val v2 = after.substring(dash + 1).trim().toIntOrNull() ?: return null
                return ParsedNumbers.Verses(ch, minOf(v1, v2), maxOf(v1, v2))
            }
            val v = after.toIntOrNull() ?: return null
            return ParsedNumbers.Verses(ch, v, v)
        }
        val dash = t.indexOf('-')
        if (dash >= 0) {
            val a = t.substring(0, dash).trim().toIntOrNull() ?: return null
            val b = t.substring(dash + 1).trim().toIntOrNull() ?: return null
            return ParsedNumbers.ChapterRange(minOf(a, b), maxOf(a, b))
        }
        val ch = t.toIntOrNull() ?: return null
        return ParsedNumbers.WholeChapter(ch)
    }

    data class ResolvedRef(
        val bookId: String,
        val displayLabel: String,
        val navigateChapter: Int,
        val navigateVerse: Int,
    )

    /**
     * Разбор одного сегмента (одна книга + глава/стихи).
     */
    fun parseSegment(segment: String): ResolvedRef? {
        val (bookId, rest) = matchBookPrefix(segment) ?: return null
        val nums = parseNumbers(rest) ?: return null
        val label = segment.trim()
        return when (nums) {
            is ParsedNumbers.WholeChapter ->
                ResolvedRef(bookId, label, nums.chapter, 1)
            is ParsedNumbers.ChapterRange ->
                ResolvedRef(bookId, label, nums.from, 1)
            is ParsedNumbers.Verses ->
                ResolvedRef(bookId, label, nums.chapter, nums.from)
        }
    }

    fun loadPassageText(
        library: BibleLibrary,
        translation: TranslationId,
        segment: String,
        maxChars: Int = 12_000,
    ): String? {
        val (bookId, rest) = matchBookPrefix(segment.trim()) ?: return null
        val nums = parseNumbers(rest) ?: return null
        val book = library.getBook(translation, bookId) ?: return null
        return when (nums) {
            is ParsedNumbers.WholeChapter -> textWholeChapter(book, nums.chapter, maxChars)
            is ParsedNumbers.ChapterRange -> textChapterRange(book, nums.from, nums.to, maxChars)
            is ParsedNumbers.Verses -> textVerseRange(book, nums.chapter, nums.from, nums.to, maxChars)
        }
    }

    private fun textWholeChapter(book: BibleBook, chapter: Int, maxChars: Int): String? {
        val ch = book.chapters.find { it.number == chapter } ?: return null
        return joinVerses(ch.verses.map { it.number to it.text }, maxChars)
    }

    private fun textChapterRange(book: BibleBook, from: Int, to: Int, maxChars: Int): String? {
        val sb = StringBuilder()
        for (cn in from..to) {
            val ch = book.chapters.find { it.number == cn } ?: continue
            if (sb.isNotEmpty()) sb.append("\n\n— Глава $cn —\n\n")
            sb.append(joinVerses(ch.verses.map { it.number to it.text }, maxChars - sb.length))
            if (sb.length >= maxChars) break
        }
        return sb.toString().ifBlank { null }
    }

    private fun textVerseRange(book: BibleBook, chapter: Int, from: Int, to: Int, maxChars: Int): String? {
        val ch = book.chapters.find { it.number == chapter } ?: return null
        val verses = ch.verses.filter { it.number in from..to }.sortedBy { it.number }
        if (verses.isEmpty()) return null
        return joinVerses(verses.map { it.number to it.text }, maxChars)
    }

    private fun joinVerses(pairs: List<Pair<Int, String>>, maxChars: Int): String {
        val sb = StringBuilder()
        for ((n, text) in pairs) {
            val line = "$n $text\n"
            if (sb.length + line.length > maxChars) {
                sb.append("…")
                break
            }
            sb.append(line)
        }
        return sb.toString().trim()
    }
}
