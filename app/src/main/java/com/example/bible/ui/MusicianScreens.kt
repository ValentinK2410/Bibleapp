package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bible.audio.PitchEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

/** Одна струна: подпись и эталонная частота (Гц, A4=440). */
data class TuningString(val label: String, val frequencyHz: Double)

private val GuitarTuningStandard: List<TuningString> = listOf(
    TuningString("6-я (E)", 82.41),
    TuningString("5-я (A)", 110.00),
    TuningString("4-я (D)", 146.83),
    TuningString("3-я (G)", 196.00),
    TuningString("2-я (B)", 246.94),
    TuningString("1-я (E)", 329.63),
)

private val ViolinTuningStandard: List<TuningString> = listOf(
    TuningString("Соль (G)", 196.00),
    TuningString("Ре (D)", 293.66),
    TuningString("Ля (A)", 440.00),
    TuningString("Ми (E)", 659.25),
)

private fun hzToNoteLabel(hz: Double): String {
    val midi = 12.0 * log2(hz / 440.0) + 69.0
    val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val n = midi.roundToInt().coerceIn(0, 127)
    val noteIndex = (n % 12 + 12) % 12
    val octave = n / 12 - 1
    return "${names[noteIndex]}$octave"
}

private fun centsBetween(hz: Double, targetHz: Double): Double =
    1200.0 * log2(hz / targetHz)

