package com.example.bible.ui.travel

import android.content.Intent
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bible.R
import com.example.bible.data.travel.TravelGeoPoint
import com.example.bible.data.travel.TravelMarkerSoundTrigger
import com.example.bible.data.travel.TravelTriggerAction
import com.example.bible.data.travel.TravelZoneTapSound
import com.example.bible.data.travel.TravelUserSoundStorage
import com.example.bible.data.travel.TravelZone
import com.example.bible.data.travel.TravelZoneKind
import com.example.bible.data.travel.travelDistanceMeters
import com.example.bible.data.travel.parseLatLonManualLine
import com.example.bible.data.travel.travelZonesAtPoint
import com.example.bible.ui.KeepScreenOnEffect
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.bible.data.travel.TravelPhotoStorage
import com.example.bible.data.travel.TravelRoutePhotoPoint
import com.example.bible.data.travel.TravelRoutePhotoSession
import java.util.UUID
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTravelsScreen(
    onBack: () -> Unit,
) {
    KeepScreenOnEffect()
    val context = LocalContext.current
    val vm: TravelViewModel = viewModel()
    val zones by vm.zones.collectAsStateWithLifecycle()
    val polyMonitor by vm.polygonMonitorEnabled.collectAsStateWithLifecycle()
    val voiceHints by vm.polygonVoiceHintsEnabled.collectAsStateWithLifecycle()
    val mapKitApiKey by vm.mapKitApiKeyForMap.collectAsStateWithLifecycle()
    val userMapKitKeyStored by vm.userMapKitKeyStored.collectAsStateWithLifecycle()
    val mapCameraSnapshot by vm.mapCameraSnapshot.collectAsStateWithLifecycle()
    val editMode by vm.editMode.collectAsStateWithLifecycle()
    val polygonDraft by vm.polygonDraft.collectAsStateWithLifecycle()
    val cameraJumpTo by vm.cameraJumpTo.collectAsStateWithLifecycle()
    val pendingSave by vm.pendingSave.collectAsStateWithLifecycle()
    val travelMenuExpanded by vm.travelMenuExpanded.collectAsStateWithLifecycle()
    val showMapKitSettings by vm.showMapKitSettings.collectAsStateWithLifecycle()
    val showListSheet by vm.showListSheet.collectAsStateWithLifecycle()
    val territoryEditEnabled by vm.territoryEditEnabled.collectAsStateWithLifecycle()
    val territoryPanelBelowMap by vm.territoryPanelBelowMap.collectAsStateWithLifecycle()
    val routePickDestination by vm.routePickDestination.collectAsStateWithLifecycle()
    val travelRouteClearNonce by vm.travelRouteClearNonce.collectAsStateWithLifecycle()
    val activeTravelRoute by vm.activeTravelRoute.collectAsStateWithLifecycle()
    val mapIncidents by vm.mapIncidents.collectAsStateWithLifecycle()
    val showMarkersEditSheet by vm.showMarkersEditSheet.collectAsStateWithLifecycle()
    val markerDefaultSoundUri by vm.markerDefaultSoundUri.collectAsStateWithLifecycle()
    val polygonEntrySoundUri by vm.polygonEntrySoundUri.collectAsStateWithLifecycle()
    val markerProximityEnabled by vm.markerProximityEnabled.collectAsStateWithLifecycle()
    val incidentPlaceMode by vm.incidentPlaceMode.collectAsStateWithLifecycle()
    val selectedZoneIdForEdit by vm.selectedZoneIdForEdit.collectAsStateWithLifecycle()
    val pendingCircleRecenterZoneId by vm.pendingCircleRecenterZoneId.collectAsStateWithLifecycle()
    val polygonRedraftZoneId by vm.polygonRedraftZoneId.collectAsStateWithLifecycle()
    val zonePropertiesEditId by vm.zonePropertiesEditId.collectAsStateWithLifecycle()
    val routePhotoSessions by vm.routePhotoSessions.collectAsStateWithLifecycle()
    val routeBurstActive by vm.routeBurstActive.collectAsStateWithLifecycle()
    val routePlaybackActive by vm.routePlaybackActive.collectAsStateWithLifecycle()
    val routePlaybackSessionIndex by vm.routePlaybackSessionIndex.collectAsStateWithLifecycle()
    val routePlaybackSim by vm.routePlaybackSim.collectAsStateWithLifecycle()
    val routePlaybackSpeedMps by vm.routePlaybackSpeedMps.collectAsStateWithLifecycle()
    val friendPeerLocation by vm.friendPeerLocation.collectAsStateWithLifecycle()
    val friendPeerPollUrl by vm.friendPeerPollUrl.collectAsStateWithLifecycle()
    val friendPeerPollIntervalSec by vm.friendPeerPollIntervalSec.collectAsStateWithLifecycle()
    val friendPeerPollEnabled by vm.friendPeerPollEnabled.collectAsStateWithLifecycle()
    val lastUserGeo by vm.lastUserGeo.collectAsStateWithLifecycle()
    val lastUserHeadingDeg by vm.lastUserHeadingDeg.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var burstSessionIdLocal by remember { mutableStateOf<String?>(null) }
    var deleteRouteSessionDialog by remember { mutableStateOf(false) }
    var deleteAllRouteSessionsConfirm by remember { mutableStateOf(false) }
    var showRoutePhotosManageSheet by remember { mutableStateOf(false) }
    var showFriendPeerSheet by remember { mutableStateOf(false) }
    var friendPeerManualDialog by remember { mutableStateOf(false) }
    var friendPeerUrlDraft by remember { mutableStateOf("") }
    var friendPeerManualLine by remember { mutableStateOf("") }
    var friendPeerManualLabel by remember { mutableStateOf("") }
    val burstDraftPoints = remember { mutableStateListOf<TravelRoutePhotoPoint>() }
    var burstImageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val burstCaptureExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { burstCaptureExecutor.shutdown() }
    }

    var camGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val camPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        camGranted = ok
        if (!ok) {
            Toast.makeText(context, R.string.travel_camera_permission_denied, Toast.LENGTH_LONG).show()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    /** Масштаб карточки превью виртуального проезда (сохраняется при повороте экрана). */
    var routePlaybackPreviewScale by rememberSaveable { mutableFloatStateOf(1f) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val markersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(showFriendPeerSheet) {
        if (showFriendPeerSheet) friendPeerUrlDraft = friendPeerPollUrl
    }

    var hasFineLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasBackgroundLocation by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }
    var hasNotifications by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }

    val refreshLocationPermissions: () -> Unit = {
        hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    DisposableEffect(routeBurstActive, hasFineLocation) {
        if (!routeBurstActive || !hasFineLocation) {
            vm.clearUserHeading()
            return@DisposableEffect onDispose { }
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            vm.clearUserHeading()
            return@DisposableEffect onDispose { }
        }
        val rMat = FloatArray(9)
        val orient = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                SensorManager.getRotationMatrixFromVector(rMat, event.values)
                SensorManager.getOrientation(rMat, orient)
                var az = Math.toDegrees(orient[0].toDouble()).toFloat()
                if (az < 0f) az += 360f
                vm.reportUserHeading(az)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose {
            sensorManager.unregisterListener(listener)
            vm.clearUserHeading()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshLocationPermissions()
                camGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backgroundPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refreshLocationPermissions()
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Toast.makeText(
                context,
                R.string.travel_background_denied,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        hasFineLocation = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (Build.VERSION.SDK_INT >= 33) {
            hasNotifications = granted[Manifest.permission.POST_NOTIFICATIONS] == true
        }
        refreshLocationPermissions()
    }

    LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        val need = perms.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need) permLauncher.launch(perms)
        refreshLocationPermissions()
    }

    val apiKeyPresent = mapKitApiKey.isNotBlank()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var toolbarInteractionNonce by remember { mutableLongStateOf(0L) }
    var showFloatingToolbar by remember { mutableStateOf(false) }
    val stickyFloatingToolbar =
        territoryEditEnabled ||
            editMode != TravelMapEditMode.VIEW ||
            incidentPlaceMode ||
            routePickDestination

    LaunchedEffect(territoryPanelBelowMap) {
        if (!territoryPanelBelowMap) {
            showFloatingToolbar = false
            toolbarInteractionNonce = 0L
        }
    }

    val bumpFloatingToolbar: () -> Unit = { toolbarInteractionNonce++ }

    LaunchedEffect(toolbarInteractionNonce, stickyFloatingToolbar, territoryPanelBelowMap, apiKeyPresent) {
        if (!territoryPanelBelowMap || !apiKeyPresent) return@LaunchedEffect
        if (toolbarInteractionNonce == 0L && !stickyFloatingToolbar) return@LaunchedEffect
        if (stickyFloatingToolbar) {
            showFloatingToolbar = true
            return@LaunchedEffect
        }
        showFloatingToolbar = true
        delay(4200)
        showFloatingToolbar = false
    }

    val burstActiveRef = rememberUpdatedState(routeBurstActive)
    val burstSidRef = rememberUpdatedState(burstSessionIdLocal)
    val lastGeoRef = rememberUpdatedState(lastUserGeo)
    val headingRef = rememberUpdatedState(lastUserHeadingDeg)

    LaunchedEffect(routeBurstActive, burstImageCapture, burstSessionIdLocal) {
        if (!routeBurstActive) return@LaunchedEffect
        val sid = burstSessionIdLocal ?: return@LaunchedEffect
        while (burstActiveRef.value && burstSidRef.value == sid) {
            val ic = burstImageCapture ?: break
            val geo = lastGeoRef.value
            val file = TravelPhotoStorage.createRouteBurstImageFile(context, sid)
            val ok = ic.captureToFileSuspend(file, burstCaptureExecutor)
            if (ok && geo != null) {
                burstDraftPoints.add(
                    TravelRoutePhotoPoint(
                        latitude = geo.latitude,
                        longitude = geo.longitude,
                        photoUri = TravelPhotoStorage.toFileUriString(file.absolutePath),
                        capturedAtMs = System.currentTimeMillis(),
                        headingDeg = headingRef.value,
                    ),
                )
            }
            delay(500)
        }
    }

    val sortedPhotoSessions = remember(routePhotoSessions) {
        routePhotoSessions.sortedByDescending { it.createdAtMs }
    }
    val stopRouteBurstAndSave: () -> Unit = {
        vm.setRouteBurstActive(false)
        val sid = burstSessionIdLocal
        val pts = burstDraftPoints.toList()
        burstSessionIdLocal = null
        burstDraftPoints.clear()
        scope.launch {
            if (sid != null && pts.isNotEmpty()) {
                vm.saveRouteBurstSession(TravelRoutePhotoSession(id = sid, points = pts))
            }
        }
    }

    val requestStartRouteBurst: () -> Unit = {
        bumpFloatingToolbar()
        when {
            !hasFineLocation -> {
                Toast.makeText(context, R.string.travel_need_location, Toast.LENGTH_LONG).show()
            }
            incidentPlaceMode || routePickDestination -> {
                Toast.makeText(context, R.string.travel_route_photo_modes_conflict, Toast.LENGTH_SHORT).show()
            }
            !camGranted -> {
                camPermLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                vm.setRoutePlaybackActive(false)
                burstDraftPoints.clear()
                burstSessionIdLocal = UUID.randomUUID().toString()
                vm.setRouteBurstActive(true)
            }
        }
    }

    var incidentDraftPoint by remember { mutableStateOf<TravelGeoPoint?>(null) }
    var incidentNoteDraft by remember { mutableStateOf("") }

    LaunchedEffect(zonePropertiesEditId, zones.map { it.id }) {
        val id = zonePropertiesEditId ?: return@LaunchedEffect
        if (zones.none { it.id == id }) {
            vm.closeZoneProperties()
        }
    }

    val jumpCameraToMyLocation: () -> Unit = {
        scope.launch {
            if (!hasFineLocation) {
                Toast.makeText(context, R.string.travel_need_location, Toast.LENGTH_LONG).show()
                return@launch
            }
            @SuppressLint("MissingPermission")
            runCatching {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val loc = client.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token,
                ).await()
                if (loc != null) {
                    vm.setCameraJump(TravelGeoPoint(loc.latitude, loc.longitude))
                } else {
                    Toast.makeText(context, R.string.travel_gps_failed, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(context, R.string.travel_gps_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val shareMyCoordinates: () -> Unit = {
        bumpFloatingToolbar()
        scope.launch {
            if (!hasFineLocation) {
                Toast.makeText(context, R.string.travel_need_location, Toast.LENGTH_LONG).show()
                return@launch
            }
            @SuppressLint("MissingPermission")
            runCatching {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val loc = client.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token,
                ).await()
                if (loc != null) {
                    val text =
                        "${String.format(Locale.US, "%.6f", loc.latitude)}, ${String.format(Locale.US, "%.6f", loc.longitude)}"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.travel_share_coords_subject))
                    }
                    context.startActivity(
                        Intent.createChooser(intent, context.getString(R.string.travel_share_coords_subject)),
                    )
                } else {
                    Toast.makeText(context, R.string.travel_share_coords_failed, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(context, R.string.travel_share_coords_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.travel_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { vm.setTravelMenuExpanded(true) }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.travel_more_menu),
                            )
                        }
                        DropdownMenu(
                            expanded = travelMenuExpanded,
                            onDismissRequest = { vm.setTravelMenuExpanded(false) },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.travel_menu_map_key)) },
                                onClick = {
                                    vm.setTravelMenuExpanded(false)
                                    vm.setShowMapKitSettings(true)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (territoryPanelBelowMap) {
                                                R.string.travel_menu_hide_territory_panel
                                            } else {
                                                R.string.travel_menu_show_territory_panel
                                            },
                                        ),
                                    )
                                },
                                onClick = {
                                    vm.setTravelMenuExpanded(false)
                                    vm.setTerritoryPanelBelowMap(!territoryPanelBelowMap)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(stringResource(R.string.travel_polygon_monitor), Modifier.weight(1f))
                                        Text(if (polyMonitor) "✓" else "")
                                    }
                                },
                                onClick = {
                                    vm.setTravelMenuExpanded(false)
                                    if (!polyMonitor) {
                                        if (!hasFineLocation) {
                                            Toast.makeText(
                                                context,
                                                R.string.travel_need_location,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        } else {
                                            vm.setPolygonMonitor(true)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocation) {
                                                backgroundPermLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                            }
                                        }
                                    } else {
                                        vm.setPolygonMonitor(false)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(stringResource(R.string.travel_voice_hints), Modifier.weight(1f))
                                        Text(if (voiceHints) "✓" else "")
                                    }
                                },
                                onClick = {
                                    vm.setTravelMenuExpanded(false)
                                    vm.setPolygonVoiceHints(!voiceHints)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.travel_list)) },
                                onClick = {
                                    vm.setTravelMenuExpanded(false)
                                    vm.setShowListSheet(true)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.travel_menu_edit_markers)) },
                                onClick = {
                                    vm.setTravelMenuExpanded(false)
                                    vm.setShowMarkersEditSheet(true)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.travel_menu_center_gps)) },
                                onClick = {
                                    vm.setTravelMenuExpanded(false)
                                    jumpCameraToMyLocation()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.travel_menu_build_route)) },
                                onClick = {
                                    vm.setTravelMenuExpanded(false)
                                    if (!hasFineLocation) {
                                        Toast.makeText(
                                            context,
                                            R.string.travel_need_location,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    } else {
                                        vm.setRoutePickMode(true)
                                        Toast.makeText(
                                            context,
                                            R.string.travel_route_tap_destination,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.travel_menu_clear_route)) },
                                onClick = {
                                    vm.setTravelMenuExpanded(false)
                                    vm.clearTravelRoute()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (incidentPlaceMode) {
                                                R.string.travel_incident_mode_stop
                                            } else {
                                                R.string.travel_menu_place_incident
                                            },
                                        ),
                                    )
                                },
                                onClick = {
                                    vm.setTravelMenuExpanded(false)
                                    if (!hasFineLocation) {
                                        Toast.makeText(
                                            context,
                                            R.string.travel_need_location,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    } else {
                                        val turningOff = incidentPlaceMode
                                        vm.setIncidentPlaceMode(!incidentPlaceMode)
                                        if (turningOff) {
                                            incidentDraftPoint = null
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.travel_menu_clear_incidents)) },
                                onClick = {
                                    vm.setTravelMenuExpanded(false)
                                    vm.clearMapIncidents()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.travel_friend_peer_menu)) },
                                onClick = {
                                    vm.setTravelMenuExpanded(false)
                                    showFriendPeerSheet = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.travel_route_delete_all_sessions_menu)) },
                                onClick = {
                                    vm.setTravelMenuExpanded(false)
                                    deleteAllRouteSessionsConfirm = true
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {},
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (!apiKeyPresent) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Text(
                            stringResource(R.string.travel_maps_key_missing),
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                } else {
                    val selectedZone = selectedZoneIdForEdit?.let { id -> zones.find { it.id == id } }
                    YandexTravelMap(
                        mapKitApiKey = mapKitApiKey,
                        modifier = Modifier.fillMaxSize(),
                        zones = zones,
                        polygonDraft = polygonDraft,
                        userLocationEnabled = hasFineLocation,
                        headingModeActive = hasFineLocation &&
                            editMode == TravelMapEditMode.VIEW &&
                            !territoryEditEnabled,
                        mapCameraSnapshot = mapCameraSnapshot,
                        onPersistMapCamera = vm::persistMapCamera,
                        territoryEditMode = territoryEditEnabled,
                        selectedZoneId = selectedZoneIdForEdit,
                        omitPolygonZoneId = polygonRedraftZoneId,
                        onMapTap = { pt ->
                            bumpFloatingToolbar()
                            if (editMode == TravelMapEditMode.VIEW && !territoryEditEnabled &&
                                pendingCircleRecenterZoneId == null && !routePickDestination && !incidentPlaceMode
                            ) {
                                val hit = mapIncidents.mapNotNull { inc ->
                                    val d = travelDistanceMeters(
                                        pt,
                                        TravelGeoPoint(inc.latitude, inc.longitude),
                                    )
                                    if (d <= TravelMarkerSoundTrigger.TAP_MAX_DISTANCE_METERS) {
                                        inc to d
                                    } else {
                                        null
                                    }
                                }.minByOrNull { it.second }?.first
                                if (hit != null) {
                                    val uri = hit.soundUri ?: markerDefaultSoundUri
                                    if (!uri.isNullOrBlank()) {
                                        TravelMarkerSoundTrigger.onMapTapNear(
                                            context,
                                            hit.id,
                                            uri,
                                            hit.note.trim().ifBlank {
                                                context.getString(R.string.travel_incidents_header)
                                            },
                                        )
                                    }
                                }
                                val markerHadAudio = hit != null &&
                                    !(hit.soundUri ?: markerDefaultSoundUri).isNullOrBlank()
                                if (!markerHadAudio) {
                                    val zoneAudio = travelZonesAtPoint(
                                        zones.filter { it.enabled },
                                        pt,
                                    ).firstOrNull {
                                        it.action == TravelTriggerAction.PLAY_SOUND &&
                                            !it.mediaUri.isNullOrBlank()
                                    }
                                    if (zoneAudio != null) {
                                        TravelZoneTapSound.onMapTapInZone(
                                            context,
                                            zoneAudio.id,
                                            zoneAudio.mediaUri!!,
                                            zoneAudio.name,
                                        )
                                    }
                                }
                            }
                            val recId = pendingCircleRecenterZoneId
                            when {
                                recId != null -> vm.applyCircleRecenter(recId, pt)
                                territoryEditEnabled && editMode == TravelMapEditMode.POLYGON_DRAW &&
                                    polygonRedraftZoneId != null -> vm.addPolygonDraftPoint(pt)
                                territoryEditEnabled -> {
                                    val hit = travelZonesAtPoint(zones, pt).firstOrNull()
                                    vm.selectZoneForEdit(hit?.id)
                                }
                                else -> when (editMode) {
                                    TravelMapEditMode.CIRCLE_TAP -> {
                                        vm.setPendingSave(TravelPendingZoneSave.CircleZone(pt, 150f))
                                    }
                                    TravelMapEditMode.POLYGON_DRAW -> {
                                        vm.addPolygonDraftPoint(pt)
                                    }
                                    TravelMapEditMode.VIEW -> {}
                                }
                            }
                        },
                        cameraJumpTo = cameraJumpTo,
                        onCameraJumpConsumed = { vm.consumeCameraJump() },
                        routePickMode = routePickDestination,
                        routeClearNonce = travelRouteClearNonce,
                        hasFineLocation = hasFineLocation,
                        onTravelRouteMessage = { resId ->
                            Toast.makeText(context, resId, Toast.LENGTH_LONG).show()
                        },
                        onTravelRouteBuilt = vm::onTravelRouteBuilt,
                        activeTravelRoute = activeTravelRoute,
                        onActiveTravelRouteChange = vm::setActiveTravelRoute,
                        mapIncidents = mapIncidents,
                        incidentPlaceMode = incidentPlaceMode,
                        onIncidentPlaced = { pt ->
                            incidentDraftPoint = pt
                            incidentNoteDraft = ""
                        },
                        onUserLocationUpdated = { lat, lng -> vm.reportUserLocation(lat, lng) },
                        routePhotoSessions = routePhotoSessions,
                        routePlaybackSim = routePlaybackSim,
                        friendPeerLocation = friendPeerLocation,
                    )
                    TravelBurstCameraPreview(
                        enabled = routeBurstActive && camGranted,
                        modifier = Modifier.align(Alignment.TopStart),
                        onImageCaptureReady = { burstImageCapture = it },
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                if (routeBurstActive) stopRouteBurstAndSave() else requestStartRouteBurst()
                            },
                            containerColor = if (routeBurstActive) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                        ) {
                            Icon(
                                if (routeBurstActive) Icons.Default.Stop else Icons.Default.CameraAlt,
                                contentDescription = stringResource(
                                    if (routeBurstActive) {
                                        R.string.travel_route_burst_stop_cd
                                    } else {
                                        R.string.travel_route_burst_start_cd
                                    },
                                ),
                            )
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                bumpFloatingToolbar()
                                if (routeBurstActive) stopRouteBurstAndSave()
                                when {
                                    incidentPlaceMode || routePickDestination -> {
                                        Toast.makeText(
                                            context,
                                            R.string.travel_route_photo_modes_conflict,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    !routePlaybackActive && sortedPhotoSessions.isEmpty() -> {
                                        Toast.makeText(
                                            context,
                                            R.string.travel_route_photo_no_sessions,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    else -> vm.setRoutePlaybackActive(!routePlaybackActive)
                                }
                            },
                            containerColor = if (routePlaybackActive) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ) {
                            Icon(
                                Icons.Default.VideoLibrary,
                                contentDescription = stringResource(R.string.travel_route_playback_cd),
                            )
                        }
                        if (routePlaybackActive && sortedPhotoSessions.size > 1) {
                            SmallFloatingActionButton(
                                onClick = {
                                    bumpFloatingToolbar()
                                    vm.cycleRoutePlaybackSession()
                                },
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = stringResource(
                                        R.string.travel_route_playback_next_session_cd,
                                    ),
                                )
                            }
                        }
                        if (sortedPhotoSessions.isNotEmpty()) {
                            SmallFloatingActionButton(
                                onClick = {
                                    bumpFloatingToolbar()
                                    deleteRouteSessionDialog = true
                                },
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.travel_route_delete_session_cd),
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (routePlaybackActive) {
                            Card(
                                modifier = Modifier.widthIn(max = 280.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                                ),
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        stringResource(R.string.travel_route_playback_speed_label),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Slider(
                                        value = routePlaybackSpeedMps,
                                        onValueChange = vm::setRoutePlaybackSpeedMps,
                                        valueRange = 0.5f..28f,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        stringResource(
                                            R.string.travel_route_playback_speed_fmt,
                                            routePlaybackSpeedMps,
                                            (routePlaybackSpeedMps * 3.6f).toInt(),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (routePlaybackSim?.currentPhotoUri != null) {
                            Card(
                                modifier = Modifier.widthIn(max = 280.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                                ),
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        stringResource(R.string.travel_route_preview_scale_label),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Slider(
                                        value = routePlaybackPreviewScale,
                                        onValueChange = { v ->
                                            routePlaybackPreviewScale = v.coerceIn(0.5f, 2.5f)
                                        },
                                        valueRange = 0.5f..2.5f,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        stringResource(
                                            R.string.travel_route_preview_scale_fmt,
                                            (routePlaybackPreviewScale * 100f).toInt(),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        routePlaybackSim?.let { sim ->
                            sim.currentPhotoUri?.let { uriStr ->
                                Card(
                                    modifier = Modifier
                                        .width((220 * routePlaybackPreviewScale).dp)
                                        .height((140 * routePlaybackPreviewScale).dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Box(Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = Uri.parse(uriStr),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Surface(
                                            modifier = Modifier.align(Alignment.BottomCenter),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                        ) {
                                            Text(
                                                "${(sim.progress * 100f).toInt()}% · ${sim.distanceAlongMeters.toInt()} м / ${sim.totalPathMeters.toInt()} м",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    TravelMapFloatingToolbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        visible = showFloatingToolbar,
                        enabled = territoryPanelBelowMap,
                        polygonSelected = editMode == TravelMapEditMode.POLYGON_DRAW,
                        markerModeActive = incidentPlaceMode,
                        routePickActive = routePickDestination,
                        circleModeActive = editMode == TravelMapEditMode.CIRCLE_TAP,
                        onPolygonClick = {
                            bumpFloatingToolbar()
                            vm.setIncidentPlaceMode(false)
                            vm.setRoutePickMode(false)
                            incidentDraftPoint = null
                            vm.togglePolygonDrawMode()
                        },
                        onMarkersClick = {
                            bumpFloatingToolbar()
                            if (!hasFineLocation) {
                                Toast.makeText(
                                    context,
                                    R.string.travel_need_location,
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                val turningOff = incidentPlaceMode
                                vm.setIncidentPlaceMode(!incidentPlaceMode)
                                if (turningOff) {
                                    incidentDraftPoint = null
                                }
                            }
                        },
                        onRouteClick = {
                            bumpFloatingToolbar()
                            if (routePickDestination) {
                                vm.clearTravelRoute()
                            } else if (!hasFineLocation) {
                                Toast.makeText(
                                    context,
                                    R.string.travel_need_location,
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                vm.setRoutePickMode(true)
                                Toast.makeText(
                                    context,
                                    R.string.travel_route_tap_destination,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        onShareClick = shareMyCoordinates,
                        onCircleClick = {
                            bumpFloatingToolbar()
                            vm.setIncidentPlaceMode(false)
                            vm.setRoutePickMode(false)
                            incidentDraftPoint = null
                            vm.toggleCircleDrawMode()
                        },
                    )
                    if (pendingCircleRecenterZoneId != null) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                                .widthIn(max = 340.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        ) {
                            Text(
                                stringResource(R.string.travel_edit_recenter_wait),
                                Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (routePickDestination) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                                .widthIn(max = 340.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.travel_route_tap_destination),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                TextButton(onClick = { vm.setRoutePickMode(false) }) {
                                    Text(stringResource(R.string.travel_cancel))
                                }
                            }
                        }
                    }
                    if (incidentPlaceMode) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = if (routePickDestination) 72.dp else 8.dp)
                                .widthIn(max = 340.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.travel_incident_tap_hint),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                TextButton(onClick = {
                                    vm.setIncidentPlaceMode(false)
                                    incidentDraftPoint = null
                                }) {
                                    Text(stringResource(R.string.travel_cancel))
                                }
                            }
                        }
                    }
                    if (editMode == TravelMapEditMode.POLYGON_DRAW && polygonDraft.isNotEmpty()) {
                        val isRedraft = polygonRedraftZoneId != null
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isRedraft) {
                                FilledTonalButton(
                                    onClick = { vm.cancelPolygonRedraft() },
                                ) {
                                    Text(stringResource(R.string.travel_edit_cancel_outline))
                                }
                            }
                            FilledTonalButton(
                                onClick = {
                                    if (polygonDraft.size < 3) {
                                        Toast.makeText(
                                            context,
                                            R.string.travel_polygon_need_three,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else if (isRedraft) {
                                        vm.applyPolygonRedraft()
                                        Toast.makeText(
                                            context,
                                            R.string.travel_edit_outline_saved,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        vm.setPendingSave(TravelPendingZoneSave.PolygonZone(polygonDraft))
                                    }
                                },
                            ) {
                                Icon(Icons.Default.AddLocationAlt, null, Modifier.padding(end = 4.dp))
                                Text(
                                    stringResource(
                                        if (isRedraft) {
                                            R.string.travel_edit_save_outline
                                        } else {
                                            R.string.travel_polygon_save
                                        },
                                    ),
                                )
                            }
                        }
                    }
                    if (territoryEditEnabled && polygonRedraftZoneId != null && polygonDraft.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 56.dp)
                                .widthIn(max = 340.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        ) {
                            Text(
                                stringResource(R.string.travel_edit_polygon_redraft_hint),
                                Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    selectedZone?.let { z ->
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                                .fillMaxWidth()
                                .widthIn(max = 400.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(z.name, style = MaterialTheme.typography.titleSmall)
                                if (z.kind == TravelZoneKind.CIRCLE) {
                                    var radiusDraft by remember(z.id) {
                                        mutableFloatStateOf(z.radiusMeters)
                                    }
                                    LaunchedEffect(z.id, z.radiusMeters) {
                                        radiusDraft = z.radiusMeters
                                    }
                                    Text(
                                        stringResource(R.string.travel_radius_m, radiusDraft.toInt()),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Slider(
                                        value = radiusDraft,
                                        onValueChange = { radiusDraft = it },
                                        onValueChangeFinished = {
                                            vm.updateZoneRadius(z.id, radiusDraft)
                                        },
                                        valueRange = 100f..2000f,
                                    )
                                    TextButton(
                                        onClick = { vm.setPendingCircleRecenter(z.id) },
                                        enabled = pendingCircleRecenterZoneId == null,
                                    ) {
                                        Text(stringResource(R.string.travel_edit_recenter))
                                    }
                                } else if (z.kind == TravelZoneKind.POLYGON) {
                                    TextButton(
                                        onClick = {
                                            vm.beginPolygonRedraft(z.id)
                                            Toast.makeText(
                                                context,
                                                R.string.travel_edit_polygon_redraft_hint,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        },
                                    ) {
                                        Text(stringResource(R.string.travel_edit_new_polygon_outline))
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    TextButton(onClick = { vm.openZoneProperties(z.id) }) {
                                        Text(stringResource(R.string.travel_edit_properties))
                                    }
                                    TextButton(onClick = { vm.selectZoneForEdit(null) }) {
                                        Text(stringResource(R.string.travel_edit_close_panel))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (territoryPanelBelowMap && apiKeyPresent) {
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 12.dp,
                            vertical = if (isLandscape) 4.dp else 6.dp,
                        ),
                ) {
                    Text(
                        stringResource(R.string.travel_edit_territories),
                        style = if (isLandscape) {
                            MaterialTheme.typography.labelLarge
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = territoryEditEnabled,
                        onCheckedChange = { vm.setTerritoryEditEnabled(it) },
                    )
                }
                Text(
                    stringResource(R.string.travel_edit_territories_hint),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isLandscape) 9.5.sp else MaterialTheme.typography.labelSmall.fontSize,
                        lineHeight = if (isLandscape) 12.sp else MaterialTheme.typography.labelSmall.lineHeight,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = if (isLandscape) 2.dp else 4.dp),
                )
            }
        }
    }

    if (showMapKitSettings) {
        TravelMapKitSettingsDialog(
            initialUserKey = userMapKitKeyStored,
            onDismiss = { vm.setShowMapKitSettings(false) },
            onSave = { typed ->
                scope.launch {
                    val needsRestart = vm.saveMapKitUserKey(typed)
                    vm.setShowMapKitSettings(false)
                    if (needsRestart) {
                        Toast.makeText(
                            context,
                            R.string.travel_mapkit_restart_hint,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
        )
    }

    pendingSave?.let { p ->
        SaveZoneDialog(
            pending = p,
            onDismiss = { vm.setPendingSave(null) },
            onSave = { zone ->
                vm.saveZone(zone)
                vm.setPendingSave(null)
                vm.clearPolygonDraftAndViewMode()
                Toast.makeText(context, R.string.travel_saved, Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (deleteRouteSessionDialog && sortedPhotoSessions.isNotEmpty()) {
        val deleteSid =
            sortedPhotoSessions[routePlaybackSessionIndex % sortedPhotoSessions.size].id
        val selSession = sortedPhotoSessions[routePlaybackSessionIndex % sortedPhotoSessions.size]
        AlertDialog(
            onDismissRequest = { deleteRouteSessionDialog = false },
            title = { Text(stringResource(R.string.travel_route_delete_session_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.travel_route_delete_session_message))
                    Text(
                        stringResource(R.string.travel_route_delete_session_hint_detail),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(
                        onClick = {
                            deleteRouteSessionDialog = false
                            showRoutePhotosManageSheet = true
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.travel_route_delete_session_manage_points))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteRoutePhotoSession(deleteSid)
                        deleteRouteSessionDialog = false
                        vm.setRoutePlaybackActive(false)
                    },
                ) {
                    Text(
                        stringResource(
                            R.string.travel_route_delete_session_confirm_fmt,
                            selSession.points.size,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteRouteSessionDialog = false }) {
                    Text(stringResource(R.string.travel_cancel))
                }
            },
        )
    }

    if (deleteAllRouteSessionsConfirm) {
        AlertDialog(
            onDismissRequest = { deleteAllRouteSessionsConfirm = false },
            title = { Text(stringResource(R.string.travel_route_delete_all_sessions_title)) },
            text = { Text(stringResource(R.string.travel_route_delete_all_sessions_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteAllRoutePhotoSessions()
                        deleteAllRouteSessionsConfirm = false
                    },
                ) {
                    Text(stringResource(R.string.travel_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAllRouteSessionsConfirm = false }) {
                    Text(stringResource(R.string.travel_cancel))
                }
            },
        )
    }

    if (showFriendPeerSheet) {
        ModalBottomSheet(onDismissRequest = { showFriendPeerSheet = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(R.string.travel_friend_peer_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    stringResource(R.string.travel_friend_peer_json_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.travel_friend_peer_poll_switch))
                    Switch(
                        checked = friendPeerPollEnabled,
                        onCheckedChange = { vm.setFriendPeerPollEnabled(it) },
                    )
                }
                OutlinedTextField(
                    value = friendPeerUrlDraft,
                    onValueChange = { friendPeerUrlDraft = it },
                    label = { Text(stringResource(R.string.travel_friend_peer_url_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                )
                TextButton(
                    onClick = {
                        vm.setFriendPeerPollUrl(friendPeerUrlDraft.trim())
                        Toast.makeText(context, R.string.travel_saved, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.travel_friend_peer_save_url))
                }
                Text(
                    stringResource(R.string.travel_friend_peer_interval_label, friendPeerPollIntervalSec),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Slider(
                    value = friendPeerPollIntervalSec.toFloat(),
                    onValueChange = {
                        vm.setFriendPeerPollIntervalSec(it.roundToInt().coerceIn(5, 300))
                    },
                    valueRange = 5f..300f,
                    modifier = Modifier.fillMaxWidth(),
                )
                val loc = friendPeerLocation
                if (loc != null) {
                    Text(
                        stringResource(
                            R.string.travel_friend_peer_current_fmt,
                            loc.latitude,
                            loc.longitude,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    loc.label?.takeIf { it.isNotBlank() }?.let { lb ->
                        Text(
                            lb,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.travel_friend_peer_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                FilledTonalButton(
                    onClick = {
                        vm.centerMapOnFriendPeer()
                        showFriendPeerSheet = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    enabled = friendPeerLocation != null,
                ) {
                    Text(stringResource(R.string.travel_friend_peer_center_map))
                }
                TextButton(
                    onClick = {
                        friendPeerManualLine = ""
                        friendPeerManualLabel = ""
                        friendPeerManualDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.travel_friend_peer_manual))
                }
                TextButton(
                    onClick = { vm.clearFriendPeerManual() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.travel_friend_peer_clear_manual))
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (friendPeerManualDialog) {
        AlertDialog(
            onDismissRequest = { friendPeerManualDialog = false },
            title = { Text(stringResource(R.string.travel_friend_peer_manual_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = friendPeerManualLine,
                        onValueChange = { friendPeerManualLine = it },
                        label = { Text(stringResource(R.string.travel_friend_peer_manual_coords_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                    )
                    OutlinedTextField(
                        value = friendPeerManualLabel,
                        onValueChange = { friendPeerManualLabel = it },
                        label = { Text(stringResource(R.string.travel_friend_peer_manual_label_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pair = parseLatLonManualLine(friendPeerManualLine)
                        if (pair == null) {
                            Toast.makeText(
                                context,
                                R.string.travel_friend_peer_invalid_coords,
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            vm.setFriendPeerManual(
                                pair.first,
                                pair.second,
                                friendPeerManualLabel.trim().takeIf { it.isNotEmpty() },
                            )
                            friendPeerManualDialog = false
                            Toast.makeText(context, R.string.travel_saved, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.travel_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { friendPeerManualDialog = false }) {
                    Text(stringResource(R.string.travel_cancel))
                }
            },
        )
    }

    if (showRoutePhotosManageSheet && sortedPhotoSessions.isNotEmpty()) {
        val sessionForManage =
            sortedPhotoSessions[routePlaybackSessionIndex % sortedPhotoSessions.size]
        ModalBottomSheet(
            onDismissRequest = { showRoutePhotosManageSheet = false },
        ) {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.travel_route_manage_photos_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(sessionForManage.points, key = { it.photoUri }) { pt ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = Uri.parse(pt.photoUri),
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            stringResource(
                                R.string.travel_route_photo_point_short,
                                pt.latitude,
                                pt.longitude,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    vm.removePhotosFromRouteSession(
                                        sessionForManage.id,
                                        setOf(pt.photoUri),
                                    )
                                }
                            },
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.travel_delete))
                        }
                    }
                }
            }
        }
    }

    incidentDraftPoint?.let { pt ->
        AlertDialog(
            onDismissRequest = {
                incidentDraftPoint = null
                incidentNoteDraft = ""
            },
            title = { Text(stringResource(R.string.travel_incident_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = incidentNoteDraft,
                    onValueChange = { incidentNoteDraft = it },
                    label = { Text(stringResource(R.string.travel_incident_note_label)) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.addMapIncidentAt(pt, incidentNoteDraft)
                        incidentDraftPoint = null
                        incidentNoteDraft = ""
                        Toast.makeText(context, R.string.travel_incident_saved, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(stringResource(R.string.travel_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        incidentDraftPoint = null
                        incidentNoteDraft = ""
                    },
                ) {
                    Text(stringResource(R.string.travel_cancel))
                }
            },
        )
    }

    zonePropertiesEditId?.let { propId ->
        zones.find { it.id == propId }?.let { z ->
            EditZonePropertiesDialog(
                zone = z,
                onDismiss = { vm.closeZoneProperties() },
                onSave = { updated ->
                    vm.saveZone(updated)
                    vm.closeZoneProperties()
                    Toast.makeText(context, R.string.travel_saved, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    if (showListSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.setShowListSheet(false) },
            sheetState = sheetState,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.travel_list_sheet_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (mapIncidents.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.travel_incidents_header),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(mapIncidents, key = { it.id }) { inc ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        inc.note.trim().ifBlank { stringResource(R.string.travel_incident_dialog_title) },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        String.format(
                                            Locale.getDefault(),
                                            "%.4f, %.4f",
                                            inc.latitude,
                                            inc.longitude,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { vm.removeMapIncident(inc.id) }) {
                                    Text(stringResource(R.string.travel_incident_delete))
                                }
                            }
                        }
                    }
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                }
                items(zones, key = { it.id }) { z ->
                    Card(colors = CardDefaults.cardColors()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(z.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    when (z.kind) {
                                        TravelZoneKind.CIRCLE -> stringResource(
                                            R.string.travel_zone_circle_sub,
                                            z.radiusMeters.toInt(),
                                        )
                                        TravelZoneKind.POLYGON -> stringResource(R.string.travel_zone_polygon_sub)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    stringResource(R.string.travel_zone_action, actionLabel(context, z.action)),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Switch(
                                checked = z.enabled,
                                onCheckedChange = { vm.setZoneEnabled(z.id, it) },
                            )
                            TextButton(onClick = { vm.removeZone(z.id) }) {
                                Text(stringResource(R.string.travel_delete))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showMarkersEditSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.setShowMarkersEditSheet(false) },
            sheetState = markersSheetState,
        ) {
            TravelMarkersEditorSheet(
                vm = vm,
                mapIncidents = mapIncidents,
                markerProximityEnabled = markerProximityEnabled,
                markerDefaultSoundUri = markerDefaultSoundUri,
                polygonEntrySoundUri = polygonEntrySoundUri,
                onDismiss = { vm.setShowMarkersEditSheet(false) },
            )
        }
    }
}

@Composable
private fun TravelMapKitSettingsDialog(
    initialUserKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember { mutableStateOf(initialUserKey) }
    LaunchedEffect(initialUserKey) {
        draft = initialUserKey
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.travel_mapkit_settings_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.travel_mapkit_key_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(stringResource(R.string.travel_mapkit_key_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                TextButton(
                    onClick = { draft = "" },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(stringResource(R.string.travel_mapkit_clear_saved))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text(stringResource(R.string.travel_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.travel_cancel))
            }
        },
    )
}

@Composable
private fun actionLabel(ctx: android.content.Context, a: TravelTriggerAction): String =
    when (a) {
        TravelTriggerAction.NOTIFICATION_ONLY -> ctx.getString(R.string.travel_action_notif)
        TravelTriggerAction.BEEP -> ctx.getString(R.string.travel_action_beep)
        TravelTriggerAction.PLAY_SOUND -> ctx.getString(R.string.travel_action_sound)
        TravelTriggerAction.PLAY_VIDEO -> ctx.getString(R.string.travel_action_video)
    }

@Composable
private fun EditZonePropertiesDialog(
    zone: TravelZone,
    onDismiss: () -> Unit,
    onSave: (TravelZone) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(zone.id) { mutableStateOf(zone.name) }
    var action by remember(zone.id) { mutableStateOf(zone.action) }
    var mediaUri by remember(zone.id) { mutableStateOf(zone.mediaUri) }
    var showRecordSound by remember(zone.id) { mutableStateOf(false) }

    val pickSound = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                TravelUserSoundStorage.copyUriToFilesDir(context, uri)
            } ?: return@launch
            mediaUri = TravelUserSoundStorage.toFileUriString(path)
        }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        mediaUri = uri?.toString()
    }

    if (showRecordSound) {
        TravelRecordSoundDialog(
            onDismiss = { showRecordSound = false },
            onSoundSaved = { stored ->
                mediaUri = stored
                showRecordSound = false
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.travel_edit_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.travel_zone_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.travel_action_label), style = MaterialTheme.typography.labelMedium)
                TravelTriggerAction.values().forEach { a ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = action == a,
                            onClick = { action = a },
                        )
                        Text(actionLabel(context, a), Modifier.padding(start = 4.dp))
                    }
                }
                if (action == TravelTriggerAction.PLAY_SOUND) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { pickSound.launch("audio/*") }) {
                            Text(stringResource(R.string.travel_pick_sound))
                        }
                        TextButton(onClick = { showRecordSound = true }) {
                            Text(stringResource(R.string.travel_marker_sound_record))
                        }
                    }
                }
                if (action == TravelTriggerAction.PLAY_VIDEO) {
                    TextButton(onClick = { pickVideo.launch("video/*") }) {
                        Text(stringResource(R.string.travel_pick_video))
                    }
                }
                mediaUri?.let { u ->
                    Text(
                        stringResource(R.string.travel_media_picked, u.take(48)),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val n = name.trim().ifBlank { context.getString(R.string.travel_unnamed_place) }
                    onSave(
                        zone.copy(
                            name = n,
                            action = action,
                            mediaUri = mediaUri.takeIf {
                                action == TravelTriggerAction.PLAY_SOUND ||
                                    action == TravelTriggerAction.PLAY_VIDEO
                            },
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.travel_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.travel_cancel))
            }
        },
    )
}

@Composable
private fun SaveZoneDialog(
    pending: TravelPendingZoneSave,
    onDismiss: () -> Unit,
    onSave: (TravelZone) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var radius by remember(pending) {
        mutableFloatStateOf(
            when (pending) {
                is TravelPendingZoneSave.CircleZone -> pending.radius
                else -> 150f
            },
        )
    }
    var action by remember { mutableStateOf(TravelTriggerAction.NOTIFICATION_ONLY) }
    var mediaUri by remember { mutableStateOf<String?>(null) }
    var showRecordSound by remember { mutableStateOf(false) }

    val pickSound = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                TravelUserSoundStorage.copyUriToFilesDir(context, uri)
            } ?: return@launch
            mediaUri = TravelUserSoundStorage.toFileUriString(path)
        }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        mediaUri = uri?.toString()
    }

    if (showRecordSound) {
        TravelRecordSoundDialog(
            onDismiss = { showRecordSound = false },
            onSoundSaved = { stored ->
                mediaUri = stored
                showRecordSound = false
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.travel_save_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.travel_zone_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (pending is TravelPendingZoneSave.CircleZone) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.travel_radius_m, radius.toInt()))
                    Slider(
                        value = radius,
                        onValueChange = { radius = it },
                        valueRange = 100f..800f,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.travel_action_label), style = MaterialTheme.typography.labelMedium)
                TravelTriggerAction.values().forEach { a ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = action == a,
                            onClick = { action = a },
                        )
                        Text(actionLabel(context, a), Modifier.padding(start = 4.dp))
                    }
                }
                if (action == TravelTriggerAction.PLAY_SOUND) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { pickSound.launch("audio/*") }) {
                            Text(stringResource(R.string.travel_pick_sound))
                        }
                        TextButton(onClick = { showRecordSound = true }) {
                            Text(stringResource(R.string.travel_marker_sound_record))
                        }
                    }
                }
                if (action == TravelTriggerAction.PLAY_VIDEO) {
                    TextButton(onClick = { pickVideo.launch("video/*") }) {
                        Text(stringResource(R.string.travel_pick_video))
                    }
                }
                mediaUri?.let { u ->
                    Text(
                        stringResource(R.string.travel_media_picked, u.take(48)),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val n = name.trim().ifBlank { context.getString(R.string.travel_unnamed_place) }
                    when (pending) {
                        is TravelPendingZoneSave.CircleZone -> {
                            onSave(
                                TravelZone(
                                    name = n,
                                    kind = TravelZoneKind.CIRCLE,
                                    centerLat = pending.center.latitude,
                                    centerLng = pending.center.longitude,
                                    radiusMeters = max(radius, 100f),
                                    action = action,
                                    mediaUri = mediaUri.takeIf {
                                        action == TravelTriggerAction.PLAY_SOUND ||
                                            action == TravelTriggerAction.PLAY_VIDEO
                                    },
                                ),
                            )
                        }
                        is TravelPendingZoneSave.PolygonZone -> {
                            val pts = pending.points
                            val cLat = pts.map { it.latitude }.average()
                            val cLng = pts.map { it.longitude }.average()
                            onSave(
                                TravelZone(
                                    name = n,
                                    kind = TravelZoneKind.POLYGON,
                                    centerLat = cLat,
                                    centerLng = cLng,
                                    radiusMeters = 0f,
                                    polygonPoints = pts,
                                    action = action,
                                    mediaUri = mediaUri.takeIf {
                                        action == TravelTriggerAction.PLAY_SOUND ||
                                            action == TravelTriggerAction.PLAY_VIDEO
                                    },
                                ),
                            )
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.travel_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.travel_cancel))
            }
        },
    )
}
