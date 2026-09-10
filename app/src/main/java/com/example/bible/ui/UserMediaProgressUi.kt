package com.example.bible.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bible.data.UserMediaKind
import com.example.bible.data.UserMediaPlaybackProgress
import com.example.bible.data.completedLabelRu

fun buildInitialSeekMap(
    mediaIds: Collection<String>,
    progressMap: Map<String, UserMediaPlaybackProgress>,
): Map<String, Long> = mediaIds.mapNotNull { id ->
    val p = progressMap[id]
    if (p != null && p.isResumable()) id to p.positionMs else null
}.toMap()

@Composable
fun MediaProgressIndicator(
    progress: UserMediaPlaybackProgress?,
    kind: UserMediaKind,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val label = progress.completedLabelRu(kind)
    if (!compact && progress != null && progress.percent > 0f && !progress.completed) {
        LinearProgressIndicator(
            progress = { progress.percent },
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
    if (label.isNotBlank()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (progress?.completed == true) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.tertiary
            },
            modifier = if (compact) modifier else modifier.padding(top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Тонкая полоска прогресса поверх превью в плейлисте. */
@Composable
fun MediaProgressThumbOverlay(
    progress: UserMediaPlaybackProgress?,
    modifier: Modifier = Modifier,
) {
    if (progress == null || progress.completed || progress.percent <= 0f) return
    LinearProgressIndicator(
        progress = { progress.percent },
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp),
        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
    )
}

@Composable
fun MediaWatchedToggleButton(
    progress: UserMediaPlaybackProgress?,
    kind: UserMediaKind,
    onToggle: () -> Unit,
) {
    val completed = progress?.completed == true
    val cd = when {
        completed && kind == UserMediaKind.VIDEO -> "Снять отметку просмотрено"
        completed -> "Снять отметку прослушано"
        kind == UserMediaKind.VIDEO -> "Отметить просмотренным"
        else -> "Отметить прослушанным"
    }
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (completed) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
            contentDescription = cd,
            tint = if (completed) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
