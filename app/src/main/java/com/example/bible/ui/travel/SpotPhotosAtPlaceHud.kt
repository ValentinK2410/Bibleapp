package com.example.bible.ui.travel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.travel.TravelRoutePhotoPoint

@Composable
fun SpotPhotosAtPlaceHudSection(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    photos: List<TravelRoutePhotoPoint>,
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
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        items(
                            items = photos,
                            key = { "${it.photoUri}_${it.capturedAtMs}" },
                        ) { point ->
                            FolkBurstPhotoThumb(
                                photoUriString = point.photoUri,
                                modifier = Modifier.size(width = 76.dp, height = 56.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
