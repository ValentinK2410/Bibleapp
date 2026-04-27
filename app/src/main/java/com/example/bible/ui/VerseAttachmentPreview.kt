package com.example.bible.ui

import android.content.Intent
import android.media.MediaPlayer
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.bible.R
import com.example.bible.data.AttachmentKind
import com.example.bible.data.VerseAttachment
import com.example.bible.data.resolveFile
import kotlinx.coroutines.delay
import java.io.File
import kotlin.text.Charsets

@Composable
fun AttachmentPreviewDialog(
    attachment: VerseAttachment,
    onDismiss: () -> Unit,
    onPauseMainAudio: () -> Unit,
) {
    val context = LocalContext.current
    val file = remember(attachment) { attachment.resolveFile(context) }
    if (!file.isFile) {
        LaunchedEffect(attachment.id) { onDismiss() }
        return
    }
    when (attachment.kind()) {
        AttachmentKind.Audio -> AttachmentAudioPreviewDialog(
            file = file,
            title = attachment.displayName,
            onDismiss = onDismiss,
            onBeforeStart = onPauseMainAudio,
        )
        AttachmentKind.Image -> AttachmentImagePreviewDialog(
            file = file,
            onDismiss = onDismiss,
        )
        AttachmentKind.Video -> AttachmentVideoPreviewDialog(
            file = file,
            onDismiss = onDismiss,
            onBeforeStart = onPauseMainAudio,
        )
        AttachmentKind.Text -> AttachmentTextPreviewDialog(
            file = file,
            title = attachment.displayName,
            onDismiss = onDismiss,
        )
        AttachmentKind.Other -> {
            LaunchedEffect(attachment.id) {
                onPauseMainAudio()
                openWithExternalApp(context, file, attachment.mimeType)
                onDismiss()
            }
        }
    }
}

private fun openWithExternalApp(context: android.content.Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun AttachmentAudioPreviewDialog(
    file: File,
    title: String,
    onDismiss: () -> Unit,
    onBeforeStart: () -> Unit,
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var posMs by remember { mutableIntStateOf(0) }
    var durMs by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(file) {
        onBeforeStart()
        val mp = MediaPlayer()
        try {
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener { p ->
                durMs = p.duration
                p.start()
                isPlaying = true
            }
            mp.setOnCompletionListener {
                isPlaying = false
                posMs = 0
            }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            loadError = e.message
        }
        onDispose {
            runCatching {
                mp.release()
            }
            player = null
        }
    }

    val p = player
    LaunchedEffect(p, isPlaying) {
        if (p == null || !isPlaying) return@LaunchedEffect
        while (true) {
            posMs = try {
                p.currentPosition
            } catch (_: Exception) {
                0
            }
            delay(400)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = context.getString(R.string.back))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                }
                if (loadError != null) {
                    Text(
                        loadError!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                } else if (p == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val progress = if (durMs > 0) posMs.toFloat() / durMs.toFloat() else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                    Text(
                        formatMs(posMs) + " / " + formatMs(durMs),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Row(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                runCatching {
                                    if (isPlaying) {
                                        p.pause()
                                        isPlaying = false
                                    } else {
                                        p.start()
                                        isPlaying = true
                                    }
                                }
                            },
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

@Composable
private fun AttachmentImagePreviewDialog(
    file: File,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 6f)
                                offset += pan
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(file)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = context.getString(R.string.back),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentVideoPreviewDialog(
    file: File,
    onDismiss: () -> Unit,
    onBeforeStart: () -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(file) {
        onBeforeStart()
        onDispose { }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            setVideoPath(file.absolutePath)
                            setOnPreparedListener { it.start() }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { it.stopPlayback() },
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = context.getString(R.string.back),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentTextPreviewDialog(
    file: File,
    title: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var content by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(file) {
        content = runCatching {
            if (file.length() > 500_000L) {
                context.getString(R.string.attachment_text_too_large)
            } else {
                file.readText(Charsets.UTF_8)
            }
        }.getOrElse { e ->
            err = e.message
            null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = context.getString(R.string.back))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                val scroll = rememberScrollState()
                when {
                    err != null -> Text(
                        err!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                    content == null -> Box(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    else -> Text(
                        content!!,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scroll)
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}
