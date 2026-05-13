package com.example.bible.ui

import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.R
import com.example.bible.data.AzbukaExercise
import com.example.bible.data.AzbukaLesson
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.AzbukaRepository
import com.example.bible.data.BibleLibrary
import com.example.bible.data.BibleWordGamePool
import com.example.bible.data.LetterType
import com.example.bible.data.RussianLetter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
private fun rememberAzbukaCursiveFont(): FontFamily {
    return remember {
        FontFamily(Font(R.font.bad_script, FontWeight.Normal))
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AzbukaScreen(
    onBack: () -> Unit,
    library: BibleLibrary,
) {
    val tabs = listOf("Алфавит", "Уроки", "Игра", "Правила", "Песочница")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val cursiveFont = rememberAzbukaCursiveFont()
    val context = LocalContext.current
    val progressRepo = remember { AzbukaProgressRepository(context) }
    val ttsUtteranceCallbacks = remember { ConcurrentHashMap<String, () -> Unit>() }
    val tts = remember {
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
                    // Prefer female voice: name hints containing "female", "a" suffix patterns,
                    // known Google TTS female voice names
                    val femaleKeywords = listOf("female", "woman", "девуш", "жен", "алёна", "алена",
                        "elena", "svetlana", "anna", "olga", "maria", "tatiana", "natalia", "irina")

                    val femaleVoice = ruVoices
                        .sortedByDescending { it.quality }
                        .firstOrNull { voice ->
                            val n = voice.name.lowercase()
                            femaleKeywords.any { kw -> n.contains(kw) }
                        }

                    // If no explicitly female voice found, pick highest quality;
                    // Google TTS ru-RU default is typically female
                    val bestVoice = femaleVoice
                        ?: ruVoices.sortedWith(
                            compareByDescending<android.speech.tts.Voice> { it.quality }
                                .thenBy { it.latency }
                        ).firstOrNull()

                    if (bestVoice != null) {
                        e.voice = bestVoice
                        android.util.Log.d("AzbukaTTS", "Selected voice: ${bestVoice.name}, quality=${bestVoice.quality}")
                    }
                }

                // Slightly slow and warm — pleasant female tutor style
                e.setSpeechRate(0.9f)
                e.setPitch(1.15f)
            }
        }
        engine
    }
    DisposableEffect(Unit) {
        onDispose { tts?.shutdown() }
    }
    val speak: (String) -> Unit = { text ->
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "azbuka_${System.nanoTime()}")
    }

    /** Озвучка с вызовом [onDone] после окончания фразы (или при ошибке движка). */
    val speakWhenDone: (String, () -> Unit) -> Unit = { text, onDone ->
        val engine = tts
        if (engine == null) {
            onDone()
        } else {
            val id = "azbuka_done_${System.nanoTime()}"
            ttsUtteranceCallbacks[id] = onDone
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (result == TextToSpeech.ERROR) {
                ttsUtteranceCallbacks.remove(id)
                onDone()
            }
        }
    }

    /** Сначала название буквы, пауза 1 с, затем слово-пример (отдельное произнесение). */
    var pendingLetterExampleJob by remember { mutableStateOf<Job?>(null) }
    val speakLetterThenExample: (String, String) -> Unit = { letterName, exampleWord ->
        pendingLetterExampleJob?.cancel()
        tts?.speak(letterName, TextToSpeech.QUEUE_FLUSH, null, "azbuka_letter_${System.nanoTime()}")
        pendingLetterExampleJob = scope.launch {
            delay(1_000L)
            tts?.speak(exampleWord, TextToSpeech.QUEUE_FLUSH, null, "azbuka_ex_${System.nanoTime()}")
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Азбука", style = MaterialTheme.typography.titleLarge) },
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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> AlphabetTab(
                        speak = speak,
                        speakLetterThenExample = speakLetterThenExample,
                        cursiveFont = cursiveFont,
                        progressRepo = progressRepo,
                    )
                    1 -> LessonsTab(
                        speak = speak,
                        speakLetterThenExample = speakLetterThenExample,
                        progressRepo = progressRepo,
                        scope = scope,
                    )
                    2 -> QuizTab(
                        speak = speak,
                        speakWhenDone = speakWhenDone,
                        progressRepo = progressRepo,
                        scope = scope,
                        library = library,
                    )
                    3 -> RulesTab(speak = speak)
                    4 -> SandboxTab(speak = speak)
                }
            }
        }
    }
}

