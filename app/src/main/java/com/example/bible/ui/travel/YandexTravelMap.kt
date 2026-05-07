package com.example.bible.ui.travel

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.os.SystemClock
import android.view.Choreographer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.bible.R
import com.example.bible.data.travel.TravelGeoPoint
import com.example.bible.data.travel.TravelHazardMapKind
import com.example.bible.data.travel.TravelManeuverInfo
import com.example.bible.data.travel.TravelManeuvers
import com.example.bible.data.travel.TravelMapIncident
import com.example.bible.data.travel.TravelNavHudState
import com.example.bible.data.travel.TravelRouteGuidanceSession
import com.example.bible.data.travel.TravelTriggerAction
import com.example.bible.data.travel.TravelZone
import com.example.bible.data.travel.TravelZoneKind
import com.example.bible.data.travel.TRAVEL_ZONE_CIRCLE_RADIUS_MIN_M
import com.example.bible.data.travel.FriendPeerLocation
import com.example.bible.data.travel.RoutePlaybackPolyline
import com.example.bible.data.travel.RoutePlaybackSimState
import com.example.bible.data.travel.TravelRoutePhotoSession
import com.example.bible.data.travel.TripHistoryReplayPose
import com.example.bible.data.travel.TravelTripTrackPoint
import com.example.bible.data.travel.tripTrackSegmentSpeedKmhForDisplay
import com.example.bible.data.travel.tripTrackSpeedKmhToArgb
import com.example.bible.data.travel.TRAVEL_MAP_HUD_PANEL_SCALE_MAX
import com.example.bible.data.travel.TRAVEL_MAP_HUD_PANEL_SCALE_MIN
import com.example.bible.data.travel.TravelRoutePhotoPoint
import com.example.bible.data.travel.bearingDegForRoutePhotoMapOrNull
import com.example.bible.data.travel.bearingDegreesLatLon
import com.example.bible.data.travel.distanceMetersToRoutePolyline
import com.example.bible.data.travel.buildRoutePhotoDirectionSegments
import com.example.bible.data.travel.interpolateRoutePlayback
import com.example.bible.data.travel.nearestDistanceAlongPolyline
import com.example.bible.map.MapKitBootstrap
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yandex.mapkit.Animation
import com.yandex.mapkit.ScreenPoint
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.DrivingOptions
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.directions.driving.DrivingRouterType
import com.yandex.mapkit.directions.driving.DrivingSession
import com.yandex.mapkit.directions.driving.VehicleOptions
import com.yandex.mapkit.geometry.Circle
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.LinearRing
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.geometry.Polygon
import com.yandex.mapkit.location.LocationListener as YandexLocationListener
import com.yandex.mapkit.location.LocationStatus
import com.yandex.mapkit.location.LocationViewSourceFactory
import com.yandex.mapkit.location.Purpose
import com.yandex.mapkit.location.SubscriptionSettings
import com.yandex.mapkit.location.UseInBackground
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.map.RotationType
import com.yandex.mapkit.map.TextStyle
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.Error
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.coroutines.coroutineContext

/** Интервал запроса к Fused Location при следовании (реальная частота часто ~1 Гц на телефоне). */
private const val TRAVEL_FOLLOW_LOCATION_INTERVAL_MS = 33L
/**
 * Сглаживание координат в тике GPS при малой скорости (пешком / стоянка) — без выбросов.
 * При движении доля к новой точке резко повышается, а между фиксами позиция догоняется экстраполяцией в [Choreographer].
 */
private const val TRAVEL_CAMERA_POS_SMOOTH_SLOW = 0.42f
/** λ (1/с) для экспоненциального сближения «экранной» камеры с целью GPS за один кадр (выше — меньше отставание от экстраполированной точки). */
private const val TRAVEL_FOLLOW_DISPLAY_POS_LAMBDA = 28.0
/** Меньше λ — экранный зум медленнее тянется к цели (меньше рывков при авто-сближении/отдалении). */
private const val TRAVEL_FOLLOW_DISPLAY_ZOOM_LAMBDA = 8.5
private const val TRAVEL_FOLLOW_DISPLAY_ANGLE_LAMBDA = 18.0
/** Максимум метров «вперёд» по последнему курсу между двумя фиксами GPS (страховка от скачков часов). */
private const val TRAVEL_FOLLOW_MAX_EXTRAPOLATE_M = 55f
/** Макс. «возраст» последней фиксации для экстраполяции — должен перекрывать типичный интервал GPS (~1 с). */
private const val TRAVEL_FOLLOW_MAX_GPS_AGE_SEC = 1.05
/** Наклон в режиме навигатора, как у наклонённой 3D-карты. */
private const val TRAVEL_NAV_TARGET_TILT = 52f
private const val TRAVEL_NAV_TILT_SMOOTH = 0.10f
/** Шаг между стрелками направления движения вдоль полилинии маршрута (м). */
private const val TRAVEL_ROUTE_LANE_ARROW_SPACING_M = 38f

/** Во сколько раз больше минимальной иконки делается bitmap маркера (прозрачные поля) — палец можно схватить не только за оранжевый кружок. */
private const val ROUTE_WALKER_TOUCH_BITMAP_FACTOR = 2.85f

/** Макс. отступ от линии трека (м), при котором жест перехватывается для «перемотки» человечка. */
private const val ROUTE_WALKER_SCRUB_CORRIDOR_M = 55f

/** Радиус (в dp) вокруг маркера — жест там тоже начинает перемотку (не только по линии). */
private const val ROUTE_WALKER_SCRUB_NEAR_MARKER_DP = 130f

private data class MapWalkerGesturesBackup(
    val scroll: Boolean,
    val zoom: Boolean,
    val rotate: Boolean,
    val tilt: Boolean,
)

private class RouteWalkerScrubGestureHolder {
    var active: Boolean = false
    var backup: MapWalkerGesturesBackup? = null
}

/**
 * Вертикальный охват в метрах для плоского вида сверху (оценка по Web Mercator и зуму MapKit);
 * сопоставима с «высотой» виртуальной камеры над плоскостью карты.
 */
private fun travelVerticalViewSpanMeters(
    mapView: MapView,
    zoom: Float,
    centerLatDeg: Double,
): Int {
    val hPx = when {
        mapView.height > 0 -> mapView.height
        else -> 800
    }
    if (zoom <= 0.5f) return 0
    val mpp = 156543.03392 * cos(Math.toRadians(centerLatDeg)) / 2.0.pow(zoom.toDouble())
    return (hPx * mpp).roundToInt().coerceIn(0, 1_000_000)
}

/**
 * Подбор зума MapKit под заданный вертикальный охват (м) и широту.
 * 2^z = 156543.03392 * cos(lat) * hPx / vSpan
 */
private fun zoomForViewSpanMeters(
    mapView: MapView,
    centerLatDeg: Double,
    verticalSpanM: Double,
): Float {
    val hPx = (if (mapView.height > 0) mapView.height else 800).toDouble().coerceAtLeast(1.0)
    if (verticalSpanM < 1.0) return 15f
    val cosLat = cos(Math.toRadians(centerLatDeg))
    val twoPowZ = 156543.03392 * cosLat * hPx / verticalSpanM
    if (twoPowZ <= 0) return 15f
    val z = ln(twoPowZ) / ln(2.0)
    return z.toFloat().coerceIn(2f, 20.5f)
}

/**
 * Целевой вертикальный охват по скорости без резких ступеней (ступени давали рывки зума у порогов ~58 км/ч).
 */
private fun targetViewSpanMetersSmooth(speedKmh: Float): Double {
    val v = speedKmh.coerceIn(0f, 140f)
    fun lerp(a: Double, b: Double, t: Float): Double =
        a + (b - a) * t.toDouble().coerceIn(0.0, 1.0)
    return when {
        v < 12f -> lerp(200.0, 400.0, v / 12f)
        v < 54f -> 400.0
        v < 70f -> lerp(400.0, 1100.0, (v - 54f) / 16f)
        v < 98f -> lerp(1100.0, 1500.0, (v - 70f) / 28f)
        else -> lerp(1500.0, 2000.0, ((v - 98f) / 42f).coerceIn(0f, 1f))
    }
}

private const val USER_CHOSEN_MIN_SPAN_PRESERVE_M = 2000
private const val RECENTER_VIEW_SPAN_M = 900.0

private const val EARTH_RADIUS_M = 6371009.0

/**
 * Смещает точку на [distanceM] по азимуту [bearingDeg] (0° — север, по часовой стрелке).
 * Короткие дистанции — достаточно плоской модели для плавного догона между тиками GPS.
 */
private fun extrapolateLatLon(
    latDeg: Double,
    lonDeg: Double,
    bearingDeg: Float,
    distanceM: Float,
): Pair<Double, Double> {
    if (distanceM <= 0f) return latDeg to lonDeg
    val d = distanceM.coerceAtMost(TRAVEL_FOLLOW_MAX_EXTRAPOLATE_M).toDouble()
    val br = Math.toRadians(bearingDeg.toDouble())
    val latRad = Math.toRadians(latDeg)
    val north = d * cos(br)
    val east = d * sin(br)
    val dLat = north / EARTH_RADIUS_M * (180.0 / Math.PI)
    val dLon = east / (EARTH_RADIUS_M * cos(latRad).coerceAtLeast(1e-6)) * (180.0 / Math.PI)
    return (latDeg + dLat) to (lonDeg + dLon)
}

/** Доля сглаживания новой GPS-точки: на парковке — сильнее, в движении — почти сырая точка (меньше отставания). */
private fun travelGpsPosBlend(speedMps: Float): Float {
    val v = speedMps.coerceAtLeast(0f)
    return when {
        v < 0.8f -> TRAVEL_CAMERA_POS_SMOOTH_SLOW
        v < 4f -> 0.55f + (v - 0.8f) / 3.2f * 0.38f
        else -> 0.93f + (v / 35f).coerceAtMost(0.06f)
    }.coerceIn(0f, 1f)
}

/** Цель с GPS и текущее отображаемое положение камеры — обновляется на каждом кадре. */
private class TravelFollowCameraTargets {
    var tLat = 0.0
    var tLon = 0.0
    var tZoom = 15f
    var tAzimuth = 0f
    var tTilt = 0f
    var dLat = Double.NaN
    var dLon = Double.NaN
    var dZoom = Float.NaN
    var dAzimuth = Float.NaN
    var dTilt = Float.NaN
    var lastFrameNs = 0L
}

/**
 * Состояние кнопки «вернуть карту к следованию за курсом» для размещения в колонке FAB на экране путешествий.
 */
data class TravelRecenterFabSlot(
    val alpha: Float,
    val onRecenter: () -> Unit,
)

