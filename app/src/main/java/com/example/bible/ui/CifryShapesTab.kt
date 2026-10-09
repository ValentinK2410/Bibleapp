package com.example.bible.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.CifryRepository
import com.example.bible.games.KidsGameAudio
import com.example.bible.games.KidsMusicTrack
import com.example.bible.games.KidsSfx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val SHAPE_ROUNDS = 10
private val ShapePraise = listOf("Молодец!", "Правильно!", "Ура! Верно!", "Отлично!", "Здорово!", "Умница!")

@Composable
internal fun CifryShapesTab(speak: (String) -> Unit, isActive: Boolean) {
    if (!isActive) {
        Box(Modifier.fillMaxSize())
        return
    }
    KidsGameMusic(KidsMusicTrack.GAMES)
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        KidsTabs(
            tabs = listOf("🎯 Найди фигуру", "📐 Сколько углов", "📚 Все фигуры"),
            selected = mode,
            onSelect = { mode = it },
        )
        when (mode) {
            0 -> ShapeFindGame(speak)
            1 -> ShapeCornersGame(speak)
            else -> ShapesGallery(speak)
        }
    }
}

/** Тёмная карточка задания с крупной надписью и кнопкой «повторить». */
@Composable
internal fun KidsVoiceQuestionCard(
    label: String,
    prompt: String,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    promptSize: Int? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(KidsNightGradient))
            .clickable(onClick = onRepeat)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.size(8.dp))
                KidsStreakBadge()
            }
            val fs = promptSize ?: when {
                prompt.length <= 9 -> 28
                prompt.length <= 12 -> 23
                else -> 19
            }
            Text(prompt, color = Color.White, fontSize = fs.sp, lineHeight = (fs + 4).sp, fontWeight = FontWeight.Black, maxLines = 2)
        }
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Повторить", tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

private data class ShapeRound(val target: KidsShape, val options: List<Pair<KidsShape, Color>>)

private fun newShapeRound(round: Int, previous: KidsShape?, random: Random): ShapeRound {
    val pool = if (round < 4) KidsShape.basic else KidsShape.entries
    val count = when {
        round < 3 -> 3
        round < 7 -> 4
        else -> 6
    }
    val target = pool.filter { it != previous }.random(random)
    val others = pool.filter { it != target }.shuffled(random).take(count - 1)
    val colors = KidsShapeColors.shuffled(random)
    val shapes = (others + target).shuffled(random)
    return ShapeRound(target, shapes.mapIndexed { i, s -> s to colors[i % colors.size] })
}

