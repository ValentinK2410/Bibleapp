package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.R
import com.example.bible.data.COMMENTARY_SOURCES
import com.example.bible.data.CrossReference
import com.example.bible.data.StrongWord
import com.example.bible.data.TranslationId
import com.example.bible.data.VerseComparison

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudyToolsSheet(
    translation: TranslationId,
    bookId: String,
    bookName: String,
    chapter: Int,
    verse: Int,
    viewModel: BibleViewModel,
    onDismiss: () -> Unit,
    onNavigateToVerse: ((String, Int, Int) -> Unit)? = null,
    totalVerses: Int = 0,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isLoading by viewModel.studyLoading.collectAsState()
    val commentary by viewModel.onlineCommentary.collectAsState()
    val comparisons by viewModel.verseComparisons.collectAsState()
    val crossRefs by viewModel.crossReferences.collectAsState()
    val strongWords by viewModel.strongWords.collectAsState()
    val speakStudy = rememberStudyTextToSpeech(translation)
    val speakComparisons = rememberComparisonSpeech()

    var currentVerse by remember { mutableIntStateOf(verse) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Комментарии", "Переводы", "Ссылки", "Стронг")

    val maxVerse = if (totalVerses > 0) totalVerses else 176

    fun reloadCurrentTab() {
        when (selectedTab) {
            1 -> viewModel.loadVerseComparison(bookId, chapter, currentVerse)
            2 -> viewModel.loadCrossReferences(bookId, chapter, currentVerse)
            3 -> viewModel.loadStrongNumbers(bookId, chapter, currentVerse)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.clearStudyData()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (currentVerse > 1) {
                            currentVerse--
                            reloadCurrentTab()
                        }
                    },
                    enabled = currentVerse > 1,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Предыдущий стих",
                        tint = if (currentVerse > 1) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    )
                }
                Text(
                    "$bookName $chapter:$currentVerse",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = {
                        if (currentVerse < maxVerse) {
                            currentVerse++
                            reloadCurrentTab()
                        }
                    },
                    enabled = currentVerse < maxVerse,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Следующий стих",
                        tint = if (currentVerse < maxVerse) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = {
                    viewModel.clearStudyData()
                    onDismiss()
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Закрыть")
                }
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            when (index) {
                                0 -> {}
                                1 -> viewModel.loadVerseComparison(bookId, chapter, currentVerse)
                                2 -> viewModel.loadCrossReferences(bookId, chapter, currentVerse)
                                3 -> viewModel.loadStrongNumbers(bookId, chapter, currentVerse)
                            }
                        },
                        text = { Text(title, maxLines = 1) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (selectedTab) {
                    0 -> CommentaryTab(
                        bookName = bookName,
                        bookId = bookId,
                        chapter = chapter,
                        verse = currentVerse,
                        commentary = commentary,
                        isLoading = isLoading,
                        onLoad = { slug -> viewModel.loadOnlineCommentary(slug, bookId, chapter) },
                        tts = speakStudy,
                        viewModel = viewModel,
                    )
                    1 -> ComparisonTab(
                        bookId = bookId,
                        chapter = chapter,
                        verse = currentVerse,
                        comparisons = comparisons,
                        isLoading = isLoading,
                        comparisonTts = speakComparisons,
                        viewModel = viewModel,
                    )
                    2 -> CrossRefsTab(
                        bookId = bookId,
                        chapter = chapter,
                        verse = currentVerse,
                        refs = crossRefs,
                        isLoading = isLoading,
                        onNavigateToVerse = onNavigateToVerse,
                        viewModel = viewModel,
                    )
                    3 -> StrongTab(
                        bookId = bookId,
                        chapter = chapter,
                        verse = currentVerse,
                        words = strongWords,
                        isLoading = isLoading,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentaryTab(
    bookName: String,
    bookId: String,
    chapter: Int,
    verse: Int,
    commentary: String,
    isLoading: Boolean,
    onLoad: (String) -> Unit,
    tts: BibleVoiceTts,
    viewModel: BibleViewModel,
) {
    var selectedSource by remember { mutableStateOf<String?>(null) }

    if (commentary.isBlank() || selectedSource == null) {
        Text(
            "Комментарий на главу $chapter (стих $verse):",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            COMMENTARY_SOURCES.forEachIndexed { index, src ->
                val isSelected = selectedSource == src.id
                val cachedOffline = viewModel.hasCachedChapterCommentary(src.urlSlug, bookId, chapter)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedSource = src.id
                            onLoad(src.urlSlug)
                        }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        src.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (cachedOffline) {
                        Icon(
                            Icons.Filled.CloudDone,
                            contentDescription = stringResource(R.string.study_cached_offline),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index < COMMENTARY_SOURCES.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp,
                    )
                }
            }
        }
        if (isLoading) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(Modifier.size(20.dp).padding(2.dp), strokeWidth = 2.dp)
        } else if (selectedSource != null && commentary.isBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Комментарий не найден",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                .clickable { selectedSource = null }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val name = COMMENTARY_SOURCES.find { it.id == selectedSource }?.name ?: ""
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Изменить",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        val slug = COMMENTARY_SOURCES.find { it.id == selectedSource }?.urlSlug
        if (slug != null && viewModel.hasCachedChapterCommentary(slug, bookId, chapter)) {
            StudyCacheBadgeRow()
            Spacer(Modifier.height(8.dp))
        }
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            val speechText = buildCommentarySpeechText(bookName, chapter, verse, commentary)
            FilledTonalButton(
                onClick = { tts.speak(speechText) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.verse_action_speak))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { tts.stop() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.audio_stop))
            }
            Spacer(Modifier.height(8.dp))
            val verseSection = extractVerseSection(commentary, verse)
            if (verseSection != null) {
                Text(
                    "Стих $verse:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    verseSection,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Полный комментарий на главу:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                commentary,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun buildCommentarySpeechText(
    bookName: String,
    chapter: Int,
    verse: Int,
    commentary: String,
): String {
    val verseSection = extractVerseSection(commentary, verse)
    return buildString {
        append("Комментарий к ")
        append(bookName)
        append(", глава ")
        append(chapter)
        append(", стих ")
        append(verse)
        append(". ")
        if (verseSection != null) {
            append("Отрывок к стиху: ")
            append(verseSection.trim())
            append(". ")
        }
        append(commentary.trim())
    }
}

@Composable
private fun StudyCacheBadgeRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.CloudDone,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.study_cached_offline),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun extractVerseSection(commentary: String, verse: Int): String? {
    val lines = commentary.lines()
    val versePatterns = listOf(
        Regex("^\\s*${verse}[.)]\\s"),
        Regex("^\\s*${verse}\\s*[-–—]\\s"),
        Regex("^\\s*Стих\\s+${verse}[.:]?\\s", RegexOption.IGNORE_CASE),
        Regex("^\\s*${verse}\\.\\s"),
    )
    val nextVersePatterns = listOf(
        Regex("^\\s*${verse + 1}[.)]\\s"),
        Regex("^\\s*${verse + 1}\\s*[-–—]\\s"),
        Regex("^\\s*Стих\\s+${verse + 1}[.:]?\\s", RegexOption.IGNORE_CASE),
        Regex("^\\s*${verse + 1}\\.\\s"),
    )

    var startIdx = -1
    for (i in lines.indices) {
        if (versePatterns.any { it.containsMatchIn(lines[i]) }) {
            startIdx = i
            break
        }
    }
    if (startIdx < 0) return null

    var endIdx = lines.size
    for (i in (startIdx + 1) until lines.size) {
        if (nextVersePatterns.any { it.containsMatchIn(lines[i]) }) {
            endIdx = i
            break
        }
    }

    val section = lines.subList(startIdx, endIdx).joinToString("\n").trim()
    return section.ifBlank { null }
}

@Composable
private fun ComparisonTab(
    bookId: String,
    chapter: Int,
    verse: Int,
    comparisons: List<VerseComparison>,
    isLoading: Boolean,
    comparisonTts: BibleComparisonVoiceTts,
    viewModel: BibleViewModel,
) {
    if (isLoading) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        return
    }
    if (comparisons.isEmpty()) {
        Text(
            "Нажмите на вкладку для загрузки",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (viewModel.hasCachedVerseComparisons(bookId, chapter, verse)) {
        StudyCacheBadgeRow()
        Spacer(Modifier.height(8.dp))
    }
    FilledTonalButton(
        onClick = { comparisonTts.speak(comparisons) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(stringResource(R.string.verse_action_speak))
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { comparisonTts.stop() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Filled.Stop,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(stringResource(R.string.audio_stop))
    }
    Spacer(Modifier.height(8.dp))
    comparisons.forEach { vc ->
        Text(
            vc.translationName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            vc.text,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrossRefsTab(
    bookId: String,
    chapter: Int,
    verse: Int,
    refs: List<CrossReference>,
    isLoading: Boolean,
    onNavigateToVerse: ((String, Int, Int) -> Unit)? = null,
    viewModel: BibleViewModel,
) {
    if (isLoading) {
        CircularProgressIndicator(Modifier.padding(16.dp))
        return
    }
    if (refs.isEmpty()) {
        Text(
            "Нажмите на вкладку для загрузки",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (viewModel.hasCachedCrossReferences(bookId, chapter, verse)) {
        StudyCacheBadgeRow()
        Spacer(Modifier.height(8.dp))
    }
    Text(
        "Параллельные места (${refs.size}):",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        refs.forEach { ref ->
            val isNavigable = ref.bookId != null && ref.chapter > 0
            Text(
                ref.ref,
                style = MaterialTheme.typography.labelSmall,
                color = if (isNavigable) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isNavigable) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    )
                    .clickable(enabled = isNavigable) {
                        onNavigateToVerse?.invoke(ref.bookId!!, ref.chapter, ref.verse)
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun StrongTab(
    bookId: String,
    chapter: Int,
    verse: Int,
    words: List<StrongWord>,
    isLoading: Boolean,
    viewModel: BibleViewModel,
) {
    if (isLoading) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        return
    }
    if (words.isEmpty()) {
        Text(
            "Нажмите на вкладку для загрузки",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (viewModel.hasCachedStrongWords(bookId, chapter, verse)) {
        StudyCacheBadgeRow()
        Spacer(Modifier.height(8.dp))
    }
    words.forEach { w ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                w.original,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (w.transliteration.isNotBlank()) {
                Text(
                    w.transliteration,
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    fontSize = 10.sp,
                )
            }
            Text(
                w.number,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                w.meaning,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(2f),
                fontSize = 12.sp,
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}
