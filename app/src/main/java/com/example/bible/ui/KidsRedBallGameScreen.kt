package com.example.bible.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.KidsGames
import com.example.bible.games.KidsGameAudio
import com.example.bible.games.KidsSfx
import com.example.bible.games.RedBallDraft
import com.example.bible.games.RedBallLevelMeta
import com.example.bible.games.RedBallLevelStore
import com.example.bible.games.RedBallPiece
import com.example.bible.games.RedBallPortal
import com.example.bible.games.RedBallScene
import com.example.bible.games.RedBallSim
import com.example.bible.ui.theme.OutlinedTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RedBallPage { MENU, LEVELS, EDITOR, PLAY }

private class PlayRequest(val scene: RedBallScene, val test: Boolean)

@Composable
fun KidsRedBallGameScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { RedBallLevelStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(RedBallPage.MENU) }
    var draft by remember { mutableStateOf(RedBallDraft.fresh()) }
    var savedPieces by remember { mutableStateOf(draft.pieces) }
    var savedName by remember { mutableStateOf(draft.name) }
    var fromList by remember { mutableStateOf(false) }
    val undo = remember { ArrayDeque<List<RedBallPiece>>() }
    var tool by remember { mutableStateOf(RedBallTool.GROUND) }
    var camX by remember { mutableStateOf(0f) }
    var camY by remember { mutableStateOf(0f) }
    var play by remember { mutableStateOf<PlayRequest?>(null) }
    var listRev by remember { mutableIntStateOf(0) }
    var warning by remember { mutableStateOf<String?>(null) }
    val savedLevels by produceState<List<RedBallLevelMeta>?>(null, listRev, store) {
        value = withContext(Dispatchers.IO) { store.list() }
    }
    val destinations = remember(savedLevels, draft.id) {
        buildList {
            add(RedBallDestination(RedBallScene.BUILTIN_ID, "Горка"))
            add(RedBallDestination(draft.id, "Этот уровень"))
            for (level in savedLevels.orEmpty()) {
                if (level.id != draft.id) {
                    add(RedBallDestination(level.id, level.name.ifBlank { "Без имени" }))
                }
            }
        }
    }
    val portalCaution = savedLevels?.let { levels ->
        val known = levels.map { it.id }.toSet() + RedBallScene.BUILTIN_ID + draft.id
        draft.portalWarning(known)
    }

    fun cleanName(raw: String) = raw.trim().ifBlank { "Без имени" }.take(32)
    fun dirty() = draft.pieces != savedPieces || cleanName(draft.name) != cleanName(savedName)

    fun persist(showToast: Boolean): Boolean {
        return try {
            val stamped = draft.copy(name = cleanName(draft.name), updatedAt = System.currentTimeMillis())
            store.save(stamped)
            draft = stamped
            savedPieces = stamped.pieces
            savedName = stamped.name
            fromList = true
            listRev++
            if (showToast) {
                Toast.makeText(context, "Уровень сохранён", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (_: Exception) {
            Toast.makeText(context, "Не получилось сохранить", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun openDraft(next: RedBallDraft, listed: Boolean) {
        draft = next
        savedPieces = next.pieces
        savedName = next.name
        fromList = listed
        undo.clear()
        tool = RedBallTool.GROUND
        camX = 0f
        camY = 0f
        warning = null
        page = RedBallPage.EDITOR
    }

    fun closeEditor() {
        if (dirty() && !persist(showToast = true)) return
        page = if (fromList) RedBallPage.LEVELS else RedBallPage.MENU
    }

    fun beginPlay(scene: RedBallScene, test: Boolean) {
        scope.launch {
            val known = withContext(Dispatchers.IO) {
                store.list().map { it.id }.toSet() + RedBallScene.BUILTIN_ID + scene.id
            }
            play = PlayRequest(scene.withKnownPortals(known), test)
            page = RedBallPage.PLAY
        }
    }

    fun leavePlay() {
        val current = play
        page = when {
            current?.test == true -> RedBallPage.EDITOR
            current?.scene?.builtin == false -> RedBallPage.LEVELS
            else -> RedBallPage.MENU
        }
    }

    KidsGameMusic(com.example.bible.games.KidsMusicTrack.GAMES)
    BackHandler {
        when (page) {
            RedBallPage.MENU -> onBack()
            RedBallPage.LEVELS -> page = RedBallPage.MENU
            RedBallPage.EDITOR -> closeEditor()
            RedBallPage.PLAY -> leavePlay()
        }
    }

    when (page) {
        RedBallPage.MENU -> RedBallMenu(
            onBack = onBack,
            onPlayBuiltin = { beginPlay(RedBallScene.builtin(), test = false) },
            onMyLevels = { page = RedBallPage.LEVELS },
            onCreate = { openDraft(RedBallDraft.fresh(), listed = false) },
        )
        RedBallPage.LEVELS -> RedBallLevelList(
            store = store,
            revision = listRev,
            onBack = { page = RedBallPage.MENU },
            onPlay = { id ->
                scope.launch {
                    val loaded = withContext(Dispatchers.IO) { store.load(id) }
                    if (loaded == null) {
                        Toast.makeText(context, "Уровень не открылся", Toast.LENGTH_SHORT).show()
                        listRev++
                        return@launch
                    }
                    val reason = loaded.playBlockReason()
                    if (reason != null) {
                        Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    beginPlay(loaded.toScene(), test = false)
                }
            },
            onEdit = { id ->
                scope.launch {
                    val loaded = withContext(Dispatchers.IO) { store.load(id) }
                    if (loaded == null) {
                        Toast.makeText(context, "Уровень не открылся", Toast.LENGTH_SHORT).show()
                        listRev++
                    } else {
                        openDraft(loaded, listed = true)
                    }
                }
            },
            onChanged = { listRev++ },
        )
        RedBallPage.EDITOR -> KidsRedBallEditor(
            draft = draft,
            tool = tool,
            onTool = { tool = it },
            camX = camX,
            onCamX = { camX = it },
            camY = camY,
            onCamY = { camY = it },
            canUndo = undo.isNotEmpty(),
            onUndo = {
                val prev = undo.removeLastOrNull() ?: return@KidsRedBallEditor
                draft = draft.copy(pieces = prev)
                warning = null
            },
            onDraft = { draft = it },
            onCommit = { updated, snapshot ->
                if (snapshot != null && snapshot != updated.pieces) {
                    undo.addLast(snapshot)
                    while (undo.size > 40) undo.removeFirst()
                }
                draft = updated
                warning = null
            },
            warning = warning,
            caution = portalCaution,
            destinations = destinations,
            catalogReady = savedLevels != null,
            store = store,
            onBack = { closeEditor() },
            onTest = {
                val reason = draft.playBlockReason()
                if (reason != null) {
                    warning = reason
                    return@KidsRedBallEditor
                }
                if (dirty() && !persist(showToast = false)) return@KidsRedBallEditor
                beginPlay(draft.toScene(), test = true)
            },
            onSave = { persist(showToast = true) },
        )
        RedBallPage.PLAY -> {
            val request = play
            if (request != null) {
                RedBallPlay(
                    scene = request.scene,
                    testMode = request.test,
                    store = store,
                    onExit = { leavePlay() },
                )
            }
        }
    }
}

@Composable
private fun RedBallMenu(
    onBack: () -> Unit,
    onPlayBuiltin: () -> Unit,
    onMyLevels: () -> Unit,
    onCreate: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF7EC8F5)),
    ) {
        Canvas(Modifier.fillMaxSize()) { drawFairySky(0f, 1f) }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color(0xFF7C2D12))
            }
            Text(
                "Красный мяч",
                fontWeight = FontWeight.Black,
                fontSize = 34.sp,
                color = Color(0xFF7C2D12),
            )
            Text(
                "Кати мяч до двери, шагни в портал или собери свою горку.",
                color = Color(0xFF9A3412),
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(22.dp))
            MenuCard("Играть", "Готовая горка: ящик, пружина и дверь", Color(0xFFDC2626), onPlayBuiltin)
            Spacer(Modifier.height(12.dp))
            MenuCard("Мои уровни", "Сыграть, поправить, переименовать или удалить", Color(0xFFC2410C), onMyLevels)
            Spacer(Modifier.height(12.dp))
            MenuCard("Создать уровень", "Земля, горки, ящики, шипы, дверь и портал", Color(0xFF15803D), onCreate)
        }
    }
}

