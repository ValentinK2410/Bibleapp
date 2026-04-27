package com.example.bible.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StudyDao {

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
