package com.example.bible.data.db

import androidx.room.Entity

/**
 * Унифицированное хранение JSON для сравнений переводов, перекрёстных ссылок,
 * слов Стронга по стиху и комментария API по стиху.
 */
@Entity(
    tableName = "study_verse_blob",
    primaryKeys = ["kind", "translationCode", "bookId", "chapter", "verse"],
)
data class StudyVerseBlobEntity(
    /** [StudyVerseBlobKind] */
    val kind: String,
    /** Пустая строка, если не привязано к коду перевода (API — реальный код). */
    val translationCode: String,
    val bookId: String,
    val chapter: Int,
    val verse: Int,
    val payload: String,
)

object StudyVerseBlobKind {
    const val VERSE_COMPARISON = "VERSE_CMP"
    const val CROSS_REFERENCE = "CROSS"
    const val STRONG_WORDS = "STRONG"
    const val VERSE_COMMENTARY_API = "COMMENTARY_API"
}
