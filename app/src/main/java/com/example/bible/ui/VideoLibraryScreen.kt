package com.example.bible.ui

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.BibleUserVideo
import com.example.bible.data.MediaCatalogPaths
import com.example.bible.data.UserMediaKind
import com.example.bible.data.UserMediaPlaylistKind
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun BibleUserVideo.matchesMediaSearch(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val tokens = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    val titleLc = title.lowercase()
    return tokens.all { token ->
        titleLc.contains(token) || tags.any { it.lowercase().contains(token) }
    }
}

/**
 * Компактная строка поиска: занимает вдвое меньше места, чем обычное поле Material,
 * — список видео важнее, чем рамка вокруг запроса.
 */
@Composable
private fun CompactSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Поиск по названию или меткам",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Очистить",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibraryScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onOpenVideoDownload: () -> Unit = {},
    onOpenPlaylists: () -> Unit = {},
) {
    val context = LocalContext.current
    val videos by viewModel.bibleUserVideos.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.userMediaPlaybackProgress.collectAsStateWithLifecycle()
    val titleScale by viewModel.videoLibraryTitleScale.collectAsStateWithLifecycle()
    val videoItems = remember(videos) {
        videos.filter { MediaCatalogPaths.isLikelyVideoFileName(it.fileName) }
    }
    val mediaPlaylists by viewModel.userMediaPlaylists.collectAsStateWithLifecycle()
    /** Видео, уже разложенные по плейлистам: в общем списке их прячем. */
    val playlistVideoIds = remember(mediaPlaylists) {
        mediaPlaylists
            .filter { it.kind == UserMediaPlaylistKind.VIDEO }
            .flatMap { it.itemIds }
            .toSet()
    }
    var showPlaylistVideos by remember { mutableStateOf(false) }

    var showAddSheet by remember { mutableStateOf(false) }
    var showMetaDialog by remember { mutableStateOf(false) }
    var metaEditing by remember { mutableStateOf<BibleUserVideo?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSource by remember { mutableStateOf("gallery") }

    var draftTitle by remember { mutableStateOf("") }
    var draftTags by remember { mutableStateOf("") }

    var librarySearchQuery by remember { mutableStateOf("") }
    /** Очередь воспроизведения видеобиблиотеки: только записи с существующим файлом, порядок как в списке. */
    var libraryVideoTracksAndStartIndex by remember { mutableStateOf<Pair<List<Pair<BibleUserVideo, File>>, Int>?>(null) }
    var playlistTargetVideo by remember { mutableStateOf<BibleUserVideo?>(null) }

    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        viewModel.syncLegacyDownloadsFromPublicFolder()
    }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
            pendingSource = "gallery"
            draftTitle = ""
            draftTags = ""
            showMetaDialog = true
        }
    }

    val captureUriHolder = remember { mutableStateOf<Uri?>(null) }
    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo(),
    ) { ok ->
        if (ok && captureUriHolder.value != null) {
            pendingUri = captureUriHolder.value
            pendingSource = "camera"
            draftTitle = ""
            draftTags = ""
            showMetaDialog = true
        }
    }

    val videoPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) {
            val dir = MediaCatalogPaths.videosDir(context)
            val f = File(dir, "capture_temp.mp4")
            try {
                if (f.exists()) f.delete()
                f.createNewFile()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    f,
                )
                captureUriHolder.value = uri
                captureVideoLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Камера", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Нужны разрешения камеры и микрофона для записи видео", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchVideoCapture() {
        val allGranted = videoPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            val dir = MediaCatalogPaths.videosDir(context)
            val f = File(dir, "capture_temp.mp4")
            try {
                if (f.exists()) f.delete()
                f.createNewFile()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    f,
                )
                captureUriHolder.value = uri
                captureVideoLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Камера", Toast.LENGTH_SHORT).show()
            }
        } else {
            permissionLauncher.launch(videoPermissions)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Медиа — Видео")
                        Text(
                            "${videoItems.size} в базе",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPlaylists) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Плейлисты")
                    }
                    TextButton(
                        onClick = { viewModel.adjustVideoLibraryTitleScale(-VideoLibraryFontDefaults.STEP) },
                        enabled = titleScale > VideoLibraryFontDefaults.MIN + 0.001f,
                    ) {
                        Text("A−", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(
                        onClick = { viewModel.adjustVideoLibraryTitleScale(VideoLibraryFontDefaults.STEP) },
                        enabled = titleScale < VideoLibraryFontDefaults.MAX - 0.001f,
                    ) {
                        Text("A+", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(end = 4.dp, bottom = 4.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить")
            }
        },
    ) { padding ->
        // Видео, разложенные по плейлистам, не засоряют общий список — но находятся поиском.
        val searching = librarySearchQuery.isNotBlank()
        val filteredSorted = remember(videoItems, librarySearchQuery, playlistVideoIds, showPlaylistVideos) {
            videoItems
                .filter { it.matchesMediaSearch(librarySearchQuery) }
                .filter {
                    searching || showPlaylistVideos || it.id !in playlistVideoIds
                }
                .sortedByDescending { it.addedAt }
        }
        val hiddenByPlaylists = remember(videoItems, playlistVideoIds) {
            videoItems.count { it.id in playlistVideoIds }
        }
        val playableFiltered = remember(filteredSorted) {
            filteredSorted.mapNotNull { v ->
                val fl = MediaCatalogPaths.videoFile(context, v.fileName)
                if (fl.exists()) v to fl else null
            }
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            CompactSearchField(
                query = librarySearchQuery,
                onQueryChange = { librarySearchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (hiddenByPlaylists > 0 && !searching) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showPlaylistVideos) {
                            "Показаны все видео"
                        } else {
                            "Скрыто в плейлистах: $hiddenByPlaylists"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { showPlaylistVideos = !showPlaylistVideos },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text(
                            if (showPlaylistVideos) "Скрыть" else "Показать",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            if (videoItems.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Пока пусто.\nНажмите + — галерея, камера или «Поиск в интернете» (скачать по ссылке). Ранее скачанные файлы из папки «Загрузки/Bible» подхватятся автоматически.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (filteredSorted.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (!searching && hiddenByPlaylists > 0) {
                            "Все видео разложены по плейлистам.\nОткройте «Плейлисты» или нажмите «Показать»."
                        } else {
                            "Ничего не найдено"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filteredSorted, key = { it.id }) { vid ->
                        val f = MediaCatalogPaths.videoFile(context, vid.fileName)
                        val titleSp = (VideoLibraryFontDefaults.BASE_TITLE_SP * titleScale).sp
                        val metaSp = (VideoLibraryFontDefaults.BASE_META_SP * titleScale).sp
                        val metaLine = buildString {
                            if (f.exists()) {
                                append(mediaLibrarySizeMb(f.length()))
                                append(" · ")
                            }
                            append(dateFmt.format(Date(vid.addedAt)))
                            append(" · ")
                            append(mediaLibrarySourceLabelRu(vid.source))
                        }
                        LibraryCompactVideoRow(
                            title = vid.title,
                            titleSp = titleSp,
                            metaSp = metaSp,
                            metaLine = metaLine,
                            videoFile = f,
                            progress = playbackProgress[vid.id],
                            onPlay = {
                                if (playableFiltered.isEmpty()) {
                                    Toast.makeText(context, "Нет файлов для воспроизведения", Toast.LENGTH_SHORT).show()
                                    return@LibraryCompactVideoRow
                                }
                                if (!f.exists()) {
                                    Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                                    return@LibraryCompactVideoRow
                                }
                                val ix = playableFiltered.indexOfFirst { it.first.id == vid.id }
                                    .let { i -> if (i >= 0) i else 0 }
                                libraryVideoTracksAndStartIndex = playableFiltered to ix
                            },
                            onEdit = {
                                pendingUri = null
                                metaEditing = vid
                                draftTitle = vid.title
                                draftTags = vid.tags.joinToString(", ")
                                showMetaDialog = true
                            },
                            onToggleWatched = {
                                val p = playbackProgress[vid.id]
                                if (p?.completed == true) {
                                    viewModel.unmarkMediaFullyWatched(vid.id)
                                    Toast.makeText(context, "Отметка снята", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.markMediaFullyWatched(
                                        vid.id,
                                        UserMediaKind.VIDEO,
                                        p?.durationMs ?: 0L,
                                    )
                                    Toast.makeText(context, "Отмечено как просмотренное", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onAddToPlaylist = { playlistTargetVideo = vid },
                            onShare = {
                                if (!f.exists()) {
                                    Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                                } else {
                                    shareMediaFile(context, f, "video/*")
                                }
                            },
                            onDelete = {
                                viewModel.deleteBibleVideo(vid)
                                Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
        }
    }

    libraryVideoTracksAndStartIndex?.let { (tracks, ix) ->
        LibraryVideoPlayerDialog(
            tracks = tracks,
            startIndex = ix,
            initialSeekByMediaId = buildInitialSeekMap(tracks.map { it.first.id }, playbackProgress),
            onPlaybackProgress = { mediaId, pos, dur ->
                viewModel.updateMediaPlaybackProgress(mediaId, UserMediaKind.VIDEO, pos, dur)
            },
            onMarkFullyWatched = { mediaId, dur ->
                viewModel.markMediaFullyWatched(mediaId, UserMediaKind.VIDEO, dur)
            },
            onDismiss = { libraryVideoTracksAndStartIndex = null },
            onOpenInOtherApp = { file ->
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file,
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "video/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Видео"))
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        e.message ?: "Не удалось открыть",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }

    playlistTargetVideo?.let { vid ->
        AddMediaToPlaylistSheet(
            viewModel = viewModel,
            kind = UserMediaPlaylistKind.VIDEO,
            mediaItemId = vid.id,
            onDismiss = { playlistTargetVideo = null },
        )
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(16.dp).padding(bottom = 24.dp)) {
                Text("Добавить видео", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddSheet = false
                            pickLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                            )
                        },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null)
                        Text("Из галереи", modifier = Modifier.padding(start = 16.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddSheet = false
                            launchVideoCapture()
                        },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Text("Записать камерой", modifier = Modifier.padding(start = 16.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddSheet = false
                            onOpenVideoDownload()
                        },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.TravelExplore, contentDescription = null)
                        Text("Поиск в интернете", modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }

    if (showMetaDialog) {
        val isNew = metaEditing == null
        AlertDialog(
            onDismissRequest = {
                showMetaDialog = false
                pendingUri = null
                metaEditing = null
            },
            title = { Text(if (isNew) "Новое видео" else "Редактирование") },
            text = {
                Column {
                    if (isNew && pendingUri != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Videocam,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "Видео выбрано",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = draftTitle,
                        onValueChange = { draftTitle = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draftTags,
                        onValueChange = { draftTags = it },
                        label = { Text("Метки через запятую") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!isNew) {
                        Text(
                            "ID: ${metaEditing?.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        TextButton(
                            onClick = {
                                val ed = metaEditing ?: return@TextButton
                                viewModel.deleteBibleVideo(ed)
                                Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                                showMetaDialog = false
                                metaEditing = null
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Удалить из базы", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isNew && pendingUri != null) {
                            viewModel.importBibleVideoFromUri(
                                uri = pendingUri!!,
                                title = draftTitle,
                                tags = draftTags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                source = pendingSource,
                            ) { err ->
                                if (err != null) Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                else Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                                showMetaDialog = false
                                pendingUri = null
                            }
                        } else {
                            val ed = metaEditing
                            if (ed != null) {
                                viewModel.updateBibleVideo(
                                    ed.copy(
                                        title = draftTitle.trim().ifBlank { ed.title },
                                        tags = draftTags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                    ),
                                )
                                Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                            }
                            showMetaDialog = false
                            metaEditing = null
                        }
                    },
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMetaDialog = false
                        pendingUri = null
                        metaEditing = null
                    },
                ) {
                    Text("Отмена")
                }
            },
        )
    }
}
