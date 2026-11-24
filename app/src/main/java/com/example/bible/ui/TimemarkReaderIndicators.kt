package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