@Composable
fun YandexTravelMap(
    mapKitApiKey: String,
    modifier: Modifier = Modifier,
    zones: List<TravelZone>,
    polygonDraft: List<TravelGeoPoint>,
    userLocationEnabled: Boolean,
    /** Режим как в навигаторе Яндекса: стрелка «вверх», карта вращается по курсу. */
    headingModeActive: Boolean,
    /** Снимок камеры до поворота экрана / ухода с экрана (восстанавливается в новом [MapView]). */
    mapCameraSnapshot: TravelSavedMapCamera?,
    onPersistMapCamera: (TravelSavedMapCamera) -> Unit,
    territoryEditMode: Boolean,
    selectedZoneId: String?,
    omitPolygonZoneId: String?,
    onMapTap: (TravelGeoPoint) -> Unit,
    cameraJumpTo: TravelGeoPoint?,
    onCameraJumpConsumed: () -> Unit,
    routePickMode: Boolean,
    routeClearNonce: Long,
    hasFineLocation: Boolean,
    onTravelRouteMessage: (Int) -> Unit,
    onTravelRouteBuilt: () -> Unit,
    activeTravelRoute: DrivingRoute?,
    onActiveTravelRouteChange: (DrivingRoute?) -> Unit,
    mapIncidents: List<TravelMapIncident>,
    incidentPlaceMode: Boolean,
    onIncidentPlaced: (TravelGeoPoint) -> Unit,
    onUserLocationUpdated: ((Double, Double) -> Unit)? = null,
    routePhotoSessions: List<TravelRoutePhotoSession> = emptyList(),
    routeBurstDraftPoints: List<TravelRoutePhotoPoint> = emptyList(),
    routePlaybackSim: RoutePlaybackSimState? = null,
    tripHistoryReplayPose: TripHistoryReplayPose? = null,
    friendPeerLocation: FriendPeerLocation? = null,
    hideNavigatorHud: Boolean = false,
    navigatorHudExtras: (@Composable () -> Unit)? = null,
    tripHistoryTrack: List<TravelTripTrackPoint>? = null,
    tripTrackEraseHighlight: List<TravelTripTrackPoint>? = null,
    onTripGpsSample: ((latitude: Double, longitude: Double, timestampMs: Long, speedMps: Float) -> Unit)? = null,
    onFolkMapCrosshairGeoChanged: ((latitude: Double, longitude: Double, azimuthDeg: Float) -> Unit)? = null,
    /** Скрыть нативную синюю метку GPS, оставив подписку на координаты (виртуальный маркер не перекрывается). */
    suppressNativeUserLocationPin: Boolean = false,
    routePlaybackWalkerPolyline: RoutePlaybackPolyline? = null,
    onRouteWalkerDragPreview: ((distanceAlongPathMeters: Float) -> Unit)? = null,
    onRouteWalkerDragCommit: ((distanceAlongPathMeters: Float) -> Unit)? = null,
    onRouteWalkerFingerDragging: ((active: Boolean) -> Unit)? = null,
    mapHudPanelScale: Float = 1f,
    onMapHudPanelScaleChange: ((Float) -> Unit)? = null,
    /** Если задано, кнопка перецентровки не рисуется на карте, а передаётся в колонку FAB родителя. */
    onTravelRecenterFabSlot: ((TravelRecenterFabSlot?) -> Unit)? = null,
) {
    val context = LocalContext.current
    var mapReady by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(mapKitApiKey) {
        mapReady = null
        mapReady = MapKitBootstrap.ensure(context.applicationContext, mapKitApiKey)
    }

    when (mapReady) {
        null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        false -> {
            val message = if (mapKitApiKey.isBlank()) {
                stringResource(R.string.travel_maps_key_missing)
            } else {
                stringResource(R.string.travel_map_init_failed)
            }
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        true -> {
            YandexTravelMapContent(
                modifier = modifier,
                zones = zones,
                polygonDraft = polygonDraft,
                userLocationEnabled = userLocationEnabled,
                headingModeActive = headingModeActive,
                mapCameraSnapshot = mapCameraSnapshot,
                onPersistMapCamera = onPersistMapCamera,
                territoryEditMode = territoryEditMode,
                selectedZoneId = selectedZoneId,
                omitPolygonZoneId = omitPolygonZoneId,
                onMapTap = onMapTap,
                cameraJumpTo = cameraJumpTo,
                onCameraJumpConsumed = onCameraJumpConsumed,
                routePickMode = routePickMode,
                routeClearNonce = routeClearNonce,
                hasFineLocation = hasFineLocation,
                onTravelRouteMessage = onTravelRouteMessage,
                onTravelRouteBuilt = onTravelRouteBuilt,
                activeTravelRoute = activeTravelRoute,
                onActiveTravelRouteChange = onActiveTravelRouteChange,
                mapIncidents = mapIncidents,
                incidentPlaceMode = incidentPlaceMode,
                onIncidentPlaced = onIncidentPlaced,
                onUserLocationUpdated = onUserLocationUpdated,
                routePhotoSessions = routePhotoSessions,
                routeBurstDraftPoints = routeBurstDraftPoints,
                routePlaybackSim = routePlaybackSim,
                tripHistoryReplayPose = tripHistoryReplayPose,
                friendPeerLocation = friendPeerLocation,
                hideNavigatorHud = hideNavigatorHud,
                navigatorHudExtras = navigatorHudExtras,
                tripHistoryTrack = tripHistoryTrack,
                tripTrackEraseHighlight = tripTrackEraseHighlight,
                onTripGpsSample = onTripGpsSample,
                onFolkMapCrosshairGeoChanged = onFolkMapCrosshairGeoChanged,
                suppressNativeUserLocationPin = suppressNativeUserLocationPin,
                routePlaybackWalkerPolyline = routePlaybackWalkerPolyline,
                onRouteWalkerDragPreview = onRouteWalkerDragPreview,
                onRouteWalkerDragCommit = onRouteWalkerDragCommit,
                onRouteWalkerFingerDragging = onRouteWalkerFingerDragging,
                mapHudPanelScale = mapHudPanelScale,
                onMapHudPanelScaleChange = onMapHudPanelScaleChange,
                onTravelRecenterFabSlot = onTravelRecenterFabSlot,
            )
        }
    }
}

@Composable
private fun YandexTravelMapContent(
    modifier: Modifier,
    zones: List<TravelZone>,
    polygonDraft: List<TravelGeoPoint>,
    userLocationEnabled: Boolean,
    headingModeActive: Boolean,
    mapCameraSnapshot: TravelSavedMapCamera?,
    onPersistMapCamera: (TravelSavedMapCamera) -> Unit,
    territoryEditMode: Boolean,
    selectedZoneId: String?,
    omitPolygonZoneId: String?,
    onMapTap: (TravelGeoPoint) -> Unit,
    cameraJumpTo: TravelGeoPoint?,
    onCameraJumpConsumed: () -> Unit,
    routePickMode: Boolean,
    routeClearNonce: Long,
    hasFineLocation: Boolean,
    onTravelRouteMessage: (Int) -> Unit,
    onTravelRouteBuilt: () -> Unit,
    activeTravelRoute: DrivingRoute?,
    onActiveTravelRouteChange: (DrivingRoute?) -> Unit,
    mapIncidents: List<TravelMapIncident>,
    incidentPlaceMode: Boolean,
    onIncidentPlaced: (TravelGeoPoint) -> Unit,
    onUserLocationUpdated: ((Double, Double) -> Unit)? = null,
    routePhotoSessions: List<TravelRoutePhotoSession> = emptyList(),
    routeBurstDraftPoints: List<TravelRoutePhotoPoint> = emptyList(),
    routePlaybackSim: RoutePlaybackSimState? = null,
    tripHistoryReplayPose: TripHistoryReplayPose? = null,
    friendPeerLocation: FriendPeerLocation? = null,
    hideNavigatorHud: Boolean = false,
    navigatorHudExtras: (@Composable () -> Unit)? = null,
    tripHistoryTrack: List<TravelTripTrackPoint>? = null,
    tripTrackEraseHighlight: List<TravelTripTrackPoint>? = null,
    onTripGpsSample: ((latitude: Double, longitude: Double, timestampMs: Long, speedMps: Float) -> Unit)? = null,
    onFolkMapCrosshairGeoChanged: ((latitude: Double, longitude: Double, azimuthDeg: Float) -> Unit)? = null,
    suppressNativeUserLocationPin: Boolean = false,
    routePlaybackWalkerPolyline: RoutePlaybackPolyline? = null,
    onRouteWalkerDragPreview: ((distanceAlongPathMeters: Float) -> Unit)? = null,
    onRouteWalkerDragCommit: ((distanceAlongPathMeters: Float) -> Unit)? = null,
    onRouteWalkerFingerDragging: ((active: Boolean) -> Unit)? = null,
    mapHudPanelScale: Float = 1f,
    onMapHudPanelScaleChange: ((Float) -> Unit)? = null,
    /** Если задано, кнопка перецентровки не рисуется на карте, а передаётся в колонку FAB родителя. */
    onTravelRecenterFabSlot: ((TravelRecenterFabSlot?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    /** Следование GPS/компасу только после нажатия на стрелку; жесты по карте отключают следование. */
    var followUserActive by remember { mutableStateOf(false) }
    var recenterFabAlphaTarget by remember { mutableFloatStateOf(0f) }
    val hideRecenterFabJob = remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(headingModeActive) {
        if (!headingModeActive) {
            followUserActive = false
            hideRecenterFabJob.value?.cancel()
            hideRecenterFabJob.value = null
            recenterFabAlphaTarget = 0f
        }
    }

    /** [applicationContext]: при смене конфигурации MapView не пересоздаётся зря. */
    val mapView = remember {
        MapView(context.applicationContext)
    }
    val hudSpeedKmh = remember { mutableFloatStateOf(0f) }
    val hudViewSpanM = remember { mutableIntStateOf(0) }
    var navHudState by remember { mutableStateOf<TravelNavHudState?>(null) }
    /** Ручной зум с «высотой» от 2000 м — не подменяем авто-высотой по скорости, пока не сброс курса. */
    val userLockedWideView = remember { AtomicBoolean(false) }

    val zoneOverlay = remember(mapView) {
        mapView.mapWindow.map.mapObjects.addCollection().apply { zIndex = 0.5f }
    }
    val routeOverlay = remember(mapView) {
        mapView.mapWindow.map.mapObjects.addCollection().apply { zIndex = 5f }
    }
    val routeLineLayer = remember(mapView) {
        routeOverlay.addCollection().apply { zIndex = 4.2f }
    }
    val routeArrowLayer = remember(mapView) {
        routeOverlay.addCollection().apply { zIndex = 4.35f }
    }
    val routeManeuverLayer = remember(mapView) {
        routeOverlay.addCollection().apply { zIndex = 5.6f }
    }
    val routeHazardLayer = remember(mapView) {
        routeOverlay.addCollection().apply { zIndex = 5.55f }
    }
    val pinsOverlay = remember(mapView) {
        mapView.mapWindow.map.mapObjects.addCollection().apply { zIndex = 6f }
    }
    val routePhotoBurstLayer = remember(mapView) {
        routeOverlay.addCollection().apply { zIndex = 4.08f }
    }
    val routePhotoArrowLayer = remember(mapView) {
        routeOverlay.addCollection().apply { zIndex = 4.38f }
    }
    val tripHistoryLayer = remember(mapView) {
        routeOverlay.addCollection().apply { zIndex = 4.09f }
    }
    val tripEraseHighlightLayer = remember(mapView) {
        routeOverlay.addCollection().apply { zIndex = 4.11f }
    }
    /** Выше любых слоёв внутри [routeOverlay], [pinsOverlay] (z=6) и полигонов зон. */
    val routePlaybackWalkerLayer = remember(mapView) {
        mapView.mapWindow.map.mapObjects.addCollection().apply { zIndex = 50f }
    }
    val friendPeerLayer = remember(mapView) {
        mapView.mapWindow.map.mapObjects.addCollection().apply { zIndex = 51f }
    }
    /** Отмена устаревших ответов [DrivingSession] при новом запросе или [routeClearNonce]. */
    val routeRequestGeneration = remember { AtomicLong(0L) }
    val drivingRouter = remember(mapView) {
        DirectionsFactory.getInstance().createDrivingRouter(DrivingRouterType.ONLINE)
    }
    val routeSession = remember { mutableStateOf<DrivingSession?>(null) }

    val routePickActive = rememberUpdatedState(routePickMode)
    val incidentPickActive = rememberUpdatedState(incidentPlaceMode)
    val onIncidentTap = rememberUpdatedState(onIncidentPlaced)
    val hasLocationState = rememberUpdatedState(hasFineLocation)
    val onRouteMsg = rememberUpdatedState(onTravelRouteMessage)
    val onRouteBuilt = rememberUpdatedState(onTravelRouteBuilt)
    val onActiveRoute = rememberUpdatedState(onActiveTravelRouteChange)
    val onUserLocation = rememberUpdatedState(onUserLocationUpdated)
    val onTripGpsSampleCb = rememberUpdatedState(onTripGpsSample)
    val routePlaybackSimState = rememberUpdatedState(routePlaybackSim)
    val tripHistoryReplayPoseState = rememberUpdatedState(tripHistoryReplayPose)
    val friendPeerLocationState = rememberUpdatedState(friendPeerLocation)
    val folkCrosshairCbState = rememberUpdatedState(onFolkMapCrosshairGeoChanged)
    val routeWalkerPolyForInput = rememberUpdatedState(routePlaybackWalkerPolyline)
    val onWalkerCommitFromMap = rememberUpdatedState(onRouteWalkerDragCommit)
    val territoryEditForInput = rememberUpdatedState(territoryEditMode)
    val folkCrosshairThrottleLastMs = remember { AtomicLong(0L) }

    LaunchedEffect(routePhotoSessions, routeBurstDraftPoints, routePhotoBurstLayer, mapView) {
        routePhotoBurstLayer.clear()
        val segments = buildRoutePhotoDirectionSegments(routePhotoSessions)
        for (seg in segments) {
            val poly = Polyline(listOf(Point(seg.lat1, seg.lon1), Point(seg.lat2, seg.lon2)))
            val line = routePhotoBurstLayer.addPolyline(poly)
            line.setStrokeColor(seg.colorArgb)
            line.strokeWidth = 11f
            line.zIndex = 4.06f
        }
        if (routeBurstDraftPoints.isNotEmpty()) {
            val draftSegs = buildRoutePhotoDirectionSegments(
                listOf(TravelRoutePhotoSession(id = "__draft__", points = routeBurstDraftPoints)),
            )
            draftSegs.forEachIndexed { idx, seg ->
                val poly = Polyline(listOf(Point(seg.lat1, seg.lon1), Point(seg.lat2, seg.lon2)))
                val line = routePhotoBurstLayer.addPolyline(poly)
                val last = idx == draftSegs.lastIndex
                line.setStrokeColor(if (last) 0xFF00E676.toInt() else seg.colorArgb)
                line.strokeWidth = if (last) 15f else 12f
                line.zIndex = 4.07f
            }
        }
    }

    LaunchedEffect(tripHistoryTrack, tripHistoryLayer, mapView) {
        tripHistoryLayer.clear()
        val pts = tripHistoryTrack ?: return@LaunchedEffect
        if (pts.size < 2) return@LaunchedEffect
        val ordered = pts.sortedBy { it.timestampMs }
        for (i in 1 until ordered.size) {
            val a = ordered[i - 1]
            val b = ordered[i]
            val polySeg =
                Polyline(listOf(Point(a.latitude, a.longitude), Point(b.latitude, b.longitude)))
            val line = tripHistoryLayer.addPolyline(polySeg)
            line.setStrokeColor(tripTrackSpeedKmhToArgb(tripTrackSegmentSpeedKmhForDisplay(a, b)))
            line.strokeWidth = 10f
            line.zIndex = 4.09f
        }
    }

    LaunchedEffect(tripTrackEraseHighlight, tripEraseHighlightLayer, mapView) {
        tripEraseHighlightLayer.clear()
        val pts = tripTrackEraseHighlight ?: return@LaunchedEffect
        if (pts.size < 2) return@LaunchedEffect
        val ordered = pts.sortedBy { it.timestampMs }
        val yellow = 0xFFFFE082.toInt()
        for (i in 1 until ordered.size) {
            val a = ordered[i - 1]
            val b = ordered[i]
            val polySeg =
                Polyline(listOf(Point(a.latitude, a.longitude), Point(b.latitude, b.longitude)))
            val line = tripEraseHighlightLayer.addPolyline(polySeg)
            line.setStrokeColor(yellow)
            line.strokeWidth = 13f
            line.zIndex = 4.11f
        }
    }

    LaunchedEffect(routePhotoSessions, routeBurstDraftPoints, routePhotoArrowLayer, mapView, context) {
        routePhotoArrowLayer.clear()
        val folkArrowIcon = laneDirectionArrowImageProvider(context)
        fun addArrowsForSessions(sessions: List<TravelRoutePhotoSession>, iconScale: Float, z: Float) {
            for (session in sessions) {
                val pts = session.points.sortedBy { it.capturedAtMs }
                for (i in pts.indices) {
                    val bearing = bearingDegForRoutePhotoMapOrNull(
                        pts.getOrNull(i - 1),
                        pts[i],
                        pts.getOrNull(i + 1),
                    ) ?: continue
                    val p = pts[i]
                    val pm = routePhotoArrowLayer.addPlacemark(Point(p.latitude, p.longitude), folkArrowIcon)
                    pm.direction = bearing
                    pm.setIconStyle(
                        IconStyle().apply {
                            anchor = PointF(0.5f, 0.5f)
                            rotationType = RotationType.ROTATE
                            scale = iconScale
                            zIndex = z
                        },
                    )
                }
            }
        }
        addArrowsForSessions(routePhotoSessions, iconScale = 0.5f, z = 4.37f)
        if (routeBurstDraftPoints.isNotEmpty()) {
            addArrowsForSessions(
                listOf(TravelRoutePhotoSession(id = "__draft__", points = routeBurstDraftPoints)),
                iconScale = 0.47f,
                z = 4.375f,
            )
        }
    }

    val walkerStyleReplay = remember {
        IconStyle().apply {
            anchor = PointF(0.5f, 0.5f)
            rotationType = RotationType.ROTATE
            scale = 1.22f
        }
    }
    val walkerStyleFollowCam = remember {
        IconStyle().apply {
            anchor = PointF(0.5f, 0.5f)
            rotationType = RotationType.ROTATE
            scale = 1.12f
        }
    }
    val walkerStyleFreeCam = remember {
        IconStyle().apply {
            anchor = PointF(0.5f, 0.5f)
            rotationType = RotationType.ROTATE
            scale = 1.86f
        }
    }

    val onWalkerPreview = rememberUpdatedState(onRouteWalkerDragPreview)
    val onWalkerCommit = rememberUpdatedState(onRouteWalkerDragCommit)
    val onWalkerFinger = rememberUpdatedState(onRouteWalkerFingerDragging)
    val routeWalkerScrub = remember { RouteWalkerScrubGestureHolder() }

    LaunchedEffect(routePlaybackWalkerLayer, mapView, context) {
        val iconWalker = routePlaybackWalkerImageProvider(context)
        val pm = routePlaybackWalkerLayer.addPlacemark(Point(0.0, 0.0), iconWalker)
        pm.zIndex = 100f
        pm.setIconStyle(walkerStyleFollowCam)
        pm.setVisible(false)
        pm.setDraggable(false)

        while (coroutineContext.isActive) {
            val sim = routePlaybackSimState.value
            val replay = tripHistoryReplayPoseState.value
            val mapInst = mapView.mapWindow.map
            val cp = mapInst.cameraPosition
            when {
                replay != null -> {
                    pm.setIconStyle(walkerStyleReplay)
                    pm.setVisible(true)
                    pm.geometry = Point(replay.latitude, replay.longitude)
                    pm.direction = replay.bearingDeg
                    // LINEAR/0: как при follow-GPS в Choreographer — SMOOTH каждые ~16ms даёт наложение анимаций и рывки.
                    mapInst.move(
                        CameraPosition(Point(replay.latitude, replay.longitude), cp.zoom, cp.azimuth, cp.tilt),
                        Animation(Animation.Type.LINEAR, 0f),
                        null,
                    )
                }
                sim != null && sim.latitude.isFinite() && sim.longitude.isFinite() -> {
                    pm.setIconStyle(
                        if (sim.followCameraWithWalker) walkerStyleFollowCam else walkerStyleFreeCam,
                    )
                    pm.setVisible(true)
                    pm.geometry = Point(sim.latitude, sim.longitude)
                    pm.direction = sim.bearingDeg
                    if (sim.followCameraWithWalker) {
                        mapInst.move(
                            CameraPosition(Point(sim.latitude, sim.longitude), cp.zoom, cp.azimuth, cp.tilt),
                            Animation(Animation.Type.LINEAR, 0f),
                            null,
                        )
                    }
                }
                else -> pm.setVisible(false)
            }
            delay(if (sim != null || replay != null) 16L else 120L)
        }
    }

    DisposableEffect(mapView) {
        val dm = context.resources.displayMetrics.density
        val nearWalkerPx = ROUTE_WALKER_SCRUB_NEAR_MARKER_DP * dm
        val listener =
            View.OnTouchListener l@{ _, event ->
                if (territoryEditForInput.value) return@l false
                if (folkCrosshairCbState.value != null) return@l false
                if (tripHistoryReplayPoseState.value != null) return@l false
                if (routePickActive.value || incidentPickActive.value) return@l false
                val poly = routeWalkerPolyForInput.value ?: return@l false
                if (routePlaybackSimState.value == null) return@l false
                val mw = mapView.mapWindow
                val mapInst = mw.map
                fun geo(ev: MotionEvent): Point? {
                    val sp = ScreenPoint(ev.x, ev.y)
                    return mw.screenToWorld(sp)
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val sim = routePlaybackSimState.value ?: return@l false
                        val g = geo(event) ?: return@l false
                        val distM = distanceMetersToRoutePolyline(poly, g.latitude, g.longitude)
                        val wScr = mw.worldToScreen(Point(sim.latitude, sim.longitude)) ?: return@l false
                        val ddx = event.x - wScr.x
                        val ddy = event.y - wScr.y
                        val screenDist = hypot(ddx.toDouble(), ddy.toDouble()).toFloat()
                        val onCorridor = distM <= ROUTE_WALKER_SCRUB_CORRIDOR_M
                        val nearWalker = screenDist <= nearWalkerPx
                        if (!onCorridor && !nearWalker) return@l false
                        routeWalkerScrub.active = true
                        routeWalkerScrub.backup =
                            MapWalkerGesturesBackup(
                                scroll = mapInst.isScrollGesturesEnabled,
                                zoom = mapInst.isZoomGesturesEnabled,
                                rotate = mapInst.isRotateGesturesEnabled,
                                tilt = mapInst.isTiltGesturesEnabled,
                            ).also { b ->
                                mapInst.isScrollGesturesEnabled = false
                                mapInst.isZoomGesturesEnabled = false
                                mapInst.isRotateGesturesEnabled = false
                                mapInst.isTiltGesturesEnabled = false
                            }
                        onWalkerFinger.value?.invoke(true)
                        val d0 = nearestDistanceAlongPolyline(poly, g.latitude, g.longitude)
                        onWalkerPreview.value?.invoke(d0)
                        return@l true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!routeWalkerScrub.active) return@l false
                        val g = geo(event) ?: return@l true
                        val d = nearestDistanceAlongPolyline(poly, g.latitude, g.longitude)
                        onWalkerPreview.value?.invoke(d)
                        return@l true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!routeWalkerScrub.active) return@l false
                        routeWalkerScrub.active = false
                        val g = geo(event)
                            ?: routePlaybackSimState.value?.let { Point(it.latitude, it.longitude) }
                            ?: return@l true
                        val d = nearestDistanceAlongPolyline(poly, g.latitude, g.longitude)
                        onWalkerCommit.value?.invoke(d)
                        onWalkerFinger.value?.invoke(false)
                        routeWalkerScrub.backup?.let { b ->
                            mapInst.isScrollGesturesEnabled = b.scroll
                            mapInst.isZoomGesturesEnabled = b.zoom
                            mapInst.isRotateGesturesEnabled = b.rotate
                            mapInst.isTiltGesturesEnabled = b.tilt
                        }
                        routeWalkerScrub.backup = null
                        return@l true
                    }
                }
                false
            }
        mapView.setOnTouchListener(listener)
        onDispose {
            mapView.setOnTouchListener(null)
            if (routeWalkerScrub.active) {
                routeWalkerScrub.active = false
                onWalkerFinger.value?.invoke(false)
                val m = mapView.mapWindow.map
                routeWalkerScrub.backup?.let { b ->
                    m.isScrollGesturesEnabled = b.scroll
                    m.isZoomGesturesEnabled = b.zoom
                    m.isRotateGesturesEnabled = b.rotate
                    m.isTiltGesturesEnabled = b.tilt
                }
                routeWalkerScrub.backup = null
            }
        }
    }

    LaunchedEffect(activeTravelRoute, mapView, routeLineLayer, routeArrowLayer, routeManeuverLayer, routeHazardLayer) {
        val r = activeTravelRoute
        if (r == null) {
            routeLineLayer.clear()
            routeArrowLayer.clear()
            routeManeuverLayer.clear()
            routeHazardLayer.clear()
            return@LaunchedEffect
        }
        val geometry = r.geometry
        routeLineLayer.clear()
        routeArrowLayer.clear()
        val line = routeLineLayer.addPolyline(geometry)
        line.setStrokeColor(0xFF00C853.toInt())
        line.strokeWidth = 6.5f
        line.zIndex = 4f

        val laneArrowIcon = laneDirectionArrowImageProvider(context)
        val arrows = sampleLaneArrowsAlongPolyline(geometry, TRAVEL_ROUTE_LANE_ARROW_SPACING_M)
        for ((pt, bearingDeg) in arrows) {
            val pm = routeArrowLayer.addPlacemark(pt, laneArrowIcon)
            pm.direction = bearingDeg
            pm.setIconStyle(
                IconStyle().apply {
                    anchor = PointF(0.5f, 0.5f)
                    rotationType = RotationType.ROTATE
                    scale = 1f
                    zIndex = 4.34f
                },
            )
        }

        val mapInst = mapView.mapWindow.map
        val g = Geometry.fromPolyline(geometry)
        val pos = mapInst.cameraPosition(g)
        mapInst.move(pos, Animation(Animation.Type.SMOOTH, 0.35f), null)
    }

    @SuppressLint("MissingPermission")
    suspend fun requestDrivingRouteTo(dest: Point) {
        followUserActive = false
        if (!hasLocationState.value) {
            onRouteMsg.value(R.string.travel_need_location)
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        var loc = client.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            com.google.android.gms.tasks.CancellationTokenSource().token,
        ).await()
        if (loc == null) {
            loc = client.lastLocation.await()
        }
        if (loc == null) {
            onRouteMsg.value(R.string.travel_gps_failed)
            return
        }
        val start = Point(loc.latitude, loc.longitude)
        val myGen = routeRequestGeneration.incrementAndGet()
        routeSession.value?.cancel()
        routeSession.value = drivingRouter.requestRoutes(
            listOf(
                RequestPoint(start, RequestPointType.WAYPOINT, "", "", ""),
                RequestPoint(dest, RequestPointType.WAYPOINT, "", "", ""),
            ),
            DrivingOptions().setRoutesCount(1),
            VehicleOptions(),
            object : DrivingSession.DrivingRouteListener {
                override fun onDrivingRoutes(routes: MutableList<DrivingRoute>) {
                    if (myGen != routeRequestGeneration.get()) return
                    val route = routes.firstOrNull()
                    val geometry = route?.geometry
                    if (route == null || geometry == null) {
                        onActiveRoute.value(null)
                        onRouteMsg.value(R.string.travel_route_failed)
                        return
                    }
                    onActiveRoute.value(route)
                    routeSession.value = null
                    onRouteBuilt.value()
                }

                override fun onDrivingRoutesError(error: Error) {
                    if (myGen != routeRequestGeneration.get()) return
                    onActiveRoute.value(null)
                    routeSession.value = null
                    onRouteMsg.value(R.string.travel_route_failed)
                }
            },
        )
    }

    val transparentLabelIcon = remember(context) {
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(AndroidColor.TRANSPARENT)
        ImageProvider.fromBitmap(bmp)
    }

    val incidentPinIcon = remember(context) {
        incidentPinImageProvider(context)
    }
    val incidentPinWithAudioIcon = remember(context) {
        incidentPinWithAudioImageProvider(context)
    }
    val zoneAudioBadgeIcon = remember(context) {
        zoneAudioBadgeImageProvider(context)
    }
    val userNavArrowIcon = remember(context) {
        userNavigationArrowImageProvider(context)
    }

    val onTapState = rememberUpdatedState(onMapTap)
    val inputListener = remember(scope, hideRecenterFabJob) {
        object : InputListener {
            override fun onMapTap(map: Map, point: Point) {
                if (routePickActive.value) {
                    scope.launch {
                        requestDrivingRouteTo(point)
                    }
                    return
                }
                if (incidentPickActive.value) {
                    followUserActive = false
                    onIncidentTap.value(TravelGeoPoint(point.latitude, point.longitude))
                    return
                }
                hideRecenterFabJob.value?.cancel()
                recenterFabAlphaTarget = 1f
                hideRecenterFabJob.value = scope.launch {
                    delay(1700)
                    recenterFabAlphaTarget = 0f
                }
                onTapState.value(TravelGeoPoint(point.latitude, point.longitude))
            }

            override fun onMapLongTap(map: Map, point: Point) {
                if (territoryEditForInput.value) return
                if (folkCrosshairCbState.value != null) return
                if (tripHistoryReplayPoseState.value != null) return
                if (routePickActive.value || incidentPickActive.value) return
                val poly = routeWalkerPolyForInput.value ?: return
                if (routePlaybackSimState.value == null) return
                val d = nearestDistanceAlongPolyline(poly, point.latitude, point.longitude)
                onWalkerCommitFromMap.value?.invoke(d)
            }
        }
    }

    val onPersistState = rememberUpdatedState(onPersistMapCamera)
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (MapKitBootstrap.isReady) {
                        MapKitFactory.getInstance().onStart()
                        mapView.onStart()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (MapKitBootstrap.isReady) {
                        runCatching {
                            val cp = mapView.mapWindow.map.cameraPosition
                            val t = cp.target
                            onPersistState.value(
                                TravelSavedMapCamera(
                                    latitude = t.latitude,
                                    longitude = t.longitude,
                                    zoom = cp.zoom,
                                    azimuth = cp.azimuth,
                                    tilt = cp.tilt,
                                ),
                            )
                        }
                        mapView.onStop()
                        MapKitFactory.getInstance().onStop()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val userLocationLayer = remember(mapView) {
        if (MapKitBootstrap.isReady) {
            MapKitFactory.getInstance().createUserLocationLayer(mapView.mapWindow)
        } else {
            null
        }
    }

    LaunchedEffect(userLocationEnabled, headingModeActive, followUserActive, userLocationLayer, suppressNativeUserLocationPin) {
        val layer = userLocationLayer ?: return@LaunchedEffect
        val useSmoothedFollowPin = userLocationEnabled && headingModeActive && followUserActive
        layer.isVisible = userLocationEnabled && !useSmoothedFollowPin && !suppressNativeUserLocationPin
        if (userLocationEnabled) {
            // Поворот и центрирование задаём сами (компас + bearing), иначе дублируется с UserLocationLayer.
            layer.isHeadingModeActive = false
            // Автомасштаб MapKit отключён: при режиме «навигатор» масштаб задаётся по GPS-скорости.
            layer.isAutoZoomEnabled = false
        } else {
            layer.isHeadingModeActive = false
            layer.isAutoZoomEnabled = false
        }
    }

    @SuppressLint("MissingPermission")
    DisposableEffect(
        headingModeActive,
        userLocationEnabled,
        hasFineLocation,
        routePickMode,
        incidentPlaceMode,
        followUserActive,
        mapView,
        userNavArrowIcon,
        onUserLocation,
        userLocationLayer,
    ) {
        // Скорость: пока тапаем маршрут/инцидент, не подписываемся на GPS, чтобы не мешать.
        if (!userLocationEnabled || !hasFineLocation || routePickMode || incidentPlaceMode) {
            return@DisposableEffect onDispose { }
        }
        val followCamera = headingModeActive && followUserActive
        var smoothedZoom = mapView.mapWindow.map.cameraPosition.zoom
        /** Доп. сглаживание цели зума по тикам GPS (поверх [smoothedZoom]), чтобы [followTargets.tZoom] не прыгала. */
        var smoothFollowZoomTarget = Float.NaN
        var smoothedTilt = mapView.mapWindow.map.cameraPosition.tilt
        var smoothedLat: Double? = null
        var smoothedLon: Double? = null
        var prevAndroidLoc: Location? = null
        var prevMkPrev: MkLocPrev? = null
        val followTargets = TravelFollowCameraTargets()
        var gpsTargetReady = false
        val choreographer = Choreographer.getInstance()
        /** Последний якорь GPS и [Location.getElapsedRealtimeNanos] для экстраполяции между тиками. */
        var navFixElapsedRealtimeNs = 0L
        var navFixLat = 0.0
        var navFixLon = 0.0
        var navSpeedMps = 0f
        var navBearingDeg = 0f
        var navAnchorInitialized = false
        var smoothFollowUserColl: MapObjectCollection? = null
        var smoothFollowUserPin: PlacemarkMapObject? = null
        if (followCamera) {
            val map = mapView.mapWindow.map
            val coll = map.mapObjects.addCollection().apply { zIndex = 7.35f }
            smoothFollowUserColl = coll
            val pm = coll.addPlacemark(Point(0.0, 0.0), userNavArrowIcon)
            pm.setIconStyle(
                IconStyle().apply {
                    anchor = PointF(0.5f, 0.5f)
                    rotationType = RotationType.ROTATE
                    scale = 1.08f
                },
            )
            smoothFollowUserPin = pm
        }
        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!followCamera) return
                if (!gpsTargetReady || !navAnchorInitialized) {
                    choreographer.postFrameCallback(this)
                    return
                }
                val map = mapView.mapWindow.map
                val dt = if (followTargets.lastFrameNs == 0L) {
                    1.0 / 60.0
                } else {
                    ((frameTimeNanos - followTargets.lastFrameNs) / 1e9).coerceIn(0.001, 0.055)
                }
                followTargets.lastFrameNs = frameTimeNanos
                val ageSec =
                    ((SystemClock.elapsedRealtimeNanos() - navFixElapsedRealtimeNs) / 1e9).toDouble().coerceIn(
                        0.0,
                        TRAVEL_FOLLOW_MAX_GPS_AGE_SEC,
                    )
                val extrapDist =
                    (navSpeedMps * ageSec.toFloat()).coerceAtMost(TRAVEL_FOLLOW_MAX_EXTRAPOLATE_M)
                val (exLat, exLon) = extrapolateLatLon(navFixLat, navFixLon, navBearingDeg, extrapDist)
                followTargets.tLat = exLat
                followTargets.tLon = exLon
                val aPos = travelExpSmoothAlpha(TRAVEL_FOLLOW_DISPLAY_POS_LAMBDA, dt).toFloat()
                val aZm = travelExpSmoothAlpha(TRAVEL_FOLLOW_DISPLAY_ZOOM_LAMBDA, dt).toFloat()
                val aAng = travelExpSmoothAlpha(TRAVEL_FOLLOW_DISPLAY_ANGLE_LAMBDA, dt).toFloat()
                if (followTargets.dLat.isNaN()) {
                    val cp = map.cameraPosition
                    val p = cp.target
                    followTargets.dLat = p.latitude
                    followTargets.dLon = p.longitude
                    followTargets.dZoom = cp.zoom
                    followTargets.dAzimuth = cp.azimuth
                    followTargets.dTilt = cp.tilt
                }
                followTargets.dLat = travelLerpD(followTargets.dLat, followTargets.tLat, aPos)
                followTargets.dLon = travelLerpD(followTargets.dLon, followTargets.tLon, aPos)
                followTargets.dZoom += (followTargets.tZoom - followTargets.dZoom) * aZm
                followTargets.dAzimuth = lerpAngleDegrees(followTargets.dAzimuth, followTargets.tAzimuth, aAng)
                followTargets.dTilt += (followTargets.tTilt - followTargets.dTilt) * aAng
                smoothFollowUserPin?.geometry = Point(followTargets.dLat, followTargets.dLon)
                smoothFollowUserPin?.direction = followTargets.dAzimuth
                map.move(
                    CameraPosition(
                        Point(followTargets.dLat, followTargets.dLon),
                        followTargets.dZoom,
                        followTargets.dAzimuth,
                        followTargets.dTilt,
                    ),
                    Animation(Animation.Type.LINEAR, 0f),
                    null,
                )
                choreographer.postFrameCallback(this)
            }
        }
        /** Азимут компаса (0° — север), обновляется сенсором между тиками GPS. */
        val compassDeg = FloatArray(1).apply { this[0] = Float.NaN }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val sensorListener = object : SensorEventListener {
            private val rMat = FloatArray(9)
            private val orient = FloatArray(3)
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                SensorManager.getRotationMatrixFromVector(rMat, event.values)
                SensorManager.getOrientation(rMat, orient)
                var az = Math.toDegrees(orient[0].toDouble()).toFloat()
                if (az < 0f) az += 360f
                val prev = compassDeg[0]
                compassDeg[0] = if (prev.isNaN()) {
                    az
                } else {
                    lerpAngleDegrees(prev, az, 0.12f)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (followCamera && rotationSensor != null) {
            sensorManager.registerListener(
                sensorListener,
                rotationSensor,
                SensorManager.SENSOR_DELAY_GAME,
            )
        }
        fun notifyLocation(
            latitude: Double,
            longitude: Double,
            speedMps: Float,
            hasBearing: Boolean,
            bearingRaw: Float,
            elapsedRealtimeNs: Long,
        ) {
            onUserLocation.value?.invoke(latitude, longitude)
            onTripGpsSampleCb.value?.invoke(latitude, longitude, System.currentTimeMillis(), speedMps)
            val kmh = (speedMps * 3.6f).coerceIn(0f, 400f)
            val hudBlend = when {
                kmh < 10f -> 0.48f
                kmh < 55f -> 0.68f
                else -> 0.84f
            }
            hudSpeedKmh.floatValue = hudSpeedKmh.floatValue * (1f - hudBlend) + kmh * hudBlend
            if (!followCamera) return
            val zoomKmh = hudSpeedKmh.floatValue.coerceIn(0f, 400f)
            val map = mapView.mapWindow.map
            val cp = map.cameraPosition
            val blend = travelGpsPosBlend(speedMps)
            val sLat0 = smoothedLat
            val sLon0 = smoothedLon
            val anchorLat: Double
            val anchorLon: Double
            if (sLat0 == null || sLon0 == null) {
                anchorLat = latitude
                anchorLon = longitude
            } else {
                anchorLat = travelLerpD(sLat0, latitude, blend)
                anchorLon = travelLerpD(sLon0, longitude, blend)
            }
            smoothedLat = anchorLat
            smoothedLon = anchorLon
            val bearingT = (0.2f + 0.58f * (speedMps / 26f).coerceIn(0f, 1f)).coerceIn(0.2f, 0.78f)
            val azimuth = when {
                speedMps > 0.85f && hasBearing -> {
                    val br = (bearingRaw % 360f + 360f) % 360f
                    val prevA = cp.azimuth
                    if (prevA.isNaN()) br else lerpAngleDegrees(prevA, br, bearingT)
                }
                !compassDeg[0].isNaN() -> compassDeg[0]
                hasBearing -> (bearingRaw % 360f + 360f) % 360f
                else -> cp.azimuth
            }
            navFixElapsedRealtimeNs = elapsedRealtimeNs
            navFixLat = anchorLat
            navFixLon = anchorLon
            navSpeedMps = speedMps
            navBearingDeg = azimuth
            navAnchorInitialized = true
            val z = if (userLockedWideView.get()) {
                cp.zoom.also {
                    smoothedZoom = it
                    smoothFollowZoomTarget = it
                }
            } else {
                val targetZoom = zoomForViewSpanMeters(
                    mapView,
                    latitude,
                    targetViewSpanMetersSmooth(zoomKmh),
                )
                smoothedZoom = smoothedZoom * 0.88f + targetZoom * 0.12f
                val blended = smoothedZoom.coerceIn(2f, 20.5f)
                smoothFollowZoomTarget =
                    if (smoothFollowZoomTarget.isNaN()) {
                        blended
                    } else {
                        smoothFollowZoomTarget * 0.82f + blended * 0.18f
                    }
                smoothFollowZoomTarget.coerceIn(2f, 20.5f)
            }
            smoothedTilt = smoothedTilt + (TRAVEL_NAV_TARGET_TILT - smoothedTilt) * TRAVEL_NAV_TILT_SMOOTH
            followTargets.tZoom = z
            followTargets.tAzimuth = azimuth
            followTargets.tTilt = smoothedTilt
            gpsTargetReady = true
        }

        var stopLocationUpdates: () -> Unit = {}
        if (MapKitBootstrap.isReady) {
            val mkLm = MapKitFactory.getInstance().createLocationManager()
            userLocationLayer?.setSource(LocationViewSourceFactory.createLocationViewSource(mkLm))
            val purpose = if (followCamera) Purpose.AUTOMOTIVE_NAVIGATION else Purpose.GENERAL
            val settings = SubscriptionSettings(UseInBackground.DISALLOW, purpose)
            val mkListener = object : YandexLocationListener {
                override fun onLocationUpdated(location: com.yandex.mapkit.location.Location) {
                    val pos = location.position
                    val lat = pos.latitude
                    val lon = pos.longitude
                    val speedMps = effectiveSpeedMpsMapKit(location, prevMkPrev)
                    prevMkPrev = MkLocPrev(lat, lon, location.relativeTimestamp)
                    val hd = location.heading
                    val hasBearing = hd != null
                    val bearingRaw = hd?.toFloat() ?: 0f
                    notifyLocation(lat, lon, speedMps, hasBearing, bearingRaw, location.relativeTimestamp)
                }

                override fun onLocationStatusUpdated(status: LocationStatus) {
                    if (status == LocationStatus.NOT_AVAILABLE) {
                        gpsTargetReady = false
                    }
                }
            }
            mkLm.subscribeForLocationUpdates(settings, mkListener)
            stopLocationUpdates = {
                runCatching { mkLm.unsubscribe(mkListener) }
                runCatching { userLocationLayer?.setDefaultSource() }
            }
        } else {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fun applyAndroidLocation(loc: Location) {
                        val speedMps = effectiveSpeedMps(loc, prevAndroidLoc)
                        prevAndroidLoc = Location(loc)
                        notifyLocation(
                            loc.latitude,
                            loc.longitude,
                            speedMps,
                            loc.hasBearing(),
                            loc.bearing,
                            loc.elapsedRealtimeNanos,
                        )
                    }
                    val locs = result.locations
                    if (locs.isNotEmpty()) {
                        for (loc in locs) applyAndroidLocation(loc)
                    } else {
                        result.lastLocation?.let { applyAndroidLocation(it) }
                    }
                }
            }
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                TRAVEL_FOLLOW_LOCATION_INTERVAL_MS,
            )
                .setMinUpdateIntervalMillis(TRAVEL_FOLLOW_LOCATION_INTERVAL_MS)
                .setMaxUpdateDelayMillis(0)
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            stopLocationUpdates = {
                client.removeLocationUpdates(callback)
            }
        }
        if (followCamera) {
            choreographer.postFrameCallback(frameCallback)
        }
        onDispose {
            choreographer.removeFrameCallback(frameCallback)
            smoothFollowUserColl?.let { coll ->
                runCatching { mapView.mapWindow.map.mapObjects.remove(coll) }
            }
            smoothFollowUserColl = null
            smoothFollowUserPin = null
            if (followCamera && rotationSensor != null) {
                sensorManager.unregisterListener(sensorListener)
            }
            stopLocationUpdates()
        }
    }

    LaunchedEffect(routeClearNonce) {
        if (routeClearNonce == 0L) return@LaunchedEffect
        routeRequestGeneration.incrementAndGet()
        routeSession.value?.cancel()
        routeSession.value = null
    }

    DisposableEffect(activeTravelRoute, hasFineLocation) {
        val route = activeTravelRoute
        if (route == null || !hasFineLocation) {
            navHudState = null
            return@DisposableEffect onDispose { }
        }
        val session = TravelRouteGuidanceSession(
            context.applicationContext,
            route,
            onHudState = { navHudState = it },
        )
        session.start()
        onDispose {
            session.stop()
            navHudState = null
        }
    }

    DisposableEffect(mapView) {
        onDispose {
            routeSession.value?.cancel()
        }
    }

    DisposableEffect(mapView, inputListener) {
        val map = mapView.mapWindow.map
        map.addInputListener(inputListener)
        onDispose {
            map.removeInputListener(inputListener)
        }
    }

    var mapZoom by remember(mapView) { mutableStateOf(mapView.mapWindow.map.cameraPosition.zoom) }
    val animatedRecenterFabAlpha by animateFloatAsState(
        targetValue = recenterFabAlphaTarget,
        animationSpec = tween(420),
        label = "travel_recenter_fab_alpha",
    )
    val performRecenter = remember(mapView, scope, context) {
        {
            hideRecenterFabJob.value?.cancel()
            followUserActive = true
            recenterFabAlphaTarget = 0f
            userLockedWideView.set(false)
            scope.launch {
                @SuppressLint("MissingPermission")
                runCatching {
                    val client = LocationServices.getFusedLocationProviderClient(context)
                    var loc = client.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        com.google.android.gms.tasks.CancellationTokenSource().token,
                    ).await()
                    if (loc == null) {
                        loc = client.lastLocation.await()
                    }
                    if (loc != null) {
                        val map = mapView.mapWindow.map
                        val cp = map.cameraPosition
                        val zoom = zoomForViewSpanMeters(
                            mapView,
                            loc.latitude,
                            RECENTER_VIEW_SPAN_M,
                        )
                        val az = if (loc.hasBearing()) {
                            (loc.bearing % 360f + 360f) % 360f
                        } else {
                            cp.azimuth
                        }
                        map.move(
                            CameraPosition(
                                Point(loc.latitude, loc.longitude),
                                zoom,
                                az,
                                TRAVEL_NAV_TARGET_TILT,
                            ),
                            Animation(Animation.Type.SMOOTH, 0.35f),
                            null,
                        )
                    }
                }
            }
            Unit
        }
    }
    DisposableEffect(onTravelRecenterFabSlot) {
        onDispose { onTravelRecenterFabSlot?.invoke(null) }
    }
    val showRecenterFab = headingModeActive && userLocationEnabled && hasFineLocation
    SideEffect {
        val publish = onTravelRecenterFabSlot ?: return@SideEffect
        val visible = showRecenterFab &&
            (animatedRecenterFabAlpha > 0.02f || recenterFabAlphaTarget > 0.02f)
        publish(
            if (visible) {
                TravelRecenterFabSlot(alpha = animatedRecenterFabAlpha, onRecenter = performRecenter)
            } else {
                null
            },
        )
    }
    DisposableEffect(mapView, scope) {
        val map = mapView.mapWindow.map
        val listener = object : CameraListener {
            override fun onCameraPositionChanged(
                mapInstance: Map,
                cameraPosition: CameraPosition,
                cameraUpdateReason: CameraUpdateReason,
                finished: Boolean,
            ) {
                mapZoom = cameraPosition.zoom
                hudViewSpanM.intValue = travelVerticalViewSpanMeters(
                    mapView,
                    cameraPosition.zoom,
                    cameraPosition.target.latitude,
                )
                folkCrosshairCbState.value?.let { cb ->
                    val now = SystemClock.uptimeMillis()
                    val prev = folkCrosshairThrottleLastMs.get()
                    if (finished || now - prev >= 72L) {
                        folkCrosshairThrottleLastMs.set(now)
                        val t = cameraPosition.target
                        cb(t.latitude, t.longitude, cameraPosition.azimuth)
                    }
                }
                if (cameraUpdateReason == CameraUpdateReason.GESTURES) {
                    hideRecenterFabJob.value?.cancel()
                    followUserActive = false
                    recenterFabAlphaTarget = 1f
                    if (finished) {
                        val spanM = travelVerticalViewSpanMeters(
                            mapView,
                            cameraPosition.zoom,
                            cameraPosition.target.latitude,
                        )
                        userLockedWideView.set(spanM >= USER_CHOSEN_MIN_SPAN_PRESERVE_M)
                        hideRecenterFabJob.value = scope.launch {
                            delay(1700)
                            recenterFabAlphaTarget = 0f
                        }
                    }
                }
            }
        }
        map.addCameraListener(listener)
        mapZoom = map.cameraPosition.zoom
        folkCrosshairCbState.value?.let { cb ->
            val cp = map.cameraPosition
            val t = cp.target
            cb(t.latitude, t.longitude, cp.azimuth)
        }
        onDispose {
            hideRecenterFabJob.value?.cancel()
            map.removeCameraListener(listener)
        }
    }

    val hazardSchoolIcon = remember(context) { travelHazardDotProvider(context, 0xFFFFC107.toInt()) }
    val hazardBumpIcon = remember(context) { travelHazardDotProvider(context, 0xFFFF6F00.toInt()) }
    val hazardSignIcon = remember(context) { travelHazardDotProvider(context, 0xFF1976D2.toInt()) }

    LaunchedEffect(activeTravelRoute, mapZoom, routeManeuverLayer, transparentLabelIcon) {
        val r = activeTravelRoute
        if (r == null) {
            routeManeuverLayer.clear()
            return@LaunchedEffect
        }
        routeManeuverLayer.clear()
        val poly = r.geometry
        val z = mapZoom
        for ((i, m) in TravelManeuvers.buildList(r).withIndex()) {
            val pt = runCatching { TravelManeuvers.pointOnRoute(poly, m.position) }.getOrNull() ?: continue
            val p = routeManeuverLayer.addPlacemark(pt, transparentLabelIcon)
            p.setText(maneuverMapCaption(i + 1, m), travelManeuverTextStyle(z))
            p.zIndex = 5.7f
        }
    }

    LaunchedEffect(
        activeTravelRoute,
        mapZoom,
        routeHazardLayer,
        hazardSchoolIcon,
        hazardBumpIcon,
        hazardSignIcon,
    ) {
        val r = activeTravelRoute
        routeHazardLayer.clear()
        if (r == null) return@LaunchedEffect
        val z = mapZoom
        val style = travelHazardTextStyle(z)
        for (h in TravelRouteGuidanceSession.hazardMapItems(r)) {
            val icon = when (h.kind) {
                TravelHazardMapKind.SCHOOL -> hazardSchoolIcon
                TravelHazardMapKind.SPEED_BUMP -> hazardBumpIcon
                TravelHazardMapKind.DIRECTION_SIGN -> hazardSignIcon
            }
            val pm = routeHazardLayer.addPlacemark(h.point, icon)
            pm.setText(h.label, style)
            pm.zIndex = 5.56f
        }
    }

    var initialCameraPlaced by remember(mapView, userLocationEnabled) { mutableStateOf(false) }

    LaunchedEffect(userLocationEnabled, mapCameraSnapshot, mapView) {
        if (initialCameraPlaced) return@LaunchedEffect
        val map = mapView.mapWindow.map
        val snapshot = mapCameraSnapshot
        if (snapshot != null) {
            map.move(
                CameraPosition(
                    Point(snapshot.latitude, snapshot.longitude),
                    snapshot.zoom,
                    snapshot.azimuth,
                    snapshot.tilt,
                ),
                Animation(Animation.Type.SMOOTH, 0f),
                null,
            )
            initialCameraPlaced = true
            return@LaunchedEffect
        }
        // Без сохранённого вида: общий ракурс; «я по центру» — только после нажатия на стрелку.
        map.move(
            CameraPosition(Point(31.78, 35.23), 8f, 0f, 0f),
            Animation(Animation.Type.SMOOTH, 0f),
            null,
        )
        initialCameraPlaced = true
    }

    LaunchedEffect(cameraJumpTo, mapView) {
        val target = cameraJumpTo ?: return@LaunchedEffect
        followUserActive = false
        val map = mapView.mapWindow.map
        map.move(
            CameraPosition(Point(target.latitude, target.longitude), 14f, 0f, 0f),
            Animation(Animation.Type.SMOOTH, 0.35f),
            null,
        )
        onCameraJumpConsumed()
    }

    LaunchedEffect(
        zones,
        polygonDraft,
        mapView,
        zoneOverlay,
        transparentLabelIcon,
        territoryEditMode,
        selectedZoneId,
        omitPolygonZoneId,
        mapZoom,
    ) {
        redrawZoneOverlays(
            zoneOverlay,
            zones,
            polygonDraft,
            transparentLabelIcon,
            zoneAudioBadgeIcon,
            territoryEditMode,
            selectedZoneId,
            omitPolygonZoneId,
            mapZoom,
        )
    }

    LaunchedEffect(mapIncidents, pinsOverlay, incidentPinIcon, incidentPinWithAudioIcon, mapZoom) {
        redrawIncidentPins(
            pinsOverlay,
            mapIncidents,
            incidentPinIcon,
            incidentPinWithAudioIcon,
            mapZoom,
        )
    }

    LaunchedEffect(friendPeerLayer, mapView, context, mapZoom) {
        val icon = friendPeerPinImageProvider(context)
        val pm = friendPeerLayer.addPlacemark(Point(0.0, 0.0), icon)
        pm.zIndex = 90f
        pm.setIconStyle(
            IconStyle().apply {
                anchor = PointF(0.5f, 1f)
                rotationType = RotationType.NO_ROTATION
                scale = 1f
            },
        )
        pm.setVisible(false)
        snapshotFlow { friendPeerLocationState.value to mapZoom }.collect { (loc, z) ->
            if (loc == null) {
                pm.setVisible(false)
                return@collect
            }
            pm.setVisible(true)
            pm.geometry = Point(loc.latitude, loc.longitude)
            val raw = loc.label?.trim()?.takeIf { it.isNotEmpty() }
            val caption = raw ?: context.getString(R.string.travel_friend_peer_pin_default)
            val short = if (caption.length > 22) caption.take(21) + "…" else caption
            pm.setText(short, incidentLabelStyle(z))
        }
    }

    val showMapHud =
        userLocationEnabled && hasFineLocation && !routePickMode && !incidentPlaceMode && !hideNavigatorHud
    val hudScaleRef = rememberUpdatedState(mapHudPanelScale)
    val onHudScaleCb = rememberUpdatedState(onMapHudPanelScaleChange)

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
        if (showMapHud) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp, 8.dp, 4.dp, 4.dp)
                    .graphicsLayer {
                        scaleX = mapHudPanelScale
                        scaleY = mapHudPanelScale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .then(
                        if (onMapHudPanelScaleChange != null) {
                            Modifier.pointerInput(Unit) {
                                detectTransformGestures { _, _, zoomChange, _ ->
                                    val next = (hudScaleRef.value * zoomChange).coerceIn(
                                        TRAVEL_MAP_HUD_PANEL_SCALE_MIN,
                                        TRAVEL_MAP_HUD_PANEL_SCALE_MAX,
                                    )
                                    onHudScaleCb.value?.invoke(next)
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 3.dp,
            ) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    TravelNavigatorHud(
                        navHud = if (activeTravelRoute != null) navHudState else null,
                        fallbackSpeedKmh = hudSpeedKmh.floatValue,
                        viewSpanM = hudViewSpanM.intValue,
                        mapZoom = mapZoom,
                        modifier = Modifier,
                    )
                    navigatorHudExtras?.invoke()
                }
            }
        }
        if (
            onTravelRecenterFabSlot == null &&
            showRecenterFab &&
            (animatedRecenterFabAlpha > 0.02f || recenterFabAlphaTarget > 0.02f)
        ) {
            FloatingActionButton(
                onClick = performRecenter,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 132.dp)
                    .alpha(animatedRecenterFabAlpha),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = stringResource(R.string.travel_recenter_map_cd),
                )
            }
        }
    }
}

