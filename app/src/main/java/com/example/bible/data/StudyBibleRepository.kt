package com.example.bible.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "StudyBible"
private const val BASE = "https://studybible.ru"

private val BOOK_NUM_MAP: Map<String, Int> by lazy {
    BibleCanon.allBooks.mapIndexed { i, e -> e.id to (i + 1) }.toMap()
}

private val NUM_TO_BOOK_MAP: Map<Int, String> by lazy {
    BibleCanon.allBooks.mapIndexed { i, e -> (i + 1) to e.id }.toMap()
}

fun bookToStudyBibleNum(bookId: String): Int = BOOK_NUM_MAP[bookId] ?: 1
fun studyBibleNumToBookId(num: Int): String? = NUM_TO_BOOK_MAP[num]

data class CommentarySource(
    val id: String,
    val name: String,
    val urlSlug: String,
)

val COMMENTARY_SOURCES = listOf(
    CommentarySource("geneva", "Новая Женевская Библия", "geneva-bible"),
    CommentarySource("macarthur", "Учебная Библия МакАртура", "macarthur-bible"),
    CommentarySource("henry", "Толкование Мэтью Генри", "matthew-henry"),
    CommentarySource("mcdonald", "Комментарии МакДональда", "mcdonald"),
    CommentarySource("lopuhin", "Толковая Библия Лопухина", "lopuhin-bible"),
    CommentarySource("barclay", "Комментарии Баркли", "barclay"),
    CommentarySource("zlatoust", "Иоанн Златоуст", "zlatoust"),
    CommentarySource("feofilakt", "Феофилакт Болгарский", "feofilakt"),
    CommentarySource("nbc", "Новый Библейский Комментарий", "nbc"),
    CommentarySource("rogers", "Лингвистический ключ", "rogers"),
    CommentarySource("stern", "Комментарии Давида Стерна", "cent"),
    CommentarySource("scofield", "Комментарии Скоуфилда", "scofield-bible"),
    CommentarySource("ryle", "Комментарии Джона Райла", "ryle"),
)

data class VerseComparison(
    val translationName: String,
    val text: String,
)

data class CrossReference(
    val ref: String,
    val bookId: String? = null,
    val chapter: Int = 0,
    val verse: Int = 0,
    val text: String = "",
)

data class StrongWord(
    val original: String,
    val transliteration: String,
    val number: String,
    val meaning: String,
)

object StudyBibleRepository {

    private fun fetchHtml(path: String): String? = try {
        val conn = URL("$BASE$path").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.connect()
        if (conn.responseCode == 200) {
            conn.inputStream.bufferedReader().readText()
        } else null
    } catch (e: Exception) {
        Log.e(TAG, "fetch $path", e)
        null
    }

