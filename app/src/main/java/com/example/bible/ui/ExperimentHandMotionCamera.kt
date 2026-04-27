package com.example.bible.ui

import android.content.Context
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.BiblePreferences
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Распознанная мимика (для экрана «Камера 4» и отладки). */
data class ExperimentMimicSignals(
    val facePresent: Boolean,
    /** Улыбка по ML Kit (как в оверлее рта). */
    val smile: Boolean,
    /** Сильная / «двойная» улыбка — более высокая вероятность от ML Kit. */
    val strongSmile: Boolean,
    /** Сдвиг кончика носа относительно спокойной базы (в долях ширины/высоты лица). */
    val noseShiftLeft: Boolean,
    val noseShiftRight: Boolean,
    val noseShiftUp: Boolean,
    val noseShiftDown: Boolean,
    val mouthOpen: Boolean,
    /** Рот заметно шире открыт (вторая ступень по зазору губ). */
    val mouthOpenWide: Boolean,
    /** Брови подняты — больше расстояние «бровь — глаз». */
    val eyebrowsRaised: Boolean,
    /** Брови опущены / сведены ближе к глазам. */
    val eyebrowsLowered: Boolean,
    /**
     * Глаз открыт по геометрии контура [FaceContour.LEFT_EYE] / [FaceContour.RIGHT_EYE].
     * `null` — контур недостаточен или зона неоднозначна.
     * Для фронтальной камеры это **анатомически левый / правый** глаз человека (как в ML Kit).
     */
    val leftEyeOpen: Boolean?,
    val rightEyeOpen: Boolean?,
    /**
     * Открытость глаза по контуру (и асимметрии), **до** правки «нет пятна зрачка → считаем закрытым».
     * Нужна, чтобы звук «оба закрыты» не срабатывал при простой потере зрачка в кадре.
     */
    val leftEyeOpenFromGeometry: Boolean?,
    val rightEyeOpenFromGeometry: Boolean?,
    /**
     * Зрачок виден по кадру: тёмная область внутри контура глаза ([PupilPositionDetector]) при открытом веке.
     * При закрытом глазе — `false`. `null` — нет лица или неоднозначно (глаз «неясно», нет пятна).
     */
    val leftPupilVisible: Boolean?,
    val rightPupilVisible: Boolean?,
    /** Губы сложены «трубочкой» (свист / поцелуй) — по узости рта и малому зазору губ в 2D. */
    val lipsPursedTube: Boolean,
    /**
     * Импульс моргания: краткое закрытие глаза и снова открытие ([BlinkSignalTracker]).
     * Анатомически левый/правый как у ML Kit на фронтальной камере.
     */
    val leftEyeBlink: Boolean,
    val rightEyeBlink: Boolean,
    /** Оба глаза моргнули в этом же кадре (синхронное моргание). */
    val bothEyesBlink: Boolean,
)

/** Один кадр: запястья, кулаки, зрачки (контур глаза + тёмное пятно в Y). */
data class ExperimentHandMotionFrame(
    val imageWidth: Int,
    val imageHeight: Int,
    val leftX: Float?,
    val leftY: Float?,
    val rightX: Float?,
    val rightY: Float?,
    val leftFist: Boolean,
    val rightFist: Boolean,
    val leftPupilX: Float?,
    val leftPupilY: Float?,
    val rightPupilX: Float?,
    val rightPupilY: Float?,
    val faceInFrame: Boolean,
    val leftMoving: Boolean,
    val rightMoving: Boolean,
    val wristsInFrame: Boolean,
    /** Светлый прямоугольник в координатах ML Kit — стиратель (или null). */
    val eraserRectInImage: RectF?,
    /** Жест «ОК» (большой и указательный кольцом) — озвучить выделенную цитату. */
    val gestureOk: Boolean,
    /** Открытая ладонь или палец ко рту — остановить озвучку. */
    val gestureStopSpeech: Boolean,
    /** Нормализованная Y запястья (0…1) для прокрутки списка; приоритет правая рука. */
    val wristNormYScroll: Float?,
    /**
     * Квадрат вокруг лица в пикселях [PreviewView] (как у слоя с Canvas поверх превью), или null.
     */
    val faceBoundsInView: RectF?,
    /** Рот: нейтральный — белый овал; улыбка — жёлтая дуга; открыт — красный круг по проёму. */
    val mouthOverlay: ExperimentMouthOverlay?,
    /** Глаза — чёрные эллипсы, нос — коричневый треугольник. */
    val faceDetailOverlay: ExperimentFaceDetailOverlay?,
    /** Сетка лица в стиле «Камера 5» (контуры + плотные точки MediaPipe при включённой геометрии). */
    val camera5StyleMesh: ExperimentFaceCamera5StyleMesh? = null,
    /** Наклон головы в градусах (ML Kit `headEulerAngleZ`); оверлеи лица вращаются вокруг центра рамки на этот угол. */
    val headEulerZDeg: Float?,
    /** Поворот головы влево-вправо в градусах (ML Kit `headEulerAngleY`). */
    val headEulerYDeg: Float?,
    /** Наклон головы вверх-вниз в градусах (ML Kit `headEulerAngleX`). */
    val headEulerXDeg: Float?,
    /** Мимика лица (ML Kit), заполняется в [processVisionFrame]. */
    val mimicSignals: ExperimentMimicSignals? = null,
)

/** Наложение рта в координатах [PreviewView]. */
data class ExperimentMouthOverlay(
    val isSmile: Boolean,
    val ellipseInView: RectF?,
    val smileArcPointsInView: List<Offset>?,
    /**
     * Открытый рот — красный круг по центру [ellipseInView] (тот же прямоугольник, что у белого овала).
     */
    val isOpenMouth: Boolean,
)

/** Глаза, нос и брови в координатах [PreviewView]. */
data class ExperimentFaceDetailOverlay(
    val leftEyeEllipseInView: RectF?,
    val rightEyeEllipseInView: RectF?,
    /** Ровно три вершины треугольника носа. */
    val noseTriangleInView: List<Offset>?,
    /** Верхний контур брови (дуга вверх), слева направо. */
    val leftEyebrowArcInView: List<Offset>?,
    val rightEyebrowArcInView: List<Offset>?,
    /** Оценка положения зрачка в координатах превью ([PupilPositionDetector]). */
    val leftPupilInView: Offset? = null,
    val rightPupilInView: Offset? = null,
)

private class WristMotionTracker(
    private val moveThresholdNorm: Double = 0.032,
) {
    private var lnx = Float.NaN
    private var lny = Float.NaN
    private var rnx = Float.NaN
    private var rny = Float.NaN

    fun feed(
        lx: Float?,
        ly: Float?,
        rx: Float?,
        ry: Float?,
        imgW: Float,
        imgH: Float,
    ): Pair<Boolean, Boolean> {
        var leftPulse = false
        var rightPulse = false
        if (lx != null && ly != null && imgW > 0f && imgH > 0f) {
            val nx = lx / imgW
            val ny = ly / imgH
            if (!lnx.isNaN()) {
                val d = hypot((nx - lnx).toDouble(), (ny - lny).toDouble())
                if (d > moveThresholdNorm) leftPulse = true
            }
            lnx = nx
            lny = ny
        } else {
            lnx = Float.NaN
            lny = Float.NaN
        }
        if (rx != null && ry != null && imgW > 0f && imgH > 0f) {
            val nx = rx / imgW
            val ny = ry / imgH
            if (!rnx.isNaN()) {
                val d = hypot((nx - rnx).toDouble(), (ny - rny).toDouble())
                if (d > moveThresholdNorm) rightPulse = true
            }
            rnx = nx
            rny = ny
        } else {
            rnx = Float.NaN
            rny = Float.NaN
        }
        return leftPulse to rightPulse
    }
}

/**
 * Переводит точку из координат кадра анализа в координаты слоя превью (как [PreviewView] FILL_CENTER).
 * [ix], [iy] — пиксели ML Kit; фронтальная камера зеркалится по X, как в превью.
 */