@Composable
private fun ShapeFindGame(speak: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val random = remember { Random(System.currentTimeMillis()) }
    var gameNonce by remember { mutableIntStateOf(0) }
    var roundIndex by remember(gameNonce) { mutableIntStateOf(0) }
    var round by remember(gameNonce) { mutableStateOf(newShapeRound(0, null, random)) }
    var firstTry by remember(gameNonce) { mutableIntStateOf(0) }
    var mistakes by remember(roundIndex, gameNonce) { mutableIntStateOf(0) }
    var solved by remember(roundIndex, gameNonce) { mutableStateOf(false) }
    var wrongPick by remember(roundIndex, gameNonce) { mutableStateOf<KidsShape?>(null) }
    var finished by remember(gameNonce) { mutableStateOf(false) }
    val results = remember(gameNonce) { mutableStateListOf<Boolean>() }

    val question = "Найди ${round.target.findRu}"
    LaunchedEffect(round, finished) {
        if (finished) return@LaunchedEffect
        delay(350)
        speak(question)
    }

    if (finished) {
        KidsGameFinish(firstTry = firstTry, total = SHAPE_ROUNDS, points = firstTry * 3, onAgain = { gameNonce++ })
        return
    }

    fun onPick(shape: KidsShape) {
        if (solved) return
        if (shape == round.target) {
            solved = true
            val clean = mistakes == 0
            if (clean) firstTry++
            results.add(clean)
            KidsGameStreak.answered(true)
            speak("${ShapePraise.random(random)} Это ${shape.nameRu.lowercase()}!")
            scope.launch {
                delay(1700)
                if (roundIndex + 1 >= SHAPE_ROUNDS) {
                    if (firstTry > 0) repo.addPoints(firstTry * 3)
                    KidsGameAudio.play(KidsSfx.WIN)
                    speak("Ура! Игра пройдена!")
                    finished = true
                } else {
                    val next = roundIndex + 1
                    round = newShapeRound(next, round.target, random)
                    roundIndex = next
                }
            }
        } else {
            mistakes++
            wrongPick = shape
            KidsGameStreak.answered(false)
            speak("Это ${shape.nameRu.lowercase()}. Найди ${round.target.findRu}!")
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KidsRoundsProgress(results = results, current = roundIndex, total = SHAPE_ROUNDS)
            KidsVoiceQuestionCard(
                label = "🔍 Найди фигуру",
                prompt = round.target.nameRu.uppercase(),
                onRepeat = { speak(question) },
            )
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val count = round.options.size
                val columns = if (count <= 4) 2 else 3
                val rows = (count + columns - 1) / columns
                val gap = 12.dp
                val cellW = (maxWidth - gap * (columns - 1)) / columns
                val cellH = (maxHeight - gap * (rows - 1)) / rows
                val side = minOf(cellW, cellH)
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    round.options.chunked(columns).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            rowItems.forEach { (shape, color) ->
                                ShapeOptionTile(
                                    shape = shape,
                                    color = color,
                                    sizeDp = side.value,
                                    appearKey = "$gameNonce-$roundIndex",
                                    correct = solved && shape == round.target,
                                    dimmed = solved && shape != round.target,
                                    shake = wrongPick == shape,
                                    shakeKey = mistakes,
                                    onClick = { onPick(shape) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
        KidsCorrectBurst()
    }
}

@Composable
private fun ShapeOptionTile(
    shape: KidsShape,
    color: Color,
    sizeDp: Float,
    appearKey: String,
    correct: Boolean,
    dimmed: Boolean,
    shake: Boolean,
    shakeKey: Int,
    onClick: () -> Unit,
) {
    val appear = remember(appearKey) { Animatable(0f) }
    LaunchedEffect(appearKey) {
        delay(Random.nextLong(0, 160))
        appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
    }
    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(shakeKey, shake) {
        if (!shake || shakeKey == 0) return@LaunchedEffect
        for (x in listOf(-14f, 12f, -9f, 6f, -3f, 0f)) shakeAnim.animateTo(x, tween(55))
    }
    val pop by animateFloatAsState(
        targetValue = when {
            correct -> 1.08f
            dimmed -> 0.85f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 260f),
        label = "shapePop",
    )
    val shapeCard = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .size(sizeDp.dp)
            .graphicsLayer {
                val s = appear.value * pop
                scaleX = s
                scaleY = s
                translationX = shakeAnim.value * density
                alpha = if (dimmed) 0.4f else 1f
            }
            .clip(shapeCard)
            .background(
                if (correct) Brush.linearGradient(KidsCorrectGradient)
                else Brush.linearGradient(listOf(color.copy(alpha = 0.12f), color.copy(alpha = 0.22f))),
            )
            .border(if (correct) 3.dp else 2.dp, if (correct) Color.White else color.copy(alpha = 0.35f), shapeCard)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        KidsShapeImage(shape, color, Modifier.fillMaxSize().padding((sizeDp * 0.1f).dp))
        if (correct) {
            Text("⭐", fontSize = (sizeDp * 0.2f).sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

private data class CornersRound(val shape: KidsShape, val color: Color, val options: List<Int>)

private fun newCornersRound(previous: KidsShape?, random: Random): CornersRound {
    val shape = KidsShape.withCorners.filter { it != previous }.random(random)
    val correct = shape.corners ?: 0
    val wrong = listOf(0, 3, 4, 5, 6, 8).filter { it != correct }.shuffled(random).take(3)
    return CornersRound(shape, KidsShapeColors.random(random), (wrong + correct).sorted())
}

@Composable
private fun ShapeCornersGame(speak: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val random = remember { Random(System.currentTimeMillis()) }
    var roundKey by remember { mutableIntStateOf(0) }
    var round by remember { mutableStateOf(newCornersRound(null, random)) }
    var picked by remember(roundKey) { mutableStateOf<Int?>(null) }
    var solved by remember(roundKey) { mutableStateOf(false) }
    var mistakes by remember(roundKey) { mutableIntStateOf(0) }
    val cornersAnim = remember(roundKey) { Animatable(0f) }

    val question = "Сколько углов у ${round.shape.ofRu}?"
    LaunchedEffect(roundKey) {
        delay(350)
        speak(question)
    }

    fun onPick(n: Int) {
        if (solved) return
        picked = n
        val correct = round.shape.corners ?: 0
        if (n == correct) {
            solved = true
            KidsGameStreak.answered(true)
            val counting = (1..correct).joinToString(", ") { CifryRepository.nameForValue(it) }
            val answer = if (correct == 0) {
                "Правильно! У ${round.shape.ofRu} нет углов!"
            } else {
                "$counting! Правильно! У ${round.shape.ofRu} ${CifryRepository.nameForValue(correct)} ${cornersWord(correct)}!"
            }
            speak(answer)
            scope.launch {
                if (mistakes == 0) repo.addPoints(3)
                cornersAnim.animateTo(1f, tween(durationMillis = 450 * correct.coerceAtLeast(1), easing = LinearEasing))
                delay(1800)
                round = newCornersRound(round.shape, random)
                roundKey++
            }
        } else {
            mistakes++
            KidsGameStreak.answered(false)
            speak("Нет, посчитай уголки ещё раз!")
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KidsVoiceQuestionCard(
                label = "📐 Уголки",
                prompt = round.shape.nameRu,
                onRepeat = { speak(question) },
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(round.color.copy(alpha = 0.10f), round.color.copy(alpha = 0.24f))))
                    .clickable { speak(round.shape.nameRu) },
                contentAlignment = Alignment.Center,
            ) {
                KidsShapeImage(
                    shape = round.shape,
                    color = round.color,
                    cornersProgress = cornersAnim.value,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                round.options.forEachIndexed { i, n ->
                    val correct = n == (round.shape.corners ?: 0)
                    KidsAnswerTile(
                        text = "$n",
                        index = i + 2,
                        state = when {
                            solved && correct -> KidsAnswerState.Correct
                            solved -> KidsAnswerState.Dimmed
                            picked == n -> KidsAnswerState.Wrong
                            else -> KidsAnswerState.Idle
                        },
                        onClick = { onPick(n) },
                        modifier = Modifier.weight(1f),
                        height = 84.dp,
                        fontSize = 36,
                        appearKey = roundKey,
                    )
                }
            }
        }
        KidsCorrectBurst()
    }
}

private fun cornersWord(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "угол"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "угла"
    else -> "углов"
}

private sealed interface GalleryItem {
    data class Header(val title: String) : GalleryItem
    data class Item(val shape: KidsShape, val index: Int) : GalleryItem
}

@Composable
private fun ShapesGallery(speak: (String) -> Unit) {
    val items = remember {
        buildList {
            add(GalleryItem.Header("✏️ Плоские фигуры"))
            KidsShape.flat.forEachIndexed { i, s -> add(GalleryItem.Item(s, i)) }
            add(GalleryItem.Header("🧊 Объёмные фигуры"))
            KidsShape.solids.forEachIndexed { i, s -> add(GalleryItem.Item(s, i + 3)) }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            items,
            key = { if (it is GalleryItem.Item) it.shape.name else (it as GalleryItem.Header).title },
            span = { GridItemSpan(if (it is GalleryItem.Header) maxLineSpan else 1) },
        ) { item ->
            when (item) {
                is GalleryItem.Header -> Text(
                    item.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
                is GalleryItem.Item -> GalleryShapeCell(item.shape, KidsShapeColors[item.index % KidsShapeColors.size]) {
                    KidsGameAudio.play(KidsSfx.TAP)
                    speak(item.shape.nameRu)
                }
            }
        }
    }
}

@Composable
private fun GalleryShapeCell(shape: KidsShape, color: Color, onClick: () -> Unit) {
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.10f), color.copy(alpha = 0.24f))))
            .border(2.dp, color.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .clickable {
                onClick()
                scope.launch {
                    bounce.snapTo(0.88f)
                    bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 400f))
                }
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KidsShapeImage(shape, color, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(
            shape.nameRu,
            fontSize = when {
                shape.nameRu.length <= 8 -> 14.sp
                shape.nameRu.length <= 11 -> 12.sp
                else -> 10.sp
            },
            fontWeight = FontWeight.Black,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
