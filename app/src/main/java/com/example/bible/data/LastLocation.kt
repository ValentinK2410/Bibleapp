package com.example.bible.data

import org.json.JSONObject

/**
 * Точное место чтения: перевод, книга, глава и стих у верхнего края.
 * Для сравнения переводов второе окно — поля companion*.
 */
data class ReadingAnchor(
    val translationCode: String,
    val bookId: String,
    val chapter: Int,
    val verse: Int,
    val companionTranslationCode: String = "",
    val companionBookId: String = "",
    val companionChapter: Int = 0,
    val companionVerse: Int = 0,
) {
    fun isPrecise(): Boolean =
        translationCode.isNotBlank() && bookId.isNotBlank() && chapter >= 1 && verse >= 1
}

/**
 * Последний экран приложения: шаблон маршрута Navigation и значения аргументов.
 * [reading] заполняется для читалки и сравнения переводов и переживает прокрутку,
 * которая не меняет аргументы back stack.
 */
data class LastLocation(
    val routePattern: String,
    val args: Map<String, String>,
    val translationCode: String = "",
    val reading: ReadingAnchor? = null,
) {
    fun navigableRoute(): String? {
        val enriched = reading?.let { withReading(it) } ?: this
        return buildNavigableRoute(enriched.routePattern, enriched.args)
    }

    fun restoresGlobalTranslation(): Boolean {
        val head = routeHead(routePattern)
        return head == "read" || head == "dual" || head == "commentary"
    }

    fun acceptsReadingUpdates(): Boolean {
        val head = routeHead(routePattern)
        return head == "read" || head == "dual"
    }

    fun withReading(anchor: ReadingAnchor): LastLocation {
        if (!anchor.isPrecise()) return this
        val head = routeHead(routePattern)
        val next = args.toMutableMap()
        when (head) {
            "read" -> {
                next["bookId"] = anchor.bookId
                next["chapter"] = anchor.chapter.toString()
                next["scrollVerse"] = anchor.verse.toString()
            }
            "dual" -> {
                next["bookId"] = anchor.bookId
                next["chapter"] = anchor.chapter.toString()
                next["verse"] = anchor.verse.toString()
                next["primary"] = anchor.translationCode
                putOrRemove(next, "companion", anchor.companionTranslationCode)
                putOrRemove(next, "cBook", anchor.companionBookId)
                putOrRemove(next, "cChapter", positiveInt(anchor.companionChapter))
                putOrRemove(next, "cVerse", positiveInt(anchor.companionVerse))
            }
            else -> return this
        }
        return copy(
            args = next,
            translationCode = anchor.translationCode,
            reading = anchor,
        )
    }

    fun toJson(): String {
        val root = JSONObject()
        root.put("route", routePattern)
        root.put("tr", translationCode)
        val argsObj = JSONObject()
        args.keys.sorted().forEach { key -> argsObj.put(key, args.getValue(key)) }
        root.put("args", argsObj)
        reading?.let { anchor ->
            root.put(
                "read",
                JSONObject().apply {
                    put("tr", anchor.translationCode)
                    put("b", anchor.bookId)
                    put("c", anchor.chapter)
                    put("v", anchor.verse)
                    put("tr2", anchor.companionTranslationCode)
                    put("b2", anchor.companionBookId)
                    put("c2", anchor.companionChapter)
                    put("v2", anchor.companionVerse)
                },
            )
        }
        return root.toString()
    }

    companion object {
        /** Главный экран и мгновенные редиректы в сетку книг не запоминаем. */
        fun shouldRecord(routePattern: String): Boolean {
            val path = routePattern.substringBefore("?").trim()
            if (path.isEmpty()) return false
            val head = path.substringBefore("/")
            if (head == "books") return false
            if (path == "chapters/{bookId}" || path == "verses/{bookId}/{chapter}") return false
            return true
        }

        fun argumentNames(routePattern: String): List<String> =
            ARG_PLACEHOLDER.findAll(routePattern).map { it.groupValues[1] }.distinct().toList()

        fun capture(
            routePattern: String,
            args: Map<String, String>,
            translationCode: String,
        ): LastLocation? {
            if (!shouldRecord(routePattern)) return null
            val path = routePattern.substringBefore("?")
            val pathNames = argumentNames(path)
            if (pathNames.any { args[it].isNullOrBlank() }) return null
            val location = LastLocation(
                routePattern = routePattern,
                args = args.filterValues { it.isNotBlank() },
                translationCode = translationCode,
            )
            return readingFromRoute(location)?.let { location.withReading(it) } ?: location
        }

        fun fromJson(raw: String): LastLocation? {
            if (raw.isBlank()) return null
            return try {
                val root = JSONObject(raw)
                val route = root.optString("route", "")
                if (route.isBlank() || !shouldRecord(route)) return null
                val argsObj = root.optJSONObject("args")
                val args = buildMap {
                    if (argsObj != null) {
                        val keys = argsObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = argsObj.optString(key, "")
                            if (value.isNotBlank()) put(key, value)
                        }
                    }
                }
                val translation = root.optString("tr", "")
                val reading = root.optJSONObject("read")?.let { read ->
                    ReadingAnchor(
                        translationCode = read.optString("tr", translation),
                        bookId = read.optString("b", ""),
                        chapter = read.optInt("c", 0),
                        verse = read.optInt("v", 0),
                        companionTranslationCode = read.optString("tr2", ""),
                        companionBookId = read.optString("b2", ""),
                        companionChapter = read.optInt("c2", 0),
                        companionVerse = read.optInt("v2", 0),
                    ).takeIf { it.isPrecise() }
                }
                val location = LastLocation(route, args, translation, reading)
                reading?.let { location.withReading(it) } ?: location
            } catch (_: Exception) {
                null
            }
        }

        private fun readingFromRoute(location: LastLocation): ReadingAnchor? {
            val head = routeHead(location.routePattern)
            val args = location.args
            return when (head) {
                "read" -> ReadingAnchor(
                    translationCode = location.translationCode,
                    bookId = args["bookId"].orEmpty(),
                    chapter = args["chapter"]?.toIntOrNull() ?: 0,
                    verse = args["scrollVerse"]?.toIntOrNull() ?: 0,
                ).takeIf { it.isPrecise() }
                "dual" -> {
                    val verse = args["verse"]?.toIntOrNull() ?: 0
                    val translation = args["primary"].orEmpty().ifBlank { location.translationCode }
                    ReadingAnchor(
                        translationCode = translation,
                        bookId = args["bookId"].orEmpty(),
                        chapter = args["chapter"]?.toIntOrNull() ?: 0,
                        verse = verse,
                        companionTranslationCode = args["companion"].orEmpty(),
                        companionBookId = args["cBook"].orEmpty(),
                        companionChapter = args["cChapter"]?.toIntOrNull() ?: 0,
                        companionVerse = args["cVerse"]?.toIntOrNull() ?: 0,
                    ).takeIf { it.isPrecise() }
                }
                else -> null
            }
        }
    }
}

