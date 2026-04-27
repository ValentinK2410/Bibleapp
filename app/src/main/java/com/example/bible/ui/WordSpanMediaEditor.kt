package com.example.bible.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.bible.R
import com.example.bible.data.BibleUserAudio
import com.example.bible.data.BibleUserImage
import com.example.bible.data.BibleUserVideo
import com.example.bible.data.LexiconMediaRefs
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordSpanMediaEditor(
    media: LexiconMediaRefs,
    onMediaChange: (LexiconMediaRefs) -> Unit,
    bibleUserImages: List<BibleUserImage>,
    bibleUserVideos: List<BibleUserVideo>,
    bibleUserAudios: List<BibleUserAudio> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mediaState = rememberUpdatedState(media)
    var showAudioRecordDialog by remember { mutableStateOf(false) }
    var pendingVideoCaptureUri by remember { mutableStateOf<Uri?>(null) }
    val captureVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { success ->
        val uri = pendingVideoCaptureUri
        pendingVideoCaptureUri = null
        if (success == true && uri != null) {
            val m = mediaState.value
            onMediaChange(
                m.copy(
                    videoFileUri = uri.toString(),
                    videoUrl = null,
                    videoLibraryId = null,
                ),
            )
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val uri = pendingVideoCaptureUri
        if (granted && uri != null) {
            captureVideoLauncher.launch(uri)
        } else if (!granted) {
            pendingVideoCaptureUri = null
        }
    }
    val pickAudioDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            tryTakePersistableRead(context, it)
            val m = mediaState.value
            onMediaChange(m.copy(audioFileUri = it.toString(), audioUrl = null, audioLibraryId = null))
        }
    }
    val pickImageDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            tryTakePersistableRead(context, it)
            val m = mediaState.value
            onMediaChange(
                m.copy(
                    imageFileUri = it.toString(),
                    imageUrl = null,
                    imageLibraryId = null,
                ),
            )
        }
    }
    val pickVideoDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            tryTakePersistableRead(context, it)
            val m = mediaState.value
            onMediaChange(
                m.copy(
                    videoFileUri = it.toString(),
                    videoUrl = null,
                    videoLibraryId = null,
                ),
            )
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.semantic_lexicon_media_section), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = media.audioUrl.orEmpty(),
            onValueChange = {
                onMediaChange(
                    media.copy(
                        audioUrl = it.takeIf { s -> s.isNotBlank() },
                        audioLibraryId = null,
                    ),
                )
            },
            label = { Text(stringResource(R.string.semantic_lexicon_audio_url)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = media.audioFileUri.orEmpty(),
                onValueChange = {
                    onMediaChange(
                        media.copy(
                            audioFileUri = it.takeIf { s -> s.isNotBlank() },
                            audioLibraryId = null,
                        ),
                    )
                },
                label = { Text(stringResource(R.string.semantic_lexicon_audio_file_uri)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        pickAudioDoc.launch(
                            arrayOf("audio/*", "application/ogg", "application/x-flac"),
                        )
                    },
                ) {
                    Text(
                        stringResource(R.string.semantic_lexicon_pick_audio_file),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                OutlinedButton(onClick = { showAudioRecordDialog = true }) {
                    Text(
                        stringResource(R.string.semantic_lexicon_record_audio),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Text(stringResource(R.string.semantic_lexicon_media_library_audio), style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = media.audioLibraryId.isNullOrBlank(),
                onClick = { onMediaChange(media.copy(audioLibraryId = null)) },
                label = { Text(stringResource(R.string.semantic_lexicon_media_none)) },
            )
            bibleUserAudios.forEach { aud ->
                FilterChip(
                    selected = media.audioLibraryId == aud.id,
                    onClick = {
                        onMediaChange(
                            media.copy(
                                audioLibraryId = aud.id,
                                audioUrl = null,
                                audioFileUri = null,
                            ),
                        )
                    },
                    label = { Text(aud.title.ifBlank { aud.id.take(8) }, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        if (!media.audioFileUri.isNullOrBlank()) {
            TextButton(onClick = { onMediaChange(media.copy(audioFileUri = null)) }) {
                Text(stringResource(R.string.semantic_lexicon_clear_file))
            }
        }
        OutlinedTextField(
            value = media.imageUrl.orEmpty(),
            onValueChange = {
                onMediaChange(
                    media.copy(
                        imageUrl = it.takeIf { s -> s.isNotBlank() },
                        imageLibraryId = null,
                    ),
                )
            },
            label = { Text(stringResource(R.string.semantic_lexicon_image_url)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = media.imageFileUri.orEmpty(),
                onValueChange = {
                    onMediaChange(
                        media.copy(
                            imageFileUri = it.takeIf { s -> s.isNotBlank() },
                            imageLibraryId = null,
                        ),
                    )
                },
                label = { Text(stringResource(R.string.semantic_lexicon_image_file_uri)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { pickImageDoc.launch(arrayOf("image/*")) }) {
                    Text(
                        stringResource(R.string.semantic_lexicon_pick_image_file),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        if (!media.imageFileUri.isNullOrBlank()) {
            TextButton(onClick = { onMediaChange(media.copy(imageFileUri = null)) }) {
                Text(stringResource(R.string.semantic_lexicon_clear_file))
            }
        }
        Text(stringResource(R.string.semantic_lexicon_media_library_image), style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = media.imageLibraryId.isNullOrBlank(),
                onClick = { onMediaChange(media.copy(imageLibraryId = null)) },
                label = { Text(stringResource(R.string.semantic_lexicon_media_none)) },
            )
            bibleUserImages.forEach { img ->
                FilterChip(
                    selected = media.imageLibraryId == img.id,
                    onClick = {
                        onMediaChange(
                            media.copy(
                                imageLibraryId = img.id,
                                imageUrl = null,
                                imageFileUri = null,
                            ),
                        )
                    },
                    label = { Text(img.title.ifBlank { img.id.take(8) }, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        OutlinedTextField(
            value = media.videoUrl.orEmpty(),
            onValueChange = {
                onMediaChange(
                    media.copy(
                        videoUrl = it.takeIf { s -> s.isNotBlank() },
                        videoLibraryId = null,
                    ),
                )
            },
            label = { Text(stringResource(R.string.semantic_lexicon_video_url)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = media.videoFileUri.orEmpty(),
                onValueChange = {
                    onMediaChange(
                        media.copy(
                            videoFileUri = it.takeIf { s -> s.isNotBlank() },
                            videoLibraryId = null,
                        ),
                    )
                },
                label = { Text(stringResource(R.string.semantic_lexicon_video_file_uri)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { pickVideoDoc.launch(arrayOf("video/*")) },
                ) {
                    Text(
                        stringResource(R.string.semantic_lexicon_pick_video_file),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                OutlinedButton(
                    onClick = {
                        val file = File(context.cacheDir, "lexicon_video_${System.currentTimeMillis()}.mp4")
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file,
                        )
                        pendingVideoCaptureUri = uri
                        when (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA,
                            )
                        ) {
                            PackageManager.PERMISSION_GRANTED -> captureVideoLauncher.launch(uri)
                            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.semantic_lexicon_record_video),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        if (!media.videoFileUri.isNullOrBlank()) {
            TextButton(onClick = { onMediaChange(media.copy(videoFileUri = null)) }) {
                Text(stringResource(R.string.semantic_lexicon_clear_file))
            }
        }
        Text(stringResource(R.string.semantic_lexicon_media_library_video), style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = media.videoLibraryId.isNullOrBlank(),
                onClick = { onMediaChange(media.copy(videoLibraryId = null)) },
                label = { Text(stringResource(R.string.semantic_lexicon_media_none)) },
            )
            bibleUserVideos.forEach { vid ->
                FilterChip(
                    selected = media.videoLibraryId == vid.id,
                    onClick = {
                        onMediaChange(
                            media.copy(
                                videoLibraryId = vid.id,
                                videoUrl = null,
                                videoFileUri = null,
                            ),
                        )
                    },
                    label = { Text(vid.title.ifBlank { vid.id.take(8) }, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
    if (showAudioRecordDialog) {
        LexiconAudioRecorderDialog(
            onDismiss = { showAudioRecordDialog = false },
            onUriRecorded = { uri ->
                tryTakePersistableRead(context, uri)
                val m = mediaState.value
                onMediaChange(m.copy(audioFileUri = uri.toString(), audioUrl = null, audioLibraryId = null))
                showAudioRecordDialog = false
            },
        )
    }
}

private fun tryTakePersistableRead(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    } catch (_: SecurityException) {
    }
}
