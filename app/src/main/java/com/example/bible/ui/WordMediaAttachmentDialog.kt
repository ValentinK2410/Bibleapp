package com.example.bible.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.BibleUserAudio
import com.example.bible.data.BibleUserImage
import com.example.bible.data.BibleUserVideo
import com.example.bible.data.LexiconMediaRefs

/**
 * Собственная вёрстка вместо [androidx.compose.material3.AlertDialog]: в стандартном M3 слот `text`
 * оборачивается в `Box(Modifier.weight(1f))`, из‑за чего область текста растягивается по высоте
 * окна и вокруг короткого содержимого появляется большой пустой зазор.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordMediaAttachmentDialog(
    title: String,
    initialMedia: LexiconMediaRefs,
    bibleUserImages: List<BibleUserImage>,
    bibleUserVideos: List<BibleUserVideo>,
    bibleUserAudios: List<BibleUserAudio> = emptyList(),
    showDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (LexiconMediaRefs) -> Unit,
    onDelete: () -> Unit,
) {
    var media by remember { mutableStateOf(initialMedia) }
    LaunchedEffect(initialMedia) {
        media = initialMedia
    }
    val scroll = rememberScrollState()

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AlertDialogDefaults.titleContentColor,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Column(
                    Modifier
                        .padding(bottom = 24.dp)
                        .heightIn(max = 480.dp)
                        .verticalScroll(scroll),
                ) {
                    WordSpanMediaEditor(
                        media = media,
                        onMediaChange = { media = it },
                        bibleUserImages = bibleUserImages,
                        bibleUserVideos = bibleUserVideos,
                        bibleUserAudios = bibleUserAudios,
                    )
                    if (showDelete) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                onDelete()
                                onDismiss()
                            },
                        ) {
                            Text(
                                stringResource(R.string.word_media_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.back))
                    }
                    TextButton(
                        onClick = {
                            onSave(media)
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(R.string.word_media_save))
                    }
                }
            }
        }
    }
}
