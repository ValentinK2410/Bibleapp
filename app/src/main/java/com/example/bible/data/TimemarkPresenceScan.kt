package com.example.bible.data

import java.io.Reader

/**
 * Поля проекта таймкодов, которых хватает для точек на плитках.
 * Массив cues целиком не разбирается: важно только, что он не пустой.
 */
internal data class TimemarkPresenceHeader(
    val translationCode: String,
    val bookId: String,
    val chapter: Int,
    val hasCues: Boolean,
)

internal fun scanTimemarkPresence(json: String): TimemarkPresenceHeader? =
    scanTimemarkPresence(json.reader())

/**
 * Один проход по JSON. Как только известны книга, перевод, глава и то,
 * что в cues есть хотя бы один элемент, остаток файла можно не читать.
 * Пустой cues и битый JSON дают тот же итог, что и полный разбор: проект не попадает в индекс.
 */
internal fun scanTimemarkPresence(reader: Reader): TimemarkPresenceHeader? {
    val pull = JsonPull(reader)
    return try {
        pull.skipWs()
        if (pull.peek() == '\uFEFF') pull.next()
        pull.skipWs()
        if (!pull.consume('{')) return null
        var bookId: String? = null
        var translation: String? = null
        var chapter: Int? = null
        var hasCues = false
        while (true) {
            pull.skipWs()
            when (pull.peek()) {
                null -> return null
                '}' -> {
                    pull.next()
                    pull.skipWs()
                    if (pull.peek() != null) return null
                    break
                }
                '"' -> Unit
                else -> return null
            }
            val key = pull.parseString() ?: return null
            pull.skipWs()
            if (!pull.consume(':')) return null
            when (key) {
                "bookId" -> bookId = pull.parseString() ?: return null
                "translationCode" -> translation = pull.parseString() ?: return null
                "chapter" -> chapter = pull.parseInt() ?: return null
                "cues" -> {
                    pull.skipWs()
                    if (pull.peek() != '[') {
                        if (!pull.skipValue()) return null
                        hasCues = false
                    } else {
                        val nonEmpty = pull.arrayHasElement() ?: return null
                        hasCues = nonEmpty
                        if (nonEmpty) {
                            if (isPresenceReady(bookId, translation, chapter)) {
                                return TimemarkPresenceHeader(translation!!, bookId!!, chapter!!, true)
                            }
                            if (!pull.skipRestOfNonEmptyArray()) return null
                        }
                    }
                }
                else -> if (!pull.skipValue()) return null
            }
            pull.skipWs()
            when (pull.peek()) {
                ',' -> {
                    pull.next()
                    pull.skipWs()
                    if (pull.peek() == '}') return null
                }
                '}' -> Unit
                else -> return null
            }
        }
        if (!isPresenceReady(bookId, translation, chapter)) return null
        TimemarkPresenceHeader(translation!!, bookId!!, chapter!!, hasCues)
    } catch (_: Exception) {
        null
    }
}

private fun isPresenceReady(bookId: String?, translation: String?, chapter: Int?): Boolean =
    !bookId.isNullOrBlank() && !translation.isNullOrBlank() && chapter != null

private class JsonPull(private val reader: Reader) {
    private var pushed: Int = EMPTY

    fun peek(): Char? {
        if (pushed == EMPTY) pushed = reader.read()
        if (pushed < 0) return null
        return pushed.toChar()
    }

    fun next(): Char? {
        val c = peek() ?: return null
        pushed = EMPTY
        return c
    }

    fun consume(expected: Char): Boolean {
        if (peek() != expected) return false
        pushed = EMPTY
        return true
    }

    fun skipWs() {
        while (true) {
            when (peek()) {
                ' ', '\n', '\r', '\t' -> pushed = EMPTY
                else -> return
            }
        }
    }

