package com.example.bible.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.bible.R
import java.io.File

@Composable
internal fun AiChatImageBlock(
    file: File,
    maxHeight: Dp,
    enabled: Boolean,
    onSaveImage: (File) -> Unit,
) {
    var showDownload by remember(file) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = file,
            contentDescription = stringResource(R.string.gigachat_image_cd),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .clickable(enabled = enabled) {
                    showDownload = !showDownload
                },
            contentScale = ContentScale.Fit,
        )
        if (showDownload) {
            FilledTonalIconButton(
                onClick = {
                    onSaveImage(file)
                    showDownload = false
                },
                enabled = enabled,
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = stringResource(R.string.gigachat_image_save_cd),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
