package com.example.bible.ui

import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bible.R
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.CifryMathMode
import com.example.bible.data.CifryMathRepository
import com.example.bible.data.CifryMathSolvedEntry
import com.example.bible.data.CifryRepository
import com.example.bible.data.CifryShapes
import com.example.bible.data.DigitInfo
import com.example.bible.data.MathVisualTheme
import com.example.bible.data.OperandBoundsMode
import com.example.bible.data.buildMathChoices
import com.example.bible.data.cifryMathDivisorCap
import com.example.bible.data.cifryMathMaxOperand
import com.example.bible.data.cifryMathMultOperandCap
import com.example.bible.data.digitsChainToInt
import com.example.bible.data.nextCifryMathProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val CIFRY_GAME_WRONG_PHRASES = listOf(
    "Неправильно. Попробуй ещё.",
    "Неверно, попробуй ещё.",
    "Не та цифра. Ищи дальше.",
    "Пока не угадано. Подумай ещё раз.",
    "Мимо! Попробуй другую цифру.",
    "Не подходит. Ищи нужную.",
    "Это другая цифра.",
    "Ещё не то. Ищи дальше.",
    "Нет, смотри внимательнее.",
    "Неверно. Попробуй ещё раз.",
)

private val CIFRY_GAME_CORRECT_PHRASES = listOf(
    "Отлично! Верно!",
    "Супер! Угадано!",
    "Здорово! Так держать!",
    "Правильно! Очень хорошо!",
    "Да! Всё получилось!",
    "Класс! Получилось!",
    "Ура! Верно!",
    "Точно! Супер!",
    "Прекрасно! Так и нужно!",
    "Чудесно! Верно угадано!",
)

private val CIFRY_MATH_WRONG = listOf(
    "Пока неверно. Попробуй ещё.",
    "Не то. Посчитай ещё раз.",
    "Ошибка. Подумай ещё.",
    "Неверно. Другой ответ.",
)

private val CIFRY_MATH_CORRECT = listOf(
    "Верно! Здорово!",
    "Супер! Всё верно!",
    "Правильно! Так держать!",
    "Отлично! Всё сходится!",
    "Да! Задача решена!",
)

/**
 * Разные предметы вокруг цифры на вкладке «Цифры»: столько значков, какова цифра (0 — без картинок).
 * [salt] — значение цифры, чтобы наборы на соседних кнопках отличались.
 */
private val CIFRY_COUNT_OBJECT_EMOJIS = listOf(
    "⭐", "🍎", "🌙", "⚽", "🎈", "🌸", "🔵", "🍀", "🐟", "☀️",
    "🦋", "🍓", "🎵", "💎", "🌿", "🎯", "🧸", "🚀", "🐝", "❤️",
    "🎁", "🦆", "🍊", "🎨", "📚", "🐚", "🌈", "🎪", "🍋", "🪁",
)

private fun cifryDistinctEmojisForCount(count: Int, salt: Int): List<String> {
    if (count <= 0) return emptyList()
    val pool = CIFRY_COUNT_OBJECT_EMOJIS
    val start = (salt * 3 + 5) % pool.size
    val ordered = List(pool.size) { i -> pool[(start + i) % pool.size] }
    return ordered.distinct().take(count)
}

/** Цепочка цифр в песочнице; не private — иначе ViewModelProvider не создаст экземпляр. */
internal class CifrySandboxViewModel : ViewModel() {
    val chain = mutableStateListOf<Int>()
}

private sealed class DigitTapFeedback {
    data object Idle : DigitTapFeedback()
    data class Correct(val value: Int) : DigitTapFeedback()
    data class Wrong(val value: Int) : DigitTapFeedback()
}

private data class FindDigitRound(
    val target: DigitInfo,
    val choices: List<DigitInfo>,
)

