package com.example.bible.data

import java.text.Normalizer

/**
 * Русские глоссы подстрочника Винокурова из JSON ([InterlinearWord.translation]) — только офлайн.
 */
object HebrewInterlinearOfflineGloss {

    /** Все слова стиха: иврит — русская подпись, по порядку в стихе. */
    fun verseWordsGlossBlock(words: List<InterlinearWord>): String =
        words.joinToString("\n") { w -> "${w.original} — ${w.translation}" }

    /**
     * Подпись для текста в поле: совпадение с одним из [words] после нормализации (никкуд, маккаф и т.д.).
     */
    fun glossForEditedHebrew(
        edited: String,
        words: List<InterlinearWord>,
        selectedWordIndex: Int?,
    ): String? {
        val norm = normalizeForMatch(edited)
        if (norm.isEmpty()) return null
        selectedWordIndex?.let { i ->
            words.getOrNull(i)?.let { w ->
                if (normalizeForMatch(w.original) == norm) {
                    return w.translation.trim().takeIf { it.isNotEmpty() }
                }
            }
        }
        return words.firstOrNull { normalizeForMatch(it.original) == norm }
            ?.translation
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun normalizeForMatch(s: String): String {
        val collapsed = buildString {
            for (ch in s) {
                when (ch) {
                    '\u05BE' -> append(' ')
                    '\u200c', '\u200d' -> Unit
                    else -> append(ch)
                }
            }
        }
        return Normalizer.normalize(collapsed, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase()
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
