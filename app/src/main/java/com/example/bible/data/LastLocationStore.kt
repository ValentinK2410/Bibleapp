package com.example.bible.data

import android.content.Context
import android.os.Bundle

interface LastLocationStore {
    fun load(): LastLocation?
    fun save(location: LastLocation)
}

class SharedPrefsLastLocationStore(
    context: Context,
) : LastLocationStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): LastLocation? =
        LastLocation.fromJson(prefs.getString(KEY, "").orEmpty())

    override fun save(location: LastLocation) {
        prefs.edit().putString(KEY, location.toJson()).apply()
    }

    private companion object {
        const val PREFS = "last_app_location"
        const val KEY = "json"
    }
}

/** Имена плейсхолдеров маршрута → строки из Bundle Navigation (String, Int, Boolean, …). */
fun navArgumentsForLastLocation(routePattern: String, bundle: Bundle?): Map<String, String> {
    if (bundle == null) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (name in LastLocation.argumentNames(routePattern)) {
        if (!bundle.containsKey(name)) continue
        @Suppress("DEPRECATION")
        val value = bundle.get(name) ?: continue
        val text = when (value) {
            is String -> value
            is Boolean, is Int, is Long, is Float, is Double -> value.toString()
            else -> value.toString()
        }
        if (text.isNotBlank()) out[name] = text
    }
    return out
}
