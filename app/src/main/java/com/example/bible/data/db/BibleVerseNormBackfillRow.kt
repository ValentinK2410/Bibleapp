package com.example.bible.data.db

/** Пакетное заполнение [BibleVerseEntity.searchNorm] после миграции 1→2. */
data class BibleVerseNormBackfillRow(
    val translationCode: String,
    val bookId: String,
    val chapterNumber: Int,
    val verseNumber: Int,
    val text: String,
)
