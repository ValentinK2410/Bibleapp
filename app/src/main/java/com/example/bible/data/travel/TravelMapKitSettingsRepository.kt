package com.example.bible.data.travel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.travelMapKitSettingsDataStore by preferencesDataStore(name = "travel_mapkit_settings")

private object TravelMapKitKeys {
    val USER_MAPKIT_API_KEY = stringPreferencesKey("user_mapkit_api_key")
}

class TravelMapKitSettingsRepository(
    context: Context,
) {
    private val app = context.applicationContext

    val userMapKitApiKey: Flow<String> = app.travelMapKitSettingsDataStore.data.map { prefs ->
        prefs[TravelMapKitKeys.USER_MAPKIT_API_KEY]?.trim().orEmpty()
    }

    suspend fun setUserMapKitApiKey(value: String) {
        val trimmed = value.trim()
        app.travelMapKitSettingsDataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(TravelMapKitKeys.USER_MAPKIT_API_KEY)
            } else {
                prefs[TravelMapKitKeys.USER_MAPKIT_API_KEY] = trimmed
            }
        }
    }
}
