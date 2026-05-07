package com.example.bible.ui.travel

import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.bible.R
import com.example.bible.data.travel.TRAVEL_SPOT_ROUTE_PHOTO_FRAME_SCALE_MAX
import com.example.bible.data.travel.TRAVEL_SPOT_ROUTE_PHOTO_FRAME_SCALE_MIN
import com.example.bible.data.travel.TravelRoutePhotoPoint

@Composable
fun FolkBurstPreviewPanel(
    /** Кадры в радиусе от центра экрана карты (перекрестье), с фильтром по направлению камеры. */
    cursorIntersectPhotos: List<TravelRoutePhotoPoint>,
    totalCapturedCount: Int,
    onImageCaptureReady: (ImageCapture?) -> Unit,
    photoFrameScale: Float,
    onPhotoFrameScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var panelOffset by remember { mutableStateOf(Offset.Zero) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pinchScale
                scaleY = pinchScale
                translationX = panelOffset.x
                translationY = panelOffset.y
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    pinchScale = (pinchScale * zoomChange).coerceIn(0.42f, 3.9f)
                    panelOffset = Offset(
                        panelOffset.x + panChange.x,
                        panelOffset.y + panChange.y,
                    )
                }
            },
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 4.dp,
        shadowElevation = 5.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.travel_route_folk_title_bar),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.travel_route_folk_gesture_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    stringResource(R.string.travel_route_folk_cursor_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (cursorIntersectPhotos.isEmpty()) {
                Text(
                    stringResource(R.string.travel_route_folk_cursor_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                val primary = cursorIntersectPhotos.first()
                val w = (132f * photoFrameScale).dp
                val h = (92f * photoFrameScale).dp
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FolkBurstPhotoThumb(
                        photoUriString = primary.photoUri,
                        modifier = Modifier.size(width = w, height = h),
                    )
                    Text(
                        stringResource(R.string.travel_spot_photo_size_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = photoFrameScale,
                        onValueChange = onPhotoFrameScaleChange,
                        valueRange = TRAVEL_SPOT_ROUTE_PHOTO_FRAME_SCALE_MIN..TRAVEL_SPOT_ROUTE_PHOTO_FRAME_SCALE_MAX,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 1.dp,
            ) {
                TravelBurstCameraPreview(
                    enabled = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(108.dp),
                    onImageCaptureReady = onImageCaptureReady,
                )
            }
            Text(
                stringResource(R.string.travel_route_folk_frames_fmt, totalCapturedCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun FolkBurstPhotoThumb(
    photoUriString: String,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val uri = remember(photoUriString) {
        runCatching { Uri.parse(photoUriString.trim()) }.getOrNull()
            ?.takeUnless { it === Uri.EMPTY }
    }
    val request = remember(photoUriString, uri, ctx) {
        val u = uri ?: return@remember null
        ImageRequest.Builder(ctx)
            .data(u)
            .crossfade(false)
            .diskCachePolicy(CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    val brokenPainter = rememberVectorPainter(Icons.Outlined.BrokenImage)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                request == null -> Icon(
                    Icons.Outlined.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(12.dp),
                )
                else -> AsyncImage(
                    model = request,
                    contentDescription = stringResource(R.string.travel_route_burst_last_frame_cd),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = brokenPainter,
                    error = brokenPainter,
                )
            }
        }
    }
}
