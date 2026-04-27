package com.example.bible.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.bible.R

class TravelMediaService : Service() {

    private var player: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_PLAY) {
            stopSelf()
            return START_NOT_STICKY
        }
        val uriStr = intent.getStringExtra(EXTRA_URI) ?: return START_NOT_STICKY
        val uri = Uri.parse(uriStr)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        ensureChannel()
        startForeground(
            NOTIF_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.travel_media_service_title))
                .setContentText(title.ifBlank { getString(R.string.travel_media_service_playing) })
                .setOngoing(true)
                .build(),
        )
        releasePlayer()
        runCatching {
            player = MediaPlayer.create(this, uri)?.apply {
                setOnCompletionListener {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                start()
            }
        }
        if (player == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.travel_media_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "travel_media_playback"
        private const val NOTIF_ID = 8822
        const val ACTION_PLAY = "play"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_TITLE = "title"

        fun startSound(context: android.content.Context, uri: Uri, title: String) {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, TravelMediaService::class.java).apply {
                    action = ACTION_PLAY
                    putExtra(EXTRA_URI, uri.toString())
                    putExtra(EXTRA_TITLE, title)
                },
            )
        }
    }
}