@Composable
private fun MenuCard(title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(color)
            .kidsPressable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, lineHeight = 19.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RedBallLevelList(
    store: RedBallLevelStore,
    revision: Int,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onEdit: (String) -> Unit,
    onChanged: () -> Unit,
) {
    val levels by produceState(emptyList<RedBallLevelMeta>(), revision, store) {
        value = withContext(Dispatchers.IO) { store.list() }
    }
    var rename by remember { mutableStateOf<RedBallLevelMeta?>(null) }
    var renameText by remember { mutableStateOf("") }
    var remove by remember { mutableStateOf<RedBallLevelMeta?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF7ED)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color(0xFF7C2D12))
                }
                Text("Мои уровни", fontWeight = FontWeight.Black, fontSize = 26.sp, color = Color(0xFF7C2D12))
            }
            if (levels.isEmpty()) {
                Text(
                    "Пока пусто. Нажми «Создать уровень» и собери свою горку.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = Color(0xFF9A3412),
                    fontSize = 16.sp,
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(levels, key = { it.id }) { level ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .padding(16.dp),
                    ) {
                        Text(level.name, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFF7C2D12))
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LevelChip("Играть", Color(0xFF15803D)) { onPlay(level.id) }
                            LevelChip("Править", Color(0xFFC2410C)) { onEdit(level.id) }
                            LevelChip("Имя", Color(0xFFB45309)) {
                                rename = level
                                renameText = level.name
                            }
                            LevelChip("Удалить", Color(0xFFBE123C)) { remove = level }
                        }
                    }
                }
            }
        }
    }
    val renaming = rename
    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text("Новое имя", fontWeight = FontWeight.Black) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(32) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = renaming.id
                    val name = renameText
                    rename = null
                    scope.launch {
                        withContext(Dispatchers.IO) { store.rename(id, name) }
                        onChanged()
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("Отмена") } },
        )
    }
    val removing = remove
    if (removing != null) {
        AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text("Удалить уровень?", fontWeight = FontWeight.Black) },
            text = { Text("«${removing.name}» пропадёт с этого телефона.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = removing.id
                    remove = null
                    scope.launch {
                        withContext(Dispatchers.IO) { store.delete(id) }
                        onChanged()
                    }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("Оставить") } },
        )
    }
}

