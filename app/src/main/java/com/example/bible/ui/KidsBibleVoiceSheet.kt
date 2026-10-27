package com.example.bible.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.bible.data.KidsBibleAge
import com.example.bible.data.KidsBibleVoice
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Запись, файл или ссылка для одной истории и одного возраста.
 * Вызывается только после кода родителя.
 */
@Composable
internal fun KidsBibleVoiceSheet(
    storyId: String,
    age: KidsBibleAge,
    partId: String?,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { KidsBibleVoice.root(context.filesDir) }
    val saved = remember(storyId, age, partId) {
        if (partId == null) KidsBibleVoice.wholeFile(root, storyId, age.id)
        else KidsBibleVoice.partFile(root, storyId, age.id, partId)
    }
    var savedExists by remember(storyId, age, partId) {
        mutableStateOf(saved != null && saved.isFile && saved.length() > 0L)
    }
    var draft by remember { mutableStateOf<File?>(null) }
    var draftFrom by remember { mutableStateOf("mic") }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recording by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<MediaPlayer?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    fun stopPreview() {
        preview?.runCatching {
            stop()
            release()
        }
        preview = null
    }

    fun stopRecorder() {
        val current = recorder
        recorder = null
        recording = false
        current?.runCatching { stop() }
        current?.runCatching { release() }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopRecorder()
            stopPreview()
        }
    }

    fun draftFile(): File {
        val piece = partId ?: "whole"
        return File(context.cacheDir, "kids_voice_draft_${storyId}_${age.id}_$piece.m4a")
    }

    fun startRecording() {
        stopPreview()
        stopRecorder()
        val file = draftFile()
        if (file.exists()) file.delete()
        file.parentFile?.mkdirs()
        try {
            val next = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setOutputFile(file.absolutePath)
            next.prepare()
            next.start()
            recorder = next
            recording = true
            draft = null
            status = "Идёт запись."
        } catch (_: Exception) {
            stopRecorder()
            status = "Запись не началась."
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else status = "Без микрофона записать нельзя."
    }

    fun finishRecording() {
        val file = draftFile()
        stopRecorder()
        if (file.isFile && file.length() > 0L) {
            draft = file
            draftFrom = "mic"
            status = "Запись готова. Можно послушать и сохранить."
        } else {
            draft = null
            status = "Запись пустая."
        }
    }

    fun listen(file: File?) {
        if (file == null || !file.isFile) {
            status = "Сначала запишите или выберите звук."
            return
        }
        stopPreview()
        val player = MediaPlayer()
        try {
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                it.release()
                if (preview === it) preview = null
            }
            player.prepare()
            player.start()
            preview = player
        } catch (_: Exception) {
            player.release()
            status = "Этот звук не открылся."
        }
    }

    fun commit(source: String) {
        val from = draft
        val dest = saved
        if (from == null || dest == null) {
            status = "Нечего сохранять."
            return
        }
        runCatching { KidsBibleVoice.place(from, dest) }
            .onSuccess {
                KidsBibleVoice.putNote(root, storyId, age.id, partId ?: "whole", source)
                savedExists = true
                draft = null
                from.delete()
                status = "Сохранено. История звучит этим голосом."
                onChanged()
            }
            .onFailure { status = "Сохранить не удалось." }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = "Проверяю файл."
            val file = draftFile()
            val ok = withContext(Dispatchers.IO) {
                copyStream(context, uri, file) && audioFile(file, null)
            }
            busy = false
            if (ok) {
                draft = file
                draftFrom = "file"
                status = "Файл подходит. Можно послушать и сохранить."
            } else {
                file.delete()
                draft = null
                status = "В файле нет звука."
            }
        }
    }

    fun download() {
        val raw = url.trim()
        val parsed = runCatching { URL(raw) }.getOrNull()
        if (parsed == null || (parsed.protocol != "http" && parsed.protocol != "https")) {
            status = "Нужна ссылка http или https."
            return
        }
        scope.launch {
            busy = true
            status = "Скачиваю."
            val file = draftFile()
            val result = withContext(Dispatchers.IO) { downloadAudio(raw, file) }
            busy = false
            if (result == null) {
                draft = file
                draftFrom = "url"
                status = "Ссылка скачана. Можно послушать и сохранить."
            } else {
                file.delete()
                draft = null
                status = result
            }
        }
    }

    val title = if (partId == null) "Своя озвучка всей истории" else "Своя озвучка абзаца"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 24.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Возраст ${age.label}. Ребёнок этот голос сам не заменит.",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                if (savedExists) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Сейчас играет своя озвучка.",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
                if (busy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(status, fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ссылка на звук", fontFamily = FontFamily.SansSerif) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Column {
                TextButton(onClick = {
                    if (recording) finishRecording() else {
                        when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
                            PackageManager.PERMISSION_GRANTED -> startRecording()
                            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }) {
                    Text(if (recording) "Стоп" else "Записать", fontFamily = FontFamily.SansSerif)
                }
                TextButton(onClick = { listen(draft ?: saved?.takeIf { it.isFile }) }) {
                    Text("Слушать", fontFamily = FontFamily.SansSerif)
                }
                TextButton(enabled = draft != null && !busy && !recording, onClick = { commit(draftFrom) }) {
                    Text("Сохранить", fontFamily = FontFamily.SansSerif)
                }
            }
        },
        dismissButton = {
            Column {
                TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("audio/*")) }) {
                    Text("Файл с телефона", fontFamily = FontFamily.SansSerif)
                }
                TextButton(enabled = !busy && !recording, onClick = { download() }) {
                    Text("Скачать ссылку", fontFamily = FontFamily.SansSerif)
                }
                if (savedExists) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Удалить свою озвучку", fontFamily = FontFamily.SansSerif)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Закрыть", fontFamily = FontFamily.SansSerif)
                }
            }
        },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить свою озвучку?", fontFamily = FontFamily.SansSerif) },
            text = {
                Text(
                    "Файл сотрётся. Вернётся обычный голос, если он есть в истории.",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (partId == null) KidsBibleVoice.deleteAge(root, storyId, age.id)
                    else KidsBibleVoice.deletePart(root, storyId, age.id, partId)
                    savedExists = false
                    confirmDelete = false
                    status = "Своя озвучка удалена."
                    onChanged()
                }) { Text("Удалить", fontFamily = FontFamily.SansSerif) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Оставить", fontFamily = FontFamily.SansSerif)
                }
            },
        )
    }
}

