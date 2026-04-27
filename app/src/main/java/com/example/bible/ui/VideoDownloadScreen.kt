package com.example.bible.ui

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.data.FonkiExtractor
import com.example.bible.data.VideoExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "VideoDownload"

enum class MediaDownloadImportTarget {
    Video,
    Audio,
}

private enum class DownloadStatus {
    IDLE, INITIALIZING, DOWNLOADING, DONE, ERROR,
}

private data class QualityOption(
    val label: String,
    val height: Int,
)

private val VIDEO_QUALITIES = listOf(
    QualityOption("360p", 360),
    QualityOption("480p", 480),
    QualityOption("720p", 720),
    QualityOption("1080p", 1080),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDownloadScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
) {
    MediaDownloadScreen(viewModel, onBack, MediaDownloadImportTarget.Video)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDownloadScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
) {
    MediaDownloadScreen(viewModel, onBack, MediaDownloadImportTarget.Audio)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaDownloadScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    importTarget: MediaDownloadImportTarget,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var audioOnly by remember { mutableStateOf(importTarget == MediaDownloadImportTarget.Audio) }
    var selectedQuality by remember { mutableIntStateOf(720) }
    var status by remember { mutableStateOf(DownloadStatus.IDLE) }
    var statusText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(-1f) }
    var ytdlpReady by remember { mutableStateOf(false) }
    var fonkiLyrics by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                VideoExtractor.init(context)
            }
            ytdlpReady = true
            statusText = "Обновление yt-dlp..."
            try {
                withContext(Dispatchers.IO) {
                    VideoExtractor.ensureUpdated(context)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "yt-dlp update failed: ${e.message}")
            }
            statusText = ""
        } catch (e: Throwable) {
            Log.e(TAG, "Init failed", e)
            errorText = "Ошибка инициализации: ${e.message}"
        }
    }

    fun startDownload() {
        val trimmedUrl = url.trim()
        val typeLabel = if (audioOnly) "аудио" else "видео"
        if (trimmedUrl.isBlank()) {
            errorText = "Вставьте ссылку на $typeLabel"
            return
        }
        if (!trimmedUrl.startsWith("http")) {
            errorText = "Ссылка должна начинаться с http:// или https://"
            return
        }
        errorText = ""
        progress = -1f
        status = DownloadStatus.DOWNLOADING
        val platform = VideoExtractor.detectPlatform(trimmedUrl)
        val qualityLabel = if (!audioOnly) " (${selectedQuality}p)" else ""
        statusText = "Скачивание: $platform$qualityLabel..."

        val mainHandler = Handler(Looper.getMainLooper())
        fonkiLyrics = null

        if (FonkiExtractor.isFonkiUrl(trimmedUrl)) {
            scope.launch(Dispatchers.IO) {
                try {
                    mainHandler.post { statusText = "Загрузка страницы fonki.pro..." }
                    val song = FonkiExtractor.extract(trimmedUrl)
                    mainHandler.post {
                        fonkiLyrics = "${song.title}\n${song.artist}\n\n${song.lyrics}"
                        statusText = "Скачивание аудио: ${song.title}..."
                    }

                    val trackUrl = song.tracks.firstOrNull()?.url
                        ?: throw RuntimeException("Аудиофайл не найден")
                    val audioFile = FonkiExtractor.downloadAudio(
                        context = context,
                        url = trackUrl,
                        songTitle = song.title,
                        songArtist = song.artist,
                    ) { pct ->
                        mainHandler.post {
                            progress = pct.toFloat()
                            statusText = "Скачивание: $pct%"
                        }
                    }

                    FonkiExtractor.saveLyrics(song)

                    val importTitle = buildString {
                        if (song.artist.isNotBlank()) append("${song.artist} - ")
                        append(song.title)
                    }
                    mainHandler.post {
                        status = DownloadStatus.DONE
                        statusText = "Скачано: ${audioFile.name}"
                        url = ""
                        progress = 100f
                        Toast.makeText(context, "Песня и текст сохранены!", Toast.LENGTH_SHORT).show()
                        val doneMsg = when (importTarget) {
                            MediaDownloadImportTarget.Video -> "Аудио добавлено в «Медиа → Видео»"
                            MediaDownloadImportTarget.Audio -> "Аудио добавлено в «Медиа → Аудио»"
                        }
                        when (importTarget) {
                            MediaDownloadImportTarget.Video -> viewModel.importBibleVideoFromPublicDownloadFile(
                                sourceFile = audioFile,
                                title = importTitle,
                                sourceUrl = trimmedUrl,
                            ) { err ->
                                if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                else Toast.makeText(context, doneMsg, Toast.LENGTH_SHORT).show()
                            }
                            MediaDownloadImportTarget.Audio -> viewModel.importBibleAudioFromPublicDownloadFile(
                                sourceFile = audioFile,
                                title = importTitle,
                                sourceUrl = trimmedUrl,
                            ) { err ->
                                if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                else Toast.makeText(context, doneMsg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Fonki download failed", e)
                    mainHandler.post {
                        status = DownloadStatus.ERROR
                        errorText = e.message ?: "Ошибка загрузки с fonki.pro"
                        statusText = ""
                        progress = -1f
                    }
                }
            }
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting download: $trimmedUrl audio=$audioOnly q=$selectedQuality")
                val file = VideoExtractor.download(
                    url = trimmedUrl,
                    audioOnly = audioOnly,
                    videoQuality = selectedQuality,
                    onProgress = { p, eta ->
                        mainHandler.post {
                            progress = p
                            val etaText = if (eta > 0) " · ~${eta}с" else ""
                            statusText = "Скачивание: ${p.toInt()}%$etaText"
                        }
                    },
                )
                Log.d(TAG, "Download complete: ${file.absolutePath}")

                mainHandler.post {
                    status = DownloadStatus.DONE
                    statusText = "Скачано: ${file.name}"
                    url = ""
                    progress = 100f
                    val toastOk = when (importTarget) {
                        MediaDownloadImportTarget.Video -> "Файл добавлен в «Медиа → Видео»"
                        MediaDownloadImportTarget.Audio -> "Файл добавлен в «Медиа → Аудио»"
                    }
                    when (importTarget) {
                        MediaDownloadImportTarget.Video -> viewModel.importBibleVideoFromPublicDownloadFile(
                            sourceFile = file,
                            title = file.nameWithoutExtension,
                            sourceUrl = trimmedUrl,
                        ) { err ->
                            if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            else Toast.makeText(context, toastOk, Toast.LENGTH_SHORT).show()
                        }
                        MediaDownloadImportTarget.Audio -> viewModel.importBibleAudioFromPublicDownloadFile(
                            sourceFile = file,
                            title = file.nameWithoutExtension,
                            sourceUrl = trimmedUrl,
                        ) { err ->
                            if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            else Toast.makeText(context, toastOk, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Download failed", e)
                val msg = e.message ?: "Неизвестная ошибка"
                mainHandler.post {
                    status = DownloadStatus.ERROR
                    errorText = when {
                        "No address associated with hostname" in msg ||
                        "Unable to resolve host" in msg || "DNS" in msg.uppercase() ||
                        "Network is unreachable" in msg || "Connection refused" in msg ->
                            "Пожалуйста, подключитесь к сети Интернет"
                        "HTTP Error 403" in msg ->
                            "Доступ запрещён (403). Возможно, ссылка устарела."
                        "HTTP Error 404" in msg ->
                            "Видео не найдено (404). Проверьте ссылку."
                        "is not a valid URL" in msg || "Unsupported URL" in msg ->
                            "Неподдерживаемая ссылка."
                        "Unable to extract" in msg || "please report this issue" in msg ->
                            "Эта платформа временно не поддерживается.\nНажмите ↻ для обновления yt-dlp."
                        "yt-dlp -U" in msg ->
                            "Требуется обновление. Нажмите ↻ в правом верхнем углу."
                        "not a bot" in msg.lowercase() || "sign in to confirm" in msg.lowercase() ->
                            "YouTube запросил проверку (бот). Нажмите ↻ и обновите yt-dlp, затем повторите. Если снова ошибка — попробуйте позже или другую сеть (Wi‑Fi / мобильный интернет)."
                        else -> msg
                    }
                    statusText = ""
                    progress = -1f
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Скачать медиа", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            statusText = "Обновление yt-dlp..."
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    VideoExtractor.updateYtDlp(context)
                                }
                                when (result) {
                                    com.yausername.youtubedl_android.YoutubeDL.UpdateStatus.DONE ->
                                        Toast.makeText(context, "yt-dlp обновлён", Toast.LENGTH_SHORT).show()
                                    else ->
                                        Toast.makeText(context, "Уже актуальная версия", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Throwable) {
                                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                            statusText = ""
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить yt-dlp")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (!ytdlpReady && errorText.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Инициализация...", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Вставьте ссылку",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "YouTube, Rutube, VK Video, Vimeo, TikTok, Дзен и др.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !audioOnly,
                            onClick = { audioOnly = false },
                            label = { Text("Видео") },
                            leadingIcon = {
                                Icon(Icons.Default.Videocam, null, Modifier.size(18.dp))
                            },
                        )
                        FilterChip(
                            selected = audioOnly,
                            onClick = { audioOnly = true },
                            label = { Text("Аудио") },
                            leadingIcon = {
                                Icon(Icons.Default.MusicNote, null, Modifier.size(18.dp))
                            },
                        )
                    }

                    AnimatedVisibility(visible = !audioOnly) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.HighQuality, null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Качество видео",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                VIDEO_QUALITIES.forEach { q ->
                                    FilterChip(
                                        selected = selectedQuality == q.height,
                                        onClick = { selectedQuality = q.height },
                                        label = { Text(q.label, fontSize = 13.sp) },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            if (errorText.isNotEmpty()) errorText = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                val text = clipboard.getText()?.text ?: ""
                                if (text.isNotBlank()) url = text
                            }) {
                                Icon(Icons.Default.ContentPaste, "Вставить")
                            }
                        },
                        isError = errorText.isNotEmpty(),
                        supportingText = if (errorText.isNotEmpty()) {
                            { Text(errorText, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        shape = RoundedCornerShape(12.dp),
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { startDownload() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = (ytdlpReady || FonkiExtractor.isFonkiUrl(url.trim())) &&
                            status != DownloadStatus.DOWNLOADING,
                    ) {
                        if (status == DownloadStatus.DOWNLOADING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Скачать")
                    }

                    AnimatedVisibility(visible = status == DownloadStatus.DOWNLOADING) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            if (progress >= 0f) {
                                LinearProgressIndicator(
                                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    AnimatedVisibility(visible = status == DownloadStatus.DONE) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x224CAF50)),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                statusText,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = fonkiLyrics != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
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
                                "Текст песни",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { fonkiLyrics = null }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Закрыть",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            fonkiLyrics ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                when (importTarget) {
                    MediaDownloadImportTarget.Video ->
                        "Список всех роликов и аудио — в разделе «Медиа → Видео» (иконки показывают источник: «Скачано», «Галерея», «Камера»)."
                    MediaDownloadImportTarget.Audio ->
                        "Список треков — в разделе «Медиа → Аудио» (только метки и файлы из этой базы)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
