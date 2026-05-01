package com.example.bible.data

import android.content.Context

/** Флаг озвучки входящих SMS (раздел «Эксперимент»); SharedPreferences — чтобы [BroadcastReceiver] читал синхронно. */
object ExperimentSmsSpeakPrefs {
    private const val PREFS_NAME = "experiment_sms_speak"
    private const val KEY_SPEAK_INCOMING = "speak_incoming_sms"

    fun isSpeakIncomingEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SPEAK_INCOMING, false)

    fun setSpeakIncomingEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SPEAK_INCOMING, enabled)
            .apply()
    }
}
