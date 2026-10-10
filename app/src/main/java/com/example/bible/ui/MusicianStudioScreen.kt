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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.bible.audio.StudioMixer
import com.example.bible.data.StudioLibrary
import com.example.bible.data.StudioTrack
import kotlinx.coroutines.delay

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
    var tracks by remember { mutableStateOf(StudioLibrary.listTracks(context)) }
    var padTick by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(emptySet<String>()) }
    var recording by remember { mutableStateOf<String?>(null) }
    var pendingRecord by remember { mutableStateOf<String?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var clock by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }

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

    DisposableEffect(Unit) {
        onDispose { engine.release() }
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    onHoldStart = {
                                        if (recording == null && has &&
                                            engine.playWhileHeld(key, StudioLibrary.padFile(context, index))
                                        ) {
                                            playing = playing + key
                                        }
                                    },
                                    onHoldEnd = {
                                        engine.stop(key)
                                        playing = playing - key
                                    },
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
                    "Запишите дорожку, потом ещё. Включите несколько сразу — они звучат параллельно. Зацикливание крутит дорожку без конца.",
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
                    playing = key in playing,
                    onTogglePlay = {
                        if (engine.toggle(key, track.file, track.loop)) playing = playing + key
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
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
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
    val gesture = when {
        editing || recording || !hasAudio -> Modifier.clickable(onClick = onClick)
        else -> Modifier.pointerInput(hasAudio) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down.consume()
                onHoldStart()
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                } finally {
                    onHoldEnd()
                }
            }
        }
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
                if (loop) "Крутится без конца" else "Один раз",
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

private fun formatStudioTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

private class StudioEngine(private val context: Context) {
    private val mixer = StudioMixer()
    private var recorder: MediaRecorder? = null
    var onStopped: (String) -> Unit = {}

    init {
        mixer.onStopped = { key -> onStopped(key) }
    }

    /** Звук плитки с начала, пока палец удерживает её. */
    fun playWhileHeld(key: String, file: File): Boolean = mixer.playWhileHeld(key, file)

    /** Включает или выключает дорожку. true — сейчас играет. */
    fun toggle(key: String, file: File, loop: Boolean): Boolean = mixer.toggle(key, file, loop)

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
