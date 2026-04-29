package com.example.bible.data.travel

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.travelZonesDataStore by preferencesDataStore(name = "travel_zones")

private object TravelKeys {
    val ZONES_JSON = stringPreferencesKey("travel_zones_json")
    val POLYGON_MONITOR_ENABLED = booleanPreferencesKey("polygon_monitor_enabled")
    val POLYGON_VOICE_HINTS = booleanPreferencesKey("polygon_voice_hints")
    val TERRITORY_EDIT_ENABLED = booleanPreferencesKey("territory_edit_enabled")
    /** Кнопки и настройки зон под картой (круг, полигон, фон, меню «Редактировать территории»). */
    val TERRITORY_PANEL_BELOW_MAP = booleanPreferencesKey("territory_panel_below_map")
    val MAP_INCIDENTS_JSON = stringPreferencesKey("travel_map_incidents_json")
    /** Общий звук при входе в полигон (если у зоны нет своего PLAY_SOUND). */
    val POLYGON_ENTRY_SOUND_URI = stringPreferencesKey("travel_polygon_entry_sound_uri")
    /** Звук отметки по умолчанию, если у отметки не задан свой. */
    val MARKER_DEFAULT_SOUND_URI = stringPreferencesKey("travel_marker_default_sound_uri")
    /** Оповещать при GPS-приближении к отметкам (если задан звук). */
    val MARKER_PROXIMITY_ENABLED = booleanPreferencesKey("travel_marker_proximity_enabled")
    /** Серии фото по GPS-маршруту (JSON-массив сессий). */
    val ROUTE_PHOTO_SESSIONS_JSON = stringPreferencesKey("travel_route_photo_sessions_json")
}