    fun parseString(): String? {
        skipWs()
        if (!consume('"')) return null
        val out = StringBuilder()
        while (true) {
            when (val c = next() ?: return null) {
                '"' -> return out.toString()
                '\\' -> {
                    when (val esc = next() ?: return null) {
                        '"', '\\', '/' -> out.append(esc)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            val hex = readHex4() ?: return null
                            out.append(hex.toInt(16).toChar())
                        }
                        else -> return null
                    }
                }
                else -> out.append(c)
            }
        }
    }

    fun parseInt(): Int? {
        skipWs()
        val negative = consume('-')
        if (peek() !in '0'..'9') return null
        var value = 0
        while (true) {
            val c = peek() ?: break
            if (c !in '0'..'9') break
            next()
            val digit = c.code - '0'.code
            if (value > (Int.MAX_VALUE - digit) / 10) return null
            value = value * 10 + digit
        }
        if (peek() == '.' || peek() == 'e' || peek() == 'E') return null
        if (negative && value == 0) return null
        return if (negative) -value else value
    }

    /** Курсор на `[`. true — в массиве есть элемент, курсор на нём. */
    fun arrayHasElement(): Boolean? {
        if (!consume('[')) return null
        skipWs()
        if (consume(']')) return false
        if (peek() == null) return null
        return true
    }

    /** Курсор на первом элементе непустого массива. Дочитывает до `]`. */
    fun skipRestOfNonEmptyArray(): Boolean {
        if (!skipValue()) return false
        while (true) {
            skipWs()
            when (peek()) {
                ',' -> {
                    next()
                    skipWs()
                    if (peek() == ']') return false
                    if (!skipValue()) return false
                }
                ']' -> {
                    next()
                    return true
                }
                else -> return false
            }
        }
    }

    fun skipValue(): Boolean {
        skipWs()
        return when (peek()) {
            '"' -> skipString()
            '{' -> skipObject()
            '[' -> skipArray()
            't' -> consumeLiteral("true")
            'f' -> consumeLiteral("false")
            'n' -> consumeLiteral("null")
            '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> skipNumber()
            else -> false
        }
    }

    private fun skipObject(): Boolean {
        if (!consume('{')) return false
        skipWs()
        if (consume('}')) return true
        while (true) {
            if (!skipString()) return false
            skipWs()
            if (!consume(':')) return false
            if (!skipValue()) return false
            skipWs()
            when {
                consume(',') -> {
                    skipWs()
                    if (peek() == '}') return false
                }
                consume('}') -> return true
                else -> return false
            }
        }
    }

    private fun skipArray(): Boolean {
        if (!consume('[')) return false
        skipWs()
        if (consume(']')) return true
        while (true) {
            if (!skipValue()) return false
            skipWs()
            when {
                consume(',') -> {
                    skipWs()
                    if (peek() == ']') return false
                }
                consume(']') -> return true
                else -> return false
            }
        }
    }

    private fun skipString(): Boolean {
        if (!consume('"')) return false
        while (true) {
            when (val c = next() ?: return false) {
                '"' -> return true
                '\\' -> {
                    val esc = next() ?: return false
                    if (esc == 'u' && readHex4() == null) return false
                }
            }
        }
    }

    private fun skipNumber(): Boolean {
        consume('-')
        if (peek() !in '0'..'9') return false
        while (peek() in '0'..'9') next()
        if (consume('.')) {
            if (peek() !in '0'..'9') return false
            while (peek() in '0'..'9') next()
        }
        if (peek() == 'e' || peek() == 'E') {
            next()
            if (peek() == '+' || peek() == '-') next()
            if (peek() !in '0'..'9') return false
            while (peek() in '0'..'9') next()
        }
        return true
    }

    private fun consumeLiteral(text: String): Boolean {
        for (ch in text) {
            if (!consume(ch)) return false
        }
        return true
    }

    private fun readHex4(): String? {
        val chars = CharArray(4)
        for (i in 0 until 4) {
            val c = next() ?: return null
            val hex = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
            if (!hex) return null
            chars[i] = c
        }
        return String(chars)
    }

    private companion object {
        const val EMPTY = Int.MIN_VALUE
    }
}
