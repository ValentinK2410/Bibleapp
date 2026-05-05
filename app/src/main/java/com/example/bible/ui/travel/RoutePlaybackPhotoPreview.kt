package com.example.bible.ui.travel

import android.content.Context
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.io.File
import java.util.Locale

/**
 * Резолвер пути для Coil: надёжно открывает `file://`, в т. ч. когда важнее [File], чем [Uri].
 */
fun playbackPhotoCoilModel(context: Context, uriStr: String): Any {
    val t = uriStr.trim()
    if (t.isEmpty()) return t
    val uri = runCatching { Uri.parse(t) }.getOrNull() ?: return t
    return when (uri.scheme?.lowercase(Locale.US)) {
        "file" -> {
            val raw = uri.path ?: return uri
            val decoded = Uri.decode(raw)
            File(decoded).takeIf { it.isFile && it.canRead() } ?: uri
        }
        else -> uri
    }
}

/**
 * Превью кадров виртуального проезда: Coil [AsyncImage] с предсказуемым состоянием загрузки/ошибки
 * (без «прозрачного» переднего слоя у старых painter’ов).
 */
@Composable
fun RoutePlaybackSmoothPhoto(
    uriStr: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val modelData = remember(uriStr) { playbackPhotoCoilModel(context, uriStr) }

    val phColor = MaterialTheme.colorScheme.surfaceVariant
    val errColor = MaterialTheme.colorScheme.errorContainer

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(modelData)
            .crossfade(120)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = remember(phColor) { ColorPainter(phColor.copy(alpha = 0.92f)) },
        error = remember(errColor) { ColorPainter(errColor.copy(alpha = 0.88f)) },
    )
}
