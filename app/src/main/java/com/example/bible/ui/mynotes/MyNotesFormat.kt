package com.example.bible.ui.mynotes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.bible.data.mynotes.MyNoteKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun kindIcon(kind: MyNoteKind): ImageVector = when (kind) {
    MyNoteKind.TEXT -> Icons.AutoMirrored.Filled.Notes
    MyNoteKind.CHECKLIST -> Icons.Filled.Checklist
    MyNoteKind.TASK -> Icons.Filled.TaskAlt
    MyNoteKind.RECIPE -> Icons.Filled.Restaurant
    MyNoteKind.SKETCH -> Icons.Filled.Brush
}

internal fun notesCountRu(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> "заметка"
        mod10 in 2..4 && mod100 !in 12..14 -> "заметки"
        else -> "заметок"
    }
    return "$count $word"
}

internal fun formatDueRu(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")))
}.getOrDefault(iso)

internal fun formatDayRu(ms: Long): String = runCatching {
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru")))
}.getOrDefault("")
