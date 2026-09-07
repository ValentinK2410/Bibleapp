package com.example.bible.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.BibleAudioDownloadQueue
import com.example.bible.data.BibleAudioNarrators
import com.example.bible.data.BibleAudioPreviewPlayer
import com.example.bible.data.BibleCanon
import com.example.bible.data.chapterCountForDownloadEntireBible
import com.example.bible.data.previewChapterForNarrator
import com.example.bible.service.BibleAudioDownloadService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleAudioDownloadPickerSheet(
    initialSelectedId: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val downloadState by BibleAudioDownloadQueue.state.collectAsStateWithLifecycle()
    val previewId by BibleAudioPreviewPlayer.playingNarratorId.collectAsStateWithLifecycle()

    var selected by remember {
        mutableStateOf(setOf(initialSelectedId))
    }

    DisposableEffect(Unit) {
        onDispose { BibleAudioPreviewPlayer.stop() }
    }

    val allIds = remember { BibleAudioNarrators.forPicker.map { it.id } }
    val totalSelectedChapters = remember(selected) {
        selected.sumOf { id ->
            chapterCountForDownloadEntireBible(BibleAudioNarrators.byId(id))
        }
    }

    ModalBottomSheet(onDismissRequest = {
        if (!downloadState.running) BibleAudioPreviewPlayer.stop()
        onDismiss()
    }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.bible_audio_download_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.bible_audio_download_sheet_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )

            if (downloadState.running) {
                LinearProgressIndicator(
                    progress = {
                        if (downloadState.totalChapters > 0) {
                            downloadState.doneChapters.toFloat() / downloadState.totalChapters
                        } else {
                            0f
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        R.string.download_full_bible_progress,
                        downloadState.doneChapters,
                        downloadState.totalChapters,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (downloadState.currentLabel.isNotBlank()) {
                    Text(
                        downloadState.currentLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (downloadState.errors > 0) {
                    Text(
                        stringResource(R.string.bible_audio_download_errors, downloadState.errors),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { BibleAudioDownloadService.cancel(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.bible_audio_download_cancel))
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { selected = allIds.toSet() }) {
                        Text(stringResource(R.string.bible_audio_download_select_all))
                    }
                    TextButton(onClick = { selected = emptySet() }) {
                        Text(stringResource(R.string.bible_audio_download_select_none))
                    }
                }

                Column(Modifier.heightIn(max = 360.dp)) {
                    BibleAudioNarrators.forPicker.forEach { narrator ->
                        val checked = narrator.id in selected
                        val (bookId, chapter) = previewChapterForNarrator(narrator)
                        val bookLabel = BibleCanon.byId(bookId)?.abbrRu ?: bookId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (checked) selected - narrator.id else selected + narrator.id
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { on ->
                                    selected = if (on) selected + narrator.id else selected - narrator.id
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    narrator.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                Text(
                                    stringResource(
                                        R.string.bible_audio_download_sample,
                                        bookLabel,
                                        chapter,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    BibleAudioPreviewPlayer.previewSample(context, narrator.id) { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                },
                            ) {
                                Icon(
                                    if (previewId == narrator.id) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.bible_audio_download_preview),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(
                        R.string.bible_audio_download_summary,
                        selected.size,
                        totalSelectedChapters,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (selected.isEmpty()) {
                            Toast.makeText(
                                context,
                                R.string.bible_audio_download_pick_one,
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@Button
                        }
                        BibleAudioPreviewPlayer.stop()
                        BibleAudioDownloadService.start(context, selected.toList())
                        Toast.makeText(
                            context,
                            R.string.bible_audio_download_started,
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.bible_audio_download_start))
                }
            }

            if (downloadState.finished && !downloadState.running) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        R.string.bible_audio_download_finished,
                        downloadState.doneChapters,
                        downloadState.errors,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(
                    onClick = {
                        BibleAudioDownloadQueue.dismissFinished()
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.bible_audio_download_done_btn))
                }
            }
        }
    }
}
