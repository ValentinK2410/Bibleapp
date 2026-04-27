package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.bible.R

/** Экспоненциальное сглаживание позиции носа (0…1), больше — быстрее реакция. */
private const val NosePositionSmoothing = 0.28f

private const val SyntheticTapCooldownMs = 450L

private fun noseCentroidInPreview(det: ExperimentFaceDetailOverlay?): Offset? {
    val pts = det?.noseTriangleInView ?: return null
    if (pts.size != 3) return null
    val x = (pts[0].x + pts[1].x + pts[2].x) / 3f
    val y = (pts[0].y + pts[1].y + pts[2].y) / 3f
    return Offset(x, y)
}

/**
 * Эксперимент «Камера 4»: управление «курсором» по лицу на базе **уже используемого в приложении**
 * пайплайна ML Kit ([ExperimentHandMotionCamera]) + сглаживание + синтетическое касание
 * ([dispatchSyntheticTapAt]), как в режиме мимики в читалке. Отдельного стороннего SDK для «курсора по лицу»
 * в экосистеме Android обычно нет — типичный подход именно такой.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentCamera4Screen(
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

    var smileOnlyCursor by remember { mutableStateOf(true) }
    var showFaceOverlay by remember { mutableStateOf(true) }
    val smileOnlyState by rememberUpdatedState(smileOnlyCursor)

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

    var previewAnchor by remember { mutableStateOf<View?>(null) }
    var cursorPreviewPos by remember { mutableStateOf<Offset?>(null) }
    val smooth = remember {
        object {
            var x = Float.NaN
            var y = Float.NaN
        }
    }
    val tapEdge = remember {
        object {
            var prevOpenMouth = false
            var lastTapMs = 0L
        }
    }
    val bothEyesClosedSound = remember { BothEyesClosedSoundOnEdge() }

    var tapTestCount by remember { mutableIntStateOf(0) }

    val smileOverlay = motionFrame.mouthOverlay?.isSmile == true
    val yesShort = stringResource(R.string.experiment_yes_short)
    val noShort = stringResource(R.string.experiment_no_short)
    val mimicStatusText = buildString {
        val m = motionFrame.mimicSignals
        if (m == null || !m.facePresent) {
            appendLine(stringResource(R.string.experiment_camera4_mimic_no_face))
        } else {
            appendLine(stringResource(R.string.experiment_camera4_mimic_face))
            if (m.smile) appendLine(stringResource(R.string.experiment_camera4_mimic_smile))
            if (m.strongSmile) appendLine(stringResource(R.string.experiment_camera4_mimic_strong_smile))
            if (m.noseShiftLeft) appendLine(stringResource(R.string.experiment_camera4_mimic_nose_left))
            if (m.noseShiftRight) appendLine(stringResource(R.string.experiment_camera4_mimic_nose_right))
            if (m.noseShiftUp) appendLine(stringResource(R.string.experiment_camera4_mimic_nose_up))
            if (m.noseShiftDown) appendLine(stringResource(R.string.experiment_camera4_mimic_nose_down))
            if (m.mouthOpen) appendLine(stringResource(R.string.experiment_camera4_mimic_mouth_open))
            if (m.mouthOpenWide) appendLine(stringResource(R.string.experiment_camera4_mimic_mouth_wide))
            if (m.lipsPursedTube) appendLine(stringResource(R.string.experiment_camera4_mimic_lips_tube))
            if (m.eyebrowsRaised) appendLine(stringResource(R.string.experiment_camera4_mimic_brows_up))
            if (m.eyebrowsLowered) appendLine(stringResource(R.string.experiment_camera4_mimic_brows_down))
            val lo = m.leftEyeOpen
            val ro = m.rightEyeOpen
            val openL = stringResource(R.string.experiment_camera4_mimic_eye_open)
            val closedL = stringResource(R.string.experiment_camera4_mimic_eye_closed)
            val unclearL = stringResource(R.string.experiment_camera4_mimic_eye_unclear)
            fun label(o: Boolean?) = when (o) {
                true -> openL
                false -> closedL
                null -> unclearL
            }
            when {
                lo == null && ro == null -> {
                    appendLine(
                        stringResource(
                            R.string.experiment_camera4_mimic_eyes_partial,
                            unclearL,
                            unclearL,
                        ),
                    )
                }
                lo != null && ro != null -> when {
                    lo && ro -> appendLine(stringResource(R.string.experiment_camera4_mimic_eyes_both_open))
                    !lo && !ro -> appendLine(stringResource(R.string.experiment_camera4_mimic_eyes_both_closed))
                    lo && !ro -> appendLine(
                        stringResource(R.string.experiment_camera4_mimic_eyes_left_open_right_closed),
                    )
                    else -> appendLine(
                        stringResource(R.string.experiment_camera4_mimic_eyes_left_closed_right_open),
                    )
                }
                else -> {
                    appendLine(
                        stringResource(
                            R.string.experiment_camera4_mimic_eyes_partial,
                            label(lo),
                            label(ro),
                        ),
                    )
                }
            }
            val lp = m.leftPupilVisible
            val rp = m.rightPupilVisible
            val pupVis = stringResource(R.string.experiment_camera4_mimic_pupil_visible)
            val pupNo = stringResource(R.string.experiment_camera4_mimic_pupil_not_visible)
            fun pupLabel(p: Boolean?) = when (p) {
                true -> pupVis
                false -> pupNo
                null -> unclearL
            }
            when {
                lp == null && rp == null -> {
                    appendLine(
                        stringResource(
                            R.string.experiment_camera4_mimic_pupils_partial,
                            unclearL,
                            unclearL,
                        ),
                    )
                }
                lp != null && rp != null -> when {
                    lp && rp -> appendLine(stringResource(R.string.experiment_camera4_mimic_pupils_both_visible))
                    !lp && !rp -> appendLine(stringResource(R.string.experiment_camera4_mimic_pupils_both_hidden))
                    lp && !rp -> appendLine(
                        stringResource(R.string.experiment_camera4_mimic_pupils_left_visible_right_hidden),
                    )
                    else -> appendLine(
                        stringResource(R.string.experiment_camera4_mimic_pupils_left_hidden_right_visible),
                    )
                }
                else -> {
                    appendLine(
                        stringResource(
                            R.string.experiment_camera4_mimic_pupils_partial,
                            pupLabel(lp),
                            pupLabel(rp),
                        ),
                    )
                }
            }
            if (m.bothEyesBlink) {
                appendLine(stringResource(R.string.experiment_camera4_mimic_blink_both))
            } else {
                if (m.leftEyeBlink) appendLine(stringResource(R.string.experiment_camera4_mimic_blink_left))
                if (m.rightEyeBlink) appendLine(stringResource(R.string.experiment_camera4_mimic_blink_right))
            }
        }
        appendLine()
        append(
            stringResource(
                R.string.experiment_camera4_mimic_aux,
                if (cursorPreviewPos != null) yesShort else noShort,
                if (smileOverlay) yesShort else noShort,
                if (motionFrame.wristsInFrame) yesShort else noShort,
            ),
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.experiment_camera4_title)) },
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
        if (!cameraGranted) {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Text(
                    stringResource(R.string.experiment_camera_permission),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.padding(12.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                ) {
                    Text(stringResource(R.string.experiment_camera_grant))
                }
            }
        } else {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                Text(
                    text = stringResource(R.string.experiment_camera4_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.experiment_camera4_cursor_smile_only),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = smileOnlyCursor,
                        onCheckedChange = { smileOnlyCursor = it },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.experiment_camera4_show_face_overlay),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = showFaceOverlay,
                        onCheckedChange = { showFaceOverlay = it },
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    ExperimentHandMotionCamera(
                        modifier = Modifier.fillMaxSize(),
                        onPreviewViewReady = { pv: PreviewView -> previewAnchor = pv },
                    ) { frame ->
                        motionFrame = frame
                        bothEyesClosedSound.consume(frame.mimicSignals)
                        val smiling = frame.mouthOverlay?.isSmile == true // оверлей рта; мимика — frame.mimicSignals
                        val nose = noseCentroidInPreview(frame.faceDetailOverlay)
                        val openMouth = frame.mouthOverlay?.isOpenMouth == true
                        val wantSmile = smileOnlyState
                        val cursorOk = if (wantSmile) smiling && nose != null else nose != null

                        if (cursorOk && nose != null) {
                            val pv = previewAnchor
                            if (pv != null && openMouth && !tapEdge.prevOpenMouth) {
                                val now = SystemClock.uptimeMillis()
                                if (now - tapEdge.lastTapMs >= SyntheticTapCooldownMs) {
                                    val sx = if (smooth.x.isNaN()) nose.x else smooth.x
                                    val sy = if (smooth.y.isNaN()) nose.y else smooth.y
                                    dispatchSyntheticTapFromPreviewLocal(
                                        pv,
                                        context,
                                        Offset(sx, sy),
                                    )
                                    tapEdge.lastTapMs = now
                                }
                            }
                            tapEdge.prevOpenMouth = openMouth

                            val nx = nose.x
                            val ny = nose.y
                            if (smooth.x.isNaN()) {
                                smooth.x = nx
                                smooth.y = ny
                            } else {
                                smooth.x += (nx - smooth.x) * NosePositionSmoothing
                                smooth.y += (ny - smooth.y) * NosePositionSmoothing
                            }
                            cursorPreviewPos = Offset(smooth.x, smooth.y)
                        } else {
                            tapEdge.prevOpenMouth = false
                            cursorPreviewPos = null
                            smooth.x = Float.NaN
                            smooth.y = Float.NaN
                        }
                    }
                    if (showFaceOverlay) {
                        MimicFacePreviewOverlay(
                            frame = motionFrame,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    MimicControlCursor(
                        noseCenter = cursorPreviewPos,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                    Button(
                        onClick = { tapTestCount++ },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        Text(stringResource(R.string.experiment_camera4_tap_test, tapTestCount))
                    }
                    Text(
                        text = mimicStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
