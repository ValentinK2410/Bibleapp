package com.example.bible.data.travel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.travelMapKitSettingsDataStore by preferencesDataStore(name = "travel_mapkit_settings")

private object TravelMapKitKeys {
    val USER_MAPKIT_API_KEY = stringPreferencesKey("user_mapkit_api_key")
    val MAP_HUD_PANEL_SCALE = floatPreferencesKey("map_hud_panel_scale")
    val SPOT_ROUTE_PHOTO_FRAME_SCALE = floatPreferencesKey("spot_route_photo_frame_scale")
}

internal const val TRAVEL_MAP_HUD_PANEL_SCALE_MIN = 0.52f
internal const val TRAVEL_MAP_HUD_PANEL_SCALE_MAX = 1.42f
internal const val TRAVEL_SPOT_ROUTE_PHOTO_FRAME_SCALE_MIN = 0.48f
internal const val TRAVEL_SPOT_ROUTE_PHOTO_FRAME_SCALE_MAX = 2.5f

class TravelMapKitSettingsRepository(
    context: Context,
) {
    private val app = context.applicationContext

    val userMapKitApiKey: Flow<String> = app.travelMapKitSettingsDataStore.data.map { prefs ->
        prefs[TravelMapKitKeys.USER_MAPKIT_API_KEY]?.trim().orEmpty()
    }

    val mapHudPanelScale: Flow<Float> = app.travelMapKitSettingsDataStore.data.map { prefs ->
        (prefs[TravelMapKitKeys.MAP_HUD_PANEL_SCALE] ?: 1f)
            .coerceIn(TRAVEL_MAP_HUD_PANEL_SCALE_MIN, TRAVEL_MAP_HUD_PANEL_SCALE_MAX)
    }

    val spotRoutePhotoFrameScale: Flow<Float> = app.travelMapKitSettingsDataStore.data.map { prefs ->
        (prefs[TravelMapKitKeys.SPOT_ROUTE_PHOTO_FRAME_SCALE] ?: 1f)
            .coerceIn(TRAVEL_SPOT_ROUTE_PHOTO_FRAME_SCALE_MIN, TRAVEL_SPOT_ROUTE_PHOTO_FRAME_SCALE_MAX)
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

    suspend fun setMapHudPanelScale(value: Float) {
        val v = value.coerceIn(TRAVEL_MAP_HUD_PANEL_SCALE_MIN, TRAVEL_MAP_HUD_PANEL_SCALE_MAX)
        app.travelMapKitSettingsDataStore.edit { prefs ->
            prefs[TravelMapKitKeys.MAP_HUD_PANEL_SCALE] = v
        }
    }

    suspend fun setSpotRoutePhotoFrameScale(value: Float) {
        val v = value.coerceIn(TRAVEL_SPOT_ROUTE_PHOTO_FRAME_SCALE_MIN, TRAVEL_SPOT_ROUTE_PHOTO_FRAME_SCALE_MAX)
        app.travelMapKitSettingsDataStore.edit { prefs ->
            prefs[TravelMapKitKeys.SPOT_ROUTE_PHOTO_FRAME_SCALE] = v
        }
    }
}
