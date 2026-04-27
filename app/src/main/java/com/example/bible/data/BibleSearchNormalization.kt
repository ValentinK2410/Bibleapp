package com.example.bible.data

import com.example.bible.ui.SearchScope
import com.example.bible.ui.SearchSettings
import java.text.Normalizer
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

private fun List<IntRange>.mergeOverlappingRanges(): List<IntRange> {
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
 * Диапазоны в [text] для подсветки по [query] с учётом [SearchSettings].
 * Любая ошибка regex/границ (редкие символы, движок на устройстве) — пустой список, без падения UI.
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
    if (settings.ignoreSeparators) {
        val tokens = searchQueryTokensForHighlight(query, settings)
        if (tokens.isEmpty()) return emptyList()
        return tokens
            .flatMap { tok ->
                runCatching {
                    wordBoundaryRegexForToken(tok, settings).findAll(text).map { it.range }.toList()
                }.getOrElse { emptyList() }
            }
            .mergeOverlappingRanges()
    }
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val ranges = ArrayList<IntRange>(8)
    if (!settings.caseSensitive) {
        Regex(Regex.escape(needle), RegexOption.IGNORE_CASE)
            .findAll(text)
            .forEach { ranges.add(it.range) }
    } else {
        var idx = 0
        while (idx < text.length) {
            val found = text.indexOf(needle, idx)
            if (found < 0) break
            ranges.add(found until found + needle.length)
            idx = found + needle.length
        }
    }
    return ranges.mergeOverlappingRanges()
}
