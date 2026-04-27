package com.example.bible.data

import android.content.Context
import com.example.bible.data.db.StrongsEntryEntity
import com.example.bible.data.db.StudyDatabase
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

private fun StrongsEntryEntity.toStrongsEntry(): StrongsEntry =
    StrongsEntry(
        code = code,
        lemma = lemma,
        translit = translit,
        pronunciation = pronunciation,
        definition = definition,
        kjvUsage = kjvUsage,
        origin = origin,
    )

class StrongsDictionary(private val context: Context) {
    private val dao = StudyDatabase.getInstance(context.applicationContext).studyDao()
    private val cache: MutableMap<String, StrongsEntry> = mutableMapOf()
    private var jsonFallback: JSONObject? = null
    private var jsonFallbackTried = false

    private fun assetsJsonFallback(): JSONObject? {
        if (jsonFallbackTried) return jsonFallback
        jsonFallbackTried = true
        jsonFallback = try {
            JSONObject(
                context.assets.open("strongs_dictionary.json").bufferedReader().use { it.readText() },
            )
        } catch (_: Exception) {
            null
        }
        return jsonFallback
    }

    private fun entryFromJsonObject(code: String, obj: JSONObject): StrongsEntry =
        StrongsEntry(
            code = code,
            lemma = obj.optString("l", ""),
            translit = obj.optString("t", ""),
            pronunciation = obj.optString("p", ""),
            definition = obj.optString("d", ""),
            kjvUsage = obj.optString("k", ""),
            origin = obj.optString("o", ""),
        )

    fun lookup(strongCode: String?): StrongsEntry? {
        if (strongCode.isNullOrBlank()) return null

        cache[strongCode]?.let { return it }

        val normalized = normalizeStrongCode(strongCode)
        cache[normalized]?.let { return it }

        dao.getStrongsEntry(normalized)?.let { row ->
            val e = row.toStrongsEntry()
            cache[strongCode] = e
            cache[normalized] = e
            return e
        }

        val json = assetsJsonFallback() ?: return null
        val obj = json.optJSONObject(normalized) ?: return null
        val e = entryFromJsonObject(normalized, obj)
        cache[strongCode] = e
        cache[normalized] = e
        return e
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
