package com.example.bible.ui

import android.text.Html
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bible.data.AudioPlayerHolder
import com.example.bible.data.PlaylistLook
import com.example.bible.data.PvHymn
import com.example.bible.data.SongItem
import com.example.bible.data.SongListSort
import com.example.bible.data.UserSongPlaylist
import com.example.bible.ui.theme.PesnopenieMaterialTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import java.text.Collator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSongPlaylistsListScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val playlists by viewModel.userSongPlaylists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<UserSongPlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<UserSongPlaylist?>(null) }
    var styleTarget by remember { mutableStateOf<UserSongPlaylist?>(null) }

    val permanent = playlists.filter { !it.temporary }
    val temporary = playlists.filter { it.temporary }

    PesnopenieMaterialTheme(useDark = false) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Списки песен") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Новый список")
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (playlists.isEmpty()) {
                    item {
                        Text(
                            "Создайте постоянный список или временный — например, на служение. Порядок песен можно задать вручную перетаскиванием.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (temporary.isNotEmpty()) {
                    item {
                        Text("Временные", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    items(temporary, key = { it.id }) { pl ->
                        SongListCard(
                            playlist = pl,
                            onClick = { onOpenPlaylist(pl.id) },
                            onRename = { renameTarget = pl },
                            onStyle = { styleTarget = pl },
                            onDelete = { deleteTarget = pl },
                        )
                    }
                }
                if (permanent.isNotEmpty()) {
                    item {
                        Text("Постоянные", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    items(permanent, key = { it.id }) { pl ->
                        SongListCard(
                            playlist = pl,
                            onClick = { onOpenPlaylist(pl.id) },
                            onRename = { renameTarget = pl },
                            onStyle = { styleTarget = pl },
                            onDelete = { deleteTarget = pl },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        SongListNameDialog(
            title = "Новый список",
            initialName = "",
            showTemporarySwitch = true,
            onDismiss = { showCreate = false },
            onConfirm = { name, temporary ->
                viewModel.createUserSongPlaylist(name, temporary)
                showCreate = false
            },
        )
    }
    renameTarget?.let { pl ->
        SongListNameDialog(
            title = "Переименовать",
            initialName = pl.name,
            showTemporarySwitch = false,
            onDismiss = { renameTarget = null },
            onConfirm = { name, _ ->
                viewModel.renameUserSongPlaylist(pl.id, name)
                renameTarget = null
            },
        )
    }
    deleteTarget?.let { pl ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить список?") },
            text = { Text("«${pl.name}» будет удалён. Песни в коллекции останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteUserSongPlaylist(pl.id)
                    deleteTarget = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            },
        )
    }
    styleTarget?.let { pl ->
        SongListStyleSheet(
            playlist = pl,
            onLook = { viewModel.updateUserSongPlaylistLook(pl.id, it) },
            onSubtitle = { viewModel.updateUserSongPlaylistSubtitle(pl.id, it) },
            onTemporary = { viewModel.setUserSongPlaylistTemporary(pl.id, it) },
            onDismiss = { styleTarget = null },
        )
    }
}

@Composable
private fun SongListCard(
    playlist: UserSongPlaylist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onStyle: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(playlist.look.brush()),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 4.dp, bottom = 14.dp, top = 10.dp),
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
                        if (playlist.temporary) append("Временный · ")
                        if (playlist.subtitle.isNotBlank()) {
                            append(playlist.subtitle)
                            append(" · ")
                        }
                        append("${playlist.songRefs.size} песен")
                    }
                    Text(
                        line,
                        color = Color.White.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.Palette, contentDescription = "Ещё", tint = Color.White)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Тема и подзаголовок") }, onClick = { menu = false; onStyle() })
                        DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun SongListNameDialog(
    title: String,
    initialName: String,
    showTemporarySwitch: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, temporary: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var temporary by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showTemporarySwitch) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Временный список", modifier = Modifier.weight(1f))
                        Switch(checked = temporary, onCheckedChange = { temporary = it })
                    }
                    Text(
                        "Временный удобен на одно служение: его легко найти отдельно и потом удалить.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = { onConfirm(name.trim(), temporary) },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongListStyleSheet(
    playlist: UserSongPlaylist,
    onLook: (String) -> Unit,
    onSubtitle: (String) -> Unit,
    onTemporary: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var subtitle by remember(playlist.id, playlist.subtitle) { mutableStateOf(playlist.subtitle) }
    ModalBottomSheet(onDismissRequest = {
        onSubtitle(subtitle)
        onDismiss()
    }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Оформление списка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                label = { Text("Подзаголовок") },
                placeholder = { Text("Например: воскресное служение") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Временный", modifier = Modifier.weight(1f))
                Switch(checked = playlist.temporary, onCheckedChange = onTemporary)
            }
            Spacer(Modifier.height(8.dp))
            Text("Тема", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PlaylistLook.entries, key = { it.id }) { look ->
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
                                .background(look.brush()),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(look.titleRu, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSongPlaylistDetailScreen(
    playlistId: String,
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onOpenSong: (SongItem) -> Unit,
    onOpenPvHymn: (String) -> Unit,
) {
    val context = LocalContext.current
    val playlists by viewModel.userSongPlaylists.collectAsState()
    val songs by viewModel.userSongs.collectAsState()
    val overlays by viewModel.pvHymnOverlays.collectAsState()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    val pvHymns = rememberPvCatalog(overlays).hymns
    var showPick by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var styleOpen by remember { mutableStateOf(false) }

    if (playlist == null) {
        PesnopenieMaterialTheme(useDark = false) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("Список") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                            }
                        },
                    )
                },
            ) { padding ->
                Text("Список не найден", modifier = Modifier.padding(padding).padding(16.dp))
            }
        }
        return
    }

    val resolved = remember(playlist.songRefs, playlist.sort, songs, pvHymns) {
        orderedSongRows(playlist, songs, pvHymns)
    }
    val draftRefs = remember(playlistId) {
        mutableStateListOf<String>().apply { addAll(playlist.songRefs) }
    }
    var awaitingPersist by remember(playlistId) { mutableStateOf(false) }
    LaunchedEffect(playlist.songRefs, playlist.sort, awaitingPersist) {
        if (!awaitingPersist && playlist.sort == SongListSort.MANUAL) {
            draftRefs.clear()
            draftRefs.addAll(playlist.songRefs)
        }
    }

    val haptic = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (from.index in draftRefs.indices && to.index in draftRefs.indices) {
            draftRefs.apply { add(to.index, removeAt(from.index)) }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    var wasDragging by remember { mutableStateOf(false) }
    LaunchedEffect(reorderableState) {
        snapshotFlow { reorderableState.isAnyItemDragging }.collect { dragging ->
            if (wasDragging && !dragging) {
                awaitingPersist = true
                viewModel.setUserSongPlaylistOrder(playlistId, draftRefs.toList())
                awaitingPersist = false
            }
            wasDragging = dragging
        }
    }

    val displayRefs = if (playlist.sort == SongListSort.MANUAL) draftRefs else resolved.map { it.ref }

    PesnopenieMaterialTheme(useDark = false) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            playlist.name,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Сортировка")
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SongListSort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = { Text(sort.titleRu) },
                                        onClick = {
                                            viewModel.updateUserSongPlaylistSort(playlistId, sort)
                                            sortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { styleOpen = true }) {
                            Icon(Icons.Default.Palette, contentDescription = "Оформление")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showPick = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить песни")
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                if (playlist.subtitle.isNotBlank()) {
                    Text(
                        playlist.subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Text(
                    if (playlist.sort == SongListSort.MANUAL) {
                        "Перетащите за ручку, чтобы задать свой порядок."
                    } else {
                        "Сейчас: ${playlist.sort.titleRu}. Для перетаскивания выберите «Свой порядок»."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (playlist.sort == SongListSort.MANUAL) {
                    items(displayRefs, key = { it }) { ref ->
                        val row = resolved.firstOrNull { it.ref == ref }
                        ReorderableItem(reorderableState, key = ref) { dragging ->
                            SongListRow(
                                title = row?.title ?: "Песня удалена",
                                subtitle = row?.subtitle.orEmpty(),
                                hasAudio = row?.hasAudio == true,
                                showHandle = true,
                                handleModifier = Modifier.draggableHandle(),
                                dragging = dragging,
                                onPlay = {
                                    val rowsInOrder = displayRefs.mapNotNull { r ->
                                        resolved.firstOrNull { it.ref == r }
                                    }
                                    val idx = rowsInOrder.indexOfFirst { it.ref == ref }
                                    if (idx >= 0) playSongListRow(rowsInOrder, idx)
                                },
                                onOpen = {
                                    when {
                                        row?.song != null -> onOpenSong(row.song)
                                        row?.pvId != null -> onOpenPvHymn(row.pvId)
                                    }
                                },
                                onRemove = { viewModel.removeSongFromUserSongPlaylist(playlistId, ref) },
                            )
                        }
                    }
                } else {
                    items(resolved, key = { it.ref }) { row ->
                        SongListRow(
                            title = row.title,
                            subtitle = row.subtitle,
                            hasAudio = row.hasAudio,
                            showHandle = false,
                            handleModifier = Modifier,
                            dragging = false,
                            onPlay = {
                                val idx = resolved.indexOf(row)
                                if (idx >= 0) playSongListRow(resolved, idx)
                            },
                            onOpen = {
                                when {
                                    row.song != null -> onOpenSong(row.song)
                                    row.pvId != null -> onOpenPvHymn(row.pvId)
                                }
                            },
                            onRemove = { viewModel.removeSongFromUserSongPlaylist(playlistId, row.ref) },
                        )
                    }
                }
            }
            }
        }
    }

    if (showPick) {
        PickSongsForListSheet(
            songs = songs,
            pvHymns = pvHymns,
            already = playlist.songRefs.toSet(),
            onDismiss = { showPick = false },
            onConfirm = { refs ->
                viewModel.addSongsToUserSongPlaylist(playlistId, refs)
                showPick = false
            },
        )
    }
    if (styleOpen) {
        SongListStyleSheet(
            playlist = playlist,
            onLook = { viewModel.updateUserSongPlaylistLook(playlistId, it) },
            onSubtitle = { viewModel.updateUserSongPlaylistSubtitle(playlistId, it) },
            onTemporary = { viewModel.setUserSongPlaylistTemporary(playlistId, it) },
            onDismiss = { styleOpen = false },
        )
    }
}

private fun playSongListRow(rows: List<SongListRowData>, startIndex: Int) {
    fun playAt(index: Int) {
        val row = rows.getOrNull(index) ?: run {
            AudioPlayerHolder.onSkipToNext = null
            return
        }
        val path = row.audioPath ?: run {
            AudioPlayerHolder.onSkipToNext = null
            return
        }
        AudioPlayerHolder.onSkipToNext = {
            var j = index + 1
            var played = false
            while (j < rows.size && !played) {
                val next = rows[j]
                if (next.hasAudio && next.audioPath != null) {
                    playAt(j)
                    played = true
                } else {
                    j++
                }
            }
            if (!played) {
                AudioPlayerHolder.onSkipToNext = null
            }
        }
        AudioPlayerHolder.play(path, row.title)
    }
    if (rows.isEmpty()) return
    playAt(startIndex.coerceIn(0, rows.lastIndex))
}

private data class SongListRowData(
    val ref: String,
    val title: String,
    val subtitle: String,
    val hasAudio: Boolean,
    val audioPath: String?,
    val song: SongItem?,
    val pvId: String?,
)

private fun orderedSongRows(
    playlist: UserSongPlaylist,
    songs: List<SongItem>,
    pvHymns: List<PvHymn>,
): List<SongListRowData> {
    val songById = songs.associateBy { it.id }
    val pvById = pvHymns.associateBy { it.id }
    val pvByNumber = pvHymns.associateBy { it.number }
    val rows = playlist.songRefs.map { ref ->
        val pvHymn = pvById[ref]
            ?: UserSongPlaylist.pvNumber(ref)?.let { pvByNumber[it] }
        if (pvHymn != null || UserSongPlaylist.isPvRef(ref) || ref.startsWith("pvuser:")) {
            SongListRowData(
                ref = ref,
                title = pvHymn?.let { "${it.number}. ${it.title}" } ?: "Гимн",
                subtitle = "Песнь возрождения",
                hasAudio = pvHymn?.audioPaths?.any { File(it).exists() } == true,
                audioPath = pvHymn?.audioPaths?.firstOrNull { File(it).exists() },
                song = null,
                pvId = pvHymn?.id,
            )
        } else {
            val s = songById[ref]
            SongListRowData(
                ref = ref,
                title = s?.title ?: "Песня удалена",
                subtitle = s?.artist.orEmpty(),
                hasAudio = s?.audioPaths?.any { File(it).exists() } == true,
                audioPath = s?.audioPaths?.firstOrNull { File(it).exists() },
                song = s,
                pvId = null,
            )
        }
    }
    val collator = Collator.getInstance(Locale("ru", "RU")).apply { strength = Collator.PRIMARY }
    return when (playlist.sort) {
        SongListSort.MANUAL -> rows
        SongListSort.TITLE_AZ -> rows.sortedWith { a, b -> collator.compare(a.title, b.title) }
        SongListSort.TITLE_ZA -> rows.sortedWith { a, b -> collator.compare(b.title, a.title) }
        SongListSort.NEWEST -> rows.reversed()
    }
}

@Composable
private fun SongListRow(
    title: String,
    subtitle: String,
    hasAudio: Boolean,
    showHandle: Boolean,
    handleModifier: Modifier,
    dragging: Boolean,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (dragging) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showHandle) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Переместить",
                modifier = handleModifier,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(
                decodeHtmlText(title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    decodeHtmlText(subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hasAudio) {
            IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Играть", modifier = Modifier.size(20.dp))
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Убрать", modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickSongsForListSheet(
    songs: List<SongItem>,
    pvHymns: List<PvHymn>,
    already: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var tabPv by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    val q = query.trim().lowercase()
    val filteredSongs = remember(songs, q) {
        if (q.isEmpty()) songs else songs.filter {
            q in it.title.lowercase() || q in it.artist.lowercase()
        }
    }
    val filteredPv = remember(pvHymns, q) {
        if (q.isEmpty()) pvHymns.take(80)
        else pvHymns.filter {
            q in it.title.lowercase() ||
                it.number.toString() == q ||
                it.number.toString().startsWith(q) ||
                (q.length >= 3 && q in it.lyrics.lowercase().take(400))
        }.take(80)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Добавить в список", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !tabPv, onClick = { tabPv = false }, label = { Text("Мои песни") })
                FilterChip(selected = tabPv, onClick = { tabPv = true }, label = { Text("Песнь возрождения") })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (tabPv) "Номер или название" else "Поиск") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.height(360.dp)) {
                if (!tabPv) {
                    items(filteredSongs, key = { it.id }) { song ->
                        val ref = song.id
                        val inList = ref in already
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !inList) {
                                    if (ref in selected) selected.remove(ref) else selected.add(ref)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = inList || ref in selected,
                                onCheckedChange = {
                                    if (inList) return@Checkbox
                                    if (ref in selected) selected.remove(ref) else selected.add(ref)
                                },
                                enabled = !inList,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (song.artist.isNotBlank()) {
                                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                } else {
                    items(filteredPv, key = { it.id }) { hymn ->
                        val ref = hymn.id
                        val inList = ref in already
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !inList) {
                                    if (ref in selected) selected.remove(ref) else selected.add(ref)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = inList || ref in selected,
                                onCheckedChange = {
                                    if (inList) return@Checkbox
                                    if (ref in selected) selected.remove(ref) else selected.add(ref)
                                },
                                enabled = !inList,
                            )
                            Text("${hymn.number}. ${hymn.title}", modifier = Modifier.weight(1f), maxLines = 2)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onConfirm(selected.toList()) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Добавить (${selected.size})")
            }
        }
    }
}

private fun decodeHtmlText(raw: String): String {
    if (raw.isEmpty() || '&' !in raw) return raw
    return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
}
