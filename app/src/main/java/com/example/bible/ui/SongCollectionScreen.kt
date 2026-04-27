package com.example.bible.ui

import android.content.res.Configuration
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.bible.R
import com.example.bible.ui.theme.PesnopenieMaterialTheme
import com.example.bible.data.AudioPlayerHolder
import com.example.bible.data.FonkiExtractor
import com.example.bible.data.FonkiSong
import com.example.bible.data.SongCatalogHit
import com.example.bible.data.SongItem
import com.example.bible.data.SongLyricCue
import com.example.bible.data.SongShareImportError
import com.example.bible.data.SongShareImportOutcome
import com.example.bible.data.SongSharePackage
import com.example.bible.data.currentLineIndexForSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "SongCollection"

@Composable
private fun pesnopenieListHorizontalPadding(): Dp {
    return if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        8.dp
    } else {
        16.dp
    }
}

@Composable
private fun pesnopenieSongTextHorizontalPadding(): Dp {
    return if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        4.dp
    } else {
        16.dp
    }
}

private fun playFile(context: android.content.Context, path: String, mimeType: String) {
    try {
        val uri = if (path.startsWith("http://") || path.startsWith("https://")) {
            Uri.parse(path)
        } else {
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                return
            }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e(TAG, "playFile failed: $path", e)
        Toast.makeText(context, "Не удалось воспроизвести", Toast.LENGTH_SHORT).show()
    }
}

