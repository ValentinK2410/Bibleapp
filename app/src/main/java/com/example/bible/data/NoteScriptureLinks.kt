package com.example.bible.data

import com.example.bible.data.genealogy.GenealogyScriptureParser

/** Режим озвучки по ссылке из заметки. */
enum class ScriptureAudioPlayMode(val code: String) {
    /** Один стих. */
    VERSE("v"),
    /** Непрерывный диапазон стихов. */
    RANGE("r"),
    /** От указанного стиха до конца главы. */
    TO_CHAPTER_END("e"),
    /** Несколько сегментов: 1-3,8,11-12,* */
    SEGMENTS("s"),
    ;

    companion object {
        fun fromCode(code: String): ScriptureAudioPlayMode =
            entries.find { it.code == code } ?: VERSE
    }
}

/** Сегмент озвучки: от startVerse до endVerseInclusive или до конца главы. */
data class ScriptureAudioSegment(
    val startVerse: Int,
    val endVerseInclusive: Int?,
)

/** Параметры озвучки, закодированные в ссылке заметки. */
data class ScriptureAudioNavigation(
    val mode: ScriptureAudioPlayMode,
    val translationCode: String,
    val segmentSpec: String? = null,
)

/** Распознанная навигация по ссылке в заметке. */
data class ParsedScriptureNavigation(
    val bookId: String,
    val chapter: Int,
    val verses: Set<Int>,
    val audio: ScriptureAudioNavigation? = null,
)

/** Диапазон ссылки на Писание внутри текста заметки. */
data class ScriptureLinkRange(
    val start: Int,
    val end: Int,
    val bookId: String,
    val chapter: Int,
    val verses: Set<Int>,
    val isAudioLink: Boolean = false,
    val audioPlayMode: ScriptureAudioPlayMode = ScriptureAudioPlayMode.VERSE,
    val translationCode: String? = null,
    val segmentSpec: String? = null,
) {
    val verse: Int get() = verses.minOrNull() ?: 1
    val verseEnd: Int get() = verses.maxOrNull() ?: verse

    fun toParsedNavigation(): ParsedScriptureNavigation = ParsedScriptureNavigation(
        bookId = bookId,
        chapter = chapter,
        verses = verses,
        audio = if (isAudioLink && translationCode != null) {
            ScriptureAudioNavigation(audioPlayMode, translationCode, segmentSpec)
        } else {
            null
        },
    )
}

/**
 * Поиск ссылок вида «Исаия 41:3», «🔊 Рим 6:22@rbo», «🔊 Рим 6:1-3,8,11-12,*@rbo».
 */
object NoteScriptureLinks {

    private val chapterWithVerseSpec = Regex("""^(\d+)\s*:\s*([\d,\-*–—]+)""")
    private val chapterOnlyInRest = Regex("""^(\d+)""")
    private val audioPrefixes = listOf("🔊", "🎧")

    /** Разбор спецификации сегментов: «1-3,8,11-12,*». */
    fun parseSegmentSpec(spec: String, chapterVerseCount: Int): List<ScriptureAudioSegment> {
        val tokens = spec.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val segments = mutableListOf<ScriptureAudioSegment>()
        var maxEnd = 0
        for (token in tokens) {
            when {
                token == "*" -> {
                    val start = (maxEnd + 1).coerceAtLeast(1)
                    if (chapterVerseCount <= 0 || start <= chapterVerseCount) {
                        segments.add(ScriptureAudioSegment(start, null))
                    }
                }
                token.contains('-') || token.contains('–') || token.contains('—') -> {
                    val parts = token.split(Regex("""[-–—]"""), limit = 2)
                    val a = parts[0].trim().toIntOrNull() ?: continue
                    val b = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: a
                    val from = minOf(a, b)
                    val to = maxOf(a, b)
                    segments.add(ScriptureAudioSegment(from, to))
                    maxEnd = maxOf(maxEnd, to)
                }
                else -> {
                    val v = token.toIntOrNull() ?: continue
                    segments.add(ScriptureAudioSegment(v, v))
                    maxEnd = maxOf(maxEnd, v)
                }
            }
        }
        return segments
    }

    fun versesForSegment(segment: ScriptureAudioSegment, chapterVerseCount: Int): Set<Int> {
        val end = segment.endVerseInclusive
            ?: chapterVerseCount.coerceAtLeast(segment.startVerse)
        if (end < segment.startVerse) return setOf(segment.startVerse)
        return (segment.startVerse..end).toSet()
    }

    fun expandSegmentSpecToVerses(spec: String, chapterVerseCount: Int): Set<Int> =
        parseSegmentSpec(spec, chapterVerseCount)
            .flatMap { versesForSegment(it, chapterVerseCount).toList() }
            .toSet()

