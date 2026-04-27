package com.example.bible.data

import android.content.Context
import com.example.bible.data.db.StudyChapterCommentaryEntity
import com.example.bible.data.db.StudyDatabase
import com.example.bible.data.db.StudyVerseBlobEntity
import com.example.bible.data.db.StudyVerseBlobKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Материалы «Изучение» и кэш комментариев API — в SQLite ([StudyDatabase]).
 * Старый каталог [study_cache] подхватывается лениво при get* и однократным фоновым проходом из [BibleApplication].
 */
class StudyContentCache(context: Context) {

    private val appContext = context.applicationContext
    private val db: StudyDatabase = StudyDatabase.getInstance(appContext)
    private val dao = db.studyDao()

    private val legacyRoot: File = File(appContext.filesDir, "study_cache")

    private fun commentaryFile(slug: String, bookId: String, chapter: Int): File =
        File(File(legacyRoot, "commentary/$slug"), "${bookId}_$chapter.txt")

    private fun verseCmpFile(bookId: String, chapter: Int, verse: Int): File =
        File(legacyRoot, "verse_cmp/${bookId}_${chapter}_$verse.json")

    private fun crossFile(bookId: String, chapter: Int, verse: Int): File =
        File(legacyRoot, "cross/${bookId}_${chapter}_$verse.json")

    private fun strongFile(bookId: String, chapter: Int, verse: Int): File =
        File(legacyRoot, "strong/${bookId}_${chapter}_$verse.json")

    private fun verseCommentaryApiFile(
        translationCode: String,
        bookId: String,
        chapter: Int,
        verse: Int,
    ): File = File(legacyRoot, "verse_commentary_api/${translationCode}_${bookId}_${chapter}_$verse.json")

