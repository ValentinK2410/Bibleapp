package com.example.bible.ui

import android.graphics.Rect
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.atan2
import kotlin.math.max

/**
 * Индексы вершин в топологии Face Landmarker (совместимы с классическим Face Mesh 468+).
 */
private object MpFaceIndices {
    val FACE_OVAL = intArrayOf(
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377,
        152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109,
    )
    val LEFT_EYE = intArrayOf(
        362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398,
    )
    val RIGHT_EYE = intArrayOf(
        33, 7, 163, 144, 145, 153, 154, 155, 133, 246, 161, 160, 159, 158, 157, 173,
    )
    val NOSE_BRIDGE = intArrayOf(168, 6, 197, 195, 5, 4, 1)
    val LEFT_EYEBROW_VERTS = intArrayOf(276, 282, 283, 285, 293, 295, 296, 300, 334, 336)
    val RIGHT_EYEBROW_VERTS = intArrayOf(46, 52, 53, 55, 63, 65, 66, 70, 105, 107)
    val LIP_VERTS = intArrayOf(
        0, 13, 14, 17, 37, 39, 40, 61, 78, 80, 81, 82, 84, 87, 88, 91, 95, 146, 178, 181, 185, 191,
        267, 269, 270, 291, 308, 310, 311, 312, 314, 317, 318, 321, 324, 375, 402, 405, 409, 415,
    )
    const val NOSE_TIP = 1
    const val MOUTH_BOTTOM_CENTER = 17
    const val LEFT_EYE_CENTER = 468
    const val RIGHT_EYE_CENTER = 473
}

private fun NormalizedLandmark.toPx(iw: Int, ih: Int): ExperimentFacePoint =
    ExperimentFacePoint(x() * iw, y() * ih)

private fun List<NormalizedLandmark>.pointOrNull(i: Int, iw: Int, ih: Int): ExperimentFacePoint? {
    if (i !in indices) return null
    return this[i].toPx(iw, ih)
}

private fun List<NormalizedLandmark>.polyline(indices: IntArray, iw: Int, ih: Int): List<ExperimentFacePoint> {
    val out = ArrayList<ExperimentFacePoint>(indices.size)
    for (i in indices) {
        if (i in this.indices) out.add(this[i].toPx(iw, ih))
    }
    return out
}

private fun centroid(pts: List<ExperimentFacePoint>): ExperimentFacePoint {
    var sx = 0f
    var sy = 0f
    for (p in pts) {
        sx += p.x
        sy += p.y
    }
    val n = max(pts.size, 1)
    return ExperimentFacePoint(sx / n, sy / n)
}

private fun sortByPolarAngle(pts: List<ExperimentFacePoint>): List<ExperimentFacePoint> {
    if (pts.size < 2) return pts
    val c = centroid(pts)
    return pts.sortedBy { atan2((it.y - c.y).toDouble(), (it.x - c.x).toDouble()) }
}

private fun splitLipContours(
    lipPx: List<ExperimentFacePoint>,
): Pair<List<ExperimentFacePoint>, List<ExperimentFacePoint>> {
    if (lipPx.size < 4) {
        return Pair(emptyList(), emptyList())
    }
    val sortedY = lipPx.map { it.y }.sorted()
    val medianY = sortedY[sortedY.size / 2]
    val upper = lipPx.filter { it.y <= medianY }.sortedBy { it.x }
    val lower = lipPx.filter { it.y > medianY }.sortedBy { it.x }
    return upper to lower
}

private fun splitEyebrowTopBottom(verts: IntArray, lm: List<NormalizedLandmark>, iw: Int, ih: Int): Pair<List<ExperimentFacePoint>, List<ExperimentFacePoint>> {
    val pts = lm.polyline(verts, iw, ih)
    if (pts.size < 3) {
        return Pair(emptyList<ExperimentFacePoint>(), emptyList())
    }
    val minY = pts.minOf { it.y }
    val maxY = pts.maxOf { it.y }
    val mid = (minY + maxY) * 0.5f
    val top = pts.filter { it.y <= mid }.sortedBy { it.x }
    val bot = pts.filter { it.y > mid }.sortedBy { it.x }
    return top to bot
}

private fun boundsFromPoints(pts: Iterable<ExperimentFacePoint>): Rect {
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
    if (minX > maxX) return Rect(0, 0, 1, 1)
    val pad = maxOf((maxX - minX), (maxY - minY)) * 0.04f + 4f
    val l = (minX - pad).toInt().coerceAtLeast(0)
    val t = (minY - pad).toInt().coerceAtLeast(0)
    val r = (maxX + pad).toInt().coerceAtLeast(l + 1)
    val b = (maxY + pad).toInt().coerceAtLeast(t + 1)
    return Rect(l, t, r, b)
}

