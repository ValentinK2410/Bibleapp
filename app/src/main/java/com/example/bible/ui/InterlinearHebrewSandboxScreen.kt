package com.example.bible.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.BibleCanon
import com.example.bible.data.BibleLibrary
import com.example.bible.data.BiblePreferences
import com.example.bible.data.HebrewInterlinearOfflineGloss
import com.example.bible.data.InterlinearTts
import com.example.bible.data.TranslationId
import kotlinx.coroutines.launch

private const val HEBREW_SANDBOX_FONT_STEP = 0.1f

private fun TextStyle.scaled(by: Float) = copy(fontSize = fontSize * by)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InterlinearHebrewSandboxScreen(
    library: BibleLibrary,
    preferences: BiblePreferences,
    bookId: String,
    chapter: Int,
    initialVerse: Int,
    /** Сразу открыть весь стих в поле (режим подстрочника Винокурова). */
    openAsWholeVerse: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hebrewScale by preferences.interlinearHebrewSandboxTextScale.collectAsStateWithLifecycle(
        initialValue = BiblePreferences.QURAN_READER_TEXT_SCALE_DEFAULT,
    )
    var sandboxOverflowOpen by remember { mutableStateOf(false) }
    val tts = remember { InterlinearTts(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    if (!BibleCanon.isOldTestament(bookId)) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.hebrew_sandbox_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Text(
                stringResource(R.string.hebrew_sandbox_ot_only),
                modifier = Modifier.padding(padding).padding(20.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        return
    }

    val book = remember(bookId) { library.getBook(TranslationId.INTERLINEAR, bookId) }
    val chapterObj = remember(book, chapter) { book?.chapters?.find { it.number == chapter } }
    val versesWithWords = remember(chapterObj) {
        chapterObj?.verses?.filter { !it.interlinearWords.isNullOrEmpty() }.orEmpty()
    }

    var verseIndex by remember(versesWithWords) { mutableIntStateOf(0) }
    LaunchedEffect(versesWithWords, initialVerse) {
        if (versesWithWords.isEmpty()) return@LaunchedEffect
        val idx = versesWithWords.indexOfFirst { it.number == initialVerse }
        verseIndex = if (idx >= 0) idx else 0
    }
    if (verseIndex >= versesWithWords.size) {
        verseIndex = (versesWithWords.size - 1).coerceAtLeast(0)
    }

    val verse = versesWithWords.getOrNull(verseIndex)
    val words = verse?.interlinearWords.orEmpty()
    val verseHebrewJoined = remember(words) {
        words.joinToString(" ") { it.original }
    }
    var focusWholeVerse by remember { mutableStateOf(openAsWholeVerse) }
    var selectedWordIndex by remember(verseIndex) { mutableStateOf<Int?>(null) }
    val selectedWord = selectedWordIndex?.let { words.getOrNull(it) }
    val wordFromVerse = selectedWord?.original.orEmpty()
    var editedWord by remember { mutableStateOf("") }

    LaunchedEffect(verseIndex) {
        focusWholeVerse = openAsWholeVerse
    }

    LaunchedEffect(verseIndex, words, focusWholeVerse, selectedWordIndex, verseHebrewJoined) {
        if (words.isEmpty()) return@LaunchedEffect
        editedWord = when {
            focusWholeVerse -> verseHebrewJoined
            selectedWordIndex != null -> words.getOrNull(selectedWordIndex!!)?.original.orEmpty()
            else -> ""
        }
    }

    val (translationRu, translateFailed) =
        remember(editedWord, words, focusWholeVerse, selectedWordIndex) {
            val t = editedWord.trim()
            if (t.isEmpty()) {
                return@remember Pair<String?, Boolean>(null, false)
            }
            val tr =
                if (focusWholeVerse) {
                    HebrewInterlinearOfflineGloss.verseWordsGlossBlock(words).trim().takeIf { it.isNotEmpty() }
                } else {
                    HebrewInterlinearOfflineGloss.glossForEditedHebrew(t, words, selectedWordIndex)
                }
            if (tr.isNullOrBlank()) Pair(null, true) else Pair(tr, false)
        }

    val lettersEdited = remember(editedWord) { tts.hebrewSandboxLetters(editedWord) }
    val skeleton = remember(editedWord) { tts.hebrewLetterSkeleton(editedWord) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.hebrew_sandbox_title)) },
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
                        IconButton(onClick = { sandboxOverflowOpen = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.hebrew_sandbox_overflow_cd),
                            )
                        }
                        DropdownMenu(
                            expanded = sandboxOverflowOpen,
                            onDismissRequest = { sandboxOverflowOpen = false },
                            modifier = Modifier.widthIn(min = 200.dp),
                        ) {
                            val fontIncOk =
                                hebrewScale < BiblePreferences.QURAN_READER_TEXT_SCALE_MAX - 0.001f
                            val fontDecOk =
                                hebrewScale > BiblePreferences.QURAN_READER_TEXT_SCALE_MIN + 0.001f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = fontIncOk) {
                                        scope.launch {
                                            preferences.setInterlinearHebrewSandboxTextScale(
                                                hebrewScale + HEBREW_SANDBOX_FONT_STEP,
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
                                        scope.launch {
                                            preferences.setInterlinearHebrewSandboxTextScale(
                                                hebrewScale - HEBREW_SANDBOX_FONT_STEP,
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
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (book == null) {
                Text(
                    stringResource(R.string.error_load),
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }
            if (versesWithWords.isEmpty()) {
                Text(
                    stringResource(R.string.hebrew_sandbox_no_interlinear),
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }
            Text(
                stringResource(R.string.hebrew_sandbox_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                book.name + " $chapter",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = {
                            if (verseIndex > 0) verseIndex--
                        },
                        enabled = verseIndex > 0,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.hebrew_sandbox_verse_prev),
                        )
                    }
                    Text(
                        stringResource(R.string.hebrew_sandbox_verse_label, versesWithWords[verseIndex].number),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(
                        onClick = {
                            if (verseIndex < versesWithWords.lastIndex) verseIndex++
                        },
                        enabled = verseIndex < versesWithWords.lastIndex,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.hebrew_sandbox_verse_next),
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.hebrew_sandbox_pick_word),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    words.forEachIndexed { i, w ->
                        val sel = !focusWholeVerse && selectedWordIndex == i
                        Text(
                            text = w.original,
                            style = MaterialTheme.typography.titleMedium.scaled(hebrewScale),
                            modifier = Modifier
                                .clickable {
                                    focusWholeVerse = false
                                    selectedWordIndex = i
                                    tts.speakHebrew(w.original)
                                }
                                .then(
                                    if (sel) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(6.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (words.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            focusWholeVerse = true
                            selectedWordIndex = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.hebrew_sandbox_whole_verse))
                    }
                }
            }
            val baselineHebrew = if (focusWholeVerse) verseHebrewJoined else wordFromVerse
            val showEditor = focusWholeVerse || selectedWord != null
            if (showEditor) {
                Text(
                    stringResource(
                        if (focusWholeVerse) {
                            R.string.hebrew_sandbox_selected_verse
                        } else {
                            R.string.hebrew_sandbox_selected
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    stringResource(R.string.hebrew_sandbox_in_verse),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        baselineHebrew,
                        style = MaterialTheme.typography.headlineSmall.scaled(hebrewScale),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tts.speakHebrew(baselineHebrew) },
                        textAlign = TextAlign.Start,
                    )
                    OutlinedTextField(
                        value = editedWord,
                        onValueChange = { editedWord = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.hebrew_sandbox_word_edit_label)) },
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            textDirection = TextDirection.Rtl,
                            textAlign = TextAlign.Start,
                        ).scaled(hebrewScale),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                        ),
                        trailingIcon = {
                            IconButton(onClick = { tts.speakHebrew(editedWord) }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = stringResource(R.string.hebrew_sandbox_speak_field_cd),
                                )
                            }
                        },
                        minLines = if (focusWholeVerse) 3 else 1,
                        maxLines = if (focusWholeVerse) 10 else 1,
                        singleLine = !focusWholeVerse,
                    )
                }
                if (editedWord != baselineHebrew) {
                    TextButton(onClick = { editedWord = baselineHebrew }) {
                        Text(stringResource(R.string.hebrew_sandbox_reset_word))
                    }
                }
                if (lettersEdited.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.hebrew_sandbox_letters) + ": ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Text(
                                    lettersEdited.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall.scaled(hebrewScale),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start,
                                )
                            }
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ),
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.hebrew_sandbox_analysis_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.hebrew_sandbox_grapheme_count, lettersEdited.size),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(
                                R.string.hebrew_sandbox_niqqud,
                                stringResource(
                                    if (tts.containsHebrewNiqqud(editedWord)) {
                                        R.string.hebrew_sandbox_niqqud_yes
                                    } else {
                                        R.string.hebrew_sandbox_niqqud_no
                                    },
                                ),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.hebrew_sandbox_skeleton_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                skeleton.ifBlank { "—" },
                                style = MaterialTheme.typography.titleMedium.scaled(hebrewScale),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                            )
                        }
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.hebrew_sandbox_translation_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when {
                            translateFailed -> {
                                Text(
                                    stringResource(R.string.hebrew_sandbox_translation_error),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            !translationRu.isNullOrBlank() -> {
                                Text(
                                    translationRu!!,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.hebrew_sandbox_translation_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { tts.speakHebrew(editedWord) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.hebrew_sandbox_speak_word), maxLines = 2)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { tts.speakHebrewLettersSequential(editedWord) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.hebrew_sandbox_speak_letters_seq), maxLines = 2)
                    }
                }
                OutlinedButton(
                    onClick = { tts.speakHebrewLettersSpaced(editedWord) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.hebrew_sandbox_speak_letters_spaced))
                }
            } else {
                Text(
                    stringResource(R.string.hebrew_sandbox_tap_word_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}
