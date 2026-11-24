package com.example.bible.ui

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.BibleAudioNarrators
import com.example.bible.data.BibleAudioPlayer
import com.example.bible.data.BibleAudioRepeatMode
import com.example.bible.data.BibleCanon
import com.example.bible.data.BibleVerse
import com.example.bible.data.BibleVerseRepeatConfig
import com.example.bible.ui.theme.BibleTheme
import com.example.bible.data.NoteScriptureLinks
import com.example.bible.data.TranslationId
import com.example.bible.data.localAudioFile
import com.example.bible.data.narratorForReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SPEED_STEPS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val REPEAT_COUNT_STEPS = listOf(0, 2, 3, 5, 10)

@Composable
fun BibleAudioBar(
    modifier: Modifier = Modifier,
    /** Высота панели — чтобы список стихов имел нижний отступ и не уходил под плеер */
    onBarHeightChanged: ((Dp) -> Unit)? = null,
) {
    val ps by BibleAudioPlayer.state.collectAsState()
    val continueChapters by BibleAudioPlayer.continueChapters.collectAsState()
    val repeatMode by BibleAudioPlayer.repeatMode.collectAsState()
    val repeatTargetCount by BibleAudioPlayer.repeatTargetCount.collectAsState()
    val repeatCompletedCount by BibleAudioPlayer.repeatCompletedCount.collectAsState()
    val verseRepeatConfig by BibleAudioPlayer.verseRepeatConfig.collectAsState()
    val startOffsetMs by BibleAudioPlayer.segmentLoopStartOffsetMs.collectAsState()
    val endOffsetMs by BibleAudioPlayer.segmentLoopEndOffsetMs.collectAsState()
    val playbackSpeed by BibleAudioPlayer.playbackSpeed.collectAsState()
    val sleepRemaining by BibleAudioPlayer.sleepTimerRemainingMs.collectAsState()
    val sleepAwaitChapter by BibleAudioPlayer.sleepAwaitingChapterEnd.collectAsState()
    val ttsLoopState by ReaderVerseTtsLooper.state.collectAsState()
    val activeChapterSnap by ReaderChapterAudioContext.snapshot.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSeeking by remember { mutableStateOf(false) }
    var seekPos by remember { mutableStateOf(0) }
    var isDownloading by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showRepeatDialog by remember { mutableStateOf(false) }
    var showOffsetAdjust by remember { mutableStateOf(false) }
    var playerCollapsed by rememberSaveable { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(ps.isPlaying) {
        while (ps.isPlaying) {
            BibleAudioPlayer.updatePosition()
            delay(300)
        }
    }

    val isAudioActive = ps.bookId.isNotBlank()
    val isTtsLoopActive = ttsLoopState.isActive
    val isActive = isAudioActive || isTtsLoopActive

    LaunchedEffect(isActive) {
        if (!isActive) onBarHeightChanged?.invoke(0.dp)
    }

    if (showRepeatDialog) {
        val dlgBookId = when {
            isAudioActive -> ps.bookId
            isTtsLoopActive -> ttsLoopState.bookId
            else -> activeChapterSnap.bookId
        }
        val dlgChapter = when {
            isAudioActive && ps.chapter > 0 -> ps.chapter
            isTtsLoopActive && ttsLoopState.chapter > 0 -> ttsLoopState.chapter
            else -> activeChapterSnap.chapter.coerceAtLeast(1)
        }
        val dlgNarratorId = if (isAudioActive && ps.narratorId.isNotBlank()) {
            ps.narratorId
        } else {
            activeChapterSnap.narratorId
        }
        val dlgVerses = if (activeChapterSnap.bookId == dlgBookId && activeChapterSnap.chapter == dlgChapter) {
            activeChapterSnap.verses
        } else if (isTtsLoopActive) {
            ttsLoopState.verses
        } else {
            emptyList()
        }
        BibleAudioRepeatDialog(
            bookId = dlgBookId,
            chapter = dlgChapter,
            translation = activeChapterSnap.translation,
            narratorId = dlgNarratorId,
            chapterVerses = dlgVerses,
            initialStartVerse = verseRepeatConfig?.startVerse
                ?: ttsLoopState.verses.firstOrNull()?.number
                ?: 1,
            initialEndVerse = verseRepeatConfig?.endVerse
                ?: ttsLoopState.verses.lastOrNull()?.number
                ?: dlgVerses.size.coerceAtLeast(1),
            initialSegmentSpec = verseRepeatConfig?.segmentSpec.orEmpty(),
            initialUseTts = isTtsLoopActive,
            onDismiss = { showRepeatDialog = false },
        )
    }

    AnimatedVisibility(
        visible = isActive,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        if (isTtsLoopActive && !isAudioActive) {
            TtsVerseRepeatBarContent(
                ttsState = ttsLoopState,
                collapsed = playerCollapsed,
                onToggleCollapsed = { playerCollapsed = !playerCollapsed },
                onBarHeightChanged = onBarHeightChanged,
                onOpenRepeatDialog = { showRepeatDialog = true },
            )
            return@AnimatedVisibility
        }

        val bookName = BibleCanon.byId(ps.bookId)?.nameRu ?: ps.bookId
        val narrator = BibleAudioNarrators.byId(ps.narratorId)
        val displayPos = if (isSeeking) seekPos else ps.positionMs
        val isLocal = remember(ps.bookId, ps.chapter, ps.narratorId) {
            if (ps.bookId.isBlank()) false
            else localAudioFile(context, ps.narratorId, ps.bookId, ps.chapter).let {
                it.exists() && it.length() > 1024
            }
        }

        if (showSleepDialog) {
            SleepTimerDialog(
                onDismiss = { showSleepDialog = false },
                onConfirm = { minutes, stopAtChapterEnd ->
                    BibleAudioPlayer.setSleepTimer(minutes, stopAtChapterEnd)
                    showSleepDialog = false
                },
            )
        }

        val repeatLoopBadge = remember(repeatMode, repeatTargetCount, repeatCompletedCount, verseRepeatConfig) {
            when (repeatMode) {
                BibleAudioRepeatMode.OFF -> null
                BibleAudioRepeatMode.CHAPTER -> {
                    val cnt = if (repeatTargetCount <= 0) "∞" else "${(repeatCompletedCount + 1).coerceAtMost(repeatTargetCount)}/$repeatTargetCount"
                    "🔂 Повтор главы ($cnt)"
                }
                BibleAudioRepeatMode.BOOK -> {
                    val cnt = if (repeatTargetCount <= 0) "∞" else "${(repeatCompletedCount + 1).coerceAtMost(repeatTargetCount)}/$repeatTargetCount"
                    "🔁 Повтор книги ($cnt)"
                }
                BibleAudioRepeatMode.VERSES -> {
                    val vLabel = verseRepeatConfig?.labelRu ?: "стихи"
                    val cnt = if (repeatTargetCount <= 0) "∞" else "${(repeatCompletedCount + 1).coerceAtMost(repeatTargetCount)}/$repeatTargetCount"
                    "🔂 Повтор $vLabel ($cnt)"
                }
            }
        }

        AudioBarFrame(
            landscape = landscape,
            collapsed = playerCollapsed,
            onBarHeightChanged = onBarHeightChanged,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (landscape || playerCollapsed) 6.dp else 10.dp,
                        vertical = if (landscape || playerCollapsed) 2.dp else 6.dp,
                    ),
            ) {
                if (playerCollapsed) {
                    BibleAudioCollapsedBar(
                        title = "$bookName ${ps.chapter} · ${narrator.name}",
                        isPlaying = ps.isPlaying,
                        isLoading = ps.isLoading,
                        onTogglePlay = { BibleAudioPlayer.togglePlay() },
                        onExpand = { playerCollapsed = false },
                        onClose = { stopReaderChapterAudio() },
                    )
                } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (ps.isLoading) {
                        CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
                    } else {
                        FilledTonalIconButton(
                            onClick = { BibleAudioPlayer.togglePlay() },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                if (ps.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "$bookName ${ps.chapter} (${narrator.name})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (repeatLoopBadge != null) {
                                Text(
                                    text = repeatLoopBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            } else if (verseRepeatConfig != null) {
                                Text(
                                    text = "🎧 ${verseRepeatConfig!!.labelRu}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatMs(displayPos),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Slider(
                                value = if (ps.durationMs > 0) displayPos.toFloat() / ps.durationMs else 0f,
                                onValueChange = {
                                    isSeeking = true
                                    seekPos = (it * ps.durationMs).toInt()
                                },
                                onValueChangeFinished = {
                                    BibleAudioPlayer.seekTo(seekPos)
                                    isSeeking = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(if (landscape) 18.dp else 22.dp),
                            )
                            Text(
                                formatMs(ps.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (!isLocal && !isDownloading && ps.bookId.isNotBlank()) {
                        IconButton(
                            onClick = {
                                isDownloading = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        BibleAudioPlayer.downloadChapter(
                                            context, narrator, ps.bookId, ps.chapter,
                                        )
                                        launch(Dispatchers.Main) {
                                            isDownloading = false
                                            Toast.makeText(context, "Скачано для офлайн", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        launch(Dispatchers.Main) {
                                            isDownloading = false
                                            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(Icons.Default.CloudDownload, "Скачать", Modifier.size(18.dp))
                        }
                    }
                    if (isDownloading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }

                    IconButton(
                        onClick = { showSleepDialog = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            stringResource(R.string.bible_audio_sleep_timer),
                            Modifier.size(20.dp),
                            tint = if (sleepRemaining != null || sleepAwaitChapter) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }

                    IconButton(
                        onClick = { showRepeatDialog = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = when (repeatMode) {
                                BibleAudioRepeatMode.CHAPTER,
                                BibleAudioRepeatMode.VERSES,
                                -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Режим повтора и выбор стихов",
                            modifier = Modifier.size(20.dp),
                            tint = if (repeatMode != BibleAudioRepeatMode.OFF || verseRepeatConfig != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }

                    IconToggleButton(
                        checked = continueChapters || repeatMode == BibleAudioRepeatMode.BOOK,
                        onCheckedChange = { enabled ->
                            if (!enabled && repeatMode == BibleAudioRepeatMode.BOOK) {
                                BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                            }
                            BibleAudioPlayer.setContinueChapters(enabled)
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = stringResource(R.string.bible_audio_continue_chapters),
                            Modifier.size(20.dp),
                        )
                    }

                    BibleAudioCollapseButton(
                        collapsed = false,
                        onClick = { playerCollapsed = true },
                    )

                    IconButton(
                        onClick = { stopReaderChapterAudio() },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            "Закрыть",
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (landscape) {
                    ChapterAudioLandscapeOptions(
                        repeatMode = repeatMode,
                        repeatTargetCount = repeatTargetCount,
                        continueChapters = continueChapters,
                        verseRepeatConfig = verseRepeatConfig,
                        playbackSpeed = playbackSpeed,
                        startOffsetMs = startOffsetMs,
                        endOffsetMs = endOffsetMs,
                        showOffsetAdjust = showOffsetAdjust,
                        onToggleOffsetAdjust = { showOffsetAdjust = !showOffsetAdjust },
                        sleepRemaining = sleepRemaining,
                        sleepAwaitChapter = sleepAwaitChapter,
                        error = ps.error,
                        onOpenRepeatDialog = { showRepeatDialog = true },
                    )
                }
                if (!landscape) {
                // Ряд быстрого переключения режима повтора (Глава / Книга / Повтор главы / Повтор книги / Стихи)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Режим:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilterChip(
                        selected = repeatMode == BibleAudioRepeatMode.OFF && !continueChapters && verseRepeatConfig == null,
                        onClick = {
                            resetReaderVerseRangeToFullChapter()
                            BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                            BibleAudioPlayer.setContinueChapters(false)
                        },
                        label = { Text("1 глава", style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = repeatMode == BibleAudioRepeatMode.OFF && continueChapters,
                        onClick = {
                            resetReaderVerseRangeToFullChapter()
                            BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                            BibleAudioPlayer.setContinueChapters(true)
                        },
                        label = { Text("Книга подряд", style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = repeatMode == BibleAudioRepeatMode.CHAPTER,
                        onClick = {
                            if (repeatMode == BibleAudioRepeatMode.CHAPTER) {
                                BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                            } else {
                                resetReaderVerseRangeToFullChapter()
                                BibleAudioPlayer.setContinueChapters(false)
                                BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.CHAPTER, repeatTargetCount)
                            }
                        },
                        label = { Text("🔂 Повтор главы", style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = repeatMode == BibleAudioRepeatMode.BOOK,
                        onClick = {
                            if (repeatMode == BibleAudioRepeatMode.BOOK) {
                                BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                            } else {
                                resetReaderVerseRangeToFullChapter()
                                BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.BOOK, repeatTargetCount)
                            }
                        },
                        label = { Text("🔁 Повтор книги", style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = repeatMode == BibleAudioRepeatMode.VERSES || verseRepeatConfig != null,
                        onClick = { showRepeatDialog = true },
                        label = {
                            val label = verseRepeatConfig?.let { "🔂 Стихи (${it.labelRu})" } ?: "🔂 Стихи…"
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        },
                    )
                }

                // Если включён любой режим повтора — быстрый выбор числа повторений (∞, 2×, 3×, 5×, 10×)
                if (repeatMode != BibleAudioRepeatMode.OFF) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Повторов:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        REPEAT_COUNT_STEPS.forEach { count ->
                            FilterChip(
                                selected = repeatTargetCount == count,
                                onClick = { BibleAudioPlayer.setRepeatTargetCount(count) },
                                label = {
                                    Text(
                                        if (count == 0) "∞ По кругу" else "${count}×",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                        }
                    }
                }

                // Панель активного отрезка стихов (с возможностью включить повтор, подстроить границы ±2с или сбросить до всей главы)
                verseRepeatConfig?.let { cfg ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = if (repeatMode == BibleAudioRepeatMode.VERSES) {
                                        "🔂 Повтор ${cfg.labelRu}"
                                    } else {
                                        "🎧 Отрывок ${cfg.labelRu}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                FilterChip(
                                    selected = repeatMode == BibleAudioRepeatMode.VERSES,
                                    onClick = {
                                        if (repeatMode == BibleAudioRepeatMode.VERSES) {
                                            BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                                            BibleAudioPlayer.setVerseRepeatConfig(cfg)
                                        } else {
                                            BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.VERSES, repeatTargetCount)
                                            BibleAudioPlayer.setVerseRepeatConfig(cfg)
                                            restartActiveVerseRepeatIfConfigured(context)
                                        }
                                    },
                                    label = {
                                        Text(
                                            if (repeatMode == BibleAudioRepeatMode.VERSES) "Повтор вкл" else "Повторять",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                )
                                AssistChip(
                                    onClick = { showRepeatDialog = true },
                                    label = { Text("Стихи…", style = MaterialTheme.typography.labelSmall) },
                                )
                                AssistChip(
                                    onClick = { showOffsetAdjust = !showOffsetAdjust },
                                    leadingIcon = {
                                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    label = {
                                        val hasOff = startOffsetMs != 0 || endOffsetMs != 0
                                        Text(
                                            if (hasOff) "Границы (${formatSignedSec(startOffsetMs)}/${formatSignedSec(endOffsetMs)})"
                                            else "Границы ±2с",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                )
                                TextButton(
                                    onClick = {
                                        resetReaderVerseRangeToFullChapter()
                                        BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 28.dp),
                                ) {
                                    Text("Вся глава", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (showOffsetAdjust) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        "Начало (${formatSignedSec(startOffsetMs)}):",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    TextButton(
                                        onClick = { nudgeReaderVerseLoopStart(-2_000) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 26.dp),
                                    ) { Text("−2с", style = MaterialTheme.typography.labelSmall) }
                                    TextButton(
                                        onClick = { nudgeReaderVerseLoopStart(2_000) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 26.dp),
                                    ) { Text("+2с", style = MaterialTheme.typography.labelSmall) }

                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Конец (${formatSignedSec(endOffsetMs)}):",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    TextButton(
                                        onClick = { BibleAudioPlayer.adjustSegmentLoopEndOffsetMs(-2_000) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 26.dp),
                                    ) { Text("−2с", style = MaterialTheme.typography.labelSmall) }
                                    TextButton(
                                        onClick = { BibleAudioPlayer.adjustSegmentLoopEndOffsetMs(2_000) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 26.dp),
                                    ) { Text("+2с", style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                    }
                }

                // Ряд выбора скорости
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.bible_audio_speed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SPEED_STEPS.forEach { s ->
                        FilterChip(
                            selected = kotlin.math.abs(playbackSpeed - s) < 0.01f,
                            onClick = { BibleAudioPlayer.setPlaybackSpeed(s) },
                            label = {
                                Text(
                                    if (s == 1f) "1×" else "${s}×".replace(".0", ""),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }

                if (sleepRemaining != null) {
                    Text(
                        stringResource(R.string.bible_audio_sleep_remaining, formatSleepCountdown(sleepRemaining!!)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (sleepAwaitChapter) {
                    Text(
                        stringResource(R.string.bible_audio_sleep_after_chapter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                if (ps.error != null) {
                    Text(
                        ps.error!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                } // портрет: полные ряды режимов, скорости и отрывка
                } // развёрнутая панель
            }
        }
    }
}

@Composable
private fun TtsVerseRepeatBarContent(
    ttsState: ReaderVerseTtsLoopState,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onBarHeightChanged: ((Dp) -> Unit)?,
    onOpenRepeatDialog: () -> Unit,
) {
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val bookName = BibleCanon.byId(ttsState.bookId)?.nameRu ?: ttsState.bookId
    val loopBadge = if (ttsState.repeatEnabled) {
        val cnt = if (ttsState.targetLoops <= 0) "∞" else "${(ttsState.completedLoops + 1).coerceAtMost(ttsState.targetLoops)}/${ttsState.targetLoops}"
        "🔂 Повтор ($cnt)"
    } else {
        "1 раз"
    }

    AudioBarFrame(
        landscape = landscape,
        collapsed = collapsed,
        onBarHeightChanged = onBarHeightChanged,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (landscape || collapsed) 6.dp else 10.dp,
                    vertical = if (landscape || collapsed) 2.dp else 8.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (landscape) 2.dp else 4.dp),
        ) {
            if (collapsed) {
                BibleAudioCollapsedBar(
                    title = "🗣️ $bookName ${ttsState.chapter}: ${ttsState.rangeLabelRu}",
                    isPlaying = ttsState.isPlaying,
                    isLoading = false,
                    onTogglePlay = { ReaderVerseTtsLooper.togglePlay(context) },
                    onExpand = onToggleCollapsed,
                    onClose = { ReaderVerseTtsLooper.stop() },
                )
            } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { ReaderVerseTtsLooper.togglePlay(context) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (ttsState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🗣️ $bookName ${ttsState.chapter}: ${ttsState.rangeLabelRu} · $loopBadge",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Стих ${ttsState.currentVerseNumber}: ${ttsState.currentVerseText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { ReaderVerseTtsLooper.skipVerse(context, -1) },
                    enabled = ttsState.verses.size > 1,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущий стих", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { ReaderVerseTtsLooper.skipVerse(context, 1) },
                    enabled = ttsState.verses.size > 1,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Следующий стих", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onOpenRepeatDialog,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Default.RepeatOne,
                        contentDescription = "Настроить повтор",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                BibleAudioCollapseButton(
                    collapsed = false,
                    onClick = onToggleCollapsed,
                )
                IconButton(
                    onClick = { ReaderVerseTtsLooper.stop() },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Закрыть",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = ttsState.repeatEnabled,
                    onClick = {
                        ReaderVerseTtsLooper.setRepeatSettings(!ttsState.repeatEnabled, ttsState.targetLoops)
                    },
                    label = { Text("🔂 Повторять", style = MaterialTheme.typography.labelSmall) },
                )
                REPEAT_COUNT_STEPS.forEach { count ->
                    FilterChip(
                        selected = ttsState.repeatEnabled && ttsState.targetLoops == count,
                        onClick = {
                            ReaderVerseTtsLooper.setRepeatSettings(true, count)
                        },
                        label = {
                            Text(
                                if (count == 0) "∞" else "${count}×",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
                AssistChip(
                    onClick = onOpenRepeatDialog,
                    label = { Text("Изменить стихи / Диктор", style = MaterialTheme.typography.labelSmall) },
                )
            }
            } // развёрнутый повтор TTS
        }
    }
}

/**
 * Диалог настройки прослушивания и повтора:
 * - Повтор текущей главы, всей книги или выбранных стихов (диапазона / набора стихов);
 * - Выбор количества повторов (∞, 1 раз, 2×, 3×, 5×, 10×);
 * - Выбор источника озвучки для стихов: Аудио диктора или Голос TTS (по стихам).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BibleAudioRepeatDialog(
    bookId: String,
    chapter: Int,
    translation: TranslationId,
    narratorId: String,
    chapterVerses: List<BibleVerse>,
    initialStartVerse: Int = 1,
    initialEndVerse: Int = 1,
    initialSegmentSpec: String = "",
    initialMode: BibleAudioRepeatMode? = null,
    initialUseTts: Boolean = false,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val currentRepeatMode by BibleAudioPlayer.repeatMode.collectAsState()
    val currentContinue by BibleAudioPlayer.continueChapters.collectAsState()
    val currentTargetCount by BibleAudioPlayer.repeatTargetCount.collectAsState()

    val maxVerse = chapterVerses.size.coerceAtLeast(maxOf(initialStartVerse, initialEndVerse, 1))
    val bookName = BibleCanon.byId(bookId)?.nameRu ?: bookId
    val narrator = remember(translation, bookId, narratorId) {
        narratorForReading(translation, bookId, narratorId)
    }

    // 0 = 1 глава, 1 = Книга подряд, 2 = Повтор главы, 3 = Повтор всей книги, 4 = Выбранные стихи
    var selectedScope by remember {
        mutableIntStateOf(
            when (initialMode ?: currentRepeatMode) {
                BibleAudioRepeatMode.VERSES -> 4
                BibleAudioRepeatMode.CHAPTER -> 2
                BibleAudioRepeatMode.BOOK -> 3
                BibleAudioRepeatMode.OFF -> when {
                    initialSegmentSpec.isNotBlank() || initialStartVerse > 1 || initialEndVerse < maxVerse -> 4
                    currentContinue -> 1
                    else -> 2 // По умолчанию при нажатии кнопки повтора предлагаем повтор главы или стихов
                }
            },
        )
    }

    var startVerse by remember(maxVerse) { mutableIntStateOf(initialStartVerse.coerceIn(1, maxVerse)) }
    var endVerse by remember(maxVerse) { mutableIntStateOf(initialEndVerse.coerceIn(startVerse, maxVerse)) }
    var customSpec by remember { mutableStateOf(initialSegmentSpec) }
    var repeatEnabledForVerses by remember { mutableStateOf(true) }
    var targetLoops by remember { mutableIntStateOf(currentTargetCount) }
    var useTtsForVerses by remember { mutableStateOf(initialUseTts) }

    val verseMap = remember(chapterVerses) { chapterVerses.associateBy { it.number } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Прослушивание и повтор", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "$bookName, глава $chapter · Всего стихов: $maxVerse",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Что прослушивать:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = selectedScope == 4,
                        onClick = { selectedScope = 4 },
                        label = { Text("🔂 Выбранные стихи") },
                    )
                    FilterChip(
                        selected = selectedScope == 2,
                        onClick = { selectedScope = 2 },
                        label = { Text("🔂 Повтор главы") },
                    )
                    FilterChip(
                        selected = selectedScope == 3,
                        onClick = { selectedScope = 3 },
                        label = { Text("🔁 Повтор всей книги") },
                    )
                    FilterChip(
                        selected = selectedScope == 1,
                        onClick = { selectedScope = 1 },
                        label = { Text("▶️ Книга подряд (1 раз)") },
                    )
                    FilterChip(
                        selected = selectedScope == 0,
                        onClick = { selectedScope = 0 },
                        label = { Text("▶️ Текущая глава (1 раз)") },
                    )
                }

                if (selectedScope == 4) {
                    HorizontalDivider()
                    Text(
                        "Диапазон стихов в главе $chapter:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("С какого стиха", style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(
                                    onClick = {
                                        if (startVerse > 1) {
                                            startVerse--
                                            customSpec = ""
                                        }
                                    },
                                    enabled = startVerse > 1,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = "$startVerse",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                                FilledTonalIconButton(
                                    onClick = {
                                        if (startVerse < maxVerse) {
                                            startVerse++
                                            if (endVerse < startVerse) endVerse = startVerse
                                            customSpec = ""
                                        }
                                    },
                                    enabled = startVerse < maxVerse,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Text("—", style = MaterialTheme.typography.titleMedium)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("По какой стих", style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(
                                    onClick = {
                                        if (endVerse > startVerse) {
                                            endVerse--
                                            customSpec = ""
                                        }
                                    },
                                    enabled = endVerse > startVerse,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = "$endVerse",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                                FilledTonalIconButton(
                                    onClick = {
                                        if (endVerse < maxVerse) {
                                            endVerse++
                                            customSpec = ""
                                        }
                                    },
                                    enabled = endVerse < maxVerse,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Быстрые кнопки выбора длины отрывка
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AssistChip(
                            onClick = {
                                endVerse = startVerse
                                customSpec = ""
                            },
                            label = { Text("Только $startVerse-й", style = MaterialTheme.typography.labelSmall) },
                        )
                        AssistChip(
                            onClick = {
                                endVerse = (startVerse + 2).coerceAtMost(maxVerse)
                                customSpec = ""
                            },
                            label = { Text("3 стиха", style = MaterialTheme.typography.labelSmall) },
                        )
                        AssistChip(
                            onClick = {
                                endVerse = (startVerse + 4).coerceAtMost(maxVerse)
                                customSpec = ""
                            },
                            label = { Text("5 стихов", style = MaterialTheme.typography.labelSmall) },
                        )
                        AssistChip(
                            onClick = {
                                endVerse = maxVerse
                                customSpec = ""
                            },
                            label = { Text("До конца ($maxVerse)", style = MaterialTheme.typography.labelSmall) },
                        )
                    }

                    // Произвольный список стихов (например 1-3, 7, 10-12)
                    OutlinedTextField(
                        value = customSpec,
                        onValueChange = { customSpec = it },
                        label = { Text("Или укажите стихи через запятую (напр. 1-3, 7, 12)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Превью текста выбранных стихов
                    val startText = verseMap[startVerse]?.text
                    val endText = if (endVerse != startVerse) verseMap[endVerse]?.text else null
                    if (!startText.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "$startVerse. $startText",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!endText.isNullOrBlank()) {
                                    Text(
                                        text = "… $endVerse. $endText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    // Выбор способа озвучки стихов: Диктор или Голос TTS
                    Text(
                        "Озвучка выбранных стихов:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !useTtsForVerses,
                            onClick = { useTtsForVerses = false },
                            leadingIcon = { Icon(Icons.Default.Headphones, null, Modifier.size(16.dp)) },
                            label = { Text("Диктор (${narrator.name})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = useTtsForVerses,
                            onClick = { useTtsForVerses = true },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(16.dp)) },
                            label = { Text("По стихам (TTS)") },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Повторять выбранные стихи по кругу", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = repeatEnabledForVerses, onCheckedChange = { repeatEnabledForVerses = it })
                    }
                }

                // Настройка числа повторений (для Повтора главы, Повтора книги и Повтора стихов)
                if (selectedScope in listOf(2, 3) || (selectedScope == 4 && repeatEnabledForVerses)) {
                    HorizontalDivider()
                    Text(
                        "Сколько раз повторять:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        REPEAT_COUNT_STEPS.forEach { count ->
                            FilterChip(
                                selected = targetLoops == count,
                                onClick = { targetLoops = count },
                                label = {
                                    Text(if (count == 0) "∞ Бесконечно" else "$count раза".replace("5 раза", "5 раз").replace("10 раза", "10 раз"))
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (bookId.isBlank() || chapter <= 0) {
                        onDismiss()
                        return@Button
                    }
                    when (selectedScope) {
                        0 -> {
                            // 1 глава (без повтора)
                            ReaderVerseTtsLooper.stop()
                            resetReaderVerseRangeToFullChapter()
                            BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                            BibleAudioPlayer.setContinueChapters(false)
                            if (BibleAudioPlayer.state.value.bookId != bookId ||
                                BibleAudioPlayer.state.value.chapter != chapter ||
                                !BibleAudioPlayer.state.value.isPlaying
                            ) {
                                BibleAudioPlayer.playChapter(context, narrator, bookId, chapter)
                            }
                        }
                        1 -> {
                            // Книга подряд (до конца книги)
                            ReaderVerseTtsLooper.stop()
                            resetReaderVerseRangeToFullChapter()
                            BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                            BibleAudioPlayer.setContinueChapters(true)
                            if (BibleAudioPlayer.state.value.bookId != bookId ||
                                BibleAudioPlayer.state.value.chapter != chapter ||
                                !BibleAudioPlayer.state.value.isPlaying
                            ) {
                                BibleAudioPlayer.playChapter(context, narrator, bookId, chapter)
                            }
                        }
                        2 -> {
                            // Повтор текущей главы
                            ReaderVerseTtsLooper.stop()
                            resetReaderVerseRangeToFullChapter()
                            BibleAudioPlayer.setContinueChapters(false)
                            BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.CHAPTER, targetLoops)
                            if (BibleAudioPlayer.state.value.bookId != bookId ||
                                BibleAudioPlayer.state.value.chapter != chapter ||
                                !BibleAudioPlayer.state.value.isPlaying
                            ) {
                                BibleAudioPlayer.playChapter(context, narrator, bookId, chapter)
                            }
                        }
                        3 -> {
                            // Повтор всей книги
                            ReaderVerseTtsLooper.stop()
                            resetReaderVerseRangeToFullChapter()
                            BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.BOOK, targetLoops)
                            if (BibleAudioPlayer.state.value.bookId != bookId ||
                                BibleAudioPlayer.state.value.chapter != chapter ||
                                !BibleAudioPlayer.state.value.isPlaying
                            ) {
                                BibleAudioPlayer.playChapter(context, narrator, bookId, chapter)
                            }
                        }
                        4 -> {
                            // Выбранные стихи
                            val cleanSpec = customSpec.trim()
                            if (useTtsForVerses && chapterVerses.isNotEmpty()) {
                                val wantedNums = if (cleanSpec.isNotBlank()) {
                                    NoteScriptureLinks.expandSegmentSpecToVerses(cleanSpec, maxVerse)
                                } else {
                                    (startVerse..endVerse).toSet()
                                }
                                val pickedVerses = chapterVerses.filter { it.number in wantedNums }
                                    .ifEmpty { chapterVerses.filter { it.number in startVerse..endVerse } }
                                val label = when {
                                    cleanSpec.isNotBlank() -> "ст. $cleanSpec"
                                    startVerse == endVerse -> "ст. $startVerse"
                                    else -> "ст. $startVerse–$endVerse"
                                }
                                ReaderVerseTtsLooper.start(
                                    context = context,
                                    bookId = bookId,
                                    chapter = chapter,
                                    translation = translation,
                                    verses = pickedVerses,
                                    repeatEnabled = repeatEnabledForVerses,
                                    targetLoops = targetLoops,
                                    rangeLabelRu = label,
                                )
                            } else {
                                startReaderVerseRangeAudio(
                                    context = context,
                                    narrator = narrator,
                                    bookId = bookId,
                                    chapter = chapter,
                                    translation = translation,
                                    startVerse = startVerse,
                                    endVerse = endVerse,
                                    chapterVerseCount = maxVerse,
                                    repeat = repeatEnabledForVerses,
                                    targetLoops = targetLoops,
                                    segmentSpec = cleanSpec.takeIf { it.isNotBlank() },
                                )
                            }
                        }
                    }
                    onDismiss()
                },
            ) {
                Text("Запустить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int, stopAtChapterEnd: Boolean) -> Unit,
) {
    var stopAtEnd by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bible_audio_sleep_timer)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.bible_audio_sleep_pick),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(5, 15, 30, 45, 60).chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            row.forEach { m ->
                                TextButton(
                                    onClick = { onConfirm(m, stopAtEnd) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("$m мин")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.bible_audio_sleep_stop_after_chapter))
                    Switch(checked = stopAtEnd, onCheckedChange = { stopAtEnd = it })
                }
                TextButton(
                    onClick = {
                        BibleAudioPlayer.cancelSleepTimer()
                        onDismiss()
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(stringResource(R.string.bible_audio_sleep_off))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        dismissButton = {},
    )
}

/** Компактная панель паузы и остановки озвучки (редактор заметок, без полной [BibleAudioBar]). */
@Composable
fun BibleAudioMiniBar(modifier: Modifier = Modifier) {
    val ps by BibleAudioPlayer.state.collectAsState()
    val isActive = ps.bookId.isNotBlank() || ps.isLoading
    AnimatedVisibility(
        visible = isActive,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier,
    ) {
        val bookName = BibleCanon.byId(ps.bookId)?.abbrRu ?: ps.bookId
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ps.isLoading) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                } else {
                    FilledTonalIconButton(
                        onClick = { BibleAudioPlayer.togglePlay() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            if (ps.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(
                                if (ps.isPlaying) R.string.audio_pause else R.string.audio_resume,
                            ),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        if (bookName.isNotBlank() && ps.chapter > 0) {
                            "$bookName ${ps.chapter}"
                        } else {
                            stringResource(R.string.verse_action_play_narration)
                        }
                        ,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (ps.isPlaying) {
                            stringResource(R.string.bible_audio_playing_hint)
                        } else {
                            stringResource(R.string.bible_audio_paused_hint)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
                IconButton(onClick = { stopReaderChapterAudio() }) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = stringResource(R.string.audio_stop),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun formatSignedSec(ms: Int): String {
    val s = ms / 1000
    return when {
        s > 0 -> "+${s}с"
        s < 0 -> "${s}с"
        else -> "0с"
    }
}

private fun formatMs(ms: Int): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private fun formatSleepCountdown(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Общая рамка панели: в ландшафте высота ограничена, чтобы над плеером
 * оставалась колонка текста. Лишние контролы прокручиваются внутри панели.
 */
@Composable
private fun AudioBarFrame(
    landscape: Boolean,
    collapsed: Boolean,
    onBarHeightChanged: ((Dp) -> Unit)?,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val incoming = maxHeight.value.takeIf { it.isFinite() }?.toInt() ?: 0
        val capModifier = if (landscape) {
            Modifier
                .heightIn(max = landscapeAudioBarMaxHeightDp(incoming, collapsed).dp)
                .verticalScroll(scroll)
        } else {
            Modifier
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(capModifier)
                .animateContentSize()
                .onGloballyPositioned { coords ->
                    onBarHeightChanged?.invoke(with(density) { coords.size.height.toDp() })
                },
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
            shadowElevation = 8.dp,
            tonalElevation = 3.dp,
        ) {
            content()
        }
    }
}

@Composable
private fun BibleAudioCollapseButton(
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (collapsed) "Развернуть плеер" else "Свернуть плеер",
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
internal fun BibleAudioCollapsedBar(
    title: String,
    isPlaying: Boolean,
    isLoading: Boolean,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
        } else {
            FilledTonalIconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.audio_pause else R.string.audio_resume,
                    ),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BibleAudioCollapseButton(collapsed = true, onClick = onExpand)
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Закрыть",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Одна горизонтальная лента режимов: в ландшафте вторая строка панели, без роста вниз. */
@Composable
private fun ChapterAudioLandscapeOptions(
    repeatMode: BibleAudioRepeatMode,
    repeatTargetCount: Int,
    continueChapters: Boolean,
    verseRepeatConfig: BibleVerseRepeatConfig?,
    playbackSpeed: Float,
    startOffsetMs: Int,
    endOffsetMs: Int,
    showOffsetAdjust: Boolean,
    onToggleOffsetAdjust: () -> Unit,
    sleepRemaining: Long?,
    sleepAwaitChapter: Boolean,
    error: String?,
    onOpenRepeatDialog: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.bible_audio_speed),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SPEED_STEPS.forEach { s ->
            FilterChip(
                selected = kotlin.math.abs(playbackSpeed - s) < 0.01f,
                onClick = { BibleAudioPlayer.setPlaybackSpeed(s) },
                label = {
                    Text(
                        if (s == 1f) "1×" else "${s}×".replace(".0", ""),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
        FilterChip(
            selected = repeatMode == BibleAudioRepeatMode.OFF && !continueChapters && verseRepeatConfig == null,
            onClick = {
                resetReaderVerseRangeToFullChapter()
                BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                BibleAudioPlayer.setContinueChapters(false)
            },
            label = { Text("1 глава", style = MaterialTheme.typography.labelSmall) },
        )
        FilterChip(
            selected = repeatMode == BibleAudioRepeatMode.OFF && continueChapters,
            onClick = {
                resetReaderVerseRangeToFullChapter()
                BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                BibleAudioPlayer.setContinueChapters(true)
            },
            label = { Text("Книга подряд", style = MaterialTheme.typography.labelSmall) },
        )
        FilterChip(
            selected = repeatMode == BibleAudioRepeatMode.CHAPTER,
            onClick = {
                if (repeatMode == BibleAudioRepeatMode.CHAPTER) {
                    BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                } else {
                    resetReaderVerseRangeToFullChapter()
                    BibleAudioPlayer.setContinueChapters(false)
                    BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.CHAPTER, repeatTargetCount)
                }
            },
            label = { Text("🔂 Повтор главы", style = MaterialTheme.typography.labelSmall) },
        )
        FilterChip(
            selected = repeatMode == BibleAudioRepeatMode.BOOK,
            onClick = {
                if (repeatMode == BibleAudioRepeatMode.BOOK) {
                    BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                } else {
                    resetReaderVerseRangeToFullChapter()
                    BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.BOOK, repeatTargetCount)
                }
            },
            label = { Text("🔁 Повтор книги", style = MaterialTheme.typography.labelSmall) },
        )
        FilterChip(
            selected = repeatMode == BibleAudioRepeatMode.VERSES || verseRepeatConfig != null,
            onClick = onOpenRepeatDialog,
            label = {
                val label = verseRepeatConfig?.let { "🔂 Стихи (${it.labelRu})" } ?: "🔂 Стихи…"
                Text(label, style = MaterialTheme.typography.labelSmall)
            },
        )
        if (repeatMode != BibleAudioRepeatMode.OFF) {
            REPEAT_COUNT_STEPS.forEach { count ->
                FilterChip(
                    selected = repeatTargetCount == count,
                    onClick = { BibleAudioPlayer.setRepeatTargetCount(count) },
                    label = {
                        Text(
                            if (count == 0) "∞" else "${count}×",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
        verseRepeatConfig?.let { cfg ->
            Text(
                text = if (repeatMode == BibleAudioRepeatMode.VERSES) {
                    "🔂 ${cfg.labelRu}"
                } else {
                    "🎧 ${cfg.labelRu}"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            FilterChip(
                selected = repeatMode == BibleAudioRepeatMode.VERSES,
                onClick = {
                    if (repeatMode == BibleAudioRepeatMode.VERSES) {
                        BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                        BibleAudioPlayer.setVerseRepeatConfig(cfg)
                    } else {
                        BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.VERSES, repeatTargetCount)
                        BibleAudioPlayer.setVerseRepeatConfig(cfg)
                        restartActiveVerseRepeatIfConfigured(context)
                    }
                },
                label = {
                    Text(
                        if (repeatMode == BibleAudioRepeatMode.VERSES) "Повтор вкл" else "Повторять",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
            AssistChip(
                onClick = onToggleOffsetAdjust,
                leadingIcon = {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                },
                label = {
                    val hasOff = startOffsetMs != 0 || endOffsetMs != 0
                    Text(
                        if (hasOff) {
                            "Границы (${formatSignedSec(startOffsetMs)}/${formatSignedSec(endOffsetMs)})"
                        } else {
                            "Границы ±2с"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
            if (showOffsetAdjust) {
                Text(
                    "Начало ${formatSignedSec(startOffsetMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                TextButton(
                    onClick = { nudgeReaderVerseLoopStart(-2_000) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 26.dp),
                ) { Text("−2с", style = MaterialTheme.typography.labelSmall) }
                TextButton(
                    onClick = { nudgeReaderVerseLoopStart(2_000) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 26.dp),
                ) { Text("+2с", style = MaterialTheme.typography.labelSmall) }
                Text(
                    "Конец ${formatSignedSec(endOffsetMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                TextButton(
                    onClick = { BibleAudioPlayer.adjustSegmentLoopEndOffsetMs(-2_000) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 26.dp),
                ) { Text("−2с", style = MaterialTheme.typography.labelSmall) }
                TextButton(
                    onClick = { BibleAudioPlayer.adjustSegmentLoopEndOffsetMs(2_000) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 26.dp),
                ) { Text("+2с", style = MaterialTheme.typography.labelSmall) }
            }
            TextButton(
                onClick = {
                    resetReaderVerseRangeToFullChapter()
                    BibleAudioPlayer.setRepeatMode(BibleAudioRepeatMode.OFF)
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 28.dp),
            ) {
                Text("Вся глава", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (sleepRemaining != null) {
            Text(
                stringResource(R.string.bible_audio_sleep_remaining, formatSleepCountdown(sleepRemaining)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        if (sleepAwaitChapter) {
            Text(
                stringResource(R.string.bible_audio_sleep_after_chapter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
            )
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
            )
        }
    }
}

@Preview(name = "Плеер свёрнут", widthDp = 360, heightDp = 96, showBackground = true)
@Preview(
    name = "Плеер свёрнут, тёмная",
    widthDp = 360,
    heightDp = 96,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun BibleAudioCollapsedBarPreview() {
    BibleTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            BibleAudioCollapsedBar(
                title = "Бытие 1 · Бондаренко",
                isPlaying = true,
                isLoading = false,
                onTogglePlay = {},
                onExpand = {},
                onClose = {},
            )
        }
    }
}

@Preview(name = "Ландшафт: текст над плеером", widthDp = 800, heightDp = 280, showBackground = true)
@Preview(
    name = "Ландшафт: текст над плеером, тёмная",
    widthDp = 800,
    heightDp = 280,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun BibleAudioLandscapeStackPreview() {
    BibleTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Текст главы над плеером",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            AudioBarFrame(
                landscape = true,
                collapsed = false,
                onBarHeightChanged = null,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = {},
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "Пауза",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            "Бытие 1 · Бондаренко",
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        BibleAudioCollapseButton(collapsed = false, onClick = {})
                        IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть", modifier = Modifier.size(16.dp))
                        }
                    }
                    ChapterAudioLandscapeOptions(
                        repeatMode = BibleAudioRepeatMode.OFF,
                        repeatTargetCount = 0,
                        continueChapters = false,
                        verseRepeatConfig = null,
                        playbackSpeed = 1f,
                        startOffsetMs = 0,
                        endOffsetMs = 0,
                        showOffsetAdjust = false,
                        onToggleOffsetAdjust = {},
                        sleepRemaining = null,
                        sleepAwaitChapter = false,
                        error = null,
                        onOpenRepeatDialog = {},
                    )
                }
            }
        }
    }
}
