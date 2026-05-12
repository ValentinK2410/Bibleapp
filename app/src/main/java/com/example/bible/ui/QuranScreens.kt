package com.example.bible.ui

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.BiblePreferences
import com.example.bible.data.InterlinearTts
import com.example.bible.data.QuranAyahAudioApi
import com.example.bible.data.QuranAyahAudioStorage
import com.example.bible.data.QuranAyahStreamingPlayer
import com.example.bible.data.QuranSearchHistoryEntry
import com.example.bible.data.QuranRepository
import com.example.bible.data.QuranSearchHit
import com.example.bible.data.QuranSurahContent
import com.example.bible.data.QuranSurahSummary
import com.example.bible.data.QuranTafsirApi
import com.example.bible.data.QuranTafsirStorage
import com.example.bible.data.QuranTanzilLatinToCyrillic
import com.example.bible.data.QuranVerse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.navigation.NavBackStackEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private fun scaledTextStyle(base: TextStyle, scale: Float): TextStyle =
    base.copy(fontSize = base.fontSize * scale)

private const val QURAN_READER_FONT_STEP = 0.1f

sealed class QuranBulkAyahDownloadUi {
    data class Running(val processed: Int, val total: Int, val savedOk: Int) : QuranBulkAyahDownloadUi()
    data class Finished(val savedOk: Int, val total: Int) : QuranBulkAyahDownloadUi()
}

/** Слова аята по пробелам (исходная огласовка сохраняется для отображения и передаётся в TTS). */
private fun quranArabicWordTokens(arabic: String): List<String> =
    arabic.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

