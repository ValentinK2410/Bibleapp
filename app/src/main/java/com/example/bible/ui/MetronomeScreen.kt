package com.example.bible.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.audio.MusicSynth
import com.example.bible.audio.MusicSynth.ClickLevel
import com.example.bible.audio.MusicSynth.ClickSound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BPM_MIN = 30
private const val BPM_MAX = 250

private enum class BeatAccent { ACCENT, NORMAL, MUTE }

private data class TimeSignature(val label: String, val beats: Int, val accents: List<Int>)

private val Signatures = listOf(
    TimeSignature("2/4", 2, listOf(0)),
    TimeSignature("3/4", 3, listOf(0)),
    TimeSignature("4/4", 4, listOf(0)),
    TimeSignature("5/4", 5, listOf(0, 3)),
    TimeSignature("6/8", 6, listOf(0, 3)),
    TimeSignature("7/8", 7, listOf(0, 2, 4)),
    TimeSignature("9/8", 9, listOf(0, 3, 6)),
    TimeSignature("12/8", 12, listOf(0, 3, 6, 9)),
)

private val Subdivisions = listOf(1 to "♩", 2 to "♫", 3 to "3", 4 to "♬")

private fun tempoName(bpm: Int): String = when {
    bpm < 45 -> "Grave"
    bpm < 60 -> "Largo"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 168 -> "Allegro"
    bpm < 200 -> "Presto"
    else -> "Prestissimo"
}

