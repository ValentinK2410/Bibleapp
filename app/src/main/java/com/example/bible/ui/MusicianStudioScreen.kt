package com.example.bible.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import java.io.File
import androidx.compose.runtime.rememberCoroutineScope
import com.example.bible.data.AppUsageEvents
import com.example.bible.data.BiblePreferences
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import com.example.bible.audio.StudioMixer
import com.example.bible.data.StudioBoard
import com.example.bible.data.StudioClip
import com.example.bible.data.StudioCycle
import com.example.bible.data.StudioLibrary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val PadColors = listOf(
    Color(0xFFEF4444),
    Color(0xFFF97316),
    Color(0xFFEAB308),
    Color(0xFF22C55E),
    Color(0xFF06B6D4),
    Color(0xFF3B82F6),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicianStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { StudioEngine(context.applicationContext) }
    val padFiles = remember { Array(StudioLibrary.PAD_COUNT) { StudioLibrary.padFile(context, it) } }
    var board by remember { mutableStateOf(StudioLibrary.loadBoard(context)) }
    var cycle by remember { mutableStateOf(StudioLibrary.loadCycle(context)) }
    var padTick by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(emptySet<String>()) }
    var recording by remember { mutableStateOf<String?>(null) }
    var pendingRecord by remember { mutableStateOf<String?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var clock by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var cue by remember { mutableStateOf<TrackCue?>(null) }
    var cueRun by remember { mutableIntStateOf(0) }
    var cueStep by remember { mutableStateOf("") }
    var pauseText by remember { mutableStateOf("500") }
    val pauseMs = pauseText.toLongOrNull()?.coerceIn(1L, MAX_TRACK_PAUSE_MS) ?: 500L
    val pauseHolder = rememberUpdatedState(pauseMs)
    val trackKeyIds = board.lanes.joinToString(",") { lane ->
        board.clips.filter { it.laneId == lane }.joinToString("+") {
            "${it.id}:${it.offsetMs}:${it.trimStartMs}:${it.trimEndMs}"
        }
    }

    engine.onStopped = { key -> playing = playing - key }

    val seconds = if (recording == null || clock < 0) {
        0
    } else {
        ((SystemClock.elapsedRealtime() - startedAt) / 1000L).toInt()
    }

    val recordPeaks = remember { mutableStateListOf<Int>() }
    LaunchedEffect(recording) {
        while (recording != null) {
            delay(250)
            clock++
        }
    }
    LaunchedEffect(recording) {
        if (recording != "track") return@LaunchedEffect
        recordPeaks.clear()
        while (isActive) {
            recordPeaks.add(engine.recordingPeak())
            delay(RECORD_PEAK_MS)
        }
    }
    LaunchedEffect(cycle) {
        engine.setCycle(cycle.enabled, cycle.startMs, cycle.endMs)
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun begin(target: String) {
        if (recording != null) {
            toast("Сначала остановите запись")
            return
        }
        if (target.startsWith("p:")) {
            engine.stop(target)
            playing = playing - target
        }
        if (engine.startRecording()) {
            recording = target
            startedAt = SystemClock.elapsedRealtime()
        } else {
            toast("Не удалось начать запись")
        }
    }

    fun finish() {
        val target = recording ?: return
        val file = engine.stopRecording()
        recording = null
        if (file == null) {
            toast("Запись слишком короткая")
            return
        }
        if (target == "track") {
            if (StudioLibrary.createTrack(context, file) == null) toast("Не удалось сохранить дорожку")
            board = StudioLibrary.loadBoard(context)
        } else if (target.startsWith("p:")) {
            val index = target.removePrefix("p:").toIntOrNull()
            if (index == null || !StudioLibrary.savePad(context, index, file)) toast("Не удалось сохранить плитку")
            padTick++
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pendingRecord
        pendingRecord = null
        if (granted && target != null) begin(target)
        else if (!granted) toast("Нужен микрофон, чтобы записать звук")
    }

    fun stopPads() {
        playing.filter { it.startsWith("p:") }.forEach { engine.stop(it) }
        playing = playing.filterNot { it.startsWith("p:") }.toSet()
    }

    fun clipsOnLane(index: Int): List<StudioClip> {
        val lane = board.lanes.getOrNull(index) ?: return emptyList()
        return board.clips.filter { it.laneId == lane }
    }

    fun recordStudio(type: String, name: String) {
        scope.launch {
            BiblePreferences(context.applicationContext).recordUsageEvent(
                type,
                "media_musician",
                AppUsageEvents.namedDetail("music", name),
            )
        }
    }

    fun playClip(clip: StudioClip) {
        val key = "t:${clip.id}"
        engine.setCycle(cycle.enabled, cycle.startMs, cycle.endMs)
        engine.start(
            key,
            StudioLibrary.sourceFile(context, clip.sourceId),
            clip.loop,
            clip.offsetMs,
            clip.trimStartMs,
            clip.trimEndMs,
        )
        playing = playing + key
        recordStudio(AppUsageEvents.TYPE_PLAY, "Студия ${clip.sourceId.take(8)}")
    }

    fun playTrackAt(index: Int) {
        clipsOnLane(index).forEach(::playClip)
    }

    fun silenceTrackAt(index: Int) {
        clipsOnLane(index).forEach { clip ->
            val key = "t:${clip.id}"
            engine.stop(key)
            playing = playing - key
        }
    }

    fun silenceTracks() {
        board.lanes.indices.forEach(::silenceTrackAt)
    }

    fun reloadBoard(): StudioBoard {
        val fresh = StudioLibrary.loadBoard(context)
        board = fresh
        return fresh
    }

    fun setEditing(on: Boolean) {
        if (!on && recording?.startsWith("p:") == true) finish()
        stopPads()
        editing = on
    }

    fun request(target: String) {
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> begin(target)
            else -> {
                pendingRecord = target
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(padTick) { engine.prepare() }

    DisposableEffect(Unit) {
        onDispose { engine.release() }
    }

    LaunchedEffect(cue, cueRun, trackKeyIds) {
        val scheme = cue ?: return@LaunchedEffect
        if (board.lanes.isEmpty()) return@LaunchedEffect
        fun group(evenNumbers: Boolean) {
            board.lanes.forEachIndexed { index, _ ->
                val even = (index + 1) % 2 == 0
                if (even == evenNumbers) playTrackAt(index) else silenceTrackAt(index)
            }
        }
        when (scheme) {
            TrackCue.ALL -> {
                cueStep = "Все сразу"
                board.lanes.indices.forEach { playTrackAt(it) }
            }
            TrackCue.EVERY_OTHER -> {
                cueStep = "Дорожки 1, 3, 5…"
                board.lanes.forEachIndexed { index, _ ->
                    if (index % 2 == 0) playTrackAt(index) else silenceTrackAt(index)
                }
            }
            TrackCue.EVENS -> {
                cueStep = "Чётные: 2, 4, 6…"
                group(evenNumbers = true)
            }
            TrackCue.SWAP -> {
                var evens = true
                while (isActive) {
                    cueStep = if (evens) "Сейчас чётные" else "Сейчас нечётные"
                    group(evenNumbers = evens)
                    delay(pauseHolder.value)
                    evens = !evens
                }
            }
            TrackCue.SEQUENCE -> {
                var index = 0
                while (isActive) {
                    cueStep = "По очереди: ${index + 1}"
                    board.lanes.indices.forEach { if (it == index) playTrackAt(it) else silenceTrackAt(it) }
                    delay(pauseHolder.value)
                    index = (index + 1) % board.lanes.size
                }
            }
            TrackCue.REVERSE -> {
                var index = board.lanes.lastIndex
                while (isActive) {
                    cueStep = "С конца: ${index + 1}"
                    board.lanes.indices.forEach { if (it == index) playTrackAt(it) else silenceTrackAt(it) }
                    delay(pauseHolder.value)
                    index = if (index == 0) board.lanes.lastIndex else index - 1
                }
            }
            TrackCue.STACK -> {
                silenceTracks()
                var next = 0
                while (isActive) {
                    if (next >= board.lanes.size) {
                        cueStep = "Все включены, пауза и сначала"
                        delay(pauseHolder.value)
                        silenceTracks()
                        next = 0
                    }
                    cueStep = "Подряд до ${next + 1}"
                    playTrackAt(next)
                    next++
                    delay(pauseHolder.value)
                }
            }
            TrackCue.RANDOM -> {
                while (isActive) {
                    val index = board.lanes.indices.random()
                    cueStep = "Случайная: ${index + 1}"
                    board.lanes.indices.forEach { if (it == index) playTrackAt(it) else silenceTrackAt(it) }
                    delay(pauseHolder.value)
                }
            }
            TrackCue.WAVE -> {
                while (isActive) {
                    silenceTracks()
                    for (index in board.lanes.indices) {
                        if (!isActive) return@LaunchedEffect
                        cueStep = "Волна: включаю ${index + 1}"
                        playTrackAt(index)
                        delay(pauseHolder.value)
                    }
                    for (index in board.lanes.indices) {
                        if (!isActive) return@LaunchedEffect
                        cueStep = "Волна: выключаю ${index + 1}"
                        silenceTrackAt(index)
                        delay(pauseHolder.value)
                    }
                }
            }
        }
    }

    val together = playing.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Студия", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (together > 0) {
                        Text(
                            "вместе $together",
                            color = Color(0xFF7C3AED),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Плитки",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                )
                Text(
                    "Держите плитку — звук идёт, отпустите — он стихает. Можно держать несколько сразу. Чтобы записать или перезаписать, включите редактирование.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (editing) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceContainerLow,
                        )
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (editing) "Редактирование" else "Только игра",
                            fontWeight = FontWeight.ExtraBold,
                            color = if (editing) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (editing) "Нажмите плитку, чтобы записать звук" else "Нажатие только проигрывает",
                            color = if (editing) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    Switch(
                        checked = editing,
                        onCheckedChange = { setEditing(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFC4B5FD),
                            checkedBorderColor = Color.White,
                        ),
                    )
                }
            }
            item {
                val padDown = rememberUpdatedState<(Int) -> Unit>({ index ->
                    val key = "p:$index"
                    if (recording != null || !StudioLibrary.hasPad(context, index)) return@rememberUpdatedState
                    if (engine.playWhileHeld(key, padFiles[index])) playing = playing + key
                })
                val padUp = rememberUpdatedState<(Int) -> Unit>({ index ->
                    val key = "p:$index"
                    engine.stop(key)
                    playing = playing - key
                })
                Column(
                    modifier = Modifier.pointerInput(padTick, editing) {
                        if (editing) return@pointerInput
                        awaitEachGesture {
                            val held = HashMap<PointerId, Int>()
                            val count = IntArray(StudioLibrary.PAD_COUNT)
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val gap = 10.dp.toPx()
                                    event.changes.forEach { change ->
                                        val existing = held[change.id]
                                        if (existing == null) {
                                            if (!change.pressed) return@forEach
                                            val index = studioPadAt(
                                                change.position.x,
                                                change.position.y,
                                                size.width.toFloat(),
                                                gap,
                                            ) ?: return@forEach
                                            if (!StudioLibrary.hasPad(context, index)) return@forEach
                                            held[change.id] = index
                                            change.consume()
                                            padDown.value(index)
                                            count[index]++
                                        } else if (!change.pressed) {
                                            held.remove(change.id)
                                            count[existing] = (count[existing] - 1).coerceAtLeast(0)
                                            if (count[existing] == 0) padUp.value(existing)
                                            change.consume()
                                        } else {
                                            change.consume()
                                        }
                                    }
                                    if (held.isEmpty() && event.changes.none { it.pressed }) break
                                }
                            } finally {
                                held.values.distinct().forEach { index ->
                                    if (count[index] > 0) padUp.value(index)
                                }
                            }
                        }
                    },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (row in 0 until 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            for (col in 0 until 4) {
                                val index = row * 4 + col
                                val key = "p:$index"
                                val has = padTick >= 0 && StudioLibrary.hasPad(context, index)
                                StudioPad(
                                    index = index,
                                    color = PadColors[index],
                                    hasAudio = has,
                                    editing = editing,
                                    recording = recording == key,
                                    playing = key in playing,
                                    seconds = if (recording == key) seconds else 0,
                                    onClick = {
                                        when {
                                            recording == key -> finish()
                                            recording != null -> toast("Сначала остановите запись")
                                            editing -> request(key)
                                            else -> toast("Включите редактирование, чтобы записать плитку")
                                        }
                                    },
                                    onClear = {
                                        if (recording != null) {
                                            toast("Сначала остановите запись")
                                        } else {
                                            engine.stop(key)
                                            engine.forgetPad(key)
                                            playing = playing - key
                                            StudioLibrary.deletePad(context, index)
                                            padTick++
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text("Дорожки", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(
                    "Потяните кусок вправо, чтобы он вступил позже, и вверх или вниз — на другую дорожку. Ножницы режут в месте нажатия. Ниже и выше меняют высоту, уже и шире — масштаб. Жёлтая полоска — цикл.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
            }
            item {
                val recordingTrack = recording == "track"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFEC4899))))
                        .clickable {
                            when {
                                recordingTrack -> finish()
                                recording != null -> toast("Сначала остановите запись")
                                else -> request("track")
                            }
                        }
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (recordingTrack) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null,
                        tint = Color.White,
                    )
                    Text(
                        if (recordingTrack) "  Стоп  ${formatStudioTime(seconds)}" else "  Новая дорожка",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                    )
                }
            }
            item {
                    StudioTimeline(
                        board = board,
                        cycle = cycle,
                        recording = recording == "track",
                        recordingPeaks = if (recording == "track") recordPeaks.toIntArray() else IntArray(0),
                        onCycle = { next ->
                            cycle = next
                            StudioLibrary.saveCycle(context, next)
                        },
                        onOffset = { id, ms ->
                            StudioLibrary.setClipOffset(context, id, ms)
                            val fresh = reloadBoard()
                            fresh.clips.find { it.id == id }?.let { clip ->
                                if ("t:${clip.id}" in playing) playClip(clip)
                            }
                        },
                        onMove = { id, steps ->
                            StudioLibrary.moveClip(context, id, steps)
                            val fresh = reloadBoard()
                            fresh.clips.find { it.id == id }?.let { clip ->
                                if ("t:${clip.id}" in playing) playClip(clip)
                            }
                        },
                        onCut = { id, atMs, durationMs ->
                            val rightId = if (durationMs <= 0L) {
                                toast("Подождите, дорожка ещё читается")
                                null
                            } else {
                                StudioLibrary.splitClip(context, id, atMs, durationMs)
                            }
                            if (durationMs > 0L && rightId == null) {
                                toast("Слишком близко к краю")
                            } else if (rightId != null) {
                                val wasPlaying = "t:$id" in playing
                                engine.stop("t:$id")
                                playing = playing - "t:$id"
                                val fresh = reloadBoard()
                                if (wasPlaying) {
                                    fresh.clips.filter { it.id == id || it.id == rightId }.forEach(::playClip)
                                }
                            }
                        },
                        onAddLane = {
                            StudioLibrary.addEmptyLane(context)
                            board = StudioLibrary.loadBoard(context)
                        },
                        onLoadWave = { file, onReady -> engine.loadWave(file, onReady) },
                    )
            }
            if (board.clips.isNotEmpty()) {
                item {
                    TrackCuePanel(
                        cue = cue,
                        step = cueStep,
                        pauseText = pauseText,
                        pauseMs = pauseMs,
                        onPauseText = { pauseText = it.filter(Char::isDigit).take(8) },
                        onPreset = { pauseText = it.toString() },
                        onPick = {
                            cue = it
                            cueRun++
                        },
                        onStop = {
                            cue = null
                            cueStep = ""
                            silenceTracks()
                        },
                    )
                }
            }
            if (board.lanes.isEmpty()) {
                item {
                    Text(
                        "Пока нет дорожек. Нажмите «Новая дорожка» или «Пустой трек» на шкале.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            }
            itemsIndexed(board.lanes, key = { _, lane -> lane }) { index, laneId ->
                val clips = board.clips.filter { it.laneId == laneId }
                val keys = clips.map { "t:${it.id}" }
                val lanePlaying = keys.any { it in playing }
                val loop = clips.isNotEmpty() && clips.all { it.loop }
                val detail = when {
                    clips.isEmpty() -> "Пустая — перетащите сюда кусок"
                    clips.size == 1 -> formatStudioOffset(clips.first().offsetMs) +
                        if (loop) " · крутится без конца" else " · один раз"
                    else -> "${clips.size} куска · " + if (loop) "крутятся" else "один раз"
                }
                StudioTrackRow(
                    title = if (clips.isEmpty()) "Пустая ${index + 1}" else "Дорожка ${index + 1}",
                    detail = detail,
                    loop = loop,
                    playing = lanePlaying,
                    playEnabled = clips.isNotEmpty(),
                    onTogglePlay = {
                        if (clips.isEmpty()) return@StudioTrackRow
                        engine.setCycle(cycle.enabled, cycle.startMs, cycle.endMs)
                        if (lanePlaying) {
                            clips.forEach { clip ->
                                engine.stop("t:${clip.id}")
                                playing = playing - "t:${clip.id}"
                            }
                        } else {
                            clips.forEach(::playClip)
                        }
                    },
                    onToggleLoop = {
                        if (clips.isEmpty()) return@StudioTrackRow
                        val next = !loop
                        StudioLibrary.setLaneLoop(context, laneId, next)
                        clips.forEach { engine.setLoop("t:${it.id}", next) }
                        board = StudioLibrary.loadBoard(context)
                    },
                    onDelete = {
                        val removed = clips.firstOrNull()?.let { "Студия ${it.sourceId.take(8)}" } ?: "Дорожка студии"
                        clips.forEach { clip ->
                            engine.stop("t:${clip.id}")
                            playing = playing - "t:${clip.id}"
                        }
                        StudioLibrary.deleteLane(context, laneId)
                        board = StudioLibrary.loadBoard(context)
                        recordStudio(AppUsageEvents.TYPE_DELETE, removed)
                    },
                )
            }
            item {
                Text(
                    "Если пишете новую партию, пока другие уже звучат, лучше надеть наушники — иначе микрофон запишет и их.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StudioPad(
    index: Int,
    color: Color,
    hasAudio: Boolean,
    editing: Boolean,
    recording: Boolean,
    playing: Boolean,
    seconds: Int,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = when {
        recording -> Color(0xFFDC2626)
        playing -> color
        hasAudio -> color.copy(alpha = 0.88f)
        editing -> color.copy(alpha = 0.55f)
        else -> color.copy(alpha = 0.16f)
    }
    val labelColor = if (hasAudio || recording || playing || editing) Color.White else color
    val gesture = if (editing || recording || !hasAudio) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(fill)
            .border(
                width = if (playing || recording) 3.dp else 1.5.dp,
                color = if (playing || recording) Color.White else color.copy(alpha = if (hasAudio) 0f else 0.7f),
                shape = RoundedCornerShape(18.dp),
            )
            .then(gesture),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (recording || editing || hasAudio || playing) {
                Icon(
                    when {
                        recording -> Icons.Filled.Stop
                        editing -> Icons.Filled.Mic
                        playing -> Icons.Filled.Stop
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                when {
                    recording -> formatStudioTime(seconds)
                    else -> "${index + 1}"
                },
                color = labelColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (editing && hasAudio && !recording) {
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Стереть плитку", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun StudioTrackRow(
    title: String,
    detail: String,
    loop: Boolean,
    playing: Boolean,
    playEnabled: Boolean,
    onTogglePlay: () -> Unit,
    onToggleLoop: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (playing) Color(0xFF7C3AED) else Color(0xFF7C3AED).copy(alpha = if (playEnabled) 0.16f else 0.06f))
                .clickable(enabled = playEnabled, onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Стоп" else "Играть",
                tint = if (playing) Color.White else Color(0xFF7C3AED),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        IconButton(onClick = onToggleLoop) {
            Icon(
                Icons.Filled.Loop,
                contentDescription = "Зациклить",
                tint = if (loop) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class TrackCue(val title: String) {
    ALL("Все сразу"),
    EVERY_OTHER("Через одну"),
    EVENS("Чётные"),
    SWAP("Чёт ↔ нечёт"),
    SEQUENCE("По очереди"),
    STACK("Подряд"),
    REVERSE("С конца"),
    RANDOM("Случайная"),
    WAVE("Волна"),
}

private const val MAX_TRACK_PAUSE_MS = 12L * 60L * 60L * 1000L

private val PausePresets = listOf(100L, 500L, 1_000L, 5_000L, 60_000L)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackCuePanel(
    cue: TrackCue?,
    step: String,
    pauseText: String,
    pauseMs: Long,
    onPauseText: (String) -> Unit,
    onPreset: (Long) -> Unit,
    onPick: (TrackCue) -> Unit,
    onStop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Как включать", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrackCue.entries.forEach { item ->
                FilterChip(
                    selected = cue == item,
                    onClick = { onPick(item) },
                    label = { Text(item.title) },
                )
            }
            FilterChip(
                selected = false,
                onClick = onStop,
                label = { Text(if (cue == null) "Выключить все" else "Стоп") },
            )
        }
        if (step.isNotBlank()) {
            Text(step, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        OutlinedTextField(
            value = pauseText,
            onValueChange = onPauseText,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Пауза, миллисекунды") },
            supportingText = {
                Text("Сейчас ${formatTrackPause(pauseMs)} · от 1 мс до 12 часов")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PausePresets.forEach { preset ->
                FilterChip(
                    selected = pauseMs == preset,
                    onClick = { onPreset(preset) },
                    label = { Text(formatTrackPause(preset)) },
                )
            }
        }
        Text(
            "«Чёт ↔ нечёт» держит чётные дорожки (2, 4, 6…) заданную паузу, выключает их и включает нечётные (1, 3, 5…). «По очереди», «подряд», «с конца», «случайная» и «волна» тоже ждут эту паузу.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

private fun formatTrackPause(ms: Long): String {
    if (ms < 1_000L) return "$ms мс"
    val hours = ms / 3_600_000L
    val minutes = (ms % 3_600_000L) / 60_000L
    val seconds = (ms % 60_000L) / 1_000L
    val rest = ms % 1_000L
    return buildString {
        if (hours > 0) append("$hours ч ")
        if (minutes > 0) append("$minutes мин ")
        if (seconds > 0) append("$seconds с ")
        if (rest > 0 && hours == 0L) append("$rest мс")
    }.trim()
}

private fun formatStudioTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

private fun formatStudioOffset(ms: Long): String {
    if (ms < 50L) return "Сразу"
    val tenths = ms / 100L
    return "Старт +${tenths / 10},${tenths % 10} с"
}

private val LaneHeights = floatArrayOf(36f, 52f, 76f, 108f, 148f)
private val TimelineZooms = floatArrayOf(36f, 56f, 72f, 110f, 168f, 240f)
private const val RECORD_PEAK_MS = 50L

private fun timelineDp(ms: Long, dpPerSec: Float) = (ms.coerceAtLeast(0L) * dpPerSec / 1000f).dp

private fun stepZoom(current: Float, options: FloatArray, direction: Int): Float {
    val index = options.indices.minBy { kotlin.math.abs(options[it] - current) }
    return options[(index + direction).coerceIn(options.indices)]
}

private fun clipLengthMs(clip: StudioClip, durationMs: Long): Long {
    if (durationMs <= 0L) return 3_000L
    val end = if (clip.trimEndMs < 0L) durationMs else clip.trimEndMs.coerceAtMost(durationMs)
    return (end - clip.trimStartMs).coerceAtLeast(80L)
}

private fun slicePeaks(full: IntArray?, clip: StudioClip, durationMs: Long): IntArray? {
    if (full == null || full.isEmpty() || durationMs <= 0L) return full
    val end = if (clip.trimEndMs < 0L) durationMs else clip.trimEndMs.coerceAtMost(durationMs)
    val start = clip.trimStartMs.coerceIn(0L, durationMs)
    val from = ((start * full.size) / durationMs).toInt().coerceIn(0, full.lastIndex)
    val to = ((end * full.size) / durationMs).toInt().coerceIn(from + 1, full.size)
    return full.copyOfRange(from, to)
}

@Composable
private fun StudioTimeline(
    board: StudioBoard,
    cycle: StudioCycle,
    recording: Boolean,
    recordingPeaks: IntArray,
    onCycle: (StudioCycle) -> Unit,
    onOffset: (String, Long) -> Unit,
    onMove: (String, Int) -> Unit,
    onCut: (String, Long, Long) -> Unit,
    onAddLane: () -> Unit,
    onLoadWave: (File, (IntArray, Long) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    var laneDp by remember { mutableFloatStateOf(52f) }
    var zoomDp by remember { mutableFloatStateOf(72f) }
    var scissors by remember { mutableStateOf(false) }
    var waves by remember { mutableStateOf(mapOf<String, IntArray>()) }
    var durations by remember { mutableStateOf(mapOf<String, Long>()) }
    var draft by remember { mutableStateOf(mapOf<String, Long>()) }
    var lift by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var cycleDraft by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val cycleStart = cycleDraft?.first ?: cycle.startMs
    val cycleEnd = cycleDraft?.second ?: cycle.endMs
    val sources = board.clips.map { it.sourceId }.distinct()
    val signature = sources.joinToString { id ->
        val file = StudioLibrary.sourceFile(context, id)
        "$id:${file.length()}:${file.lastModified()}"
    }
    LaunchedEffect(signature) {
        sources.forEach { id ->
            onLoadWave(StudioLibrary.sourceFile(context, id)) { peaks, durationMs ->
                waves = waves + (id to peaks)
                if (durationMs > 0L) durations = durations + (id to durationMs)
            }
        }
    }
    val spanMs = run {
        var end = 8_000L
        board.clips.forEach { clip ->
            val start = draft[clip.id] ?: clip.offsetMs
            val length = clipLengthMs(clip, durations[clip.sourceId] ?: 0L)
            if (start + length + 1_500L > end) end = start + length + 1_500L
        }
        if (recording) {
            val rec = recordingPeaks.size * RECORD_PEAK_MS + 1_500L
            if (rec > end) end = rec
        }
        if (cycleEnd + 1_500L > end) end = cycleEnd + 1_500L
        end.coerceAtMost(StudioLibrary.MAX_OFFSET_MS + 60_000L)
    }
    val contentWidth = timelineDp(spanMs, zoomDp)
    val lane = laneDp.dp
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimelineTool("Ниже", laneDp > LaneHeights.first()) { laneDp = stepZoom(laneDp, LaneHeights, -1) }
        TimelineTool("Выше", laneDp < LaneHeights.last()) { laneDp = stepZoom(laneDp, LaneHeights, 1) }
        TimelineTool("Уже", zoomDp > TimelineZooms.first()) { zoomDp = stepZoom(zoomDp, TimelineZooms, -1) }
        TimelineTool("Шире", zoomDp < TimelineZooms.last()) { zoomDp = stepZoom(zoomDp, TimelineZooms, 1) }
        TimelineTool(if (scissors) "Ножницы вкл" else "Ножницы", true, selected = scissors) { scissors = !scissors }
        TimelineTool("Пустой трек", true) { onAddLane() }
    }
    Text(
        if (scissors) {
            "Нажмите на кусок в том месте, где его разрезать. Правая часть останется на шкале, её можно перетащить на пустую дорожку."
        } else {
            "Во время записи снизу растёт её диаграмма — по ней видно, как новая партия ложится на уже записанные."
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(92.dp)) {
            Box(Modifier.height(40.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    if (cycle.enabled) "Цикл" else "Без цикла",
                    color = if (cycle.enabled) Color(0xFF78350F) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (cycle.enabled) Color(0xFFFBBF24) else Color(0xFF292524))
                        .clickable {
                            onCycle(cycle.copy(enabled = !cycle.enabled, startMs = cycleStart, endMs = cycleEnd))
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            board.lanes.forEachIndexed { index, laneId ->
                val pieces = board.clips.filter { it.laneId == laneId }
                val start = pieces.minOfOrNull { draft[it.id] ?: it.offsetMs }
                val landing = lift?.let { (clipId, steps) ->
                    val fromLane = board.clips.find { it.id == clipId }?.laneId
                    val from = board.lanes.indexOf(fromLane)
                    from >= 0 && board.lanes.getOrNull(from + steps) == laneId && laneId != fromLane
                } == true
                Column(
                    Modifier.height(lane).padding(end = 6.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (pieces.isEmpty()) "Пустая" else "Дорожка ${index + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        color = if (landing) Color(0xFF7C3AED) else Color.Unspecified,
                    )
                    Text(
                        when {
                            pieces.isEmpty() -> "для куска"
                            pieces.size > 1 -> "${pieces.size} куска"
                            else -> formatStudioOffset(start ?: 0L)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
            if (recording) {
                Column(Modifier.height(lane), verticalArrangement = Arrangement.Center) {
                    Text("Новая", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFDC2626))
                    Text("идёт запись", color = Color(0xFFDC2626), fontSize = 11.sp)
                }
            }
        }
        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
            Column(Modifier.width(contentWidth)) {
                StudioTimelineRuler(
                    spanMs = spanMs,
                    dpPerSec = zoomDp,
                    startMs = cycleStart,
                    endMs = cycleEnd,
                    enabled = cycle.enabled,
                    onDrag = { start, end -> cycleDraft = start to end },
                    onDragEnd = {
                        val draftRange = cycleDraft
                        cycleDraft = null
                        if (draftRange != null) {
                            onCycle(cycle.copy(startMs = draftRange.first, endMs = draftRange.second))
                        }
                    },
                )
                board.lanes.forEach { laneId ->
                    val pieces = board.clips.filter { it.laneId == laneId }
                    val landing = lift?.let { (clipId, steps) ->
                        val fromLane = board.clips.find { it.id == clipId }?.laneId
                        val from = board.lanes.indexOf(fromLane)
                        from >= 0 && board.lanes.getOrNull(from + steps) == laneId && laneId != fromLane
                    } == true
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(lane)
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (landing) Color(0xFF4C1D95) else if (pieces.isEmpty()) Color(0xFF1C1917) else Color(0xFF052E16)),
                    ) {
                        if (pieces.isEmpty()) {
                            Text(
                                "Пусто",
                                color = Color(0xFFA8A29E),
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                            )
                        }
                        pieces.forEach { clip ->
                            val start = draft[clip.id] ?: clip.offsetMs
                            val full = durations[clip.sourceId] ?: 0L
                            val length = clipLengthMs(clip, full)
                            StudioTimelineClip(
                                offsetMs = start,
                                durationMs = length,
                                height = (laneDp - 4f).dp,
                                lanePitch = lane,
                                dpPerSec = zoomDp,
                                scissors = scissors,
                                peaks = slicePeaks(waves[clip.sourceId], clip, full),
                                color = Color(0xFF16A34A),
                                onDragBy = { deltaMs, steps ->
                                    val base = draft[clip.id] ?: clip.offsetMs
                                    draft = draft + (clip.id to (base + deltaMs).coerceIn(0L, StudioLibrary.MAX_OFFSET_MS))
                                    lift = clip.id to steps
                                },
                                onDragEnd = { steps ->
                                    val ms = draft[clip.id]
                                    draft = draft - clip.id
                                    lift = null
                                    if (ms != null && ms != clip.offsetMs) onOffset(clip.id, ms)
                                    if (steps != 0) onMove(clip.id, steps)
                                },
                                onCut = { atMs -> onCut(clip.id, atMs, full) },
                            )
                        }
                    }
                }
                if (recording) {
                    val recMs = (recordingPeaks.size * RECORD_PEAK_MS).coerceAtLeast(RECORD_PEAK_MS)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(lane)
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF3F1212)),
                    ) {
                        Box(
                            Modifier
                                .width(timelineDp(recMs, zoomDp).coerceAtLeast(28.dp))
                                .height((laneDp - 4f).dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFDC2626)),
                        ) {
                            StudioWaveform(recordingPeaks, Modifier.fillMaxSize().padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

private enum class CycleDrag { Start, End, Move }

@Composable
private fun StudioTimelineRuler(
    spanMs: Long,
    dpPerSec: Float,
    startMs: Long,
    endMs: Long,
    enabled: Boolean,
    onDrag: (Long, Long) -> Unit,
    onDragEnd: () -> Unit,
) {
    val drag = rememberUpdatedState(onDrag)
    val finish = rememberUpdatedState(onDragEnd)
    val startState = rememberUpdatedState(startMs)
    val endState = rememberUpdatedState(endMs)
    val zoom = rememberUpdatedState(dpPerSec)
    Box(Modifier.fillMaxWidth().height(40.dp)) {
        Box(
            Modifier
                .offset(x = timelineDp(startMs, dpPerSec))
                .width(timelineDp((endMs - startMs).coerceAtLeast(StudioLibrary.MIN_CYCLE_MS), dpPerSec).coerceAtLeast(28.dp))
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (enabled) Color(0xFFFBBF24) else Color(0xFF92400E)),
        )
        var second = 0
        val last = (spanMs / 1000L).toInt()
        while (second <= last) {
            Text(
                "$second",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = timelineDp(second * 1000L, dpPerSec)),
            )
            second += 2
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(spanMs) {
                    val edge = 18.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var pxPerMs = zoom.value.dp.toPx() / 1000f
                        val startPx = startState.value * pxPerMs
                        val endPx = endState.value * pxPerMs
                        val mode = when {
                            down.position.x in (startPx - edge)..(startPx + edge) -> CycleDrag.Start
                            down.position.x in (endPx - edge)..(endPx + edge) -> CycleDrag.End
                            down.position.x in startPx..endPx -> CycleDrag.Move
                            else -> null
                        }
                        if (mode == null) return@awaitEachGesture
                        down.consume()
                        val originStart = startState.value
                        val originEnd = endState.value
                        val length = (originEnd - originStart).coerceAtLeast(StudioLibrary.MIN_CYCLE_MS)
                        var fraction = 0f
                        var movedStart = originStart
                        var movedEnd = originEnd
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                pxPerMs = zoom.value.dp.toPx() / 1000f
                                fraction += (change.position.x - change.previousPosition.x) / pxPerMs
                                val whole = fraction.toLong()
                                if (whole == 0L) continue
                                fraction -= whole
                                val max = StudioLibrary.MAX_OFFSET_MS
                                when (mode) {
                                    CycleDrag.Move -> {
                                        val start = (originStart + (movedStart - originStart) + whole)
                                            .coerceIn(0L, max - length)
                                        movedStart = start
                                        movedEnd = start + length
                                    }
                                    CycleDrag.Start -> {
                                        movedStart = (movedStart + whole).coerceIn(0L, movedEnd - StudioLibrary.MIN_CYCLE_MS)
                                    }
                                    CycleDrag.End -> {
                                        movedEnd = (movedEnd + whole).coerceIn(movedStart + StudioLibrary.MIN_CYCLE_MS, max)
                                    }
                                }
                                drag.value(movedStart, movedEnd)
                            }
                        } finally {
                            finish.value()
                        }
                    }
                },
        )
    }
}

@Composable
private fun StudioTimelineClip(
    offsetMs: Long,
    durationMs: Long,
    height: androidx.compose.ui.unit.Dp,
    lanePitch: androidx.compose.ui.unit.Dp,
    dpPerSec: Float,
    scissors: Boolean,
    peaks: IntArray?,
    color: Color,
    onDragBy: (Long, Int) -> Unit,
    onDragEnd: (Int) -> Unit,
    onCut: (Long) -> Unit,
) {
    val dragBy = rememberUpdatedState(onDragBy)
    val dragEnd = rememberUpdatedState(onDragEnd)
    val cut = rememberUpdatedState(onCut)
    val zoom = rememberUpdatedState(dpPerSec)
    val pitch = rememberUpdatedState(lanePitch)
    val originMs = rememberUpdatedState(offsetMs)
    Box(
        Modifier
            .offset(x = timelineDp(offsetMs, dpPerSec))
            .width(timelineDp(durationMs, dpPerSec).coerceAtLeast(48.dp))
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .pointerInput(scissors) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    down.consume()
                    if (scissors) {
                        val start = down.position
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            if ((change.position - start).getDistance() > viewConfiguration.touchSlop) moved = true
                        }
                        if (!moved) {
                            val pxPerMs = zoom.value.dp.toPx() / 1000f
                            val at = originMs.value + if (pxPerMs > 0f) (start.x / pxPerMs).toLong() else 0L
                            cut.value(at)
                        }
                        return@awaitEachGesture
                    }
                    var fraction = 0f
                    var dy = 0f
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val pxPerMs = zoom.value.dp.toPx() / 1000f
                            val lanePx = pitch.value.toPx().coerceAtLeast(1f)
                            fraction += if (pxPerMs > 0f) (change.position.x - change.previousPosition.x) / pxPerMs else 0f
                            dy += change.position.y - change.previousPosition.y
                            val whole = fraction.toLong()
                            val steps = (dy / lanePx).roundToInt()
                            if (whole != 0L) fraction -= whole
                            if (whole != 0L || steps != 0) dragBy.value(whole, steps)
                        }
                    } finally {
                        val lanePx = pitch.value.toPx().coerceAtLeast(1f)
                        dragEnd.value((dy / lanePx).roundToInt())
                    }
                }
            },
    ) {
        StudioWaveform(peaks, Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp))
    }
}

@Composable
private fun StudioWaveform(peaks: IntArray?, modifier: Modifier = Modifier) {
    if (peaks == null || peaks.isEmpty()) return
    Canvas(modifier) {
        val max = peaks.maxOrNull()?.coerceAtLeast(1) ?: 1
        val mid = size.height / 2f
        val step = size.width / peaks.size
        peaks.forEachIndexed { index, peak ->
            val bar = peak / max.toFloat() * size.height
            val x = index * step + step / 2f
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(x, mid - bar / 2f),
                end = Offset(x, mid + bar / 2f),
                strokeWidth = step.coerceAtLeast(1.5f) * 0.55f,
            )
        }
    }
}

@Composable
private fun TimelineTool(
    label: String,
    enabled: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    selected -> Color(0xFF7C3AED)
                    enabled -> Color(0xFF7C3AED).copy(alpha = 0.14f)
                    else -> Color(0xFF292524)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = when {
            selected -> Color.White
            enabled -> Color(0xFF7C3AED)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
    )
}

/** Какая плитка под пальцем: сетка 2×4 с зазором [gap]. */
private fun studioPadAt(x: Float, y: Float, width: Float, gap: Float): Int? {
    if (width <= gap * 3f) return null
    val pad = (width - 3f * gap) / 4f
    if (pad <= 0f) return null
    val pitch = pad + gap
    val col = (x / pitch).toInt()
    val row = (y / pitch).toInt()
    if (row !in 0..1 || col !in 0..3) return null
    val localX = x - col * pitch
    val localY = y - row * pitch
    if (localX < 0f || localY < 0f || localX > pad || localY > pad) return null
    return row * 4 + col
}

private class StudioEngine(private val context: Context) {
    private val mixer = StudioMixer()
    private var recorder: MediaRecorder? = null
    var onStopped: (String) -> Unit = {}

    init {
        mixer.onStopped = { key -> onStopped(key) }
        prepare()
    }

    /** Звук плитки с начала, пока палец удерживает её. */
    fun playWhileHeld(key: String, file: File): Boolean = mixer.playWhileHeld(key, file)

    fun recordingPeak(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun prepare() {
        mixer.warm()
        for (index in 0 until StudioLibrary.PAD_COUNT) {
            val key = "p:$index"
            val file = StudioLibrary.padFile(context, index)
            if (file.exists() && file.length() > 800L) mixer.preloadPad(key, file)
            else mixer.forgetPad(key)
        }
    }

    fun forgetPad(key: String) = mixer.forgetPad(key)

    fun start(
        key: String,
        file: File,
        loop: Boolean,
        offsetMs: Long = 0L,
        trimStartMs: Long = 0L,
        trimEndMs: Long = -1L,
    ) = mixer.start(key, file, loop, offsetMs, trimStartMs, trimEndMs)

    /** Включает или выключает дорожку. true — сейчас играет. */
    fun toggle(
        key: String,
        file: File,
        loop: Boolean,
        offsetMs: Long = 0L,
        trimStartMs: Long = 0L,
        trimEndMs: Long = -1L,
    ): Boolean = mixer.toggle(key, file, loop, offsetMs, trimStartMs, trimEndMs)

    fun loadWave(file: File, onReady: (IntArray, Long) -> Unit) = mixer.loadWave(file, onReady = onReady)

    fun setCycle(enabled: Boolean, startMs: Long, endMs: Long) = mixer.setCycle(enabled, startMs, endMs)

    fun setLoop(key: String, loop: Boolean) = mixer.setLoop(key, loop)

    fun stop(key: String) = mixer.stop(key)

    fun startRecording(): Boolean {
        abandonRecording()
        val tmp = StudioLibrary.tempFile(context)
        if (tmp.exists()) tmp.delete()
        return try {
            val created = try {
                openRecorder(tmp.absolutePath, MediaRecorder.AudioSource.CAMCORDER)
            } catch (_: Exception) {
                openRecorder(tmp.absolutePath, MediaRecorder.AudioSource.MIC)
            }
            created.start()
            recorder = created
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stopRecording(): java.io.File? {
        val active = recorder
        recorder = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        val tmp = StudioLibrary.tempFile(context)
        if (!tmp.exists() || tmp.length() <= 800L) {
            if (tmp.exists()) tmp.delete()
            return null
        }
        return tmp
    }

    fun release() {
        mixer.release()
        abandonRecording()
    }

    private fun abandonRecording() {
        val active = recorder
        recorder = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        val tmp = StudioLibrary.tempFile(context)
        if (tmp.exists()) tmp.delete()
    }

    private fun openRecorder(path: String, source: Int): MediaRecorder {
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            created.setAudioSource(source)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioChannels(1)
            created.setAudioSamplingRate(48_000)
            created.setAudioEncodingBitRate(128_000)
            created.setOutputFile(path)
            created.prepare()
            return created
        } catch (e: Exception) {
            runCatching { created.release() }
            throw e
        }
    }
}
