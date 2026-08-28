package com.example.bible.ui

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.BibleUserAudio
import com.example.bible.data.BibleUserVideo
import com.example.bible.data.MediaCatalogPaths
import com.example.bible.data.UserMediaKind
import com.example.bible.data.UserMediaPlaybackProgress
import com.example.bible.data.completedLabelRu
import com.example.bible.data.UserMediaPlaylist
import com.example.bible.data.UserMediaPlaylistKind
import com.example.bible.data.UserMediaPlaylistSharePackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Те же элементы с теми же кратностями (порядок не важен). */
private fun List<String>.sameElementBag(other: List<String>): Boolean =
    groupingBy { it }.eachCount() == other.groupingBy { it }.eachCount()

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

private fun BibleUserAudio.matchesMediaSearch(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val tokens = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    val titleLc = title.lowercase()
    return tokens.all { token ->
        titleLc.contains(token) || tags.any { it.lowercase().contains(token) }
    }
}

@Composable
private fun PlaylistVideoThumb(file: File, modifier: Modifier = Modifier) {
    var bmp by remember(file.absolutePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.absolutePath) {
        bmp = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(file.absolutePath)
                r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                null
            } finally {
                try {
                    r.release()
                } catch (_: Exception) {
                }
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Filled.Videocam,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserMediaPlaylistsListScreen(
    viewModel: BibleViewModel,
    kind: UserMediaPlaylistKind,
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allPlaylists by viewModel.userMediaPlaylists.collectAsStateWithLifecycle()
    val videos by viewModel.bibleUserVideos.collectAsStateWithLifecycle()
    val audios by viewModel.bibleUserAudios.collectAsStateWithLifecycle()
    val playlists =
        remember(allPlaylists, kind) {
            allPlaylists
                .filter { it.kind == kind }
                .sortedByDescending { it.updatedAt }
        }

    var showCreateDialog by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }

    var menuPlaylistId by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<UserMediaPlaylist?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<UserMediaPlaylist?>(null) }
    var shareTarget by remember { mutableStateOf<UserMediaPlaylist?>(null) }
    var busyMessage by remember { mutableStateOf<String?>(null) }

    val importPlaylistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ext = playlistImportExtension(context, uri)
            val tmp = File(context.cacheDir, "pl_import_${System.currentTimeMillis()}$ext")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                } ?: run {
                    Toast.makeText(context, "Не удалось открыть файл", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                busyMessage = "Импорт плейлиста…"
                viewModel.importUserMediaPlaylistFromFile(tmp) { msg ->
                    busyMessage = null
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    tmp.delete()
                }
            } catch (e: Exception) {
                busyMessage = null
                tmp.delete()
                Toast.makeText(context, e.message ?: "Ошибка импорта", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val kindTitle =
        when (kind) {
            UserMediaPlaylistKind.VIDEO -> "Видео"
            UserMediaPlaylistKind.AUDIO -> "Аудио"
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Плейлисты — $kindTitle")
                        Text(
                            "${playlists.size} групп",
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
                    IconButton(
                        onClick = {
                            importPlaylistLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/json",
                                    "text/plain",
                                    "application/octet-stream",
                                ),
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Импорт плейлиста")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    draftName = ""
                    showCreateDialog = true
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Новый плейлист")
            }
        },
    ) { padding ->
        if (playlists.isEmpty()) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Нет плейлистов.\nНажмите +, задайте название — затем добавляйте файлы из списка медиа.\nИмпорт — кнопка загрузки вверху: JSON со ссылками или ZIP с файлами.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(playlists, key = { it.id }) { pl ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPlaylist(pl.id) },
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    pl.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${pl.itemIds.size} файлов",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = {
                                        menuPlaylistId =
                                            if (menuPlaylistId == pl.id) null else pl.id
                                    },
                                ) {
                                    Icon(Icons.Filled.MoreVert, "Меню")
                                }
                                DropdownMenu(
                                    expanded = menuPlaylistId == pl.id,
                                    onDismissRequest = { menuPlaylistId = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Поделиться") },
                                        onClick = {
                                            menuPlaylistId = null
                                            shareTarget = pl
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Share, null)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Переименовать") },
                                        onClick = {
                                            menuPlaylistId = null
                                            renameTarget = pl
                                            renameDraft = pl.name
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Edit, null)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Удалить плейлист") },
                                        onClick = {
                                            menuPlaylistId = null
                                            deleteTarget = pl
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Новый плейлист") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = { Text("Название") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (draftName.isNotBlank()) {
                            viewModel.createUserMediaPlaylist(draftName, kind)
                            showCreateDialog = false
                            Toast.makeText(context, "Плейлист создан", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    renameTarget?.let { pl ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Переименовать") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("Название") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameUserMediaPlaylist(pl.id, renameDraft)
                        renameTarget = null
                    },
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Отмена")
                }
            },
        )
    }

    deleteTarget?.let { pl ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить плейлист?") },
            text = {
                Text(
                    "«${pl.name}» будет удалён. Сами файлы в медиатеке останутся.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteUserMediaPlaylist(pl.id)
                        deleteTarget = null
                        Toast.makeText(context, "Плейлист удалён", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Отмена")
                }
            },
        )
    }

    shareTarget?.let { pl ->
        PlaylistShareChoiceDialog(
            playlist = pl,
            urlCount = UserMediaPlaylistSharePackage.countItemsWithUrl(pl, videos, audios),
            fileCount = UserMediaPlaylistSharePackage.countLocalFiles(context, pl, videos, audios),
            onDismiss = { shareTarget = null },
            onLinks = {
                shareTarget = null
                shareUserMediaPlaylist(
                    context = context,
                    scope = scope,
                    playlist = pl,
                    videos = videos,
                    audios = audios,
                    includeFiles = false,
                    onBusy = { busyMessage = it },
                )
            },
            onFiles = {
                shareTarget = null
                shareUserMediaPlaylist(
                    context = context,
                    scope = scope,
                    playlist = pl,
                    videos = videos,
                    audios = audios,
                    includeFiles = true,
                    onBusy = { busyMessage = it },
                )
            },
        )
    }

    PlaylistBusyDialog(busyMessage)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserMediaPlaylistDetailScreen(
    viewModel: BibleViewModel,
    playlistId: String,
    kind: UserMediaPlaylistKind,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlists by viewModel.userMediaPlaylists.collectAsStateWithLifecycle()
    val videos by viewModel.bibleUserVideos.collectAsStateWithLifecycle()
    val audios by viewModel.bibleUserAudios.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.userMediaPlaybackProgress.collectAsStateWithLifecycle()
    val titleScale by viewModel.videoLibraryTitleScale.collectAsStateWithLifecycle()

    val playlist = remember(playlists, playlistId) {
        playlists.firstOrNull { it.id == playlistId }
    }

    /** Очередь встроенного видеоплеера (скорость, очередь). */
    var videoPlayerPayload by remember { mutableStateOf<Pair<List<Pair<BibleUserVideo, File>>, Int>?>(null) }
    /** Очередь для встроенного плеера: список треков и индекс старта. */
    var audioPlayerPayload by remember { mutableStateOf<Pair<List<Pair<BibleUserAudio, File>>, Int>?>(null) }
    var deletePlaylistConfirm by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    var showPickMediaSheet by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var busyMessage by remember { mutableStateOf<String?>(null) }

    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    if (playlist == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Плейлист не найден", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val videoById = remember(videos) { videos.associateBy { it.id } }
    val audioById = remember(audios) { audios.associateBy { it.id } }
    val missingWithUrlCount = remember(playlist, videos, audios) {
        playlist.itemIds.count { id ->
            if (kind == UserMediaPlaylistKind.VIDEO) {
                val v = videoById[id] ?: return@count false
                val f = MediaCatalogPaths.videoFile(context, v.fileName)
                !(f.isFile && f.length() > 64) && !v.sourceUrl.isNullOrBlank()
            } else {
                val a = audioById[id] ?: return@count false
                val f = MediaCatalogPaths.audioFile(context, a.fileName)
                !(f.isFile && f.length() > 64) && !a.sourceUrl.isNullOrBlank()
            }
        }
    }

    val draftIds = remember(playlistId) { mutableStateListOf<String>() }
    var localReorderAwaitingPersist by remember(playlistId) { mutableStateOf(false) }

    fun buildAudioQueue(ids: List<String>): List<Pair<BibleUserAudio, File>> =
        ids.mapNotNull { id ->
            audioById[id]?.let { a -> a to MediaCatalogPaths.audioFile(context, a.fileName) }
        }

    fun buildPlayableVideoQueue(ids: List<String>): List<Pair<BibleUserVideo, File>> =
        ids.mapNotNull { id ->
            videoById[id]?.let { v ->
                val fl = MediaCatalogPaths.videoFile(context, v.fileName)
                if (fl.exists()) v to fl else null
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            playlist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${draftIds.size} в группе",
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
                    IconButton(
                        onClick = { showShareDialog = true },
                        enabled = playlist.itemIds.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Поделиться плейлистом")
                    }
                    IconButton(onClick = { showPickMediaSheet = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Добавить в плейлист")
                    }
                    IconButton(
                        onClick = {
                            renameDraft = playlist.name
                            renameOpen = true
                        },
                    ) {
                        Icon(Icons.Filled.Edit, "Переименовать")
                    }
                    IconButton(onClick = { deletePlaylistConfirm = true }) {
                        Icon(Icons.Filled.Delete, "Удалить плейлист", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showPickMediaSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить в плейлист")
            }
        },
    ) { padding ->
        val lazyListState = rememberLazyListState()
        val haptic = LocalHapticFeedback.current
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            draftIds.apply { add(to.index, removeAt(from.index)) }
            // Сохраняем один раз после отпускания: иначе гонки с коллекцией переигрывают draftIds во время перетаскивания.
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }

        LaunchedEffect(playlist.itemIds, reorderableState.isAnyItemDragging) {
            if (reorderableState.isAnyItemDragging) return@LaunchedEffect
            val cur = draftIds.toList()
            val srv = playlist.itemIds
            if (localReorderAwaitingPersist) {
                if (cur == srv) {
                    localReorderAwaitingPersist = false
                    return@LaunchedEffect
                }
                if (cur.sameElementBag(srv)) return@LaunchedEffect
                localReorderAwaitingPersist = false
            }
            if (cur != srv) {
                draftIds.clear()
                draftIds.addAll(srv)
            }
        }

        LaunchedEffect(reorderableState, playlistId) {
            var wasDragging = false
            snapshotFlow { reorderableState.isAnyItemDragging }.collect { dragging ->
                if (wasDragging && !dragging) {
                    localReorderAwaitingPersist = true
                    viewModel.setUserMediaPlaylistItemOrder(playlistId, draftIds.toList())
                }
                wasDragging = dragging
            }
        }

        val hintText =
            when (kind) {
                UserMediaPlaylistKind.VIDEO ->
                    "+ — добавить · ≡ — порядок · ▶ — воспроизвести"
                UserMediaPlaylistKind.AUDIO ->
                    "+ — добавить · ≡ — порядок · ▶ — в приложении"
            }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Text(
                hintText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 4.dp, bottom = 4.dp),
            )
            if (missingWithUrlCount > 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Нет $missingWithUrlCount ${if (missingWithUrlCount == 1) "файла" else "файлов"} на устройстве — можно скачать по ссылкам.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                busyMessage = "Скачивание…"
                                viewModel.downloadMissingUserMediaPlaylistFiles(
                                    playlistId,
                                    onProgress = { done, total ->
                                        busyMessage = "Скачивание $done / $total…"
                                    },
                                    onDone = { ok, fail ->
                                        busyMessage = null
                                        val msg = when {
                                            fail == 0 -> "Скачано файлов: $ok"
                                            ok == 0 -> "Не удалось скачать файлы"
                                            else -> "Скачано $ok, ошибок $fail"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    },
                                )
                            },
                        ) {
                            Text("Скачать")
                        }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = lazyListState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(draftIds, key = { it }) { mediaId ->
                    ReorderableItem(reorderableState, key = mediaId) { isDragging ->
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 8.dp else 0.dp,
                        label = "pl_drag",
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = elevation,
                        shadowElevation = elevation,
                        color = if (isDragging) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ) {
                        when (kind) {
                            UserMediaPlaylistKind.VIDEO -> {
                                val vid = videoById[mediaId]
                                if (vid == null) {
                                    OrphanPlaylistEntry(
                                        dragHandleModifier = Modifier.draggableHandle(),
                                        onRemove = {
                                            viewModel.removeItemFromUserMediaPlaylist(playlistId, mediaId)
                                        },
                                    )
                                } else {
                                    val f = MediaCatalogPaths.videoFile(context, vid.fileName)
                                    val titleSp =
                                        (VideoLibraryFontDefaults.BASE_TITLE_SP * titleScale).sp
                                    val metaSp =
                                        (VideoLibraryFontDefaults.BASE_META_SP * titleScale).sp
                                    val metaLine = buildString {
                                        if (f.exists()) {
                                            append("%.1f МБ".format(f.length() / (1024.0 * 1024.0)))
                                            append(" · ")
                                        } else if (!vid.sourceUrl.isNullOrBlank()) {
                                            append("нет файла · есть ссылка · ")
                                        } else {
                                            append("нет файла · ")
                                        }
                                        append(dateFmt.format(Date(vid.addedAt)))
                                    }
                                    VideoPlaylistEntry(
                                        dragHandleModifier = Modifier.draggableHandle(),
                                        title = vid.title,
                                        titleSp = titleSp,
                                        metaSp = metaSp,
                                        metaLine = metaLine,
                                        videoFile = f,
                                        progress = playbackProgress[mediaId],
                                        onToggleWatched = {
                                            val p = playbackProgress[mediaId]
                                            if (p?.completed == true) {
                                                viewModel.unmarkMediaFullyWatched(mediaId)
                                                Toast.makeText(context, "Отметка снята", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.markMediaFullyWatched(
                                                    mediaId,
                                                    UserMediaKind.VIDEO,
                                                    p?.durationMs ?: 0L,
                                                )
                                                Toast.makeText(context, "Отмечено как просмотренное", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onPlay = {
                                            if (!f.exists()) {
                                                Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val queue = buildPlayableVideoQueue(draftIds)
                                                if (queue.isEmpty()) {
                                                    Toast.makeText(
                                                        context,
                                                        "Нет файлов для воспроизведения",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                } else {
                                                    val ix =
                                                        queue.indexOfFirst { it.first.id == vid.id }
                                                            .let { i -> if (i >= 0) i else 0 }
                                                    videoPlayerPayload = queue to ix
                                                }
                                            }
                                        },
                                        onShare = {
                                            if (f.exists()) shareMediaFile(context, f, "video/*")
                                            else Toast.makeText(context, "Файл ещё не скачан", Toast.LENGTH_SHORT).show()
                                        },
                                        onDownload = if (!f.exists() && !vid.sourceUrl.isNullOrBlank()) {
                                            {
                                                busyMessage = "Скачивание…"
                                                viewModel.downloadUserMediaItemFromUrl(
                                                    mediaId,
                                                    UserMediaKind.VIDEO,
                                                ) { _, msg ->
                                                    busyMessage = null
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                        onRemoveFromPlaylist = {
                                            viewModel.removeItemFromUserMediaPlaylist(playlistId, mediaId)
                                            Toast.makeText(context, "Убрано из плейлиста", Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                }
                            }
                            UserMediaPlaylistKind.AUDIO -> {
                                val a = audioById[mediaId]
                                if (a == null) {
                                    OrphanPlaylistEntry(
                                        dragHandleModifier = Modifier.draggableHandle(),
                                        onRemove = {
                                            viewModel.removeItemFromUserMediaPlaylist(playlistId, mediaId)
                                        },
                                    )
                                } else {
                                    val f = MediaCatalogPaths.audioFile(context, a.fileName)
                                    val titleSp =
                                        (VideoLibraryFontDefaults.BASE_TITLE_SP * titleScale).sp
                                    val metaSp =
                                        (VideoLibraryFontDefaults.BASE_META_SP * titleScale).sp
                                    val metaLine = buildString {
                                        if (f.exists()) {
                                            append("%.1f МБ".format(f.length() / (1024.0 * 1024.0)))
                                            append(" · ")
                                        } else if (!a.sourceUrl.isNullOrBlank()) {
                                            append("нет файла · есть ссылка · ")
                                        } else {
                                            append("нет файла · ")
                                        }
                                        append(dateFmt.format(Date(a.addedAt)))
                                    }
                                    AudioPlaylistEntry(
                                        dragHandleModifier = Modifier.draggableHandle(),
                                        title = a.title,
                                        titleSp = titleSp,
                                        metaSp = metaSp,
                                        metaLine = metaLine,
                                        progress = playbackProgress[mediaId],
                                        onToggleWatched = {
                                            val p = playbackProgress[mediaId]
                                            if (p?.completed == true) {
                                                viewModel.unmarkMediaFullyWatched(mediaId)
                                                Toast.makeText(context, "Отметка снята", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.markMediaFullyWatched(
                                                    mediaId,
                                                    UserMediaKind.AUDIO,
                                                    p?.durationMs ?: 0L,
                                                )
                                                Toast.makeText(context, "Отмечено как прослушанное", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onPlayInApp = {
                                            if (!f.exists()) {
                                                Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val queue = buildAudioQueue(draftIds)
                                                val ix =
                                                    queue.indexOfFirst { it.first.id == a.id }
                                                if (ix < 0) {
                                                    Toast.makeText(
                                                        context,
                                                        "Не удалось открыть плеер",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                } else {
                                                    audioPlayerPayload = queue to ix
                                                }
                                            }
                                        },
                                        onPlayExternally = {
                                            if (!f.exists()) {
                                                Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                                                return@AudioPlaylistEntry
                                            }
                                            try {
                                                val uri =
                                                    FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.provider",
                                                        f,
                                                    )
                                                val intent =
                                                    Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, "audio/*")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                context.startActivity(Intent.createChooser(intent, "Аудио"))
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    e.message ?: "Не удалось открыть",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        },
                                        onShare = {
                                            if (f.exists()) shareMediaFile(context, f, "audio/*")
                                            else Toast.makeText(context, "Файл ещё не скачан", Toast.LENGTH_SHORT).show()
                                        },
                                        onDownload = if (!f.exists() && !a.sourceUrl.isNullOrBlank()) {
                                            {
                                                busyMessage = "Скачивание…"
                                                viewModel.downloadUserMediaItemFromUrl(
                                                    mediaId,
                                                    UserMediaKind.AUDIO,
                                                ) { _, msg ->
                                                    busyMessage = null
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                        onRemoveFromPlaylist = {
                                            viewModel.removeItemFromUserMediaPlaylist(playlistId, mediaId)
                                            Toast.makeText(context, "Убрано из плейлиста", Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    videoPlayerPayload?.let { (tracks, ix) ->
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
            onDismiss = { videoPlayerPayload = null },
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

    audioPlayerPayload?.let { (trackList, startIx) ->
        PlaylistAudioPlayerSheet(
            tracks = trackList,
            startIndex = startIx,
            initialSeekByMediaId = buildInitialSeekMap(trackList.map { it.first.id }, playbackProgress),
            onPlaybackProgress = { mediaId, pos, dur ->
                viewModel.updateMediaPlaybackProgress(mediaId, UserMediaKind.AUDIO, pos, dur)
            },
            onMarkFullyWatched = { mediaId, dur ->
                viewModel.markMediaFullyWatched(mediaId, UserMediaKind.AUDIO, dur)
            },
            onDismiss = { audioPlayerPayload = null },
        )
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Переименовать") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("Название") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameUserMediaPlaylist(playlistId, renameDraft)
                        renameOpen = false
                    },
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    if (deletePlaylistConfirm) {
        AlertDialog(
            onDismissRequest = { deletePlaylistConfirm = false },
            title = { Text("Удалить плейлист?") },
            text = {
                Text("Группа будет удалена; файлы в медиатеке останутся.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteUserMediaPlaylist(playlistId)
                        deletePlaylistConfirm = false
                        onBack()
                    },
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletePlaylistConfirm = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    if (showPickMediaSheet) {
        PickLibraryMediaForPlaylistSheet(
            viewModel = viewModel,
            playlistId = playlistId,
            kind = kind,
            existingItemIds = draftIds.toSet(),
            onDismiss = { showPickMediaSheet = false },
        )
    }

    if (showShareDialog) {
        PlaylistShareChoiceDialog(
            playlist = playlist,
            urlCount = UserMediaPlaylistSharePackage.countItemsWithUrl(playlist, videos, audios),
            fileCount = UserMediaPlaylistSharePackage.countLocalFiles(context, playlist, videos, audios),
            onDismiss = { showShareDialog = false },
            onLinks = {
                showShareDialog = false
                shareUserMediaPlaylist(
                    context = context,
                    scope = scope,
                    playlist = playlist,
                    videos = videos,
                    audios = audios,
                    includeFiles = false,
                    onBusy = { busyMessage = it },
                )
            },
            onFiles = {
                showShareDialog = false
                shareUserMediaPlaylist(
                    context = context,
                    scope = scope,
                    playlist = playlist,
                    videos = videos,
                    audios = audios,
                    includeFiles = true,
                    onBusy = { busyMessage = it },
                )
            },
        )
    }

    PlaylistBusyDialog(busyMessage)
}

@Composable
private fun OrphanPlaylistEntry(
    dragHandleModifier: Modifier,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistDragHandle(dragHandleModifier)
        Text(
            "Файл удалён из медиатеки",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRemove) {
            Text("Убрать")
        }
    }
}

@Composable
private fun PlaylistDragHandle(dragHandleModifier: Modifier) {
    Icon(
        Icons.Filled.DragHandle,
        contentDescription = "Порядок",
        modifier = dragHandleModifier
            .padding(horizontal = 2.dp)
            .size(22.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
    )
}

@Composable
private fun PlaylistCompactIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
    ) {
        Icon(icon, contentDescription, modifier = Modifier.size(18.dp), tint = tint)
    }
}

@Composable
private fun PlaylistEntryOverflowMenu(
    progress: UserMediaPlaybackProgress?,
    kind: UserMediaKind,
    onToggleWatched: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    onPlayExternal: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
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
        PlaylistCompactIconButton(
            onClick = { expanded = true },
            icon = Icons.Filled.MoreVert,
            contentDescription = "Ещё",
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                        tint = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    },
                )
            }
            if (onDownload != null) {
                DropdownMenuItem(
                    text = { Text("Скачать по ссылке") },
                    onClick = {
                        expanded = false
                        onDownload()
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Download, contentDescription = null)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Поделиться") },
                onClick = {
                    expanded = false
                    onShare()
                },
                leadingIcon = {
                    Icon(Icons.Filled.Share, contentDescription = null)
                },
            )
            DropdownMenuItem(
                text = { Text("Убрать из плейлиста") },
                onClick = {
                    expanded = false
                    onRemove()
                },
                leadingIcon = {
                    Icon(Icons.Filled.Remove, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
            )
        }
    }
}

@Composable
private fun PlaylistEntryTextBlock(
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
private fun VideoPlaylistEntry(
    dragHandleModifier: Modifier,
    title: String,
    titleSp: TextUnit,
    metaSp: TextUnit,
    metaLine: String,
    videoFile: File,
    progress: UserMediaPlaybackProgress? = null,
    onToggleWatched: () -> Unit = {},
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onDownload: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistDragHandle(dragHandleModifier)
        Box(
            Modifier
                .size(width = 88.dp, height = 50.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPlay),
        ) {
            PlaylistVideoThumb(videoFile, Modifier.fillMaxSize())
            MediaProgressThumbOverlay(
                progress = progress,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        PlaylistEntryTextBlock(
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
        PlaylistEntryOverflowMenu(
            progress = progress,
            kind = UserMediaKind.VIDEO,
            onToggleWatched = onToggleWatched,
            onShare = onShare,
            onRemove = onRemoveFromPlaylist,
            onDownload = onDownload,
        )
    }
}

@Composable
private fun AudioPlaylistEntry(
    dragHandleModifier: Modifier,
    title: String,
    titleSp: TextUnit,
    metaSp: TextUnit,
    metaLine: String,
    progress: UserMediaPlaybackProgress? = null,
    onToggleWatched: () -> Unit = {},
    onPlayInApp: () -> Unit,
    onPlayExternally: () -> Unit,
    onShare: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onDownload: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistDragHandle(dragHandleModifier)
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
        PlaylistEntryTextBlock(
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
        PlaylistEntryOverflowMenu(
            progress = progress,
            kind = UserMediaKind.AUDIO,
            onToggleWatched = onToggleWatched,
            onShare = onShare,
            onRemove = onRemoveFromPlaylist,
            onPlayExternal = onPlayExternally,
            onDownload = onDownload,
        )
    }
}

/** Выбор файла из медиатеки для добавления в открытый плейлист. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickLibraryMediaForPlaylistSheet(
    viewModel: BibleViewModel,
    playlistId: String,
    kind: UserMediaPlaylistKind,
    existingItemIds: Set<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val videos by viewModel.bibleUserVideos.collectAsStateWithLifecycle()
    val audios by viewModel.bibleUserAudios.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.userMediaPlaybackProgress.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var previewAudio by remember { mutableStateOf<Pair<BibleUserAudio, File>?>(null) }
    var previewVideo by remember { mutableStateOf<Pair<List<Pair<BibleUserVideo, File>>, Int>?>(null) }

    val availableVideos = remember(videos, existingItemIds, searchQuery) {
        videos
            .filter { MediaCatalogPaths.isLikelyVideoFileName(it.fileName) }
            .filter { it.id !in existingItemIds }
            .filter { it.matchesMediaSearch(searchQuery) }
            .sortedByDescending { it.addedAt }
    }
    val availableAudios = remember(audios, existingItemIds, searchQuery) {
        audios
            .filter { MediaCatalogPaths.isLikelyAudioFileName(it.fileName) }
            .filter { it.id !in existingItemIds }
            .filter { it.matchesMediaSearch(searchQuery) }
            .sortedByDescending { it.addedAt }
    }

    val kindLabel =
        when (kind) {
            UserMediaPlaylistKind.VIDEO -> "видео"
            UserMediaPlaylistKind.AUDIO -> "аудио"
        }
    val availableCount =
        when (kind) {
            UserMediaPlaylistKind.VIDEO -> availableVideos.size
            UserMediaPlaylistKind.AUDIO -> availableAudios.size
        }
    val availableIds = remember(availableVideos, availableAudios, kind) {
        when (kind) {
            UserMediaPlaylistKind.VIDEO -> availableVideos.map { it.id }
            UserMediaPlaylistKind.AUDIO -> availableAudios.map { it.id }
        }
    }
    val selectedIds = remember { mutableStateListOf<String>() }
    LaunchedEffect(existingItemIds, availableIds) {
        selectedIds.removeAll { it !in availableIds || it in existingItemIds }
    }

    fun toggleSelected(id: String) {
        if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
    }

    fun addSelected() {
        val ids = selectedIds.toList()
        if (ids.isEmpty()) return
        viewModel.addItemsToUserMediaPlaylist(playlistId, ids)
        Toast.makeText(
            context,
            if (ids.size == 1) "Добавлен 1 файл" else "Добавлено файлов: ${ids.size}",
            Toast.LENGTH_SHORT,
        ).show()
        selectedIds.clear()
    }

    fun previewAudioTrack(audio: BibleUserAudio) {
        val file = MediaCatalogPaths.audioFile(context, audio.fileName)
        if (!file.exists()) {
            Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
            return
        }
        if (previewAudio?.first?.id == audio.id) {
            previewAudio = null
            return
        }
        previewVideo = null
        previewAudio = audio to file
    }

    fun previewVideoTrack(video: BibleUserVideo) {
        val file = MediaCatalogPaths.videoFile(context, video.fileName)
        if (!file.exists()) {
            Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
            return
        }
        if (previewVideo?.first?.firstOrNull()?.first?.id == video.id) {
            previewVideo = null
            return
        }
        previewAudio = null
        previewVideo = listOf(video to file) to 0
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "Добавить $kindLabel в плейлист",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Поиск") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    availableCount == 0 && searchQuery.isBlank() ->
                        "Все $kindLabel из медиатеки уже в этом плейлисте"
                    availableCount == 0 ->
                        "Ничего не найдено"
                    else ->
                        "▶ — прослушать для ознакомления, галочка — выбрать. Доступно: $availableCount"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (availableCount > 0) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            selectedIds.clear()
                            selectedIds.addAll(availableIds)
                        },
                    ) {
                        Text("Выбрать все")
                    }
                    if (selectedIds.isNotEmpty()) {
                        TextButton(onClick = { selectedIds.clear() }) {
                            Text("Сбросить")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { addSelected() },
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Text(
                            if (selectedIds.isEmpty()) "Добавить"
                            else "Добавить (${selectedIds.size})",
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (previewAudio != null) 220.dp else 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (kind) {
                    UserMediaPlaylistKind.VIDEO -> {
                        items(availableVideos, key = { it.id }) { video ->
                            val file = MediaCatalogPaths.videoFile(context, video.fileName)
                            val checked = video.id in selectedIds
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggleSelected(video.id) },
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { toggleSelected(video.id) },
                                    )
                                    Box(Modifier.size(48.dp)) {
                                        PlaylistVideoThumb(file, Modifier.fillMaxSize())
                                    }
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(horizontal = 12.dp),
                                    ) {
                                        Text(
                                            video.title,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (file.exists()) {
                                            Text(
                                                "%.1f МБ".format(file.length() / (1024.0 * 1024.0)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    val isPreviewing = previewVideo?.first?.firstOrNull()?.first?.id == video.id
                                    FilledTonalIconButton(
                                        onClick = { previewVideoTrack(video) },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            if (isPreviewing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPreviewing) "Остановить" else "Просмотреть",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    UserMediaPlaylistKind.AUDIO -> {
                        items(availableAudios, key = { it.id }) { audio ->
                            val checked = audio.id in selectedIds
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggleSelected(audio.id) },
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { toggleSelected(audio.id) },
                                    )
                                    Box(
                                        Modifier
                                            .size(44.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.MusicNote,
                                            contentDescription = null,
                                            tint = Color(0xFFFF9800),
                                        )
                                    }
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(horizontal = 12.dp),
                                    ) {
                                        Text(
                                            audio.title,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val file = MediaCatalogPaths.audioFile(context, audio.fileName)
                                        if (file.exists()) {
                                            Text(
                                                "%.1f МБ".format(file.length() / (1024.0 * 1024.0)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    val isPreviewing = previewAudio?.first?.id == audio.id
                                    FilledTonalIconButton(
                                        onClick = { previewAudioTrack(audio) },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            if (isPreviewing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPreviewing) "Остановить" else "Прослушать",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            previewAudio?.let { (audio, file) ->
                Spacer(Modifier.height(8.dp))
                key(audio.id) {
                    PlaylistAudioPlayer(
                        tracks = listOf(audio to file),
                        startIndex = 0,
                        embedded = true,
                        autoPlayOnStart = true,
                        restartOnTrackListChange = true,
                        onClose = { previewAudio = null },
                        initialSeekByMediaId = buildInitialSeekMap(listOf(audio.id), playbackProgress),
                        onPlaybackProgress = { mediaId, pos, dur ->
                            viewModel.updateMediaPlaybackProgress(mediaId, UserMediaKind.AUDIO, pos, dur)
                        },
                        onMarkFullyWatched = { mediaId, dur ->
                            viewModel.markMediaFullyWatched(mediaId, UserMediaKind.AUDIO, dur)
                        },
                    )
                }
            }
        }
    }

    previewVideo?.let { (tracks, ix) ->
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
            onDismiss = { previewVideo = null },
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
}

/** Выбор плейлиста для добавления текущего файла. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMediaToPlaylistSheet(
    viewModel: BibleViewModel,
    kind: UserMediaPlaylistKind,
    mediaItemId: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val all by viewModel.userMediaPlaylists.collectAsStateWithLifecycle()
    val playlists =
        remember(all, kind) { all.filter { it.kind == kind }.sortedByDescending { it.updatedAt } }

    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(16.dp).padding(bottom = 28.dp)) {
            Text("Добавить в плейлист", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Новый плейлист") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = {
                    val n = newName.trim()
                    if (n.isNotEmpty()) {
                        viewModel.createUserMediaPlaylist(n, kind, listOf(mediaItemId))
                        Toast.makeText(context, "Создано и добавлено", Toast.LENGTH_SHORT).show()
                        newName = ""
                        onDismiss()
                    }
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Создать и добавить")
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Или выберите существующий:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (playlists.isEmpty()) {
                Text(
                    "Пока нет других плейлистов",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                playlists.forEach { pl ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (mediaItemId in pl.itemIds) {
                                    Toast.makeText(context, "Уже в этом плейлисте", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addItemToUserMediaPlaylist(pl.id, mediaItemId)
                                    Toast.makeText(context, "Добавлено в «${pl.name}»", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            },
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(pl.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "${pl.itemIds.size} файлов",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistShareChoiceDialog(
    playlist: UserMediaPlaylist,
    urlCount: Int,
    fileCount: Int,
    onDismiss: () -> Unit,
    onLinks: () -> Unit,
    onFiles: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Поделиться «${playlist.name}»") },
        text = {
            Column {
                Text(
                    "Только ссылки — лёгкий JSON. Получатель откроет плейлист в приложении и скачает файлы у себя на телефоне.\n\n" +
                        "Вместе с файлами — ZIP с видео/аудио. Архив может быть большим.\n\n" +
                        "Ссылок: $urlCount · файлов на устройстве: $fileCount",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onLinks) { Text("Только ссылки") }
                TextButton(
                    onClick = onFiles,
                    enabled = playlist.itemIds.isNotEmpty(),
                ) { Text("Вместе с файлами") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun PlaylistBusyDialog(message: String?) {
    if (message == null) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(message) },
        text = {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {},
    )
}

private fun playlistImportExtension(context: android.content.Context, uri: Uri): String {
    val name = uri.lastPathSegment.orEmpty().substringAfterLast('/')
    when {
        name.endsWith(".json", ignoreCase = true) -> return ".json"
        name.endsWith(".zip", ignoreCase = true) -> return ".zip"
    }
    val mime = context.contentResolver.getType(uri).orEmpty()
    return when {
        mime.contains("json", ignoreCase = true) -> ".json"
        mime.contains("zip", ignoreCase = true) -> ".zip"
        else -> ".bin"
    }
}

private fun shareUserMediaPlaylist(
    context: android.content.Context,
    scope: CoroutineScope,
    playlist: UserMediaPlaylist,
    videos: List<BibleUserVideo>,
    audios: List<BibleUserAudio>,
    includeFiles: Boolean,
    onBusy: (String?) -> Unit,
) {
    if (playlist.itemIds.isEmpty()) {
        Toast.makeText(context, "Плейлист пустой", Toast.LENGTH_SHORT).show()
        return
    }
    if (!includeFiles) {
        val urls = UserMediaPlaylistSharePackage.countItemsWithUrl(playlist, videos, audios)
        if (urls == 0) {
            Toast.makeText(
                context,
                "Нет ссылок — получатель не сможет скачать файлы сам. Можно отправить вместе с файлами.",
                Toast.LENGTH_LONG,
            ).show()
        }
    } else {
        val files = UserMediaPlaylistSharePackage.countLocalFiles(context, playlist, videos, audios)
        if (files == 0) {
            Toast.makeText(
                context,
                "Локальных файлов нет — в архиве будут только названия и ссылки.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    scope.launch {
        onBusy(if (includeFiles) "Готовим архив…" else "Готовим ссылки…")
        try {
            val file = withContext(Dispatchers.IO) {
                if (includeFiles) {
                    UserMediaPlaylistSharePackage.exportZip(
                        context,
                        playlist,
                        videos,
                        audios,
                        includeFiles = true,
                    )
                } else {
                    UserMediaPlaylistSharePackage.exportLinksJson(
                        context,
                        playlist,
                        videos,
                        audios,
                    )
                }
            }
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                if (includeFiles) {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                } else {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        UserMediaPlaylistSharePackage.linksText(playlist, videos, audios),
                    )
                    putExtra(Intent.EXTRA_SUBJECT, "Плейлист «${playlist.name}»")
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(
                    send,
                    if (includeFiles) "Плейлист с файлами" else "Плейлист — ссылки",
                ),
            )
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Не удалось поделиться", Toast.LENGTH_LONG).show()
        } finally {
            onBusy(null)
        }
    }
}
