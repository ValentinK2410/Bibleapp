package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bible.audio.InstrumentSamplePlayer
import com.example.bible.audio.NoteTimbre
import com.example.bible.audio.PitchEstimator
import com.example.bible.audio.SineTonePlayer
import com.example.bible.music.MusicTheoryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TheorySection(val title: String, val body: String)

private val theoryHandbook: List<TheorySection> = listOf(
    TheorySection(
        title = "Нотный стан",
        body = "Пять линий, на которых записывается высота звука. Ноты читаются снизу вверх: чем выше положение на стане, тем выше звук. Можно использовать дополнительные линейки (лежеры) для очень высоких или низких нот.",
    ),
    TheorySection(
        title = "Скрипичный и басовый ключи",
        body = "Скрипичный ключ (G) задаёт положение ноты соль на второй линии снизу. Басовый ключ (F) — положение фа между двумя точками. Альтовый ключ (C) чаще для альта и виолы. В приложении песочница ориентирована на диапазон, удобный для скрипичного ключа.",
    ),
    TheorySection(
        title = "Длительности",
        body = "Целая, половинная, четвертная, восьмая, шестнадцатая и т.д. Точка увеличивает длительность в полтора раза. В песочнице задаётся длительность звучания каждой ноты в миллисекундах (упрощённо).",
    ),
    TheorySection(
        title = "Размер такта",
        body = "В числителе — число долей в такте, в знаменателе — какая нота считается за одну долю (4 = четверть). Например 4/4 — четыре четверти, 3/4 — три четверти (вальс).",
    ),
    TheorySection(
        title = "Тональность и знаки при ключе",
        body = "Знаки диеза/бемоля в начале обозначают тональность. Диез повышает ступень на полтона, бемоль понижает. Бекар отменяет действие знака при ключе.",
    ),
    TheorySection(
        title = "Интервалы",
        body = "Расстояние между двумя высотами. Примы, секунды, терции… Чистая квинта — 7 полутонов, большая терция — 4 полутона. Созвучия строятся из интервалов.",
    ),
    TheorySection(
        title = "Ступени и лады",
        body = "Мажорный лад: ступени I–VII с характерным набором тонов и полутонов (например между III–IV и VII–I). Натуральный минор, гармонический и мелодический минор — отличия по VII ступени.",
    ),
    TheorySection(
        title = "Сольфеджио",
        body = "В русской системе: До, Ре, Ми, Фа, Соль, Ля, Си; повышение — «диез», понижение — «бемоль». Можно субтоновое (с подвижным «До») или буквенное обозначение (C, D, E…).",
    ),
    TheorySection(
        title = "Квартовый круг",
        body = "Связь тональностей: добавление диезов идёт по квинтам вверх (Фа→До→Соль…), бемолей — по квинтам вниз. Помогает запоминать знаки при ключе и модуляции.",
    ),
    TheorySection(
        title = "Советы по уху",
        body = "Пойте интервалы, прогрессии, настройтесь на эталон (Ля 440 Гц). В разделе «Определение ноты» используйте микрофон: тишина и чистый тон дают точнее всего, избегайте шумов.",
    ),
)

private data class CapturedPitch(val midi: Int, val hz: Float, val labelEn: String, val labelRu: String)

private data class SandboxNote(val id: Long, val midi: Int, val durationMs: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTheoryNotesScreen(onBack: () -> Unit) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Справочник", "Определение ноты", "Песочница")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ноты") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = tabIndex == i,
                        onClick = { tabIndex = i },
                        text = {
                            Text(
                                title,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }
            when (tabIndex) {
                0 -> TheoryHandbookTab()
                1 -> PitchListenerTab()
                2 -> SandboxTab()
            }
        }
    }
}