// ==================== TAB 1: ALPHABET ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlphabetTab(
    speak: (String) -> Unit,
    speakLetterThenExample: (String, String) -> Unit,
    cursiveFont: FontFamily,
    progressRepo: AzbukaProgressRepository,
) {
    var selectedLetter by remember { mutableStateOf<RussianLetter?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(AzbukaRepository.ALPHABET) { index, letter ->
            var appeared by remember { mutableStateOf(false) }
            LaunchedEffect(index) {
                delay(index * 16L)
                appeared = true
            }
            AnimatedVisibility(
                visible = appeared,
                enter = fadeIn(tween(260)) + scaleIn(
                    initialScale = 0.82f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ),
            ) {
                LetterCard(
                    letter = letter,
                    cursiveFont = cursiveFont,
                    onClick = {
                        selectedLetter = letter
                        speakLetterThenExample(letter.name, letter.exampleWord)
                    },
                )
            }
        }
    }

    selectedLetter?.let { letter ->
        LetterDetailSheet(
            letter = letter,
            speak = speak,
            speakLetterThenExample = speakLetterThenExample,
            cursiveFont = cursiveFont,
            progressRepo = progressRepo,
            onDismiss = { selectedLetter = null },
        )
    }
}

// ==================== TAB: ПЕСОЧНИЦА ====================

/** Уменьшает цепочку букв по ширине поля, если иначе не помещается (без горизонтальной прокрутки). */
@Composable
private fun SandboxChainRowScaleToFit(
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
private fun SandboxPaletteLazyGrid(
    modifier: Modifier = Modifier,
    columns: Int,
    tapSlopPx: Float,
    swipeUpPx: Float,
    workspaceCoordsState: State<LayoutCoordinates?>,
    paletteCoordsMap: MutableMap<RussianLetter, LayoutCoordinates>,
    paletteGhostLetter: RussianLetter?,
    onAddLetter: (RussianLetter) -> Unit,
    onSpeakPaletteLetter: (RussianLetter) -> Unit,
    onPaletteDragWindow: (RussianLetter?, Offset?) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(AzbukaRepository.ALPHABET) { letter ->
            SandboxPaletteLetter(
                letter = letter,
                tapSlopPx = tapSlopPx,
                swipeUpPx = swipeUpPx,
                workspaceCoordsState = workspaceCoordsState,
                paletteCoordsMap = paletteCoordsMap,
                onAddToChain = { onAddLetter(letter) },
                onSpeakOnTap = { onSpeakPaletteLetter(letter) },
                onPaletteDragWindow = onPaletteDragWindow,
                draggingThis = paletteGhostLetter == letter,
            )
        }
    }
}

