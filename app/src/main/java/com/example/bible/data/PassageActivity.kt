package com.example.bible.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Сколько раз стих читали или слушали и когда это было в последний раз.
 * Ключ: `R|перевод|книга|глава|стих` или `L|…`, значение: `число|время`.
 */
object PassageActivity {
    const val READ = "R"
    const val LISTEN = "L"

    data class Hit(
        val chapter: Int,
        val verse: Int,
        val count: Int,
        val lastAt: Long,
    )

    fun bump(
        json: String,
        kind: String,
        trackId: String,
        bookId: String,
        chapter: Int,
        verses: Collection<Int>,
        now: Long,
        alreadyMarked: Set<Int> = emptySet(),
    ): String {
        if (trackId.isBlank() || bookId.isBlank() || chapter <= 0) return json
        val root = parse(json)
        for (verse in verses) {
            if (verse <= 0) continue
            val key = key(kind, trackId, bookId, chapter, verse)
            val (count, _) = decodeValue(root[key].orEmpty())
            val next = when {
                count > 0 -> count + 1
                verse in alreadyMarked -> 2
                else -> 1
            }
            root[key] = "$next|$now"
        }
        return encode(root)
    }

    fun hits(json: String, kind: String, trackId: String, bookId: String): List<Hit> {
        if (json.isBlank() || trackId.isBlank() || bookId.isBlank()) return emptyList()
        val prefix = "$kind|$trackId|$bookId|"
        val root = parse(json)
        val out = ArrayList<Hit>()
        for ((key, value) in root) {
            if (!key.startsWith(prefix)) continue
            val rest = key.substring(prefix.length)
            val bar = rest.indexOf('|')
            if (bar <= 0) continue
            val chapter = rest.substring(0, bar).toIntOrNull() ?: continue
            val verse = rest.substring(bar + 1).toIntOrNull() ?: continue
            val (count, at) = decodeValue(value)
            if (count > 0) out += Hit(chapter, verse, count, at)
        }
        return out
    }

    fun maxCount(json: String, kind: String, trackId: String, bookId: String): Int =
        hits(json, kind, trackId, bookId).maxOfOrNull { it.count } ?: 0

    /** Строки для окна книги: главы и стихи, число раз и последнее время. */
    fun linesForBook(
        json: String,
        kind: String,
        trackId: String,
        bookId: String,
        coveredVerses: Map<Int, Set<Int>>,
    ): List<String> {
        val byVerse = hits(json, kind, trackId, bookId).associateBy { it.chapter to it.verse }
        val chapters = (byVerse.keys.map { it.first } + coveredVerses.keys).distinct().sorted()
        if (chapters.isEmpty()) return emptyList()
        val lines = ArrayList<String>()
        for (chapter in chapters) {
            val verses = LinkedHashMap<Int, Hit>()
            for (verse in coveredVerses[chapter].orEmpty()) {
                verses[verse] = byVerse[chapter to verse] ?: Hit(chapter, verse, 1, 0L)
            }
            for ((key, hit) in byVerse) {
                if (key.first == chapter) verses[hit.verse] = hit
            }
            val sorted = verses.values.sortedBy { it.verse }
            if (sorted.isEmpty()) continue
            var start = sorted.first()
            var prev = start
            var groupLast = start.lastAt
            fun flush(groupEnd: Hit) {
                val span = if (start.verse == groupEnd.verse) {
                    "${start.verse}"
                } else {
                    "${start.verse}–${groupEnd.verse}"
                }
                val whenText = formatWhen(groupLast)
                val whenPart = if (whenText.isEmpty()) "" else " · $whenText"
                lines += "гл. $chapter, стихи $span · ${timesLabel(start.count)}$whenPart"
            }
            for (index in 1 until sorted.size) {
                val hit = sorted[index]
                val sameRun = hit.verse == prev.verse + 1 && hit.count == start.count
                if (sameRun) {
                    prev = hit
                    if (hit.lastAt > groupLast) groupLast = hit.lastAt
                } else {
                    flush(prev)
                    start = hit
                    prev = hit
                    groupLast = hit.lastAt
                }
            }
            flush(prev)
        }
        return lines
    }

    fun timesLabel(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        val word = when {
            mod100 in 11..14 -> "раз"
            mod10 == 1 -> "раз"
            mod10 in 2..4 -> "раза"
            else -> "раз"
        }
        return "$count $word"
    }

    fun formatWhen(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        return date.format(WHEN_FORMAT)
    }

    private fun key(kind: String, trackId: String, bookId: String, chapter: Int, verse: Int) =
        "$kind|$trackId|$bookId|$chapter|$verse"

    private fun parse(raw: String): MutableMap<String, String> {
        val map = LinkedHashMap<String, String>()
        if (raw.isBlank()) return map
        for (part in raw.split(';')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            map[part.substring(0, eq)] = part.substring(eq + 1)
        }
        return map
    }

    private fun encode(map: Map<String, String>): String =
        map.entries.joinToString(";") { (key, value) -> "$key=$value" }

    private fun decodeValue(raw: String): Pair<Int, Long> {
        if (raw.isBlank()) return 0 to 0L
        val bar = raw.indexOf('|')
        if (bar <= 0) return (raw.toIntOrNull() ?: 0) to 0L
        val count = raw.substring(0, bar).toIntOrNull() ?: 0
        val at = raw.substring(bar + 1).toLongOrNull() ?: 0L
        return count to at
    }

    private val WHEN_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))
}
