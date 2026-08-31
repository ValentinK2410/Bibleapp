package com.example.bible.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.bible.data.MicroblogImageOps
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class CropAspect(val label: String, val ratio: Float?) {
    FREE("Свободно", null),
    SQUARE("1:1", 1f),
    R45("4:5", 4f / 5f),
    R43("4:3", 4f / 3f),
    R32("3:2", 3f / 2f),
    R169("16:9", 16f / 9f),
}

private enum class CropHandle {
    None, Move, L, R, T, B, TL, TR, BL, BR
}

/** Геометрия вписанного в экран изображения: рамка задаётся в долях [0..1] от исходного файла. */
private data class CropLayout(
    val imageRect: Rect,
    val srcWidth: Int,
    val srcHeight: Int,
) {
    fun toView(norm: Rect): Rect = Rect(
        imageRect.left + norm.left * imageRect.width,
        imageRect.top + norm.top * imageRect.height,
        imageRect.left + norm.right * imageRect.width,
        imageRect.top + norm.bottom * imageRect.height,
    )

    fun toNorm(view: Rect): Rect {
        if (imageRect.width <= 0f || imageRect.height <= 0f) return Rect(0f, 0f, 1f, 1f)
        return Rect(
            ((view.left - imageRect.left) / imageRect.width).coerceIn(0f, 1f),
            ((view.top - imageRect.top) / imageRect.height).coerceIn(0f, 1f),
            ((view.right - imageRect.left) / imageRect.width).coerceIn(0f, 1f),
            ((view.bottom - imageRect.top) / imageRect.height).coerceIn(0f, 1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicroblogImageCropScreen(
    file: File,
    onCancel: () -> Unit,
    onApply: (left: Float, top: Float, right: Float, bottom: Float, outputScale: Float) -> Unit,
) {
    val bitmap = remember(file.absolutePath, file.length()) {
        MicroblogImageOps.loadBitmap(file)
    }
    if (bitmap == null) {
        LaunchedEffect(file.absolutePath) { onCancel() }
        return
    }
    var aspect by remember { mutableStateOf(CropAspect.FREE) }
    var crop by remember(bitmap.width, bitmap.height) { mutableStateOf(FullCrop) }
    var outputScale by remember { mutableFloatStateOf(1f) }
    BackHandler(onBack = onCancel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Кадр и размер") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        aspect = CropAspect.FREE
                        crop = FullCrop
                        outputScale = 1f
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Сбросить")
                    }
                    IconButton(onClick = {
                        onApply(crop.left, crop.top, crop.right, crop.bottom, outputScale)
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Применить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF101214))
                    .padding(10.dp),
            ) {
                CropCanvas(
                    bitmap = bitmap,
                    crop = crop,
                    aspect = aspect.ratio,
                    onCrop = { crop = it },
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CropAspect.entries.forEach { item ->
                        val selected = item == aspect
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable {
                                    aspect = item
                                    val ratio = item.ratio
                                    if (ratio != null) {
                                        crop = centeredAspectCrop(
                                            crop,
                                            bitmap.width,
                                            bitmap.height,
                                            ratio,
                                        )
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
                val outW = max(1, ((crop.width * bitmap.width) * outputScale).roundToInt())
                val outH = max(1, ((crop.height * bitmap.height) * outputScale).roundToInt())
                Text(
                    "Размер файла ${(outputScale * 100).roundToInt()}% — на выходе $outW × $outH px",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = outputScale,
                    onValueChange = { outputScale = it },
                    valueRange = 0.35f..1f,
                )
                Text(
                    "Тяните уголки или края рамки, внутри рамки — перенос кадра целиком.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    crop: Rect,
    aspect: Float?,
    onCrop: (Rect) -> Unit,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val handlePx = with(density) { 30.dp.toPx() }
    val minSizePx = with(density) { 56.dp.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val layout = remember(boxW, boxH, bitmap.width, bitmap.height) {
            val fitted = MicroblogImageOps.fitRect(
                boxW,
                boxH,
                bitmap.width.toFloat(),
                bitmap.height.toFloat(),
            )
            CropLayout(
                imageRect = Rect(fitted[0], fitted[1], fitted[2], fitted[3]),
                srcWidth = bitmap.width,
                srcHeight = bitmap.height,
            )
        }
        // pointerInput не пересоздаётся при изменении рамки: иначе жест обрывался на каждом кадре.
        val cropLatest = rememberUpdatedState(crop)
        val aspectLatest = rememberUpdatedState(aspect)
        val layoutLatest = rememberUpdatedState(layout)
        val onCropLatest = rememberUpdatedState(onCrop)
        var activeHandle by remember { mutableStateOf(CropHandle.None) }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var handle = CropHandle.None
                    var startView = Rect.Zero
                    var total = Offset.Zero
                    detectDragGestures(
                        onDragStart = { position ->
                            startView = layoutLatest.value.toView(cropLatest.value)
                            handle = hitHandle(position, startView, handlePx)
                            total = Offset.Zero
                            activeHandle = handle
                        },
                        onDragEnd = {
                            handle = CropHandle.None
                            activeHandle = CropHandle.None
                        },
                        onDragCancel = {
                            handle = CropHandle.None
                            activeHandle = CropHandle.None
                        },
                        onDrag = { change, amount ->
                            if (handle == CropHandle.None) return@detectDragGestures
                            change.consume()
                            total += amount
                            val info = layoutLatest.value
                            // Рамка считается от снимка на старте жеста, поэтому она не «уезжает».
                            val next = resizeCrop(
                                start = startView,
                                handle = handle,
                                delta = total,
                                bounds = info.imageRect,
                                aspect = aspectLatest.value,
                                minSize = minSizePx,
                            )
                            onCropLatest.value(info.toNorm(next))
                        },
                    )
                },
        ) {
            val dragging = activeHandle != CropHandle.None
            Canvas(Modifier.fillMaxSize()) {
                val imageRect = layout.imageRect
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()),
                    dstSize = IntSize(imageRect.width.roundToInt(), imageRect.height.roundToInt()),
                )
                val view = layout.toView(crop)
                val scrim = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
                clipPath(Path().apply { addRect(view) }, ClipOp.Difference) {
                    drawPath(scrim, Color.Black.copy(alpha = 0.6f))
                }
                drawRect(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = Offset(view.left, view.top),
                    size = Size(view.width, view.height),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                if (dragging) {
                    val gridColor = Color.White.copy(alpha = 0.35f)
                    val gridStroke = 1.dp.toPx()
                    for (i in 1..2) {
                        val x = view.left + view.width * i / 3f
                        val y = view.top + view.height * i / 3f
                        drawLine(gridColor, Offset(x, view.top), Offset(x, view.bottom), gridStroke)
                        drawLine(gridColor, Offset(view.left, y), Offset(view.right, y), gridStroke)
                    }
                }
                val cornerLen = min(28.dp.toPx(), min(view.width, view.height) / 3f)
                val cornerStroke = 4.dp.toPx()
                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(
                        Color.White,
                        Offset(x, y),
                        Offset(x + dx * cornerLen, y),
                        cornerStroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        Color.White,
                        Offset(x, y),
                        Offset(x, y + dy * cornerLen),
                        cornerStroke,
                        cap = StrokeCap.Round,
                    )
                }
                corner(view.left, view.top, 1f, 1f)
                corner(view.right, view.top, -1f, 1f)
                corner(view.left, view.bottom, 1f, -1f)
                corner(view.right, view.bottom, -1f, -1f)

                val edgeLen = min(24.dp.toPx(), min(view.width, view.height) / 3f)
                val centerX = view.left + view.width / 2f
                val centerY = view.top + view.height / 2f
                drawLine(
                    Color.White,
                    Offset(centerX - edgeLen / 2f, view.top),
                    Offset(centerX + edgeLen / 2f, view.top),
                    cornerStroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    Color.White,
                    Offset(centerX - edgeLen / 2f, view.bottom),
                    Offset(centerX + edgeLen / 2f, view.bottom),
                    cornerStroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    Color.White,
                    Offset(view.left, centerY - edgeLen / 2f),
                    Offset(view.left, centerY + edgeLen / 2f),
                    cornerStroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    Color.White,
                    Offset(view.right, centerY - edgeLen / 2f),
                    Offset(view.right, centerY + edgeLen / 2f),
                    cornerStroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private val FullCrop = Rect(0f, 0f, 1f, 1f)

private fun hitHandle(p: Offset, view: Rect, handlePx: Float): CropHandle {
    val slop = min(handlePx, max(view.width, view.height) / 2f)
    fun nearCorner(x: Float, y: Float) = abs(p.x - x) <= slop && abs(p.y - y) <= slop
    val insideY = p.y >= view.top - slop && p.y <= view.bottom + slop
    val insideX = p.x >= view.left - slop && p.x <= view.right + slop
    return when {
        nearCorner(view.left, view.top) -> CropHandle.TL
        nearCorner(view.right, view.top) -> CropHandle.TR
        nearCorner(view.left, view.bottom) -> CropHandle.BL
        nearCorner(view.right, view.bottom) -> CropHandle.BR
        abs(p.x - view.left) <= slop && insideY -> CropHandle.L
        abs(p.x - view.right) <= slop && insideY -> CropHandle.R
        abs(p.y - view.top) <= slop && insideX -> CropHandle.T
        abs(p.y - view.bottom) <= slop && insideX -> CropHandle.B
        p.x in view.left..view.right && p.y in view.top..view.bottom -> CropHandle.Move
        else -> CropHandle.None
    }
}

/** Новая рамка считается от [start] и полного смещения пальца, без накопления ошибки. */
private fun resizeCrop(
    start: Rect,
    handle: CropHandle,
    delta: Offset,
    bounds: Rect,
    aspect: Float?,
    minSize: Float,
): Rect {
    if (handle == CropHandle.None) return start
    if (handle == CropHandle.Move) {
        val dx = delta.x.coerceIn(bounds.left - start.left, bounds.right - start.right)
        val dy = delta.y.coerceIn(bounds.top - start.top, bounds.bottom - start.bottom)
        return Rect(start.left + dx, start.top + dy, start.right + dx, start.bottom + dy)
    }
    val minW = min(minSize, bounds.width)
    val minH = min(minSize, bounds.height)
    if (aspect == null) {
        var l = start.left
        var t = start.top
        var r = start.right
        var b = start.bottom
        if (handle.touchesLeft) l = (start.left + delta.x).coerceIn(bounds.left, r - minW)
        if (handle.touchesRight) r = (start.right + delta.x).coerceIn(l + minW, bounds.right)
        if (handle.touchesTop) t = (start.top + delta.y).coerceIn(bounds.top, b - minH)
        if (handle.touchesBottom) b = (start.bottom + delta.y).coerceIn(t + minH, bounds.bottom)
        return Rect(l, t, r, b)
    }
    return resizeWithAspect(start, handle, delta, bounds, aspect, max(minW, minH * aspect))
}

private fun resizeWithAspect(
    start: Rect,
    handle: CropHandle,
    delta: Offset,
    bounds: Rect,
    aspect: Float,
    minWidth: Float,
): Rect {
    val dirX = if (handle.touchesLeft) -1 else 1
    val dirY = if (handle.touchesTop) -1 else 1
    return when (handle) {
        CropHandle.TL, CropHandle.TR, CropHandle.BL, CropHandle.BR -> {
            val anchorX = if (handle.touchesLeft) start.right else start.left
            val anchorY = if (handle.touchesTop) start.bottom else start.top
            val widthFromX = start.width + dirX * delta.x
            val heightFromY = start.height + dirY * delta.y
            val width = if (abs(delta.x) >= abs(delta.y)) widthFromX else heightFromY * aspect
            rectFromAnchor(anchorX, anchorY, dirX, dirY, width, aspect, bounds, minWidth)
        }
        CropHandle.L, CropHandle.R -> {
            val anchorX = if (handle.touchesLeft) start.right else start.left
            val width = start.width + dirX * delta.x
            centeredVertically(anchorX, dirX, start.center.y, width, aspect, bounds, minWidth)
        }
        else -> {
            val anchorY = if (handle.touchesTop) start.bottom else start.top
            val height = start.height + dirY * delta.y
            centeredHorizontally(anchorY, dirY, start.center.x, height * aspect, aspect, bounds, minWidth)
        }
    }
}

private fun rectFromAnchor(
    anchorX: Float,
    anchorY: Float,
    dirX: Int,
    dirY: Int,
    desiredWidth: Float,
    aspect: Float,
    bounds: Rect,
    minWidth: Float,
): Rect {
    val maxW = if (dirX > 0) bounds.right - anchorX else anchorX - bounds.left
    val maxH = if (dirY > 0) bounds.bottom - anchorY else anchorY - bounds.top
    var w = desiredWidth.coerceAtLeast(minWidth)
    w = min(w, maxW)
    w = min(w, maxH * aspect)
    if (w < 1f) return Rect(anchorX, anchorY, anchorX, anchorY)
    val h = w / aspect
    val x2 = anchorX + dirX * w
    val y2 = anchorY + dirY * h
    return Rect(min(anchorX, x2), min(anchorY, y2), max(anchorX, x2), max(anchorY, y2))
}

private fun centeredVertically(
    anchorX: Float,
    dirX: Int,
    centerY: Float,
    desiredWidth: Float,
    aspect: Float,
    bounds: Rect,
    minWidth: Float,
): Rect {
    val maxW = if (dirX > 0) bounds.right - anchorX else anchorX - bounds.left
    val allowedH = 2f * min(centerY - bounds.top, bounds.bottom - centerY)
    var w = desiredWidth.coerceAtLeast(minWidth)
    w = min(w, maxW)
    w = min(w, allowedH * aspect)
    if (w < 1f) return Rect(anchorX, centerY, anchorX, centerY)
    val h = w / aspect
    val x2 = anchorX + dirX * w
    return Rect(min(anchorX, x2), centerY - h / 2f, max(anchorX, x2), centerY + h / 2f)
}

private fun centeredHorizontally(
    anchorY: Float,
    dirY: Int,
    centerX: Float,
    desiredWidth: Float,
    aspect: Float,
    bounds: Rect,
    minWidth: Float,
): Rect {
    val maxH = if (dirY > 0) bounds.bottom - anchorY else anchorY - bounds.top
    val allowedW = 2f * min(centerX - bounds.left, bounds.right - centerX)
    var w = desiredWidth.coerceAtLeast(minWidth)
    w = min(w, allowedW)
    w = min(w, maxH * aspect)
    if (w < 1f) return Rect(centerX, anchorY, centerX, anchorY)
    val h = w / aspect
    val y2 = anchorY + dirY * h
    return Rect(centerX - w / 2f, min(anchorY, y2), centerX + w / 2f, max(anchorY, y2))
}

/**
 * Пропорции задаются в пикселях файла, поэтому в долях кадр пересчитывается через размеры источника.
 */
private fun centeredAspectCrop(
    current: Rect,
    srcWidth: Int,
    srcHeight: Int,
    aspect: Float,
): Rect {
    if (srcWidth <= 0 || srcHeight <= 0) return current
    val srcW = srcWidth.toFloat()
    val srcH = srcHeight.toFloat()
    val centerX = current.center.x.coerceIn(0f, 1f)
    val centerY = current.center.y.coerceIn(0f, 1f)
    var widthPx = min(srcW, current.width * srcW)
    var heightPx = widthPx / aspect
    val maxHeightPx = min(srcH, current.height * srcH).coerceAtLeast(1f)
    if (heightPx > maxHeightPx) {
        heightPx = maxHeightPx
        widthPx = heightPx * aspect
    }
    var w = (widthPx / srcW).coerceIn(0.05f, 1f)
    var h = (heightPx / srcH).coerceIn(0.05f, 1f)
    // Кадр должен целиком попадать в изображение — при необходимости уменьшаем с сохранением пропорций.
    val shrink = min(1f, min(1f / w, 1f / h))
    w *= shrink
    h *= shrink
    val left = (centerX - w / 2f).coerceIn(0f, 1f - w)
    val top = (centerY - h / 2f).coerceIn(0f, 1f - h)
    return Rect(left, top, left + w, top + h)
}

private val CropHandle.touchesLeft: Boolean
    get() = this == CropHandle.L || this == CropHandle.TL || this == CropHandle.BL

private val CropHandle.touchesRight: Boolean
    get() = this == CropHandle.R || this == CropHandle.TR || this == CropHandle.BR

private val CropHandle.touchesTop: Boolean
    get() = this == CropHandle.T || this == CropHandle.TL || this == CropHandle.TR

private val CropHandle.touchesBottom: Boolean
    get() = this == CropHandle.B || this == CropHandle.BL || this == CropHandle.BR
