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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.bible.data.travel.TravelRoutePhotoPoint
import com.example.bible.data.travel.folkBurstFilteredPointsForViewer
import java.io.File

@Composable
fun FolkBurstPreviewPanel(
    burstDraftPoints: List<TravelRoutePhotoPoint>,
    viewerLat: Double?,
    viewerLon: Double?,
    viewerHeadingDeg: Float?,
    totalCapturedCount: Int,
    onImageCaptureReady: (ImageCapture?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = burstDraftPoints
    val filtered = remember(snapshot, viewerLat, viewerLon, viewerHeadingDeg) {
        folkBurstFilteredPointsForViewer(snapshot, viewerLat, viewerLon, viewerHeadingDeg)
    }

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
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            pinchScale = (pinchScale * zoomChange).coerceIn(0.55f, 3.4f)
                            panelOffset = Offset(panelOffset.x + panChange.x, panelOffset.y + panChange.y)
                        }
                    },
            ) {
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
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(
                    items = filtered,
                    key = { "${it.photoUri}_${it.capturedAtMs}" },
                ) { point ->
                    FolkBurstPhotoThumb(
                        photoUriString = point.photoUri,
                        modifier = Modifier.size(width = 102.dp, height = 76.dp),
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
private fun FolkBurstPhotoThumb(
    photoUriString: String,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val file = remember(photoUriString) {
        runCatching { Uri.parse(photoUriString).path?.let(::File) }.getOrNull()
    }
    val request = remember(photoUriString, file) {
        val f = file?.takeIf { it.exists() && it.length() > 0L } ?: return@remember null
        ImageRequest.Builder(ctx)
            .data(f)
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
