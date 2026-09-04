package com.example.bible.data

import com.example.bible.ui.SearchScope
import com.example.bible.ui.SearchSettings
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import android.util.Log

private const val TAG_SEARCH_HIGHLIGHT = "BibleSearchHighlight"

internal val BibleSearchWhitespaceSplit = Regex("\\s+")

/**
 * Настройки, с которыми в БД хранится [com.example.bible.data.db.BibleVerseEntity.searchNorm]
 * и строится быстрый FTS5-поиск (должны совпадать с [canUseFtsFastPath]).
 */
val BibleVerseSearchNormSettings = SearchSettings(
    wholeWords = false,
    orderedWords = false,
    ignoreSeparators = true,
    accentSensitive = false,
    caseSensitive = false,
    punctuationSensitive = false,
    highlightMatches = true,
    scope = SearchScope.ALL,
    singleBookId = null,
)

/** Нормализация текста стиха для индекса FTS (тот же алгоритм, что и при типичном поиске). */
fun verseSearchNormForStored(text: String): String =
    normalizeVerseForCompare(text, BibleVerseSearchNormSettings)

/** Ключ для дедупликации записей в истории поиска по Библии (совпадает с нормализацией индекса [searchNorm]). */
fun bibleSearchHistoryDedupKey(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return ""
    val k = normalizeSearchQueryForCompare(t, BibleVerseSearchNormSettings)
    if (k.isNotEmpty()) return k
    return t.lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("\\s+"), " ")
        .trim()
}

/** Быстрый путь FTS5 даёт те же результаты, что и полный перебор, только при этих флагах. */
fun canUseFtsFastPath(settings: SearchSettings): Boolean =
    !settings.wholeWords &&
        !settings.orderedWords &&
        settings.ignoreSeparators &&
        !settings.accentSensitive &&
        !settings.caseSensitive &&
        !settings.punctuationSensitive

