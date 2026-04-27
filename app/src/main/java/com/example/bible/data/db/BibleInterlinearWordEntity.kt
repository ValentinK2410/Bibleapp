package com.example.bible.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "bible_interlinear_words",
    primaryKeys = [
        "translationCode",
        "bookId",
        "chapterNumber",
        "verseNumber",
        "wordIndex",
    ],
    indices = [Index(value = ["translationCode", "bookId", "chapterNumber", "verseNumber"])],
)
data class BibleInterlinearWordEntity(
    val translationCode: String,
    val bookId: String,
    val chapterNumber: Int,
    val verseNumber: Int,
    val wordIndex: Int,
    val original: String,
    val transliteration: String,
    /** Сырое поле r из JSON; для подстрочника при отображении применяется VinokurovInterlinearFixes. */
    val gloss: String,
    val strong: String?,
    val morph: String?,
)
