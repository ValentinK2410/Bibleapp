package com.example.bible.data.db

import com.example.bible.data.verseSearchNormForStored

/**
 * Заполняет пустые [BibleVerseEntity.searchNorm] после миграций (нужно для LIKE-поиска).
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
    }
}
