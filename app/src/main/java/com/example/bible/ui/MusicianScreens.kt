package com.example.bible.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.bible.audio.MusicSynth
import com.example.bible.audio.PitchEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal val MusicHeroGradient = listOf(Color(0xFF0F172A), Color(0xFF312E81), Color(0xFF7C3AED))
internal val MusicTunerGradient = listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))
internal val MusicStringsGradient = listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
internal val MusicMetronomeGradient = listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))
internal val MusicNotesGradient = listOf(Color(0xFF10B981), Color(0xFF14B8A6))
private val InTuneColor = Color(0xFF22C55E)
private val NearColor = Color(0xFFF59E0B)
private val OffColor = Color(0xFFEF4444)

internal fun musicianPrefs(context: Context) =
    context.applicationContext.getSharedPreferences("musician_prefs", Context.MODE_PRIVATE)

private val NoteNames = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
private val NoteNamesRu = listOf("До", "До♯", "Ре", "Ре♯", "Ми", "Фа", "Фа♯", "Соль", "Соль♯", "Ля", "Ля♯", "Си")

private fun midiOf(hz: Double, a4: Double): Double = 12.0 * log2(hz / a4) + 69.0
private fun hzOfMidi(midi: Int, a4: Double): Double = a4 * 2.0.pow((midi - 69) / 12.0)
private fun centsBetween(hz: Double, targetHz: Double): Double = 1200.0 * log2(hz / targetHz)

/** «E2», «C#4» → номер MIDI. */
private fun midiOfName(name: String): Int {
    val sharp = name.contains('#')
    val letter = name.first()
    val octave = name.filter { it.isDigit() }.toInt()
    val base = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11).getValue(letter)
    return (octave + 1) * 12 + base + if (sharp) 1 else 0
}

private data class TunerPreset(val name: String, val notes: List<String>)

private enum class TunerInstrument(val label: String, val emoji: String, val minHz: Double, val presets: List<TunerPreset>) {
    GUITAR(
        "Гитара", "🎸", 65.0,
        listOf(
            TunerPreset("Стандарт", listOf("E2", "A2", "D3", "G3", "B3", "E4")),
            TunerPreset("Drop D", listOf("D2", "A2", "D3", "G3", "B3", "E4")),
            TunerPreset("Полтона ниже", listOf("D#2", "G#2", "C#3", "F#3", "A#3", "D#4")),
            TunerPreset("Open G", listOf("D2", "G2", "D3", "G3", "B3", "D4")),
            TunerPreset("DADGAD", listOf("D2", "A2", "D3", "G3", "A3", "D4")),
        ),
    ),
    BASS(
        "Бас", "🎸", 28.0,
        listOf(
            TunerPreset("4 струны", listOf("E1", "A1", "D2", "G2")),
            TunerPreset("5 струн", listOf("B0", "E1", "A1", "D2", "G2")),
            TunerPreset("Drop D", listOf("D1", "A1", "D2", "G2")),
        ),
    ),
    UKULELE(
        "Укулеле", "🪕", 120.0,
        listOf(
            TunerPreset("Стандарт", listOf("G4", "C4", "E4", "A4")),
            TunerPreset("Низкая G", listOf("G3", "C4", "E4", "A4")),
            TunerPreset("Баритон", listOf("D3", "G3", "B3", "E4")),
        ),
    ),
    VIOLIN("Скрипка", "🎻", 150.0, listOf(TunerPreset("Стандарт", listOf("G3", "D4", "A4", "E5")))),
    VIOLA("Альт", "🎻", 110.0, listOf(TunerPreset("Стандарт", listOf("C3", "G3", "D4", "A4")))),
    CELLO("Виолончель", "🎻", 55.0, listOf(TunerPreset("Стандарт", listOf("C2", "G2", "D3", "A3")))),
    CHROMATIC("Хроматический", "🎼", 40.0, emptyList()),
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
    val context = LocalContext.current
    val prefs = remember { musicianPrefs(context) }
    val bpm = remember { prefs.getInt("metronome_bpm", 100) }
    val a4 = remember { prefs.getInt("tuner_a4", 440) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Для музыканта", fontWeight = FontWeight.ExtraBold) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MusicianHero(bpm = bpm, a4 = a4)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MusicianTile(
                    title = "Тюнер",
                    subtitle = "Гитара, бас, укулеле и хроматический",
                    gradient = MusicTunerGradient,
                    art = { MiniGaugeArt(it) },
                    onClick = onOpenGuitarTuner,
                )
                MusicianTile(
                    title = "Смычковые",
                    subtitle = "Скрипка, альт и виолончель",
                    gradient = MusicStringsGradient,
                    art = { EmojiArt("🎻", it) },
                    onClick = onOpenViolinTuner,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MusicianTile(
                    title = "Метроном",
                    subtitle = "Любой размер, доли, tap-темп",
                    gradient = MusicMetronomeGradient,
                    art = { MiniMetronomeArt(it) },
                    onClick = onOpenMetronome,
                )
                MusicianTile(
                    title = "Ноты",
                    subtitle = "Теория, определение ноты, песочница",
                    gradient = MusicNotesGradient,
                    art = { EmojiArt("🎼", it) },
                    onClick = onOpenMusicNotes,
                )
            }
            Text(
                "Тюнеру нужен доступ к микрофону. Метроном и эталонные ноты звучат через динамик.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MusicianHero(bpm: Int, a4: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(MusicHeroGradient))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("ДЛЯ МУЗЫКАНТА", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Твоя карманная студия", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Настрой инструмент, держи темп и разбирайся в нотах — всё под рукой.",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroStat("♩ = $bpm", "темп")
            HeroStat("A = $a4", "Гц")
            HeroStat("7", "инструментов")
        }
    }
}

