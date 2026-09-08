package com.example.bible.data

/**
 * Аккорды в тексте песни — как на HolyChords:
 * строка аккордов над строкой слов (пробелами) или ChordPro: `[Am]слово`.
 * Нотация H = си, B = си-бемоль (как на holychords.pro).
 */
object SongChordMarkup {

    /** Токен аккорда: G, Hm, D/F#, Esus2, C#m7. */
    val CHORD_TOKEN = Regex(
        """[A-H](?:#|b)?(?:m|min|maj|dim|aug|sus|add)?[0-9]*(?:/[A-H](?:#|b)?)?""",
    )

    private val CHORD_PRO = Regex("""\[([^]]+)]""")

    /** Немецкая/церковная последовательность, как на HolyChords. */
    val NOTES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "B", "H")

    fun hasChords(text: String): Boolean {
        if (text.isBlank()) return false
        if (CHORD_PRO.containsMatchIn(text)) return true
        return text.lineSequence().any { isChordLine(it) }
    }

    fun isChordLine(line: String): Boolean {
        val t = line.replace("[", "").replace("]", "").trim()
        if (t.isEmpty()) return false
        val leftover = CHORD_TOKEN.replace(t, "")
            .replace(Regex("""[\s|/.\-]+"""), "")
        return leftover.isEmpty() && CHORD_TOKEN.containsMatchIn(t)
    }

    fun stripChords(text: String): String {
        if (text.isBlank()) return text
        return text.lineSequence()
            .filterNot { isChordLine(it) }
            .map { CHORD_PRO.replace(it, "") }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * HTML из `<pre id="music_text">`: сохраняем пробелы и строки аккордов.
     */
    fun fromHtmlFragment(htmlInner: String): String {
        if (htmlInner.isBlank()) return ""
        var s = htmlInner
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        s = s.replace(Regex("<[^>]+>"), "")
        return s.replace(Regex("\n{3,}"), "\n\n").trimEnd().trimStart('\n')
    }

    fun transpose(text: String, semitones: Int): String {
        if (text.isBlank() || semitones == 0) return text
        val n = Math.floorMod(semitones, 12)
        if (n == 0) return text
        return buildString(text.length) {
            var i = 0
            while (i < text.length) {
                if (text[i] == '[') {
                    val close = text.indexOf(']', i + 1)
                    if (close > i) {
                        append('[')
                        append(transposeChordToken(text.substring(i + 1, close), n))
                        append(']')
                        i = close + 1
                        continue
                    }
                }
                val m = CHORD_TOKEN.matchAt(text, i)
                if (m != null && isChordContext(text, i, m.value.length)) {
                    append(transposeChordToken(m.value, n))
                    i += m.value.length
                } else {
                    append(text[i])
                    i++
                }
            }
        }
    }

    /**
     * Строки для показа: аккорд над слогом. ChordPro разворачивается в две строки.
     */
    fun displayLines(text: String, showChords: Boolean, transposeSemitones: Int): List<DisplayLine> {
        val src = if (transposeSemitones == 0) text else transpose(text, transposeSemitones)
        if (!showChords) {
            return stripChords(src).split("\n").map { DisplayLine(it, isChord = false) }
        }
        val out = ArrayList<DisplayLine>()
        for (raw in src.split("\n")) {
            when {
                isChordLine(raw) -> out.add(DisplayLine(unwrapChordMarkers(raw), isChord = true))
                isInlineChordPro(raw) -> {
                    val (chords, lyrics) = expandChordPro(raw)
                    if (chords.isNotBlank()) out.add(DisplayLine(chords, isChord = true))
                    out.add(DisplayLine(lyrics, isChord = false))
                }
                else -> out.add(DisplayLine(raw, isChord = false))
            }
        }
        return out
    }

    /** Уникальные аккорды песни в порядке появления, уже с транспонированием. */
    fun uniqueChordNames(text: String, transposeSemitones: Int = 0): List<String> {
        if (text.isBlank()) return emptyList()
        val src = if (transposeSemitones == 0) text else transpose(text, transposeSemitones)
        val found = linkedSetOf<String>()
        CHORD_PRO.findAll(src).forEach { match ->
            val inner = match.groupValues[1].trim()
            if (CHORD_TOKEN.matchEntire(inner) != null) found.add(inner)
        }
        src.lineSequence().forEach { line ->
            if (!isChordLine(line)) return@forEach
            val clean = line.replace("[", "").replace("]", "")
            CHORD_TOKEN.findAll(clean).forEach { found.add(it.value) }
        }
        return found.toList()
    }

    fun pitchClass(root: String): Int? = noteIndex(root)

    fun noteName(pitchClass: Int): String = NOTES[Math.floorMod(pitchClass, 12)]

    data class ChordSpan(val name: String, val start: Int, val endExclusive: Int)

    sealed class ChordLinePart {
        data class Chord(val name: String, val start: Int) : ChordLinePart()
        data class Gap(val text: String) : ChordLinePart()
    }

    /** Позиции аккордов в уже развёрнутой строке просмотра (`Am    E`). */
    fun chordSpans(line: String): List<ChordSpan> {
        if (line.isBlank()) return emptyList()
        return CHORD_TOKEN.findAll(line).map { match ->
            ChordSpan(match.value, match.range.first, match.range.last + 1)
        }.toList()
    }

    /** Аккорды и исходные пробелы между ними, чтобы не склеивать `Am    E`. */
    fun chordLineParts(line: String): List<ChordLinePart> {
        val spans = chordSpans(line)
        if (spans.isEmpty()) {
            return if (line.isEmpty()) emptyList() else listOf(ChordLinePart.Gap(line))
        }
        val out = ArrayList<ChordLinePart>(spans.size * 2)
        var i = 0
        for (span in spans) {
            if (span.start > i) out.add(ChordLinePart.Gap(line.substring(i, span.start)))
            out.add(ChordLinePart.Chord(span.name, span.start))
            i = span.endExclusive
        }
        if (i < line.length) out.add(ChordLinePart.Gap(line.substring(i)))
        return out
    }

    /** Строка только из аккордов: `[Am]    [E]` — не разворачивать как ChordPro. */
    private fun isInlineChordPro(line: String): Boolean {
        if (!CHORD_PRO.containsMatchIn(line)) return false
        if (isChordLine(line)) return false
        val without = CHORD_PRO.replace(line, "")
        return without.any { it.isLetter() }
    }

    /** `[Am]` → `Am  `, чтобы колонки пробелов совпали с тем, как расставляли в редакторе. */
    private fun unwrapChordMarkers(line: String): String {
        if ('[' !in line) return line
        return CHORD_PRO.replace(line) { match ->
            match.groupValues[1].padEnd(match.value.length)
        }
    }

    private fun expandChordPro(line: String): Pair<String, String> {
        val chords = StringBuilder()
        val lyrics = StringBuilder()
        var i = 0
        while (i < line.length) {
            if (line[i] == '[') {
                val close = line.indexOf(']', i + 1)
                if (close < 0) {
                    lyrics.append(line[i])
                    i++
                    continue
                }
                val chord = line.substring(i + 1, close)
                while (chords.length < lyrics.length) chords.append(' ')
                chords.append(chord)
                i = close + 1
            } else {
                lyrics.append(line[i])
                if (chords.length < lyrics.length) chords.append(' ')
                i++
            }
        }
        return chords.toString().trimEnd() to lyrics.toString()
    }

    private fun isChordContext(text: String, start: Int, len: Int): Boolean {
        val before = if (start == 0) ' ' else text[start - 1]
        val after = if (start + len >= text.length) ' ' else text[start + len]
        val boundaryBefore = before.isWhitespace() || before == '[' || before == '/' || before == '('
        val boundaryAfter = after.isWhitespace() || after == ']' || after == '/' || after == ')' || after == '\n'
        if (!boundaryBefore || !boundaryAfter) return false
        val lineStart = text.lastIndexOf('\n', start - 1) + 1
        val lineEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        return isChordLine(text.substring(lineStart, lineEnd))
    }

    private fun transposeChordToken(token: String, n: Int): String {
        val slash = token.indexOf('/')
        if (slash > 0) {
            return transposeRoot(token.substring(0, slash), n) + "/" +
                transposeRoot(token.substring(slash + 1), n)
        }
        return transposeRoot(token, n)
    }

    private fun transposeRoot(token: String, n: Int): String {
        val rootLen = when {
            token.startsWith("C#") || token.startsWith("D#") ||
                token.startsWith("F#") || token.startsWith("G#") ||
                token.startsWith("A#") || token.startsWith("Db") ||
                token.startsWith("Eb") || token.startsWith("Gb") ||
                token.startsWith("Ab") || token.startsWith("Bb") -> 2
            token.startsWith("Hb") -> 2
            else -> 1
        }
        if (token.length < rootLen) return token
        val root = token.substring(0, rootLen)
        val suffix = token.substring(rootLen)
        val idx = noteIndex(root) ?: return token
        return NOTES[(idx + n) % 12] + suffix
    }

    fun noteIndex(root: String): Int? = when (root) {
        "C" -> 0
        "C#", "Db" -> 1
        "D" -> 2
        "D#", "Eb" -> 3
        "E" -> 4
        "F" -> 5
        "F#", "Gb" -> 6
        "G" -> 7
        "G#", "Ab" -> 8
        "A" -> 9
        "A#", "Bb", "B" -> 10
        "H", "Hb" -> 11
        else -> null
    }

    data class DisplayLine(val text: String, val isChord: Boolean)
}
