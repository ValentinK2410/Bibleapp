package com.example.bible.data

import android.content.Context
import com.example.bible.sms.SmsCryptoSecureStore
import com.example.bible.sms.SmsPayloadCipher
import org.json.JSONObject

/**
 * При входящей SMS с полезной нагрузкой координат (как из «Мои путешествия» → SMS)
 * обновляет поля широты/долготы у контакта приложения, если нормализованный номер совпадает с отправителем.
 */
object SmsIncomingGeoContactsSync {

    fun onIncomingSms(context: Context, originatingAddressRaw: String?, messageBodyFull: String) {
        val app = context.applicationContext
        val plain = resolvePlaintext(app, messageBodyFull)
        val coords = parseGeoPayload(plain) ?: return
        val (lat, lon) = coords
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return

        val repo = ContactsRepository(app)
        val list = repo.load()
        if (list.isEmpty()) return

        var changed = false
        val updated = list.map { contact ->
            if (!phoneMatchesSender(originatingAddressRaw, contact.phone)) {
                contact
            } else {
                changed = true
                contact.copy(latitude = lat, longitude = lon)
            }
        }
        if (changed) {
            repo.save(updated)
        }
    }

    private fun resolvePlaintext(context: Context, rawBody: String): String {
        val t = rawBody.trim()
        if (!SmsPayloadCipher.looksEncrypted(t)) return t
        val pass = SmsCryptoSecureStore.getPassphrase(context) ?: return t
        return SmsPayloadCipher.decrypt(pass, t) ?: t
    }

    private fun parseGeoPayload(plain: String): Pair<Double, Double>? {
        val trimmed = plain.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("{").not()) return null
        val j = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        val type = j.optString("t", "").trim()
        if (type != "geo") return null
        val lat = when {
            j.has("lat") -> j.optDouble("lat", Double.NaN)
            j.has("latitude") -> j.optDouble("latitude", Double.NaN)
            else -> Double.NaN
        }
        val lon = when {
            j.has("lon") -> j.optDouble("lon", Double.NaN)
            j.has("longitude") -> j.optDouble("longitude", Double.NaN)
            j.has("lng") -> j.optDouble("lng", Double.NaN)
            else -> Double.NaN
        }
        if (!lat.isFinite() || !lon.isFinite()) return null
        return lat to lon
    }

    /** Сравнение номера отправителя SMS и строки телефона в контакте (РФ и смежные случаи). */
    internal fun phoneMatchesSender(originatingAddressRaw: String?, contactPhoneRaw: String): Boolean {
        val a = canonicalDigits(originatingAddressRaw.orEmpty())
        val b = canonicalDigits(contactPhoneRaw)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val ta = a.takeLast(minOf(10, a.length))
        val tb = b.takeLast(minOf(10, b.length))
        return ta.length >= 10 && tb.length >= 10 && ta == tb
    }

    private fun canonicalDigits(raw: String): String =
        raw.normalizeRussianOutboundPhoneDigits().normalizeSmsDigits()
}
