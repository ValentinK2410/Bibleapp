package com.example.bible.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.KidsAgeMode
import com.example.bible.data.KidsColorPage
import com.example.bible.data.KidsColorRegion
import com.example.bible.data.KidsColorShape
import com.example.bible.data.KidsColoringPages
import com.example.bible.data.KidsFamilyStore
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

private data class Ink(val color: Color, val points: List<Offset>, val erase: Boolean)

@Composable
fun KidsColoringScreen(onBack: () -> Unit, pageId: String? = null) {
    var open by remember { mutableStateOf(pageId?.let { KidsColoringPages.byId(it) }) }
    if (open != null) {
        ColoringCanvas(open!!, onBack = { open = null })
        return
    }
    KidsSectionScaffold("Раскраски", onBack) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(
                    "Контуры-заглушки из простых фигур. Настоящие раскраски — файлы kids_coloring_<id>.json с областями path.",
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
    var color by remember { mutableStateOf(KidsColoringPages.palette[2].second) }
    var colorName by remember { mutableStateOf(KidsColoringPages.palette[2].first) }
    var tool by remember { mutableStateOf(if (young) "fill" else "fill") }
    val fills = remember { mutableStateOf(loadFills(context, family.bucketId(), page.id)) }
    val strokes = remember { mutableStateListOf<Ink>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var gate by remember { mutableStateOf(false) }
    KidsSectionScaffold(page.title, onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Text("Заглушка вместо ${page.assetName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFFFFBF5))
                    .pointerInput(tool, color) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (tool == "fill") {
                                    val hit = hitRegion(page.regions, offset, size.width.toFloat(), size.height.toFloat())
                                    if (hit != null) {
                                        fills.value = fills.value + (hit.id to color)
                                        saveFills(context, family.bucketId(), page.id, fills.value)
                                        if (fills.value.size >= page.regions.size) {
                                            scope.launch { KidsFamilyStore.recordColoring(context, page.id) }
                                        }
                                    }
                                } else {
                                    current = listOf(offset)
                                }
                            },
                            onDrag = { change, _ ->
                                if (tool != "fill") {
                                    current = current + change.position
                                }
                            },
                            onDragEnd = {
                                if (tool != "fill" && current.size > 1) {
                                    strokes += Ink(color, current, tool == "erase")
                                    current = emptyList()
                                }
                            },
                        )
                    },
            ) {
                page.regions.forEach { region ->
                    val paint = fills.value[region.id] ?: Color.White
                    drawShape(region, paint)
                    drawShape(region, Color(0xFF334155), stroke = true)
                }
                (strokes + Ink(color, current, tool == "erase")).forEach { ink ->
                    if (ink.points.size < 2) return@forEach
                    val path = Path().apply {
                        moveTo(ink.points.first().x, ink.points.first().y)
                        ink.points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path,
                        if (ink.erase) Color(0xFFFFFBF5) else ink.color,
                        style = Stroke(width = if (ink.erase) 28f else 14f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KidsColoringPages.palette.take(6).forEach { (name, swatch) ->
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(swatch)
                            .border(2.dp, if (color == swatch) Color.Black else Color.Transparent, CircleShape)
                            .clickable {
                                color = swatch
                                colorName = name
                                speak(name)
                            }
                            .padding(16.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { tool = "fill" }) { Text(if (tool == "fill") "• Заливка" else "Заливка") }
                if (!young) {
                    TextButton(onClick = { tool = "brush" }) { Text(if (tool == "brush") "• Кисть" else "Кисть") }
                    TextButton(onClick = { tool = "erase" }) { Text(if (tool == "erase") "• Ластик" else "Ластик") }
                }
                TextButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) { Text("Отмена") }
            }
            Text("Цвет: $colorName", fontSize = 13.sp)
            Button(onClick = { gate = true }) { Text("Сохранить в галерею") }
        }
    }
    if (gate) {
        ParentGateDialog(
            onDismiss = { gate = false },
            onPassed = {
                gate = false
                exportColoring(context, page, fills.value)
            },
        )
    }
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

private fun exportColoring(context: android.content.Context, page: KidsColorPage, fills: Map<String, Color>) {
    val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.parseColor("#FFFBF5"))
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    page.regions.forEach { region ->
        val color = fills[region.id]
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = color?.value?.toInt() ?: android.graphics.Color.WHITE
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