    fun segmentCountForNavigation(nav: ParsedScriptureNavigation, chapterVerseCount: Int = 999): Int {
        val spec = nav.audio?.segmentSpec
        if (spec != null) return parseSegmentSpec(spec, chapterVerseCount).size.coerceAtLeast(1)
        return 1
    }

    fun allAudioSegmentsForNavigation(
        nav: ParsedScriptureNavigation,
        chapterVerseCount: Int,
    ): List<ScriptureAudioSegment> {
        nav.audio?.segmentSpec?.let { spec ->
            val segments = parseSegmentSpec(spec, chapterVerseCount)
            if (segments.isNotEmpty()) return coalesceAdjacentAudioSegments(segments)
        }
        return listOf(resolveAudioSegment(nav, 0, chapterVerseCount))
    }

    /** «12,13» и «11-12,13» — один непрерывный отрезок, без паузы между соседними стихами. */
    fun coalesceAdjacentAudioSegments(
        segments: List<ScriptureAudioSegment>,
    ): List<ScriptureAudioSegment> {
        if (segments.size <= 1) return segments
        val out = ArrayList<ScriptureAudioSegment>(segments.size)
        for (seg in segments) {
            val last = out.lastOrNull()
            val lastEnd = last?.endVerseInclusive
            val nextEnd = seg.endVerseInclusive
            if (last != null && lastEnd != null && nextEnd != null && seg.startVerse <= lastEnd + 1) {
                out[out.lastIndex] = last.copy(endVerseInclusive = maxOf(lastEnd, nextEnd))
            } else {
                out += seg
            }
        }
        return out
    }

    /** Непрерывное воспроизведение всех сегментов ссылки без повторного нажатия. */
    fun shouldAutoPlayAllSegments(mode: ScriptureAudioPlayMode): Boolean =
        mode == ScriptureAudioPlayMode.RANGE || mode == ScriptureAudioPlayMode.SEGMENTS

    fun resolveAudioSegment(
        nav: ParsedScriptureNavigation,
        segmentIndex: Int,
        chapterVerseCount: Int,
    ): ScriptureAudioSegment {
        when (nav.audio?.mode) {
            ScriptureAudioPlayMode.TO_CHAPTER_END -> {
                val v = nav.verses.minOrNull() ?: 1
                return ScriptureAudioSegment(v, null)
            }
            else -> Unit
        }
        nav.audio?.segmentSpec?.let { spec ->
            val segments = parseSegmentSpec(spec, chapterVerseCount)
            if (segments.isNotEmpty()) {
                return segments[segmentIndex.coerceIn(0, segments.lastIndex)]
            }
        }
        return when (nav.audio?.mode) {
            ScriptureAudioPlayMode.RANGE -> {
                val min = nav.verses.minOrNull() ?: 1
                val max = nav.verses.maxOrNull() ?: min
                ScriptureAudioSegment(min, max)
            }
            ScriptureAudioPlayMode.TO_CHAPTER_END -> {
                val v = nav.verses.minOrNull() ?: 1
                ScriptureAudioSegment(v, null)
            }
            else -> {
                val v = nav.verses.minOrNull() ?: 1
                ScriptureAudioSegment(v, v)
            }
        }
    }

