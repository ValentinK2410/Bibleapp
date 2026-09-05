package com.example.bible.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TravelExplore
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.BibleUserAudio
import com.example.bible.data.MediaCatalogPaths
import com.example.bible.data.MediaLibrarySort
import com.example.bible.data.UserMediaKind
import com.example.bible.data.UserMediaPlaylistKind
import com.example.bible.data.sortedByMediaLibrary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLibraryScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onOpenAudioDownload: () -> Unit = {},
    onOpenPlaylists: () -> Unit = {},
) {
    val context = LocalContext.current
    val audios by viewModel.bibleUserAudios.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.userMediaPlaybackProgress.collectAsStateWithLifecycle()
    val titleScale by viewModel.videoLibraryTitleScale.collectAsStateWithLifecycle()
    val audioItems = remember(audios) {
        audios.filter { MediaCatalogPaths.isLikelyAudioFileName(it.fileName) }
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var showMetaDialog by remember { mutableStateOf(false) }
    var metaEditing by remember { mutableStateOf<BibleUserAudio?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSource by remember { mutableStateOf("gallery") }
    var importingCount by remember { mutableStateOf<Int?>(null) }

    var draftTitle by remember { mutableStateOf("") }
    var draftTags by remember { mutableStateOf("") }

    var librarySearchQuery by remember { mutableStateOf("") }
    var librarySort by rememberSaveable { mutableStateOf(MediaLibrarySort.NEWEST.name) }
    val audioSort = MediaLibrarySort.fromName(librarySort)
    var playlistTargetAudio by remember { mutableStateOf<BibleUserAudio?>(null) }
    /** Очередь встроенного плеера: только файлы на диске, порядок как в списке. */
    var libraryAudioTracksAndStart by remember { mutableStateOf<Pair<List<Pair<BibleUserAudio, File>>, Int>?>(null) }
    /** Если системное приложение не зарегистрировало [MediaStore.Audio.Media.RECORD_SOUND_ACTION], пишем через MediaRecorder. */
    var showInAppRecorder by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        viewModel.syncLegacyAudioDownloadsFromPublicFolder()
    }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        pendingSource = "gallery"
        if (uris.size == 1) {
            pendingUri = uris.first()
            draftTitle = ""
            draftTags = ""
            showMetaDialog = true
        } else {
            importingCount = uris.size
            viewModel.importBibleAudiosFromUris(uris, source = "gallery") { ok, fail ->
                importingCount = null
                val msg = when {
                    fail == 0 -> "Добавлено файлов: $ok"
                    ok == 0 -> "Не удалось добавить файлы"
                    else -> "Добавлено $ok, ошибок $fail"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val recordSoundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                pendingUri = uri
                pendingSource = "recorder"
                draftTitle = ""
                draftTags = ""
                showMetaDialog = true
            }
        }
    }

    fun launchRecordSound() {
        val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        if (intent.resolveActivity(context.packageManager) != null) {
            recordSoundLauncher.launch(intent)
        } else {
            showInAppRecorder = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Медиа — Аудио")
                        Text(
                            "${audioItems.size} в базе",
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
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить")
            }
        },
    ) { padding ->
        val filteredSorted = remember(audioItems, librarySearchQuery, audioSort, playbackProgress) {
            audioItems
                .filter { it.matchesMediaSearch(librarySearchQuery) }
                .sortedByMediaLibrary(
                    sort = audioSort,
                    title = { it.title },
                    addedAt = { it.addedAt },
                    lastPlayedAt = { playbackProgress[it.id]?.updatedAt ?: 0L },
                )
        }
        val playableFiltered = remember(filteredSorted) {
            filteredSorted.mapNotNull { a ->
                val fl = MediaCatalogPaths.audioFile(context, a.fileName)
                if (fl.exists()) a to fl else null
            }
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            MediaLibrarySearchRow(
                query = librarySearchQuery,
                onQueryChange = { librarySearchQuery = it },
                sort = audioSort,
                onSortChange = { librarySort = it.name },
                kind = UserMediaKind.AUDIO,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Text(
                "▶ — в приложении · ⋮ — плейлист, другое приложение, поделиться, удалить · A− / A+ размер названий.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
            )
            Spacer(Modifier.height(4.dp))
            if (audioItems.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Пока пусто.\nНажмите + — файлы, запись или «Поиск в интернете». Файлы из «Загрузки/Bible» подхватываются автоматически.",
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
                        "Ничего не найдено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = 4.dp,
                        bottom = MediaLibraryFabListBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filteredSorted, key = { it.id }) { item ->
                        val f = MediaCatalogPaths.audioFile(context, item.fileName)
                        val titleSp = (VideoLibraryFontDefaults.BASE_TITLE_SP * titleScale).sp
                        val metaSp = (VideoLibraryFontDefaults.BASE_META_SP * titleScale).sp
                        val metaLine = buildString {
                            if (f.exists()) {
                                append(mediaLibrarySizeMb(f.length()))
                                append(" · ")
                            }
                            append(dateFmt.format(Date(item.addedAt)))
                            append(" · ")
                            append(mediaLibrarySourceLabelRu(item.source))
                        }
                        LibraryCompactAudioRow(
                            title = item.title,
                            titleSp = titleSp,
                            metaSp = metaSp,
                            metaLine = metaLine,
                            progress = playbackProgress[item.id],
                            onPlayInApp = {
                                if (playableFiltered.isEmpty()) {
                                    Toast.makeText(context, "Нет файлов для воспроизведения", Toast.LENGTH_SHORT).show()
                                    return@LibraryCompactAudioRow
                                }
                                if (!f.exists()) {
                                    Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                                    return@LibraryCompactAudioRow
                                }
                                val ix = playableFiltered.indexOfFirst { it.first.id == item.id }
                                    .let { i -> if (i >= 0) i else 0 }
                                libraryAudioTracksAndStart = playableFiltered to ix
                            },
                            onPlayExternally = {
                                if (!f.exists()) {
                                    Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                                    return@LibraryCompactAudioRow
                                }
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        f,
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
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
                            onEdit = {
                                pendingUri = null
                                metaEditing = item
                                draftTitle = item.title
                                draftTags = item.tags.joinToString(", ")
                                showMetaDialog = true
                            },
                            onToggleWatched = {
                                val p = playbackProgress[item.id]
                                if (p?.completed == true) {
                                    viewModel.unmarkMediaFullyWatched(item.id)
                                    Toast.makeText(context, "Отметка снята", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.markMediaFullyWatched(
                                        item.id,
                                        UserMediaKind.AUDIO,
                                        p?.durationMs ?: 0L,
                                    )
                                    Toast.makeText(context, "Отмечено как прослушанное", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onAddToPlaylist = { playlistTargetAudio = item },
                            onShare = {
                                if (!f.exists()) {
                                    Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                                } else {
                                    shareMediaFile(context, f, "audio/*")
                                }
                            },
                            onDelete = {
                                viewModel.deleteBibleAudio(item)
                                Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
        }
    }

    playlistTargetAudio?.let { audio ->
        AddMediaToPlaylistSheet(
            viewModel = viewModel,
            kind = UserMediaPlaylistKind.AUDIO,
            mediaItemId = audio.id,
            onDismiss = { playlistTargetAudio = null },
        )
    }

    libraryAudioTracksAndStart?.let { (tracks, startIx) ->
        PlaylistAudioPlayerSheet(
            tracks = tracks,
            startIndex = startIx,
            initialSeekByMediaId = buildInitialSeekMap(tracks.map { it.first.id }, playbackProgress),
            onPlaybackProgress = { mediaId, pos, dur ->
                viewModel.updateMediaPlaybackProgress(mediaId, UserMediaKind.AUDIO, pos, dur)
            },
            onMarkFullyWatched = { mediaId, dur ->
                viewModel.markMediaFullyWatched(mediaId, UserMediaKind.AUDIO, dur)
            },
            onDismiss = { libraryAudioTracksAndStart = null },
        )
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(16.dp).padding(bottom = 24.dp)) {
                Text("Добавить аудио", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddSheet = false
                            pickLauncher.launch(arrayOf("audio/*"))
                        },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null)
                        Text("Из файлов / галереи — можно несколько", modifier = Modifier.padding(start = 16.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddSheet = false
                            launchRecordSound()
                        },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null)
                        Text("Записать звук", modifier = Modifier.padding(start = 16.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddSheet = false
                            onOpenAudioDownload()
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
            title = { Text(if (isNew) "Новое аудио" else "Редактирование") },
            text = {
                Column {
                    if (isNew && pendingUri != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
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
                                viewModel.deleteBibleAudio(ed)
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
                            viewModel.importBibleAudioFromUri(
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
                                viewModel.updateBibleAudio(
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

    if (importingCount != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Добавление аудио") },
            text = { Text("Копирую файлы: $importingCount…") },
            confirmButton = {},
        )
    }

    if (showInAppRecorder) {
        LexiconAudioRecorderDialog(
            onDismiss = { showInAppRecorder = false },
            onUriRecorded = { uri ->
                showInAppRecorder = false
                pendingUri = uri
                pendingSource = "recorder"
                draftTitle = ""
                draftTags = ""
                showMetaDialog = true
            },
        )
    }
}
