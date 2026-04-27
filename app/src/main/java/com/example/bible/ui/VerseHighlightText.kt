package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.BibleVerse
import com.example.bible.data.SemanticHighlightSession
import com.example.bible.data.SemanticLexiconRule
import com.example.bible.data.SemanticStyleSpan
import com.example.bible.data.TextHighlight
import com.example.bible.data.VerseRef
import com.example.bible.data.WordSpanMediaAttachment
import com.example.bible.data.appliesToVerse
import com.example.bible.data.buildHighlightedVerseAnnotated
import com.example.bible.data.findFullReaderSemanticSpans
import kotlin.math.max
import kotlin.math.min

data class VerseHighlightSelection(
    val ref: VerseRef,
    val start: Int,
    val end: Int,
)

/** Режим ручного выделения в читалке. */
enum class ReaderHighlightMode {
    BACKGROUND,
    FOREGROUND,
    UNDERLINE,
}

/** Палитра фона для подсветки (пастельные, хорошо читаются на светлой теме). */
private val PresetBackgroundColors = listOf(
    Color(0xFFFFEB3B),
    Color(0xFFFFF176),
    Color(0xFFFFECB3),
    Color(0xFFFFE082),
    Color(0xFFFFD54F),
    Color(0xFFFFCC80),
    Color(0xFFFFAB91),
    Color(0xFFFF8A65),
    Color(0xFFFF9800),
    Color(0xFFE91E63),
    Color(0xFFF48FB1),
    Color(0xFFF8BBD0),
    Color(0xFFCE93D8),
    Color(0xFFE1BEE7),
    Color(0xFFD1C4E9),
    Color(0xFFB39DDB),
    Color(0xFF9FA8DA),
    Color(0xFF90CAF9),
    Color(0xFF81D4FA),
    Color(0xFF4FC3F7),
    Color(0xFF00BCD4),
    Color(0xFF80DEEA),
    Color(0xFFB2EBF2),
    Color(0xFFB2DFDB),
    Color(0xFFA5D6A7),
    Color(0xFF8BC34A),
    Color(0xFFC5E1A5),
    Color(0xFFDCE775),
    Color(0xFFE6EE9C),
    Color(0xFFF0F4C3),
    Color(0xFFD7CCC8),
    Color(0xFFB0BEC5),
    Color(0xFFCFD8DC),
    Color(0xFFECEFF1),
    Color(0xFFF5F5F5),
    Color.White,
)

private fun needsSwatchBorder(c: Color): Boolean {
    val r = c.red
    val g = c.green
    val b = c.blue
    val lum = r * 0.299f + g * 0.587f + b * 0.114f
    return lum > 0.88f
}

private val PresetForegroundColors = listOf(
    Color(0xFFE53935),
    Color(0xFFD32F2F),
    Color(0xFF1E88E5),
    Color(0xFF1565C0),
    Color(0xFF8E24AA),
    Color(0xFF6A1B9A),
    Color(0xFF00897B),
    Color(0xFF00695C),
    Color(0xFFEF6C00),
    Color(0xFF4E342E),
    Color(0xFF2E7D32),
    Color(0xFF37474F),
    Color.White,
)

private val SwatchBorderLight = Color(0xFF9E9E9E)

