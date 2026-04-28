package com.example.bible.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.bible.data.ExportBundleOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun BackupOptionRow(
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
fun BackupScreen(
    onBack: () -> Unit,
    exportBundle: suspend (ExportBundleOptions) -> java.io.File,
    exportFullLegacy: suspend () -> java.io.File,
    importZip: suspend (java.io.File) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(true) }
    var userData by remember { mutableStateOf(true) }
    var songsMedia by remember { mutableStateOf(true) }
    var studyOffline by remember { mutableStateOf(true) }
    var timemark by remember { mutableStateOf(true) }
    var verseAttachments by remember { mutableStateOf(true) }
    var bibleAudio by remember { mutableStateOf(false) }
    var importInProgress by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importInProgress = true
            Toast.makeText(
                context,
                context.getString(R.string.backup_import_working),
                Toast.LENGTH_LONG,
            ).show()
            val ok = try {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext false
                    val tmp = java.io.File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")
                    try {
                        stream.use { input ->
                            tmp.outputStream().use { out -> input.copyTo(out) }
                        }
                        importZip(tmp)
                    } finally {
                        tmp.delete()
                    }
                }
            } catch (_: Exception) {
                false
            } finally {
                importInProgress = false
            }
            Toast.makeText(
                context,
                if (ok) context.getString(R.string.backup_import_ok) else context.getString(R.string.backup_import_fail),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun shareZip(file: java.io.File) {
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
        context.startActivity(android.content.Intent.createChooser(send, context.getString(R.string.backup_export)))
        Toast.makeText(context, R.string.backup_done, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
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
            if (importInProgress) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.backup_import_working),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                stringResource(R.string.backup_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.backup_section_selective),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))

            BackupOptionRow(stringResource(R.string.backup_opt_settings), settings) { settings = it }
            BackupOptionRow(stringResource(R.string.backup_opt_user_data), userData) { userData = it }
            BackupOptionRow(stringResource(R.string.backup_opt_songs_media), songsMedia) { songsMedia = it }
            BackupOptionRow(stringResource(R.string.backup_opt_study_offline), studyOffline) { studyOffline = it }
            BackupOptionRow(stringResource(R.string.backup_opt_timemark), timemark) { timemark = it }
            BackupOptionRow(stringResource(R.string.backup_opt_verse_attachments), verseAttachments) { verseAttachments = it }
            BackupOptionRow(stringResource(R.string.backup_opt_bible_audio), bibleAudio) { bibleAudio = it }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        val opts = ExportBundleOptions(
                            settings = settings,
                            userData = userData,
                            songsMedia = songsMedia,
                            studyOffline = studyOffline,
                            timemark = timemark,
                            verseAttachments = verseAttachments,
                            bibleAudio = bibleAudio,
                        )
                        if (!opts.anySelected()) {
                            Toast.makeText(
                                context,
                                R.string.backup_export_nothing_selected,
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@launch
                        }
                        val file = exportBundle(opts)
                        shareZip(file)
                    }
                },
                enabled = !importInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_export))
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        val file = exportFullLegacy()
                        shareZip(file)
                    }
                },
                enabled = !importInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_export_full_legacy))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.backup_import_pick_file),
                        Toast.LENGTH_SHORT,
                    ).show()
                    importLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                        ),
                    )
                },
                enabled = !importInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_import))
            }
        }
    }
}
