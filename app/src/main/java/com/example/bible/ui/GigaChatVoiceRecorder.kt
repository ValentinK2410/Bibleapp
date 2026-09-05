package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.bible.R
import java.io.File

/**
 * Запись в приложение, без системного SpeechRecognizer —
 * иначе на части телефонов открывается встроенный ассистент.
 */
@Composable
fun rememberGigaChatVoiceRecorder(
    onRecorded: (File) -> Unit,
): AiSpeechToText {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var output by remember { mutableStateOf<File?>(null) }

    fun releaseRecorder() {
        val rec = recorder
        recorder = null
        if (rec != null) {
            runCatching { rec.stop() }
            runCatching { rec.reset() }
            runCatching { rec.release() }
        }
        listening = false
    }

    DisposableEffect(Unit) {
        onDispose { releaseRecorder() }
    }

    fun finishAndSend() {
        val file = output
        releaseRecorder()
        if (file == null || !file.exists() || file.length() < 800) {
            Toast.makeText(context, R.string.gigachat_voice_too_short, Toast.LENGTH_SHORT).show()
            file?.delete()
            return
        }
        onRecorded(file)
    }

    fun beginRecording() {
        releaseRecorder()
        val file = File(context.cacheDir, "gigachat_voice_${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioSamplingRate(16_000)
            rec.setAudioEncodingBitRate(64_000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            output = file
            listening = true
        } catch (_: Exception) {
            runCatching { rec.release() }
            file.delete()
            Toast.makeText(context, R.string.gigachat_voice_record_error, Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) beginRecording()
        else Toast.makeText(context, R.string.ai_ask_voice_need_mic, Toast.LENGTH_SHORT).show()
    }

    fun requestOrStart() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) beginRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    return AiSpeechToText(
        available = true,
        listening = listening,
        start = { requestOrStart() },
        stop = { finishAndSend() },
    )
}
