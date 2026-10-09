package com.example.bible.ui

import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.KidsTopicItem
import com.example.bible.data.KidsTopicsRepository
import com.example.bible.games.KidsGameAudio
import com.example.bible.games.KidsMusicTrack
import com.example.bible.games.KidsSfx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

private const val COLOR_ROUNDS = 10

private data class ColorRound(val target: KidsTopicItem, val options: List<KidsTopicItem>)

private fun colorChoicesFor(round: Int): Int = when {
    round < 3 -> 3
    round < 6 -> 4
    else -> 6
}

private fun newColorRound(round: Int, previous: KidsTopicItem?, random: Random): ColorRound {
    val all = KidsTopicsRepository.colors
    val target = all.filter { it != previous }.random(random)
    val others = all.filter { it != target }.shuffled(random).take(colorChoicesFor(round) - 1)
    return ColorRound(target, (others + target).shuffled(random))
}

private val ColorPraise = listOf("Молодец!", "Правильно!", "Ура! Верно!", "Отлично!", "Здорово!", "Умница!")

private fun KidsTopicItem.color(): Color = Color(swatchArgb!!.toLong())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsColorsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.forLanguageTag("ru-RU")
                engine?.setSpeechRate(0.9f)
                engine?.setPitch(1.1f)
                tts = engine
            }
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
            tts = null
        }
    }
    val speak: (String) -> Unit = { text ->
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kids_color_${System.nanoTime()}")
    }
    var tab by remember { mutableIntStateOf(0) }
    KidsGameMusic(KidsMusicTrack.GAMES)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Цвета", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = { KidsAudioToggles() },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            KidsTabs(
                tabs = listOf("🎯 Найди цвет", "🎨 Все цвета"),
                selected = tab,
                onSelect = {
                    tts?.stop()
                    tab = it
                },
            )
            when (tab) {
                0 -> FindColorGame(speak = speak, ttsReady = tts != null)
                else -> AllColorsGrid(speak = speak)
            }
        }
    }
}

