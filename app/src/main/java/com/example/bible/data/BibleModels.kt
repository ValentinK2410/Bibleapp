package com.example.bible.data

data class BibleData(
    val translation: String,
    val books: List<BibleBook>,
)

data class BibleBook(
    val id: String,
    val name: String,
    val chapters: List<BibleChapter>,
)

data class BibleChapter(
    val number: Int,
    val verses: List<BibleVerse>,
)

data class BibleVerse(
    val number: Int,
    val text: String,
    val imageUrl: String? = null,
    val interlinearWords: List<InterlinearWord>? = null,
)

data class InterlinearWord(
    val original: String,
    val transliteration: String,
    val translation: String,
    val strong: String? = null,
    val morph: String? = null,
)
