package com.example.bible.data

object ServerConfig {
    var baseUrl: String = "https://sotvori.pro/api"

    fun audioUrl(translation: TranslationId, bookId: String, chapter: Int): String =
        "$baseUrl/audio/${translation.code}/$bookId/$chapter.mp3"

    fun commentaryUrl(translation: TranslationId, bookId: String, chapter: Int, verse: Int): String =
        "$baseUrl/commentary/${translation.code}/$bookId/$chapter/$verse.json"
}
