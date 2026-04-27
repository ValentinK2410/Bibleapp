package com.example.bible.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.BiblePreferences
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot
import kotlin.math.min

private data class Camera5FaceOverlayState(
    val contours: Map<Int, List<Offset>>,
    val landmarks: Map<Int, Offset>,
)

/** Контуры «неоном», чтобы отличать от палитры ML Kit, когда активна плотная сетка MediaPipe. */
private const val CAMERA5_CURSOR_VEL_GAIN = 2.35f
private const val CAMERA5_SYNTH_MOVE_EPS_PX = 2.5f

/** Ниже этой скорости (норма вектора носа, px/кадр) буст не копится и затухает. */
private const val CAMERA5_BOOST_MIN_SPEED = 0.42f
/** Косинус угла между «якорным» направлением и текущим: выше — считаем то же направление. */
private const val CAMERA5_BOOST_MIN_COS = 0.88f
/** Сколько мс держать направление, прежде чем начать разгон. */
private const val CAMERA5_BOOST_HOLD_MS = 380L
/** Прирост множителя за кадр после удержания (плавный разгон). */
private const val CAMERA5_BOOST_RAMP_PER_FRAME = 0.14f
private const val CAMERA5_BOOST_MAX = 5f
private const val CAMERA5_BOOST_DECAY_ON_TURN = 0.82f
private const val CAMERA5_BOOST_DECAY_SLOW = 0.93f

private fun camera5StrokeForContourMediaPipe(type: Int): Color = when (type) {
    FaceContour.FACE -> Color(0xFFE040FB)
    FaceContour.LEFT_EYE, FaceContour.RIGHT_EYE -> Color(0xFFFFEA00)
    FaceContour.LEFT_EYEBROW_TOP, FaceContour.LEFT_EYEBROW_BOTTOM,
    FaceContour.RIGHT_EYEBROW_TOP, FaceContour.RIGHT_EYEBROW_BOTTOM,
    -> Color(0xFF00E5FF)
    FaceContour.NOSE_BRIDGE -> Color(0xFFFF6E40)
    FaceContour.UPPER_LIP_TOP, FaceContour.UPPER_LIP_BOTTOM,
    FaceContour.LOWER_LIP_TOP, FaceContour.LOWER_LIP_BOTTOM,
    -> Color(0xFF76FF03)
    else -> Color(0xFFFF4081)
}

private val camera5ContourDrawOrder: IntArray = intArrayOf(
    FaceContour.FACE,
    FaceContour.LEFT_EYEBROW_TOP,
    FaceContour.LEFT_EYEBROW_BOTTOM,
    FaceContour.RIGHT_EYEBROW_TOP,
    FaceContour.RIGHT_EYEBROW_BOTTOM,
    FaceContour.LEFT_EYE,
    FaceContour.RIGHT_EYE,
    FaceContour.NOSE_BRIDGE,
    FaceContour.UPPER_LIP_TOP,
    FaceContour.UPPER_LIP_BOTTOM,
    FaceContour.LOWER_LIP_TOP,
    FaceContour.LOWER_LIP_BOTTOM,
)

private fun buildCamera5OverlayState(
    face: ExperimentFaceObservation,
    imageProxy: ImageProxy,
    previewView: PreviewView,
    iw: Int,
    ih: Int,
): Camera5FaceOverlayState? {
    if (previewView.width <= 0 || previewView.height <= 0) return null
    val contours = HashMap<Int, List<Offset>>()
    for ((type, pts) in face.contours) {
        if (pts.size < 2) continue
        val pairs = pts.map { it.x to it.y }
        val off = mapMlKitPointsToPreviewView(pairs, imageProxy, previewView, iw, ih)
        if (off.size >= 2) contours[type] = off
    }
    val landmarks = HashMap<Int, Offset>()
    for ((type, pt) in face.landmarks) {
        val o = mapMlKitPointsToPreviewView(listOf(pt.x to pt.y), imageProxy, previewView, iw, ih)
            .firstOrNull() ?: continue
        landmarks[type] = o
    }
    return Camera5FaceOverlayState(contours, landmarks)
}

private fun largestFace(obs: List<ExperimentFaceObservation>?): ExperimentFaceObservation? =
    obs?.maxByOrNull { it.boundingBox.width().toLong() * it.boundingBox.height() }

