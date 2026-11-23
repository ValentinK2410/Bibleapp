package com.example.bible.ui.mynotes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.bible.data.mynotes.SketchPoint
import com.example.bible.data.mynotes.SketchStroke
import kotlin.math.hypot

@Composable
internal fun SketchSurface(
    strokes: List<SketchStroke>,
    modifier: Modifier = Modifier,
    enabled: Boolean = false,
    inkArgb: Int = SketchInks.first(),
    widthDp: Float = 5.5f,
    eraser: Boolean = false,
    onCommit: (SketchStroke) -> Unit = {},
) {
    val dark = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f
    val paper = paperColor(dark)
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var live by remember { mutableStateOf<SketchStroke?>(null) }
    var liveTick by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, inkColor(0x22000000), shape)
            .onSizeChanged { canvasSize = it }
            .pointerInput(enabled, inkArgb, widthDp, eraser, canvasSize) {
                if (!enabled || canvasSize.width == 0 || canvasSize.height == 0) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val stroke = SketchStroke(
                            argb = inkArgb,
                            width = if (eraser) widthDp.coerceAtLeast(14f) else widthDp,
                            eraser = eraser,
                            points = listOf(offset.toPoint(canvasSize)),
                        )
                        live = stroke
                        liveTick++
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val current = live ?: return@detectDragGestures
                        val point = change.position.toPoint(canvasSize)
                        val last = current.points.lastOrNull()
                        if (last == null || point.distanceTo(last) >= 0.004f) {
                            live = current.copy(points = current.points + point)
                            liveTick++
                        }
                    },
                    onDragEnd = {
                        val done = live
                        live = null
                        liveTick++
                        if (done != null && done.points.isNotEmpty()) onCommit(done)
                    },
                    onDragCancel = {
                        live = null
                        liveTick++
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            liveTick
            drawRect(paper)
            val all = if (live == null) strokes else strokes + live!!
            all.forEach { stroke ->
                val widthPx = with(density) { stroke.width.dp.toPx() }
                val color = if (stroke.eraser) paper else inkColor(stroke.argb)
                val mapped = stroke.points.map { point ->
                    Offset(point.x * size.width, point.y * size.height)
                }
                if (mapped.size <= 1) {
                    val center = mapped.firstOrNull() ?: return@forEach
                    drawCircle(color = color, radius = widthPx / 2f, center = center)
                } else {
                    val path = Path().apply {
                        moveTo(mapped.first().x, mapped.first().y)
                        mapped.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}

private fun Offset.toPoint(size: IntSize): SketchPoint {
    val x = if (size.width == 0) 0f else (x / size.width).coerceIn(0f, 1f)
    val y = if (size.height == 0) 0f else (y / size.height).coerceIn(0f, 1f)
    return SketchPoint(x, y)
}

private fun SketchPoint.distanceTo(other: SketchPoint): Float =
    hypot(x - other.x, y - other.y)
