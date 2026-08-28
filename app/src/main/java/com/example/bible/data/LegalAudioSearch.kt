package com.example.bible.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Трек из открытого каталога (CC / общественное достояние), с прямой ссылкой на файл.
 */
data class LegalAudioTrack(
    val title: String,
    val creator: String,
    val fileUrl: String,
    val pageUrl: String,
    val license: String,
    val origin: String,
    val durationSec: Int? = null,
) {
    val id: String get() = fileUrl.trim().substringBefore('?').lowercase()

    fun displayTitle(): String {
        val t = title.trim().ifBlank { "Без названия" }
        val c = creator.trim()
        return if (c.isNotEmpty() && !t.contains(c, ignoreCase = true)) "$c — $t" else t
    }

    fun licenseLabel(): String {
        val s = license.lowercase().replace('_', '-').replace(' ', '-')
        return when {
            "cc0" in s || s == "zero" -> "CC0"
            "pdm" in s || "publicdomain" in s.replace("-", "") -> "общественное достояние"
            "by-nc-sa" in s -> "CC BY-NC-SA"
            "by-nc-nd" in s -> "CC BY-NC-ND"
            "by-nc" in s -> "CC BY-NC"
            "by-sa" in s -> "CC BY-SA"
            "by-nd" in s -> "CC BY-ND"
            s == "by" || s.startsWith("by-") -> "CC BY"
            license.isBlank() -> "открытая лицензия"
            else -> license
        }
    }

    fun originLabel(): String = when (origin) {
        "openverse" -> "Openverse"
        "commons" -> "Wikimedia Commons"
        "yandex_local" -> "Яндекс Музыка (файлы на телефоне)"
        "device" -> "Папка на устройстве"
        else -> origin
    }

    fun isLocalOnDevice(): Boolean =
        origin == "yandex_local" || origin == "device"
}

/**
 * Поиск легально скачиваемой музыки: [Openverse](https://openverse.org) (CC) и Wikimedia Commons.
 * Коммерческие стриминги не используются.
 */
object LegalAudioSearch {

    private const val USER_AGENT = "BibleApp/1.0 (Android; legal audio search; not a bot)"

    suspend fun search(query: String, limit: Int = 28): List<LegalAudioTrack> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext emptyList()
            if (SafeImagePolicy.isBlockedQuery(q)) return@withContext emptyList()
            coroutineScope {
                val openverse = async { searchOpenverse(q) }
                val commons = async { searchCommons(q) }
                merge(openverse.await(), commons.await(), limit)
                    .filter { !SafeImagePolicy.isBlockedRemoteImport(it.fileUrl, it.title, it.pageUrl) }
            }
        }

    private fun searchOpenverse(query: String): List<LegalAudioTrack> {
        val enc = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val out = mutableListOf<LegalAudioTrack>()
        for (page in 1..2) {
            if (out.size >= 24) break
            val url =
                "https://api.openverse.org/v1/audio/?q=$enc&page_size=20&page=$page" +
                    "&unstable__include_sensitive_results=false"
            val json = httpGetJson(url) ?: continue
            val root = runCatching { JSONObject(json) }.getOrNull() ?: continue
            val results = root.optJSONArray("results") ?: continue
            for (i in 0 until results.length()) {
                if (out.size >= 24) break
                val o = results.optJSONObject(i) ?: continue
                if (o.optBoolean("mature", false)) continue
                val file = o.optString("url", "")
                if (file.isBlank() || !file.startsWith("http")) continue
                if (!looksLikeAudioUrl(file) && o.optString("filetype", "").isBlank()) {
                    // Openverse often отдаёт прямую ссылку без расширения — всё равно пробуем.
                }
                val durMs = o.optLong("duration", 0L)
                val durationSec = when {
                    durMs <= 0L -> null
                    durMs > 10_000L -> (durMs / 1000L).toInt()
                    else -> durMs.toInt()
                }
                val landing = o.optString("foreign_landing_url", "").ifBlank { file }
                out.add(
                    LegalAudioTrack(
                        title = o.optString("title", "").ifBlank { "Openverse" },
                        creator = o.optString("creator", ""),
                        fileUrl = file,
                        pageUrl = landing,
                        license = o.optString("license", ""),
                        origin = "openverse",
                        durationSec = durationSec,
                    ),
                )
            }
        }
        return out
    }

    private fun searchCommons(query: String): List<LegalAudioTrack> {
        val enc = URLEncoder.encode("$query filetype:ogg", StandardCharsets.UTF_8.name())
        val url =
            "https://commons.wikimedia.org/w/api.php?action=query&generator=search" +
                "&gsrsearch=$enc&gsrlimit=20" +
                "&gsrnamespace=6&prop=imageinfo&iiprop=url|mime|extmetadata" +
                "&format=json"
        val json = httpGetJson(url) ?: return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val out = mutableListOf<LegalAudioTrack>()
        val keys = pages.keys()
        while (keys.hasNext()) {
            val page = pages.optJSONObject(keys.next()) ?: continue
            val title = page.optString("title", "").removePrefix("File:")
            val ii = page.optJSONArray("imageinfo") ?: continue
            if (ii.length() == 0) continue
            val info = ii.getJSONObject(0)
            val mime = info.optString("mime", "")
            if (!mime.startsWith("audio/")) continue
            val file = info.optString("url", "")
            if (file.isBlank() || !file.startsWith("http")) continue
            val artist = info.optJSONObject("extmetadata")
                ?.optJSONObject("Artist")
                ?.optString("value", "")
                .orEmpty()
                .replace(Regex("<[^>]+>"), "")
                .trim()
            val descUrl = info.optString("descriptionurl", file)
            out.add(
                LegalAudioTrack(
                    title = title.substringBeforeLast('.').ifBlank { title },
                    creator = artist,
                    fileUrl = file,
                    pageUrl = descUrl,
                    license = "cc",
                    origin = "commons",
                    durationSec = null,
                ),
            )
        }
        return out
    }

    private fun merge(
        a: List<LegalAudioTrack>,
        b: List<LegalAudioTrack>,
        limit: Int,
    ): List<LegalAudioTrack> {
        val seen = mutableSetOf<String>()
        val out = ArrayList<LegalAudioTrack>(limit)
        var i = 0
        var j = 0
        fun add(t: LegalAudioTrack): Boolean {
            val k = t.id
            if (k.isBlank() || k in seen) return false
            seen.add(k)
            out.add(t)
            return true
        }
        while (out.size < limit && (i < a.size || j < b.size)) {
            if (i < a.size) add(a[i++])
            if (out.size >= limit) break
            if (j < b.size) add(b[j++])
        }
        return out
    }

    private fun looksLikeAudioUrl(url: String): Boolean {
        val p = url.substringBefore('?').lowercase()
        return p.endsWith(".mp3") || p.endsWith(".ogg") || p.endsWith(".oga") ||
            p.endsWith(".opus") || p.endsWith(".flac") || p.endsWith(".wav") ||
            p.endsWith(".m4a") || p.endsWith(".aac") || "audio" in p
    }

    private fun httpGetJson(urlStr: String): String? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 18_000
            conn.readTimeout = 28_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }.take(900_000)
        } catch (_: Exception) {
            null
        }
    }
}
