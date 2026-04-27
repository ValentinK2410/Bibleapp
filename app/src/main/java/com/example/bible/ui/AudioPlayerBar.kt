package com.example.bible.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.R
import com.example.bible.data.AudioPlaybackState
import kotlinx.coroutines.delay

@Composable
fun AudioPlayerBar(
    playbackState: AudioPlaybackState,
    playbackSpeed: Float,
    onTogglePause: () -> Unit,
    onCycleSpeed: () -> Unit,
    onStop: () -> Unit,
    getProgress: () -> Pair<Int, Int>,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = playbackState == AudioPlaybackState.PLAYING
            || playbackState == AudioPlaybackState.LOADING
            || playbackState == AudioPlaybackState.PAUSED

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        val onContainer = MaterialTheme.colorScheme.onPrimaryContainer

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(top = 8.dp, bottom = 4.dp),
        ) {
            var progress by remember { mutableFloatStateOf(0f) }
            var posText by remember { mutableFloatStateOf(0f) }
            var durText by remember { mutableFloatStateOf(0f) }

            if (playbackState == AudioPlaybackState.PLAYING || playbackState == AudioPlaybackState.PAUSED) {
                LaunchedEffect(playbackState) {
                    while (true) {
                        val (pos, dur) = getProgress()
                        if (dur > 0) {
                            progress = pos.toFloat() / dur.toFloat()
                            posText = pos.toFloat()
                            durText = dur.toFloat()
                        }
                        delay(500)
                    }
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (playbackState == AudioPlaybackState.LOADING) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = onContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.audio_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    IconButton(onClick = onTogglePause) {
                        Icon(
                            imageVector = if (playbackState == AudioPlaybackState.PAUSED) {
                                Icons.Default.PlayArrow
                            } else {
                                Icons.Default.Pause
                            },
                            contentDescription = if (playbackState == AudioPlaybackState.PAUSED) {
                                stringResource(R.string.audio_resume)
                            } else {
                                stringResource(R.string.audio_pause)
                            },
                            tint = onContainer,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Text(
                        text = formatTime(posText.toInt()) + " / " + formatTime(durText.toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = onContainer.copy(alpha = 0.7f),
                    )

                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = onCycleSpeed) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = onContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = formatSpeed(playbackSpeed),
                            color = onContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }

                IconButton(onClick = onStop) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.audio_stop),
                        tint = onContainer,
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

private fun formatSpeed(speed: Float): String {
    return if (speed == speed.toInt().toFloat()) {
        "${speed.toInt()}x"
    } else {
        "%.2gx".format(speed)
    }
}
