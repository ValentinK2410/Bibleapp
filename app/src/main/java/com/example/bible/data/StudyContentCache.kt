package com.example.bible.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Постоянный кэш материалов studybible.ru (комментарии, сравнения, ссылки, Стронг).
 * После первой загрузки при наличии сети данные доступны офлайн.
 */
class StudyContentCache(context: Context) {

    private val root: File = File(context.filesDir, "study_cache").apply { mkdirs() }

    private fun commentaryFile(slug: String, bookId: String, chapter: Int): File =
        File(File(root, "commentary/$slug"), "${bookId}_$chapter.txt")

    private fun verseCmpFile(bookId: String, chapter: Int, verse: Int): File =
        File(root, "verse_cmp/${bookId}_${chapter}_$verse.json")

    private fun crossFile(bookId: String, chapter: Int, verse: Int): File =
        File(root, "cross/${bookId}_${chapter}_$verse.json")

    private fun strongFile(bookId: String, chapter: Int, verse: Int): File =
        File(root, "strong/${bookId}_${chapter}_$verse.json")

    fun getCommentary(slug: String, bookId: String, chapter: Int): String? {
        val f = commentaryFile(slug, bookId, chapter)
        if (!f.exists() || f.length() == 0L) return null
        return try {
            f.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun putCommentary(slug: String, bookId: String, chapter: Int, text: String) {
        if (text.isBlank()) return
        try {
            val f = commentaryFile(slug, bookId, chapter)
            f.parentFile?.mkdirs()
            f.writeText(text, Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    fun getVerseComparisons(bookId: String, chapter: Int, verse: Int): List<VerseComparison>? {
        val f = verseCmpFile(bookId, chapter, verse)
        if (!f.exists()) return null
        return try {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                VerseComparison(
                    translationName = o.getString("n"),
                    text = o.getString("t"),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun putVerseComparisons(bookId: String, chapter: Int, verse: Int, list: List<VerseComparison>) {
        if (list.isEmpty()) return
        try {
            val arr = JSONArray()
            for (vc in list) {
                arr.put(
                    JSONObject().apply {
                        put("n", vc.translationName)
                        put("t", vc.text)
                    },
                )
            }
            val f = verseCmpFile(bookId, chapter, verse)
            f.parentFile?.mkdirs()
            f.writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    fun getCrossReferences(bookId: String, chapter: Int, verse: Int): List<CrossReference>? {
        val f = crossFile(bookId, chapter, verse)
        if (!f.exists()) return null
        return try {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CrossReference(
                    ref = o.getString("r"),
                    bookId = if (o.has("b") && !o.isNull("b")) o.getString("b") else null,
                    chapter = o.optInt("c", 0),
                    verse = o.optInt("v", 0),
                    text = o.optString("x", ""),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun putCrossReferences(bookId: String, chapter: Int, verse: Int, list: List<CrossReference>) {
        if (list.isEmpty()) return
        try {
            val arr = JSONArray()
            for (cr in list) {
                arr.put(
                    JSONObject().apply {
                        put("r", cr.ref)
                        if (cr.bookId != null) put("b", cr.bookId)
                        put("c", cr.chapter)
                        put("v", cr.verse)
                        if (cr.text.isNotEmpty()) put("x", cr.text)
                    },
                )
            }
            val f = crossFile(bookId, chapter, verse)
            f.parentFile?.mkdirs()
            f.writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    fun getStrongWords(bookId: String, chapter: Int, verse: Int): List<StrongWord>? {
        val f = strongFile(bookId, chapter, verse)
        if (!f.exists()) return null
        return try {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                StrongWord(
                    original = o.optString("o", ""),
                    transliteration = o.optString("tr", ""),
                    number = o.optString("n", ""),
                    meaning = o.optString("m", ""),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun putStrongWords(bookId: String, chapter: Int, verse: Int, list: List<StrongWord>) {
        if (list.isEmpty()) return
        try {
            val arr = JSONArray()
            for (w in list) {
                arr.put(
                    JSONObject().apply {
                        put("o", w.original)
                        put("tr", w.transliteration)
                        put("n", w.number)
                        put("m", w.meaning)
                    },
                )
            }
            val f = strongFile(bookId, chapter, verse)
            f.parentFile?.mkdirs()
            f.writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    /** Кэш комментария к стиху с API (экран «Комментарий» из меню стиха). */
    private fun verseCommentaryApiFile(
        translationCode: String,
        bookId: String,
        chapter: Int,
        verse: Int,
    ): File = File(root, "verse_commentary_api/${translationCode}_${bookId}_${chapter}_$verse.json")

    fun getVerseCommentaryApi(
        translationCode: String,
        bookId: String,
        chapter: Int,
        verse: Int,
    ): VerseCommentary? {
        val f = verseCommentaryApiFile(translationCode, bookId, chapter, verse)
        if (!f.exists() || f.length() == 0L) return null
        return try {
            val o = JSONObject(f.readText(Charsets.UTF_8))
            VerseCommentary(
                bookId = o.optString("bookId", bookId),
                chapter = o.optInt("chapter", chapter),
                verse = o.optInt("verse", verse),
                text = o.optString("text", ""),
                audioUrl = if (o.has("audioUrl") && !o.isNull("audioUrl")) o.optString("audioUrl") else null,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun putVerseCommentaryApi(
        translationCode: String,
        bookId: String,
        chapter: Int,
        verse: Int,
        commentary: VerseCommentary,
    ) {
        try {
            val o = JSONObject().apply {
                put("bookId", commentary.bookId)
                put("chapter", commentary.chapter)
                put("verse", commentary.verse)
                put("text", commentary.text)
                if (commentary.audioUrl != null) put("audioUrl", commentary.audioUrl)
            }
            val f = verseCommentaryApiFile(translationCode, bookId, chapter, verse)
            f.parentFile?.mkdirs()
            f.writeText(o.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }
}
