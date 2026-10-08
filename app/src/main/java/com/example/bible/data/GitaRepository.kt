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
)

data class GitaChapter(
    val summary: GitaChapterSummary,
    val verses: List<GitaVerse>,
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
            val verses = buildList {
                for (i in 0 until versesArr.length()) {
                    val v = versesArr.optJSONObject(i) ?: continue
                    add(
                        GitaVerse(
                            id = v.optInt("id"),
                            sanskrit = v.optString("sanskrit"),
                            transliteration = v.optString("transliteration"),
                            translationEn = v.optString("translationEn"),
                        ),
                    )
                }
            }
            GitaChapter(parseSummary(o), verses)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSummary(o: JSONObject) = GitaChapterSummary(
        id = o.optInt("id"),
        transliteration = o.optString("transliteration"),
        translation = o.optString("translation"),
        totalVerses = o.optInt("total_verses"),
    )

    private companion object {
        const val INDEX = "gita/index.json"
        const val CHAPTERS = "gita/chapters"
    }
}