/** MP3 аята: локальный файл или подбор URL (suspend). `false` — нет источника воспроизведения. */
private suspend fun startQuranAyahPlaybackOrToast(
    context: Context,
    ayahPlayer: QuranAyahStreamingPlayer,
    surahNumber: Int,
    verseNumber: Int,
    onError: () -> Unit,
): Boolean {
    val appCtx = context.applicationContext
    val local = QuranAyahAudioStorage.localFileIfReady(appCtx, surahNumber, verseNumber)
    if (local != null) {
        ayahPlayer.playLocalFile(surahNumber, verseNumber, local.absolutePath, onError)
        return true
    }
    val urls = QuranAyahAudioApi.fetchAlafasyAudioUrls(surahNumber, verseNumber)
    if (urls.isNotEmpty()) {
        ayahPlayer.playStreamUrls(urls, surahNumber, verseNumber, onError)
        return true
    }
    return false
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuranArabicTextWordByWord(
    arabic: String,
    textStyle: TextStyle,
    onSpeakWord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val words = remember(arabic) { quranArabicWordTokens(arabic) }
    if (words.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (w in words) {
            Text(
                text = w,
                style = textStyle,
                modifier = Modifier
                    .clickable { onSpeakWord(w) }
                    .padding(vertical = 2.dp, horizontal = 1.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranSurahListScreen(
    repository: QuranRepository,
    navBackStackEntry: NavBackStackEntry,
    onBack: () -> Unit,
    onOpenSurah: (Int) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val context = LocalContext.current
    val listScope = rememberCoroutineScope()
    var showAlphabet by remember { mutableStateOf(false) }
    var offlineContentCacheTick by remember { mutableStateOf(0) }
    var downloadingSurahNumber by remember { mutableStateOf<Int?>(null) }
    var downloadingTafsirSurahNumber by remember { mutableStateOf<Int?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                offlineContentCacheTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(navBackStackEntry) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                offlineContentCacheTick++
            }
        }
        navBackStackEntry.lifecycle.addObserver(observer)
        onDispose { navBackStackEntry.lifecycle.removeObserver(observer) }
    }
    val summaries = repository.loadIndex()
    if (showAlphabet) {
        AlphabetReferenceSheet(onDismiss = { showAlphabet = false })
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quran_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAlphabet = true }) {
                        Icon(Icons.Default.School, contentDescription = stringResource(R.string.quran_alphabet_cd))
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.quran_search_title))
                    }
                },
            )
        },
    ) { padding ->
        if (summaries == null) {
            Text(
                stringResource(R.string.quran_assets_missing),
                modifier = Modifier
                    .padding(padding)
                    .padding(20.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.quran_attribution_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(summaries, key = { it.number }) { s ->
                val appCtx = context.applicationContext
                val fullyCached = remember(s.number, s.totalVerses, offlineContentCacheTick) {
                    QuranAyahAudioStorage.isSurahFullyCached(appCtx, s.number, s.totalVerses)
                }
                val tafsirFullyCached = remember(s.number, s.totalVerses, offlineContentCacheTick) {
                    if (s.totalVerses <= 0) {
                        false
                    } else {
                        (1..s.totalVerses).all { ayah ->
                            QuranTafsirStorage.isVerseCommentaryFullyCached(appCtx, s.number, ayah)
                        }
                    }
                }
                QuranSurahListItem(
                    summary = s,
                    onClick = { onOpenSurah(s.number) },
                    fullyCached = fullyCached,
                    tafsirFullyCached = tafsirFullyCached,
                    isDownloading = downloadingSurahNumber == s.number,
                    isDownloadingTafsir = downloadingTafsirSurahNumber == s.number,
                    onDownloadSurahMp3 = {
                        if (downloadingSurahNumber != null) return@QuranSurahListItem
                        downloadingSurahNumber = s.number
                        val surahNum = s.number
                        listScope.launch(Dispatchers.IO) {
                            val loaded = repository.loadSurah(surahNum)
                            if (loaded == null || loaded.verses.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    downloadingSurahNumber = null
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.quran_surah_not_found),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                return@launch
                            }
                            var ok = 0
                            for (v in loaded.verses) {
                                if (QuranAyahAudioStorage.downloadAyah(appCtx, surahNum, v.number)) {
                                    ok++
                                }
                            }
                            withContext(Dispatchers.Main) {
                                downloadingSurahNumber = null
                                offlineContentCacheTick++
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.quran_surah_list_download_done,
                                        loaded.summary.nameRussian,
                                        ok,
                                        loaded.verses.size,
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                    onDownloadSurahTafsir = {
                        if (downloadingTafsirSurahNumber != null || downloadingSurahNumber != null) {
                            return@QuranSurahListItem
                        }
                        downloadingTafsirSurahNumber = s.number
                        val surahNum = s.number
                        listScope.launch(Dispatchers.IO) {
                            val loaded = repository.loadSurah(surahNum)
                            if (loaded == null || loaded.verses.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    downloadingTafsirSurahNumber = null
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.quran_surah_not_found),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                return@launch
                            }
                            val ok = QuranTafsirStorage.downloadSurahCommentaries(
                                appCtx,
                                surahNum,
                                loaded.verses,
                            )
                            withContext(Dispatchers.Main) {
                                downloadingTafsirSurahNumber = null
                                offlineContentCacheTick++
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.quran_surah_list_tafsir_download_done,
                                        loaded.summary.nameRussian,
                                        ok,
                                        loaded.verses.size,
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun QuranSurahListItem(
    summary: QuranSurahSummary,
    onClick: () -> Unit,
    fullyCached: Boolean,
    tafsirFullyCached: Boolean,
    isDownloading: Boolean,
    isDownloadingTafsir: Boolean,
    onDownloadSurahMp3: () -> Unit,
    onDownloadSurahTafsir: () -> Unit,
) {
    val context = LocalContext.current
    val translitCyrillic = remember(summary.nameTransliteration) {
        QuranTanzilLatinToCyrillic.convert(summary.nameTransliteration)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
            ) {
                Text(
                    "${summary.number}. ${summary.nameRussian}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        summary.nameArabic,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    summary.nameTransliteration,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (translitCyrillic.isNotBlank()) {
                    Text(
                        translitCyrillic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(
                    stringResource(R.string.quran_ayah_count, summary.totalVerses),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (fullyCached) {
                Icon(
                    Icons.Default.Headphones,
                    contentDescription = stringResource(R.string.quran_surah_list_all_saved_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (tafsirFullyCached) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = stringResource(R.string.quran_surah_list_tafsir_all_saved_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            IconButton(
                onClick = onDownloadSurahMp3,
                enabled = !isDownloading && !isDownloadingTafsir,
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(R.string.quran_surah_list_download_cd),
                    )
                }
            }
            IconButton(
                onClick = {
                    if (tafsirFullyCached) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.quran_tafsir_already_cached),
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@IconButton
                    }
                    onDownloadSurahTafsir()
                },
                enabled = !isDownloadingTafsir && !isDownloading,
            ) {
                if (isDownloadingTafsir) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (tafsirFullyCached) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.quran_tafsir_already_cached),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = stringResource(R.string.quran_surah_list_tafsir_download_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranSurahReaderScreen(
    repository: QuranRepository,
    preferences: BiblePreferences,
    surahNumber: Int,
    scrollToVerseNumber: Int? = null,
    onBack: () -> Unit,
    /** Номер аята (как в тексте) для открытия песочницы на этой суре. */
    onOpenArabicSandbox: (verseNumber: Int) -> Unit = { _ -> },
) {
    val context = LocalContext.current
    val readerScope = rememberCoroutineScope()
    var readerOverflowOpen by remember { mutableStateOf(false) }
    var bulkAyahDownload by remember { mutableStateOf<QuranBulkAyahDownloadUi?>(null) }
    val quranTextScale by preferences.quranReaderTextScale.collectAsStateWithLifecycle(
        initialValue = BiblePreferences.QURAN_READER_TEXT_SCALE_DEFAULT,
    )
    val arabicWordByWordTts by preferences.quranArabicWordByWordTts.collectAsStateWithLifecycle(
        initialValue = false,
    )
    val tts = remember { InterlinearTts(context.applicationContext) }
    val ayahPlayer = remember { QuranAyahStreamingPlayer(context) }
    DisposableEffect(Unit) {
        onDispose {
            tts.shutdown()
            ayahPlayer.release()
        }
    }
    val content = repository.loadSurah(surahNumber)
    val titleTranslitCyr = remember(content?.summary?.nameTransliteration) {
        content?.summary?.nameTransliteration?.let { QuranTanzilLatinToCyrillic.convert(it) }.orEmpty()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (content != null) {
                        Column {
                            Text(
                                "${content.summary.number}. ${content.summary.nameRussian}",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                content.summary.nameTransliteration,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (titleTranslitCyr.isNotBlank()) {
                                Text(
                                    titleTranslitCyr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else {
                        Text(stringResource(R.string.quran_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { readerOverflowOpen = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.quran_reader_overflow_cd),
                            )
                        }
                        DropdownMenu(
                            expanded = readerOverflowOpen,
                            onDismissRequest = { readerOverflowOpen = false },
                        ) {
                            val fontIncOk =
                                quranTextScale < BiblePreferences.QURAN_READER_TEXT_SCALE_MAX - 0.001f
                            val fontDecOk =
                                quranTextScale > BiblePreferences.QURAN_READER_TEXT_SCALE_MIN + 0.001f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = fontIncOk) {
                                        readerScope.launch {
                                            preferences.setQuranReaderTextScale(
                                                quranTextScale + QURAN_READER_FONT_STEP,
                                            )
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Text(
                                    stringResource(R.string.quran_font_increase),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (fontIncOk) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = fontDecOk) {
                                        readerScope.launch {
                                            preferences.setQuranReaderTextScale(
                                                quranTextScale - QURAN_READER_FONT_STEP,
                                            )
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Text(
                                    stringResource(R.string.quran_font_decrease),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (fontDecOk) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                                )
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.quran_menu_download_all_ayahs)) },
                                onClick = {
                                    readerOverflowOpen = false
                                    val c = content
                                    if (c != null) {
                                        val total = c.verses.size
                                        bulkAyahDownload = QuranBulkAyahDownloadUi.Running(0, total, 0)
                                        readerScope.launch(Dispatchers.IO) {
                                            var ok = 0
                                            val appCtx = context.applicationContext
                                            c.verses.forEachIndexed { index, v ->
                                                if (QuranAyahAudioStorage.downloadAyah(
                                                        appCtx,
                                                        surahNumber,
                                                        v.number,
                                                    )
                                                ) {
                                                    ok++
                                                }
                                                val done = index + 1
                                                withContext(Dispatchers.Main) {
                                                    bulkAyahDownload =
                                                        QuranBulkAyahDownloadUi.Running(done, total, ok)
                                                }
                                            }
                                            withContext(Dispatchers.Main) {
                                                bulkAyahDownload =
                                                    QuranBulkAyahDownloadUi.Finished(ok, total)
                                            }
                                        }
                                    }
                                },
                                enabled = content != null,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.quran_menu_arabic_sandbox)) },
                                onClick = {
                                    readerOverflowOpen = false
                                    val first = content?.verses?.firstOrNull()?.number ?: 1
                                    onOpenArabicSandbox(first)
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (content == null) {
            Text(
                stringResource(R.string.quran_surah_not_found),
                modifier = Modifier
                    .padding(padding)
                    .padding(20.dp),
                color = MaterialTheme.colorScheme.error,
            )
            return@Scaffold
        }
        QuranSurahBody(
            content = content,
            surahNumber = content.summary.number,
            preferences = preferences,
            tts = tts,
            ayahPlayer = ayahPlayer,
            readerTextScale = quranTextScale,
            arabicWordByWordTts = arabicWordByWordTts,
            scrollToVerseNumber = scrollToVerseNumber,
            onOpenArabicSandbox = onOpenArabicSandbox,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
        val bulkDl = bulkAyahDownload
        if (bulkDl != null) {
            val finished = bulkDl is QuranBulkAyahDownloadUi.Finished
            AlertDialog(
                onDismissRequest = { if (finished) bulkAyahDownload = null },
                properties = DialogProperties(
                    dismissOnBackPress = finished,
                    dismissOnClickOutside = finished,
                ),
                title = { Text(stringResource(R.string.quran_menu_download_all_ayahs)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (bulkDl) {
                            is QuranBulkAyahDownloadUi.Running -> {
                                val frac = if (bulkDl.total > 0) {
                                    bulkDl.processed.toFloat() / bulkDl.total.toFloat()
                                } else {
                                    0f
                                }
                                LinearProgressIndicator(
                                    progress = { frac },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    stringResource(
                                        R.string.quran_download_all_ayahs_processing,
                                        bulkDl.processed,
                                        bulkDl.total,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    stringResource(
                                        R.string.quran_download_all_ayahs_remaining,
                                        (bulkDl.total - bulkDl.processed).coerceAtLeast(0),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    stringResource(
                                        R.string.quran_download_all_ayahs_saved_now,
                                        bulkDl.savedOk,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            is QuranBulkAyahDownloadUi.Finished -> {
                                Text(
                                    stringResource(
                                        R.string.quran_menu_download_all_ayahs_done,
                                        bulkDl.savedOk,
                                        bulkDl.total,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (bulkDl is QuranBulkAyahDownloadUi.Finished) {
                        TextButton(onClick = { bulkAyahDownload = null }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun QuranSurahBody(
    content: QuranSurahContent,
    surahNumber: Int,
    preferences: BiblePreferences,
    tts: InterlinearTts,
    ayahPlayer: QuranAyahStreamingPlayer,
    readerTextScale: Float,
    arabicWordByWordTts: Boolean,
    scrollToVerseNumber: Int? = null,
    onOpenArabicSandbox: (verseNumber: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToVerseNumber, content.verses) {
        val target = scrollToVerseNumber ?: return@LaunchedEffect
        val vi = content.verses.indexOfFirst { it.number == target }
        if (vi >= 0) {
            listState.scrollToItem(vi + 1)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                stringResource(R.string.quran_attribution_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.quran_reader_remote_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        items(content.verses, key = { it.number }) { verse ->
            QuranVerseCard(
                surahNumber = surahNumber,
                verse = verse,
                preferences = preferences,
                tts = tts,
                ayahPlayer = ayahPlayer,
                readerTextScale = readerTextScale,
                arabicWordByWordTts = arabicWordByWordTts,
                onOpenArabicSandbox = onOpenArabicSandbox,
            )
        }
    }
}

private const val QURAN_IBN_KATHIR_DISPLAY_MAX = 14_000

@Composable
private fun QuranVerseCard(
    surahNumber: Int,
    verse: QuranVerse,
    preferences: BiblePreferences,
    tts: InterlinearTts,
    ayahPlayer: QuranAyahStreamingPlayer,
    readerTextScale: Float,
    arabicWordByWordTts: Boolean,
    onOpenArabicSandbox: (verseNumber: Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val translitRu = remember(verse.transliteration) {
        QuranTanzilLatinToCyrillic.convert(verse.transliteration)
    }
    val appCtx = context.applicationContext
    var saadiOpen by remember { mutableStateOf(false) }
    var saadiText by remember { mutableStateOf<String?>(null) }
    var saadiLoading by remember { mutableStateOf(false) }
    LaunchedEffect(saadiOpen) {
        if (!saadiOpen || saadiText != null) return@LaunchedEffect
        saadiLoading = true
        saadiText = QuranTafsirStorage.readSaadi(appCtx, surahNumber, verse.number)
            ?: QuranTafsirApi.fetchSaadiRussian(surahNumber, verse.number)
        saadiLoading = false
    }
    var ikOpen by remember { mutableStateOf(false) }
    var ikText by remember { mutableStateOf<String?>(null) }
    var ikLoading by remember { mutableStateOf(false) }
    LaunchedEffect(ikOpen) {
        if (!ikOpen || ikText != null) return@LaunchedEffect
        ikLoading = true
        val rawCached = QuranTafsirStorage.readIbnKathir(appCtx, surahNumber, verse.number)
        val raw = rawCached ?: QuranTafsirApi.fetchIbnKathirArabic(surahNumber, verse.number)
        ikText = raw?.let { r ->
            if (r.length <= QURAN_IBN_KATHIR_DISPLAY_MAX) {
                r
            } else {
                r.take(QURAN_IBN_KATHIR_DISPLAY_MAX) + "\n\n" + context.getString(R.string.quran_tafsir_truncated_note)
            }
        }
        ikLoading = false
    }
    var commentarySaved by remember(surahNumber, verse.number) {
        mutableStateOf(QuranTafsirStorage.isVerseCommentaryFullyCached(appCtx, surahNumber, verse.number))
    }
    LaunchedEffect(surahNumber, verse.number) {
        commentarySaved = QuranTafsirStorage.isVerseCommentaryFullyCached(appCtx, surahNumber, verse.number)
    }
    var tafsirVerseBusy by remember { mutableStateOf(false) }
    var audioBusy by remember { mutableStateOf(false) }
    var downloadBusy by remember { mutableStateOf(false) }
    var ayahAudioSaved by remember(surahNumber, verse.number) {
        mutableStateOf(
            QuranAyahAudioStorage.isDownloaded(context.applicationContext, surahNumber, verse.number),
        )
    }
    val audioErrorToast: () -> Unit = {
        Toast.makeText(
            context,
            R.string.quran_ayah_audio_error,
            Toast.LENGTH_SHORT,
        ).show()
    }
    val activeAyahKey by ayahPlayer.activeAyahKey.collectAsStateWithLifecycle()
    val isThisAyahMp3Playing = activeAyahKey == surahNumber to verse.number
    var repeatLoopEnabled by remember { mutableStateOf(false) }
    val repeatPauseMs by preferences.quranAyahRepeatPauseMs.collectAsStateWithLifecycle(
        initialValue = BiblePreferences.QURAN_AYAH_REPEAT_PAUSE_MS_DEFAULT,
    )
    val repeatLoopRef = rememberUpdatedState(repeatLoopEnabled)
    val repeatPauseRef = rememberUpdatedState(repeatPauseMs)
    var pauseSliderMs by remember { mutableLongStateOf(repeatPauseMs) }
    LaunchedEffect(repeatPauseMs) {
        pauseSliderMs = repeatPauseMs
    }
    LaunchedEffect(surahNumber, verse.number, repeatLoopEnabled) {
        if (!repeatLoopEnabled) return@LaunchedEffect
        ayahPlayer.ayahPlaybackCompleted.collect { (s, a) ->
            if (s != surahNumber || a != verse.number) return@collect
            delay(repeatPauseRef.value)
            if (!repeatLoopRef.value) return@collect
            if (startQuranAyahPlaybackOrToast(context, ayahPlayer, surahNumber, verse.number, audioErrorToast)) {
                // started
            } else {
                audioErrorToast()
            }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.quran_ayah_number, verse.number),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (isThisAyahMp3Playing) {
                                ayahPlayer.stop()
                                return@IconButton
                            }
                            scope.launch {
                                audioBusy = true
                                val ok = startQuranAyahPlaybackOrToast(
                                    context,
                                    ayahPlayer,
                                    surahNumber,
                                    verse.number,
                                    audioErrorToast,
                                )
                                audioBusy = false
                                if (!ok) audioErrorToast()
                            }
                        },
                        enabled = (!audioBusy && !downloadBusy) || isThisAyahMp3Playing,
                    ) {
                        when {
                            audioBusy && !isThisAyahMp3Playing -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(10.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            isThisAyahMp3Playing -> {
                                Icon(
                                    Icons.Filled.Stop,
                                    contentDescription = stringResource(R.string.quran_ayah_recitation_stop_cd),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                            else -> {
                                Icon(
                                    Icons.Filled.Headphones,
                                    contentDescription = stringResource(R.string.quran_ayah_recitation_cd),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            if (ayahAudioSaved) {
                                Toast.makeText(
                                    context,
                                    R.string.quran_ayah_download_already,
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@IconButton
                            }
                            scope.launch {
                                downloadBusy = true
                                val ok = QuranAyahAudioStorage.downloadAyah(
                                    context.applicationContext,
                                    surahNumber,
                                    verse.number,
                                )
                                downloadBusy = false
                                ayahAudioSaved = QuranAyahAudioStorage.isDownloaded(
                                    context.applicationContext,
                                    surahNumber,
                                    verse.number,
                                )
                                Toast.makeText(
                                    context,
                                    if (ok) R.string.quran_ayah_download_ok else R.string.quran_ayah_download_fail,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        enabled = !downloadBusy && !audioBusy,
                    ) {
                        if (downloadBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(10.dp),
                                strokeWidth = 2.dp,
                            )
                        } else if (ayahAudioSaved) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = stringResource(R.string.quran_ayah_audio_saved_cd),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = stringResource(R.string.quran_ayah_download_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (!repeatLoopEnabled) {
                                if (arabicWordByWordTts) {
                                    tts.speakQuranVerseArabicWordByWord(verse.arabic)
                                } else {
                                    tts.speakQuranVerseArabic(verse.arabic)
                                }
                                return@IconButton
                            }
                            fun scheduleNext() {
                                if (arabicWordByWordTts) {
                                    tts.speakQuranVerseArabicWordByWord(verse.arabic) {
                                        scope.launch {
                                            delay(repeatPauseRef.value)
                                            if (repeatLoopRef.value) scheduleNext()
                                        }
                                    }
                                } else {
                                    tts.speakQuranVerseArabic(verse.arabic) {
                                        scope.launch {
                                            delay(repeatPauseRef.value)
                                            if (repeatLoopRef.value) scheduleNext()
                                        }
                                    }
                                }
                            }
                            scheduleNext()
                        },
                        enabled = verse.arabic.isNotBlank(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(
                                if (arabicWordByWordTts) {
                                    R.string.quran_ayah_speak_arabic_words_cd
                                } else {
                                    R.string.quran_ayah_speak_arabic_cd
                                },
                            ),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (!repeatLoopEnabled) {
                                tts.speakRussian(verse.translationRu)
                                return@IconButton
                            }
                            fun scheduleNext() {
                                tts.speakRussian(verse.translationRu) {
                                    scope.launch {
                                        delay(repeatPauseRef.value)
                                        if (repeatLoopRef.value) scheduleNext()
                                    }
                                }
                            }
                            scheduleNext()
                        },
                        enabled = verse.translationRu.isNotBlank(),
                    ) {
                        Icon(
                            Icons.Filled.Translate,
                            contentDescription = stringResource(R.string.quran_ayah_speak_russian_cd),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Box(
                        modifier = Modifier.then(
                            if (repeatLoopEnabled) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                )
                            } else {
                                Modifier
                            },
                        ),
                    ) {
                        IconButton(
                            onClick = { repeatLoopEnabled = !repeatLoopEnabled },
                        ) {
                            Icon(
                                Icons.Filled.Repeat,
                                contentDescription = stringResource(R.string.quran_ayah_repeat_cd),
                                tint = if (repeatLoopEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(visible = repeatLoopEnabled) {
                val pauseMin = BiblePreferences.QURAN_AYAH_REPEAT_PAUSE_MS_MIN.toFloat()
                val pauseMax = BiblePreferences.QURAN_AYAH_REPEAT_PAUSE_MS_MAX.toFloat()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text(
                        stringResource(
                            R.string.quran_ayah_repeat_pause_label,
                            String.format(Locale.getDefault(), "%.1f", pauseSliderMs / 1000f),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = pauseSliderMs.toFloat().coerceIn(pauseMin, pauseMax),
                        onValueChange = { v ->
                            pauseSliderMs = v.toLong().coerceIn(
                                BiblePreferences.QURAN_AYAH_REPEAT_PAUSE_MS_MIN,
                                BiblePreferences.QURAN_AYAH_REPEAT_PAUSE_MS_MAX,
                            )
                        },
                        onValueChangeFinished = {
                            val v = pauseSliderMs
                            scope.launch { preferences.setQuranAyahRepeatPauseMs(v) }
                        },
                        valueRange = pauseMin..pauseMax,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.quran_label_arabic),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { onOpenArabicSandbox(verse.number) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Filled.School,
                        contentDescription = stringResource(R.string.quran_ayah_open_sandbox_cd),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                stringResource(R.string.quran_arabic_tap_word_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                QuranArabicTextWordByWord(
                    arabic = verse.arabic,
                    textStyle = scaledTextStyle(MaterialTheme.typography.bodyLarge, readerTextScale),
                    onSpeakWord = { w -> tts.speakArabic(w) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                stringResource(R.string.quran_label_transliteration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                translitRu,
                style = scaledTextStyle(MaterialTheme.typography.bodyMedium, readerTextScale),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.quran_label_translation_kuliev),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                verse.translationRu,
                style = scaledTextStyle(MaterialTheme.typography.bodyLarge, readerTextScale),
                color = MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.quran_tafsir_saadi_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { saadiOpen = !saadiOpen },
                )
                IconButton(
                    onClick = {
                        if (commentarySaved) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.quran_tafsir_already_cached),
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@IconButton
                        }
                        scope.launch {
                            tafsirVerseBusy = true
                            val ok = QuranTafsirStorage.downloadVerseCommentaries(
                                appCtx,
                                surahNumber,
                                verse.number,
                            )
                            tafsirVerseBusy = false
                            commentarySaved = QuranTafsirStorage.isVerseCommentaryFullyCached(
                                appCtx,
                                surahNumber,
                                verse.number,
                            )
                            Toast.makeText(
                                context,
                                if (ok) {
                                    context.getString(R.string.quran_tafsir_download_verse_ok)
                                } else {
                                    context.getString(R.string.quran_tafsir_download_partial)
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    enabled = !tafsirVerseBusy,
                ) {
                    if (tafsirVerseBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else if (commentarySaved) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.quran_tafsir_verse_saved_cd),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = stringResource(R.string.quran_tafsir_download_verse_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    if (saadiOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.clickable { saadiOpen = !saadiOpen },
                )
            }
            AnimatedVisibility(visible = saadiOpen) {
                Column(Modifier.padding(top = 6.dp)) {
                    if (saadiLoading && saadiText == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(vertical = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    when {
                        saadiText != null -> {
                            Text(
                                saadiText!!,
                                style = scaledTextStyle(MaterialTheme.typography.bodyMedium, readerTextScale),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        !saadiLoading -> {
                            Text(
                                stringResource(R.string.quran_tafsir_unavailable),
                                color = MaterialTheme.colorScheme.error,
                                style = scaledTextStyle(MaterialTheme.typography.bodyMedium, readerTextScale),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.quran_tafsir_ibn_kathir_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { ikOpen = !ikOpen },
                )
                Icon(
                    if (ikOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.clickable { ikOpen = !ikOpen },
                )
            }
            AnimatedVisibility(visible = ikOpen) {
                Column(Modifier.padding(top = 6.dp)) {
                    if (ikLoading && ikText == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(vertical = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    when {
                        ikText != null -> {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Text(
                                    ikText!!,
                                    style = scaledTextStyle(MaterialTheme.typography.bodyMedium, readerTextScale),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                        !ikLoading -> {
                            Text(
                                stringResource(R.string.quran_tafsir_unavailable),
                                color = MaterialTheme.colorScheme.error,
                                style = scaledTextStyle(MaterialTheme.typography.bodyMedium, readerTextScale),
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.quran_tafsir_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun rememberQuranRepository(): QuranRepository {
    val context = LocalContext.current
    return remember { QuranRepository(context) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranSearchScreen(
    repository: QuranRepository,
    preferences: BiblePreferences,
    onBack: () -> Unit,
    onOpenHit: (surah: Int, verse: Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<QuranSearchHit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var historyMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val searchHistory by preferences.quranSearchHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val minLen = 1
    LaunchedEffect(query) {
        delay(320)
        val qn = repository.normalizeRuQuery(query)
        if (qn.length < minLen) {
            results = emptyList()
            busy = false
            return@LaunchedEffect
        }
        busy = true
        results = withContext(Dispatchers.Default) {
            repository.searchTranslationRu(query, minLength = minLen)
        }
        busy = false
    }
    LaunchedEffect(query) {
        val qn = repository.normalizeRuQuery(query)
        if (qn.length < minLen) return@LaunchedEffect
        delay(1200)
        if (repository.normalizeRuQuery(query) != qn) return@LaunchedEffect
        preferences.appendQuranSearchHistory(query, qn)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quran_search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (searchHistory.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { historyMenu = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.quran_search_history_menu_cd),
                                )
                            }
                            DropdownMenu(
                                expanded = historyMenu,
                                onDismissRequest = { historyMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.quran_search_history_clear)) },
                                    onClick = {
                                        historyMenu = false
                                        scope.launch { preferences.clearQuranSearchHistory() }
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.quran_search_hint)) },
                singleLine = false,
                minLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            if (searchHistory.isNotEmpty()) {
                Text(
                    stringResource(R.string.quran_search_history_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                searchHistory.reversed().take(12).forEach { entry ->
                    QuranSearchHistoryRow(
                        entry = entry,
                        onApply = { query = entry.query },
                        onRemove = {
                            scope.launch { preferences.removeQuranSearchHistoryEntry(entry) }
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(4.dp))
            }
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            val qNorm = repository.normalizeRuQuery(query)
            when {
                qNorm.length < minLen -> {
                    Text(
                        stringResource(R.string.quran_search_intro),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                busy && results.isEmpty() -> {
                    Text(
                        stringResource(R.string.quran_search_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                results.isEmpty() -> {
                    Text(
                        stringResource(R.string.quran_search_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(results, key = { "${it.surahNumber}:${it.verseNumber}" }) { hit ->
                            QuranSearchHitCard(
                                hit = hit,
                                queryRaw = query,
                                repository = repository,
                                onClick = { onOpenHit(hit.surahNumber, hit.verseNumber) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuranSearchHistoryRow(
    entry: QuranSearchHistoryEntry,
    onApply: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        onClick = onApply,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
                Text(
                    entry.query,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatQuranSearchHistoryTime(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.quran_search_history_remove_cd),
                )
            }
        }
    }
}

private fun formatQuranSearchHistoryTime(epochMs: Long): String {
    val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .withLocale(Locale.getDefault())
    return fmt.format(Instant.ofEpochMilli(epochMs))
}

@Composable
private fun QuranSearchHitCard(
    hit: QuranSearchHit,
    queryRaw: String,
    repository: QuranRepository,
    onClick: () -> Unit,
) {
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val annotated = remember(hit.translationRu, queryRaw, highlightBg) {
        quranSearchAnnotatedTranslation(hit.translationRu, queryRaw, repository, highlightBg)
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "${hit.surahNumber}. ${hit.surahNameRu}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.quran_search_result_subtitle, hit.surahNumber, hit.verseNumber),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                annotated,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun mergeIntRanges(ranges: List<IntRange>): List<IntRange> {
    if (ranges.isEmpty()) return emptyList()
    val s = ranges.sortedBy { it.first }
    val out = ArrayList<IntRange>()
    var cur = s[0]
    for (i in 1 until s.size) {
        val r = s[i]
        cur = if (r.first <= cur.last + 1) {
            cur.first..maxOf(cur.last, r.last)
        } else {
            out.add(cur)
            r
        }
    }
    out.add(cur)
    return out
}

private fun quranSearchAnnotatedTranslation(
    translationRu: String,
    queryRaw: String,
    repository: QuranRepository,
    highlightBg: Color,
): AnnotatedString {
    val ranges = mergeIntRanges(repository.highlightRangesInTranslation(translationRu, queryRaw))
    if (ranges.isEmpty() || repository.normalizeRuQuery(queryRaw).isEmpty()) {
        return AnnotatedString(translationRu)
    }
    return buildAnnotatedString {
        var pos = 0
        for (r in ranges) {
            val a = r.first.coerceIn(0, translationRu.length)
            val b = (r.last + 1).coerceIn(0, translationRu.length)
            if (a > b) continue
            if (pos < a) append(translationRu.substring(pos, a))
            if (a < b) {
                withStyle(SpanStyle(background = highlightBg)) {
                    append(translationRu.substring(a, b))
                }
            }
            pos = maxOf(pos, b)
        }
        if (pos < translationRu.length) append(translationRu.substring(pos))
    }
}
