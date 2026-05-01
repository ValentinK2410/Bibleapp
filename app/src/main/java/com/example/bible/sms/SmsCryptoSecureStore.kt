package com.example.bible.sms

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Парольная фраза для SMS-крипто в покое шифруется Master Key (Keystore). */
object SmsCryptoSecureStore {

    private const val PREFS_FILE = "sms_crypto_secure_prefs"
    private const val KEY_PASSPHRASE = "shared_passphrase"

    private fun prefs(context: Context): SharedPreferences {
        val app = context.applicationContext
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            app,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun hasPassphrase(context: Context): Boolean =
        !getPassphrase(context).isNullOrBlank()

    fun getPassphrase(context: Context): String? =
        prefs(context).getString(KEY_PASSPHRASE, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setPassphrase(context: Context, passphrase: String) {
        val t = passphrase.trim()
        val ed = prefs(context).edit()
        if (t.isEmpty()) {
            ed.remove(KEY_PASSPHRASE)
        } else {
            ed.putString(KEY_PASSPHRASE, t)
        }
        ed.apply()
    }

    fun clearPassphrase(context: Context) {
        prefs(context).edit().remove(KEY_PASSPHRASE).apply()
    }
}
