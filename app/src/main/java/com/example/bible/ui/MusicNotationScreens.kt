package com.example.bible.ui

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.bible.audio.InstrumentSamplePlayer
import com.example.bible.audio.MusicSynth
import com.example.bible.audio.MusicSynth.NoteEvent
import com.example.bible.audio.NoteTimbre
import com.example.bible.audio.PitchEstimator
import com.example.bible.audio.SineTonePlayer
import com.example.bible.music.MusicTheoryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

private val PaperGradient = listOf(Color(0xFFFFFBEB), Color(0xFFFDF2F8))

private fun playEventsAsync(scope: kotlinx.coroutines.CoroutineScope, events: List<NoteEvent>) {
    scope.launch(Dispatchers.Default) { MusicSynth.playEvents(events) }
}

private fun melody(midis: List<Int>, step: Double = 0.42, dur: Double = 0.38): List<NoteEvent> =
    midis.mapIndexed { i, m -> NoteEvent(m, i * step, dur) }

private fun arpeggioThenChord(root: Int, intervals: List<Int>): List<NoteEvent> {
    val arp = intervals.mapIndexed { i, s -> NoteEvent(root + s, i * 0.32, 0.3) }
    val at = intervals.size * 0.32 + 0.15
    return arp + intervals.map { NoteEvent(root + it, at, 1.4, 0.8) }
}

private fun intervalDemo(root: Int, semis: Int): List<NoteEvent> = listOf(
    NoteEvent(root, 0.0, 0.55),
    NoteEvent(root + semis, 0.6, 0.55),
    NoteEvent(root, 1.3, 1.1, 0.8),
    NoteEvent(root + semis, 1.3, 1.1, 0.8),
)

private fun rhythm(midi: Int, beats: List<Double>, bpm: Int = 90): List<NoteEvent> {
    val beat = 60.0 / bpm
    var t = 0.0
    return beats.map { b ->
        NoteEvent(midi, t, b * beat * 0.9).also { t += b * beat }
    }
}

private fun meter(beatsPerBar: Int, bars: Int = 2, bpm: Int = 110, strong: Set<Int> = setOf(0)): List<NoteEvent> {
    val beat = 60.0 / bpm
    return (0 until beatsPerBar * bars).map { i ->
        val inBar = i % beatsPerBar
        NoteEvent(if (inBar in strong) 84 else 79, i * beat, 0.09, if (inBar in strong) 1.0 else 0.55)
    }
}

private fun scaleOf(root: Int, steps: List<Int>): List<Int> = steps.map { root + it } + (root + 12)

private sealed interface TheoryDemo {
    data class Staff(val notes: List<StaffNote>) : TheoryDemo
    data class Sounds(val items: List<Pair<String, List<NoteEvent>>>) : TheoryDemo
    data object Fifths : TheoryDemo
}

private data class TheorySection(
    val emoji: String,
    val title: String,
    val body: String,
    val gradient: List<Color>,
    val demos: List<TheoryDemo> = emptyList(),
)

private val CMajor = listOf(60, 62, 64, 65, 67, 69, 71, 72)

