package com.example.bible.ui

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.bible.R
import com.example.bible.data.BibleUserAudio
import com.example.bible.data.BibleUserImage
import com.example.bible.data.BibleUserVideo
import com.example.bible.data.MediaCatalogPaths
import com.example.bible.data.DictResult
import com.example.bible.data.LexiconMediaRefs
import com.example.bible.data.SemanticLexiconRule

/** Состояние нижнего окна словаря при тапе по слову. */
data class LexiconDictionarySheetState(
    val word: String,
    val results: List<DictResult>,
    val seeAlso: List<String>,
    val lexiconRule: SemanticLexiconRule? = null,
    /** Медиа к выделенному фрагменту (отдельно от правил лексикона). */
    val attachedMedia: LexiconMediaRefs? = null,
)

/**
 * Метка смысла из лексикона (без медиа — медиа только у выделения в читалке).
 */
@Composable
fun LexiconRuleHeaderAndMedia(rule: SemanticLexiconRule) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            rule.senseLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/**
 * Блок медиа к выделению / слову в окне словаря.
 */
@Composable
fun LexiconMediaSection(
    contentLabel: String,
    media: LexiconMediaRefs,
    mediaLibraryImages: List<BibleUserImage>,
    mediaLibraryVideos: List<BibleUserVideo>,
    mediaLibraryAudios: List<BibleUserAudio> = emptyList(),
) {
    if (!media.hasAny()) return
    val ctx = LocalContext.current
    LexiconMediaContent(
        media = media,
        imageContentDescription = contentLabel,
        mediaLibraryImages = mediaLibraryImages,
        mediaLibraryVideos = mediaLibraryVideos,
        mediaLibraryAudios = mediaLibraryAudios,
    )
}

@Composable
private fun LexiconMediaContent(
    media: LexiconMediaRefs,
    imageContentDescription: String,
    mediaLibraryImages: List<BibleUserImage>,
    mediaLibraryVideos: List<BibleUserVideo>,
    mediaLibraryAudios: List<BibleUserAudio>,
) {
    val m = media
    val ctx = LocalContext.current
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer?.release()
            audioPlayer = null
        }
    }

    fun playAudio(uri: Uri) {
        try {
            audioPlayer?.release()
            audioPlayer = MediaPlayer().apply {
                setDataSource(ctx, uri)
                prepare()
                start()
                setOnCompletionListener { it.release(); audioPlayer = null }
            }
        } catch (_: Exception) {
            audioPlayer?.release()
            audioPlayer = null
        }
    }

    fun playAudioUrl(url: String) {
        try {
            audioPlayer?.release()
            audioPlayer = MediaPlayer().apply {
                setDataSource(url)
                prepare()
                start()
                setOnCompletionListener { it.release(); audioPlayer = null }
            }
        } catch (_: Exception) {
            audioPlayer?.release()
            audioPlayer = null
        }
    }

    Column(Modifier.fillMaxWidth()) {
        val hasAudio = !m.audioUrl.isNullOrBlank() || !m.audioFileUri.isNullOrBlank() ||
            !m.audioLibraryId.isNullOrBlank()
        if (hasAudio) {
            RowAudioControls(
                onPlay = {
                    when {
                        !m.audioUrl.isNullOrBlank() -> playAudioUrl(m.audioUrl!!)
                        !m.audioFileUri.isNullOrBlank() -> playAudio(Uri.parse(m.audioFileUri!!))
                        !m.audioLibraryId.isNullOrBlank() -> {
                            val a = mediaLibraryAudios.find { it.id == m.audioLibraryId }
                            if (a != null) {
                                val f = MediaCatalogPaths.audioFile(ctx, a.fileName)
                                if (f.isFile) playAudio(Uri.fromFile(f))
                            }
                        }
                    }
                },
                onStop = {
                    audioPlayer?.release()
                    audioPlayer = null
                },
            )
            Spacer(Modifier.height(8.dp))
        }

        val imageModel: Any? = when {
            !m.imageUrl.isNullOrBlank() -> m.imageUrl
            !m.imageFileUri.isNullOrBlank() -> Uri.parse(m.imageFileUri)
            !m.imageLibraryId.isNullOrBlank() -> {
                val img = mediaLibraryImages.find { it.id == m.imageLibraryId }
                if (img != null) MediaCatalogPaths.pictureFile(ctx, img.fileName) else null
            }
            else -> null
        }
        if (imageModel != null) {
            LexiconZoomableImage(
                model = imageModel,
                contentDescription = imageContentDescription,
            )
            Spacer(Modifier.height(8.dp))
        }

        val videoUri: Uri? = when {
            !m.videoUrl.isNullOrBlank() -> Uri.parse(m.videoUrl)
            !m.videoFileUri.isNullOrBlank() -> Uri.parse(m.videoFileUri)
            !m.videoLibraryId.isNullOrBlank() -> {
                val v = mediaLibraryVideos.find { it.id == m.videoLibraryId }
                if (v != null) Uri.fromFile(MediaCatalogPaths.videoFile(ctx, v.fileName)) else null
            }
            else -> null
        }
        if (videoUri != null) {
            AndroidView(
                factory = { VideoView(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                update = { vv ->
                    vv.setVideoURI(videoUri)
                    vv.setOnPreparedListener { it.start() }
                },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

private const val LexiconImageZoomMin = 0.5f
private const val LexiconImageZoomMax = 6f
private const val LexiconImageZoomStep = 1.2f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LexiconZoomableImage(
    model: Any,
    contentDescription: String?,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun resetView() {
        scale = 1f
        offset = Offset.Zero
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(LexiconImageZoomMin, LexiconImageZoomMax)
                        offset += pan
                    }
                },
        ) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
                contentScale = ContentScale.Fit,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    scale = (scale / LexiconImageZoomStep).coerceIn(LexiconImageZoomMin, LexiconImageZoomMax)
                },
            ) {
                Icon(
                    Icons.Filled.ZoomOut,
                    contentDescription = stringResource(R.string.lexicon_image_zoom_out),
                )
            }
            TextButton(onClick = { resetView() }) {
                Text(stringResource(R.string.lexicon_image_zoom_reset))
            }
            IconButton(
                onClick = {
                    scale = (scale * LexiconImageZoomStep).coerceIn(LexiconImageZoomMin, LexiconImageZoomMax)
                },
            ) {
                Icon(
                    Icons.Filled.ZoomIn,
                    contentDescription = stringResource(R.string.lexicon_image_zoom_in),
                )
            }
        }
    }
}

@Composable
private fun RowAudioControls(
    onPlay: () -> Unit,
    onStop: () -> Unit,
) {
    Column {
        Text("Аудио к слову", style = MaterialTheme.typography.labelLarge)
        Row {
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Воспроизвести")
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Stop, contentDescription = "Стоп")
            }
        }
    }
}
