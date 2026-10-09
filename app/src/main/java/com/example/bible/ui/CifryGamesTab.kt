package com.example.bible.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.CifryRepository
import com.example.bible.data.MathVisualThemes
import com.example.bible.games.KidsMusicTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.random.Random

/** Палочки нарисованы парой — их неудобно считать. */
private val CountThemes by lazy { MathVisualThemes.all.filter { it.nameRuPlural != "палочки" } }

private val DigitPraise = listOf("Отлично!", "Супер!", "Правильно!", "Молодец!", "Ура! Верно!", "Здорово!")

@Composable
internal fun CifryGamesTab(speak: (String) -> Unit, isActive: Boolean) {
    if (!isActive) {
        Box(Modifier.fillMaxSize())
        return
    }
    KidsGameMusic(KidsMusicTrack.GAMES)
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        KidsTabs(
            tabs = listOf("🔍 Найди цифру", "🍎 Сосчитай", "⚖️ Где больше?"),
            selected = mode,
            onSelect = { mode = it },
        )
        when (mode) {
            0 -> FindDigitGame(speak)
            1 -> CountGame(speak)
            else -> CompareGame(speak)
        }
    }
}

@Composable
private fun FindDigitGame(speak: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val random = remember { Random(System.currentTimeMillis()) }
    var roundKey by remember { mutableIntStateOf(0) }
    val round = remember(roundKey) {
        val target = random.nextInt(0, 10)
        val choices = ((0..9).filter { it != target }.shuffled(random).take(3) + target).shuffled(random)
        target to choices
    }
    val (target, choices) = round
    var picked by remember(roundKey) { mutableStateOf<Int?>(null) }
    var solved by remember(roundKey) { mutableStateOf(false) }
    val question = "Найди цифру ${CifryRepository.nameForValue(target)}"
    LaunchedEffect(roundKey) {
        delay(300)
        speak(question)
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KidsVoiceQuestionCard(label = "🔍 Найди цифру", prompt = CifryRepository.nameForValue(target).uppercase(), onRepeat = { speak(question) })
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                choices.chunked(2).forEachIndexed { rowIdx, row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEachIndexed { colIdx, d ->
                            BigDigitTile(
                                digit = d,
                                index = d,
                                state = when {
                                    solved && d == target -> KidsAnswerState.Correct
                                    solved -> KidsAnswerState.Dimmed
                                    picked == d -> KidsAnswerState.Wrong
                                    else -> KidsAnswerState.Idle
                                },
                                appearKey = roundKey * 10 + rowIdx * 2 + colIdx,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = {
                                    if (solved) return@BigDigitTile
                                    picked = d
                                    if (d == target) {
                                        solved = true
                                        KidsGameStreak.answered(true)
                                        speak("${DigitPraise.random(random)} Это ${CifryRepository.nameForValue(d)}!")
                                        scope.launch {
                                            repo.addPoints(3)
                                            delay(1600)
                                            roundKey++
                                        }
                                    } else {
                                        KidsGameStreak.answered(false)
                                        speak("Это ${CifryRepository.nameForValue(d)}. Найди ${CifryRepository.nameForValue(target)}!")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        KidsCorrectBurst()
    }
}

/** Большая плитка с цифрой: прыгает при появлении, качается при ошибке. */
@Composable
private fun BigDigitTile(
    digit: Int,
    index: Int,
    state: KidsAnswerState,
    appearKey: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val appear = remember(appearKey) { Animatable(0.5f) }
    LaunchedEffect(appearKey) { appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
    val shake = remember(appearKey) { Animatable(0f) }
    LaunchedEffect(state) {
        if (state == KidsAnswerState.Wrong) for (x in listOf(-12f, 10f, -8f, 5f, -2f, 0f)) shake.animateTo(x, tween(55))
    }
    val pop by animateFloatAsState(
        when (state) {
            KidsAnswerState.Correct -> 1.05f
            KidsAnswerState.Dimmed -> 0.92f
            else -> 1f
        },
        spring(dampingRatio = 0.45f, stiffness = 300f),
        label = "digitPop",
    )
    val gradient = when (state) {
        KidsAnswerState.Correct -> KidsCorrectGradient
        KidsAnswerState.Wrong -> KidsWrongGradient
        else -> kidsGradient(index)
    }
    BoxWithConstraints(
        modifier
            .graphicsLayer {
                val s = appear.value * pop
                scaleX = s
                scaleY = s
                translationX = shake.value * density
                alpha = if (state == KidsAnswerState.Dimmed) 0.45f else 1f
            }
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(gradient))
            .border(if (state == KidsAnswerState.Correct) 4.dp else 0.dp, Color.White, RoundedCornerShape(30.dp))
            .clickable(enabled = state != KidsAnswerState.Dimmed, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val fs = (minOf(maxWidth.value, maxHeight.value) * 0.62f).coerceIn(36f, 140f)
        Text("$digit", color = Color.White, fontSize = fs.sp, fontWeight = FontWeight.Black)
        if (state == KidsAnswerState.Correct) {
            Text("⭐", fontSize = 28.sp, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}

/** Предметы, разложенные ровной сеткой внутри своей рамки. */
@Composable
private fun EmojiPile(emoji: String, count: Int, modifier: Modifier = Modifier, sizeAsCount: Int = count) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (count <= 0) return@BoxWithConstraints
        val layoutCount = maxOf(count, sizeAsCount)
        val ratio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val cols = ceil(sqrt(layoutCount * ratio.toDouble())).toInt().coerceIn(1, layoutCount)
        val rows = (layoutCount + cols - 1) / cols
        val cell = minOf(maxWidth.value / cols, maxHeight.value / rows)
        val fs = (cell * 0.62f).coerceIn(14f, 110f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            (0 until count).chunked(cols).forEach { row ->
                Row {
                    row.forEach { _ ->
                        Box(Modifier.padding((cell * 0.08f).dp), contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = fs.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountGame(speak: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val random = remember { Random(System.currentTimeMillis()) }
    var roundKey by remember { mutableIntStateOf(0) }
    val theme = remember(roundKey) { CountThemes.random(random) }
    val count = remember(roundKey) { random.nextInt(1, 11) }
    val options = remember(roundKey) {
        ((1..10).filter { it != count }.shuffled(random).take(3) + count).sorted()
    }
    var picked by remember(roundKey) { mutableStateOf<Int?>(null) }
    var solved by remember(roundKey) { mutableStateOf(false) }
    val question = "Сосчитай: сколько тут ${theme.nameRuPlural}?"
    LaunchedEffect(roundKey) {
        delay(300)
        speak(question)
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KidsVoiceQuestionCard(label = "🍎 Сосчитай", prompt = "Сколько ${theme.emoji}?", onRepeat = { speak(question) })
            EmojiPile(
                emoji = theme.emoji,
                count = count,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFEF3C7), Color(0xFFFCE7F3))))
                    .padding(12.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                options.forEachIndexed { i, n ->
                    KidsAnswerTile(
                        text = "$n",
                        index = i + 4,
                        state = when {
                            solved && n == count -> KidsAnswerState.Correct
                            solved -> KidsAnswerState.Dimmed
                            picked == n -> KidsAnswerState.Wrong
                            else -> KidsAnswerState.Idle
                        },
                        onClick = {
                            if (solved) return@KidsAnswerTile
                            picked = n
                            if (n == count) {
                                solved = true
                                KidsGameStreak.answered(true)
                                val counting = (1..count).joinToString(", ") { CifryRepository.nameForValue(it) }
                                speak("$counting! ${DigitPraise.random(random)}")
                                scope.launch {
                                    repo.addPoints(3)
                                    delay(1400L + count * 350L)
                                    roundKey++
                                }
                            } else {
                                KidsGameStreak.answered(false)
                                speak("Нет, посчитай ещё раз!")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        height = 84.dp,
                        fontSize = 34,
                        appearKey = roundKey,
                    )
                }
            }
        }
        KidsCorrectBurst()
    }
}

@Composable
private fun CompareGame(speak: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val random = remember { Random(System.currentTimeMillis()) }
    var roundKey by remember { mutableIntStateOf(0) }
    val theme = remember(roundKey) { CountThemes.random(random) }
    val counts = remember(roundKey) {
        val a = random.nextInt(1, 10)
        var b = random.nextInt(1, 10)
        while (b == a) b = random.nextInt(1, 10)
        a to b
    }
    var picked by remember(roundKey) { mutableStateOf<Int?>(null) }
    var solved by remember(roundKey) { mutableStateOf(false) }
    val bigger = if (counts.first > counts.second) 0 else 1
    val question = "Где больше? Нажми!"
    LaunchedEffect(roundKey) {
        delay(300)
        speak(question)
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KidsVoiceQuestionCard(label = "⚖️ Сравни", prompt = "Где больше?", onRepeat = { speak(question) })
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(counts.first, counts.second).forEachIndexed { side, n ->
                    val state = when {
                        solved && side == bigger -> KidsAnswerState.Correct
                        solved -> KidsAnswerState.Dimmed
                        picked == side -> KidsAnswerState.Wrong
                        else -> KidsAnswerState.Idle
                    }
                    val bg = when (state) {
                        KidsAnswerState.Correct -> KidsCorrectGradient
                        KidsAnswerState.Wrong -> KidsWrongGradient
                        else -> listOf(kidsGradient(side * 3 + 1)[0].copy(alpha = 0.18f), kidsGradient(side * 3 + 1)[1].copy(alpha = 0.3f))
                    }
                    val shake = remember(roundKey, side) { Animatable(0f) }
                    LaunchedEffect(state) {
                        if (state == KidsAnswerState.Wrong) for (x in listOf(-12f, 10f, -8f, 5f, 0f)) shake.animateTo(x, tween(55))
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer {
                                translationX = shake.value * density
                                alpha = if (state == KidsAnswerState.Dimmed) 0.5f else 1f
                            }
                            .clip(RoundedCornerShape(28.dp))
                            .background(Brush.linearGradient(bg))
                            .border(if (state == KidsAnswerState.Correct) 4.dp else 0.dp, Color.White, RoundedCornerShape(28.dp))
                            .clickable(enabled = !solved) {
                                picked = side
                                if (side == bigger) {
                                    solved = true
                                    KidsGameStreak.answered(true)
                                    val big = maxOf(counts.first, counts.second)
                                    val small = minOf(counts.first, counts.second)
                                    speak("Правильно! ${CifryRepository.nameForValue(big).replaceFirstChar { it.uppercase() }} больше, чем ${CifryRepository.nameForValue(small)}!")
                                    scope.launch {
                                        repo.addPoints(3)
                                        delay(2600)
                                        roundKey++
                                    }
                                } else {
                                    KidsGameStreak.answered(false)
                                    speak("Здесь меньше. Посмотри ещё раз!")
                                }
                            }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmojiPile(theme.emoji, n, Modifier.weight(1f).fillMaxWidth(), sizeAsCount = maxOf(counts.first, counts.second))
                        Text(
                            if (solved) "$n" else " ",
                            color = if (state == KidsAnswerState.Idle || state == KidsAnswerState.Dimmed) Color(0xFF1E1B4B) else Color.White,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
        KidsCorrectBurst()
    }
}