@Composable
private fun TheoryHandbookTab() {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(theoryHandbook) { _, section ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        section.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PitchListenerTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listening by remember { mutableStateOf(false) }
    var displayHz by remember { mutableFloatStateOf(0f) }
    var hasSignal by remember { mutableStateOf(false) }
    var smoothed by remember { mutableStateOf<Float?>(null) }
    var currentMidi by remember { mutableIntStateOf(69) }
    var captured by remember { mutableStateOf(listOf<CapturedPitch>()) }
    var capturedNoteDurationMs by remember { mutableFloatStateOf(350f) }
    var capturedPauseMs by remember { mutableFloatStateOf(80f) }
    var pitchTimbre by remember { mutableStateOf(NoteTimbre.SINE) }
    var pitchPlaybackSpeed by remember { mutableFloatStateOf(1f) }
    var playCapturedJob by remember { mutableStateOf<Job?>(null) }
    val capturedRef = rememberUpdatedState(captured)
    val capturedDurRef = rememberUpdatedState(capturedNoteDurationMs)
    val capturedPauseRef = rememberUpdatedState(capturedPauseMs)
    val pitchTimbreRef = rememberUpdatedState(pitchTimbre)
    val pitchSpeedRef = rememberUpdatedState(pitchPlaybackSpeed)

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    SideEffect {
        SineTonePlayer.bindInstrumentSampleContext(context)
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            InstrumentSamplePlayer.ensureLoaded(context.applicationContext)
        }
    }

    LaunchedEffect(listening, hasPermission) {
        if (!listening || !hasPermission) {
            hasSignal = false
            smoothed = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
            if (minBuf <= 0) return@withContext
            val bufSize = maxOf(minBuf, 8192)
            @Suppress("MissingPermission")
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                encoding,
                bufSize,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return@withContext
            }
            val buffer = ShortArray(4096)
            record.startRecording()
            try {
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read < 2048) continue
                    val hz = PitchEstimator.estimateHz(buffer.copyOf(read), sampleRate)
                    withContext(Dispatchers.Main) {
                        if (hz != null) {
                            val s = PitchEstimator.smooth(smoothed, hz)
                            smoothed = s
                            displayHz = s
                            hasSignal = true
                            currentMidi = MusicTheoryUtils.midiFromHz(s.toDouble())
                        }
                    }
                }
            } finally {
                try {
                    record.stop()
                } catch (_: Exception) {
                }
                record.release()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { playCapturedJob?.cancel() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Говорите или пойте в микрофон — отображается ближайшая нота в равномерно темперированном строе. Нажмите «Зафиксировать», чтобы добавить ноту в список (для записи мелодии буквами).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasPermission) {
            OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Разрешить микрофон")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = listening,
                onClick = { listening = !listening },
                label = { Text(if (listening) "Слушаю…" else "Слушать") },
                leadingIcon = { Icon(Icons.Default.Mic, null, Modifier.size(18.dp)) },
            )
        }
        if (hasSignal && smoothed != null) {
            val hz = smoothed!!
            val en = MusicTheoryUtils.englishName(currentMidi)
            val ru = MusicTheoryUtils.russianName(currentMidi)
            val cents = MusicTheoryUtils.centsFromEqualTemperament(hz.toDouble(), currentMidi)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "%.1f Гц".format(hz),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("$en  ·  $ru", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Отклонение от строя: %.0f ¢".format(cents),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val hz = smoothed ?: return@OutlinedButton
                        val midi = MusicTheoryUtils.midiFromHz(hz.toDouble())
                        captured = captured + CapturedPitch(
                            midi = midi,
                            hz = hz,
                            labelEn = MusicTheoryUtils.englishName(midi),
                            labelRu = MusicTheoryUtils.russianName(midi),
                        )
                    },
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Text("Зафиксировать")
                }
                OutlinedButton(
                    onClick = {
                        val sp = pitchPlaybackSpeed.coerceIn(0.25f, 4f)
                        val d = (capturedNoteDurationMs.toInt().coerceIn(40, 2000) / sp)
                            .toInt()
                            .coerceIn(40, 4000)
                        SineTonePlayer.playMidiNote(currentMidi, d, timbre = pitchTimbre)
                    },
                    enabled = playCapturedJob?.isActive != true,
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Text("Проиграть ноту")
                }
            }
        } else if (listening) {
            Text("Пойте или сыграйте устойчивый тон…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (captured.isNotEmpty() || (hasSignal && smoothed != null)) {
            Text("Тембр при проигрывании", style = MaterialTheme.typography.labelMedium)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = pitchTimbre == NoteTimbre.SINE,
                    onClick = { pitchTimbre = NoteTimbre.SINE },
                    label = { Text("Синус") },
                )
                FilterChip(
                    selected = pitchTimbre == NoteTimbre.PIANO,
                    onClick = { pitchTimbre = NoteTimbre.PIANO },
                    label = { Text("Пианино") },
                )
                FilterChip(
                    selected = pitchTimbre == NoteTimbre.VIOLIN,
                    onClick = { pitchTimbre = NoteTimbre.VIOLIN },
                    label = { Text("Скрипка") },
                )
            }
            Text(
                "Длительность воспроизведения ноты: ${capturedNoteDurationMs.toInt()} мс",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = capturedNoteDurationMs,
                onValueChange = { capturedNoteDurationMs = it },
                valueRange = 80f..900f,
            )
            Text(
                "Интервал между нотами при проигрывании списка: ${capturedPauseMs.toInt()} мс",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = capturedPauseMs,
                onValueChange = { capturedPauseMs = it },
                valueRange = 0f..500f,
            )
            Text(
                "Скорость прослушивания: ${"%.2f".format(pitchPlaybackSpeed)}× " +
                    "(выше — быстрее ноты и паузы при воспроизведении)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = pitchPlaybackSpeed,
                onValueChange = { pitchPlaybackSpeed = it },
                valueRange = 0.25f..3f,
            )
        }
        Text("Зафиксированные ноты", style = MaterialTheme.typography.titleSmall)
        if (captured.isEmpty()) {
            Text("Пока пусто", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (playCapturedJob?.isActive == true) {
                            playCapturedJob?.cancel()
                            playCapturedJob = null
                        } else {
                            playCapturedJob = scope.launch {
                                try {
                                    val seq = capturedRef.value
                                    if (seq.isEmpty()) return@launch
                                    val speed = pitchSpeedRef.value.coerceIn(0.25f, 4f)
                                    val dur = (capturedDurRef.value.toInt().coerceIn(40, 2000) / speed)
                                        .toInt()
                                        .coerceIn(40, 4000)
                                    val p = (capturedPauseRef.value / speed).toLong().coerceIn(0, 2000)
                                    val timbre = pitchTimbreRef.value
                                    for (i in seq.indices) {
                                        ensureActive()
                                        withContext(Dispatchers.IO) {
                                            SineTonePlayer.playMidiNoteBlocking(
                                                seq[i].midi,
                                                dur,
                                                timbre = timbre,
                                            )
                                        }
                                        if (i < seq.lastIndex) delay(p)
                                    }
                                } finally {
                                    playCapturedJob = null
                                }
                            }
                        }
                    },
                    enabled = captured.isNotEmpty(),
                ) {
                    if (playCapturedJob?.isActive == true) {
                        Icon(Icons.Default.Pause, null, Modifier.size(18.dp))
                        Text("Стоп")
                    } else {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Text("Играть список")
                    }
                }
            }
            captured.forEachIndexed { i, c ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}. ${c.labelRu} (${c.labelEn}) ${"%.1f".format(c.hz)} Гц")
                    TextButton(
                        onClick = {
                            val sp = pitchPlaybackSpeed.coerceIn(0.25f, 4f)
                            val d = (capturedNoteDurationMs.toInt().coerceIn(40, 2000) / sp)
                                .toInt()
                                .coerceIn(40, 4000)
                            SineTonePlayer.playMidiNote(c.midi, d, timbre = pitchTimbre)
                        },
                        enabled = playCapturedJob?.isActive != true,
                    ) {
                        Text("▶")
                    }
                }
            }
            TextButton(
                onClick = {
                    playCapturedJob?.cancel()
                    playCapturedJob = null
                    captured = emptyList()
                },
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Очистить список")
            }
        }
    }
}