private data class MetronomeConfig(
    val bpm: Int,
    val accents: List<BeatAccent>,
    val subdivision: Int,
    val sound: ClickSound,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { musicianPrefs(context) }
    var bpm by remember { mutableIntStateOf(prefs.getInt("metronome_bpm", 100).coerceIn(BPM_MIN, BPM_MAX)) }
    var signatureIndex by remember { mutableIntStateOf(prefs.getInt("metronome_sig", 2).coerceIn(0, Signatures.lastIndex)) }
    var subdivision by remember { mutableIntStateOf(prefs.getInt("metronome_sub", 1)) }
    var sound by remember {
        mutableStateOf(ClickSound.entries.getOrNull(prefs.getInt("metronome_sound", 0)) ?: ClickSound.CLASSIC)
    }
    val accents = remember { mutableStateListOf<BeatAccent>() }
    fun resetAccents(sig: TimeSignature) {
        accents.clear()
        repeat(sig.beats) { i -> accents.add(if (i in sig.accents) BeatAccent.ACCENT else BeatAccent.NORMAL) }
    }
    LaunchedEffect(signatureIndex) { resetAccents(Signatures[signatureIndex]) }

    var running by remember { mutableStateOf(false) }
    var beatInBar by remember { mutableIntStateOf(-1) }
    var beatCount by remember { mutableIntStateOf(0) }
    val taps = remember { mutableListOf<Long>() }

    fun updateBpm(v: Int) {
        bpm = v.coerceIn(BPM_MIN, BPM_MAX)
        prefs.edit().putInt("metronome_bpm", bpm).apply()
    }

    val config = MetronomeConfig(bpm, accents.toList(), subdivision, sound)
    val configState = rememberUpdatedState(config)
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(running) {
        if (!running) {
            beatInBar = -1
            return@LaunchedEffect
        }
        val uiScope = this
        withContext(Dispatchers.IO) {
            val track = MusicSynth.newStreamTrack()
            val clicks = HashMap<Pair<ClickSound, ClickLevel>, ShortArray>()
            fun clickOf(s: ClickSound, l: ClickLevel) = clicks.getOrPut(s to l) { MusicSynth.click(s, l) }
            track.play()
            var beat = 0
            try {
                while (isActive) {
                    val cfg = configState.value
                    val beats = cfg.accents.size.coerceAtLeast(1)
                    val inBar = beat % beats
                    val samplesPerBeat = (MusicSynth.SAMPLE_RATE * 60.0 / cfg.bpm).toInt()
                    val sub = cfg.subdivision.coerceIn(1, 4)
                    val subLen = samplesPerBeat / sub
                    uiScope.launch {
                        beatInBar = inBar
                        beatCount++
                    }
                    for (s in 0 until sub) {
                        val len = if (s == sub - 1) samplesPerBeat - subLen * (sub - 1) else subLen
                        val chunk = ShortArray(len)
                        val click = when {
                            s > 0 -> clickOf(cfg.sound, ClickLevel.SUB)
                            cfg.accents.getOrNull(inBar) == BeatAccent.ACCENT -> clickOf(cfg.sound, ClickLevel.ACCENT)
                            cfg.accents.getOrNull(inBar) == BeatAccent.NORMAL -> clickOf(cfg.sound, ClickLevel.NORMAL)
                            else -> null
                        }
                        click?.copyInto(chunk, 0, 0, minOf(click.size, len))
                        var off = 0
                        while (off < len && isActive) {
                            val w = track.write(chunk, off, len - off)
                            if (w <= 0) break
                            off += w
                        }
                    }
                    beat++
                }
            } finally {
                try {
                    track.pause()
                    track.flush()
                    track.stop()
                } catch (_: Exception) {
                }
                track.release()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Метроном", fontWeight = FontWeight.ExtraBold) },
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
            MetronomeHero(
                bpm = bpm,
                running = running,
                beatCount = beatCount,
                accents = accents,
                beatInBar = beatInBar,
                onCycleAccent = { i ->
                    accents[i] = when (accents[i]) {
                        BeatAccent.ACCENT -> BeatAccent.NORMAL
                        BeatAccent.NORMAL -> BeatAccent.MUTE
                        BeatAccent.MUTE -> BeatAccent.ACCENT
                    }
                },
            )
            Slider(
                value = bpm.toFloat(),
                onValueChange = { updateBpm(it.roundToIntSafe()) },
                valueRange = BPM_MIN.toFloat()..BPM_MAX.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MusicMetronomeGradient.last(),
                    activeTrackColor = MusicMetronomeGradient.first(),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-5, -1, 1, 5).forEach { d ->
                    TempoStepButton(d, Modifier.weight(1f)) { updateBpm(bpm + d) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(if (running) listOf(Color(0xFFEF4444), Color(0xFFF97316)) else MusicMetronomeGradient))
                        .clickable { running = !running },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (running) "Стоп" else "Старт", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(
                    Modifier
                        .width(96.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MusicMetronomeGradient.first().copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                        .clickable {
                            val now = System.currentTimeMillis()
                            if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
                            taps.add(now)
                            if (taps.size > 5) taps.removeAt(0)
                            if (taps.size >= 2) {
                                val avg = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
                                updateBpm((60_000.0 / avg).roundToIntSafe())
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.TouchApp, contentDescription = null, tint = MusicMetronomeGradient.first())
                    Text("Tap", fontWeight = FontWeight.ExtraBold, color = MusicMetronomeGradient.first())
                }
            }
            TmSectionCard(icon = Icons.Filled.ViewWeek, title = "Размер", gradient = MusicMetronomeGradient) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Signatures.forEachIndexed { i, sig ->
                        TmGradientPill(sig.label, i == signatureIndex, MusicMetronomeGradient) {
                            signatureIndex = i
                            prefs.edit().putInt("metronome_sig", i).apply()
                        }
                    }
                }
                Text(
                    "Нажмите на долю, чтобы сменить её: акцент → обычная → тишина.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TmSectionCard(icon = Icons.Filled.GraphicEq, title = "Дробление доли", gradient = MusicTunerGradient) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Subdivisions.forEach { (n, label) ->
                        Box(Modifier.weight(1f)) {
                            SubdivisionPill(label = label, selected = subdivision == n) {
                                subdivision = n
                                prefs.edit().putInt("metronome_sub", n).apply()
                            }
                        }
                    }
                }
            }
            TmSectionCard(icon = Icons.AutoMirrored.Filled.VolumeUp, title = "Звук", gradient = MusicNotesGradient) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClickSound.entries.forEach { s ->
                        TmGradientPill(s.label, s == sound, MusicNotesGradient) {
                            sound = s
                            prefs.edit().putInt("metronome_sound", s.ordinal).apply()
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun Float.roundToIntSafe(): Int = kotlin.math.round(this).toInt()
private fun Double.roundToIntSafe(): Int = kotlin.math.round(this).toInt()

@Composable
private fun MetronomeHero(
    bpm: Int,
    running: Boolean,
    beatCount: Int,
    accents: List<BeatAccent>,
    beatInBar: Int,
    onCycleAccent: (Int) -> Unit,
) {
    val swing = remember { Animatable(0f) }
    LaunchedEffect(beatCount, running) {
        if (!running) {
            swing.animateTo(0f, tween(300))
            return@LaunchedEffect
        }
        val target = if (beatCount % 2 == 0) -28f else 28f
        swing.animateTo(target, tween((60_000 / bpm).coerceAtLeast(120), easing = FastOutSlowInEasing))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(MusicHeroGradient))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                Modifier
                    .width(110.dp)
                    .height(140.dp),
            ) {
                val cx = size.width / 2f
                val bottom = size.height
                val body = Path().apply {
                    moveTo(cx - size.width * 0.18f, 0f)
                    lineTo(cx + size.width * 0.18f, 0f)
                    lineTo(cx + size.width * 0.46f, bottom)
                    lineTo(cx - size.width * 0.46f, bottom)
                    close()
                }
                drawPath(body, Brush.verticalGradient(listOf(Color(0xFF7C3AED), Color(0xFF4C1D95))))
                drawPath(body, Color.White.copy(alpha = 0.25f), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                val pivot = Offset(cx, bottom * 0.82f)
                rotate(swing.value, pivot) {
                    val tip = Offset(cx, bottom * 0.06f)
                    drawLine(Color.White, pivot, tip, strokeWidth = 6f, cap = StrokeCap.Round)
                    val weightY = pivot.y - (pivot.y - tip.y) * (0.25f + 0.55f * (1f - (bpm - BPM_MIN).toFloat() / (BPM_MAX - BPM_MIN)))
                    drawRoundRect(
                        Color(0xFFEC4899),
                        topLeft = Offset(cx - 14f, weightY - 9f),
                        size = androidx.compose.ui.geometry.Size(28f, 18f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f),
                    )
                }
                drawCircle(Color.White, 7f, pivot)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tempoName(bpm).uppercase(), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$bpm", color = Color.White, fontSize = 72.sp, lineHeight = 72.sp, fontWeight = FontWeight.Black)
                    Text(" уд/мин", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                }
                Text(
                    if (running) "Идёт отсчёт" else "Нажмите «Старт»",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            accents.forEachIndexed { i, a ->
                val current = running && i == beatInBar
                val scale by animateFloatAsState(if (current) 1.12f else 1f, spring(dampingRatio = 0.4f, stiffness = 900f), label = "beat")
                val fill = when {
                    current && a == BeatAccent.ACCENT -> Brush.linearGradient(MusicMetronomeGradient)
                    current -> Brush.linearGradient(MusicTunerGradient)
                    a == BeatAccent.ACCENT -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.25f)))
                    a == BeatAccent.NORMAL -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.1f)))
                    else -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(if (accents.size > 7) 52.dp else 64.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(fill)
                        .border(1.5.dp, Color.White.copy(alpha = if (a == BeatAccent.MUTE) 0.35f else 0.2f), RoundedCornerShape(14.dp))
                        .clickable { onCycleAccent(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${i + 1}",
                            color = Color.White.copy(alpha = if (a == BeatAccent.MUTE) 0.4f else 1f),
                            fontSize = if (accents.size > 7) 14.sp else 20.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            when (a) {
                                BeatAccent.ACCENT -> ">"
                                BeatAccent.NORMAL -> "•"
                                BeatAccent.MUTE -> "–"
                            },
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TempoStepButton(delta: Int, modifier: Modifier, onClick: () -> Unit) {
    val big = kotlin.math.abs(delta) == 1
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (big) Modifier.background(Brush.linearGradient(MusicMetronomeGradient))
                else Modifier
                    .background(MusicMetronomeGradient.first().copy(alpha = 0.08f))
                    .border(1.dp, MusicMetronomeGradient.first().copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            )
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (big) Color.White else MusicMetronomeGradient.first()
        Icon(if (delta < 0) Icons.Filled.Remove else Icons.Filled.Add, contentDescription = if (delta < 0) "Медленнее на ${-delta}" else "Быстрее на $delta", tint = tint, modifier = Modifier.size(20.dp))
        Text("${kotlin.math.abs(delta)}", color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

@Composable
private fun SubdivisionPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (selected) Modifier.background(Brush.linearGradient(MusicTunerGradient))
                else Modifier
                    .background(MusicTunerGradient.first().copy(alpha = 0.08f))
                    .border(1.dp, MusicTunerGradient.first().copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Black, color = if (selected) Color.White else MusicTunerGradient.first())
    }
}
