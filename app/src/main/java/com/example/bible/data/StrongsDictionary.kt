package com.example.bible.data

import android.content.Context
import org.json.JSONObject

data class StrongsEntry(
    val code: String,
    /** Лемма в оригинале (иврит или греческий текст). */
    val lemma: String,
    val translit: String,
    val pronunciation: String,
    /** Краткое значение на русском (из определения Стронга). */
    val definition: String,
    /** Расширенные оттенки; в данных часто встречается «шум» от автоперевода KJV — см. [shouldShowKjvUsageRu]. */
    val kjvUsage: String,
    val origin: String,
) {
    /** Показывать ли блок k: только если в строке заметная доля кириллицы (иначе это обычно обломки англ.). */
    fun shouldShowKjvUsageRu(): Boolean {
        val k = kjvUsage
        if (k.isBlank()) return false
        var cyr = 0
        var letters = 0
        for (ch in k) {
            when {
                ch in '\u0400'..'\u04FF' || ch == 'ё' || ch == 'Ё' -> {
                    cyr++
                    letters++
                }
                ch.isLetter() -> letters++
            }
        }
        if (letters < 4) return cyr >= 2
        return cyr.toDouble() / letters >= 0.28
    }
}

class StrongsDictionary(private val context: Context) {
    private var cache: MutableMap<String, StrongsEntry> = mutableMapOf()
    private var loaded = false
    private var rawJson: JSONObject? = null

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            val text = context.assets.open("strongs_dictionary.json")
                .bufferedReader().use { it.readText() }
            rawJson = JSONObject(text)
        } catch (_: Exception) {
            rawJson = null
        }
    }

    fun lookup(strongCode: String?): StrongsEntry? {
        if (strongCode.isNullOrBlank()) return null

        cache[strongCode]?.let { return it }

        ensureLoaded()
        val json = rawJson ?: return null

        val normalized = normalizeStrongCode(strongCode)
        val obj = json.optJSONObject(normalized) ?: return null

        val entry = StrongsEntry(
            code = normalized,
            lemma = obj.optString("l", ""),
            translit = obj.optString("t", ""),
            pronunciation = obj.optString("p", ""),
            definition = obj.optString("d", ""),
            kjvUsage = obj.optString("k", ""),
            origin = obj.optString("o", ""),
        )
        cache[strongCode] = entry
        cache[normalized] = entry
        return entry
    }

    companion object {
        /** H1697 → H1697, G26 → G0026 (как в JSON). */
        fun normalizeStrongCode(code: String): String {
            val t = code.trim().uppercase()
            val prefix = t.firstOrNull() ?: return code
            if (prefix != 'G' && prefix != 'H') return t
            val num = t.drop(1).trimStart('0').ifEmpty { "0" }
            return "$prefix${num.padStart(4, '0')}"
        }

        /**
         * Разбор ввода пользователя: «H1697», «g26», «G 0026».
         * @return нормализованный код или null
         */
        fun parseUserInput(raw: String): String? {
            val s = raw.trim().uppercase().replace(" ", "")
            if (s.isEmpty()) return null
            val m = Regex("^([GH])(\\d{1,5})$").find(s) ?: return null
            return normalizeStrongCode(m.groupValues[1] + m.groupValues[2])
        }
    }
}
