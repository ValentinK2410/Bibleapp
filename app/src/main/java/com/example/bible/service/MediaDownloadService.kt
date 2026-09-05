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
import com.example.bible.data.MediaDownloadItem
import com.example.bible.data.MediaDownloadItemStatus
import com.example.bible.data.MediaDownloadQueue
import com.example.bible.data.MediaDownloadState
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Скачивает медиа в фоне: очередь переживает выход с экрана и сворачивание приложения,
 * прогресс виден в шторке уведомлений. Файлы сразу попадают в «Медиа → Видео/Аудио».
 *
 * Очередь пополняется на ходу, несколько файлов качаются одновременно, каждую загрузку
 * можно поставить на паузу и продолжить — недокачанный файл дописывается, а не качается заново.
 */
class MediaDownloadService : Service() {

    private enum class StopKind { PAUSE, CANCEL }

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)

    /** Общий замок на переходы очереди: два воркера не должны взять одну задачу. */
    private val queueLock = Mutex()
    private val readyLock = Mutex()
    private val workers = Collections.synchronizedList(mutableListOf<Job>())
    private val runningWorkers = AtomicInteger(0)

    /** Задачи, которые пользователь остановил: воркер прочитает причину и не посчитает это ошибкой. */
    private val stopRequests = ConcurrentHashMap<String, StopKind>()
    private val knownStems: MutableSet<String> = Collections.synchronizedSet(mutableSetOf<String>())
    private val failures: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())

    private var parallel = MediaDownloadState.DEFAULT_PARALLEL
    private var ready = false
    private var foregroundStarted = false
    private var lastNotifyAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelAll()
                return START_NOT_STICKY
            }

            ACTION_PAUSE_ITEM -> {
                intent.getStringExtra(EXTRA_ITEM_ID)?.let { pauseItem(it) }
                afterControlAction()
                return START_NOT_STICKY
            }

            ACTION_RESUME_ITEM -> {
                intent.getStringExtra(EXTRA_ITEM_ID)?.let { resumeItem(it) }
                afterControlAction()
                return START_NOT_STICKY
            }

            ACTION_CANCEL_ITEM -> {
                intent.getStringExtra(EXTRA_ITEM_ID)?.let { cancelItem(it) }
                afterControlAction()
                return START_NOT_STICKY
            }

            ACTION_PAUSE_ALL -> {
                MediaDownloadQueue.state.value.items
                    .filter { it.status == MediaDownloadItemStatus.RUNNING || it.status == MediaDownloadItemStatus.QUEUED }
                    .forEach { pauseItem(it.id) }
                afterControlAction()
                return START_NOT_STICKY
            }

            ACTION_RESUME_ALL -> {
                MediaDownloadQueue.state.value.items
                    .filter { it.status == MediaDownloadItemStatus.PAUSED }
                    .forEach { resumeItem(it.id) }
                afterControlAction()
                return START_NOT_STICKY
            }
        }

        intent?.getIntExtra(EXTRA_PARALLEL, 0)?.takeIf { it > 0 }?.let { requested ->
            parallel = requested.coerceIn(1, MediaDownloadState.MAX_PARALLEL)
            MediaDownloadQueue.update { it.copy(parallel = parallel) }
        }

        val tasks = MediaDownloadTask.listFromJson(intent?.getStringExtra(EXTRA_TASKS))
        // Уведомление показываем всегда: система требует startForeground после startForegroundService.
        startForegroundWith(buildNotification())
        if (tasks.isEmpty() && !hasLiveWork()) {
            stopEverything()
            return START_NOT_STICKY
        }
        addTasks(tasks)
        ensureWorkers()
        return START_NOT_STICKY
    }

    /** Добавляет задачи в конец очереди, подчищая старые завершённые строки. */
    private fun addTasks(tasks: List<MediaDownloadTask>) {
        if (tasks.isEmpty()) return
        MediaDownloadQueue.update { state ->
            val live = state.items.filterNot { it.status.terminal }
            val history = state.items.filter { it.status.terminal }
            val room = (MAX_HISTORY - live.size - tasks.size).coerceAtLeast(0)
            state.copy(
                items = history.takeLast(room) + live + tasks.map { MediaDownloadItem(task = it) },
                parallel = parallel,
                finishedMessage = null,
                error = null,
                failures = emptyList(),
            )
        }
        failures.clear()
    }

    private fun hasLiveWork(): Boolean = MediaDownloadQueue.state.value.hasWork

    /** Держит нужное число одновременных загрузок: слот освободился — берём следующую ссылку. */
    @Synchronized
    private fun ensureWorkers() {
        workers.removeAll { !it.isActive }
        while (runningWorkers.get() < parallel) {
            runningWorkers.incrementAndGet()
            workers += scope.launch { workerLoop() }
        }
    }

    private suspend fun workerLoop() {
        try {
            ensureReady()
            while (runningWorkers.get() <= parallel) {
                val item = takeNext() ?: break
                runTask(item)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Worker failed", e)
            failures += VideoExtractor.userMessage(e.message ?: "Ошибка загрузки")
        } finally {
            runningWorkers.decrementAndGet()
            maybeFinish()
        }
    }

    /** Разовая подготовка: yt-dlp и список уже скачанных названий для пропуска дублей. */
    private suspend fun ensureReady() = readyLock.withLock {
        if (ready) return@withLock
        VideoExtractor.init(applicationContext)
        knownStems.addAll(loadKnownStems())
        ready = true
    }

    private suspend fun takeNext(): MediaDownloadItem? = queueLock.withLock {
        val next = MediaDownloadQueue.state.value.items
            .firstOrNull { it.status == MediaDownloadItemStatus.QUEUED }
            ?: return@withLock null
        MediaDownloadQueue.updateItem(next.id) {
            it.copy(status = MediaDownloadItemStatus.RUNNING, progress = -1f, message = "")
        }
        MediaDownloadQueue.item(next.id)
    }

    private suspend fun runTask(item: MediaDownloadItem) {
        val task = item.task
        var label = task.title
        refreshNotification(force = true)
        try {
            if (FonkiExtractor.isFonkiUrl(task.url)) {
                downloadFonki(item)
                return
            }
            if (label.isBlank()) {
                label = runCatching { VideoExtractor.fetchInfo(task.url).title }.getOrDefault("")
                if (label.isNotBlank()) {
                    MediaDownloadQueue.updateItem(item.id) { it.copy(title = label) }
                }
            }
            if (task.skipIfExists && isKnown(label)) {
                finishItem(item.id, MediaDownloadItemStatus.SKIPPED, getString(com.example.bible.R.string.media_download_skip_existing))
                return
            }
            // Паузу могли нажать, пока узнавали название, — тогда не начинаем качать.
            if (stopRequests.containsKey(item.id)) throw RuntimeException(STOP_MARKER)
            val file = VideoExtractor.download(
                url = task.url,
                audioOnly = task.audioOnly,
                videoQuality = task.videoQuality,
                skipIfFileExists = task.skipIfExists,
                processId = item.id,
                onProgress = { percent, eta -> publishProgress(item.id, percent, eta) },
            )
            importFile(file, label.ifBlank { file.nameWithoutExtension }, item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            handleTaskFailure(item, label, e)
        }
    }

    private suspend fun downloadFonki(item: MediaDownloadItem) {
        val task = item.task
        val song = FonkiExtractor.extract(task.url)
        val title = buildString {
            if (song.artist.isNotBlank()) append("${song.artist} - ")
            append(song.title)
        }
        MediaDownloadQueue.updateItem(item.id) { it.copy(title = title) }
        if (task.skipIfExists && (isKnown(title) || isKnown(song.title))) {
            finishItem(item.id, MediaDownloadItemStatus.SKIPPED, getString(com.example.bible.R.string.media_download_skip_existing))
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
            // У fonki нет отдельного процесса, поэтому паузу проверяем прямо в колбэке прогресса.
            if (stopRequests.containsKey(item.id)) throw RuntimeException(STOP_MARKER)
            publishProgress(item.id, percent.toFloat(), 0)
        }
        runCatching { FonkiExtractor.saveLyrics(song) }
        importFile(file, title, item)
    }

    private suspend fun importFile(file: File, title: String, item: MediaDownloadItem) {
        val error = MediaDownloadImporter.import(
            context = applicationContext,
            file = file,
            title = title,
            sourceUrl = item.task.url,
            asAudio = item.task.importAsAudio,
        )
        if (error != null) {
            failures += "${title.take(48)} — $error"
            finishItem(item.id, MediaDownloadItemStatus.FAILED, error)
            return
        }
        val saved = title.ifBlank { file.nameWithoutExtension }
        remember(title)
        remember(file.name)
        remember(file.nameWithoutExtension)
        MediaDownloadQueue.update { it.copy(lastCompleted = saved) }
        finishItem(item.id, MediaDownloadItemStatus.DONE, getString(com.example.bible.R.string.media_download_saved))
    }

    /** Пауза и отмена прилетают исключением из yt-dlp — отличаем их от настоящей ошибки. */
    private fun handleTaskFailure(item: MediaDownloadItem, label: String, e: Throwable) {
        when (stopRequests.remove(item.id)) {
            StopKind.PAUSE -> {
                MediaDownloadQueue.updateItem(item.id) {
                    it.copy(
                        status = MediaDownloadItemStatus.PAUSED,
                        message = getString(com.example.bible.R.string.media_download_item_paused),
                    )
                }
                refreshNotification(force = true)
            }

            StopKind.CANCEL -> {
                MediaDownloadQueue.removeItem(item.id)
                refreshNotification(force = true)
            }

            null -> {
                Log.e(TAG, "Download failed: ${item.task.url}", e)
                val reason = VideoExtractor.userMessage(e.message ?: "ошибка")
                failures += if (label.isBlank()) reason else "${label.take(48)} — $reason"
                finishItem(item.id, MediaDownloadItemStatus.FAILED, reason)
            }
        }
    }

    private fun finishItem(id: String, status: MediaDownloadItemStatus, message: String) {
        stopRequests.remove(id)
        MediaDownloadQueue.updateItem(id) {
            it.copy(
                status = status,
                message = message,
                progress = if (status == MediaDownloadItemStatus.DONE) 100f else -1f,
            )
        }
        refreshNotification(force = true)
    }

    private fun publishProgress(id: String, percent: Float, etaSec: Long) {
        val current = MediaDownloadQueue.item(id) ?: return
        if (current.progress >= 0f && kotlin.math.abs(current.progress - percent) < 0.5f) return
        MediaDownloadQueue.updateItem(id) { it.copy(progress = percent, etaSec = etaSec) }
        refreshNotification()
    }

    // ------------------------------------------------------------------ управление

    private fun pauseItem(id: String) {
        val item = MediaDownloadQueue.item(id) ?: return
        when (item.status) {
            MediaDownloadItemStatus.RUNNING -> {
                stopRequests[id] = StopKind.PAUSE
                VideoExtractor.cancelProcess(id)
            }

            MediaDownloadItemStatus.QUEUED -> {
                MediaDownloadQueue.updateItem(id) {
                    it.copy(
                        status = MediaDownloadItemStatus.PAUSED,
                        message = getString(com.example.bible.R.string.media_download_item_paused),
                    )
                }
            }

            else -> Unit
        }
    }

    private fun resumeItem(id: String) {
        val item = MediaDownloadQueue.item(id) ?: return
        if (item.status != MediaDownloadItemStatus.PAUSED) return
        stopRequests.remove(id)
        MediaDownloadQueue.updateItem(id) {
            it.copy(status = MediaDownloadItemStatus.QUEUED, message = "")
        }
        ensureWorkers()
    }

    private fun cancelItem(id: String) {
        val item = MediaDownloadQueue.item(id) ?: return
        if (item.status == MediaDownloadItemStatus.RUNNING) {
            stopRequests[id] = StopKind.CANCEL
            VideoExtractor.cancelProcess(id)
        } else {
            MediaDownloadQueue.removeItem(id)
        }
    }

    /** После кнопок паузы/отмены: либо обновляем шторку, либо гасим сервис, если работы нет. */
    private fun afterControlAction() {
        if (hasLiveWork()) {
            if (foregroundStarted) refreshNotification(force = true)
        } else {
            scope.launch { maybeFinish() }
        }
    }

    private fun cancelAll() {
        val state = MediaDownloadQueue.state.value
        state.items.filter { it.status == MediaDownloadItemStatus.RUNNING }.forEach {
            stopRequests[it.id] = StopKind.CANCEL
            VideoExtractor.cancelProcess(it.id)
        }
        workers.forEach { it.cancel() }
        workers.clear()
        runningWorkers.set(0)
        stopRequests.clear()
        MediaDownloadQueue.update {
            it.copy(
                items = it.items.filter { item -> item.status.terminal },
                finishedMessage = getString(com.example.bible.R.string.media_download_cancelled),
            )
        }
        failures.clear()
        stopEverything()
    }

    private suspend fun maybeFinish() = queueLock.withLock {
        val state = MediaDownloadQueue.state.value
        if (state.items.isEmpty()) {
            stopEverything()
            return@withLock
        }
        if (state.active.isNotEmpty() || state.queued > 0) return@withLock
        if (state.paused > 0) {
            // Ждём пользователя: сервис остаётся живым, чтобы паузу можно было снять.
            refreshNotification(force = true)
            return@withLock
        }
        val downloaded = state.downloaded
        val skipped = state.skipped
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
                failures = failures.take(3),
                finishedMessage = message,
                error = failures.firstOrNull(),
            )
        }
        showSummaryNotification(message)
        failures.clear()
        stopEverything()
    }

    private fun stopEverything() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        stopSelf()
    }

    // ------------------------------------------------------------------ дубликаты

    private suspend fun loadKnownStems(): Set<String> {
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
        )
    }

    private fun isKnown(title: String): Boolean {
        val key = MediaDownloadDedup.stemKey(title)
        return key.isNotEmpty() && key in knownStems
    }

    private fun remember(raw: String) {
        val key = MediaDownloadDedup.stemKey(raw)
        if (key.isNotEmpty()) knownStems.add(key)
    }

    // ------------------------------------------------------------------ уведомление

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
        foregroundStarted = true
    }

    private fun serviceIntent(action: String, itemId: String? = null) =
        Intent(this, MediaDownloadService::class.java).apply {
            this.action = action
            itemId?.let { putExtra(EXTRA_ITEM_ID, it) }
        }

    private fun buildNotification(): android.app.Notification {
        val state = MediaDownloadQueue.state.value
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelAll = PendingIntent.getService(
            this,
            1,
            serviceIntent(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val allPaused = state.active.isEmpty() && state.queued == 0 && state.paused > 0
        val toggle = PendingIntent.getService(
            this,
            2,
            serviceIntent(if (allPaused) ACTION_RESUME_ALL else ACTION_PAUSE_ALL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val done = state.downloaded + state.skipped + state.failed
        val heading = when {
            state.total > 1 ->
                getString(com.example.bible.R.string.media_download_position, done + 1, state.total)
            else -> getString(com.example.bible.R.string.media_download_notif_title)
        }
        val text = when {
            allPaused -> getString(com.example.bible.R.string.media_download_all_paused)
            state.active.isEmpty() -> getString(com.example.bible.R.string.media_download_preparing)
            else -> state.active.joinToString("\n") { item ->
                val percent = if (item.progress >= 0f) " · ${item.progress.toInt()}%" else ""
                "${item.label.take(60)}$percent"
            }
        }
        val overall = state.overallProgress
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(heading)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText(
                if (state.remaining > 0) {
                    getString(com.example.bible.R.string.media_download_remaining, state.remaining)
                } else {
                    null
                },
            )
            .setProgress(100, overall.toInt().coerceIn(0, 100), overall < 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(
                0,
                getString(
                    if (allPaused) {
                        com.example.bible.R.string.media_download_resume_all
                    } else {
                        com.example.bible.R.string.media_download_pause_all
                    },
                ),
                toggle,
            )
            .addAction(0, getString(com.example.bible.R.string.media_download_cancel), cancelAll)
            .build()
    }

    /** Шторку трогаем не чаще пары раз в секунду, иначе несколько загрузок её захлёбывают. */
    private fun refreshNotification(force: Boolean = false) {
        if (!foregroundStarted) return
        val now = System.currentTimeMillis()
        if (!force && now - lastNotifyAt < NOTIFY_INTERVAL_MS) return
        lastNotifyAt = now
        notifyQuietly(NOTIF_ID, buildNotification())
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
        private const val NOTIFY_INTERVAL_MS = 600L
        private const val MAX_HISTORY = 60
        private const val STOP_MARKER = "media-download-stopped"

        const val ACTION_CANCEL = "MediaDownloadService.cancel"
        const val ACTION_PAUSE_ITEM = "MediaDownloadService.pauseItem"
        const val ACTION_RESUME_ITEM = "MediaDownloadService.resumeItem"
        const val ACTION_CANCEL_ITEM = "MediaDownloadService.cancelItem"
        const val ACTION_PAUSE_ALL = "MediaDownloadService.pauseAll"
        const val ACTION_RESUME_ALL = "MediaDownloadService.resumeAll"
        private const val EXTRA_TASKS = "tasks"
        private const val EXTRA_ITEM_ID = "itemId"
        private const val EXTRA_PARALLEL = "parallel"

        /** Добавляет задачи в очередь: если сервис уже работает, они встанут в хвост. */
        fun enqueue(context: Context, tasks: List<MediaDownloadTask>, parallel: Int = 0) {
            if (tasks.isEmpty()) return
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, MediaDownloadService::class.java).apply {
                    putExtra(EXTRA_TASKS, MediaDownloadTask.listToJson(tasks))
                    if (parallel > 0) putExtra(EXTRA_PARALLEL, parallel)
                },
            )
        }

        fun cancel(context: Context) = control(context, ACTION_CANCEL)

        fun pauseItem(context: Context, itemId: String) =
            control(context, ACTION_PAUSE_ITEM, itemId)

        fun resumeItem(context: Context, itemId: String) =
            control(context, ACTION_RESUME_ITEM, itemId)

        fun cancelItem(context: Context, itemId: String) =
            control(context, ACTION_CANCEL_ITEM, itemId)

        fun pauseAll(context: Context) = control(context, ACTION_PAUSE_ALL)

        fun resumeAll(context: Context) = control(context, ACTION_RESUME_ALL)

        private fun control(context: Context, action: String, itemId: String? = null) {
            val app = context.applicationContext
            runCatching {
                app.startService(
                    Intent(app, MediaDownloadService::class.java).apply {
                        this.action = action
                        itemId?.let { putExtra(EXTRA_ITEM_ID, it) }
                    },
                )
            }
        }
    }
}
