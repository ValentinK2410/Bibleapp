package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bible.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

private val LeftPupilDrawColor = Color(0xFFFFEB3B)
private val RightPupilDrawColor = Color(0xFF00E5FF)
private val LeftFistColor = Color(0xFFFF6D00)
private val RightFistColor = Color(0xFFD500F9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentCameraScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> cameraGranted = granted }

    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var motionFrame by remember {
        mutableStateOf(
            ExperimentHandMotionFrame(
                imageWidth = 1,
                imageHeight = 1,
                leftX = null,
                leftY = null,
                rightX = null,
                rightY = null,
                leftFist = false,
                rightFist = false,
                leftPupilX = null,
                leftPupilY = null,
                rightPupilX = null,
                rightPupilY = null,
                faceInFrame = false,
                leftMoving = false,
                rightMoving = false,
                wristsInFrame = false,
                eraserRectInImage = null,
                gestureOk = false,
                gestureStopSpeech = false,
                wristNormYScroll = null,
                faceBoundsInView = null,
                mouthOverlay = null,
                faceDetailOverlay = null,
                headEulerZDeg = null,
                headEulerYDeg = null,
                headEulerXDeg = null,
            ),
        )
    }

    val statusText = when {
        !cameraGranted -> stringResource(R.string.experiment_camera_permission)
        !motionFrame.wristsInFrame -> stringResource(R.string.experiment_hand_none_visible)
        motionFrame.leftMoving && motionFrame.rightMoving -> stringResource(R.string.experiment_hand_both)
        motionFrame.leftMoving -> stringResource(R.string.experiment_hand_left)
        motionFrame.rightMoving -> stringResource(R.string.experiment_hand_right)
        else -> stringResource(R.string.experiment_hand_idle)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.experiment_camera_title)) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.experiment_camera_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            if (!cameraGranted) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.experiment_camera_grant))
                }
            } else {
                val density = LocalDensity.current
                val leftDrawColor = MaterialTheme.colorScheme.primary
                val rightDrawColor = MaterialTheme.colorScheme.tertiary
                val minStrokeStep = with(density) { 6.dp.toPx() }
                val lineWidth = with(density) { 7.dp.toPx() }
                val pupilLineWidth = with(density) { 6.dp.toPx() }

                var trackLeftHand by remember { mutableStateOf(true) }
                var trackRightHand by remember { mutableStateOf(true) }
                var trackPupils by remember { mutableStateOf(true) }

                val leftSegments = remember { mutableStateListOf<MutableList<Offset>>() }
                val rightSegments = remember { mutableStateListOf<MutableList<Offset>>() }
                val leftFistSegments = remember { mutableStateListOf<MutableList<Offset>>() }
                val rightFistSegments = remember { mutableStateListOf<MutableList<Offset>>() }
                val leftPupilSegments = remember { mutableStateListOf<MutableList<Offset>>() }
                val rightPupilSegments = remember { mutableStateListOf<MutableList<Offset>>() }

                var needNewLeft by remember { mutableStateOf(true) }
                var needNewRight by remember { mutableStateOf(true) }
                var needNewLeftFist by remember { mutableStateOf(true) }
                var needNewRightFist by remember { mutableStateOf(true) }
                var needNewLeftPupil by remember { mutableStateOf(true) }
                var needNewRightPupil by remember { mutableStateOf(true) }
                val bothEyesClosedSound = remember { BothEyesClosedSoundOnEdge() }

                LaunchedEffect(trackLeftHand) {
                    if (!trackLeftHand) {
                        needNewLeft = true
                        needNewLeftFist = true
                    }
                }
                LaunchedEffect(trackRightHand) {
                    if (!trackRightHand) {
                        needNewRight = true
                        needNewRightFist = true
                    }
                }
                LaunchedEffect(trackPupils) {
                    if (!trackPupils) {
                        needNewLeftPupil = true
                        needNewRightPupil = true
                    }
                }

                fun appendStroke(
                    segments: MutableList<MutableList<Offset>>,
                    needNew: Boolean,
                    setNeedNew: (Boolean) -> Unit,
                    o: Offset,
                ) {
                    if (needNew) {
                        segments.add(mutableListOf(o))
                        setNeedNew(false)
                    } else if (segments.isNotEmpty()) {
                        val idx = segments.lastIndex
                        val cur = segments[idx]
                        if (cur.isEmpty() ||
                            hypot(cur.last().x - o.x, cur.last().y - o.y) >= minStrokeStep
                        ) {
                            segments[idx] = cur.toMutableList().apply { add(o) }
                        }
                    }
                }

                val clearDrawing = rememberUpdatedState {
                    leftSegments.clear()
                    rightSegments.clear()
                    leftFistSegments.clear()
                    rightFistSegments.clear()
                    leftPupilSegments.clear()
                    rightPupilSegments.clear()
                    needNewLeft = true
                    needNewRight = true
                    needNewLeftFist = true
                    needNewRightFist = true
                    needNewLeftPupil = true
                    needNewRightPupil = true
                }

                val mainHandler = remember { Handler(Looper.getMainLooper()) }
                var ttsReady by remember { mutableStateOf(false) }
                val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }

                DisposableEffect(context.applicationContext) {
                    lateinit var engine: TextToSpeech
                    engine = TextToSpeech(context.applicationContext) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            engine.language = Locale.forLanguageTag("ru-RU")
                            ttsReady = true
                        }
                        ttsRef.value = engine
                        engine.setOnUtteranceProgressListener(
                            object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {}

                                override fun onDone(utteranceId: String?) {
                                    if (utteranceId == "experiment_shape") {
                                        mainHandler.post { clearDrawing.value.invoke() }
                                    }
                                }

                                @Deprecated("Deprecated in Java")
                                override fun onError(utteranceId: String?) {
                                    if (utteranceId == "experiment_shape") {
                                        mainHandler.post { clearDrawing.value.invoke() }
                                    }
                                }

                                override fun onError(utteranceId: String?, errorCode: Int) {
                                    if (utteranceId == "experiment_shape") {
                                        mainHandler.post { clearDrawing.value.invoke() }
                                    }
                                }
                            },
                        )
                    }
                    onDispose {
                        ttsReady = false
                        ttsRef.value?.stop()
                        ttsRef.value?.shutdown()
                        ttsRef.value = null
                    }
                }

                fun hasDrawing(): Boolean {
                    if (trackLeftHand &&
                        (leftSegments + leftFistSegments).any { it.size >= 2 }
                    ) {
                        return true
                    }
                    if (trackRightHand &&
                        (rightSegments + rightFistSegments).any { it.size >= 2 }
                    ) {
                        return true
                    }
                    if (trackPupils &&
                        (leftPupilSegments + rightPupilSegments).any { it.size >= 2 }
                    ) {
                        return true
                    }
                    return false
                }

                @Composable
                fun TrackCheckRow(
                    checked: Boolean,
                    onCheckedChange: (Boolean) -> Unit,
                    label: String,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = onCheckedChange,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }

                @Composable
                fun CameraSectionTitle(title: String) {
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                CameraSectionTitle(stringResource(R.string.experiment_camera_section_polygons))
                Text(
                    text = stringResource(R.string.experiment_camera_polygons_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val engine = ttsRef.value
                        if (!ttsReady || engine == null || !hasDrawing()) return@Button
                        val leftForShape = buildList {
                            if (trackLeftHand) {
                                addAll(leftSegments.map { it.toList() })
                                addAll(leftFistSegments.map { it.toList() })
                            }
                            if (trackPupils) {
                                addAll(leftPupilSegments.map { it.toList() })
                            }
                        }
                        val rightForShape = buildList {
                            if (trackRightHand) {
                                addAll(rightSegments.map { it.toList() })
                                addAll(rightFistSegments.map { it.toList() })
                            }
                            if (trackPupils) {
                                addAll(rightPupilSegments.map { it.toList() })
                            }
                        }
                        val shape = HandDrawShapeClassifier.classify(
                            leftForShape,
                            rightForShape,
                        )
                        val phrase = when (shape) {
                            HandDrawShapeClassifier.Shape.LINE ->
                                context.getString(R.string.experiment_shape_speak_line)
                            HandDrawShapeClassifier.Shape.TRIANGLE ->
                                context.getString(R.string.experiment_shape_speak_triangle)
                            HandDrawShapeClassifier.Shape.RECTANGLE ->
                                context.getString(R.string.experiment_shape_speak_rectangle)
                            HandDrawShapeClassifier.Shape.CIRCLE ->
                                context.getString(R.string.experiment_shape_speak_circle)
                            HandDrawShapeClassifier.Shape.POLYGON ->
                                context.getString(R.string.experiment_shape_speak_polygon)
                            HandDrawShapeClassifier.Shape.UNKNOWN ->
                                context.getString(R.string.experiment_shape_speak_unknown)
                        }
                        val params = Bundle()
                        engine.speak(
                            phrase,
                            TextToSpeech.QUEUE_FLUSH,
                            params,
                            "experiment_shape",
                        )
                    },
                    enabled = ttsReady && hasDrawing(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.experiment_shape_recognize))
                }

                CameraSectionTitle(stringResource(R.string.experiment_camera_section_drawing))
                Text(
                    text = stringResource(R.string.experiment_camera_drawing_hint_block),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                val faceOutlineColor = MaterialTheme.colorScheme.tertiary
                val faceOutlineStrokePx = with(density) { 2.5.dp.toPx() }
                val mouthStrokePx = with(density) { 2.5.dp.toPx() }
                val smileArcColor = Color(0xFFFFEB3B)
                val openMouthIndicatorColor = Color(0xFFE53935)
                val eyebrowGray = Color(0xFF9E9E9E)

                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                ) {
                    val vw = with(density) { maxWidth.toPx() }
                    val vh = with(density) { maxHeight.toPx() }

                    Box(Modifier.fillMaxSize()) {
                        ExperimentHandMotionCamera(
                            modifier = Modifier.fillMaxSize(),
                        ) { frame ->
                            motionFrame = frame
                            bothEyesClosedSound.consume(frame.mimicSignals)
                            val imgW = frame.imageWidth.toFloat().coerceAtLeast(1f)
                            val imgH = frame.imageHeight.toFloat().coerceAtLeast(1f)

                            frame.eraserRectInImage?.let { ir ->
                                val er = mapImageRectToViewLtrb(
                                    ir,
                                    imgW,
                                    imgH,
                                    vw,
                                    vh,
                                    mirrorX = true,
                                )
                                eraseStrokesInRect(leftSegments, er)
                                eraseStrokesInRect(rightSegments, er)
                                eraseStrokesInRect(leftFistSegments, er)
                                eraseStrokesInRect(rightFistSegments, er)
                                eraseStrokesInRect(leftPupilSegments, er)
                                eraseStrokesInRect(rightPupilSegments, er)
                            }

                            if (trackLeftHand &&
                                frame.leftX != null && frame.leftY != null && !frame.leftFist
                            ) {
                                val o = mapImagePointToViewFillCenter(
                                    frame.leftX,
                                    frame.leftY,
                                    imgW,
                                    imgH,
                                    vw,
                                    vh,
                                    mirrorX = true,
                                )
                                appendStroke(leftSegments, needNewLeft, { needNewLeft = it }, o)
                            } else {
                                needNewLeft = true
                            }

                            if (trackRightHand &&
                                frame.rightX != null && frame.rightY != null && !frame.rightFist
                            ) {
                                val o = mapImagePointToViewFillCenter(
                                    frame.rightX,
                                    frame.rightY,
                                    imgW,
                                    imgH,
                                    vw,
                                    vh,
                                    mirrorX = true,
                                )
                                appendStroke(rightSegments, needNewRight, { needNewRight = it }, o)
                            } else {
                                needNewRight = true
                            }

                            if (trackLeftHand &&
                                frame.leftFist && frame.leftX != null && frame.leftY != null
                            ) {
                                val o = mapImagePointToViewFillCenter(
                                    frame.leftX,
                                    frame.leftY,
                                    imgW,
                                    imgH,
                                    vw,
                                    vh,
                                    mirrorX = true,
                                )
                                appendStroke(
                                    leftFistSegments,
                                    needNewLeftFist,
                                    { needNewLeftFist = it },
                                    o,
                                )
                            } else {
                                needNewLeftFist = true
                            }

                            if (trackRightHand &&
                                frame.rightFist && frame.rightX != null && frame.rightY != null
                            ) {
                                val o = mapImagePointToViewFillCenter(
                                    frame.rightX,
                                    frame.rightY,
                                    imgW,
                                    imgH,
                                    vw,
                                    vh,
                                    mirrorX = true,
                                )
                                appendStroke(
                                    rightFistSegments,
                                    needNewRightFist,
                                    { needNewRightFist = it },
                                    o,
                                )
                            } else {
                                needNewRightFist = true
                            }

                            if (trackPupils && frame.leftPupilX != null && frame.leftPupilY != null) {
                                val o = mapImagePointToViewFillCenter(
                                    frame.leftPupilX,
                                    frame.leftPupilY,
                                    imgW,
                                    imgH,
                                    vw,
                                    vh,
                                    mirrorX = true,
                                )
                                appendStroke(
                                    leftPupilSegments,
                                    needNewLeftPupil,
                                    { needNewLeftPupil = it },
                                    o,
                                )
                            } else {
                                needNewLeftPupil = true
                            }
                            if (trackPupils && frame.rightPupilX != null && frame.rightPupilY != null) {
                                val o = mapImagePointToViewFillCenter(
                                    frame.rightPupilX,
                                    frame.rightPupilY,
                                    imgW,
                                    imgH,
                                    vw,
                                    vh,
                                    mirrorX = true,
                                )
                                appendStroke(
                                    rightPupilSegments,
                                    needNewRightPupil,
                                    { needNewRightPupil = it },
                                    o,
                                )
                            } else {
                                needNewRightPupil = true
                            }
                        }

                        Canvas(Modifier.fillMaxSize()) {
                            fun drawGroup(
                                segments: List<List<Offset>>,
                                col: Color,
                                sw: Float,
                            ) {
                                segments.forEach { seg ->
                                    if (seg.size >= 2) {
                                        for (i in 0 until seg.lastIndex) {
                                            drawLine(
                                                color = col,
                                                start = seg[i],
                                                end = seg[i + 1],
                                                strokeWidth = sw,
                                                cap = StrokeCap.Round,
                                            )
                                        }
                                    } else if (seg.size == 1) {
                                        drawCircle(
                                            color = col,
                                            radius = sw / 2f,
                                            center = seg[0],
                                        )
                                    }
                                }
                            }
                            if (trackLeftHand) {
                                drawGroup(leftSegments, leftDrawColor, lineWidth)
                                drawGroup(leftFistSegments, LeftFistColor, lineWidth)
                            }
                            if (trackRightHand) {
                                drawGroup(rightSegments, rightDrawColor, lineWidth)
                                drawGroup(rightFistSegments, RightFistColor, lineWidth)
                            }
                            if (trackPupils) {
                                drawGroup(leftPupilSegments, LeftPupilDrawColor, pupilLineWidth)
                                drawGroup(rightPupilSegments, RightPupilDrawColor, pupilLineWidth)
                            }
                            val mf = motionFrame
                            val pivotFace = mf.faceBoundsInView?.let { Offset(it.centerX(), it.centerY()) }
                                ?: mf.faceDetailOverlay?.let { d ->
                                    val le = d.leftEyeEllipseInView
                                    val re = d.rightEyeEllipseInView
                                    when {
                                        le != null && re != null -> Offset(
                                            (le.centerX() + re.centerX()) / 2f,
                                            (le.centerY() + re.centerY()) / 2f,
                                        )
                                        le != null -> Offset(le.centerX(), le.centerY())
                                        re != null -> Offset(re.centerX(), re.centerY())
                                        else -> null
                                    }
                                }
                            val eulerZ = mf.headEulerZDeg
                            fun drawFaceAlignedOverlays() {
                                mf.faceBoundsInView?.let { vr ->
                                    drawRect(
                                        color = faceOutlineColor,
                                        topLeft = Offset(vr.left, vr.top),
                                        size = Size(vr.width(), vr.height()),
                                        style = Stroke(width = faceOutlineStrokePx),
                                    )
                                }
                                mf.mouthOverlay?.let { mo ->
                                    when {
                                        mo.isSmile && !mo.smileArcPointsInView.isNullOrEmpty() -> {
                                            val pts = mo.smileArcPointsInView
                                            val path = Path().apply {
                                                moveTo(pts[0].x, pts[0].y)
                                                for (i in 1 until pts.size) {
                                                    lineTo(pts[i].x, pts[i].y)
                                                }
                                            }
                                            drawPath(
                                                path = path,
                                                color = smileArcColor,
                                                style = Stroke(
                                                    width = mouthStrokePx,
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round,
                                                ),
                                            )
                                        }
                                        mo.isOpenMouth && mo.ellipseInView != null -> {
                                            val er = mo.ellipseInView
                                            val r = min(er.width(), er.height()) / 2f
                                            drawCircle(
                                                color = openMouthIndicatorColor,
                                                radius = r,
                                                center = Offset(er.centerX(), er.centerY()),
                                                style = Stroke(
                                                    width = mouthStrokePx,
                                                    cap = StrokeCap.Round,
                                                ),
                                            )
                                        }
                                        mo.ellipseInView != null -> {
                                            val er = mo.ellipseInView
                                            drawOval(
                                                color = Color.White,
                                                topLeft = Offset(er.left, er.top),
                                                size = Size(er.width(), er.height()),
                                                style = Stroke(width = mouthStrokePx),
                                            )
                                        }
                                    }
                                }
                                mf.faceDetailOverlay?.let { det ->
                                    det.leftEyeEllipseInView?.let { er ->
                                        drawOval(
                                            color = Color.Black,
                                            topLeft = Offset(er.left, er.top),
                                            size = Size(er.width(), er.height()),
                                            style = Stroke(width = mouthStrokePx),
                                        )
                                    }
                                    det.rightEyeEllipseInView?.let { er ->
                                        drawOval(
                                            color = Color.Black,
                                            topLeft = Offset(er.left, er.top),
                                            size = Size(er.width(), er.height()),
                                            style = Stroke(width = mouthStrokePx),
                                        )
                                    }
                                    drawPupilGazeHints(
                                        det,
                                        mouthStrokePx,
                                        mf.mimicSignals?.leftPupilVisible,
                                        mf.mimicSignals?.rightPupilVisible,
                                    )
                                    det.noseTriangleInView?.let { pts ->
                                        if (pts.size == 3) {
                                            val nPath = Path().apply {
                                                moveTo(pts[0].x, pts[0].y)
                                                lineTo(pts[1].x, pts[1].y)
                                                lineTo(pts[2].x, pts[2].y)
                                                close()
                                            }
                                            drawPath(
                                                nPath,
                                                color = noseTriangleFillFromMimic(mf.mimicSignals),
                                                style = Fill,
                                            )
                                        }
                                    }
                                    fun drawBrowArc(pts: List<Offset>) {
                                        if (pts.size < 2) return
                                        val bPath = Path().apply {
                                            moveTo(pts[0].x, pts[0].y)
                                            for (i in 1 until pts.size) {
                                                lineTo(pts[i].x, pts[i].y)
                                            }
                                        }
                                        drawPath(
                                            bPath,
                                            color = eyebrowGray,
                                            style = Stroke(
                                                width = mouthStrokePx,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round,
                                            ),
                                        )
                                    }
                                    det.leftEyebrowArcInView?.let { drawBrowArc(it) }
                                    det.rightEyebrowArcInView?.let { drawBrowArc(it) }
                                }
                            }
                            if (pivotFace != null && eulerZ != null && abs(eulerZ) > 0.05f) {
                                rotate(degrees = eulerZ, pivot = pivotFace) {
                                    drawFaceAlignedOverlays()
                                }
                            } else {
                                drawFaceAlignedOverlays()
                            }
                        }
                    }
                }

                CameraSectionTitle(stringResource(R.string.experiment_camera_section_control))
                Button(
                    onClick = {
                        clearDrawing.value.invoke()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.experiment_hand_clear))
                }
                Spacer(Modifier.height(12.dp))
                TrackCheckRow(
                    checked = trackLeftHand,
                    onCheckedChange = { trackLeftHand = it },
                    label = stringResource(R.string.experiment_track_left_hand),
                )
                TrackCheckRow(
                    checked = trackRightHand,
                    onCheckedChange = { trackRightHand = it },
                    label = stringResource(R.string.experiment_track_right_hand),
                )
                TrackCheckRow(
                    checked = trackPupils,
                    onCheckedChange = { trackPupils = it },
                    label = stringResource(R.string.experiment_track_gaze),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.experiment_hand_draw_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
