package com.example.bible.data.languagestudy

import android.content.Context

/** Фоновая подгрузка полных zip-колод из assets при старте приложения. */
object LanguageStudyBootstrap {
    private val LANG_CODES = listOf("english", "irit", "greek", "arabic")

    fun importBundledIfNeeded(context: Context) {
        val repo = LanguageStudyRepository(context.applicationContext)
        for (lang in LANG_CODES) {
            try {
                repo.ensureBundledFullOrDemo(lang)
            } catch (_: Exception) {
                // не блокируем остальную инициализацию Study DB
            }
        }
    }
}
