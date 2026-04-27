package com.example.bible.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Операции с виртуальной таблицей FTS5 (Room не включает её в схему — только через raw SQL).
 */
internal object BibleFtsNative {

    fun countRows(db: SupportSQLiteDatabase): Long =
        db.compileStatement("SELECT COUNT(*) FROM bible_verses_fts").use { it.simpleQueryForLong() }

    fun clear(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM bible_verses_fts")
    }

    fun populateAll(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO bible_verses_fts(searchNorm, translationCode, bookId, chapterNumber, verseNumber) " +
                "SELECT searchNorm, translationCode, bookId, chapterNumber, verseNumber FROM bible_verses " +
                "WHERE searchNorm != ''",
        )
    }

    fun syncBook(db: SupportSQLiteDatabase, translationCode: String, bookId: String) {
        db.execSQL(
            "DELETE FROM bible_verses_fts WHERE translationCode = ? AND bookId = ?",
            arrayOf(translationCode, bookId),
        )
        db.execSQL(
            "INSERT INTO bible_verses_fts(searchNorm, translationCode, bookId, chapterNumber, verseNumber) " +
                "SELECT searchNorm, translationCode, bookId, chapterNumber, verseNumber FROM bible_verses " +
                "WHERE translationCode = ? AND bookId = ? AND searchNorm != ''",
            arrayOf(translationCode, bookId),
        )
    }
}