private fun copyStream(context: android.content.Context, uri: Uri, dest: File): Boolean {
    return runCatching {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return false
            dest.parentFile?.mkdirs()
            dest.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_AUDIO_BYTES) return false
                    output.write(buffer, 0, read)
                }
            }
        }
        dest.isFile && dest.length() > 0L
    }.getOrDefault(false)
}

private fun downloadAudio(raw: String, dest: File): String? {
    val connection = (URL(raw).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
    }
    return try {
        connection.connect()
        val protocol = connection.url.protocol
        if (protocol != "http" && protocol != "https") return "Ссылка ушла не на http."
        if (connection.responseCode !in 200..299) return "Ссылка не открылась."
        val type = connection.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (type.startsWith("text/") || "html" in type) return "По ссылке страница, не звук."
        connection.inputStream.use { input ->
            dest.parentFile?.mkdirs()
            dest.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_AUDIO_BYTES) return "Файл больше 32 МБ."
                    output.write(buffer, 0, read)
                }
            }
        }
        if (!dest.isFile || dest.length() == 0L) return "Файл пустой."
        if (type.startsWith("audio/")) return null
        if (audioFile(dest, type)) null else "По этой ссылке нет звука."
    } catch (_: Exception) {
        "Скачать не удалось."
    } finally {
        connection.disconnect()
    }
}

private fun audioFile(file: File, contentType: String?): Boolean {
    val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    if (type.startsWith("text/") || "html" in type) return false
    if (type.startsWith("audio/")) return true
    val player = MediaPlayer()
    return try {
        player.setDataSource(file.absolutePath)
        player.prepare()
        player.duration > 0
    } catch (_: Exception) {
        false
    } finally {
        player.release()
    }
}

private const val MAX_AUDIO_BYTES = 32 * 1024 * 1024
