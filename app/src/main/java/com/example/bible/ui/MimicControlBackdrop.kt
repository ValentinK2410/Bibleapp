package com.example.bible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import com.google.mlkit.vision.face.FaceLandmark
import androidx.camera.view.PreviewView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Заливка треугольника носа: базовый коричневый; при сдвиге кончика носа от спокойной базы —
 * влево → жёлтый, вправо → красный, вверх → голубой, вниз → зелёный ([ExperimentMimicSignals]).
 */
/** Доля меньшей стороны овала глаза: смещение зрачка больше — «не по центру». */
private const val PupilOffCenterFrac = 0.072f

/** Оранжевая обводка зрачка и шевроны направления (к центру смещения). */
private val PupilHintOrange = Color(0xFFFF9800)

/**
 * Если зрачок заметно смещён от центра овала глаза — оранжевое кольцо вокруг зрачка
 * и несколько маленьких стрелочек от центра глаза к зрачку.
 * Оранжевая разметка только при [leftPupilVisible]/[rightPupilVisible] == true (зрачок реально виден).
 */
internal fun DrawScope.drawPupilGazeHints(
    det: ExperimentFaceDetailOverlay,
    lineStrokePx: Float,
    leftPupilVisible: Boolean?,
    rightPupilVisible: Boolean?,
) {
    fun oneEye(eye: RectF?, pupil: Offset?, pupilVisible: Boolean?) {
        if (eye == null || pupil == null) return
        if (pupilVisible != true) return
        val cx = eye.centerX()
        val cy = eye.centerY()
        val eyeMin = minOf(eye.width(), eye.height()).coerceAtLeast(1f)
        val dx = pupil.x - cx
        val dy = pupil.y - cy
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist <= eyeMin * PupilOffCenterFrac) return
        val ux = dx / dist
        val uy = dy / dist
        val ringR = eyeMin * 0.11f
        drawCircle(
            color = PupilHintOrange,
            radius = ringR,
            center = pupil,
            style = Stroke(width = max(lineStrokePx * 0.95f, 2.1f)),
        )
        val start = Offset(cx + ux * eyeMin * 0.11f, cy + uy * eyeMin * 0.11f)
        val end = Offset(
            pupil.x - ux * (ringR + 5f),
            pupil.y - uy * (ringR + 5f),
        )
        val span = (end.x - start.x) * ux + (end.y - start.y) * uy
        if (span > eyeMin * 0.06f) {
            drawPupilDirectionChevrons(start, end, PupilHintOrange, max(lineStrokePx * 0.78f, 1.6f))
        }
    }
    oneEye(det.leftEyeEllipseInView, det.leftPupilInView, leftPupilVisible)
    oneEye(det.rightEyeEllipseInView, det.rightPupilInView, rightPupilVisible)
}

private fun DrawScope.drawPupilDirectionChevrons(
    from: Offset,
    to: Offset,
    color: Color,
    strokeW: Float,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (len < 5f) return
    val ux = dx / len
    val uy = dy / len
    val n = 3
    val chevronA = (len * 0.24f).coerceIn(5.5f, 12f)
    for (i in 1..n) {
        val t = i / (n + 1).toFloat()
        val cx = from.x + dx * t
        val cy = from.y + dy * t
        val px = -uy * chevronA * 0.42f
        val py = ux * chevronA * 0.42f
        val tip = Offset(cx + ux * chevronA * 0.4f, cy + uy * chevronA * 0.4f)
        drawLine(color, Offset(cx - px, cy - py), tip, strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color, Offset(cx + px, cy + py), tip, strokeWidth = strokeW, cap = StrokeCap.Round)
    }
}

internal fun noseTriangleFillFromMimic(mimic: ExperimentMimicSignals?): Color {
    val base = Color(0xFF6D4C41)
    val m = mimic ?: return base
    if (!m.facePresent) return base
    val mix = 0.58f
    var c = base
    if (m.noseShiftLeft) c = lerp(c, Color(0xFFFFEB3B), mix)
    if (m.noseShiftRight) c = lerp(c, Color(0xFFE53935), mix)
    if (m.noseShiftUp) c = lerp(c, Color(0xFF29B6F6), mix)
    if (m.noseShiftDown) c = lerp(c, Color(0xFF43A047), mix)
    return c
}

/** Чувствительность сдвига носа для прокрутки стихов — только при открытом рте и улыбке. */
private const val MimicNoseScrollSensitivity = 2.6f

/** Сдвиг курсора по экрану на 1° поворота головы Y при открытом рте (указатель «зажат»). */
private const val MimicOpenMouthYawPxPerDeg = 14f

/** Сдвиг по вертикали на 1° pitch (Euler X) при открытом рте. */
private const val MimicOpenMouthPitchPxPerDeg = 12f

/** Два открытия рта подряд (фронты) не дальше этого интервала — переключение «курсор нажат». */
private const val MimicDoubleOpenMouthWindowMs = 720L

/** После переключения игнорировать новые фронты открытия рта (антидребезг). */
private const val MimicDoubleOpenMouthDebounceMs = 420L

/** Два фронта «брови подняты» (false→true) подряд в этом окне — синтетический одиночный клик. */
private const val MimicDoubleBrowTapWindowMs = 720L

private const val MimicDoubleBrowTapDebounceMs = 420L

/** Минимальный сдвиг курсора (px) для очередного синтетического MOVE. */
private const val MimicSyntheticMoveEpsilonPx = 0.45f

