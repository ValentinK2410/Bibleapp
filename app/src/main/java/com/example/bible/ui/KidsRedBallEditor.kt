package com.example.bible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.games.RedBallDraft
import com.example.bible.games.RedBallPiece
import com.example.bible.games.RedBallPieceKind
import com.example.bible.games.RedBallTune
import com.example.bible.games.snapAnchor
import com.example.bible.games.tune
import com.example.bible.ui.theme.OutlinedTextField

internal enum class RedBallTool(val kind: RedBallPieceKind?, val flip: Boolean, val emoji: String, val label: String, val tint: Color) {
    GROUND(RedBallPieceKind.GROUND, false, "🟩", "Земля", Color(0xFF15803D)),
    CONCRETE(RedBallPieceKind.CONCRETE, false, "🪨", "Бетон", Color(0xFF57534E)),
    SLOPE_RIGHT(RedBallPieceKind.SLOPE, false, "↘️", "Горка →", Color(0xFFB45309)),
    SLOPE_LEFT(RedBallPieceKind.SLOPE, true, "↙️", "Горка ←", Color(0xFFB45309)),
    LADDER(RedBallPieceKind.LADDER, false, "🪜", "Лестница", Color(0xFFB45309)),
    CRATE(RedBallPieceKind.CRATE, false, "📦", "Ящик", Color(0xFFC4843A)),
    SPRING(RedBallPieceKind.SPRING, false, "🌀", "Пружина", Color(0xFFCA8A04)),
    SPIKE(RedBallPieceKind.SPIKE, false, "🔺", "Шипы", Color(0xFF57534E)),
    STAR(RedBallPieceKind.STAR, false, "⭐", "Звезда", Color(0xFFCA8A04)),
    MAGNET(RedBallPieceKind.MAGNET, false, "🧲", "Магнит", Color(0xFF0369A1)),
    ENEMY(RedBallPieceKind.ENEMY, false, "😡", "Враг", Color(0xFFBE123C)),
    START(RedBallPieceKind.START, false, "🔴", "Старт", Color(0xFFDC2626)),
    DOOR(RedBallPieceKind.DOOR, false, "🚪", "Дверь", Color(0xFF7C3AED)),
    PROPS(null, false, "⚙️", "Свойства", Color(0xFF0F766E)),
    ERASE(null, false, "🧽", "Ластик", Color(0xFFBE185D)),
}

