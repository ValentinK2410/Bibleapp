package com.example.bible.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Поиск видео на Wikimedia Commons (тот же API, что у [CommonsImageSearch], только MIME с префиксом video).
 */
object CommonsVideoSearch {

    private const val USER_AGENT = "BibleApp/1.0 (Android; video search; not a bot)"

    suspend fun search(query: String, limit: Int = 24): List<CommonsSearchResult> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext emptyList()
            if (SafeImagePolicy.isBlockedQuery(q)) return@withContext emptyList()
            try {
                val enc = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
                val url =
                    "https://commons.wikimedia.org/w/api.php?action=query&generator=search" +
                        "&gsrsearch=$enc&gsrlimit=${limit.coerceIn(1, 50)}" +
                        "&gsrnamespace=6&prop=imageinfo&iiprop=url|mime" +
                        "&iiurlwidth=480&format=json"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.connectTimeout = 15_000
                conn.readTimeout = 25_000
                conn.connect()
                if (conn.responseCode != 200) return@withContext emptyList()
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                SafeImagePolicy.filterResults(parseResponse(text))
            } catch (_: Exception) {
                emptyList()
            }
        }

    private fun parseResponse(json: String): List<CommonsSearchResult> {
        val root = JSONObject(json)
        val query = root.optJSONObject("query") ?: return emptyList()
        val pages = query.optJSONObject("pages") ?: return emptyList()
        val out = mutableListOf<CommonsSearchResult>()
        val keys = pages.keys()
        while (keys.hasNext()) {
            val page = pages.optJSONObject(keys.next()) ?: continue
            val title = page.optString("title", "")
            if (title.isBlank()) continue
            val ii = page.optJSONArray("imageinfo") ?: continue
            if (ii.length() == 0) continue
            val info = ii.getJSONObject(0)
            val mime = info.optString("mime", "")
            if (!mime.startsWith("video/")) continue
            val full = info.optString("url", "")
            if (full.isBlank()) continue
            val thumb = info.optString("thumburl", "").ifBlank { full }
            out.add(CommonsSearchResult(pageTitle = title, thumbUrl = thumb, fullUrl = full))
        }
        return out
    }
}