@Composable
private fun TravelNavigatorHud(
    navHud: TravelNavHudState?,
    fallbackSpeedKmh: Float,
    viewSpanM: Int,
    mapZoom: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val speedKmh = navHud?.speedKmh ?: fallbackSpeedKmh.roundToInt()
    val limit = navHud?.speedLimitKmh
    val over = navHud?.isOverSpeedLimit == true
    val ring = if (over) Color(0xFFE53935) else Color(0xFFE0E0E0)
    val fill = Color(0xFF1E1E1E)
    val hudCd = stringResource(R.string.travel_nav_speed_cd)
    Column(
        modifier.semantics { contentDescription = hudCd },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                val strokePx = with(density) { 4.dp.toPx() }
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f - strokePx * 0.5f
                    drawCircle(color = fill, radius = r, center = c)
                    drawCircle(color = ring, radius = r, center = c, style = Stroke(width = strokePx))
                }
                Text(
                    text = "$speedKmh",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 34.sp,
                )
            }
            if (limit != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .padding(1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val strokePx = with(density) { 2.5.dp.toPx() }
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = size.minDimension / 2f - strokePx * 0.5f
                        drawCircle(color = fill, radius = r, center = c)
                        drawCircle(
                            color = Color(0xFFBDBDBD),
                            radius = r,
                            center = c,
                            style = Stroke(width = strokePx),
                        )
                    }
                    Text(
                        text = "$limit",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }
        }
        if (over) {
            Text(
                stringResource(R.string.travel_nav_slow_down),
                color = Color(0xFFE53935),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        val next = navHud?.nextRoadNote
        val dM = navHud?.nextRoadNoteDistanceM
        if (next != null && dM != null) {
            Text(
                stringResource(R.string.travel_nav_next_road_note, dM, next),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            stringResource(R.string.travel_map_hud_view_from_above, viewSpanM, mapZoom),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Метка на карте: школа / лежачий / знак. */
private fun travelHazardDotProvider(context: Context, argb: Int): ImageProvider {
    val d = (28 * context.resources.displayMetrics.density).toInt().coerceIn(24, 40)
    val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = d / 2f
    val cy = d / 2f
    val r = d * 0.38f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = d * 0.1f
    }
    canvas.drawCircle(cx, cy, r, fill)
    canvas.drawCircle(cx, cy, r, stroke)
    return ImageProvider.fromBitmap(bmp)
}

private fun travelHazardTextStyle(zoom: Float): TextStyle {
    val z = zoom.coerceIn(2f, 21f)
    val fontSize = (7.5f + (z - 3f) * 0.42f).coerceIn(7f, 14f)
    val outlineWidth = (1f + (z - 3f) * 0.07f).coerceIn(1f, 2.8f)
    return TextStyle(
        fontSize,
        0xE61A237E.toInt(),
        outlineWidth,
        0xFFFFFFFF.toInt(),
        TextStyle.Placement.BOTTOM,
        0f,
        false,
        false,
    )
}

private fun travelLerpD(from: Double, to: Double, t: Float): Double =
    from + (to - from) * t.toDouble()

/** Доля приближения к цели за кадр при экспоненциальном сглаживании (устойчиво к разному FPS). */
private fun travelExpSmoothAlpha(lambdaPerSec: Double, dtSec: Double): Double =
    (1.0 - kotlin.math.exp(-lambdaPerSec * dtSec)).coerceIn(0.0001, 1.0)

/** Линейная интерполяция угла в градусах с учётом перехода через 0/360. */
private fun lerpAngleDegrees(from: Float, to: Float, t: Float): Float {
    var d = (to - from) % 360f
    if (d < -180f) d += 360f
    if (d > 180f) d -= 360f
    var r = from + d * t
    r %= 360f
    if (r < 0f) r += 360f
    return r
}

/** Скорость в м/с; если датчик не даёт [Location.hasSpeed], оцениваем по смещению от предыдущей точки. */
private fun effectiveSpeedMps(loc: Location, prev: Location?): Float {
    if (loc.hasSpeed()) {
        return loc.speed.coerceAtLeast(0f)
    }
    if (prev != null) {
        val dtSec = (loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000_000f
        if (dtSec > 0.04f) {
            return (loc.distanceTo(prev) / dtSec).coerceAtLeast(0f)
        }
    }
    return 0f
}

private data class MkLocPrev(val lat: Double, val lon: Double, val relativeNs: Long)

/** Аналог [effectiveSpeedMps] для локаций MapKit (тяга Яндекса к навигатору — свой пайплайн). */
private fun effectiveSpeedMpsMapKit(loc: com.yandex.mapkit.location.Location, prev: MkLocPrev?): Float {
    val spd = loc.speed
    if (spd != null && spd >= 0.0) return spd.toFloat().coerceAtLeast(0f)
    val pos = loc.position
    if (prev != null) {
        val dtSec = (loc.relativeTimestamp - prev.relativeNs) / 1e9f
        if (dtSec > 0.04f) {
            val results = FloatArray(1)
            Location.distanceBetween(prev.lat, prev.lon, pos.latitude, pos.longitude, results)
            return (results[0] / dtSec).coerceAtLeast(0f)
        }
    }
    return 0f
}

private fun maneuverMapCaption(index: Int, m: TravelManeuverInfo): String {
    val base = "$index. ${m.shortPhrase}"
    return if (base.length <= 32) base else base.take(29) + "…"
}

/** Подписи поворотов / перекрёстков на маршруте. */
private fun travelManeuverTextStyle(zoom: Float): TextStyle {
    val z = zoom.coerceIn(2f, 21f)
    val fontSize = (8f + (z - 3f) * 0.48f).coerceIn(7f, 16f)
    val outlineWidth = (1.1f + (z - 3f) * 0.07f).coerceIn(1f, 3f)
    return TextStyle(
        fontSize,
        0xE51A237E.toInt(),
        outlineWidth,
        0xFFFFFFFF.toInt(),
        TextStyle.Placement.CENTER,
        0f,
        false,
        false,
    )
}

/** Размер подписи от масштаба: дальше отдаление — мельче текст (меньше наложений). */
private fun travelZoneNameTextStyle(zoom: Float): TextStyle {
    val z = zoom.coerceIn(2f, 21f)
    val fontSize = (8f + (z - 3f) * 0.52f).coerceIn(8f, 17f)
    val outlineWidth = (1.2f + (z - 3f) * 0.08f).coerceIn(1f, 3.5f)
    return TextStyle(
        fontSize,
        0xE6000000.toInt(),
        outlineWidth,
        0xFFFFFFFF.toInt(),
        TextStyle.Placement.CENTER,
        0f,
        false,
        false,
    )
}

private fun polygonVertexLabelStyle(zoom: Float): TextStyle {
    val z = zoom.coerceIn(2f, 21f)
    val fontSize = (10f + (z - 3f) * 0.45f).coerceIn(9f, 18f)
    val outlineWidth = (1f + (z - 3f) * 0.07f).coerceIn(1f, 3f)
    return TextStyle(
        fontSize,
        0xE6000000.toInt(),
        outlineWidth,
        0xFFFFFFFF.toInt(),
        TextStyle.Placement.CENTER,
        0f,
        false,
        false,
    )
}

private fun addZoneNameInside(
    zonesCollection: MapObjectCollection,
    lat: Double,
    lng: Double,
    name: String,
    transparentIcon: ImageProvider,
    zoom: Float,
) {
    val label = name.trim()
    if (label.isEmpty()) return
    val placemark = zonesCollection.addPlacemark(Point(lat, lng))
    placemark.setIcon(transparentIcon)
    placemark.setText(label, travelZoneNameTextStyle(zoom))
}

private fun redrawZoneOverlays(
    zonesCollection: MapObjectCollection,
    zones: List<TravelZone>,
    polygonDraft: List<TravelGeoPoint>,
    transparentLabelIcon: ImageProvider,
    zoneAudioBadgeIcon: ImageProvider,
    territoryEditMode: Boolean,
    selectedZoneId: String?,
    omitPolygonZoneId: String?,
    mapZoom: Float,
) {
    zonesCollection.clear()
    val toShow = if (territoryEditMode) zones else zones.filter { it.enabled }
    toShow.forEach { z ->
        val isSelected = z.id == selectedZoneId
        val isDisabled = !z.enabled
        when (z.kind) {
            TravelZoneKind.CIRCLE -> {
                val circle = zonesCollection.addCircle(
                    Circle(Point(z.centerLat, z.centerLng), max(z.radiusMeters, TRAVEL_ZONE_CIRCLE_RADIUS_MIN_M)),
                )
                circle.strokeWidth = if (isSelected) 4f else 2f
                when {
                    isSelected -> {
                        circle.strokeColor = 0xFFFFC107.toInt()
                        circle.fillColor = 0x44FFC107.toInt()
                    }
                    territoryEditMode && isDisabled -> {
                        circle.strokeColor = 0xFF78909C.toInt()
                        circle.fillColor = 0x2278909C.toInt()
                    }
                    else -> {
                        circle.strokeColor = 0xFF00BCD4.toInt()
                        circle.fillColor = 0x3300BCD4.toInt()
                    }
                }
                addZoneNameInside(zonesCollection, z.centerLat, z.centerLng, z.name, transparentLabelIcon, mapZoom)
                addZoneAudioBadgeIfNeeded(
                    zonesCollection,
                    z,
                    z.centerLat + 0.00007,
                    z.centerLng + 0.00011,
                    zoneAudioBadgeIcon,
                )
            }
            TravelZoneKind.POLYGON -> {
                if (z.id == omitPolygonZoneId) return@forEach
                if (z.polygonPoints.size >= 3) {
                    val pts = ArrayList<Point>()
                    z.polygonPoints.forEach { pts.add(Point(it.latitude, it.longitude)) }
                    val ring = LinearRing(pts)
                    val poly = zonesCollection.addPolygon(Polygon(ring, ArrayList()))
                    poly.strokeWidth = if (isSelected) 4f else 2f
                    when {
                        isSelected -> {
                            poly.strokeColor = 0xFFFFC107.toInt()
                            poly.fillColor = 0x44FFC107.toInt()
                        }
                        territoryEditMode && isDisabled -> {
                            poly.strokeColor = 0xFF78909C.toInt()
                            poly.fillColor = 0x2278909C.toInt()
                        }
                        else -> {
                            poly.strokeColor = 0xFF4CAF50.toInt()
                            poly.fillColor = 0x334CAF50.toInt()
                        }
                    }
                    val (iLat, iLng) = polygonInteriorPoint(z.polygonPoints)
                    addZoneNameInside(zonesCollection, iLat, iLng, z.name, transparentLabelIcon, mapZoom)
                    addZoneAudioBadgeIfNeeded(
                        zonesCollection,
                        z,
                        iLat + 0.00007,
                        iLng + 0.00011,
                        zoneAudioBadgeIcon,
                    )
                }
            }
        }
    }
    val vertexStyle = polygonVertexLabelStyle(mapZoom)
    polygonDraft.forEachIndexed { i, pt ->
        val pm = zonesCollection.addPlacemark(Point(pt.latitude, pt.longitude))
        pm.setText("${i + 1}", vertexStyle)
    }
    if (polygonDraft.size >= 3) {
        val pts = ArrayList<Point>()
        polygonDraft.forEach { pts.add(Point(it.latitude, it.longitude)) }
        val ring = LinearRing(pts)
        val poly = zonesCollection.addPolygon(Polygon(ring, ArrayList()))
        poly.strokeWidth = 2f
        poly.strokeColor = 0xFFFF9800.toInt()
        poly.fillColor = 0x44FF9800.toInt()
    }
}

/**
 * Равномерные точки вдоль маршрута с азимутом сегмента — тонкие стрелки как у навигатора в полосах.
 */
private fun sampleLaneArrowsAlongPolyline(polyline: Polyline, spacingM: Float): List<Pair<Point, Float>> {
    val pts = polyline.points
    if (pts.size < 2) return emptyList()
    val spacing = spacingM.coerceAtLeast(10f)
    val segments = ArrayList<Triple<Point, Point, Float>>()
    var totalLen = 0f
    for (i in 0 until pts.size - 1) {
        val a = pts[i]
        val b = pts[i + 1]
        val dist = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, dist)
        val len = dist[0]
        if (len > 0.5f) {
            segments += Triple(a, b, len)
            totalLen += len
        }
    }
    if (segments.isEmpty() || totalLen <= 0f) return emptyList()
    val out = ArrayList<Pair<Point, Float>>()
    var cursor = spacing * 0.35f
    var cumulative = 0f
    var si = 0
    while (cursor <= totalLen && si < segments.size) {
        while (si < segments.size && cumulative + segments[si].third < cursor) {
            cumulative += segments[si].third
            si++
        }
        if (si >= segments.size) break
        val (a, b, len) = segments[si]
        val t = ((cursor - cumulative) / len).coerceIn(0f, 1f)
        val lat = a.latitude + (b.latitude - a.latitude) * t
        val lon = a.longitude + (b.longitude - a.longitude) * t
        val bearingDeg = bearingDegreesLatLon(a.latitude, a.longitude, b.latitude, b.longitude)
        out.add(Point(lat, lon) to bearingDeg)
        cursor += spacing
    }
    return out
}

/** Компактная тёмная стрелка «вверх» по экрану bitmap для [RotationType.ROTATE] и [bearingDegreesLatLon]. */
private fun laneDirectionArrowImageProvider(context: android.content.Context): ImageProvider {
    val d = (28 * context.resources.displayMetrics.density).toInt().coerceIn(22, 44)
    val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = d / 2f
    val cy = d / 2f
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDD212121.toInt()
        style = Paint.Style.STROKE
        strokeWidth = max(1f, d * 0.048f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val stemHalf = d * 0.34f
    val tipY = cy - stemHalf * 0.85f
    val stemBot = cy + stemHalf * 0.55f
    canvas.drawLine(cx, stemBot, cx, tipY + d * 0.06f, stroke)
    val wing = d * 0.13f
    val wingBase = tipY + d * 0.11f
    canvas.drawLine(cx - wing * 0.15f, wingBase, cx - wing, wingBase + wing * 0.95f, stroke)
    canvas.drawLine(cx + wing * 0.15f, wingBase, cx + wing, wingBase + wing * 0.95f, stroke)
    return ImageProvider.fromBitmap(bmp)
}

/** Маркер виртуального проезда: рисунок прежнего размера, bitmap с прозрачным полем — MapKit ловит drag по большей области. */
private fun routePlaybackWalkerImageProvider(context: android.content.Context): ImageProvider {
    val density = context.resources.displayMetrics.density
    val inner = (52 * density).toInt().coerceIn(44, 88)
    val outer = (inner * ROUTE_WALKER_TOUCH_BITMAP_FACTOR).toInt().coerceIn(inner + 32, 260)
    val bmp = Bitmap.createBitmap(outer, outer, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val padX = (outer - inner) / 2f
    val padY = (outer - inner) / 2f
    canvas.translate(padX, padY)
    val d = inner.toFloat()
    val cx = d / 2f
    val cy = d / 2f - d * 0.06f
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF6F00.toInt() }
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = max(2f, d * 0.11f)
    }
    canvas.drawCircle(cx, cy, d * 0.3f, body)
    canvas.drawCircle(cx, cy, d * 0.3f, rim)
    val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
    canvas.drawCircle(cx, cy - d * 0.34f, d * 0.11f, head)
    return ImageProvider.fromBitmap(bmp)
}

/**
 * Стрелка вверх по bitmap: при [RotationType.ROTATE] и direction, совпадающем с азимутом камеры,
 * метка визуально совпадает с режимом навигатора (нет «ползущего» нативного GPS-пина).
 */
private fun userNavigationArrowImageProvider(context: android.content.Context): ImageProvider {
    val d = (52 * context.resources.displayMetrics.density).toInt().coerceIn(44, 88)
    val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = d / 2f
    val cy = d / 2f
    val tri = Path().apply {
        moveTo(cx, cy - d * 0.38f)
        lineTo(cx - d * 0.24f, cy + d * 0.28f)
        lineTo(cx + d * 0.24f, cy + d * 0.28f)
        close()
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E88E5.toInt()
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = d * 0.08f
    }
    canvas.drawPath(tri, fill)
    canvas.drawPath(tri, stroke)
    return ImageProvider.fromBitmap(bmp)
}

private fun friendPeerPinImageProvider(context: android.content.Context): ImageProvider {
    val d = (44 * context.resources.displayMetrics.density).toInt().coerceIn(36, 72)
    val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E88E5.toInt() }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = d * 0.1f
    }
    val pad = d * 0.12f
    canvas.drawOval(RectF(pad, pad, d - pad, d - pad), fill)
    canvas.drawOval(RectF(pad, pad, d - pad, d - pad), stroke)
    return ImageProvider.fromBitmap(bmp)
}

private fun incidentPinImageProvider(context: android.content.Context): ImageProvider {
    val d = (44 * context.resources.displayMetrics.density).toInt().coerceIn(36, 72)
    val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = d * 0.1f
    }
    val pad = d * 0.12f
    canvas.drawOval(RectF(pad, pad, d - pad, d - pad), fill)
    canvas.drawOval(RectF(pad, pad, d - pad, d - pad), stroke)
    return ImageProvider.fromBitmap(bmp)
}

