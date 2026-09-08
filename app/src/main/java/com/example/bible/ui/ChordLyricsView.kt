package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.SongChordMarkup

private val InsertChords = listOf("C", "Dm", "Em", "F", "G", "Am", "D", "A", "E", "Hm", "H")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordLyricsView(
    lyrics: String,
    fontSizeSp: Float,
    showChords: Boolean,
    transpose: Int,
    modifier: Modifier = Modifier,
) {
    val lines = remember(lyrics, showChords, transpose) {
        SongChordMarkup.displayLines(lyrics, showChords, transpose)
    }
    var selected by remember { mutableStateOf<SelectedLyricChord?>(null) }
    LaunchedEffect(lyrics, transpose) { selected = null }
    val selectedName = selected?.let { sel ->
        val line = lines.getOrNull(sel.lineIndex) ?: return@let null
        if (!line.isChord) return@let null
        SongChordMarkup.chordSpans(line.text).firstOrNull { it.start == sel.start }?.name
    }

    Column(modifier.fillMaxWidth()) {
        if (showChords && selectedName == null && lines.any { it.isChord }) {
            Text(
                "Нажмите аккорд — схема появится, ещё раз — скроется.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
        }
        lines.forEachIndexed { index, line ->
            if (showChords && line.isChord) {
                ClickableChordLine(
                    text = line.text,
                    fontSizeSp = fontSizeSp,
                    lineIndex = index,
                    selected = selected,
                    onToggle = { lineIndex, start ->
                        selected = if (selected?.lineIndex == lineIndex && selected?.start == start) {
                            null
                        } else {
                            SelectedLyricChord(lineIndex, start)
                        }
                    },
                )
            } else {
                Text(
                    text = line.text.ifBlank { " " },
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.38f).sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Default,
                    softWrap = true,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showChords && selected?.lineIndex == index && selectedName != null) {
                Spacer(Modifier.height(6.dp))
                InstrumentFingeringGuide(chordName = selectedName)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private data class SelectedLyricChord(val lineIndex: Int, val start: Int)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClickableChordLine(
    text: String,
    fontSizeSp: Float,
    lineIndex: Int,
    selected: SelectedLyricChord?,
    onToggle: (lineIndex: Int, start: Int) -> Unit,
) {
    val parts = remember(text) { SongChordMarkup.chordLineParts(text) }
    val chordColor = MaterialTheme.colorScheme.primary
    val selectedBg = MaterialTheme.colorScheme.primaryContainer
    if (parts.none { it is SongChordMarkup.ChordLinePart.Chord }) {
        Text(
            text = text.ifBlank { " " },
            fontSize = fontSizeSp.sp,
            color = chordColor,
            fontWeight = FontWeight.Bold,
            softWrap = true,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        parts.forEach { part ->
            when (part) {
                is SongChordMarkup.ChordLinePart.Gap -> {
                    Text(
                        text = part.text,
                        fontSize = fontSizeSp.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false,
                        maxLines = 1,
                    )
                }
                is SongChordMarkup.ChordLinePart.Chord -> {
                    val isSel = selected?.lineIndex == lineIndex && selected.start == part.start
                    Text(
                        text = part.name,
                        fontSize = fontSizeSp.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = chordColor,
                        textDecoration = if (isSel) TextDecoration.Underline else TextDecoration.None,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) selectedBg else Color.Transparent)
                            .clickable { onToggle(lineIndex, part.start) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SongChordToolbar(
    hasChords: Boolean,
    showChords: Boolean,
    onShowChordsChange: (Boolean) -> Unit,
    transpose: Int,
    onTranspose: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showAudioTracks: Boolean? = null,
    onShowAudioTracksChange: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilterChip(
            selected = showChords,
            onClick = { onShowChordsChange(!showChords) },
            label = { Text(if (hasChords) "Аккорды" else "Аккорды (нет)") },
            enabled = hasChords || showChords,
        )
        if (showChords && hasChords) {
            IconButton(onClick = { onTranspose(transpose - 1) }) {
                Icon(Icons.Default.Remove, contentDescription = "Тоном ниже")
            }
            Text(
                if (transpose == 0) "тон" else {
                    val sign = if (transpose > 0) "+" else ""
                    "$sign$transpose"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = { onTranspose(transpose + 1) }) {
                Icon(Icons.Default.Add, contentDescription = "Тоном выше")
            }
            if (transpose != 0) {
                TextButton(onClick = { onTranspose(0) }) { Text("Сброс") }
            }
        }
        if (showAudioTracks != null && onShowAudioTracksChange != null) {
            FilterChip(
                selected = showAudioTracks,
                onClick = { onShowAudioTracksChange(!showAudioTracks) },
                label = { Text("Озвучка") },
            )
        }
    }
}

@Composable
fun ChordLyricEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Текст",
    minHeight: Dp = 180.dp,
) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (value != field.text) {
            field = TextFieldValue(value, TextRange(value.length))
        }
    }
    Column(modifier) {
        Text(
            "Аккорды — отдельной строкой над словами. Шрифт как при просмотре: пробелы совпадут.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InsertChords.forEach { chord ->
                SuggestionChip(
                    onClick = {
                        val ins = "[$chord]"
                        val start = field.selection.start.coerceIn(0, field.text.length)
                        val end = field.selection.end.coerceIn(0, field.text.length)
                        val a = minOf(start, end)
                        val b = maxOf(start, end)
                        val newText = field.text.substring(0, a) + ins + field.text.substring(b)
                        val pos = a + ins.length
                        val next = TextFieldValue(newText, TextRange(pos))
                        field = next
                        onValueChange(newText)
                    },
                    label = { Text(chord) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = field,
            onValueChange = {
                field = it
                onValueChange(it.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(minHeight),
            label = { Text(label) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        )
    }
}
