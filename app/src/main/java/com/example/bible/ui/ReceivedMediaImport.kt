package com.example.bible.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.MediaCatalogPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class ReceivedImportKind {
    PLAYLIST,
    VIDEO,
    AUDIO,
    UNKNOWN,
}

/** Очередь URI из «Поделиться» / «Открыть в приложении». */
object SharedMediaImportQueue {
    private val _uri = MutableStateFlow<Uri?>(null)
    val uri = _uri.asStateFlow()

    fun offer(uri: Uri) {
        _uri.value = uri
    }

    fun consume() {
        _uri.value = null
    }
}

fun extractShareIntentUri(intent: Intent?): Uri? {
    if (intent == null) return null
    return when (intent.action) {
        Intent.ACTION_SEND -> {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        Intent.ACTION_VIEW -> intent.data
        else -> null
    }
}

fun takeReadUriPermission(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    } catch (_: SecurityException) {
        // Временное разрешение из ACTION_SEND достаточно для одного чтения.
    }
}

fun detectReceivedImportKind(context: Context, uri: Uri): ReceivedImportKind {
    val name = uri.lastPathSegment.orEmpty().substringAfterLast('/').lowercase()
    val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
    when {
        name.endsWith(".zip") || name.endsWith(".json") ||
            mime.contains("zip") || mime.contains("json") ->
            return ReceivedImportKind.PLAYLIST
        mime.startsWith("video/") || MediaCatalogPaths.isLikelyVideoFileName(name) ->
            return ReceivedImportKind.VIDEO
        mime.startsWith("audio/") || MediaCatalogPaths.isLikelyAudioFileName(name) ->
            return ReceivedImportKind.AUDIO
    }
    return ReceivedImportKind.UNKNOWN
}

private fun playlistImportExtension(context: Context, uri: Uri): String {
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

private fun copyUriToCacheFile(context: Context, uri: Uri, prefix: String, ext: String): File? {
    val tmp = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}$ext")
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tmp).use { output -> input.copyTo(output) }
        } ?: run {
            tmp.delete()
            null
        }
        tmp
    } catch (_: Exception) {
        tmp.delete()
        null
    }
}

class ReceivedMediaImportHandlers internal constructor(
    val launchImportPlaylist: () -> Unit,
    val launchImportVideo: () -> Unit,
    val launchImportAudio: () -> Unit,
    val importSharedUri: (Uri) -> Unit,
)

@Composable
fun rememberReceivedMediaImportHandlers(viewModel: BibleViewModel): ReceivedMediaImportHandlers {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busyMessage by remember { mutableStateOf<String?>(null) }

    fun importPlaylistFromUri(uri: Uri) {
        scope.launch {
            takeReadUriPermission(context, uri)
            val ext = playlistImportExtension(context, uri)
            val tmp = copyUriToCacheFile(context, uri, "pl_import", ext)
            if (tmp == null) {
                Toast.makeText(context, "Не удалось открыть файл", Toast.LENGTH_SHORT).show()
                return@launch
            }
            busyMessage = "Импорт плейлиста…"
            viewModel.importUserMediaPlaylistFromFile(tmp) { msg ->
                busyMessage = null
                tmp.delete()
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun importVideoFromUri(uri: Uri) {
        scope.launch {
            takeReadUriPermission(context, uri)
            busyMessage = "Импорт видео…"
            viewModel.importReceivedVideoFromUri(uri) { err ->
                busyMessage = null
                Toast.makeText(
                    context,
                    err ?: "Видео добавлено в библиотеку",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun importAudioFromUri(uri: Uri) {
        scope.launch {
            takeReadUriPermission(context, uri)
            busyMessage = "Импорт аудио…"
            viewModel.importBibleAudiosFromUris(listOf(uri), source = "received") { ok, fail ->
                busyMessage = null
                val msg = when {
                    fail == 0 && ok > 0 -> "Аудио добавлено в библиотеку"
                    fail > 0 && ok == 0 -> "Не удалось импортировать аудио"
                    else -> "Добавлено: $ok, ошибок: $fail"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun importSharedUri(uri: Uri) {
        when (detectReceivedImportKind(context, uri)) {
            ReceivedImportKind.PLAYLIST -> importPlaylistFromUri(uri)
            ReceivedImportKind.VIDEO -> importVideoFromUri(uri)
            ReceivedImportKind.AUDIO -> importAudioFromUri(uri)
            ReceivedImportKind.UNKNOWN -> importPlaylistFromUri(uri)
        }
    }

    val importPlaylistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) importPlaylistFromUri(uri)
    }

    val importVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) importVideoFromUri(uri)
    }

    val importAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) importAudioFromUri(uri)
    }

    ReceivedMediaImportBusyOverlay(busyMessage)

    return remember(viewModel) {
        ReceivedMediaImportHandlers(
            launchImportPlaylist = {
                importPlaylistLauncher.launch(
                    arrayOf(
                        "application/zip",
                        "application/json",
                        "text/plain",
                        "application/octet-stream",
                    ),
                )
            },
            launchImportVideo = {
                importVideoLauncher.launch(
                    arrayOf(
                        "video/*",
                        "application/octet-stream",
                    ),
                )
            },
            launchImportAudio = {
                importAudioLauncher.launch(
                    arrayOf(
                        "audio/*",
                        "application/octet-stream",
                    ),
                )
            },
            importSharedUri = ::importSharedUri,
        )
    }
}

@Composable
private fun ReceivedMediaImportBusyOverlay(busyMessage: String?) {
    if (busyMessage == null) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(busyMessage) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {},
    )
}

@Composable
fun ReceivedMediaImportSheetRows(
    handlers: ReceivedMediaImportHandlers,
    includePlaylist: Boolean = true,
    includeVideo: Boolean = false,
    includeAudio: Boolean = false,
    onItemClick: () -> Unit = {},
) {
    if (includePlaylist) {
        Spacer(Modifier.height(8.dp))
        Card(
            Modifier
                .fillMaxWidth()
                .clickable {
                    onItemClick()
                    handlers.launchImportPlaylist()
                },
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlaylistPlay, contentDescription = null)
                Column(Modifier.padding(start = 16.dp)) {
                    Text("Полученный плейлист")
                    Text(
                        "ZIP или JSON из мессенджера",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (includeVideo) {
        Spacer(Modifier.height(8.dp))
        Card(
            Modifier
                .fillMaxWidth()
                .clickable {
                    onItemClick()
                    handlers.launchImportVideo()
                },
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.VideoFile, contentDescription = null)
                Column(Modifier.padding(start = 16.dp)) {
                    Text("Полученное видео")
                    Text(
                        "Файл, отправленный вам",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (includeAudio) {
        Spacer(Modifier.height(8.dp))
        Card(
            Modifier
                .fillMaxWidth()
                .clickable {
                    onItemClick()
                    handlers.launchImportAudio()
                },
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null)
                Column(Modifier.padding(start = 16.dp)) {
                    Text("Полученное аудио")
                    Text(
                        "Файл, отправленный вам",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun SharedMediaImportEffect(viewModel: BibleViewModel) {
    val handlers = rememberReceivedMediaImportHandlers(viewModel)
    val pendingUri by SharedMediaImportQueue.uri.collectAsStateWithLifecycle()

    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        SharedMediaImportQueue.consume()
        handlers.importSharedUri(uri)
    }
}
