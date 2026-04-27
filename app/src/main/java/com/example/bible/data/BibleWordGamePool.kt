package com.example.bible.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class BibleWordItem(
    val word: String,
    val bookName: String,
    val reference: String,
    val verseText: String,
)

/**
 * Слова из русских текстов Библии для игр азбуки.
 */
object BibleWordGamePool {

    private val sourceBookIds = listOf(
        "genesis", "psalms", "proverbs", "isaiah", "john", "matthew", "luke", "mark",
        "romans", "1_corinthians", "revelation", "job", "ecclesiastes",
    )

    private val skipLowercase = setOf(
        "который", "которая", "которое", "которые", "было", "были", "был", "была",
        "этот", "эта", "это", "эти", "что", "как", "все", "всё", "его", "её", "их",
        "для", "или", "при", "над", "под", "от", "из", "ко", "об", "до", "по",
        "не", "ни", "же", "ли", "бы", "то", "та", "те", "тот", "там", "туда",
        "где", "когда", "если", "так", "уже", "ещё", "еще", "только", "даже",
    )

    private val wordRegex = Regex("[А-Яа-яЁё]{3,14}")

    fun pickTranslationWithText(library: BibleLibrary): TranslationId {
        val order = listOf(
            TranslationId.SYNODAL,
            TranslationId.NRT,
            TranslationId.RBO,
            TranslationId.BTI,
        )
        for (t in order) {
            val b = library.getBook(t, "john")
            if (b != null && b.chapters.isNotEmpty()) return t
        }
        return TranslationId.SYNODAL
    }

    suspend fun collectWords(
        library: BibleLibrary,
        translation: TranslationId,
        maxWords: Int = 220,
        random: Random = Random.Default,
    ): List<BibleWordItem> = withContext(Dispatchers.Default) {
        val result = mutableListOf<BibleWordItem>()
        val seen = mutableSetOf<String>()

        for (bookId in sourceBookIds.shuffled(random)) {
            val book = library.getBook(translation, bookId) ?: continue
            val chapters = book.chapters.shuffled(random).take(8)
            for (ch in chapters) {
                for (v in ch.verses.shuffled(random).take(12)) {
                    for (m in wordRegex.findAll(v.text)) {
                        val raw = m.value
                        val w = raw.trim().trimEnd('.', ',', ';', ':', '!', '?', '»', ')', ']', '…')
                        if (w.length < 3 || w.length > 14) continue
                        val low = w.lowercase()
                        if (low in skipLowercase) continue
                        if (!seen.add(low)) continue
                        val ref = "${book.name} ${ch.number}:${v.number}"
                        result.add(
                            BibleWordItem(
                                word = w,
                                bookName = book.name,
                                reference = ref,
                                verseText = v.text,
                            ),
                        )
                        if (result.size >= maxWords) return@withContext result.shuffled(random)
                    }
                }
            }
        }
        result.shuffled(random)
    }

    fun shuffledLetters(word: String): String {
        val chars = word.toMutableList()
        var s = chars.shuffled().joinToString("")
        var guard = 0
        while (s.equals(word, ignoreCase = true) && chars.size > 1 && guard++ < 12) {
            s = chars.shuffled().joinToString("")
        }
        return s
    }

    /** Делит слово на 2–4 части для игры «собери слово». */
    fun splitIntoChunks(word: String, targetParts: Int = 3): List<String> {
        val w = word.trim()
        if (w.length < 4) return listOf(w)
        val parts = targetParts.coerceIn(2, 4)
        if (w.length <= parts) return listOf(w)
        val chunkLen = w.length / parts
        val list = mutableListOf<String>()
        var i = 0
        var remaining = parts
        while (i < w.length && remaining > 0) {
            val len = if (remaining == 1) w.length - i else maxOf(1, chunkLen)
            val end = (i + len).coerceAtMost(w.length)
            list.add(w.substring(i, end))
            i = end
            remaining--
        }
        return list.filter { it.isNotBlank() }
    }

    fun randomSyllableFromWord(word: String, minLen: Int = 2): String? {
        val chunks = splitIntoChunks(word, 3)
        return chunks.filter { it.length >= minLen }.randomOrNull()
    }

    /** Грубая оценка числа слогов для игр: число гласных букв. */
    fun approximateSyllableCount(word: String): Int {
        val vowels = setOf(
            'а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я',
            'А', 'Е', 'Ё', 'И', 'О', 'У', 'Ы', 'Э', 'Ю', 'Я',
        )
        return word.count { it in vowels }.coerceAtLeast(1)
    }

    fun pickDistractorWords(
        pool: List<BibleWordItem>,
        correct: String,
        count: Int,
        random: Random,
    ): List<String> {
        val correctLow = correct.lowercase()
        val candidates = pool
            .map { it.word }
            .filter { it.lowercase() != correctLow }
            .distinct()
            .shuffled(random)
        val out = mutableListOf<String>()
        for (w in candidates) {
            if (out.size >= count) break
            if (kotlin.math.abs(w.length - correct.length) <= 4) out.add(w)
        }
        for (w in candidates) {
            if (out.size >= count) break
            if (w !in out) out.add(w)
        }
        return out.take(count)
    }
}
