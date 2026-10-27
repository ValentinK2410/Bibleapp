package com.example.bible.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.bible.R
import com.example.bible.data.MediaHomeSectionOrder

/** Иллюстрации разделов каталога медиа. */
@Composable
internal fun MediaHubArt(sectionId: String, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(mediaHubArtRes(sectionId)),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@DrawableRes
private fun mediaHubArtRes(sectionId: String): Int = when (sectionId) {
    MediaHomeSectionOrder.PICTURES -> R.drawable.media_hub_pictures
    MediaHomeSectionOrder.VIDEOS -> R.drawable.media_hub_video
    MediaHomeSectionOrder.AUDIOS -> R.drawable.media_hub_audio
    MediaHomeSectionOrder.PESNOPENIE -> R.drawable.media_hub_songs
    MediaHomeSectionOrder.MUSICIAN -> R.drawable.media_hub_musician
    else -> R.drawable.media_hub_microblog
}
