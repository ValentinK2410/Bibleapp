package com.example.bible.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.bible.data.AppMediaButtonSession

/** Доставка [Intent.ACTION_MEDIA_BUTTON] в активную [androidx.media.session.MediaSessionCompat]. */
class AppMediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            AppMediaButtonSession.onMediaButtonIntent(intent)
        }
    }
}
