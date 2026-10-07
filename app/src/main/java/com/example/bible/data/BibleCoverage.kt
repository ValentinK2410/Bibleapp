package com.example.bible.data

/**
 * Учёт прочитанных и прослушанных глав по переводу.
 *
 * Ключ главы: `перевод|книга|номер`, например `SYN|genesis|1`.
 * Иврит и греческий — отдельные дорожки прослушивания, не текстовые переводы.
 */
object BibleCoverage {
    const val HEBREW = "HEB"
    const val GREEK = "GRK"

    data class Track(
        val id: String,
        val shortLabel: String,
        val label: String,
        val textTranslation: TranslationId?,
        /** Есть отдельная озвучка этого перевода. У подстрочника слушают иврит или греческий. */
        val hasListen: Boolean,
        /** Есть текст, который открывают в читалке. */
        val hasRead: Boolean,
    )

    val tracks: List<Track> = listOf(
        Track(TranslationId.SYNODAL.code, "СИН", "Синодальный", TranslationId.SYNODAL, hasListen = true, hasRead = true),
        Track(TranslationId.NRT.code, "НРП", "Новый русский", TranslationId.NRT, hasListen = true, hasRead = true),
        Track(TranslationId.RBO.code, "РБО", "РБО", TranslationId.RBO, hasListen = true, hasRead = true),
        Track(TranslationId.BTI.code, "КУЛ", "Кулаковых", TranslationId.BTI, hasListen = true, hasRead = true),
        Track(TranslationId.WEB.code, "WEB", "WEB (англ.)", TranslationId.WEB, hasListen = true, hasRead = true),
        Track(TranslationId.INTERLINEAR.code, "ПОДСТР", "Подстрочный", TranslationId.INTERLINEAR, hasListen = false, hasRead = true),
        Track(HEBREW, "ИВР", "Иврит (ВЗ)", textTranslation = null, hasListen = true, hasRead = false),
        Track(GREEK, "ГРЕЧ", "Греческий (НЗ)", textTranslation = null, hasListen = true, hasRead = false),
    )

    fun trackById(id: String): Track? = tracks.find { it.id == id }

    fun chapterKey(trackId: String, bookId: String, chapter: Int): String =
        "$trackId|$bookId|$chapter"

    /** Дорожка прослушивания по диктору. Синодальные чтецы сходятся в один перевод. */
    fun trackForNarrator(narratorId: String): String? = when (narratorId) {
        "bondarenko", "kozlov", "efimov", "jbl" -> TranslationId.SYNODAL.code
        "new-russian" -> TranslationId.NRT.code
        "rbo" -> TranslationId.RBO.code
        "bti" -> TranslationId.BTI.code
        "web" -> TranslationId.WEB.code
        "hebrew-ot" -> HEBREW
        "greek-nt" -> GREEK
        else -> null
    }

    fun booksFor(trackId: String): List<CanonBookEntry> = when (trackId) {
        HEBREW -> BibleCanon.allBooks.filter { BibleCanon.isOldTestament(it.id) }
        GREEK -> BibleCanon.allBooks.filter { BibleCanon.isNewTestament(it.id) }
        else -> BibleCanon.allBooks
    }

    fun countsByBook(keys: Set<String>, trackId: String): Map<String, Int> {
        if (keys.isEmpty()) return emptyMap()
        val prefix = "$trackId|"
        val counts = HashMap<String, Int>()
        for (key in keys) {
            if (!key.startsWith(prefix)) continue
            val rest = key.substring(prefix.length)
            val bar = rest.indexOf('|')
            if (bar <= 0) continue
            val bookId = rest.substring(0, bar)
            val chapter = rest.substring(bar + 1).toIntOrNull() ?: continue
            val total = BibleCanon.byId(bookId)?.chapters ?: continue
            if (chapter !in 1..total) continue
            counts[bookId] = (counts[bookId] ?: 0) + 1
        }
        return counts
    }

    data class BookProgress(
        val book: CanonBookEntry,
        val read: Int,
        val listened: Int,
    ) {
        val readComplete: Boolean get() = read >= book.chapters
        val listenComplete: Boolean get() = listened >= book.chapters

        fun firstMissing(readKeysForBook: Set<Int>, wantRead: Boolean): Int {
            val marked = if (wantRead) read else listened
            if (marked >= book.chapters) return 1
            val have = readKeysForBook
            for (ch in 1..book.chapters) {
                if (ch !in have) return ch
            }
            return 1
        }
    }

    fun progressForTrack(
        trackId: String,
        readKeys: Set<String>,
        listenKeys: Set<String>,
    ): List<BookProgress> {
        val readCounts = countsByBook(readKeys, trackId)
        val listenCounts = countsByBook(listenKeys, trackId)
        return booksFor(trackId).map { book ->
            BookProgress(
                book = book,
                read = (readCounts[book.id] ?: 0).coerceAtMost(book.chapters),
                listened = (listenCounts[book.id] ?: 0).coerceAtMost(book.chapters),
            )
        }
    }

    fun chaptersMarked(keys: Set<String>, trackId: String, bookId: String): Set<Int> {
        val prefix = "$trackId|$bookId|"
        val total = BibleCanon.byId(bookId)?.chapters ?: return emptySet()
        val out = HashSet<Int>()
        for (key in keys) {
            if (!key.startsWith(prefix)) continue
            val chapter = key.substring(prefix.length).toIntOrNull() ?: continue
            if (chapter in 1..total) out += chapter
        }
        return out
    }

    data class TrackSummary(
        val track: Track,
        val readChapters: Int,
        val listenChapters: Int,
        val totalChapters: Int,
        val booksRead: Int,
        val booksListened: Int,
        val bookCount: Int,
    ) {
        val readFraction: Float
            get() = if (totalChapters == 0) 0f else readChapters.toFloat() / totalChapters
        val listenFraction: Float
            get() = if (totalChapters == 0) 0f else listenChapters.toFloat() / totalChapters
    }

    fun summarize(readKeys: Set<String>, listenKeys: Set<String>): List<TrackSummary> =
        tracks.map { track ->
            val books = progressForTrack(track.id, readKeys, listenKeys)
            TrackSummary(
                track = track,
                readChapters = books.sumOf { it.read },
                listenChapters = books.sumOf { it.listened },
                totalChapters = books.sumOf { it.book.chapters },
                booksRead = books.count { it.readComplete },
                booksListened = books.count { it.listenComplete },
                bookCount = books.size,
            )
        }

    /** Счётчики для плиток книг текущего текстового перевода. */
    fun tileCounts(
        trackId: String,
        readKeys: Set<String>,
        listenKeys: Set<String>,
    ): Map<String, Pair<Int, Int>> {
        val readCounts = countsByBook(readKeys, trackId)
        val listenCounts = countsByBook(listenKeys, trackId)
        val ids = (readCounts.keys + listenCounts.keys)
        return ids.associateWith { id ->
            val total = BibleCanon.byId(id)?.chapters ?: 0
            (readCounts[id] ?: 0).coerceAtMost(total) to (listenCounts[id] ?: 0).coerceAtMost(total)
        }
    }
}