@Composable
fun ReaderHighlightToolbar(
    selection: VerseHighlightSelection,
    onApply: (ReaderHighlightMode, colorArgb: Long) -> Unit,
    onRemoveOverlapping: () -> Unit,
    onDismiss: () -> Unit,
    /** Медиа к выделенному фрагменту (отдельное окно). */
    onAttachMedia: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var mode by remember(selection.ref, selection.start, selection.end) {
        mutableStateOf(ReaderHighlightMode.BACKGROUND)
    }
    var pickedArgb by remember(selection.ref, selection.start, selection.end, mode) {
        mutableLongStateOf(
            when (mode) {
                ReaderHighlightMode.BACKGROUND -> PresetBackgroundColors.first().toArgb().toLong() and 0xFFFFFFFFL
                ReaderHighlightMode.FOREGROUND, ReaderHighlightMode.UNDERLINE ->
                    PresetForegroundColors.first().toArgb().toLong() and 0xFFFFFFFFL
            },
        )
    }

    LaunchedEffect(selection.ref, selection.start, selection.end, mode) {
        val palette = when (mode) {
            ReaderHighlightMode.BACKGROUND -> PresetBackgroundColors
            ReaderHighlightMode.FOREGROUND, ReaderHighlightMode.UNDERLINE -> PresetForegroundColors
        }
        pickedArgb = palette.first().toArgb().toLong() and 0xFFFFFFFFL
    }

    val palette = when (mode) {
        ReaderHighlightMode.BACKGROUND -> PresetBackgroundColors
        ReaderHighlightMode.FOREGROUND, ReaderHighlightMode.UNDERLINE -> PresetForegroundColors
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = mode == ReaderHighlightMode.BACKGROUND,
            onClick = { mode = ReaderHighlightMode.BACKGROUND },
            label = { Text(stringResource(R.string.highlight_mode_background)) },
        )
        FilterChip(
            selected = mode == ReaderHighlightMode.FOREGROUND,
            onClick = { mode = ReaderHighlightMode.FOREGROUND },
            label = { Text(stringResource(R.string.highlight_mode_foreground)) },
        )
        FilterChip(
            selected = mode == ReaderHighlightMode.UNDERLINE,
            onClick = { mode = ReaderHighlightMode.UNDERLINE },
            label = { Text(stringResource(R.string.highlight_mode_underline)) },
        )
        palette.forEach { c ->
            val argb = c.toArgb().toLong() and 0xFFFFFFFFL
            val selected = pickedArgb == argb
            val needsEdge = needsSwatchBorder(c)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .then(
                        when {
                            selected -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            needsEdge -> Modifier.border(1.dp, SwatchBorderLight, CircleShape)
                            else -> Modifier
                        },
                    )
                    .clip(CircleShape)
                    .background(c)
                    .clickable {
                        pickedArgb = argb
                        onApply(mode, argb)
                    },
            )
        }
        TextButton(onClick = onAttachMedia) {
            Text(stringResource(R.string.reader_attach_word_media))
        }
        TextButton(onClick = onRemoveOverlapping) {
            Text(stringResource(R.string.highlight_remove))
        }
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.highlight_done))
        }
    }
}

