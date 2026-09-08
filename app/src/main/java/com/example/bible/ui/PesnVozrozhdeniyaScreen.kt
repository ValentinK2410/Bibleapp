package com.example.bible.ui

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.AudioPlayerHolder
import com.example.bible.data.FonkiExtractor
import com.example.bible.data.PlayerState
import com.example.bible.data.PesnVozrozhdeniyaCatalog
import com.example.bible.data.PvHymn
import com.example.bible.data.PvHymnOverlay
import com.example.bible.ui.theme.PesnopenieMaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal data class PvCatalogUi(
    val hymns: List<PvHymn> = emptyList(),
    val loading: Boolean = true,
)

@Composable
internal fun rememberPvCatalog(overlays: List<PvHymnOverlay>): PvCatalogUi {
    val context = LocalContext.current
    val builtIn by produceState<List<PvHymn>?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { PesnVozrozhdeniyaCatalog.builtIn(context) }
    }
    val hymns = remember(builtIn, overlays) {
        val src = builtIn ?: return@remember emptyList()
        PesnVozrozhdeniyaCatalog.merge(src, overlays)
    }
    return PvCatalogUi(hymns = hymns, loading = builtIn == null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PesnVozrozhdeniyaListScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onOpenHymn: (String) -> Unit,
) {
    val overlays by viewModel.pvHymnOverlays.collectAsState()
    val catalog = rememberPvCatalog(overlays)
    val hymns = catalog.hymns
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val q = query.trim().lowercase()
    val shown = remember(hymns, q) {
        if (q.isEmpty()) hymns
        else hymns.filter { hymn ->
            val num = hymn.number.toString()
            q == num ||
                q in num ||
                q in hymn.title.lowercase() ||
                (q.length >= 3 && q in hymn.lyrics.lowercase())
        }
    }

    PesnopenieMaterialTheme(useDark = false) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Песнь возрождения") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить гимн")
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text("Номер, название или слова из текста") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Text(
                    if (catalog.loading) {
                        "Загрузка сборника…"
                    } else {
                        "Гимнов: ${shown.size} из ${hymns.size}. Можно открыть псалом и добавить свою фонограмму."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                if (catalog.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(32.dp)
                            .align(Alignment.CenterHorizontally),
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(shown, key = { it.id }) { hymn ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenHymn(hymn.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                hymn.number.toString(),
                                modifier = Modifier.width(52.dp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    hymn.title,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (hymn.audioPaths.isNotEmpty()) {
                                    Text(
                                        "Есть аудио",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var lyrics by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Новый гимн") },
            text = {
                Column {
                    Text(
                        "Гимн добавится в конец сборника (после №3300). Аудио можно прикрепить после сохранения.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lyrics,
                        onValueChange = { lyrics = it },
                        label = { Text("Текст") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = title.trim().isNotEmpty(),
                    onClick = {
                        val n = PesnVozrozhdeniyaCatalog.nextUserNumber(hymns)
                        viewModel.savePvHymnOverlay(
                            PvHymnOverlay(
                                id = PesnVozrozhdeniyaCatalog.newUserId(),
                                number = n,
                                title = title.trim(),
                                lyrics = lyrics.trim(),
                                builtIn = false,
                            ),
                        )
                        showAdd = false
                    },
                ) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PesnVozrozhdeniyaHymnScreen(
    hymnId: String,
    viewModel: BibleViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val overlays by viewModel.pvHymnOverlays.collectAsState()
    val playlists by viewModel.userSongPlaylists.collectAsState()
    val catalog = rememberPvCatalog(overlays)
    val hymns = catalog.hymns
    val hymn = hymns.firstOrNull { it.id == hymnId }
    val persistedFontSize by viewModel.songFontSize.collectAsState()
    var lyricsFontSize by remember { mutableFloatStateOf(persistedFontSize) }
    LaunchedEffect(persistedFontSize) { lyricsFontSize = persistedFontSize }
    val playerState by AudioPlayerHolder.state.collectAsState()
    var activeAudioPath by remember { mutableStateOf<String?>(null) }
    var linkUrl by remember { mutableStateOf("") }
    var linkLoading by remember { mutableStateOf(false) }
    var showAddToList by remember { mutableStateOf(false) }
    LaunchedEffect(hymn?.id, hymn?.audioPaths, playerState.audioPath) {
        val path = playerState.audioPath
        if (path.isNotBlank() && hymn?.audioPaths?.contains(path) == true) {
            activeAudioPath = path
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null || hymn == null) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) { copyPvAudio(context, uri) }
            if (copied == null) {
                Toast.makeText(context, "Не удалось добавить аудио", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val overlay = overlayFrom(hymn).copy(
                audioPaths = hymn.audioPaths + copied.first,
                audioLabels = hymn.audioLabels + copied.second,
                audioSourceUrls = hymn.audioSourceUrls + "",
            )
            viewModel.savePvHymnOverlay(overlay)
        }
    }

    PesnopenieMaterialTheme(useDark = false) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            hymn?.let { "${it.number}. ${it.title}" } ?: "Гимн",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                lyricsFontSize = (lyricsFontSize - 2f).coerceAtLeast(3f)
                                viewModel.setSongFontSize(lyricsFontSize)
                            },
                            enabled = hymn != null,
                        ) {
                            Icon(Icons.Default.TextDecrease, contentDescription = "Уменьшить текст")
                        }
                        IconButton(
                            onClick = {
                                lyricsFontSize = (lyricsFontSize + 2f).coerceAtMost(150f)
                                viewModel.setSongFontSize(lyricsFontSize)
                            },
                            enabled = hymn != null,
                        ) {
                            Icon(Icons.Default.TextIncrease, contentDescription = "Увеличить текст")
                        }
                        IconButton(onClick = { showAddToList = true }, enabled = hymn != null) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = "В список")
                        }
                    },
                )
            },
            bottomBar = {
                val path = activeAudioPath
                if (hymn != null && path != null && File(path).exists()) {
                    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                        Column(Modifier.fillMaxWidth()) {
                            PvPhonogramModesRow(playerState = playerState)
                            SongPlayerBar(
                                audioPath = path,
                                title = "${hymn.number}. ${hymn.title}",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            if (catalog.loading) {
                CircularProgressIndicator(modifier = Modifier.padding(padding).padding(32.dp))
                return@Scaffold
            }
            if (hymn == null) {
                Text("Гимн не найден", modifier = Modifier.padding(padding).padding(16.dp))
                return@Scaffold
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    hymn.lyrics.ifBlank { "Текст пока не добавлен." },
                    fontSize = lyricsFontSize.sp,
                    lineHeight = (lyricsFontSize * 1.35f).sp,
                )
                Spacer(Modifier.height(20.dp))
                Text("Фонограмма", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Прослушивание: пауза, стоп, повтор и скорость. Текст — кнопками А− / А+ в шапке.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (hymn.audioPaths.isEmpty()) {
                    Text(
                        "Аудио ещё нет — добавьте файл с устройства или ссылку fonki.pro / holychords.pro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    hymn.audioPaths.forEachIndexed { idx, path ->
                        val exists = File(path).exists()
                        val isThis = playerState.audioPath == path
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                hymn.audioLabels.getOrNull(idx)?.ifBlank { null } ?: File(path).name,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = {
                                    if (!exists) return@IconButton
                                    if (isThis) {
                                        AudioPlayerHolder.togglePlay()
                                    } else {
                                        AudioPlayerHolder.play(path, "${hymn.number}. ${hymn.title}")
                                    }
                                    activeAudioPath = path
                                },
                                enabled = exists,
                            ) {
                                Icon(
                                    if (isThis && playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isThis && playerState.isPlaying) "Пауза" else "Играть",
                                )
                            }
                            IconButton(
                                onClick = { AudioPlayerHolder.stop() },
                                enabled = isThis && (playerState.isPlaying || playerState.positionMs > 0),
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Стоп")
                            }
                            IconButton(
                                onClick = {
                                    if (activeAudioPath == path) {
                                        AudioPlayerHolder.stop()
                                        activeAudioPath = null
                                    }
                                    val paths = hymn.audioPaths.toMutableList().also { it.removeAt(idx) }
                                    val labels = hymn.audioLabels.toMutableList().let { l ->
                                        if (idx < l.size) l.apply { removeAt(idx) } else l
                                    }
                                    val urls = hymn.audioSourceUrls.toMutableList().let { u ->
                                        if (idx < u.size) u.apply { removeAt(idx) } else u
                                    }
                                    viewModel.savePvHymnOverlay(
                                        overlayFrom(hymn).copy(
                                            audioPaths = paths,
                                            audioLabels = labels,
                                            audioSourceUrls = urls,
                                        ),
                                    )
                                },
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить дорожку")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { audioPicker.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.AudioFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить аудио с устройства")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ссылка fonki / holychords") },
                    trailingIcon = {
                        IconButton(onClick = {
                            val t = clipboard.getText()?.text.orEmpty()
                            if (t.isNotBlank()) linkUrl = t
                        }) {
                            Icon(Icons.Default.Link, contentDescription = "Вставить")
                        }
                    },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val trimmed = linkUrl.trim()
                        if (!FonkiExtractor.isFonkiUrl(trimmed)) {
                            Toast.makeText(context, "Нужна ссылка fonki.pro или holychords.pro", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        linkLoading = true
                        scope.launch {
                            try {
                                val extracted = withContext(Dispatchers.IO) { FonkiExtractor.extract(trimmed) }
                                val track = extracted.tracks.firstOrNull()
                                if (track == null) {
                                    Toast.makeText(context, "На странице нет аудио", Toast.LENGTH_SHORT).show()
                                } else {
                                    val file = withContext(Dispatchers.IO) {
                                        FonkiExtractor.downloadAudio(
                                            context,
                                            track.url,
                                            hymn.title,
                                            "Песнь возрождения",
                                            track.label,
                                        )
                                    }
                                    viewModel.savePvHymnOverlay(
                                        overlayFrom(hymn).copy(
                                            audioPaths = hymn.audioPaths + file.absolutePath,
                                            audioLabels = hymn.audioLabels + track.label,
                                            audioSourceUrls = hymn.audioSourceUrls + track.url,
                                            lyrics = hymn.lyrics.ifBlank { extracted.lyrics },
                                        ),
                                    )
                                    Toast.makeText(context, "Дорожка добавлена", Toast.LENGTH_SHORT).show()
                                    linkUrl = ""
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "Ошибка", Toast.LENGTH_LONG).show()
                            } finally {
                                linkLoading = false
                            }
                        }
                    },
                    enabled = !linkLoading && linkUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (linkLoading) "Загрузка…" else "Скачать фонограмму по ссылке")
                }
                if (!hymn.builtIn) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            viewModel.deletePvHymnOverlay(hymn.id)
                            onBack()
                        },
                    ) { Text("Удалить добавленный гимн") }
                }
            }
        }
    }

    if (showAddToList && hymn != null) {
        AlertDialog(
            onDismissRequest = { showAddToList = false },
            title = { Text("В список") },
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text("Сначала создайте список в разделе «Списки песен».")
                    } else {
                        playlists.forEach { pl ->
                            TextButton(
                                onClick = {
                                    viewModel.addSongsToUserSongPlaylist(pl.id, listOf(hymn.id))
                                    Toast.makeText(context, "Добавлено в «${pl.name}»", Toast.LENGTH_SHORT).show()
                                    showAddToList = false
                                },
                            ) { Text(pl.name) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToList = false }) { Text("Закрыть") }
            },
        )
    }
}

private val PvSpeedPresets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@Composable
private fun PvPhonogramModesRow(playerState: PlayerState) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            "Режим и скорость",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = !playerState.looping,
                onClick = { AudioPlayerHolder.setLooping(false) },
                label = { Text("Один раз") },
                leadingIcon = { Icon(Icons.Default.RepeatOne, null, Modifier.size(16.dp)) },
            )
            FilterChip(
                selected = playerState.looping,
                onClick = { AudioPlayerHolder.setLooping(true) },
                label = { Text("По кругу") },
                leadingIcon = { Icon(Icons.Default.Repeat, null, Modifier.size(16.dp)) },
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PvSpeedPresets.forEach { speed ->
                val selected = kotlin.math.abs(playerState.speed - speed) < 0.04f
                FilterChip(
                    selected = selected,
                    onClick = { AudioPlayerHolder.setSpeed(speed) },
                    label = {
                        Text(
                            if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "${speed}×",
                        )
                    },
                )
            }
        }
    }
}

private fun overlayFrom(hymn: PvHymn): PvHymnOverlay = PvHymnOverlay(
    id = hymn.id,
    number = hymn.number,
    title = hymn.title,
    lyrics = hymn.lyrics,
    audioPaths = hymn.audioPaths,
    audioLabels = hymn.audioLabels,
    audioSourceUrls = hymn.audioSourceUrls,
    builtIn = hymn.builtIn,
)

private fun copyPvAudio(context: android.content.Context, uri: Uri): Pair<String, String>? {
    return try {
        val dir = File(context.filesDir, "songs_audio").apply { mkdirs() }
        val ext = context.contentResolver.getType(uri)
            ?.substringAfterLast('/')?.take(4) ?: "mp3"
        val dest = File(dir, "pv_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { inp ->
            dest.outputStream().use { out -> inp.copyTo(out) }
        } ?: return null
        val label = run {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(dest.absolutePath)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            } finally {
                mmr.release()
            }
        }?.takeIf { it.isNotBlank() } ?: dest.nameWithoutExtension
        dest.absolutePath to label
    } catch (_: Exception) {
        null
    }
}
