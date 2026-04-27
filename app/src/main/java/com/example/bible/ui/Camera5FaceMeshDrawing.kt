package com.example.bible.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.mlkit.vision.face.FaceContour

/**
 * Контуры и маркеры в координатах [androidx.camera.view.PreviewView] (как на «Камере 5»);
 * [mediaPipeDensePoints] — все точки Face Landmarker в превью, если режим MediaPipe дал сетку.
 */
data class ExperimentFaceCamera5StyleMesh(
    val contours: Map<Int, List<Offset>>,
    val landmarks: Map<Int, Offset>,
    val mediaPipeDensePoints: List<Offset>?,
)

/** «Неон» при плотной сетке MediaPipe (как [ExperimentCamera5MediaPipeScreen]). */
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

private fun strokeForContourMlKit(type: Int, scheme: ColorScheme): Color = when (type) {
    FaceContour.FACE -> scheme.primary.copy(alpha = 0.85f)
    FaceContour.LEFT_EYE, FaceContour.RIGHT_EYE -> Color(0xFFE91E63)
    FaceContour.LEFT_EYEBROW_TOP, FaceContour.LEFT_EYEBROW_BOTTOM,
    FaceContour.RIGHT_EYEBROW_TOP, FaceContour.RIGHT_EYEBROW_BOTTOM,
    -> Color(0xFF9C27B0)
    FaceContour.NOSE_BRIDGE -> Color(0xFF00BCD4)
    FaceContour.UPPER_LIP_TOP, FaceContour.UPPER_LIP_BOTTOM,
    FaceContour.LOWER_LIP_TOP, FaceContour.LOWER_LIP_BOTTOM,
    -> Color(0xFFFF9800)
    else -> scheme.tertiary
}

/** Отрисовка совпадает с Canvas «Камера 5» (без стрелки скорости и курсора). */
fun DrawScope.drawExperimentFaceCamera5StyleMesh(
    mesh: ExperimentFaceCamera5StyleMesh,
    colorScheme: ColorScheme,
) {
    val dense = mesh.mediaPipeDensePoints
    val mpActive = (dense?.size ?: 0) >= 80
    fun strokeForType(type: Int): Color =
        if (mpActive) camera5StrokeForContourMediaPipe(type) else strokeForContourMlKit(type, colorScheme)
    val stateContours = mesh.contours
    val drawn = HashSet<Int>()
    for (type in camera5ContourDrawOrder) {
        val pts = stateContours[type] ?: continue
        drawn.add(type)
        if (pts.size < 2) continue
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                lineTo(pts[i].x, pts[i].y)
            }
        }
        val strokeW = if (mpActive) {
            if (type == FaceContour.FACE) 5f else 4f
        } else {
            if (type == FaceContour.FACE) 3.5f else 2.8f
        }
        drawPath(path = path, color = strokeForType(type), style = Stroke(width = strokeW))
        for (p in pts) {
            drawCircle(
                color = strokeForType(type).copy(alpha = 0.9f),
                radius = if (type == FaceContour.FACE) 2f else 2.8f,
                center = p,
            )
        }
    }
    for ((type, pts) in stateContours) {
        if (type in drawn || pts.size < 2) continue
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                lineTo(pts[i].x, pts[i].y)
            }
        }
        drawPath(path, strokeForType(type), style = Stroke(width = if (mpActive) 4f else 2.5f))
        for (p in pts) {
            drawCircle(color = strokeForType(type), radius = 2.6f, center = p)
        }
    }
    for ((_, p) in mesh.landmarks) {
        drawCircle(color = Color.White.copy(alpha = 0.95f), radius = 5f, center = p)
        drawCircle(
            color = Color(0xFF2196F3),
            radius = 5f,
            center = p,
            style = Stroke(width = 2f),
        )
    }
    if (dense != null) {
        val dotFill = Color(0xFFFF1744).copy(alpha = 0.85f)
        val dotRing = Color.White.copy(alpha = 0.75f)
        for (p in dense) {
            drawCircle(color = dotFill, radius = 4.2f, center = p)
            drawCircle(color = dotRing, radius = 4.2f, center = p, style = Stroke(width = 1.2f))
        }
    }
}
