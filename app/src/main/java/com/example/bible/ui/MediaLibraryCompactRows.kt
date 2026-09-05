package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.bible.data.UserMediaKind
import com.example.bible.data.UserMediaPlaybackProgress
import com.example.bible.data.completedLabelRu
import java.io.File

/** Чтобы FAB «+» не перекрывал последний пункт, даже если список не крутится. */
internal val MediaLibraryFabListBottomPadding = 88.dp

internal fun mediaLibrarySourceLabelRu(source: String): String = when (source) {
    "download" -> "Скачано"
    "camera" -> "Камера"
    "recorder" -> "Запись"
    "commons" -> "Интернет"
    else -> "Файлы"
}

internal fun mediaLibrarySizeMb(bytes: Long): String =
    "%.1f МБ".format(bytes / (1024.0 * 1024.0))

@Composable
internal fun LibraryCompactVideoRow(
    title: String,
    titleSp: TextUnit,
    metaSp: TextUnit,
    metaLine: String,
    videoFile: File,
    progress: UserMediaPlaybackProgress?,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onToggleWatched: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 88.dp, height = 50.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPlay),
        ) {
            VideoFileThumbnail(file = videoFile, modifier = Modifier.fillMaxSize())
            MediaProgressThumbOverlay(
                progress = progress,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        LibraryCompactTextBlock(
            title = title,
            titleSp = titleSp,
            metaLine = metaLine,
            metaSp = metaSp,
            progress = progress,
            kind = UserMediaKind.VIDEO,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .clickable(onClick = onPlay),
        )
        FilledTonalIconButton(
            onClick = onPlay,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Воспроизвести",
                modifier = Modifier.size(20.dp),
            )
        }
        LibraryCompactOverflowMenu(
            progress = progress,
            kind = UserMediaKind.VIDEO,
            onToggleWatched = onToggleWatched,
            onEdit = onEdit,
            onAddToPlaylist = onAddToPlaylist,
            onShare = onShare,
            onDelete = onDelete,
        )
    }
}

@Composable
internal fun LibraryCompactAudioRow(
    title: String,
    titleSp: TextUnit,
    metaSp: TextUnit,
    metaLine: String,
    progress: UserMediaPlaybackProgress?,
    onPlayInApp: () -> Unit,
    onPlayExternally: () -> Unit,
    onEdit: () -> Unit,
    onToggleWatched: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onPlayInApp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            MediaProgressThumbOverlay(
                progress = progress,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        LibraryCompactTextBlock(
            title = title,
            titleSp = titleSp,
            metaLine = metaLine,
            metaSp = metaSp,
            progress = progress,
            kind = UserMediaKind.AUDIO,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .clickable(onClick = onPlayInApp),
        )
        FilledTonalIconButton(
            onClick = onPlayInApp,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "В приложении",
                modifier = Modifier.size(20.dp),
            )
        }
        LibraryCompactOverflowMenu(
            progress = progress,
            kind = UserMediaKind.AUDIO,
            onToggleWatched = onToggleWatched,
            onEdit = onEdit,
            onAddToPlaylist = onAddToPlaylist,
            onShare = onShare,
            onDelete = onDelete,
            onPlayExternal = onPlayExternally,
        )
    }
}

@Composable
private fun LibraryCompactTextBlock(
    title: String,
    titleSp: TextUnit,
    metaLine: String,
    metaSp: TextUnit,
    progress: UserMediaPlaybackProgress?,
    kind: UserMediaKind,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = titleSp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            metaLine,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = metaSp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        val progressLabel = progress.completedLabelRu(kind)
        if (progress != null && progress.percent > 0f && !progress.completed) {
            LinearProgressIndicator(
                progress = { progress.percent },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(3.dp),
            )
        }
        if (progressLabel.isNotBlank()) {
            Text(
                progressLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (progress?.completed == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun LibraryCompactOverflowMenu(
    progress: UserMediaPlaybackProgress?,
    kind: UserMediaKind,
    onToggleWatched: () -> Unit,
    onEdit: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onPlayExternal: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val completed = progress?.completed == true
    val watchedLabel = when {
        completed && kind == UserMediaKind.VIDEO -> "Снять отметку просмотрено"
        completed -> "Снять отметку прослушано"
        kind == UserMediaKind.VIDEO -> "Отметить просмотренным"
        else -> "Отметить прослушанным"
    }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Ещё",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Изменить название") },
                onClick = {
                    expanded = false
                    onEdit()
                },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text("В плейлист") },
                onClick = {
                    expanded = false
                    onAddToPlaylist()
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text(watchedLabel) },
                onClick = {
                    expanded = false
                    onToggleWatched()
                },
                leadingIcon = {
                    Icon(
                        if (completed) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = if (completed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
            )
            if (onPlayExternal != null) {
                DropdownMenuItem(
                    text = { Text("В другом приложении") },
                    onClick = {
                        expanded = false
                        onPlayExternal()
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                )
            }
            DropdownMenuItem(
                text = { Text("Поделиться") },
                onClick = {
                    expanded = false
                    onShare()
                },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text("Удалить") },
                onClick = {
                    expanded = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }
}