private fun nearestString(hz: Double, strings: List<TuningString>): TuningString? {
    if (strings.isEmpty()) return null
    return strings.minByOrNull { abs(centsBetween(hz, it.frequencyHz)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicianSectionScreen(
    onBack: () -> Unit,
    onOpenGuitarTuner: () -> Unit,
    onOpenViolinTuner: () -> Unit,
    onOpenMetronome: () -> Unit,
    onOpenMusicNotes: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Для музыканта") },
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
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Тюнеры: микрофон и разрешение на запись. Метроном — звук через динамик, без микрофона.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            ElevatedCard(
                onClick = onOpenMusicNotes,
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text("Ноты", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Справочник по теории, определение высоты звука, песочница с проигрыванием",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
            ElevatedCard(
                onClick = onOpenMetronome,
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text("Метроном", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Размер 4/4, акцент на первую долю, темп 40–240 уд/мин",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
            ElevatedCard(
                onClick = onOpenGuitarTuner,
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text("Тюнер для гитары", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Строй E A D G B E — частота и отклонение в центах",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
            ElevatedCard(
                onClick = onOpenViolinTuner,
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text("Тюнер для скрипки", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Строй G D A E — частота и отклонение в центах",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun GuitarTunerScreen(onBack: () -> Unit) {
    InstrumentTunerScreen(
        title = "Тюнер: гитара",
        strings = GuitarTuningStandard,
        onBack = onBack,
    )
}

@Composable
fun ViolinTunerScreen(onBack: () -> Unit) {
    InstrumentTunerScreen(
        title = "Тюнер: скрипка",
        strings = ViolinTuningStandard,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstrumentTunerScreen(
    title: String,
    strings: List<TuningString>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var displayHz by remember { mutableFloatStateOf(0f) }
    var hasSignal by remember { mutableStateOf(false) }
    var smoothed by remember { mutableStateOf<Float?>(null) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
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
                            smoothed = PitchEstimator.smooth(smoothed, hz)
                            displayHz = smoothed!!
                            hasSignal = true
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!hasPermission) {
                Text(
                    "Для тюнера нужен доступ к микрофону.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Выдать разрешение")
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!listening) {
                        OutlinedButton(
                            onClick = { listening = true },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text("Слушать")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { listening = false },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text("Стоп")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (listening && hasSignal) {
                    val hz = displayHz.toDouble().coerceAtLeast(1.0)
                    val note = hzToNoteLabel(hz)
                    val nearest = nearestString(hz, strings)
                    val cents = nearest?.let { centsBetween(hz, it.frequencyHz) } ?: 0.0
                    Text(
                        "%.1f Гц".format(hz),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        note,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    nearest?.let { n ->
                        Text(
                            "Ближе к: ${n.label} (${"%.1f".format(n.frequencyHz)} Гц)",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            when {
                                abs(cents) < 5 -> "В норме"
                                cents > 0 -> "Выше на ${"%.0f".format(abs(cents))} ¢"
                                else -> "Ниже на ${"%.0f".format(abs(cents))} ¢"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        val t = (cents.coerceIn(-50.0, 50.0) + 50.0) / 100.0
                        LinearProgressIndicator(
                            progress = { t.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .height(8.dp),
                        )
                        Text(
                            "−50 ¢ … 0 … +50 ¢",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                } else if (listening) {
                    Text(
                        "Играйте струну у микрофона…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Нажмите «Слушать» и настройте струну по шкале.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Эталон (Гц)", style = MaterialTheme.typography.labelLarge)
                        strings.forEach { s ->
                            Text(
                                "${s.label}: ${"%.2f".format(s.frequencyHz)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class MetronomeToneOption(
    val label: String,
    val toneId: Int,
    val durationMs: Int,
)

private val metronomeToneOptions: List<MetronomeToneOption> = listOf(
    MetronomeToneOption("Бип", ToneGenerator.TONE_PROP_BEEP, 100),
    MetronomeToneOption("Бип 2", ToneGenerator.TONE_PROP_BEEP2, 100),
    MetronomeToneOption("Квит", ToneGenerator.TONE_PROP_ACK, 100),
    MetronomeToneOption("Отказ", ToneGenerator.TONE_PROP_NACK, 100),
    MetronomeToneOption("Звонок", ToneGenerator.TONE_SUP_RINGTONE, 200),
    MetronomeToneOption("DTMF 1", ToneGenerator.TONE_DTMF_1, 120),
    MetronomeToneOption("DTMF 2", ToneGenerator.TONE_DTMF_2, 120),
    MetronomeToneOption("DTMF 3", ToneGenerator.TONE_DTMF_3, 120),
    MetronomeToneOption("DTMF 4", ToneGenerator.TONE_DTMF_4, 120),
    MetronomeToneOption("DTMF 5", ToneGenerator.TONE_DTMF_5, 120),
    MetronomeToneOption("DTMF 6", ToneGenerator.TONE_DTMF_6, 120),
    MetronomeToneOption("DTMF 7", ToneGenerator.TONE_DTMF_7, 120),
    MetronomeToneOption("DTMF 8", ToneGenerator.TONE_DTMF_8, 120),
    MetronomeToneOption("DTMF 9", ToneGenerator.TONE_DTMF_9, 120),
    MetronomeToneOption("DTMF 0", ToneGenerator.TONE_DTMF_0, 120),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetronomeBeatToneDropdown(
    beatNumber: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val safeIndex = selectedIndex.coerceIn(0, metronomeToneOptions.lastIndex)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = metronomeToneOptions[safeIndex].label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Доля $beatNumber") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            singleLine = true,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp),
        ) {
            metronomeToneOptions.forEachIndexed { i, opt ->
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    onClick = {
                        onSelect(i)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeScreen(onBack: () -> Unit) {
    var bpm by remember { mutableIntStateOf(100) }
    var bpmText by remember { mutableStateOf("100") }
    var running by remember { mutableStateOf(false) }
    var beatInBar by remember { mutableIntStateOf(0) }
    var beatToneChoice by remember {
        mutableStateOf(intArrayOf(0, 1, 2, 3))
    }

    fun setBpm(v: Int) {
        val n = v.coerceIn(40, 240)
        bpm = n
        bpmText = n.toString()
    }

    val toneGen = remember {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                toneGen.release()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(
        running,
        bpm,
        beatToneChoice[0],
        beatToneChoice[1],
        beatToneChoice[2],
        beatToneChoice[3],
    ) {
        if (!running) return@LaunchedEffect
        var beat = 0
        while (isActive) {
            val periodMs = (60_000.0 / bpm).toLong().coerceAtLeast(30L)
            val inBar = beat % 4
            val toneIdx = beatToneChoice[inBar].coerceIn(0, metronomeToneOptions.lastIndex)
            val opt = metronomeToneOptions[toneIdx]
            toneGen.startTone(opt.toneId, opt.durationMs)
            beatInBar = inBar
            beat++
            delay(periodMs)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Метроном") },
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
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Размер 4/4 — для каждой доли можно выбрать свой звук",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(
                    onClick = { setBpm(bpm - 1) },
                    enabled = bpm > 40,
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Минус один уд/мин")
                }
                OutlinedTextField(
                    value = bpmText,
                    onValueChange = { s ->
                        val f = s.filter { it.isDigit() }.take(3)
                        bpmText = f
                        f.toIntOrNull()?.let { v ->
                            if (v in 40..240) bpm = v
                        }
                    },
                    label = { Text("Темп") },
                    suffix = { Text("уд/мин") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .widthIn(min = 120.dp, max = 160.dp)
                        .padding(horizontal = 8.dp),
                )
                IconButton(
                    onClick = { setBpm(bpm + 1) },
                    enabled = bpm < 240,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Плюс один уд/мин")
                }
            }
            Text(
                "Введите число от 40 до 240 или используйте ± и слайдер",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = bpm.toFloat(),
                onValueChange = { setBpm(it.toInt()) },
                valueRange = 40f..240f,
                steps = 199,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "40 — 240",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Доли",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (n in 1..4) {
                    val isCurrent = running && (beatInBar + 1 == n)
                    Text(
                        "$n",
                        style = if (isCurrent) {
                            MaterialTheme.typography.displaySmall
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                        fontWeight = if (n == 1) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            n == 1 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Звук по долям",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    for (i in 0 until 4) {
                        MetronomeBeatToneDropdown(
                            beatNumber = i + 1,
                            selectedIndex = beatToneChoice[i],
                            onSelect = { idx ->
                                beatToneChoice = beatToneChoice.copyOf().also { it[i] = idx }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = { running = !running },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (running) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Стоп")
                    } else {
                        Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Старт")
                    }
                }
            }
        }
    }
}
