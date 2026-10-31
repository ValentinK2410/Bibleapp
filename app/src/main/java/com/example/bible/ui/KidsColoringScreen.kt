package com.example.bible.ui

import android.content.ContentValues
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

private data class Ink(val color: Color, val points: List<Offset>, val erase: Boolean, val width: Float)

private data class BoardState(val fills: Map<String, Color>, val strokes: List<Ink>) {
    companion object {
        val Empty = BoardState(emptyMap(), emptyList())
    }
}

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

    fun zoomBy(factor: Float) = update(factor, Offset.Zero)

    fun reset() {
        zoom = 1f
        pan = Offset.Zero
    }
}

private val CONFETTI = listOf(
    Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFFACC15), Color(0xFF22C55E),
    Color(0xFF38BDF8), Color(0xFF8B5CF6), Color(0xFFF472B6),
)

/** Тон, которым мигают ещё не закрашенные области: белый фон иначе выглядит «уже готовым». */
private val UNPAINTED_TINT = Color(0xFFE0A15A)

private val PRAISE = listOf(
    "Молодец! Как красиво получилось!",
    "Ура! Ты раскрасил всю картинку!",
    "Здорово! Настоящий художник!",
)

@Composable
fun KidsColoringScreen(onBack: () -> Unit, pageId: String? = null) {
    var open by remember { mutableStateOf(pageId?.let { KidsColoringPages.byId(it) }) }
    var revision by remember { mutableStateOf(0) }
    if (open != null) {
        ColoringCanvas(
            page = open!!,
            onBack = {
                open = null
                revision++
            },
        )
        return
    }
    ColoringGallery(onBack = onBack, revision = revision, onOpen = { open = it })
}

// ---------------------------------------------------------------------------
// Галерея: настоящие миниатюры, прогресс и бейдж «Готово»
// ---------------------------------------------------------------------------

@Composable
private fun ColoringGallery(onBack: () -> Unit, revision: Int, onOpen: (KidsColorPage) -> Unit) {
    val context = LocalContext.current
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val young = family.resolvedMode() == KidsAgeMode.SMALL
    val bucket = family.bucketId()
    val doneIds = family.activeProgress().coloring
    val progress = remember(revision) {
        KidsColoringPages.pages.associate { page ->
            page.id to loadBoard(context, bucket, page.id)
        }
    }
    val totalDone = KidsColoringPages.pages.count { page ->
        doneIds.contains(page.id) || pageComplete(context, page, young, progress[page.id] ?: BoardState.Empty)
    }
    KidsSectionScaffold("Раскраски", onBack) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                KidsBanner(
                    text = if (totalDone == 0) {
                        "Выбери картинку и раскрась её пальцем. Малышам — крупные части, школьникам — подробный рисунок."
                    } else {
                        "Готово картинок: $totalDone из ${KidsColoringPages.pages.size}. Выбери следующую!"
                    },
                    gradient = KidsWarmSunset,
                )
            }
            items(KidsColoringPages.pages, key = { it.id }) { page ->
                ColoringPageCard(
                    page = page,
                    young = young,
                    board = progress[page.id] ?: BoardState.Empty,
                    done = doneIds.contains(page.id),
                    onOpen = { onOpen(page) },
                )
            }
        }
    }
}

