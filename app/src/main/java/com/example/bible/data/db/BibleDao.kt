package com.example.bible.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
abstract class BibleDao {

    @Query("SELECT COUNT(*) FROM bible_verses")
    abstract fun countAllVerses(): Int

    @Query("SELECT COUNT(*) FROM bible_verses WHERE searchNorm = ''")
    abstract fun countVersesWithEmptyNorm(): Int

    @Query("SELECT COUNT(*) FROM bible_books WHERE translationCode = :code")
    abstract fun countBooksForTranslation(code: String): Int

    @Query("SELECT bookId FROM bible_books WHERE translationCode = :code ORDER BY bookId")
    abstract fun listBookIds(code: String): List<String>

    @Query("SELECT name FROM bible_books WHERE translationCode = :code AND bookId = :bookId LIMIT 1")
    abstract fun getBookName(code: String, bookId: String): String?

    @Query("SELECT EXISTS(SELECT 1 FROM bible_books WHERE translationCode = :code AND bookId = :bookId LIMIT 1)")
    abstract fun hasBook(code: String, bookId: String): Boolean

    @Query(
        "SELECT translationCode, bookId, chapterNumber, verseNumber, text FROM bible_verses " +
            "WHERE translationCode = :code ORDER BY bookId, chapterNumber, verseNumber",
    )
    abstract fun getVersesForSearchByTranslation(code: String): List<BibleVerseSearchRow>

    @Query("SELECT bookId, name FROM bible_books WHERE translationCode = :code ORDER BY bookId")
    abstract fun getBookTitlesForTranslation(code: String): List<BibleBookTitleRow>

    @Query(
        "SELECT translationCode, bookId, name FROM bible_books WHERE translationCode IN (:codes) " +
            "ORDER BY translationCode, bookId",
    )
    abstract fun getBookTitlesForTranslations(codes: List<String>): List<BibleBookTitleMultiRow>

    @Query(
        "SELECT * FROM bible_verses WHERE translationCode = :code AND bookId = :bookId " +
            "ORDER BY chapterNumber, verseNumber",
    )
    abstract fun getVersesForBook(code: String, bookId: String): List<BibleVerseEntity>

    @Query(
        "SELECT translationCode, bookId, chapterNumber, verseNumber, text FROM bible_verses " +
            "WHERE searchNorm = '' LIMIT :limit",
    )
    abstract fun getVersesBatchMissingNorm(limit: Int): List<BibleVerseNormBackfillRow>

    @Query(
        "UPDATE bible_verses SET searchNorm = :norm WHERE translationCode = :code AND bookId = :bookId " +
            "AND chapterNumber = :chapter AND verseNumber = :verse",
    )
    abstract fun updateSearchNorm(
        norm: String,
        code: String,
        bookId: String,
        chapter: Int,
        verse: Int,
    )

    @Query(
        "SELECT * FROM bible_interlinear_words WHERE translationCode = :code AND bookId = :bookId " +
            "AND chapterNumber = :chapter AND verseNumber = :verse ORDER BY wordIndex",
    )
    abstract fun getInterlinear(
        code: String,
        bookId: String,
        chapter: Int,
        verse: Int,
    ): List<BibleInterlinearWordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertBook(entity: BibleBookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertBooks(entities: List<BibleBookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertVerses(verses: List<BibleVerseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertInterlinear(words: List<BibleInterlinearWordEntity>)

    @Query("DELETE FROM bible_interlinear_words WHERE translationCode = :code AND bookId = :bookId")
    abstract fun deleteInterlinearForBook(code: String, bookId: String)

    @Query("DELETE FROM bible_verses WHERE translationCode = :code AND bookId = :bookId")
    abstract fun deleteVersesForBook(code: String, bookId: String)

    @Query("DELETE FROM bible_books WHERE translationCode = :code AND bookId = :bookId")
    abstract fun deleteBook(code: String, bookId: String)

    @RawQuery
    abstract fun searchVersesFts(query: SupportSQLiteQuery): List<BibleVerseSearchRow>

    @Transaction
    open fun replaceBook(
        book: BibleBookEntity,
        verses: List<BibleVerseEntity>,
        interlinear: List<BibleInterlinearWordEntity>,
    ) {
        val code = book.translationCode
        val id = book.bookId
        deleteInterlinearForBook(code, id)
        deleteVersesForBook(code, id)
        deleteBook(code, id)
        insertBook(book)
        if (verses.isNotEmpty()) insertVerses(verses)
        if (interlinear.isNotEmpty()) insertInterlinear(interlinear)
    }
}
