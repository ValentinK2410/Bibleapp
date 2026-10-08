package com.example.bible.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val verse = chapter?.verses?.firstOrNull { it.id == verseId }
    val title = chapter?.summary?.transliteration?.let { "$it · $verseId" } ?: "Глава $chapterId · $verseId"
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
        when {
            chapter == null && !failed -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            verse == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Не удалось открыть стих", style = MaterialTheme.typography.bodyLarge)
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    chapter?.summary?.translation.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
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
