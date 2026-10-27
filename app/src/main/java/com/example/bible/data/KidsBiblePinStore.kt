package com.example.bible.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Хеш PIN и прогресс «Детской Библии» лежат в зашифрованных настройках, не открытым текстом. */
internal object KidsBiblePinStore {
    private const val FILE = "kids_bible_pin"
    private const val SALT = "salt"
    private const val PIN = "pin_hash"
    private const val HINT = "hint"
    private const val HINT_HASH = "hint_hash"
    private const val FAILS = "fails"
    private const val LOCKED = "locked_until"
    private const val READ = "read_ids"

    private fun prefs(context: Context): SharedPreferences {
        val app = context.applicationContext
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            app,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun record(context: Context): KidsBiblePin.Record? {
        val p = prefs(context)
        val salt = p.getString(SALT, null) ?: return null
        val hash = p.getString(PIN, null) ?: return null
        if (salt.isBlank() || hash.isBlank()) return null
        return KidsBiblePin.Record(
            salt = salt,
            pinHash = hash,
            hint = p.getString(HINT, "").orEmpty(),
            hintHash = p.getString(HINT_HASH, "").orEmpty(),
        )
    }

    fun isSet(context: Context): Boolean = record(context) != null

    fun gate(context: Context): KidsBiblePin.Gate {
        val p = prefs(context)
        return KidsBiblePin.Gate(
            fails = p.getInt(FAILS, 0),
            lockedUntilMs = p.getLong(LOCKED, 0L),
        )
    }

    fun saveNew(context: Context, pin: String, hint: String, answer: String) {
        val created = KidsBiblePin.create(pin, hint, answer)
        prefs(context).edit()
            .putString(SALT, created.salt)
            .putString(PIN, created.pinHash)
            .putString(HINT, created.hint)
            .putString(HINT_HASH, created.hintHash)
            .putInt(FAILS, 0)
            .putLong(LOCKED, 0L)
            .apply()
    }

    fun noteFail(context: Context, now: Long): KidsBiblePin.Gate {
        val next = KidsBiblePin.fail(gate(context), now)
        prefs(context).edit().putInt(FAILS, next.fails).putLong(LOCKED, next.lockedUntilMs).apply()
        return next
    }

    fun noteSuccess(context: Context) {
        prefs(context).edit().putInt(FAILS, 0).putLong(LOCKED, 0L).apply()
    }

    fun readIds(context: Context): Set<String> =
        prefs(context).getString(READ, "").orEmpty().split(',').filter { it.isNotBlank() }.toSet()

    fun markRead(context: Context, storyId: String) {
        val next = readIds(context) + storyId
        prefs(context).edit().putString(READ, next.joinToString(",")).apply()
    }

    /** Подсказка верна: код и отметки «прочитано» этого раздела стираются. Ребёнок настройки не видит. */
    fun resetSection(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
