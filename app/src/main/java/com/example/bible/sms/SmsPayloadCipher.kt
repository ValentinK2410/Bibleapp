package com.example.bible.sms

import android.util.Base64
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Общий секрет — парольная фраза на обоих телефонах. Ключ: PBKDF2-HMAC-SHA256 (фиксированная соль в приложении),
 * шифротекст: AES-256-GCM (случайный IV на сообщение). Перехватчик SMS без пароля не восстанавливает текст.
 */
object SmsPayloadCipher {

    const val PREFIX = "BTLSMS1:"

    private const val PBKDF2_ITERATIONS = 120_000
    private const val AES_KEY_BITS = 256
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val FIXED_PBKDF2_SALT =
        "BibleSqliteSmsCrypto.v1".toByteArray(Charsets.UTF_8)

    fun looksEncrypted(body: String): Boolean = body.startsWith(PREFIX)

    fun encrypt(passphrase: String, plaintextUtf8: String): String {
        val trimmedPass = passphrase.trim()
        if (trimmedPass.isEmpty()) throw IllegalArgumentException("empty passphrase")
        val chars = trimmedPass.toCharArray()
        try {
            val key = deriveKey(chars)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val ct = cipher.doFinal(plaintextUtf8.toByteArray(Charsets.UTF_8))
            val combined = iv + ct
            return PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } finally {
            chars.fill('\u0000')
        }
    }

    fun decrypt(passphrase: String, ciphertext: String): String? {
        val trimmedPass = passphrase.trim()
        if (trimmedPass.isEmpty() || !looksEncrypted(ciphertext)) return null
        val b64 = ciphertext.removePrefix(PREFIX).trim()
        if (b64.isEmpty()) return null
        val chars = trimmedPass.toCharArray()
        return try {
            val raw = Base64.decode(b64, Base64.NO_WRAP)
            if (raw.size <= GCM_IV_BYTES) return null
            val iv = raw.copyOfRange(0, GCM_IV_BYTES)
            val enc = raw.copyOfRange(GCM_IV_BYTES, raw.size)
            val key = deriveKey(chars)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(enc), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun deriveKey(passphraseChars: CharArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphraseChars, FIXED_PBKDF2_SALT, PBKDF2_ITERATIONS, AES_KEY_BITS)
        val tmp = factory.generateSecret(spec).encoded
        return SecretKeySpec(tmp, "AES")
    }
}