@Composable
private fun LevelChip(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .kidsPressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
    }
}

private data class PortalArrival(
    val scene: RedBallScene,
    val x: Float,
    val y: Float,
    val token: Long,
)

private fun resolveTravel(store: RedBallLevelStore, portal: RedBallPortal): PortalArrival? {
    if (portal.linkId.isBlank()) return null
    val known = store.list().map { it.id }.toSet() + RedBallScene.BUILTIN_ID
    if (portal.linkId !in known) return null
    val draft: RedBallDraft?
    val loaded = if (portal.linkId == RedBallScene.BUILTIN_ID) {
        draft = null
        RedBallScene.builtin()
    } else {
        val found = store.load(portal.linkId) ?: return null
        draft = found
        found.toScene()
    }
    val ready = loaded.withKnownPortals(known)
    val (x, y) = portal.arrivalIn(ready, draft)
    return PortalArrival(ready, x, y, System.nanoTime())
}

@Composable
private fun RedBallPlay(
    scene: RedBallScene,
    testMode: Boolean,
    store: RedBallLevelStore,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    var arrival by remember { mutableStateOf<PortalArrival?>(null) }
    var travel by remember { mutableStateOf<RedBallPortal?>(null) }
    val fade = remember { Animatable(0f) }
    val zoom = remember { Animatable(1f) }
    val shown = arrival?.scene ?: scene
    val sim = remember(shown, arrival?.token) {
        val spot = arrival
        if (spot == null) RedBallSim(shown) else RedBallSim(spot.scene).also { it.arrive(spot.x, spot.y) }
    }
    val held = remember { intArrayOf(0, 0) }
    val paused = remember(sim) { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(sim) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L && !paused.value) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.033f)
                    val move = (held[1] - held[0]).toFloat()
                    sim.step(dt, move)
                }
                last = now
                if (!paused.value) tick++
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(tick) {
        if (sim.consumeLose()) KidsGameAudio.play(KidsSfx.LOSE)
        if (sim.consumeSpring()) KidsGameAudio.play(KidsSfx.PLACE)
        if (sim.consumeWin()) KidsGameAudio.play(KidsSfx.WIN)
        if (sim.consumeStomp()) KidsGameAudio.play(KidsSfx.CORRECT)
        if (sim.consumeStars() > 0) KidsGameAudio.play(KidsSfx.CORRECT)
        if (sim.consumeMagnet()) KidsGameAudio.play(KidsSfx.PLACE)
        sim.consumeJump()
        if (travel == null && fade.value == 0f) {
            val hit = sim.consumePortal()
            if (hit != null) travel = hit
        }
    }
    androidx.compose.runtime.LaunchedEffect(travel) {
        val portal = travel ?: return@LaunchedEffect
        val resolved = withContext(Dispatchers.IO) { resolveTravel(store, portal) }
        if (!isActive) return@LaunchedEffect
        if (resolved == null) {
            sim.dismissPortal()
            Toast.makeText(context, "Эта сцена пропала. Портал уснул", Toast.LENGTH_SHORT).show()
            travel = null
            return@LaunchedEffect
        }
        KidsGameAudio.play(KidsSfx.PLACE)
        coroutineScope {
            launch { fade.animateTo(1f, tween(340, easing = FastOutSlowInEasing)) }
            launch { zoom.animateTo(1.18f, tween(340, easing = FastOutSlowInEasing)) }
        }
        arrival = resolved
        zoom.snapTo(0.86f)
        coroutineScope {
            launch { fade.animateTo(0f, tween(380, easing = FastOutSlowInEasing)) }
            launch { zoom.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
        }
        travel = null
    }
    if (!testMode) {
        KidsGameWinReward(KidsGames.RED_BALL, sim.won, points = 20, round = if (sim.won) shown.id.hashCode() else 0)
    }
    val title = when {
        testMode -> "Проверка · ${shown.name}"
        shown.builtin -> "Красный мяч"
        else -> shown.name
    }
    val progress = if (sim.stars.isNotEmpty()) {
        sim.starsCollected / sim.stars.size.toFloat()
    } else {
        val span = (shown.door.l + shown.door.r) * 0.5f - shown.startX
        if (kotlin.math.abs(span) < 1f) 0f else ((sim.ballX - shown.startX) / span).coerceIn(0f, 1f)
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF7EC8F5))) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                    alpha = 1f - 0.2f * fade.value
                },
        ) {
            @Suppress("UNUSED_VARIABLE")
            val frame = tick
            drawRedBallPlay(sim)
        }
        if (fade.value > 0.01f) {
            Canvas(Modifier.fillMaxSize()) {
                val cover = fade.value
                drawCircle(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFFE9D5FF).copy(alpha = 0.08f * cover),
                            Color(0xFF6D28D9).copy(alpha = 0.55f * cover),
                            Color(0xFF1E1B4B).copy(alpha = 0.94f * cover),
                        ),
                        center = center,
                        radius = size.minDimension * (0.18f + 0.9f * cover),
                    ),
                    radius = size.maxDimension,
                    center = center,
                )
            }
        }
        Row(
            Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(3) {
                Canvas(Modifier.size(width = 36.dp, height = 32.dp)) {
                    drawHudHeart(filled = !sim.dead)
                }
            }
        }
        if (!sim.won && !sim.dead && travel == null) {
            PauseButton(
                Modifier
                    .statusBarsPadding()
                    .padding(top = 6.dp)
                    .align(Alignment.TopCenter),
                onClick = { paused.value = true },
            )
        }
        StarBar(
            progress = progress,
            magnetOn = sim.magnetTime > 0f,
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 10.dp, end = 12.dp)
                .align(Alignment.TopEnd),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArrowPad(left = true, onHold = { held[0] = if (it) 1 else 0 })
            ArrowPad(left = false, onHold = { held[1] = if (it) 1 else 0 })
        }
        JumpPad(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 14.dp),
            onHold = { down ->
                sim.jumpHeld = down
                if (down) sim.jump()
            },
        )
        if (sim.won || sim.dead) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.95f))
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (sim.won) "Дверь открыта!" else "Ещё разок",
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    color = Color(0xFF7F1D1D),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        sim.won && shown.builtin -> "Мяч докатился сам, как надо"
                        sim.won -> "Получилось! Мяч добрался до двери"
                        shown.builtin -> "Ящик закрывает яму, пружина подбрасывает наверх"
                        else -> "Шипы и злые квадраты сбоку опасны. Сверху квадрат можно раздавить"
                    },
                    color = Color(0xFF44403C),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFDC2626))
                            .kidsPressable(onClick = { sim.reset() })
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                    ) {
                        Text("Ещё раз", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                    if (testMode) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF15803D))
                                .kidsPressable(onClick = onExit)
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        ) {
                            Text("К редактору", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
        if (paused.value) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xAA16324A))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            waitForUpOrCancellation()?.consume()
                        }
                    },
            ) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White)
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(title, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color(0xFF7C2D12))
                    Spacer(Modifier.height(6.dp))
                    Text("Пауза", fontWeight = FontWeight.Black, fontSize = 28.sp, color = Color(0xFFE11D2E))
                    Spacer(Modifier.height(16.dp))
                    PauseChoice("Дальше", Color(0xFF15803D)) { paused.value = false }
                    Spacer(Modifier.height(8.dp))
                    PauseChoice("Заново", Color(0xFFDC2626)) {
                        sim.reset()
                        paused.value = false
                    }
                    Spacer(Modifier.height(8.dp))
                    PauseChoice(if (testMode) "К редактору" else "Выход", Color(0xFFB45309), onExit)
                }
            }
        }
    }
}

