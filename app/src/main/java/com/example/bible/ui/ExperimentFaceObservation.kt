package com.example.bible.ui

import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceLandmark

/**
 * Точка лица в координатах анализа (как у ML Kit [InputImage] после поворота).
 * Общая модель для постепенного подключения MediaPipe Face Landmarker и др.
 */
data class ExperimentFacePoint(val x: Float, val y: Float)

/**
 * Наблюдение за лицом, не привязанный к конкретному SDK.
 * Сейчас заполняется из ML Kit [Face]; позже — из MediaPipe и гибридов.
 */
data class ExperimentFaceObservation(
    val boundingBox: Rect,
    val headEulerAngleX: Float?,
    val headEulerAngleY: Float?,
    val headEulerAngleZ: Float?,
    val smilingProbability: Float?,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    /** Ключи — константы [FaceContour], значения — цепочки точек контура. */
    val contours: Map<Int, List<ExperimentFacePoint>>,
    /** Ключи — константы [FaceLandmark]. */
    val landmarks: Map<Int, ExperimentFacePoint>,
)

private val experimentFaceContourTypes: IntArray = intArrayOf(
    FaceContour.FACE,
    FaceContour.LEFT_EYE,
    FaceContour.RIGHT_EYE,
    FaceContour.UPPER_LIP_TOP,
    FaceContour.UPPER_LIP_BOTTOM,
    FaceContour.LOWER_LIP_TOP,
    FaceContour.LOWER_LIP_BOTTOM,
    FaceContour.LEFT_EYEBROW_TOP,
    FaceContour.RIGHT_EYEBROW_TOP,
    FaceContour.LEFT_EYEBROW_BOTTOM,
    FaceContour.RIGHT_EYEBROW_BOTTOM,
    FaceContour.NOSE_BRIDGE,
)

private val experimentFaceLandmarkTypes: IntArray = intArrayOf(
    FaceLandmark.MOUTH_BOTTOM,
    FaceLandmark.LEFT_EYE,
    FaceLandmark.RIGHT_EYE,
    FaceLandmark.NOSE_BASE,
)

/** Снимок ML Kit [Face] в нейтральную геометрию эксперимента. */
fun Face.toExperimentFaceObservation(): ExperimentFaceObservation {
    val contours = HashMap<Int, List<ExperimentFacePoint>>()
    for (t in experimentFaceContourTypes) {
        val pts = getContour(t)?.points
        if (!pts.isNullOrEmpty()) {
            contours[t] = pts.map { ExperimentFacePoint(it.x, it.y) }
        }
    }
    val landmarks = HashMap<Int, ExperimentFacePoint>()
    for (t in experimentFaceLandmarkTypes) {
        val pos = getLandmark(t)?.position ?: continue
        landmarks[t] = ExperimentFacePoint(pos.x, pos.y)
    }
    return ExperimentFaceObservation(
        boundingBox = Rect(boundingBox),
        headEulerAngleX = headEulerAngleX,
        headEulerAngleY = headEulerAngleY,
        headEulerAngleZ = headEulerAngleZ,
        smilingProbability = smilingProbability,
        leftEyeOpenProbability = leftEyeOpenProbability,
        rightEyeOpenProbability = rightEyeOpenProbability,
        contours = contours,
        landmarks = landmarks,
    )
}

fun List<Face>.toExperimentFaceObservations(): List<ExperimentFaceObservation> =
    map { it.toExperimentFaceObservation() }

/**
 * Подставляет из [other] только метаданные ML Kit (углы, вероятности), геометрию оставляет из `this`.
 * Нужен для гибрида MediaPipe (контуры) + ML Kit (классификация).
 */
fun ExperimentFaceObservation.mergeClassificationFrom(other: ExperimentFaceObservation?): ExperimentFaceObservation {
    if (other == null) return this
    return copy(
        smilingProbability = other.smilingProbability,
        leftEyeOpenProbability = other.leftEyeOpenProbability,
        rightEyeOpenProbability = other.rightEyeOpenProbability,
        headEulerAngleX = other.headEulerAngleX,
        headEulerAngleY = other.headEulerAngleY,
        headEulerAngleZ = other.headEulerAngleZ,
    )
}
