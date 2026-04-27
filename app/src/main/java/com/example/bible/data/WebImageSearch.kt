package com.example.bible.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Сбор выдачи: Wikimedia Commons, [Openverse](https://openverse.org) (миллионы CC-изображений
 * из Flickr, музеев, Wikimedia и др.), парсинг Google / Яндекс / Bing (HTML нестабилен).
 */
object WebImageSearch {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    suspend fun searchCombined(query: String, limit: Int = 48): List<CommonsSearchResult> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext emptyList()
            if (SafeImagePolicy.isBlockedQuery(q)) return@withContext emptyList()
            val enc = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
            val maxTotal = limit.coerceIn(16, 80)

            val commons = SafeImagePolicy.filterResults(
                runCatching {
                    CommonsImageSearch.search(q, minOf(32, maxTotal))
                }.getOrDefault(emptyList()),
            )

            val openverse = SafeImagePolicy.filterResults(
                runCatching { parseOpenverseImages(enc) }.getOrDefault(emptyList()),
            )

            val google = SafeImagePolicy.filterResults(
                runCatching { parseGoogleImages(enc) }.getOrDefault(emptyList()),
            )
            val yandex = SafeImagePolicy.filterResults(
                runCatching { parseYandexImages(enc) }.getOrDefault(emptyList()),
            )
            val bing = SafeImagePolicy.filterResults(
                runCatching { parseBingImages(enc) }.getOrDefault(emptyList()),
            )
            val webMerged = mergeInterleaveThree(google, yandex, bing, 40)