@Composable
private fun PauseButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(54.dp)
            .clip(CircleShape)
            .border(3.dp, Color.White.copy(alpha = 0.95f), CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .semantics { contentDescription = "Пауза" }
            .kidsPressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                Modifier
                    .size(width = 5.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White),
            )
            Box(
                Modifier
                    .size(width = 5.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun StarBar(progress: Float, magnetOn: Boolean, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(40.dp)) { drawHudStarMedal(magnetOn) }
        Box(
            Modifier
                .padding(start = 4.dp)
                .width(112.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFE4E8EC).copy(alpha = 0.92f))
                .border(
                    2.dp,
                    if (magnetOn) Color(0xFF7DD3FC) else Color.White.copy(alpha = 0.95f),
                    RoundedCornerShape(999.dp),
                ),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFFFFE38A), Color(0xFFF5B431))),
                    ),
            )
        }
    }
}

@Composable
private fun PauseChoice(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .kidsPressable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

@Composable
private fun JumpPad(modifier: Modifier, onHold: (Boolean) -> Unit) {
    val hold = rememberUpdatedState(onHold)
    Box(
        modifier
            .size(104.dp)
            .semantics { contentDescription = "Прыжок" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    hold.value(true)
                    waitForUpOrCancellation()?.consume()
                    hold.value(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val r = size.minDimension / 2f - stroke
            val center = this.center
            drawCircle(Color.White.copy(alpha = 0.14f), r, center)
            drawCircle(Color.White.copy(alpha = 0.92f), r, center, style = Stroke(stroke))
            val s = r * 0.42f
            val path = Path().apply {
                moveTo(center.x - s, center.y + s * 0.2f)
                lineTo(center.x, center.y - s * 0.65f)
                lineTo(center.x + s, center.y + s * 0.2f)
            }
            drawPath(
                path,
                Color.White.copy(alpha = 0.95f),
                style = Stroke(width = stroke * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun ArrowPad(left: Boolean, onHold: (Boolean) -> Unit) {
    val hold = rememberUpdatedState(onHold)
    Box(
        Modifier
            .size(92.dp)
            .semantics { contentDescription = if (left) "Влево" else "Вправо" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    hold.value(true)
                    waitForUpOrCancellation()?.consume()
                    hold.value(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val inset = size.minDimension * 0.16f
            val path = Path().apply {
                if (left) {
                    moveTo(size.width - inset, inset)
                    lineTo(inset, size.height / 2f)
                    lineTo(size.width - inset, size.height - inset)
                } else {
                    moveTo(inset, inset)
                    lineTo(size.width - inset, size.height / 2f)
                    lineTo(inset, size.height - inset)
                }
                close()
            }
            drawPath(path, Color.White.copy(alpha = 0.14f))
            drawPath(
                path,
                Color.White.copy(alpha = 0.92f),
                style = Stroke(width = stroke, join = StrokeJoin.Round),
            )
        }
    }
}
