package com.example.bible.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.audio.MusicSynth
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.BrainRingBank
import com.example.bible.data.KidsGames
import com.example.bible.games.KidsGameAudio
import com.example.bible.games.KidsMusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

private const val RING_COUNT = 8
private const val RING_WIN_AT = 5
private const val RING_SECONDS_MIN = 3
private const val RING_SECONDS_MAX = 60
private val RING_SECOND_PRESETS = listOf(5, 10, 15, 20, 30)

private enum class RingPhase { Ready, Play, Done }

private data class RingQuestion(
    val id: String,
    val emoji: String,
    val prompt: String,
    val correct: String,
    val options: List<String>,
)

private val ringMelodyMutex = Mutex()
private val ringMelodyToken = AtomicInteger(0)

private suspend fun playRingCountdown(token: Int, seconds: Int) {
    ringMelodyMutex.withLock {
        if (token != ringMelodyToken.get()) return
        if (!KidsGameAudio.musicOn.value) return
        MusicSynth.playEvents(ringCountdownMelody(seconds))
        if (token != ringMelodyToken.get()) runCatching { MusicSynth.stopReference() }
    }
}

private fun cutRingCountdown() {
    ringMelodyToken.incrementAndGet()
    runCatching { MusicSynth.stopReference() }
}

/** Мелодия на всё время вопроса: каждый удар выше, в последнюю секунду торопливые ноты. */
private fun ringCountdownMelody(seconds: Int): List<MusicSynth.NoteEvent> {
    val total = seconds.coerceIn(RING_SECONDS_MIN, RING_SECONDS_MAX)
    val notes = ArrayList<MusicSynth.NoteEvent>(total * 2 + 4)
    for (beat in 0 until total) {
        val midi = (60 + beat * 28 / total).coerceAtMost(91)
        val velocity = 0.45 + 0.45 * beat / total
        val dur = if (total - beat <= 2) 0.12 else 0.18
        notes += MusicSynth.NoteEvent(midi, beat.toDouble(), dur, velocity)
        notes += MusicSynth.NoteEvent((midi - 12).coerceAtLeast(48), beat.toDouble(), dur + 0.04, velocity * 0.4)
    }
    val last = (total - 1).toDouble()
    notes += MusicSynth.NoteEvent(88, last + 0.28, 0.1, 0.9)
    notes += MusicSynth.NoteEvent(91, last + 0.5, 0.1, 1.0)
    notes += MusicSynth.NoteEvent(96, last + 0.72, 0.16, 1.0)
    return notes
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsBrainRingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val repo = remember { AzbukaProgressRepository(context) }
    val scope = rememberCoroutineScope()
    val storedAnswered by repo.brainRingAnswered.collectAsStateWithLifecycle(initialValue = emptySet())
    val storedOnlyNew by repo.brainRingOnlyNew.collectAsStateWithLifecycle(initialValue = true)
    var onlyNewOverride by remember { mutableStateOf<Boolean?>(null) }
    val onlyNew = onlyNewOverride ?: storedOnlyNew
    var extraAnswered by remember { mutableStateOf(emptySet<String>()) }
    val answered = remember(storedAnswered, extraAnswered) { storedAnswered + extraAnswered }
    val questionIds = remember { BrainRingBank.all.map { it.id }.toSet() }
    val fresh = (BrainRingBank.SIZE - answered.count { it in questionIds }).coerceAtLeast(0)
    var match by remember { mutableIntStateOf(0) }
    var roundSeconds by remember { mutableIntStateOf(5) }
    var questions by remember { mutableStateOf(emptyList<RingQuestion>()) }
    var index by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(RingPhase.Ready) }
    var correctCount by remember { mutableIntStateOf(0) }
    var marks by remember { mutableStateOf(emptyList<Boolean?>()) }
    var remaining by remember { mutableFloatStateOf(roundSeconds.toFloat()) }
    var clockQuestion by remember { mutableIntStateOf(-1) }
    var picked by remember { mutableStateOf<String?>(null) }
    var pickedIndex by remember { mutableIntStateOf(-1) }

    KidsGameAudio.init(context)
    KidsGameMusic(KidsMusicTrack.GAMES)
    DisposableEffect(Unit) {
        onDispose { cutRingCountdown() }
    }

    val showResult = phase == RingPhase.Play && pickedIndex == index
    val liveClock = phase == RingPhase.Play && clockQuestion == index
    val shownRemaining = if (liveClock) remaining else roundSeconds.toFloat()
    val urgent = phase == RingPhase.Play && !showResult && liveClock &&
        shownRemaining <= (roundSeconds * 0.34f).coerceIn(0.8f, 2f)
    val backdrop by animateColorAsState(
        targetValue = if (urgent) Color(0xFF7F1D1D) else Color(0xFF1E1B4B),
        animationSpec = tween(240),
        label = "ringBackdrop",
    )
    val winAt = if (questions.isEmpty()) {
        RING_WIN_AT
    } else {
        ((questions.size * RING_WIN_AT + RING_COUNT - 1) / RING_COUNT).coerceAtLeast(1)
    }
    val won = phase == RingPhase.Done && correctCount >= winAt
    KidsGameWinReward(game = KidsGames.BRAIN_RING, won = won, points = 20, round = match)

    fun mark(qIndex: Int, ok: Boolean) {
        if (qIndex !in marks.indices || marks[qIndex] != null) return
        marks = marks.toMutableList().also { it[qIndex] = ok }
        if (ok) correctCount += 1
        val id = questions.getOrNull(qIndex)?.id ?: return
        extraAnswered = extraAnswered + id
        scope.launch { repo.markBrainRingAnswered(id) }
    }

    LaunchedEffect(match, index, phase) {
        if (phase != RingPhase.Play) return@LaunchedEffect
        val qIndex = index
        val question = questions.getOrNull(qIndex) ?: return@LaunchedEffect
        clockQuestion = qIndex
        val limit = roundSeconds.toFloat()
        remaining = limit
        val token = ringMelodyToken.incrementAndGet()
        val melody = launch(Dispatchers.Default) { playRingCountdown(token, roundSeconds) }
        val start = SystemClock.elapsedRealtime()
        var timedOut = false
        while (pickedIndex != qIndex) {
            val left = limit - (SystemClock.elapsedRealtime() - start) / 1000f
            if (left <= 0f) {
                remaining = 0f
                timedOut = true
                break
            }
            remaining = left
            kotlinx.coroutines.delay(32)
        }
        cutRingCountdown()
        melody.join()
        if (timedOut && pickedIndex != qIndex) {
            picked = null
            pickedIndex = qIndex
            mark(qIndex, false)
            KidsGameStreak.answered(false)
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
        val ok = !timedOut && picked == question.correct
        kotlinx.coroutines.delay(if (ok) 900 else 1350)
        if (qIndex + 1 >= questions.size) phase = RingPhase.Done else index = qIndex + 1
    }

    val second = ceil(shownRemaining.toDouble()).toInt().coerceIn(0, roundSeconds)
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(second, index, phase, showResult) {
        if (phase != RingPhase.Play || showResult) return@LaunchedEffect
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        pulse.snapTo(if (second <= 2) 1.22f else 1.12f)
        pulse.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 460f))
    }

    Scaffold(
        containerColor = backdrop,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Брейн-ринг", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = { KidsAudioToggles() },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(backdrop, Color(0xFF312E81), Color(0xFF0F172A)),
                    ),
                ),
        ) {
            when (phase) {
                RingPhase.Ready -> RingReady(
                    seconds = roundSeconds,
                    onSeconds = { roundSeconds = it.coerceIn(RING_SECONDS_MIN, RING_SECONDS_MAX) },
                    onlyNew = onlyNew,
                    fresh = fresh,
                    seen = (BrainRingBank.SIZE - fresh).coerceAtLeast(0),
                    onOnlyNew = { value ->
                        onlyNewOverride = value
                        scope.launch { repo.setBrainRingOnlyNew(value) }
                    },
                    onStart = {
                        val dealt = dealRingQuestions(answered, onlyNew)
                        if (dealt.isNotEmpty()) {
                            questions = dealt
                            marks = List(dealt.size) { null }
                            index = 0
                            correctCount = 0
                            picked = null
                            pickedIndex = -1
                            KidsGameStreak.reset()
                            phase = RingPhase.Play
                        }
                    },
                )
                RingPhase.Play -> {
                    val question = questions[index]
                    RingPlay(
                        question = question,
                        index = index,
                        marks = marks,
                        correctCount = correctCount,
                        totalSeconds = roundSeconds.toFloat(),
                        shownRemaining = shownRemaining,
                        second = second,
                        pulse = pulse.value,
                        showResult = showResult,
                        picked = picked,
                        onChoose = { answer ->
                            if (pickedIndex != index && phase == RingPhase.Play) {
                                picked = answer
                                pickedIndex = index
                                val ok = answer == question.correct
                                mark(index, ok)
                                KidsGameStreak.answered(ok)
                                view.performHapticFeedback(
                                    if (ok) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT,
                                )
                                cutRingCountdown()
                            }
                        },
                    )
                }
                RingPhase.Done -> RingFinish(
                    correctCount = correctCount,
                    total = questions.size,
                    winAt = winAt,
                    won = won,
                    onAgain = {
                        match += 1
                        phase = RingPhase.Ready
                    },
                )
            }
            KidsCorrectBurst()
            if (won) KidsConfetti(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun RingReady(
    seconds: Int,
    onSeconds: (Int) -> Unit,
    onlyNew: Boolean,
    fresh: Int,
    seen: Int,
    onOnlyNew: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("⏱️", fontSize = 72.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                !onlyNew -> "Восемь вопросов из Библии"
                fresh >= RING_COUNT -> "Восемь новых вопросов"
                fresh > 0 -> "Новых осталось: $fresh"
                else -> "Новые вопросы кончились"
            },
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Слушай мелодию и жми, пока кольцо не погасло.",
            color = Color.White.copy(alpha = 0.84f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Время на ответ",
            color = Color.White.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RingTimeButton("−", seconds > RING_SECONDS_MIN) { onSeconds(seconds - 1) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$seconds", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Black)
                Text(secondsLabel(seconds), color = Color(0xFFFDE68A), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            RingTimeButton("+", seconds < RING_SECONDS_MAX) { onSeconds(seconds + 1) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RING_SECOND_PRESETS.forEach { preset ->
                val selected = preset == seconds
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.16f))
                        .clickable { onSeconds(preset) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        "$preset",
                        color = if (selected) Color(0xFF1E1B4B) else Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Какие вопросы",
            color = Color.White.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RingModeChip("Только новые", selected = onlyNew, modifier = Modifier.weight(1f)) {
                onOnlyNew(true)
            }
            RingModeChip("И отвеченные", selected = !onlyNew, modifier = Modifier.weight(1f)) {
                onOnlyNew(false)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Уже были: $seen из ${BrainRingBank.SIZE}",
            color = Color(0xFFFDE68A),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
        Text(
            when {
                !onlyNew -> "В раунде могут попасться и уже отвеченные"
                fresh == 0 -> "Включи «И отвеченные», чтобы повторять"
                fresh < RING_COUNT -> "Раунд будет короче: новых меньше восьми"
                else -> "В раунде будут только новые вопросы"
            },
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("А", "Б", "В").forEachIndexed { i, letter ->
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(kidsGradient(i))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        val canStart = !onlyNew || fresh > 0
        KidsBigButton(
            text = if (canStart) "На старт!" else "Включи повторы",
            onClick = onStart,
            enabled = canStart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RingModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF1E1B4B) else Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RingTimeButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
    }
}

private fun secondsLabel(seconds: Int): String {
    val mod10 = seconds % 10
    val mod100 = seconds % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> "секунда"
        mod10 in 2..4 && mod100 !in 12..14 -> "секунды"
        else -> "секунд"
    }
    return word
}

@Composable
private fun RingPlay(
    question: RingQuestion,
    index: Int,
    marks: List<Boolean?>,
    correctCount: Int,
    totalSeconds: Float,
    shownRemaining: Float,
    second: Int,
    pulse: Float,
    showResult: Boolean,
    picked: String?,
    onChoose: (String) -> Unit,
) {
    val cardPop = remember(index) { Animatable(0.9f) }
    LaunchedEffect(index) {
        cardPop.animateTo(1f, spring(dampingRatio = 0.52f, stiffness = 380f))
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Вопрос ${index + 1} из ${marks.size}",
                color = Color.White.copy(alpha = 0.86f),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Text(
                "Верно $correctCount",
                color = Color(0xFFFDE68A),
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            marks.forEachIndexed { i, mark ->
                val color = when {
                    i == index -> Color.White
                    mark == true -> Color(0xFF4ADE80)
                    mark == false -> Color(0xFFF87171)
                    else -> Color.White.copy(alpha = 0.28f)
                }
                Box(
                    Modifier
                        .size(if (i == index) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        CountdownRing(remaining = shownRemaining, total = totalSeconds, second = second, pulse = pulse)
        Column(Modifier.graphicsLayer { scaleX = cardPop.value; scaleY = cardPop.value }) {
            KidsQuestionCard(
                emoji = question.emoji,
                label = "Слушай и успей",
                prompt = question.prompt,
                promptSize = 24,
            )
        }
        Spacer(Modifier.height(12.dp))
        question.options.forEachIndexed { optionIndex, answer ->
            val state = when {
                !showResult -> KidsAnswerState.Idle
                answer == question.correct -> KidsAnswerState.Correct
                answer == picked -> KidsAnswerState.Wrong
                else -> KidsAnswerState.Dimmed
            }
            KidsAnswerTile(
                text = answer,
                index = optionIndex,
                state = state,
                onClick = { onChoose(answer) },
                enabled = !showResult,
                height = 74.dp,
                fontSize = 20,
                subtitle = listOf("А", "Б", "В").getOrElse(optionIndex) { "" },
                appearKey = "$index-$answer",
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (showResult) {
            val ok = picked == question.correct
            KidsAnswerBanner(
                correct = ok,
                text = when {
                    ok -> "Верно!"
                    picked == null -> "Время! Правильно: ${question.correct}"
                    else -> "Правильно: ${question.correct}"
                },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CountdownRing(remaining: Float, total: Float, second: Int, pulse: Float) {
    val fraction = (remaining / total.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val color = when {
        fraction > 0.55f -> Color(0xFF4ADE80)
        fraction > 0.28f -> Color(0xFFFBBF24)
        else -> Color(0xFFF87171)
    }
    val numberSize = if (second >= 10) 44 else 52
    Box(
        Modifier
            .size(132.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = 11.dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (fraction > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            "$second",
            color = Color.White,
            fontSize = numberSize.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun RingFinish(correctCount: Int, total: Int, winAt: Int, won: Boolean, onAgain: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (won) "🏆" else "💪", fontSize = 84.sp)
        Text(
            if (won) "Раунд взят!" else "Ещё чуть-чуть!",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Верных ответов: $correctCount из $total",
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            if (won) "+2 звезды ⭐" else "Нужно $winAt верных, чтобы забрать звёзды",
            color = Color(0xFFFDE68A),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(24.dp))
        KidsBigButton(text = "Ещё раунд", onClick = onAgain, modifier = Modifier.fillMaxWidth())
    }
}

/** Картинка плитки на экране «Игры»: кольцо таймера и три кнопки ответа. */
@Composable
internal fun BrainRingArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ring = size.minDimension * 0.28f
        val c = Offset(size.width * 0.5f, size.height * 0.38f)
        drawCircle(Color.White.copy(alpha = 0.22f), ring, c, style = Stroke(ring * 0.16f, cap = StrokeCap.Round))
        drawArc(
            color = Color(0xFFFDE68A),
            startAngle = -90f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = c - Offset(ring, ring),
            size = Size(ring * 2f, ring * 2f),
            style = Stroke(width = ring * 0.16f, cap = StrokeCap.Round),
        )
        drawCircle(Color.White, ring * 0.1f, c)
        val barW = size.width * 0.78f
        val left = (size.width - barW) / 2f
        val colors = listOf(Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFF472B6))
        colors.forEachIndexed { i, color ->
            drawRoundRect(
                color = color,
                topLeft = Offset(left, size.height * 0.68f + i * size.height * 0.1f),
                size = Size(barW, size.height * 0.075f),
                cornerRadius = CornerRadius(18f, 18f),
            )
        }
    }
}

private fun dealRingQuestions(answered: Set<String>, onlyNew: Boolean): List<RingQuestion> {
    val pool = if (onlyNew) {
        BrainRingBank.all.filter { it.id !in answered }
    } else {
        BrainRingBank.all
    }
    return pool.shuffled().take(RING_COUNT).map { fact ->
        RingQuestion(
            id = fact.id,
            emoji = fact.emoji,
            prompt = fact.prompt,
            correct = fact.correct,
            options = (fact.wrong + fact.correct).shuffled(),
        )
    }
}
