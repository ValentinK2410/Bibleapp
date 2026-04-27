package com.example.bible.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface BibleDao {
    @Query("SELECT COUNT(*) FROM bible_verses")
    fun countAllVerses(): Int

    @Query("SELECT COUNT(*) FROM bible_books WHERE translationCode = :code")
    fun countBooksForTranslation(code: String): Int

    @Query("SELECT bookId FROM bible_books WHERE translationCode = :code ORDER BY bookId")
    fun listBookIds(code: String): List<String>

    @Query("SELECT name FROM bible_books WHERE translationCode = :code AND bookId = :bookId LIMIT 1")
    fun getBookName(code: String, bookId: String): String?

    @Query("SELECT EXISTS(SELECT 1 FROM bible_books WHERE translationCode = :code AND bookId = :bookId LIMIT 1)")
    fun hasBook(code: String, bookId: String): Boolean

    @Query(
        "SELECT translationCode, bookId, chapterNumber, verseNumber, text FROM bible_verses " +
            "WHERE translationCode = :code ORDER BY bookId, chapterNumber, verseNumber",
    )
    fun getVersesForSearchByTranslation(code: String): List<BibleVerseSearchRow>

    @Query("SELECT bookId, name FROM bible_books WHERE translationCode = :code ORDER BY bookId")
    fun getBookTitlesForTranslation(code: String): List<BibleBookTitleRow>

    @Query(
        "SELECT * FROM bible_verses WHERE translationCode = :code AND bookId = :bookId " +
            "ORDER BY chapterNumber, verseNumber",
    )
    fun getVersesForBook(code: String, bookId: String): List<BibleVerseEntity>

    @Query(
        "SELECT * FROM bible_interlinear_words WHERE translationCode = :code AND bookId = :bookId " +
            "AND chapterNumber = :chapter AND verseNumber = :verse ORDER BY wordIndex",
    )
    fun getInterlinear(
        code: String,
        bookId: String,
        chapter: Int,
        verse: Int,
    ): List<BibleInterlinearWordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBook(entity: BibleBookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBooks(entities: List<BibleBookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertVerses(verses: List<BibleVerseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertInterlinear(words: List<BibleInterlinearWordEntity>)

    @Query("DELETE FROM bible_interlinear_words WHERE translationCode = :code AND bookId = :bookId")
    fun deleteInterlinearForBook(code: String, bookId: String)

    @Query("DELETE FROM bible_verses WHERE translationCode = :code AND bookId = :bookId")
    fun deleteVersesForBook(code: String, bookId: String)

    @Query("DELETE FROM bible_books WHERE translationCode = :code AND bookId = :bookId")
    fun deleteBook(code: String, bookId: String)

    @Transaction
    fun replaceBook(
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
