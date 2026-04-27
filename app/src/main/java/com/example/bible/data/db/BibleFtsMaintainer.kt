package com.example.bible.data.db

import com.example.bible.data.verseSearchNormForStored

/**
 * После миграции 1→2 заполняет [BibleVerseEntity.searchNorm] и пересобирает FTS.
 */
object BibleFtsMaintainer {

    fun ensureFtsUpToDate(database: BibleDatabase) {
        val dao = database.bibleDao()
        if (dao.countAllVerses() == 0) return
        while (dao.countVersesWithEmptyNorm() > 0) {
            val batch = dao.getVersesBatchMissingNorm(500)
            if (batch.isEmpty()) break
            database.runInTransaction {
                for (r in batch) {
                    dao.updateSearchNorm(
                        verseSearchNormForStored(r.text),
                        r.translationCode,
                        r.bookId,
                        r.chapterNumber,
                        r.verseNumber,
                    )
                }
            }
        }
        val verses = dao.countAllVerses()
        val fts = BibleFtsNative.countRows(database.openHelper.writableDatabase).toInt()
        if (verses > 0 && fts != verses) {
            database.runInTransaction {
                val db = database.openHelper.writableDatabase
                BibleFtsNative.clear(db)
                BibleFtsNative.populateAll(db)
            }
        }
    }
}
