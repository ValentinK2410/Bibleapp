package com.example.bible.data

import android.content.Context

/** Переопределения озвучки отправителей SMS (раздел «Эксперимент» → SMS). */
class SmsSpeechOverrideRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<SmsSpeechOverrideEntry> =
        SmsSpeechOverrideJson.parseEntries(prefs.getString(KEY_JSON, "").orEmpty())

    @Synchronized
    fun save(entries: List<SmsSpeechOverrideEntry>) {
        prefs.edit().putString(KEY_JSON, SmsSpeechOverrideJson.entriesToJson(entries)).apply()
    }

    companion object {
        private const val PREFS_NAME = "sms_speech_overrides_v1"
        private const val KEY_JSON = "entries_json"
    }
}
