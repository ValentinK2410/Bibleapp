package com.example.bible.data

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Массовая загрузка комментариев и данных «Изучение» в [StudyContentCache].
 * Тексты переводов уже в assets — здесь только материалы с studybible.ru и API комментариев.
 */
object StudyBulkDownloader {

    private fun booksInUserOrder(bookIdsInOrder: List<String>): List<CanonBookEntry> {
        val byId = BibleCanon.allBooks.associateBy { it.id }
        return bookIdsInOrder.mapNotNull { byId[it] }
    }

    suspend fun downloadChapterCommentaries(
        cache: StudyContentCache,
        delayMs: Long = 300L,
        bookIdsInOrder: List<String> = BibleCanon.allBooks.map { it.id },
        onProgress: (current: Int, total: Int, label: String) -> Unit,
    ) {
        val books = booksInUserOrder(bookIdsInOrder)
        val total = COMMENTARY_SOURCES.sumOf { books.sumOf { it.chapters } }
        var current = 0
        for (src in COMMENTARY_SOURCES) {
            for (book in books) {
                for (ch in 1..book.chapters) {
                    if (!coroutineContext.isActive) return
                    val cached = cache.getCommentary(src.urlSlug, book.id, ch)
                    if (cached != null && cached.isNotBlank()) {
                        current++
                        onProgress(current, total, "${src.name} · ${book.abbrRu} $ch · кэш")
                        continue
                    }
                    val text = StudyBibleRepository.fetchCommentary(src.urlSlug, book.id, ch)
                    if (text.isNotBlank()) {
                        cache.putCommentary(src.urlSlug, book.id, ch, text)
                    }
                    current++
                    onProgress(current, total, "${src.name} · ${book.abbrRu} $ch")
                    delay(delayMs)
                }
            }
        }
    }

    suspend fun downloadVerseStudyTools(
        bibleRepository: BibleRepository,
        cache: StudyContentCache,
        delayMs: Long = 280L,
        bookIdsInOrder: List<String> = BibleCanon.allBooks.map { it.id },
        onProgress: (current: Int, total: Int, label: String) -> Unit,
    ) {
        val books = booksInUserOrder(bookIdsInOrder)
        var totalSteps = 0
        for (book in books) {
            val bb = bibleRepository.loadBook(TranslationId.SYNODAL, book.id) ?: continue
            for (ch in bb.chapters) {
                totalSteps += ch.verses.size * 3
            }
        }
        var current = 0
        for (book in books) {
            val bb = bibleRepository.loadBook(TranslationId.SYNODAL, book.id) ?: continue
            for (ch in bb.chapters) {
                for (v in ch.verses) {
                    if (!coroutineContext.isActive) return
                    if (cache.getVerseComparisons(book.id, ch.number, v.number) == null) {
                        val cmp = StudyBibleRepository.fetchVerseComparison(book.id, ch.number, v.number)
                        if (cmp.isNotEmpty()) {
                            cache.putVerseComparisons(book.id, ch.number, v.number, cmp)
                        }
                        delay(delayMs)
                    }
                    current++
                    onProgress(current, totalSteps, "Переводы ${book.abbrRu} ${ch.number}:${v.number}")

                    if (!coroutineContext.isActive) return
                    if (cache.getCrossReferences(book.id, ch.number, v.number) == null) {
                        val crs = StudyBibleRepository.fetchCrossReferences(book.id, ch.number, v.number)
                        if (crs.isNotEmpty()) {
                            cache.putCrossReferences(book.id, ch.number, v.number, crs)
                        }
                        delay(delayMs)
                    }
                    current++
                    onProgress(current, totalSteps, "Ссылки ${book.abbrRu} ${ch.number}:${v.number}")

                    if (!coroutineContext.isActive) return
                    if (cache.getStrongWords(book.id, ch.number, v.number) == null) {
                        val str = StudyBibleRepository.fetchStrongNumbers(book.id, ch.number, v.number)
                        if (str.isNotEmpty()) {
                            cache.putStrongWords(book.id, ch.number, v.number, str)
                        }
                        delay(delayMs)
                    }
                    current++
                    onProgress(current, totalSteps, "Стронг ${book.abbrRu} ${ch.number}:${v.number}")
                }
            }
        }
    }

    suspend fun downloadApiVerseCommentaries(
        bibleRepository: BibleRepository,
        cache: StudyContentCache,
        commentaryRepository: CommentaryRepository,
        translation: TranslationId,
        delayMs: Long = 220L,
        bookIdsInOrder: List<String> = BibleCanon.allBooks.map { it.id },
        onProgress: (current: Int, total: Int, label: String) -> Unit,
    ) {
        val books = booksInUserOrder(bookIdsInOrder)
        var totalV = 0
        for (book in books) {
            val bb = bibleRepository.loadBook(TranslationId.SYNODAL, book.id) ?: continue
            for (ch in bb.chapters) {
                totalV += ch.verses.size
            }
        }
        var current = 0
        for (book in books) {
            val bb = bibleRepository.loadBook(TranslationId.SYNODAL, book.id) ?: continue
            for (ch in bb.chapters) {
                for (v in ch.verses) {
                    if (!coroutineContext.isActive) return
                    if (cache.getVerseCommentaryApi(translation.code, book.id, ch.number, v.number) != null) {
                        current++
                        onProgress(current, totalV, "Коммент. API ${translation.shortLabel} ${book.abbrRu} ${ch.number}:${v.number} · кэш")
                        continue
                    }
                    val r = commentaryRepository.loadCommentary(translation, book.id, ch.number, v.number)
                    if (r != null) {
                        cache.putVerseCommentaryApi(translation.code, book.id, ch.number, v.number, r)
                    }
                    current++
                    onProgress(current, totalV, "Коммент. API ${translation.shortLabel} ${book.abbrRu} ${ch.number}:${v.number}")
                    delay(delayMs)
                }
            }
        }
    }

    fun estimateChapterCommentaryRequests(): Int =
        COMMENTARY_SOURCES.size * BibleCanon.allBooks.sumOf { it.chapters }

    fun estimateVerseStudyRequests(): Int {
        // ~31005 verses * 3
        return 31005 * 3
    }
}
