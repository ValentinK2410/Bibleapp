package com.example.bible.data

import android.content.Context
import com.example.bible.data.db.BibleDatabase
import com.example.bible.data.db.BibleJsonImporter

/**
 * Тексты Библии в [BibleDatabase] (Room). JSON в assets используется только при первом запуске (импорт).
 */
class BibleRepository(
    private val context: Context,
) {
    private val database: BibleDatabase = BibleDatabase.getInstance(context)
    private val dao = database.bibleDao()
    private var librarySingleton: BibleLibrary? = null

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
        return lib
    }

    internal fun legacyDataIfMissing(): Map<TranslationId, BibleData>? = null

    fun hasTranslationFolder(translation: TranslationId): Boolean =
        dao.countBooksForTranslation(translation.code) > 0

    fun hasBookAsset(translation: TranslationId, bookId: String): Boolean =
        dao.hasBook(translation.code, bookId)

    fun loadBook(translation: TranslationId, bookId: String): BibleBook? {
        if (!dao.hasBook(translation.code, bookId)) return null
        val name = dao.getBookName(translation.code, bookId) ?: return null
        val code = translation.code
        val fixGloss = translation == TranslationId.INTERLINEAR
        val rows = dao.getVersesForBook(code, bookId)
        if (rows.isEmpty()) {
            return BibleBook(id = bookId, name = name, chapters = emptyList())
        }
        val byChapter = rows.groupBy { it.chapterNumber }
        val chapterNumbers = byChapter.keys.sorted()
        val chapters = chapterNumbers.map { chNum ->
            val list = byChapter.getValue(chNum).sortedBy { it.verseNumber }
            val verses = list.map { row ->
                val words = dao.getInterlinear(code, bookId, chNum, row.verseNumber)
                val interlinear: List<InterlinearWord>? =
                    if (words.isEmpty()) {
                        null
                    } else {
                        words.sortedBy { it.wordIndex }.map { w ->
                            val gloss = w.gloss
                            InterlinearWord(
                                original = w.original,
                                transliteration = w.transliteration,
                                translation = if (fixGloss) VinokurovInterlinearFixes.fixRussianGloss(gloss) else gloss,
                                strong = w.strong,
                                morph = w.morph,
                            )
                        }
                    }
                BibleVerse(
                    number = row.verseNumber,
                    text = row.text,
                    imageUrl = row.imageUrl,
                    interlinearWords = interlinear,
                )
            }
            BibleChapter(number = chNum, verses = verses)
        }
        return BibleBook(id = bookId, name = name, chapters = chapters)
    }

    fun listBookIds(translation: TranslationId): List<String>? {
        val ids = dao.listBookIds(translation.code)
        return if (ids.isEmpty()) null else ids
    }
}
