package com.example.bible.ui

import android.content.ContentValues
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.KidsAgeMode
import com.example.bible.data.KidsColorPage
import com.example.bible.data.KidsColorRegion
import com.example.bible.data.KidsColorShape
import com.example.bible.data.KidsColorOutline
import com.example.bible.data.KidsColoringPages
import com.example.bible.data.KidsFamilyStore
import com.example.bible.data.loadColoringOutlines
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

private data class Ink(val color: Color, val points: List<Offset>, val erase: Boolean, val width: Float)

private class BoardZoom {
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    var width = 1f
    var height = 1f

    fun update(zoomChange: Float, panChange: Offset) {
        val next = (zoom * zoomChange).coerceIn(0.5f, 4f)
        zoom = next
        pan = clampColorPan(pan + panChange, next, width, height)
    }

    fun reset() {
        zoom = 1f
        pan = Offset.Zero
    }
}

@Composable
fun KidsColoringScreen(onBack: () -> Unit, pageId: String? = null) {
    var open by remember { mutableStateOf(pageId?.let { KidsColoringPages.byId(it) }) }
    if (open != null) {
        ColoringCanvas(open!!, onBack = { open = null })
        return
    }
    KidsSectionScaffold("Раскраски", onBack) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(
                    "Малышам — крупные части, школьникам — подробный рисунок. Заливка сохраняется на этом телефоне.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(KidsColoringPages.pages, key = { it.id }) { page ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFFFFF7ED))
                        .clickable { open = page }
                        .padding(12.dp),
                ) {
                    Text(page.emoji, fontSize = 40.sp)
                    Text(page.title, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ColoringCanvas(page: KidsColorPage, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val young = family.resolvedMode() == KidsAgeMode.SMALL
    val speak = rememberKidsSpeaker()
    val startColor = KidsColoringPages.palette.first { it.first == "Жёлтый" }
    var color by remember { mutableStateOf(startColor.second) }
    var colorName by remember { mutableStateOf(startColor.first) }
    var tool by remember { mutableStateOf("fill") }
    val fills = remember { mutableStateOf(loadFills(context, family.bucketId(), page.id)) }
    val strokes = remember { mutableStateListOf<Ink>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var gate by remember { mutableStateOf(false) }
    val board = remember { BoardZoom() }
    val outlines = remember(page.assetName, young) {
        paintOutlines(loadColoringOutlines(context, page.assetName, small = young))
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    fun paintAt(point: Offset, canvasWidth: Float, canvasHeight: Float) {
        val hitId = if (outlines.isNotEmpty()) {
            hitOutline(outlines, point, canvasWidth, canvasHeight)?.id
        } else {
            hitRegion(page.regions, point, canvasWidth, canvasHeight)?.id
        }
        if (hitId == null) return
        fills.value = fills.value + (hitId to color)
        saveFills(context, family.bucketId(), page.id, fills.value)
        val total = if (outlines.isNotEmpty()) outlines.size else page.regions.size
        if (fills.value.size >= total) {
            scope.launch { KidsFamilyStore.recordColoring(context, page.id) }
        }
    }
    KidsSectionScaffold(page.title, onBack) { padding ->
        val boardBox: @Composable (Modifier) -> Unit = { boardModifier ->
            BoxWithConstraints(boardModifier, contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                Canvas(
                    Modifier
                        .size(side)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFFFBF5))
                        .pointerInput(tool, color, outlines) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val origin = down.position
                                var zooming = false
                                val painting = tool != "fill"
                                if (painting) {
                                    current = listOf(toBoardPoint(origin, size, board))
                                }
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    if (pressed.size >= 2) {
                                        if (painting) current = emptyList()
                                        zooming = true
                                        board.update(event.calculateZoom(), event.calculatePan())
                                        event.changes.forEach { it.consume() }
                                    } else if (painting && !zooming) {
                                        current = current + toBoardPoint(pressed.first().position, size, board)
                                        pressed.first().consume()
                                    }
                                }
                                if (!zooming && tool == "fill") {
                                    paintAt(
                                        toBoardPoint(origin, size, board),
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                    )
                                }
                                if (!zooming && painting && current.size > 1) {
                                    strokes += Ink(color, current, tool == "erase", if (tool == "erase") 28f else 14f)
                                }
                                if (painting) current = emptyList()
                            }
                        },
                ) {
                    board.width = size.width
                    board.height = size.height
                    val center = Offset(size.width / 2f, size.height / 2f)
                    translate(board.pan.x, board.pan.y) {
                        scale(board.zoom, board.zoom, pivot = center) {
                            if (outlines.isNotEmpty()) {
                                val factor = size.width / 800f
                                scale(factor, factor, pivot = Offset.Zero) {
                                    outlines.forEach { region ->
                                        val paint = fills.value[region.id] ?: Color.White
                                        drawPath(region.composePath, paint)
                                        drawPath(region.composePath, Color(0xFF1F2937), style = Stroke(width = 4f))
                                    }
                                }
                            } else {
                                page.regions.forEach { region ->
                                    val paint = fills.value[region.id] ?: Color.White
                                    drawShape(region, paint)
                                    drawShape(region, Color(0xFF334155), stroke = true)
                                }
                            }
                            (strokes + Ink(color, current, tool == "erase", if (tool == "erase") 28f else 14f)).forEach { ink ->
                                if (ink.points.size < 2) return@forEach
                                val path = Path().apply {
                                    moveTo(ink.points.first().x, ink.points.first().y)
                                    ink.points.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(
                                    path,
                                    if (ink.erase) Color(0xFFFFFBF5) else ink.color,
                                    style = Stroke(width = ink.width),
                                )
                            }
                        }
                    }
                }
            }
        }
        val controls: @Composable (Boolean) -> Unit = { stacked ->
            ColoringSwatches(
                selected = color,
                stacked = stacked,
                onPick = { name, swatch ->
                    color = swatch
                    colorName = name
                    speak(name)
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { tool = "fill" }) { Text(if (tool == "fill") "• Заливка" else "Заливка") }
                if (!young) {
                    TextButton(onClick = { tool = "brush" }) { Text(if (tool == "brush") "• Кисть" else "Кисть") }
                    TextButton(onClick = { tool = "erase" }) { Text(if (tool == "erase") "• Ластик" else "Ластик") }
                }
                TextButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) { Text("Отмена") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { board.zoomBy(1f / 1.25f) }) { Text("Меньше") }
                TextButton(onClick = { board.reset() }) { Text("Вся") }
                TextButton(onClick = { board.zoomBy(1.25f) }) { Text("Больше") }
            }
            Text("Цвет: $colorName", fontSize = 13.sp)
            Button(onClick = { gate = true }) { Text("Сохранить в галерею") }
        }
        if (landscape) {
            Row(
                Modifier.fillMaxSize().padding(padding).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                boardBox(Modifier.weight(1f).fillMaxHeight())
                Column(
                    Modifier
                        .width(210.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    controls(true)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                boardBox(Modifier.weight(1f).fillMaxWidth())
                controls(false)
            }
        }
    }
    if (gate) {
        ParentGateDialog(
            onDismiss = { gate = false },
            onPassed = {
                gate = false
                exportColoring(context, page, fills.value, outlines)
            },
        )
    }
}

@Composable
private fun ColoringSwatches(
    selected: Color,
    stacked: Boolean,
    onPick: (String, Color) -> Unit,
) {
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KidsColoringPages.palette.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (name, swatch) -> ColorSwatch(name, swatch, selected, onPick) }
                }
            }
        }
    } else {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KidsColoringPages.palette.forEach { (name, swatch) -> ColorSwatch(name, swatch, selected, onPick) }
        }
    }
}

