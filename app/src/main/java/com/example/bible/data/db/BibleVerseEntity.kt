package com.example.bible.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "bible_verses",
    primaryKeys = ["translationCode", "bookId", "chapterNumber", "verseNumber"],
    indices = [Index(value = ["translationCode", "bookId"])],
)
data class BibleVerseEntity(
    val translationCode: String,
    val bookId: String,
    val chapterNumber: Int,
    val verseNumber: Int,
    val text: String,
    val imageUrl: String?,
    /** Нормализованный текст для быстрого поиска (LIKE по этой колонке). */
    val searchNorm: String = "",
)
