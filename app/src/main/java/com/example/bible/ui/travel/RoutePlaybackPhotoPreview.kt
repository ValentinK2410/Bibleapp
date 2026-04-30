package com.example.bible.ui.travel

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * Превью кадров виртуального проезда: пока новый URI декодируется, остаётся предыдущий кадр
 * (без пустого «мигания» и без crossfade Coil). Обрезку скруглением задаёт родитель ([Modifier.clip]).
 */
@Composable
fun RoutePlaybackSmoothPhoto(
    uriStr: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    var stableBackdropUri by remember { mutableStateOf(uriStr) }

    val frontRequest = remember(uriStr) {
        ImageRequest.Builder(context)
            .data(Uri.parse(uriStr))
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    val backRequest = remember(stableBackdropUri) {
        ImageRequest.Builder(context)
            .data(Uri.parse(stableBackdropUri))
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    val painterFront = rememberAsyncImagePainter(frontRequest)
    val painterBack = rememberAsyncImagePainter(backRequest)

    LaunchedEffect(painterFront.state, uriStr) {
        if (painterFront.state is AsyncImagePainter.State.Success) {
            stableBackdropUri = uriStr
        }
    }

    val showFront = painterFront.state is AsyncImagePainter.State.Success ||
        (uriStr == stableBackdropUri && painterFront.state !is AsyncImagePainter.State.Error)

    Box(modifier) {
        Image(
            painter = painterBack,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )
        Image(
            painter = painterFront,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (showFront) 1f else 0f
                },
            contentScale = contentScale,
        )
    }
}
