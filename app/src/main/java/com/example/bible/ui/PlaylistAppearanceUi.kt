package com.example.bible.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.bible.data.BibleUserVideo
import com.example.bible.data.MediaCatalogPaths
import com.example.bible.data.PlaylistLook
import com.example.bible.data.UserMediaPlaylist
import com.example.bible.data.UserMediaPlaylistKind
import java.io.File

fun PlaylistLook.brush(): Brush = Brush.linearGradient(
    listOf(Color(startArgb.toInt()), Color(endArgb.toInt())),
)

@Composable
fun PlaylistCoverArt(
    playlist: UserMediaPlaylist,
    fallbackVideo: File?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cover = remember(playlist.coverFileName, playlist.id) {
        playlist.coverFileName.takeIf { it.isNotBlank() }
            ?.let { MediaCatalogPaths.playlistCoverFile(context, it) }
            ?.takeIf { it.exists() && it.length() > 0L }
    }
    Box(modifier.clip(RoundedCornerShape(18.dp))) {
        when {
            cover != null -> {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            fallbackVideo != null && fallbackVideo.exists() -> {
                VideoFileThumbnail(file = fallbackVideo, modifier = Modifier.fillMaxSize())
            }
            else -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(playlist.look.brush()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playlist.kind == UserMediaPlaylistKind.AUDIO) {
                            Icons.Filled.MusicNote
                        } else {
                            Icons.Filled.Videocam
                        },
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.62f),
                    ),
                ),
        )
    }
}

@Composable
fun PlaylistLookCard(
    playlist: UserMediaPlaylist,
    fileCount: Int,
    fallbackVideo: File?,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        PlaylistCoverArt(
            playlist = playlist,
            fallbackVideo = fallbackVideo,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val line = buildString {
                    if (playlist.subtitle.isNotBlank()) {
                        append(playlist.subtitle)
                        append(" · ")
                    }
                    append("$fileCount файлов")
                }
                Text(
                    line,
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistAppearanceSheet(
    playlist: UserMediaPlaylist,
    videos: List<BibleUserVideo>,
    onLook: (String) -> Unit,
    onSubtitle: (String) -> Unit,
    onCoverFromUri: (android.net.Uri) -> Unit,
    onCoverFromVideo: (String) -> Unit,
    onClearCover: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var subtitleDraft by remember(playlist.id, playlist.subtitle) {
        mutableStateOf(playlist.subtitle)
    }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onCoverFromUri(uri)
    }
    val videoFiles = remember(videos, playlist.itemIds) {
        playlist.itemIds.mapNotNull { id ->
            videos.firstOrNull { it.id == id }?.takeIf {
                MediaCatalogPaths.isLikelyVideoFileName(it.fileName)
            }?.let { v ->
                val f = MediaCatalogPaths.videoFile(context, v.fileName)
                if (f.exists()) v to f else null
            }
        }
    }
    val fallback = videoFiles.firstOrNull()?.second

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "Оформление плейлиста",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            PlaylistCoverArt(
                playlist = playlist,
                fallbackVideo = fallback,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = subtitleDraft,
                onValueChange = {
                    subtitleDraft = it
                    onSubtitle(it)
                },
                label = { Text("Подзаголовок") },
                placeholder = { Text("Например: лекции 2026") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Тема", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PlaylistLook.entries, key = { it.id }) { look ->
                    val selected = playlist.lookId == look.id
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onLook(look.id) }
                            .padding(4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(look.brush())
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            3.dp,
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(look.titleRu, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Превью", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "Своя картинка или кадр из видео. Если превью нет — видна выбранная тема.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = false,
                    onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    label = { Text("Из галереи") },
                )
                if (playlist.coverFileName.isNotBlank()) {
                    FilterChip(
                        selected = false,
                        onClick = onClearCover,
                        label = { Text("Убрать") },
                    )
                }
            }
            if (videoFiles.isNotEmpty()) {
                Text(
                    "Кадр из ролика плейлиста",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(videoFiles, key = { it.first.id }) { (video, file) ->
                        Column(
                            modifier = Modifier
                                .width(112.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onCoverFromVideo(video.fileName) },
                        ) {
                            VideoFileThumbnail(
                                file = file,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Text(
                                video.title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Готово")
            }
        }
    }
}
