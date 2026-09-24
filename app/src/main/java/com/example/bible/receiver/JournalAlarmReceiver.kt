package com.example.bible.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.bible.MainActivity
import com.example.bible.R
import com.example.bible.data.BiblePreferences
import com.example.bible.data.JournalAlarmScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class JournalAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank {
            app.getString(R.string.journal_alarm_title)
        }
        val tag = intent?.getStringExtra(EXTRA_TAG).orEmpty()
        val text = if (tag == "remind") {
            app.getString(R.string.journal_alarm_soon)
        } else {
            app.getString(R.string.journal_alarm_now)
        }
        showAlarm(app, title, text, (intent?.getStringExtra(EXTRA_ID).orEmpty() + tag).hashCode())
        val pending = goAsync()
        Thread {
            try {
                runBlocking {
                    val entries = BiblePreferences(app).dailyJournalEntries.first()
                    JournalAlarmScheduler.reschedule(app, entries)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun showAlarm(app: Context, title: String, text: String, notifyId: Int) {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "journal_alarm_v1"
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                app.getString(R.string.journal_alarm_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(
                    sound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
        val open = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val content = PendingIntent.getActivity(
            app,
            notifyId,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(app, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(sound)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setContentIntent(content)
            .setFullScreenIntent(content, true)
            .setAutoCancel(true)
            .build()
        notif.flags = notif.flags or Notification.FLAG_INSISTENT
        nm.notify(notifyId, notif)
    }

    companion object {
        const val EXTRA_ID = "journal_id"
        const val EXTRA_TITLE = "journal_title"
        const val EXTRA_TAG = "journal_tag"
    }
}