fun mapImagePointToViewFillCenter(
    ix: Float,
    iy: Float,
    imageWidth: Float,
    imageHeight: Float,
    viewWidth: Float,
    viewHeight: Float,
    mirrorX: Boolean,
): Offset {
    val xSrc = if (mirrorX) imageWidth - ix else ix
    val scale = max(viewWidth / imageWidth, viewHeight / imageHeight)
    val dw = imageWidth * scale
    val dh = imageHeight * scale
    val offX = (viewWidth - dw) * 0.5f
    val offY = (viewHeight - dh) * 0.5f
    return Offset(
        xSrc * scale + offX,
        iy * scale + offY,
    )
}

/** Ось-ориентированный прямоугольник кадра → в координатах превью (с зеркалом по X). */
fun mapImageRectToViewLtrb(
    rect: RectF,
    imageWidth: Float,
    imageHeight: Float,
    viewWidth: Float,
    viewHeight: Float,
    mirrorX: Boolean,
): androidx.compose.ui.geometry.Rect {
    val corners = listOf(
        rect.left to rect.top,
        rect.right to rect.top,
        rect.right to rect.bottom,
        rect.left to rect.bottom,
    )
    val pts = corners.map { (x, y) ->
        mapImagePointToViewFillCenter(x, y, imageWidth, imageHeight, viewWidth, viewHeight, mirrorX)
    }
    val l = pts.minOf { it.x }
    val r = pts.maxOf { it.x }
    val t = pts.minOf { it.y }
    val b = pts.maxOf { it.y }
    val inflate = max(viewWidth, viewHeight) * 0.012f
    return androidx.compose.ui.geometry.Rect(
        (l - inflate).coerceAtLeast(0f),
        (t - inflate).coerceAtLeast(0f),
        (r + inflate).coerceAtMost(viewWidth),
        (b + inflate).coerceAtMost(viewHeight),
    )
}

private fun isOkHand(pose: Pose, leftSide: Boolean, like: Float): Boolean {
    val thumbT = if (leftSide) PoseLandmark.LEFT_THUMB else PoseLandmark.RIGHT_THUMB
    val indexT = if (leftSide) PoseLandmark.LEFT_INDEX else PoseLandmark.RIGHT_INDEX
    val pinkyT = if (leftSide) PoseLandmark.LEFT_PINKY else PoseLandmark.RIGHT_PINKY
    val wristT = if (leftSide) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST
    val elbowT = if (leftSide) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW
    val thumb = pose.getPoseLandmark(thumbT) ?: return false
    val index = pose.getPoseLandmark(indexT) ?: return false
    val pinky = pose.getPoseLandmark(pinkyT) ?: return false
    val wrist = pose.getPoseLandmark(wristT) ?: return false
    val elbow = pose.getPoseLandmark(elbowT) ?: return false
    if (minOf(
            thumb.inFrameLikelihood,
            index.inFrameLikelihood,
            pinky.inFrameLikelihood,
            wrist.inFrameLikelihood,
            elbow.inFrameLikelihood,
        ) < like
    ) {
        return false
    }
    val scale = hypot(elbow.position.x - wrist.position.x, elbow.position.y - wrist.position.y)
        .coerceAtLeast(25f)
    val thumbIndex = hypot(thumb.position.x - index.position.x, thumb.position.y - index.position.y)
    if (thumbIndex > scale * 0.26f) return false
    val pinkyWrist = hypot(pinky.position.x - wrist.position.x, pinky.position.y - wrist.position.y)
    if (pinkyWrist < scale * 0.36f) return false
    return true
}

private fun isOkGesture(pose: Pose, like: Float): Boolean {
    return isOkHand(pose, leftSide = true, like) || isOkHand(pose, leftSide = false, like)
}

private fun isOpenPalmStopGesture(
    pose: Pose,
    leftFist: Boolean,
    rightFist: Boolean,
    like: Float,
): Boolean {
    fun sideOpen(left: Boolean): Boolean {
        val wristT = if (left) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST
        val wristLm = pose.getPoseLandmark(wristT) ?: return false
        if (wristLm.inFrameLikelihood < like) return false
        if (if (left) leftFist else rightFist) return false
        val thumbT = if (left) PoseLandmark.LEFT_THUMB else PoseLandmark.RIGHT_THUMB
        val indexT = if (left) PoseLandmark.LEFT_INDEX else PoseLandmark.RIGHT_INDEX
        val elbowT = if (left) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW
        val thumb = pose.getPoseLandmark(thumbT) ?: return false
        val index = pose.getPoseLandmark(indexT) ?: return false
        val elbow = pose.getPoseLandmark(elbowT) ?: return false
        if (minOf(thumb.inFrameLikelihood, index.inFrameLikelihood, elbow.inFrameLikelihood) < like) {
            return false
        }
        val scale = hypot(elbow.position.x - wristLm.position.x, elbow.position.y - wristLm.position.y)
            .coerceAtLeast(25f)
        val thumbIndex = hypot(thumb.position.x - index.position.x, thumb.position.y - index.position.y)
        return thumbIndex > scale * 0.30f
    }
    return sideOpen(true) || sideOpen(false)
}

private fun isFingerNearMouth(
    pose: Pose,
    face: ExperimentFaceObservation?,
    imgW: Float,
    imgH: Float,
    like: Float,
): Boolean {
    if (face == null) return false
    val mouth = face.landmarks[FaceLandmark.MOUTH_BOTTOM] ?: return false
    val mx = mouth.x
    val my = mouth.y
    val thresh = hypot(imgW, imgH) * 0.055f
    for (left in listOf(true, false)) {
        val indexT = if (left) PoseLandmark.LEFT_INDEX else PoseLandmark.RIGHT_INDEX
        val idxLm = pose.getPoseLandmark(indexT) ?: continue
        if (idxLm.inFrameLikelihood < like) continue
        if (hypot(idxLm.position.x - mx, idxLm.position.y - my) < thresh) return true
    }
    return false
}

private fun isFistClosed(pose: Pose, leftSide: Boolean, like: Float): Boolean {
    val wristT = if (leftSide) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST
    val elbowT = if (leftSide) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW
    val indexT = if (leftSide) PoseLandmark.LEFT_INDEX else PoseLandmark.RIGHT_INDEX
    val w = pose.getPoseLandmark(wristT) ?: return false
    val e = pose.getPoseLandmark(elbowT) ?: return false
    val idx = pose.getPoseLandmark(indexT) ?: return false
    if (w.inFrameLikelihood < like || e.inFrameLikelihood < like || idx.inFrameLikelihood < like) {
        return false
    }
    val forearm = hypot(e.position.x - w.position.x, e.position.y - w.position.y)
    if (forearm < 25f) return false
    val d = hypot(idx.position.x - w.position.x, idx.position.y - w.position.y)
    return d / forearm < 0.48f
}

/** Самое крупное лицо в кадре (стабильнее, чем [List.firstOrNull]). */
private fun largestObservation(observations: List<ExperimentFaceObservation>?): ExperimentFaceObservation? {
    if (observations.isNullOrEmpty()) return null
    return observations.maxByOrNull { f ->
        val b = f.boundingBox
        b.width().toLong() * b.height()
    }
}

/**
 * Прямоугольник вокруг лица в координатах анализа: сначала овал [FaceContour.FACE],
 * иначе [ExperimentFaceObservation.boundingBox]; затем квадрат по центру с небольшим запасом.
 */
