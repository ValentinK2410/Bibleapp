package com.example.bible.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bible.R

internal const val SongLandscapeSplitMin = 0.28f
internal const val SongLandscapeSplitMax = 0.78f
internal const val SongLandscapeSplitDefault = 0.58f

@Composable
fun SongLandscapeSplitLayout(
    splitFraction: Float,
    onSplitFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    var localFraction by remember {
        mutableFloatStateOf(splitFraction.coerceIn(SongLandscapeSplitMin, SongLandscapeSplitMax))
    }
    var resizeMode by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(localFraction) }
    var totalWidthPx by remember { mutableIntStateOf(1) }

    LaunchedEffect(splitFraction) {
        localFraction = splitFraction.coerceIn(SongLandscapeSplitMin, SongLandscapeSplitMax)
    }

    val leftWeight = localFraction.coerceIn(SongLandscapeSplitMin, SongLandscapeSplitMax)
    val rightWeight = (1f - leftWeight).coerceAtLeast(0.01f)

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { totalWidthPx = it.width },
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(leftWeight)
                    .fillMaxHeight(),
            ) {
                left()
            }
            HorizontalSplitHandle(
                onDragDeltaPx = { dx ->
                    if (totalWidthPx > 0) {
                        localFraction = (localFraction + dx / totalWidthPx)
                            .coerceIn(SongLandscapeSplitMin, SongLandscapeSplitMax)
                    }
                },
                onDragEnd = { onSplitFractionChange(localFraction) },
                onLongPress = {
                    dragFraction = localFraction
                    resizeMode = true
                },
            )
            Box(
                modifier = Modifier
                    .weight(rightWeight)
                    .fillMaxHeight(),
            ) {
                right()
            }
        }
        if (resizeMode) {
            HorizontalResizeOverlay(
                fraction = dragFraction,
                totalWidthPx = totalWidthPx,
                onFractionChange = {
                    dragFraction = it.coerceIn(SongLandscapeSplitMin, SongLandscapeSplitMax)
                },
                onApply = {
                    localFraction = dragFraction
                    onSplitFractionChange(dragFraction)
                    resizeMode = false
                },
                onReset = {
                    dragFraction = SongLandscapeSplitDefault
                    localFraction = SongLandscapeSplitDefault
                    onSplitFractionChange(SongLandscapeSplitDefault)
                    resizeMode = false
                },
                onDismiss = { resizeMode = false },
            )
        }
    }
}

@Composable
fun SongLandscapeChordPane(
    selectedChord: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            if (selectedChord != null) {
                InstrumentFingeringGuide(
                    chordName = selectedChord,
                    docked = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.song_landscape_chord_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