internal fun stripAccentsForSearch(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace("\\p{M}".toRegex(), "")

internal fun normalizeTypographicQuotesForSearch(s: String): String =
    s.replace('\u2014', '-')
        .replace('\u2013', '-')
        .replace('\u2012', '-')
        .replace('\u00AB', '"')
        .replace('\u00BB', '"')
        .replace('\u201C', '"')
        .replace('\u201D', '"')
        .replace('\u201E', '"')
        .replace('\u2018', '\'')
        .replace('\u2019', '\'')

/** Всё, что не буква, цифра или диакритический знак, заменяется пробелами. */
internal fun stripNonLetterNumberRuns(s: String): String =
    Regex("[^\\p{L}\\p{M}\\p{N}]+").replace(s, " ").trim()

/**
 * Нормализация строки запроса для сравнения при поиске (с [trim]).
 */
fun normalizeSearchQueryForCompare(raw: String, settings: SearchSettings): String {
    var q = raw.trim()
    if (q.isEmpty()) return ""
    if (!settings.caseSensitive) q = q.lowercase()
    if (!settings.accentSensitive) q = stripAccentsForSearch(q)
    return when {
        settings.ignoreSeparators -> stripNonLetterNumberRuns(q)
        !settings.punctuationSensitive -> normalizeTypographicQuotesForSearch(q)
        else -> q
    }
}

/**
 * Нормализация текста стиха для сравнения при поиске.
 */
fun normalizeVerseForCompare(text: String, settings: SearchSettings): String {
    var t = text
    if (!settings.caseSensitive) t = t.lowercase()
    if (!settings.accentSensitive) t = stripAccentsForSearch(t)
    return when {
        settings.ignoreSeparators -> stripNonLetterNumberRuns(t)
        !settings.punctuationSensitive -> normalizeTypographicQuotesForSearch(t)
        else -> t
    }
}

fun searchQueryTokensForHighlight(raw: String, settings: SearchSettings): List<String> {
    val n = normalizeSearchQueryForCompare(raw, settings)
    if (n.isEmpty()) return emptyList()
    return n.split(BibleSearchWhitespaceSplit).filter { it.isNotEmpty() }
}

fun wordBoundaryRegexForToken(token: String, settings: SearchSettings): Regex {
    val core =
        if (!settings.accentSensitive) {
            buildString {
                var i = 0
                while (i < token.length) {
                    val cp = token.codePointAt(i)
                    append(Regex.escape(String(Character.toChars(cp))))
                    append("\\p{M}*")
                    i += Character.charCount(cp)
                }
            }
        } else {
            Regex.escape(token)
        }
    val pat = "(?U)(?<![\\p{L}\\p{M}\\p{N}])$core(?![\\p{L}\\p{M}\\p{N}])"
    val opts = if (!settings.caseSensitive) setOf(RegexOption.IGNORE_CASE) else emptySet()
    return Regex(pat, opts)
}

internal fun List<IntRange>.mergeOverlappingRanges(): List<IntRange> {
    if (isEmpty()) return emptyList()
    val sorted = sortedBy { it.first }
    val out = ArrayList<IntRange>(sorted.size)
    var cur = sorted[0]
    for (i in 1 until sorted.size) {
        val n = sorted[i]
        if (n.first <= cur.last + 1) {
            cur = cur.first..max(cur.last, n.last)
        } else {
            out.add(cur)
            cur = n
        }
    }
    out.add(cur)
    return out
}

/**
 * Тот же разбор запроса, что и в [BibleLibrary] перед сравнением с нормализованным стихом.
 */
internal data class BiblePreparedSearchQuery(
    val needle: String,
    val wholeWordTokens: List<String>?,
    val orderedParts: List<String>?,
)

internal fun prepareBibleSearchQuery(raw: String, settings: SearchSettings): BiblePreparedSearchQuery? {
    val q = normalizeSearchQueryForCompare(raw, settings)
    if (q.isEmpty()) return null
    val wholeWordTokens = if (settings.wholeWords) {
        q.split(BibleSearchWhitespaceSplit).filter { it.isNotEmpty() }
    } else {
        null
    }
    val orderedParts = if (settings.orderedWords && !settings.wholeWords) {
        q.split(BibleSearchWhitespaceSplit).filter { it.isNotEmpty() }
    } else {
        null
    }
    return BiblePreparedSearchQuery(
        needle = q,
        wholeWordTokens = wholeWordTokens,
        orderedParts = orderedParts,
    )
}

internal fun findWholeWordInNormalizedText(text: String, word: String, startIndex: Int): Int {
    var idx = startIndex
    while (true) {
        val found = text.indexOf(word, idx)
        if (found < 0) return -1
        val before = found == 0 || !text[found - 1].isLetterOrDigit()
        val after = (found + word.length) >= text.length || !text[found + word.length].isLetterOrDigit()
        if (before && after) return found
        idx = found + 1
    }
}

/**
 * Нормализованная строка (как при поиске) и сопоставление каждого символа нормализации
 * началу соответствующего фрагмента в исходном [text] — для переноса диапазонов подсветки в оригинал.
 */
internal data class VerseNormAlignment(
    val normalized: String,
    val origStartPerNormIndex: IntArray,
)

private data class CharOrig(val ch: Char, val orig: Int)

internal fun buildVerseNormAlignment(text: String, settings: SearchSettings): VerseNormAlignment {
    val intermediates = ArrayList<CharOrig>()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val w = Character.charCount(cp)
        val origStart = i
        var s = String(Character.toChars(cp))
        if (!settings.caseSensitive) s = s.lowercase(Locale.ROOT)
        if (!settings.accentSensitive) s = stripAccentsForSearch(s)
        if (!settings.ignoreSeparators && !settings.punctuationSensitive) {
            s = normalizeTypographicQuotesForSearch(s)
        }
        if (s.isNotEmpty()) {
            for (c in s) {
                intermediates.add(CharOrig(c, origStart))
            }
        }
        i += w
    }
    if (settings.ignoreSeparators) {
        val sb = StringBuilder()
        val starts = ArrayList<Int>()
        var word = ArrayList<CharOrig>()
        var pendingSepOrig: Int? = null
        fun flushWord() {
            if (word.isEmpty()) return
            if (sb.isNotEmpty()) {
                sb.append(' ')
                starts.add(pendingSepOrig ?: word[0].orig)
                pendingSepOrig = null
            }
            for (co in word) {
                sb.append(co.ch)
                starts.add(co.orig)
            }
            word = ArrayList()
        }
        for (co in intermediates) {
            if (co.ch.isLetterOrDigit()) {
                word.add(co)
            } else {
                flushWord()
                pendingSepOrig = co.orig
            }
        }
        flushWord()
        var norm = sb.toString()
        val trimStart = norm.indexOfFirst { !it.isWhitespace() }.let { t -> if (t < 0) norm.length else t }
        val trimEnd = norm.indexOfLast { !it.isWhitespace() }.let { t -> if (t < 0) -1 else t }
        if (trimStart > trimEnd) {
            return VerseNormAlignment("", intArrayOf())
        }
        norm = norm.substring(trimStart, trimEnd + 1)
        val trimmedStarts = starts.subList(trimStart, trimEnd + 1).toIntArray()
        return VerseNormAlignment(norm, trimmedStarts)
    }
    val sb = StringBuilder()
    val starts = ArrayList<Int>()
    for (co in intermediates) {
        sb.append(co.ch)
        starts.add(co.orig)
    }
    return VerseNormAlignment(sb.toString(), starts.toIntArray())
}

