package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.bible.R
import java.io.File
import java.util.UUID

@Composable
fun LexiconAudioRecorderDialog(
    onDismiss: () -> Unit,
    onUriRecorded: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun releaseRecorder() {
        try {
            recorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {
                }
                release()
            }
        } catch (_: Exception) {
        }
        recorder = null
        isRecording = false
    }

    DisposableEffect(Unit) {
        onDispose { releaseRecorder() }
    }

    fun startRecording() {
        releaseRecorder()
        errorText = null
        val file = File(context.cacheDir, "lexicon_audio_${UUID.randomUUID()}.m4a")
        outputFile = file
        try {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            isRecording = true
        } catch (e: Exception) {
            errorText = e.message ?: "Ошибка записи"
            outputFile = null
            releaseRecorder()
        }
    }

    fun stopAndSave() {
        val file = outputFile
        releaseRecorder()
        if (file != null && file.exists() && file.length() > 0L) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file,
            )
            onUriRecorded(uri)
        } else {
            errorText = context.getString(R.string.audio_record_empty)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording()
    }

    AlertDialog(
        onDismissRequest = {
            releaseRecorder()
            onDismiss()
        },
        title = { Text(stringResource(R.string.audio_record_dialog_title)) },
        text = {
            Column {
                if (errorText != null) {
                    Text(errorText!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    if (isRecording) {
                        stringResource(R.string.audio_record_in_progress)
                    } else {
                        stringResource(R.string.audio_record_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            if (!isRecording) {
                TextButton(
                    onClick = {
                        when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
                            PackageManager.PERMISSION_GRANTED -> startRecording()
                            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                ) {
                    Text(stringResource(R.string.audio_record_start))
                }
            } else {
                TextButton(onClick = { stopAndSave() }) {
                    Text(stringResource(R.string.audio_record_stop_save))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    releaseRecorder()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.back))
            }
        },
    )
}