private val theoryHandbook: List<TheorySection> = listOf(
    TheorySection(
        "🎼", "Нотный стан",
        "Пять линий, на которых записывается высота звука. Чем выше нота на стане, тем выше звук. Для очень высоких и низких нот добавляют короткие добавочные линейки — как у «До» первой октавы.",
        MusicNotesGradient,
        listOf(
            TheoryDemo.Staff(CMajor.map { StaffNote(it) }),
            TheoryDemo.Sounds(listOf("▶ Гамма До мажор" to melody(CMajor))),
        ),
    ),
    TheorySection(
        "𝄞", "Скрипичный и басовый ключи",
        "Скрипичный ключ (ключ «соль») закручивается вокруг второй линии — там стоит Соль первой октавы. Басовый ключ (ключ «фа») отмечает Фа малой октавы на четвёртой линии. Альтовый ключ (ключ «до») — для альта.",
        MusicTunerGradient,
        listOf(
            TheoryDemo.Staff(listOf(StaffNote(67, 2f))),
            TheoryDemo.Sounds(listOf("▶ Соль первой октавы" to listOf(NoteEvent(67, 0.0, 1.2)))),
        ),
    ),
    TheorySection(
        "♩", "Длительности",
        "Целая нота звучит 4 доли, половинная — 2, четвертная — 1, восьмая — половину доли. Точка после ноты удлиняет её в полтора раза, лига соединяет две ноты в одну.",
        MusicMetronomeGradient,
        listOf(
            TheoryDemo.Staff(listOf(StaffNote(67, 4f), StaffNote(67, 2f), StaffNote(67, 1f), StaffNote(67, 0.5f), StaffNote(67, 0.5f))),
            TheoryDemo.Sounds(listOf("▶ Целая · половинная · четверть · восьмые" to rhythm(67, listOf(4.0, 2.0, 1.0, 0.5, 0.5)))),
        ),
    ),
    TheorySection(
        "🥁", "Размер такта",
        "Верхняя цифра — сколько долей в такте, нижняя — какая нота считается долей. 2/4 — марш, 3/4 — вальс, 4/4 — большинство песен, 6/8 — «покачивающийся» размер с двумя сильными долями.",
        MusicStringsGradient,
        listOf(
            TheoryDemo.Sounds(
                listOf(
                    "▶ 2/4 марш" to meter(2, bars = 3),
                    "▶ 3/4 вальс" to meter(3),
                    "▶ 4/4" to meter(4),
                    "▶ 6/8" to meter(6, bpm = 160, strong = setOf(0, 3)),
                ),
            ),
        ),
    ),
    TheorySection(
        "♯", "Диезы, бемоли и бекар",
        "Диез ♯ повышает ноту на полтона, бемоль ♭ понижает, бекар ♮ отменяет знак. Знаки в начале строки (при ключе) задают тональность и действуют на всю пьесу.",
        listOf(Color(0xFF6366F1), Color(0xFFEC4899)),
        listOf(
            TheoryDemo.Staff(listOf(StaffNote(65), StaffNote(66), StaffNote(72), StaffNote(73))),
            TheoryDemo.Sounds(listOf("▶ Фа и Фа♯" to melody(listOf(65, 66), step = 0.7, dur = 0.6), "▶ До и До♯" to melody(listOf(72, 73), step = 0.7, dur = 0.6))),
        ),
    ),
    TheorySection(
        "↔️", "Интервалы",
        "Интервал — расстояние между двумя звуками в полутонах. Терции и сексты звучат мягко, кварта и квинта — «пусто» и устойчиво, секунды и септимы — напряжённо. Нажмите, чтобы услышать по очереди и вместе.",
        MusicTunerGradient,
        listOf(
            TheoryDemo.Sounds(
                listOf(
                    "м2 · 1" to intervalDemo(60, 1),
                    "б2 · 2" to intervalDemo(60, 2),
                    "м3 · 3" to intervalDemo(60, 3),
                    "б3 · 4" to intervalDemo(60, 4),
                    "ч4 · 5" to intervalDemo(60, 5),
                    "тритон · 6" to intervalDemo(60, 6),
                    "ч5 · 7" to intervalDemo(60, 7),
                    "м6 · 8" to intervalDemo(60, 8),
                    "б6 · 9" to intervalDemo(60, 9),
                    "м7 · 10" to intervalDemo(60, 10),
                    "ч8 · 12" to intervalDemo(60, 12),
                ),
            ),
        ),
    ),
    TheorySection(
        "🎹", "Аккорды",
        "Трезвучие — три звука через терцию. Мажорное (большая + малая терция) звучит светло, минорное (малая + большая) — грустно. Септаккорд добавляет четвёртый звук и «просит» разрешения.",
        MusicMetronomeGradient,
        listOf(
            TheoryDemo.Sounds(
                listOf(
                    "▶ Мажор" to arpeggioThenChord(60, listOf(0, 4, 7)),
                    "▶ Минор" to arpeggioThenChord(60, listOf(0, 3, 7)),
                    "▶ Уменьшённый" to arpeggioThenChord(60, listOf(0, 3, 6)),
                    "▶ Увеличенный" to arpeggioThenChord(60, listOf(0, 4, 8)),
                    "▶ Доминантсептаккорд" to arpeggioThenChord(60, listOf(0, 4, 7, 10)),
                    "▶ Большой мажорный септ" to arpeggioThenChord(60, listOf(0, 4, 7, 11)),
                ),
            ),
        ),
    ),
    TheorySection(
        "🌈", "Лады и гаммы",
        "Мажор строится по формуле тон-тон-полутон-тон-тон-тон-полутон. В натуральном миноре другая формула; в гармоническом повышена VII ступень, в мелодическом при движении вверх — VI и VII. Пентатоника — пять звуков без полутонов.",
        MusicStringsGradient,
        listOf(
            TheoryDemo.Sounds(
                listOf(
                    "▶ Мажор" to melody(scaleOf(60, listOf(0, 2, 4, 5, 7, 9, 11))),
                    "▶ Натуральный минор" to melody(scaleOf(57, listOf(0, 2, 3, 5, 7, 8, 10))),
                    "▶ Гармонический минор" to melody(scaleOf(57, listOf(0, 2, 3, 5, 7, 8, 11))),
                    "▶ Мелодический минор" to melody(scaleOf(57, listOf(0, 2, 3, 5, 7, 9, 11))),
                    "▶ Пентатоника" to melody(scaleOf(60, listOf(0, 2, 4, 7, 9))),
                    "▶ Блюзовая" to melody(scaleOf(57, listOf(0, 3, 5, 6, 7, 10))),
                ),
            ),
        ),
    ),
    TheorySection(
        "🗣", "Сольфеджио",
        "В русской традиции ноты называют слогами: До, Ре, Ми, Фа, Соль, Ля, Си. В буквенной системе это C, D, E, F, G, A, B. Пойте гамму вслух вместе с примером — так быстрее запоминаются и названия, и высота.",
        MusicNotesGradient,
        listOf(
            TheoryDemo.Staff(CMajor.map { StaffNote(it) }),
            TheoryDemo.Sounds(listOf("▶ До-ре-ми вверх и вниз" to melody(CMajor + CMajor.reversed().drop(1)))),
        ),
    ),
    TheorySection(
        "⭕", "Квинтовый круг",
        "По часовой стрелке каждая тональность на квинту выше и получает новый диез, против часовой — новый бемоль. Внутри — параллельные минорные тональности с теми же знаками. Нажмите на сектор, чтобы услышать аккорд.",
        listOf(Color(0xFF8B5CF6), Color(0xFF06B6D4)),
        listOf(TheoryDemo.Fifths),
    ),
    TheorySection(
        "👂", "Советы по развитию слуха",
        "Пойте интервалы и гаммы вместе с примерами, проверяйте себя во вкладке «Тренажёр». В «Определении» пойте в тишине устойчивым звуком — так микрофон точнее узнаёт ноту. Эталон для настройки — Ля первой октавы, 440 Гц.",
        MusicTunerGradient,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTheoryNotesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { musicianPrefs(context) }
    var tabIndex by remember { mutableIntStateOf(prefs.getInt("notes_tab", 0).coerceIn(0, 3)) }
    val sandboxNotes = remember { mutableStateListOf<StaffNote>().apply { addAll(loadSandbox(prefs)) } }
    LaunchedEffect(Unit) {
        snapshotFlow { sandboxNotes.toList() }.collect { saveSandbox(prefs, it) }
    }
    SideEffect { SineTonePlayer.bindInstrumentSampleContext(context) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { InstrumentSamplePlayer.ensureLoaded(context.applicationContext) }
    }
    DisposableEffect(Unit) {
        onDispose {
            SineTonePlayer.stopSustain()
            MusicSynth.stopReference()
        }
    }
    val tabs = listOf(
        Triple("📖", "Справочник", MusicNotesGradient),
        Triple("🎹", "Песочница", MusicMetronomeGradient),
        Triple("🎯", "Тренажёр", MusicStringsGradient),
        Triple("🎤", "Голосом", MusicTunerGradient),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ноты", fontWeight = FontWeight.ExtraBold) },
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tabs.forEachIndexed { i, (emoji, title, gradient) ->
                    val selected = tabIndex == i
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .then(
                                if (selected) Modifier.background(Brush.linearGradient(gradient))
                                else Modifier
                                    .background(gradient.first().copy(alpha = 0.08f))
                                    .border(1.dp, gradient.first().copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
                            )
                            .clickable {
                                tabIndex = i
                                prefs.edit().putInt("notes_tab", i).apply()
                            }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(emoji, fontSize = 19.sp)
                        Text(title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, color = if (selected) Color.White else gradient.first())
                    }
                }
            }
            when (tabIndex) {
                0 -> TheoryHandbookTab()
                1 -> NotesSandboxTab(sandboxNotes)
                2 -> NotesTrainerTab(prefs)
                else -> PitchListenerTab(onSendToSandbox = { captured ->
                    sandboxNotes.addAll(captured)
                    tabIndex = 1
                })
            }
        }
    }
}

