package com.example.bible.ui

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.bible.R
import com.example.bible.data.BibleAudioPlayer
import com.example.bible.data.BiblePlayerState
import com.example.bible.data.TimemarkProject
import com.example.bible.data.activeTimemarkCue
import com.example.bible.data.formatTimemarkTimeMs
import com.example.bible.data.verseRangeForTimemarkPosition
import kotlinx.coroutines.delay
import java.io.File

private const val TAG = "TimemarkReaderBar"

@Composable
fun TimemarkReaderBar(
    project: TimemarkProject,
    bookId: String,
    chapter: Int,
    bibleChapterAudioState: BiblePlayerState,
    onActiveVerseRange: (IntRange?) -> Unit,
    onHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(positionMs, project.cues) {
        onActiveVerseRange(verseRangeForTimemarkPosition(positionMs, project.cues))
    }

    LaunchedEffect(bibleChapterAudioState.isPlaying, bibleChapterAudioState.bookId, bibleChapterAudioState.chapter) {
        if (
            bibleChapterAudioState.isPlaying &&
            bibleChapterAudioState.bookId == bookId &&
            bibleChapterAudioState.chapter == chapter
        ) {
            try {
                player?.pause()
            } catch (_: Exception) {
            }
            isPlaying = false
        }
    }

    DisposableEffect(project.audioFilePath) {
        val f = File(project.audioFilePath)
        val fileOk = f.exists() && f.length() > 256L
        if (!fileOk) {
            onActiveVerseRange(null)
            onHeightChanged(0.dp)
            return@DisposableEffect onDispose { }
        }
        val mp = MediaPlayer()
        var prepared = false
        try {
            mp.setDataSource(project.audioFilePath)
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
            onActiveVerseRange(null)
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

    val audioFile = remember(project.audioFilePath) { File(project.audioFilePath) }
    val fileOk = audioFile.exists() && audioFile.length() > 256L

    if (!fileOk) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .onGloballyPositioned { coords ->
                    onHeightChanged(with(density) { coords.size.height.toDp() })
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Text(
                stringResource(R.string.timemark_reader_audio_missing),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        return
    }

    val activeCue = remember(positionMs, project.cues) {
        activeTimemarkCue(positionMs, project.cues)
    }
    val noteLine = activeCue?.note?.takeIf { it.isNotBlank() }
    val imagePath = activeCue?.attachments?.firstOrNull { it.kind == "image" && !it.path.isNullOrBlank() }?.path

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .onGloballyPositioned { coords ->
                onHeightChanged(with(density) { coords.size.height.toDp() })
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                stringResource(R.string.timemark_reader_section_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                project.title.ifBlank { stringResource(R.string.timemark_untitled_project) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = {
                        val p = player ?: return@IconButton
                        if (p.isPlaying) {
                            p.pause()
                            isPlaying = false
                        } else {
                            BibleAudioPlayer.pauseIfPlaying()
                            p.start()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.timemark_reader_play_cd),
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
                        "${formatTimemarkTimeMs(positionMs)} / ${formatTimemarkTimeMs(durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (noteLine != null) {
                Text(
                    noteLine,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (imagePath != null) {
                val imgFile = File(imagePath)
                if (imgFile.exists()) {
                    AsyncImage(
                        model = imgFile,
                        contentDescription = stringResource(R.string.timemark_image_for_mark),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(10.dp),
                            ),
                    )
                }
            }
        }
    }
}
