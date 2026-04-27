package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.bible.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.collect

private val ControlLeftPupilDrawColor = Color(0xFFFFEB3B)
private val ControlRightPupilDrawColor = Color(0xFF00E5FF)
private val ControlLeftFistColor = Color(0xFFFF6D00)
private val ControlRightFistColor = Color(0xFFD500F9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentCameraControlScreen(
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
                title = { Text(stringResource(R.string.experiment_camera_control_title)) },
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
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.experiment_camera_control_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
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
                val faceOutlineColor = MaterialTheme.colorScheme.tertiary
                val faceOutlineStrokePx = with(density) { 2.5.dp.toPx() }
                val mouthStrokePx = with(density) { 2.5.dp.toPx() }
                val smileArcColor = Color(0xFFFFEB3B)
                val openMouthIndicatorColor = Color(0xFFE53935)
                val eyebrowGray = Color(0xFF9E9E9E)

                var trackLeftHand by remember { mutableStateOf(true) }
                var trackRightHand by remember { mutableStateOf(true) }
                var trackPupils by remember { mutableStateOf(true) }
                var showCameraPreview by remember { mutableStateOf(true) }

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
                val quoteListState = rememberLazyListState()
                val centerQuoteIndex by remember {
                    derivedStateOf {
                        quoteRowIndexFromLazyListIndex(lazyListCenterItemIndex(quoteListState))
                    }
                }

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

                val scrollScalePx = with(density) { 620.dp.toPx() }
                var smileHorizontalOffsetPx by remember { mutableFloatStateOf(0f) }
                var quoteFontScale by remember { mutableFloatStateOf(1f) }
                val quoteBodyStyle = MaterialTheme.typography.bodyMedium.run {
                    copy(fontSize = (fontSize.value * quoteFontScale).sp)
                }
                LaunchedEffect(quoteListState, scrollScalePx) {
                    var prevNy = Float.NaN
                    var prevOk = false
                    var prevOpenMouthQuote = false
                    var prevEyeEllipseCircular = false
                    var prevSmileCombinedY = Float.NaN
                    var prevNoseX = Float.NaN
                    var prevBrowGap = Float.NaN
                    snapshotFlow { motionFrame }.collect { f ->
                        val engine = ttsRef.value
                        if (f.gestureStopSpeech) {
                            engine?.stop()
                        }
                        val ny = f.wristNormYScroll
                        if (ny == null) {
                            prevNy = Float.NaN
                        } else {
                            if (!prevNy.isNaN()) {
                                val dy = (ny - prevNy) * scrollScalePx
                                quoteListState.scroll { scrollBy(-dy) }
                            }
                            prevNy = ny
                        }

                        val smiling = f.mouthOverlay?.isSmile == true
                        val det = f.faceDetailOverlay
                        val noseC = noseCentroid(det?.noseTriangleInView)
                        val smileC = smileArcCentroid(f.mouthOverlay?.smileArcPointsInView)
                        if (smiling) {
                            val refY = when {
                                noseC != null && smileC != null -> (noseC.y + smileC.y) / 2f
                                noseC != null -> noseC.y
                                smileC != null -> smileC.y
                                else -> null
                            }
                            if (refY != null) {
                                if (!prevSmileCombinedY.isNaN()) {
                                    val dyFace = (refY - prevSmileCombinedY) * SmileFaceScrollYSensitivity
                                    if (abs(dyFace) > 0.6f) {
                                        quoteListState.scroll { scrollBy(-dyFace) }
                                    }
                                }
                                prevSmileCombinedY = refY
                            } else {
                                prevSmileCombinedY = Float.NaN
                            }

                            val nx = noseC?.x
                            if (nx != null) {
                                if (!prevNoseX.isNaN()) {
                                    val dx = (nx - prevNoseX) * SmileFaceScrollXSensitivity
                                    if (abs(dx) > 0.6f) {
                                        val maxH = scrollScalePx * 0.85f
                                        smileHorizontalOffsetPx =
                                            (smileHorizontalOffsetPx + dx).coerceIn(-maxH, maxH)
                                    }
                                }
                                prevNoseX = nx
                            } else {
                                prevNoseX = Float.NaN
                            }

                            val gap = det?.let { eyebrowEyeGapPx(it) }
                            if (gap != null) {
                                if (!prevBrowGap.isNaN()) {
                                    val dg = gap - prevBrowGap
                                    if (dg > SmileBrowGapThresholdPx) {
                                        quoteFontScale =
                                            (quoteFontScale + QuoteFontScaleStep)
                                                .coerceAtMost(QuoteFontScaleMax)
                                    } else if (-dg > SmileBrowGapThresholdPx) {
                                        quoteFontScale =
                                            (quoteFontScale - QuoteFontScaleStep)
                                                .coerceAtLeast(QuoteFontScaleMin)
                                    }
                                }
                                prevBrowGap = gap
                            } else {
                                prevBrowGap = Float.NaN
                            }
                        } else {
                            prevSmileCombinedY = Float.NaN
                            prevNoseX = Float.NaN
                            prevBrowGap = Float.NaN
                            smileHorizontalOffsetPx *= SmileHorizontalReleaseFactor
                            if (abs(smileHorizontalOffsetPx) < 0.75f) {
                                smileHorizontalOffsetPx = 0f
                            }
                        }

                        if (f.gestureOk && !prevOk) {
                            val qIdx = quoteRowIndexFromLazyListIndex(
                                lazyListCenterItemIndex(quoteListState),
                            )
                            if (qIdx >= 0) {
                                val text = ExperimentFamousQuotesRu[qIdx]
                                engine?.speak(
                                    text,
                                    TextToSpeech.QUEUE_FLUSH,
                                    Bundle(),
                                    "experiment_quote",
                                )
                            }
                        }
                        prevOk = f.gestureOk

                        val le = f.faceDetailOverlay?.leftEyeEllipseInView
                        val re = f.faceDetailOverlay?.rightEyeEllipseInView
                        val nowEyeCircular =
                            (le != null && isEyeEllipseNearlyCircular(le)) ||
                                (re != null && isEyeEllipseNearlyCircular(re))
                        if (nowEyeCircular && !prevEyeEllipseCircular) {
                            val qIdx = quoteRowIndexFromLazyListIndex(
                                lazyListCenterItemIndex(quoteListState),
                            )
                            if (qIdx >= 0) {
                                val text = ExperimentFamousQuotesRu[qIdx]
                                engine?.speak(
                                    text,
                                    TextToSpeech.QUEUE_FLUSH,
                                    Bundle(),
                                    "experiment_quote_eye_circle",
                                )
                            }
                        }
                        prevEyeEllipseCircular = nowEyeCircular

                        val openMouthNow = f.mouthOverlay?.isOpenMouth == true
                        if (openMouthNow && !prevOpenMouthQuote) {
                            engine?.stop()
                            val qIdx = quoteRowIndexFromLazyListIndex(
                                lazyListCenterItemIndex(quoteListState),
                            )
                            if (qIdx >= 0) {
                                val text = ExperimentFamousQuotesRu[qIdx]
                                engine?.speak(
                                    text,
                                    TextToSpeech.QUEUE_FLUSH,
                                    Bundle(),
                                    "experiment_quote_open_mouth",
                                )
                            }
                        }
                        prevOpenMouthQuote = openMouthNow
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

                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val vw = with(density) { maxWidth.toPx() }
                            val vh = with(density) { maxHeight.toPx() }

                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (!showCameraPreview) {
                                            Modifier.background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                val previewAlpha = if (showCameraPreview) 1f else 0f
                                ExperimentHandMotionCamera(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(previewAlpha),
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

                                Canvas(
                                    Modifier
                                        .fillMaxSize()
                                        .alpha(previewAlpha),
                                ) {
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
                                        drawGroup(leftFistSegments, ControlLeftFistColor, lineWidth)
                                    }
                                    if (trackRightHand) {
                                        drawGroup(rightSegments, rightDrawColor, lineWidth)
                                        drawGroup(rightFistSegments, ControlRightFistColor, lineWidth)
                                    }
                                    if (trackPupils) {
                                        drawGroup(leftPupilSegments, ControlLeftPupilDrawColor, pupilLineWidth)
                                        drawGroup(rightPupilSegments, ControlRightPupilDrawColor, pupilLineWidth)
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
                    }
                    LazyColumn(
                        state = quoteListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer { translationX = smileHorizontalOffsetPx },
                        userScrollEnabled = true,
                    ) {
                        item {
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
                        }
                        item {
                            CameraSectionTitle(stringResource(R.string.experiment_camera_section_drawing))
                            Text(
                                text = stringResource(R.string.experiment_camera_drawing_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        item {
                            CameraSectionTitle(stringResource(R.string.experiment_camera_section_control))
                            TrackCheckRow(
                                checked = showCameraPreview,
                                onCheckedChange = { showCameraPreview = it },
                                label = stringResource(R.string.experiment_camera_show_preview),
                            )
                            Spacer(Modifier.height(8.dp))
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
                        }
                        itemsIndexed(ExperimentFamousQuotesRu) { index, quote ->
                            val isCenter = index == centerQuoteIndex && centerQuoteIndex >= 0
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCenter) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                            ) {
                                Text(
                                    text = quote,
                                    style = quoteBodyStyle,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.experiment_quotes_gesture_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        }
                    }
                }
            }
        }
    }
}

private const val ExperimentQuoteListHeaderItems = 3

/** Чувствительность вертикальной прокрутки при улыбке (смещение носа + дуги улыбки по Y). */
private const val SmileFaceScrollYSensitivity = 2.35f

/** Чувствительность «свайпа по X» при улыбке (смещение носа по X). */
private const val SmileFaceScrollXSensitivity = 2.35f

/** Порог изменения зазора бровь–глаз (px) для шага масштаба шрифта. */
private const val SmileBrowGapThresholdPx = 5f

private const val QuoteFontScaleStep = 0.07f
private const val QuoteFontScaleMin = 0.72f
private const val QuoteFontScaleMax = 1.48f

/** Затухание горизонтального смещения списка, когда улыбки нет. */
private const val SmileHorizontalReleaseFactor = 0.78f

/**
 * Порог «почти круг»: отношение меньшей стороны bounding box эллипса глаза к большей
 * (1.0 — идеальный круг).
 */
private const val EyeEllipseCircleRatioThreshold = 0.86f

private fun isEyeEllipseNearlyCircular(r: RectF): Boolean {
    val w = r.width()
    val h = r.height()
    if (w < 2f || h < 2f) return false
    return min(w, h) / max(w, h) >= EyeEllipseCircleRatioThreshold
}

private fun noseCentroid(pts: List<Offset>?): Offset? {
    if (pts == null || pts.size != 3) return null
    val x = (pts[0].x + pts[1].x + pts[2].x) / 3f
    val y = (pts[0].y + pts[1].y + pts[2].y) / 3f
    return Offset(x, y)
}

private fun smileArcCentroid(pts: List<Offset>?): Offset? {
    if (pts.isNullOrEmpty()) return null
    var sx = 0f
    var sy = 0f
    for (p in pts) {
        sx += p.x
        sy += p.y
    }
    val n = pts.size.toFloat()
    return Offset(sx / n, sy / n)
}

/**
 * Средний вертикальный зазор «глаза — брови» (больше, если брови подняты вверх от глаз).
 */
private fun eyebrowEyeGapPx(det: ExperimentFaceDetailOverlay): Float? {
    val le = det.leftEyeEllipseInView
    val re = det.rightEyeEllipseInView
    val lb = det.leftEyebrowArcInView
    val rb = det.rightEyebrowArcInView
    if (le == null || re == null || lb == null || rb == null || lb.size < 2 || rb.size < 2) {
        return null
    }
    val eyeMeanY = (le.centerY() + re.centerY()) / 2f
    val lbMeanY = lb.sumOf { it.y.toDouble() }.toFloat() / lb.size
    val rbMeanY = rb.sumOf { it.y.toDouble() }.toFloat() / rb.size
    val browMeanY = (lbMeanY + rbMeanY) / 2f
    return eyeMeanY - browMeanY
}

private fun quoteRowIndexFromLazyListIndex(listIndex: Int): Int {
    val n = ExperimentFamousQuotesRu.size
    if (n == 0) return -1
    if (listIndex >= ExperimentQuoteListHeaderItems &&
        listIndex < ExperimentQuoteListHeaderItems + n
    ) {
        return listIndex - ExperimentQuoteListHeaderItems
    }
    return -1
}

private fun lazyListCenterItemIndex(state: LazyListState): Int {
    val li = state.layoutInfo
    val visible = li.visibleItemsInfo
    if (visible.isEmpty()) return 0
    val center = (li.viewportStartOffset + li.viewportEndOffset) / 2
    return visible.minByOrNull { item ->
        abs(item.offset + item.size / 2 - center)
    }?.index ?: 0
}
