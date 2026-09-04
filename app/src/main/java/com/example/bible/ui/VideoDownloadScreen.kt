package com.example.bible.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.bible.R
import com.example.bible.data.FonkiExtractor
import com.example.bible.data.LegalAudioTrack
import com.example.bible.data.LocalDeviceAudioScan
import com.example.bible.data.MediaCatalogPaths
import com.example.bible.data.MediaDownloadDedup
import com.example.bible.data.MediaDownloadQueue
import com.example.bible.data.MediaDownloadTask
import com.example.bible.data.PlaylistInspection
import com.example.bible.data.VideoExtractor
import com.example.bible.service.MediaDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "VideoDownload"

private fun ytDlpErrorUserMessage(msg: String): String = VideoExtractor.userMessage(msg)

enum class MediaDownloadImportTarget {
    Video,
    Audio,
}

private class OpenYandexMusicFilesTree : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        return super.createIntent(context, input).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                input ?: LocalDeviceAudioScan.yandexInitialTreeUri(),
            )
        }
    }
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

private fun formatDurationSeconds(sec: Long?): String {
    if (sec == null || sec <= 0L) return ""
    val h = sec / 3600L
    val m = (sec % 3600L) / 60L
    val s = sec % 60L
    val ss = s.toString().padStart(2, '0')
    return if (h > 0L) "${h}:${m.toString().padStart(2, '0')}:$ss" else "${m}:$ss"
}

private const val SKIP_NAMED_MEDIA_HINT =
    "Файл с таким названием уже есть на телефоне. Снимите галочку «Не скачивать повторно», чтобы загрузить снова."

private fun biblePublicDownloadsDir(): File =
    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Bible")

private fun existingMediaStemKeys(
    context: Context,
    videos: List<com.example.bible.data.BibleUserVideo>,
    audios: List<com.example.bible.data.BibleUserAudio>,
): Set<String> =
    MediaDownloadDedup.collectStems(
        titles = videos.map { it.title } + audios.map { it.title },
        fileNames = videos.map { it.fileName } + audios.map { it.fileName },
        dirs = listOf(
            MediaCatalogPaths.videosDir(context),
            MediaCatalogPaths.audiosDir(context),
            biblePublicDownloadsDir(),
        ),
    )

private fun alreadyHaveNamedMedia(titleOrFileName: String, keys: Set<String>): Boolean {
    val k = MediaDownloadDedup.stemKey(titleOrFileName)
    return k.isNotEmpty() && k in keys
}

private fun addKnownStem(raw: String, keys: MutableSet<String>) {
    val k = MediaDownloadDedup.stemKey(raw)
    if (k.isNotEmpty()) keys.add(k)
}

