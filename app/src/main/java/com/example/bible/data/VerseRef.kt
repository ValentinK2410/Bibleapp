package com.example.bible.data

data class VerseRef(
    val translation: TranslationId,
    val bookId: String,
    val chapter: Int,
    val verse: Int,
) {
    fun toKey(): String = "${translation.code}|$bookId|$chapter|$verse"

    companion object {
        fun fromKey(key: String): VerseRef? {
            val p = key.split('|')
            if (p.size != 4) return null
            val ch = p[2].toIntOrNull() ?: return null
            val v = p[3].toIntOrNull() ?: return null
            return VerseRef(TranslationId.fromCode(p[0]), p[1], ch, v)
        }
    }
}

data class SearchHit(
    val translation: TranslationId,
    val bookId: String,
    val bookName: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
)