    fun getCommentary(slug: String, bookId: String, chapter: Int): String? {
        dao.getChapterCommentary(slug, bookId, chapter)?.let { return it }
        val f = commentaryFile(slug, bookId, chapter)
        if (!f.isFile || f.length() == 0L) return null
        val text = try {
            f.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        if (text.isBlank()) return null
        dao.upsertChapterCommentary(
            StudyChapterCommentaryEntity(slug = slug, bookId = bookId, chapter = chapter, text = text),
        )
        return text
    }

    fun putCommentary(slug: String, bookId: String, chapter: Int, text: String) {
        if (text.isBlank()) return
        dao.upsertChapterCommentary(
            StudyChapterCommentaryEntity(slug = slug, bookId = bookId, chapter = chapter, text = text),
        )
    }

    fun getVerseComparisons(bookId: String, chapter: Int, verse: Int): List<VerseComparison>? {
        dao.getVerseBlobPayload(
            StudyVerseBlobKind.VERSE_COMPARISON,
            "",
            bookId,
            chapter,
            verse,
        )?.let { return parseVerseComparisonsJson(it) }
        val f = verseCmpFile(bookId, chapter, verse)
        if (!f.isFile) return null
        val p = try {
            f.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        if (p.isBlank()) return null
        dao.upsertVerseBlob(
            StudyVerseBlobEntity(
                kind = StudyVerseBlobKind.VERSE_COMPARISON,
                translationCode = "",
                bookId = bookId,
                chapter = chapter,
                verse = verse,
                payload = p,
            ),
        )
        return parseVerseComparisonsJson(p)
    }

    fun putVerseComparisons(bookId: String, chapter: Int, verse: Int, list: List<VerseComparison>) {
        if (list.isEmpty()) return
        val arr = JSONArray()
        for (vc in list) {
            arr.put(
                JSONObject().apply {
                    put("n", vc.translationName)
                    put("t", vc.text)
                },
            )
        }
        val payload = arr.toString()
        dao.upsertVerseBlob(
            StudyVerseBlobEntity(
                kind = StudyVerseBlobKind.VERSE_COMPARISON,
                translationCode = "",
                bookId = bookId,
                chapter = chapter,
                verse = verse,
                payload = payload,
            ),
        )
    }

    fun getCrossReferences(bookId: String, chapter: Int, verse: Int): List<CrossReference>? {
        dao.getVerseBlobPayload(
            StudyVerseBlobKind.CROSS_REFERENCE,
            "",
            bookId,
            chapter,
            verse,
        )?.let { return parseCrossReferencesJson(it) }
        val f = crossFile(bookId, chapter, verse)
        if (!f.isFile) return null
        val p = try {
            f.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        if (p.isBlank()) return null
        dao.upsertVerseBlob(
            StudyVerseBlobEntity(
                kind = StudyVerseBlobKind.CROSS_REFERENCE,
                translationCode = "",
                bookId = bookId,
                chapter = chapter,
                verse = verse,
                payload = p,
            ),
        )
        return parseCrossReferencesJson(p)
    }

    fun putCrossReferences(bookId: String, chapter: Int, verse: Int, list: List<CrossReference>) {
        if (list.isEmpty()) return
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
        val payload = arr.toString()
        dao.upsertVerseBlob(
            StudyVerseBlobEntity(
                kind = StudyVerseBlobKind.CROSS_REFERENCE,
                translationCode = "",
                bookId = bookId,
                chapter = chapter,
                verse = verse,
                payload = payload,
            ),
        )
    }

    fun getStrongWords(bookId: String, chapter: Int, verse: Int): List<StrongWord>? {
        dao.getVerseBlobPayload(
            StudyVerseBlobKind.STRONG_WORDS,
            "",
            bookId,
            chapter,
            verse,
        )?.let { return parseStrongWordsJson(it) }
        val f = strongFile(bookId, chapter, verse)
        if (!f.isFile) return null
        val p = try {
            f.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        if (p.isBlank()) return null
        dao.upsertVerseBlob(
            StudyVerseBlobEntity(
                kind = StudyVerseBlobKind.STRONG_WORDS,
                translationCode = "",
                bookId = bookId,
                chapter = chapter,
                verse = verse,
                payload = p,
            ),
        )
        return parseStrongWordsJson(p)
    }

    fun putStrongWords(bookId: String, chapter: Int, verse: Int, list: List<StrongWord>) {
        if (list.isEmpty()) return
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
        val payload = arr.toString()
        dao.upsertVerseBlob(
            StudyVerseBlobEntity(
                kind = StudyVerseBlobKind.STRONG_WORDS,
                translationCode = "",
                bookId = bookId,
                chapter = chapter,
                verse = verse,
                payload = payload,
            ),
        )
    }

    fun getVerseCommentaryApi(
        translationCode: String,
        bookId: String,
        chapter: Int,
        verse: Int,
    ): VerseCommentary? {
        dao.getVerseBlobPayload(
            StudyVerseBlobKind.VERSE_COMMENTARY_API,
            translationCode,
            bookId,
            chapter,
            verse,
        )?.let { return parseVerseCommentaryApiJson(it, bookId, chapter, verse) }
        val f = verseCommentaryApiFile(translationCode, bookId, chapter, verse)
        if (!f.isFile || f.length() == 0L) return null
        val p = try {
            f.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        if (p.isBlank()) return null
        dao.upsertVerseBlob(
            StudyVerseBlobEntity(
                kind = StudyVerseBlobKind.VERSE_COMMENTARY_API,
                translationCode = translationCode,
                bookId = bookId,
                chapter = chapter,
                verse = verse,
                payload = p,
            ),
        )
        return parseVerseCommentaryApiJson(p, bookId, chapter, verse)
    }

    fun putVerseCommentaryApi(
        translationCode: String,
        bookId: String,
        chapter: Int,
        verse: Int,
        commentary: VerseCommentary,
    ) {
        val o = JSONObject().apply {
            put("bookId", commentary.bookId)
            put("chapter", commentary.chapter)
            put("verse", commentary.verse)
            put("text", commentary.text)
            if (commentary.audioUrl != null) put("audioUrl", commentary.audioUrl)
        }
        val payload = o.toString()
        dao.upsertVerseBlob(
            StudyVerseBlobEntity(
                kind = StudyVerseBlobKind.VERSE_COMMENTARY_API,
                translationCode = translationCode,
                bookId = bookId,
                chapter = chapter,
                verse = verse,
                payload = payload,
            ),
        )
    }

    private fun parseVerseComparisonsJson(json: String): List<VerseComparison>? =
        try {
            val arr = JSONArray(json)
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

    private fun parseCrossReferencesJson(json: String): List<CrossReference>? =
        try {
            val arr = JSONArray(json)
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

    private fun parseStrongWordsJson(json: String): List<StrongWord>? =
        try {
            val arr = JSONArray(json)
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

    private fun parseVerseCommentaryApiJson(
        json: String,
        bookId: String,
        chapter: Int,
        verse: Int,
    ): VerseCommentary? =
        try {
            val o = JSONObject(json)
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