private fun faceSquareInImage(observations: List<ExperimentFaceObservation>?, iw: Int, ih: Int): RectF? {
    val face = largestObservation(observations) ?: return null
    val wf = iw.toFloat().coerceAtLeast(1f)
    val hf = ih.toFloat().coerceAtLeast(1f)
    val img = RectF(0f, 0f, wf, hf)

    val pts = face.contours[FaceContour.FACE]
    val baseRect: RectF = if (!pts.isNullOrEmpty()) {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in pts) {
            minX = minOf(minX, p.x)
            minY = minOf(minY, p.y)
            maxX = maxOf(maxX, p.x)
            maxY = maxOf(maxY, p.y)
        }
        val span = maxOf(maxX - minX, maxY - minY)
        val pad = span * 0.07f + 6f
        RectF(
            (minX - pad).coerceIn(0f, wf - 1f),
            (minY - pad).coerceIn(0f, hf - 1f),
            (maxX + pad).coerceIn(0f, wf - 1f),
            (maxY + pad).coerceIn(0f, hf - 1f),
        )
    } else {
        val b = face.boundingBox
        val bw = b.width().toFloat().coerceAtLeast(1f)
        val bh = b.height().toFloat().coerceAtLeast(1f)
        val pad = maxOf(bw, bh) * 0.1f + 4f
        RectF(
            (b.left - pad).toFloat().coerceIn(0f, wf - 1f),
            (b.top - pad).toFloat().coerceIn(0f, hf - 1f),
            (b.right + pad).toFloat().coerceIn(0f, wf - 1f),
            (b.bottom + pad).toFloat().coerceIn(0f, hf - 1f),
        )
    }

       if (baseRect.width() < 4f || baseRect.height() < 4f) return null

    val cx = faceHorizontalCenterForFrame(face, baseRect)
    val cy = faceVerticalCenterForFrame(face, baseRect)
    val side = maxOf(baseRect.width(), baseRect.height()) * 1.05f
    val half = side * 0.5f
    val sq = RectF(cx - half, cy - half, cx + half, cy + half)
    if (!sq.intersect(img)) return null
    return sq
}

/** Центр по X: между глазами, иначе центр прямоугольника. */
private fun faceHorizontalCenterForFrame(face: ExperimentFaceObservation, baseRect: RectF): Float {
    val le = face.landmarks[FaceLandmark.LEFT_EYE]
    val re = face.landmarks[FaceLandmark.RIGHT_EYE]
    if (le != null && re != null) {
        return (le.x + re.x) / 2f
    }
    return baseRect.centerX()
}

/**
 * Центр по Y: между линией глаз и переносицей — овал лица ML Kit обычно ниже «середины лица»
 * (захватывает подбородок/шею), из‑за этого рамка оказывалась слишком низко.
 */
private fun faceVerticalCenterForFrame(face: ExperimentFaceObservation, baseRect: RectF): Float {
    val le = face.landmarks[FaceLandmark.LEFT_EYE]
    val re = face.landmarks[FaceLandmark.RIGHT_EYE]
    val nose = face.landmarks[FaceLandmark.NOSE_BASE]
    if (le != null && re != null) {
        val eyeY = (le.y + re.y) / 2f
        if (nose != null) {
            return eyeY * 0.52f + nose.y * 0.48f
        }
        return eyeY + (baseRect.centerY() - eyeY) * 0.22f
    }
    return baseRect.centerY() - baseRect.height() * 0.09f
}

internal fun displayRotationForCamera(context: Context, previewView: PreviewView): Int {
    previewView.display?.rotation?.let { return it }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display?.rotation ?: Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }
}

private fun buildFrameFromVision(
    pose: Pose?,
    observations: List<ExperimentFaceObservation>?,
    tracker: WristMotionTracker,
    iw: Int,
    ih: Int,
    eraserRectInImage: RectF?,
    pupils: PupilPositions,
): ExperimentHandMotionFrame {
    val w = iw.toFloat().coerceAtLeast(1f)
    val h = ih.toFloat().coerceAtLeast(1f)
    val like = 0.35f
    val faceInFrame = !observations.isNullOrEmpty()

    if (pose == null) {
        tracker.feed(null, null, null, null, w, h)
        val eulerFace = largestObservation(observations)
        return ExperimentHandMotionFrame(
            imageWidth = iw,
            imageHeight = ih,
            leftX = null,
            leftY = null,
            rightX = null,
            rightY = null,
            leftFist = false,
            rightFist = false,
            leftPupilX = pupils.leftX,
            leftPupilY = pupils.leftY,
            rightPupilX = pupils.rightX,
            rightPupilY = pupils.rightY,
            faceInFrame = faceInFrame,
            leftMoving = false,
            rightMoving = false,
            wristsInFrame = false,
            eraserRectInImage = eraserRectInImage,
            gestureOk = false,
            gestureStopSpeech = false,
            wristNormYScroll = null,
            faceBoundsInView = null,
            mouthOverlay = null,
            faceDetailOverlay = null,
            headEulerZDeg = eulerFace?.headEulerAngleZ,
            headEulerYDeg = eulerFace?.headEulerAngleY,
            headEulerXDeg = eulerFace?.headEulerAngleX,
        )
    }

    val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
    val rw = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
    val lx = if (lw != null && lw.inFrameLikelihood >= like) lw.position.x else null
    val ly = if (lw != null && lw.inFrameLikelihood >= like) lw.position.y else null
    val rx = if (rw != null && rw.inFrameLikelihood >= like) rw.position.x else null
    val ry = if (rw != null && rw.inFrameLikelihood >= like) rw.position.y else null
    val wristsVisible = (lx != null && ly != null) || (rx != null && ry != null)
    val (lMove, rMove) = tracker.feed(lx, ly, rx, ry, w, h)

    val leftFist = isFistClosed(pose, leftSide = true, like)
    val rightFist = isFistClosed(pose, leftSide = false, like)
    val primaryFace = largestObservation(observations)
    val gestureOk = isOkGesture(pose, like)
    val gestureStop = isOpenPalmStopGesture(pose, leftFist, rightFist, like) ||
        isFingerNearMouth(pose, primaryFace, w, h, like)
    val wristNormYScroll = when {
        ry != null -> ry / h
        ly != null -> ly / h
        else -> null
    }

    return ExperimentHandMotionFrame(
        imageWidth = iw,
        imageHeight = ih,
        leftX = lx,
        leftY = ly,
        rightX = rx,
        rightY = ry,
        leftFist = leftFist,
        rightFist = rightFist,
        leftPupilX = pupils.leftX,
        leftPupilY = pupils.leftY,
        rightPupilX = pupils.rightX,
        rightPupilY = pupils.rightY,
        faceInFrame = faceInFrame,
        leftMoving = lMove,
        rightMoving = rMove,
        wristsInFrame = wristsVisible,
        eraserRectInImage = eraserRectInImage,
        gestureOk = gestureOk,
        gestureStopSpeech = gestureStop,
        wristNormYScroll = wristNormYScroll,
        faceBoundsInView = null,
        mouthOverlay = null,
        faceDetailOverlay = null,
        headEulerZDeg = primaryFace?.headEulerAngleZ,
        headEulerYDeg = primaryFace?.headEulerAngleY,
        headEulerXDeg = primaryFace?.headEulerAngleX,
    )
}

/**
 * Переводит прямоугольник лица из системы ML Kit ([iw]×[ih]) в координаты [PreviewView].
 * Использует [CoordinateTransform] (тот же viewport, что у превью и анализа); при ошибке —
 * запасной путь с зеркалом по X.
 */
internal fun mapFaceMlKitToPreviewView(
    faceMl: RectF?,
    imageProxy: ImageProxy,
    previewView: PreviewView,
    iw: Int,
    ih: Int,
): RectF? {
    if (faceMl == null || previewView.width <= 0 || previewView.height <= 0) return null
    val vw = previewView.width.toFloat()
    val vh = previewView.height.toFloat()
    runCatching {
        val target = previewView.getOutputTransform() ?: return@runCatching null
        // Координаты ML Kit совпадают с «полным» кадром после поворота; crop = false устраняет сдвиг по Y.
        val factory = ImageProxyTransformFactory().apply {
            setUsingRotationDegrees(true)
            setUsingCropRect(false)
        }
        val source = factory.getOutputTransform(imageProxy)
        val rect = RectF(faceMl)
        CoordinateTransform(source, target).mapRect(rect)
        if (rect.width() > 2f && rect.height() > 2f) return rect
    }
    val r = mapImageRectToViewLtrb(
        faceMl,
        iw.toFloat(),
        ih.toFloat(),
        vw,
        vh,
        mirrorX = true,
    )
    return RectF(r.left, r.top, r.right, r.bottom)
}

