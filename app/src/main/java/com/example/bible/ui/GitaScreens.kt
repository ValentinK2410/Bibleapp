package com.example.bible.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.example.bible.R
import com.example.bible.data.GitaAudio
import com.example.bible.data.GitaChapter
import com.example.bible.data.GitaChapterSummary
import com.example.bible.data.GitaRepository
import com.example.bible.data.GitaSearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitaChapterListScreen(
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { GitaRepository(context.applicationContext) }
    var chapters by remember { mutableStateOf<List<GitaChapterSummary>?>(null) }
    var readyCount by remember { mutableIntStateOf(0) }
    var downloading by remember { mutableStateOf(false) }
    var downloadedNow by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        chapters = withContext(Dispatchers.IO) { repo.loadIndex() }
        readyCount = withContext(Dispatchers.IO) { GitaAudio.readyCount(context) }
    }
    val accent = MaterialTheme.colorScheme.tertiary
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_gita_card_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.gita_search_cd))
                    }
                },
            )
        },
    ) { padding ->
        val list = chapters
        when {
            list == null -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "download", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Button(
                            onClick = {
                                if (downloading || readyCount >= 18) return@Button
                                downloading = true
                                downloadedNow = readyCount
                                scope.launch {
                                    var failed = false
                                    for (id in 1..18) {
                                        val saved = withContext(Dispatchers.IO) {
                                            GitaAudio.downloadChapter(context, id)
                                        }
                                        if (!saved) {
                                            failed = true
                                            break
                                        }
                                        val count = withContext(Dispatchers.IO) { GitaAudio.readyCount(context) }
                                        downloadedNow = count
                                        readyCount = count
                                    }
                                    downloading = false
                                    if (failed) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.gita_download_audio_failed),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            },
                            enabled = !downloading && readyCount < 18,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (readyCount >= 18) {
                                    stringResource(R.string.gita_download_audio_done)
                                } else {
                                    stringResource(R.string.gita_download_audio)
                                },
                            )
                        }
                        if (downloading) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                stringResource(R.string.gita_download_audio_progress, downloadedNow, 18),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                items(list, key = { it.id }) { chapter ->
                    val saved = readyCount >= 18 || GitaAudio.isReady(context, chapter.id)
                    ScriptureChapterCell(
                        number = chapter.id.toString(),
                        caption = "${chapter.totalVerses} шлок",
                        accent = accent,
                        marked = saved,
                        icon = if (saved) Icons.Filled.Headphones else null,
                        onClick = { onOpenChapter(chapter.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitaChapterScreen(
    chapterId: Int,
    onBack: () -> Unit,
    onOpenVerse: (Int) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { GitaRepository(context.applicationContext) }
    var chapter by remember(chapterId) { mutableStateOf<GitaChapter?>(null) }
    var failed by remember(chapterId) { mutableStateOf(false) }
    LaunchedEffect(chapterId) {
        failed = false
        val loaded = withContext(Dispatchers.IO) { repo.loadChapter(chapterId) }
        chapter = loaded
        failed = loaded == null
    }
    val accent = MaterialTheme.colorScheme.tertiary
    val title = chapter?.summary?.transliteration ?: "Глава $chapterId"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val content = chapter
        when {
            content == null && !failed -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            content == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Не удалось открыть главу", style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(content.verses, key = { it.id }) { verse ->
                    ScriptureVerseCell(
                        number = verse.id.toString(),
                        accent = accent,
                        onClick = { onOpenVerse(verse.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitaVerseScreen(
    chapterId: Int,
    verseId: Int,
    onBack: () -> Unit,
    onOpenPassage: (chapterId: Int, verseId: Int) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { GitaRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var chapter by remember(chapterId) { mutableStateOf<GitaChapter?>(null) }
    var failed by remember(chapterId) { mutableStateOf(false) }
    var verseTotals by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    LaunchedEffect(chapterId) {
        failed = false
        val loaded = withContext(Dispatchers.IO) { repo.loadChapter(chapterId) }
        chapter = loaded
        failed = loaded == null
        if (verseTotals.isEmpty()) {
            verseTotals = withContext(Dispatchers.IO) {
                repo.loadIndex().associate { it.id to it.totalVerses }
            }
        }
    }
    var playing by remember(chapterId) { mutableStateOf(GitaAudio.playingChapterId() == chapterId) }
    DisposableEffect(chapterId) {
        onDispose {
            if (GitaAudio.playingChapterId() == chapterId) GitaAudio.stop()
        }
    }
    val verses = chapter?.verses.orEmpty()
    val openedIndex = verses.indexOfFirst { it.id == verseId }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(chapterId, verseId, verses.size) {
        if (verses.isNotEmpty()) listState.scrollToItem(openedIndex + 1)
    }
    val shownIndex = if (verses.isEmpty()) {
        0
    } else {
        (listState.firstVisibleItemIndex - 1).coerceIn(0, verses.lastIndex)
    }
    val shownVerseId = verses.getOrNull(shownIndex)?.id ?: verseId
    val title = chapter?.summary?.transliteration?.let { "$it · $shownVerseId" } ?: "Глава $chapterId · $shownVerseId"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (GitaAudio.isReady(context, chapterId)) {
                        IconButton(
                            onClick = {
                                if (playing) {
                                    GitaAudio.stop()
                                    playing = false
                                } else {
                                    playing = GitaAudio.play(context, chapterId)
                                }
                            },
                        ) {
                            Icon(
                                if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (playing) R.string.gita_stop_audio else R.string.gita_play_audio,
                                ),
                            )
                        }
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.gita_search_cd))
                    }
                },
            )
        },
        bottomBar = {
            if (verses.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            if (shownIndex > 0) {
                                scope.launch { listState.animateScrollToItem(shownIndex) }
                            } else if (chapterId > 1) {
                                val previousLast = verseTotals[chapterId - 1] ?: 1
                                onOpenPassage(chapterId - 1, previousLast)
                            }
                        },
                        enabled = shownIndex > 0 || chapterId > 1,
                    ) {
                        Text(if (shownIndex > 0) "Предыдущий стих" else "Предыдущая глава")
                    }
                    TextButton(
                        onClick = {
                            if (shownIndex < verses.lastIndex) {
                                scope.launch { listState.animateScrollToItem(shownIndex + 2) }
                            } else if (chapterId < 18) {
                                onOpenPassage(chapterId + 1, 1)
                            }
                        },
                        enabled = shownIndex < verses.lastIndex || chapterId < 18,
                    ) {
                        Text(if (shownIndex < verses.lastIndex) "Следующий стих" else "Следующая глава")
                    }
                }
            }
        },
    ) { padding ->
        when {
            chapter == null && !failed -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            verses.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Не удалось открыть стих", style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        chapter?.summary?.translation.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                items(verses, key = { it.id }) { verse ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                verse.id.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            if (verse.sanskrit.isNotBlank()) {
                                Text(verse.sanskrit, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (verse.transliteration.isNotBlank()) {
                                Text(
                                    verse.transliteration,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val russian = verse.translationRu.ifBlank { verse.translationEn }
                            if (russian.isNotBlank()) {
                                Text(russian, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitaSearchScreen(
    onBack: () -> Unit,
    onOpenHit: (chapterId: Int, verseId: Int) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { GitaRepository(context.applicationContext) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<GitaSearchHit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val text = query.trim()
        if (text.length < 2) {
            hits = emptyList()
            busy = false
            return@LaunchedEffect
        }
        busy = true
        delay(250)
        hits = withContext(Dispatchers.IO) { repo.search(text) }
        busy = false
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gita_search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.gita_search_hint)) },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            when {
                query.trim().length < 2 -> Text(
                    stringResource(R.string.gita_search_intro),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                busy && hits.isEmpty() -> CircularProgressIndicator()
                hits.isEmpty() -> Text(
                    stringResource(R.string.gita_search_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(hits, key = { "${it.chapterId}:${it.verseId}" }) { hit ->
                        Card(
                            onClick = { onOpenHit(hit.chapterId, hit.verseId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Глава ${hit.chapterId} · стих ${hit.verseId}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    hit.chapterTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    hit.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
