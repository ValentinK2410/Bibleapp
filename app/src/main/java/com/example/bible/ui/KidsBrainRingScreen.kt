package com.example.bible.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import com.example.bible.audio.MusicSynth
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
private const val RING_SECONDS = 5f
private const val RING_WIN_AT = 5

private enum class RingPhase { Ready, Play, Done }

private data class RingQuestion(
    val emoji: String,
    val prompt: String,
    val correct: String,
    val options: List<String>,
)

private val ringMelodyMutex = Mutex()
private val ringMelodyToken = AtomicInteger(0)

private suspend fun playRingCountdown(token: Int) {
    ringMelodyMutex.withLock {
        if (token != ringMelodyToken.get()) return
        if (!KidsGameAudio.musicOn.value) return
        MusicSynth.playEvents(ringCountdownMelody())
        if (token != ringMelodyToken.get()) runCatching { MusicSynth.stopReference() }
    }
}

private fun cutRingCountdown() {
    ringMelodyToken.incrementAndGet()
    runCatching { MusicSynth.stopReference() }
}

/** Короткая мелодия на 5 секунд: каждый удар выше, в конце торопливые ноты. */
private fun ringCountdownMelody(): List<MusicSynth.NoteEvent> = listOf(
    MusicSynth.NoteEvent(72, 0.00, 0.22, 0.55),
    MusicSynth.NoteEvent(64, 0.00, 0.28, 0.28),
    MusicSynth.NoteEvent(76, 1.00, 0.20, 0.62),
    MusicSynth.NoteEvent(67, 1.00, 0.26, 0.30),
    MusicSynth.NoteEvent(79, 2.00, 0.18, 0.72),
    MusicSynth.NoteEvent(71, 2.00, 0.24, 0.32),
    MusicSynth.NoteEvent(84, 3.00, 0.14, 0.82),
    MusicSynth.NoteEvent(76, 3.00, 0.20, 0.36),
    MusicSynth.NoteEvent(88, 4.00, 0.10, 0.95),
    MusicSynth.NoteEvent(84, 4.28, 0.10, 0.9),
    MusicSynth.NoteEvent(91, 4.52, 0.10, 1.0),
    MusicSynth.NoteEvent(96, 4.74, 0.16, 1.0),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsBrainRingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var match by remember { mutableIntStateOf(0) }
    val questions = remember(match) { dealRingQuestions() }
    var index by remember(match) { mutableIntStateOf(0) }
    var phase by remember(match) { mutableStateOf(RingPhase.Ready) }
    var correctCount by remember(match) { mutableIntStateOf(0) }
    var marks by remember(match) { mutableStateOf(List(RING_COUNT) { null as Boolean? }) }
    var remaining by remember { mutableFloatStateOf(RING_SECONDS) }
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
    val shownRemaining = if (liveClock) remaining else RING_SECONDS
    val urgent = phase == RingPhase.Play && !showResult && liveClock && shownRemaining <= 1.6f
    val backdrop by animateColorAsState(
        targetValue = if (urgent) Color(0xFF7F1D1D) else Color(0xFF1E1B4B),
        animationSpec = tween(240),
        label = "ringBackdrop",
    )
    val won = phase == RingPhase.Done && correctCount >= RING_WIN_AT
    KidsGameWinReward(game = KidsGames.BRAIN_RING, won = won, points = 20, round = match)

    fun mark(qIndex: Int, ok: Boolean) {
        if (qIndex !in marks.indices || marks[qIndex] != null) return
        marks = marks.toMutableList().also { it[qIndex] = ok }
        if (ok) correctCount += 1
    }

    LaunchedEffect(match, index, phase) {
        if (phase != RingPhase.Play) return@LaunchedEffect
        val qIndex = index
        val question = questions[qIndex]
        clockQuestion = qIndex
        remaining = RING_SECONDS
        val token = ringMelodyToken.incrementAndGet()
        val melody = launch(Dispatchers.Default) { playRingCountdown(token) }
        val start = SystemClock.elapsedRealtime()
        var timedOut = false
        while (pickedIndex != qIndex) {
            val left = RING_SECONDS - (SystemClock.elapsedRealtime() - start) / 1000f
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

    val second = ceil(shownRemaining.toDouble()).toInt().coerceIn(0, RING_SECONDS.toInt())
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
                    onStart = {
                        KidsGameStreak.reset()
                        phase = RingPhase.Play
                    },
                )
                RingPhase.Play -> {
                    val question = questions[index]
                    RingPlay(
                        question = question,
                        index = index,
                        marks = marks,
                        correctCount = correctCount,
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
                    won = won,
                    onAgain = { match += 1 },
                )
            }
            KidsCorrectBurst()
            if (won) KidsConfetti(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun RingReady(onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("⏱️", fontSize = 78.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Восемь вопросов из Библии",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "На каждый ответ даётся 5 секунд. Слушай мелодию — она торопит. Жми, пока кольцо не погасло.",
            color = Color.White.copy(alpha = 0.84f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(22.dp))
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
        KidsBigButton(
            text = "На старт!",
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RingPlay(
    question: RingQuestion,
    index: Int,
    marks: List<Boolean?>,
    correctCount: Int,
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
                "Вопрос ${index + 1} из $RING_COUNT",
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
        CountdownRing(remaining = shownRemaining, second = second, pulse = pulse)
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
private fun CountdownRing(remaining: Float, second: Int, pulse: Float) {
    val fraction = (remaining / RING_SECONDS).coerceIn(0f, 1f)
    val color = when {
        remaining > 3.2f -> Color(0xFF4ADE80)
        remaining > 1.6f -> Color(0xFFFBBF24)
        else -> Color(0xFFF87171)
    }
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
            fontSize = 52.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun RingFinish(correctCount: Int, won: Boolean, onAgain: () -> Unit) {
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
            "Верных ответов: $correctCount из $RING_COUNT",
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            if (won) "+2 звезды ⭐" else "Нужно $RING_WIN_AT верных, чтобы забрать звёзды",
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

private data class RingRaw(
    val emoji: String,
    val prompt: String,
    val correct: String,
    val wrong: List<String>,
)

private fun dealRingQuestions(): List<RingQuestion> =
    RING_BANK.shuffled().take(RING_COUNT).map { raw ->
        RingQuestion(
            emoji = raw.emoji,
            prompt = raw.prompt,
            correct = raw.correct,
            options = (raw.wrong + raw.correct).shuffled(),
        )
    }

private val RING_BANK = listOf(
    RingRaw("🌈", "Кто построил ковчег?", "Ной", listOf("Моисей", "Давид")),
    RingRaw("🪨", "Кто победил великана Голиафа?", "Давид", listOf("Самсон", "Иона")),
    RingRaw("🐟", "Кого проглотила большая рыба?", "Иона", listOf("Ной", "Пётр")),
    RingRaw("🍞", "Сколько хлебов было у мальчика?", "Пять", listOf("Два", "Десять")),
    RingRaw("🦁", "Кто ночевал в яме со львами?", "Даниил", listOf("Иосиф", "Авраам")),
    RingRaw("🌊", "Кто провёл народ через море?", "Моисей", listOf("Авраам", "Ной")),
    RingRaw("⭐", "Где родился Иисус?", "В Вифлееме", listOf("В Риме", "В Ниневии")),
    RingRaw("🌈", "Что появилось после потопа?", "Радуга", listOf("Комета", "Молния")),
    RingRaw("✨", "Кому Бог обещал потомков, как звёзды?", "Аврааму", listOf("Адаму", "Петру")),
    RingRaw("🧥", "Какую одежду получил Иосиф?", "Цветную", listOf("Золотую", "Белую")),
    RingRaw("🌍", "Кто создал небо и землю?", "Бог", listOf("Ной", "Адам")),
    RingRaw("🐑", "Сколько овец было у пастуха?", "Сто", listOf("Десять", "Семь")),
    RingRaw("🪨", "Чем Давид победил Голиафа?", "Камнем", listOf("Мечом", "Копьём")),
    RingRaw("👫", "Как звали первых людей?", "Адам и Ева", listOf("Каин и Авель", "Мария и Иосиф")),
    RingRaw("💡", "Что Бог создал в первый день?", "Свет", listOf("Рыб", "Ковчег")),
    RingRaw("👶", "Куда положили малыша Иисуса?", "В ясли", listOf("Во дворец", "На корабль")),
    RingRaw("🐑", "Кто искал потерянную овечку?", "Пастух", listOf("Царь", "Рыбак")),
    RingRaw("🕊️", "Какая птица принесла Ною ветку?", "Голубь", listOf("Орёл", "Воробей")),
    RingRaw("🐍", "Кто уговорил Еву сорвать плод?", "Змей", listOf("Лев", "Птица")),
    RingRaw("📜", "Сколько заповедей получил Моисей?", "Десять", listOf("Семь", "Две")),
    RingRaw("🌟", "Что вело волхвов к Иисусу?", "Звезда", listOf("Радуга", "Облако")),
    RingRaw("👩", "Как звали маму Иисуса?", "Мария", listOf("Ева", "Сарра")),
    RingRaw("🌊", "По чему шёл Иисус к ученикам?", "По воде", listOf("По огню", "По песку")),
    RingRaw("🐟", "Кем были Пётр и Андрей?", "Рыбаками", listOf("Царями", "Пастухами")),
    RingRaw("🍇", "Что Иисус превратил в вино?", "Воду", listOf("Молоко", "Мёд")),
    RingRaw("🔥", "Что не сгорало, когда Бог звал Моисея?", "Куст", listOf("Дворец", "Ковчег")),
    RingRaw("🕳️", "Кого братья опустили в ров?", "Иосифа", listOf("Давида", "Иону")),
    RingRaw("👑", "Кто просил у Бога мудрости?", "Соломон", listOf("Саул", "Ахав")),
    RingRaw("🧺", "Что собрали после чуда с хлебами?", "Корзины", listOf("Камни", "Сети")),
    RingRaw("🔨", "Кем был Иосиф, муж Марии?", "Плотником", listOf("Царём", "Рыбаком")),
)