private const val SmileProbabilityThreshold = 0.38f

/** Порог «сильная / двойная улыбка» (ML Kit `smilingProbability`). */
private const val StrongSmileProbabilityThreshold = 0.72f

/**
 * Минимальная «высота» внутреннего проёма рта (нижняя губа ниже верхней внутренней кромки),
 * относительно высоты лица — порог «рот открыт».
 */
private const val OpenMouthGapMinRelFaceH = 0.012f

/** Зазор рта для метки «рот шире открыт» (вторая ступень). */
private const val WideOpenMouthGapRelFaceH = 0.022f

/** Слишком большой зазор — скорее артефакт контура. */
private const val OpenMouthGapMaxRelFaceH = 0.24f

/** Минимальная ширина проёма относительно ширины лица. */
private const val OpenMouthMinWidthRelFaceW = 0.038f

private const val NoseShiftThresholdNorm = 0.048f

/** Быстрое смещение носа между кадрами (нормировка по размеру лица). */
private const val NoseVelocityThresholdNorm = 0.026f

private const val NoseBaselineEmaAlpha = 0.085f

private const val BrowSpreadBaselineEmaAlpha = 0.07f

private const val BrowSpreadRaisedDelta = 0.012f

private const val BrowSpreadLoweredDelta = 0.012f

/** Нейтральное лицо: низкая улыбка и рот не «открыт» по метрике зазора. */
private const val NeutralSmileProbMax = 0.22f

/**
 * Открытый глаз: соотношение высоты/ширины контура и доля высоты контура от высоты лица.
 * Закрытый / прищур — плоский контур (малые значения).
 */
private const val EyeOpenAspectMin = 0.098f

private const val EyeOpenHeightRelFaceMin = 0.0098f

private const val EyeClosedAspectMax = 0.072f

private const val EyeClosedHeightRelFaceMax = 0.0082f

/** Улыбка выше — обычно не «трубочка». */
private const val PursedLipSmileProbMax = 0.33f

/** Сужение рта относительно ширины лица (наружный контур губ). */
private const val PursedLipOuterWidthRelFaceMin = 0.11f

private const val PursedLipOuterWidthRelFaceMax = 0.28f

/** Максимальный внутренний зазор губ (доля высоты лица) — губы почти вместе. */
private const val PursedLipInnerGapRelMax = 0.014f

/** Внешний прямоугольник губ: высота/ширина — при «трубочке» уже, выше. */
private const val PursedLipOuterAspectMin = 0.38f

/** Минимум кадров подряд «глаз закрыт» для засчитывания моргания (отсекаем 1‑кадровый шум). */
private const val BlinkMinClosedFrames = 2

/** Максимум кадров «закрыт»; дольше — не моргание, а прищур / зажмуренный глаз. */
private const val BlinkMaxClosedFrames = 22

/** После зарегистрированного моргания не считать новое N кадров (двойной импульс). */
private const val BlinkRefractoryFrames = 12

/**
 * Детектор одного моргания по последовательности open → closed (коротко) → open.
 */
private class BlinkSignalTracker {
    private var armed = false
    private var closedStreak = 0
    private var refractory = 0

    fun reset() {
        armed = false
        closedStreak = 0
        refractory = 0
    }

    /**
     * @param eyeOpen итоговая открытость из мимики (`null` — не менять счётчики).
     * @return импульс «моргнул» в этом кадре.
     */
    fun feed(eyeOpen: Boolean?): Boolean {
        if (refractory > 0) refractory--
        if (eyeOpen == null) return false
        var pulse = false
        if (eyeOpen) {
            if (armed && refractory == 0 &&
                closedStreak in BlinkMinClosedFrames..BlinkMaxClosedFrames
            ) {
                pulse = true
                refractory = BlinkRefractoryFrames
            }
            armed = true
            closedStreak = 0
        } else {
            if (armed) closedStreak++
        }
        return pulse
    }
}

/**
 * Виден ли зрачок: при закрытом глазе всегда `false`; при открытом — есть ли тёмное пятно в области глаза;
 * при неясной геометрии глаза — `true` только если пятно найдено, иначе `null`.
 */
private fun pupilVisibleFromEyeAndBlob(eyeOpen: Boolean?, pupilBlobFound: Boolean): Boolean? {
    return when (eyeOpen) {
        false -> false
        true -> pupilBlobFound
        null -> if (pupilBlobFound) true else null
    }
}

/**
 * Оценка «открытости» глаза по контуру: `true` / `false` / `null` (нет данных или серая зона).
 */
private fun eyeOpenFromContour(face: ExperimentFaceObservation, leftEye: Boolean): Boolean? {
    val pts = face.contours[if (leftEye) FaceContour.LEFT_EYE else FaceContour.RIGHT_EYE]
        ?: return null
    if (pts.size < 4) return null
    var minX = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (p in pts) {
        minX = minOf(minX, p.x)
        maxX = maxOf(maxX, p.x)
        minY = minOf(minY, p.y)
        maxY = maxOf(maxY, p.y)
    }
    val w = maxX - minX
    val h = maxY - minY
    if (w < 2f || h < 0.4f) return null
    val aspect = h / w
    val fh = max(face.boundingBox.height().toFloat(), 1f)
    val hNorm = h / fh
    val clearlyOpen = aspect >= EyeOpenAspectMin && hNorm >= EyeOpenHeightRelFaceMin
    val clearlyClosed = aspect <= EyeClosedAspectMax && hNorm <= EyeClosedHeightRelFaceMax
    return when {
        clearlyOpen -> true
        clearlyClosed -> false
        aspect >= EyeOpenAspectMin * 0.88f || hNorm >= EyeOpenHeightRelFaceMin * 0.85f -> true
        aspect <= EyeClosedAspectMax * 1.12f || hNorm <= EyeClosedHeightRelFaceMax * 1.15f -> false
        else -> null
    }
}

internal data class MouthGapResult(
    val gapRel: Float,
    val cx: Float,
    val cy: Float,
    val rMl: Float,
)

/**
 * Зазор между губами и геометрия круга-индикатора; null — рот не считаем открытым.
 */
internal fun computeMouthGap(face: ExperimentFaceObservation): MouthGapResult? {
    val box = face.boundingBox
    val faceH = max(box.height().toFloat(), 1f)
    val faceW = max(box.width().toFloat(), 1f)

    val upper = face.contours[FaceContour.UPPER_LIP_BOTTOM]
    val lower = face.contours[FaceContour.LOWER_LIP_TOP]
    if (upper == null || lower == null || upper.size < 2 || lower.size < 2) return null

    val upperMeanY = upper.sumOf { it.y.toDouble() }.toFloat() / upper.size
    val lowerMeanY = lower.sumOf { it.y.toDouble() }.toFloat() / lower.size
    val gapMean = lowerMeanY - upperMeanY

    val upperMaxY = upper.maxOf { it.y }
    val lowerMinY = lower.minOf { it.y }
    val gapEdge = lowerMinY - upperMaxY

    val gap = max(gapMean, gapEdge)
    if (gap <= 0.5f) return null

    val gapRel = gap / faceH
    if (gapRel < OpenMouthGapMinRelFaceH || gapRel > OpenMouthGapMaxRelFaceH) return null

    val minX = min(upper.minOf { it.x }, lower.minOf { it.x })
    val maxX = max(upper.maxOf { it.x }, lower.maxOf { it.x })
    val openW = maxX - minX
    if (openW < faceW * OpenMouthMinWidthRelFaceW) return null

    val cx = (minX + maxX) / 2f
    val cy = (upperMeanY + lowerMeanY) / 2f
    val rMl = max(openW * 0.48f, gap * 0.52f)
        .coerceIn(faceW * 0.024f, faceW * 0.21f)
    return MouthGapResult(gapRel = gapRel, cx = cx, cy = cy, rMl = rMl)
}

