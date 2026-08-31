package com.example.bible.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
    R43("4:3", 4f / 3f),
    R169("16:9", 16f / 9f),
}

private enum class CropHandle {
    None, Move, L, R, T, B, TL, TR, BL, BR
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
    var crop by remember(bitmap.width, bitmap.height) {
        mutableStateOf(Rect(0.08f, 0.08f, 0.92f, 0.92f))
    }
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
                    IconButton(
                        onClick = {
                            onApply(crop.left, crop.top, crop.right, crop.bottom, outputScale)
                        },
                    ) {
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
                    .padding(12.dp),
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable {
                                    aspect = item
                                    crop = constrainCrop(crop, item.ratio)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                Text(
                    "Размер файла: ${(outputScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = outputScale,
                    onValueChange = { outputScale = it },
                    valueRange = 0.35f..1f,
                )
                val outW = max(1, ((crop.width * bitmap.width) * outputScale).roundToInt())
                val outH = max(1, ((crop.height * bitmap.height) * outputScale).roundToInt())
                Text(
                    "На выходе ≈ $outW × $outH px. Перетащите рамку или углы — так вы сами выбираете кадр.",
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
    val handlePx = with(LocalDensity.current) { 22.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val fitted = remember(boxW, boxH, bitmap.width, bitmap.height) {
            MicroblogImageOps.fitRect(boxW, boxH, bitmap.width.toFloat(), bitmap.height.toFloat())
        }
        val imgLeft = fitted[0]
        val imgTop = fitted[1]
        val imgRight = fitted[2]
        val imgBottom = fitted[3]
        val imgW = imgRight - imgLeft
        val imgH = imgBottom - imgTop

        fun toView(norm: Rect): Rect = Rect(
            imgLeft + norm.left * imgW,
            imgTop + norm.top * imgH,
            imgLeft + norm.right * imgW,
            imgTop + norm.bottom * imgH,
        )

        fun toNorm(view: Rect): Rect = Rect(
            ((view.left - imgLeft) / imgW).coerceIn(0f, 1f),
            ((view.top - imgTop) / imgH).coerceIn(0f, 1f),
            ((view.right - imgLeft) / imgW).coerceIn(0f, 1f),
            ((view.bottom - imgTop) / imgH).coerceIn(0f, 1f),
        )

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(crop, aspect, imgW, imgH) {
                    var handle = CropHandle.None
                    detectDragGestures(
                        onDragStart = { start ->
                            handle = hitHandle(start, toView(crop), handlePx)
                        },
                        onDragEnd = { handle = CropHandle.None },
                        onDragCancel = { handle = CropHandle.None },
                        onDrag = { change, amount ->
                            change.consume()
                            if (handle == CropHandle.None) return@detectDragGestures
                            val view = toView(crop)
                            val next = dragCrop(view, handle, amount, Rect(imgLeft, imgTop, imgRight, imgBottom), aspect)
                            onCrop(constrainCrop(toNorm(next), aspect))
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    image = imageBitmap,
                    dstOffset = androidx.compose.ui.unit.IntOffset(imgLeft.roundToInt(), imgTop.roundToInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(imgW.roundToInt(), imgH.roundToInt()),
                )
                val view = toView(crop)
                val overlay = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
                val hole = Path().apply { addRect(view) }
                clipPath(hole, ClipOp.Difference) {
                    drawPath(overlay, Color.Black.copy(alpha = 0.55f))
                }
                drawRect(
                    color = Color.White,
                    topLeft = Offset(view.left, view.top),
                    size = Size(view.width, view.height),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }
            val view = toView(crop)
            HandleDot(view.left, view.top)
            HandleDot(view.right, view.top)
            HandleDot(view.left, view.bottom)
            HandleDot(view.right, view.bottom)
        }
    }
}

@Composable
private fun HandleDot(x: Float, y: Float) {
    val density = LocalDensity.current
    Box(
        Modifier
            .padding(
                start = with(density) { (x - 8.dp.toPx()).coerceAtLeast(0f).toDp() },
                top = with(density) { (y - 8.dp.toPx()).coerceAtLeast(0f).toDp() },
            )
            .size(16.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, Color(0xFF1565C0), CircleShape),
    )
}

private fun hitHandle(p: Offset, view: Rect, handlePx: Float): CropHandle {
    fun near(x: Float, y: Float) = abs(p.x - x) <= handlePx && abs(p.y - y) <= handlePx
    return when {
        near(view.left, view.top) -> CropHandle.TL
        near(view.right, view.top) -> CropHandle.TR
        near(view.left, view.bottom) -> CropHandle.BL
        near(view.right, view.bottom) -> CropHandle.BR
        abs(p.x - view.left) <= handlePx && p.y in view.top..view.bottom -> CropHandle.L
        abs(p.x - view.right) <= handlePx && p.y in view.top..view.bottom -> CropHandle.R
        abs(p.y - view.top) <= handlePx && p.x in view.left..view.right -> CropHandle.T
        abs(p.y - view.bottom) <= handlePx && p.x in view.left..view.right -> CropHandle.B
        p.x in view.left..view.right && p.y in view.top..view.bottom -> CropHandle.Move
        else -> CropHandle.None
    }
}

private fun dragCrop(
    view: Rect,
    handle: CropHandle,
    delta: Offset,
    bounds: Rect,
    aspect: Float?,
): Rect {
    var l = view.left
    var t = view.top
    var r = view.right
    var b = view.bottom
    val minSize = 48f
    when (handle) {
        CropHandle.Move -> {
            var nl = l + delta.x
            var nt = t + delta.y
            var nr = r + delta.x
            var nb = b + delta.y
            if (nl < bounds.left) {
                nr += bounds.left - nl
                nl = bounds.left
            }
            if (nt < bounds.top) {
                nb += bounds.top - nt
                nt = bounds.top
            }
            if (nr > bounds.right) {
                nl -= nr - bounds.right
                nr = bounds.right
            }
            if (nb > bounds.bottom) {
                nt -= nb - bounds.bottom
                nb = bounds.bottom
            }
            return Rect(nl, nt, nr, nb)
        }
        CropHandle.L -> l += delta.x
        CropHandle.R -> r += delta.x
        CropHandle.T -> t += delta.y
        CropHandle.B -> b += delta.y
        CropHandle.TL -> { l += delta.x; t += delta.y }
        CropHandle.TR -> { r += delta.x; t += delta.y }
        CropHandle.BL -> { l += delta.x; b += delta.y }
        CropHandle.BR -> { r += delta.x; b += delta.y }
        CropHandle.None -> return view
    }
    if (r - l < minSize) {
        if (handle == CropHandle.L || handle == CropHandle.TL || handle == CropHandle.BL) l = r - minSize
        else r = l + minSize
    }
    if (b - t < minSize) {
        if (handle == CropHandle.T || handle == CropHandle.TL || handle == CropHandle.TR) t = b - minSize
        else b = t + minSize
    }
    var next = Rect(l, t, r, b)
    if (aspect != null && aspect > 0f) {
        next = applyAspect(next, handle, aspect)
    }
    return Rect(
        next.left.coerceAtLeast(bounds.left),
        next.top.coerceAtLeast(bounds.top),
        next.right.coerceAtMost(bounds.right),
        next.bottom.coerceAtMost(bounds.bottom),
    )
}

private fun applyAspect(rect: Rect, handle: CropHandle, aspect: Float): Rect {
    val w = rect.width
    val fromW = Rect(rect.left, rect.top, rect.right, rect.top + w / aspect)
    val fromH = Rect(rect.left, rect.top, rect.left + rect.height * aspect, rect.bottom)
    return when (handle) {
        CropHandle.L, CropHandle.R, CropHandle.TL, CropHandle.TR, CropHandle.BL, CropHandle.BR -> {
            val h = w / aspect
            when (handle) {
                CropHandle.TL -> Rect(rect.right - w, rect.bottom - h, rect.right, rect.bottom)
                CropHandle.TR -> Rect(rect.left, rect.bottom - h, rect.right, rect.bottom)
                CropHandle.BL -> Rect(rect.right - w, rect.top, rect.right, rect.top + h)
                else -> Rect(rect.left, rect.top, rect.right, rect.top + h)
            }
        }
        CropHandle.T, CropHandle.B -> fromH
        else -> fromW
    }
}

private fun constrainCrop(crop: Rect, aspect: Float?): Rect {
    var l = crop.left.coerceIn(0f, 0.95f)
    var t = crop.top.coerceIn(0f, 0.95f)
    var r = crop.right.coerceIn(0.05f, 1f)
    var b = crop.bottom.coerceIn(0.05f, 1f)
    if (r - l < 0.08f) r = (l + 0.08f).coerceAtMost(1f)
    if (b - t < 0.08f) b = (t + 0.08f).coerceAtMost(1f)
    if (aspect != null && aspect > 0f) {
        val w = r - l
        var h = w / aspect
        if (t + h > 1f) {
            h = 1f - t
            val nw = h * aspect
            r = (l + nw).coerceAtMost(1f)
            l = r - nw
        }
        b = (t + h).coerceAtMost(1f)
    }
    return Rect(l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), r.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
}
