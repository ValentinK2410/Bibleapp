package com.example.bible.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.BibleAudioNarrators
import com.example.bible.data.BibleAudioPlayer
import com.example.bible.data.BibleCanon
import com.example.bible.data.localAudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SPEED_STEPS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
fun BibleAudioBar(
    modifier: Modifier = Modifier,
    /** Высота панели — чтобы список стихов имел нижний отступ и не уходил под плеер */
    onBarHeightChanged: ((Dp) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val ps by BibleAudioPlayer.state.collectAsState()
    val continueChapters by BibleAudioPlayer.continueChapters.collectAsState()
    val playbackSpeed by BibleAudioPlayer.playbackSpeed.collectAsState()
    val sleepRemaining by BibleAudioPlayer.sleepTimerRemainingMs.collectAsState()
    val sleepAwaitChapter by BibleAudioPlayer.sleepAwaitingChapterEnd.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSeeking by remember { mutableStateOf(false) }
    var seekPos by remember { mutableStateOf(0) }
    var isDownloading by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    LaunchedEffect(ps.isPlaying) {
        while (ps.isPlaying) {
            BibleAudioPlayer.updatePosition()
            delay(300)
        }
    }

    val isActive = ps.bookId.isNotBlank()

    LaunchedEffect(isActive) {
        if (!isActive) onBarHeightChanged?.invoke(0.dp)
    }

    AnimatedVisibility(
        visible = isActive,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        val bookName = BibleCanon.byId(ps.bookId)?.nameRu ?: ps.bookId
        val narrator = BibleAudioNarrators.byId(ps.narratorId)
        val displayPos = if (isSeeking) seekPos else ps.positionMs
        val isLocal = remember(ps.bookId, ps.chapter, ps.narratorId) {
            if (ps.bookId.isBlank()) false
            else localAudioFile(context, ps.narratorId, ps.bookId, ps.chapter).let {
                it.exists() && it.length() > 1024
            }
        }

        if (showSleepDialog) {
            SleepTimerDialog(
                onDismiss = { showSleepDialog = false },
                onConfirm = { minutes, stopAtChapterEnd ->
                    BibleAudioPlayer.setSleepTimer(minutes, stopAtChapterEnd)
                    showSleepDialog = false
                },
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .onGloballyPositioned { coords ->
                    onBarHeightChanged?.invoke(
                        with(density) { coords.size.height.toDp() },
                    )
                },
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
            shadowElevation = 8.dp,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (ps.isLoading) {
                        CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
                    } else {
                        FilledTonalIconButton(
                            onClick = { BibleAudioPlayer.togglePlay() },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                if (ps.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "$bookName ${ps.chapter} (${narrator.name})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatMs(displayPos),
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
                                    BibleAudioPlayer.seekTo(seekPos)
                                    isSeeking = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(22.dp),
                            )
                            Text(
                                formatMs(ps.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (!isLocal && !isDownloading && ps.bookId.isNotBlank()) {
                        IconButton(
                            onClick = {
                                isDownloading = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        BibleAudioPlayer.downloadChapter(
                                            context, narrator, ps.bookId, ps.chapter,
                                        )
                                        launch(Dispatchers.Main) {
                                            isDownloading = false
                                            Toast.makeText(context, "Скачано для офлайн", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        launch(Dispatchers.Main) {
                                            isDownloading = false
                                            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(Icons.Default.CloudDownload, "Скачать", Modifier.size(18.dp))
                        }
                    }
                    if (isDownloading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }

                    IconButton(
                        onClick = { showSleepDialog = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            stringResource(R.string.bible_audio_sleep_timer),
                            Modifier.size(20.dp),
                            tint = if (sleepRemaining != null || sleepAwaitChapter) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }

                    IconToggleButton(
                        checked = continueChapters,
                        onCheckedChange = { BibleAudioPlayer.setContinueChapters(it) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = stringResource(R.string.bible_audio_continue_chapters),
                            Modifier.size(20.dp),
                        )
                    }

                    IconButton(
                        onClick = { BibleAudioPlayer.release() },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            "Закрыть",
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.bible_audio_speed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SPEED_STEPS.forEach { s ->
                        FilterChip(
                            selected = kotlin.math.abs(playbackSpeed - s) < 0.01f,
                            onClick = { BibleAudioPlayer.setPlaybackSpeed(s) },
                            label = {
                                Text(
                                    if (s == 1f) "1×" else "${s}×".replace(".0", ""),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }

                if (sleepRemaining != null) {
                    Text(
                        stringResource(R.string.bible_audio_sleep_remaining, formatSleepCountdown(sleepRemaining!!)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (sleepAwaitChapter) {
                    Text(
                        stringResource(R.string.bible_audio_sleep_after_chapter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                if (ps.error != null) {
                    Text(
                        ps.error!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int, stopAtChapterEnd: Boolean) -> Unit,
) {
    var stopAtEnd by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bible_audio_sleep_timer)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.bible_audio_sleep_pick),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(5, 15, 30, 45, 60).chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            row.forEach { m ->
                                TextButton(
                                    onClick = { onConfirm(m, stopAtEnd) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("$m мин")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.bible_audio_sleep_stop_after_chapter))
                    Switch(checked = stopAtEnd, onCheckedChange = { stopAtEnd = it })
                }
                TextButton(
                    onClick = {
                        BibleAudioPlayer.cancelSleepTimer()
                        onDismiss()
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(stringResource(R.string.bible_audio_sleep_off))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        dismissButton = {},
    )
}

private fun formatMs(ms: Int): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private fun formatSleepCountdown(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