private fun nextFindDigitRound(random: Random): FindDigitRound {
    val all = CifryRepository.DIGITS
    val target = all[random.nextInt(all.size)]
    val wrong = all.filter { it.value != target.value }.shuffled(random).take(3)
    val choices = (wrong + target).shuffled(random)
    return FindDigitRound(target, choices)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CifryScreen(
    onBack: () -> Unit,
) {
    val tabs = listOf("Цифры", "Песочница", "Фигуры", "Игры", "Математика")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val progressRepo = remember { AzbukaProgressRepository(context) }
    val mathRepo = remember { CifryMathRepository(context) }
    val ttsUtteranceCallbacks = remember { ConcurrentHashMap<String, () -> Unit>() }
    val tts: TextToSpeech? = remember(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val e = engine ?: return@TextToSpeech
                e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        val cb = utteranceId?.let { ttsUtteranceCallbacks.remove(it) }
                        Handler(Looper.getMainLooper()).post { cb?.invoke() }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        val cb = utteranceId?.let { ttsUtteranceCallbacks.remove(it) }
                        Handler(Looper.getMainLooper()).post { cb?.invoke() }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        val cb = utteranceId?.let { ttsUtteranceCallbacks.remove(it) }
                        Handler(Looper.getMainLooper()).post { cb?.invoke() }
                    }
                })
                e.language = Locale.forLanguageTag("ru-RU")
                val voices = e.voices
                if (voices != null) {
                    val ruVoices = voices.filter {
                        it.locale.language == "ru" && !it.isNetworkConnectionRequired
                    }
                    val femaleKeywords = listOf(
                        "female", "woman", "девуш", "жен", "алёна", "алена",
                        "elena", "svetlana", "anna", "olga", "maria", "tatiana", "natalia", "irina",
                    )
                    val femaleVoice = ruVoices
                        .sortedByDescending { it.quality }
                        .firstOrNull { voice ->
                            femaleKeywords.any { kw -> voice.name.lowercase().contains(kw) }
                        }
                    val bestVoice = femaleVoice
                        ?: ruVoices.sortedWith(
                            compareByDescending<android.speech.tts.Voice> { it.quality }
                                .thenBy { it.latency },
                        ).firstOrNull()
                    if (bestVoice != null) {
                        e.voice = bestVoice
                    }
                }
                e.setSpeechRate(0.9f)
                e.setPitch(1.12f)
            }
        }
        engine
    }
    DisposableEffect(Unit) {
        onDispose { tts?.shutdown() }
    }
    val speak: (String) -> Unit = { text ->
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cifry_${System.nanoTime()}")
    }
    val speakWhenDone: (String, () -> Unit) -> Unit = { text, onDone ->
        val engine = tts
        if (engine == null) {
            onDone()
        } else {
            val id = "cifry_done_${System.nanoTime()}"
            ttsUtteranceCallbacks[id] = onDone
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (result == TextToSpeech.ERROR) {
                ttsUtteranceCallbacks.remove(id)
                onDone()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Цифры", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { tts?.stop() }) {
                        Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.audio_stop))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 4.dp,
                containerColor = Color.Transparent,
                modifier = Modifier.height(44.dp),
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = pagerState.currentPage == i,
                        onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                        modifier = Modifier.height(40.dp),
                        text = {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
            val activePage = pagerState.currentPage
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> CifryDigitsTab(speak = speak)
                    1 -> CifrySandboxTab(speak = speak)
                    2 -> CifryShapesTab(speak = speak)
                    3 -> CifryFindDigitGameTab(
                        speak = speak,
                        speakWhenDone = speakWhenDone,
                        progressRepo = progressRepo,
                        scope = scope,
                        isActive = activePage == 3,
                    )
                    4 -> CifryMathSection(
                        speak = speak,
                        speakWhenDone = speakWhenDone,
                        progressRepo = progressRepo,
                        mathRepo = mathRepo,
                        scope = scope,
                        isActive = activePage == 4,
                    )
                }
            }
        }
    }
}

