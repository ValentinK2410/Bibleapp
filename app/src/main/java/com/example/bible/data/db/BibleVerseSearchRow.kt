package com.example.bible.data.db

/**
 * Строка для поиска по Библии без загрузки подстрочника и группировки в [BibleBook].
 */
data class BibleVerseSearchRow(
    val translationCode: String,
    val bookId: String,
    val chapterNumber: Int,
    val verseNumber: Int,
    val text: String,
)

data class BibleBookTitleRow(
    val bookId: String,
    val name: String,
)
