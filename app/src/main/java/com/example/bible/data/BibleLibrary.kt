package com.example.bible.data

import com.example.bible.data.db.BibleVerseSearchRow
import com.example.bible.ui.SearchScope
import com.example.bible.ui.SearchSettings

class BibleLibrary(
    private val repository: BibleRepository,
) {
    private val legacy: Map<TranslationId, BibleData>? = repository.legacyDataIfMissing()
    private val cache = mutableMapOf<Pair<TranslationId, String>, BibleBook?>()

    fun getBook(translation: TranslationId, bookId: String): BibleBook? {
        val key = translation to bookId
        cache[key]?.let { return it }
        if (repository.hasBookAsset(translation, bookId)) {
            val book = repository.loadBook(translation, bookId) ?: run {
                cache[key] = null
                return null
            }
            cache[key] = book
            return book
        }
        legacy?.get(translation)?.books?.find { it.id == bookId }?.let { return it }
        return null
    }

    fun isOnlineOnly(translation: TranslationId, bookId: String): Boolean {
        if (cache.containsKey(translation to bookId)) return false
        if (repository.hasBookAsset(translation, bookId)) return false
        if (legacy?.get(translation)?.books?.any { it.id == bookId } == true) return false
        return translation.onlineCode != null
    }

    fun putOnlineBook(translation: TranslationId, book: BibleBook) {
        cache[translation to book.id] = book
    }

    fun invalidate() {
        cache.clear()
    }

    /** Переводы, по которым есть локальные тексты (assets или встроенный legacy). */
    fun translationIdsWithLocalText(): List<TranslationId> =
        TranslationId.entries.filter { tid ->
            repository.hasTranslationFolder(tid) ||
                legacy?.containsKey(tid) == true ||
                BibleCanon.allBooks.any { repository.hasBookAsset(tid, it.id) }
        }

    /**
     * Поиск сразу по нескольким переводам (до [limit] совпадений суммарно).
     */
    fun searchMultiple(
        translations: List<TranslationId>,
        query: String,
        limit: Int = 400,
        settings: SearchSettings = SearchSettings(),
    ): List<SearchHit> {
        if (translations.isEmpty()) return emptyList()
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        if (canUseFtsFastPath(settings)) {
            repository.trySearchFts(translations, q, settings, limit)?.let { return it }
        }
        if (translations.size == 1) return search(translations.first(), query, limit, settings)
        val out = ArrayList<SearchHit>(minOf(limit, 64))
        for (t in translations) {
            val remaining = limit - out.size
            if (remaining <= 0) break
            out.addAll(search(t, query, remaining, settings))
        }
        return out
    }

    fun search(
        translation: TranslationId,
        query: String,
        limit: Int = 400,
        settings: SearchSettings = SearchSettings(),
    ): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val leg = legacy?.get(translation)
        if (leg != null && !hasPerBookAssets(translation)) {
            val filtered = filterByScope(leg.books, settings)
            return searchInData(translation, filtered, query, limit, settings)
        }

        if (canUseFtsFastPath(settings)) {
            repository.trySearchFts(listOf(translation), q, settings, limit)?.let { return it }
        }

        val rows = repository.getVersesForSearch(translation)
        if (rows.isEmpty()) return emptyList()
        val bookNames = repository.getBookTitlesForSearch(translation)
        val filteredRows = filterVerseRowsByScope(rows, settings)
        return searchInVerseRows(translation, filteredRows, bookNames, query, limit, settings)
    }

    private fun filterByScope(books: List<BibleBook>, settings: SearchSettings): List<BibleBook> =
        when (settings.scope) {
            SearchScope.ALL -> books
            SearchScope.OLD_TESTAMENT -> books.filter { BibleCanon.isOldTestament(it.id) }
            SearchScope.NEW_TESTAMENT -> books.filter { BibleCanon.isNewTestament(it.id) }
            SearchScope.SINGLE_BOOK -> {
                val id = settings.singleBookId ?: return books
                books.filter { it.id == id }
            }
        }

    private fun filterVerseRowsByScope(
        rows: List<BibleVerseSearchRow>,
        settings: SearchSettings,
    ): List<BibleVerseSearchRow> =
        when (settings.scope) {
            SearchScope.ALL -> rows
            SearchScope.OLD_TESTAMENT -> rows.filter { BibleCanon.isOldTestament(it.bookId) }
            SearchScope.NEW_TESTAMENT -> rows.filter { BibleCanon.isNewTestament(it.bookId) }
            SearchScope.SINGLE_BOOK -> {
                val id = settings.singleBookId ?: return rows
                rows.filter { it.bookId == id }
            }
        }

    private fun searchInVerseRows(
        translation: TranslationId,
        rows: List<BibleVerseSearchRow>,
        bookNames: Map<String, String>,
        query: String,
        limit: Int,
        settings: SearchSettings,
    ): List<SearchHit> {
        val prepared = prepareBibleSearchQuery(query, settings) ?: return emptyList()
        val out = ArrayList<SearchHit>(64)
        for (row in rows) {
            val normalizedVerse = normalizeVerseForSearch(row.text, settings)
            if (matchesPrepared(normalizedVerse, prepared, settings)) {
                out.add(
                    SearchHit(
                        translation = translation,
                        bookId = row.bookId,
                        bookName = bookNames[row.bookId] ?: row.bookId,
                        chapter = row.chapterNumber,
                        verse = row.verseNumber,
                        text = row.text,
                    ),
                )
                if (out.size >= limit) break
            }
        }
        return out
    }

    private fun hasPerBookAssets(translation: TranslationId): Boolean =
        repository.listBookIds(translation)?.isNotEmpty() == true

    private fun searchInData(
        translation: TranslationId,
        books: List<BibleBook>,
        query: String,
        limit: Int,
        settings: SearchSettings,
    ): List<SearchHit> {
        val prepared = prepareBibleSearchQuery(query, settings) ?: return emptyList()
        val out = ArrayList<SearchHit>(64)
        outer@ for (book in books) {
            for (ch in book.chapters) {
                for (v in ch.verses) {
                    val normalizedVerse = normalizeVerseForSearch(v.text, settings)
                    if (matchesPrepared(normalizedVerse, prepared, settings)) {
                        out.add(
                            SearchHit(
                                translation = translation,
                                bookId = book.id,
                                bookName = book.name,
                                chapter = ch.number,
                                verse = v.number,
                                text = v.text,
                            ),
                        )
                        if (out.size >= limit) break@outer
                    }
                }
            }
        }
        return out
    }

    private fun normalizeVerseForSearch(text: String, settings: SearchSettings): String =
        normalizeVerseForCompare(text, settings)

    private fun matchesPrepared(
        t: String,
        pq: BiblePreparedSearchQuery,
        settings: SearchSettings,
    ): Boolean {
        pq.wholeWordTokens?.let { words ->
            if (words.isEmpty()) return false
            if (settings.orderedWords) {
                var searchFrom = 0
                for (word in words) {
                    val idx = findWholeWordInNormalizedText(t, word, searchFrom)
                    if (idx < 0) return false
                    searchFrom = idx + word.length
                }
                return true
            }
            return words.all { findWholeWordInNormalizedText(t, it, 0) >= 0 }
        }
        pq.orderedParts?.let { parts ->
            var searchFrom = 0
            for (part in parts) {
                val idx = t.indexOf(part, searchFrom)
                if (idx < 0) return false
                searchFrom = idx + part.length
            }
            return true
        }
        return t.contains(pq.needle)
    }
}