@Composable
private fun CifryShapesTab(
    speak: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
    ) {
        Text(
            "Нажми на фигуру — услышишь название.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(CifryShapes.all, key = { it.nameRu }) { shape ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { speak(shape.speak) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (shape.imageRes != null) {
                            Image(
                                painter = painterResource(shape.imageRes),
                                contentDescription = shape.nameRu,
                                modifier = Modifier.size(40.dp),
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                            )
                        } else {
                            Text(
                                shape.glyph,
                                fontSize = 34.sp,
                                lineHeight = 38.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            shape.nameRu,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CifryDigitsTab(
    speak: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
    ) {
        CifryDigitsLearnGrid(speak = speak)
    }
}

@Composable
private fun CifryDigitsLearnGrid(
    speak: (String) -> Unit,
) {
    val digits = CifryRepository.DIGITS
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val byValue = remember(digits) { digits.associateBy { it.value } }
    /** Как на калькуляторе: 789 / 456 / 123 / 0 по центру. */
    val calculatorRows = remember {
        listOf(
            listOf(7, 8, 9),
            listOf(4, 5, 6),
            listOf(1, 2, 3),
            listOf(0),
        )
    }
    val gap = 6.dp
    Column(Modifier.fillMaxSize()) {
        Text(
            "Нажми на цифру — услышишь название.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            calculatorRows.forEach { rowValues ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    if (rowValues.size == 3) {
                        rowValues.forEach { v ->
                            val info = byValue.getValue(v)
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                CifryDigitLearnCell(
                                    digit = info,
                                    compactHints = isLandscape,
                                    onClick = { speak(info.nameRu) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    } else {
                        val info = byValue.getValue(rowValues.first())
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            CifryDigitLearnCell(
                                digit = info,
                                compactHints = isLandscape,
                                onClick = { speak(info.nameRu) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CifryDigitLearnCell(
    digit: DigitInfo,
    compactHints: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = digit.value
    val orbitEmojis = remember(count, digit.value) {
        cifryDistinctEmojisForCount(count = count, salt = digit.value)
    }
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 3.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val side = min(maxWidth.value, maxHeight.value)
                    val radiusFrac = if (compactHints) 0.36f else 0.40f
                    val iconFrac = if (compactHints) 0.13f else 0.16f
                    val digitFrac = if (compactHints) 0.42f else 0.48f
                    val radiusDp = (side * radiusFrac).dp
                    val iconSp = (side * iconFrac).coerceIn(10f, 26f).sp
                    val digitSp = (side * digitFrac).coerceIn(30f, 240f).sp
                    if (count > 0 && orbitEmojis.size == count) {
                        repeat(count) { i ->
                            val angle = (i * 2.0 * PI / count - PI / 2.0)
                            val dx = (radiusDp.value * cos(angle)).dp
                            val dy = (radiusDp.value * sin(angle)).dp
                            Text(
                                text = orbitEmojis[i],
                                fontSize = iconSp,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(x = dx, y = dy),
                            )
                        }
                    }
                    Text(
                        text = "${digit.value}",
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = digitSp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            val hintLines = if (compactHints) 1 else 2
            Text(
                digit.hintRu,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = hintLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun CifrySandboxChainRowScaleToFit(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        if (maxW <= 0) {
            val p = subcompose("row") {
                Row(verticalAlignment = Alignment.CenterVertically, content = content)
            }.single().measure(constraints)
            layout(p.width, p.height) { p.place(0, 0) }
        } else {
            val loose = constraints.copy(maxWidth = Constraints.Infinity, minWidth = 0)
            val placeable = subcompose("row") {
                Row(verticalAlignment = Alignment.CenterVertically, content = content)
            }.single().measure(loose)
            val w = placeable.width
            val h = placeable.height
            val scale = if (w > maxW) {
                (maxW.toFloat() / w.toFloat()).coerceAtMost(1f)
            } else {
                1f
            }
            val scaledH = (h * scale).roundToInt()
            val y = ((maxH - scaledH).coerceAtLeast(0)) / 2
            val x = ((maxW - w * scale).coerceAtLeast(0f) / 2f).roundToInt()
            layout(maxW, maxH) {
                placeable.placeWithLayer(x, y) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

@Composable
private fun CifrySandboxToolbar(
    chainNotEmpty: Boolean,
    onClear: () -> Unit,
    onListen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClear, enabled = chainNotEmpty) {
            Text("Очистить", style = MaterialTheme.typography.labelLarge)
        }
        FilledTonalButton(
            onClick = onListen,
            enabled = chainNotEmpty,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text("Прослушать число", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CifrySandboxPaletteLazyGrid(
    modifier: Modifier = Modifier,
    columns: Int,
    tapSlopPx: Float,
    swipeUpPx: Float,
    workspaceCoordsState: State<LayoutCoordinates?>,
    paletteCoordsMap: MutableMap<Int, LayoutCoordinates>,
    paletteGhostDigit: Int?,
    onAddDigit: (Int) -> Unit,
    onSpeakPaletteDigit: (Int) -> Unit,
    onPaletteDragWindow: (Int?, Offset?) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(CifryRepository.DIGITS, key = { it.value }) { info ->
            CifrySandboxPaletteDigit(
                digit = info.value,
                tapSlopPx = tapSlopPx,
                swipeUpPx = swipeUpPx,
                workspaceCoordsState = workspaceCoordsState,
                paletteCoordsMap = paletteCoordsMap,
                onAddToChain = { onAddDigit(info.value) },
                onSpeakOnTap = { onSpeakPaletteDigit(info.value) },
                onPaletteDragWindow = onPaletteDragWindow,
                draggingThis = paletteGhostDigit == info.value,
            )
        }
    }
}

@Composable
private fun CifrySandboxLandscapePalette(
    columns: Int,
    tapSlopPx: Float,
    swipeUpPx: Float,
    workspaceCoordsState: State<LayoutCoordinates?>,
    paletteCoordsMap: MutableMap<Int, LayoutCoordinates>,
    paletteGhostDigit: Int?,
    onAddDigit: (Int) -> Unit,
    onSpeakPaletteDigit: (Int) -> Unit,
    onPaletteDragWindow: (Int?, Offset?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(columns) { CifryRepository.DIGITS.chunked(columns) }
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEach { rowDigits ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowDigits.forEach { info ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        CifrySandboxPaletteDigit(
                            digit = info.value,
                            tapSlopPx = tapSlopPx,
                            swipeUpPx = swipeUpPx,
                            workspaceCoordsState = workspaceCoordsState,
                            paletteCoordsMap = paletteCoordsMap,
                            onAddToChain = { onAddDigit(info.value) },
                            onSpeakOnTap = { onSpeakPaletteDigit(info.value) },
                            onPaletteDragWindow = onPaletteDragWindow,
                            draggingThis = paletteGhostDigit == info.value,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                repeat(columns - rowDigits.size) {
                    Spacer(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun CifrySandboxDropZoneBox(
    modifier: Modifier = Modifier,
    chain: SnapshotStateList<Int>,
    chainGhost: Triple<Int, Int, Offset>?,
    swapThresholdPx: Float,
    workspaceCoordsState: State<LayoutCoordinates?>,
    onWorkspaceCoords: (LayoutCoordinates) -> Unit,
    onChainReorderDrag: (Int, Int, Offset?) -> Unit,
    clearChainGhost: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                RoundedCornerShape(14.dp),
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .onGloballyPositioned { onWorkspaceCoords(it) },
    ) {
        if (chain.isEmpty()) {
            Text(
                "Перетащите цифры сюда",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        } else {
            CifrySandboxChainRowScaleToFit(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                chain.forEachIndexed { i, d ->
                    if (i > 0) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    CifrySandboxChainDigitChip(
                        digit = d,
                        index = i,
                        draggingReorder = chainGhost?.first == i,
                        onReorderDrag = { windowPos ->
                            onChainReorderDrag(i, d, windowPos)
                        },
                        onDragEnd = { deltaX, fingerWindow ->
                            clearChainGhost()
                            val wb = workspaceCoordsState.value?.boundsInWindow()
                            val outside =
                                fingerWindow != null && wb != null && !wb.contains(fingerWindow)
                            if (outside) {
                                if (i in chain.indices) chain.removeAt(i)
                            } else {
                                when {
                                    deltaX > swapThresholdPx && i < chain.lastIndex ->
                                        chain.swapAt(i, i + 1)
                                    deltaX < -swapThresholdPx && i > 0 ->
                                        chain.swapAt(i, i - 1)
                                }
                            }
                        },
                        onReorderDragCancel = { clearChainGhost() },
                    )
                }
            }
        }
    }
}

@Composable
private fun CifrySandboxDragGhostBox(
    digit: Int,
    localPos: Offset,
    density: Density,
    ghostSize: Dp = 72.dp,
    ghostLift: Dp = 36.dp,
) {
    val halfPx = with(density) { (ghostSize / 2).toPx() }
    val liftPx = with(density) { ghostLift.toPx() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (localPos.x - halfPx).roundToInt(),
                    (localPos.y - halfPx - liftPx).roundToInt(),
                )
            }
            .size(ghostSize)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f))
            .border(
                BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary),
                RoundedCornerShape(16.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$digit",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun CifrySandboxPaletteDigit(
    digit: Int,
    tapSlopPx: Float,
    swipeUpPx: Float,
    workspaceCoordsState: State<LayoutCoordinates?>,
    paletteCoordsMap: MutableMap<Int, LayoutCoordinates>,
    onAddToChain: () -> Unit,
    onSpeakOnTap: () -> Unit,
    onPaletteDragWindow: (Int?, Offset?) -> Unit,
    draggingThis: Boolean,
    modifier: Modifier = Modifier.size(56.dp),
) {
    val onSpeakOnTapState = rememberUpdatedState(onSpeakOnTap)
    Box(
        modifier = modifier
            .alpha(if (draggingThis) 0.35f else 1f)
            .onGloballyPositioned { paletteCoordsMap[digit] = it }
            .pointerInput(digit, tapSlopPx, swipeUpPx) {
                var dragStart = Offset.Zero
                var dragAccum = Offset.Zero
                detectDragGestures(
                    onDragStart = { off ->
                        dragStart = off
                        dragAccum = Offset.Zero
                        val pc = paletteCoordsMap[digit]
                        if (pc != null && pc.isAttached) {
                            onPaletteDragWindow(digit, pc.localToWindow(dragStart))
                        }
                    },
                    onDrag = { change, dragAmount ->
                        dragAccum += dragAmount
                        change.consume()
                        val pc = paletteCoordsMap[digit]
                        if (pc != null && pc.isAttached) {
                            onPaletteDragWindow(digit, pc.localToWindow(dragStart + dragAccum))
                        }
                    },
                    onDragEnd = {
                        onPaletteDragWindow(null, null)
                        val dist = hypot(dragAccum.x.toDouble(), dragAccum.y.toDouble()).toFloat()
                        val pc = paletteCoordsMap[digit]
                        val wb = workspaceCoordsState.value?.boundsInWindow()
                        val fingerWindow = pc?.localToWindow(dragStart + dragAccum)
                        when {
                            dist < tapSlopPx -> {
                                onSpeakOnTapState.value()
                                onAddToChain()
                            }
                            fingerWindow != null && wb != null && wb.contains(fingerWindow) ->
                                onAddToChain()
                            dragAccum.y < -swipeUpPx -> onAddToChain()
                            else -> { }
                        }
                    },
                    onDragCancel = {
                        onPaletteDragWindow(null, null)
                    },
                )
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = min(maxWidth.value, maxHeight.value)
            val corner = (side * 0.22f).dp.coerceIn(3.dp, 11.dp)
            val mainFs = (maxWidth.value * 0.52f).coerceIn(16f, 36f).sp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(corner))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                        RoundedCornerShape(corner),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$digit",
                    fontSize = mainFs,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CifrySandboxChainDigitChip(
    digit: Int,
    index: Int,
    draggingReorder: Boolean,
    onReorderDrag: (windowPos: Offset) -> Unit,
    onDragEnd: (deltaX: Float, fingerWindow: Offset?) -> Unit,
    onReorderDragCancel: () -> Unit,
) {
    var dragAreaCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val dragAreaCoordsState = rememberUpdatedState(dragAreaCoords)
    Card(
        modifier = Modifier
            .fillMaxHeight()
            .defaultMinSize(minWidth = 40.dp)
            .alpha(if (draggingReorder) 0.38f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { dragAreaCoords = it }
                .pointerInput(digit, index) {
                    var dragStart = Offset.Zero
                    var dragAccum = Offset.Zero
                    detectDragGestures(
                        onDragStart = { off ->
                            dragStart = off
                            dragAccum = Offset.Zero
                            val dc = dragAreaCoordsState.value
                            if (dc != null && dc.isAttached) {
                                onReorderDrag(dc.localToWindow(dragStart))
                            }
                        },
                        onDrag = { change, dragAmount ->
                            dragAccum += dragAmount
                            change.consume()
                            val dc = dragAreaCoordsState.value
                            if (dc != null && dc.isAttached) {
                                onReorderDrag(dc.localToWindow(dragStart + dragAccum))
                            }
                        },
                        onDragEnd = {
                            val dc = dragAreaCoordsState.value
                            val fingerWindow =
                                if (dc != null && dc.isAttached) {
                                    dc.localToWindow(dragStart + dragAccum)
                                } else {
                                    null
                                }
                            onDragEnd(dragAccum.x, fingerWindow)
                        },
                        onDragCancel = { onReorderDragCancel() },
                    )
                },
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                val side = min(maxWidth.value, maxHeight.value)
                val fontSp = (side * 0.52f).coerceIn(14f, 80f)
                Text(
                    "$digit",
                    fontSize = fontSp.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun CifrySandboxTab(
    speak: (String) -> Unit,
) {
    val sandboxVm = viewModel<CifrySandboxViewModel>(key = "cifry_sandbox_chain")
    val chain = sandboxVm.chain
    val paletteCoordsMap = remember { mutableStateMapOf<Int, LayoutCoordinates>() }
    var workspaceCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val workspaceCoordsState = rememberUpdatedState(workspaceCoords)
    var sandboxRootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val sandboxRootCoordsState = rememberUpdatedState(sandboxRootCoords)
    var paletteGhost by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    var chainGhost by remember { mutableStateOf<Triple<Int, Int, Offset>?>(null) }
    val density = LocalDensity.current
    val tapSlopPx = remember(density) { with(density) { 28.dp.toPx() } }
    val swipeUpPx = remember(density) { with(density) { 56.dp.toPx() } }
    val swapThresholdPx = remember(density) { with(density) { 48.dp.toPx() } }

    val onPaletteDragWindow: (Int?, Offset?) -> Unit = { d, windowPos ->
        val root = sandboxRootCoordsState.value
        paletteGhost = if (d != null && windowPos != null && root != null && root.isAttached) {
            chainGhost = null
            d to root.windowToLocal(windowPos)
        } else {
            null
        }
    }

    val onChainReorderDrag: (Int, Int, Offset?) -> Unit = { index, digit, windowPos ->
        val root = sandboxRootCoordsState.value
        chainGhost = if (windowPos != null && root != null && root.isAttached) {
            paletteGhost = null
            Triple(index, digit, root.windowToLocal(windowPos))
        } else {
            null
        }
    }

    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val paletteColumnsPortrait = 5
    val paletteColumnsLandscape = 5
    val playNumber: () -> Unit = {
        if (chain.isNotEmpty()) {
            val n = digitsChainToInt(chain)
            speak(n.toString())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { sandboxRootCoords = it },
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CifrySandboxLandscapePalette(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    columns = paletteColumnsLandscape,
                    tapSlopPx = tapSlopPx,
                    swipeUpPx = swipeUpPx,
                    workspaceCoordsState = workspaceCoordsState,
                    paletteCoordsMap = paletteCoordsMap,
                    paletteGhostDigit = paletteGhost?.first,
                    onAddDigit = { chain.add(it) },
                    onSpeakPaletteDigit = { speak(CifryRepository.nameForValue(it)) },
                    onPaletteDragWindow = onPaletteDragWindow,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    CifrySandboxToolbar(
                        chainNotEmpty = chain.isNotEmpty(),
                        onClear = { chain.clear() },
                        onListen = playNumber,
                    )
                    Spacer(Modifier.height(4.dp))
                    CifrySandboxDropZoneBox(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        chain = chain,
                        chainGhost = chainGhost,
                        swapThresholdPx = swapThresholdPx,
                        workspaceCoordsState = workspaceCoordsState,
                        onWorkspaceCoords = { workspaceCoords = it },
                        onChainReorderDrag = onChainReorderDrag,
                        clearChainGhost = { chainGhost = null },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
            ) {
                Text(
                    "Короткий тап — название цифры; в поле — число. «Прослушать число».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                CifrySandboxToolbar(
                    chainNotEmpty = chain.isNotEmpty(),
                    onClear = { chain.clear() },
                    onListen = playNumber,
                )
                Spacer(Modifier.height(4.dp))
                CifrySandboxDropZoneBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(108.dp),
                    chain = chain,
                    chainGhost = chainGhost,
                    swapThresholdPx = swapThresholdPx,
                    workspaceCoordsState = workspaceCoordsState,
                    onWorkspaceCoords = { workspaceCoords = it },
                    onChainReorderDrag = onChainReorderDrag,
                    clearChainGhost = { chainGhost = null },
                )
                Spacer(Modifier.height(4.dp))
                CifrySandboxPaletteLazyGrid(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    columns = paletteColumnsPortrait,
                    tapSlopPx = tapSlopPx,
                    swipeUpPx = swipeUpPx,
                    workspaceCoordsState = workspaceCoordsState,
                    paletteCoordsMap = paletteCoordsMap,
                    paletteGhostDigit = paletteGhost?.first,
                    onAddDigit = { chain.add(it) },
                    onSpeakPaletteDigit = { speak(CifryRepository.nameForValue(it)) },
                    onPaletteDragWindow = onPaletteDragWindow,
                )
            }
        }

        val pg = paletteGhost
        val cg = chainGhost
        when {
            pg != null -> {
                val (d, localPos) = pg
                CifrySandboxDragGhostBox(
                    digit = d,
                    localPos = localPos,
                    density = density,
                )
            }
            cg != null -> {
                val (_, d, localPos) = cg
                CifrySandboxDragGhostBox(
                    digit = d,
                    localPos = localPos,
                    density = density,
                    ghostLift = 22.dp,
                )
            }
        }
    }
}

private fun MutableList<Int>.swapAt(i: Int, j: Int) {
    if (i !in indices || j !in indices) return
    val t = this[i]
    this[i] = this[j]
    this[j] = t
}

@Composable
private fun CifryFindDigitGameTab(
    speak: (String) -> Unit,
    speakWhenDone: (String, () -> Unit) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
    isActive: Boolean,
) {
    if (!isActive) {
        Box(Modifier.fillMaxSize())
        return
    }
    val random = remember { Random(System.currentTimeMillis()) }
    var roundKey by remember { mutableIntStateOf(0) }
    val round = remember(roundKey) { nextFindDigitRound(random) }
    var tapFeedback by remember { mutableStateOf<DigitTapFeedback>(DigitTapFeedback.Idle) }

    LaunchedEffect(roundKey) {
        tapFeedback = DigitTapFeedback.Idle
        speak("Найди цифру ${round.target.nameRu}.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            "Найди цифру",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Послушай задание и нажми на нужную цифру.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { speak("Найди цифру ${round.target.nameRu}.") }) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 6.dp),
                )
                Text("Повторить задание")
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(round.choices, key = { "${it.value}_$roundKey" }) { digit ->
                val correctShown =
                    (tapFeedback as? DigitTapFeedback.Correct)?.value == digit.value
                val wrongShown =
                    (tapFeedback as? DigitTapFeedback.Wrong)?.value == digit.value
                val idle = tapFeedback is DigitTapFeedback.Idle
                CifryGameDigitChoiceCard(
                    digit = digit,
                    showCorrectCheck = correctShown,
                    showWrongX = wrongShown,
                    enabled = idle,
                    onClick = {
                        if (idle) {
                            if (digit.value == round.target.value) {
                                tapFeedback = DigitTapFeedback.Correct(digit.value)
                                val praise = CIFRY_GAME_CORRECT_PHRASES.random(random)
                                scope.launch { progressRepo.addPoints(8) }
                                speakWhenDone(praise) {
                                    roundKey++
                                }
                            } else {
                                tapFeedback = DigitTapFeedback.Wrong(digit.value)
                                speak(CIFRY_GAME_WRONG_PHRASES.random(random))
                                scope.launch {
                                    delay(1200L)
                                    tapFeedback = DigitTapFeedback.Idle
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CifryGameDigitChoiceCard(
    digit: DigitInfo,
    showCorrectCheck: Boolean,
    showWrongX: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val baseBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
    val resultBorder = when {
        showCorrectCheck -> Color(0xFF4CAF50)
        showWrongX -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    val resultBg = when {
        showCorrectCheck -> Color(0x224CAF50)
        showWrongX -> Color(0x22F44336)
        else -> baseBg
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = resultBg),
        border = BorderStroke(2.dp, resultBorder),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${digit.value}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (showCorrectCheck) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (showWrongX) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF44336)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CifryMathSection(
    speak: (String) -> Unit,
    speakWhenDone: (String, () -> Unit) -> Unit,
    progressRepo: AzbukaProgressRepository,
    mathRepo: CifryMathRepository,
    scope: CoroutineScope,
    isActive: Boolean,
) {
    if (!isActive) {
        Box(Modifier.fillMaxSize())
        return
    }
    val difficulty by mathRepo.difficulty.collectAsStateWithLifecycle(initialValue = 0)
    val history by mathRepo.solvedHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val subTabs = remember {
        listOf("Сложение", "Вычитание", "Умножение", "Деление", "Мои достижения")
    }
    val mathSectionPager = rememberPagerState(pageCount = { subTabs.size })
    val subScope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Text(
            "Сложность примеров",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val labels = listOf("Лёгкий", "Средний", "Трудный", "Эксперт")
            labels.forEachIndexed { i, label ->
                FilterChip(
                    selected = difficulty == i,
                    onClick = { subScope.launch { mathRepo.setDifficulty(i) } },
                    label = { Text(label) },
                )
            }
        }
        ScrollableTabRow(
            selectedTabIndex = mathSectionPager.currentPage,
            edgePadding = 4.dp,
            containerColor = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
        ) {
            subTabs.forEachIndexed { i, title ->
                Tab(
                    selected = mathSectionPager.currentPage == i,
                    onClick = { subScope.launch { mathSectionPager.animateScrollToPage(i) } },
                    modifier = Modifier.height(40.dp),
                    text = {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        HorizontalPager(
            state = mathSectionPager,
            beyondViewportPageCount = 0,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> Column(Modifier.fillMaxSize()) {
                    CifryMathOperandBoundsEditor(
                        mode = CifryMathMode.PLUS,
                        difficulty = difficulty,
                        mathRepo = mathRepo,
                        scope = subScope,
                    )
                    CifryMathPracticePage(
                        mode = CifryMathMode.PLUS,
                        difficulty = difficulty,
                        mathPagerState = mathSectionPager,
                        mathPagerPageIndex = page,
                        speak = speak,
                        speakWhenDone = speakWhenDone,
                        progressRepo = progressRepo,
                        mathRepo = mathRepo,
                        scope = scope,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
                1 -> Column(Modifier.fillMaxSize()) {
                    CifryMathOperandBoundsEditor(
                        mode = CifryMathMode.MINUS,
                        difficulty = difficulty,
                        mathRepo = mathRepo,
                        scope = subScope,
                    )
                    CifryMathPracticePage(
                        mode = CifryMathMode.MINUS,
                        difficulty = difficulty,
                        mathPagerState = mathSectionPager,
                        mathPagerPageIndex = page,
                        speak = speak,
                        speakWhenDone = speakWhenDone,
                        progressRepo = progressRepo,
                        mathRepo = mathRepo,
                        scope = scope,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
                2 -> Column(Modifier.fillMaxSize()) {
                    CifryMathOperandBoundsEditor(
                        mode = CifryMathMode.MULT,
                        difficulty = difficulty,
                        mathRepo = mathRepo,
                        scope = subScope,
                    )
                    CifryMathPracticePage(
                        mode = CifryMathMode.MULT,
                        difficulty = difficulty,
                        mathPagerState = mathSectionPager,
                        mathPagerPageIndex = page,
                        speak = speak,
                        speakWhenDone = speakWhenDone,
                        progressRepo = progressRepo,
                        mathRepo = mathRepo,
                        scope = scope,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
                3 -> Column(Modifier.fillMaxSize()) {
                    CifryMathOperandBoundsEditor(
                        mode = CifryMathMode.DIV,
                        difficulty = difficulty,
                        mathRepo = mathRepo,
                        scope = subScope,
                    )
                    CifryMathPracticePage(
                        mode = CifryMathMode.DIV,
                        difficulty = difficulty,
                        mathPagerState = mathSectionPager,
                        mathPagerPageIndex = page,
                        speak = speak,
                        speakWhenDone = speakWhenDone,
                        progressRepo = progressRepo,
                        mathRepo = mathRepo,
                        scope = scope,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
                else -> CifryMathAchievementsTab(history = history)
            }
        }
    }
}

private fun cifryMathEditorCaps(mode: CifryMathMode, difficulty: Int): Pair<Int, Int> {
    val diff = difficulty.coerceIn(0, 3)
    val maxFirst = when (mode) {
        CifryMathMode.PLUS,
        CifryMathMode.MINUS,
        -> cifryMathMaxOperand(diff)
        CifryMathMode.MULT -> cifryMathMultOperandCap(diff)
        CifryMathMode.DIV -> cifryMathMaxOperand(diff)
    }
    val maxSecond = when (mode) {
        CifryMathMode.PLUS,
        CifryMathMode.MINUS,
        -> cifryMathMaxOperand(diff)
        CifryMathMode.MULT -> cifryMathMultOperandCap(diff)
        CifryMathMode.DIV -> minOf(cifryMathDivisorCap(diff), cifryMathMaxOperand(diff)).coerceAtLeast(2)
    }
    return maxFirst to maxSecond
}

@Composable
private fun CifryMathOperandBoundsEditor(
    mode: CifryMathMode,
    difficulty: Int,
    mathRepo: CifryMathRepository,
    scope: CoroutineScope,
) {
    val (capA, capB) = remember(mode, difficulty) { cifryMathEditorCaps(mode, difficulty) }
    val operandBounds by mathRepo.operandBoundsFor(mode).collectAsStateWithLifecycle(initialValue = OperandBoundsMode.Auto)
    val useCustom = operandBounds is OperandBoundsMode.Custom

    var draftMinA by remember(mode, difficulty, capA, capB) { mutableFloatStateOf(0f) }
    var draftMaxA by remember(mode, difficulty, capA, capB) { mutableFloatStateOf(capA.toFloat()) }
    val bLowDefault = if (mode == CifryMathMode.DIV) 1f else 0f
    var draftMinB by remember(mode, difficulty, capA, capB) { mutableFloatStateOf(bLowDefault) }
    var draftMaxB by remember(mode, difficulty, capA, capB) { mutableFloatStateOf(capB.toFloat()) }

    LaunchedEffect(operandBounds, capA, capB, mode) {
        val obs = operandBounds
        when (obs) {
            OperandBoundsMode.Auto -> {
                draftMinA = 0f
                draftMaxA = capA.toFloat()
                draftMinB = if (mode == CifryMathMode.DIV) 1f else 0f
                draftMaxB = capB.toFloat()
            }
            is OperandBoundsMode.Custom -> {
                draftMinA = obs.minA.toFloat().coerceIn(0f, capA.toFloat())
                draftMaxA = obs.maxA.toFloat().coerceIn(0f, capA.toFloat())
                draftMinB = obs.minB.toFloat().coerceIn(bLowDefault, capB.toFloat())
                draftMaxB = obs.maxB.toFloat().coerceIn(bLowDefault, capB.toFloat())
            }
        }
        if (draftMaxA < draftMinA) draftMaxA = draftMinA
        if (draftMaxB < draftMinB) draftMaxB = draftMinB
    }

    fun pushBounds() {
        scope.launch {
            val naLo = min(draftMinA, draftMaxA).roundToInt()
            val naHi = max(draftMinA, draftMaxA).roundToInt()
            val nbLo = min(draftMinB, draftMaxB).roundToInt()
            val nbHi = max(draftMinB, draftMaxB).roundToInt()
            val c = OperandBoundsMode.Custom(naLo, naHi, nbLo, nbHi).normalized(mode, difficulty)
            mathRepo.setOperandBounds(mode, c)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("Свои числа в примерах", style = MaterialTheme.typography.titleSmall)
            Text(
                when (mode) {
                    CifryMathMode.PLUS -> "Первое и второе слагаемое (каждое до $capA)."
                    CifryMathMode.MINUS -> "Уменьшаемое до $capA; второе число — вычитаемое (не больше первого)."
                    CifryMathMode.MULT -> "Первый и второй множители (каждый до $capA)."
                    CifryMathMode.DIV -> "Первое число — делимое (до $capA), второе — делитель от 1 до $capB; ответ целый."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Задать диапазоны", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = useCustom,
                    onCheckedChange = { on ->
                        scope.launch {
                            if (on) {
                                val c = OperandBoundsMode.Custom(
                                    0,
                                    capA,
                                    if (mode == CifryMathMode.DIV) 1 else 0,
                                    capB,
                                ).normalized(mode, difficulty)
                                mathRepo.setOperandBounds(mode, c)
                            } else {
                                mathRepo.setOperandBounds(mode, OperandBoundsMode.Auto)
                            }
                        }
                    },
                )
            }
            if (useCustom) {
                val bLow = if (mode == CifryMathMode.DIV) 1f else 0f
                Text(
                    "Первое число: от ${draftMinA.roundToInt()} до ${draftMaxA.roundToInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Slider(
                    value = draftMinA,
                    onValueChange = { v -> draftMinA = v.coerceIn(0f, capA.toFloat()) },
                    onValueChangeFinished = { pushBounds() },
                    valueRange = 0f..capA.toFloat(),
                )
                Slider(
                    value = draftMaxA,
                    onValueChange = { v -> draftMaxA = v.coerceIn(0f, capA.toFloat()) },
                    onValueChangeFinished = { pushBounds() },
                    valueRange = 0f..capA.toFloat(),
                )
                Text(
                    "Второе число: от ${draftMinB.roundToInt()} до ${draftMaxB.roundToInt()}",
                    style = MaterialTheme.typography.labelSmall,
                )
                Slider(
                    value = draftMinB,
                    onValueChange = { v -> draftMinB = v.coerceIn(bLow, capB.toFloat()) },
                    onValueChangeFinished = { pushBounds() },
                    valueRange = bLow..capB.toFloat(),
                )
                Slider(
                    value = draftMaxB,
                    onValueChange = { v -> draftMaxB = v.coerceIn(bLow, capB.toFloat()) },
                    onValueChangeFinished = { pushBounds() },
                    valueRange = bLow..capB.toFloat(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MathVisualGroups(
    theme: MathVisualTheme,
    firstCount: Int,
    secondCount: Int,
    isPlus: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Считай по картинкам: ${theme.nameRuPlural}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(firstCount.coerceAtMost(30)) {
                    Text(theme.emoji, fontSize = 24.sp)
                }
            }
            Text(
                if (isPlus) "+" else "−",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(secondCount.coerceAtMost(30)) {
                    Text(theme.emoji, fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun CifryMathPracticePage(
    mode: CifryMathMode,
    difficulty: Int,
    mathPagerState: PagerState,
    mathPagerPageIndex: Int,
    speak: (String) -> Unit,
    speakWhenDone: (String, () -> Unit) -> Unit,
    progressRepo: AzbukaProgressRepository,
    mathRepo: CifryMathRepository,
    scope: CoroutineScope,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val random = remember { Random(System.currentTimeMillis()) }
    var genKey by remember { mutableIntStateOf(0) }
    val operandBounds by mathRepo.operandBoundsFor(mode).collectAsStateWithLifecycle(initialValue = OperandBoundsMode.Auto)
    val boundsRef = rememberUpdatedState(operandBounds)
    var boundsSnapshot by remember(mode, mathPagerPageIndex) {
        mutableStateOf(operandBounds)
    }
    LaunchedEffect(genKey, difficulty) {
        boundsSnapshot = boundsRef.value
    }
    val problem = remember(genKey, mode, difficulty, boundsSnapshot) {
        nextCifryMathProblem(mode, difficulty, Random(System.nanoTime()), boundsSnapshot)
    }
    val choices = remember(problem) { buildMathChoices(problem, Random(System.nanoTime())) }
    var feedback by remember { mutableStateOf<Int?>(null) }

    // Соседние страницы HorizontalPager могут оставаться в композиции — озвучиваем только выбранную вкладку.
    LaunchedEffect(genKey, difficulty, mathPagerState.currentPage, mathPagerPageIndex) {
        if (mathPagerState.currentPage != mathPagerPageIndex) return@LaunchedEffect
        feedback = null
        speak(problem.promptRu())
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val theme = problem.visualTheme
        if (theme != null && (mode == CifryMathMode.PLUS || mode == CifryMathMode.MINUS)) {
            MathVisualGroups(
                theme = theme,
                firstCount = problem.a,
                secondCount = problem.b,
                isPlus = mode == CifryMathMode.PLUS,
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            problem.promptShortRu(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { speak(problem.promptRu()) }) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Прослушать задание")
        }
        Spacer(Modifier.height(12.dp))
        choices.forEach { opt ->
            val isCorrect = opt == problem.result
            val showOk = feedback != null && isCorrect
            val showBad = feedback == opt && !isCorrect
            val border = when {
                showOk -> Color(0xFF4CAF50)
                showBad -> Color(0xFFF44336)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            }
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable(enabled = feedback == null) {
                        feedback = opt
                        if (opt == problem.result) {
                            val praise = CIFRY_MATH_CORRECT.random(random)
                            scope.launch {
                                mathRepo.appendSolved(
                                    CifryMathSolvedEntry(
                                        timestampMs = System.currentTimeMillis(),
                                        mode = problem.mode,
                                        a = problem.a,
                                        b = problem.b,
                                        result = problem.result,
                                        difficulty = difficulty,
                                        visualEmoji = problem.visualTheme?.emoji,
                                        expressionText = problem.expressionWithResultRu(),
                                    ),
                                )
                                progressRepo.addPoints(10)
                            }
                            speakWhenDone(praise) {
                                genKey++
                            }
                        } else {
                            speak(CIFRY_MATH_WRONG.random(random))
                            scope.launch {
                                delay(1400L)
                                feedback = null
                            }
                        }
                    },
                border = BorderStroke(2.dp, border),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "$opt",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (showOk) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50))
                    } else if (showBad) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFF44336))
                    }
                }
            }
        }
    }
}

private fun cifryMathModeLabel(mode: CifryMathMode): String = when (mode) {
    CifryMathMode.PLUS -> "Сложение"
    CifryMathMode.MINUS -> "Вычитание"
    CifryMathMode.MULT -> "Умножение"
    CifryMathMode.DIV -> "Деление"
}

@Composable
private fun CifryMathAchievementsTab(history: List<CifryMathSolvedEntry>) {
    val dateFmt = remember {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru-RU"))
    }
    if (history.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Пока нет решённых примеров.\nРеши задания во вкладках сложения, вычитания, умножения или деления — они появятся здесь.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Все верно решённые примеры (новые сверху).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(history, key = { "${it.timestampMs}_${it.expressionText}" }) { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        entry.expressionText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${cifryMathModeLabel(entry.mode)} · уровень ${entry.difficulty + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entry.visualEmoji != null) {
                        Text(entry.visualEmoji, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text(
                        dateFmt.format(Date(entry.timestampMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
