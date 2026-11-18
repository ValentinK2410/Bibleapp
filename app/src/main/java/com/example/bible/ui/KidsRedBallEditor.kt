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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import com.example.bible.games.RedBallLevelStore
import com.example.bible.games.RedBallPiece
import com.example.bible.games.RedBallPieceKind
import com.example.bible.games.RedBallPortalRole
import com.example.bible.games.RedBallScene
import com.example.bible.games.RedBallTune
import com.example.bible.games.skins.RedBallSkinBitmaps
import com.example.bible.games.skins.RedBallSkinId
import com.example.bible.games.nextPortalName
import com.example.bible.games.normalizePortalName
import com.example.bible.games.snapAnchor
import com.example.bible.games.tune
import com.example.bible.ui.theme.OutlinedTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal data class RedBallDestination(val id: String, val title: String)

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
    PORTAL(RedBallPieceKind.PORTAL, false, "🟣", "Портал", Color(0xFF6D28D9)),
    WATER(RedBallPieceKind.WATER, false, "💧", "Вода", Color(0xFF0284C7)),
    CART(RedBallPieceKind.CART, false, "🛒", "Тележка", Color(0xFF92400E)),
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
    caution: String?,
    destinations: List<RedBallDestination>,
    catalogReady: Boolean,
    familyCaption: String,
    nestedCount: Int,
    reservedPortalNames: Set<String>,
    store: RedBallLevelStore,
    onManageScenes: () -> Unit,
    onBack: () -> Unit,
    skinId: RedBallSkinId,
    skin: RedBallSkinBitmaps?,
    onSkin: (RedBallSkinId) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    val draftState = rememberUpdatedState(draft)
    val onDraftState = rememberUpdatedState(onDraft)
    val onCommitState = rememberUpdatedState(onCommit)
    val onCamXState = rememberUpdatedState(onCamX)
    val onCamYState = rememberUpdatedState(onCamY)
    val onToolState = rememberUpdatedState(onTool)
    val camXState = rememberUpdatedState(camX)
    val camYState = rememberUpdatedState(camY)
    val reservedNames = rememberUpdatedState(reservedPortalNames)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var hover by remember { mutableStateOf(Hover(0, 0, 0, 0, false)) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var portalTime by remember { mutableFloatStateOf(0.8f) }
    val hasPortal = draft.pieces.any { it.kind == RedBallPieceKind.PORTAL }
    if (hasPortal) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            var last = 0L
            while (isActive) {
                withFrameNanos { now ->
                    if (last != 0L) {
                        portalTime += ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    }
                    last = now
                }
            }
        }
    }
    val lostPortalIds = if (!catalogReady) {
        emptySet()
    } else {
        draft.pieces.mapNotNull { piece ->
            if (
                piece.kind == RedBallPieceKind.PORTAL &&
                piece.linkId.isNotBlank() &&
                destinations.none { it.id == piece.linkId }
            ) {
                piece.id
            } else {
                null
            }
        }.toSet()
    }
    val brokenExitIds by produceState(emptySet<Long>(), draft.pieces, catalogReady, draft.id) {
        if (!catalogReady) {
            value = emptySet()
            return@produceState
        }
        val snapshot = draft
        value = withContext(Dispatchers.IO) {
            val ids = mutableSetOf<Long>()
            for (piece in snapshot.pieces) {
                if (piece.kind != RedBallPieceKind.PORTAL || piece.role != RedBallPortalRole.ENTRY) continue
                if (piece.exitId.isBlank()) continue
                if (piece.linkId.isBlank() || piece.linkId == RedBallScene.BUILTIN_ID) {
                    ids += piece.id
                    continue
                }
                val names = if (piece.linkId == snapshot.id) {
                    snapshot.exitPortalNames()
                } else {
                    store.load(piece.linkId)?.exitPortalNames().orEmpty()
                }
                if (piece.exitId !in names) ids += piece.id
            }
            ids
        }
    }
    val markedPortals = lostPortalIds + brokenExitIds

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
                "Вход ведёт в сцену и в её выход. Выход — место, куда мяч появляется: зайти в него нельзя. Вложенные сцены — кнопка «Сцены».",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color(0xFF7C2D12),
                fontSize = 13.sp,
                lineHeight = 16.sp,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    familyCaption,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF9A3412),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF6D28D9))
                        .kidsPressable(onClick = onManageScenes)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        if (nestedCount > 0) "Сцены · $nestedCount" else "Сцены",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                    )
                }
            }
            RedBallSkinPicker(
                selected = skinId,
                onSelect = onSkin,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
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
            if (caution != null) {
                Text(
                    caution,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFEF3C7))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color(0xFF92400E),
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
                                    else if (hit.kind == RedBallPieceKind.PORTAL) {
                                        selectedId = hit.id
                                        onToolState.value(RedBallTool.PROPS)
                                    }
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
                                    val next = working.withPlaced(
                                        kind,
                                        cell.first,
                                        cell.second,
                                        tool.flip,
                                        reservedNames.value,
                                    )
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
                                    val placed = start.withPlaced(
                                        kind,
                                        cell.first,
                                        cell.second,
                                        tool.flip,
                                        reservedNames.value,
                                    )
                                    if (placed.pieces != start.pieces) {
                                        onCommitState.value(placed, start.pieces)
                                        if (kind == RedBallPieceKind.PORTAL) {
                                            val added = placed.pieces.firstOrNull { piece ->
                                                start.pieces.none { it.id == piece.id }
                                            }
                                            if (added != null) {
                                                selectedId = added.id
                                                onToolState.value(RedBallTool.PROPS)
                                            }
                                        }
                                    }
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
                        portalTime,
                        lostPortalIds = markedPortals,
                        skin = skin,
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
                val selected = draft.pieces.firstOrNull { it.id == selectedId }
                if (selected?.kind == RedBallPieceKind.PORTAL) {
                    PortalLinkPanel(
                        draft = draft,
                        selected = selected,
                        destinations = destinations,
                        catalogReady = catalogReady,
                        reservedPortalNames = reservedPortalNames,
                        store = store,
                        onCommit = onCommit,
                    )
                } else {
                    PropsPanel(
                        draft = draft,
                        selected = selected,
                        onDraft = onDraft,
                        onCommit = onCommit,
                    )
                }
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
    RedBallPieceKind.PORTAL -> "Портал"
    RedBallPieceKind.WATER -> "Вода"
    RedBallPieceKind.CART -> "Тележка"
}

