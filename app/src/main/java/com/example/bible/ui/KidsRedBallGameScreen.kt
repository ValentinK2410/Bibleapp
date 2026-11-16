package com.example.bible.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.example.bible.games.RedBallScene
import com.example.bible.games.RedBallSim
import com.example.bible.ui.theme.OutlinedTextField
import kotlinx.coroutines.Dispatchers
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
    var play by remember { mutableStateOf<PlayRequest?>(null) }
    var listRev by remember { mutableIntStateOf(0) }
    var warning by remember { mutableStateOf<String?>(null) }

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
        warning = null
        page = RedBallPage.EDITOR
    }

    fun closeEditor() {
        if (dirty() && !persist(showToast = true)) return
        page = if (fromList) RedBallPage.LEVELS else RedBallPage.MENU
    }

    fun beginPlay(scene: RedBallScene, test: Boolean) {
        play = PlayRequest(scene, test)
        page = RedBallPage.PLAY
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
            .background(Color(0xFFBFDBFE)),
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
                "Кати мяч до двери или собери свою сказочную горку.",
                color = Color(0xFF9A3412),
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(22.dp))
            MenuCard("Играть", "Готовая горка: ящик, пружина и дверь", Color(0xFFDC2626), onPlayBuiltin)
            Spacer(Modifier.height(12.dp))
            MenuCard("Мои уровни", "Сыграть, поправить, переименовать или удалить", Color(0xFFC2410C), onMyLevels)
            Spacer(Modifier.height(12.dp))
            MenuCard("Создать уровень", "Земля, горки, ящики, шипы и дверь", Color(0xFF15803D), onCreate)
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

@Composable
private fun RedBallPlay(scene: RedBallScene, testMode: Boolean, onExit: () -> Unit) {
    val sim = remember(scene) { RedBallSim(scene) }
    val held = remember { intArrayOf(0, 0) }
    var tick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(sim) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.033f)
                    val move = (held[1] - held[0]).toFloat()
                    sim.step(dt, move)
                }
                last = now
                tick++
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(tick) {
        if (sim.consumeLose()) KidsGameAudio.play(KidsSfx.LOSE)
        if (sim.consumeSpring()) KidsGameAudio.play(KidsSfx.PLACE)
        if (sim.consumeWin()) KidsGameAudio.play(KidsSfx.WIN)
    }
    if (!testMode) {
        KidsGameWinReward(KidsGames.RED_BALL, sim.won, points = 20, round = if (sim.won) scene.id.hashCode() else 0)
    }
    val title = when {
        testMode -> "Проверка · ${scene.name}"
        scene.builtin -> "Красный мяч"
        else -> scene.name
    }
    Box(Modifier.fillMaxSize().background(Color(0xFFBFDBFE))) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val frame = tick
            drawRedBallPlay(sim)
        }
        Row(
            Modifier
                .statusBarsPadding()
                .padding(start = 8.dp, top = 6.dp, end = 72.dp)
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.86f))
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color(0xFF7C2D12))
            }
            Text(title, fontWeight = FontWeight.Black, color = Color(0xFF7C2D12), fontSize = 16.sp, maxLines = 1)
        }
        IconButton(
            onClick = { sim.reset() },
            modifier = Modifier
                .statusBarsPadding()
                .padding(6.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.86f)),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Заново", tint = Color(0xFF7C2D12))
        }
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
                        sim.won && scene.builtin -> "Мяч докатился сам, как надо"
                        sim.won -> "Получилось! Мяч добрался до двери"
                        scene.builtin -> "Ящик закрывает яму, пружина подбрасывает наверх"
                        else -> "Шипы колются, а без земли мяч падает"
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
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HoldPad("◀", onHold = { held[0] = if (it) 1 else 0 })
            Text("держи", fontWeight = FontWeight.Black, color = Color(0xFF7C2D12), fontSize = 16.sp)
            HoldPad("▶", onHold = { held[1] = if (it) 1 else 0 })
        }
    }
}

@Composable
private fun HoldPad(label: String, onHold: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(92.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f))
            .pointerInput(onHold) {
                awaitEachGesture {
                    awaitFirstDown()
                    onHold(true)
                    waitForUpOrCancellation()
                    onHold(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color(0xFFB91C1C))
    }
}