            mergeInterleaveMany(
                listOf(commons, openverse, webMerged),
                maxTotal,
            )
        }

    /** Чередование нескольких списков (разнообразие источников в одной ленте). */
    private fun mergeInterleaveMany(
        lists: List<List<CommonsSearchResult>>,
        limit: Int,
    ): List<CommonsSearchResult> {
        val seen = mutableSetOf<String>()
        fun norm(u: String) = u.trim().substringBefore('?').lowercase().removeSuffix("/")
        val indices = IntArray(lists.size)
        val out = ArrayList<CommonsSearchResult>(limit)
        while (out.size < limit) {
            var progressed = false
            for (i in lists.indices) {
                if (out.size >= limit) break
                val list = lists[i]
                while (indices[i] < list.size) {
                    val r = list[indices[i]++]
                    val k = norm(r.fullUrl)
                    if (k.isNotBlank() && k !in seen) {
                        seen.add(k)
                        out.add(r)
                        progressed = true
                        break
                    }
                }
            }
            if (!progressed) break
        }
        return out
    }

    /** Openverse — агрегатор бесплатных (в основном CC) картинок, API без ключа. */
    private fun parseOpenverseImages(encQuery: String): List<CommonsSearchResult> {
        val out = mutableListOf<CommonsSearchResult>()
        for (page in 1..2) {
            if (out.size >= 40) break
            val url =
                "https://api.openverse.org/v1/images/?q=$encQuery&page_size=20&page=$page" +
                    "&unstable__include_sensitive_results=false"
            val json = httpGetJson(url) ?: continue
            val root = runCatching { JSONObject(json) }.getOrNull() ?: continue
            val results = root.optJSONArray("results") ?: continue
            for (i in 0 until results.length()) {
                if (out.size >= 40) break
                val o = results.optJSONObject(i) ?: continue
                if (o.optBoolean("mature", false)) continue
                val full = o.optString("url", "")
                if (full.isBlank() || !full.startsWith("http")) continue
                val thumb = o.optString("thumbnail", "").ifBlank { full }
                val title = o.optString("title", "")
                val provider = o.optString("provider", "")
                val label = when {
                    title.isNotBlank() && provider.isNotBlank() -> "$title · $provider"
                    title.isNotBlank() -> title
                    else -> "Openverse · ${out.size + 1}"
                }
                out.add(
                    CommonsSearchResult(
                        pageTitle = label,
                        thumbUrl = thumb,
                        fullUrl = full,
                        origin = "openverse",
                    ),
                )
            }
        }
        return out
    }

    private fun mergeInterleaveThree(
        a: List<CommonsSearchResult>,
        b: List<CommonsSearchResult>,
        c: List<CommonsSearchResult>,
        limit: Int,
    ): List<CommonsSearchResult> {
        val seen = mutableSetOf<String>()
        fun norm(u: String) = u.trim().substringBefore('?').lowercase().removeSuffix("/")
        fun tryAdd(r: CommonsSearchResult): Boolean {
            val k = norm(r.fullUrl)
            if (k.isBlank() || k in seen) return false
            seen.add(k)
            return true
        }
        val out = ArrayList<CommonsSearchResult>(limit)
        var i = 0
        var j = 0
        var k = 0
        while (out.size < limit && (i < a.size || j < b.size || k < c.size)) {
            if (i < a.size) {
                val r = a[i++]
                if (tryAdd(r)) out.add(r)
            }
            if (out.size >= limit) break
            if (j < b.size) {
                val r = b[j++]
                if (tryAdd(r)) out.add(r)
            }
            if (out.size >= limit) break
            if (k < c.size) {
                val r = c[k++]
                if (tryAdd(r)) out.add(r)
            }
        }
        return out
    }

    private fun parseGoogleImages(encQuery: String): List<CommonsSearchResult> {
        val url =
            "https://www.google.com/search?q=$encQuery&tbm=isch&hl=ru&safe=active&ijn=0"
        val html = httpGet(url, referer = "https://www.google.com/") ?: return emptyList()
        var ouUrls = googleOuRegex.findAll(html)
            .map { jsonUnescape(it.groupValues[1]) }
            .filter { isLikelyImageUrl(it) }
            .distinct()
            .toList()
        if (ouUrls.isEmpty()) {
            ouUrls = googleOuRegexAlt.findAll(html)
                .map { jsonUnescape(it.groupValues[1]) }
                .filter { isLikelyImageUrl(it) }
                .distinct()
                .toList()
        }
        if (ouUrls.isEmpty()) {
            ouUrls = googleOuRegexLoose.findAll(html)
                .map { jsonUnescape(it.groupValues[1]) }
                .filter { isLikelyImageUrl(it) && (".jpg" in it || ".jpeg" in it || ".png" in it || ".webp" in it || "googleusercontent" in it) }
                .distinct()
                .toList()
        }
        val tuUrls = googleTuRegex.findAll(html)
            .map { jsonUnescape(it.groupValues[1]) }
            .toList()
        if (ouUrls.isEmpty()) return emptyList()
        val out = mutableListOf<CommonsSearchResult>()
        val n = minOf(ouUrls.size, 24)
        for (idx in 0 until n) {
            val full = ouUrls[idx]
            val thumb = tuUrls.getOrNull(idx)?.takeIf { it.startsWith("http") } ?: full
            out.add(
                CommonsSearchResult(
                    pageTitle = "Google · ${idx + 1}",
                    thumbUrl = thumb,
                    fullUrl = full,
                    origin = "google",
                ),
            )
        }
        return out
    }

    private fun parseYandexImages(encQuery: String): List<CommonsSearchResult> {
        val found = LinkedHashSet<String>()
        val pageUrls = listOf(
            "https://yandex.ru/images/search?from=tabbar&text=$encQuery&family=yes",
            "https://yandex.com/images/search?from=tabbar&text=$encQuery&family=yes",
        )
        for (pageUrl in pageUrls) {
            val html = httpGet(pageUrl, referer = "https://yandex.ru/") ?: continue
            for (re in yandexUrlPatterns) {
                re.findAll(html).forEach { m ->
                    val u = jsonUnescape(m.groupValues[1])
                    if (isLikelyImageUrl(u) && u.startsWith("http")) found.add(u)
                }
                if (found.size >= 32) break
            }
            if (found.size >= 24) break
        }
        return found.take(24).mapIndexed { idx, full ->
            CommonsSearchResult(
                pageTitle = "Яндекс · ${idx + 1}",
                thumbUrl = full,
                fullUrl = full,
                origin = "yandex",
            )
        }
    }

    private fun parseBingImages(encQuery: String): List<CommonsSearchResult> {
        val url =
            "https://www.bing.com/images/search?q=$encQuery&form=HDRSC2&first=1&tsc=ImageBasicHover&safeSearch=strict"
        val html = httpGet(url, referer = "https://www.bing.com/") ?: return emptyList()
        val urls = LinkedHashSet<String>()
        for (re in bingMurlPatterns) {
            re.findAll(html).forEach { m ->
                val u = jsonUnescape(m.groupValues[1])
                if (isLikelyImageUrl(u) && u.startsWith("http")) urls.add(u)
            }
            if (urls.size >= 20) break
        }
        return urls.take(24).mapIndexed { idx, full ->
            CommonsSearchResult(
                pageTitle = "Bing · ${idx + 1}",
                thumbUrl = full,
                fullUrl = full,
                origin = "bing",
            )
        }
    }

    private val googleOuRegex = Regex("\"ou\"\\s*:\\s*\"(https?://[^\"]+)\"")
    private val googleOuRegexAlt = Regex("\\\\\"ou\\\\\":\\\\\"(https?://[^\"\\\\]+)\\\\\"")
    /** Запасной вариант: встречается в data-атрибутах / JSON. */
    private val googleOuRegexLoose = Regex("\"(https://lh3\\.googleusercontent\\.com/[^\"]+)\"")
    private val googleTuRegex = Regex("\"tu\"\\s*:\\s*\"(https?://[^\"]+)\"")

    private val yandexUrlPatterns = listOf(
        Regex("\"url\"\\s*:\\s*\"(https://avatars\\.mds\\.yandex\\.net[^\"]+)\""),
        Regex("\"preview\"\\s*:\\s*\\{[^}]*\"url\"\\s*:\\s*\"(https:[^\"]+)\""),
        Regex("\"img_href\"\\s*:\\s*\"(https:[^\"]+)\""),
        Regex("\"origUrl\"\\s*:\\s*\"(https:[^\"]+)\""),
    )

    private val bingMurlPatterns = listOf(
        Regex("\"murl\"\\s*:\\s*\"(https?://[^\"]+)\""),
        Regex("\"murl\"\\s*:\\s*\"(http://[^\"]+)\""),
        Regex("murl&quot;:&quot;(https?://[^&]+)&quot;"),
    )

    private fun jsonUnescape(s: String): String =
        s.replace("\\/", "/")
            .replace("\\u003d", "=")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("\\\\", "\\")

    private fun isLikelyImageUrl(url: String): Boolean {
        val l = url.lowercase()
        if (l.startsWith("data:") || l.length < 12) return false
        if ("gstatic.com/gen_204" in l) return false
        return true
    }

    private fun httpGet(urlStr: String, referer: String?): String? {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        conn.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
        referer?.let { conn.setRequestProperty("Referer", it) }
        conn.connectTimeout = 18_000
        conn.readTimeout = 28_000
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) return null
        return conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }.take(1_500_000)
    }

    private fun httpGetJson(urlStr: String): String? {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 20_000
        conn.readTimeout = 35_000
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) return null
        return conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }.take(800_000)
    }
}
