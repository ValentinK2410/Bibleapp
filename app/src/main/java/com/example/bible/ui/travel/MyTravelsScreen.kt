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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import com.example.bible.data.ContactsRepository
import com.example.bible.data.UserContact
import com.example.bible.data.travel.FriendPeerLocation
import com.example.bible.data.travel.TravelGeoPoint
import com.example.bible.data.travel.TravelMapIncident
import com.example.bible.data.travel.TravelMarkerSoundTrigger
import com.example.bible.data.travel.TravelTriggerAction
import com.example.bible.data.travel.TravelUserSoundStorage
import com.example.bible.data.travel.TravelZone
import com.example.bible.data.travel.TravelZoneKind
import com.example.bible.data.travel.TRAVEL_ZONE_CIRCLE_RADIUS_MAX_M
import com.example.bible.data.travel.TRAVEL_ZONE_CIRCLE_RADIUS_MIN_M
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
import kotlin.math.roundToInt
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.bible.data.travel.TravelPhotoStorage
import com.example.bible.data.travel.TravelRoutePhotoPoint
import com.example.bible.data.travel.TravelRoutePhotoSession
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Превью кадра серии по маршруту из внутреннего файла: Coil грузит через File; при отсутствии файла или ошибке — иконка. */
@Composable
private fun RouteBurstStoredPhotoThumbnail(
    photoUriString: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val ctx = LocalContext.current
    val file = remember(photoUriString) {
        runCatching { Uri.parse(photoUriString).path?.let(::File) }.getOrNull()
    }
    val request = remember(photoUriString, file) {
        val f = file?.takeIf { it.exists() && it.length() > 0L } ?: return@remember null
        ImageRequest.Builder(ctx)
            .data(f)
            .crossfade(false)
            .diskCachePolicy(CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    val brokenPainter = rememberVectorPainter(Icons.Outlined.BrokenImage)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            request == null -> Icon(
                Icons.Outlined.BrokenImage,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(12.dp),
            )
            else -> AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = brokenPainter,
                error = brokenPainter,
            )
        }
    }
}

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
    val routePlaybackPickStartActive by vm.routePlaybackPickStartActive.collectAsStateWithLifecycle()
    val routePlaybackReverse by vm.routePlaybackReverse.collectAsStateWithLifecycle()
    val routePlaybackStartDistanceM by vm.routePlaybackStartDistanceM.collectAsStateWithLifecycle()
    val friendPeerLocation by vm.friendPeerLocation.collectAsStateWithLifecycle()
    val friendPeerPollUrl by vm.friendPeerPollUrl.collectAsStateWithLifecycle()
    val friendPeerPollIntervalSec by vm.friendPeerPollIntervalSec.collectAsStateWithLifecycle()
    val friendPeerPollEnabled by vm.friendPeerPollEnabled.collectAsStateWithLifecycle()
    val lastUserGeo by vm.lastUserGeo.collectAsStateWithLifecycle()
    val lastUserHeadingDeg by vm.lastUserHeadingDeg.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val contactsRepo = remember { ContactsRepository(context) }

    var burstSessionIdLocal by remember { mutableStateOf<String?>(null) }
    var deleteRouteSessionDialog by remember { mutableStateOf(false) }
    var deleteAllRouteSessionsConfirm by remember { mutableStateOf(false) }
    var showRoutePhotosManageSheet by remember { mutableStateOf(false) }
    var showRoutePlaybackControlsSheet by remember { mutableStateOf(false) }
    var showFriendPeerSheet by remember { mutableStateOf(false) }
    var showShareCoordsSmsSheet by remember { mutableStateOf(false) }
    var friendPeerContactsPickOpen by remember { mutableStateOf(false) }
    var geoContactsPickList by remember { mutableStateOf<List<UserContact>>(emptyList()) }
    var friendPeerManualDialog by remember { mutableStateOf(false) }
    var friendPeerUrlDraft by remember { mutableStateOf("") }
    var friendPeerManualLine by remember { mutableStateOf("") }
    var friendPeerManualLabel by remember { mutableStateOf("") }
    var friendPeerTapSheetSnapshot by remember { mutableStateOf<FriendPeerLocation?>(null) }
    var incidentTapSheetIncident by remember { mutableStateOf<TravelMapIncident?>(null) }
    var incidentDeleteConfirmFor by remember { mutableStateOf<TravelMapIncident?>(null) }
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
    var sendSmsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val smsSharePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        sendSmsGranted = ok
        if (!ok) {
            Toast.makeText(context, R.string.travel_share_sms_permission_denied, Toast.LENGTH_LONG).show()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    /** Масштаб карточки превью виртуального проезда (сохраняется при повороте экрана). */
    var routePlaybackPreviewScale by rememberSaveable { mutableFloatStateOf(1f) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val markersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val playbackControlsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(routePlaybackActive) {
        if (!routePlaybackActive) showRoutePlaybackControlsSheet = false
    }

    LaunchedEffect(showFriendPeerSheet) {
        if (showFriendPeerSheet) friendPeerUrlDraft = friendPeerPollUrl
    }

    LaunchedEffect(friendPeerContactsPickOpen) {
        if (friendPeerContactsPickOpen) {
            geoContactsPickList = contactsRepo.load()
                .filter { it.hasCoordinates() }
                .sortedBy { it.fullName.lowercase(Locale.getDefault()) }
        }
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

    var incidentDraftPoint by remember { mutableStateOf<TravelGeoPoint?>(null) }
    var shareMapPointPickActive by remember { mutableStateOf(false) }

    var toolbarInteractionNonce by remember { mutableLongStateOf(0L) }
    var showFloatingToolbar by remember { mutableStateOf(false) }
    val stickyFloatingToolbar =
        territoryEditEnabled ||
            editMode != TravelMapEditMode.VIEW ||
            incidentPlaceMode ||
            routePickDestination ||
            shareMapPointPickActive

    LaunchedEffect(territoryPanelBelowMap) {
        if (!territoryPanelBelowMap) {
            showFloatingToolbar = false
            toolbarInteractionNonce = 0L
            shareMapPointPickActive = false
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

    LaunchedEffect(
        incidentPlaceMode,
        routePickDestination,
        routeBurstActive,
        routePlaybackPickStartActive,
        pendingCircleRecenterZoneId,
        editMode,
        polygonRedraftZoneId,
    ) {
        if (!shareMapPointPickActive) return@LaunchedEffect
        val conflict =
            incidentPlaceMode ||
                routePickDestination ||
                routeBurstActive ||
                routePlaybackPickStartActive ||
                pendingCircleRecenterZoneId != null ||
                editMode != TravelMapEditMode.VIEW ||
                polygonRedraftZoneId != null
        if (conflict) shareMapPointPickActive = false
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
            if (ok && geo != null && file.exists() && file.length() > 0L) {
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

    val shareCoordinatesAtPoint: (TravelGeoPoint) -> Unit = { pt ->
        bumpFloatingToolbar()
        val text =
            "${String.format(Locale.US, "%.6f", pt.latitude)}, ${String.format(Locale.US, "%.6f", pt.longitude)}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.travel_share_coords_subject))
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.travel_share_coords_subject)),
        )
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
                                text = { Text(stringResource(R.string.travel_share_sms_menu)) },
                                onClick = {
                                    vm.setTravelMenuExpanded(false)
                                    showShareCoordsSmsSheet = true
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
                        onMapTap = mapTap@{ pt ->
                            bumpFloatingToolbar()
                            if (routePlaybackPickStartActive) {
                                when {
                                    sortedPhotoSessions.isEmpty() -> {
                                        vm.setRoutePlaybackPickStartActive(false)
                                        Toast.makeText(
                                            context,
                                            R.string.travel_route_photo_no_sessions,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    incidentPlaceMode || routePickDestination || routeBurstActive -> {
                                        Toast.makeText(
                                            context,
                                            R.string.travel_route_playback_pick_conflict,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    else -> {
                                        val d = vm.applyRoutePlaybackStartFromMap(pt.latitude, pt.longitude)
                                        vm.setRoutePlaybackPickStartActive(false)
                                        if (d != null) {
                                            Toast.makeText(
                                                context,
                                                context.getString(
                                                    R.string.travel_route_playback_start_applied_fmt,
                                                    d,
                                                ),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                }
                                return@mapTap
                            }
                            if (shareMapPointPickActive) {
                                shareMapPointPickActive = false
                                shareCoordinatesAtPoint(pt)
                                return@mapTap
                            }
                            friendPeerLocation?.let { loc ->
                                val fd = travelDistanceMeters(
                                    pt,
                                    TravelGeoPoint(loc.latitude, loc.longitude),
                                )
                                if (fd <= TravelMarkerSoundTrigger.TAP_MAX_DISTANCE_METERS) {
                                    friendPeerTapSheetSnapshot = loc
                                    return@mapTap
                                }
                            }
                            val incidentHit = mapIncidents.mapNotNull { inc ->
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
                            if (incidentHit != null) {
                                incidentTapSheetIncident = incidentHit
                                return@mapTap
                            }
                            val recId = pendingCircleRecenterZoneId
                            when {
                                recId != null -> vm.applyCircleRecenter(recId, pt)
                                territoryEditEnabled &&
                                    editMode == TravelMapEditMode.POLYGON_DRAW &&
                                    polygonRedraftZoneId != null -> vm.addPolygonDraftPoint(pt)
                                territoryEditEnabled ||
                                    editMode == TravelMapEditMode.VIEW -> {
                                    val zoneList = if (territoryEditEnabled) {
                                        zones
                                    } else {
                                        zones.filter { it.enabled }
                                    }
                                    val hit = travelZonesAtPoint(zoneList, pt).firstOrNull()
                                    vm.selectZoneForEdit(hit?.id)
                                }
                                else -> when (editMode) {
                                    TravelMapEditMode.CIRCLE_TAP -> {
                                        vm.setPendingSave(TravelPendingZoneSave.CircleZone(pt, 150f))
                                    }
                                    TravelMapEditMode.POLYGON_DRAW -> {
                                        vm.addPolygonDraftPoint(pt)
                                    }
                                    TravelMapEditMode.VIEW -> Unit
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
                        routeBurstDraftPoints = burstDraftPoints.toList(),
                        routePlaybackSim = routePlaybackSim,
                        friendPeerLocation = friendPeerLocation,
                        hideNavigatorHud = routePlaybackActive,
                    )
                    if (routeBurstActive && camGranted) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 10.dp, top = 52.dp)
                                .widthIn(max = 172.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(118.dp),
                                shape = RoundedCornerShape(12.dp),
                                tonalElevation = 3.dp,
                                shadowElevation = 3.dp,
                            ) {
                                TravelBurstCameraPreview(
                                    enabled = true,
                                    modifier = Modifier.fillMaxSize(),
                                    onImageCaptureReady = { burstImageCapture = it },
                                )
                            }
                            burstDraftPoints.lastOrNull()?.let { last ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                                    ),
                                ) {
                                    Column(Modifier.padding(6.dp)) {
                                        RouteBurstStoredPhotoThumbnail(
                                            photoUriString = last.photoUri,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 72.dp, max = 114.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentDescription = stringResource(R.string.travel_route_burst_last_frame_cd),
                                        )
                                        Text(
                                            stringResource(
                                                R.string.travel_route_burst_frames_fmt,
                                                burstDraftPoints.size,
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                    if (routePlaybackActive) {
                        SmallFloatingActionButton(
                            onClick = {
                                bumpFloatingToolbar()
                                showRoutePlaybackControlsSheet = true
                            },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 12.dp, bottom = 88.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = stringResource(R.string.travel_route_playback_settings_fab_cd),
                            )
                        }
                    }
                    routePlaybackSim?.let { sim ->
                        sim.currentPhotoUri?.let { uriStr ->
                            val pbScale = routePlaybackPreviewScale.coerceIn(0.5f, 2.5f)
                            val thumbW = (128f * pbScale).dp.coerceIn(88.dp, 188.dp)
                            val thumbH = (82f * pbScale).dp.coerceIn(56.dp, 120.dp)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 12.dp, bottom = 88.dp)
                                    .width(thumbW)
                                    .height(thumbH)
                                    .clip(RoundedCornerShape(12.dp)),
                            ) {
                                RoutePlaybackSmoothPhoto(
                                    uriStr = uriStr,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                Surface(
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
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
                        shareMapPointPickActive = shareMapPointPickActive,
                        onPolygonClick = {
                            bumpFloatingToolbar()
                            shareMapPointPickActive = false
                            vm.setIncidentPlaceMode(false)
                            vm.setRoutePickMode(false)
                            incidentDraftPoint = null
                            vm.togglePolygonDrawMode()
                        },
                        onMarkersClick = {
                            bumpFloatingToolbar()
                            shareMapPointPickActive = false
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
                        onCircleClick = {
                            bumpFloatingToolbar()
                            shareMapPointPickActive = false
                            vm.setIncidentPlaceMode(false)
                            vm.setRoutePickMode(false)
                            incidentDraftPoint = null
                            vm.toggleCircleDrawMode()
                        },
                        onRouteClick = {
                            bumpFloatingToolbar()
                            shareMapPointPickActive = false
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
                        onShareMapPointClick = {
                            bumpFloatingToolbar()
                            if (shareMapPointPickActive) {
                                shareMapPointPickActive = false
                                Toast.makeText(
                                    context,
                                    R.string.travel_share_map_point_pick_cancelled,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                val blocked =
                                    incidentPlaceMode ||
                                        routePickDestination ||
                                        routeBurstActive ||
                                        routePlaybackPickStartActive ||
                                        pendingCircleRecenterZoneId != null ||
                                        editMode != TravelMapEditMode.VIEW ||
                                        polygonRedraftZoneId != null
                                if (blocked) {
                                    Toast.makeText(
                                        context,
                                        R.string.travel_share_map_point_conflict,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    shareMapPointPickActive = true
                                    Toast.makeText(
                                        context,
                                        R.string.travel_share_map_point_pick_hint,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                        onShareClick = {
                            shareMapPointPickActive = false
                            shareMyCoordinates()
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
                    if (routePlaybackPickStartActive) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = if (routePickDestination) 72.dp else 8.dp)
                                .widthIn(max = 340.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.travel_route_playback_pick_hint),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                TextButton(onClick = { vm.setRoutePlaybackPickStartActive(false) }) {
                                    Text(stringResource(R.string.travel_cancel))
                                }
                            }
                        }
                    }
                    if (shareMapPointPickActive) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                                .widthIn(max = 340.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.travel_share_map_point_banner),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                TextButton(onClick = { shareMapPointPickActive = false }) {
                                    Text(stringResource(R.string.travel_cancel))
                                }
                            }
                        }
                    }
                    if (incidentPlaceMode) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(
                                    top = when {
                                        routePickDestination && routePlaybackPickStartActive -> 136.dp
                                        routePickDestination || routePlaybackPickStartActive -> 72.dp
                                        else -> 8.dp
                                    },
                                )
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
                                    TravelCircleRadiusSliderWithManualInput(
                                        rememberKey = z.id,
                                        radiusFromModel = z.radiusMeters,
                                        onRadiusCommitted = { r ->
                                            vm.updateZoneRadius(z.id, r)
                                        },
                                    )
                                    TextButton(
                                        onClick = { vm.setPendingCircleRecenter(z.id) },
                                        enabled = pendingCircleRecenterZoneId == null,
                                    ) {
                                        Text(stringResource(R.string.travel_edit_recenter))
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        FilledTonalButton(
                                            onClick = { vm.openZoneProperties(z.id) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(stringResource(R.string.travel_zone_action_edit))
                                        }
                                        TextButton(
                                            onClick = { vm.removeZone(z.id) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error,
                                            ),
                                        ) {
                                            Text(stringResource(R.string.travel_delete))
                                        }
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        FilledTonalButton(
                                            onClick = { vm.openZoneProperties(z.id) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(stringResource(R.string.travel_zone_action_edit))
                                        }
                                        TextButton(
                                            onClick = { vm.removeZone(z.id) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error,
                                            ),
                                        ) {
                                            Text(stringResource(R.string.travel_delete))
                                        }
                                    }
                                }
                                TextButton(
                                    onClick = { vm.selectZoneForEdit(null) },
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text(stringResource(R.string.travel_edit_close_panel))
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

    if (showRoutePlaybackControlsSheet && routePlaybackActive) {
        ModalBottomSheet(
            onDismissRequest = { showRoutePlaybackControlsSheet = false },
            sheetState = playbackControlsSheetState,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.travel_route_playback_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                routePlaybackSim?.currentPhotoUri?.let { uriStr ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    ) {
                        RoutePlaybackSmoothPhoto(
                            uriStr = uriStr,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Text(
                        stringResource(R.string.travel_route_preview_scale_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = routePlaybackPreviewScale,
                        onValueChange = { routePlaybackPreviewScale = it.coerceIn(0.5f, 2.5f) },
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
                    HorizontalDivider()
                }
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
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.travel_route_playback_reverse_label),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = routePlaybackReverse,
                        onCheckedChange = vm::setRoutePlaybackReverse,
                    )
                }
                Text(
                    stringResource(
                        R.string.travel_route_playback_start_fmt,
                        routePlaybackStartDistanceM,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = {
                            showRoutePlaybackControlsSheet = false
                            vm.setRoutePlaybackPickStartActive(true)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.travel_route_playback_pick_toggle))
                    }
                    TextButton(
                        onClick = {
                            showRoutePlaybackControlsSheet = false
                            vm.resetRoutePlaybackStartOnPath()
                        },
                    ) {
                        Text(stringResource(R.string.travel_route_playback_start_reset))
                    }
                }
            }
        }
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
                    onClick = { friendPeerContactsPickOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.travel_friend_peer_pick_from_contacts))
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

    if (friendPeerContactsPickOpen) {
        AlertDialog(
            onDismissRequest = { friendPeerContactsPickOpen = false },
            title = { Text(stringResource(R.string.travel_friend_peer_pick_contact_title)) },
            text = {
                if (geoContactsPickList.isEmpty()) {
                    Text(stringResource(R.string.travel_friend_peer_pick_contact_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                    ) {
                        items(geoContactsPickList, key = { it.id }) { c ->
                            val lat = c.latitude!!
                            val lon = c.longitude!!
                            TextButton(
                                onClick = {
                                    vm.setFriendPeerManual(lat, lon, c.fullName)
                                    friendPeerContactsPickOpen = false
                                    Toast.makeText(
                                        context,
                                        R.string.travel_friend_peer_pick_applied,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(c.fullName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        String.format(Locale.US, "%.6f, %.6f", lat, lon),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { friendPeerContactsPickOpen = false }) {
                    Text(stringResource(R.string.back))
                }
            },
        )
    }

    if (showShareCoordsSmsSheet) {
        ModalBottomSheet(onDismissRequest = { showShareCoordsSmsSheet = false }) {
            ShareTravelCoordinatesSmsSheetContent(
                scope = scope,
                lastLatitude = lastUserGeo?.latitude,
                lastLongitude = lastUserGeo?.longitude,
                hasFineLocation = hasFineLocation,
                sendSmsGranted = sendSmsGranted,
                onRequestSendSmsPermission = {
                    smsSharePermLauncher.launch(Manifest.permission.SEND_SMS)
                },
                onDismiss = { showShareCoordsSmsSheet = false },
            )
        }
    }

    FriendPeerMarkerTapSheet(
        snapshot = friendPeerTapSheetSnapshot,
        liveLocation = friendPeerLocation,
        onDismiss = { friendPeerTapSheetSnapshot = null },
        onRemoveFromMap = {
            vm.removeFriendPeerFromMap()
            Toast.makeText(context, R.string.travel_friend_peer_removed_from_map, Toast.LENGTH_SHORT).show()
        },
    )

    IncidentMarkerTapSheet(
        incident = incidentTapSheetIncident,
        mapIncidents = mapIncidents,
        onDismiss = { incidentTapSheetIncident = null },
        onDelete = { incidentDeleteConfirmFor = it },
        onOpenFullEditor = {
            vm.setShowMarkersEditSheet(true)
            incidentTapSheetIncident = null
        },
    )

    incidentDeleteConfirmFor?.let { inc ->
        AlertDialog(
            onDismissRequest = { incidentDeleteConfirmFor = null },
            title = { Text(stringResource(R.string.travel_incident_sheet_delete_confirm_title)) },
            text = { Text(stringResource(R.string.travel_incident_sheet_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeMapIncident(inc.id)
                        incidentDeleteConfirmFor = null
                        incidentTapSheetIncident = null
                    },
                ) {
                    Text(stringResource(R.string.travel_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { incidentDeleteConfirmFor = null }) {
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
                itemsIndexed(
                    sessionForManage.points,
                    key = { _, pt -> "${pt.photoUri}_${pt.capturedAtMs}" },
                ) { _, pt ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RouteBurstStoredPhotoThumbnail(
                            photoUriString = pt.photoUri,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentDescription = null,
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
private fun TravelCircleRadiusSliderWithManualInput(
    rememberKey: Any?,
    radiusFromModel: Float,
    onRadiusCommitted: (Float) -> Unit,
) {
    val minR = TRAVEL_ZONE_CIRCLE_RADIUS_MIN_M
    val maxR = TRAVEL_ZONE_CIRCLE_RADIUS_MAX_M
    var radiusDraft by remember(rememberKey) {
        mutableFloatStateOf(radiusFromModel.coerceIn(minR, maxR))
    }
    var manualText by remember(rememberKey) {
        mutableStateOf(radiusDraft.toInt().toString())
    }
    var manualFieldFocused by remember(rememberKey) { mutableStateOf(false) }

    LaunchedEffect(rememberKey, radiusFromModel) {
        radiusDraft = radiusFromModel.coerceIn(minR, maxR)
        if (!manualFieldFocused) {
            manualText = radiusDraft.toInt().toString()
        }
    }

    val focusManager = LocalFocusManager.current

    fun commitManualInput() {
        val parsed = manualText.toIntOrNull()?.toFloat()?.coerceIn(minR, maxR)
        val v = parsed ?: radiusDraft
        radiusDraft = v
        manualText = v.toInt().toString()
        onRadiusCommitted(v)
    }

    Text(
        stringResource(R.string.travel_radius_m, radiusDraft.toInt()),
        style = MaterialTheme.typography.labelMedium,
    )
    Slider(
        value = radiusDraft,
        onValueChange = {
            manualFieldFocused = false
            radiusDraft = it
            manualText = it.toInt().toString()
        },
        onValueChangeFinished = { onRadiusCommitted(radiusDraft) },
        valueRange = minR..maxR,
    )
    OutlinedTextField(
        value = manualText,
        onValueChange = { chunk ->
            manualFieldFocused = true
            manualText = chunk.filter { it.isDigit() }.take(5)
        },
        label = { Text(stringResource(R.string.travel_radius_manual_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        keyboardActions = KeyboardActions(
            onDone = {
                commitManualInput()
                manualFieldFocused = false
                focusManager.clearFocus()
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (state.isFocused) {
                    manualFieldFocused = true
                } else if (manualFieldFocused) {
                    commitManualInput()
                    manualFieldFocused = false
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
                is TravelPendingZoneSave.CircleZone ->
                    pending.radius.coerceIn(
                        TRAVEL_ZONE_CIRCLE_RADIUS_MIN_M,
                        TRAVEL_ZONE_CIRCLE_RADIUS_MAX_M,
                    )
                else ->
                    150f.coerceIn(
                        TRAVEL_ZONE_CIRCLE_RADIUS_MIN_M,
                        TRAVEL_ZONE_CIRCLE_RADIUS_MAX_M,
                    )
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
                    TravelCircleRadiusSliderWithManualInput(
                        rememberKey = pending,
                        radiusFromModel = radius,
                        onRadiusCommitted = { radius = it },
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
                                    radiusMeters = radius.coerceIn(
                                        TRAVEL_ZONE_CIRCLE_RADIUS_MIN_M,
                                        TRAVEL_ZONE_CIRCLE_RADIUS_MAX_M,
                                    ),
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