fun routeHead(routePattern: String): String =
    routePattern.substringBefore("?").substringBefore("/").trim()

fun buildNavigableRoute(routePattern: String, args: Map<String, String>): String? {
    if (routePattern.isBlank()) return null
    val path = routePattern.substringBefore("?")
    val query = routePattern.substringAfter("?", "")
    var pathOut = path
    for (name in LastLocation.argumentNames(path)) {
        val value = args[name]
        if (value.isNullOrBlank()) return null
        pathOut = pathOut.replace("{$name}", encodeRouteComponent(value))
    }
    if (query.isEmpty()) return pathOut
    val pairs = query.split("&").mapNotNull { piece ->
        val key = piece.substringBefore("=")
        val placeholder = piece.substringAfter("=", "")
        val name = ARG_PLACEHOLDER.find(placeholder)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
        val value = args[name]
        if (value.isNullOrBlank() || value == "0") null
        else "$key=${encodeRouteComponent(value)}"
    }
    return if (pairs.isEmpty()) pathOut else "$pathOut?${pairs.joinToString("&")}"
}

/** Как [android.net.Uri.encode]: всё, кроме RFC 3986 unreserved, в %XX. */
fun encodeRouteComponent(value: String): String {
    val bytes = value.toByteArray(Charsets.UTF_8)
    val out = StringBuilder(bytes.size)
    for (byte in bytes) {
        val c = byte.toInt() and 0xFF
        if (c.isUnreservedRouteByte()) {
            out.append(c.toChar())
        } else {
            out.append('%')
            out.append(HEX[c ushr 4])
            out.append(HEX[c and 0x0F])
        }
    }
    return out.toString()
}