@Composable
private fun ColorSwatch(
    name: String,
    swatch: Color,
    selected: Color,
    onPick: (String, Color) -> Unit,
) {
    val ring = when {
        selected == swatch -> Color.Black
        swatch == Color(0xFFFFFBF5) -> Color(0xFFCBD5E1)
        else -> Color.Transparent
    }
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(swatch)
            .border(3.dp, ring, CircleShape)
            .clickable { onPick(name, swatch) },
    )
}

private fun toBoardPoint(point: Offset, size: androidx.compose.ui.unit.IntSize, board: BoardZoom): Offset {
    val center = Offset(size.width / 2f, size.height / 2f)
    val shifted = point - board.pan
    return center + (shifted - center) / board.zoom.coerceAtLeast(0.01f)
}

private fun BoardZoom.zoomBy(factor: Float) = update(factor, Offset.Zero)

private fun clampColorPan(pan: Offset, zoom: Float, width: Float, height: Float): Offset {
    val maxX = width * kotlin.math.abs(zoom - 1f) / 2f + width * 0.08f
    val maxY = height * kotlin.math.abs(zoom - 1f) / 2f + height * 0.08f
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShape(
    region: KidsColorRegion,
    color: Color,
    stroke: Boolean = false,
) {
    val cx = region.cx * size.width
    val cy = region.cy * size.height
    val w = region.w * size.width
    val h = region.h * size.height
    val topLeft = Offset(cx - w / 2f, cy - h / 2f)
    val style = if (stroke) Stroke(width = 6f) else androidx.compose.ui.graphics.drawscope.Fill
    when (region.shape) {
        KidsColorShape.CIRCLE, KidsColorShape.OVAL -> drawOval(color, topLeft, androidx.compose.ui.geometry.Size(w, h), style = style)
        KidsColorShape.ROUND_RECT -> drawRoundRect(color, topLeft, androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(24f, 24f), style = style)
    }
}

private fun hitRegion(regions: List<KidsColorRegion>, point: Offset, width: Float, height: Float): KidsColorRegion? =
    regions.asReversed().firstOrNull { region ->
        val cx = region.cx * width
        val cy = region.cy * height
        val w = region.w * width
        val h = region.h * height
        abs(point.x - cx) <= w / 2f && abs(point.y - cy) <= h / 2f
    }

private fun fillsFile(context: android.content.Context, bucket: String, pageId: String): File {
    val dir = File(context.filesDir, "kids_coloring/$bucket").apply { mkdirs() }
    return File(dir, "$pageId.json")
}

private fun loadFills(context: android.content.Context, bucket: String, pageId: String): Map<String, Color> {
    val file = fillsFile(context, bucket, pageId)
    if (!file.exists()) return emptyMap()
    return runCatching {
        val json = JSONObject(file.readText())
        buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, Color(json.getInt(key)))
            }
        }
    }.getOrDefault(emptyMap())
}