/** Метка с личным звуком: красный кружок + синий бейдж «♪». */
private fun incidentPinWithAudioImageProvider(context: android.content.Context): ImageProvider {
    val d = (44 * context.resources.displayMetrics.density).toInt().coerceIn(36, 72)
    val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = d * 0.1f
    }
    val pad = d * 0.12f
    canvas.drawOval(RectF(pad, pad, d - pad, d - pad), fill)
    canvas.drawOval(RectF(pad, pad, d - pad, d - pad), stroke)
    val badgeR = d * 0.24f
    val cx = d - pad - badgeR * 0.35f
    val cy = d - pad - badgeR * 0.35f
    val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1565C0.toInt() }
    val badgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = d * 0.06f
    }
    canvas.drawCircle(cx, cy, badgeR, badgeBg)
    canvas.drawCircle(cx, cy, badgeR, badgeStroke)
    val note = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = badgeR * 1.35f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val fm = note.fontMetrics
    canvas.drawText("♪", cx, cy - (fm.ascent + fm.descent) / 2f, note)
    return ImageProvider.fromBitmap(bmp)
}

/** Маленький значок у зоны с собственным звуковым файлом. */
private fun zoneAudioBadgeImageProvider(context: android.content.Context): ImageProvider {
    val d = (30 * context.resources.displayMetrics.density).toInt().coerceIn(26, 44)
    val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = d / 2f
    val cy = d / 2f
    val r = d * 0.42f
    val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE61565C0.toInt() }
    val badgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = d * 0.08f
    }
    canvas.drawCircle(cx, cy, r, badgeBg)
    canvas.drawCircle(cx, cy, r, badgeStroke)
    val note = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = r * 1.5f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val fm = note.fontMetrics
    canvas.drawText("♪", cx, cy - (fm.ascent + fm.descent) / 2f, note)
    return ImageProvider.fromBitmap(bmp)
}