@Composable
private fun RowScope.HeroStat(value: String, label: String) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun RowScope.MusicianTile(
    title: String,
    subtitle: String,
    gradient: List<Color>,
    art: @Composable (Modifier) -> Unit,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(gradient))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            art(Modifier.fillMaxSize().padding(12.dp))
        }
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 15.sp, minLines = 2, maxLines = 2)
    }
}

@Composable
private fun EmojiArt(emoji: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { Text(emoji, fontSize = 52.sp) }
}

@Composable
private fun MiniGaugeArt(modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height * 0.9f)
        val r = minOf(size.width / 2f, size.height * 0.85f)
        drawArc(Color.White.copy(alpha = 0.35f), 200f, 140f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(r * 0.12f, cap = StrokeCap.Round))
        drawArc(InTuneColor, 262f, 16f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(r * 0.12f, cap = StrokeCap.Round))
        val a = Math.toRadians(-80.0)
        drawLine(Color.White, c, c + Offset((cos(a) * r * 0.85).toFloat(), (sin(a) * r * 0.85).toFloat()), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
        drawCircle(Color.White, r * 0.1f, c)
    }
}

@Composable
private fun MiniMetronomeArt(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.height * 0.8f
        val cx = size.width / 2f
        val top = size.height * 0.05f
        val bottom = size.height * 0.95f
        val body = Path().apply {
            moveTo(cx - w * 0.18f, top)
            lineTo(cx + w * 0.18f, top)
            lineTo(cx + w * 0.42f, bottom)
            lineTo(cx - w * 0.42f, bottom)
            close()
        }
        drawPath(body, Color.White.copy(alpha = 0.9f))
        val pivot = Offset(cx, bottom - size.height * 0.18f)
        val a = Math.toRadians(-70.0)
        val tip = pivot + Offset((cos(a) * size.height * 0.75).toFloat(), (sin(a) * size.height * 0.75).toFloat())
        drawLine(Color(0xFF7C3AED), pivot, tip, strokeWidth = size.height * 0.05f, cap = StrokeCap.Round)
        drawCircle(Color(0xFFEC4899), size.height * 0.07f, pivot + (tip - pivot) * 0.6f)
    }
}

