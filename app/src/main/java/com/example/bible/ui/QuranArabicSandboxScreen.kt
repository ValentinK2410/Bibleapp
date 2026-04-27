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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.bible.data.BiblePreferences
import com.example.bible.data.InterlinearTts
import com.example.bible.data.QuranArabicSandboxTranslateApi
import com.example.bible.data.QuranRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val QURAN_SANDBOX_FONT_STEP = 0.1f

private fun TextStyle.scaled(by: Float) = copy(fontSize = fontSize * by)

private fun quranWords(arabic: String): List<String> =
    arabic.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuranArabicSandboxScreen(
    repository: QuranRepository,
    preferences: BiblePreferences,
    surahNumber: Int,
    /** Номер аята в сурах (как в JSON); null — с первого аята. */
    initialVerseNumber: Int? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val arabicScale by preferences.quranSandboxArabicTextScale.collectAsStateWithLifecycle(
        initialValue = BiblePreferences.QURAN_READER_TEXT_SCALE_DEFAULT,
    )
    var sandboxOverflowOpen by remember { mutableStateOf(false) }
    val tts = remember { InterlinearTts(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }
    val content = remember(surahNumber) { repository.loadSurah(surahNumber) }
    val verses = content?.verses.orEmpty()
    var verseIndex by remember(verses) { mutableIntStateOf(0) }
    LaunchedEffect(verses, initialVerseNumber) {
        if (verses.isEmpty()) return@LaunchedEffect
        val n = initialVerseNumber
        if (n != null) {
            val idx = verses.indexOfFirst { it.number == n }
            if (idx >= 0) verseIndex = idx
        }
    }
    if (verseIndex >= verses.size) verseIndex = (verses.size - 1).coerceAtLeast(0)

    val verse = verses.getOrNull(verseIndex)
    val words = remember(verse?.arabic) { verse?.arabic?.let { quranWords(it) } ?: emptyList() }
    var selectedWordIndex by remember(verseIndex) { mutableStateOf<Int?>(null) }
    val selectedWord = selectedWordIndex?.let { words.getOrNull(it) }
    val wordFromAyah = selectedWord.orEmpty()
    var editedWord by remember { mutableStateOf("") }
    LaunchedEffect(wordFromAyah, selectedWordIndex) {
        editedWord = wordFromAyah
    }
    var translationRu by remember { mutableStateOf<String?>(null) }
    var translating by remember { mutableStateOf(false) }
    var translateFailed by remember { mutableStateOf(false) }
    LaunchedEffect(editedWord) {
        val t = editedWord.trim()
        if (t.isEmpty()) {
            translationRu = null
            translateFailed = false
            translating = false
            return@LaunchedEffect
        }
        translating = true
        translateFailed = false
        translationRu = null
        delay(450)
        val tr = QuranArabicSandboxTranslateApi.translateArToRu(t)
        translating = false
        if (tr.isNullOrBlank()) {
            translationRu = null
            translateFailed = true
        } else {
            translationRu = tr
        }
    }
    val lettersEdited = remember(editedWord) { tts.arabicSandboxLetters(editedWord) }
    val skeleton = remember(editedWord) { tts.arabicLetterSkeleton(editedWord) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.quran_sandbox_title)) },
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
                                contentDescription = stringResource(R.string.quran_sandbox_overflow_cd),
                            )
                        }
                        DropdownMenu(
                            expanded = sandboxOverflowOpen,
                            onDismissRequest = { sandboxOverflowOpen = false },
                            modifier = Modifier.widthIn(min = 200.dp),
                        ) {
                            val fontIncOk =
                                arabicScale < BiblePreferences.QURAN_READER_TEXT_SCALE_MAX - 0.001f
                            val fontDecOk =
                                arabicScale > BiblePreferences.QURAN_READER_TEXT_SCALE_MIN + 0.001f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = fontIncOk) {
                                        scope.launch {
                                            preferences.setQuranSandboxArabicTextScale(
                                                arabicScale + QURAN_SANDBOX_FONT_STEP,
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
                                            preferences.setQuranSandboxArabicTextScale(
                                                arabicScale - QURAN_SANDBOX_FONT_STEP,
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
            if (content == null || verses.isEmpty()) {
                Text(
                    stringResource(R.string.quran_surah_not_found),
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }
            Text(
                stringResource(R.string.quran_sandbox_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = {
                            selectedWordIndex = null
                            if (verseIndex > 0) verseIndex--
                        },
                        enabled = verseIndex > 0,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.quran_sandbox_ayah_prev),
                        )
                    }
                    Text(
                        stringResource(R.string.quran_sandbox_ayah_label, verses[verseIndex].number),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(
                        onClick = {
                            selectedWordIndex = null
                            if (verseIndex < verses.lastIndex) verseIndex++
                        },
                        enabled = verseIndex < verses.lastIndex,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.quran_sandbox_ayah_next),
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.quran_sandbox_pick_word),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    words.forEachIndexed { i, w ->
                        val sel = selectedWordIndex == i
                        Text(
                            text = w,
                            style = MaterialTheme.typography.titleMedium.scaled(arabicScale),
                            modifier = Modifier
                                .clickable {
                                    selectedWordIndex = i
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
            }
            if (selectedWord != null) {
                Text(
                    stringResource(R.string.quran_sandbox_selected),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    stringResource(R.string.quran_sandbox_in_ayah),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        wordFromAyah,
                        style = MaterialTheme.typography.headlineSmall.scaled(arabicScale),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                    )
                    OutlinedTextField(
                        value = editedWord,
                        onValueChange = { editedWord = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.quran_sandbox_word_edit_label)) },
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            textDirection = TextDirection.Rtl,
                            textAlign = TextAlign.Start,
                        ).scaled(arabicScale),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                        ),
                        trailingIcon = {
                            IconButton(onClick = { tts.speakArabic(editedWord) }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = stringResource(R.string.quran_sandbox_speak_field_cd),
                                )
                            }
                        },
                        singleLine = true,
                    )
                }
                if (editedWord != wordFromAyah) {
                    TextButton(onClick = { editedWord = wordFromAyah }) {
                        Text(stringResource(R.string.quran_sandbox_reset_word))
                    }
                }
                if (lettersEdited.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.quran_sandbox_letters) + ": ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Text(
                                    lettersEdited.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall.scaled(arabicScale),
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
                            stringResource(R.string.quran_sandbox_analysis_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.quran_sandbox_grapheme_count, lettersEdited.size),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(
                                R.string.quran_sandbox_tashkeel,
                                stringResource(
                                    if (tts.containsArabicTashkeel(editedWord)) {
                                        R.string.quran_sandbox_tashkeel_yes
                                    } else {
                                        R.string.quran_sandbox_tashkeel_no
                                    },
                                ),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.quran_sandbox_skeleton_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                skeleton.ifBlank { "—" },
                                style = MaterialTheme.typography.titleMedium.scaled(arabicScale),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                            )
                        }
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.quran_sandbox_translation_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when {
                            translating -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        stringResource(R.string.quran_sandbox_translation_loading),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            translateFailed -> {
                                Text(
                                    stringResource(R.string.quran_sandbox_translation_error),
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
                            stringResource(R.string.quran_sandbox_translation_disclaimer),
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
                        onClick = { tts.speakArabic(editedWord) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.quran_sandbox_speak_word), maxLines = 2)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { tts.speakArabicLettersSequential(editedWord) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.quran_sandbox_speak_letters_seq), maxLines = 2)
                    }
                }
                OutlinedButton(
                    onClick = { tts.speakArabicLettersSpaced(editedWord) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.quran_sandbox_speak_letters_spaced))
                }
            } else {
                Text(
                    stringResource(R.string.quran_arabic_tap_word_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}
