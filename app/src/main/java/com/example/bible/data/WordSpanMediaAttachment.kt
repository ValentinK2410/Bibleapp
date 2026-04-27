package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Медиа (аудио, картинка, видео), привязанное к выделенному фрагменту текста внутри стиха.
 * Не смешивается с правилами лексикона подсветки.
 */
data class WordSpanMediaAttachment(
    val id: String,
    val translation: TranslationId,
    val bookId: String,
    val chapter: Int,
    val verse: Int,
    val startOffset: Int,
    val endOffset: Int,
    val media: LexiconMediaRefs = LexiconMediaRefs(),
) {
    fun matchesVerse(ref: VerseRef): Boolean =
        translation == ref.translation && bookId == ref.bookId && chapter == ref.chapter && verse == ref.verse

    fun containsOffset(offset: Int): Boolean =
        offset >= startOffset && offset < endOffset

    fun overlapsOffsets(start: Int, end: Int): Boolean =
        startOffset < end && endOffset > start

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("t", translation.code)
        put("b", bookId)
        put("c", chapter)
        put("v", verse)
        put("s", startOffset)
        put("e", endOffset)
        if (media.hasAny()) put("media", media.toJson())
    }

    companion object {
        fun fromJsonObject(o: JSONObject): WordSpanMediaAttachment? = try {
            val mediaJo = if (o.has("media")) o.getJSONObject("media") else null
            WordSpanMediaAttachment(
                id = o.getString("id"),
                translation = TranslationId.fromCode(o.getString("t")),
                bookId = o.getString("b"),
                chapter = o.getInt("c"),
                verse = o.getInt("v"),
                startOffset = o.getInt("s"),
                endOffset = o.getInt("e"),
                media = LexiconMediaRefs.fromJson(mediaJo),
            )
        } catch (_: Exception) {
            null
        }

        fun parseList(json: String): List<WordSpanMediaAttachment> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        fromJsonObject(arr.getJSONObject(i))?.let { add(it) }
                    }
                }
            }.getOrElse { emptyList() }
        }

        fun toJsonArray(list: List<WordSpanMediaAttachment>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJsonObject()) }
            return arr.toString()
        }
    }
}

fun List<WordSpanMediaAttachment>.findForTap(ref: VerseRef, charOffset: Int): WordSpanMediaAttachment? =
    filter { it.matchesVerse(ref) && it.containsOffset(charOffset) }
        .maxWithOrNull(compareBy<WordSpanMediaAttachment> { it.endOffset - it.startOffset }.thenBy { it.id })

fun newWordSpanMediaId(): String = "wsm_" + UUID.randomUUID().toString().replace("-", "")