/** Мимика 2 — те же коэффициенты, что «Камера 5»: экранная дельта носа, разгон, рот = down/move/up. */
private const val MIMIC_V2_CURSOR_GAIN = 2.35f
private const val MIMIC_V2_BOOST_MIN_SPEED = 0.42f
private const val MIMIC_V2_BOOST_MIN_COS = 0.88f
private const val MIMIC_V2_BOOST_HOLD_MS = 380L
private const val MIMIC_V2_BOOST_RAMP_PER_FRAME = 0.14f
private const val MIMIC_V2_BOOST_MAX = 5f
private const val MIMIC_V2_BOOST_DECAY_ON_TURN = 0.82f
private const val MIMIC_V2_BOOST_DECAY_SLOW = 0.93f
private const val MIMIC_V2_VEL_ARROW_MIN_SPD = 1.2f

/** Окно для жеста «отмена» по углу [headEulerYDeg] (нейтральное лицо). */
private const val MimicCancelShakeWindowMs = 3200L

/** Минимальный поворот головы по Y (ML Kit), градусы, на одну «ногу» жеста. */
private const val MimicCancelMinYawStrokeDeg = 17f

/** Сколько подряд кадров удерживать порог — отсекает выбросы одного кадра. */
private const val MimicCancelSustainFrames = 6

private const val MimicCancelCooldownMs = 1200L

/** Ниже этой скорости (px за кадр камеры) нос считаем почти неподвижным. */
private const val MimicNoseSpeedMovingThresholdPx = 0.45f

/** Сглаживание вектора скорости носа (0…1), больше — быстрее реагирует. */
private const val MimicCursorVelEmaAlpha = 0.42f

/**
 * Затухание EMA за кадр, когда нос почти стоит, **но улыбка ещё есть** — близко к 1, чтобы направление
 * не «гасло» сразу; без улыбки EMA обнуляется отдельно.
 */
private const val MimicCursorEmaDecayWhileSmilingSlow = 0.992f

/** Базовый множитель «газа» (при новой улыбке и без улыбки). */
private const val MimicDriveRampBase = 1f

/** Насколько растёт множитель за кадр, пока нос реально двигается при улыбке (ускорение нарастает). */
private const val MimicDriveRampUpPerFrame = 0.088f

/** Потолок множителя ускорения при длительном ведении. */
private const val MimicDriveRampMax = 4.25f

/** За кадр «газ» чуть спадает, если нос почти остановился, но улыбка ещё есть (не мгновенный сброс). */
private const val MimicDriveRampDownWhenNoseSlow = 0.005f

/** Множитель: базовая тяга от движения носа (px/кадр до ramp и gain). */
private const val MimicCursorVelGain = 3.85f

/** Насколько быстро фактическая скорость курсора догоняет целевую за кадр (0…1). */
private const val MimicCursorAccelTowardTarget = 0.24f

/** Потолок скорости курсора за кадр (px) при базовом ramp=1; с ростом ramp потолок растёт пропорционально. */
private const val MimicCursorMaxSpeedPxPerFrame = 14f

/** Длина стрелки вектора на экране: база + множитель |EMA|. */
private const val MimicVectorArrowBaseLength = 52f
private const val MimicVectorArrowGain = 12f
private const val MimicVectorArrowMaxLength = 200f

/** Прозрачность видеопотока при включённом превью лица (интерфейс остаётся поверх). */
private const val MimicFacePreviewCameraAlpha = 0.40f

/** Сколько подряд кадров без лица — только тогда убираем оверлей (убирает мигание контуров). */
private const val MimicFaceOverlayClearAfterMissFrames = 22

/** Центр носа только по треугольнику контура ML Kit; без запасных точек. */
private fun noseCentroidOnly(det: ExperimentFaceDetailOverlay?): Offset? {
    val pts = det?.noseTriangleInView ?: return null
    if (pts.size != 3) return null
    val x = (pts[0].x + pts[1].x + pts[2].x) / 3f
    val y = (pts[0].y + pts[1].y + pts[2].y) / 3f
    return Offset(x, y)
}

private class MimicV2CursorState {
    var lastNoseScreen: Offset? = null
    var smoothVelX = 0f
    var smoothVelY = 0f
    var boost = 1f
    var alignAnchor: Offset? = null
    var alignSinceMs = 0L
    var prevMouthOpen = false
    var pointerDownMs = 0L
    var lastEmittedMove: Offset? = null
    var cursorInitialized = false

    fun reset() {
        lastNoseScreen = null
        smoothVelX = 0f
        smoothVelY = 0f
        boost = 1f
        alignAnchor = null
        alignSinceMs = 0L
        prevMouthOpen = false
        pointerDownMs = 0L
        lastEmittedMove = null
        cursorInitialized = false
    }
}

/**
 * Камера под интерфейсом (альфа 0): курсор — накопленные экранные координаты (не центр лица).
 * При **улыбке** целевая скорость растёт со временем, пока нос ведёт: множитель «газа» накапливается до [MimicDriveRampMax] — курсор **ускоряется**. Без улыбки множитель и скорость сбрасываются.
 * EMA при почти неподвижном носу при улыбке затухает мягко ([MimicCursorEmaDecayWhileSmilingSlow]).
 * [showVelocityVector] — стрелка направления. Лицо на миг пропало — точка курсора сохраняется.
 * Прокрутка стихов — **улыбка + открытый рот + движение носа вверх/вниз**; горизонтальная смена глав мимикой отключена.
 * **Два быстрых поднятия бровей** (брови вниз → вверх, два раза подряд) — одиночный синтетический клик в точке курсора (не выполняется, пока активен режим «зажатого» указателя после двойного открытия рта).
 * **Два быстрых открытия рта** подряд — переключение синтетического «нажатия» (DOWN / UP) и полупрозрачного ореола курсора; пока нажато — MOVE при любом движении курсора; при открытом рте дополнительно сдвиг по [headEulerAngleY]/[headEulerAngleX]. При улыбке без открытого рта курсор ведётся носом.
 * **Отмена:** нейтральное лицо — заметный поворот головы влево и вправо по ML Kit [headEulerAngleY] (порядок любой), каждая фаза не меньше [MimicCancelMinYawStrokeDeg]° и удерживается [MimicCancelSustainFrames] кадров.
 *
 * [mimicControlV2]: альтернатива — направление по экранной дельте носа и разгон (как «Камера 5»); открытый рот — удержание без двойного открытия; прокрутка при открытом рте и движении носа по Y.
 */
