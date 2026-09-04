package com.example.bible.data.db

import com.example.bible.data.verseSearchNormForStored

/**
 * Подготовка индекса поиска: [searchNorm] + опционально FTS5 (пакетами, без блокировки поиска).
 */
object BibleFtsMaintainer {

    private const val BACKFILL_BATCH = 2000

    /** Заполняет пустые [BibleVerseEntity.searchNorm] (после миграций). */
    fun backfillSearchNorm(database: BibleDatabase) {
        while (BibleSearchDbLock.shouldPauseIndexing()) {
            Thread.sleep(100)
        }
        BibleSearchDbLock.withWriteLock {
            val dao = database.bibleDao()
            if (dao.countAllVerses() == 0) return@withWriteLock
            while (dao.countVersesWithEmptyNorm() > 0) {
                if (BibleSearchDbLock.shouldPauseIndexing()) break
                if (!backfillSearchNormBatch(database, BACKFILL_BATCH)) break
            }
        }
    }

    /** Один пакет backfill; @return false если больше нечего заполнять. */
    fun backfillSearchNormBatch(database: BibleDatabase, limit: Int = BACKFILL_BATCH): Boolean {
        val dao = database.bibleDao()
        val batch = dao.getVersesBatchMissingNorm(limit)
        if (batch.isEmpty()) return false
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
        return true
    }

    /** Фоновая индексация пакетами; уступает активному поиску. */
    fun ensureSearchIndexUpToDate(database: BibleDatabase) {
        val dao = database.bibleDao()
        if (dao.countAllVerses() == 0) return
        while (true) {
            if (BibleSearchDbLock.shouldPauseIndexing()) {
                Thread.sleep(100)
                continue
            }
            val more = BibleSearchDbLock.withWriteLock {
                when {
                    dao.countVersesWithEmptyNorm() > 0 ->
                        backfillSearchNormBatch(database, BACKFILL_BATCH)
                    else ->
                        runCatching { BibleFts5Index.ensureBuiltBatch(database) }.getOrDefault(false)
                }
            }
            if (!more) break
            Thread.sleep(5)
        }
    }
}
