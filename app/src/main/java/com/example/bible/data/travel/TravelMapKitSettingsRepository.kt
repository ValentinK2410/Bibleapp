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
    val SPEED_HUD_OFF_X_DP = floatPreferencesKey("speed_hud_off_x_dp")
    val SPEED_HUD_OFF_Y_DP = floatPreferencesKey("speed_hud_off_y_dp")
    val PHOTO_HUD_OFF_X_DP = floatPreferencesKey("photo_hud_off_x_dp")
    val PHOTO_HUD_OFF_Y_DP = floatPreferencesKey("photo_hud_off_y_dp")
    val PHOTO_HUD_PANEL_SCALE = floatPreferencesKey("photo_hud_panel_scale")
}

internal const val TRAVEL_MAP_HUD_PANEL_SCALE_MIN = 0.52f
internal const val TRAVEL_MAP_HUD_PANEL_SCALE_MAX = 1.42f
internal const val TRAVEL_SPOT_ROUTE_PHOTO_FRAME_SCALE_MIN = 0.48f
internal const val TRAVEL_SPOT_ROUTE_PHOTO_FRAME_SCALE_MAX = 2.5f
internal const val TRAVEL_PHOTO_HUD_PANEL_SCALE_MIN = 0.48f
internal const val TRAVEL_PHOTO_HUD_PANEL_SCALE_MAX = 1.48f
/** Стартовое смещение окна фото по Y (dp), чтобы по умолчанию не перекрывать спидометр. */
internal const val TRAVEL_PHOTO_HUD_DEFAULT_OFF_Y_DP = 188f

private const val HUD_PANEL_OFFSET_ABS_MAX_DP = 2800f

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

    val speedHudOffsetXDp: Flow<Float> = app.travelMapKitSettingsDataStore.data.map { prefs ->
        (prefs[TravelMapKitKeys.SPEED_HUD_OFF_X_DP] ?: 0f).coerceIn(-HUD_PANEL_OFFSET_ABS_MAX_DP, HUD_PANEL_OFFSET_ABS_MAX_DP)
    }

    val speedHudOffsetYDp: Flow<Float> = app.travelMapKitSettingsDataStore.data.map { prefs ->
        (prefs[TravelMapKitKeys.SPEED_HUD_OFF_Y_DP] ?: 0f).coerceIn(-HUD_PANEL_OFFSET_ABS_MAX_DP, HUD_PANEL_OFFSET_ABS_MAX_DP)
    }

    val photoHudOffsetXDp: Flow<Float> = app.travelMapKitSettingsDataStore.data.map { prefs ->
        (prefs[TravelMapKitKeys.PHOTO_HUD_OFF_X_DP] ?: 0f).coerceIn(-HUD_PANEL_OFFSET_ABS_MAX_DP, HUD_PANEL_OFFSET_ABS_MAX_DP)
    }

    val photoHudOffsetYDp: Flow<Float> = app.travelMapKitSettingsDataStore.data.map { prefs ->
        (prefs[TravelMapKitKeys.PHOTO_HUD_OFF_Y_DP] ?: TRAVEL_PHOTO_HUD_DEFAULT_OFF_Y_DP)
            .coerceIn(-HUD_PANEL_OFFSET_ABS_MAX_DP, HUD_PANEL_OFFSET_ABS_MAX_DP)
    }

    val photoHudPanelScale: Flow<Float> = app.travelMapKitSettingsDataStore.data.map { prefs ->
        (prefs[TravelMapKitKeys.PHOTO_HUD_PANEL_SCALE] ?: 1f)
            .coerceIn(TRAVEL_PHOTO_HUD_PANEL_SCALE_MIN, TRAVEL_PHOTO_HUD_PANEL_SCALE_MAX)
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

    suspend fun setSpeedHudOffsets(xDp: Float, yDp: Float) {
        val x = xDp.coerceIn(-HUD_PANEL_OFFSET_ABS_MAX_DP, HUD_PANEL_OFFSET_ABS_MAX_DP)
        val y = yDp.coerceIn(-HUD_PANEL_OFFSET_ABS_MAX_DP, HUD_PANEL_OFFSET_ABS_MAX_DP)
        app.travelMapKitSettingsDataStore.edit { prefs ->
            prefs[TravelMapKitKeys.SPEED_HUD_OFF_X_DP] = x
            prefs[TravelMapKitKeys.SPEED_HUD_OFF_Y_DP] = y
        }
    }

    suspend fun setPhotoHudOffsets(xDp: Float, yDp: Float) {
        val x = xDp.coerceIn(-HUD_PANEL_OFFSET_ABS_MAX_DP, HUD_PANEL_OFFSET_ABS_MAX_DP)
        val y = yDp.coerceIn(-HUD_PANEL_OFFSET_ABS_MAX_DP, HUD_PANEL_OFFSET_ABS_MAX_DP)
        app.travelMapKitSettingsDataStore.edit { prefs ->
            prefs[TravelMapKitKeys.PHOTO_HUD_OFF_X_DP] = x
            prefs[TravelMapKitKeys.PHOTO_HUD_OFF_Y_DP] = y
        }
    }

    suspend fun setPhotoHudPanelScale(value: Float) {
        val v = value.coerceIn(TRAVEL_PHOTO_HUD_PANEL_SCALE_MIN, TRAVEL_PHOTO_HUD_PANEL_SCALE_MAX)
        app.travelMapKitSettingsDataStore.edit { prefs ->
            prefs[TravelMapKitKeys.PHOTO_HUD_PANEL_SCALE] = v
        }
    }
}
