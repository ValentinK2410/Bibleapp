package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * Сводка по журналу чтения и копирования стихов в одном переводе.
 * Открытия берутся из журнала «стих за стихом», время — из истории читалки.
 */
object BibleReadingStats {
    const val COPY_LIMIT = 2_000

    data class VerseCopyStat(
        val translation: String,
        val bookId: String,
        val bookName: String,
        val chapter: Int,
        val verse: Int,
        val count: Int,
        val lastAt: Long,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("t", translation)
            put("b", bookId)
            put("n", bookName)
            put("c", chapter)
            put("v", verse)
            put("ncopy", count)
            put("ts", lastAt)
        }
    }

    data class PassageRank(
        val bookId: String,
        val bookName: String,
        val chapter: Int,
        val verse: Int,
        val visits: Int,
        val dwellSeconds: Int,
        val copies: Int,
        val uniqueVerses: Int,
    )

    data class GroupStats(
        val group: CanonBookGroup,
        val title: String,
        val visits: Int,
        val dwellSeconds: Int,
        val uniqueVerses: Int,
        val chaptersOpened: Int,
        val booksTouched: Int,
        val bookCount: Int,
        val copies: Int,
        val listenedChapters: Int,
        val totalChapters: Int,
    ) {
        val activity: Int
            get() = dwellSeconds + visits * 20 + copies * 40 + uniqueVerses + listenedChapters
    }

    data class Snapshot(
        val daysActive: Int,
        val currentStreak: Int,
        val longestStreak: Int,
        val uniqueVerses: Int,
        val visits: Int,
        val dwellSeconds: Int,
        val uniqueChapters: Int,
        val uniqueBooks: Int,
        val copies: Int,
        val visitsLast7Days: Int,
        val dwellLast7Days: Int,
        val oldTestamentSeconds: Int,
        val newTestamentSeconds: Int,
        val topBooks: List<PassageRank>,
        val topChapters: List<PassageRank>,
        val topVerses: List<PassageRank>,
        val topByTime: List<PassageRank>,
        val topCopied: List<PassageRank>,
        val topListenedBooks: List<PassageRank>,
        val groups: List<GroupStats>,
        val topTools: List<Pair<String, Int>>,
    ) {
        val isEmpty: Boolean
            get() = uniqueVerses == 0 && visits == 0 && copies == 0 && topListenedBooks.isEmpty()
    }

    fun parseCopies(json: String): List<VerseCopyStat> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { index ->
                val j = arr.optJSONObject(index) ?: return@mapNotNull null
                val verse = j.optInt("v", 0)
                val chapter = j.optInt("c", 0)
                val count = j.optInt("ncopy", 0)
                if (verse <= 0 || chapter <= 0 || count <= 0) return@mapNotNull null
                VerseCopyStat(
                    translation = j.optString("t", ""),
                    bookId = j.optString("b", ""),
                    bookName = j.optString("n", ""),
                    chapter = chapter,
                    verse = verse,
                    count = count,
                    lastAt = j.optLong("ts", 0L),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun copiesToJson(list: List<VerseCopyStat>): String {
        val arr = JSONArray()
        list.take(COPY_LIMIT).forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    fun mergeCopies(
        current: List<VerseCopyStat>,
        translation: String,
        bookId: String,
        bookName: String,
        chapter: Int,
        verses: Collection<Int>,
        now: Long,
    ): List<VerseCopyStat> {
        if (translation.isBlank() || bookId.isBlank() || chapter <= 0) return current
        val adding = verses.filter { it > 0 }.distinct()
        if (adding.isEmpty()) return current
        val next = current.toMutableList()
        for (verse in adding) {
            val index = next.indexOfFirst {
                it.translation == translation && it.bookId == bookId &&
                    it.chapter == chapter && it.verse == verse
            }
            if (index >= 0) {
                val old = next[index]
                next[index] = old.copy(
                    bookName = bookName.ifBlank { old.bookName },
                    count = old.count + 1,
                    lastAt = now,
                )
            } else {
                next += VerseCopyStat(translation, bookId, bookName, chapter, verse, 1, now)
            }
        }
        return next
            .sortedWith(compareByDescending<VerseCopyStat> { it.count }.thenByDescending { it.lastAt })
            .take(COPY_LIMIT)
    }

    fun build(
        trackId: String,
        history: List<HistoryEntry>,
        trace: List<ReadingTraceEntry>,
        copies: List<VerseCopyStat>,
        listenKeys: Set<String>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        limit: Int = 8,
    ): Snapshot {
        val historyRows = history.filter { it.translation == trackId && it.bookId.isNotBlank() && it.chapter > 0 }
        val traceRows = trace.filter { it.translation == trackId && it.bookId.isNotBlank() && it.chapter > 0 }
        val copyRows = copies.filter { it.translation == trackId && it.bookId.isNotBlank() && it.verse > 0 }
        val weekStart = now - 7L * 24L * 60L * 60L * 1000L

        val books = HashMap<String, Bucket>()
        val chapters = HashMap<String, Bucket>()
        val verses = HashMap<String, Bucket>()

        fun bucket(map: HashMap<String, Bucket>, key: String, bookId: String, name: String, chapter: Int, verse: Int): Bucket {
            return map.getOrPut(key) {
                Bucket(bookId, displayBookName(bookId, name), chapter, verse)
            }.also { if (name.isNotBlank()) it.bookName = displayBookName(bookId, name) }
        }

        for (row in historyRows) {
            val name = row.bookName
            bucket(books, row.bookId, row.bookId, name, 0, 0).apply {
                dwellSeconds += row.dwellSeconds.coerceAtLeast(0)
                uniqueVerses += 1
            }
            bucket(chapters, "${row.bookId}|${row.chapter}", row.bookId, name, row.chapter, 0).apply {
                dwellSeconds += row.dwellSeconds.coerceAtLeast(0)
                uniqueVerses += 1
            }
            if (row.verse > 0) {
                bucket(verses, "${row.bookId}|${row.chapter}|${row.verse}", row.bookId, name, row.chapter, row.verse)
                    .dwellSeconds += row.dwellSeconds.coerceAtLeast(0)
            }
        }
        for (row in traceRows) {
            if (row.dwellSeconds > 0) continue
            bucket(books, row.bookId, row.bookId, row.bookName, 0, 0).visits += 1
            bucket(chapters, "${row.bookId}|${row.chapter}", row.bookId, row.bookName, row.chapter, 0).visits += 1
            if (row.verse > 0) {
                bucket(
                    verses,
                    "${row.bookId}|${row.chapter}|${row.verse}",
                    row.bookId,
                    row.bookName,
                    row.chapter,
                    row.verse,
                ).visits += 1
            }
        }
        for (row in copyRows) {
            bucket(books, row.bookId, row.bookId, row.bookName, 0, 0).copies += row.count
            bucket(chapters, "${row.bookId}|${row.chapter}", row.bookId, row.bookName, row.chapter, 0).copies += row.count
            bucket(
                verses,
                "${row.bookId}|${row.chapter}|${row.verse}",
                row.bookId,
                row.bookName,
                row.chapter,
                row.verse,
            ).copies += row.count
        }

        val days = HashSet<Long>()
        for (row in historyRows) days += dayEpoch(row.timestamp, zone)
        for (row in traceRows) days += dayEpoch(row.timestamp, zone)
        val today = dayEpoch(now, zone)
        val (currentStreak, longestStreak) = streaks(days, today)

        val toolCounts = HashMap<String, Int>()
        for (row in historyRows) {
            if (row.toolsUsed.isBlank()) continue
            for (tool in row.toolsUsed.split('|')) {
                val label = tool.trim()
                if (label.isNotEmpty()) toolCounts[label] = (toolCounts[label] ?: 0) + 1
            }
        }

        val listenedByBook = BibleCoverage.countsByBook(listenKeys, trackId)
        val groupBuckets = CanonBookGroup.entries.associateWith { GroupBucket() }
        var oldSeconds = 0
        var newSeconds = 0
        for (book in books.values) {
            val canon = BibleCanon.byId(book.bookId) ?: continue
            val bucket = groupBuckets.getValue(canon.group)
            bucket.visits += book.visits
            bucket.dwellSeconds += book.dwellSeconds
            bucket.uniqueVerses += book.uniqueVerses
            bucket.copies += book.copies
            if (book.uniqueVerses > 0 || book.visits > 0 || book.dwellSeconds > 0 || book.copies > 0) {
                bucket.touchedBooks += book.bookId
            }
            if (BibleCanon.isOldTestament(book.bookId)) oldSeconds += book.dwellSeconds else newSeconds += book.dwellSeconds
        }
        val openedChapters = HashSet<String>()
        for (row in historyRows) openedChapters += "${row.bookId}|${row.chapter}"
        for (key in openedChapters) {
            val bookId = key.substringBefore('|')
            val canon = BibleCanon.byId(bookId) ?: continue
            groupBuckets.getValue(canon.group).chaptersOpened += 1
        }
        for ((bookId, count) in listenedByBook) {
            val canon = BibleCanon.byId(bookId) ?: continue
            val bucket = groupBuckets.getValue(canon.group)
            bucket.listenedChapters += count
            if (count > 0) bucket.touchedBooks += bookId
        }
        val groups = CanonBookGroup.entries.map { group ->
            val canonBooks = BibleCanon.allBooks.filter { it.group == group }
            val bucket = groupBuckets.getValue(group)
            GroupStats(
                group = group,
                title = groupTitle(group),
                visits = bucket.visits,
                dwellSeconds = bucket.dwellSeconds,
                uniqueVerses = bucket.uniqueVerses,
                chaptersOpened = bucket.chaptersOpened,
                booksTouched = bucket.touchedBooks.size,
                bookCount = canonBooks.size,
                copies = bucket.copies,
                listenedChapters = bucket.listenedChapters,
                totalChapters = canonBooks.sumOf { it.chapters },
            )
        }.sortedByDescending { it.activity }

        return Snapshot(
            daysActive = days.size,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            uniqueVerses = historyRows.count { it.verse > 0 },
            visits = traceRows.count { it.dwellSeconds <= 0 && it.verse > 0 },
            dwellSeconds = historyRows.sumOf { it.dwellSeconds.coerceAtLeast(0) },
            uniqueChapters = historyRows.map { "${it.bookId}|${it.chapter}" }.distinct().size,
            uniqueBooks = historyRows.map { it.bookId }.distinct().size,
            copies = copyRows.sumOf { it.count },
            visitsLast7Days = traceRows.count { it.timestamp >= weekStart && it.dwellSeconds <= 0 && it.verse > 0 },
            dwellLast7Days = traceRows.filter { it.timestamp >= weekStart }.sumOf { it.dwellSeconds.coerceAtLeast(0) },
            oldTestamentSeconds = oldSeconds,
            newTestamentSeconds = newSeconds,
            topBooks = rank(books.values.filter { it.chapter == 0 }, limit),
            topChapters = rank(chapters.values, limit),
            topVerses = rank(verses.values, limit),
            topByTime = verses.values
                .filter { it.dwellSeconds > 0 }
                .sortedWith(compareByDescending<Bucket> { it.dwellSeconds }.thenByDescending { it.visits })
                .take(limit)
                .map { it.toRank() },
            topCopied = verses.values
                .filter { it.copies > 0 }
                .sortedWith(compareByDescending<Bucket> { it.copies }.thenByDescending { it.visits })
                .take(limit)
                .map { it.toRank() },
            topListenedBooks = listenedBooks(listenKeys, trackId, limit),
            groups = groups,
            topTools = toolCounts.entries.sortedByDescending { it.value }.take(4).map { it.key to it.value },
        )
    }

    private fun listenedBooks(listenKeys: Set<String>, trackId: String, limit: Int): List<PassageRank> {
        val counts = BibleCoverage.countsByBook(listenKeys, trackId)
        return counts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { (bookId, count) ->
                PassageRank(
                    bookId = bookId,
                    bookName = BibleCanon.byId(bookId)?.nameRu ?: bookId,
                    chapter = 0,
                    verse = 0,
                    visits = 0,
                    dwellSeconds = 0,
                    copies = 0,
                    uniqueVerses = count,
                )
            }
    }

    private fun rank(rows: Collection<Bucket>, limit: Int): List<PassageRank> =
        rows.filter { it.visits > 0 || it.dwellSeconds > 0 || it.copies > 0 || it.uniqueVerses > 0 }
            .sortedWith(
                compareByDescending<Bucket> { it.visits }
                    .thenByDescending { it.dwellSeconds }
                    .thenByDescending { it.copies }
                    .thenByDescending { it.uniqueVerses },
            )
            .take(limit)
            .map { it.toRank() }

    private fun displayBookName(bookId: String, fallback: String): String =
        BibleCanon.byId(bookId)?.nameRu ?: fallback.ifBlank { bookId }

    fun dayEpoch(timestampMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate().toEpochDay()

    fun streaks(days: Set<Long>, today: Long): Pair<Int, Int> {
        if (days.isEmpty()) return 0 to 0
        val sorted = days.toSortedSet().toList()
        var longest = 1
        var run = 1
        for (index in 1 until sorted.size) {
            run = if (sorted[index] == sorted[index - 1] + 1) run + 1 else 1
            if (run > longest) longest = run
        }
        val end = when {
            today in days -> today
            today - 1 in days -> today - 1
            else -> return 0 to longest
        }
        var current = 0
        var day = end
        while (day in days) {
            current++
            day--
        }
        return current to longest
    }

    private fun groupTitle(group: CanonBookGroup): String = when (group) {
        CanonBookGroup.PENTATEUCH -> "Пятикнижие"
        CanonBookGroup.HISTORY -> "Исторические"
        CanonBookGroup.WISDOM -> "Учительные"
        CanonBookGroup.MAJOR_PROPHETS -> "Большие пророки"
        CanonBookGroup.MINOR_PROPHETS -> "Малые пророки"
        CanonBookGroup.GOSPELS -> "Евангелия"
        CanonBookGroup.ACTS -> "Деяния"
        CanonBookGroup.GENERAL_EPISTLES -> "Соборные"
        CanonBookGroup.PAULINE -> "Павел"
        CanonBookGroup.HEBREWS -> "Евреям"
        CanonBookGroup.REVELATION -> "Откровение"
    }

    private class GroupBucket {
        var visits: Int = 0
        var dwellSeconds: Int = 0
        var uniqueVerses: Int = 0
        var chaptersOpened: Int = 0
        val touchedBooks = HashSet<String>()
        var copies: Int = 0
        var listenedChapters: Int = 0
    }

    private class Bucket(
        val bookId: String,
        var bookName: String,
        val chapter: Int,
        val verse: Int,
    ) {
        var visits: Int = 0
        var dwellSeconds: Int = 0
        var copies: Int = 0
        var uniqueVerses: Int = 0

        fun toRank() = PassageRank(bookId, bookName, chapter, verse, visits, dwellSeconds, copies, uniqueVerses)
    }
}
