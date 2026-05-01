package com.example.bible.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import com.example.bible.IncomingSmsSpeak
import com.example.bible.R
import com.example.bible.data.ExperimentSmsSpeakPrefs
import com.example.bible.data.SmsSpeechOverrideRepository
import com.example.bible.data.speechUtteranceForDigits
import com.example.bible.sms.SmsReactionExecutor
import com.example.bible.sms.SmsOutboundCrypto

/**
 * Озвучка входящих SMS при включённом флаге в разделе «Эксперимент» → SMS,
 * а также сценарии реакций ([SmsReactionExecutor]).
 * Объявлен в манифесте (`SMS_RECEIVED`, exported).
 */
class SmsSpeakBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val i = intent ?: return
        if (i.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(i) ?: return
        if (msgs.isEmpty()) return
        val appCtx = context.applicationContext
        val sm0 = msgs.firstOrNull() ?: return
        val rawAddress =
            sm0.displayOriginatingAddress?.trim()?.takeIf { it.isNotEmpty() }
                ?: sm0.originatingAddress?.trim()?.takeIf { it.isNotEmpty() }
        val fullBody =
            msgs.joinToString("") { sm ->
                sm.messageBody ?: sm.displayMessageBody.orEmpty()
            }
        val subId =
            i.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        SmsReactionExecutor.handleIncomingSms(appCtx, rawAddress, fullBody, subId)

        if (!ExperimentSmsSpeakPrefs.isSpeakIncomingEnabled(context)) return
        val utterance = IncomingSmsSpeakUtterance.build(context, i) ?: return
        IncomingSmsSpeak.speak(appCtx, utterance)
    }
}

object IncomingSmsSpeakUtterance {
    fun build(context: Context, intent: Intent): String? {
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return null
        if (msgs.isEmpty()) return null
        val smFirst = msgs.firstOrNull()
        val addrRaw =
            smFirst?.displayOriginatingAddress?.trim().orEmpty()
                .ifBlank { smFirst?.originatingAddress?.trim().orEmpty() }
        val digits = addrRaw.filter { it.isDigit() }
        val customUtterance =
            runCatching {
                SmsSpeechOverrideRepository(context.applicationContext).load().speechUtteranceForDigits(digits)
            }.getOrNull()
        val from =
            customUtterance?.takeIf { it.isNotBlank() }
                ?: SmsIncomingSenderDisplay.resolve(context.applicationContext, addrRaw).ifBlank {
                    context.getString(R.string.experiment_sms_unknown_sender)
                }
        val joined = msgs.joinToString("") { it.displayMessageBody.orEmpty() }
            .replace('\n', ' ')
            .trim()
        val rawBody = SmsOutboundCrypto.decryptInboundForDisplay(context.applicationContext, joined)
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
