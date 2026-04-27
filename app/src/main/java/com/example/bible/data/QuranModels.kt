package com.example.bible.data

data class QuranSurahSummary(
    val number: Int,
    val nameArabic: String,
    val nameTransliteration: String,
    val nameRussian: String,
    val type: String,
    val totalVerses: Int,
)

data class QuranSurahContent(
    val summary: QuranSurahSummary,
    val verses: List<QuranVerse>,
)

data class QuranVerse(
    val number: Int,
    val arabic: String,
    val transliteration: String,
    val translationRu: String,
)

data class QuranSearchHit(
    val surahNumber: Int,
    val surahNameRu: String,
    val verseNumber: Int,
    val translationRu: String,
)