/** Нормированное расстояние «низ брови — верх глаза» (среднее по двум сторонам). */
private fun eyebrowEyeSpreadNorm(face: ExperimentFaceObservation): Float? {
    val box = face.boundingBox
    val fh = max(box.height().toFloat(), 1f)
    fun side(left: Boolean): Float? {
        val eyePts = face.contours[if (left) FaceContour.LEFT_EYE else FaceContour.RIGHT_EYE]
            ?: return null
        val browPts = face.contours[
            if (left) FaceContour.LEFT_EYEBROW_BOTTOM else FaceContour.RIGHT_EYEBROW_BOTTOM,
        ] ?: return null
        if (eyePts.isEmpty() || browPts.isEmpty()) return null
        val eyeTopY = eyePts.minOf { it.y }
        val browLowY = browPts.maxOf { it.y }
        return (eyeTopY - browLowY) / fh
    }
    val l = side(left = true) ?: return null
    val r = side(left = false) ?: return null
    return (l + r) * 0.5f
}

private class MimicSignalTracker {
    private var noseBx = Float.NaN
    private var noseBy = Float.NaN
    private var browSpreadBase = Float.NaN
    private var prevNx = Float.NaN
    private var prevNy = Float.NaN
    private val leftBlinkTracker = BlinkSignalTracker()
    private val rightBlinkTracker = BlinkSignalTracker()

    private fun reset() {
        noseBx = Float.NaN
        noseBy = Float.NaN
        browSpreadBase = Float.NaN
        prevNx = Float.NaN
        prevNy = Float.NaN
        leftBlinkTracker.reset()
        rightBlinkTracker.reset()
    }

    fun feed(face: ExperimentFaceObservation?, pupils: PupilPositions): ExperimentMimicSignals {
        if (face == null) {
            reset()
            return ExperimentMimicSignals(
                facePresent = false,
                smile = false,
                strongSmile = false,
                noseShiftLeft = false,
                noseShiftRight = false,
                noseShiftUp = false,
                noseShiftDown = false,
                mouthOpen = false,
                mouthOpenWide = false,
                eyebrowsRaised = false,
                eyebrowsLowered = false,
                leftEyeOpen = null,
                rightEyeOpen = null,
                leftEyeOpenFromGeometry = null,
                rightEyeOpenFromGeometry = null,
                leftPupilVisible = null,
                rightPupilVisible = null,
                lipsPursedTube = false,
                leftEyeBlink = false,
                rightEyeBlink = false,
                bothEyesBlink = false,
            )
        }

        val smileProb = face.smilingProbability
        val smile = smileProb != null && smileProb >= SmileProbabilityThreshold
        val strongSmile = smileProb != null && smileProb >= StrongSmileProbabilityThreshold
        val gap = computeMouthGap(face)
        val mouthOpen = gap != null
        val mouthWide = gap != null && gap.gapRel >= WideOpenMouthGapRelFaceH

        val neutralForBaseline = (smileProb ?: 0f) < NeutralSmileProbMax && gap == null

        val nose = face.landmarks[FaceLandmark.NOSE_BASE]
        val box = face.boundingBox
        val faceW = max(box.width().toFloat(), 1f)
        val faceH = max(box.height().toFloat(), 1f)
        val fcx = box.centerX().toFloat()
        val fcy = box.centerY().toFloat()

        var noseLeft = false
        var noseRight = false
        var noseUp = false
        var noseDown = false

        if (nose != null) {
            val nx = (nose.x - fcx) / faceW
            val ny = (nose.y - fcy) / faceH
            if (neutralForBaseline) {
                val a = NoseBaselineEmaAlpha
                if (noseBx.isNaN()) {
                    noseBx = nx
                    noseBy = ny
                } else {
                    noseBx += a * (nx - noseBx)
                    noseBy += a * (ny - noseBy)
                }
            }
            if (!noseBx.isNaN()) {
                val dx = nx - noseBx
                val dy = ny - noseBy
                if (dx < -NoseShiftThresholdNorm) noseLeft = true
                if (dx > NoseShiftThresholdNorm) noseRight = true
                if (dy < -NoseShiftThresholdNorm) noseUp = true
                if (dy > NoseShiftThresholdNorm) noseDown = true
            }
            if (!prevNx.isNaN()) {
                val vx = nx - prevNx
                val vy = ny - prevNy
                if (vx < -NoseVelocityThresholdNorm) noseLeft = true
                if (vx > NoseVelocityThresholdNorm) noseRight = true
                if (vy < -NoseVelocityThresholdNorm) noseUp = true
                if (vy > NoseVelocityThresholdNorm) noseDown = true
            }
            prevNx = nx
            prevNy = ny
        } else {
            prevNx = Float.NaN
            prevNy = Float.NaN
        }

        var browRaised = false
        var browLowered = false
        val spread = eyebrowEyeSpreadNorm(face)
        if (spread != null && neutralForBaseline) {
            if (browSpreadBase.isNaN()) {
                browSpreadBase = spread
            } else {
                browSpreadBase += BrowSpreadBaselineEmaAlpha * (spread - browSpreadBase)
            }
        }
        if (spread != null && !browSpreadBase.isNaN()) {
            if (spread > browSpreadBase + BrowSpreadRaisedDelta) browRaised = true
            if (spread < browSpreadBase - BrowSpreadLoweredDelta) browLowered = true
        }

        var leftEyeOpen = eyeOpenFromContour(face, leftEye = true)
        var rightEyeOpen = eyeOpenFromContour(face, leftEye = false)
        if (leftEyeOpen != null && rightEyeOpen != null) {
            val lAsp = eyeContourAspect(face, leftEye = true)
            val rAsp = eyeContourAspect(face, leftEye = false)
            if (lAsp != null && rAsp != null) {
                val asym = abs(lAsp - rAsp)
                if (asym > 0.028f) {
                    if (lAsp < rAsp * 0.78f) leftEyeOpen = false
                    if (rAsp < lAsp * 0.78f) rightEyeOpen = false
                }
            }
        }

        val leftEyeOpenFromGeometry = leftEyeOpen
        val rightEyeOpenFromGeometry = rightEyeOpen

        val leftBlob = pupils.leftX != null && pupils.leftY != null
        val rightBlob = pupils.rightX != null && pupils.rightY != null
        // Контур часто даёт «открыт» без реального зрачка в кадре — без пятна не считаем глаз открытым.
        if (leftEyeOpen == true && !leftBlob) leftEyeOpen = false
        if (rightEyeOpen == true && !rightBlob) rightEyeOpen = false
        if (leftEyeOpen == null && leftBlob) leftEyeOpen = true
        if (rightEyeOpen == null && rightBlob) rightEyeOpen = true

        val lipsTube = lipsPursedTubeLikely(face, mouthGap = gap, smileProb = smileProb)

        val leftPupilVis = pupilVisibleFromEyeAndBlob(leftEyeOpen, leftBlob)
        val rightPupilVis = pupilVisibleFromEyeAndBlob(rightEyeOpen, rightBlob)

        val leftBlink = leftBlinkTracker.feed(leftEyeOpen)
        val rightBlink = rightBlinkTracker.feed(rightEyeOpen)

        return ExperimentMimicSignals(
            facePresent = true,
            smile = smile,
            strongSmile = strongSmile,
            noseShiftLeft = noseLeft,
            noseShiftRight = noseRight,
            noseShiftUp = noseUp,
            noseShiftDown = noseDown,
            mouthOpen = mouthOpen,
            mouthOpenWide = mouthWide,
            eyebrowsRaised = browRaised,
            eyebrowsLowered = browLowered,
            leftEyeOpen = leftEyeOpen,
            rightEyeOpen = rightEyeOpen,
            leftEyeOpenFromGeometry = leftEyeOpenFromGeometry,
            rightEyeOpenFromGeometry = rightEyeOpenFromGeometry,
            leftPupilVisible = leftPupilVis,
            rightPupilVisible = rightPupilVis,
            lipsPursedTube = lipsTube,
            leftEyeBlink = leftBlink,
            rightEyeBlink = rightBlink,
            bothEyesBlink = leftBlink && rightBlink,
        )
    }
}

