package com.example.bible.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.bible.data.VideoThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import java.io.File

@Composable
fun VideoFileThumbnail(
    file: File,
    modifier: Modifier = Modifier,
) {
    var bmp by remember(file.absolutePath, file.lastModified(), file.length()) {
        mutableStateOf<Bitmap?>(null)
    }
    LaunchedEffect(file.absolutePath, file.lastModified(), file.length()) {
        var frame = withContext(Dispatchers.IO) { VideoThumbnailLoader.load(file) }
        if (frame == null && file.exists() && file.length() > 64) {
            delay(350)
            frame = withContext(Dispatchers.IO) { VideoThumbnailLoader.load(file) }
        }
        bmp = frame
    }
    VideoThumbnailBitmapBox(bmp = bmp, modifier = modifier)
}

/** Обложка плейлиста: перебирает ролики, пока не найдёт декодируемый кадр. */
@Composable
fun PlaylistVideoCoverThumbnail(
    files: List<File>,
    modifier: Modifier = Modifier,
) {
    val keys = remember(files) {
        files.joinToString("|") { "${it.absolutePath}:${it.lastModified()}:${it.length()}" }
    }
    var bmp by remember(keys) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(keys) {
        bmp = withContext(Dispatchers.IO) { VideoThumbnailLoader.loadFirstAvailable(files) }
    }
    VideoThumbnailBitmapBox(bmp = bmp, modifier = modifier)
}

@Composable
private fun VideoThumbnailBitmapBox(
    bmp: Bitmap?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val frame = bmp
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Filled.Videocam,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