private fun addZoneAudioBadgeIfNeeded(
    zonesCollection: MapObjectCollection,
    zone: TravelZone,
    badgeLat: Double,
    badgeLng: Double,
    badgeIcon: ImageProvider,
) {
    if (zone.action != TravelTriggerAction.PLAY_SOUND || zone.mediaUri.isNullOrBlank()) return
    zonesCollection.addPlacemark(Point(badgeLat, badgeLng), badgeIcon)
}

private fun incidentLabelStyle(zoom: Float): TextStyle {
    val z = zoom.coerceIn(2f, 21f)
    val fontSize = (11f + (z - 3f) * 0.5f).coerceIn(10f, 19f)
    val outlineWidth = (1.2f + (z - 3f) * 0.08f).coerceIn(1f, 3f)
    return TextStyle(
        fontSize,
        0xE6FFFFFF.toInt(),
        outlineWidth,
        0xFF212121.toInt(),
        TextStyle.Placement.BOTTOM,
        0f,
        false,
        false,
    )
}

private fun redrawIncidentPins(
    pinsCollection: MapObjectCollection,
    incidents: List<TravelMapIncident>,
    iconNormal: ImageProvider,
    iconWithAudio: ImageProvider,
    mapZoom: Float,
) {
    pinsCollection.clear()
    val style = incidentLabelStyle(mapZoom)
    for (inc in incidents) {
        val icon = if (inc.soundUri.isNullOrBlank()) iconNormal else iconWithAudio
        val pm = pinsCollection.addPlacemark(Point(inc.latitude, inc.longitude), icon)
        val label = inc.note.trim().ifEmpty { "!" }
        val short = if (label.length > 18) label.take(17) + "…" else label
        pm.setText(short, style)
    }
}

/** Центр подписи полигона — среднее координат вершин (совпадает с логикой сохранения зоны). */
private fun polygonInteriorPoint(points: List<TravelGeoPoint>): Pair<Double, Double> {
    if (points.isEmpty()) return 0.0 to 0.0
    val cLat = points.sumOf { it.latitude } / points.size
    val cLng = points.sumOf { it.longitude } / points.size
    return cLat to cLng
}