/** Соотношение высота/ширина контура глаза; null если нет контура. */
private fun eyeContourAspect(face: ExperimentFaceObservation, leftEye: Boolean): Float? {
    val pts = face.contours[if (leftEye) FaceContour.LEFT_EYE else FaceContour.RIGHT_EYE]
        ?: return null
    if (pts.size < 4) return null
    var minX = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (p in pts) {
        minX = minOf(minX, p.x)
        maxX = maxOf(maxX, p.x)
        minY = minOf(minY, p.y)
        maxY = maxOf(maxY, p.y)
    }
    val w = maxX - minX
    val h = maxY - minY
    if (w < 2f) return null
    return h / w
}

private val lipContourTypes = intArrayOf(
    FaceContour.UPPER_LIP_TOP,
    FaceContour.UPPER_LIP_BOTTOM,
    FaceContour.LOWER_LIP_TOP,
    FaceContour.LOWER_LIP_BOTTOM,
)

/**
 * Губы «трубочкой»: рот не считаем открытым ([mouthGap] == null), улыбка умеренная,
 * наружный контур губ уже обычного, внутренний зазор мал, область губ чуть вытянута по вертикали.
 */
private fun lipsPursedTubeLikely(face: ExperimentFaceObservation, mouthGap: MouthGapResult?, smileProb: Float?): Boolean {
    if (mouthGap != null) return false
    val sp = smileProb ?: 0f
    if (sp >= PursedLipSmileProbMax) return false
    val box = face.boundingBox
    val faceW = max(box.width().toFloat(), 1f)
    val faceH = max(box.height().toFloat(), 1f)
    var minX = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var any = false
    for (type in lipContourTypes) {
        face.contours[type]?.forEach { p ->
            any = true
            minX = minOf(minX, p.x)
            maxX = maxOf(maxX, p.x)
            minY = minOf(minY, p.y)
            maxY = maxOf(maxY, p.y)
        }
    }
    if (!any || maxX <= minX + 1f) return false
    val outerW = maxX - minX
    val outerH = maxY - minY
    val wRel = outerW / faceW
    if (wRel < PursedLipOuterWidthRelFaceMin || wRel > PursedLipOuterWidthRelFaceMax) return false
    val aspectOuter = outerH / outerW
    if (aspectOuter < PursedLipOuterAspectMin) return false
    val upper = face.contours[FaceContour.UPPER_LIP_BOTTOM]
    val lower = face.contours[FaceContour.LOWER_LIP_TOP]
    if (upper != null && lower != null && upper.isNotEmpty() && lower.isNotEmpty()) {
        val upperMeanY = upper.sumOf { it.y.toDouble() }.toFloat() / upper.size
        val lowerMeanY = lower.sumOf { it.y.toDouble() }.toFloat() / lower.size
        val innerGap = lowerMeanY - upperMeanY
        if (innerGap > faceH * PursedLipInnerGapRelMax) return false
    }
    return true
}

private fun mouthEllipseBoundingRectMl(face: ExperimentFaceObservation, iw: Int, ih: Int): RectF? {
    val wf = iw.toFloat().coerceAtLeast(1f)
    val hf = ih.toFloat().coerceAtLeast(1f)
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var any = false
    for (type in lipContourTypes) {
        face.contours[type]?.forEach { p ->
            any = true
            minX = minOf(minX, p.x)
            minY = minOf(minY, p.y)
            maxX = maxOf(maxX, p.x)
            maxY = maxOf(maxY, p.y)
        }
    }
    if (!any) {
        val m = face.landmarks[FaceLandmark.MOUTH_BOTTOM] ?: return null
        val rw = wf * 0.065f
        val rh = hf * 0.038f
        return RectF(
            (m.x - rw).coerceIn(0f, wf - 1f),
            (m.y - rh * 1.6f).coerceIn(0f, hf - 1f),
            (m.x + rw).coerceIn(0f, wf - 1f),
            (m.y + rh * 0.6f).coerceIn(0f, hf - 1f),
        )
    }
    val padX = (maxX - minX) * 0.12f + 4f
    val padY = (maxY - minY) * 0.2f + 4f
    return RectF(
        (minX - padX).coerceIn(0f, wf - 1f),
        (minY - padY).coerceIn(0f, hf - 1f),
        (maxX + padX).coerceIn(0f, wf - 1f),
        (maxY + padY).coerceIn(0f, hf - 1f),
    )
}

private fun lowerLipContourPointsMl(face: ExperimentFaceObservation): List<Pair<Float, Float>> {
    val pts = face.contours[FaceContour.LOWER_LIP_BOTTOM]
        ?: face.contours[FaceContour.LOWER_LIP_TOP]
        ?: return emptyList()
    return pts.map { p -> p.x to p.y }.sortedBy { it.first }
}

/**
 * Открытый рот: верхняя внутренняя кромка [UPPER_LIP_BOTTOM] и нижняя [LOWER_LIP_TOP]
 * разведены по Y на заметный зазор относительно высоты лица.
 * Возвращает центр и радиус круга-индикатора в координатах ML Kit.
 */
internal fun detectOpenMouthMl(face: ExperimentFaceObservation): Triple<Float, Float, Float>? {
    val g = computeMouthGap(face) ?: return null
    return Triple(g.cx, g.cy, g.rMl)
}

internal fun mapMlKitPointsToPreviewView(
    points: List<Pair<Float, Float>>,
    imageProxy: ImageProxy,
    previewView: PreviewView,
    iw: Int,
    ih: Int,
): List<Offset> {
    if (points.isEmpty()) return emptyList()
    val vw = previewView.width.toFloat()
    val vh = previewView.height.toFloat()
    runCatching {
        val target = previewView.getOutputTransform() ?: return@runCatching null
        val factory = ImageProxyTransformFactory().apply {
            setUsingRotationDegrees(true)
            setUsingCropRect(false)
        }
        val source = factory.getOutputTransform(imageProxy)
        val transform = CoordinateTransform(source, target)
        val arr = FloatArray(points.size * 2)
        points.forEachIndexed { i, (x, y) ->
            arr[i * 2] = x
            arr[i * 2 + 1] = y
        }
        transform.mapPoints(arr)
        return List(points.size) { i -> Offset(arr[i * 2], arr[i * 2 + 1]) }
    }
    return points.map { (x, y) ->
        mapImagePointToViewFillCenter(
            x,
            y,
            iw.toFloat(),
            ih.toFloat(),
            vw,
            vh,
            mirrorX = true,
        )
    }
}

private fun buildMouthOverlayInView(
    face: ExperimentFaceObservation?,
    imageProxy: ImageProxy,
    previewView: PreviewView,
    iw: Int,
    ih: Int,
): ExperimentMouthOverlay? {
    if (face == null || previewView.width <= 0 || previewView.height <= 0) return null
    val smileProb = face.smilingProbability
    val isSmile = smileProb != null && smileProb >= SmileProbabilityThreshold
    val lipRectMl = mouthEllipseBoundingRectMl(face, iw, ih) ?: return null
    val ellipseInView = mapFaceMlKitToPreviewView(lipRectMl, imageProxy, previewView, iw, ih)
    val lipPts = lowerLipContourPointsMl(face)
    val arcPts = if (lipPts.size >= 3) {
        mapMlKitPointsToPreviewView(lipPts, imageProxy, previewView, iw, ih)
    } else {
        emptyList()
    }
    val isOpen = detectOpenMouthMl(face) != null

    return if (isSmile && arcPts.size >= 3) {
        ExperimentMouthOverlay(
            isSmile = true,
            ellipseInView = null,
            smileArcPointsInView = arcPts,
            isOpenMouth = false,
        )
    } else if (isOpen) {
        ExperimentMouthOverlay(
            isSmile = false,
            ellipseInView = ellipseInView,
            smileArcPointsInView = null,
            isOpenMouth = true,
        )
    } else {
        ExperimentMouthOverlay(
            isSmile = false,
            ellipseInView = ellipseInView,
            smileArcPointsInView = null,
            isOpenMouth = false,
        )
    }
}

