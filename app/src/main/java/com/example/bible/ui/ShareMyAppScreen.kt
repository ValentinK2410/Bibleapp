package com.example.bible.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.bible.R
import com.example.bible.data.AppDataExport
import com.example.bible.data.BibleAudioNarrators
import com.example.bible.data.ExportShareProgressEvent
import com.example.bible.data.ShareExportOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface ShareExportOverlay {
    data class Zipping(val ordinal: Int, val path: String) : ShareExportOverlay
    data class Copying(val done: Long, val total: Long) : ShareExportOverlay
}

/** Дерево папок с явными флагами записи — иначе на части устройств флешка не принимает файл. */
private class OpenDocumentTreeWithWrite : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: android.content.Context, input: android.net.Uri?): Intent {
        return super.createIntent(context, input).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }
}

@Composable
private fun ShareOptionRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = onChecked)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareMyAppScreen(
    onBack: () -> Unit,
    exportShare: suspend (ShareExportOptions, ((ExportShareProgressEvent) -> Unit)?) -> File,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportOverlay by remember { mutableStateOf<ShareExportOverlay?>(null) }

    var appSettings by remember { mutableStateOf(true) }
    var readerData by remember { mutableStateOf(true) }
    var personalNotes by remember { mutableStateOf(true) }
    var verseAttachments by remember { mutableStateOf(true) }
    var semanticLexicon by remember { mutableStateOf(true) }
    var wordSpanLinks by remember { mutableStateOf(true) }
    var bibleImages by remember { mutableStateOf(true) }
    var bibleVideos by remember { mutableStateOf(true) }
    var bibleAudios by remember { mutableStateOf(true) }
    var songTextsCues by remember { mutableStateOf(true) }
    var songMedia by remember { mutableStateOf(true) }
    var timemarkBible by remember { mutableStateOf(true) }
    var studyOffline by remember { mutableStateOf(true) }
    var bibleAudio by remember { mutableStateOf(false) }
    var bibleAudioNarratorSelection by remember { mutableStateOf(setOf<String>()) }
    var quranHistory by remember { mutableStateOf(true) }
    var includeInstalledApk by remember { mutableStateOf(true) }

    var narratorsOnDevice by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        narratorsOnDevice = withContext(Dispatchers.IO) {
            AppDataExport.listLocalBibleAudioNarratorIds(context)
        }
    }
    val narratorsOnDeviceSet = remember(narratorsOnDevice) { narratorsOnDevice.toSet() }

    fun narratorDisplayLabel(narratorId: String): String =
        BibleAudioNarrators.forPicker.find { it.id == narratorId }?.name ?: narratorId

    fun buildOptions(): ShareExportOptions {
        val audioFilter = when {
            !bibleAudio || bibleAudioNarratorSelection.isEmpty() -> null
            bibleAudioNarratorSelection == narratorsOnDeviceSet -> null
            else -> bibleAudioNarratorSelection
        }
        return ShareExportOptions(
            includeInstalledApk = includeInstalledApk,
            appSettings = appSettings,
            readerBookmarksHighlightsHistory = readerData,
            personalNotes = personalNotes,
            verseAttachmentsAndComments = verseAttachments,
            semanticLexicon = semanticLexicon,
            wordSpanLinks = wordSpanLinks,
            bibleCatalogImages = bibleImages,
            bibleCatalogVideos = bibleVideos,
            bibleCatalogAudios = bibleAudios,
            songTextsTagsAndLyricCues = songTextsCues,
            songMediaFiles = songMedia,
            timemarkBibleProjects = timemarkBible,
            studyOfflineMaterials = studyOffline,
            bibleDownloadedAudio = bibleAudio && bibleAudioNarratorSelection.isNotEmpty(),
            bibleAudioNarratorIds = audioFilter,
            quranSearchHistory = quranHistory,
        )
    }

    fun shareZip(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file,
        )
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(
            send,
            context.getString(R.string.share_app_send_chooser),
        ).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
        Toast.makeText(context, R.string.share_app_export_done, Toast.LENGTH_SHORT).show()
    }

    val treeLauncher = rememberLauncherForActivityResult(
        contract = OpenDocumentTreeWithWrite(),
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // часть прошивок не отдаёт persistable — копирование всё равно может сработать сразу после выбора
        }
        scope.launch {
            val opts = buildOptions()
            if (!opts.anySelected()) {
                Toast.makeText(
                    context,
                    R.string.backup_export_nothing_selected,
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            try {
                exportOverlay = ShareExportOverlay.Zipping(0, "…")
                val zip = exportShare(opts) { ev ->
                    when (ev) {
                        is ExportShareProgressEvent.ZipFileAdded ->
                            exportOverlay = ShareExportOverlay.Zipping(ev.ordinal, ev.pathInArchive)
                        is ExportShareProgressEvent.CopyToStorage ->
                            exportOverlay = ShareExportOverlay.Copying(ev.bytesDone, ev.bytesTotal)
                    }
                }
                val total = zip.length().coerceAtLeast(1L)
                exportOverlay = ShareExportOverlay.Copying(0L, total)
                val ok = withContext(Dispatchers.IO) {
                    AppDataExport.copyZipToUserFolder(
                        context,
                        treeUri,
                        zip,
                        "BibleApp_share_${System.currentTimeMillis()}",
                        onCopyProgress = { done, tot ->
                            exportOverlay = ShareExportOverlay.Copying(done, tot)
                        },
                    )
                }
                Toast.makeText(
                    context,
                    if (ok) context.getString(R.string.share_app_saved_to_folder_ok)
                    else context.getString(R.string.share_app_saved_to_folder_fail),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                exportOverlay = null
            }
        }
    }

    exportOverlay?.let { overlay ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.share_app_progress_title)) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    when (overlay) {
                        is ShareExportOverlay.Zipping -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.share_app_progress_zipping),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.share_app_progress_files_count, overlay.ordinal),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        stringResource(R.string.share_app_progress_current_file, overlay.path),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 4,
                                    )
                                }
                            }
                        }
                        is ShareExportOverlay.Copying -> {
                            val p = (overlay.done.toFloat() / overlay.total.toFloat()).coerceIn(0f, 1f)
                            Text(
                                stringResource(R.string.share_app_progress_copy),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(
                                    R.string.share_app_progress_mb,
                                    overlay.done / 1_000_000.0,
                                    overlay.total / 1_000_000.0,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = { },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.share_app_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.share_app_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            ShareOptionRow(
                stringResource(R.string.share_app_opt_include_apk),
                includeInstalledApk,
            ) { includeInstalledApk = it }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                stringResource(R.string.share_app_section_data),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            ShareOptionRow(stringResource(R.string.share_app_opt_settings), appSettings) { appSettings = it }
            ShareOptionRow(stringResource(R.string.share_app_opt_reader_text), readerData) { readerData = it }
            ShareOptionRow(stringResource(R.string.share_app_opt_notes), personalNotes) { personalNotes = it }
            ShareOptionRow(stringResource(R.string.share_app_opt_verse_attachments), verseAttachments) {
                verseAttachments = it
            }
            ShareOptionRow(stringResource(R.string.share_app_opt_lexicon), semanticLexicon) { semanticLexicon = it }
            ShareOptionRow(stringResource(R.string.share_app_opt_word_span), wordSpanLinks) { wordSpanLinks = it }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                stringResource(R.string.share_app_section_media),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            ShareOptionRow(stringResource(R.string.share_app_opt_bible_images), bibleImages) { bibleImages = it }
            ShareOptionRow(stringResource(R.string.share_app_opt_bible_videos), bibleVideos) { bibleVideos = it }
            ShareOptionRow(stringResource(R.string.share_app_opt_bible_audios), bibleAudios) { bibleAudios = it }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                stringResource(R.string.share_app_section_songs),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            ShareOptionRow(stringResource(R.string.share_app_opt_song_texts_cues), songTextsCues) { songTextsCues = it }
            ShareOptionRow(stringResource(R.string.share_app_opt_song_files), songMedia) { songMedia = it }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                stringResource(R.string.share_app_section_study),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            ShareOptionRow(stringResource(R.string.share_app_opt_study_offline), studyOffline) { studyOffline = it }
            ShareOptionRow(stringResource(R.string.share_app_opt_bible_audio_dl), bibleAudio) { want ->
                if (want && narratorsOnDevice.isEmpty()) {
                    Toast.makeText(
                        context,
                        R.string.share_app_bible_audio_none_on_device,
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    bibleAudio = want
                    bibleAudioNarratorSelection = if (want) narratorsOnDeviceSet else emptySet()
                }
            }
            if (bibleAudio && narratorsOnDevice.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.share_app_bible_audio_pick_narrators),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                Column(Modifier.padding(start = 36.dp)) {
                    narratorsOnDevice.forEach { nid ->
                        ShareOptionRow(
                            narratorDisplayLabel(nid),
                            bibleAudioNarratorSelection.contains(nid),
                        ) { checked ->
                            bibleAudioNarratorSelection = if (checked) {
                                bibleAudioNarratorSelection + nid
                            } else {
                                bibleAudioNarratorSelection - nid
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                stringResource(R.string.share_app_section_sync),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            ShareOptionRow(stringResource(R.string.share_app_opt_timemark_bible), timemarkBible) { timemarkBible = it }
            ShareOptionRow(stringResource(R.string.share_app_opt_quran_history), quranHistory) { quranHistory = it }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.share_app_footer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        val opts = buildOptions()
                        if (!opts.anySelected()) {
                            Toast.makeText(
                                context,
                                R.string.backup_export_nothing_selected,
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@launch
                        }
                        try {
                            exportOverlay = ShareExportOverlay.Zipping(0, "…")
                            val file = exportShare(opts) { ev ->
                                when (ev) {
                                    is ExportShareProgressEvent.ZipFileAdded ->
                                        exportOverlay = ShareExportOverlay.Zipping(ev.ordinal, ev.pathInArchive)
                                    is ExportShareProgressEvent.CopyToStorage -> { }
                                }
                            }
                            shareZip(file)
                        } finally {
                            exportOverlay = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.share_app_action_send))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val opts = buildOptions()
                    if (!opts.anySelected()) {
                        Toast.makeText(
                            context,
                            R.string.backup_export_nothing_selected,
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@OutlinedButton
                    }
                    treeLauncher.launch(null)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.share_app_action_save_usb))
            }
        }
    }
}
