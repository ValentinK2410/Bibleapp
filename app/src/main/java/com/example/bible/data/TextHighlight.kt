package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Подсветка фрагмента текста внутри одного стиха.
 * [startOffset] включительно, [endOffset] исключительно (как [String.substring]).
 */
data class TextHighlight(
    val translation: TranslationId,
    val bookId: String,
    val chapter: Int,
    val verse: Int,
    val startOffset: Int,
    val endOffset: Int,
    val isBackground: Boolean,
    val colorArgb: Long,
    val underline: Boolean = false,
) {
    fun matchesVerse(ref: VerseRef): Boolean =
        translation == ref.translation && bookId == ref.bookId && chapter == ref.chapter && verse == ref.verse

    fun overlapsOffsets(start: Int, end: Int): Boolean =
        startOffset < end && endOffset > start

    fun toJsonObject(): JSONObject =
        JSONObject().apply {
            put("t", translation.code)
            put("b", bookId)
            put("c", chapter)
            put("v", verse)
            put("s", startOffset)
            put("e", endOffset)
            put("bg", isBackground)
            put("col", colorArgb.toString())
            if (underline) put("u", true)
        }

    companion object {
        fun fromJsonObject(o: JSONObject): TextHighlight =
            TextHighlight(
                translation = TranslationId.fromCode(o.getString("t")),
                bookId = o.getString("b"),
                chapter = o.getInt("c"),
                verse = o.getInt("v"),
                startOffset = o.getInt("s"),
                endOffset = o.getInt("e"),
                isBackground = o.getBoolean("bg"),
                colorArgb = o.getString("col").toLong(),
                underline = o.optBoolean("u", false),
            )

        fun parseList(json: String): List<TextHighlight> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        add(fromJsonObject(arr.getJSONObject(i)))
                    }
                }
            }.getOrElse { emptyList() }
        }

        fun toJsonArray(list: List<TextHighlight>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJsonObject()) }
            return arr.toString()
        }
    }
}
