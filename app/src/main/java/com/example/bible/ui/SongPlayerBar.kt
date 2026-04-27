package com.example.bible.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bible.data.AudioPlayerHolder
import kotlinx.coroutines.delay

@Composable
fun SongPlayerBar(
    audioPath: String,
    title: String,
    modifier: Modifier = Modifier,
    /** Компактный режим (например альбом): без дублирования заголовка, меньше отступы. */
    compact: Boolean = false,
) {
    val ps by AudioPlayerHolder.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPos by remember { mutableStateOf(0) }

    LaunchedEffect(audioPath) {
        if (ps.audioPath != audioPath) {
            AudioPlayerHolder.play(audioPath, title)
        }
    }

    LaunchedEffect(ps.isPlaying) {
        while (ps.isPlaying) {
            AudioPlayerHolder.updatePosition()
            delay(250)
        }
    }

    fun formatTime(ms: Int): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    val displayPos = if (isSeeking) seekPos else ps.positionMs

    val padH = if (compact) 8.dp else 12.dp
    val padV = if (compact) 4.dp else 8.dp
    val playBtn = if (compact) 36.dp else 40.dp
    val iconInBtn = if (compact) 22.dp else 24.dp
    val sliderH = if (compact) 20.dp else 24.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(topStart = if (compact) 12.dp else 16.dp, topEnd = if (compact) 12.dp else 16.dp),
        shadowElevation = if (compact) 4.dp else 8.dp,
        tonalElevation = if (compact) 2.dp else 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = padH, vertical = padV),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { AudioPlayerHolder.togglePlay() },
                    modifier = Modifier.size(playBtn),
                ) {
                    Icon(
                        if (ps.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (ps.isPlaying) "Пауза" else "Воспроизвести",
                        modifier = Modifier.size(iconInBtn),
                    )
                }

                Spacer(Modifier.width(if (compact) 6.dp else 8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (!compact) {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatTime(displayPos),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Slider(
                            value = if (ps.durationMs > 0) displayPos.toFloat() / ps.durationMs else 0f,
                            onValueChange = {
                                isSeeking = true
                                seekPos = (it * ps.durationMs).toInt()
                            },
                            onValueChangeFinished = {
                                AudioPlayerHolder.seekTo(seekPos)
                                isSeeking = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(sliderH),
                        )
                        Text(
                            formatTime(ps.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(if (compact) 28.dp else 32.dp),
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = "Настройки",
                        modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = { AudioPlayerHolder.seekTo(maxOf(0, ps.positionMs - 10_000)) },
                            Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.Replay10, "−10с", Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { AudioPlayerHolder.seekTo(minOf(ps.durationMs, ps.positionMs + 10_000)) },
                            Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.Forward10, "+10с", Modifier.size(20.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { AudioPlayerHolder.stop() }, Modifier.size(36.dp)) {
                            Icon(Icons.Default.Stop, "Стоп", Modifier.size(20.dp))
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Скорость",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(68.dp),
                        )
                        Slider(
                            value = ps.speed,
                            onValueChange = { AudioPlayerHolder.setSpeed(it) },
                            valueRange = 0.5f..2.0f,
                            steps = 5,
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp),
                        )
                        Text(
                            "×${"%.1f".format(ps.speed)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(36.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Тональность",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(68.dp),
                        )
                        Slider(
                            value = ps.pitch,
                            onValueChange = { AudioPlayerHolder.setPitch(it) },
                            valueRange = -6f..6f,
                            steps = 11,
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp),
                        )
                        val sign = if (ps.pitch >= 0) "+" else ""
                        Text(
                            "${sign}${ps.pitch.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(36.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "Сбросить",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    AudioPlayerHolder.setSpeed(1.0f)
                                    AudioPlayerHolder.setPitch(0f)
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