/**
 * Первая найденная фасовка → [ExperimentFaceObservation] в пикселях [iw]×[ih] (как у ML Kit [InputImage]).
 */
fun FaceLandmarkerResult.toExperimentFaceObservation(iw: Int, ih: Int): ExperimentFaceObservation? {
    val faceLm = faceLandmarks().firstOrNull() ?: return null
    if (faceLm.isEmpty()) return null

    val lipPx = buildList {
        for (idx in MpFaceIndices.LIP_VERTS) {
            faceLm.pointOrNull(idx, iw, ih)?.let(::add)
        }
    }
    val lipOutline = sortByPolarAngle(lipPx)
    val (upperInner, lowerInner) = splitLipContours(lipPx)
    val upperAll = if (lipOutline.isNotEmpty()) lipOutline else upperInner
    val contours = HashMap<Int, List<ExperimentFacePoint>>()
    contours[FaceContour.FACE] = faceLm.polyline(MpFaceIndices.FACE_OVAL, iw, ih)
    contours[FaceContour.LEFT_EYE] = faceLm.polyline(MpFaceIndices.LEFT_EYE, iw, ih)
    contours[FaceContour.RIGHT_EYE] = faceLm.polyline(MpFaceIndices.RIGHT_EYE, iw, ih)
    contours[FaceContour.NOSE_BRIDGE] = faceLm.polyline(MpFaceIndices.NOSE_BRIDGE, iw, ih)

    val (leTop, leBot) = splitEyebrowTopBottom(MpFaceIndices.LEFT_EYEBROW_VERTS, faceLm, iw, ih)
    val (reTop, reBot) = splitEyebrowTopBottom(MpFaceIndices.RIGHT_EYEBROW_VERTS, faceLm, iw, ih)
    if (leTop.isNotEmpty()) contours[FaceContour.LEFT_EYEBROW_TOP] = leTop
    if (leBot.isNotEmpty()) contours[FaceContour.LEFT_EYEBROW_BOTTOM] = leBot
    if (reTop.isNotEmpty()) contours[FaceContour.RIGHT_EYEBROW_TOP] = reTop
    if (reBot.isNotEmpty()) contours[FaceContour.RIGHT_EYEBROW_BOTTOM] = reBot

    if (upperAll.isNotEmpty()) {
        contours[FaceContour.UPPER_LIP_TOP] = upperAll
        contours[FaceContour.UPPER_LIP_BOTTOM] = if (upperInner.isNotEmpty()) upperInner else upperAll
        contours[FaceContour.LOWER_LIP_TOP] = if (lowerInner.isNotEmpty()) lowerInner else upperAll
        contours[FaceContour.LOWER_LIP_BOTTOM] = upperAll
    }

    val landmarks = HashMap<Int, ExperimentFacePoint>()
    faceLm.pointOrNull(MpFaceIndices.NOSE_TIP, iw, ih)?.let { landmarks[FaceLandmark.NOSE_BASE] = it }
    faceLm.pointOrNull(MpFaceIndices.MOUTH_BOTTOM_CENTER, iw, ih)?.let { landmarks[FaceLandmark.MOUTH_BOTTOM] = it }

    faceLm.pointOrNull(MpFaceIndices.LEFT_EYE_CENTER, iw, ih)?.let { landmarks[FaceLandmark.LEFT_EYE] = it }
    if (FaceLandmark.LEFT_EYE !in landmarks) {
        val c = contours[FaceContour.LEFT_EYE].orEmpty()
        if (c.isNotEmpty()) landmarks[FaceLandmark.LEFT_EYE] = centroid(c)
    }
    faceLm.pointOrNull(MpFaceIndices.RIGHT_EYE_CENTER, iw, ih)?.let { landmarks[FaceLandmark.RIGHT_EYE] = it }
    if (FaceLandmark.RIGHT_EYE !in landmarks) {
        val c = contours[FaceContour.RIGHT_EYE].orEmpty()
        if (c.isNotEmpty()) landmarks[FaceLandmark.RIGHT_EYE] = centroid(c)
    }

    val allForBox = ArrayList<ExperimentFacePoint>()
    contours.values.forEach { allForBox.addAll(it) }
    val box = if (allForBox.isNotEmpty()) boundsFromPoints(allForBox) else Rect(0, 0, iw.coerceAtLeast(1), ih.coerceAtLeast(1))

    return ExperimentFaceObservation(
        boundingBox = box,
        headEulerAngleX = null,
        headEulerAngleY = null,
        headEulerAngleZ = null,
        smilingProbability = null,
        leftEyeOpenProbability = null,
        rightEyeOpenProbability = null,
        contours = contours,
        landmarks = landmarks,
    )
}