@Composable
private fun SandboxTab() {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(listOf<SandboxNote>()) }
    var nextNoteId by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableFloatStateOf(350f) }
    var pauseMs by remember { mutableFloatStateOf(80f) }
    var loop by remember { mutableStateOf(false) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var playingIndex by remember { mutableStateOf<Int?>(null) }
    var noteProgress by remember { mutableFloatStateOf(0f) }
    var pauseRemainingMs by remember { mutableStateOf<Float?>(null) }
    var draftMidi by remember { mutableStateOf<Int?>(null) }
    var sandboxTimbre by remember { mutableStateOf(NoteTimbre.SINE) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val notesRef = rememberUpdatedState(notes)
    val loopRef = rememberUpdatedState(loop)
    val pauseRef = rememberUpdatedState(pauseMs)
    val timbreRef = rememberUpdatedState(sandboxTimbre)
    val speedRef = rememberUpdatedState(playbackSpeed)

    SideEffect {
        SineTonePlayer.bindInstrumentSampleContext(context)
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            InstrumentSamplePlayer.ensureLoaded(context.applicationContext)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playJob?.cancel()
            SineTonePlayer.stopSustain()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Нажмите на стан и удерживайте: звучит тон (синус — синтез; пианино и скрипка — записанные сэмплы), перетаскивайте палец вверх/вниз — меняется высота. Отпустите палец — нота сохранится в последовательность. Длительность и пауза при проигрывании списка — ниже.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Тембр", style = MaterialTheme.typography.labelMedium)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = sandboxTimbre == NoteTimbre.SINE,
                onClick = { sandboxTimbre = NoteTimbre.SINE },
                label = { Text("Синус") },
            )
            FilterChip(
                selected = sandboxTimbre == NoteTimbre.PIANO,
                onClick = { sandboxTimbre = NoteTimbre.PIANO },
                label = { Text("Пианино") },
            )
            FilterChip(
                selected = sandboxTimbre == NoteTimbre.VIOLIN,
                onClick = { sandboxTimbre = NoteTimbre.VIOLIN },
                label = { Text("Скрипка") },
            )
        }
        Text(
            "Пианино — сэмплы рояля Salamander (CC-BY). Скрипка — запись смычка виолончели (Philharmonia). Высота в пределах октав подстраивается программно.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StaffTapCanvas(
            lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            noteColor = MaterialTheme.colorScheme.primary,
            notes = notes,
            highlightIndex = playingIndex,
            draftMidi = draftMidi,
            timbre = sandboxTimbre,
            onDraftChange = { draftMidi = it },
            onNoteCommitted = { midi ->
                notes = notes + SandboxNote(nextNoteId++, midi, durationMs.toInt())
            },
            onPointerDown = {
                playJob?.cancel()
                playJob = null
                playingIndex = null
                noteProgress = 0f
                pauseRemainingMs = null
            },
        )
        Text(
            "Удерживайте и ведите палец по вертикали; отпускание фиксирует ноту.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                playJob?.cancel()
                playJob = null
                playingIndex = null
                noteProgress = 0f
                pauseRemainingMs = null
                notes = emptyList()
            },
            enabled = notes.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
            Text("Очистить нотный стан")
        }
        Text("Длительность ноты: ${durationMs.toInt()} мс")
        Slider(
            value = durationMs,
            onValueChange = { durationMs = it },
            valueRange = 80f..900f,
        )
        Text("Пауза между нотами: ${pauseMs.toInt()} мс")
        Slider(
            value = pauseMs,
            onValueChange = { pauseMs = it },
            valueRange = 0f..500f,
        )
        Text(
            "Скорость воспроизведения: ${"%.2f".format(playbackSpeed)}× " +
                "(быстрее — выше, длительности и паузы при «Играть» короче)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = playbackSpeed,
            onValueChange = { playbackSpeed = it },
            valueRange = 0.25f..3f,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = loop, onCheckedChange = { loop = it })
            Icon(Icons.Default.Repeat, null, Modifier.size(20.dp))
            Text("Зациклить проигрывание")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (playJob?.isActive == true) {
                        playJob?.cancel()
                        playJob = null
                        playingIndex = null
                        noteProgress = 0f
                        pauseRemainingMs = null
                    } else if (notes.isNotEmpty()) {
                        playJob = scope.launch {
                            try {
                                do {
                                    val seq = notesRef.value.toList()
                                    if (seq.isEmpty()) break
                                    val speed = speedRef.value.coerceIn(0.25f, 4f)
                                    for (i in seq.indices) {
                                        ensureActive()
                                        val n = seq[i]
                                        val dur = (n.durationMs.coerceIn(40, 2000) / speed)
                                            .toInt()
                                            .coerceIn(40, 4000)
                                        val p = (pauseRef.value / speed).toLong().coerceIn(0, 2000)
                                        val progressJob = launch {
                                            val start = System.currentTimeMillis()
                                            while (isActive) {
                                                val elapsed =
                                                    (System.currentTimeMillis() - start).toFloat()
                                                if (elapsed >= dur) {
                                                    playingIndex = i
                                                    noteProgress = 1f
                                                    break
                                                }
                                                playingIndex = i
                                                noteProgress = (elapsed / dur).coerceIn(0f, 1f)
                                                delay(32)
                                            }
                                        }
                                        withContext(Dispatchers.IO) {
                                            SineTonePlayer.playMidiNoteBlocking(
                                                n.midi,
                                                dur,
                                                timbre = timbreRef.value,
                                            )
                                        }
                                        progressJob.cancel()
                                        progressJob.join()
                                        playingIndex = null
                                        noteProgress = 0f
                                        if (p > 0) {
                                            ensureActive()
                                            val pauseStart = System.currentTimeMillis()
                                            while (isActive) {
                                                val rem = p - (System.currentTimeMillis() - pauseStart)
                                                if (rem <= 0) {
                                                    pauseRemainingMs = null
                                                    break
                                                }
                                                pauseRemainingMs = rem.toFloat()
                                                delay(32)
                                            }
                                        }
                                    }
                                } while (loopRef.value && isActive)
                            } finally {
                                playingIndex = null
                                noteProgress = 0f
                                pauseRemainingMs = null
                                playJob = null
                            }
                        }
                    }
                },
                enabled = notes.isNotEmpty(),
            ) {
                if (playJob?.isActive == true) {
                    Icon(Icons.Default.Pause, null, Modifier.size(18.dp))
                    Text("Стоп")
                } else {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Text("Играть")
                }
            }
            OutlinedButton(onClick = { notes = notes.dropLast(1) }, enabled = notes.isNotEmpty()) {
                Text("Удалить последнюю")
            }
            OutlinedButton(
                onClick = {
                    playJob?.cancel()
                    playJob = null
                    playingIndex = null
                    noteProgress = 0f
                    pauseRemainingMs = null
                    notes = emptyList()
                },
                enabled = notes.isNotEmpty(),
            ) {
                Text("Очистить")
            }
        }
        if (playJob?.isActive == true) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (val idx = playingIndex) {
                        null -> {
                            val rem = pauseRemainingMs
                            if (rem != null) {
                                Text(
                                    "Пауза до следующей ноты: ${rem.toInt()} мс",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            } else {
                                Text(
                                    "Воспроизведение…",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                        else -> {
                            val n = notes.getOrNull(idx)
                            if (n != null) {
                                val spd = playbackSpeed.coerceIn(0.25f, 4f)
                                val total =
                                    (n.durationMs.coerceIn(40, 2000) / spd).toInt().coerceIn(40, 4000)
                                val elapsed = (noteProgress * total).toInt()
                                val left = (total - elapsed).coerceAtLeast(0)
                                Text(
                                    "Сейчас: ${idx + 1}. ${MusicTheoryUtils.russianName(n.midi)} " +
                                        "(${MusicTheoryUtils.englishName(n.midi)})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "Время ноты: $elapsed / $total мс · осталось ~$left мс " +
                                        "(${"%.2f".format(playbackSpeed)}×)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                LinearProgressIndicator(
                                    progress = { noteProgress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Text("Последовательность (${notes.size})", style = MaterialTheme.typography.titleSmall)
        notes.forEachIndexed { i, n ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "${i + 1}. ${MusicTheoryUtils.russianName(n.midi)} (${MusicTheoryUtils.englishName(n.midi)}) — ${n.durationMs} мс",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (i > 0) {
                            val m = notes.toMutableList()
                            val t = m[i - 1]
                            m[i - 1] = m[i]
                            m[i] = t
                            notes = m
                        }
                    },
                    enabled = i > 0,
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Выше")
                }
                IconButton(
                    onClick = {
                        if (i < notes.lastIndex) {
                            val m = notes.toMutableList()
                            val t = m[i + 1]
                            m[i + 1] = m[i]
                            m[i] = t
                            notes = m
                        }
                    },
                    enabled = i < notes.lastIndex,
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Ниже")
                }
                IconButton(onClick = { notes = notes.filterIndexed { j, _ -> j != i } }) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
                }
            }
        }
    }
}

/** Нотный стан: удержание — звук и перетаскивание по вертикали (MIDI 55–79), отпускание фиксирует ноту. */
@Composable
private fun StaffTapCanvas(
    lineColor: Color,
    noteColor: Color,
    notes: List<SandboxNote>,
    highlightIndex: Int?,
    draftMidi: Int?,
    timbre: NoteTimbre,
    onDraftChange: (Int?) -> Unit,
    onNoteCommitted: (Int) -> Unit,
    onPointerDown: () -> Unit,
) {
    val onDraftState = rememberUpdatedState(onDraftChange)
    val onCommitState = rememberUpdatedState(onNoteCommitted)
    val onDownState = rememberUpdatedState(onPointerDown)
    val timbreState = rememberUpdatedState(timbre)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        ),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .pointerInput(timbre) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onDownState.value()
                        val pointerId = down.id
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        fun yToMidi(y: Float): Int {
                            val yy = y.coerceIn(0f, h)
                            val t = 1f - (yy / h)
                            return (55 + t * (79 - 55)).toInt().coerceIn(55, 79)
                        }
                        var lastMidi = yToMidi(down.position.y)
                        SineTonePlayer.startSustain(lastMidi, timbre = timbreState.value)
                        onDraftState.value(lastMidi)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.find { it.id == pointerId }
                            if (change == null) {
                                SineTonePlayer.stopSustain()
                                onDraftState.value(null)
                                break
                            }
                            lastMidi = yToMidi(change.position.y)
                            if (!change.pressed) {
                                SineTonePlayer.stopSustain()
                                onDraftState.value(null)
                                onCommitState.value(lastMidi)
                                change.consume()
                                break
                            }
                            SineTonePlayer.setSustainMidi(lastMidi)
                            onDraftState.value(lastMidi)
                            change.consume()
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val lineCount = 5
            val sp = h / (lineCount + 1)
            for (i in 1..lineCount) {
                val yLine = sp * i
                drawLine(
                    color = lineColor,
                    start = Offset(0f, yLine),
                    end = Offset(w, yLine),
                    strokeWidth = 2f,
                )
            }
            fun yFromMidi(midi: Int): Float {
                val t = (midi - 55) / (79 - 55).toFloat()
                return (1f - t.coerceIn(0f, 1f)) * h
            }
            fun xForIndex(index: Int, total: Int): Float {
                return if (total <= 1) {
                    w * 0.5f
                } else {
                    (index + 1f) / (total + 1f) * w
                }
            }
            val n = notes.size
            notes.forEachIndexed { index, note ->
                val x = xForIndex(index, n)
                val cy = yFromMidi(note.midi)
                val isPlaying = highlightIndex == index
                val r = if (isPlaying) 26f else 18f
                val fill = if (isPlaying) {
                    noteColor.copy(alpha = 1f)
                } else {
                    noteColor.copy(alpha = 0.9f)
                }
                drawCircle(color = fill, radius = r, center = Offset(x, cy))
                drawCircle(
                    color = lineColor,
                    radius = r,
                    center = Offset(x, cy),
                    style = Stroke(width = if (isPlaying) 3.5f else 2.5f),
                )
            }
            val dm = draftMidi
            if (dm != null) {
                val xDraft = xForIndex(n, n + 1)
                val cy = yFromMidi(dm)
                val r = 22f
                drawCircle(
                    color = noteColor.copy(alpha = 0.45f),
                    radius = r,
                    center = Offset(xDraft, cy),
                )
                drawCircle(
                    color = lineColor.copy(alpha = 0.9f),
                    radius = r,
                    center = Offset(xDraft, cy),
                    style = Stroke(width = 3f),
                )
            }
        }
    }
}
