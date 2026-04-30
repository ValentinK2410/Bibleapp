package com.example.bible.ui.travel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bible.BuildConfig
import com.example.bible.data.travel.FriendPeerLocation
import com.example.bible.data.travel.FriendPeerLocationRepository
import com.example.bible.data.travel.pollOnce
import com.example.bible.data.travel.TravelGeoPoint
import com.example.bible.data.travel.TravelGeofenceManager
import com.example.bible.data.travel.TravelMapIncident
import com.example.bible.data.travel.TravelMapKitSettingsRepository
import com.example.bible.data.travel.RoutePlaybackSimState
import com.example.bible.data.travel.TravelRoutePhotoSession
import com.example.bible.data.travel.TravelZone
import com.example.bible.data.travel.buildRoutePlaybackPolyline
import com.example.bible.data.travel.interpolateRoutePlayback
import com.example.bible.data.travel.routePlaybackPhotoUriAtDistance
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import coil.imageLoader
import coil.request.ImageRequest

enum class TravelMapEditMode {
    VIEW,
    CIRCLE_TAP,
    POLYGON_DRAW,
}

private const val ROUTE_PLAYBACK_SPEED_DEFAULT_MPS = 6f
private const val ROUTE_PLAYBACK_SPEED_MIN_MPS = 0.5f
private const val ROUTE_PLAYBACK_SPEED_MAX_MPS = 28f

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
    private val friendPeerRepo = FriendPeerLocationRepository(app)
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

    private val _routePlaybackSim = MutableStateFlow<RoutePlaybackSimState?>(null)
    val routePlaybackSim: StateFlow<RoutePlaybackSimState?> = _routePlaybackSim.asStateFlow()

    /** Скорость виртуального проезда по полилинии (м/с); применяется на каждом тике симуляции. */
    private val _routePlaybackSpeedMps = MutableStateFlow(ROUTE_PLAYBACK_SPEED_DEFAULT_MPS)
    val routePlaybackSpeedMps: StateFlow<Float> = _routePlaybackSpeedMps.asStateFlow()

    private var playbackSimJob: Job? = null

    private val _friendPeerLocationPoll = MutableStateFlow<FriendPeerLocation?>(null)
    private val _friendPeerLocationManual = MutableStateFlow<FriendPeerLocation?>(null)
    private var friendPeerPollJob: Job? = null

    val friendPeerPollUrl: StateFlow<String> = friendPeerRepo.pollUrl.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "",
    )
    val friendPeerPollIntervalSec: StateFlow<Int> = friendPeerRepo.pollIntervalSec.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        20,
    )
    val friendPeerPollEnabled: StateFlow<Boolean> = friendPeerRepo.pollEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    val friendPeerLocation: StateFlow<FriendPeerLocation?> = combine(
        _friendPeerLocationManual,
        _friendPeerLocationPoll,
    ) { manual, poll -> manual ?: poll }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
        viewModelScope.launch {
            combine(
                _routePlaybackActive,
                _routePlaybackSessionIndex,
                routePhotoSessions,
            ) { _, _, _ -> Unit }
                .collect {
                    if (_routePlaybackActive.value) {
                        restartRoutePlaybackSimulation()
                    } else {
                        playbackSimJob?.cancel()
                        playbackSimJob = null
                        _routePlaybackSim.value = null
                    }
                }
        }
        viewModelScope.launch {
            combine(
                friendPeerRepo.pollEnabled,
                friendPeerRepo.pollUrl,
                friendPeerRepo.pollIntervalSec,
            ) { _, _, _ -> Unit }
                .collect {
                    restartFriendPeerPolling()
                }
        }
    }

    private fun restartFriendPeerPolling() {
        friendPeerPollJob?.cancel()
        friendPeerPollJob = viewModelScope.launch {
            _friendPeerLocationPoll.value = null
            while (isActive) {
                val enabled = friendPeerRepo.pollEnabled.first()
                val url = friendPeerRepo.pollUrl.first().trim()
                if (!enabled || url.isEmpty() || !url.startsWith("https://")) {
                    return@launch
                }
                val snap = withContext(Dispatchers.IO) {
                    friendPeerRepo.pollOnce()
                }
                _friendPeerLocationPoll.value = snap
                val intervalSec = friendPeerRepo.pollIntervalSec.first().coerceIn(5, 300)
                delay(intervalSec * 1000L)
            }
        }
    }

    /** Зацикленная симуляция вдоль полилинии; скорость задаётся [routePlaybackSpeedMps]. */
    private fun restartRoutePlaybackSimulation() {
        playbackSimJob?.cancel()
        playbackSimJob = null
        if (!_routePlaybackActive.value) {
            _routePlaybackSim.value = null
            return
        }
        val sortedSessions = routePhotoSessions.value.sortedByDescending { it.createdAtMs }
        if (sortedSessions.isEmpty()) {
            _routePlaybackSim.value = null
            return
        }
        val idx = _routePlaybackSessionIndex.value % sortedSessions.size
        val session = sortedSessions[idx]
        val poly = buildRoutePlaybackPolyline(session.points) ?: run {
            _routePlaybackSim.value = null
            return
        }
        val total = poly.totalLengthM.coerceAtLeast(1f)
        playbackSimJob = viewModelScope.launch {
            launch(Dispatchers.IO) {
                val app = getApplication<Application>()
                val loader = app.imageLoader
                for (p in session.points) {
                    runCatching {
                        loader.enqueue(
                            ImageRequest.Builder(app)
                                .data(Uri.parse(p.photoUri))
                                .crossfade(false)
                                .build(),
                        )
                    }
                }
            }
            var dist = 0f
            while (isActive && _routePlaybackActive.value) {
                val speed = _routePlaybackSpeedMps.value.coerceIn(
                    ROUTE_PLAYBACK_SPEED_MIN_MPS,
                    ROUTE_PLAYBACK_SPEED_MAX_MPS,
                )
                val (lat, lon, bear) = interpolateRoutePlayback(poly, dist)
                val uri = routePlaybackPhotoUriAtDistance(poly, dist)
                _routePlaybackSim.value = RoutePlaybackSimState(
                    latitude = lat,
                    longitude = lon,
                    bearingDeg = bear,
                    progress = (dist / total).coerceIn(0f, 1f),
                    distanceAlongMeters = dist,
                    totalPathMeters = total,
                    currentPhotoUri = uri,
                )
                delay(33)
                if (!_routePlaybackActive.value) break
                dist += speed * 0.033f
                if (dist >= total) dist %= total
            }
        }
    }

    fun setRoutePlaybackSpeedMps(mps: Float) {
        _routePlaybackSpeedMps.value = mps.coerceIn(
            ROUTE_PLAYBACK_SPEED_MIN_MPS,
            ROUTE_PLAYBACK_SPEED_MAX_MPS,
        )
    }

    fun setFriendPeerPollUrl(url: String) {
        viewModelScope.launch { friendPeerRepo.setPollUrl(url) }
    }

    fun setFriendPeerPollIntervalSec(sec: Int) {
        viewModelScope.launch { friendPeerRepo.setPollIntervalSec(sec) }
    }

    fun setFriendPeerPollEnabled(enabled: Boolean) {
        viewModelScope.launch { friendPeerRepo.setPollEnabled(enabled) }
    }

    fun setFriendPeerManual(latitude: Double, longitude: Double, label: String?) {
        _friendPeerLocationManual.value = FriendPeerLocation(
            latitude = latitude,
            longitude = longitude,
            label = label?.trim()?.takeIf { it.isNotEmpty() },
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    fun clearFriendPeerManual() {
        _friendPeerLocationManual.value = null
    }

    fun centerMapOnFriendPeer() {
        val loc = friendPeerLocation.value ?: return
        setCameraJump(TravelGeoPoint(loc.latitude, loc.longitude))
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
    }

    fun cycleRoutePlaybackSession() {
        val n = routePhotoSessions.value.sortedByDescending { it.createdAtMs }.size
        if (n <= 1) return
        _routePlaybackSessionIndex.update { (it + 1) % n }
    }

    suspend fun saveRouteBurstSession(session: TravelRoutePhotoSession) {
        if (session.points.isEmpty()) return
        repo.addRoutePhotoSession(session)
    }

    fun deleteRoutePhotoSession(sessionId: String) {
        viewModelScope.launch {
            repo.removeRoutePhotoSession(sessionId)
            val remaining = repo.snapshotRoutePhotoSessions()
            _routePlaybackSessionIndex.update { idx ->
                if (remaining.isEmpty()) 0
                else idx.coerceIn(0, (remaining.size - 1).coerceAtLeast(0))
            }
        }
    }

    fun deleteAllRoutePhotoSessions() {
        viewModelScope.launch {
            repo.clearAllRoutePhotoSessions()
            playbackSimJob?.cancel()
            playbackSimJob = null
            _routePlaybackActive.value = false
            _routePlaybackSim.value = null
            _routePlaybackSessionIndex.value = 0
        }
    }

    fun removePhotosFromRouteSession(sessionId: String, photoUris: Set<String>) {
        if (photoUris.isEmpty()) return
        viewModelScope.launch {
            repo.removePhotoPointsFromSession(sessionId, photoUris)
        }
    }
}
