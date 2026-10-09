package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
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
import com.example.bible.ui.theme.OutlinedTextField
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
    onSelectedChordChange: (String?) -> Unit = {},
    header: (@Composable () -> Unit)? = null,
) {
    val lines = remember(lyrics, showChords, transpose) {
        SongChordMarkup.displayLines(lyrics, showChords, transpose)
    }
    val songChords = remember(lyrics, transpose) { SongChordMarkup.uniqueChordNames(lyrics, transpose) }
    var selected by remember { mutableStateOf<SelectedLyricChord?>(null) }
    LaunchedEffect(lyrics, transpose) { selected = null }
    LaunchedEffect(selected, showChords) {
        onSelectedChordChange(if (showChords) selected?.name else null)
    }
    fun toggle(sel: SelectedLyricChord) {
        selected = if (selected == sel) null else sel
    }

    Column(modifier.fillMaxWidth()) {
        header?.invoke()
        if (showChords && songChords.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                songChords.forEachIndexed { i, name ->
                    val sel = SelectedLyricChord(-1, i, name)
                    val isSel = selected?.name == name
                    Text(
                        name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = if (isSel) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            )
                            .clickable { toggle(sel) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            if (selected == null) {
                Text(
                    "Нажмите на аккорд — внизу появится аппликатура.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val next = lines.getOrNull(index + 1)
            when {
                showChords && line.isChord && next != null && !next.isChord && next.text.isNotBlank() &&
                    !SongChordMarkup.isSectionHeader(next.text) -> {
                    ChordOverWordsLine(
                        chordLine = line.text,
                        lyricLine = next.text,
                        fontSizeSp = fontSizeSp,
                        lineIndex = index,
                        selected = selected,
                        onToggle = ::toggle,
                    )
                    index += 2
                    continue
                }
                showChords && line.isChord -> ClickableChordLine(
                    text = line.text,
                    fontSizeSp = fontSizeSp,
                    lineIndex = index,
                    selected = selected,
                    onToggle = ::toggle,
                )
                SongChordMarkup.isSectionHeader(line.text) -> SongSectionLabel(line.text.trim().trimEnd(':', '.'))
                else -> Text(
                    text = line.text.ifBlank { " " },
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.38f).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            index++
        }
    }
}

private data class SelectedLyricChord(val lineIndex: Int, val start: Int, val name: String)

@Composable
private fun SongSectionLabel(text: String) {
    Text(
        text.replaceFirstChar { it.uppercase() },
        fontWeight = FontWeight.ExtraBold,
        fontSize = 13.sp,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier
            .padding(top = 14.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

private data class ChordPiece(val chord: SongChordMarkup.ChordSpan?, val text: String)

/** Слова строки; аккорд внутри слова делит его на слоги, но слово переносится целиком. */
private fun chordWordUnits(chordLine: String, lyricLine: String): List<List<ChordPiece>> {
    val spans = SongChordMarkup.chordSpans(chordLine)
    val words = Regex("""\S+\s*|\s+""").findAll(lyricLine).toList()
    val units = ArrayList<List<ChordPiece>>()
    for (w in words) {
        val start = w.range.first
        val end = w.range.last + 1
        val inside = spans.filter { it.start in start until end }
        if (inside.isEmpty()) {
            units.add(listOf(ChordPiece(null, w.value)))
            continue
        }
        val pieces = ArrayList<ChordPiece>()
        if (inside.first().start > start) pieces.add(ChordPiece(null, lyricLine.substring(start, inside.first().start)))
        inside.forEachIndexed { k, span ->
            val to = inside.getOrNull(k + 1)?.start ?: end
            pieces.add(ChordPiece(span, lyricLine.substring(span.start, to)))
        }
        units.add(pieces)
    }
    spans.filter { it.start >= lyricLine.length }.forEach { units.add(listOf(ChordPiece(it, ""))) }
    return units
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChordOverWordsLine(
    chordLine: String,
    lyricLine: String,
    fontSizeSp: Float,
    lineIndex: Int,
    selected: SelectedLyricChord?,
    onToggle: (SelectedLyricChord) -> Unit,
) {
    val units = remember(chordLine, lyricLine) { chordWordUnits(chordLine, lyricLine) }
    val chordColor = MaterialTheme.colorScheme.primary
    val chordSize = (fontSizeSp * 0.8f).sp
    val chordLine = (fontSizeSp * 1.02f).sp
    FlowRow(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        units.forEach { pieces ->
            Row {
                pieces.forEach { piece ->
                    Column {
                        val c = piece.chord
                        if (c != null) {
                            val isSel = selected?.lineIndex == lineIndex && selected.start == c.start
                            Text(
                                c.name,
                                fontSize = chordSize,
                                lineHeight = chordLine,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSel) Color.White else chordColor,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) chordColor else Color.Transparent)
                                    .clickable { onToggle(SelectedLyricChord(lineIndex, c.start, c.name)) }
                                    .padding(horizontal = 2.dp),
                            )
                        } else {
                            Text(" ", fontSize = chordSize, lineHeight = chordLine)
                        }
                        Text(
                            piece.text.replace(' ', '\u00A0').ifEmpty { "\u00A0" },
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.3f).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClickableChordLine(
    text: String,
    fontSizeSp: Float,
    lineIndex: Int,
    selected: SelectedLyricChord?,
    onToggle: (SelectedLyricChord) -> Unit,
) {
    val spans = remember(text) { SongChordMarkup.chordSpans(text) }
    val chordColor = MaterialTheme.colorScheme.primary
    if (spans.isEmpty()) {
        Text(text.ifBlank { " " }, fontSize = fontSizeSp.sp, color = chordColor, fontWeight = FontWeight.Bold)
        return
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        spans.forEach { span ->
            val isSel = selected?.lineIndex == lineIndex && selected.start == span.start
            Text(
                span.name,
                fontSize = (fontSizeSp * 0.82f).sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSel) Color.White else chordColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSel) chordColor else chordColor.copy(alpha = 0.1f))
                    .clickable { onToggle(SelectedLyricChord(lineIndex, span.start, span.name)) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
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
    onFontDown: (() -> Unit)? = null,
    onFontUp: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasChords) {
            TmGradientPill("🎸 Аккорды", showChords, MusicMetronomeGradient) { onShowChordsChange(!showChords) }
        }
        if (showChords && hasChords) {
            ToolbarCapsule(
                label = if (transpose == 0) "Тон" else (if (transpose > 0) "+$transpose" else "$transpose"),
                onMinus = { onTranspose(transpose - 1) },
                onPlus = { onTranspose(transpose + 1) },
                minusDescription = "Тоном ниже",
                plusDescription = "Тоном выше",
                onLabelClick = if (transpose != 0) ({ onTranspose(0) }) else null,
            )
        }
        if (onFontDown != null && onFontUp != null) {
            ToolbarCapsule(
                label = "Aa",
                onMinus = onFontDown,
                onPlus = onFontUp,
                minusDescription = "Уменьшить текст",
                plusDescription = "Увеличить текст",
            )
        }
        if (showAudioTracks != null && onShowAudioTracksChange != null) {
            TmGradientPill("🎧 Озвучка", showAudioTracks, MusicTunerGradient) { onShowAudioTracksChange(!showAudioTracks) }
        }
    }
}

@Composable
private fun ToolbarCapsule(
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusDescription: String,
    plusDescription: String,
    onLabelClick: (() -> Unit)? = null,
) {
    val accent = MusicMetronomeGradient.first()
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(999.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Remove,
            contentDescription = minusDescription,
            tint = accent,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onMinus)
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .size(18.dp),
        )
        Text(
            label,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            color = accent,
            modifier = if (onLabelClick != null) Modifier.clickable(onClick = onLabelClick) else Modifier,
        )
        Icon(
            Icons.Default.Add,
            contentDescription = plusDescription,
            tint = accent,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onPlus)
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .size(18.dp),
        )
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
