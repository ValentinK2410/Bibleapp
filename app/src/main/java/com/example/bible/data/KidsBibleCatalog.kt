package com.example.bible.data

import android.content.Context

internal data class KidsBibleCue(
    val part: String,
    val startMs: Int,
    val endMs: Int,
)

internal enum class KidsBibleAge(val id: String, val label: String) {
    Y5_7("5_7", "5–7"),
    Y8_10("8_10", "8–10"),
    Y11_13("11_13", "11–13"),
    ;

    companion object {
        fun fromId(id: String?): KidsBibleAge = entries.firstOrNull { it.id == id } ?: Y8_10
    }
}

/** Голос приложения или будущая запись мамы/папы. Экран выбора пока не нужен. */
internal enum class KidsBibleNarration { App, Family }

internal data class KidsBibleTrack(
    val paragraphs: List<String>,
    val meaning: String,
    val christLink: String,
    val questions: List<String>,
    val prayer: String,
    val activity: String,
    val cues: List<KidsBibleCue> = emptyList(),
)

internal data class KidsBibleReading(
    val id: String,
    val bookId: String,
    val order: Int,
    val title: String,
    val refs: String,
    val kind: String,
    val keyVerse: String,
    val keyVerseRef: String,
    val art: String,
    val tracks: Map<KidsBibleAge, KidsBibleTrack>,
) {
    val isOverview: Boolean get() = kind == "overview"

    fun track(age: KidsBibleAge): KidsBibleTrack =
        tracks[age] ?: tracks.getValue(KidsBibleAge.Y8_10)

    fun audioAsset(age: KidsBibleAge): String = "kids_bible/audio/${id}_${age.id}.m4a"

    fun familyAudioAsset(age: KidsBibleAge): String = "kids_bible/audio/family/${id}_${age.id}.m4a"

    fun narrationAsset(age: KidsBibleAge, narration: KidsBibleNarration): String =
        if (narration == KidsBibleNarration.Family) familyAudioAsset(age) else audioAsset(age)
}

internal data class KidsBibleBookRow(
    val book: CanonBookEntry,
    val stories: List<KidsBibleReading>,
    val comingSoon: Boolean,
)

internal object KidsBibleCatalog {
    fun parseStories(raw: String): List<KidsBibleReading> {
        val root = JsonTok(raw).parse() as? Map<*, *> ?: error("Нет каталога историй")
        val arr = root["stories"] as? List<*> ?: emptyList<Any>()
        return arr.mapNotNull { item ->
            val o = item as? Map<*, *> ?: return@mapNotNull null
            val trackMap = o["tracks"] as? Map<*, *> ?: error("Нет возрастов у ${o.str("id")}")
            val tracks = KidsBibleAge.entries.associateWith { age ->
                val rawTrack = trackMap[age.id] as? Map<*, *> ?: error("Нет текста ${age.id} у ${o.str("id")}")
                rawTrack.toTrack()
            }
            KidsBibleReading(
                id = o.str("id"),
                bookId = o.str("bookId"),
                order = o.num("order"),
                title = o.str("title"),
                refs = o.str("refs"),
                kind = o.str("kind").ifBlank { "story" },
                keyVerse = o.str("keyVerse"),
                keyVerseRef = o.str("keyVerseRef"),
                art = o.str("art"),
                tracks = tracks,
            )
        }
    }

    fun parseCues(raw: String): List<KidsBibleCue> {
        val root = JsonTok(raw).parse() as? Map<*, *> ?: return emptyList()
        val arr = root["cues"] as? List<*> ?: return emptyList()
        return arr.mapNotNull { item ->
            val o = item as? Map<*, *> ?: return@mapNotNull null
            KidsBibleCue(o.str("part"), o.num("startMs"), o.num("endMs"))
        }
    }

    fun shelf(stories: List<KidsBibleReading>): List<KidsBibleBookRow> =
        BibleCanon.allBooks.map { book ->
            val items = stories.filter { it.bookId == book.id }.sortedBy { it.order }
            KidsBibleBookRow(book, items, comingSoon = items.isEmpty())
        }

