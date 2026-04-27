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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.BibleUserAudio
import com.example.bible.data.MediaCatalogPaths
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

private fun formatSizeMb(bytes: Long): String =
    "%.1f МБ".format(bytes / (1024.0 * 1024.0))

private fun sourceLabelRu(source: String) = when (source) {
    "download" -> "Скачано"
    "camera", "recorder" -> "Запись"
    "commons" -> "Интернет (Commons)"
    else -> "Файлы"
}

@Composable
private fun AudioSourceIcon(source: String) {
    val (icon, accent) = when (source) {
        "download" -> Icons.Filled.Download to Color(0xFF1565C0)
        "camera", "recorder" -> Icons.Filled.Mic to Color(0xFF6A1B9A)
        "commons" -> Icons.Filled.TravelExplore to Color(0xFF00695C)
        else -> Icons.Filled.PhotoLibrary to Color(0xFF2E7D32)
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(accent.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, sourceLabelRu(source), tint = accent, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLibraryScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onOpenAudioDownload: () -> Unit = {},
) {
    val context = LocalContext.current
    val audios by viewModel.bibleUserAudios.collectAsStateWithLifecycle()
    val titleScale by viewModel.videoLibraryTitleScale.collectAsStateWithLifecycle()
    val audioItems = remember(audios) {
        audios.filter { MediaCatalogPaths.isLikelyAudioFileName(it.fileName) }
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var showMetaDialog by remember { mutableStateOf(false) }
    var metaEditing by remember { mutableStateOf<BibleUserAudio?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSource by remember { mutableStateOf("gallery") }

    var draftTitle by remember { mutableStateOf("") }
    var draftTags by remember { mutableStateOf("") }

    var librarySearchQuery by remember { mutableStateOf("") }
    /** Если системное приложение не зарегистрировало [MediaStore.Audio.Media.RECORD_SOUND_ACTION], пишем через MediaRecorder. */
    var showInAppRecorder by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        viewModel.syncLegacyAudioDownloadsFromPublicFolder()
    }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
            pendingSource = "gallery"
            draftTitle = ""
            draftTags = ""
            showMetaDialog = true
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
        val filteredSorted = remember(audioItems, librarySearchQuery) {
            audioItems
                .filter { it.matchesMediaSearch(librarySearchQuery) }
                .sortedByDescending { it.addedAt }
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = librarySearchQuery,
                onValueChange = { librarySearchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Поиск по названию или меткам") },
                leadingIcon = {
                    Icon(Icons.Filled.MusicNote, contentDescription = null)
                },
                singleLine = true,
            )
            Text(
                "Иконка — источник; A− / A+ — размер названий (как в разделе «Видео»).",
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
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredSorted, key = { it.id }) { item ->
                        val f = MediaCatalogPaths.audioFile(context, item.fileName)
                        val titleSp = (VideoLibraryFontDefaults.BASE_TITLE_SP * titleScale).sp
                        val lineSp = (VideoLibraryFontDefaults.BASE_TITLE_SP * titleScale * 1.35f).sp
                        val metaSp = (VideoLibraryFontDefaults.BASE_META_SP * titleScale).sp
                        val srcSp = (10f * titleScale).sp
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = item.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pendingUri = null
                                            metaEditing = item
                                            draftTitle = item.title
                                            draftTags = item.tags.joinToString(", ")
                                            showMetaDialog = true
                                        },
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = titleSp,
                                        lineHeight = lineSp,
                                    ),
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                pendingUri = null
                                                metaEditing = item
                                                draftTitle = item.title
                                                draftTags = item.tags.joinToString(", ")
                                                showMetaDialog = true
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AudioSourceIcon(item.source)
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier.size(64.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    Icons.Filled.MusicNote,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(36.dp),
                                                    tint = Color(0xFFFF9800),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                buildString {
                                                    if (f.exists()) {
                                                        append(formatSizeMb(f.length()))
                                                        append(" · ")
                                                    }
                                                    append(dateFmt.format(Date(item.addedAt)))
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = metaSp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                sourceLabelRu(item.source),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = srcSp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                ),
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            if (!f.exists()) {
                                                Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                                                return@IconButton
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
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = "Воспроизвести",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (!f.exists()) {
                                                Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                                                return@IconButton
                                            }
                                            shareMediaFile(context, f, "audio/*")
                                        },
                                    ) {
                                        Icon(
                                            Icons.Filled.Share,
                                            contentDescription = "Поделиться",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteBibleAudio(item)
                                            Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                                        },
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Удалить",
                                            tint = MaterialTheme.colorScheme.error,
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
                            pickLauncher.launch("audio/*")
                        },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null)
                        Text("Из файлов / галереи", modifier = Modifier.padding(start = 16.dp))
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