private fun openExternalMediaPreview(context: android.content.Context, pageUrl: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (e: Throwable) {
        Toast.makeText(
            context,
            "Не удалось открыть ссылку: ${e.message}",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

/** Ролик в списке плейлиста: превью с длительностью, отметка «уже на телефоне» и выбор. */
@Composable
private fun PlaylistDownloadRow(
    title: String,
    thumbnail: String?,
    durationLabel: String,
    checked: Boolean,
    alreadyOnPhone: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (checked) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 104.dp, height = 58.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onPreview),
        ) {
            if (!thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Открыть на платформе",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (durationLabel.isNotBlank()) {
                Text(
                    durationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (alreadyOnPhone) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Уже на телефоне",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
        )
    }
}

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
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var url by remember { mutableStateOf("") }
    var audioOnly by remember { mutableStateOf(importTarget == MediaDownloadImportTarget.Audio) }
    var selectedQuality by remember { mutableIntStateOf(720) }
    var localStatus by remember { mutableStateOf(DownloadStatus.IDLE) }
    var localStatusText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var localProgress by remember { mutableFloatStateOf(-1f) }
    var ytdlpReady by remember { mutableStateOf(false) }
    var fonkiLyrics by remember { mutableStateOf<String?>(null) }
    var playlistInspection by remember { mutableStateOf<PlaylistInspection?>(null) }
    var playlistBusy by remember { mutableStateOf(false) }
    var playlistParseError by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    /** Не скачивать, если на телефоне уже есть файл с таким же названием. */
    var skipDuplicates by remember { mutableStateOf(true) }
    /** Настройки свёрнуты, когда открыт список: он важнее и занимает всю высоту. */
    var optionsExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(playlistInspection) {
        optionsExpanded = playlistInspection == null
    }

    // Загрузка живёт в фоновом сервисе, поэтому состояние берём из общей очереди, а не из composition.
    val queue by MediaDownloadQueue.state.collectAsStateWithLifecycle()
    val status = when {
        queue.running -> DownloadStatus.DOWNLOADING
        queue.error != null -> DownloadStatus.ERROR
        queue.finishedMessage != null -> DownloadStatus.DONE
        else -> localStatus
    }
    val statusText = when {
        queue.running -> queue.statusText
        queue.finishedMessage != null -> queue.finishedMessage.orEmpty()
        else -> localStatusText
    }
    val progress = if (queue.running) queue.progress else localProgress

    LaunchedEffect(queue.failures) {
        if (queue.failures.isNotEmpty()) {
            errorText = queue.failures.joinToString("\n")
        }
    }

    val bibleVideos by viewModel.bibleUserVideos.collectAsStateWithLifecycle()
    val bibleAudios by viewModel.bibleUserAudios.collectAsStateWithLifecycle()
    val existingNamedMediaKeys =
        remember(bibleVideos, bibleAudios) {
            existingMediaStemKeys(context, bibleVideos, bibleAudios)
        }

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                VideoExtractor.init(context)
            }
            ytdlpReady = true
            localStatusText = "Обновление yt-dlp..."
            try {
                withContext(Dispatchers.IO) {
                    VideoExtractor.ensureUpdated(context)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "yt-dlp update failed: ${e.message}")
            }
            localStatusText = ""
        } catch (e: Throwable) {
            Log.e(TAG, "Init failed", e)
            errorText = "Ошибка инициализации: ${e.message}"
        }
    }

    fun loadPlaylistOutline() {
        val trimmedUrl = url.trim()
        playlistParseError = null
        if (trimmedUrl.isBlank()) {
            playlistParseError = "Укажите ссылку"
            return
        }
        if (!trimmedUrl.startsWith("http")) {
            playlistParseError = "Ссылка должна начинаться с http или https"
            return
        }
        if (FonkiExtractor.isFonkiUrl(trimmedUrl)) {
            playlistParseError = "Для fonki.pro режим списка не используется"
            return
        }
        playlistBusy = true
        scope.launch(Dispatchers.IO) {
            try {
                val inspection = VideoExtractor.inspectPlaylist(trimmedUrl)
                if (inspection.items.isEmpty()) {
                    throw RuntimeException("Список пуст или не удалось распознать ответ платформы")
                }
                mainHandler.post {
                    playlistInspection = inspection
                    selectedIds = inspection.items.map { it.stableId }.toSet()
                    playlistBusy = false
                    Toast.makeText(
                        context,
                        "Отметьте дорожки и нажмите «Скачать» — или «Выбрать всё»",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Playlist inspect failed", e)
                mainHandler.post {
                    playlistBusy = false
                    playlistInspection = null
                    selectedIds = emptySet()
                    playlistParseError =
                        ytDlpErrorUserMessage(e.message ?: "Не удалось получить список")
                }
            }
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Ставит выбранное в очередь фонового сервиса: сама загрузка переживает выход с экрана
     * и из приложения, прогресс дублируется в шторке уведомлений.
     */
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

        val inspected = playlistInspection
        val playlistMode = inspected != null && inspected.items.isNotEmpty()
        val batch = inspected?.items
            ?.filter { selectedIds.contains(it.stableId) }
            .orEmpty()
        if (playlistMode && batch.isEmpty()) {
            errorText = "Отметьте хотя бы один элемент в списке"
            return
        }

        errorText = ""
        fonkiLyrics = null
        localStatus = DownloadStatus.IDLE
        localStatusText = ""
        localProgress = -1f
        MediaDownloadQueue.clearResult()

        val importAsAudio = importTarget == MediaDownloadImportTarget.Audio
        val tasks =
            if (playlistMode) {
                batch.map { item ->
                    MediaDownloadTask(
                        url = item.pageUrl,
                        title = item.title,
                        audioOnly = audioOnly,
                        videoQuality = selectedQuality,
                        skipIfExists = skipDuplicates,
                        importAsAudio = importAsAudio,
                    )
                }
            } else {
                listOf(
                    MediaDownloadTask(
                        url = trimmedUrl,
                        title = "",
                        audioOnly = audioOnly,
                        videoQuality = selectedQuality,
                        skipIfExists = skipDuplicates,
                        importAsAudio = importAsAudio,
                    ),
                )
            }
        askNotificationPermission()
        MediaDownloadService.enqueue(context, tasks)
        Toast.makeText(context, R.string.media_download_started, Toast.LENGTH_LONG).show()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (importTarget == MediaDownloadImportTarget.Audio) "Скачать аудио"
                        else "Скачать медиа",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            localStatusText = "Обновление yt-dlp..."
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
                            localStatusText = ""
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить yt-dlp")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 12.dp) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    AnimatedVisibility(visible = status == DownloadStatus.DOWNLOADING) {
                        Column(modifier = Modifier.padding(bottom = 10.dp)) {
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
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = status == DownloadStatus.DONE && statusText.isNotBlank(),
                    ) {
                        Row(
                            modifier = Modifier.padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Button(
                        onClick = { startDownload() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = (ytdlpReady || FonkiExtractor.isFonkiUrl(url.trim())) &&
                            status != DownloadStatus.DOWNLOADING &&
                            !playlistBusy &&
                            !(playlistInspection != null && selectedIds.isEmpty()),
                    ) {
                        if (status == DownloadStatus.DOWNLOADING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (playlistInspection != null) {
                                "Скачать выбранное (${selectedIds.size})"
                            } else {
                                "Скачать"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Со списком высоту делит он сам (weight), без списка — прокручиваем настройки целиком.
        val listOpen = playlistInspection != null
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .then(
                    if (listOpen) {
                        Modifier
                    } else {
                        Modifier.verticalScroll(rememberScrollState())
                    },
                ),
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

            if (importTarget == MediaDownloadImportTarget.Audio) {
                LegalAudioSearchCard(
                    viewModel = viewModel,
                    downloading = status == DownloadStatus.DOWNLOADING,
                    onDownloadingChanged = { busy, text ->
                        if (busy) {
                            localStatus = DownloadStatus.DOWNLOADING
                            localStatusText = text
                            errorText = ""
                            localProgress = -1f
                        }
                    },
                    onImported = { title ->
                        localStatus = DownloadStatus.DONE
                        localStatusText = "Сохранено: $title"
                        localProgress = 100f
                    },
                    onError = { msg ->
                        localStatus = DownloadStatus.ERROR
                        errorText = msg
                        localStatusText = ""
                        localProgress = -1f
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !audioOnly,
                            onClick = { audioOnly = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {
                                Icon(Icons.Default.Videocam, null, Modifier.size(18.dp))
                            },
                            label = { Text("Видео") },
                        )
                        SegmentedButton(
                            selected = audioOnly,
                            onClick = { audioOnly = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {
                                Icon(Icons.Default.MusicNote, null, Modifier.size(18.dp))
                            },
                            label = { Text("Аудио") },
                        )
                    }

                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            if (errorText.isNotEmpty()) errorText = ""
                            playlistInspection = null
                            selectedIds = emptySet()
                            playlistParseError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Вставьте ссылку") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        singleLine = true,
                        trailingIcon = {
                            if (url.isNotBlank()) {
                                IconButton(onClick = {
                                    url = ""
                                    playlistInspection = null
                                    selectedIds = emptySet()
                                    playlistParseError = null
                                }) {
                                    Icon(Icons.Default.Close, "Очистить")
                                }
                            } else {
                                IconButton(onClick = {
                                    val text = clipboard.getText()?.text ?: ""
                                    if (text.isNotBlank()) {
                                        url = text
                                        playlistInspection = null
                                        selectedIds = emptySet()
                                        playlistParseError = null
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, "Вставить")
                                }
                            }
                        },
                        isError = errorText.isNotEmpty(),
                        supportingText = if (errorText.isNotEmpty()) {
                            { Text(errorText, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        shape = RoundedCornerShape(16.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!FonkiExtractor.isFonkiUrl(url.trim())) {
                            FilledTonalButton(
                                onClick = { loadPlaylistOutline() },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                enabled = ytdlpReady &&
                                    url.trim().startsWith("http") &&
                                    status != DownloadStatus.DOWNLOADING &&
                                    !playlistBusy,
                            ) {
                                if (playlistBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("Показать список", maxLines = 1, softWrap = false)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { optionsExpanded = !optionsExpanded },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text("Настройки")
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (optionsExpanded) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    AnimatedVisibility(visible = optionsExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AnimatedVisibility(visible = !audioOnly) {
                                Column {
                                    Text(
                                        "Качество видео",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        VIDEO_QUALITIES.forEach { q ->
                                            FilterChip(
                                                selected = selectedQuality == q.height,
                                                onClick = { selectedQuality = q.height },
                                                shape = RoundedCornerShape(12.dp),
                                                label = { Text(q.label, fontSize = 13.sp) },
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { skipDuplicates = !skipDuplicates }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Не скачивать повторно",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        "Пропускать файлы с таким же названием",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = skipDuplicates,
                                    onCheckedChange = { skipDuplicates = it },
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = playlistParseError != null) {
                Text(
                    playlistParseError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            playlistInspection?.let { insp ->
                val onPhoneCount = insp.items.count { listItem ->
                    alreadyHaveNamedMedia(listItem.title, existingNamedMediaKeys)
                }
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    insp.playlistTitle?.let { t -> "\u00AB$t\u00BB" }
                                        ?: "${insp.items.size} дорож.",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "Выбрано ${selectedIds.size} из ${insp.items.size}" +
                                        if (onPhoneCount > 0) " · уже есть: $onPhoneCount" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(
                                onClick = {
                                    playlistInspection = null
                                    selectedIds = emptySet()
                                    playlistParseError = null
                                },
                                enabled = !playlistBusy,
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Скрыть список")
                            }
                        }

                        if (insp.items.size > 1) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AssistChip(
                                    onClick = {
                                        selectedIds = insp.items.map { it.stableId }.toSet()
                                    },
                                    enabled = status != DownloadStatus.DOWNLOADING && !playlistBusy,
                                    shape = RoundedCornerShape(12.dp),
                                    label = { Text("Выбрать все") },
                                )
                                AssistChip(
                                    onClick = { selectedIds = emptySet() },
                                    enabled = status != DownloadStatus.DOWNLOADING && !playlistBusy,
                                    shape = RoundedCornerShape(12.dp),
                                    label = { Text("Снять") },
                                )
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(insp.items, key = { it.stableId }) { item ->
                                PlaylistDownloadRow(
                                    title = item.title,
                                    thumbnail = item.thumbnail,
                                    durationLabel = formatDurationSeconds(item.durationSec),
                                    checked = selectedIds.contains(item.stableId),
                                    alreadyOnPhone = alreadyHaveNamedMedia(
                                        item.title,
                                        existingNamedMediaKeys,
                                    ),
                                    enabled = status != DownloadStatus.DOWNLOADING,
                                    onToggle = {
                                        selectedIds = if (selectedIds.contains(item.stableId)) {
                                            selectedIds - item.stableId
                                        } else {
                                            selectedIds + item.stableId
                                        }
                                    },
                                    onPreview = {
                                        openExternalMediaPreview(context, item.pageUrl)
                                    },
                                )
                            }
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

@Composable
private fun LegalAudioSearchCard(
    viewModel: BibleViewModel,
    downloading: Boolean,
    onDownloadingChanged: (busy: Boolean, text: String) -> Unit,
    onImported: (title: String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val results by viewModel.legalAudioSearchResults.collectAsStateWithLifecycle()
    val loading by viewModel.legalAudioSearchLoading.collectAsStateWithLifecycle()
    val localHint by viewModel.legalAudioLocalHint.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var previewId by remember { mutableStateOf<String?>(null) }
    var downloadingId by remember { mutableStateOf<String?>(null) }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = OpenYandexMusicFilesTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
        previewId = null
        viewModel.searchAudioInDocumentTree(uri, query)
    }

    DisposableEffect(previewId) {
        val id = previewId
        val track = results.firstOrNull { it.id == id }
        if (id == null || track == null) {
            return@DisposableEffect onDispose { }
        }
        val player = MediaPlayer()
        try {
            when {
                track.fileUrl.startsWith("content:", ignoreCase = true) ->
                    player.setDataSource(context, Uri.parse(track.fileUrl))
                track.isLocalOnDevice() ->
                    player.setDataSource(track.fileUrl.removePrefix("file://"))
                else ->
                    player.setDataSource(track.fileUrl)
            }
            player.setOnPreparedListener { mp ->
                try {
                    mp.start()
                } catch (_: Exception) {
                }
            }
            player.setOnCompletionListener { previewId = null }
            player.setOnErrorListener { _, _, _ ->
                previewId = null
                Toast.makeText(context, "Не удалось прослушать, попробуйте скачать", Toast.LENGTH_SHORT).show()
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            previewId = null
            Toast.makeText(context, "Не удалось прослушать", Toast.LENGTH_SHORT).show()
        }
        onDispose {
            try {
                player.release()
            } catch (_: Exception) {
            }
        }
    }

    fun runSearch() {
        previewId = null
        viewModel.searchLegalAudio(query)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Поиск легальной музыки",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Openverse и Wikimedia Commons — открытые лицензии. Плюс файлы на телефоне, если система пускает в Android/data/ru.yandex.music/files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Гимн, классика, имя исполнителя…") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                trailingIcon = {
                    IconButton(onClick = { runSearch() }, enabled = !loading) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = "Искать")
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { runSearch() },
                    enabled = !loading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (loading) "Ищу…" else "Найти")
                }
                OutlinedButton(
                    onClick = { folderLauncher.launch(LocalDeviceAudioScan.yandexInitialTreeUri()) },
                    enabled = !loading,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Папка")
                }
            }
            Text(
                "«Папка» — выберите Android/data/ru.yandex.music/files, если проводник её открывает.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            localHint?.let { hint ->
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (!loading && query.isNotBlank() && results.isEmpty() && localHint == null) {
                Text(
                    "Ничего не найдено. Попробуйте другое слово — лучше ищутся гимны, классика, фольклор и CC-треки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (results.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Найдено: ${results.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(results, key = { it.id }) { track ->
                        val busy = downloadingId == track.id || (downloading && downloadingId != null)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp),
                                )
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp),
                                ) {
                                    Text(
                                        track.displayTitle(),
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val meta = buildString {
                                        append(track.originLabel())
                                        append(" · ")
                                        append(track.licenseLabel())
                                        val d = track.durationSec
                                        if (d != null && d > 0) {
                                            append(" · ")
                                            append(formatDurationSeconds(d.toLong()))
                                        }
                                    }
                                    Text(
                                        meta,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        previewId = if (previewId == track.id) null else track.id
                                    },
                                    enabled = !busy,
                                ) {
                                    Icon(
                                        if (previewId == track.id) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Прослушать",
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        downloadingId = track.id
                                        previewId = null
                                        onDownloadingChanged(true, "Скачивание: ${track.displayTitle()}")
                                        viewModel.importLegalAudioTrack(track) { err ->
                                            downloadingId = null
                                            if (err != null) {
                                                onError(err)
                                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                            } else {
                                                onImported(track.displayTitle())
                                                Toast.makeText(
                                                    context,
                                                    "Добавлено в «Медиа → Аудио»",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        }
                                    },
                                    enabled = !busy,
                                ) {
                                    if (downloadingId == track.id) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Filled.Download, contentDescription = "Скачать")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