private fun loadSandbox(prefs: SharedPreferences): List<StaffNote> =
    prefs.getString("notes_sandbox", "").orEmpty().split(',').mapNotNull { part ->
        val p = part.split(':')
        val m = p.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        StaffNote(m.coerceIn(36, 96), p.getOrNull(1)?.toFloatOrNull() ?: 1f)
    }

private fun saveSandbox(prefs: SharedPreferences, notes: List<StaffNote>) {
    prefs.edit().putString("notes_sandbox", notes.joinToString(",") { "${it.midi}:${it.beats}" }).apply()
}

@Composable
private fun PaperCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(PaperGradient))
            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(22.dp)),
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TheoryHandbookTab() {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(setOf(0)) }
    var fifthsSelected by remember { mutableStateOf<Int?>(null) }
    var fifthsInfo by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(theoryHandbook, key = { _, s -> s.title }) { index, section ->
            val open = index in expanded
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(1.dp, section.gradient.first().copy(alpha = 0.22f), RoundedCornerShape(24.dp)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = if (open) expanded - index else expanded + index }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Brush.linearGradient(section.gradient)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(section.emoji, fontSize = 20.sp, color = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(section.title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                }
                AnimatedVisibility(open) {
                    Column(
                        Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(section.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        section.demos.forEach { demo ->
                            when (demo) {
                                is TheoryDemo.Staff -> PaperCard {
                                    MusicStaff(
                                        notes = demo.notes,
                                        accent = section.gradient.first(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                            .padding(horizontal = 6.dp),
                                        onTapNote = { i -> playEventsAsync(scope, listOf(NoteEvent(demo.notes[i].midi, 0.0, 0.8))) },
                                    )
                                }
                                is TheoryDemo.Sounds -> FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    demo.items.forEach { (label, events) ->
                                        TmGradientPill(label, true, section.gradient) { playEventsAsync(scope, events) }
                                    }
                                }
                                TheoryDemo.Fifths -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    CircleOfFifths(
                                        modifier = Modifier
                                            .fillMaxWidth(0.92f)
                                            .aspectRatio(1f),
                                        selected = fifthsSelected,
                                        onTap = { i, minor ->
                                            fifthsSelected = i
                                            val root = fifthsMajorRootMidi(i)
                                            val (r, chord) = if (minor) (root - 3).let { if (it < 57) it + 12 else it } to listOf(0, 3, 7) else root to listOf(0, 4, 7)
                                            fifthsInfo = "${noteNameRu(r)} ${if (minor) "минор" else "мажор"}"
                                            playEventsAsync(scope, arpeggioThenChord(r, chord))
                                        },
                                    )
                                    if (fifthsInfo.isNotEmpty()) {
                                        Text("🎵 $fifthsInfo", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DurationChoices = listOf(4f to "Целая", 2f to "½", 1f to "¼", 0.5f to "⅛")

private val SandboxExamples = listOf(
    "⭐ Звёздочка" to listOf(60 to 1f, 60 to 1f, 67 to 1f, 67 to 1f, 69 to 1f, 69 to 1f, 67 to 2f, 65 to 1f, 65 to 1f, 64 to 1f, 64 to 1f, 62 to 1f, 62 to 1f, 60 to 2f),
    "🎶 Ода к радости" to listOf(64 to 1f, 64 to 1f, 65 to 1f, 67 to 1f, 67 to 1f, 65 to 1f, 64 to 1f, 62 to 1f, 60 to 1f, 60 to 1f, 62 to 1f, 64 to 1f, 64 to 1f, 62 to 0.5f, 62 to 2f),
    "🪜 Гамма" to CMajor.map { it to 1f },
)

@Composable
private fun NotesSandboxTab(notes: SnapshotStateList<StaffNote>) {
    val scope = rememberCoroutineScope()
    var timbre by remember { mutableStateOf(NoteTimbre.PIANO) }
    var beats by remember { mutableFloatStateOf(1f) }
    var bpm by remember { mutableIntStateOf(96) }
    var loop by remember { mutableStateOf(false) }
    var playingIndex by remember { mutableStateOf<Int?>(null) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var keyHighlight by remember { mutableStateOf<Int?>(null) }
    val staffScroll = rememberScrollState()

    LaunchedEffect(notes.size) { staffScroll.animateScrollTo(staffScroll.maxValue) }
    DisposableEffect(Unit) { onDispose { playJob?.cancel() } }

    fun stop() {
        playJob?.cancel()
        playJob = null
        playingIndex = null
        keyHighlight = null
    }

    fun play() {
        if (notes.isEmpty()) return
        playJob = scope.launch {
            try {
                do {
                    val seq = notes.toList()
                    for (i in seq.indices) {
                        val n = seq[i]
                        val durMs = (n.beats * 60_000f / bpm).toLong()
                        playingIndex = i
                        keyHighlight = n.midi
                        val started = System.currentTimeMillis()
                        withContext(Dispatchers.IO) {
                            SineTonePlayer.playMidiNoteBlocking(n.midi, (durMs * 0.92f).toInt().coerceIn(40, 4000), timbre = timbre)
                        }
                        val rest = durMs - (System.currentTimeMillis() - started)
                        if (rest > 0) delay(rest)
                    }
                } while (loop && isActive)
            } finally {
                playingIndex = null
                keyHighlight = null
                playJob = null
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(NoteTimbre.PIANO to "🎹 Пианино", NoteTimbre.VIOLIN to "🎻 Смычок", NoteTimbre.SINE to "〰️ Синус").forEach { (t, label) ->
                TmGradientPill(label, timbre == t, MusicMetronomeGradient) { timbre = t }
            }
            SandboxExamples.forEach { (label, seq) ->
                TmGradientPill(label, false, MusicNotesGradient) {
                    stop()
                    notes.clear()
                    notes.addAll(seq.map { (m, b) -> StaffNote(m, b) })
                    selected = null
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DurationChoices.forEach { (b, label) ->
                val isSel = beats == b
                Row(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .then(
                            if (isSel) Modifier.background(Brush.linearGradient(MusicTunerGradient))
                            else Modifier
                                .background(MusicTunerGradient.first().copy(alpha = 0.08f))
                                .border(1.dp, MusicTunerGradient.first().copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                        )
                        .clickable {
                            beats = b
                            selected?.let { i -> if (i in notes.indices) notes[i] = notes[i].copy(beats = b) }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MusicStaffDurationIcon(b, if (isSel) Color.White else MusicTunerGradient.first())
                    Spacer(Modifier.width(4.dp))
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (isSel) Color.White else MusicTunerGradient.first())
                }
            }
        }
        PaperCard(Modifier.height(170.dp)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val spacing = with(density) { 46.dp.toPx() }
                val minWidth = with(density) { maxWidth.toPx() }
                val half = with(density) { 170.dp.toPx() } / 22f
                val needed = half * 8.5f + spacing * (notes.size + 1)
                val widthDp = with(density) { maxOf(minWidth, needed).toDp() }
                Box(Modifier.fillMaxSize().horizontalScroll(staffScroll)) {
                    MusicStaff(
                        notes = notes,
                        highlight = playingIndex,
                        selected = selected,
                        noteSpacingPx = spacing,
                        accent = MusicMetronomeGradient.last(),
                        modifier = Modifier
                            .width(widthDp)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp),
                        onTapNote = { i ->
                            selected = if (selected == i) null else i
                            notes.getOrNull(i)?.let {
                                beats = it.beats
                                SineTonePlayer.playMidiNote(it.midi, 450, timbre = timbre)
                            }
                        },
                        onTapStaff = { midi ->
                            notes.add(StaffNote(midi, beats))
                            selected = null
                            SineTonePlayer.playMidiNote(midi, 450, timbre = timbre)
                        },
                    )
                }
                if (notes.isEmpty()) {
                    Text(
                        "Нажмите на стан или на клавиши ниже",
                        color = Color(0xFF7C3AED),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val playing = playJob?.isActive == true
            Row(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(if (playing) listOf(Color(0xFFEF4444), Color(0xFFF97316)) else MusicMetronomeGradient))
                    .clickable(enabled = notes.isNotEmpty()) { if (playing) stop() else play() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                Text(if (playing) " Стоп" else " Играть", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            SquareAction(Icons.Filled.Repeat, "Повтор", active = loop) { loop = !loop }
            SquareAction(Icons.AutoMirrored.Filled.Backspace, if (selected != null) "Удалить выбранную" else "Удалить последнюю", enabled = notes.isNotEmpty()) {
                stop()
                val i = selected
                if (i != null && i in notes.indices) notes.removeAt(i) else if (notes.isNotEmpty()) notes.removeAt(notes.lastIndex)
                selected = null
            }
            SquareAction(Icons.Filled.Delete, "Очистить", enabled = notes.isNotEmpty()) {
                stop()
                notes.clear()
                selected = null
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Темп ♩ = $bpm", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            SquareAction(Icons.Filled.Remove, "Медленнее") { bpm = (bpm - 6).coerceAtLeast(40) }
            SquareAction(Icons.Filled.Add, "Быстрее") { bpm = (bpm + 6).coerceAtMost(220) }
        }
        PianoKeyboard(
            lowMidi = 60,
            highMidi = 83,
            highlightMidi = keyHighlight,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 110.dp, max = 200.dp)
                .padding(bottom = 8.dp),
            onDown = { m ->
                stop()
                SineTonePlayer.startSustain(m, timbre = timbre)
            },
            onUp = { m ->
                SineTonePlayer.stopSustain()
                notes.add(StaffNote(m, beats))
                selected = null
            },
        )
    }
}

@Composable
private fun MusicStaffDurationIcon(beats: Float, color: Color) {
    Canvas(Modifier.size(width = 14.dp, height = 24.dp)) {
        val headW = size.width * 0.8f
        val headH = size.width * 0.6f
        val cy = size.height - headH
        if (beats >= 2f) {
            drawOval(color, Offset(0f, cy - headH / 2), androidx.compose.ui.geometry.Size(headW, headH), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
        } else {
            drawOval(color, Offset(0f, cy - headH / 2), androidx.compose.ui.geometry.Size(headW, headH))
        }
        if (beats < 4f) {
            val x = headW - 1.5f
            drawLine(color, Offset(x, cy), Offset(x, 1f), strokeWidth = 2.5f, cap = StrokeCap.Round)
            if (beats <= 0.5f) drawLine(color, Offset(x, 1f), Offset(size.width, size.height * 0.35f), strokeWidth = 2.5f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun SquareAction(icon: ImageVector, description: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (active) Modifier.background(Brush.linearGradient(MusicTunerGradient))
                else Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MusicMetronomeGradient.first().copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = when {
                active -> Color.White
                enabled -> MusicMetronomeGradient.first()
                else -> MaterialTheme.colorScheme.outline
            },
        )
    }
}

private enum class TrainerMode(val label: String) { READ("📖 Читаем ноты"), EAR("👂 Нота на слух"), INTERVAL("↔️ Интервалы") }

private val TrainerIntervals = listOf(
    2 to "Большая секунда",
    3 to "Малая терция",
    4 to "Большая терция",
    5 to "Кварта",
    7 to "Квинта",
    9 to "Большая секста",
    12 to "Октава",
)

private data class TrainerQuestion(val target: Int, val root: Int, val options: List<Int>, val correctOption: Int)

private fun newQuestion(mode: TrainerMode, previous: TrainerQuestion?, random: Random): TrainerQuestion = when (mode) {
    TrainerMode.READ -> {
        val naturals = (60..81).filter { !isBlackKey(it) }
        val midi = naturals.filter { it != previous?.target }.random(random)
        val letter = staffLetter(midi)
        val opts = ((0..6).filter { it != letter }.shuffled(random).take(3) + letter).shuffled(random)
        TrainerQuestion(midi, midi, opts, letter)
    }
    TrainerMode.EAR -> {
        val naturals = (60..71).filter { !isBlackKey(it) }
        val midi = naturals.filter { it != previous?.target }.random(random)
        val letter = staffLetter(midi)
        val opts = ((0..6).filter { it != letter }.shuffled(random).take(3) + letter).shuffled(random)
        TrainerQuestion(midi, midi, opts, letter)
    }
    TrainerMode.INTERVAL -> {
        val semis = TrainerIntervals.map { it.first }.filter { it != previous?.target }.random(random)
        val root = random.nextInt(55, 67)
        val opts = (TrainerIntervals.map { it.first }.filter { it != semis }.shuffled(random).take(3) + semis).sorted()
        TrainerQuestion(semis, root, opts, semis)
    }
}

private fun staffLetter(midi: Int): Int = listOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)[Math.floorMod(midi, 12)]

@Composable
private fun NotesTrainerTab(prefs: SharedPreferences) {
    val scope = rememberCoroutineScope()
    val random = remember { Random(System.currentTimeMillis()) }
    var mode by remember { mutableStateOf(TrainerMode.READ) }
    var question by remember(mode) { mutableStateOf(newQuestion(mode, null, random)) }
    var roundKey by remember { mutableIntStateOf(0) }
    var picked by remember(roundKey, mode) { mutableStateOf<Int?>(null) }
    var solved by remember(roundKey, mode) { mutableStateOf(false) }
    var correct by remember(mode) { mutableIntStateOf(0) }
    var total by remember(mode) { mutableIntStateOf(0) }
    var streak by remember(mode) { mutableIntStateOf(0) }
    var best by remember(mode) { mutableIntStateOf(prefs.getInt("notes_trainer_best_${mode.name}", 0)) }

    fun playQuestion(q: TrainerQuestion) {
        when (mode) {
            TrainerMode.READ -> Unit
            TrainerMode.EAR -> playEventsAsync(scope, listOf(NoteEvent(q.target, 0.0, 1.0)))
            TrainerMode.INTERVAL -> playEventsAsync(scope, intervalDemo(q.root, q.target))
        }
    }
    LaunchedEffect(question) {
        delay(350)
        playQuestion(question)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrainerMode.entries.forEach { m ->
                TmGradientPill(m.label, m == mode, MusicStringsGradient) { mode = m }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(MusicHeroGradient))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrainerStat("$correct / $total", "верно")
            TrainerStat("🔥 $streak", "подряд")
            TrainerStat("🏆 $best", "рекорд")
        }
        val prompt = when (mode) {
            TrainerMode.READ -> "Какая это нота?"
            TrainerMode.EAR -> "Какая нота прозвучала?"
            TrainerMode.INTERVAL -> "Какой интервал прозвучал?"
        }
        Text(prompt, fontSize = 22.sp, fontWeight = FontWeight.Black)
        when (mode) {
            TrainerMode.READ -> PaperCard {
                MusicStaff(
                    notes = listOf(StaffNote(question.target, 1f)),
                    showNames = solved,
                    highlight = if (solved) 0 else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .padding(horizontal = 6.dp),
                    onTapNote = if (solved) ({ playEventsAsync(scope, listOf(NoteEvent(question.target, 0.0, 0.8))) }) else null,
                )
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ListenButton("▶ Ещё раз", MusicStringsGradient) { playQuestion(question) }
                if (mode == TrainerMode.EAR) {
                    ListenButton("🎯 Эталон «До»", MusicTunerGradient) { playEventsAsync(scope, listOf(NoteEvent(60, 0.0, 0.9))) }
                }
            }
        }
        question.options.chunked(2).forEachIndexed { rowIdx, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { colIdx, opt ->
                    val label = if (mode == TrainerMode.INTERVAL) TrainerIntervals.first { it.first == opt }.second else NoteLettersRu[opt]
                    KidsAnswerTile(
                        text = label,
                        index = rowIdx * 2 + colIdx,
                        state = when {
                            solved && opt == question.correctOption -> KidsAnswerState.Correct
                            solved -> KidsAnswerState.Dimmed
                            picked == opt -> KidsAnswerState.Wrong
                            else -> KidsAnswerState.Idle
                        },
                        height = 76.dp,
                        fontSize = if (mode == TrainerMode.INTERVAL) 17 else 26,
                        appearKey = roundKey,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (solved) return@KidsAnswerTile
                            val firstTry = picked == null
                            picked = opt
                            if (opt == question.correctOption) {
                                solved = true
                                total++
                                if (firstTry) {
                                    correct++
                                    streak++
                                    if (streak > best) {
                                        best = streak
                                        prefs.edit().putInt("notes_trainer_best_${mode.name}", best).apply()
                                    }
                                }
                                if (mode == TrainerMode.READ) playEventsAsync(scope, listOf(NoteEvent(question.target, 0.0, 0.8)))
                                scope.launch {
                                    delay(1300)
                                    question = newQuestion(mode, question, random)
                                    roundKey++
                                }
                            } else {
                                streak = 0
                            }
                        },
                    )
                }
            }
        }
        Text(
            when (mode) {
                TrainerMode.READ -> "Ноты от До первой октавы до Ля второй. После ответа нажмите на ноту, чтобы услышать её."
                TrainerMode.EAR -> "Сравнивайте с эталоном «До»: так проще услышать, насколько выше звучит нота."
                TrainerMode.INTERVAL -> "Сначала звуки звучат по очереди, потом вместе."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RowScope.TrainerStat(value: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun RowScope.ListenButton(text: String, gradient: List<Color>, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(gradient))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

@Composable
private fun PitchListenerTab(onSendToSandbox: (List<StaffNote>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listening by remember { mutableStateOf(false) }
    var hz by remember { mutableFloatStateOf(0f) }
    var hasSignal by remember { mutableStateOf(false) }
    var autoRecord by remember { mutableStateOf(true) }
    var timbre by remember { mutableStateOf(NoteTimbre.PIANO) }
    val captured = remember { mutableStateListOf<StaffNote>() }
    var playingIndex by remember { mutableStateOf<Int?>(null) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    val staffScroll = rememberScrollState()

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) listening = true
    }

    var stableMidi by remember { mutableIntStateOf(-1) }
    var stableSince by remember { mutableStateOf(0L) }
    var armed by remember { mutableStateOf(true) }

    LaunchedEffect(listening, hasPermission) {
        if (!listening || !hasPermission) {
            hasSignal = false
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val sampleRate = 44100
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) return@withContext
            @Suppress("MissingPermission")
            val record = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 8192))
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
                    val est = if (rms > 300) PitchEstimator.estimateHz(buffer.copyOf(read), sampleRate) else null
                    val now = System.currentTimeMillis()
                    if (est != null) {
                        val prev = smoothed
                        smoothed = if (prev != null && abs(1200 * kotlin.math.ln(est / prev) / kotlin.math.ln(2f)) > 70) est else PitchEstimator.smooth(prev, est)
                        lastSignal = now
                    } else if (now - lastSignal > 500) {
                        smoothed = null
                    }
                    val shown = smoothed
                    withContext(Dispatchers.Main) {
                        if (shown != null) {
                            hz = shown
                            hasSignal = true
                            val midi = MusicTheoryUtils.midiFromHz(shown.toDouble())
                            if (midi != stableMidi) {
                                stableMidi = midi
                                stableSince = now
                            } else if (autoRecord && armed && now - stableSince > 320 && playJob?.isActive != true) {
                                captured.add(StaffNote(midi.coerceIn(55, 88), 1f))
                                armed = false
                            }
                        } else {
                            hasSignal = false
                            stableMidi = -1
                            armed = true
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
    LaunchedEffect(stableMidi) { if (stableMidi >= 0) armed = armed || captured.lastOrNull()?.midi != stableMidi }
    LaunchedEffect(captured.size) { staffScroll.animateScrollTo(staffScroll.maxValue) }
    DisposableEffect(Unit) { onDispose { playJob?.cancel() } }

    val midi = if (hasSignal) MusicTheoryUtils.midiFromHz(hz.toDouble()) else null
    val cents = midi?.let { MusicTheoryUtils.centsFromEqualTemperament(hz.toDouble(), it) } ?: 0.0
    val centsAnim by animateFloatAsState(cents.toFloat().coerceIn(-50f, 50f), spring(dampingRatio = 0.7f), label = "cents")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(MusicHeroGradient))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (midi != null) {
                Text(noteNameRu(midi), color = Color.White, fontSize = 60.sp, lineHeight = 62.sp, fontWeight = FontWeight.Black)
                Text("${MusicTheoryUtils.englishName(midi)} · ${"%.1f".format(hz)} Гц", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
            } else {
                Text("—", color = Color.White.copy(alpha = 0.5f), fontSize = 60.sp, lineHeight = 62.sp, fontWeight = FontWeight.Black)
                Text(
                    when {
                        !hasPermission -> "Нужен доступ к микрофону"
                        listening -> "Спойте или сыграйте ноту"
                        else -> "Нажмите на микрофон"
                    },
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(18.dp),
            ) {
                val y = size.height / 2
                drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), strokeWidth = 8f, cap = StrokeCap.Round)
                drawLine(Color(0xFF22C55E), Offset(size.width * 0.45f, y), Offset(size.width * 0.55f, y), strokeWidth = 8f, cap = StrokeCap.Round)
                if (midi != null) {
                    val x = size.width * (0.5f + centsAnim / 100f)
                    drawCircle(if (abs(cents) < 10) Color(0xFF22C55E) else Color(0xFFF59E0B), size.height / 2, Offset(x, y))
                }
            }
            Text(if (midi != null) "${if (cents >= 0) "+" else ""}${cents.toInt()} ¢" else " ", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Icon(if (listening) Icons.Filled.Mic else Icons.Filled.MicOff, contentDescription = if (listening) "Остановить" else "Слушать", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(if (listening) "Слушаю…" else "Микрофон выключен", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(
                    if (autoRecord) "Каждая спетая нота сама попадёт на стан" else "Нажмите «Записать», чтобы добавить ноту",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TmGradientPill(if (autoRecord) "✨ Автозапись: вкл" else "✨ Автозапись: выкл", autoRecord, MusicNotesGradient) { autoRecord = !autoRecord }
            TmGradientPill("📌 Записать", false, MusicStringsGradient) {
                midi?.let { captured.add(StaffNote(it.coerceIn(55, 88), 1f)) }
            }
        }
        Text("Записанная мелодия (${captured.size})", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        PaperCard(Modifier.height(160.dp)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val spacing = with(density) { 44.dp.toPx() }
                val half = with(density) { 160.dp.toPx() } / 22f
                val widthDp = with(density) { maxOf(maxWidth.toPx(), half * 8.5f + spacing * (captured.size + 1)).toDp() }
                Box(Modifier.fillMaxSize().horizontalScroll(staffScroll)) {
                    MusicStaff(
                        notes = captured,
                        highlight = playingIndex,
                        noteSpacingPx = spacing,
                        accent = MusicTunerGradient.last(),
                        modifier = Modifier
                            .width(widthDp)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp),
                        onTapNote = { i -> captured.getOrNull(i)?.let { SineTonePlayer.playMidiNote(it.midi, 450, timbre = timbre) } },
                    )
                }
                if (captured.isEmpty()) {
                    Text(
                        "Спойте мелодию — ноты появятся здесь",
                        color = MusicTunerGradient.last(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                    )
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(NoteTimbre.PIANO to "🎹 Пианино", NoteTimbre.VIOLIN to "🎻 Смычок", NoteTimbre.SINE to "〰️ Синус").forEach { (t, label) ->
                TmGradientPill(label, timbre == t, MusicTunerGradient) { timbre = t }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val playing = playJob?.isActive == true
            Row(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(if (playing) listOf(Color(0xFFEF4444), Color(0xFFF97316)) else MusicTunerGradient))
                    .clickable(enabled = captured.isNotEmpty()) {
                        if (playing) {
                            playJob?.cancel()
                        } else {
                            playJob = scope.launch {
                                try {
                                    captured.toList().forEachIndexed { i, n ->
                                        playingIndex = i
                                        withContext(Dispatchers.IO) { SineTonePlayer.playMidiNoteBlocking(n.midi, 420, timbre = timbre) }
                                        delay(70)
                                    }
                                } finally {
                                    playingIndex = null
                                    playJob = null
                                }
                            }
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                Text(if (playing) " Стоп" else " Играть", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            SquareAction(Icons.AutoMirrored.Filled.ArrowForward, "В песочницу", enabled = captured.isNotEmpty()) {
                onSendToSandbox(captured.toList())
            }
            SquareAction(Icons.AutoMirrored.Filled.Backspace, "Удалить последнюю", enabled = captured.isNotEmpty()) {
                if (captured.isNotEmpty()) captured.removeAt(captured.lastIndex)
            }
            SquareAction(Icons.Filled.Delete, "Очистить", enabled = captured.isNotEmpty()) {
                playJob?.cancel()
                captured.clear()
            }
        }
        Text(
            "Кнопка со стрелкой переносит мелодию в «Песочницу» — там её можно отредактировать и сыграть разными тембрами.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}