@Composable
private fun ColoringPageCard(
    page: KidsColorPage,
    young: Boolean,
    board: BoardState,
    done: Boolean,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val outlines = remember(page.assetName, young) { cachedOutlines(context, page.assetName, young) }
    val regionIds = remember(outlines, page) { regionIdSet(outlines, page) }
    val total = regionIds.size
    val filled = board.fills.keys.count { it in regionIds }
    val complete = done || (total > 0 && filled >= total)
    Column(
        Modifier
            .kidsLifted()
            .clip(KidsCardShape)
            .background(KidsPaper)
            .kidsPressable(onClick = onOpen)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(KidsPhotoShape)
                .background(Color(0xFFFFFBF5))
                .border(2.dp, Color.White, KidsPhotoShape),
        ) {
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val factor = minOf(size.width, size.height) / 800f
                val dx = (size.width - 800f * factor) / 2f
                val dy = (size.height - 800f * factor) / 2f
                translate(dx, dy) {
                    scale(factor, factor, pivot = Offset.Zero) {
                        if (outlines.isNotEmpty()) {
                            outlines.forEach { region ->
                                drawPath(region.composePath, Color.White)
                                drawPath(region.composePath, Color(0xFF1F2937), style = Stroke(width = 5f))
                            }
                        } else {
                            page.regions.forEach { region ->
                                drawShape(region, Color.White)
                                drawShape(region, Color(0xFF334155), stroke = true)
                            }
                        }
                    }
                }
            }
            if (complete) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text("Готово", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            page.title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE7DCC8)),
            ) {
                val ratio = if (total > 0) (filled.toFloat() / total).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(ratio)
                        .clip(CircleShape)
                        .background(if (complete) Color(0xFF22C55E) else Color(0xFFE0A15A)),
                )
            }
            Text(
                if (total > 0) "$filled/$total" else page.emoji,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (young) "Крупные части" else "Подробный рисунок",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Холст
// ---------------------------------------------------------------------------

@Composable
private fun ColoringCanvas(page: KidsColorPage, onBack: () -> Unit) {
    val context = LocalContext.current
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val young = family.resolvedMode() == KidsAgeMode.SMALL
    val speak = rememberKidsSpeaker()
    val startColor = KidsColoringPages.palette.first { it.first == "Жёлтый" }
    var color by remember { mutableStateOf(startColor.second) }
    var colorName by remember { mutableStateOf(startColor.first) }
    var tool by remember { mutableStateOf("fill") }
    var board by remember { mutableStateOf(loadBoard(context, family.bucketId(), page.id)) }
    val undoStack = remember { mutableStateListOf<BoardState>() }
    val redoStack = remember { mutableStateListOf<BoardState>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var gate by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var hintRegion by remember { mutableStateOf<String?>(null) }
    var showUnpainted by remember { mutableStateOf(true) }
    var celebrating by remember { mutableStateOf(false) }
    val boardZoom = remember { BoardZoom() }
    val outlines = remember(page.assetName, young) {
        paintOutlines(loadColoringOutlines(context, page.assetName, small = young))
    }
    val regionIds = remember(outlines, page) { regionIdSet(outlines, page) }
    val total = regionIds.size
    val filled = board.fills.keys.count { it in regionIds }
    val hintTransition = rememberInfiniteTransition(label = "hint")
    val hintPulse by hintTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "hint-pulse",
    )
    val unpaintedPulse by hintTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "unpainted-pulse",
    )
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun commit(next: BoardState) {
        undoStack.add(board)
        if (undoStack.size > 60) undoStack.removeAt(0)
        redoStack.clear()
        board = next
        saveBoard(context, family.bucketId(), page.id, next)
    }

    fun paintAt(point: Offset, canvasWidth: Float, canvasHeight: Float) {
        val hitId = if (outlines.isNotEmpty()) {
            hitOutline(outlines, point, canvasWidth, canvasHeight)?.id
        } else {
            hitRegion(page.regions, point, canvasWidth, canvasHeight)?.id
        } ?: return
        commit(board.copy(fills = board.fills + (hitId to color)))
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.add(board)
        board = previous
        saveBoard(context, family.bucketId(), page.id, previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(board)
        board = next
        saveBoard(context, family.bucketId(), page.id, next)
    }

    fun showHint() {
        val target = outlines.firstOrNull { outline -> !board.fills.containsKey(outline.id) } ?: return
        val (name, swatch) = KidsColoringPages.nearestPalette(target.hintArgb)
        hintRegion = target.id
        color = swatch
        colorName = name
        speak("Подсказка: $name")
    }

    LaunchedEffect(filled, total) {
        if (total > 0 && filled >= total && !celebrating) {
            hintRegion = null
            celebrating = true
            KidsFamilyStore.recordColoring(context, page.id)
            speak(PRAISE[(page.id.hashCode() and Int.MAX_VALUE) % PRAISE.size])
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
                                    current = listOf(toBoardPoint(origin, size, boardZoom))
                                }
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    if (pressed.size >= 2) {
                                        if (painting) current = emptyList()
                                        zooming = true
                                        boardZoom.update(event.calculateZoom(), event.calculatePan())
                                        event.changes.forEach { it.consume() }
                                    } else if (painting && !zooming) {
                                        current = current + toBoardPoint(pressed.first().position, size, boardZoom)
                                        pressed.first().consume()
                                    }
                                }
                                if (!zooming && tool == "fill") {
                                    paintAt(
                                        toBoardPoint(origin, size, boardZoom),
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                    )
                                }
                                if (!zooming && painting && current.size > 1) {
                                    val ink = Ink(color, current, tool == "erase", if (tool == "erase") 28f else 14f)
                                    commit(board.copy(strokes = board.strokes + ink))
                                }
                                if (painting) current = emptyList()
                            }
                        },
                ) {
                    boardZoom.width = size.width
                    boardZoom.height = size.height
                    val center = Offset(size.width / 2f, size.height / 2f)
                    translate(boardZoom.pan.x, boardZoom.pan.y) {
                        scale(boardZoom.zoom, boardZoom.zoom, pivot = center) {
                            if (outlines.isNotEmpty()) {
                                val factor = size.width / 800f
                                scale(factor, factor, pivot = Offset.Zero) {
                                    outlines.forEach { region ->
                                        val paint = board.fills[region.id] ?: Color.White
                                        drawPath(region.composePath, paint)
                                        drawPath(region.composePath, Color(0xFF1F2937), style = Stroke(width = 4f))
                                    }
                                    if (showUnpainted) {
                                        outlines.forEach { region ->
                                            if (!board.fills.containsKey(region.id)) {
                                                drawPath(region.composePath, UNPAINTED_TINT.copy(alpha = unpaintedPulse))
                                            }
                                        }
                                    }
                                    hintRegion?.let { id ->
                                        outlines.firstOrNull { it.id == id }?.let { region ->
                                            drawPath(region.composePath, Color(region.hintArgb).copy(alpha = hintPulse))
                                        }
                                    }
                                }
                            } else {
                                page.regions.forEach { region ->
                                    val paint = board.fills[region.id] ?: Color.White
                                    drawShape(region, paint)
                                    drawShape(region, Color(0xFF334155), stroke = true)
                                }
                                if (showUnpainted) {
                                    page.regions.forEach { region ->
                                        if (!board.fills.containsKey(region.id)) {
                                            drawShape(region, UNPAINTED_TINT.copy(alpha = unpaintedPulse))
                                        }
                                    }
                                }
                            }
                            (board.strokes + Ink(color, current, tool == "erase", if (tool == "erase") 28f else 14f)).forEach { ink ->
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
            ColoringPalette(
                selected = color,
                stacked = stacked,
                onPick = { name, swatch ->
                    color = swatch
                    colorName = name
                    hintRegion = null
                    speak(name)
                },
            )
            ColoringToolRow(
                tool = tool,
                young = young,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                showUnpainted = showUnpainted,
                onTool = { tool = it },
                onHint = ::showHint,
                onToggleUnpainted = { showUnpainted = !showUnpainted },
                onUndo = ::undo,
                onRedo = ::redo,
                onClear = { confirmClear = true },
                onZoom = { boardZoom.zoomBy(it) },
                onFit = { boardZoom.reset() },
            )
            Text(
                "Цвет: $colorName · закрашено $filled из $total",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KidsBigButton(
                text = "Сохранить в галерею",
                onClick = { gate = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (landscape) {
            Row(
                Modifier.fillMaxSize().padding(padding).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                boardBox(Modifier.weight(1f).fillMaxHeight())
                Column(
                    Modifier
                        .width(230.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    controls(true)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                boardBox(Modifier.weight(1f).fillMaxWidth())
                controls(false)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить картинку?") },
            text = { Text("Вся раскраска и рисунки кистью на этой странице сотрутся.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    commit(BoardState.Empty)
                    hintRegion = null
                }) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Оставить") }
            },
        )
    }
    if (gate) {
        ParentGateDialog(
            onDismiss = { gate = false },
            onPassed = {
                gate = false
                exportColoring(context, page, board, outlines)
            },
        )
    }
    if (celebrating) {
        CelebrationOverlay(onDismiss = { celebrating = false })
    }
}

@Composable
private fun ColoringToolRow(
    tool: String,
    young: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    showUnpainted: Boolean,
    onTool: (String) -> Unit,
    onHint: () -> Unit,
    onToggleUnpainted: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onZoom: (Float) -> Unit,
    onFit: () -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToolButton("Заливка", Icons.Filled.FormatColorFill, tool == "fill") { onTool("fill") }
        if (!young) {
            ToolButton("Кисть", Icons.Filled.Brush, tool == "brush") { onTool("brush") }
            ToolButton("Ластик", Icons.Filled.Delete, tool == "erase") { onTool("erase") }
        }
        ToolButton("Подсказка", Icons.Filled.Lightbulb, active = false, tint = Color(0xFFE0A15A), onClick = onHint)
        ToolButton("Подсветка", Icons.Filled.FlashOn, active = showUnpainted, onClick = onToggleUnpainted)
        ToolButton("Отмена", Icons.Filled.Undo, active = false, enabled = canUndo, onClick = onUndo)
        ToolButton("Вернуть", Icons.Filled.Redo, active = false, enabled = canRedo, onClick = onRedo)
        ToolButton("Очистить", Icons.Filled.RestartAlt, active = false, onClick = onClear)
        ToolButton("Меньше", Icons.Filled.ZoomOut, active = false) { onZoom(1f / 1.25f) }
        ToolButton("Вся", Icons.Filled.FitScreen, active = false, onClick = onFit)
        ToolButton("Больше", Icons.Filled.ZoomIn, active = false) { onZoom(1.25f) }
    }
}

@Composable
private fun ToolButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .width(64.dp)
            .clip(shape)
            .background(
                when {
                    !enabled -> Color(0xFFE7E0D4)
                    active -> Color(0xFFE0A15A)
                    else -> Color(0xFFFFFBF5)
                },
            )
            .border(2.dp, if (active) Color(0xFFC46A3A) else Color(0xFFE7DCC8), shape)
            .then(if (enabled) Modifier.kidsPressable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> Color(0xFFB4AC9E)
                active -> Color.White
                tint != Color.Unspecified -> tint
                else -> Color(0xFF5C4A3A)
            },
            modifier = Modifier.size(24.dp),
        )
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else Color(0xFF5C4A3A),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
private fun ColoringPalette(
    selected: Color,
    stacked: Boolean,
    onPick: (String, Color) -> Unit,
) {
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KidsColoringPages.palette.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (name, swatch) ->
                        PaletteSwatch(name, swatch, selected, onPick, compact = true)
                    }
                }
            }
        }
    } else {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KidsColoringPages.palette.forEach { (name, swatch) ->
                PaletteSwatch(name, swatch, selected, onPick, compact = false)
            }
        }
    }
}

