package com.example.bible.sms

import android.content.Context

/** Флаги шифрования SMS (читает BroadcastReceiver и фон без DataStore). */
object SmsCryptoPrefs {
    private const val PREFS = "sms_crypto_prefs"
    private const val KEY_ENCRYPT_OUTBOUND = "encrypt_outbound"
    private const val KEY_DECRYPT_INBOUND = "decrypt_inbound_preview"

    fun isEncryptOutboundEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENCRYPT_OUTBOUND, false)

    fun setEncryptOutboundEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENCRYPT_OUTBOUND, enabled)
            .apply()
    }

    fun isDecryptInboundEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DECRYPT_INBOUND, false)

    fun setDecryptInboundEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DECRYPT_INBOUND, enabled)
            .apply()
    }
}
