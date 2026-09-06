package com.example.bible.data

/** Тексты запросов к ИИ по стиху, диапазону, главе или книге. */
object BiblePassagePrompts {

    enum class Style {
        /** DeepSeek: объяснение и разбор. */
        EXPLAIN,
        /** GigaChat: духовное размышление. */
        REFLECT,
    }

    fun build(
        style: Style,
        translation: TranslationId,
        bookId: String,
        bookName: String,
        chapter: Int,
        verse: Int,
        verseText: String,
        scope: DeepSeekPassageScope,
        rangeStart: Int,
        rangeEnd: Int,
        fallbackChapterTexts: Map<Int, String>,
        loadChapter: (TranslationId, String, Int) -> List<BibleVerse>?,
        loadBook: (TranslationId, String) -> BibleBook?,
    ): String? {
        val chapterVerses = loadChapter(translation, bookId, chapter)
            ?: DeepSeekPassageFormatter.fromMap(
                fallbackChapterTexts,
                1,
                fallbackChapterTexts.keys.maxOrNull() ?: verse,
            )
        return when (scope) {
            DeepSeekPassageScope.VERSE -> versePrompt(style, bookName, chapter, verse, chapterVerses, verseText)
            DeepSeekPassageScope.RANGE -> rangePrompt(
                style,
                bookName,
                chapter,
                rangeStart,
                rangeEnd,
                chapterVerses,
                fallbackChapterTexts,
            )
            DeepSeekPassageScope.CHAPTER -> chapterPrompt(style, bookName, chapter, chapterVerses, fallbackChapterTexts)
            DeepSeekPassageScope.BOOK -> bookPrompt(style, bookName, translation, bookId, loadBook)
        }
    }

    private fun versePrompt(
        style: Style,
        bookName: String,
        chapter: Int,
        verse: Int,
        chapterVerses: List<BibleVerse>,
        verseText: String,
    ): String {
        val text = chapterVerses.firstOrNull { it.number == verse }?.text?.ifBlank { null }
            ?: verseText
        return when (style) {
            Style.EXPLAIN ->
                "Объясни стих $bookName $chapter:$verse.\nТекст: «$text»\n" +
                    "Кратко: смысл, ближайший контекст, как применить."
            Style.REFLECT ->
                "Помоги поразмышлять над стихом $bookName $chapter:$verse.\n" +
                    "Текст: «$text»\n\n" +
                    "Поделись: что говорит этот стих; какие вопросы он поднимает; " +
                    "что в нём главное для сердца; как можно применить в жизни. " +
                    "Можешь задать 1–2 наводящих вопросы для личного размышления."
        }
    }

    private fun rangePrompt(
        style: Style,
        bookName: String,
        chapter: Int,
        rangeStart: Int,
        rangeEnd: Int,
        chapterVerses: List<BibleVerse>,
        fallbackChapterTexts: Map<Int, String>,
    ): String? {
        val a = minOf(rangeStart, rangeEnd).coerceAtLeast(1)
        val b = maxOf(rangeStart, rangeEnd)
        val picked = chapterVerses.filter { it.number in a..b }
            .ifEmpty { DeepSeekPassageFormatter.fromMap(fallbackChapterTexts, a, b) }
        if (picked.isEmpty()) return null
        val body = DeepSeekPassageFormatter.versesBlock(picked)
        return when (style) {
            Style.EXPLAIN ->
                "Порассуждай над отрывком $bookName $chapter:$a–$b как над цельным пассажем.\n" +
                    "Тема, ход мысли, связь стихов, как применить.\n\n$body"
            Style.REFLECT ->
                "Помоги поразмышлять над отрывком $bookName $chapter:$a–$b.\n" +
                    "Рассмотри отрывок как цельное целое: главная мысль, связь стихов, " +
                    "что трогает сердце, как применить.\n\n$body"
        }
    }

    private fun chapterPrompt(
        style: Style,
        bookName: String,
        chapter: Int,
        chapterVerses: List<BibleVerse>,
        fallbackChapterTexts: Map<Int, String>,
    ): String? {
        val verses = chapterVerses.ifEmpty {
            DeepSeekPassageFormatter.fromMap(
                fallbackChapterTexts,
                1,
                fallbackChapterTexts.keys.maxOrNull() ?: 1,
            )
        }
        if (verses.isEmpty()) return null
        val body = DeepSeekPassageFormatter.chapterBlock(chapter, verses)
        return when (style) {
            Style.EXPLAIN ->
                "Порассуждай над главой $bookName $chapter целиком.\n" +
                    "Структура, главная мысль, ключевые стихи, как глава встраивается в книгу.\n\n$body"
            Style.REFLECT ->
                "Помоги поразмышлять над главой $bookName $chapter целиком.\n" +
                    "Структура, главная мысль, ключевые стихи, духовный урок, личное применение.\n\n$body"
        }
    }

    private fun bookPrompt(
        style: Style,
        bookName: String,
        translation: TranslationId,
        bookId: String,
        loadBook: (TranslationId, String) -> BibleBook?,
    ): String? {
        val book = loadBook(translation, bookId) ?: return null
        if (book.chapters.none { it.verses.isNotEmpty() }) return null
        val (body, truncated) = DeepSeekPassageFormatter.bookBlock(book)
        if (body.isBlank()) return null
        val note = if (truncated) {
            "Текст книги сокращён: опирайся на то, что есть, и не достраивай пропущенные главы как цитаты.\n\n"
        } else {
            ""
        }
        return when (style) {
            Style.EXPLAIN ->
                "Порассуждай над книгой «$bookName» целиком.\n" +
                    "Замысел, структура по главам, главные темы, кому адресована, чем важна для чтения.\n\n" +
                    note + body
            Style.REFLECT ->
                "Помоги поразмышлять над книгой «$bookName» целиком.\n" +
                    "Замысел, структура, главные темы, духовный урок, как книга говорит читателю сегодня.\n\n" +
                    note + body
        }
    }
}