/**
 * Сессия «куда вернуться». Диск пишет вызывающий код: прокрутка стиха копится
 * [DEBOUNCE_MS], смена экрана и уход с читалки фиксируют место сразу.
 * Для одного и того же back stack entry уточнённый стих не затирается исходным аргументом маршрута.
 */
class LastLocationSession(
    initial: LastLocation? = null,
) {
    var current: LastLocation? = initial
        private set

    private var lastEntryKey: String = ""
    private var pending: LastLocation? = null
    private var pendingAtMs: Long = 0L
    private val refinedByEntry = mutableMapOf<String, ReadingAnchor>()

    /**
     * @param captured null — этот экран не запоминаем (главный «Книги» и редиректы),
     * предыдущее место остаётся.
     * @return запись, которую нужно сохранить, если она изменилась.
     */
    fun onDestination(entryKey: String, captured: LastLocation?): LastLocation? {
        if (entryKey.isNotEmpty() && entryKey == lastEntryKey) return null
        if (entryKey.isNotEmpty()) lastEntryKey = entryKey
        val before = current
        pending?.let {
            current = it
            pending = null
        }
        if (captured != null) {
            val refined = refinedByEntry[entryKey]
            current = if (
                refined != null &&
                captured.acceptsReadingUpdates() &&
                sameReadingPlace(captured, refined)
            ) {
                captured.withReading(refined)
            } else {
                captured
            }
        }
        return current?.takeIf { it != before }
    }

    /**
     * Верхний видимый стих. true — место изменилось и его нужно записать после паузы.
     */
    fun noteReading(anchor: ReadingAnchor, nowMs: Long): Boolean {
        if (!anchor.isPrecise()) return false
        val base = pending ?: current ?: return false
        if (!base.acceptsReadingUpdates()) return false
        if (!anchorMatchesOpenReader(base, anchor)) return false
        if (lastEntryKey.isNotEmpty()) refinedByEntry[lastEntryKey] = anchor
        val updated = base.withReading(anchor)
        if (updated == (pending ?: current)) return false
        pending = updated
        pendingAtMs = nowMs
        return true
    }

    fun flushDue(nowMs: Long): LastLocation? {
        val next = pending ?: return null
        if (nowMs - pendingAtMs < DEBOUNCE_MS) return null
        return commitPending(next)
    }

    fun flushNow(): LastLocation? {
        val next = pending ?: return null
        return commitPending(next)
    }

    private fun commitPending(next: LastLocation): LastLocation? {
        pending = null
        if (next == current) return null
        current = next
        return next
    }

    /** Читалка не меняет книгу и главу без нового маршрута. Сравнение переводов — меняет. */
    private fun anchorMatchesOpenReader(open: LastLocation, anchor: ReadingAnchor): Boolean {
        if (routeHead(open.routePattern) != "read") return true
        val book = open.args["bookId"]
        val chapter = open.args["chapter"]
        if (!book.isNullOrBlank() && book != anchor.bookId) return false
        if (!chapter.isNullOrBlank() && chapter != "0" && chapter != anchor.chapter.toString()) return false
        return true
    }

    private fun sameReadingPlace(captured: LastLocation, refined: ReadingAnchor): Boolean {
        return when (routeHead(captured.routePattern)) {
            "read" ->
                captured.args["bookId"] == refined.bookId &&
                    captured.args["chapter"] == refined.chapter.toString()
            "dual" -> true
            else -> false
        }
    }

    companion object {
        const val DEBOUNCE_MS = 400L
    }
}

private val ARG_PLACEHOLDER = Regex("\\{([A-Za-z_][A-Za-z0-9_]*)\\}")
private val HEX = "0123456789ABCDEF".toCharArray()

private fun Int.isUnreservedRouteByte(): Boolean =
    this in 'A'.code..'Z'.code ||
        this in 'a'.code..'z'.code ||
        this in '0'.code..'9'.code ||
        this == '-'.code || this == '_'.code || this == '.'.code || this == '~'.code

private fun positiveInt(value: Int): String = if (value > 0) value.toString() else ""

private fun putOrRemove(args: MutableMap<String, String>, key: String, value: String) {
    if (value.isBlank()) args.remove(key) else args[key] = value
}