private fun eyeEllipseBoundingRectMl(face: ExperimentFaceObservation, contourType: Int, iw: Int, ih: Int): RectF? {
    val wf = iw.toFloat().coerceAtLeast(1f)
    val hf = ih.toFloat().coerceAtLeast(1f)
    val pts = face.contours[contourType] ?: return null
    if (pts.isEmpty()) return null
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (p in pts) {
        minX = minOf(minX, p.x)
        minY = minOf(minY, p.y)
        maxX = maxOf(maxX, p.x)
        maxY = maxOf(maxY, p.y)
    }
    val padX = (maxX - minX) * 0.1f + 3f
    val padY = (maxY - minY) * 0.14f + 3f
    return RectF(
        (minX - padX).coerceIn(0f, wf - 1f),
        (minY - padY).coerceIn(0f, hf - 1f),
        (maxX + padX).coerceIn(0f, wf - 1f),
        (maxY + padY).coerceIn(0f, hf - 1f),
    )
}

private fun eyeEllipseFromLandmarkMl(face: ExperimentFaceObservation, landmarkType: Int, iw: Int, ih: Int): RectF? {
    val p = face.landmarks[landmarkType] ?: return null
    val wf = iw.toFloat().coerceAtLeast(1f)
    val hf = ih.toFloat().coerceAtLeast(1f)
    val rx = max(wf, hf) * 0.032f
    val ry = max(wf, hf) * 0.022f
    return RectF(
        (p.x - rx).coerceIn(0f, wf - 1f),
        (p.y - ry).coerceIn(0f, hf - 1f),
        (p.x + rx).coerceIn(0f, wf - 1f),
        (p.y + ry).coerceIn(0f, hf - 1f),
    )
}

private fun eyeEllipseMl(face: ExperimentFaceObservation, leftEye: Boolean, iw: Int, ih: Int): RectF? {
    val contour = if (leftEye) FaceContour.LEFT_EYE else FaceContour.RIGHT_EYE
    val landmark = if (leftEye) FaceLandmark.LEFT_EYE else FaceLandmark.RIGHT_EYE
    return eyeEllipseBoundingRectMl(face, contour, iw, ih)
        ?: eyeEllipseFromLandmarkMl(face, landmark, iw, ih)
}

private fun noseTrianglePointsMl(face: ExperimentFaceObservation, iw: Int, ih: Int): List<Pair<Float, Float>>? {
    val wf = iw.toFloat().coerceAtLeast(1f)
    val hf = ih.toFloat().coerceAtLeast(1f)
    val bridge = face.contours[FaceContour.NOSE_BRIDGE]
    if (bridge != null && bridge.size >= 3) {
        val top = bridge[0]
        val mid = bridge[bridge.size / 2]
        val bot = bridge[bridge.lastIndex]
        return listOf(top.x to top.y, mid.x to mid.y, bot.x to bot.y)
    }
    val base = face.landmarks[FaceLandmark.NOSE_BASE] ?: return null
    val halfW = wf * 0.03f
    return listOf(
        base.x to (base.y - wf * 0.05f).coerceIn(0f, hf - 1f),
        (base.x - halfW).coerceIn(0f, wf - 1f) to (base.y + halfW * 0.85f).coerceIn(0f, hf - 1f),
        (base.x + halfW).coerceIn(0f, wf - 1f) to (base.y + halfW * 0.85f).coerceIn(0f, hf - 1f),
    )
}

/** Верхняя линия брови (изгиб вверх): точки слева направо. */
private fun eyebrowTopArcPointsMl(face: ExperimentFaceObservation, leftEyebrow: Boolean): List<Pair<Float, Float>> {
    val type = if (leftEyebrow) FaceContour.LEFT_EYEBROW_TOP else FaceContour.RIGHT_EYEBROW_TOP
    val pts = face.contours[type] ?: return emptyList()
    return pts.map { p -> p.x to p.y }.sortedBy { it.first }
}

private fun mapPupilPointToPreviewView(
    x: Float?,
    y: Float?,
    imageProxy: ImageProxy,
    previewView: PreviewView,
    iw: Int,
    ih: Int,
): Offset? {
    if (x == null || y == null) return null
    val pts = mapMlKitPointsToPreviewView(listOf(x to y), imageProxy, previewView, iw, ih)
    return pts.firstOrNull()
}

private fun buildFaceDetailOverlayInView(
    face: ExperimentFaceObservation?,
    imageProxy: ImageProxy,
    previewView: PreviewView,
    iw: Int,
    ih: Int,
    pupils: PupilPositions,
): ExperimentFaceDetailOverlay? {
    if (face == null || previewView.width <= 0 || previewView.height <= 0) return null
    val leMl = eyeEllipseMl(face, leftEye = true, iw, ih)
    val reMl = eyeEllipseMl(face, leftEye = false, iw, ih)
    val leftIn = leMl?.let { mapFaceMlKitToPreviewView(it, imageProxy, previewView, iw, ih) }
    val rightIn = reMl?.let { mapFaceMlKitToPreviewView(it, imageProxy, previewView, iw, ih) }
    val nosePts = noseTrianglePointsMl(face, iw, ih)
    val noseIn = nosePts?.let {
        mapMlKitPointsToPreviewView(it, imageProxy, previewView, iw, ih)
    }?.takeIf { it.size == 3 }
    val leBrowMl = eyebrowTopArcPointsMl(face, leftEyebrow = true)
    val riBrowMl = eyebrowTopArcPointsMl(face, leftEyebrow = false)
    val leBrowV = if (leBrowMl.size >= 2) {
        mapMlKitPointsToPreviewView(leBrowMl, imageProxy, previewView, iw, ih)
    } else {
        null
    }
    val riBrowV = if (riBrowMl.size >= 2) {
        mapMlKitPointsToPreviewView(riBrowMl, imageProxy, previewView, iw, ih)
    } else {
        null
    }
    val lpView = mapPupilPointToPreviewView(pupils.leftX, pupils.leftY, imageProxy, previewView, iw, ih)
    val rpView = mapPupilPointToPreviewView(pupils.rightX, pupils.rightY, imageProxy, previewView, iw, ih)
    if (leftIn == null && rightIn == null && noseIn == null && leBrowV == null && riBrowV == null &&
        lpView == null && rpView == null
    ) {
        return null
    }
    return ExperimentFaceDetailOverlay(
        leftEyeEllipseInView = leftIn,
        rightEyeEllipseInView = rightIn,
        noseTriangleInView = noseIn,
        leftEyebrowArcInView = leBrowV,
        rightEyebrowArcInView = riBrowV,
        leftPupilInView = lpView,
        rightPupilInView = rpView,
    )
}

internal fun buildExperimentFaceCamera5StyleMesh(
    face: ExperimentFaceObservation?,
    imageProxy: ImageProxy,
    previewView: PreviewView,
    iw: Int,
    ih: Int,
    mediaPipeNorms: List<Pair<Float, Float>>?,
): ExperimentFaceCamera5StyleMesh? {
    if (face == null || previewView.width <= 0 || previewView.height <= 0) return null
    val contours = HashMap<Int, List<Offset>>()
    for ((type, pts) in face.contours) {
        if (pts.size < 2) continue
        val pairs = pts.map { p -> p.x to p.y }
        val off = mapMlKitPointsToPreviewView(pairs, imageProxy, previewView, iw, ih)
        if (off.size >= 2) contours[type] = off
    }
    val landmarks = HashMap<Int, Offset>()
    for ((type, pt) in face.landmarks) {
        val o = mapMlKitPointsToPreviewView(listOf(pt.x to pt.y), imageProxy, previewView, iw, ih)
            .firstOrNull() ?: continue
        landmarks[type] = o
    }
    val dense = if (!mediaPipeNorms.isNullOrEmpty()) {
        val mlSpace = mediaPipeNorms.map { (nx, ny) -> nx * iw to ny * ih }
        mapMlKitPointsToPreviewView(mlSpace, imageProxy, previewView, iw, ih)
    } else {
        null
    }
    if (contours.isEmpty() && landmarks.isEmpty() && dense.isNullOrEmpty()) return null
    return ExperimentFaceCamera5StyleMesh(contours, landmarks, dense)
}

