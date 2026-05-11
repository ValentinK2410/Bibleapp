package com.example.bible.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StudyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertLangVocab(words: List<LangVocabWordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertLangSrsCards(cards: List<LangSrsCardEntity>)

    @Query("DELETE FROM lang_vocab_words WHERE langCode = :langCode")
    fun deleteLangWordsForLanguage(langCode: String)

    @Query("SELECT COUNT(*) FROM lang_vocab_words WHERE langCode = :langCode")
    fun countLangWords(langCode: String): Int

    @Query("SELECT * FROM lang_vocab_words WHERE langCode = :langCode ORDER BY frequencyRank ASC, lemma ASC")
    fun listLangWords(langCode: String): List<LangVocabWordEntity>

    @Query(
        "SELECT * FROM lang_vocab_words WHERE langCode = :langCode AND " +
            "(lemma LIKE '%' || :needle || '%' OR display LIKE '%' || :needle || '%' OR glossRu LIKE '%' || :needle || '%') " +
            "ORDER BY frequencyRank ASC, lemma ASC LIMIT :lim",
    )
    fun searchLangWords(langCode: String, needle: String, lim: Int): List<LangVocabWordEntity>

    @Query("SELECT * FROM lang_vocab_words WHERE wordKey = :wordKey LIMIT 1")
    fun getLangWord(wordKey: String): LangVocabWordEntity?

    @Query("SELECT * FROM lang_srs_cards WHERE wordKey = :wordKey LIMIT 1")
    fun getLangSrsCard(wordKey: String): LangSrsCardEntity?

    @Query(
        """
        SELECT w.* FROM lang_vocab_words w
        LEFT JOIN lang_srs_cards s ON w.wordKey = s.wordKey
        WHERE w.langCode = :langCode
        AND (s.wordKey IS NULL OR s.nextReviewAtEpochMs <= :nowMs)
        ORDER BY CASE WHEN s.wordKey IS NULL THEN 0 ELSE 1 END ASC,
                 COALESCE(s.nextReviewAtEpochMs, 9223372036854775807) ASC
        LIMIT :lim
        """,
    )
    fun getDueLangWords(langCode: String, nowMs: Long, lim: Int): List<LangVocabWordEntity>

    @Query(
        """
        SELECT COUNT(*) FROM lang_vocab_words w
        LEFT JOIN lang_srs_cards s ON w.wordKey = s.wordKey
        WHERE w.langCode = :langCode
        AND (s.wordKey IS NULL OR s.nextReviewAtEpochMs <= :nowMs)
        """,
    )
    fun countDueLangWords(langCode: String, nowMs: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertChapterCommentary(entity: StudyChapterCommentaryEntity)

    @Query(
        "SELECT text FROM study_chapter_commentary WHERE slug = :slug AND bookId = :bookId AND chapter = :chapter LIMIT 1",
    )
    fun getChapterCommentary(slug: String, bookId: String, chapter: Int): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertVerseBlob(entity: StudyVerseBlobEntity)

    @Query(
        "SELECT payload FROM study_verse_blob WHERE kind = :kind AND translationCode = :translationCode " +
            "AND bookId = :bookId AND chapter = :chapter AND verse = :verse LIMIT 1",
    )
    fun getVerseBlobPayload(
        kind: String,
        translationCode: String,
        bookId: String,
        chapter: Int,
        verse: Int,
    ): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStrongs(entries: List<StrongsEntryEntity>)

    @Query("SELECT * FROM strongs_entries WHERE code = :code LIMIT 1")
    fun getStrongsEntry(code: String): StrongsEntryEntity?

    @Query("SELECT COUNT(*) FROM strongs_entries")
    fun countStrongsEntries(): Int
}
