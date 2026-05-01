package com.example.bible.sms

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log

private const val TAG = "SmsSendHelper"

fun smsManagerForSubscription(context: Context, subscriptionId: Int): SmsManager {
    if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
    ) {
        runCatching {
            return SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }.onFailure { Log.w(TAG, "SmsManager for sub=$subscriptionId", it) }
    }
    context.getSystemService(SmsManager::class.java)?.let { return it }
    @Suppress("DEPRECATION")
    return SmsManager.getDefault()
}

@SuppressLint("MissingPermission")
fun sendSmsMultipart(
    context: Context,
    subscriptionId: Int,
    destinationDigits: String,
    body: String,
) {
    val dest = destinationDigits.trim()
    val text = body
    if (dest.isEmpty() || text.isEmpty()) return
    val mgr = smsManagerForSubscription(context, subscriptionId)
    val parts = mgr.divideMessage(text)
    runCatching {
        if (parts.size <= 1) {
            mgr.sendTextMessage(dest, null, text, null, null)
        } else {
            mgr.sendMultipartTextMessage(dest, null, parts, null, null)
        }
    }.onFailure { Log.w(TAG, "send sms to=$dest parts=${parts.size}", it) }
}