@Composable
fun SelectableVerseText(
    verse: BibleVerse,
    verseRef: VerseRef,
    highlights: List<TextHighlight>,
    activeVerseRef: VerseRef?,
    clearSelectionSignal: Int,
    onSelectionRange: (start: Int, end: Int) -> Unit,
    onSelectionCollapsed: () -> Unit,
    /** senseLabel, lexiconRuleId, смещение символа в тексте стиха — для словаря и вложений к выделению. */
    onWordTap: ((String, String?, String?, Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
    semanticHighlightSession: SemanticHighlightSession? = null,
    userLexiconRules: List<SemanticLexiconRule> = emptyList(),
    presetLexiconRules: List<SemanticLexiconRule> = emptyList(),
    presetLexiconEnabled: Boolean = true,
    /** Вложения медиа к фрагментам этого стиха (иконка у конца фрагмента). */
    wordSpanMediaForVerse: List<WordSpanMediaAttachment> = emptyList(),
) {
    val plain = verse.text
    val sessionForVerse = semanticHighlightSession?.takeIf { it.appliesToVerse(verseRef) }
    val semanticSpans: List<SemanticStyleSpan> = remember(
        plain,
        verseRef,
        sessionForVerse,
        userLexiconRules,
        presetLexiconRules,
        presetLexiconEnabled,
    ) {
        findFullReaderSemanticSpans(
            plain,
            sessionForVerse,
            verseRef.translation,
            presetLexiconEnabled,
            userLexiconRules,
            presetLexiconRules,
        )
    }
    val annotated = remember(plain, highlights, verseRef, semanticSpans) {
        buildHighlightedVerseAnnotated(
            plain,
            highlights.filter { it.matchesVerse(verseRef) },
            semanticSpans,
        )
    }
    var fieldValue by remember(verse.number, plain) {
        mutableStateOf(TextFieldValue(annotatedString = annotated, selection = TextRange.Zero))
    }

    LaunchedEffect(annotated) {
        fieldValue = fieldValue.copy(annotatedString = annotated, selection = fieldValue.selection)
    }

    LaunchedEffect(activeVerseRef, verseRef) {
        if (activeVerseRef != null && activeVerseRef != verseRef) {
            fieldValue = fieldValue.copy(selection = TextRange.Zero)
        }
    }

    LaunchedEffect(clearSelectionSignal) {
        if (clearSelectionSignal > 0) {
            fieldValue = fieldValue.copy(selection = TextRange.Zero, annotatedString = annotated)
        }
    }

    var lastSelCollapsedAt by remember { mutableLongStateOf(0L) }

    val attachmentsWithMedia = remember(wordSpanMediaForVerse) {
        wordSpanMediaForVerse
            .filter { it.media.hasAny() }
            .distinctBy { it.endOffset }
    }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current

    Box(modifier.fillMaxWidth()) {
        BasicTextField(
        value = fieldValue,
        onValueChange = { new ->
            if (new.text != plain) {
                fieldValue = TextFieldValue(annotatedString = annotated, selection = new.selection)
                return@BasicTextField
            }
            fieldValue = new
            val sel = new.selection
            if (sel.collapsed) {
                val now = System.currentTimeMillis()
                if (onWordTap != null && sel.start > 0 && now - lastSelCollapsedAt > 300) {
                    val word = extractWordAt(plain, sel.start)
                    if (word.isNotBlank()) {
                        val sp = spanAtOffset(semanticSpans, sel.start)
                        onWordTap(word, sp?.senseLabel, sp?.lexiconRuleId, sel.start)
                    }
                }
                lastSelCollapsedAt = now
                onSelectionCollapsed()
            } else {
                val start = min(sel.start, sel.end)
                val end = max(sel.start, sel.end)
                if (end > start) {
                    onSelectionRange(start, end)
                }
            }
        },
        readOnly = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(Color.Transparent),
        onTextLayout = { textLayout = it },
        modifier = Modifier.fillMaxWidth(),
    )
        val lr = textLayout
        if (lr != null && attachmentsWithMedia.isNotEmpty()) {
            val iconDp = 11.dp
            val iconPx = with(density) { iconDp.toPx() }
            for (att in attachmentsWithMedia) {
                val end = att.endOffset.coerceIn(0, plain.length)
                if (end <= 0) continue
                val charIdx = (end - 1).coerceIn(0, (plain.length - 1).coerceAtLeast(0))
                val box = lr.getBoundingBox(charIdx)
                val yCentered = box.top + (box.height - iconPx).coerceAtLeast(0f) / 2f
                Icon(
                    imageVector = Icons.Filled.AttachFile,
                    contentDescription = stringResource(R.string.word_media_attachment_marker_cd),
                    modifier = Modifier
                        .offset(
                            x = with(density) { box.right.toDp() },
                            y = with(density) { yCentered.toDp() },
                        )
                        .size(iconDp),
                    tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                )
            }
        }
    }
}

private fun spanAtOffset(spans: List<SemanticStyleSpan>, offset: Int): SemanticStyleSpan? {
    for (s in spans) {
        if (offset >= s.start && offset < s.end) return s
    }
    return null
}

private fun extractWordAt(text: String, offset: Int): String {
    if (offset < 0 || offset > text.length) return ""
    var start = offset
    while (start > 0 && text[start - 1].isLetter()) start--
    var end = offset
    while (end < text.length && text[end].isLetter()) end++
    return if (end > start) text.substring(start, end) else ""
}