private fun saveFills(context: android.content.Context, bucket: String, pageId: String, fills: Map<String, Color>) {
    val json = JSONObject()
    fills.forEach { (id, color) -> json.put(id, color.toArgb()) }
    fillsFile(context, bucket, pageId).writeText(json.toString())
}

private data class PaintedOutline(
    val id: String,
    val path: android.graphics.Path,
    val composePath: Path,
)

private fun paintOutlines(outlines: List<KidsColorOutline>): List<PaintedOutline> =
    outlines.mapNotNull { outline ->
        val path = androidx.core.graphics.PathParser.createPathFromPathData(outline.d) ?: return@mapNotNull null
        PaintedOutline(outline.id, path, path.asComposePath())
    }

private fun hitOutline(regions: List<PaintedOutline>, point: Offset, width: Float, height: Float): PaintedOutline? {
    if (width <= 0f || height <= 0f) return null
    val x = (point.x * 800f / width).toInt()
    val y = (point.y * 800f / height).toInt()
    val clip = android.graphics.Region(0, 0, 800, 800)
    return regions.asReversed().firstOrNull { region ->
        val hit = android.graphics.Region()
        hit.setPath(region.path, clip)
        hit.contains(x, y)
    }
}

private fun exportColoring(
    context: android.content.Context,
    page: KidsColorPage,
    fills: Map<String, Color>,
    outlines: List<PaintedOutline>,
) {
    val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.parseColor("#FFFBF5"))
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    if (outlines.isNotEmpty()) {
        outlines.forEach { region ->
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = fills[region.id]?.toArgb() ?: android.graphics.Color.WHITE
            canvas.drawPath(region.path, paint)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = android.graphics.Color.parseColor("#1F2937")
            canvas.drawPath(region.path, paint)
        }
    } else {
        page.regions.forEach { region ->
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = fills[region.id]?.toArgb() ?: android.graphics.Color.WHITE
            val cx = region.cx * 800f
            val cy = region.cy * 800f
            val w = region.w * 800f
            val h = region.h * 800f
            canvas.drawOval(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, paint)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 8f
            paint.color = android.graphics.Color.DKGRAY
            canvas.drawOval(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, paint)
        }
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "bible_kids_${page.id}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BibleKids")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
    context.contentResolver.openOutputStream(uri)?.use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
}