@Composable
private fun Camera5PreviewModeBanner(
    mimicCamera: Boolean,
    faceOverlay: Boolean,
    mediaPipeOn: Boolean,
    mediaPipeModelPresent: Boolean,
    modifier: Modifier = Modifier,
) {
    val title: String
    val subtitle: String
    val bg: Color
    val fg: Color
    when {
        !mimicCamera -> {
            title = stringResource(R.string.experiment_camera5_preview_badge_camera_hidden)
            subtitle = stringResource(R.string.experiment_camera5_preview_badge_camera_hidden_sub)
            bg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f)
            fg = MaterialTheme.colorScheme.onErrorContainer
        }
        !faceOverlay -> {
            title = stringResource(R.string.experiment_camera5_preview_badge_mesh_off)
            subtitle = stringResource(R.string.experiment_camera5_preview_badge_mesh_off_sub)
            bg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f)
            fg = MaterialTheme.colorScheme.onSurfaceVariant
        }
        mediaPipeOn && !mediaPipeModelPresent -> {
            title = stringResource(R.string.experiment_camera5_preview_badge_mediapipe_no_model_title)
            subtitle = stringResource(R.string.experiment_camera5_preview_badge_mediapipe_no_model_sub)
            bg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f)
            fg = MaterialTheme.colorScheme.onErrorContainer
        }
        !mediaPipeOn -> {
            title = stringResource(R.string.experiment_camera5_preview_badge_mlkit)
            subtitle = stringResource(R.string.experiment_camera5_preview_badge_mlkit_sub)
            bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f)
            fg = MaterialTheme.colorScheme.onPrimaryContainer
        }
        else -> {
            title = stringResource(R.string.experiment_camera5_preview_badge_mediapipe)
            subtitle = stringResource(R.string.experiment_camera5_preview_badge_mediapipe_sub)
            bg = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.96f)
            fg = MaterialTheme.colorScheme.onTertiaryContainer
        }
    }
    Surface(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(10.dp),
        color = bg,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = fg,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = fg.copy(alpha = 0.92f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Camera5GeometrySourceCard(
    selected: Boolean,
    title: String,
    description: String,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val borderWidth = if (selected) 2.dp else 1.dp
    val borderColor = if (selected) scheme.primary else scheme.outline.copy(alpha = 0.35f)
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onSelect() },
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
            )
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) scheme.primary else scheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Эксперимент «Камера 5»: превью камеры (верх) и переключатели как в настройках (низ);
 * распознавание ML Kit + опционально MediaPipe Face Landmarker, отрисовка контуров частей лица.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentCamera5MediaPipeScreen(
    preferences: BiblePreferences,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mediaPipeAssetCheckTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mediaPipeAssetCheckTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val mimicCamera by preferences.mimicCameraPreviewEnabled.collectAsStateWithLifecycle(initialValue = true)
    val faceOverlay by preferences.mimicFaceOverlayEnabled.collectAsStateWithLifecycle(initialValue = true)
    val mediaPipeOn by preferences.mimicMediaPipeFaceGeometryEnabled.collectAsStateWithLifecycle(initialValue = false)
    val velocityOn by preferences.mimicVelocityVectorVisible.collectAsStateWithLifecycle(initialValue = false)
    val mediaPipeModelPresent = remember(context, mediaPipeAssetCheckTick) {
        MediaPipeFaceLandmarkerHelper.isModelAssetPresent(context.applicationContext)
    }

    var overlayState by remember { mutableStateOf<Camera5FaceOverlayState?>(null) }
    var mediaPipeDensePointsInPreview by remember { mutableStateOf<List<Offset>?>(null) }
    var liveMpLandmarkCount by remember { mutableIntStateOf(0) }
    var smoothVel by remember { mutableStateOf(Offset.Zero) }
    var lastNose by remember { mutableStateOf<Offset?>(null) }
    var camera5CursorPos by remember { mutableStateOf(Offset.Zero) }
    var camera5CursorTracking by remember { mutableStateOf(false) }
    var camera5MouthOpen by remember { mutableStateOf(false) }
    var camera5PointerDownTime by remember { mutableLongStateOf(0L) }
    var prevMouthOpenCamera5 by remember { mutableStateOf(false) }
    var lastSynthMovePreview by remember { mutableStateOf<Offset?>(null) }
    var camera5SpeedBoost by remember { mutableFloatStateOf(1f) }
    var camera5VelAlignAnchor by remember { mutableStateOf<Offset?>(null) }
    var camera5VelAlignSinceMs by remember { mutableLongStateOf(0L) }

    val faceOverlayState = rememberUpdatedState(faceOverlay)
    val velocityState = rememberUpdatedState(velocityOn)
    val colorScheme = MaterialTheme.colorScheme
    fun strokeForContour(type: Int): Color = when (type) {
        FaceContour.FACE -> colorScheme.primary.copy(alpha = 0.85f)
        FaceContour.LEFT_EYE, FaceContour.RIGHT_EYE -> Color(0xFFE91E63)
        FaceContour.LEFT_EYEBROW_TOP, FaceContour.LEFT_EYEBROW_BOTTOM,
        FaceContour.RIGHT_EYEBROW_TOP, FaceContour.RIGHT_EYEBROW_BOTTOM,
        -> Color(0xFF9C27B0)
        FaceContour.NOSE_BRIDGE -> Color(0xFF00BCD4)
        FaceContour.UPPER_LIP_TOP, FaceContour.UPPER_LIP_BOTTOM,
        FaceContour.LOWER_LIP_TOP, FaceContour.LOWER_LIP_BOTTOM,
        -> Color(0xFFFF9800)
        else -> colorScheme.tertiary
    }

    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            isClickable = false
            isFocusable = false
        }
    }

    DisposableEffect(lifecycleOwner, previewView, mediaPipeOn) {
        val disposed = AtomicBoolean(false)
        val faceDetector: FaceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .enableTracking()
                .build(),
        )
        val mediaPipe = if (mediaPipeOn) {
            MediaPipeFaceLandmarkerHelper.createOrNull(context.applicationContext)
        } else {
            null
        }
        val executor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val listener = Runnable {
            if (disposed.get()) return@Runnable
            val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@Runnable
            previewView.post {
                if (disposed.get()) return@post
                val attempts = intArrayOf(0)
                fun tryBind() {
                    if (disposed.get()) return
                    val vw = previewView.width
                    val vh = previewView.height
                    if (vw <= 0 || vh <= 0) {
                        if (attempts[0]++ < 60) previewView.post { tryBind() }
                        return
                    }
                    val targetRotation = displayRotationForCamera(context, previewView)
                    val preview = Preview.Builder()
                        .setTargetRotation(targetRotation)
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setTargetRotation(targetRotation)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { imageProxy: ImageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val image = InputImage.fromMediaImage(mediaImage, rotation)
                        val iw = image.width
                        val ih = image.height
                        val task = faceDetector.process(image)
                        task.addOnCompleteListener {
                            val faces = if (task.isSuccessful) task.result else null
                            val mlKitObs = faces?.map { it.toExperimentFaceObservation() }
                            val mergeResult = mergeMediaPipeGeometryIfNeeded(
                                imageProxy = imageProxy,
                                iw = iw,
                                ih = ih,
                                mlKitObservations = mlKitObs,
                                mediaPipe = mediaPipe,
                                enabled = mediaPipeOn,
                            )
                            val observations = mergeResult.observations
                            val primary = largestFace(observations)
                            val norms = mergeResult.mediaPipeLandmarksNormalized
                            mainHandler.post {
                                if (disposed.get()) {
                                    imageProxy.close()
                                    return@post
                                }
                                if (primary == null) {
                                    overlayState = null
                                    mediaPipeDensePointsInPreview = null
                                    liveMpLandmarkCount = 0
                                    lastNose = null
                                    smoothVel = Offset.Zero
                                    camera5CursorTracking = false
                                    camera5MouthOpen = false
                                    if (camera5PointerDownTime != 0L) {
                                        val (ux, uy) = previewLocalToScreen(previewView, camera5CursorPos)
                                        dispatchSyntheticDragUpFromScreen(
                                            context.applicationContext,
                                            ux,
                                            uy,
                                            camera5PointerDownTime,
                                        )
                                        camera5PointerDownTime = 0L
                                    }
                                    prevMouthOpenCamera5 = false
                                    lastSynthMovePreview = null
                                    camera5SpeedBoost = 1f
                                    camera5VelAlignAnchor = null
                                    camera5VelAlignSinceMs = 0L
                                } else {
                                    liveMpLandmarkCount = norms?.size ?: 0
                                    val faceInView = buildCamera5OverlayState(
                                        primary,
                                        imageProxy,
                                        previewView,
                                        iw,
                                        ih,
                                    )
                                    if (faceOverlayState.value && faceInView != null) {
                                        overlayState = faceInView
                                        mediaPipeDensePointsInPreview =
                                            if (!norms.isNullOrEmpty()) {
                                                val mlSpace = norms.map { (nx, ny) -> nx * iw to ny * ih }
                                                mapMlKitPointsToPreviewView(
                                                    mlSpace,
                                                    imageProxy,
                                                    previewView,
                                                    iw,
                                                    ih,
                                                )
                                            } else {
                                                null
                                            }
                                    } else {
                                        overlayState = null
                                        mediaPipeDensePointsInPreview = null
                                    }
                                    val nose = faceInView?.landmarks?.get(FaceLandmark.NOSE_BASE)
                                    val vw = previewView.width.toFloat()
                                    val vh = previewView.height.toFloat()
                                    val appCtx = context.applicationContext
                                    if (nose != null && vw > 0f && vh > 0f) {
                                        if (!camera5CursorTracking) {
                                            camera5CursorPos = Offset(vw * 0.5f, vh * 0.5f)
                                            camera5CursorTracking = true
                                            camera5SpeedBoost = 1f
                                            camera5VelAlignAnchor = null
                                            camera5VelAlignSinceMs = 0L
                                        }
                                        val prevN = lastNose
                                        if (prevN != null) {
                                            val raw = Offset(nose.x - prevN.x, nose.y - prevN.y)
                                            smoothVel = Offset(
                                                smoothVel.x * 0.82f + raw.x * 0.18f,
                                                smoothVel.y * 0.82f + raw.y * 0.18f,
                                            )
                                        }
                                        lastNose = nose
                                        val vx = smoothVel.x
                                        val vy = smoothVel.y
                                        val spd = hypot(vx.toDouble(), vy.toDouble()).toFloat()
                                        val nowMs = SystemClock.uptimeMillis()
                                        if (spd < CAMERA5_BOOST_MIN_SPEED) {
                                            camera5SpeedBoost =
                                                (camera5SpeedBoost * CAMERA5_BOOST_DECAY_SLOW).coerceAtLeast(1f)
                                            camera5VelAlignAnchor = null
                                            camera5VelAlignSinceMs = 0L
                                        } else {
                                            val ux = vx / spd
                                            val uy = vy / spd
                                            val anchor = camera5VelAlignAnchor
                                            if (anchor == null) {
                                                camera5VelAlignAnchor = Offset(ux, uy)
                                                camera5VelAlignSinceMs = nowMs
                                            } else {
                                                val dot = anchor.x * ux + anchor.y * uy
                                                if (dot < CAMERA5_BOOST_MIN_COS) {
                                                    camera5VelAlignAnchor = Offset(ux, uy)
                                                    camera5VelAlignSinceMs = nowMs
                                                    camera5SpeedBoost =
                                                        (camera5SpeedBoost * CAMERA5_BOOST_DECAY_ON_TURN)
                                                            .coerceAtLeast(1f)
                                                } else {
                                                    val held = nowMs - camera5VelAlignSinceMs
                                                    if (held >= CAMERA5_BOOST_HOLD_MS) {
                                                        camera5SpeedBoost =
                                                            (camera5SpeedBoost + CAMERA5_BOOST_RAMP_PER_FRAME)
                                                                .coerceAtMost(CAMERA5_BOOST_MAX)
                                                    }
                                                }
                                            }
                                        }
                                        val moveMul = camera5SpeedBoost
                                        camera5CursorPos = Offset(
                                            (camera5CursorPos.x + smoothVel.x * CAMERA5_CURSOR_VEL_GAIN * moveMul)
                                                .coerceIn(0f, vw - 1f),
                                            (camera5CursorPos.y + smoothVel.y * CAMERA5_CURSOR_VEL_GAIN * moveMul)
                                                .coerceIn(0f, vh - 1f),
                                        )
                                        val mouthOpen = detectOpenMouthMl(primary) != null
                                        camera5MouthOpen = mouthOpen
                                        if (mouthOpen && !prevMouthOpenCamera5) {
                                            val (sx, sy) = previewLocalToScreen(previewView, camera5CursorPos)
                                            camera5PointerDownTime = dispatchSyntheticDragDownFromScreen(appCtx, sx, sy)
                                            lastSynthMovePreview = camera5CursorPos
                                        }
                                        if (mouthOpen && camera5PointerDownTime != 0L) {
                                            val lastM = lastSynthMovePreview
                                            if (lastM == null ||
                                                hypot(
                                                    (camera5CursorPos.x - lastM.x).toDouble(),
                                                    (camera5CursorPos.y - lastM.y).toDouble(),
                                                ) >= CAMERA5_SYNTH_MOVE_EPS_PX
                                            ) {
                                                val (mx, my) = previewLocalToScreen(previewView, camera5CursorPos)
                                                dispatchSyntheticDragMoveFromScreen(
                                                    appCtx,
                                                    mx,
                                                    my,
                                                    camera5PointerDownTime,
                                                )
                                                lastSynthMovePreview = camera5CursorPos
                                            }
                                        }
                                        if (!mouthOpen && prevMouthOpenCamera5) {
                                            if (camera5PointerDownTime != 0L) {
                                                val (ux, uy) = previewLocalToScreen(previewView, camera5CursorPos)
                                                dispatchSyntheticDragUpFromScreen(
                                                    appCtx,
                                                    ux,
                                                    uy,
                                                    camera5PointerDownTime,
                                                )
                                                camera5PointerDownTime = 0L
                                            }
                                            lastSynthMovePreview = null
                                        }
                                        prevMouthOpenCamera5 = mouthOpen
                                    } else {
                                        lastNose = null
                                        smoothVel = Offset.Zero
                                        camera5SpeedBoost = 1f
                                        camera5VelAlignAnchor = null
                                        camera5VelAlignSinceMs = 0L
                                        if (camera5PointerDownTime != 0L) {
                                            val (ux, uy) = previewLocalToScreen(previewView, camera5CursorPos)
                                            dispatchSyntheticDragUpFromScreen(
                                                appCtx,
                                                ux,
                                                uy,
                                                camera5PointerDownTime,
                                            )
                                            camera5PointerDownTime = 0L
                                        }
                                        prevMouthOpenCamera5 = false
                                        camera5MouthOpen = false
                                        lastSynthMovePreview = null
                                    }
                                }
                                imageProxy.close()
                            }
                        }
                    }
                    val viewPort = ViewPort.Builder(android.util.Rational(vw, vh), targetRotation)
                        .setLayoutDirection(previewView.layoutDirection)
                        .build()
                    val useCaseGroup = UseCaseGroup.Builder()
                        .setViewPort(viewPort)
                        .addUseCase(preview)
                        .addUseCase(analysis)
                        .build()
                    runCatching {
                        cameraProvider.unbindAll()
                        if (!disposed.get()) {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                useCaseGroup,
                            )
                        }
                    }
                }
                tryBind()
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)
        onDispose {
            disposed.set(true)
            runCatching {
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
            }
            executor.shutdownNow()
            faceDetector.close()
            mediaPipe?.close()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.experiment_camera5_mediapipe_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
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
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize(),
                )
                if (!mimicCamera) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                }
                if (mimicCamera && faceOverlay) {
                    val tint = when {
                        !mediaPipeOn -> Color(0x330000FF)
                        liveMpLandmarkCount >= 80 -> Color(0x4400FF00)
                        mediaPipeModelPresent -> Color(0x55FF8800)
                        else -> Color(0x44FF0000)
                    }
                    Box(Modifier.fillMaxSize().background(tint))
                }
                if (faceOverlay && overlayState != null) {
                    val state = overlayState!!
                    val densePts = mediaPipeDensePointsInPreview
                    val mpActiveThisFrame = (densePts?.size ?: 0) >= 80
                    fun strokeForType(type: Int): Color =
                        if (mpActiveThisFrame) camera5StrokeForContourMediaPipe(type) else strokeForContour(type)
                    Canvas(Modifier.fillMaxSize()) {
                        val drawn = HashSet<Int>()
                        for (type in camera5ContourDrawOrder) {
                            val pts = state.contours[type] ?: continue
                            drawn.add(type)
                            if (pts.size < 2) continue
                            val path = Path().apply {
                                moveTo(pts[0].x, pts[0].y)
                                for (i in 1 until pts.size) {
                                    lineTo(pts[i].x, pts[i].y)
                                }
                            }
                            val strokeW = if (mpActiveThisFrame) {
                                if (type == FaceContour.FACE) 5f else 4f
                            } else {
                                if (type == FaceContour.FACE) 3.5f else 2.8f
                            }
                            drawPath(
                                path = path,
                                color = strokeForType(type),
                                style = Stroke(width = strokeW),
                            )
                            for (p in pts) {
                                drawCircle(
                                    color = strokeForType(type).copy(alpha = 0.9f),
                                    radius = if (type == FaceContour.FACE) 2f else 2.8f,
                                    center = p,
                                )
                            }
                        }
                        for ((type, pts) in state.contours) {
                            if (type in drawn || pts.size < 2) continue
                            val path = Path().apply {
                                moveTo(pts[0].x, pts[0].y)
                                for (i in 1 until pts.size) {
                                    lineTo(pts[i].x, pts[i].y)
                                }
                            }
                            drawPath(path, strokeForType(type), style = Stroke(width = if (mpActiveThisFrame) 4f else 2.5f))
                            for (p in pts) {
                                drawCircle(
                                    color = strokeForType(type),
                                    radius = 2.6f,
                                    center = p,
                                )
                            }
                        }
                        for ((_, p) in state.landmarks) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.95f),
                                radius = 5f,
                                center = p,
                            )
                            drawCircle(
                                color = Color(0xFF2196F3),
                                radius = 5f,
                                center = p,
                                style = Stroke(width = 2f),
                            )
                        }
                        if (velocityOn) {
                            val nose = state.landmarks[FaceLandmark.NOSE_BASE]
                            val v = smoothVel
                            if (nose != null) {
                                val len = hypot(v.x.toDouble(), v.y.toDouble()).toFloat()
                                if (len > 1.2f) {
                                    val scale = min(120f, 40f + len * 3f)
                                    val nx = v.x / len
                                    val ny = v.y / len
                                    val end = Offset(nose.x + nx * scale, nose.y + ny * scale)
                                    drawLine(
                                        color = Color(0xFFFFEB3B),
                                        start = nose,
                                        end = end,
                                        strokeWidth = 5f,
                                    )
                                    val ah = 14f
                                    val back = Offset(-nx, -ny)
                                    val perp = Offset(-ny, nx)
                                    val tip = end
                                    val left = Offset(
                                        tip.x + back.x * ah + perp.x * ah * 0.5f,
                                        tip.y + back.y * ah + perp.y * ah * 0.5f,
                                    )
                                    val right = Offset(
                                        tip.x + back.x * ah - perp.x * ah * 0.5f,
                                        tip.y + back.y * ah - perp.y * ah * 0.5f,
                                    )
                                    val headPath = Path().apply {
                                        moveTo(tip.x, tip.y)
                                        lineTo(left.x, left.y)
                                        lineTo(right.x, right.y)
                                        close()
                                    }
                                    drawPath(headPath, Color(0xFFFFEB3B))
                                }
                            }
                        }
                        if (densePts != null) {
                            val dotFill = Color(0xFFFF1744).copy(alpha = 0.85f)
                            val dotRing = Color.White.copy(alpha = 0.75f)
                            for (p in densePts) {
                                drawCircle(color = dotFill, radius = 4.2f, center = p)
                                drawCircle(
                                    color = dotRing,
                                    radius = 4.2f,
                                    center = p,
                                    style = Stroke(width = 1.2f),
                                )
                            }
                        }
                    }
                }
                if (mimicCamera && camera5CursorTracking) {
                    Canvas(Modifier.fillMaxSize()) {
                        val p = camera5CursorPos
                        val down = camera5MouthOpen
                        drawCircle(
                            color = if (down) {
                                Color(0xFFFF5252).copy(alpha = 0.92f)
                            } else {
                                Color(0xFF00E676).copy(alpha = 0.88f)
                            },
                            radius = 24f,
                            center = p,
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 24f,
                            center = p,
                            style = Stroke(width = 3.5f),
                        )
                    }
                }
                if (mimicCamera && faceOverlay) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text(
                            text = when {
                                !mediaPipeOn -> stringResource(R.string.experiment_camera5_preview_live_mlkit)
                                liveMpLandmarkCount >= 80 -> stringResource(
                                    R.string.experiment_camera5_preview_live_mediapipe,
                                    liveMpLandmarkCount,
                                )
                                mediaPipeModelPresent -> stringResource(R.string.experiment_camera5_preview_live_mp_no_points)
                                else -> stringResource(R.string.experiment_camera5_preview_live_mp_no_file)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
                Camera5PreviewModeBanner(
                    mimicCamera = mimicCamera,
                    faceOverlay = faceOverlay,
                    mediaPipeOn = mediaPipeOn,
                    mediaPipeModelPresent = mediaPipeModelPresent,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(PaddingValues(horizontal = 8.dp, vertical = 8.dp)),
            ) {
                Text(
                    text = stringResource(R.string.experiment_camera5_mediapipe_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)),
                )
                Text(
                    text = stringResource(R.string.experiment_camera5_status_banner_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)),
                )
                Text(
                    text = stringResource(R.string.experiment_camera5_cursor_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)),
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.main_settings_mimic_camera_preview)) },
                    supportingContent = { Text(stringResource(R.string.main_settings_mimic_camera_preview_hint)) },
                    leadingContent = {
                        Icon(Icons.Filled.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    },
                    trailingContent = {
                        Switch(
                            checked = mimicCamera,
                            onCheckedChange = { want ->
                                if (want != mimicCamera) {
                                    scope.launch { preferences.setMimicCameraPreviewEnabled(want) }
                                }
                            },
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.experiment_camera5_toggle_draw_mesh)) },
                    supportingContent = { Text(stringResource(R.string.experiment_camera5_toggle_draw_mesh_hint)) },
                    leadingContent = {
                        Icon(Icons.Outlined.Face, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    },
                    trailingContent = {
                        Switch(
                            checked = faceOverlay,
                            onCheckedChange = { want ->
                                if (want != faceOverlay) {
                                    scope.launch { preferences.setMimicFaceOverlayEnabled(want) }
                                }
                            },
                        )
                    },
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.experiment_camera5_geometry_section_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                )
                Text(
                    text = stringResource(R.string.experiment_camera5_geometry_section_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                )
                Camera5GeometrySourceCard(
                    selected = !mediaPipeOn,
                    title = stringResource(R.string.experiment_camera5_geometry_pick_mlkit),
                    description = stringResource(R.string.experiment_camera5_geometry_pick_mlkit_desc),
                    onSelect = {
                        if (mediaPipeOn) {
                            scope.launch { preferences.setMimicMediaPipeFaceGeometryEnabled(false) }
                        }
                    },
                )
                Camera5GeometrySourceCard(
                    selected = mediaPipeOn,
                    title = stringResource(R.string.experiment_camera5_geometry_pick_mediapipe),
                    description = stringResource(R.string.experiment_camera5_geometry_pick_mediapipe_desc),
                    onSelect = {
                        if (!mediaPipeOn) {
                            scope.launch { preferences.setMimicMediaPipeFaceGeometryEnabled(true) }
                        }
                    },
                )
                if (!mediaPipeModelPresent) {
                    Text(
                        text = stringResource(R.string.experiment_camera5_mediapipe_model_missing_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.main_settings_mimic_velocity_vector)) },
                    supportingContent = { Text(stringResource(R.string.main_settings_mimic_velocity_vector_hint)) },
                    leadingContent = {
                        Icon(Icons.Filled.Navigation, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    },
                    trailingContent = {
                        Switch(
                            checked = velocityOn,
                            onCheckedChange = { want ->
                                if (want != velocityOn) {
                                    scope.launch { preferences.setMimicVelocityVectorVisible(want) }
                                }
                            },
                        )
                    },
                )
            }
        }
    }
}
