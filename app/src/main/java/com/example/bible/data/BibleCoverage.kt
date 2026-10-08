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

    /**
     * Дорожка прослушивания для экрана выбора.
     * У подстрочника слушают иврит (Ветхий Завет) или греческий (Новый).
     */
    fun listenTrackFor(translation: TranslationId, bookId: String): String =
        if (translation == TranslationId.INTERLINEAR) {
            if (BibleCanon.isOldTestament(bookId)) HEBREW else GREEK
        } else {
            translation.code
        }

    fun percent(done: Int, total: Int): Int =
        if (total <= 0) 0 else ((done.coerceAtLeast(0) * 100L) / total).toInt().coerceIn(0, 100)

    /** Цвет метки перевода: свой цвет вкладки, иначе постоянная палитра. */
    fun markColorArgb(trackId: String, custom: Map<String, Int> = emptyMap()): Int {
        custom[trackId]?.let { return it }
        custom.entries.firstOrNull { it.key.equals(trackId, ignoreCase = true) }?.value?.let { return it }
        return defaultMarkColors[trackId] ?: 0xFF546E7A.toInt()
    }

    private val defaultMarkColors: Map<String, Int> = mapOf(
        TranslationId.SYNODAL.code to 0xFF1565C0.toInt(),
        TranslationId.NRT.code to 0xFF2E7D32.toInt(),
        TranslationId.RBO.code to 0xFF6A1B9A.toInt(),
        TranslationId.BTI.code to 0xFFEF6C00.toInt(),
        TranslationId.WEB.code to 0xFF00838F.toInt(),
        TranslationId.INTERLINEAR.code to 0xFF6D4C41.toInt(),
        HEBREW to 0xFFC62828.toInt(),
        GREEK to 0xFF283593.toInt(),
    )

    data class VerseMark(
        val trackId: String,
        val read: Boolean,
        val listened: Boolean,
    )

    /** Сжатая запись стихов главы: `перевод|книга|глава|1-3,5`. */
    fun encodeVerseSpans(verses: Collection<Int>): String {
        val sorted = verses.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val parts = ArrayList<String>(sorted.size)
        var start = sorted[0]
        var prev = start
        for (index in 1 until sorted.size) {
            val verse = sorted[index]
            if (verse == prev + 1) {
                prev = verse
            } else {
                parts += verseSpan(start, prev)
                start = verse
                prev = verse
            }
        }
        parts += verseSpan(start, prev)
        return parts.joinToString(",")
    }

    fun decodeVerseSpans(spec: String): Set<Int> {
        if (spec.isBlank()) return emptySet()
        val out = HashSet<Int>()
        for (raw in spec.split(',')) {
            val part = raw.trim()
            if (part.isEmpty()) continue
            val dash = part.indexOf('-')
            if (dash <= 0) {
                part.toIntOrNull()?.let { if (it > 0) out += it }
            } else {
                val from = part.substring(0, dash).toIntOrNull() ?: continue
                val to = part.substring(dash + 1).toIntOrNull() ?: continue
                if (from <= 0 || to < from || to - from > 400) continue
                for (verse in from..to) out += verse
            }
        }
        return out
    }

    /**
     * Добавляет стихи в единственную запись главы.
     * @return true, если набор ключей изменился.
     */
    fun mergeVerseRecord(
        keys: MutableSet<String>,
        trackId: String,
        bookId: String,
        chapter: Int,
        extra: Collection<Int>,
    ): Boolean {
        if (trackId.isBlank() || bookId.isBlank() || chapter <= 0) return false
        val adding = extra.filter { it > 0 }
        if (adding.isEmpty()) return false
        val prefix = "$trackId|$bookId|$chapter|"
        val existingKeys = keys.filter { it.startsWith(prefix) }
        val have = HashSet<Int>()
        for (key in existingKeys) have += decodeVerseSpans(key.substring(prefix.length))
        if (adding.all { it in have } && existingKeys.size == 1) return false
        have += adding
        if (existingKeys.isNotEmpty()) keys.removeAll(existingKeys.toSet())
        keys += prefix + encodeVerseSpans(have)
        return true
    }

    fun removeBookVerseRecords(keys: MutableSet<String>, trackId: String, bookId: String) {
        if (trackId.isBlank() || bookId.isBlank()) return
        val prefix = "$trackId|$bookId|"
        keys.removeAll { it.startsWith(prefix) }
    }

    fun marksForChapter(
        readKeys: Set<String>,
        listenKeys: Set<String>,
        bookId: String,
        chapter: Int,
    ): Map<Int, List<VerseMark>> {
        if (bookId.isBlank() || chapter <= 0) return emptyMap()
        val readByTrack = versesByTrack(readKeys, bookId, chapter)
        val listenByTrack = versesByTrack(listenKeys, bookId, chapter)
        val verseNumbers = HashSet<Int>()
        readByTrack.values.forEach { verseNumbers += it }
        listenByTrack.values.forEach { verseNumbers += it }
        if (verseNumbers.isEmpty()) return emptyMap()
        val trackIds = (readByTrack.keys + listenByTrack.keys).sortedBy { trackOrder(it) }
        return verseNumbers.associateWith { verse ->
            trackIds.mapNotNull { trackId ->
                val read = verse in (readByTrack[trackId] ?: emptySet())
                val listened = verse in (listenByTrack[trackId] ?: emptySet())
                if (!read && !listened) null else VerseMark(trackId, read, listened)
            }
        }
    }

    /** Стихи книги по главам на одной дорожке. */
    fun versesInBook(keys: Set<String>, trackId: String, bookId: String): Map<Int, Set<Int>> {
        if (keys.isEmpty() || trackId.isBlank() || bookId.isBlank()) return emptyMap()
        val prefix = "$trackId|$bookId|"
        val out = HashMap<Int, MutableSet<Int>>()
        for (key in keys) {
            if (!key.startsWith(prefix)) continue
            val rest = key.substring(prefix.length)
            val bar = rest.indexOf('|')
            if (bar <= 0) continue
            val chapter = rest.substring(0, bar).toIntOrNull() ?: continue
            val verses = decodeVerseSpans(rest.substring(bar + 1))
            if (verses.isEmpty()) continue
            out.getOrPut(chapter) { HashSet() }.addAll(verses)
        }
        return out
    }

    /** Сколько стихов прослушано в каждой главе книги на одной дорожке. */
    fun listenedVersesByChapter(keys: Set<String>, trackId: String, bookId: String): Map<Int, Int> {
        if (keys.isEmpty() || trackId.isBlank() || bookId.isBlank()) return emptyMap()
        val prefix = "$trackId|$bookId|"
        val counts = HashMap<Int, Int>()
        for (key in keys) {
            if (!key.startsWith(prefix)) continue
            val rest = key.substring(prefix.length)
            val bar = rest.indexOf('|')
            if (bar <= 0) continue
            val chapter = rest.substring(0, bar).toIntOrNull() ?: continue
            val verses = decodeVerseSpans(rest.substring(bar + 1))
            if (verses.isEmpty()) continue
            counts[chapter] = (counts[chapter] ?: 0) + verses.size
        }
        return counts
    }

    private fun versesByTrack(keys: Set<String>, bookId: String, chapter: Int): Map<String, Set<Int>> {
        if (keys.isEmpty()) return emptyMap()
        val needle = "|$bookId|$chapter|"
        val out = HashMap<String, MutableSet<Int>>()
        for (key in keys) {
            val at = key.indexOf(needle)
            if (at <= 0) continue
            val trackId = key.substring(0, at)
            if (trackId.contains('|')) continue
            val verses = decodeVerseSpans(key.substring(at + needle.length))
            if (verses.isEmpty()) continue
            out.getOrPut(trackId) { HashSet() }.addAll(verses)
        }
        return out
    }

    private fun trackOrder(trackId: String): Int {
        val index = tracks.indexOfFirst { it.id == trackId }
        return if (index < 0) tracks.size else index
    }

    private fun verseSpan(start: Int, end: Int): String =
        if (start == end) "$start" else "$start-$end"

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

    /** Цифры на плитке книги: главы целиком и стихи, прослушанные частично. */
    data class BookTileCoverage(
        val readChapters: Int = 0,
        val listenedChapters: Int = 0,
        val listenedVerses: Int = 0,
        /** Главы, где есть стихи, но глава ещё не засчитана целиком. */
        val listenedOpenChapters: Int = 0,
    )

    fun homeTiles(
        translation: TranslationId,
        readKeys: Set<String>,
        listenChapterKeys: Set<String>,
        listenVerseKeys: Set<String>,
    ): Map<String, BookTileCoverage> {
        val readCounts = countsByBook(readKeys, translation.code)
        val listenFull = HashMap<String, MutableSet<Int>>()
        for (key in listenChapterKeys) {
            val parts = key.split('|')
            if (parts.size != 3) continue
            val bookId = parts[1]
            if (parts[0] != listenTrackFor(translation, bookId)) continue
            val chapter = parts[2].toIntOrNull() ?: continue
            val total = BibleCanon.byId(bookId)?.chapters ?: continue
            if (chapter !in 1..total) continue
            listenFull.getOrPut(bookId) { HashSet() }.add(chapter)
        }
        val verseCount = HashMap<String, Int>()
        val verseChapters = HashMap<String, MutableSet<Int>>()
        for (key in listenVerseKeys) {
            val parts = key.split('|')
            if (parts.size < 4) continue
            val bookId = parts[1]
            if (parts[0] != listenTrackFor(translation, bookId)) continue
            val chapter = parts[2].toIntOrNull() ?: continue
            val verses = decodeVerseSpans(parts.subList(3, parts.size).joinToString("|"))
            if (verses.isEmpty()) continue
            verseCount[bookId] = (verseCount[bookId] ?: 0) + verses.size
            verseChapters.getOrPut(bookId) { HashSet() }.add(chapter)
        }
        val ids = readCounts.keys + listenFull.keys + verseCount.keys
        return ids.associateWith { id ->
            val total = BibleCanon.byId(id)?.chapters ?: 0
            val full = listenFull[id].orEmpty()
            val open = verseChapters[id].orEmpty().count { it !in full }
            BookTileCoverage(
                readChapters = (readCounts[id] ?: 0).coerceAtMost(total),
                listenedChapters = full.size.coerceAtMost(total),
                listenedVerses = verseCount[id] ?: 0,
                listenedOpenChapters = open,
            )
        }
    }

    /** Доля полоски: целая глава = 1, начатая глава = половина. */
    fun listenFill(chapters: Int, coverage: BookTileCoverage): Float {
        if (chapters <= 0) return 0f
        val full = coverage.listenedChapters.coerceAtMost(chapters)
        val room = (chapters - full).coerceAtLeast(0)
        val open = coverage.listenedOpenChapters.coerceAtMost(room)
        return ((full + open * 0.5f) / chapters).coerceIn(0f, 1f)
    }
}
