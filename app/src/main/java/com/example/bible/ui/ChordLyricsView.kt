package com.example.bible.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
    Column(
        modifier.then(
            if (showChords) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
        ),
    ) {
        lines.forEach { line ->
            Text(
                text = line.text.ifBlank { " " },
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.28f).sp,
                fontWeight = if (line.isChord) FontWeight.Bold else FontWeight.Normal,
                fontFamily = if (showChords) FontFamily.Monospace else FontFamily.Default,
                softWrap = !showChords,
                maxLines = if (showChords) 1 else Int.MAX_VALUE,
                color = if (line.isChord) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
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
    lyrics: String = "",
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        }
        if (showChords && hasChords && lyrics.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            InstrumentFingeringGuide(lyrics = lyrics, transpose = transpose)
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
