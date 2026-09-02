package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.TranslationId

/** Палитра подсветки вкладок переводов — пользователь выбирает цвет для каждой. */
val TranslationTabHighlightPalette = listOf(
    Color(0xFFFFEB3B),
    Color(0xFFFFD54F),
    Color(0xFFFF9800),
    Color(0xFFFF8A65),
    Color(0xFFF48FB1),
    Color(0xFFE91E63),
    Color(0xFFCE93D8),
    Color(0xFFB39DDB),
    Color(0xFF90CAF9),
    Color(0xFF4FC3F7),
    Color(0xFF80DEEA),
    Color(0xFFA5D6A7),
    Color(0xFF8BC34A),
    Color(0xFFC5E1A5),
    Color(0xFFFFCC80),
    Color(0xFFBCAAA4),
    Color(0xFFB0BEC5),
    Color(0xFF90A4AE),
)

fun translationTabContrastColor(background: Color): Color {
    val lum = background.red * 0.299f + background.green * 0.587f + background.blue * 0.114f
    return if (lum > 0.55f) Color(0xFF1C1B1F) else Color.White
}

@Composable
fun TranslationTabLabel(
    translation: TranslationId,
    selected: Boolean,
    highlightArgb: Int?,
) {
    val highlight = highlightArgb?.let { Color(it) }
    val textColor = when {
        highlight != null -> translationTabContrastColor(highlight)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        translation.labelRu,
        fontSize = 12.sp,
        maxLines = 1,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (highlight != null) {
                    Modifier.background(highlight.copy(alpha = if (selected) 1f else 0.72f))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun TranslationTabColorsDialog(
    colors: Map<String, Int>,
    onPick: (TranslationId, Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подсветка вкладок") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Выберите цвет для каждого перевода. Долгое нажатие на вкладку тоже открывает этот список.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TranslationId.entries.forEach { tid ->
                    val current = colors[tid.code]
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            tid.labelRu,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TranslationTabColorSwatch(
                                color = Color.Transparent,
                                selected = current == null,
                                labelNone = true,
                                onClick = { onPick(tid, null) },
                            )
                            TranslationTabHighlightPalette.forEach { c ->
                                val argb = c.toArgb()
                                TranslationTabColorSwatch(
                                    color = c,
                                    selected = current == argb,
                                    onClick = { onPick(tid, argb) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        },
    )
}

@Composable
private fun TranslationTabColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    labelNone: Boolean = false,
) {
    val border = when {
        selected -> MaterialTheme.colorScheme.primary
        labelNone || color.alpha < 0.05f -> MaterialTheme.colorScheme.outline
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .border(if (selected) 2.dp else 1.dp, border, CircleShape)
            .clip(CircleShape)
            .background(if (labelNone) MaterialTheme.colorScheme.surface else color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (labelNone) {
            Text("×", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
