package com.example.bible.data

import android.content.Context
import android.telephony.SubscriptionManager

/** Выбранная SIM для отправки координат по SMS из «Мои путешествия». */
object TravelSmsSharePrefs {
    private const val PREFS = "travel_sms_share_prefs"
    private const val KEY_SUB_ID = "share_coords_subscription_id"

    fun getShareSubscriptionId(context: Context): Int {
        val v = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SUB_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        return v
    }

    fun setShareSubscriptionId(context: Context, subscriptionId: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SUB_ID, subscriptionId)
            .apply()
    }
}
