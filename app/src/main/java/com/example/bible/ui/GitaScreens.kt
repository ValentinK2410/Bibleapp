package com.example.bible.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.GitaChapter
import com.example.bible.data.GitaChapterSummary
import com.example.bible.data.GitaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitaChapterListScreen(
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { GitaRepository(context.applicationContext) }
    var chapters by remember { mutableStateOf<List<GitaChapterSummary>?>(null) }
    LaunchedEffect(Unit) {
        chapters = withContext(Dispatchers.IO) { repo.loadIndex() }
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
                items(list, key = { it.id }) { chapter ->
                    ScriptureChapterCell(
                        number = chapter.id.toString(),
                        caption = "${chapter.totalVerses} шлок",
                        accent = accent,
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
