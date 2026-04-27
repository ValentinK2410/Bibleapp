package com.example.bible.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.BibleCanon
import com.example.bible.data.InterlinearTts
import com.example.bible.data.InterlinearWord
import com.example.bible.data.StrongsConcordance
import com.example.bible.data.StrongsDictionary
import com.example.bible.data.StrongVerseRef
import com.example.bible.data.VerseAttachment
import com.example.bible.data.VerseRef

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun InterlinearVerseContent(
    words: List<InterlinearWord>,
    verseNumber: Int,
    interlinearTts: InterlinearTts,
    /** Книга для TTS: ВЗ → иврит, НЗ → греческий. */
    bookId: String,
    /**
     * Индекс первого слова этого стиха в плоском списке слов главы
     * (для подсветки при «Глава по словам»). Для «Стих по словам» оставить 0.
     */
    interlinearChapterWordOffset: Int = 0,
    modifier: Modifier = Modifier,
    verseRef: VerseRef? = null,
    onVerseNumberClick: (() -> Unit)? = null,
    onVerseNumberLongPress: (() -> Unit)? = null,
    onAttachmentImageClick: (VerseAttachment) -> Unit = {},
    /** Для двойного экрана (тёмная тема панели). */
    verseNumberColor: Color = MaterialTheme.colorScheme.primary,
    /** Перейти к стиху в основной читалке (книга, глава, стих). В двойном экране обычно null. */
    onNavigateToVerse: ((String, Int, Int) -> Unit)? = null,
    /** У стиха есть личная заметка (иконка у номера). */
    hasVerseNote: Boolean = false,
    /** Быстрое открытие заметки по нажатию на иконку (если заметка есть). */
    onVerseNoteIconClick: (() -> Unit)? = null,
) {
    var selectedWord by remember { mutableStateOf<InterlinearWord?>(null) }
    val seqHighlight by interlinearTts.sequenceHighlight.collectAsStateWithLifecycle(initialValue = null)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (verseRef != null && onVerseNumberClick != null) {
                    Text(
                        text = "$verseNumber",
                        fontSize = 9.sp,
                        color = verseNumberColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 1.dp, end = 2.dp)
                            .combinedClickable(
                                onClick = onVerseNumberClick,
                                onLongClick = onVerseNumberLongPress,
                            ),
                    )
                    VerseAttachmentIndicator(
                        verseRef = verseRef,
                        iconTint = verseNumberColor,
                        thumbBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        onImageClick = onAttachmentImageClick,
                    )
                    if (hasVerseNote) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                            contentDescription = stringResource(R.string.verse_has_personal_note),
                            modifier = Modifier
                                .size(11.dp)
                                .then(
                                    if (onVerseNoteIconClick != null) {
                                        Modifier.clickable { onVerseNoteIconClick() }
                                    } else {
                                        Modifier
                                    },
                                ),
                            tint = verseNumberColor.copy(alpha = 0.9f),
                        )
                    }
                } else {
                    Text(
                        text = "$verseNumber",
                        fontSize = 9.sp,
                        color = verseNumberColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 1.dp, end = 2.dp),
                    )
                }
            }
            if (words.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    TextButton(
                        onClick = { interlinearTts.speakSequence(words, bookId) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(
                            stringResource(R.string.interlinear_speak_verse_sequence),
                            fontSize = 9.sp,
                            maxLines = 1,
                        )
                    }
                    TextButton(
                        onClick = { interlinearTts.stop() },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(stringResource(R.string.interlinear_stop_speech), fontSize = 9.sp)
                    }
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            words.forEachIndexed { i, word ->
                val playing = seqHighlight?.wordIndex == interlinearChapterWordOffset + i
                InterlinearWordCell(
                    word = word,
                    onClick = { selectedWord = word },
                    onSpeak = { interlinearTts.speak(word, bookId) },
                    playingInSequence = playing,
                )
            }
        }
    }

    selectedWord?.let { word ->
        InterlinearWordDetail(
            word = word,
            currentVerseRef = verseRef,
            onNavigateToVerse = onNavigateToVerse,
            onDismiss = { selectedWord = null },
            onSpeak = { interlinearTts.speak(word, bookId) },
        )
    }
}