private data class PortalTarget(val draft: RedBallDraft?, val ready: Boolean)

@Composable
private fun PortalLinkPanel(
    draft: RedBallDraft,
    selected: RedBallPiece,
    destinations: List<RedBallDestination>,
    catalogReady: Boolean,
    reservedPortalNames: Set<String>,
    store: RedBallLevelStore,
    onCommit: (RedBallDraft, List<RedBallPiece>?) -> Unit,
) {
    val link = selected.linkId
    val known = destinations.any { it.id == link }
    val entry = selected.role != RedBallPortalRole.EXIT
    var nameText by remember(selected.id) { mutableStateOf(selected.portalName) }
    val target by produceState(PortalTarget(null, false), link, draft.id, draft.pieces) {
        value = when {
            link.isBlank() || link == RedBallScene.BUILTIN_ID -> PortalTarget(null, true)
            link == draft.id -> PortalTarget(draft, true)
            else -> PortalTarget(withContext(Dispatchers.IO) { store.load(link) }, true)
        }
    }
    val cleanName = normalizePortalName(nameText)
    val nameClash = cleanName.isNotBlank() &&
        cleanName != selected.portalName &&
        portalNameClash(draft, selected.id, cleanName, reservedPortalNames)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text("Портал", color = Color(0xFF5B21B6), fontWeight = FontWeight.Black, fontSize = 14.sp)
        OutlinedTextField(
            value = nameText,
            onValueChange = { raw ->
                val shown = raw.take(24)
                nameText = shown
                val clean = normalizePortalName(shown)
                if (clean.isBlank() || clean == selected.portalName) return@OutlinedTextField
                if (portalNameClash(draft, selected.id, clean, reservedPortalNames)) return@OutlinedTextField
                onCommit(draft.withPiece(selected.id) { it.copy(portalName = clean) }, draft.pieces)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            singleLine = true,
            placeholder = { Text("Имя в этой семье") },
        )
        if (nameClash) {
            Text(
                "Такое имя уже есть в этой семье",
                color = Color(0xFF991B1B),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpawnChip("Вход", entry) {
                if (!entry) {
                    val renamed = renamedForRole(draft, selected, RedBallPortalRole.ENTRY, reservedPortalNames)
                    nameText = renamed
                    onCommit(
                        draft.withPiece(selected.id) {
                            it.copy(role = RedBallPortalRole.ENTRY, portalName = renamed)
                        },
                        draft.pieces,
                    )
                }
            }
            SpawnChip("Выход", !entry) {
                if (entry) {
                    val renamed = renamedForRole(draft, selected, RedBallPortalRole.EXIT, reservedPortalNames)
                    nameText = renamed
                    onCommit(
                        draft.withPiece(selected.id) {
                            it.copy(
                                role = RedBallPortalRole.EXIT,
                                portalName = renamed,
                                linkId = "",
                                exitId = "",
                                spawnC = null,
                                spawnR = null,
                            )
                        },
                        draft.pieces,
                    )
                }
            }
        }
        if (!entry) {
            Text(
                "Сюда мяч появляется из входа. В выход зайти нельзя.",
                modifier = Modifier.padding(top = 6.dp),
                color = Color(0xFF115E59),
                fontSize = 13.sp,
            )
            return@Column
        }
        Text(
            "Куда ведёт вход",
            modifier = Modifier.padding(top = 8.dp),
            color = Color(0xFF5B21B6),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        if (!catalogReady) {
            Text("Сцены ещё читаются…", color = Color(0xFF57534E), fontSize = 13.sp)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (dest in destinations) {
                val on = dest.id == link
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (on) Color(0xFF6D28D9) else Color(0xFFEDE9FE))
                        .kidsPressable(onClick = {
                            if (!on) {
                                onCommit(
                                    draft.withPiece(selected.id) {
                                        it.copy(linkId = dest.id, exitId = "", spawnC = null, spawnR = null)
                                    },
                                    draft.pieces,
                                )
                            }
                        })
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        dest.title.take(22),
                        color = if (on) Color.White else Color(0xFF5B21B6),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        val legacyCell = selected.spawnC != null && selected.spawnR != null && selected.exitId.isBlank()
        val exits = target.draft?.pieces?.filter {
            it.kind == RedBallPieceKind.PORTAL &&
                it.role == RedBallPortalRole.EXIT &&
                it.portalName.isNotBlank()
        }.orEmpty()
        val exitKnown = selected.exitId.isNotBlank() && exits.any { it.portalName == selected.exitId }
        val hint = when {
            link.isBlank() -> "Портал спит, пока не выбрана сцена"
            !known && catalogReady -> "Этой сцены больше нет. Портал спит"
            link == RedBallScene.BUILTIN_ID -> "Мяч появится на старте горки"
            selected.exitId.isNotBlank() && target.ready && !exitKnown ->
                "Выхода «${selected.exitId}» больше нет — мяч появится на старте"
            exitKnown -> "Мяч появится у выхода «${selected.exitId}»"
            legacyCell -> "Раньше мяч появлялся в клетке ${selected.spawnC}, ${selected.spawnR}. Выбери выход или старт"
            else -> "Мяч появится на старте выбранной сцены"
        }
        val hintAlert = (link.isNotBlank() && !known && catalogReady) ||
            (selected.exitId.isNotBlank() && target.ready && !exitKnown && link != RedBallScene.BUILTIN_ID)
        Text(
            hint,
            modifier = Modifier.padding(top = 6.dp),
            color = if (hintAlert) Color(0xFF991B1B) else Color(0xFF57534E),
            fontSize = 13.sp,
        )
        if (link.isNotBlank() && link != RedBallScene.BUILTIN_ID && (known || link == draft.id)) {
            if (!target.ready) {
                Text("Выходы читаются…", color = Color(0xFF57534E), fontSize = 13.sp)
            } else {
                Text(
                    "Где появиться",
                    modifier = Modifier.padding(top = 6.dp),
                    color = Color(0xFF115E59),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val atStart = selected.exitId.isBlank() && !legacyCell
                    SpawnChip("На старте", atStart) {
                        if (!atStart) {
                            onCommit(
                                draft.withPiece(selected.id) {
                                    it.copy(exitId = "", spawnC = null, spawnR = null)
                                },
                                draft.pieces,
                            )
                        }
                    }
                    for (exit in exits) {
                        val on = exit.portalName == selected.exitId
                        SpawnChip(exit.portalName, on) {
                            if (!on) {
                                onCommit(
                                    draft.withPiece(selected.id) {
                                        it.copy(exitId = exit.portalName, spawnC = null, spawnR = null)
                                    },
                                    draft.pieces,
                                )
                            }
                        }
                    }
                }
                if (exits.isEmpty()) {
                    Text(
                        "В этой сцене нет выходов — мяч появится на старте",
                        modifier = Modifier.padding(top = 4.dp),
                        color = Color(0xFF57534E),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

private fun portalNameClash(
    draft: RedBallDraft,
    pieceId: Long,
    name: String,
    reserved: Set<String>,
): Boolean {
    if (name.isBlank()) return false
    if (name in reserved) return true
    return draft.pieces.any { piece ->
        piece.kind == RedBallPieceKind.PORTAL && piece.id != pieceId && piece.portalName == name
    }
}

private fun renamedForRole(
    draft: RedBallDraft,
    piece: RedBallPiece,
    role: RedBallPortalRole,
    reserved: Set<String>,
): String {
    val current = piece.portalName
    val from = if (role == RedBallPortalRole.EXIT) "Вход " else "Выход "
    val prefix = if (role == RedBallPortalRole.EXIT) "Выход " else "Вход "
    val candidate = if (current.startsWith(from)) prefix + current.removePrefix(from) else current
    val resolved = candidate.ifBlank { nextPortalName(draft.portalNames() + reserved, role) }
    return if (portalNameClash(draft, piece.id, resolved, reserved)) {
        current.ifBlank { nextPortalName(draft.portalNames() + reserved + resolved, role) }
    } else {
        resolved
    }
}

@Composable
private fun SpawnChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) Color(0xFF0F766E) else Color(0xFFCCFBF1))
            .kidsPressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (on) Color.White else Color(0xFF115E59),
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
        )
    }
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
