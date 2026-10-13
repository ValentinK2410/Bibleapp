package com.example.bible.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import java.io.File
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.bible.data.StudioCycle
import com.example.bible.data.StudioLibrary
import com.example.bible.data.StudioTrack
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
    val engine = remember { StudioEngine(context.applicationContext) }
    val padFiles = remember { Array(StudioLibrary.PAD_COUNT) { StudioLibrary.padFile(context, it) } }
    var tracks by remember { mutableStateOf(StudioLibrary.listTracks(context)) }
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
    val trackKeyIds = tracks.joinToString(",") { it.id }

    engine.onStopped = { key -> playing = playing - key }

    val seconds = if (recording == null || clock < 0) {
        0
    } else {
        ((SystemClock.elapsedRealtime() - startedAt) / 1000L).toInt()
    }

    LaunchedEffect(recording) {
        while (recording != null) {
            delay(250)
            clock++
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
            tracks = StudioLibrary.listTracks(context)
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

    fun playTrackAt(index: Int) {
        val track = tracks.getOrNull(index) ?: return
        val key = "t:${track.id}"
        engine.setCycle(cycle.enabled, cycle.startMs, cycle.endMs)
        engine.start(key, track.file, track.loop, track.offsetMs)
        playing = playing + key
    }

    fun silenceTrackAt(index: Int) {
        val track = tracks.getOrNull(index) ?: return
        val key = "t:${track.id}"
        engine.stop(key)
        playing = playing - key
    }

    fun silenceTracks() {
        tracks.forEachIndexed { index, _ -> silenceTrackAt(index) }
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
        if (tracks.isEmpty()) return@LaunchedEffect
        fun group(evenNumbers: Boolean) {
            tracks.forEachIndexed { index, _ ->
                val even = (index + 1) % 2 == 0
                if (even == evenNumbers) playTrackAt(index) else silenceTrackAt(index)
            }
        }
        when (scheme) {
            TrackCue.ALL -> {
                cueStep = "Все сразу"
                tracks.indices.forEach { playTrackAt(it) }
            }
            TrackCue.EVERY_OTHER -> {
                cueStep = "Дорожки 1, 3, 5…"
                tracks.forEachIndexed { index, _ ->
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
                    tracks.indices.forEach { if (it == index) playTrackAt(it) else silenceTrackAt(it) }
                    delay(pauseHolder.value)
                    index = (index + 1) % tracks.size
                }
            }
            TrackCue.REVERSE -> {
                var index = tracks.lastIndex
                while (isActive) {
                    cueStep = "С конца: ${index + 1}"
                    tracks.indices.forEach { if (it == index) playTrackAt(it) else silenceTrackAt(it) }
                    delay(pauseHolder.value)
                    index = if (index == 0) tracks.lastIndex else index - 1
                }
            }
            TrackCue.STACK -> {
                silenceTracks()
                var next = 0
                while (isActive) {
                    if (next >= tracks.size) {
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
                    val index = tracks.indices.random()
                    cueStep = "Случайная: ${index + 1}"
                    tracks.indices.forEach { if (it == index) playTrackAt(it) else silenceTrackAt(it) }
                    delay(pauseHolder.value)
                }
            }
            TrackCue.WAVE -> {
                while (isActive) {
                    silenceTracks()
                    for (index in tracks.indices) {
                        if (!isActive) return@LaunchedEffect
                        cueStep = "Волна: включаю ${index + 1}"
                        playTrackAt(index)
                        delay(pauseHolder.value)
                    }
                    for (index in tracks.indices) {
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
                    "Потяните дорожку вправо, чтобы она вступила позже. Жёлтая полоска — цикл: перетащите её в любое место шкалы, края меняют длину. Ниже схемы включают дорожки вместе или по очереди.",
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
            if (tracks.isNotEmpty() || recording == "track") {
                item {
                    StudioTimeline(
                        tracks = tracks,
                        cycle = cycle,
                        recording = recording == "track",
                        recordingSeconds = if (recording == "track") seconds else 0,
                        onCycle = { next ->
                            cycle = next
                            StudioLibrary.saveCycle(context, next)
                        },
                        onOffset = { id, ms ->
                            StudioLibrary.setOffset(context, id, ms)
                            val fresh = StudioLibrary.listTracks(context)
                            tracks = fresh
                            val sounding = fresh.filter { "t:${it.id}" in playing }
                            if (sounding.isNotEmpty()) {
                                sounding.forEach { track ->
                                    engine.start("t:${track.id}", track.file, track.loop, track.offsetMs)
                                }
                            }
                        },
                        onLoadWave = { file, onReady -> engine.loadWave(file, onReady) },
                    )
                }
            }
            if (tracks.isNotEmpty()) {
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
            if (tracks.isEmpty()) {
                item {
                    Text(
                        "Пока нет дорожек. Нажмите «Новая дорожка» и сыграйте или спойте.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            }
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                val key = "t:${track.id}"
                StudioTrackRow(
                    title = "Дорожка ${index + 1}",
                    loop = track.loop,
                    offsetMs = track.offsetMs,
                    playing = key in playing,
                    onTogglePlay = {
                        engine.setCycle(cycle.enabled, cycle.startMs, cycle.endMs)
                        if (engine.toggle(key, track.file, track.loop, track.offsetMs)) playing = playing + key
                        else playing = playing - key
                    },
                    onToggleLoop = {
                        val next = !track.loop
                        StudioLibrary.setLoop(context, track.id, next)
                        engine.setLoop(key, next)
                        tracks = StudioLibrary.listTracks(context)
                    },
                    onDelete = {
                        engine.stop(key)
                        playing = playing - key
                        StudioLibrary.deleteTrack(context, track.id)
                        tracks = StudioLibrary.listTracks(context)
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
    loop: Boolean,
    offsetMs: Long,
    playing: Boolean,
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
                .background(if (playing) Color(0xFF7C3AED) else Color(0xFF7C3AED).copy(alpha = 0.16f))
                .clickable(onClick = onTogglePlay),
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
                buildString {
                    append(formatStudioOffset(offsetMs))
                    append(if (loop) " · крутится без конца" else " · один раз")
                },
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

private const val TIMELINE_DP_PER_SEC = 72f

private fun timelineDp(ms: Long) = (ms.coerceAtLeast(0L) * TIMELINE_DP_PER_SEC / 1000f).dp

@Composable
private fun StudioTimeline(
    tracks: List<StudioTrack>,
    cycle: StudioCycle,
    recording: Boolean,
    recordingSeconds: Int,
    onCycle: (StudioCycle) -> Unit,
    onOffset: (String, Long) -> Unit,
    onLoadWave: (File, (IntArray, Long) -> Unit) -> Unit,
) {
    val scroll = rememberScrollState()
    var waves by remember { mutableStateOf(mapOf<String, IntArray>()) }
    var durations by remember { mutableStateOf(mapOf<String, Long>()) }
    var draft by remember { mutableStateOf(mapOf<String, Long>()) }
    var cycleDraft by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val cycleStart = cycleDraft?.first ?: cycle.startMs
    val cycleEnd = cycleDraft?.second ?: cycle.endMs
    val signature = tracks.joinToString { "${it.id}:${it.file.length()}:${it.file.lastModified()}" }
    LaunchedEffect(signature) {
        tracks.forEach { track ->
            onLoadWave(track.file) { peaks, durationMs ->
                waves = waves + (track.id to peaks)
                if (durationMs > 0L) durations = durations + (track.id to durationMs)
            }
        }
    }
    val spanMs = run {
        var end = 8_000L
        tracks.forEach { track ->
            val start = draft[track.id] ?: track.offsetMs
            val length = durations[track.id] ?: 3_000L
            if (start + length + 1_500L > end) end = start + length + 1_500L
        }
        if (recording) {
            val rec = recordingSeconds * 1000L + 1_500L
            if (rec > end) end = rec
        }
        if (cycleEnd + 1_500L > end) end = cycleEnd + 1_500L
        end.coerceAtMost(StudioLibrary.MAX_OFFSET_MS + 60_000L)
    }
    val contentWidth = timelineDp(spanMs)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
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
            tracks.forEachIndexed { index, track ->
                val start = draft[track.id] ?: track.offsetMs
                Column(
                    Modifier.height(52.dp).padding(end = 6.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Дорожка ${index + 1}", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    Text(
                        formatStudioOffset(start),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
            if (recording) {
                Column(Modifier.height(52.dp), verticalArrangement = Arrangement.Center) {
                    Text("Новая", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFDC2626))
                    Text("идёт запись", color = Color(0xFFDC2626), fontSize = 11.sp)
                }
            }
        }
        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
            Column(Modifier.width(contentWidth)) {
                StudioTimelineRuler(
                    spanMs = spanMs,
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
                tracks.forEach { track ->
                    val start = draft[track.id] ?: track.offsetMs
                    val length = (durations[track.id] ?: 3_000L).coerceAtLeast(400L)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF052E16)),
                    ) {
                        StudioTimelineClip(
                            offsetMs = start,
                            durationMs = length,
                            peaks = waves[track.id],
                            color = Color(0xFF16A34A),
                            onDragBy = { deltaMs ->
                                val base = draft[track.id] ?: track.offsetMs
                                draft = draft + (track.id to (base + deltaMs).coerceIn(0L, StudioLibrary.MAX_OFFSET_MS))
                            },
                            onDragEnd = {
                                val ms = draft[track.id] ?: return@StudioTimelineClip
                                draft = draft - track.id
                                if (ms != track.offsetMs) onOffset(track.id, ms)
                            },
                        )
                    }
                }
                if (recording) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF3F1212)),
                    ) {
                        Box(
                            Modifier
                                .offset(x = 0.dp)
                                .width(timelineDp(recordingSeconds * 1000L).coerceAtLeast(28.dp))
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFDC2626)),
                        )
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
    Box(Modifier.fillMaxWidth().height(40.dp)) {
        Box(
            Modifier
                .offset(x = timelineDp(startMs))
                .width(timelineDp((endMs - startMs).coerceAtLeast(StudioLibrary.MIN_CYCLE_MS)).coerceAtLeast(28.dp))
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
                    .offset(x = timelineDp(second * 1000L)),
            )
            second += 2
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(spanMs) {
                    val pxPerMs = TIMELINE_DP_PER_SEC.dp.toPx() / 1000f
                    val edge = 18.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
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
    peaks: IntArray?,
    color: Color,
    onDragBy: (Long) -> Unit,
    onDragEnd: () -> Unit,
) {
    val dragBy = rememberUpdatedState(onDragBy)
    val dragEnd = rememberUpdatedState(onDragEnd)
    Box(
        Modifier
            .offset(x = timelineDp(offsetMs))
            .width(timelineDp(durationMs).coerceAtLeast(48.dp))
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .pointerInput(Unit) {
                val pxPerMs = TIMELINE_DP_PER_SEC.dp.toPx() / 1000f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    down.consume()
                    var fraction = 0f
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val dx = change.position.x - change.previousPosition.x
                            fraction += if (pxPerMs > 0f) dx / pxPerMs else 0f
                            val whole = fraction.toLong()
                            if (whole != 0L) {
                                fraction -= whole
                                dragBy.value(whole)
                            }
                        }
                    } finally {
                        dragEnd.value()
                    }
                }
            },
    ) {
        if (peaks != null && peaks.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp)) {
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
    }
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

    fun start(key: String, file: File, loop: Boolean, offsetMs: Long = 0L) = mixer.start(key, file, loop, offsetMs)

    /** Включает или выключает дорожку. true — сейчас играет. */
    fun toggle(key: String, file: File, loop: Boolean, offsetMs: Long = 0L): Boolean =
        mixer.toggle(key, file, loop, offsetMs)

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