    suspend fun fetchChapterText(
        onlineCode: String,
        bookId: String,
        chapter: Int,
    ): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        val bookNum = bookToStudyBibleNum(bookId)
        val html = fetchHtml("/$onlineCode/$bookNum/$chapter/") ?: return@withContext emptyList()
        parseVerses(html)
    }

    suspend fun fetchCommentary(
        commentarySlug: String,
        bookId: String,
        chapter: Int,
    ): String = withContext(Dispatchers.IO) {
        val bookNum = bookToStudyBibleNum(bookId)
        val html = fetchHtml("/$commentarySlug/$bookNum/$chapter/") ?: return@withContext ""
        parseCommentaryText(html)
    }

    suspend fun fetchVerseComparison(
        bookId: String,
        chapter: Int,
        verse: Int,
    ): List<VerseComparison> = withContext(Dispatchers.IO) {
        val bookNum = bookToStudyBibleNum(bookId)
        val html = fetchHtml("/verse/$bookNum/$chapter/$verse/") ?: return@withContext emptyList()
        parseVerseComparisons(html)
    }

    suspend fun fetchCrossReferences(
        bookId: String,
        chapter: Int,
        verse: Int,
    ): List<CrossReference> = withContext(Dispatchers.IO) {
        val bookNum = bookToStudyBibleNum(bookId)
        val html = fetchHtml("/tsk/$bookNum/$chapter/$verse/") ?: return@withContext emptyList()
        parseCrossReferences(html)
    }

    suspend fun fetchStrongNumbers(
        bookId: String,
        chapter: Int,
        verse: Int,
    ): List<StrongWord> = withContext(Dispatchers.IO) {
        val bookNum = bookToStudyBibleNum(bookId)
        val html = fetchHtml("/strong/$bookNum/$chapter/$verse/") ?: return@withContext emptyList()
        parseStrongWords(html)
    }

    private fun parseVerses(html: String): List<Pair<Int, String>> {
        val results = mutableListOf<Pair<Int, String>>()
        val textDivStart = html.indexOf("class=\"text ")
        if (textDivStart < 0) return results
        val textDivOpenEnd = html.indexOf(">", textDivStart)
        val textSection = html.substring(textDivOpenEnd + 1)

        val divPattern = Regex(
            """<div\s+id="(\d+)"[^>]*>(.*?)</div>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        for (m in divPattern.findAll(textSection)) {
            val num = m.groupValues[1].toIntOrNull() ?: continue
            val raw = m.groupValues[2]
                .replace(Regex("<sup[^>]*>.*?</sup>"), "")
                .replace(Regex("<span[^>]*class=\"sub\"[^>]*>.*?</span>"), "")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#171;", "«")
                .replace("&#187;", "»")
                .replace("&laquo;", "«")
                .replace("&raquo;", "»")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (raw.isNotBlank()) results.add(num to raw)
        }
        return results
    }

    private fun parseCommentaryText(html: String): String {
        val marker = "itemprop=\"articleBody\""
        val bodyStart = html.indexOf(marker).takeIf { it >= 0 }
            ?: return ""
        val textDivStart = html.indexOf("class=\"text", bodyStart).takeIf { it >= 0 }
            ?: bodyStart
        val openTag = html.indexOf(">", textDivStart)
        if (openTag < 0) return ""

        val scriptStart = html.indexOf("<script", openTag)
        val sectionEnd = if (scriptStart > openTag) scriptStart else html.length
        val section = html.substring(openTag + 1, sectionEnd)

        return cleanHtmlToText(section)
    }

    private fun cleanHtmlToText(raw: String): String =
        raw
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<p[^>]*>"), "\n\n")
            .replace("</p>", "")
            .replace(Regex("<h[1-6][^>]*>"), "\n\n### ")
            .replace(Regex("</h[1-6]>"), "\n")
            .replace(Regex("<strong[^>]*>"), "**")
            .replace("</strong>", "**")
            .replace(Regex("<b[^>]*>"), "**")
            .replace("</b>", "**")
            .replace(Regex("<i>|<em>"), "_")
            .replace(Regex("</i>|</em>"), "_")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&#171;", "«")
            .replace("&#187;", "»")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&minus;", "−")
            .replace(Regex("&[a-z]+;"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    private fun parseVerseComparisons(html: String): List<VerseComparison> {
        val results = mutableListOf<VerseComparison>()
        val containerPattern = Regex(
            """<div\s+class="container\s+(\S+)\s+verse-item"""
        )
        for (m in containerPattern.findAll(html)) {
            val start = m.range.first
            val chunk = html.substring(start, (start + 2000).coerceAtMost(html.length))

            val nameMatch = Regex("""<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL).find(chunk)
            val nameRaw = nameMatch?.groupValues?.get(1) ?: continue
            val name = nameRaw
                .replace(Regex("<[^>]+>"), "")
                .replace("+", "")
                .trim()
            if (name.length < 3) continue

            val vsMatch = Regex(
                """<div[^>]*class="[^"]*\bvs\b[^"]*"[^>]*>(.*?)</div>""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(chunk)
            val rawText = vsMatch?.groupValues?.get(1) ?: continue
            val text = rawText
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&#171;", "«")
                .replace("&#187;", "»")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (text.isNotBlank() && text.length > 3) {
                results.add(VerseComparison(name, text))
            }
        }
        return results
    }

    private fun parseCrossReferences(html: String): List<CrossReference> {
        val results = mutableListOf<CrossReference>()
        val pattern = Regex("""<a[^>]*href="[^"]*/(?:syn|nrt|rbo-2015|bti)/(\d+)/(\d+)/#(\d+)(?:-\d+)?"[^>]*>([^<]+)</a>""")
        for (m in pattern.findAll(html)) {
            val bookNum = m.groupValues[1].toIntOrNull() ?: continue
            val ch = m.groupValues[2].toIntOrNull() ?: continue
            val vs = m.groupValues[3].toIntOrNull() ?: 1
            val refText = m.groupValues[4].trim()
            val bId = studyBibleNumToBookId(bookNum)
            results.add(CrossReference(ref = refText, bookId = bId, chapter = ch, verse = vs))
        }
        if (results.isEmpty()) {
            val simplePattern = Regex("""([1-3]?\s?[А-Яа-яA-Za-z]+\.?\s+\d+:\d+)""")
            for (m in simplePattern.findAll(html)) {
                results.add(CrossReference(ref = m.groupValues[1].trim()))
            }
        }
        return results.distinctBy { it.ref }
    }

    private fun parseStrongWords(html: String): List<StrongWord> {
        val results = mutableListOf<StrongWord>()
        val wordPattern = Regex(
            """<div\s+class="str-word[^"]*"[^>]*>(.*?)</div>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val greekPattern = Regex("""<span\s+class="str-greek"[^>]*>([^<]*)</span>""")
        val ruPattern = Regex("""<span\s+class="str-ru"[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
        val numPattern = Regex("""<a\s+href="/strong/(?:greek|hebrew)/(\d+)/"[^>]*>(\d+)</a>""")
        val morphPattern = Regex("""<span\s+class="morh"[^>]*>([^<]*)</span>""")

        for (m in wordPattern.findAll(html)) {
            val block = m.groupValues[1]
            val original = greekPattern.find(block)?.groupValues?.get(1)?.trim() ?: ""
            val ruRaw = ruPattern.find(block)?.groupValues?.get(1)
                ?.replace(Regex("<[^>]+>"), "")?.replace("&nbsp;", " ")?.trim() ?: ""
            val num = numPattern.find(block)?.groupValues?.get(2) ?: ""
            val morph = morphPattern.find(block)?.groupValues?.get(1)?.trim() ?: ""
            if (original.isBlank() && ruRaw.isBlank()) continue
            results.add(
                StrongWord(
                    original = original,
                    transliteration = morph,
                    number = num,
                    meaning = ruRaw,
                ),
            )
        }

        if (results.isEmpty()) {
            val synSection = html.indexOf("id=\"tab-strong-syn\"")
            val synEnd = if (synSection >= 0) {
                html.indexOf("id=\"tab-strong-orig\"", synSection).takeIf { it > synSection } ?: html.length
            } else html.length
            if (synSection >= 0) {
                val synBlock = html.substring(synSection, synEnd)
                val strongLink = Regex("""<a[^>]*class="strong"[^>]*onclick="return chSTR\(this,'(\d+)'\)"[^>]*>([^<]+)</a>""")
                for (sm in strongLink.findAll(synBlock)) {
                    results.add(
                        StrongWord(
                            original = "",
                            transliteration = "H${sm.groupValues[1]}",
                            number = sm.groupValues[1],
                            meaning = sm.groupValues[2].trim(),
                        ),
                    )
                }
            }
        }
        return results
    }
}
