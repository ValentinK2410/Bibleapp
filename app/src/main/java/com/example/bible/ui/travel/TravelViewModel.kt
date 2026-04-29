package com.example.bible.ui.travel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bible.BuildConfig
import com.example.bible.data.travel.TravelGeoPoint
import com.example.bible.data.travel.TravelGeofenceManager
import com.example.bible.data.travel.TravelMapIncident
import com.example.bible.data.travel.TravelMapKitSettingsRepository
import com.example.bible.data.travel.TravelRoutePhotoSession
import com.example.bible.data.travel.TravelZone
import com.example.bible.data.travel.TravelZoneKind
import com.example.bible.data.travel.TravelZoneRepository
import com.example.bible.map.MapKitBootstrap
import com.example.bible.service.TravelMonitorService
import com.yandex.mapkit.directions.driving.DrivingRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TravelMapEditMode {
    VIEW,
    CIRCLE_TAP,
    POLYGON_DRAW,
}

data class TravelSavedMapCamera(
    val latitude: Double,
    val longitude: Double,
    val zoom: Float,
    val azimuth: Float,
    val tilt: Float,
)

sealed class TravelPendingZoneSave {
    data class CircleZone(val center: TravelGeoPoint, val radius: Float) : TravelPendingZoneSave()
    data class PolygonZone(val points: List<TravelGeoPoint>) : TravelPendingZoneSave()
}

