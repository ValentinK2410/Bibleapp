package com.example.bible.ui

import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceLandmark
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/** Координаты зрачков в системе ML Kit (как поза и InputImage). */
data class PupilPositions(
    val leftX: Float?,
    val leftY: Float?,
    val rightX: Float?,
    val rightY: Float?,
)

/**
 * Оценка положения зрачка: контур глаза ML Kit + поиск самой тёмной области в Y (зрачок тёмнее склеры).
 */
object PupilPositionDetector {

    private const val MAX_LUMA_FOR_PUPIL = 118
    private const val PUPIL_BAND = 14

    fun detectBoth(
        imageProxy: ImageProxy,
        observations: List<ExperimentFaceObservation>?,
        mlKitW: Int,
        mlKitH: Int,
    ): PupilPositions {
        if (observations.isNullOrEmpty() || mlKitW < 16 || mlKitH < 16) {
            return PupilPositions(null, null, null, null)
        }
        val face = observations.maxByOrNull { f ->
            val b = f.boundingBox
            b.width().toLong() * b.height()
        } ?: return PupilPositions(null, null, null, null)

        val left = findPupil(imageProxy, face, FaceContour.LEFT_EYE, FaceLandmark.LEFT_EYE, mlKitW, mlKitH)
        val right = findPupil(imageProxy, face, FaceContour.RIGHT_EYE, FaceLandmark.RIGHT_EYE, mlKitW, mlKitH)
        return PupilPositions(left?.first, left?.second, right?.first, right?.second)
    }

    private fun findPupil(
        imageProxy: ImageProxy,
        face: ExperimentFaceObservation,
        contourType: Int,
        landmarkType: Int,
        mlW: Int,
        mlH: Int,
    ): Pair<Float, Float>? {
        val contourPts = face.contours[contourType]
        val landmarkPt = face.landmarks[landmarkType]
        val bbox = eyeBoxMl(contourPts, landmarkPt, mlW, mlH) ?: return null
        return darkestClusterInBox(imageProxy, bbox)
    }

    private fun eyeBoxMl(
        contour: List<ExperimentFacePoint>?,
        landmark: ExperimentFacePoint?,
        mlW: Int,
        mlH: Int,
    ): RectF? {
        val pts = contour
        if (!pts.isNullOrEmpty()) {
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (p in pts) {
                minX = min(minX, p.x)
                minY = min(minY, p.y)
                maxX = max(maxX, p.x)
                maxY = max(maxY, p.y)
            }
            val w = maxX - minX
            val h = maxY - minY
            val padX = max(w * 0.14f, 5f)
            val padY = max(h * 0.22f, 6f)
            val r = RectF(minX - padX, minY - padY, maxX + padX, maxY + padY)
            r.left = r.left.coerceIn(0f, (mlW - 1).toFloat())
            r.top = r.top.coerceIn(0f, (mlH - 1).toFloat())
            r.right = r.right.coerceIn(0f, (mlW - 1).toFloat())
            r.bottom = r.bottom.coerceIn(0f, (mlH - 1).toFloat())
            if (r.width() < 8f || r.height() < 8f) return null
            return r
        }
        val pos = landmark ?: return null
        val rad = max((mlW + mlH) * 0.018f, 14f)
        return RectF(
            (pos.x - rad).coerceIn(0f, (mlW - 1).toFloat()),
            (pos.y - rad).coerceIn(0f, (mlH - 1).toFloat()),
            (pos.x + rad).coerceIn(0f, (mlW - 1).toFloat()),
            (pos.y + rad).coerceIn(0f, (mlH - 1).toFloat()),
        )
    }

    private fun darkestClusterInBox(imageProxy: ImageProxy, bbox: RectF): Pair<Float, Float>? {
        val plane = imageProxy.planes.getOrNull(0) ?: return null
        val buf = plane.buffer.duplicate()
        buf.rewind()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val limit = buf.limit()
        val rot = imageProxy.imageInfo.rotationDegrees
        val pw = imageProxy.width
        val ph = imageProxy.height

        val left = bbox.left.toInt().coerceAtLeast(0)
        val top = bbox.top.toInt().coerceAtLeast(0)
        val right = bbox.right.toInt().coerceAtMost(99999)
        val bottom = bbox.bottom.toInt().coerceAtMost(99999)

        val step = max(1, min(right - left, bottom - top) / 12)
        var minLum = 256
        for (my in top..bottom step step) {
            for (mx in left..right step step) {
                val lum = luminanceMl(mx.toFloat(), my.toFloat(), buf, rowStride, pixelStride, rot, pw, ph, limit)
                if (lum < minLum) minLum = lum
            }
        }
        if (minLum > MAX_LUMA_FOR_PUPIL) return null

        var sumX = 0f
        var sumY = 0f
        var n = 0
        val fine = max(1, step / 2)
        for (my in top..bottom step fine) {
            for (mx in left..right step fine) {
                val lum = luminanceMl(mx.toFloat(), my.toFloat(), buf, rowStride, pixelStride, rot, pw, ph, limit)
                if (lum <= minLum + PUPIL_BAND) {
                    sumX += mx
                    sumY += my
                    n++
                }
            }
        }
        if (n == 0) return null
        return sumX / n to sumY / n
    }

    private fun luminanceMl(
        mx: Float,
        my: Float,
        buf: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        rot: Int,
        pw: Int,
        ph: Int,
        limit: Int,
    ): Int {
        val (bx, by) = mlToBuffer(mx, my, rot, pw, ph)
        val off = by * rowStride + bx * pixelStride
        if (off < 0 || off >= limit) return 255
        return buf.get(off).toInt() and 0xFF
    }

    /** Координаты ML Kit → индексы в Y-плоскости [ImageProxy]. */
    private fun mlToBuffer(mx: Float, my: Float, rot: Int, pw: Int, ph: Int): Pair<Int, Int> {
        return when (rot) {
            0 -> mx.toInt().coerceIn(0, pw - 1) to my.toInt().coerceIn(0, ph - 1)
            90 -> (pw - 1 - my.toInt()).coerceIn(0, pw - 1) to mx.toInt().coerceIn(0, ph - 1)
            180 -> (pw - 1 - mx.toInt()).coerceIn(0, pw - 1) to (ph - 1 - my.toInt()).coerceIn(0, ph - 1)
            270 -> my.toInt().coerceIn(0, pw - 1) to (ph - 1 - mx.toInt()).coerceIn(0, ph - 1)
            else -> mx.toInt().coerceIn(0, pw - 1) to my.toInt().coerceIn(0, ph - 1)
        }
    }
}