@Composable
private fun PaletteSwatch(
    name: String,
    swatch: Color,
    selected: Color,
    onPick: (String, Color) -> Unit,
    compact: Boolean,
) {
    val isSelected = selected == swatch
    Column(
        Modifier
            .width(if (compact) 38.dp else 52.dp)
            .kidsPressable(onClick = { onPick(name, swatch) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(if (compact) 30.dp else 40.dp)
                .clip(CircleShape)
                .background(swatch)
                .border(
                    width = if (isSelected) 4.dp else 2.dp,
                    color = if (isSelected) Color(0xFFC46A3A) else Color(0xFFE0D8C8),
                    shape = CircleShape,
                ),
        )
        Text(
            name,
            fontSize = 9.sp,
            maxLines = 1,
            color = if (isSelected) Color(0xFFC46A3A) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Праздник завершения
// ---------------------------------------------------------------------------

@Composable
private fun CelebrationOverlay(onDismiss: () -> Unit) {
    val motion = kidsMotionEnabled()
    val transition = rememberInfiniteTransition(label = "confetti")
    val fall by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Restart),
        label = "fall",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC1F2937))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (!motion) return@Canvas
            val random = Random(11)
            repeat(70) { index ->
                val x = random.nextFloat() * size.width
                val delay = random.nextFloat() * 0.45f
                val progress = (fall - delay).coerceIn(0f, 1f)
                val y = progress * (size.height + 120f) - 60f
                val w = 10f + random.nextFloat() * 12f
                drawRect(
                    color = CONFETTI[index % CONFETTI.size],
                    topLeft = Offset(x, y),
                    size = Size(w, w * 0.62f),
                )
            }
        }
        Column(
            Modifier
                .clip(KidsCardShape)
                .background(KidsPaper)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎉", fontSize = 46.sp)
            Text(
                "Молодец!",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFC46A3A),
            )
            Text(
                "Картинка раскрашена целиком",
                fontSize = 14.sp,
                color = Color(0xFF5C4A3A),
                textAlign = TextAlign.Center,
            )
            KidsRewardStars(count = 3)
            KidsBigButton(
                text = "Дальше",
                onClick = onDismiss,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Служебное: координаты, попадание, рисование фигур
// ---------------------------------------------------------------------------

private fun toBoardPoint(point: Offset, size: androidx.compose.ui.unit.IntSize, board: BoardZoom): Offset {
    val center = Offset(size.width / 2f, size.height / 2f)
    val shifted = point - board.pan
    return center + (shifted - center) / board.zoom.coerceAtLeast(0.01f)
}

private fun clampColorPan(pan: Offset, zoom: Float, width: Float, height: Float): Offset {
    val maxX = width * abs(zoom - 1f) / 2f + width * 0.08f
    val maxY = height * abs(zoom - 1f) / 2f + height * 0.08f
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
        KidsColorShape.CIRCLE, KidsColorShape.OVAL -> drawOval(color, topLeft, Size(w, h), style = style)
        KidsColorShape.ROUND_RECT -> drawRoundRect(color, topLeft, Size(w, h), androidx.compose.ui.geometry.CornerRadius(24f, 24f), style = style)
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

private data class PaintedOutline(
    val id: String,
    val path: android.graphics.Path,
    val composePath: Path,
    val hintArgb: Int,
)

private val outlineCache = object : LruCache<String, List<PaintedOutline>>(24) {}

private fun cachedOutlines(context: android.content.Context, assetName: String, small: Boolean): List<PaintedOutline> {
    val key = "$assetName:${if (small) "s" else "d"}"
    outlineCache.get(key)?.let { return it }
    val parsed = paintOutlines(loadColoringOutlines(context, assetName, small))
    outlineCache.put(key, parsed)
    return parsed
}

private fun regionIdSet(outlines: List<PaintedOutline>, page: KidsColorPage): Set<String> =
    if (outlines.isNotEmpty()) outlines.map { it.id }.toSet() else page.regions.map { it.id }.toSet()

private fun pageComplete(
    context: android.content.Context,
    page: KidsColorPage,
    young: Boolean,
    board: BoardState,
): Boolean {
    val ids = regionIdSet(cachedOutlines(context, page.assetName, young), page)
    return ids.isNotEmpty() && board.fills.keys.count { it in ids } >= ids.size
}

private fun paintOutlines(outlines: List<KidsColorOutline>): List<PaintedOutline> =
    outlines.mapNotNull { outline ->
        val path = androidx.core.graphics.PathParser.createPathFromPathData(outline.d) ?: return@mapNotNull null
        PaintedOutline(outline.id, path, path.asComposePath(), outline.hintArgb)
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

// ---------------------------------------------------------------------------
// Сохранение: заливка и штрихи в одном файле
// ---------------------------------------------------------------------------

private fun fillsFile(context: android.content.Context, bucket: String, pageId: String): File {
    val dir = File(context.filesDir, "kids_coloring/$bucket").apply { mkdirs() }
    return File(dir, "$pageId.json")
}

private fun loadBoard(context: android.content.Context, bucket: String, pageId: String): BoardState {
    val file = fillsFile(context, bucket, pageId)
    if (!file.exists()) return BoardState.Empty
    return runCatching {
        val json = JSONObject(file.readText())
        if (!json.has("v")) return@runCatching BoardState(parseFills(json), emptyList())
        BoardState(
            fills = parseFills(json.optJSONObject("fills") ?: JSONObject()),
            strokes = parseStrokes(json.optJSONArray("strokes") ?: JSONArray()),
        )
    }.getOrDefault(BoardState.Empty)
}

private fun parseFills(json: JSONObject): Map<String, Color> = buildMap {
    val keys = json.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        put(key, Color(json.getInt(key)))
    }
}

private fun parseStrokes(array: JSONArray): List<Ink> = buildList {
    for (index in 0 until array.length()) {
        val item = array.getJSONObject(index)
        val pointsArray = item.getJSONArray("p")
        val points = buildList {
            var i = 0
            while (i + 1 < pointsArray.length()) {
                add(Offset(pointsArray.getDouble(i).toFloat(), pointsArray.getDouble(i + 1).toFloat()))
                i += 2
            }
        }
        if (points.size < 2) continue
        add(
            Ink(
                color = Color(item.getInt("c")),
                points = points,
                erase = item.optInt("e", 0) == 1,
                width = item.optDouble("w", 14.0).toFloat(),
            ),
        )
    }
}

private fun saveBoard(context: android.content.Context, bucket: String, pageId: String, state: BoardState) {
    val json = JSONObject()
    json.put("v", 2)
    val fills = JSONObject()
    state.fills.forEach { (id, color) -> fills.put(id, color.toArgb()) }
    json.put("fills", fills)
    val strokes = JSONArray()
    state.strokes.forEach { ink ->
        val item = JSONObject()
        item.put("c", ink.color.toArgb())
        item.put("e", if (ink.erase) 1 else 0)
        item.put("w", ink.width.toDouble())
        val points = JSONArray()
        ink.points.forEach { point ->
            points.put(point.x.toDouble())
            points.put(point.y.toDouble())
        }
        item.put("p", points)
        strokes.put(item)
    }
    json.put("strokes", strokes)
    fillsFile(context, bucket, pageId).writeText(json.toString())
}

// ---------------------------------------------------------------------------
// Экспорт в галерею
// ---------------------------------------------------------------------------

private fun exportColoring(
    context: android.content.Context,
    page: KidsColorPage,
    board: BoardState,
    outlines: List<PaintedOutline>,
) {
    val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.parseColor("#FFFBF5"))
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    if (outlines.isNotEmpty()) {
        outlines.forEach { region ->
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = board.fills[region.id]?.toArgb() ?: android.graphics.Color.WHITE
            canvas.drawPath(region.path, paint)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = android.graphics.Color.parseColor("#1F2937")
            canvas.drawPath(region.path, paint)
        }
    } else {
        page.regions.forEach { region ->
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = board.fills[region.id]?.toArgb() ?: android.graphics.Color.WHITE
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
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    board.strokes.forEach { ink ->
        if (ink.points.size < 2) return@forEach
        val path = android.graphics.Path()
        path.moveTo(ink.points.first().x, ink.points.first().y)
        ink.points.drop(1).forEach { path.lineTo(it.x, it.y) }
        strokePaint.strokeWidth = ink.width
        strokePaint.color = if (ink.erase) android.graphics.Color.parseColor("#FFFBF5") else ink.color.toArgb()
        canvas.drawPath(path, strokePaint)
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
