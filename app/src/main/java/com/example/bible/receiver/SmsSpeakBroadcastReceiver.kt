package com.example.bible.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.bible.IncomingSmsSpeak
import com.example.bible.R
import com.example.bible.data.ExperimentSmsSpeakPrefs

/**
 * Озвучка входящих SMS при включённом флаге в разделе «Эксперимент» → SMS.
 * Регистрируется из [com.example.bible.BibleApplication].
 */
class SmsSpeakBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!ExperimentSmsSpeakPrefs.isSpeakIncomingEnabled(context)) return
        val utterance = IncomingSmsSpeakUtterance.build(context, intent) ?: return
        IncomingSmsSpeak.speak(context.applicationContext, utterance)
    }
}

object IncomingSmsSpeakUtterance {
    fun build(context: Context, intent: Intent): String? {
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return null
        if (msgs.isEmpty()) return null
        val from = msgs.firstOrNull()?.displayOriginatingAddress?.trim().orEmpty()
            .ifBlank { context.getString(R.string.experiment_sms_unknown_sender) }
        val rawBody = msgs.joinToString("") { it.displayMessageBody.orEmpty() }
            .replace('\n', ' ')
            .trim()
        val body = if (rawBody.length > IncomingSmsSpeak.MAX_UTTERANCE_CHARS) {
            rawBody.take(IncomingSmsSpeak.MAX_UTTERANCE_CHARS) + "…"
        } else {
            rawBody
        }
        if (body.isEmpty()) {
            return context.getString(R.string.experiment_sms_speak_body_empty_fmt, from)
        }
        return context.getString(R.string.experiment_sms_speak_utterance_fmt, from, body)
    }
}
