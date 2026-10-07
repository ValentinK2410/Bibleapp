package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.R
import com.example.bible.data.BibleBook
import com.example.bible.data.BibleCanon
import com.example.bible.data.BibleCoverage
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
        var verseCounts by remember(translation, bookId) { mutableStateOf(emptyMap<Int, Int>()) }
        LaunchedEffect(translation, bookId) {
            verseCounts = viewModel.chapterVerseCounts(bookId, translation)
        }
        val (readChapters, listenedChapters) = rememberPassageCoverageChapters(
            viewModel = viewModel,
            translation = translation,
            bookId = bookId,
        )
        ChapterGrid(
            modifier = modifier.fillMaxSize(),
            book = book,
            bookId = bookId,
            chaptersWithAudio = chaptersWithAudio,
            verseCounts = verseCounts,
            readChapters = readChapters,
            listenedChapters = listenedChapters,
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
                bookId = bookId,
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

/**
 * Строка-навигация под панелью: «Книга › Глава N». Название книги не обрезается,
 * как это было в заголовке панели рядом с иконками.
 */
@Composable
internal fun PassagePickerBreadcrumbs(
    bookId: String,
    translation: TranslationId,
    chapter: Int,
    onBookClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canon = BibleCanon.byId(bookId)
    val accent = canon?.group?.let { groupTextColor(it) } ?: MaterialTheme.colorScheme.primary
    val bookTitle = passagePickerBookTitle(bookId, translation)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Text(
            text = bookTitle,
            color = accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (chapter > 0) {
                Modifier.clickable(onClick = onBookClick)
            } else {
                Modifier
            },
        )
        if (chapter > 0) {
            Text(
                text = "›",
                color = accent.copy(alpha = 0.6f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "глава $chapter",
                color = accent.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.weight(1f))
        val hint = when {
            chapter > 0 -> "выберите стих"
            canon != null -> "${canon.chapters} ${chapterCountSuffix(canon.chapters)}"
            else -> ""
        }
        if (hint.isNotEmpty()) {
            Text(
                text = hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

private fun chapterCountSuffix(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "глав"
        mod10 == 1 -> "глава"
        mod10 in 2..4 -> "главы"
        else -> "глав"
    }
}

/** Прочитанные и прослушанные номера глав этой книги в текущем переводе. */
@Composable
internal fun rememberPassageCoverageChapters(
    viewModel: BibleViewModel,
    translation: TranslationId,
    bookId: String,
): Pair<Set<Int>, Set<Int>> {
    val readKeys by viewModel.coverageReadChapters.collectAsStateWithLifecycle()
    val listenKeys by viewModel.coverageListenChapters.collectAsStateWithLifecycle()
    val listenTrack = if (translation == TranslationId.INTERLINEAR) {
        if (BibleCanon.isOldTestament(bookId)) BibleCoverage.HEBREW else BibleCoverage.GREEK
    } else {
        translation.code
    }
    val readChapters = remember(readKeys, translation, bookId) {
        BibleCoverage.chaptersMarked(readKeys, translation.code, bookId)
    }
    val listenedChapters = remember(listenKeys, listenTrack, bookId) {
        BibleCoverage.chaptersMarked(listenKeys, listenTrack, bookId)
    }
    return readChapters to listenedChapters
}

internal fun passagePickerBookTitle(bookId: String, translation: TranslationId): String =
    com.example.bible.data.BibleCanon.byId(bookId)?.let {
        com.example.bible.data.BibleCanon.displayName(it, translation)
    } ?: bookId
