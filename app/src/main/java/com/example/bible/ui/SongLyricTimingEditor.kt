package com.example.bible.ui

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.SongLyricCue
import com.example.bible.data.mergeSongLyricCue
import kotlinx.coroutines.delay
import java.io.File

private const val TAG = "SongLyricTiming"

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).toInt()
    val m = s / 60
    val r = s % 60
    return "%d:%02d".format(m, r)
}

@Composable
fun SongLyricTimingEditor(
    lyrics: String,
    audioPath: String,
    cues: List<SongLyricCue>,
    onCuesChange: (List<SongLyricCue>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = remember(lyrics) { lyrics.split("\n") }
    if (lines.isEmpty() || audioPath.isBlank()) return

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    val markedLineIndices = remember(cues) { cues.map { it.lineIndex }.toSet() }

    DisposableEffect(audioPath) {
        val f = File(audioPath)
        if (!f.exists()) {
            return@DisposableEffect onDispose { }
        }
        val mp = MediaPlayer()
        var prepared = false
        try {
            mp.setDataSource(audioPath)
            mp.prepare()
            mp.setOnCompletionListener {
                isPlaying = false
                positionMs = mp.duration.toLong()
            }
            player = mp
            durationMs = mp.duration.toLong()
            positionMs = 0L
            prepared = true
        } catch (e: Exception) {
            Log.e(TAG, "prepare", e)
            mp.release()
            player = null
        }
        onDispose {
            if (prepared) {
                try {
                    mp.release()
                } catch (_: Exception) {
                }
            }
            player = null
            isPlaying = false
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(200)
            player?.let { p ->
                if (p.isPlaying) {
                    positionMs = p.currentPosition.toLong()
                }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.song_lyric_timing_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.song_lyric_timing_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                IconButton(
                    onClick = {
                        val p = player ?: return@IconButton
                        if (p.isPlaying) {
                            p.pause()
                            isPlaying = false
                        } else {
                            p.start()
                            isPlaying = true
                        }
                    },
                    enabled = player != null,
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                    )
                }
                Column(Modifier.weight(1f)) {
                    if (durationMs > 0) {
                        Slider(
                            value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                            onValueChange = { v ->
                                player?.seekTo(v.toInt())
                                positionMs = v.toLong()
                            },
                            valueRange = 0f..durationMs.toFloat(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp),
                        )
                    }
                    Text(
                        "${formatMs(positionMs)} / ${formatMs(durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Text(
                stringResource(R.string.song_lyric_timing_pick_line),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )

            val linesScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(linesScroll)
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                lines.forEachIndexed { idx, line ->
                    val hasCue = markedLineIndices.contains(idx)
                    val bg = if (hasCue) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .clickable(enabled = player != null) {
                                onCuesChange(mergeSongLyricCue(cues, positionMs, idx))
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            "${idx + 1}.",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = line.ifBlank { " " },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (cues.isNotEmpty()) {
                Text(
                    stringResource(R.string.song_lyric_timing_marks),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
                cues.sortedBy { it.timeMs }.forEach { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val linePreview = lines.getOrNull(c.lineIndex)?.trim()?.take(40) ?: ""
                        Text(
                            "${formatMs(c.timeMs)} → ${c.lineIndex + 1}: $linePreview",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                onCuesChange(cues.filter { it != c })
                            },
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.song_lyric_timing_delete_mark))
                        }
                    }
                }
            }
        }
    }
}
