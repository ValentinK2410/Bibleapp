package com.example.bible.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.bible.MainActivity
import com.example.bible.data.BiblePreferences
import com.example.bible.data.FonkiExtractor
import com.example.bible.data.MediaCatalogPaths
import com.example.bible.data.MediaDownloadDedup
import com.example.bible.data.MediaDownloadImporter
import com.example.bible.data.MediaDownloadQueue
import com.example.bible.data.MediaDownloadTask
import com.example.bible.data.VideoExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Скачивает медиа в фоне: очередь переживает выход с экрана и сворачивание приложения,
 * прогресс виден в шторке уведомлений. Файлы сразу попадают в «Медиа → Видео/Аудио».
 */
class MediaDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val queue = ConcurrentLinkedQueue<MediaDownloadTask>()
    private var worker: Job? = null

    private var cancelled = false
    private var lastCompleted: String? = null
    private var total = 0
    private var index = 0
    private var downloaded = 0
    private var skipped = 0
    private val failures = mutableListOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelAll()
            return START_NOT_STICKY
        }
        val tasks = MediaDownloadTask.listFromJson(intent?.getStringExtra(EXTRA_TASKS))
        // Уведомление показываем всегда: система требует startForeground после startForegroundService.
        startForegroundWith(buildNotification(getString(com.example.bible.R.string.media_download_preparing), -1f))
        if (tasks.isEmpty() && worker?.isActive != true) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        cancelled = false
        queue.addAll(tasks)
        total += tasks.size
        publish(text = getString(com.example.bible.R.string.media_download_preparing), progress = -1f)
        ensureWorker()
        return START_NOT_STICKY
    }

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            try {
                VideoExtractor.init(applicationContext)
                val known = knownStems()
                while (true) {
                    val task = queue.poll() ?: break
                    index++
                    runTask(task, known)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Queue failed", e)
                failures += VideoExtractor.userMessage(e.message ?: "Ошибка загрузки")
            } finally {
                finishQueue()
            }
        }
    }

    private suspend fun runTask(task: MediaDownloadTask, known: MutableSet<String>) {
        var label = task.title
        publish(
            text = position() + getString(com.example.bible.R.string.media_download_preparing),
            progress = -1f,
            title = label,
        )
        updateNotification(label.ifBlank { task.url }, -1f)
        try {
            if (FonkiExtractor.isFonkiUrl(task.url)) {
                downloadFonki(task, known)
                return
            }
            label = task.title.ifBlank {
                runCatching { VideoExtractor.fetchInfo(task.url).title }.getOrDefault("")
            }
            if (task.skipIfExists && isKnown(label, known)) {
                skipped++
                publish(
                    text = position() + getString(com.example.bible.R.string.media_download_skip_existing),
                    progress = -1f,
                    title = label,
                )
                return
            }
            val file = VideoExtractor.download(
                url = task.url,
                audioOnly = task.audioOnly,
                videoQuality = task.videoQuality,
                skipIfFileExists = task.skipIfExists,
                onProgress = { percent, eta ->
                    val etaText = if (eta > 0) " · ~${eta}с" else ""
                    publish(
                        text = "${position()}${percent.toInt()}%$etaText",
                        progress = percent,
                        title = label,
                    )
                    updateNotification(label.ifBlank { task.url }, percent)
                },
            )
            importFile(file, label.ifBlank { file.nameWithoutExtension }, task, known)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Download failed: ${task.url}", e)
            val reason = VideoExtractor.userMessage(e.message ?: "ошибка")
            failures += if (label.isBlank()) reason else "${label.take(48)} — $reason"
        }
    }

    private suspend fun downloadFonki(task: MediaDownloadTask, known: MutableSet<String>) {
        val song = FonkiExtractor.extract(task.url)
        val title = buildString {
            if (song.artist.isNotBlank()) append("${song.artist} - ")
            append(song.title)
        }
        if (task.skipIfExists && (isKnown(title, known) || isKnown(song.title, known))) {
            skipped++
            publish(
                text = position() + getString(com.example.bible.R.string.media_download_skip_existing),
                progress = -1f,
                title = title,
            )
            return
        }
        val trackUrl = song.tracks.firstOrNull()?.url
            ?: throw RuntimeException("Аудиофайл не найден")
        val file = FonkiExtractor.downloadAudio(
            context = applicationContext,
            url = trackUrl,
            songTitle = song.title,
            songArtist = song.artist,
        ) { percent ->
            publish(text = "${position()}$percent%", progress = percent.toFloat(), title = title)
            updateNotification(title, percent.toFloat())
        }
        runCatching { FonkiExtractor.saveLyrics(song) }
        importFile(file, title, task, known)
    }

    private suspend fun importFile(
        file: File,
        title: String,
        task: MediaDownloadTask,
        known: MutableSet<String>,
    ) {
        val error = MediaDownloadImporter.import(
            context = applicationContext,
            file = file,
            title = title,
            sourceUrl = task.url,
            asAudio = task.importAsAudio,
        )
        if (error != null) {
            failures += "${title.take(48)} — $error"
            return
        }
        downloaded++
        lastCompleted = title.ifBlank { file.nameWithoutExtension }
        publish(
            text = position() + getString(com.example.bible.R.string.media_download_saved),
            progress = 100f,
            title = title,
        )
        remember(title, known)
        remember(file.name, known)
        remember(file.nameWithoutExtension, known)
    }

    private fun finishQueue() {
        if (cancelled) return
        val message = when {
            downloaded > 0 && skipped > 0 ->
                getString(com.example.bible.R.string.media_download_done_mixed, downloaded, skipped)
            downloaded > 0 ->
                getString(com.example.bible.R.string.media_download_done_ok, downloaded)
            skipped > 0 && failures.isEmpty() ->
                getString(com.example.bible.R.string.media_download_done_skipped, skipped)
            failures.isEmpty() ->
                getString(com.example.bible.R.string.media_download_done_empty)
            else ->
                getString(com.example.bible.R.string.media_download_done_failed, failures.size)
        }
        MediaDownloadQueue.update {
            it.copy(
                running = false,
                progress = -1f,
                statusText = "",
                currentTitle = "",
                lastCompleted = lastCompleted,
                index = index,
                total = total,
                downloaded = downloaded,
                skipped = skipped,
                failures = failures.take(3),
                finishedMessage = message,
                error = failures.firstOrNull(),
            )
        }
        showSummaryNotification(message)
        lastCompleted = null
        total = 0
        index = 0
        downloaded = 0
        skipped = 0
        failures.clear()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cancelAll() {
        cancelled = true
        queue.clear()
        worker?.cancel()
        worker = null
        MediaDownloadQueue.update {
            it.copy(
                running = false,
                progress = -1f,
                statusText = "",
                currentTitle = "",
                finishedMessage = getString(com.example.bible.R.string.media_download_cancelled),
            )
        }
        lastCompleted = null
        total = 0
        index = 0
        downloaded = 0
        skipped = 0
        failures.clear()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun position(): String =
        if (total > 1) getString(com.example.bible.R.string.media_download_position, index, total) + " · " else ""

    private fun publish(text: String, progress: Float, title: String = "") {
        MediaDownloadQueue.update {
            it.copy(
                running = true,
                statusText = text,
                progress = progress,
                currentTitle = title,
                lastCompleted = lastCompleted,
                index = index,
                total = total,
                downloaded = downloaded,
                skipped = skipped,
                finishedMessage = null,
                error = null,
            )
        }
    }

    private suspend fun knownStems(): MutableSet<String> {
        val prefs = BiblePreferences(applicationContext)
        val videos = prefs.userBibleVideos.first()
        val audios = prefs.userBibleAudios.first()
        return MediaDownloadDedup.collectStems(
            titles = videos.map { it.title } + audios.map { it.title },
            fileNames = videos.map { it.fileName } + audios.map { it.fileName },
            dirs = listOf(
                MediaCatalogPaths.videosDir(applicationContext),
                MediaCatalogPaths.audiosDir(applicationContext),
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Bible",
                ),
            ),
        ).toMutableSet()
    }

    private fun isKnown(title: String, known: Set<String>): Boolean {
        val key = MediaDownloadDedup.stemKey(title)
        return key.isNotEmpty() && key in known
    }

    private fun remember(raw: String, known: MutableSet<String>) {
        val key = MediaDownloadDedup.stemKey(raw)
        if (key.isNotEmpty()) known.add(key)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(com.example.bible.R.string.media_download_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun startForegroundWith(notification: android.app.Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun buildNotification(text: String, progress: Float): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaDownloadService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val heading = if (total > 1) {
            getString(com.example.bible.R.string.media_download_position, index, total)
        } else {
            getString(com.example.bible.R.string.media_download_notif_title)
        }
        val remaining = (total - index).coerceAtLeast(0)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(heading)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText(
                if (total > 1) {
                    getString(com.example.bible.R.string.media_download_remaining, remaining)
                } else {
                    null
                },
            )
            .setProgress(100, progress.toInt().coerceIn(0, 100), progress < 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(
                0,
                getString(com.example.bible.R.string.media_download_cancel),
                cancel,
            )
            .build()
    }

    private fun updateNotification(text: String, progress: Float) {
        notifyQuietly(NOTIF_ID, buildNotification(text, progress))
    }

    private fun showSummaryNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(com.example.bible.R.string.media_download_notif_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        notifyQuietly(SUMMARY_NOTIF_ID, notification)
    }

    private fun notifyQuietly(id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(this).notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Нет разрешения на уведомления: ${e.message}")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MediaDownloadService"
        private const val CHANNEL_ID = "media_download"
        private const val NOTIF_ID = 7731
        private const val SUMMARY_NOTIF_ID = 7732
        const val ACTION_CANCEL = "MediaDownloadService.cancel"
        private const val EXTRA_TASKS = "tasks"

        fun enqueue(context: Context, tasks: List<MediaDownloadTask>) {
            if (tasks.isEmpty()) return
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, MediaDownloadService::class.java).apply {
                    putExtra(EXTRA_TASKS, MediaDownloadTask.listToJson(tasks))
                },
            )
        }

        fun cancel(context: Context) {
            context.applicationContext.startService(
                Intent(context.applicationContext, MediaDownloadService::class.java).apply {
                    action = ACTION_CANCEL
                },
            )
        }
    }
}
