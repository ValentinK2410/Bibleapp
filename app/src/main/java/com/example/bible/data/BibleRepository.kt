package com.example.bible.data

import android.content.Context
import com.example.bible.data.db.BibleDatabase
import com.example.bible.data.db.BibleSearchDbLock
import com.example.bible.data.db.BibleFts5Index
import com.example.bible.data.db.BibleFtsMaintainer
import com.example.bible.data.db.BibleJsonImporter
import com.example.bible.data.db.BibleNormLikeSearch
import com.example.bible.data.db.BibleVerseSearchRow
import com.example.bible.ui.SearchSettings
import java.util.concurrent.Executors

/**
 * Тексты Библии в [BibleDatabase] (Room). JSON в assets используется только при первом запуске (импорт).
 */
class BibleRepository(
    private val context: Context,
) {
    private val database: BibleDatabase by lazy { BibleDatabase.getInstance(context) }
    private val dao by lazy { database.bibleDao() }
    private var librarySingleton: BibleLibrary? = null

    companion object {
        private val searchIndexExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "bible-search-index").apply { isDaemon = true }
        }
    }

    fun clearCache() {
        librarySingleton?.invalidate()
        librarySingleton = null
    }

    fun loadLibrary(): BibleLibrary {
        librarySingleton?.let { return it }
        BibleJsonImporter.importFromAssetsIfNeeded(context, database)
        val hasAny = TranslationId.entries.any { dao.countBooksForTranslation(it.code) > 0 }
        if (!hasAny) {
            throw IllegalStateException("Нет текстов Библии: добавьте папки переводов в assets/bible/ или sample JSON.")
        }
        val lib = BibleLibrary(this)
        librarySingleton = lib
        // Индекс searchNorm + FTS5 — в фоне пакетами; поиск имеет приоритет.
        searchIndexExecutor.execute {
            BibleFts5Index.markReadyIfPopulated(database)
            BibleFtsMaintainer.ensureSearchIndexUpToDate(database)
        }
        return lib
    }

    internal fun legacyDataIfMissing(): Map<TranslationId, BibleData>? = null

    fun hasTranslationFolder(translation: TranslationId): Boolean =
        dao.countBooksForTranslation(translation.code) > 0

    fun hasBookAsset(translation: TranslationId, bookId: String): Boolean =
        dao.hasBook(translation.code, bookId)

    fun loadBookShell(translation: TranslationId, bookId: String): BibleBook? {
        if (!dao.hasBook(translation.code, bookId)) return null
        val name = dao.getBookName(translation.code, bookId) ?: return null
        val maxChapter = dao.getMaxChapterNumber(translation.code, bookId)
            ?: BibleCanon.byId(bookId)?.chapters
            ?: 1
        return BibleBook(
            id = bookId,
            name = name,
            chapters = (1..maxChapter).map { n -> BibleChapter(number = n, verses = emptyList()) },
        )
    }

    fun loadChapter(translation: TranslationId, bookId: String, chapterNum: Int): BibleChapter? {
        if (!dao.hasBook(translation.code, bookId)) return null
        val code = translation.code
        val fixGloss = translation == TranslationId.INTERLINEAR
        val rows = dao.getVersesForChapter(code, bookId, chapterNum)
        if (rows.isEmpty()) return null
        val wordsByVerse = dao.getInterlinearForChapter(code, bookId, chapterNum)
            .groupBy { it.verseNumber }
        val verses = rows.sortedBy { it.verseNumber }.map { row ->
            mapVerseRow(row, wordsByVerse[row.verseNumber].orEmpty(), fixGloss)
        }
        return BibleChapter(number = chapterNum, verses = verses)
    }

    fun loadBook(translation: TranslationId, bookId: String): BibleBook? {
        if (!dao.hasBook(translation.code, bookId)) return null
        val name = dao.getBookName(translation.code, bookId) ?: return null
        val code = translation.code
        val fixGloss = translation == TranslationId.INTERLINEAR
        val rows = dao.getVersesForBook(code, bookId)
        if (rows.isEmpty()) {
            return BibleBook(id = bookId, name = name, chapters = emptyList())
        }
        val wordsByVerse = dao.getInterlinearForBook(code, bookId)
            .groupBy { it.chapterNumber to it.verseNumber }
        val byChapter = rows.groupBy { it.chapterNumber }
        val chapterNumbers = byChapter.keys.sorted()
        val chapters = chapterNumbers.map { chNum ->
            val list = byChapter.getValue(chNum).sortedBy { it.verseNumber }
            val verses = list.map { row ->
                mapVerseRow(row, wordsByVerse[chNum to row.verseNumber].orEmpty(), fixGloss)
            }
            BibleChapter(number = chNum, verses = verses)
        }
        return BibleBook(id = bookId, name = name, chapters = chapters)
    }

    private fun mapVerseRow(
        row: com.example.bible.data.db.BibleVerseEntity,
        words: List<com.example.bible.data.db.BibleInterlinearWordEntity>,
        fixGloss: Boolean,
    ): BibleVerse {
        val interlinear: List<InterlinearWord>? =
            if (words.isEmpty()) {
                null
            } else {
                words.sortedBy { it.wordIndex }.map { w ->
                    val gloss = w.gloss
                    InterlinearWord(
                        original = w.original,
                        transliteration = w.transliteration,
                        translation = if (fixGloss) {
                            VinokurovInterlinearFixes.fixRussianGloss(gloss, w.strong.orEmpty())
                        } else {
                            gloss
                        },
                        strong = w.strong,
                        morph = w.morph,
                    )
                }
            }
        return BibleVerse(
            number = row.verseNumber,
            text = row.text,
            imageUrl = row.imageUrl,
            interlinearWords = interlinear,
        )
    }

    fun listBookIds(translation: TranslationId): List<String>? {
        val ids = dao.listBookIds(translation.code)
        return if (ids.isEmpty()) null else ids
    }

    fun searchVersesPaged(
        translation: TranslationId,
        limit: Int,
        offset: Int,
    ): List<BibleVerseSearchRow> =
        dao.getVersesForSearchByTranslationPage(translation.code, limit, offset)

    fun getBookTitlesForSearch(translation: TranslationId): Map<String, String> =
        dao.getBookTitlesForTranslation(translation.code).associate { it.bookId to it.name }

    /**
     * Быстрый поиск: FTS5 (если доступен) или LIKE по [searchNorm].
     * Пустой список — нет совпадений; null только если в БД нет стихов.
     */
    fun trySearchFts(
        translations: List<TranslationId>,
        query: String,
        settings: SearchSettings,
        limit: Int,
    ): List<SearchHit>? {
        if (dao.countAllVerses() == 0) return null
        val norm = normalizeSearchQueryForCompare(query, BibleVerseSearchNormSettings)
        if (norm.isEmpty()) return emptyList()
        val codes = translations.map { it.code }
        BibleSearchDbLock.onSearchStarted()
        return try {
            runCatching {
                val rows = searchIndexedRows(codes, settings, limit, norm)
                rowsToHits(rows, codes)
            }.getOrElse { emptyList() }
        } finally {
            BibleSearchDbLock.onSearchFinished()
        }
    }

    private fun searchIndexedRows(
        codes: List<String>,
        settings: SearchSettings,
        limit: Int,
        norm: String,
    ): List<BibleVerseSearchRow> {
        if (BibleFts5Index.isIndexReady(database)) {
            val match = BibleFts5Index.matchExpressionForNormalizedQuery(norm)
            if (match != null) {
                val ftsQuery = BibleFts5Index.buildSearchQuery(codes, settings, limit, match)
                if (ftsQuery != null) {
                    runCatching { return dao.searchVersesFastSql(ftsQuery) }
                }
            }
        }
        val pattern = BibleNormLikeSearch.likePatternForNormalizedQuery(norm) ?: return emptyList()
        val likeQuery = BibleNormLikeSearch.buildLikeQuery(codes, settings, limit, pattern)
            ?: return emptyList()
        return dao.searchVersesFastSql(likeQuery)
    }

    private fun rowsToHits(rows: List<BibleVerseSearchRow>, codes: List<String>): List<SearchHit> {
        val titleRows = dao.getBookTitlesForTranslations(codes)
        val titleMap = titleRows.associate { Pair(it.translationCode, it.bookId) to it.name }
        return rows.map { r ->
            SearchHit(
                translation = TranslationId.fromCode(r.translationCode),
                bookId = r.bookId,
                bookName = titleMap[r.translationCode to r.bookId] ?: r.bookId,
                chapter = r.chapterNumber,
                verse = r.verseNumber,
                text = r.text,
            )
        }
    }
}
