package com.example.bible.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.bible.MainActivity
import com.example.bible.R
import com.example.bible.data.BibleAudioDownloadQueue
import com.example.bible.data.BibleAudioNarrators
import com.example.bible.data.BibleAudioPlayer
import com.example.bible.data.booksForDownloadEntireBible
import com.example.bible.data.chapterCountForDownloadEntireBible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Фоновая загрузка озвучки Библии: продолжается при сворачивании приложения.
 */
class BibleAudioDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var workerJob: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                BibleAudioDownloadQueue.requestCancel()
                workerJob?.cancel()
                updateNotification(getString(R.string.bible_audio_download_cancelled))
                BibleAudioDownloadQueue.finish(cancelled = true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val ids = intent.getStringArrayListExtra(EXTRA_NARRATOR_IDS)
                    ?: parseIds(intent.getStringExtra(EXTRA_NARRATOR_IDS_JSON))
                if (ids.isNullOrEmpty()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundWith(buildNotification(0, 0, ""))
                if (workerJob?.isActive == true) {
                    return START_STICKY
                }
                workerJob = scope.launch { runDownloads(ids) }
                return START_STICKY
            }
        }
        stopSelf()
        return START_NOT_STICKY
    }

    private suspend fun runDownloads(narratorIds: List<String>) {
        val narrators = narratorIds.map { BibleAudioNarrators.byId(it) }
        val total = narrators.sumOf { chapterCountForDownloadEntireBible(it) }
        BibleAudioDownloadQueue.begin(narratorIds, total)
        var done = 0
        var errors = 0

        try {
            for (narrator in narrators) {
                if (BibleAudioDownloadQueue.cancelRequested) break
                val books = booksForDownloadEntireBible(narrator)
                for (book in books) {
                    if (BibleAudioDownloadQueue.cancelRequested) break
                    for (ch in 1..book.chapters) {
                        if (BibleAudioDownloadQueue.cancelRequested) break
                        val label = "${narrator.name}: ${book.abbrRu} $ch"
                        try {
                            BibleAudioPlayer.downloadChapter(
                                applicationContext,
                                narrator,
                                book.id,
                                ch,
                            )
                        } catch (_: Exception) {
                            errors++
                            BibleAudioDownloadQueue.incrementError()
                        }
                        done++
                        BibleAudioDownloadQueue.updateProgress(done, label, errors)
                        updateNotification(done, total, label)
                    }
                }
            }
        } finally {
            val cancelled = BibleAudioDownloadQueue.cancelRequested
            BibleAudioDownloadQueue.finish(cancelled = cancelled)
            if (cancelled) {
                updateNotification(getString(R.string.bible_audio_download_cancelled))
            } else {
                updateNotification(getString(R.string.bible_audio_download_done, done, errors))
            }
            stopSelf()
        }
    }

    private fun updateNotification(done: Int, total: Int, label: String) {
        startForegroundWith(buildNotification(done, total, label))
    }

    private fun updateNotification(text: String) {
        startForegroundWith(buildNotificationText(text))
    }

    private fun buildNotification(done: Int, total: Int, label: String): android.app.Notification {
        val progress = if (total > 0) (done * 100 / total).coerceIn(0, 100) else 0
        val content = if (label.isNotBlank()) {
            getString(R.string.bible_audio_download_notif_progress, done, total, label)
        } else {
            getString(R.string.bible_audio_download_notif_preparing)
        }
        return baseNotificationBuilder(content)
            .setProgress(100, progress, total <= 0)
            .build()
    }

    private fun buildNotificationText(text: String): android.app.Notification =
        baseNotificationBuilder(text)
            .setProgress(0, 0, false)
            .build()

    private fun baseNotificationBuilder(content: String): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, BibleAudioDownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.bible_audio_download_notif_title))
            .setContentText(content)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.bible_audio_download_cancel), cancel)
    }

    private fun startForegroundWith(notification: android.app.Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.bible_audio_download_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onDestroy() {
        workerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "bible_audio_download"
        private const val NOTIF_ID = 7741

        const val ACTION_START = "BibleAudioDownloadService.start"
        const val ACTION_CANCEL = "BibleAudioDownloadService.cancel"
        private const val EXTRA_NARRATOR_IDS = "narratorIds"
        private const val EXTRA_NARRATOR_IDS_JSON = "narratorIdsJson"

        fun start(context: Context, narratorIds: List<String>) {
            if (narratorIds.isEmpty()) return
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, BibleAudioDownloadService::class.java).apply {
                    action = ACTION_START
                    putStringArrayListExtra(EXTRA_NARRATOR_IDS, ArrayList(narratorIds))
                },
            )
        }

        fun cancel(context: Context) {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, BibleAudioDownloadService::class.java).apply {
                    action = ACTION_CANCEL
                },
            )
        }

        private fun parseIds(json: String?): List<String>? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
            }.getOrNull()
        }
    }
}
