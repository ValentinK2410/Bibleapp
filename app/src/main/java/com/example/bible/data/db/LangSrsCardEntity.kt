package com.example.bible.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lang_srs_cards",
    foreignKeys = [
        ForeignKey(
            entity = LangVocabWordEntity::class,
            parentColumns = ["wordKey"],
            childColumns = ["wordKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["wordKey"], unique = true)],
)
data class LangSrsCardEntity(
    /** Совпадает с [LangVocabWordEntity.wordKey]. */
    @PrimaryKey val wordKey: String,
    /** SM-2: множитель лёгкости (мин. ~1.3). */
    val easeFactor: Double,
    val intervalDays: Int,
    val repetitions: Int,
    val nextReviewAtEpochMs: Long,
    val lastReviewAtEpochMs: Long?,
    /** Пользовательская заметка (мнемоника). */
    val userNote: String?,
)
