package com.example.bible.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class QuranRepository(private val context: Context) {

    fun loadIndex(): List<QuranSurahSummary>? =
        try {
            val text = context.assets.open(INDEX_PATH).bufferedReader().use { it.readText() }
            parseIndexArray(JSONArray(text))
        } catch (_: Exception) {
            null
        }

    fun loadSurah(surahNumber: Int): QuranSurahContent? {
        if (surahNumber !in 1..114) return null
        return try {
            val path = "$CHAPTERS_DIR/$surahNumber.json"
            val json = context.assets.open(path).bufferedReader().use { it.readText() }
            parseSurah(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Поиск подстроки в русском переводе (без учёта регистра, ё ≈ е, любая часть слова).
     * Обходит все суры; при пустом или коротком запросе возвращает пустой список.
     */
    fun searchTranslationRu(query: String, minLength: Int = 1): List<QuranSearchHit> {
        val q = normalizeRuQuery(query)
        if (q.length < minLength) return emptyList()
        val idx = loadIndex() ?: return emptyList()
        val nameBySurah = idx.associate { it.number to it.nameRussian }
        val out = ArrayList<QuranSearchHit>(48)
        for (n in 1..114) {
            val surah = loadSurah(n) ?: continue
            val surahName = nameBySurah[n] ?: surah.summary.nameRussian
            for (v in surah.verses) {
                if (normalizeRuQuery(v.translationRu).contains(q)) {
                    out.add(
                        QuranSearchHit(
                            surahNumber = n,
                            surahNameRu = surahName,
                            verseNumber = v.number,
                            translationRu = v.translationRu,
                        ),
                    )
                }
            }
        }
        return out
    }

    fun normalizeRuQuery(s: String): String =
        s.lowercase()
            .replace('ё', 'е')
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Диапазоны в исходном [translationRu], где вхождение совпадает с логикой [searchTranslationRu]
     * (фрагменты слова, регистр, ё/е, несколько слов через пробел).
     */
    fun highlightRangesInTranslation(translationRu: String, queryRaw: String): List<IntRange> {
        val rx = ruTranslationSearchRegex(normalizeRuQuery(queryRaw)) ?: return emptyList()
        return rx.findAll(translationRu).map { it.range }.toList()
    }

    /** Regex по уже нормализованному запросу (нижний регистр, е вместо ё, пробелы схлопнуты). */
    private fun ruTranslationSearchRegex(normalizedQuery: String): Regex? {
        if (normalizedQuery.isEmpty()) return null
        val parts = normalizedQuery.split(' ').filter { it.isNotEmpty() }
        val segment = parts.joinToString("\\s+") { part ->
            part.toCharArray().joinToString("") { ch ->
                when (ch) {
                    'е' -> "(?:е|ё)"
                    else -> Regex.escape(ch.toString())
                }
            }
        }
        return Regex(segment, RegexOption.IGNORE_CASE)
    }

    private fun parseIndexArray(arr: JSONArray): List<QuranSurahSummary> =
        (0 until arr.length()).map { i ->
            parseSummary(arr.getJSONObject(i))
        }

    private fun parseSummary(o: JSONObject): QuranSurahSummary =
        QuranSurahSummary(
            number = o.getInt("id"),
            nameArabic = o.optString("name", ""),
            nameTransliteration = o.optString("transliteration", ""),
            nameRussian = o.optString("translation", ""),
            type = o.optString("type", ""),
            totalVerses = o.optInt("total_verses", 0),
        )

    private fun parseSurah(root: JSONObject): QuranSurahContent {
        val summary = parseSummary(root)
        val versesArr = root.getJSONArray("verses")
        val verses = (0 until versesArr.length()).map { vi ->
            val v = versesArr.getJSONObject(vi)
            QuranVerse(
                number = v.getInt("id"),
                arabic = v.optString("text", ""),
                transliteration = v.optString("transliteration", ""),
                translationRu = v.optString("translation", ""),
            )
        }
        return QuranSurahContent(summary, verses)
    }

    companion object {
        private const val INDEX_PATH = "quran/index.json"
        private const val CHAPTERS_DIR = "quran/chapters"
    }
}