    fun findInText(text: String): List<ScriptureLinkRange> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<ScriptureLinkRange>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (!ch.isLetter() && !ch.isDigit() && !isAudioPrefixStart(text, i)) {
                i++
                continue
            }
            val linkStart = i
            var isAudio = false
            for (prefix in audioPrefixes) {
                if (text.startsWith(prefix, i)) {
                    isAudio = true
                    i += prefix.length
                    break
                }
            }
            while (i < text.length && text[i].isWhitespace()) i++
            val slice = text.substring(i)
            val bookMatch = GenealogyScriptureParser.matchBook(slice) ?: run {
                i = linkStart + 1
                continue
            }
            val bookPartLen = slice.length - bookMatch.rest.length
            val parsed = parseReferenceTail(bookMatch.rest) ?: run {
                i = linkStart + 1
                continue
            }
            var end = i + bookPartLen + parsed.consumed
            var audioMode = detectAudioMode(parsed)
            var segmentSpec = parsed.segmentSpec
            if (isAudio && end < text.length && text[end] == '→') {
                audioMode = ScriptureAudioPlayMode.TO_CHAPTER_END
                if (segmentSpec == null) {
                    segmentSpec = parsed.verses.minOrNull()?.toString()
                }
                end++
            }
            var translationCode: String? = null
            if (isAudio && end < text.length && text[end] == '@') {
                val codeStart = end + 1
                var codeEnd = codeStart
                while (codeEnd < text.length && (text[codeEnd].isLetterOrDigit() || text[codeEnd] == '_')) {
                    codeEnd++
                }
                if (codeEnd > codeStart) {
                    translationCode = text.substring(codeStart, codeEnd)
                    end = codeEnd
                }
            }
            val highlightVerses = if (parsed.segmentSpec != null) {
                expandSegmentSpecToVerses(parsed.segmentSpec, 999)
            } else {
                parsed.verses
            }
            result.add(
                ScriptureLinkRange(
                    start = linkStart,
                    end = end,
                    bookId = bookMatch.bookId,
                    chapter = parsed.chapter,
                    verses = highlightVerses,
                    isAudioLink = isAudio,
                    audioPlayMode = if (isAudio) audioMode else ScriptureAudioPlayMode.VERSE,
                    translationCode = translationCode,
                    segmentSpec = if (isAudio) segmentSpec else null,
                ),
            )
            i = end
        }
        return result
    }

    fun formatNavigationAnnotation(
        bookId: String,
        chapter: Int,
        verses: Set<Int>,
        audio: ScriptureAudioNavigation? = null,
    ): String {
        val versesPart = when {
            audio?.segmentSpec != null -> "seg:${audio.segmentSpec}"
            else -> verses.sorted().joinToString(",")
        }
        val base = "$bookId:$chapter:$versesPart"
        if (audio == null) return base
        return "$base:a:${audio.mode.code}:${audio.translationCode}"
    }

    fun parseNavigationAnnotation(raw: String): ParsedScriptureNavigation? {
        val parts = raw.split(':')
        if (parts.size < 3) return null
        val bookId = parts[0]
        val chapter = parts[1].toIntOrNull() ?: return null
        val audioIndex = parts.indexOf("a")
        val versesPart = if (audioIndex >= 0) {
            parts.subList(2, audioIndex).joinToString(":")
        } else {
            parts.subList(2, parts.size).joinToString(":")
        }
        val segmentSpec = if (versesPart.startsWith("seg:")) versesPart.removePrefix("seg:") else null
        val verses = if (segmentSpec != null) {
            expandSegmentSpecToVerses(segmentSpec, 999)
        } else {
            versesPart.split(',', '，')
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet()
        }
        if (verses.isEmpty()) return null
        val audio = if (audioIndex >= 0 && audioIndex + 2 < parts.size) {
            ScriptureAudioNavigation(
                mode = ScriptureAudioPlayMode.fromCode(parts[audioIndex + 1]),
                translationCode = parts[audioIndex + 2],
                segmentSpec = segmentSpec,
            )
        } else {
            null
        }
        return ParsedScriptureNavigation(bookId, chapter, verses, audio)
    }

    /** Текст ссылки для вставки в заметку. */
    fun formatAudioLinkText(
        bookId: String,
        chapter: Int,
        verses: Set<Int>,
        mode: ScriptureAudioPlayMode,
        translation: TranslationId,
        segmentSpec: String? = null,
    ): String {
        val entry = BibleCanon.byId(bookId) ?: return ""
        val abbr = entry.abbrRu
        val sorted = verses.sorted()
        val refBody = when (mode) {
            ScriptureAudioPlayMode.TO_CHAPTER_END -> {
                val v = sorted.firstOrNull() ?: 1
                "$chapter:$v→"
            }
            ScriptureAudioPlayMode.SEGMENTS -> {
                val spec = segmentSpec?.trim().orEmpty()
                if (spec.isEmpty()) "$chapter:${sorted.firstOrNull() ?: 1}" else "$chapter:$spec"
            }
            ScriptureAudioPlayMode.RANGE -> {
                val spec = segmentSpec?.trim()
                if (!spec.isNullOrEmpty()) {
                    "$chapter:$spec"
                } else {
                    val min = sorted.first()
                    val max = sorted.last()
                    "$chapter:$min-$max"
                }
            }
            ScriptureAudioPlayMode.VERSE -> {
                if (sorted.size == 1) {
                    "$chapter:${sorted.first()}"
                } else {
                    "$chapter:${sorted.joinToString(",")}"
                }
            }
        }
        return "🔊 $abbr $refBody@${translation.code}"
    }

    /**
     * Ссылка на озвучку плюс текст стихов — чтобы в заметке было видно, что будет звучать.
     */
    fun formatAudioLinkWithVerseTexts(
        bookId: String,
        chapter: Int,
        verses: Set<Int>,
        mode: ScriptureAudioPlayMode,
        translation: TranslationId,
        verseTextsByNumber: Map<Int, String>,
        chapterVerseCount: Int = 0,
        segmentSpec: String? = null,
    ): String {
        val link = formatAudioLinkText(bookId, chapter, verses, mode, translation, segmentSpec)
        if (link.isBlank()) return ""
        val numbers = verseNumbersForCopiedAudio(verses, mode, chapterVerseCount, segmentSpec)
        val body = numbers.mapNotNull { n ->
            val text = verseTextsByNumber[n]?.trim().orEmpty()
            if (text.isEmpty()) null else "$n. $text"
        }
        if (body.isEmpty()) return link
        return link + "\n\n" + body.joinToString("\n\n")
    }

    fun verseNumbersForCopiedAudio(
        verses: Set<Int>,
        mode: ScriptureAudioPlayMode,
        chapterVerseCount: Int,
        segmentSpec: String? = null,
    ): List<Int> {
        val maxV = chapterVerseCount.coerceAtLeast(verses.maxOrNull() ?: 1)
        return when (mode) {
            ScriptureAudioPlayMode.TO_CHAPTER_END -> {
                val start = verses.minOrNull() ?: 1
                (start..maxV).toList()
            }
            ScriptureAudioPlayMode.SEGMENTS, ScriptureAudioPlayMode.RANGE -> {
                val spec = segmentSpec?.trim()
                if (!spec.isNullOrEmpty()) {
                    expandSegmentSpecToVerses(spec, maxV).sorted()
                } else {
                    verses.sorted()
                }
            }
            ScriptureAudioPlayMode.VERSE -> verses.sorted()
        }
    }

    fun startVerseForAudio(nav: ParsedScriptureNavigation, segmentIndex: Int = 0, chapterVerseCount: Int = 999): Int =
        resolveAudioSegment(nav, segmentIndex, chapterVerseCount).startVerse

    fun showFullChapterForAudio(
        nav: ParsedScriptureNavigation,
        segmentIndex: Int = 0,
        chapterVerseCount: Int = 999,
    ): Boolean {
        val segment = resolveAudioSegment(nav, segmentIndex, chapterVerseCount)
        return segment.endVerseInclusive == null
    }

    fun versesForAudioSegment(
        nav: ParsedScriptureNavigation,
        segmentIndex: Int,
        chapterVerseCount: Int,
    ): Set<Int> = versesForSegment(
        resolveAudioSegment(nav, segmentIndex, chapterVerseCount),
        chapterVerseCount,
    )

    private fun detectAudioMode(parsed: ParsedTail): ScriptureAudioPlayMode {
        val spec = parsed.segmentSpec ?: return ScriptureAudioPlayMode.VERSE
        if (spec.contains('*') || spec.contains(',')) return ScriptureAudioPlayMode.SEGMENTS
        if (spec.contains('-') || spec.contains('–') || spec.contains('—')) return ScriptureAudioPlayMode.RANGE
        return ScriptureAudioPlayMode.VERSE
    }

    private fun isAudioPrefixStart(text: String, index: Int): Boolean =
        audioPrefixes.any { text.startsWith(it, index) }

    private data class ParsedTail(
        val chapter: Int,
        val verses: Set<Int>,
        val segmentSpec: String?,
        val consumed: Int,
    )

    private fun parseReferenceTail(rest: String): ParsedTail? {
        var pos = 0
        while (pos < rest.length && rest[pos].isWhitespace()) pos++
        val tail = rest.substring(pos)

        chapterWithVerseSpec.matchAt(tail, 0)?.let { m ->
            val ch = m.groupValues[1].toIntOrNull() ?: return null
            val spec = m.groupValues[2].trimEnd(',', ' ')
            if (spec.isEmpty()) return null
            var consumed = pos + m.range.last + 1
            consumed += skipTrailingRefPunct(rest, consumed)
            val verses = expandSegmentSpecToVerses(spec, 999)
            if (verses.isEmpty()) {
                val single = spec.toIntOrNull()
                if (single != null) {
                    return ParsedTail(ch, setOf(single), spec, consumed)
                }
                return null
            }
            return ParsedTail(ch, verses, spec, consumed)
        }

        chapterOnlyInRest.matchAt(tail, 0)?.let { m ->
            val nextIdx = m.range.last + 1
            if (nextIdx < tail.length && tail[nextIdx] == ':') return null
            val ch = m.groupValues[1].toIntOrNull() ?: return null
            var consumed = pos + m.range.last + 1
            consumed += skipTrailingRefPunct(rest, consumed)
            return ParsedTail(ch, setOf(1), "1", consumed)
        }
        return null
    }

    /** Завершающая пунктуация ссылки (запятая — часть списка стихов, не пропускаем). */
    private fun skipTrailingRefPunct(rest: String, from: Int): Int {
        var n = 0
        var p = from
        while (p < rest.length && rest[p] in ".;)" ) {
            n++
            p++
        }
        return n
    }
}
