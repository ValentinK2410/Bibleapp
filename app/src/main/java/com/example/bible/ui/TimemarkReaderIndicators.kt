package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bible.R

/** Маленькая точка «есть таймкоды» на иконке в шапке читалки. */
@Composable
fun TimemarkAvailabilityDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(7.dp)
            .background(ReaderTopBarColors.Accent, CircleShape),
    )
}

/** Точка в правом верхнем углу кнопки (шапка читалки). */
@Composable
fun BoxScope.TimemarkIconBadge() {
    TimemarkAvailabilityDot(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = (-1).dp, y = 1.dp),
    )
}

/** Компактная подсказка над текстом главы. */
@Composable
fun TimemarkChapterHintBanner(
    matchesCurrentNarration: Boolean,
    modifier: Modifier = Modifier,
) {
    val textRes = if (matchesCurrentNarration) {
        R.string.timemark_reader_chapter_hint_active
    } else {
        R.string.timemark_reader_chapter_hint
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Schedule,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(textRes),
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
