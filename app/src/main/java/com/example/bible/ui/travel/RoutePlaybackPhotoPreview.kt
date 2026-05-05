package com.example.bible.ui.travel

import android.net.Uri
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.net.toFile
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.io.File
import java.util.Locale
import kotlin.math.max

internal data class PlaybackCoilTarget(
    val model: Any?,
    /** Локальные файлы — без дискового кэша. */
    val skipDiskCache: Boolean,
)

/**
 * Строит данные для Coil: `file://` → читаемый [File] ([toFile] / путь), иначе [Uri] / строка.
 */
internal fun playbackPhotoCoilTarget(uriStr: String): PlaybackCoilTarget {
    val t = uriStr.trim()
    if (t.isEmpty()) return PlaybackCoilTarget(t, false)
    val uri = runCatching { Uri.parse(t) }.getOrNull() ?: return PlaybackCoilTarget(t, false)
    return when (uri.scheme?.lowercase(Locale.US)) {
        "file" -> {
            val f = runCatching { uri.toFile() }.getOrNull()
                ?: uri.path?.let { path -> File(Uri.decode(path)) }
            if (f != null && f.isFile && f.canRead()) {
                PlaybackCoilTarget(f, skipDiskCache = true)
            } else {
                PlaybackCoilTarget(uri, skipDiskCache = true)
            }
        }
        else -> PlaybackCoilTarget(uri, skipDiskCache = false)
    }
}

/**
 * Превью кадров виртуального проезда: размер декода = размер блока (иначе Coil может «висеть» в Loading).
 */
@Composable
fun RoutePlaybackSmoothPhoto(
    uriStr: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val target = remember(uriStr) { playbackPhotoCoilTarget(uriStr) }

    val phColor = MaterialTheme.colorScheme.surfaceVariant
    val errColor = MaterialTheme.colorScheme.errorContainer

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val wPx = max(1, with(density) { maxWidth.roundToPx() })
        val hPx = max(1, with(density) { maxHeight.roundToPx() })
        val req = remember(target, wPx, hPx) {
            val b = ImageRequest.Builder(context)
                .data(target.model)
                .size(wPx, hPx)
                .allowHardware(false)
                .crossfade(120)
                .memoryCachePolicy(CachePolicy.ENABLED)
            if (target.skipDiskCache) {
                b.diskCachePolicy(CachePolicy.DISABLED)
            } else {
                b.diskCachePolicy(CachePolicy.ENABLED)
            }
            b.build()
        }
        AsyncImage(
            model = req,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            placeholder = remember(phColor) { ColorPainter(phColor.copy(alpha = 0.92f)) },
            error = remember(errColor) { ColorPainter(errColor.copy(alpha = 0.88f)) },
        )
    }
}