@Composable
private fun InterlinearWordCell(
    word: InterlinearWord,
    onClick: () -> Unit,
    onSpeak: () -> Unit,
    playingInSequence: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                color =
                    if (playingInSequence) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        Color.Transparent
                    },
                shape = RoundedCornerShape(2.dp),
            )
            .border(
                width = if (playingInSequence) 1.5.dp else 0.5.dp,
                color =
                    if (playingInSequence) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    },
                shape = RoundedCornerShape(2.dp),
            )
            .padding(start = 3.dp, end = 1.dp, top = 0.dp, bottom = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 168.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 0.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = word.original,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = word.transliteration,
                    fontSize = 6.5.sp,
                    lineHeight = 7.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = word.translation,
                fontSize = 8.5.sp,
                lineHeight = 9.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(width = 20.dp, height = 22.dp)
                .clickable(onClick = onSpeak),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(R.string.interlinear_speak_cd),
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            )
        }
    }
}

private fun formatStrongOccurrenceLabel(ref: StrongVerseRef): String {
    val abbr = BibleCanon.byId(ref.bookId)?.abbrRu ?: ref.bookId
    return "$abbr ${ref.chapter}:${ref.verse}"
}

private fun isSameVerse(ref: StrongVerseRef, vr: VerseRef?): Boolean =
    vr != null && ref.bookId == vr.bookId && ref.chapter == vr.chapter && ref.verse == vr.verse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterlinearWordDetail(
    word: InterlinearWord,
    currentVerseRef: VerseRef?,
    onNavigateToVerse: ((String, Int, Int) -> Unit)?,
    onDismiss: () -> Unit,
    onSpeak: () -> Unit,
) {
    val context = LocalContext.current
    val strongsDict = remember { StrongsDictionary(context) }
    val strongsEntry = remember(word.strong) { strongsDict.lookup(word.strong) }
    var concordance by remember(word.strong) { mutableStateOf<List<StrongVerseRef>>(emptyList()) }
    LaunchedEffect(word.strong) {
        concordance = StrongsConcordance.loadOccurrences(context, word.strong)
    }
    val maxShow = 100
    val shown = remember(concordance) { concordance.take(maxShow) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = word.original,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 44.dp),
                    textAlign = TextAlign.Center,
                )
                IconButton(
                    onClick = onSpeak,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.interlinear_speak_cd),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // Transliteration
            Text(
                text = word.transliteration,
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            // Перевод подстрочника (рус., из разметки стиха)
            DetailRow(stringResource(R.string.interlinear_gloss_ru), word.translation)

            // Словарь Стронга: лемма на иврите/греческом + значение на русском
            strongsEntry?.let { entry ->
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                StrongsLexiconSection(
                    entry = entry,
                    originalInVerse = word.original,
                )
            }

            if (word.strong != null && concordance.isNotEmpty()) {
                val code = StrongsDictionary.normalizeStrongCode(word.strong!!)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.interlinear_strongs_other_uses, code),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.interlinear_strongs_occurrences_count,
                        concordance.size,
                        minOf(maxShow, concordance.size),
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                )
                if (onNavigateToVerse == null) {
                    Text(
                        text = stringResource(R.string.interlinear_strongs_no_navigation),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.interlinear_strongs_tap_to_open),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    shown.forEach { ref ->
                        val here = isSameVerse(ref, currentVerseRef)
                        val label = formatStrongOccurrenceLabel(ref) +
                            if (here) " — " + stringResource(R.string.interlinear_strongs_here) else ""
                        if (onNavigateToVerse != null) {
                            TextButton(
                                onClick = {
                                    if (!here) {
                                        onNavigateToVerse(ref.bookId, ref.chapter, ref.verse)
                                        onDismiss()
                                    }
                                },
                                enabled = !here,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start,
                                    color = if (here) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                            }
                        } else {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                color = if (here) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }

            // Morphology
            word.morph?.let { m ->
                Spacer(Modifier.height(8.dp))
                DetailRow(stringResource(R.string.interlinear_morphology), m)
            }

            if (strongsEntry == null && word.strong != null) {
                Spacer(Modifier.height(8.dp))
                StrongsNotFoundHint(word.strong!!)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