/** Текст со ссылкой на страницу приложения в Google Play (без вложений). */
private fun shareAppPlayStoreInvite(context: android.content.Context) {
    try {
        val pkg = context.packageName
        val url = "https://play.google.com/store/apps/details?id=$pkg"
        val text = context.getString(
            R.string.song_share_app_only_body,
            context.getString(R.string.app_name),
            url,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(
                send,
                context.getString(R.string.song_share_app_only_chooser),
            ),
        )
    } catch (e: Exception) {
        Log.e(TAG, "shareAppPlayStoreInvite", e)
        Toast.makeText(context, R.string.song_share_app_only_failed, Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SongCollectionScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val songs by viewModel.userSongs.collectAsState()
    val allTags by viewModel.songTags.collectAsState()
    val songHighlightLineWhilePlaying by viewModel.songHighlightLineWhilePlaying.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<SongItem?>(null) }
    var deleteTarget by remember { mutableStateOf<SongItem?>(null) }
    /** Список: выбор файла для воспроизведения из карточки, если дорожек несколько. */
    var audioPickList by remember { mutableStateOf<List<String>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeTagFilter by remember { mutableStateOf<String?>(null) }
    var pesnopenieNight by rememberSaveable { mutableStateOf(false) }

    val sortedTags = remember(allTags) { allTags.sorted() }

    val importSongZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val tmp = File(context.cacheDir, "song_import_${System.currentTimeMillis()}.zip")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                } ?: run {
                    Toast.makeText(context, R.string.song_import_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val outcome = withContext(Dispatchers.IO) {
                    SongSharePackage.importFromZip(context, tmp)
                }
                when (outcome) {
                    is SongShareImportOutcome.Err -> {
                        val msgRes = when (outcome.error) {
                            SongShareImportError.MISSING_MANIFEST -> R.string.song_import_err_manifest
                            SongShareImportError.FULL_APP_BACKUP -> R.string.song_import_err_full_backup
                            SongShareImportError.WRONG_FORMAT -> R.string.song_import_err_wrong_format
                            SongShareImportError.MISSING_OR_BAD_SONG_JSON -> R.string.song_import_err_song_json
                            SongShareImportError.NO_AUDIO_REFS -> R.string.song_import_err_no_audio_refs
                            SongShareImportError.MEDIA_ENTRY_MISSING -> R.string.song_import_err_media_missing
                            SongShareImportError.IO_OR_PARSE -> R.string.song_import_err_io
                        }
                        Toast.makeText(context, msgRes, Toast.LENGTH_LONG).show()
                    }
                    is SongShareImportOutcome.Ok -> {
                        val result = outcome.result
                        result.highlightLineWhilePlayingHint?.let { hint ->
                            viewModel.setSongHighlightLineWhilePlaying(hint)
                        }
                        result.song.tags.forEach { viewModel.addSongTag(it) }
                        viewModel.saveSong(result.song)
                        Toast.makeText(context, R.string.song_import_ok, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "import song zip", e)
                Toast.makeText(context, R.string.song_import_failed, Toast.LENGTH_SHORT).show()
            } finally {
                runCatching { tmp.delete() }
            }
        }
    }

    val filteredSongs = remember(songs, searchQuery, activeTagFilter) {
        var list = songs
        if (activeTagFilter != null) {
            list = list.filter { activeTagFilter in it.tags }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.lyrics.lowercase().contains(q)
            }
        }
        list
    }

    PesnopenieMaterialTheme(useDark = pesnopenieNight) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Песнопение", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        if (selectedSong == null) {
                            IconButton(
                                onClick = {
                                    importSongZipLauncher.launch(
                                        arrayOf(
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/octet-stream",
                                        ),
                                    )
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = stringResource(R.string.song_import_package_cd),
                                )
                            }
                        }
                        IconButton(onClick = { pesnopenieNight = !pesnopenieNight }) {
                            Icon(
                                imageVector = if (pesnopenieNight) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = stringResource(
                                    if (pesnopenieNight) {
                                        R.string.song_section_theme_day_cd
                                    } else {
                                        R.string.song_section_theme_night_cd
                                    },
                                ),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить песню")
                }
            },
        ) { padding ->
            val listH = pesnopenieListHorizontalPadding()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = listH, vertical = 8.dp),
                placeholder = { Text("Поиск песни...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, "Очистить")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
            )

            if (sortedTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = listH),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sortedTags.forEach { tag ->
                        FilterChip(
                            selected = activeTagFilter == tag,
                            onClick = {
                                activeTagFilter = if (activeTagFilter == tag) null else tag
                            },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (activeTagFilter == tag) {
                                {
                                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                }
                            } else null,
                            modifier = Modifier.height(32.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            if (filteredSongs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isNotEmpty() || activeTagFilter != null) "Ничего не найдено"
                            else "Список песен пуст",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (searchQuery.isEmpty() && activeTagFilter == null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Нажмите + чтобы добавить песню",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = listH, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredSongs, key = { it.id }) { song ->
                        SongCard(
                            song = song,
                            onClick = { selectedSong = song },
                            onDelete = { deleteTarget = song },
                            onPlayAudio = { s ->
                                val paths = s.audioPaths.filter { File(it).exists() }
                                when (paths.size) {
                                    0 -> Toast.makeText(context, "Аудиофайл не найден", Toast.LENGTH_SHORT).show()
                                    1 -> playFile(context, paths[0], "audio/*")
                                    else -> audioPickList = paths
                                }
                            },
                            onPlayVideo = { path -> playFile(context, path, "video/*") },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
        }

        if (showAddSheet) {
            AddSongSheet(
                allTags = sortedTags,
                onAddTag = { viewModel.addSongTag(it) },
                onDismiss = { showAddSheet = false },
                onSave = { song ->
                    song.tags.forEach { viewModel.addSongTag(it) }
                    viewModel.saveSong(song)
                    showAddSheet = false
                },
            )
        }

        val persistedSongFontSize by viewModel.songFontSize.collectAsState()
        if (selectedSong != null) {
            SongViewScreen(
                song = selectedSong!!,
                allTags = sortedTags,
                onAddTag = { viewModel.addSongTag(it) },
                onBack = { selectedSong = null },
                onEdit = { updated ->
                    viewModel.saveSong(updated)
                    selectedSong = updated
                },
                songFontSize = persistedSongFontSize,
                onSongFontSizeChange = { viewModel.setSongFontSize(it) },
                highlightLineWhilePlaying = songHighlightLineWhilePlaying,
                onHighlightLineChange = { viewModel.setSongHighlightLineWhilePlaying(it) },
                onSharePortableSong = {
                    scope.launch {
                        val s = selectedSong ?: return@launch
                        if (!SongSharePackage.canShareSong(s)) {
                            Toast.makeText(context, R.string.song_share_cannot, Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        try {
                            val zip = withContext(Dispatchers.IO) {
                                SongSharePackage.exportToZip(
                                    context,
                                    s,
                                    songHighlightLineWhilePlaying,
                                )
                            }
                            val shareUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                zip,
                            )
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    send,
                                    context.getString(R.string.song_share_chooser_title),
                                ),
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "share song package", e)
                            Toast.makeText(context, R.string.song_share_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }

        if (deleteTarget != null) {
            val target = deleteTarget!!
            val hasFiles = target.audioPaths.isNotEmpty() || target.videoPath != null
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Удалить песню?") },
                text = {
                    Column {
                        Text("«${target.title}»")
                        if (hasFiles) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "К этой песне прикреплены файлы. Удалить вместе с файлами?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    if (hasFiles) {
                        TextButton(onClick = {
                            target.audioPaths.forEach { runCatching { File(it).delete() } }
                            target.videoPath?.let { runCatching { File(it).delete() } }
                            viewModel.deleteSong(target.id)
                            if (selectedSong?.id == target.id) selectedSong = null
                            deleteTarget = null
                            Toast.makeText(context, "Удалено вместе с файлами", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Удалить всё", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(onClick = {
                            viewModel.deleteSong(target.id)
                            if (selectedSong?.id == target.id) selectedSong = null
                            deleteTarget = null
                        }) {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                dismissButton = {
                    Row {
                        if (hasFiles) {
                            TextButton(onClick = {
                                viewModel.deleteSong(target.id)
                                if (selectedSong?.id == target.id) selectedSong = null
                                deleteTarget = null
                                Toast.makeText(context, "Текст удалён, файлы сохранены", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Только из списка")
                            }
                        }
                        TextButton(onClick = { deleteTarget = null }) { Text("Отмена") }
                    }
                },
            )
        }
    }

    audioPickList?.let { paths ->
        AlertDialog(
            onDismissRequest = { audioPickList = null },
            title = { Text("Какой файл воспроизвести?") },
            text = {
                Column {
                    paths.forEach { path ->
                        TextButton(
                            onClick = {
                                playFile(context, path, "audio/*")
                                audioPickList = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                File(path).name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { audioPickList = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongCard(
    song: SongItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onPlayAudio: (SongItem) -> Unit,
    onPlayVideo: (String) -> Unit,
) {
    val hasAudio = song.audioPaths.isNotEmpty()
    val hasVideo = song.videoPath != null
    val hasText = song.lyrics.isNotBlank()
    val hasLyricSync = song.hasLyricSync()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val leadIcon = when {
                    hasVideo && hasAudio -> Icons.Default.VideoFile
                    hasVideo -> Icons.Default.Videocam
                    hasAudio -> Icons.Default.AudioFile
                    else -> Icons.Default.MusicNote
                }
                val leadTint = when {
                    hasVideo -> Color(0xFFE53935)
                    hasAudio -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(
                    leadIcon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = leadTint,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (song.artist.isNotBlank()) {
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasLyricSync) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = stringResource(R.string.song_lyric_sync_cd),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (hasAudio) {
                        Icon(
                            Icons.Default.AudioFile,
                            contentDescription = "Есть аудио",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (hasVideo) {
                        Icon(
                            Icons.Default.VideoFile,
                            contentDescription = "Есть видео",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFE53935),
                        )
                    }
                    if (!hasAudio && !hasVideo) {
                        Text(
                            if (hasText) "текст" else "нет медиа",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                if (hasAudio) {
                    IconButton(
                        onClick = { onPlayAudio(song) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Аудио",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (hasVideo) {
                    IconButton(
                        onClick = { onPlayVideo(song.videoPath!!) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Видео",
                            modifier = Modifier.size(22.dp),
                            tint = Color(0xFFE53935),
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                    )
                }
            }
            if (song.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    song.tags.forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(tag, style = MaterialTheme.typography.labelSmall)
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.Label, null, Modifier.size(12.dp))
                            },
                            modifier = Modifier.height(22.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddSongSheet(
    allTags: List<String>,
    onAddTag: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (SongItem) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var tabIndex by remember { mutableIntStateOf(0) }

    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var lyrics by remember { mutableStateOf("") }
    var pickedVideoPath by remember { mutableStateOf<String?>(null) }
    var pickedAudioPath by remember { mutableStateOf<String?>(null) }
    var lyricCues by remember { mutableStateOf(listOf<SongLyricCue>()) }
    val selectedTags = remember { mutableStateListOf<String>() }
    var newTagText by remember { mutableStateOf("") }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "songs_video").apply { mkdirs() }
                val ext = context.contentResolver.getType(uri)
                    ?.substringAfterLast('/')?.take(4) ?: "mp4"
                val dest = File(dir, "vid_${System.currentTimeMillis()}.$ext")
                context.contentResolver.openInputStream(uri)?.use { inp ->
                    dest.outputStream().use { out -> inp.copyTo(out) }
                }
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(dest.absolutePath)
                    val extractedTitle = mmr.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_TITLE,
                    )
                    withContext(Dispatchers.Main) {
                        pickedVideoPath = dest.absolutePath
                        if (title.isBlank() && !extractedTitle.isNullOrBlank()) {
                            title = extractedTitle
                        }
                        if (title.isBlank()) {
                            title = dest.nameWithoutExtension
                        }
                    }
                } finally {
                    mmr.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video copy failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Не удалось загрузить видео", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "songs_audio").apply { mkdirs() }
                val ext = context.contentResolver.getType(uri)
                    ?.substringAfterLast('/')?.take(4) ?: "mp3"
                val dest = File(dir, "aud_${System.currentTimeMillis()}.$ext")
                context.contentResolver.openInputStream(uri)?.use { inp ->
                    dest.outputStream().use { out -> inp.copyTo(out) }
                }
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(dest.absolutePath)
                    val extractedTitle = mmr.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_TITLE,
                    )
                    val extractedArtist = mmr.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_ARTIST,
                    )
                    withContext(Dispatchers.Main) {
                        pickedAudioPath = dest.absolutePath
                        if (title.isBlank() && !extractedTitle.isNullOrBlank()) {
                            title = extractedTitle
                        }
                        if (title.isBlank()) {
                            title = dest.nameWithoutExtension
                        }
                        if (artist.isBlank() && !extractedArtist.isNullOrBlank()) {
                            artist = extractedArtist
                        }
                    }
                } finally {
                    mmr.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio copy failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Не удалось загрузить аудио", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var linkUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf("") }

    var loadedSong by remember { mutableStateOf<FonkiSong?>(null) }
    var downloadingTrack by remember { mutableIntStateOf(-1) }
    var downloadProgress by remember { mutableFloatStateOf(-1f) }
    val downloadedPaths = remember { mutableStateListOf<String>() }

    var searchWebQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SongCatalogHit>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf("") }
    var searchAttempted by remember { mutableStateOf(false) }
    val sheetScroll = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Добавить песню",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.song_add_tab_manual))
                    }
                }
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Link, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.song_add_tab_link))
                    }
                }
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.song_add_tab_search))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .animateContentSize()
                    .then(if (tabIndex != 2) Modifier.verticalScroll(sheetScroll) else Modifier),
            ) {
                when (tabIndex) {
                0 -> {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название песни") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Исполнитель") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lyrics,
                        onValueChange = { lyrics = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        label = { Text("Текст песни") },
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { audioPickerLauncher.launch("audio/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.AudioFile, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (pickedAudioPath != null) "Аудио ✓" else "Аудио",
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.Videocam, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (pickedVideoPath != null) "Видео ✓" else "Видео",
                                maxLines = 1,
                            )
                        }
                    }

                    if (pickedAudioPath != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Default.AudioFile,
                                null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                File(pickedAudioPath!!).name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { pickedAudioPath = null },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Убрать",
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    if (pickedVideoPath != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Default.VideoFile,
                                null,
                                Modifier.size(16.dp),
                                tint = Color(0xFFE53935),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                File(pickedVideoPath!!).name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { pickedVideoPath = null },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Убрать",
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    if (pickedAudioPath != null && lyrics.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        SongLyricTimingEditor(
                            lyrics = lyrics,
                            audioPath = pickedAudioPath!!,
                            cues = lyricCues,
                            onCuesChange = { lyricCues = it },
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Метки",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (allTags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            allTags.forEach { tag ->
                                FilterChip(
                                    selected = tag in selectedTags,
                                    onClick = {
                                        if (tag in selectedTags) selectedTags.remove(tag)
                                        else selectedTags.add(tag)
                                    },
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = if (tag in selectedTags) {
                                        { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                                    } else null,
                                    modifier = Modifier.height(30.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newTagText,
                            onValueChange = { newTagText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Новая метка...") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val t = newTagText.trim()
                                if (t.isNotBlank()) {
                                    onAddTag(t)
                                    if (t !in selectedTags) selectedTags.add(t)
                                    newTagText = ""
                                }
                            },
                            enabled = newTagText.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(
                                    SongItem(
                                        title = title.trim(),
                                        artist = artist.trim(),
                                        lyrics = lyrics.trim(),
                                        audioPaths = listOfNotNull(pickedAudioPath),
                                        videoPath = pickedVideoPath,
                                        tags = selectedTags.toList(),
                                        lyricCues = lyricCues,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = title.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Добавить")
                    }
                }
                1 -> {
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = {
                            linkUrl = it
                            loadError = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://fonki.pro/minus/... или holychords.pro/...") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                val text = clipboard.getText()?.text ?: ""
                                if (text.isNotBlank()) linkUrl = text
                            }) {
                                Icon(Icons.Default.ContentPaste, "Вставить")
                            }
                        },
                        isError = loadError.isNotEmpty(),
                        supportingText = if (loadError.isNotEmpty()) {
                            { Text(loadError, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        shape = RoundedCornerShape(12.dp),
                    )

                    Spacer(Modifier.height(8.dp))

                    if (loadedSong == null) {
                        AnimatedVisibility(visible = isLoading) {
                            Column {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Загрузка страницы...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val trimmed = linkUrl.trim()
                                if (trimmed.isBlank()) return@Button
                                if (!FonkiExtractor.isFonkiUrl(trimmed)) {
                                    loadError = "Поддерживаются: fonki.pro, holychords.pro"
                                    return@Button
                                }
                                isLoading = true
                                loadError = ""
                                val mainHandler = Handler(Looper.getMainLooper())
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val song = FonkiExtractor.extract(trimmed)
                                        mainHandler.post {
                                            loadedSong = song
                                            isLoading = false
                                        }
                                    } catch (e: Throwable) {
                                        Log.e(TAG, "Extract failed", e)
                                        mainHandler.post {
                                            isLoading = false
                                            loadError = e.message ?: "Ошибка загрузки"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = linkUrl.isNotBlank() && !isLoading,
                        ) {
                            Icon(Icons.Default.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Загрузить")
                        }
                    }

                    val song = loadedSong
                    if (song != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0x224CAF50),
                            ),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50),
                                )
                                if (song.artist.isNotBlank()) {
                                    Text(
                                        song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF4CAF50),
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    song.lyrics.take(150) + if (song.lyrics.length > 150) "…" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 4,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (song.tracks.isNotEmpty()) {
                            Text(
                                "Аудио (${song.tracks.size}):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(6.dp))

                            song.tracks.forEachIndexed { idx, track ->
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            track.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(
                                            onClick = { playFile(context, track.url, "audio/*") },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                "Прослушать",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        if (downloadingTrack == idx) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    downloadingTrack = idx
                                                    downloadProgress = -1f
                                                    val mainHandler = Handler(Looper.getMainLooper())
                                                    scope.launch(Dispatchers.IO) {
                                                        try {
                                                            val file = FonkiExtractor.downloadAudio(
                                                                context = context,
                                                                url = track.url,
                                                                songTitle = song.title,
                                                                songArtist = song.artist,
                                                                trackLabel = track.label,
                                                            ) { pct ->
                                                                mainHandler.post { downloadProgress = pct.toFloat() }
                                                            }
                                                            mainHandler.post {
                                                                downloadingTrack = -1
                                                                downloadedPaths.add(file.absolutePath)
                                                                Toast.makeText(context, "Скачано: ${file.name}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } catch (e: Throwable) {
                                                            Log.e(TAG, "Track download failed", e)
                                                            mainHandler.post {
                                                                downloadingTrack = -1
                                                                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(
                                                    Icons.Default.Download,
                                                    "Скачать",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                    if (downloadingTrack == idx && downloadProgress >= 0f) {
                                        LinearProgressIndicator(
                                            progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Метки",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        if (allTags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                allTags.forEach { tag ->
                                    FilterChip(
                                        selected = tag in selectedTags,
                                        onClick = {
                                            if (tag in selectedTags) selectedTags.remove(tag)
                                            else selectedTags.add(tag)
                                        },
                                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = if (tag in selectedTags) {
                                            { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                                        } else null,
                                        modifier = Modifier.height(30.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = newTagText,
                                onValueChange = { newTagText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Новая метка...") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val t = newTagText.trim()
                                    if (t.isNotBlank()) {
                                        onAddTag(t)
                                        if (t !in selectedTags) selectedTags.add(t)
                                        newTagText = ""
                                    }
                                },
                                enabled = newTagText.isNotBlank(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                FonkiExtractor.run {
                                    scope.launch(Dispatchers.IO) {
                                        saveLyrics(song)
                                    }
                                }
                                onSave(
                                    SongItem(
                                        title = song.title,
                                        artist = song.artist,
                                        lyrics = song.lyrics,
                                        audioPaths = downloadedPaths.distinct().toList(),
                                        sourceUrl = linkUrl.trim(),
                                        tags = selectedTags.toList(),
                                    ),
                                )
                                Toast.makeText(context, "Песня добавлена!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Добавить в коллекцию")
                        }

                        TextButton(
                            onClick = {
                                loadedSong = null
                                downloadedPaths.clear()
                                downloadingTrack = -1
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Другая ссылка")
                        }
                    }
                }
                2 -> {
                    Column(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = searchWebQuery,
                            onValueChange = {
                                searchWebQuery = it
                                searchError = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.song_search_hint)) },
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val q = searchWebQuery.trim()
                                if (q.length < 2) {
                                    Toast.makeText(context, R.string.song_search_short, Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                searchAttempted = true
                                searchLoading = true
                                searchError = ""
                                searchResults = emptyList()
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val hits = FonkiExtractor.searchSongCatalog(q)
                                        withContext(Dispatchers.Main) {
                                            searchResults = hits
                                            searchLoading = false
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            searchError = e.message ?: "Ошибка"
                                            searchLoading = false
                                        }
                                    }
                                }
                            },
                            enabled = !searchLoading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.song_search_button))
                        }
                        AnimatedVisibility(visible = searchLoading) {
                            Column {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.song_search_loading),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (searchError.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                searchError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.song_search_footer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (searchAttempted && !searchLoading && searchResults.isEmpty() && searchError.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.song_search_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                        ) {
                            items(searchResults, key = { it.pageUrl }) { hit ->
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            val url = hit.pageUrl
                                            isLoading = true
                                            loadError = ""
                                            val mainHandler = Handler(Looper.getMainLooper())
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val song = FonkiExtractor.extract(url)
                                                    mainHandler.post {
                                                        loadedSong = song
                                                        linkUrl = url
                                                        tabIndex = 1
                                                        isLoading = false
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.song_search_opened_link),
                                                            Toast.LENGTH_LONG,
                                                        ).show()
                                                    }
                                                } catch (e: Throwable) {
                                                    Log.e(TAG, "Search pick extract failed", e)
                                                    mainHandler.post {
                                                        isLoading = false
                                                        Toast.makeText(
                                                            context,
                                                            e.message ?: "Ошибка",
                                                            Toast.LENGTH_LONG,
                                                        ).show()
                                                    }
                                                }
                                            }
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                hit.title,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            if (hit.artist.isNotBlank()) {
                                                Text(
                                                    hit.artist,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Text(
                                                hit.sourceLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        Icon(
                                            Icons.Default.Link,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(20.dp),
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
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SongViewScreen(
    song: SongItem,
    allTags: List<String>,
    onAddTag: (String) -> Unit,
    onBack: () -> Unit,
    onEdit: (SongItem) -> Unit,
    songFontSize: Float = 18f,
    onSongFontSizeChange: (Float) -> Unit = {},
    highlightLineWhilePlaying: Boolean = true,
    onHighlightLineChange: (Boolean) -> Unit = {},
    onSharePortableSong: () -> Unit = {},
) {
    var isEditing by remember { mutableStateOf(false) }
    var editTitle by remember(song) { mutableStateOf(song.title) }
    var editArtist by remember(song) { mutableStateOf(song.artist) }
    var editLyrics by remember(song) { mutableStateOf(song.lyrics) }
    var editLyricCues by remember(song.id) { mutableStateOf(song.lyricCues) }
    val editTags = remember(song) { mutableStateListOf<String>().apply { addAll(song.tags) } }
    var editNewTag by remember { mutableStateOf("") }
    var lyricsFontSize by remember { mutableFloatStateOf(songFontSize) }
    val context = LocalContext.current

    LaunchedEffect(song.lyricCues) {
        editLyricCues = song.lyricCues
    }

    val existingAudioPaths = remember(song.audioPaths) {
        song.audioPaths.filter { File(it).exists() }
    }
    var selectedAudioIndex by remember(song.id) { mutableIntStateOf(0) }
    LaunchedEffect(song.id, existingAudioPaths) {
        if (selectedAudioIndex >= existingAudioPaths.size) {
            selectedAudioIndex = 0
        }
    }
    val activeAudioPath = existingAudioPaths.getOrNull(selectedAudioIndex)

    val hasVideo = song.videoPath != null && File(song.videoPath).exists()
    val hasAudio = activeAudioPath != null
    val hasPlayerBar = hasAudio && !hasVideo
    val useKaraoke =
        highlightLineWhilePlaying &&
            song.hasLyricSync() &&
            song.lyrics.isNotBlank() &&
            hasAudio &&
            !hasVideo &&
            !isEditing
    val contentScroll = rememberScrollState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val multiTrackInset = if (existingAudioPaths.size > 1) {
        48.dp + 40.dp * existingAudioPaths.size + 8.dp
    } else {
        0.dp
    }
    val bottomInsetPlayer = when {
        !hasPlayerBar -> 0.dp
        isLandscape -> 72.dp + multiTrackInset
        else -> 120.dp + multiTrackInset
    }
    val contentPadV = if (isLandscape) 4.dp else 8.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        if (isLandscape) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                if (song.hasLyricSync()) {
                                    Icon(
                                        Icons.Default.SmartToy,
                                        contentDescription = stringResource(R.string.song_lyric_sync_cd),
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .size(18.dp),
                                        tint = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                Text(
                                    text = buildString {
                                        append(song.title)
                                        if (song.artist.isNotBlank()) {
                                            append(" · ")
                                            append(song.artist)
                                        }
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                if (song.hasLyricSync()) {
                                    Icon(
                                        Icons.Default.SmartToy,
                                        contentDescription = stringResource(R.string.song_lyric_sync_cd),
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .size(22.dp),
                                        tint = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        song.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (song.artist.isNotBlank()) {
                                        Text(
                                            song.artist,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = if (isLandscape) Modifier.size(40.dp) else Modifier,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Назад",
                                modifier = if (isLandscape) Modifier.size(22.dp) else Modifier,
                            )
                        }
                    },
                    actions = {
                        if (!hasVideo || song.lyrics.isNotBlank()) {
                            val iconSz = if (isLandscape) 18.dp else 20.dp
                            IconButton(
                                onClick = {
                                    lyricsFontSize = (lyricsFontSize - 2f).coerceAtLeast(3f)
                                    onSongFontSizeChange(lyricsFontSize)
                                },
                                modifier = if (isLandscape) Modifier.size(40.dp) else Modifier,
                            ) {
                                Icon(Icons.Default.TextDecrease, "Уменьшить текст", Modifier.size(iconSz))
                            }
                            IconButton(
                                onClick = {
                                    lyricsFontSize = (lyricsFontSize + 2f).coerceAtMost(150f)
                                    onSongFontSizeChange(lyricsFontSize)
                                },
                                modifier = if (isLandscape) Modifier.size(40.dp) else Modifier,
                            ) {
                                Icon(Icons.Default.TextIncrease, "Увеличить текст", Modifier.size(iconSz))
                            }
                        }
                        if (SongSharePackage.canShareSong(song) && !isEditing) {
                            IconButton(
                                onClick = onSharePortableSong,
                                modifier = if (isLandscape) Modifier.size(40.dp) else Modifier,
                            ) {
                                Icon(
                                    Icons.Filled.Share,
                                    stringResource(R.string.song_share_package_cd),
                                    modifier = if (isLandscape) Modifier.size(22.dp) else Modifier,
                                )
                            }
                        }
                        if (!isEditing) {
                            IconButton(
                                onClick = { shareAppPlayStoreInvite(context) },
                                modifier = if (isLandscape) Modifier.size(40.dp) else Modifier,
                            ) {
                                Icon(
                                    Icons.Filled.Link,
                                    stringResource(R.string.song_share_app_only_cd),
                                    modifier = if (isLandscape) Modifier.size(22.dp) else Modifier,
                                )
                            }
                        }
                        if (song.hasLyricSync() && hasAudio && !hasVideo && !isEditing) {
                            Switch(
                                checked = highlightLineWhilePlaying,
                                onCheckedChange = onHighlightLineChange,
                                modifier = if (isLandscape) {
                                    Modifier
                                        .height(40.dp)
                                        .padding(end = 2.dp)
                                } else {
                                    Modifier.padding(end = 4.dp)
                                },
                            )
                        }
                        IconButton(
                            onClick = {
                                if (isEditing) {
                                    editTags.forEach { onAddTag(it) }
                                    onEdit(
                                        song.copy(
                                            title = editTitle.trim().ifBlank { song.title },
                                            artist = editArtist.trim(),
                                            lyrics = editLyrics.trim(),
                                            tags = editTags.toList(),
                                            lyricCues = editLyricCues,
                                        ),
                                    )
                                    isEditing = false
                                } else {
                                    isEditing = true
                                }
                            },
                            modifier = if (isLandscape) Modifier.size(40.dp) else Modifier,
                        ) {
                            Icon(
                                if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                                if (isEditing) "Сохранить" else "Редактировать",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = if (isLandscape) Modifier.size(22.dp) else Modifier,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            val textH = pesnopenieSongTextHorizontalPadding()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(bottom = bottomInsetPlayer)
                    .then(if (useKaraoke) Modifier else Modifier.verticalScroll(contentScroll))
                    .padding(horizontal = textH, vertical = contentPadV),
            ) {
                if (hasVideo && !isEditing) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoPath(song.videoPath)
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = false
                                        start()
                                    }
                                    setOnErrorListener { _, what, extra ->
                                        Log.e(TAG, "VideoView error: what=$what extra=$extra")
                                        Toast.makeText(ctx, "Ошибка воспроизведения видео", Toast.LENGTH_SHORT).show()
                                        true
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (isEditing) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Исполнитель") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editLyrics,
                        onValueChange = { editLyrics = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        label = { Text("Текст") },
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Метки",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (editTags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            editTags.toList().forEach { tag ->
                                InputChip(
                                    selected = true,
                                    onClick = { editTags.remove(tag) },
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Close, "Убрать", Modifier.size(14.dp))
                                    },
                                    modifier = Modifier.height(30.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    val availableTags = allTags.filter { it !in editTags }
                    if (availableTags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            availableTags.forEach { tag ->
                                FilterChip(
                                    selected = false,
                                    onClick = { editTags.add(tag) },
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(30.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = editNewTag,
                            onValueChange = { editNewTag = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Новая метка...") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val t = editNewTag.trim()
                                if (t.isNotBlank()) {
                                    onAddTag(t)
                                    if (t !in editTags) editTags.add(t)
                                    editNewTag = ""
                                }
                            },
                            enabled = editNewTag.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        }
                    }
                    if (song.audioPaths.any { File(it).exists() } && editLyrics.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        SongLyricTimingEditor(
                            lyrics = editLyrics,
                            audioPath = song.audioPaths.first { File(it).exists() },
                            cues = editLyricCues,
                            onCuesChange = { editLyricCues = it },
                        )
                    }
                } else if (song.lyrics.isNotBlank()) {
                    if (useKaraoke) {
                        val syncPath = activeAudioPath!!
                        val ps by AudioPlayerHolder.state.collectAsState()
                        val lineLines = remember(song.lyrics) { song.lyrics.split("\n") }
                        val activeLine = remember(ps.positionMs, ps.audioPath, song.lyricCues, syncPath) {
                            if (syncPath == ps.audioPath) {
                                currentLineIndexForSong(ps.positionMs, song.lyricCues)
                            } else {
                                null
                            }
                        }
                        val listState = rememberLazyListState()
                        LaunchedEffect(activeLine) {
                            val idx = activeLine ?: return@LaunchedEffect
                            if (idx in lineLines.indices) {
                                listState.scrollToItem(idx)
                            }
                        }
                        LaunchedEffect(ps.isPlaying, ps.audioPath, syncPath) {
                            while (ps.isPlaying && ps.audioPath == syncPath) {
                                AudioPlayerHolder.updatePosition()
                                delay(200)
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            itemsIndexed(lineLines, key = { i, _ -> i }) { idx, line ->
                                val hl = activeLine == idx
                                Text(
                                    text = line.ifBlank { " " },
                                    fontSize = lyricsFontSize.sp,
                                    lineHeight = (lyricsFontSize * if (isLandscape) 1.38f else 1.5f).sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (hl) {
                                                Modifier
                                                    .background(
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                                                        RoundedCornerShape(8.dp),
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                            } else {
                                                Modifier.padding(vertical = 2.dp)
                                            },
                                        ),
                                )
                            }
                        }
                    } else {
                        Text(
                            song.lyrics,
                            fontSize = lyricsFontSize.sp,
                            lineHeight = (lyricsFontSize * if (isLandscape) 1.38f else 1.5f).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else if (!hasVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Нет текста. Нажмите ✎ чтобы добавить.",
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                if (song.sourceUrl != null && !isEditing) {
                    Spacer(Modifier.height(if (isLandscape) 6.dp else 16.dp))
                    Text(
                        "Источник: ${song.sourceUrl}",
                        style = if (isLandscape) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                Spacer(Modifier.height(if (isLandscape) 8.dp else 32.dp))
            }
        }

        if (hasPlayerBar) {
            Column(
                Modifier.align(Alignment.BottomCenter),
            ) {
                if (existingAudioPaths.size > 1) {
                    Surface(
                        tonalElevation = 3.dp,
                        shadowElevation = 0.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(
                                "Озвучка",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                existingAudioPaths.forEachIndexed { idx, path ->
                                    val name = File(path).name
                                    FilterChip(
                                        selected = idx == selectedAudioIndex,
                                        onClick = { selectedAudioIndex = idx },
                                        label = {
                                            Text(
                                                name,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 36.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                SongPlayerBar(
                    audioPath = activeAudioPath!!,
                    title = song.title,
                    modifier = Modifier.fillMaxWidth(),
                    compact = isLandscape,
                )
            }
        }
    }
}
