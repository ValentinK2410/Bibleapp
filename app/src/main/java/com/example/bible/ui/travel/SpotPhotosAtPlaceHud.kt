package com.example.bible.ui.travel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.travel.TRAVEL_SPOT_ROUTE_PHOTO_FRAME_SCALE_MAX
import com.example.bible.data.travel.TRAVEL_SPOT_ROUTE_PHOTO_FRAME_SCALE_MIN
import com.example.bible.data.travel.TravelRoutePhotoPoint

@Composable
fun SpotPhotosAtPlaceHudSection(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    photos: List<TravelRoutePhotoPoint>,
    photoFrameScale: Float,
    onPhotoFrameScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        IconToggleButton(
            checked = expanded,
            onCheckedChange = onExpandedChange,
        ) {
            Icon(
                Icons.Filled.PhotoLibrary,
                contentDescription = stringResource(R.string.travel_spot_photos_toggle_cd),
            )
        }
        if (expanded) {
            when {
                photos.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.travel_spot_photos_empty),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                    )
                }
                else -> {
                    val primary = photos.first()
                    val w = (132f * photoFrameScale).dp
                    val h = (92f * photoFrameScale).dp
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FolkBurstPhotoThumb(
                            photoUriString = primary.photoUri,
                            modifier = Modifier.size(width = w, height = h),
                        )
                        Text(
                            text = stringResource(R.string.travel_spot_photo_size_label),
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
            }
        }
    }
}
