package com.example.bible.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.BibleBook
import com.example.bible.data.BibleVerse
import com.example.bible.data.TranslationId

@Composable
internal fun BiblePassagePickerChapterStep(
    book: BibleBook,
    bookId: String,
    translation: TranslationId,
    viewModel: BibleViewModel,
    narratorId: String,
    downloadTick: Int,
    onChapterSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chaptersWithAudio = remember(translation, bookId, narratorId, downloadTick) {
        val fromAssets = book.chapters.mapNotNull { ch ->
            if (viewModel.hasChapterAudio(translation, bookId, ch.number)) ch.number else null
        }.toSet()
        val eff = com.example.bible.data.narratorForTranslation(translation, narratorId).id
        val downloaded = viewModel.downloadedChaptersFor(eff, bookId)
        fromAssets + downloaded
    }
    if (book.chapters.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.no_chapters_loaded),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
    } else {
        ChapterGrid(
            modifier = modifier.fillMaxSize(),
            book = book,
            bookId = bookId,
            chaptersWithAudio = chaptersWithAudio,
            onChapterClick = onChapterSelected,
        )
    }
}

@Composable
internal fun BiblePassagePickerVerseStep(
    bookId: String,
    chapterNum: Int,
    translation: TranslationId,
    viewModel: BibleViewModel,
    onVerseSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var verseNumbers by remember(bookId, chapterNum, translation) { mutableStateOf<List<Int>?>(null) }
    var loadError by remember(bookId, chapterNum, translation) { mutableStateOf<String?>(null) }
    var reloadTick by remember(bookId, chapterNum, translation) { mutableIntStateOf(0) }

    LaunchedEffect(bookId, chapterNum, translation, reloadTick) {
        verseNumbers = null
        loadError = null
        val numbers = viewModel.verseNumbersForPicker(bookId, chapterNum, translation)
        if (numbers.isEmpty()) {
            loadError = "Нет стихов в этой главе"
        } else {
            verseNumbers = numbers
        }
    }

    when {
        verseNumbers == null && loadError == null -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        verseNumbers != null -> {
            VerseGrid(
                modifier = modifier.fillMaxSize(),
                verses = verseNumbers!!.map { BibleVerse(number = it, text = "") },
                onVerseClick = onVerseSelected,
            )
        }
        else -> {
            Column(
                modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    loadError ?: stringResource(R.string.no_chapters_loaded),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { reloadTick++ }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

internal fun passagePickerBookTitle(bookId: String, translation: TranslationId): String =
    com.example.bible.data.BibleCanon.byId(bookId)?.let {
        com.example.bible.data.BibleCanon.displayName(it, translation)
    } ?: bookId