@Composable
private fun AllColorsGrid(speak: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(KidsTopicsRepository.colors, key = { it.label }) { item ->
            val bg = item.color()
            val onBg = if (bg.luminance() > 0.55f) Color(0xFF1A1A1A) else Color.White
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .shadow(6.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(bg)
                    .border(
                        if (bg.luminance() > 0.9f) 2.dp else 0.dp,
                        Color(0xFFCBD5E1),
                        RoundedCornerShape(26.dp),
                    )
                    .clickable {
                        KidsGameAudio.play(KidsSfx.TAP)
                        speak(item.speak)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(item.label, color = onBg, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun FindColorGame(speak: (String) -> Unit, ttsReady: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val random = remember { Random(System.currentTimeMillis()) }
    var gameNonce by remember { mutableIntStateOf(0) }
    var roundIndex by remember(gameNonce) { mutableIntStateOf(0) }
    var round by remember(gameNonce) { mutableStateOf(newColorRound(0, null, random)) }
    var firstTry by remember(gameNonce) { mutableIntStateOf(0) }
    var mistakesThisRound by remember(roundIndex, gameNonce) { mutableIntStateOf(0) }
    var solvedThisRound by remember(roundIndex, gameNonce) { mutableStateOf(false) }
    var wrongPick by remember(roundIndex, gameNonce) { mutableStateOf<KidsTopicItem?>(null) }
    var finished by remember(gameNonce) { mutableStateOf(false) }
    val results = remember(gameNonce) { mutableListOf<Boolean>() }

    val question = "Найди ${round.target.label.lowercase()} цвет"
    LaunchedEffect(round, ttsReady) {
        if (!ttsReady || finished) return@LaunchedEffect
        delay(350)
        speak(question)
    }

    if (finished) {
        ColorGameFinish(
            firstTry = firstTry,
            onAgain = { gameNonce++ },
        )
        return
    }

    fun onPick(item: KidsTopicItem) {
        if (solvedThisRound) return
        if (item == round.target) {
            solvedThisRound = true
            val clean = mistakesThisRound == 0
            if (clean) firstTry++
            results.add(clean)
            KidsGameAudio.play(KidsSfx.CORRECT)
            speak("${ColorPraise.random(random)} Это ${item.label.lowercase()}!")
            scope.launch {
                delay(1700)
                if (roundIndex + 1 >= COLOR_ROUNDS) {
                    val points = firstTry * 3
                    if (points > 0) repo.addPoints(points)
                    KidsGameAudio.play(KidsSfx.WIN)
                    speak("Ура! Игра пройдена!")
                    finished = true
                } else {
                    val next = roundIndex + 1
                    round = newColorRound(next, round.target, random)
                    roundIndex = next
                }
            }
        } else {
            mistakesThisRound++
            wrongPick = item
            KidsGameAudio.play(KidsSfx.WRONG)
            speak("Это ${item.label.lowercase()}. Найди ${round.target.label.lowercase()}!")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ColorProgress(results = results, current = roundIndex)
        ColorQuestionCard(
            target = round.target,
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
            val gap = 14.dp
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
                        rowItems.forEach { item ->
                            ColorBlob(
                                item = item,
                                sizeDp = side.value,
                                appearKey = "$gameNonce-$roundIndex",
                                correct = solvedThisRound && item == round.target,
                                dimmed = solvedThisRound && item != round.target,
                                shake = wrongPick == item,
                                shakeKey = mistakesThisRound,
                                onClick = { onPick(item) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ColorProgress(results: List<Boolean>, current: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(COLOR_ROUNDS) { i ->
            val text = when {
                i < results.size && results[i] -> "⭐"
                i < results.size -> "✔️"
                i == current -> "🔵"
                else -> "⚪"
            }
            Text(text, fontSize = if (i == current) 20.sp else 16.sp)
        }
    }
}

@Composable
private fun ColorQuestionCard(target: KidsTopicItem, onRepeat: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(KidsNightGradient))
            .clickable(onClick = onRepeat)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("🔍 Найди цвет", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                target.label.uppercase(),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Повторить вопрос",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun ColorBlob(
    item: KidsTopicItem,
    sizeDp: Float,
    appearKey: String,
    correct: Boolean,
    dimmed: Boolean,
    shake: Boolean,
    shakeKey: Int,
    onClick: () -> Unit,
) {
    val color = item.color()
    val appear = remember(appearKey) { Animatable(0f) }
    LaunchedEffect(appearKey) {
        delay(Random.nextLong(0, 160))
        appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
    }
    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(shakeKey, shake) {
        if (!shake || shakeKey == 0) return@LaunchedEffect
        for (target in listOf(-14f, 12f, -9f, 6f, -3f, 0f)) {
            shakeAnim.animateTo(target, tween(55))
        }
    }
    val pop by animateFloatAsState(
        targetValue = when {
            correct -> 1.15f
            dimmed -> 0.82f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 260f),
        label = "blobPop",
    )
    val wobble = rememberInfiniteTransition(label = "wobble")
    val breathe by wobble.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val light = color.luminance() > 0.9f
    Box(
        Modifier
            .size(sizeDp.dp)
            .graphicsLayer {
                val s = appear.value * pop * (1f + 0.025f * sin(breathe * Math.PI.toFloat()))
                scaleX = s
                scaleY = s
                translationX = shakeAnim.value * density
                alpha = if (dimmed) 0.45f else 1f
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f * 0.9f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color.Black.copy(alpha = 0.18f), r, c + Offset(0f, r * 0.08f))
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.55f), color, color),
                    center = c - Offset(r * 0.4f, r * 0.45f),
                    radius = r * 1.5f,
                ),
                r,
                c,
            )
            if (light) {
                drawCircle(Color(0xFFCBD5E1), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.05f))
            }
            drawCircle(Color.White.copy(alpha = 0.6f), r * 0.16f, c - Offset(r * 0.42f, r * 0.46f))
            drawCircle(Color.White.copy(alpha = 0.35f), r * 0.07f, c - Offset(r * 0.18f, r * 0.62f))
            if (correct) {
                drawCircle(Color.White, r * 1.0f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.08f))
            }
        }
        if (correct) {
            Text("⭐", fontSize = (sizeDp * 0.32f).sp)
        }
    }
}

@Composable
private fun ColorGameFinish(firstTry: Int, onAgain: () -> Unit) {
    val stars = (firstTry * 3) / 10
    Box(Modifier.fillMaxSize()) {
        KidsConfetti(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🏆", fontSize = 96.sp)
            Text("Ура! Молодец!", fontSize = 32.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "С первого раза: $firstTry из $COLOR_ROUNDS",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (stars > 0) "+$stars ${starsWord(stars)} ⭐" else "Сыграй ещё, чтобы получить звёздочку ⭐",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF59E0B),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(24.dp))
            KidsBigButton(text = "🎯 Играть ещё", onClick = onAgain, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun KidsConfetti(modifier: Modifier = Modifier) {
    val pieces = remember {
        List(42) {
            Triple(Random.nextFloat(), Random.nextFloat(), KidsTopicsRepository.colors.random().color())
        }
    }
    val fall = rememberInfiniteTransition(label = "confetti")
    val t by fall.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "confettiFall",
    )
    Canvas(modifier) {
        pieces.forEachIndexed { i, (x, offset, color) ->
            val progress = (t + offset) % 1f
            val px = size.width * x + sin((progress * 6f + i) .toDouble()).toFloat() * 18f
            val py = size.height * progress
            drawCircle(color.copy(alpha = 0.85f), radius = 7f + (i % 4) * 2f, center = Offset(px, py))
        }
    }
}