@Composable
fun ExperimentHandMotionCamera(
    modifier: Modifier = Modifier,
    onPreviewViewReady: ((PreviewView) -> Unit)? = null,
    onFrame: (ExperimentHandMotionFrame) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember(context) { BiblePreferences(context.applicationContext) }
    val useMediaPipeFaceGeometry by preferences.mimicMediaPipeFaceGeometryEnabled.collectAsStateWithLifecycle(false)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val tracker = remember { WristMotionTracker() }
    val mimicTracker = remember { MimicSignalTracker() }
    val onFrameState = rememberUpdatedState(onFrame)
    val previewReadyState = rememberUpdatedState(onPreviewViewReady)

    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // Не перехватывать касания: сверху могут быть Compose-кнопки; для синтетического тапа тоже лучше.
            isClickable = false
            isFocusable = false
        }
    }

    DisposableEffect(previewView) {
        previewReadyState.value?.invoke(previewView)
        onDispose { }
    }

    DisposableEffect(lifecycleOwner, previewView, useMediaPipeFaceGeometry) {
        val disposed = AtomicBoolean(false)
        val poseDetector: PoseDetector = PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build(),
        )
        val faceDetector: FaceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .enableTracking()
                .build(),
        )
        val mediaPipe = if (useMediaPipeFaceGeometry) {
            MediaPipeFaceLandmarkerHelper.createOrNull(context.applicationContext)
        } else {
            null
        }
        val executor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            if (disposed.get()) return@Runnable
            val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull()
                ?: return@Runnable
            previewView.post {
                if (disposed.get()) return@post
                val attempts = intArrayOf(0)
                fun tryBind() {
                    if (disposed.get()) return
                    val vw = previewView.width
                    val vh = previewView.height
                    if (vw <= 0 || vh <= 0) {
                        if (attempts[0]++ < 60) {
                            previewView.post { tryBind() }
                        }
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
                        processVisionFrame(
                            imageProxy = imageProxy,
                            poseDetector = poseDetector,
                            faceDetector = faceDetector,
                            tracker = tracker,
                            mimicTracker = mimicTracker,
                            mainHandler = mainHandler,
                            previewView = previewView,
                            mediaPipe = mediaPipe,
                            useMediaPipeFaceGeometry = useMediaPipeFaceGeometry,
                            onFrame = { onFrameState.value(it) },
                        )
                    }
                    val viewPort = ViewPort.Builder(Rational(vw, vh), targetRotation)
                        .setLayoutDirection(previewView.layoutDirection)
                        .build()
                    val useCaseGroup = UseCaseGroup.Builder()
                        .setViewPort(viewPort)
                        .addUseCase(preview)
                        .addUseCase(analysis)
                        .build()
                    runCatching {
                        cameraProvider.unbindAll()
                        if (disposed.get()) {
                            cameraProvider.unbindAll()
                            return
                        }
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            useCaseGroup,
                        )
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
            poseDetector.close()
            faceDetector.close()
            mediaPipe?.close()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

/**
 * Результат слияния: наблюдения для логики/контуров и (опционально) все точки MediaPipe в нормализованных координатах.
 */
internal data class FaceGeometryMergeResult(
    val observations: List<ExperimentFaceObservation>?,
    /** Точки [0,1]×[0,1] первого лица; не null только если MediaPipe реально отработал на кадре. */
    val mediaPipeLandmarksNormalized: List<Pair<Float, Float>>?,
)

/**
 * Подмена контуров на MediaPipe при [enabled]; метаданные (улыбка, углы) — из первого лица ML Kit.
 */
internal fun mergeMediaPipeGeometryIfNeeded(
    imageProxy: ImageProxy,
    iw: Int,
    ih: Int,
    mlKitObservations: List<ExperimentFaceObservation>?,
    mediaPipe: MediaPipeFaceLandmarkerHelper?,
    enabled: Boolean,
): FaceGeometryMergeResult {
    if (!enabled || mediaPipe == null) {
        return FaceGeometryMergeResult(mlKitObservations, null)
    }
    val mpResult = mediaPipe.detectFaceLandmarks(imageProxy)
        ?: return FaceGeometryMergeResult(mlKitObservations, null)
    val faceLm = mpResult.faceLandmarks().firstOrNull()
    val normPts = faceLm?.map { it.x() to it.y() }?.takeIf { it.isNotEmpty() }
    val mpObs = mpResult.toExperimentFaceObservation(iw, ih)
    if (mpObs == null) {
        return FaceGeometryMergeResult(mlKitObservations, normPts)
    }
    val primaryMl = largestObservation(mlKitObservations)
    val obs = if (primaryMl != null) {
        listOf(mpObs.mergeClassificationFrom(primaryMl))
    } else {
        listOf(mpObs)
    }
    return FaceGeometryMergeResult(obs, normPts)
}

private fun processVisionFrame(
    imageProxy: ImageProxy,
    poseDetector: PoseDetector,
    faceDetector: FaceDetector,
    tracker: WristMotionTracker,
    mimicTracker: MimicSignalTracker,
    mainHandler: Handler,
    previewView: PreviewView,
    mediaPipe: MediaPipeFaceLandmarkerHelper?,
    useMediaPipeFaceGeometry: Boolean,
    onFrame: (ExperimentHandMotionFrame) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val rotation = imageProxy.imageInfo.rotationDegrees
    val image = InputImage.fromMediaImage(mediaImage, rotation)
    val iw = image.width
    val ih = image.height

    val eraserRect = runCatching {
        LightPaperEraserDetector.detectInMlKitSpace(imageProxy, iw, ih)
    }.getOrNull()

    val poseTask = poseDetector.process(image)
    val faceTask = faceDetector.process(image)
    Tasks.whenAllComplete(poseTask, faceTask).addOnCompleteListener {
        val pose = if (poseTask.isSuccessful) poseTask.result else null
        val faces = if (faceTask.isSuccessful) faceTask.result else null
        val mlKitObs = faces?.map { it.toExperimentFaceObservation() }
        // Зрачки — по буферу YUV ImageProxy. MediaPipe в merge закрывает базовый Image при mpImage.close(),
        // поэтому зрачки считаем до merge, по контуру лица ML Kit (до подмены контуров MediaPipe).
        val pupils = PupilPositionDetector.detectBoth(imageProxy, mlKitObs, iw, ih)
        val mergeResult = mergeMediaPipeGeometryIfNeeded(
            imageProxy = imageProxy,
            iw = iw,
            ih = ih,
            mlKitObservations = mlKitObs,
            mediaPipe = mediaPipe,
            enabled = useMediaPipeFaceGeometry,
        )
        val observations = mergeResult.observations
        val mediaPipeNorms = mergeResult.mediaPipeLandmarksNormalized
        val frame = buildFrameFromVision(pose, observations, tracker, iw, ih, eraserRect, pupils)
        val faceSquareMl = faceSquareInImage(observations, iw, ih)
        mainHandler.post {
            val primary = largestObservation(observations)
            val inView = mapFaceMlKitToPreviewView(faceSquareMl, imageProxy, previewView, iw, ih)
            val mouth = buildMouthOverlayInView(primary, imageProxy, previewView, iw, ih)
            val details = buildFaceDetailOverlayInView(primary, imageProxy, previewView, iw, ih, pupils)
            val mesh = buildExperimentFaceCamera5StyleMesh(
                primary,
                imageProxy,
                previewView,
                iw,
                ih,
                mediaPipeNorms,
            )
            val mimic = mimicTracker.feed(primary, pupils)
            onFrame(
                frame.copy(
                    faceBoundsInView = inView,
                    mouthOverlay = mouth,
                    faceDetailOverlay = details,
                    camera5StyleMesh = mesh,
                    mimicSignals = mimic,
                ),
            )
            imageProxy.close()
        }
    }
}
