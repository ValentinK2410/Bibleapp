package com.example.bible.sms

import android.content.Context
import android.util.Log

private const val TAG = "SmsOutboundCrypto"

object SmsOutboundCrypto {

    fun wrapOutboundBody(context: Context, plainBody: String): String {
        val plain = plainBody.trim()
        if (plain.isEmpty()) return plain
        if (!SmsCryptoPrefs.isEncryptOutboundEnabled(context)) return plain
        val pass = SmsCryptoSecureStore.getPassphrase(context)
        if (pass.isNullOrEmpty()) {
            Log.w(TAG, "encrypt requested but no passphrase")
            return plain
        }
        return runCatching { SmsPayloadCipher.encrypt(pass, plain) }
            .getOrElse { e ->
                Log.w(TAG, "encrypt failed", e)
                plain
            }
    }

    /** Для списка входящих и озвучки: показать расшифрованное, если включено и получилось. */
    fun decryptInboundForDisplay(context: Context, rawBody: String): String {
        if (!SmsCryptoPrefs.isDecryptInboundEnabled(context)) return rawBody
        val pass = SmsCryptoSecureStore.getPassphrase(context)
        if (pass.isNullOrEmpty() || !SmsPayloadCipher.looksEncrypted(rawBody)) return rawBody
        return SmsPayloadCipher.decrypt(pass, rawBody) ?: rawBody
    }
}
