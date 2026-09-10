package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.bible.R
import com.example.bible.data.TimemarkProject
import com.example.bible.data.activeTimemarkCue
import com.example.bible.data.formatTimemarkTimeMs
import java.io.File

/**
 * Панель активной метки таймкода при озвучке главы ([BibleAudioPlayer]).
 * Позиция трека приходит извне — отдельный плеер не используется.
 */
@Composable
fun TimemarkReaderBar(
    project: TimemarkProject,
    positionMs: Long,
    onHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val activeCue = activeTimemarkCue(positionMs, project.cues)
    val noteLine = activeCue?.note?.takeIf { it.isNotBlank() }
    val imagePath = activeCue?.attachments?.firstOrNull { it.kind == "image" && !it.path.isNullOrBlank() }?.path
    val hasExtra = noteLine != null || (imagePath != null && File(imagePath).exists())

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .onGloballyPositioned { coords ->
                onHeightChanged(with(density) { coords.size.height.toDp() })
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                stringResource(R.string.timemark_reader_section_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                project.title.ifBlank { stringResource(R.string.timemark_untitled_project) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            Text(
                formatTimemarkTimeMs(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasExtra) {
                if (noteLine != null) {
                    Text(
                        noteLine,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (imagePath != null) {
                    val imgFile = File(imagePath)
                    if (imgFile.exists()) {
                        AsyncImage(
                            model = imgFile,
                            contentDescription = stringResource(R.string.timemark_image_for_mark),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(10.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}
