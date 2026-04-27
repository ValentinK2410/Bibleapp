package com.example.bible.data.travel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.bible.MainActivity
import com.example.bible.R
import com.example.bible.service.TravelMediaService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object TravelTriggerExecutor {

    private const val PREFS = "travel_trigger_cooldown"
    private const val POLY_STATE = "travel_poly_inside"

    private fun channelId(ctx: Context) = "travel_geofence"

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                channelId(ctx),
                ctx.getString(R.string.travel_notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    fun canTrigger(context: Context, zone: TravelZone): Boolean {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = sp.getLong(zone.id, 0L)
        return System.currentTimeMillis() - last >= zone.cooldownMs
    }

    private fun markTriggered(context: Context, zone: TravelZone) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(zone.id, System.currentTimeMillis())
            .apply()
    }

    fun setPolygonInside(context: Context, zoneId: String, inside: Boolean) {
        context.getSharedPreferences(POLY_STATE, Context.MODE_PRIVATE).edit()
            .putBoolean(zoneId, inside)
            .apply()
    }

    fun isPolygonInside(context: Context, zoneId: String): Boolean =
        context.getSharedPreferences(POLY_STATE, Context.MODE_PRIVATE).getBoolean(zoneId, false)

    /**
     * Срабатывание при входе в зону (геозона или полигон).
     */
    fun onEnterZone(context: Context, zoneId: String) {
        val app = context.applicationContext
        val zone = runBlocking {
            TravelZoneRepository(app).snapshot().find { it.id == zoneId }
        } ?: return
        if (!zone.enabled) return
        if (!canTrigger(app, zone)) return
        markTriggered(app, zone)
        val polygonGlobalSound = runBlocking {
            TravelZoneRepository(app).polygonEntrySoundUri.first()
        }
        if (zone.kind == TravelZoneKind.POLYGON &&
            !polygonGlobalSound.isNullOrBlank() &&
            !(zone.action == TravelTriggerAction.PLAY_SOUND && !zone.mediaUri.isNullOrBlank())
        ) {
            runCatching {
                TravelMediaService.startSound(app, Uri.parse(polygonGlobalSound), zone.name)
            }
        }
        if (zone.kind == TravelZoneKind.POLYGON &&
            zone.action != TravelTriggerAction.PLAY_SOUND
        ) {
            val voiceOn = runBlocking { TravelZoneRepository(app).polygonVoiceHintsEnabled.first() }
            if (voiceOn) {
                val phrase = app.getString(R.string.travel_voice_enter_polygon, zone.name)
                TravelVoicePrompter.speak(app, phrase)
            }
        }
        ensureChannel(app)
        when (zone.action) {
            TravelTriggerAction.NOTIFICATION_ONLY ->
                showNotification(app, zone, withSound = false)
            TravelTriggerAction.BEEP -> {
                showNotification(app, zone, withSound = true)
                runCatching {
                    ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                        .startTone(ToneGenerator.TONE_PROP_BEEP, 400)
                }
            }
            TravelTriggerAction.PLAY_SOUND -> {
                showNotification(app, zone, withSound = false)
                val uri = zone.mediaUri?.let { Uri.parse(it) }
                if (uri != null) {
                    TravelMediaService.startSound(app, uri, zone.name)
                } else {
                    showNotification(app, zone, withSound = true)
                }
            }
            TravelTriggerAction.PLAY_VIDEO -> {
                showNotification(app, zone, withSound = false)
                val uri = zone.mediaUri?.let { Uri.parse(it) }
                if (uri != null) {
                    val open = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "video/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { app.startActivity(open) }
                }
            }
        }
    }

    private fun showNotification(context: Context, zone: TravelZone, withSound: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            zone.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(context, channelId(context))
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(context.getString(R.string.travel_notif_title))
            .setContentText(zone.name)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (withSound) b.setDefaults(NotificationCompat.DEFAULT_SOUND)
        nm.notify(zone.id.hashCode() and 0x7FFFFFFF, b.build())
    }
}