@Composable
fun GuitarTunerScreen(onBack: () -> Unit) = InstrumentTunerScreen(TunerInstrument.GUITAR, onBack)

@Composable
fun ViolinTunerScreen(onBack: () -> Unit) = InstrumentTunerScreen(TunerInstrument.VIOLIN, onBack)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstrumentTunerScreen(initial: TunerInstrument, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { musicianPrefs(context) }
    val instruments = remember(initial) {
        if (initial == TunerInstrument.VIOLIN) {
            listOf(TunerInstrument.VIOLIN, TunerInstrument.VIOLA, TunerInstrument.CELLO, TunerInstrument.CHROMATIC)
        } else {
            listOf(TunerInstrument.GUITAR, TunerInstrument.BASS, TunerInstrument.UKULELE, TunerInstrument.CHROMATIC)
        }
    }
    val prefKey = "tuner_instrument_${initial.name}"
    var instrument by remember {
        mutableStateOf(
            prefs.getString(prefKey, null)?.let { n -> instruments.firstOrNull { it.name == n } } ?: initial,
        )
    }
    var presetIndex by remember(instrument) { mutableIntStateOf(prefs.getInt("tuner_preset_${instrument.name}", 0)) }
    var a4 by remember { mutableIntStateOf(prefs.getInt("tuner_a4", 440)) }
    val preset = instrument.presets.getOrNull(presetIndex) ?: instrument.presets.firstOrNull()
    val stringMidis = remember(preset) { preset?.notes?.map(::midiOfName).orEmpty() }
    var lockedString by remember(instrument, presetIndex) { mutableStateOf<Int?>(null) }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    var listening by remember { mutableStateOf(true) }
    var hz by remember { mutableFloatStateOf(0f) }
    var hasSignal by remember { mutableStateOf(false) }
    var inTuneSince by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val view = LocalView.current
    DisposableEffect(listening) {
        view.keepScreenOn = listening
        onDispose {
            view.keepScreenOn = false
            MusicSynth.stopReference()
        }
    }

    LaunchedEffect(listening, hasPermission, instrument) {
        if (!listening || !hasPermission) {
            hasSignal = false
            return@LaunchedEffect
        }
        val minHz = instrument.minHz
        withContext(Dispatchers.IO) {
            val sampleRate = 44100
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) return@withContext
            @Suppress("MissingPermission")
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, 8192),
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return@withContext
            }
            val buffer = ShortArray(4096)
            var smoothed: Float? = null
            var lastSignal = 0L
            record.startRecording()
            try {
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read < 2048) continue
                    var sum = 0.0
                    for (i in 0 until read) sum += buffer[i].toDouble() * buffer[i]
                    val rms = sqrt(sum / read)
                    val est = if (rms > 250) PitchEstimator.estimateHz(buffer.copyOf(read), sampleRate, minHz) else null
                    val now = System.currentTimeMillis()
                    if (est != null) {
                        val prev = smoothed
                        smoothed = if (prev != null && abs(centsBetween(est.toDouble(), prev.toDouble())) > 80) est else PitchEstimator.smooth(prev, est)
                        lastSignal = now
                    } else if (now - lastSignal > 1200) {
                        smoothed = null
                    }
                    val shown = smoothed
                    withContext(Dispatchers.Main) {
                        if (shown != null) {
                            hz = shown
                            hasSignal = true
                        } else {
                            hasSignal = false
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

    val a4d = a4.toDouble()
    val reading: TunerReading? = if (hasSignal && hz > 0f) {
        val f = hz.toDouble()
        val targetMidi = when {
            lockedString != null -> stringMidis.getOrNull(lockedString!!)
            stringMidis.isNotEmpty() -> stringMidis.minByOrNull { abs(centsBetween(f, hzOfMidi(it, a4d))) }
            else -> null
        } ?: midiOf(f, a4d).roundToInt()
        TunerReading(f, targetMidi, centsBetween(f, hzOfMidi(targetMidi, a4d)))
    } else {
        null
    }
    val inTuneNow = reading != null && abs(reading.cents) < 5
    LaunchedEffect(inTuneNow) {
        inTuneSince = if (inTuneNow) System.currentTimeMillis() else 0L
    }
    val settled = inTuneNow && inTuneSince > 0 && System.currentTimeMillis() - inTuneSince > 400

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тюнер", fontWeight = FontWeight.ExtraBold) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                instruments.forEach { inst ->
                    TmGradientPill(
                        text = "${inst.emoji} ${inst.label}",
                        selected = inst == instrument,
                        gradient = MusicTunerGradient,
                        onClick = {
                            instrument = inst
                            prefs.edit().putString(prefKey, inst.name).apply()
                        },
                    )
                }
            }
            if (instrument.presets.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    instrument.presets.forEachIndexed { i, p ->
                        TmGradientPill(
                            text = p.name,
                            selected = i == presetIndex,
                            gradient = MusicStringsGradient,
                            onClick = {
                                presetIndex = i
                                prefs.edit().putInt("tuner_preset_${instrument.name}", i).apply()
                            },
                        )
                    }
                }
            }
            TunerGaugeCard(
                reading = reading,
                settled = settled,
                listening = listening && hasPermission,
                needsPermission = !hasPermission,
            )
            if (stringMidis.isNotEmpty()) {
                TunerStringsRow(
                    midis = stringMidis,
                    a4 = a4d,
                    activeMidi = reading?.targetMidi,
                    inTune = settled,
                    locked = lockedString,
                    onTap = { i ->
                        lockedString = if (lockedString == i) null else i
                        MusicSynth.playReference(hzOfMidi(stringMidis[i], a4d))
                    },
                )
                Text(
                    if (lockedString != null) "🔒 Струна выбрана вручную — нажмите её ещё раз, чтобы вернуть автоопределение."
                    else "Нажмите на струну, чтобы услышать эталонную ноту.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(if (listening) MusicMetronomeGradient else MusicTunerGradient))
                        .clickable {
                            if (!hasPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) else listening = !listening
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (listening && hasPermission) Icons.Filled.Mic else Icons.Filled.MicOff,
                        contentDescription = if (listening) "Остановить" else "Слушать",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            !hasPermission -> "Разрешите микрофон"
                            listening -> "Слушаю…"
                            else -> "Пауза"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                    )
                    Text("Калибровка: Ля = $a4 Гц", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CalibrationButton(Icons.Filled.Remove, "Ниже") {
                    a4 = (a4 - 1).coerceAtLeast(415)
                    prefs.edit().putInt("tuner_a4", a4).apply()
                }
                CalibrationButton(Icons.Filled.Add, "Выше") {
                    a4 = (a4 + 1).coerceAtMost(466)
                    prefs.edit().putInt("tuner_a4", a4).apply()
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private data class TunerReading(val hz: Double, val targetMidi: Int, val cents: Double)

@Composable
private fun CalibrationButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TunerGaugeCard(reading: TunerReading?, settled: Boolean, listening: Boolean, needsPermission: Boolean) {
    val cents = reading?.cents?.toFloat()?.coerceIn(-50f, 50f) ?: 0f
    val needle by animateFloatAsState(cents, spring(dampingRatio = 0.7f, stiffness = 120f), label = "needle")
    val stateColor = when {
        reading == null -> Color.White.copy(alpha = 0.5f)
        abs(reading.cents) < 5 -> InTuneColor
        abs(reading.cents) < 15 -> NearColor
        else -> OffColor
    }
    val glow by animateColorAsState(if (settled) InTuneColor.copy(alpha = 0.35f) else Color.Transparent, label = "glow")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(MusicHeroGradient))
            .border(3.dp, glow, RoundedCornerShape(28.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.9f)) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height * 0.96f)
                val r = minOf(size.width / 2f * 0.92f, size.height * 0.9f)
                val arcTopLeft = Offset(c.x - r, c.y - r)
                val arcSize = Size(r * 2, r * 2)
                val stroke = r * 0.07f
                fun angleOf(ct: Float) = -90f + ct / 50f * 60f
                drawArc(Color.White.copy(alpha = 0.12f), angleOf(-50f), 120f, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(NearColor.copy(alpha = 0.55f), angleOf(-15f), 36f, false, arcTopLeft, arcSize, style = Stroke(stroke))
                drawArc(InTuneColor, angleOf(-5f), 12f, false, arcTopLeft, arcSize, style = Stroke(stroke))
                for (t in -50..50 step 10) {
                    val a = Math.toRadians(angleOf(t.toFloat()).toDouble())
                    val inner = if (t == 0) r * 0.78f else r * 0.84f
                    val p1 = c + Offset((cos(a) * inner).toFloat(), (sin(a) * inner).toFloat())
                    val p2 = c + Offset((cos(a) * r * 0.9f).toFloat(), (sin(a) * r * 0.9f).toFloat())
                    drawLine(Color.White.copy(alpha = if (t == 0) 0.9f else 0.4f), p1, p2, strokeWidth = if (t == 0) 5f else 3f, cap = StrokeCap.Round)
                }
                val a = Math.toRadians(angleOf(needle).toDouble())
                val tip = c + Offset((cos(a) * r * 0.95f).toFloat(), (sin(a) * r * 0.95f).toFloat())
                drawLine(stateColor, c, tip, strokeWidth = r * 0.035f, cap = StrokeCap.Round)
                drawCircle(stateColor, r * 0.07f, c)
                drawCircle(Color.White, r * 0.03f, c)
            }
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("♭", color = Color.White.copy(alpha = 0.6f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("♯", color = Color.White.copy(alpha = 0.6f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (reading != null) {
            val idx = Math.floorMod(reading.targetMidi, 12)
            val octave = reading.targetMidi / 12 - 1
            Row(verticalAlignment = Alignment.Bottom) {
                Text(NoteNames[idx], color = Color.White, fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Black)
                Text("$octave", color = Color.White.copy(alpha = 0.7f), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text("${NoteNamesRu[idx]} · ${"%.1f".format(reading.hz)} Гц", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            val c = reading.cents
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(stateColor.copy(alpha = 0.22f))
                    .border(1.dp, stateColor.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(
                    when {
                        abs(c) < 5 -> "✓ Настроено"
                        c > 0 -> "Высоко на ${abs(c).roundToInt()} ¢ — ослабьте ↓"
                        else -> "Низко на ${abs(c).roundToInt()} ¢ — подтяните ↑"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                )
            }
        } else {
            Text("—", color = Color.White.copy(alpha = 0.5f), fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Black)
            Text(
                when {
                    needsPermission -> "Нужен доступ к микрофону"
                    listening -> "Сыграйте ноту возле микрофона"
                    else -> "Нажмите на микрофон, чтобы начать"
                },
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun TunerStringsRow(
    midis: List<Int>,
    a4: Double,
    activeMidi: Int?,
    inTune: Boolean,
    locked: Int?,
    onTap: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        midis.forEachIndexed { i, m ->
            val active = locked == i || (locked == null && m == activeMidi)
            val bg = when {
                active && inTune -> listOf(InTuneColor, Color(0xFF10B981))
                active -> MusicTunerGradient
                else -> null
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .then(
                        if (bg != null) Modifier.background(Brush.linearGradient(bg))
                        else Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MusicTunerGradient.first().copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
                    )
                    .clickable { onTap(i) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val idx = Math.floorMod(m, 12)
                Text(
                    NoteNames[idx],
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = if (bg != null) Color.White else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${m / 12 - 1}",
                    fontSize = 11.sp,
                    color = if (bg != null) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (locked == i) {
                    Icon(Icons.Filled.Lock, contentDescription = "Выбрана", tint = Color.White, modifier = Modifier.size(12.dp))
                } else {
                    Text(
                        "${"%.0f".format(hzOfMidi(m, a4))}",
                        fontSize = 10.sp,
                        color = if (bg != null) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