@Composable
fun MimicControlBackdrop(
    modifier: Modifier = Modifier,
    mimicControlV2: Boolean = false,
    showCameraPreview: Boolean = false,
    showFaceOverlay: Boolean = false,
    /** Если задан — кадр для [MimicFacePreviewOverlay] обновляется сюда, чтобы слой можно было рисовать поверх NavHost. */
    faceOverlayFrameState: MutableState<ExperimentHandMotionFrame?>? = null,
    showVelocityVector: Boolean = false,
    /** Экранные координаты центра «носа» (px): [android.view.View.getLocationOnScreen] превью + точка в превью. */
    onNoseScreenPosition: (Offset?) -> Unit,
    /** Начало стрелки = центр курсора, конец = направление EMA; (null,null) — нерисовать. */
    onVelocityVector: ((Offset?, Offset?) -> Unit)? = null,
    /** Вертикальная прокрутка текста (стихи): вызывать только из логики «рот открыт + улыбка». */
    onVerticalScrollDy: (Float) -> Unit,
    /** Жест отмены (поворот головы влево-вправо при нейтральном лице) — обычно [androidx.activity.OnBackPressedDispatcher.onBackPressed]. */
    onMimicCancel: (() -> Unit)? = null,
    /** Синтетическое «нажатие» активно (два открытия рта) — для ореола курсора в оверлее. */
    onSyntheticPointerPressedChange: ((Boolean) -> Unit)? = null,
) {
    val onNose by rememberUpdatedState(onNoseScreenPosition)
    val onVelVec by rememberUpdatedState(onVelocityVector)
    val onScrollY by rememberUpdatedState(onVerticalScrollDy)
    val onCancel by rememberUpdatedState(onMimicCancel)
    val onPressChange by rememberUpdatedState(onSyntheticPointerPressedChange)
    val trackOverlayFrame by rememberUpdatedState(showFaceOverlay)
    val mimicV2 by rememberUpdatedState(mimicControlV2)
    var prevNoseY by remember { mutableFloatStateOf(Float.NaN) }
    val overlayFrame = faceOverlayFrameState ?: remember { mutableStateOf<ExperimentHandMotionFrame?>(null) }
    val cursorStep = remember {
        object {
            var lockedScreen: Offset? = null
            var prevSmileAccumNose: Offset? = null
            var wasSmiling = false
            var velEmaX = 0f
            var velEmaY = 0f
            var cursorVelX = 0f
            var cursorVelY = 0f
            var driveRamp = MimicDriveRampBase
        }
    }
    var overlayMissStreak by remember { mutableIntStateOf(0) }
    val appContext = LocalContext.current
    val bothEyesClosedSound = remember { BothEyesClosedSoundOnEdge() }
    var previewAnchor by remember { mutableStateOf<View?>(null) }
    val tapEdge = remember {
        object {
            var prevOpenMouth = false
        }
    }
    val browClick = remember {
        object {
            var lastRiseMs = 0L
            var ignoreRiseUntilMs = 0L
        }
    }
    val browEdge = remember {
        object {
            var prevRaised = false
        }
    }
    val mouthDrag = remember {
        object {
            var downTime = 0L
            /** Синтетический указатель «зажат» (после двойного открытия рта). */
            var pointerPressed = false
            var lastYaw = Float.NaN
            var lastPitch = Float.NaN
            var lastOpenMouthRiseMs = 0L
            var ignoreOpenMouthRiseUntilMs = 0L
            var lastEmittedMove: Offset? = null
        }
    }
    val v2State = remember { MimicV2CursorState() }
    val cancelShake = remember {
        object {
            var phase = 0
            /** 1 = сначала поворот в сторону уменьшения Y; 2 = сначала в сторону увеличения Y. */
            var firstLeg = 0
            var sessionStartMs = 0L
            var originYaw = Float.NaN
            var minYaw = Float.NaN
            var maxYaw = Float.NaN
            var commitExtreme = Float.NaN
            var leg2Max = Float.NaN
            var leg2Min = Float.NaN
            var sustainNeg = 0
            var sustainPos = 0
            var sustainLeg2 = 0
            var lastFireMs = 0L
        }
    }

    LaunchedEffect(showFaceOverlay) {
        if (!showFaceOverlay) {
            overlayFrame.value = null
            overlayMissStreak = 0
        }
    }

    LaunchedEffect(mimicControlV2) {
        if (mimicControlV2) {
            if (mouthDrag.pointerPressed && mouthDrag.downTime != 0L) {
                cursorStep.lockedScreen?.let { tip ->
                    dispatchSyntheticDragUpFromScreen(appContext, tip.x, tip.y, mouthDrag.downTime)
                }
            }
            mouthDrag.pointerPressed = false
            mouthDrag.downTime = 0L
            mouthDrag.lastEmittedMove = null
            mouthDrag.lastOpenMouthRiseMs = 0L
            mouthDrag.ignoreOpenMouthRiseUntilMs = 0L
            tapEdge.prevOpenMouth = false
            v2State.reset()
            onPressChange?.invoke(false)
        } else {
            if (v2State.pointerDownMs != 0L) {
                cursorStep.lockedScreen?.let { tip ->
                    dispatchSyntheticDragUpFromScreen(appContext, tip.x, tip.y, v2State.pointerDownMs)
                }
            }
            v2State.reset()
            tapEdge.prevOpenMouth = false
            onPressChange?.invoke(false)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ExperimentHandMotionCamera(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (showCameraPreview) MimicFacePreviewCameraAlpha else 0f),
            onPreviewViewReady = { pv: PreviewView -> previewAnchor = pv },
        ) { frame ->
            bothEyesClosedSound.consume(frame.mimicSignals)
            if (trackOverlayFrame) {
                val hasFaceDrawData = frame.faceBoundsInView != null ||
                    frame.faceDetailOverlay != null ||
                    frame.mouthOverlay != null
                if (hasFaceDrawData) {
                    overlayFrame.value = frame
                    overlayMissStreak = 0
                } else {
                    overlayMissStreak++
                    if (overlayMissStreak >= MimicFaceOverlayClearAfterMissFrames) {
                        overlayFrame.value = null
                    }
                }
            }
            val smiling = frame.mouthOverlay?.isSmile == true
            val browsRaised = frame.mimicSignals?.eyebrowsRaised == true
            val nose = noseCentroidOnly(frame.faceDetailOverlay)
            val openMouth = frame.mouthOverlay?.isOpenMouth == true
            val pitchEuler = frame.headEulerXDeg
            val cancelCb = onCancel
            val yaw = frame.headEulerYDeg
            val nowShake = SystemClock.uptimeMillis()

            fun releaseSyntheticPointer() {
                if (mouthDrag.pointerPressed && mouthDrag.downTime != 0L) {
                    cursorStep.lockedScreen?.let { tip ->
                        dispatchSyntheticDragUpFromScreen(
                            appContext,
                            tip.x,
                            tip.y,
                            mouthDrag.downTime,
                        )
                    }
                }
                mouthDrag.pointerPressed = false
                mouthDrag.downTime = 0L
                mouthDrag.lastEmittedMove = null
                onPressChange?.invoke(false)
            }
            fun releaseV2SyntheticIfDown() {
                if (v2State.pointerDownMs != 0L) {
                    cursorStep.lockedScreen?.let { tip ->
                        dispatchSyntheticDragUpFromScreen(
                            appContext,
                            tip.x,
                            tip.y,
                            v2State.pointerDownMs,
                        )
                    }
                    v2State.pointerDownMs = 0L
                    v2State.lastEmittedMove = null
                    onPressChange?.invoke(false)
                }
            }
            fun resetCancelGesture() {
                cancelShake.phase = 0
                cancelShake.firstLeg = 0
                cancelShake.sessionStartMs = 0L
                cancelShake.originYaw = Float.NaN
                cancelShake.minYaw = Float.NaN
                cancelShake.maxYaw = Float.NaN
                cancelShake.commitExtreme = Float.NaN
                cancelShake.leg2Max = Float.NaN
                cancelShake.leg2Min = Float.NaN
                cancelShake.sustainNeg = 0
                cancelShake.sustainPos = 0
                cancelShake.sustainLeg2 = 0
            }
            if (cancelCb != null && yaw != null && !smiling && !openMouth) {
                if (nowShake - cancelShake.lastFireMs < MimicCancelCooldownMs) {
                    resetCancelGesture()
                } else {
                    if (cancelShake.originYaw.isNaN()) {
                        cancelShake.originYaw = yaw
                        cancelShake.minYaw = yaw
                        cancelShake.maxYaw = yaw
                        cancelShake.sessionStartMs = nowShake
                        cancelShake.phase = 0
                        cancelShake.firstLeg = 0
                    }
                    if (nowShake - cancelShake.sessionStartMs > MimicCancelShakeWindowMs) {
                        resetCancelGesture()
                        cancelShake.originYaw = yaw
                        cancelShake.minYaw = yaw
                        cancelShake.maxYaw = yaw
                        cancelShake.sessionStartMs = nowShake
                    } else if (cancelShake.phase == 0) {
                        cancelShake.minYaw = min(cancelShake.minYaw, yaw)
                        cancelShake.maxYaw = max(cancelShake.maxYaw, yaw)
                        if (cancelShake.originYaw - cancelShake.minYaw >= MimicCancelMinYawStrokeDeg) {
                            cancelShake.sustainNeg++
                        } else {
                            cancelShake.sustainNeg = 0
                        }
                        if (cancelShake.maxYaw - cancelShake.originYaw >= MimicCancelMinYawStrokeDeg) {
                            cancelShake.sustainPos++
                        } else {
                            cancelShake.sustainPos = 0
                        }
                        when {
                            cancelShake.sustainNeg >= MimicCancelSustainFrames -> {
                                cancelShake.phase = 1
                                cancelShake.firstLeg = 1
                                cancelShake.commitExtreme = cancelShake.minYaw
                                cancelShake.leg2Max = yaw
                                cancelShake.sustainLeg2 = 0
                            }
                            cancelShake.sustainPos >= MimicCancelSustainFrames -> {
                                cancelShake.phase = 1
                                cancelShake.firstLeg = 2
                                cancelShake.commitExtreme = cancelShake.maxYaw
                                cancelShake.leg2Min = yaw
                                cancelShake.sustainLeg2 = 0
                            }
                        }
                    } else if (cancelShake.firstLeg == 1) {
                        cancelShake.leg2Max = max(cancelShake.leg2Max, yaw)
                        if (cancelShake.leg2Max - cancelShake.commitExtreme >= MimicCancelMinYawStrokeDeg) {
                            cancelShake.sustainLeg2++
                        } else {
                            cancelShake.sustainLeg2 = 0
                        }
                        if (cancelShake.sustainLeg2 >= MimicCancelSustainFrames) {
                            releaseSyntheticPointer()
                            releaseV2SyntheticIfDown()
                            cancelCb()
                            cancelShake.lastFireMs = nowShake
                            resetCancelGesture()
                        }
                    } else if (cancelShake.firstLeg == 2) {
                        cancelShake.leg2Min = min(cancelShake.leg2Min, yaw)
                        if (cancelShake.commitExtreme - cancelShake.leg2Min >= MimicCancelMinYawStrokeDeg) {
                            cancelShake.sustainLeg2++
                        } else {
                            cancelShake.sustainLeg2 = 0
                        }
                        if (cancelShake.sustainLeg2 >= MimicCancelSustainFrames) {
                            releaseSyntheticPointer()
                            releaseV2SyntheticIfDown()
                            cancelCb()
                            cancelShake.lastFireMs = nowShake
                            resetCancelGesture()
                        }
                    }
                }
            } else {
                resetCancelGesture()
            }

            if (nose == null) {
                releaseSyntheticPointer()
                releaseV2SyntheticIfDown()
                v2State.reset()
                mouthDrag.lastYaw = Float.NaN
                mouthDrag.lastPitch = Float.NaN
                mouthDrag.lastOpenMouthRiseMs = 0L
                tapEdge.prevOpenMouth = false
                browEdge.prevRaised = false
                browClick.lastRiseMs = 0L
                browClick.ignoreRiseUntilMs = 0L
                cursorStep.prevSmileAccumNose = null
                cursorStep.cursorVelX = 0f
                cursorStep.cursorVelY = 0f
                cursorStep.velEmaX = 0f
                cursorStep.velEmaY = 0f
                cursorStep.driveRamp = MimicDriveRampBase
                if (showVelocityVector) onVelVec?.invoke(null, null)
                onNose(cursorStep.lockedScreen)
                prevNoseY = Float.NaN
                return@ExperimentHandMotionCamera
            }

            if (mimicV2) {
                val pvV2 = previewAnchor
                val screenNoseV2 = if (pvV2 != null) {
                    val sl = IntArray(2)
                    pvV2.getLocationOnScreen(sl)
                    Offset(sl[0] + nose.x, sl[1] + nose.y)
                } else {
                    null
                }
                if (screenNoseV2 == null) {
                    releaseV2SyntheticIfDown()
                    v2State.reset()
                    if (showVelocityVector) onVelVec?.invoke(null, null)
                    onNose(cursorStep.lockedScreen)
                    prevNoseY = Float.NaN
                    return@ExperimentHandMotionCamera
                }
                if (cursorStep.lockedScreen == null) {
                    cursorStep.lockedScreen = screenNoseV2
                }
                if (!v2State.cursorInitialized) {
                    cursorStep.lockedScreen = screenNoseV2
                    v2State.lastNoseScreen = screenNoseV2
                    v2State.smoothVelX = 0f
                    v2State.smoothVelY = 0f
                    v2State.cursorInitialized = true
                    v2State.boost = 1f
                    v2State.alignAnchor = null
                    v2State.alignSinceMs = 0L
                    v2State.prevMouthOpen = false
                    onNose(cursorStep.lockedScreen)
                    prevNoseY = Float.NaN
                    if (showVelocityVector) onVelVec?.invoke(null, null)
                    return@ExperimentHandMotionCamera
                }

                val prevS = v2State.lastNoseScreen
                if (prevS != null) {
                    val rawX = screenNoseV2.x - prevS.x
                    val rawY = screenNoseV2.y - prevS.y
                    v2State.smoothVelX = v2State.smoothVelX * 0.82f + rawX * 0.18f
                    v2State.smoothVelY = v2State.smoothVelY * 0.82f + rawY * 0.18f
                }
                v2State.lastNoseScreen = screenNoseV2

                val vx = v2State.smoothVelX
                val vy = v2State.smoothVelY
                val spd = hypot(vx.toDouble(), vy.toDouble()).toFloat()
                val nowMsV2 = SystemClock.uptimeMillis()
                if (spd < MIMIC_V2_BOOST_MIN_SPEED) {
                    v2State.boost = (v2State.boost * MIMIC_V2_BOOST_DECAY_SLOW).coerceAtLeast(1f)
                    v2State.alignAnchor = null
                    v2State.alignSinceMs = 0L
                } else {
                    val ux = vx / spd
                    val uy = vy / spd
                    val anchor = v2State.alignAnchor
                    if (anchor == null) {
                        v2State.alignAnchor = Offset(ux, uy)
                        v2State.alignSinceMs = nowMsV2
                    } else {
                        val dot = anchor.x * ux + anchor.y * uy
                        if (dot < MIMIC_V2_BOOST_MIN_COS) {
                            v2State.alignAnchor = Offset(ux, uy)
                            v2State.alignSinceMs = nowMsV2
                            v2State.boost = (v2State.boost * MIMIC_V2_BOOST_DECAY_ON_TURN).coerceAtLeast(1f)
                        } else {
                            val held = nowMsV2 - v2State.alignSinceMs
                            if (held >= MIMIC_V2_BOOST_HOLD_MS) {
                                v2State.boost =
                                    (v2State.boost + MIMIC_V2_BOOST_RAMP_PER_FRAME).coerceAtMost(MIMIC_V2_BOOST_MAX)
                            }
                        }
                    }
                }

                val curV2 = cursorStep.lockedScreen
                if (curV2 != null) {
                    cursorStep.lockedScreen = Offset(
                        curV2.x + vx * MIMIC_V2_CURSOR_GAIN * v2State.boost,
                        curV2.y + vy * MIMIC_V2_CURSOR_GAIN * v2State.boost,
                    )
                }

                if (openMouth && !v2State.prevMouthOpen) {
                    val tip = cursorStep.lockedScreen
                    if (tip != null) {
                        v2State.pointerDownMs = dispatchSyntheticDragDownFromScreen(appContext, tip.x, tip.y)
                        v2State.lastEmittedMove = tip
                        onPressChange?.invoke(v2State.pointerDownMs != 0L)
                    }
                }
                if (openMouth && v2State.pointerDownMs != 0L) {
                    val tip = cursorStep.lockedScreen
                    val prevM = v2State.lastEmittedMove
                    if (tip != null &&
                        (prevM == null ||
                            hypot(tip.x - prevM.x, tip.y - prevM.y) >= MimicSyntheticMoveEpsilonPx)
                    ) {
                        dispatchSyntheticDragMoveFromScreen(
                            appContext,
                            tip.x,
                            tip.y,
                            v2State.pointerDownMs,
                        )
                        v2State.lastEmittedMove = tip
                    }
                }
                if (!openMouth && v2State.prevMouthOpen) {
                    if (v2State.pointerDownMs != 0L) {
                        val tip = cursorStep.lockedScreen
                        if (tip != null) {
                            dispatchSyntheticDragUpFromScreen(
                                appContext,
                                tip.x,
                                tip.y,
                                v2State.pointerDownMs,
                            )
                        }
                        v2State.pointerDownMs = 0L
                        onPressChange?.invoke(false)
                    }
                    v2State.lastEmittedMove = null
                }
                v2State.prevMouthOpen = openMouth

                if (showVelocityVector) {
                    val curDraw = cursorStep.lockedScreen
                    val cb = onVelVec
                    if (cb != null && curDraw != null && spd > MIMIC_V2_VEL_ARROW_MIN_SPD) {
                        val len = min(120f, 40f + spd * 3f)
                        val uxv = vx / spd
                        val uyv = vy / spd
                        cb(curDraw, Offset(curDraw.x + uxv * len, curDraw.y + uyv * len))
                    } else {
                        cb?.invoke(null, null)
                    }
                }

                onNose(cursorStep.lockedScreen)

                val nyV2 = nose.y
                if (openMouth) {
                    if (!prevNoseY.isNaN()) {
                        val rawY = (nyV2 - prevNoseY) * MimicNoseScrollSensitivity
                        if (abs(rawY) > 0.45f) {
                            onScrollY(-rawY)
                        }
                    }
                    prevNoseY = nyV2
                } else {
                    prevNoseY = Float.NaN
                }
                return@ExperimentHandMotionCamera
            }

            // Двойное открытие рта: переключить синтетическое нажатие (DOWN / UP).
            val nowOpenEdge = SystemClock.uptimeMillis()
            if (openMouth && !tapEdge.prevOpenMouth) {
                if (nowOpenEdge >= mouthDrag.ignoreOpenMouthRiseUntilMs) {
                    if (mouthDrag.lastOpenMouthRiseMs != 0L &&
                        nowOpenEdge - mouthDrag.lastOpenMouthRiseMs <= MimicDoubleOpenMouthWindowMs
                    ) {
                        val tip = cursorStep.lockedScreen
                        if (mouthDrag.pointerPressed) {
                            releaseSyntheticPointer()
                        } else if (tip != null) {
                            val dt = dispatchSyntheticDragDownFromScreen(appContext, tip.x, tip.y)
                            mouthDrag.downTime = dt
                            mouthDrag.pointerPressed = dt != 0L
                            mouthDrag.lastEmittedMove = tip
                            onPressChange?.invoke(mouthDrag.pointerPressed)
                        }
                        mouthDrag.lastOpenMouthRiseMs = 0L
                        mouthDrag.ignoreOpenMouthRiseUntilMs = nowOpenEdge + MimicDoubleOpenMouthDebounceMs
                    } else {
                        mouthDrag.lastOpenMouthRiseMs = nowOpenEdge
                    }
                }
            }

            // Пока «нажато» и рот открыт — сдвиг курсора поворотом головы (Y / X).
            if (mouthDrag.pointerPressed && openMouth && yaw != null) {
                val cur = cursorStep.lockedScreen
                if (cur != null && tapEdge.prevOpenMouth) {
                    val dyaw = if (!mouthDrag.lastYaw.isNaN()) yaw - mouthDrag.lastYaw else 0f
                    val dpitch = if (pitchEuler != null && !mouthDrag.lastPitch.isNaN()) {
                        pitchEuler - mouthDrag.lastPitch
                    } else {
                        0f
                    }
                    if (dyaw != 0f || dpitch != 0f) {
                        cursorStep.lockedScreen = Offset(
                            cur.x + dyaw * MimicOpenMouthYawPxPerDeg,
                            cur.y - dpitch * MimicOpenMouthPitchPxPerDeg,
                        )
                    }
                }
                mouthDrag.lastYaw = yaw
                if (pitchEuler != null) {
                    mouthDrag.lastPitch = pitchEuler
                }
            }
            if (!openMouth) {
                mouthDrag.lastYaw = Float.NaN
                mouthDrag.lastPitch = Float.NaN
            }
            tapEdge.prevOpenMouth = openMouth

            val pvForCursor = previewAnchor
            val screenNose = if (pvForCursor != null) {
                val sl = IntArray(2)
                pvForCursor.getLocationOnScreen(sl)
                Offset(sl[0] + nose.x, sl[1] + nose.y)
            } else {
                null
            }

            if (cursorStep.lockedScreen == null && screenNose != null) {
                cursorStep.lockedScreen = screenNose
            }

            // Два фронта «брови подняты» подряд — одиночный тап (не во время «зажатого» указателя).
            val nowBrowEdge = SystemClock.uptimeMillis()
            if (!mouthDrag.pointerPressed && browsRaised && !browEdge.prevRaised) {
                if (nowBrowEdge >= browClick.ignoreRiseUntilMs) {
                    if (browClick.lastRiseMs != 0L &&
                        nowBrowEdge - browClick.lastRiseMs <= MimicDoubleBrowTapWindowMs
                    ) {
                        cursorStep.lockedScreen?.let { tipClick ->
                            dispatchSyntheticTapFromScreen(appContext, tipClick.x, tipClick.y)
                        }
                        browClick.lastRiseMs = 0L
                        browClick.ignoreRiseUntilMs = nowBrowEdge + MimicDoubleBrowTapDebounceMs
                    } else {
                        browClick.lastRiseMs = nowBrowEdge
                    }
                }
            }
            browEdge.prevRaised = browsRaised

            if (smiling) {
                if (!cursorStep.wasSmiling) {
                    cursorStep.prevSmileAccumNose = null
                    cursorStep.cursorVelX = 0f
                    cursorStep.cursorVelY = 0f
                    cursorStep.velEmaX = 0f
                    cursorStep.velEmaY = 0f
                    cursorStep.driveRamp = MimicDriveRampBase
                }
                cursorStep.wasSmiling = true

                if (screenNose != null && !openMouth) {
                    val prev = cursorStep.prevSmileAccumNose
                    var dvx = 0f
                    var dvy = 0f
                    if (prev != null) {
                        dvx = screenNose.x - prev.x
                        dvy = screenNose.y - prev.y
                    }
                    val speed = hypot(dvx, dvy)
                    val a = MimicCursorVelEmaAlpha

                    if (speed > MimicNoseSpeedMovingThresholdPx) {
                        cursorStep.velEmaX = cursorStep.velEmaX * (1f - a) + dvx * a
                        cursorStep.velEmaY = cursorStep.velEmaY * (1f - a) + dvy * a
                        cursorStep.driveRamp =
                            (cursorStep.driveRamp + MimicDriveRampUpPerFrame).coerceAtMost(MimicDriveRampMax)
                    } else {
                        cursorStep.velEmaX *= MimicCursorEmaDecayWhileSmilingSlow
                        cursorStep.velEmaY *= MimicCursorEmaDecayWhileSmilingSlow
                        if (hypot(cursorStep.velEmaX, cursorStep.velEmaY) < 0.03f) {
                            cursorStep.velEmaX = 0f
                            cursorStep.velEmaY = 0f
                        }
                        cursorStep.driveRamp =
                            (cursorStep.driveRamp - MimicDriveRampDownWhenNoseSlow)
                                .coerceAtLeast(MimicDriveRampBase)
                    }

                    val ramp = cursorStep.driveRamp
                    var targetVx = cursorStep.velEmaX * MimicCursorVelGain * ramp
                    var targetVy = cursorStep.velEmaY * MimicCursorVelGain * ramp
                    val cap = MimicCursorMaxSpeedPxPerFrame * ramp
                    val tMag = hypot(targetVx, targetVy)
                    if (tMag > cap) {
                        targetVx *= cap / tMag
                        targetVy *= cap / tMag
                    }

                    val beta = MimicCursorAccelTowardTarget
                    cursorStep.cursorVelX += (targetVx - cursorStep.cursorVelX) * beta
                    cursorStep.cursorVelY += (targetVy - cursorStep.cursorVelY) * beta

                    val cur = cursorStep.lockedScreen
                    if (cur != null) {
                        cursorStep.lockedScreen = Offset(
                            cur.x + cursorStep.cursorVelX,
                            cur.y + cursorStep.cursorVelY,
                        )
                    }
                    cursorStep.prevSmileAccumNose = screenNose

                    if (showVelocityVector) {
                        val cb = onVelVec
                        val curDraw = cursorStep.lockedScreen
                        if (cb != null && curDraw != null) {
                            val em = hypot(cursorStep.velEmaX, cursorStep.velEmaY)
                            val vm = hypot(cursorStep.cursorVelX, cursorStep.cursorVelY)
                            if (em > 0.12f) {
                                val rampVis = cursorStep.driveRamp.coerceIn(1f, MimicDriveRampMax)
                                val len = ((MimicVectorArrowBaseLength + em * MimicVectorArrowGain) * (0.88f + 0.12f * rampVis))
                                    .coerceIn(28f, MimicVectorArrowMaxLength)
                                val ux = cursorStep.velEmaX / em
                                val uy = cursorStep.velEmaY / em
                                cb(curDraw, Offset(curDraw.x + ux * len, curDraw.y + uy * len))
                            } else if (vm > 0.07f) {
                                val len = MimicVectorArrowBaseLength * 0.85f
                                val ux = cursorStep.cursorVelX / vm
                                val uy = cursorStep.cursorVelY / vm
                                cb(curDraw, Offset(curDraw.x + ux * len, curDraw.y + uy * len))
                            } else {
                                cb(null, null)
                            }
                        }
                    }
                } else if (screenNose != null && openMouth && showVelocityVector) {
                    onVelVec?.invoke(null, null)
                }
            } else {
                cursorStep.wasSmiling = false
                cursorStep.prevSmileAccumNose = null
                cursorStep.cursorVelX = 0f
                cursorStep.cursorVelY = 0f
                cursorStep.velEmaX = 0f
                cursorStep.velEmaY = 0f
                cursorStep.driveRamp = MimicDriveRampBase
                if (showVelocityVector) onVelVec?.invoke(null, null)
            }

            if (mouthDrag.pointerPressed && mouthDrag.downTime != 0L) {
                val tip = cursorStep.lockedScreen
                val prevM = mouthDrag.lastEmittedMove
                if (tip != null &&
                    (prevM == null ||
                        hypot(tip.x - prevM.x, tip.y - prevM.y) >= MimicSyntheticMoveEpsilonPx)
                ) {
                    dispatchSyntheticDragMoveFromScreen(
                        appContext,
                        tip.x,
                        tip.y,
                        mouthDrag.downTime,
                    )
                    mouthDrag.lastEmittedMove = tip
                }
            }

            onNose(cursorStep.lockedScreen)

            val ny = nose.y
            if (smiling && openMouth) {
                if (!prevNoseY.isNaN()) {
                    val rawY = (ny - prevNoseY) * MimicNoseScrollSensitivity
                    if (abs(rawY) > 0.45f) {
                        onScrollY(-rawY)
                    }
                }
                prevNoseY = ny
            } else {
                prevNoseY = Float.NaN
            }
        }
    }
}

