package com.example.bible.data

import android.content.Context

/** Сценарии реакций на SMS (раздел «Эксперимент»). */
class SmsReactionRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<SmsReactionScenario> =
        SmsReactionJson.parseScenarios(prefs.getString(KEY_JSON, "").orEmpty())

    @Synchronized
    fun save(scenarios: List<SmsReactionScenario>) {
        prefs.edit().putString(KEY_JSON, SmsReactionJson.scenariosToJson(scenarios)).apply()
    }

    companion object {
        private const val PREFS_NAME = "sms_reaction_scenarios_v1"
        private const val KEY_JSON = "scenarios_json"
    }
}
