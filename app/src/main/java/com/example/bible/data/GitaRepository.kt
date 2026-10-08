package com.example.bible.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GitaChapterSummary(
    val id: Int,
    val transliteration: String,
    val translation: String,
    val totalVerses: Int,
)

data class GitaVerse(
    val id: Int,
    val sanskrit: String,
    val transliteration: String,
    val translationEn: String,
    val translationRu: String,
)

data class GitaChapter(
    val summary: GitaChapterSummary,
    val verses: List<GitaVerse>,
)

data class GitaSearchHit(
    val chapterId: Int,
    val chapterTitle: String,
    val verseId: Int,
    val text: String,
)

class GitaRepository(private val context: Context) {
    fun loadIndex(): List<GitaChapterSummary> =
        try {
            val text = context.assets.open(INDEX).bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(parseSummary(o))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }

    fun loadChapter(id: Int): GitaChapter? {
        if (id !in 1..18) return null
        return try {
            val json = context.assets.open("$CHAPTERS/$id.json").bufferedReader().use { it.readText() }
            val o = JSONObject(json)
            val versesArr = o.optJSONArray("verses") ?: JSONArray()
            val russian = loadRussian(id)
            val verses = buildList {
                for (i in 0 until versesArr.length()) {
                    val v = versesArr.optJSONObject(i) ?: continue
                    add(
                        GitaVerse(
                            id = v.optInt("id"),
                            sanskrit = v.optString("sanskrit"),
                            transliteration = v.optString("transliteration"),
                            translationEn = v.optString("translationEn"),
                            translationRu = russian.getOrElse(i) { "" },
                        ),
                    )
                }
            }
            GitaChapter(parseSummary(o), verses)
        } catch (_: Exception) {
            null
        }
    }

    fun search(query: String, minLength: Int = 2, limit: Int = 80): List<GitaSearchHit> {
        val needle = normalize(query)
        if (needle.length < minLength) return emptyList()
        val out = ArrayList<GitaSearchHit>()
        for (chapter in allChapters()) {
            val title = chapter.summary.translation.ifBlank { chapter.summary.transliteration }
            for (verse in chapter.verses) {
                val russian = verse.translationRu.ifBlank { verse.translationEn }
                val haystack = normalize(russian + "\n" + verse.transliteration + "\n" + verse.sanskrit)
                if (!haystack.contains(needle)) continue
                out.add(
                    GitaSearchHit(
                        chapterId = chapter.summary.id,
                        chapterTitle = title,
                        verseId = verse.id,
                        text = russian,
                    ),
                )
                if (out.size >= limit) return out
            }
        }
        return out
    }

    private fun allChapters(): List<GitaChapter> {
        cache?.let { return it }
        val loaded = (1..18).mapNotNull { loadChapter(it) }
        cache = loaded
        return loaded
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace('ё', 'е')
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun loadRussian(chapterId: Int): List<String> =
        try {
            val text = context.assets.open("$RU/$chapterId.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            buildList {
                for (i in 0 until arr.length()) add(arr.optString(i))
            }
        } catch (_: Exception) {
            emptyList()
        }

    private fun parseSummary(o: JSONObject) = GitaChapterSummary(
        id = o.optInt("id"),
        transliteration = o.optString("transliteration"),
        translation = o.optString("translation"),
        totalVerses = o.optInt("total_verses"),
    )

    private var cache: List<GitaChapter>? = null

    private companion object {
        const val INDEX = "gita/index.json"
        const val CHAPTERS = "gita/chapters"
        const val RU = "gita/ru"
    }
}