class TravelViewModel(
    app: Application,
) : AndroidViewModel(app) {

    private val repo = TravelZoneRepository(app)
    private val mapKitSettings = TravelMapKitSettingsRepository(app)

    val mapKitApiKeyForMap: StateFlow<String> = mapKitSettings.userMapKitApiKey
        .map { user -> user.ifBlank { BuildConfig.MAPKIT_API_KEY } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BuildConfig.MAPKIT_API_KEY,
        )

    val userMapKitKeyStored: StateFlow<String> = mapKitSettings.userMapKitApiKey.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "",
    )

    val zones: StateFlow<List<TravelZone>> = repo.zones.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val polygonMonitorEnabled: StateFlow<Boolean> = repo.polygonMonitorEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    val polygonVoiceHintsEnabled: StateFlow<Boolean> = repo.polygonVoiceHintsEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true,
    )

    val territoryEditEnabled: StateFlow<Boolean> = repo.territoryEditEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    val territoryPanelBelowMap: StateFlow<Boolean> = repo.territoryPanelBelowMap.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    private val _mapCameraSnapshot = MutableStateFlow<TravelSavedMapCamera?>(null)
    val mapCameraSnapshot: StateFlow<TravelSavedMapCamera?> = _mapCameraSnapshot.asStateFlow()

    private val _editMode = MutableStateFlow(TravelMapEditMode.VIEW)
    val editMode: StateFlow<TravelMapEditMode> = _editMode.asStateFlow()

    private val _polygonDraft = MutableStateFlow<List<TravelGeoPoint>>(emptyList())
    val polygonDraft: StateFlow<List<TravelGeoPoint>> = _polygonDraft.asStateFlow()

    private val _cameraJumpTo = MutableStateFlow<TravelGeoPoint?>(null)
    val cameraJumpTo: StateFlow<TravelGeoPoint?> = _cameraJumpTo.asStateFlow()

    private val _pendingSave = MutableStateFlow<TravelPendingZoneSave?>(null)
    val pendingSave: StateFlow<TravelPendingZoneSave?> = _pendingSave.asStateFlow()

    private val _travelMenuExpanded = MutableStateFlow(false)
    val travelMenuExpanded: StateFlow<Boolean> = _travelMenuExpanded.asStateFlow()

    private val _showMapKitSettings = MutableStateFlow(false)
    val showMapKitSettings: StateFlow<Boolean> = _showMapKitSettings.asStateFlow()

    private val _showListSheet = MutableStateFlow(false)
    val showListSheet: StateFlow<Boolean> = _showListSheet.asStateFlow()

    private val _selectedZoneIdForEdit = MutableStateFlow<String?>(null)
    val selectedZoneIdForEdit: StateFlow<String?> = _selectedZoneIdForEdit.asStateFlow()

    private val _pendingCircleRecenterZoneId = MutableStateFlow<String?>(null)
    val pendingCircleRecenterZoneId: StateFlow<String?> = _pendingCircleRecenterZoneId.asStateFlow()

    /** Если не null, черновик полигона относится к правке существующей зоны с этим id. */
    private val _polygonRedraftZoneId = MutableStateFlow<String?>(null)
    val polygonRedraftZoneId: StateFlow<String?> = _polygonRedraftZoneId.asStateFlow()

    private val _zonePropertiesEditId = MutableStateFlow<String?>(null)
    val zonePropertiesEditId: StateFlow<String?> = _zonePropertiesEditId.asStateFlow()

    /** Режим: следующий тап по карте — конечная точка автомаршрута от текущих координат. */
    private val _routePickDestination = MutableStateFlow(false)
    val routePickDestination: StateFlow<Boolean> = _routePickDestination.asStateFlow()

    /**
     * Построенный маршрут (онлайн): храним в ViewModel, чтобы не терять при смене ориентации
     * и пересоздании [com.yandex.mapkit.mapview.MapView].
     */
    private val _activeTravelRoute = MutableStateFlow<DrivingRoute?>(null)
    val activeTravelRoute: StateFlow<DrivingRoute?> = _activeTravelRoute.asStateFlow()

    fun setActiveTravelRoute(route: DrivingRoute?) {
        _activeTravelRoute.value = route
    }

    /** Увеличить, чтобы сбросить отрисованный маршрут и отменить запрос. */
    private val _travelRouteClearNonce = MutableStateFlow(0L)
    val travelRouteClearNonce: StateFlow<Long> = _travelRouteClearNonce.asStateFlow()

    val mapIncidents: StateFlow<List<TravelMapIncident>> = repo.mapIncidents.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val routePhotoSessions: StateFlow<List<TravelRoutePhotoSession>> = repo.routePhotoSessions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val _lastUserGeo = MutableStateFlow<TravelGeoPoint?>(null)
    val lastUserGeo: StateFlow<TravelGeoPoint?> = _lastUserGeo.asStateFlow()

    /** Азимут «куда смотрит» пользователь (0° — север), с [android.hardware.Sensor.TYPE_ROTATION_VECTOR] при просмотре/съёмке. */
    private val _lastUserHeadingDeg = MutableStateFlow<Float?>(null)
    val lastUserHeadingDeg: StateFlow<Float?> = _lastUserHeadingDeg.asStateFlow()

    private val _routeBurstActive = MutableStateFlow(false)
    val routeBurstActive: StateFlow<Boolean> = _routeBurstActive.asStateFlow()

    private val _routePlaybackActive = MutableStateFlow(false)
    val routePlaybackActive: StateFlow<Boolean> = _routePlaybackActive.asStateFlow()

    /** Индекс сессии среди списка отсортированных по времени (новые первые). */
    private val _routePlaybackSessionIndex = MutableStateFlow(0)
    val routePlaybackSessionIndex: StateFlow<Int> = _routePlaybackSessionIndex.asStateFlow()

    val polygonEntrySoundUri: StateFlow<String?> = repo.polygonEntrySoundUri.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    val markerDefaultSoundUri: StateFlow<String?> = repo.markerDefaultSoundUri.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    val markerProximityEnabled: StateFlow<Boolean> = repo.markerProximityEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true,
    )

    private val _incidentPlaceMode = MutableStateFlow(false)
    val incidentPlaceMode: StateFlow<Boolean> = _incidentPlaceMode.asStateFlow()

    private val _showMarkersEditSheet = MutableStateFlow(false)
    val showMarkersEditSheet: StateFlow<Boolean> = _showMarkersEditSheet.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.zones,
                repo.polygonMonitorEnabled,
                repo.mapIncidents,
                repo.markerProximityEnabled,
                repo.markerDefaultSoundUri,
            ) { zones, polyMon, incidents, markerProx, markerDefSound ->
                TravelGeofenceManager.sync(getApplication(), zones)
                val hasPoly = zones.any {
                    it.enabled && it.kind == TravelZoneKind.POLYGON && it.polygonPoints.size >= 3
                }
                val polyMonitorWanted = polyMon && hasPoly
                val markerAlertsWanted = markerProx && (
                    !markerDefSound.isNullOrBlank() ||
                        incidents.any { !it.soundUri.isNullOrBlank() }
                    )
                if (polyMonitorWanted || markerAlertsWanted) {
                    TravelMonitorService.start(getApplication())
                } else {
                    TravelMonitorService.stop(getApplication())
                }
            }.collect { }
        }
    }

    fun setShowMarkersEditSheet(show: Boolean) {
        _showMarkersEditSheet.value = show
    }

    fun persistMapCamera(camera: TravelSavedMapCamera) {
        _mapCameraSnapshot.value = camera
    }

    fun setTravelMenuExpanded(value: Boolean) {
        _travelMenuExpanded.value = value
    }

    fun setShowMapKitSettings(value: Boolean) {
        _showMapKitSettings.value = value
    }

    fun setShowListSheet(value: Boolean) {
        _showListSheet.value = value
    }

    fun setRoutePickMode(active: Boolean) {
        if (active) {
            _incidentPlaceMode.value = false
        }
        _routePickDestination.value = active
    }

    fun clearTravelRoute() {
        _activeTravelRoute.value = null
        _travelRouteClearNonce.update { it + 1L }
        _routePickDestination.value = false
    }

    fun onTravelRouteBuilt() {
        _routePickDestination.value = false
    }

    fun setIncidentPlaceMode(active: Boolean) {
        if (active) {
            _routePickDestination.value = false
        }
        _incidentPlaceMode.value = active
    }

    fun addMapIncidentAt(point: TravelGeoPoint, note: String = "") {
        viewModelScope.launch {
            repo.addMapIncident(
                TravelMapIncident(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    note = note.trim(),
                ),
            )
        }
        _incidentPlaceMode.value = false
    }

    fun removeMapIncident(id: String) {
        viewModelScope.launch {
            repo.removeMapIncident(id)
        }
    }

    fun clearMapIncidents() {
        viewModelScope.launch {
            repo.clearMapIncidents()
        }
    }

    fun replaceMapIncident(incident: TravelMapIncident) {
        viewModelScope.launch {
            repo.replaceMapIncident(incident)
        }
    }

    fun setPolygonEntrySoundUri(uri: String?) {
        viewModelScope.launch {
            repo.setPolygonEntrySoundUri(uri)
        }
    }

    fun setMarkerDefaultSoundUri(uri: String?) {
        viewModelScope.launch {
            repo.setMarkerDefaultSoundUri(uri)
        }
    }

    fun setMarkerProximityEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repo.setMarkerProximityEnabled(enabled)
        }
    }

    fun toggleCircleDrawMode() {
        if (territoryEditEnabled.value) return
        _editMode.update { cur ->
            if (cur == TravelMapEditMode.CIRCLE_TAP) TravelMapEditMode.VIEW else TravelMapEditMode.CIRCLE_TAP
        }
        _polygonDraft.value = emptyList()
    }

    fun togglePolygonDrawMode() {
        if (territoryEditEnabled.value) return
        _editMode.update { cur ->
            if (cur == TravelMapEditMode.POLYGON_DRAW) TravelMapEditMode.VIEW else TravelMapEditMode.POLYGON_DRAW
        }
        _polygonDraft.value = emptyList()
    }

    fun setTerritoryEditEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                _editMode.value = TravelMapEditMode.VIEW
                _polygonDraft.value = emptyList()
                _pendingSave.value = null
                _polygonRedraftZoneId.value = null
                _pendingCircleRecenterZoneId.value = null
            } else {
                _selectedZoneIdForEdit.value = null
                _pendingCircleRecenterZoneId.value = null
                _polygonRedraftZoneId.value = null
                _polygonDraft.value = emptyList()
                _editMode.value = TravelMapEditMode.VIEW
            }
            repo.setTerritoryEditEnabled(enabled)
        }
    }

    fun setTerritoryPanelBelowMap(visible: Boolean) {
        viewModelScope.launch {
            repo.setTerritoryPanelBelowMap(visible)
            if (!visible) {
                repo.setTerritoryEditEnabled(false)
                _selectedZoneIdForEdit.value = null
                _pendingCircleRecenterZoneId.value = null
                _polygonRedraftZoneId.value = null
                _polygonDraft.value = emptyList()
                _pendingSave.value = null
                _editMode.value = TravelMapEditMode.VIEW
            }
        }
    }

    fun selectZoneForEdit(zoneId: String?) {
        _selectedZoneIdForEdit.value = zoneId
        _pendingCircleRecenterZoneId.value = null
    }

    fun setPendingCircleRecenter(zoneId: String?) {
        _pendingCircleRecenterZoneId.value = zoneId
    }

    fun applyCircleRecenter(zoneId: String, center: TravelGeoPoint) {
        viewModelScope.launch {
            val z = repo.snapshot().find { it.id == zoneId } ?: return@launch
            if (z.kind != TravelZoneKind.CIRCLE) return@launch
            repo.replace(
                z.copy(centerLat = center.latitude, centerLng = center.longitude),
            )
            _pendingCircleRecenterZoneId.value = null
        }
    }

    fun updateZoneRadius(zoneId: String, radiusMeters: Float) {
        viewModelScope.launch {
            val z = repo.snapshot().find { it.id == zoneId } ?: return@launch
            if (z.kind != TravelZoneKind.CIRCLE) return@launch
            repo.replace(z.copy(radiusMeters = maxOf(radiusMeters, 100f)))
        }
    }

    fun beginPolygonRedraft(zoneId: String) {
        viewModelScope.launch {
            val z = repo.snapshot().find { it.id == zoneId } ?: return@launch
            if (z.kind != TravelZoneKind.POLYGON || z.polygonPoints.size < 3) return@launch
            _polygonRedraftZoneId.value = zoneId
            _polygonDraft.value = emptyList()
            _editMode.value = TravelMapEditMode.POLYGON_DRAW
            _selectedZoneIdForEdit.value = null
        }
    }

    fun applyPolygonRedraft() {
        viewModelScope.launch {
            val zoneId = _polygonRedraftZoneId.value ?: return@launch
            val draft = _polygonDraft.value
            if (draft.size < 3) return@launch
            val z = repo.snapshot().find { it.id == zoneId } ?: return@launch
            val cLat = draft.map { it.latitude }.average()
            val cLng = draft.map { it.longitude }.average()
            repo.replace(
                z.copy(
                    centerLat = cLat,
                    centerLng = cLng,
                    polygonPoints = draft,
                ),
            )
            _polygonRedraftZoneId.value = null
            _polygonDraft.value = emptyList()
            _editMode.value = TravelMapEditMode.VIEW
        }
    }

    fun cancelPolygonRedraft() {
        _polygonRedraftZoneId.value = null
        _polygonDraft.value = emptyList()
        _editMode.value = TravelMapEditMode.VIEW
    }

    fun openZoneProperties(zoneId: String) {
        _zonePropertiesEditId.value = zoneId
    }

    fun closeZoneProperties() {
        _zonePropertiesEditId.value = null
    }

    fun addPolygonDraftPoint(point: TravelGeoPoint) {
        _polygonDraft.update { it + point }
    }

    fun setPendingSave(pending: TravelPendingZoneSave?) {
        _pendingSave.value = pending
    }

    fun setCameraJump(point: TravelGeoPoint?) {
        _cameraJumpTo.value = point
    }

    fun consumeCameraJump() {
        _cameraJumpTo.value = null
    }

    fun clearPolygonDraftAndViewMode() {
        _polygonDraft.value = emptyList()
        _polygonRedraftZoneId.value = null
        _editMode.value = TravelMapEditMode.VIEW
    }

    fun setPolygonMonitor(enabled: Boolean) {
        viewModelScope.launch {
            repo.setPolygonMonitorEnabled(enabled)
        }
    }

    fun setPolygonVoiceHints(enabled: Boolean) {
        viewModelScope.launch {
            repo.setPolygonVoiceHintsEnabled(enabled)
        }
    }

    fun saveZone(zone: TravelZone) {
        viewModelScope.launch {
            repo.replace(zone)
        }
    }

    fun removeZone(id: String) {
        viewModelScope.launch {
            repo.remove(id)
        }
    }

    fun setZoneEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repo.setEnabled(id, enabled)
        }
    }

    /**
     * @return true если MapKit уже был инициализирован и эффективный ключ изменился —
     *         для применения нового ключа нужен перезапуск приложения.
     */
    suspend fun saveMapKitUserKey(typed: String): Boolean {
        val oldEffective = mapKitSettings.userMapKitApiKey.first()
            .ifBlank { BuildConfig.MAPKIT_API_KEY }
        val newEffective = typed.trim().ifBlank { BuildConfig.MAPKIT_API_KEY }
        mapKitSettings.setUserMapKitApiKey(typed)
        return MapKitBootstrap.isReady && oldEffective != newEffective
    }

    fun reportUserLocation(latitude: Double, longitude: Double) {
        _lastUserGeo.value = TravelGeoPoint(latitude, longitude)
    }

    fun reportUserHeading(degrees: Float) {
        if (!degrees.isFinite()) return
        var v = degrees % 360f
        if (v < 0f) v += 360f
        _lastUserHeadingDeg.value = v
    }

    fun clearUserHeading() {
        _lastUserHeadingDeg.value = null
    }

    fun setRouteBurstActive(active: Boolean) {
        _routeBurstActive.value = active
    }

    fun setRoutePlaybackActive(active: Boolean) {
        _routePlaybackActive.value = active
        if (!active) {
            _routePlaybackSessionIndex.value = 0
        }
    }

    fun cycleRoutePlaybackSession() {
        val n = routePhotoSessions.value.size
        if (n <= 1) return
        _routePlaybackSessionIndex.update { (it + 1) % n }
    }

    suspend fun saveRouteBurstSession(session: TravelRoutePhotoSession) {
        if (session.points.isEmpty()) return
        repo.addRoutePhotoSession(session)
    }
}