/** Контуры лица в координатах превью (как в эксперименте с рисованием); для мимики и «Камера 4». */
@Composable
internal fun MimicFacePreviewOverlay(
    frame: ExperimentHandMotionFrame?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    val faceOutlineColor = colorScheme.tertiary
    val faceOutlineStrokePx = with(density) { 2.5.dp.toPx() }
    val mouthStrokePx = with(density) { 2.5.dp.toPx() }
    val smileArcColor = Color(0xFFFFEB3B)
    val openMouthIndicatorColor = Color(0xFFE53935)
    val eyebrowGray = Color(0xFF9E9E9E)

    Canvas(modifier = modifier) {
        val mf = frame ?: return@Canvas
        val noseFillColor = noseTriangleFillFromMimic(mf.mimicSignals)
        val mesh = mf.camera5StyleMesh
        val eulerZ = mf.headEulerZDeg
        val pivotFace = mf.faceBoundsInView?.let { Offset(it.centerX(), it.centerY()) }
            ?: mesh?.landmarks?.get(FaceLandmark.NOSE_BASE)
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

        fun drawCamera5Mesh() {
            drawExperimentFaceCamera5StyleMesh(mesh!!, colorScheme)
        }

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
                        val pts = mo.smileArcPointsInView!!
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
                        val er = mo.ellipseInView!!
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
                        val er = mo.ellipseInView!!
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
                        drawPath(nPath, color = noseFillColor, style = Fill)
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

        when {
            mesh != null -> {
                if (pivotFace != null && eulerZ != null && abs(eulerZ) > 0.05f) {
                    rotate(degrees = eulerZ, pivot = pivotFace) { drawCamera5Mesh() }
                } else {
                    drawCamera5Mesh()
                }
            }
            else -> {
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

/**
 * Курсор управления мимикой: красный центр; полупрозрачный ореол — только когда [showPointerHalo]
 * (синтетическое «нажатие» после двойного открытия рта).
 * При изменении [clickPulseKey] — короткая анимация: увеличение и падение непрозрачности, затем как было.
 */
@Composable
fun MimicControlCursor(
    noseCenter: Offset?,
    modifier: Modifier = Modifier,
    /** Ореол при активном синтетическом нажатии (двойное открытие рта). */
    showPointerHalo: Boolean = false,
    /** Счётчик импульса (например [MimicCursorOverlay.clickPulseState]); 0 = только начальное состояние. */
    clickPulseKey: Int = 0,
    haloRadiusDp: Dp = 30.dp,
    coreRadiusDp: Dp = 9.dp,
) {
    if (noseCenter == null) return
    val density = LocalDensity.current
    val haloPx = with(density) { haloRadiusDp.toPx() }
    val corePx = with(density) { coreRadiusDp.toPx() }
    val halfOffset = if (showPointerHalo) haloPx else corePx
    val boxSize = if (showPointerHalo) haloRadiusDp * 2 else coreRadiusDp * 2

    val scalePulse = remember { Animatable(1f) }
    val alphaPulse = remember { Animatable(1f) }
    LaunchedEffect(clickPulseKey) {
        if (clickPulseKey == 0) return@LaunchedEffect
        coroutineScope {
            launch {
                scalePulse.snapTo(1f)
                scalePulse.animateTo(
                    targetValue = 1.56f,
                    animationSpec = tween(durationMillis = 130, easing = FastOutSlowInEasing),
                )
                scalePulse.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 165, easing = FastOutSlowInEasing),
                )
            }
            launch {
                alphaPulse.snapTo(1f)
                alphaPulse.animateTo(
                    targetValue = 0.22f,
                    animationSpec = tween(durationMillis = 130),
                )
                alphaPulse.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 165),
                )
            }
        }
    }
    val pulseScale = scalePulse.value
    val pulseAlpha = alphaPulse.value

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (noseCenter.x - halfOffset).roundToInt(),
                    (noseCenter.y - halfOffset).roundToInt(),
                )
            }
            .size(boxSize)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                alpha = pulseAlpha
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            if (showPointerHalo) {
                drawCircle(
                    color = Color(0x55E53935),
                    radius = size.minDimension / 2f,
                    center = c,
                )
            }
            val coreOutlinePx = 2.dp.toPx()
            drawCircle(
                color = Color(0xFFE53935),
                radius = corePx,
                center = c,
                style = Fill,
            )
            drawCircle(
                color = Color.Black,
                radius = corePx,
                center = c,
                style = Stroke(width = coreOutlinePx),
            )
        }
    }
}
