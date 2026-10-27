package com.example.bible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.bible.data.MediaHomeSectionOrder

/** Современные рисунки разделов каталога медиа вместо эмодзи. */
@Composable
internal fun MediaHubArt(sectionId: String, modifier: Modifier = Modifier) {
    val ink = Color.White
    Canvas(modifier) {
        when (sectionId) {
            MediaHomeSectionOrder.PICTURES -> drawPictures(ink)
            MediaHomeSectionOrder.VIDEOS -> drawVideo(ink)
            MediaHomeSectionOrder.AUDIOS -> drawAudio(ink)
            MediaHomeSectionOrder.PESNOPENIE -> drawSongs(ink)
            MediaHomeSectionOrder.MUSICIAN -> drawGuitar(ink)
            else -> drawMicroblog(ink)
        }
    }
}

private fun DrawScope.drawMicroblog(ink: Color) {
    val w = size.width
    val h = size.height
    val stroke = (minOf(w, h) * 0.035f).coerceAtLeast(2f)
    drawRoundRect(
        color = ink.copy(alpha = 0.28f),
        topLeft = Offset(w * 0.22f, h * 0.16f),
        size = Size(w * 0.62f, h * 0.58f),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
    )
    drawRoundRect(
        color = ink.copy(alpha = 0.92f),
        topLeft = Offset(w * 0.12f, h * 0.26f),
        size = Size(w * 0.62f, h * 0.58f),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
    )
    val line = Color(0xFF7C3AED)
    repeat(3) { index ->
        drawRoundRect(
            color = line.copy(alpha = 0.85f - index * 0.18f),
            topLeft = Offset(w * 0.22f, h * (0.40f + index * 0.12f)),
            size = Size(w * (0.42f - index * 0.06f), stroke),
            cornerRadius = CornerRadius(stroke, stroke),
        )
    }
    drawCircle(Color(0xFFF97316), radius = w * 0.07f, center = Offset(w * 0.78f, h * 0.30f))
}

private fun DrawScope.drawPictures(ink: Color) {
    val w = size.width
    val h = size.height
    rotate(degrees = -8f, pivot = Offset(w * 0.38f, h * 0.55f)) {
        drawRoundRect(
            color = ink.copy(alpha = 0.35f),
            topLeft = Offset(w * 0.08f, h * 0.22f),
            size = Size(w * 0.48f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
        )
    }
    drawRoundRect(
        color = ink.copy(alpha = 0.95f),
        topLeft = Offset(w * 0.30f, h * 0.18f),
        size = Size(w * 0.56f, h * 0.64f),
        cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
    )
    drawCircle(Color(0xFFFBBF24), radius = w * 0.07f, center = Offset(w * 0.46f, h * 0.36f))
    val mountain = Path().apply {
        moveTo(w * 0.36f, h * 0.68f)
        lineTo(w * 0.52f, h * 0.46f)
        lineTo(w * 0.64f, h * 0.60f)
        lineTo(w * 0.78f, h * 0.42f)
        lineTo(w * 0.80f, h * 0.72f)
        lineTo(w * 0.36f, h * 0.72f)
        close()
    }
    drawPath(mountain, Color(0xFF2563EB).copy(alpha = 0.9f))
}

private fun DrawScope.drawVideo(ink: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = ink.copy(alpha = 0.95f),
        topLeft = Offset(w * 0.12f, h * 0.20f),
        size = Size(w * 0.76f, h * 0.58f),
        cornerRadius = CornerRadius(w * 0.1f, w * 0.1f),
    )
    val play = Path().apply {
        moveTo(w * 0.42f, h * 0.36f)
        lineTo(w * 0.42f, h * 0.62f)
        lineTo(w * 0.64f, h * 0.49f)
        close()
    }
    drawPath(play, Color(0xFFEF4444))
    drawRoundRect(
        color = ink.copy(alpha = 0.85f),
        topLeft = Offset(w * 0.28f, h * 0.84f),
        size = Size(w * 0.44f, h * 0.045f),
        cornerRadius = CornerRadius(h * 0.03f, h * 0.03f),
    )
}

private fun DrawScope.drawAudio(ink: Color) {
    val w = size.width
    val h = size.height
    val stroke = (minOf(w, h) * 0.055f).coerceAtLeast(3f)
    drawArc(
        color = ink,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(w * 0.22f, h * 0.08f),
        size = Size(w * 0.56f, h * 0.62f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    drawCircle(ink, radius = w * 0.11f, center = Offset(w * 0.28f, h * 0.62f))
    drawCircle(Color(0xFF4F46E5), radius = w * 0.045f, center = Offset(w * 0.28f, h * 0.62f))
    drawCircle(ink, radius = w * 0.11f, center = Offset(w * 0.72f, h * 0.62f))
    drawCircle(Color(0xFF4F46E5), radius = w * 0.045f, center = Offset(w * 0.72f, h * 0.62f))
    val bars = listOf(0.28f, 0.48f, 0.36f, 0.58f, 0.32f)
    bars.forEachIndexed { index, height ->
        val left = w * (0.30f + index * 0.09f)
        drawRoundRect(
            color = ink.copy(alpha = 0.9f),
            topLeft = Offset(left, h * (0.90f - height * 0.28f)),
            size = Size(w * 0.045f, h * height * 0.28f),
            cornerRadius = CornerRadius(w * 0.02f, w * 0.02f),
        )
    }
}

private fun DrawScope.drawSongs(ink: Color) {
    val w = size.width
    val h = size.height
    val stroke = (minOf(w, h) * 0.028f).coerceAtLeast(2f)
    repeat(5) { index ->
        val y = h * (0.28f + index * 0.09f)
        drawLine(
            color = ink.copy(alpha = 0.85f),
            start = Offset(w * 0.12f, y),
            end = Offset(w * 0.88f, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
    drawNote(ink, Offset(w * 0.38f, h * 0.46f), w)
    drawNote(ink, Offset(w * 0.62f, h * 0.37f), w)
}

private fun DrawScope.drawNote(ink: Color, head: Offset, width: Float) {
    drawCircle(ink, radius = width * 0.055f, center = head)
    drawLine(
        color = ink,
        start = Offset(head.x + width * 0.05f, head.y),
        end = Offset(head.x + width * 0.05f, head.y - width * 0.22f),
        strokeWidth = width * 0.02f,
        cap = StrokeCap.Round,
    )
    val flag = Path().apply {
        moveTo(head.x + width * 0.05f, head.y - width * 0.22f)
        quadraticBezierTo(
            head.x + width * 0.18f,
            head.y - width * 0.16f,
            head.x + width * 0.12f,
            head.y - width * 0.04f,
        )
    }
    drawPath(flag, ink, style = Stroke(width = width * 0.02f, cap = StrokeCap.Round))
}

private fun DrawScope.drawGuitar(ink: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = ink,
        topLeft = Offset(w * 0.46f, h * 0.06f),
        size = Size(w * 0.08f, h * 0.42f),
        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f),
    )
    drawCircle(ink, radius = w * 0.055f, center = Offset(w * 0.50f, h * 0.08f))
    drawCircle(ink.copy(alpha = 0.95f), radius = w * 0.20f, center = Offset(w * 0.50f, h * 0.58f))
    drawCircle(ink.copy(alpha = 0.95f), radius = w * 0.16f, center = Offset(w * 0.50f, h * 0.74f))
    drawCircle(Color(0xFF047857), radius = w * 0.055f, center = Offset(w * 0.50f, h * 0.64f))
    drawCircle(ink.copy(alpha = 0.9f), radius = w * 0.018f, center = Offset(w * 0.50f, h * 0.64f))
}