class TravelZoneRepository(
    context: Context,
) {
    private val app = context.applicationContext

    val zones: Flow<List<TravelZone>> = app.travelZonesDataStore.data.map { prefs ->
        TravelZone.parseList(prefs[TravelKeys.ZONES_JSON] ?: "[]")
    }

    val polygonMonitorEnabled: Flow<Boolean> = app.travelZonesDataStore.data.map { prefs ->
        prefs[TravelKeys.POLYGON_MONITOR_ENABLED] ?: false
    }

    /** Озвучивать вход в полигон (по умолчанию включено). */
    val polygonVoiceHintsEnabled: Flow<Boolean> = app.travelZonesDataStore.data.map { prefs ->
        prefs[TravelKeys.POLYGON_VOICE_HINTS] ?: true
    }

    /** Режим «Редактировать территории» на карте. */
    val territoryEditEnabled: Flow<Boolean> = app.travelZonesDataStore.data.map { prefs ->
        prefs[TravelKeys.TERRITORY_EDIT_ENABLED] ?: false
    }

    /** Показывать панель управления зонами под картой. */
    val territoryPanelBelowMap: Flow<Boolean> = app.travelZonesDataStore.data.map { prefs ->
        prefs[TravelKeys.TERRITORY_PANEL_BELOW_MAP] ?: false
    }

    val mapIncidents: Flow<List<TravelMapIncident>> = app.travelZonesDataStore.data.map { prefs ->
        TravelMapIncident.parseList(prefs[TravelKeys.MAP_INCIDENTS_JSON] ?: "[]")
    }

    val polygonEntrySoundUri: Flow<String?> = app.travelZonesDataStore.data.map { prefs ->
        prefs[TravelKeys.POLYGON_ENTRY_SOUND_URI]?.trim()?.takeIf { it.isNotBlank() }
    }

    val markerDefaultSoundUri: Flow<String?> = app.travelZonesDataStore.data.map { prefs ->
        prefs[TravelKeys.MARKER_DEFAULT_SOUND_URI]?.trim()?.takeIf { it.isNotBlank() }
    }

    val markerProximityEnabled: Flow<Boolean> = app.travelZonesDataStore.data.map { prefs ->
        prefs[TravelKeys.MARKER_PROXIMITY_ENABLED] ?: true
    }

    val routePhotoSessions: Flow<List<TravelRoutePhotoSession>> = app.travelZonesDataStore.data.map { prefs ->
        TravelRoutePhotoSession.parseList(prefs[TravelKeys.ROUTE_PHOTO_SESSIONS_JSON] ?: "[]")
    }

    suspend fun snapshotRoutePhotoSessions(): List<TravelRoutePhotoSession> =
        routePhotoSessions.first()

    suspend fun saveRoutePhotoSessions(list: List<TravelRoutePhotoSession>) {
        app.travelZonesDataStore.edit { prefs ->
            prefs[TravelKeys.ROUTE_PHOTO_SESSIONS_JSON] = TravelRoutePhotoSession.toJsonArray(list)
        }
    }

    suspend fun addRoutePhotoSession(session: TravelRoutePhotoSession) {
        val cur = snapshotRoutePhotoSessions()
        saveRoutePhotoSessions(cur + session)
    }

    suspend fun removeRoutePhotoSession(id: String) {
        val cur = snapshotRoutePhotoSessions()
        TravelPhotoStorage.deleteRouteSessionDir(app, id)
        saveRoutePhotoSessions(cur.filter { it.id != id })
    }

    suspend fun replaceRoutePhotoSession(updated: TravelRoutePhotoSession) {
        val cur = snapshotRoutePhotoSessions()
        saveRoutePhotoSessions(cur.map { if (it.id == updated.id) updated else it })
    }

    suspend fun snapshot(): List<TravelZone> = zones.first()

    suspend fun snapshotIncidents(): List<TravelMapIncident> = mapIncidents.first()

    suspend fun saveZones(list: List<TravelZone>) {
        app.travelZonesDataStore.edit { prefs ->
            prefs[TravelKeys.ZONES_JSON] = TravelZone.toJsonArray(list)
        }
    }

    suspend fun replace(zone: TravelZone) {
        val cur = snapshot()
        saveZones(cur.filter { it.id != zone.id } + zone)
    }

    suspend fun remove(id: String) {
        saveZones(snapshot().filter { it.id != id })
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        saveZones(snapshot().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    suspend fun setPolygonMonitorEnabled(enabled: Boolean) {
        app.travelZonesDataStore.edit { prefs ->
            prefs[TravelKeys.POLYGON_MONITOR_ENABLED] = enabled
        }
    }

    suspend fun setPolygonVoiceHintsEnabled(enabled: Boolean) {
        app.travelZonesDataStore.edit { prefs ->
            prefs[TravelKeys.POLYGON_VOICE_HINTS] = enabled
        }
    }

    suspend fun setTerritoryEditEnabled(enabled: Boolean) {
        app.travelZonesDataStore.edit { prefs ->
            prefs[TravelKeys.TERRITORY_EDIT_ENABLED] = enabled
        }
    }

    suspend fun setTerritoryPanelBelowMap(visible: Boolean) {
        app.travelZonesDataStore.edit { prefs ->
            prefs[TravelKeys.TERRITORY_PANEL_BELOW_MAP] = visible
        }
    }

    suspend fun addMapIncident(incident: TravelMapIncident) {
        val cur = snapshotIncidents()
        app.travelZonesDataStore.edit { prefs ->
            prefs[TravelKeys.MAP_INCIDENTS_JSON] = TravelMapIncident.toJsonArray(cur + incident)
        }
    }

    suspend fun removeMapIncident(id: String) {
        val cur = snapshotIncidents()
        app.travelZonesDataStore.edit { prefs ->
            prefs[TravelKeys.MAP_INCIDENTS_JSON] = TravelMapIncident.toJsonArray(cur.filter { it.id != id })
        }
    }

    suspend fun clearMapIncidents() {
        app.travelZonesDataStore.edit { prefs ->
            prefs[TravelKeys.MAP_INCIDENTS_JSON] = "[]"
        }
    }

    suspend fun replaceMapIncident(updated: TravelMapIncident) {
        val cur = snapshotIncidents()
        saveMapIncidents(cur.map { if (it.id == updated.id) updated else it })
    }

    suspend fun saveMapIncidents(list: List<TravelMapIncident>) {
        app.travelZonesDataStore.edit { prefs ->
            prefs[TravelKeys.MAP_INCIDENTS_JSON] = TravelMapIncident.toJsonArray(list)
        }
    }

    suspend fun setPolygonEntrySoundUri(uri: String?) {
        app.travelZonesDataStore.edit { prefs ->
            if (uri.isNullOrBlank()) {
                prefs.remove(TravelKeys.POLYGON_ENTRY_SOUND_URI)
            } else {
                prefs[TravelKeys.POLYGON_ENTRY_SOUND_URI] = uri
            }
        }
    }

    suspend fun setMarkerDefaultSoundUri(uri: String?) {
        app.travelZonesDataStore.edit { prefs ->
            if (uri.isNullOrBlank()) {
                prefs.remove(TravelKeys.MARKER_DEFAULT_SOUND_URI)
            } else {
                prefs[TravelKeys.MARKER_DEFAULT_SOUND_URI] = uri
            }
        }
    }

    suspend fun setMarkerProximityEnabled(enabled: Boolean) {
        app.travelZonesDataStore.edit { prefs ->
            prefs[TravelKeys.MARKER_PROXIMITY_ENABLED] = enabled
        }
    }
}
