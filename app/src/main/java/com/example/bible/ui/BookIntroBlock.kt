package com.example.bible.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.BibleCanon
import com.example.bible.data.BookIntros

/** Разделитель и краткая справка сразу под плитками глав. Цвета берутся из темы. */
@Composable
fun BookIntroBlock(
    bookId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val intro by produceState(initialValue = BookIntros.peek(bookId), bookId) {
        value = BookIntros.load(context)[bookId]
    }
    val info = intro ?: return
    val lines = info.displayLines()
    val group = BibleCanon.byId(bookId)?.group
    val accent = if (group != null) groupTextColor(group) else MaterialTheme.colorScheme.primary
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = accent.copy(alpha = 0.45f),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = shape,
            color = scheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, accent.copy(alpha = 0.28f), shape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                lines.forEachIndexed { index, line ->
                    val sourceLine = index == lines.lastIndex
                    Text(
                        text = line,
                        color = if (sourceLine) scheme.onSurfaceVariant else scheme.onSurface,
                        fontSize = if (sourceLine) 11.sp else 12.5.sp,
                        lineHeight = if (sourceLine) 14.sp else 16.sp,
                        fontWeight = if (sourceLine) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