internal fun findNormHighlightRanges(
    norm: String,
    pq: BiblePreparedSearchQuery,
    settings: SearchSettings,
): List<IntRange> {
    pq.wholeWordTokens?.let { words ->
        if (words.isEmpty()) return emptyList()
        if (settings.orderedWords) {
            val ranges = ArrayList<IntRange>()
            var searchFrom = 0
            for (word in words) {
                val idx = findWholeWordInNormalizedText(norm, word, searchFrom)
                if (idx < 0) return emptyList()
                ranges.add(idx until idx + word.length)
                searchFrom = idx + word.length
            }
            return ranges.mergeOverlappingRanges()
        }
        val ranges = ArrayList<IntRange>()
        for (word in words) {
            var start = 0
            while (true) {
                val idx = findWholeWordInNormalizedText(norm, word, start)
                if (idx < 0) break
                ranges.add(idx until idx + word.length)
                start = idx + 1
            }
        }
        return ranges.mergeOverlappingRanges()
    }
    pq.orderedParts?.let { parts ->
        val ranges = ArrayList<IntRange>()
        var searchFrom = 0
        for (part in parts) {
            val idx = norm.indexOf(part, searchFrom)
            if (idx < 0) return emptyList()
            ranges.add(idx until idx + part.length)
            searchFrom = idx + part.length
        }
        return ranges.mergeOverlappingRanges()
    }
    val needle = pq.needle
    if (needle.isEmpty()) return emptyList()
    val ranges = ArrayList<IntRange>()
    var idx = 0
    while (idx <= norm.length - needle.length) {
        val found = norm.indexOf(needle, idx)
        if (found < 0) break
        ranges.add(found until found + needle.length)
        idx = found + 1
    }
    return ranges.mergeOverlappingRanges()
}

private fun mapNormRangesToOriginal(
    ranges: List<IntRange>,
    alignment: VerseNormAlignment,
    text: String,
): List<IntRange> {
    if (ranges.isEmpty()) return emptyList()
    val n = alignment.normalized.length
    if (n == 0) return emptyList()
    val textLength = text.length
    val s = alignment.origStartPerNormIndex
    fun origCharEndExclusive(normIndex: Int): Int {
        val origStart = s[normIndex].coerceIn(0, textLength)
        if (origStart >= textLength) return textLength
        val cp = text.codePointAt(origStart)
        return (origStart + Character.charCount(cp)).coerceAtMost(textLength)
    }
    return ranges.map { r ->
        val a = r.first.coerceIn(0, n - 1)
        val b = r.last.coerceIn(0, n - 1)
        val start = s[a].coerceIn(0, textLength)
        val endExcl = origCharEndExclusive(b).coerceIn(start, textLength)
        start until endExcl
    }.mergeOverlappingRanges()
}

/**
 * Диапазоны в [text] для подсветки по [query] с учётом [SearchSettings].
 * Совпадает с правилами совпадения в [BibleLibrary] (нормализация + целые слова / порядок / подстрока).
 */
fun computeSearchHighlightRanges(text: String, query: String, settings: SearchSettings): List<IntRange> =
    runCatching { computeSearchHighlightRangesImpl(text, query, settings) }
        .onFailure { e -> Log.w(TAG_SEARCH_HIGHLIGHT, "highlight ranges failed", e) }
        .getOrElse { emptyList() }

private fun computeSearchHighlightRangesImpl(
    text: String,
    query: String,
    settings: SearchSettings,
): List<IntRange> {
    if (query.isBlank() || !settings.highlightMatches) return emptyList()
    val pq = prepareBibleSearchQuery(query, settings) ?: return emptyList()
    val alignment = buildVerseNormAlignment(text, settings)
    if (alignment.normalized.isEmpty()) return emptyList()
    val normRanges = findNormHighlightRanges(alignment.normalized, pq, settings)
    if (normRanges.isEmpty()) return emptyList()
    return mapNormRangesToOriginal(normRanges, alignment, text)
}