@Composable
internal fun KidsRedBallEditor(
    draft: RedBallDraft,
    tool: RedBallTool,
    onTool: (RedBallTool) -> Unit,
    camX: Float,
    onCamX: (Float) -> Unit,
    camY: Float,
    onCamY: (Float) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDraft: (RedBallDraft) -> Unit,
    onCommit: (RedBallDraft, List<RedBallPiece>?) -> Unit,
    warning: String?,
    onBack: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    val draftState = rememberUpdatedState(draft)
    val onDraftState = rememberUpdatedState(onDraft)
    val onCommitState = rememberUpdatedState(onCommit)
    val onCamXState = rememberUpdatedState(onCamX)
    val onCamYState = rememberUpdatedState(onCamY)
    val camXState = rememberUpdatedState(camX)
    val camYState = rememberUpdatedState(camY)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var hover by remember { mutableStateOf(Hover(0, 0, 0, 0, false)) }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF7EC8F5)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color(0xFF7C2D12))
                }
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraft(draft.copy(name = it.take(32))) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Имя уровня") },
                )
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Отменить",
                        tint = if (canUndo) Color(0xFF7C2D12) else Color(0xFF7C2D12).copy(alpha = 0.35f),
                    )
                }
            }
            Text(
                "Землю, бетон, лестницы и шипы веди пальцем. Стрелки листают поле во все стороны. «Свойства» — гравитация, толчок, трение и масса.",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color(0xFF7C2D12),
                fontSize = 13.sp,
                lineHeight = 16.sp,
            )
            if (warning != null) {
                Text(
                    warning,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFEE2E2))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color(0xFF991B1B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { canvasSize = it },
            ) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(tool) {
                            val slop = viewConfiguration.touchSlop
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val start = draftState.value
                                val scale = editorScale(size.height.toFloat(), start.cell)
                                fun clampedCam(): Pair<Float, Float> {
                                    val range = editorCamRange(size.width.toFloat(), size.height.toFloat(), draftState.value)
                                    val x = camXState.value.coerceIn(range.minX, range.maxX)
                                    val y = camYState.value.coerceIn(range.minY, range.maxY)
                                    return x to y
                                }
                                fun world(p: Offset): Offset {
                                    val (cx, cy) = clampedCam()
                                    return Offset(p.x / scale + cx, p.y / scale + cy)
                                }
                                val startWorld = world(down.position)
                                if (tool == RedBallTool.PROPS) {
                                    selectedId = start.hitPiece(startWorld.x, startWorld.y)?.id
                                    hover = Hover(0, 0, 0, 0, false)
                                    return@awaitEachGesture
                                }
                                val painting = tool == RedBallTool.GROUND || tool == RedBallTool.SPIKE ||
                                    tool == RedBallTool.CONCRETE || tool == RedBallTool.LADDER
                                if (tool == RedBallTool.ERASE) {
                                    var working = start
                                    fun erase(at: Offset) {
                                        val next = working.withErasedAt(at.x, at.y)
                                        if (next.pieces != working.pieces) {
                                            working = next
                                            onDraftState.value(working)
                                        }
                                    }
                                    erase(startWorld)
                                    drag(down.id) { change ->
                                        change.consume()
                                        erase(world(change.position))
                                    }
                                    if (working.pieces != start.pieces) onCommitState.value(working, start.pieces)
                                    hover = Hover(0, 0, 0, 0, false)
                                    return@awaitEachGesture
                                }
                                val hit = start.hitPiece(startWorld.x, startWorld.y)
                                if (hit != null) {
                                    val startCell = start.cellOf(startWorld.x, startWorld.y)
                                    var working = start
                                    drag(down.id) { change ->
                                        change.consume()
                                        val cell = start.cellOf(world(change.position).x, world(change.position).y)
                                        val moved = start.withMoved(
                                            hit.id,
                                            hit.c + cell.first - startCell.first,
                                            hit.r + cell.second - startCell.second,
                                        )
                                        if (moved.pieces != working.pieces) {
                                            working = moved
                                            onDraftState.value(working)
                                        }
                                    }
                                    if (working.pieces != start.pieces) onCommitState.value(working, start.pieces)
                                    hover = Hover(0, 0, 0, 0, false)
                                    return@awaitEachGesture
                                }
                                val (originCamX, originCamY) = clampedCam()
                                var moved = false
                                var working = start
                                val kind = tool.kind
                                fun paintAt(at: Offset) {
                                    if (kind == null) return
                                    val cell = start.cellOf(at.x, at.y)
                                    val (cc, rr) = kind.snapAnchor(cell.first, cell.second)
                                    hover = Hover(cc, rr, kind.w, kind.h, true)
                                    val next = working.withPlaced(kind, cell.first, cell.second, tool.flip)
                                    if (next.pieces != working.pieces) {
                                        working = next
                                        onDraftState.value(working)
                                    }
                                }
                                drag(down.id) { change ->
                                    val delta = change.position - down.position
                                    if (!moved && delta.getDistance() > slop) {
                                        moved = true
                                        if (painting) paintAt(startWorld)
                                    }
                                    if (painting && kind != null && moved) {
                                        paintAt(world(change.position))
                                    } else if (moved) {
                                        val range = editorCamRange(size.width.toFloat(), size.height.toFloat(), start)
                                        val nextX = (originCamX - (change.position.x - down.position.x) / scale)
                                            .coerceIn(range.minX, range.maxX)
                                        val nextY = (originCamY - (change.position.y - down.position.y) / scale)
                                            .coerceIn(range.minY, range.maxY)
                                        onCamXState.value(nextX)
                                        onCamYState.value(nextY)
                                    }
                                    change.consume()
                                }
                                if (!moved && kind != null) {
                                    val cell = start.cellOf(startWorld.x, startWorld.y)
                                    val placed = start.withPlaced(kind, cell.first, cell.second, tool.flip)
                                    if (placed.pieces != start.pieces) onCommitState.value(placed, start.pieces)
                                } else if (working.pieces != start.pieces) {
                                    onCommitState.value(working, start.pieces)
                                }
                                hover = Hover(0, 0, 0, 0, false)
                            }
                        },
                ) {
                    drawRedBallEditor(
                        draft,
                        camX,
                        camY,
                        hover.c,
                        hover.r,
                        hover.w,
                        hover.h,
                        hover.show,
                        if (tool == RedBallTool.PROPS) selectedId else null,
                    )
                }
                ScrollNub("▲", "Вверх", Modifier.align(Alignment.TopCenter)) {
                    nudgeCam(canvasSize, draft, camX, camY, 0f, -draft.cell * 4f, onCamX, onCamY)
                }
                ScrollNub("▼", "Вниз", Modifier.align(Alignment.BottomCenter)) {
                    nudgeCam(canvasSize, draft, camX, camY, 0f, draft.cell * 4f, onCamX, onCamY)
                }
                ScrollNub("◀", "Влево", Modifier.align(Alignment.CenterStart)) {
                    nudgeCam(canvasSize, draft, camX, camY, -draft.cell * 4f, 0f, onCamX, onCamY)
                }
                ScrollNub("▶", "Вправо", Modifier.align(Alignment.CenterEnd)) {
                    nudgeCam(canvasSize, draft, camX, camY, draft.cell * 4f, 0f, onCamX, onCamY)
                }
            }
            if (tool == RedBallTool.PROPS) {
                PropsPanel(
                    draft = draft,
                    selected = draft.pieces.firstOrNull { it.id == selectedId },
                    onDraft = onDraft,
                    onCommit = onCommit,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorAction("Проверить", Color(0xFFDC2626), Modifier.weight(1f), onTest)
                EditorAction("Сохранить", Color(0xFF15803D), Modifier.weight(1f), onSave)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (item in RedBallTool.entries) {
                    val selected = item == tool
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(width = 68.dp, height = 52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) item.tint else item.tint.copy(alpha = 0.78f))
                                .then(
                                    if (selected) Modifier.background(Color.White.copy(alpha = 0.18f)) else Modifier,
                                )
                                .kidsPressable(onClick = { onTool(item) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(item.emoji, fontSize = 24.sp)
                        }
                        Text(
                            item.label,
                            color = Color(0xFF7C2D12),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PropsPanel(
    draft: RedBallDraft,
    selected: RedBallPiece?,
    onDraft: (RedBallDraft) -> Unit,
    onCommit: (RedBallDraft, List<RedBallPiece>?) -> Unit,
) {
    val propBase = remember { mutableStateOf<List<RedBallPiece>?>(null) }
    val staged = remember { mutableStateOf<RedBallDraft?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (selected == null) {
            Text("Коснись предмета на поле", color = Color(0xFF7C2D12), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            return@Column
        }
        val tune = selected.kind.tune()
        Text(
            selected.kind.label(),
            color = Color(0xFF7C2D12),
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
        )
        if (tune == RedBallTune.NONE) {
            Text("У этого предмета нет физики", color = Color(0xFF57534E), fontSize = 13.sp)
            return@Column
        }
        if (tune == RedBallTune.FULL) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Гравитация", modifier = Modifier.weight(1f), color = Color(0xFF44403C), fontSize = 14.sp)
                Switch(
                    checked = selected.gravity,
                    onCheckedChange = { value ->
                        onCommit(draft.withPiece(selected.id) { it.copy(gravity = value) }, draft.pieces)
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Можно толкать", modifier = Modifier.weight(1f), color = Color(0xFF44403C), fontSize = 14.sp)
                Switch(
                    checked = selected.movable,
                    onCheckedChange = { value ->
                        onCommit(draft.withPiece(selected.id) { it.copy(movable = value) }, draft.pieces)
                    },
                )
            }
        }
        Text(
            "Трение ${"%.2f".format(selected.friction)}",
            color = Color(0xFF44403C),
            fontSize = 13.sp,
        )
        Slider(
            value = selected.friction,
            onValueChange = { value ->
                if (propBase.value == null) propBase.value = draft.pieces
                val next = draft.withPiece(selected.id) { it.copy(friction = value) }
                staged.value = next
                onDraft(next)
            },
            onValueChangeFinished = {
                val base = propBase.value ?: return@Slider
                val next = staged.value ?: return@Slider
                staged.value = null
                propBase.value = null
                onCommit(next, base)
            },
            valueRange = 0f..1f,
        )
        if (tune == RedBallTune.FULL) {
            Text(
                "Масса ${"%.1f".format(selected.mass)}",
                color = Color(0xFF44403C),
                fontSize = 13.sp,
            )
            Slider(
                value = selected.mass,
                onValueChange = { value ->
                    if (propBase.value == null) propBase.value = draft.pieces
                    val next = draft.withPiece(selected.id) { it.copy(mass = value) }
                    staged.value = next
                    onDraft(next)
                },
                onValueChangeFinished = {
                    val base = propBase.value ?: return@Slider
                    val next = staged.value ?: return@Slider
                    staged.value = null
                    propBase.value = null
                    onCommit(next, base)
                },
                valueRange = 0.3f..6f,
            )
        }
    }
}

private fun RedBallPieceKind.label(): String = when (this) {
    RedBallPieceKind.GROUND -> "Земля"
    RedBallPieceKind.CONCRETE -> "Бетон"
    RedBallPieceKind.SLOPE -> "Горка"
    RedBallPieceKind.LADDER -> "Лестница"
    RedBallPieceKind.CRATE -> "Ящик"
    RedBallPieceKind.SPRING -> "Пружина"
    RedBallPieceKind.SPIKE -> "Шипы"
    RedBallPieceKind.STAR -> "Звезда"
    RedBallPieceKind.MAGNET -> "Магнит"
    RedBallPieceKind.ENEMY -> "Злой квадрат"
    RedBallPieceKind.START -> "Старт"
    RedBallPieceKind.DOOR -> "Дверь"
}

private fun nudgeCam(
    canvas: IntSize,
    draft: RedBallDraft,
    camX: Float,
    camY: Float,
    dx: Float,
    dy: Float,
    onCamX: (Float) -> Unit,
    onCamY: (Float) -> Unit,
) {
    val range = editorCamRange(canvas.width.toFloat(), canvas.height.toFloat(), draft)
    onCamX((camX + dx).coerceIn(range.minX, range.maxX))
    onCamY((camY + dy).coerceIn(range.minY, range.maxY))
}

@Composable
private fun ScrollNub(label: String, description: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .padding(6.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.86f))
            .semantics { contentDescription = description }
            .kidsPressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF7C2D12))
    }
}

@Composable
private fun EditorAction(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .kidsPressable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
    }
}

private data class Hover(val c: Int, val r: Int, val w: Int, val h: Int, val show: Boolean)
