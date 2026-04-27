package com.example.bible.data

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Блокировка запросов и результатов с откровенно сексуальным / порнографическим содержанием.
 * Комбинация границ слов (чтобы не резать «бисексуал» и т.п.) и явных фраз в URL.
 */
object SafeImagePolicy {

    fun isBlockedQuery(query: String): Boolean = matches(normalize(query))

    fun isBlockedResult(r: CommonsSearchResult): Boolean =
        matches(normalize("${r.pageTitle} ${r.fullUrl} ${r.thumbUrl}"))

    fun filterResults(list: List<CommonsSearchResult>): List<CommonsSearchResult> =
        list.filter { !isBlockedResult(it) }

    fun isBlockedRemoteImport(fullUrl: String, title: String, sourceUrl: String): Boolean {
        val decoded = runCatching {
            URLDecoder.decode(fullUrl, StandardCharsets.UTF_8.name()) + " " +
                URLDecoder.decode(sourceUrl, StandardCharsets.UTF_8.name())
        }.getOrDefault("$fullUrl $sourceUrl")
        return matches(normalize("$title $decoded $fullUrl $sourceUrl"))
    }

    private fun normalize(s: String): String =
        s.lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ").trim()

    private fun matches(text: String): Boolean {
        if (text.isBlank()) return false
        for (re in COMBINED_PATTERNS) {
            if (re.containsMatchIn(text)) return true
        }
        return false
    }

    /**
     * Паттерны с учётом границ для латиницы и кириллицы.
     * [https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/-regex/]
     */
    private val COMBINED_PATTERNS: List<Regex> = listOf(
        // Латиница: целые слова / корни
        Regex(
            """(?i)(?<![a-z0-9])(porn|porno|pornhub|xvideos|xhamster|redtube|youporn|onlyfans|chaturbate|nsfw|hentai|rule34|futanari|lolicon|shotacon|erotica|nudes?|naked|blowjob|handjob|creampie|gangbang|threesome|orgy|milf|bdsm|bondage|fetish|dildo|vibrator|masturbat|ejacul|cumshot|bukkake|deepthroat|vagina|penis|clitoris|pussy|cunt|cock|dick|tits|boobs|nipples|anus|incest|zoophil|bestiality|necrophil|pedophil|prostitut|escort|whore|slut|hooker|upskirt|downblouse|cameltoe|livejasmin)(?![a-z0-9])""",
        ),
        Regex("""(?i)(?<![a-z0-9])sex(?![a-z0-9])"""),
        Regex("""(?i)(?<![a-z0-9])anal(?![a-z0-9])"""),
        Regex("""(?i)(?<![a-z0-9])xxx(?![a-z0-9])"""),
        Regex("""(?i)(porn\.|\.xxx|/xxx/|/porn/|adult\.|nsfw\.)"""),
        // Кириллица
        Regex(
            """(?<![а-яё0-9])(порно|порнух|порнограф|порноактрис|порнозвезд|эротик|секс|сиськ|минет|кунилинг|трах|ебат|ебёт|ебет|хуй|хуя|хуе|пизд|вагин|сперм|оргазм|мастурб|дилдо|вибратор|голая|голые|голый|обнажен|нюд|интим|шлюх|бляд|проститут|эскорт|зоофил|инцест|педофил|лесбиян|гейпорно|нарезка|порево)(?![а-яё0-9])""",
        ),
    )
}