/** Альбом: все буквы видны без прокрутки — равные строки/столбцы по доступной высоте. */
@Composable
private fun SandboxLandscapePalette(
    columns: Int,
    tapSlopPx: Float,
    swipeUpPx: Float,
    workspaceCoordsState: State<LayoutCoordinates?>,
    paletteCoordsMap: MutableMap<RussianLetter, LayoutCoordinates>,
    paletteGhostLetter: RussianLetter?,
    onAddLetter: (RussianLetter) -> Unit,
    onSpeakPaletteLetter: (RussianLetter) -> Unit,
    onPaletteDragWindow: (RussianLetter?, Offset?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(columns) { AzbukaRepository.ALPHABET.chunked(columns) }
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEach { rowLetters ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowLetters.forEach { letter ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        SandboxPaletteLetter(
                            letter = letter,
                            tapSlopPx = tapSlopPx,
                            swipeUpPx = swipeUpPx,
                            workspaceCoordsState = workspaceCoordsState,
                            paletteCoordsMap = paletteCoordsMap,
                            onAddToChain = { onAddLetter(letter) },
                            onSpeakOnTap = { onSpeakPaletteLetter(letter) },
                            onPaletteDragWindow = onPaletteDragWindow,
                            draggingThis = paletteGhostLetter == letter,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                repeat(columns - rowLetters.size) {
                    Spacer(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun SandboxDropZoneBox(
    modifier: Modifier = Modifier,
    chain: SnapshotStateList<RussianLetter>,
    chainGhost: Triple<Int, RussianLetter, Offset>?,
    swapThresholdPx: Float,
    workspaceCoordsState: State<LayoutCoordinates?>,
    onWorkspaceCoords: (LayoutCoordinates) -> Unit,
    onChainReorderDrag: (Int, RussianLetter, Offset?) -> Unit,
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
                "Перетащите буквы сюда",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        } else {
            SandboxChainRowScaleToFit(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                chain.forEachIndexed { i, letter ->
                    if (i > 0) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    SandboxChainChip(
                        letter = letter,
                        index = i,
                        draggingReorder = chainGhost?.first == i,
                        onReorderDrag = { windowPos ->
                            onChainReorderDrag(i, letter, windowPos)
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
private fun SandboxToolbar(
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
            Text("Прослушать", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Цепочка букв в песочнице: хранится в ViewModel, чтобы не терялась при смене конфигурации (поворот).
 *
 * Не `private`: иначе [ViewModelProvider] из androidx не может создать экземпляр (другой пакет) — падение при открытии вкладки.
 */
internal class AzbukaSandboxViewModel : ViewModel() {
    val chain = mutableStateListOf<RussianLetter>()
}

@Composable
private fun SandboxTab(
    speak: (String) -> Unit,
) {
    val sandboxVm = viewModel<AzbukaSandboxViewModel>(key = "azbuka_sandbox_chain")
    val chain = sandboxVm.chain
    val paletteCoordsMap = remember { mutableStateMapOf<RussianLetter, LayoutCoordinates>() }
    var workspaceCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val workspaceCoordsState = rememberUpdatedState(workspaceCoords)
    var sandboxRootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val sandboxRootCoordsState = rememberUpdatedState(sandboxRootCoords)
    var paletteGhost by remember { mutableStateOf<Pair<RussianLetter, Offset>?>(null) }
    var chainGhost by remember { mutableStateOf<Triple<Int, RussianLetter, Offset>?>(null) }
    val density = LocalDensity.current
    val tapSlopPx = remember(density) { with(density) { 28.dp.toPx() } }
    val swipeUpPx = remember(density) { with(density) { 56.dp.toPx() } }
    val swapThresholdPx = remember(density) { with(density) { 48.dp.toPx() } }

    val onPaletteDragWindow: (RussianLetter?, Offset?) -> Unit = { letter, windowPos ->
        val root = sandboxRootCoordsState.value
        paletteGhost = if (letter != null && windowPos != null && root != null && root.isAttached) {
            chainGhost = null
            letter to root.windowToLocal(windowPos)
        } else {
            null
        }
    }

    val onChainReorderDrag: (Int, RussianLetter, Offset?) -> Unit = { index, letter, windowPos ->
        val root = sandboxRootCoordsState.value
        chainGhost = if (windowPos != null && root != null && root.isAttached) {
            paletteGhost = null
            Triple(index, letter, root.windowToLocal(windowPos))
        } else {
            null
        }
    }

    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    /** Портрет: сетка с прокруткой. Альбом: сетка без прокрутки (7 колонок, 5 рядов на 33 буквы). */
    val paletteColumnsPortrait = 6
    val paletteColumnsLandscape = 7
    val playChain: () -> Unit = {
        if (chain.isNotEmpty()) {
            speak(chain.joinToString("") { it.lower.toString() })
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
                SandboxLandscapePalette(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    columns = paletteColumnsLandscape,
                    tapSlopPx = tapSlopPx,
                    swipeUpPx = swipeUpPx,
                    workspaceCoordsState = workspaceCoordsState,
                    paletteCoordsMap = paletteCoordsMap,
                    paletteGhostLetter = paletteGhost?.first,
                    onAddLetter = { chain.add(it) },
                    onSpeakPaletteLetter = { speak(it.name) },
                    onPaletteDragWindow = onPaletteDragWindow,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    SandboxToolbar(
                        chainNotEmpty = chain.isNotEmpty(),
                        onClear = { chain.clear() },
                        onListen = playChain,
                    )
                    Spacer(Modifier.height(4.dp))
                    SandboxDropZoneBox(
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
                SandboxToolbar(
                    chainNotEmpty = chain.isNotEmpty(),
                    onClear = { chain.clear() },
                    onListen = playChain,
                )
                Spacer(Modifier.height(4.dp))
                SandboxDropZoneBox(
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
                SandboxPaletteLazyGrid(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    columns = paletteColumnsPortrait,
                    tapSlopPx = tapSlopPx,
                    swipeUpPx = swipeUpPx,
                    workspaceCoordsState = workspaceCoordsState,
                    paletteCoordsMap = paletteCoordsMap,
                    paletteGhostLetter = paletteGhost?.first,
                    onAddLetter = { chain.add(it) },
                    onSpeakPaletteLetter = { speak(it.name) },
                    onPaletteDragWindow = onPaletteDragWindow,
                )
            }
        }

        val pg = paletteGhost
        val cg = chainGhost
        when {
            pg != null -> {
                val (letter, localPos) = pg
                SandboxDragGhostBox(
                    letter = letter,
                    localPos = localPos,
                    density = density,
                    ghostLift = 40.dp,
                )
            }
            cg != null -> {
                val (_, letter, localPos) = cg
                SandboxDragGhostBox(
                    letter = letter,
                    localPos = localPos,
                    density = density,
                    ghostLift = 22.dp,
                )
            }
        }
    }
}

@Composable
private fun SandboxDragGhostBox(
    letter: RussianLetter,
    localPos: Offset,
    density: Density,
    ghostSize: Dp = 80.dp,
    ghostLift: Dp = 40.dp,
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
            .background(
                when (letter.type) {
                    LetterType.VOWEL -> Color(0xEEF44336)
                    LetterType.CONSONANT -> Color(0xEE2196F3)
                    LetterType.SIGN -> Color(0xEE9E9E9E)
                },
            )
            .border(
                BorderStroke(2.5.dp, MaterialTheme.colorScheme.onSurface),
                RoundedCornerShape(16.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${letter.upper}${letter.lower}",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun <T> MutableList<T>.swapAt(i: Int, j: Int) {
    if (i !in indices || j !in indices) return
    val t = this[i]
    this[i] = this[j]
    this[j] = t
}

@Composable
private fun SandboxPaletteLetter(
    letter: RussianLetter,
    tapSlopPx: Float,
    swipeUpPx: Float,
    workspaceCoordsState: State<LayoutCoordinates?>,
    paletteCoordsMap: MutableMap<RussianLetter, LayoutCoordinates>,
    onAddToChain: () -> Unit,
    onSpeakOnTap: () -> Unit,
    onPaletteDragWindow: (RussianLetter?, Offset?) -> Unit,
    draggingThis: Boolean,
    modifier: Modifier = Modifier.size(50.dp),
) {
    val onSpeakOnTapState = rememberUpdatedState(onSpeakOnTap)
    val bgColor = when (letter.type) {
        LetterType.VOWEL -> Color(0x33F44336)
        LetterType.CONSONANT -> Color(0x332196F3)
        LetterType.SIGN -> Color(0x339E9E9E)
    }
    val borderColor = when (letter.type) {
        LetterType.VOWEL -> Color(0xFFF44336)
        LetterType.CONSONANT -> Color(0xFF2196F3)
        LetterType.SIGN -> Color(0xFF9E9E9E)
    }
    Box(
        modifier = modifier
            .alpha(if (draggingThis) 0.35f else 1f)
            .onGloballyPositioned { paletteCoordsMap[letter] = it }
            .pointerInput(letter, tapSlopPx, swipeUpPx) {
                var dragStart = Offset.Zero
                var dragAccum = Offset.Zero
                detectDragGestures(
                    onDragStart = { off ->
                        dragStart = off
                        dragAccum = Offset.Zero
                        val pc = paletteCoordsMap[letter]
                        if (pc != null && pc.isAttached) {
                            onPaletteDragWindow(letter, pc.localToWindow(dragStart))
                        }
                    },
                    onDrag = { change, dragAmount ->
                        dragAccum += dragAmount
                        change.consume()
                        val pc = paletteCoordsMap[letter]
                        if (pc != null && pc.isAttached) {
                            onPaletteDragWindow(letter, pc.localToWindow(dragStart + dragAccum))
                        }
                    },
                    onDragEnd = {
                        onPaletteDragWindow(null, null)
                        val dist = hypot(dragAccum.x.toDouble(), dragAccum.y.toDouble()).toFloat()
                        val pc = paletteCoordsMap[letter]
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
            val mainFs = (maxWidth.value * 0.38f).coerceIn(7f, 17f).sp
            val emojiFs = (maxWidth.value * 0.12f).coerceIn(3f, 8f).sp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(corner))
                    .background(bgColor)
                    .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(corner)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "${letter.upper}${letter.lower}",
                        fontSize = mainFs,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(letter.type.emoji, fontSize = emojiFs, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SandboxChainChip(
    letter: RussianLetter,
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
            .defaultMinSize(minWidth = 44.dp)
            .alpha(if (draggingReorder) 0.38f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = when (letter.type) {
                LetterType.VOWEL -> Color(0x44F44336)
                LetterType.CONSONANT -> Color(0x442196F3)
                LetterType.SIGN -> Color(0x449E9E9E)
            },
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { dragAreaCoords = it }
                .pointerInput(letter, index) {
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
                    "${letter.upper}${letter.lower}",
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
private fun LetterCard(
    letter: RussianLetter,
    cursiveFont: FontFamily,
    onClick: () -> Unit,
) {
    val bgColor = when (letter.type) {
        LetterType.VOWEL -> Color(0x33F44336)
        LetterType.CONSONANT -> Color(0x332196F3)
        LetterType.SIGN -> Color(0x339E9E9E)
    }
    val borderColor = when (letter.type) {
        LetterType.VOWEL -> Color(0xFFF44336)
        LetterType.CONSONANT -> Color(0xFF2196F3)
        LetterType.SIGN -> Color(0xFF9E9E9E)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "letter_scale",
    )
    Card(
        modifier = Modifier
            .size(width = 62.dp, height = 82.dp)
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${letter.upper}${letter.lower}",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp,
                )
                Text(
                    "${letter.upper}${letter.lower}",
                    fontSize = 17.sp,
                    fontFamily = cursiveFont,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LetterDetailSheet(
    letter: RussianLetter,
    speak: (String) -> Unit,
    speakLetterThenExample: (String, String) -> Unit,
    cursiveFont: FontFamily,
    progressRepo: AzbukaProgressRepository,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(letter) {
        progressRepo.tryRegisterLetterOpened(letter.upper)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Печатные",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
            Text(
                "${letter.upper} ${letter.lower}",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { speakLetterThenExample(letter.name, letter.exampleWord) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Пропись",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
            Text(
                "${letter.upper} ${letter.lower}",
                fontSize = 48.sp,
                fontFamily = cursiveFont,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { speakLetterThenExample(letter.name, letter.exampleWord) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Слово на эту букву",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            )
            Text(
                letter.exampleWord,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { speak(letter.exampleWord) }
                    .padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ==================== TAB 2: LESSONS ====================

@Composable
private fun LessonsTab(
    speak: (String) -> Unit,
    speakLetterThenExample: (String, String) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
) {
    var openLesson by remember { mutableStateOf<AzbukaLesson?>(null) }

    if (openLesson != null) {
        LessonScreen(
            lesson = openLesson!!,
            speak = speak,
            speakLetterThenExample = speakLetterThenExample,
            progressRepo = progressRepo,
            scope = scope,
            onBack = { openLesson = null },
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(AzbukaRepository.LESSONS) { index, lesson ->
                LessonCard(index = index, lesson = lesson, onClick = { openLesson = lesson })
            }
        }
    }
}

@Composable
private fun LessonCard(index: Int, lesson: AzbukaLesson, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    lesson.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    lesson.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        "${lesson.letters.size} букв",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${lesson.syllables.size} слогов",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${lesson.exercises.size} заданий",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonScreen(
    lesson: AzbukaLesson,
    speak: (String) -> Unit,
    speakLetterThenExample: (String, String) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    // 0 = letters, 1 = syllables, 2 = words, 3 = exercises
    val totalSteps = 4

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (step > 0) step-- else onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Урок ${lesson.id}: ${lesson.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    listOf("Буквы", "Слоги", "Слова", "Задания")[step],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "${step + 1}/$totalSteps",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { (step + 1f) / totalSteps },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            when (step) {
                0 -> {
                    Text("Знакомимся с буквами", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    lesson.letters.forEach { letter ->
                        LessonLetterRow(
                            letter = letter,
                            speak = speak,
                            speakLetterThenExample = speakLetterThenExample,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                1 -> {
                    Text("Читаем слоги", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Нажимай на слог, чтобы услышать произношение",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (lesson.syllables.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            lesson.syllables.forEach { syl ->
                                SyllableChip(syllable = syl, onClick = { speak(syl) })
                            }
                        }
                    } else {
                        Text(
                            "В этом уроке нет слогов — только знаки.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                2 -> {
                    Text("Читаем слова", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    lesson.words.forEach { (word, meaning) ->
                        WordCard(word = word, meaning = meaning, speak = speak)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                3 -> {
                    Text("Проверим знания!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    ExerciseBlock(
                        exercises = lesson.exercises,
                        speak = speak,
                        progressRepo = progressRepo,
                        scope = scope,
                        onComplete = { onBack() },
                    )
                }
            }
        }

        if (step < totalSteps - 1) {
            Button(
                onClick = { step++ },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Далее")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun LessonLetterRow(
    letter: RussianLetter,
    speak: (String) -> Unit,
    speakLetterThenExample: (String, String) -> Unit,
) {
    val bgColor = when (letter.type) {
        LetterType.VOWEL -> Color(0x22F44336)
        LetterType.CONSONANT -> Color(0x222196F3)
        LetterType.SIGN -> Color(0x229E9E9E)
    }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .clickable { speakLetterThenExample(letter.name, letter.exampleWord) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${letter.upper}${letter.lower}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Буква «${letter.name}»",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (letter.sound != "—") {
                    Text(
                        "Звук: [${letter.sound}]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "${letter.exampleWord} — ${letter.exampleTranslation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { speak(letter.name) }) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Прослушать")
            }
        }
    }
}

@Composable
private fun SyllableChip(syllable: String, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                syllable,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Произнести",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun WordCard(word: String, meaning: String, speak: (String) -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { speak(word) },
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    word,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    meaning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { speak(word) }) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Прослушать")
            }
        }
    }
}

// ==================== TAB 3: QUIZ ====================

/** Фразы озвучки при неверном выборе в игре «Найди букву» (нейтральные по полу ребёнка). */
private val FIND_LETTER_WRONG_PHRASES: List<String> = listOf(
    "Неправильно. Попробуй ещё.",
    "Неверно, попробуй ещё.",
    "Нет, ищи нужную букву.",
    "Не та буква, попробуй снова.",
    "Здесь ошибка. Выбери другую.",
    "Пока не угадано. Подумай ещё раз.",
    "Это другая буква. Ищи дальше.",
    "Почти, но нет — попробуй ещё.",
    "Не та буква. Послушай задание ещё раз.",
    "Мимо! Попробуй другую букву.",
    "Нет, смотри внимательнее на буквы.",
    "Неправильный выбор, попытайся снова.",
    "Не подходит. Ищи нужную.",
    "Это не та буква, попробуй ещё.",
    "Увы, неверно. Ещё разок.",
    "Нет, это другая. Продолжай искать.",
    "Не то. Слушай задание и выбери снова.",
    "Ошибочка! Другая буква.",
    "Неверно. Попробуй ещё раз.",
    "Ещё не то. Ищи дальше.",
)

/** Случайная похвала при верном ответе (формулировки без мужского/женского рода о ребёнке). */
private val FIND_LETTER_CORRECT_PHRASES: List<String> = listOf(
    "Отлично! Верно!",
    "Супер! Угадано!",
    "Супер! Всё правильно!",
    "Здорово! Так держать!",
    "Замечательно! Это та самая буква!",
    "Правильно! Очень хорошо!",
    "Да! Всё получилось!",
    "Браво! Верный выбор!",
    "Класс! Получилось!",
    "Великолепно! Именно эта буква!",
    "Ура! Верно!",
    "Точно! Супер!",
    "Гениально! Правильная буква!",
    "Вот это да! Всё верно!",
    "Прекрасно! Так и нужно!",
    "Хорошая работа! Верно!",
    "Так точно! Правильный ответ!",
    "Ловко! Именно эта!",
    "Идеально! Можно идти дальше!",
    "Чудесно! Верно угадано!",
)

private sealed class FindLetterTapFeedback {
    data object Idle : FindLetterTapFeedback()
    data class Correct(val upper: Char) : FindLetterTapFeedback()
    data class Wrong(val upper: Char) : FindLetterTapFeedback()
}

private data class FindLetterRound(
    val target: RussianLetter,
    val choices: List<RussianLetter>,
)

private fun nextFindLetterRound(random: Random): FindLetterRound {
    val alphabet = AzbukaRepository.ALPHABET
    val target = alphabet[random.nextInt(alphabet.size)]
    val wrong = alphabet.filter { it.upper != target.upper }.shuffled(random).take(3)
    val choices = (wrong + target).shuffled(random)
    return FindLetterRound(target, choices)
}

@Composable
private fun FindLetterGamePane(
    speak: (String) -> Unit,
    speakWhenDone: (String, () -> Unit) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
) {
    val random = remember { Random(System.currentTimeMillis()) }
    var roundKey by remember { mutableIntStateOf(0) }
    val round = remember(roundKey) { nextFindLetterRound(random) }
    var tapFeedback by remember { mutableStateOf<FindLetterTapFeedback>(FindLetterTapFeedback.Idle) }

    LaunchedEffect(roundKey) {
        tapFeedback = FindLetterTapFeedback.Idle
        speak("Найди букву ${round.target.name}.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            "Найди букву",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Послушай задание и нажми на нужную букву.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { speak("Найди букву ${round.target.name}.") }) {
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
            items(round.choices, key = { "${it.upper}${roundKey}" }) { letter ->
                val correctShown =
                    (tapFeedback as? FindLetterTapFeedback.Correct)?.upper == letter.upper
                val wrongShown =
                    (tapFeedback as? FindLetterTapFeedback.Wrong)?.upper == letter.upper
                val idle = tapFeedback is FindLetterTapFeedback.Idle
                FindLetterChoiceCard(
                    letter = letter,
                    showCorrectCheck = correctShown,
                    showWrongX = wrongShown,
                    enabled = idle,
                    onClick = {
                        if (idle) {
                            if (letter.upper == round.target.upper) {
                                tapFeedback = FindLetterTapFeedback.Correct(letter.upper)
                                val praise = FIND_LETTER_CORRECT_PHRASES.random(random)
                                scope.launch { progressRepo.addPoints(8) }
                                speakWhenDone(praise) {
                                    roundKey++
                                }
                            } else {
                                tapFeedback = FindLetterTapFeedback.Wrong(letter.upper)
                                speak(FIND_LETTER_WRONG_PHRASES.random(random))
                                scope.launch {
                                    delay(1200L)
                                    tapFeedback = FindLetterTapFeedback.Idle
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
private fun FindLetterChoiceCard(
    letter: RussianLetter,
    showCorrectCheck: Boolean,
    showWrongX: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = when (letter.type) {
        LetterType.VOWEL -> Color(0x33F44336)
        LetterType.CONSONANT -> Color(0x332196F3)
        LetterType.SIGN -> Color(0x339E9E9E)
    }
    val borderColor = when (letter.type) {
        LetterType.VOWEL -> Color(0xFFF44336)
        LetterType.CONSONANT -> Color(0xFF2196F3)
        LetterType.SIGN -> Color(0xFF9E9E9E)
    }
    val resultBorder = when {
        showCorrectCheck -> Color(0xFF4CAF50)
        showWrongX -> Color(0xFFF44336)
        else -> borderColor
    }
    val resultBg = when {
        showCorrectCheck -> Color(0x224CAF50)
        showWrongX -> Color(0x22F44336)
        else -> bgColor
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
            Text(
                "${letter.upper}${letter.lower}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center),
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizTab(
    speak: (String) -> Unit,
    speakWhenDone: (String, () -> Unit) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
    library: BibleLibrary,
) {
    var subMode by remember { mutableIntStateOf(0) }
    val gameTranslation = remember(library) { BibleWordGamePool.pickTranslationWithText(library) }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = subMode,
            edgePadding = 8.dp,
            modifier = Modifier.fillMaxWidth(),
            divider = {},
        ) {
            Tab(
                selected = subMode == 0,
                onClick = { subMode = 0 },
                text = { Text("Уроки", maxLines = 1) },
            )
            Tab(
                selected = subMode == 1,
                onClick = { subMode = 1 },
                text = { Text("Библия", maxLines = 1) },
            )
            Tab(
                selected = subMode == 2,
                onClick = { subMode = 2 },
                text = { Text("Буквы", maxLines = 1) },
            )
            Tab(
                selected = subMode == 3,
                onClick = { subMode = 3 },
                text = { Text("Части речи", maxLines = 1) },
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (subMode) {
                0 -> LessonQuizPane(speak = speak, progressRepo = progressRepo, scope = scope)
                1 -> BibleGamesSection(
                    library = library,
                    translation = gameTranslation,
                    speak = speak,
                    progressRepo = progressRepo,
                    scope = scope,
                )
                2 -> FindLetterGamePane(
                    speak = speak,
                    speakWhenDone = speakWhenDone,
                    progressRepo = progressRepo,
                    scope = scope,
                )
                3 -> PartsOfSpeechGamePane(
                    speak = speak,
                    speakWhenDone = speakWhenDone,
                    progressRepo = progressRepo,
                    scope = scope,
                )
            }
        }
    }
}

@Composable
private fun LessonQuizPane(
    speak: (String) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
) {
    val allExercises = remember {
        AzbukaRepository.LESSONS.flatMap { lesson ->
            lesson.exercises.map { ex -> lesson.title to ex }
        }.shuffled()
    }
    var currentIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var answered by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var quizBonusGiven by remember { mutableStateOf(false) }

    LaunchedEffect(finished, score, answered) {
        if (finished && !quizBonusGiven && answered > 0) {
            quizBonusGiven = true
            progressRepo.addPoints(score * 2 + 12)
        }
    }

    if (finished || currentIndex >= allExercises.size) {
        QuizResultScreen(
            score = score,
            total = answered,
            onRestart = {
                currentIndex = 0
                score = 0
                answered = 0
                finished = false
                quizBonusGiven = false
            },
        )
    } else {
        val (topic, exercise) = allExercises[currentIndex]
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Вопрос ${currentIndex + 1}/${allExercises.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                    Text(" $score", fontWeight = FontWeight.Bold)
                }
            }
            LinearProgressIndicator(
                progress = { (currentIndex + 1f) / allExercises.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                topic,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))

            QuizQuestion(
                exercise = exercise,
                speak = speak,
                onAnswer = { correct ->
                    answered++
                    if (correct) {
                        score++
                        scope.launch { progressRepo.addPoints(10) }
                    }
                },
                onNext = {
                    currentIndex++
                    if (currentIndex >= allExercises.size) finished = true
                },
            )
        }
    }
}

@Composable
private fun QuizQuestion(
    exercise: AzbukaExercise,
    speak: (String) -> Unit,
    onAnswer: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    var selectedIndex by remember(exercise) { mutableIntStateOf(-1) }
    var showResult by remember(exercise) { mutableStateOf(false) }
    val isCorrect = selectedIndex == exercise.correctIndex

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    exercise.question,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp,
                )
                if (exercise.hint.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFFD700))
                        Spacer(Modifier.width(4.dp))
                        Text(exercise.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        exercise.options.forEachIndexed { index, option ->
            val borderColor by animateColorAsState(
                when {
                    !showResult -> if (selectedIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    index == exercise.correctIndex -> Color(0xFF4CAF50)
                    selectedIndex == index -> Color(0xFFF44336)
                    else -> MaterialTheme.colorScheme.outline
                },
                label = "borderColor",
            )
            val bgColor by animateColorAsState(
                when {
                    !showResult -> if (selectedIndex == index) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    index == exercise.correctIndex -> Color(0x224CAF50)
                    selectedIndex == index -> Color(0x22F44336)
                    else -> Color.Transparent
                },
                label = "bgColor",
            )

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(enabled = !showResult) {
                        selectedIndex = index
                        speak(option)
                    },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(2.dp, borderColor),
                colors = CardDefaults.outlinedCardColors(containerColor = bgColor),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(2.dp, borderColor, CircleShape)
                            .background(if (selectedIndex == index && !showResult) MaterialTheme.colorScheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showResult && index == exercise.correctIndex) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                        } else if (showResult && selectedIndex == index && !isCorrect) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(18.dp))
                        } else {
                            Text(
                                "${('А'.code + index).toChar()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selectedIndex == index && !showResult) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        option,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (showResult && index == exercise.correctIndex) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        AnimatedVisibility(visible = showResult) {
            val msg = if (isCorrect) "Правильно! ⭐" else "Неверно. Правильный ответ: ${exercise.options[exercise.correctIndex]}"
            val color = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    msg,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
        }

        if (!showResult && selectedIndex >= 0) {
            Button(
                onClick = {
                    showResult = true
                    onAnswer(selectedIndex == exercise.correctIndex)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Проверить")
            }
        }
        if (showResult) {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Далее")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun QuizResultScreen(score: Int, total: Int, onRestart: () -> Unit) {
    val pct = if (total > 0) score * 100 / total else 0
    val emoji = when {
        pct >= 90 -> "🏆"
        pct >= 70 -> "⭐"
        pct >= 50 -> "👍"
        else -> "📚"
    }
    val message = when {
        pct >= 90 -> "Превосходно! Ты отлично знаешь буквы!"
        pct >= 70 -> "Хороший результат! Ещё немного практики!"
        pct >= 50 -> "Неплохо! Продолжай учиться!"
        else -> "Не расстраивайся! Повтори уроки и попробуй снова."
    }

    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            emoji,
            fontSize = 72.sp,
            modifier = Modifier.scale(scale),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Результат",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "$score из $total правильных ($pct%)",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRestart,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Пройти ещё раз")
        }
    }
}

@Composable
private fun ExerciseBlock(
    exercises: List<AzbukaExercise>,
    speak: (String) -> Unit,
    progressRepo: AzbukaProgressRepository,
    scope: CoroutineScope,
    onComplete: () -> Unit,
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }

    if (currentIndex >= exercises.size) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val pct = if (exercises.isNotEmpty()) score * 100 / exercises.size else 0
            Text(if (pct >= 70) "🎉" else "📚", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Результат: $score из ${exercises.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (pct >= 70) "Отлично! Урок пройден!" else "Попробуй повторить урок.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = {
                    val pct = if (exercises.isNotEmpty()) score * 100 / exercises.size else 0
                    scope.launch {
                        progressRepo.addPoints(if (pct >= 70) 40 else 18)
                    }
                    onComplete()
                },
            ) {
                Text("К урокам")
            }
        }
    } else {
        QuizQuestion(
            exercise = exercises[currentIndex],
            speak = speak,
            onAnswer = { correct ->
                if (correct) {
                    score++
                    scope.launch { progressRepo.addPoints(8) }
                }
            },
            onNext = { currentIndex++ },
        )
    }
}

// ==================== TAB 4: RULES ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RulesTab(speak: (String) -> Unit) {
    val syllableLessons = AzbukaRepository.SYLLABLE_LESSONS
    val rules = AzbukaRepository.READING_RULES
    val spellingExceptions = AzbukaRepository.SPELLING_EXCEPTIONS

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Слоги и чтение",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        items(syllableLessons) { sl ->
            SyllableLessonCard(sl, speak = speak)
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Правила русского языка",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        items(rules) { rule ->
            RuleCard(rule, speak = speak)
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Исключения в написании",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item {
            Text(
                "Когда и какие буквы пишутся не так, как в обычном правиле — запоминай отдельно или проверяй по словарю.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(spellingExceptions) { rule ->
            RuleCard(rule, speak = speak)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyllableLessonCard(lesson: com.example.bible.data.SyllableLesson, speak: (String) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    lesson.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { speak("${lesson.title}. ${lesson.description}") },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Прослушать", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                lesson.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                lesson.syllables.forEach { syl ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { speak(syl) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            syl,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Примеры:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            lesson.exampleWords.forEach { (word, desc) ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        word,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        " — $desc",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(2f),
                    )
                    IconButton(
                        onClick = { speak(word.replace("-", "")) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Прослушать", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleCard(rule: com.example.bible.data.ReadingRule, speak: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    rule.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { speak("${rule.title}. ${rule.explanation}") },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Прослушать правило", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(4.dp))
                val arrow = if (expanded) "▲" else "▼"
                Text(arrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        rule.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    rule.examples.forEach { ex ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("•  ", color = MaterialTheme.colorScheme.primary)
                            Text(
                                ex,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { speak(ex) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Прослушать", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (!expanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    rule.explanation.take(80) + "…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