    fun load(context: Context): List<KidsBibleReading> {
        val raw = context.assets.open("kids_bible/stories.json").bufferedReader().use { it.readText() }
        return parseStories(raw).map { story ->
            story.copy(
                tracks = story.tracks.mapValues { (age, track) ->
                    val cues = runCatching {
                        context.assets.open("kids_bible/audio/${story.id}_${age.id}.timings.json")
                            .bufferedReader()
                            .use { it.readText() }
                    }.map { parseCues(it) }.getOrDefault(emptyList())
                    track.copy(cues = cues)
                },
            )
        }
    }

    fun groupTitle(group: CanonBookGroup): String = when (group) {
        CanonBookGroup.PENTATEUCH -> "Пятикнижие"
        CanonBookGroup.HISTORY -> "Исторические"
        CanonBookGroup.WISDOM -> "Учительные"
        CanonBookGroup.MAJOR_PROPHETS -> "Большие пророки"
        CanonBookGroup.MINOR_PROPHETS -> "Малые пророки"
        CanonBookGroup.GOSPELS -> "Евангелия"
        CanonBookGroup.ACTS -> "Деяния"
        CanonBookGroup.GENERAL_EPISTLES -> "Соборные послания"
        CanonBookGroup.PAULINE -> "Послания Павла"
        CanonBookGroup.HEBREWS -> "Евреям"
        CanonBookGroup.REVELATION -> "Откровение"
    }
}

private fun Map<*, *>.str(key: String): String = this[key] as? String ?: ""

private fun Map<*, *>.num(key: String): Int = when (val v = this[key]) {
    is Int -> v
    is Long -> v.toInt()
    is Double -> v.toInt()
    is String -> v.toIntOrNull() ?: 0
    else -> 0
}

private fun Map<*, *>.strings(key: String): List<String> =
    (this[key] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

private fun Map<*, *>.toTrack(): KidsBibleTrack = KidsBibleTrack(
    paragraphs = strings("paragraphs"),
    meaning = str("meaning"),
    christLink = str("christLink"),
    questions = strings("questions"),
    prayer = str("prayer"),
    activity = str("activity"),
)

/** Небольшой разбор JSON без android.jar, чтобы тесты и телефон читали один файл. */
private class JsonTok(private val s: String) {
    private var i = 0

    fun parse(): Any? {
        skip()
        return value()
    }

    private fun value(): Any? {
        skip()
        return when (val c = peek()) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> lit("true", true)
            'f' -> lit("false", false)
            'n' -> lit("null", null)
            else -> if (c == '-' || c.isDigit()) num() else error("JSON на $i")
        }
    }

    private fun obj(): Map<String, Any?> {
        expect('{')
        val map = linkedMapOf<String, Any?>()
        skip()
        if (peek() == '}') {
            i++
            return map
        }
        while (true) {
            skip()
            val key = str()
            skip()
            expect(':')
            map[key] = value()
            skip()
            when (peek()) {
                ',' -> i++
                '}' -> {
                    i++
                    return map
                }
                else -> error("JSON объект на $i")
            }
        }
    }

    private fun arr(): List<Any?> {
        expect('[')
        val list = mutableListOf<Any?>()
        skip()
        if (peek() == ']') {
            i++
            return list
        }
        while (true) {
            list += value()
            skip()
            when (peek()) {
                ',' -> i++
                ']' -> {
                    i++
                    return list
                }
                else -> error("JSON массив на $i")
            }
        }
    }

    private fun str(): String {
        expect('"')
        val out = StringBuilder()
        while (i < s.length) {
            when (val c = s[i++]) {
                '"' -> return out.toString()
                '\\' -> {
                    val e = s[i++]
                    out.append(
                        when (e) {
                            '"', '\\', '/' -> e
                            'b' -> '\b'
                            'f' -> '\u000c'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'u' -> s.substring(i, i + 4).toInt(16).toChar().also { i += 4 }
                            else -> e
                        },
                    )
                }
                else -> out.append(c)
            }
        }
        error("Строка не закрыта")
    }

    private fun num(): Int {
        val start = i
        if (peek() == '-') i++
        while (i < s.length && s[i].isDigit()) i++
        if (i < s.length && s[i] == '.') {
            i++
            while (i < s.length && s[i].isDigit()) i++
        }
        return s.substring(start, i).toDouble().toInt()
    }

    private fun lit(word: String, v: Any?): Any? {
        check(s.startsWith(word, i))
        i += word.length
        return v
    }

    private fun skip() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun peek(): Char = s[i]

    private fun expect(c: Char) {
        if (s[i] != c) error("Ждали $c на $i")
        i++
    }
}
