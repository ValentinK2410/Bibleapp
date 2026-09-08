package com.example.bible.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AudioTrack(
    val url: String,
    val label: String,
)

data class FonkiSong(
    val title: String,
    val artist: String,
    val lyrics: String,
    val tracks: List<AudioTrack>,
) {
    val audioUrl: String get() = tracks.firstOrNull()?.url ?: ""
}

/** Результат поиска по каталогам holychords.pro и fonki.pro. */
data class SongCatalogHit(
    val title: String,
    val artist: String,
    val pageUrl: String,
    val sourceLabel: String,
    val snippet: String = "",
)

enum class SongCatalogSource(val baseUrl: String, val label: String) {
    HolyChords("https://holychords.pro", "HolyChords"),
    Fonki("https://fonki.pro", "Fonki"),
}

private data class RankedSongHit(
    val hit: SongCatalogHit,
    val score: Int,
    val views: Int,
)

object FonkiExtractor {

    fun isFonkiUrl(url: String): Boolean {
        val lower = url.lowercase()
        return "fonki.pro/" in lower || "holychords.pro/" in lower
    }

    suspend fun extract(pageUrl: String): FonkiSong = withContext(Dispatchers.IO) {
        val cleanUrl = pageUrl.substringBefore("?")
        val html = fetchHtml(cleanUrl)
        val isHolyChords = "holychords.pro" in pageUrl.lowercase()

        if (isHolyChords) extractHolyChords(html, cleanUrl)
        else extractFonki(html)
    }

    /**
     * Поиск через API сайтов `/search?name=` — по названию, исполнителю и тексту песни.
     */
    suspend fun searchSongCatalog(
        query: String,
        maxResults: Int = 60,
    ): List<SongCatalogHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()

        coroutineScope {
            val holyChords = async {
                runCatching { searchSiteJson(SongCatalogSource.HolyChords, q) }
            }
            val fonki = async {
                runCatching { searchSiteJson(SongCatalogSource.Fonki, q) }
            }
            val hcRes = holyChords.await()
            val fonkiRes = fonki.await()
            if (hcRes.isFailure && fonkiRes.isFailure) {
                throw hcRes.exceptionOrNull()
                    ?: fonkiRes.exceptionOrNull()
                    ?: RuntimeException("Ошибка поиска")
            }
            (hcRes.getOrDefault(emptyList()) + fonkiRes.getOrDefault(emptyList()))
                .distinctBy { it.hit.pageUrl }
                .sortedWith(
                    compareByDescending<RankedSongHit> { it.score }
                        .thenByDescending { it.views },
                )
                .map { it.hit }
                .take(maxResults)
        }
    }

    private fun searchSiteJson(source: SongCatalogSource, query: String): List<RankedSongHit> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name()).replace("+", "%20")
        val json = fetchJson("${source.baseUrl}/search?name=$encoded")
        val data = JSONObject(json).optJSONObject("musics")?.optJSONArray("data")
            ?: return emptyList()
        val out = mutableListOf<RankedSongHit>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optLong("id")
            if (id <= 0L) continue
            val title = jsonText(item, "name")
            if (title.isBlank()) continue
            val artistObj = item.optJSONObject("artist")
            val artist = listOf(
                jsonText(artistObj, "isp_name"),
                jsonText(artistObj, "name"),
            ).firstOrNull { it.isNotBlank() }.orEmpty()
            val text = jsonText(item, "text")
            val pageUrl = if (source == SongCatalogSource.Fonki) {
                "https://fonki.pro/minus/$id"
            } else {
                "https://holychords.pro/$id"
            }
            out.add(
                RankedSongHit(
                    hit = SongCatalogHit(
                        title = title,
                        artist = artist,
                        pageUrl = pageUrl,
                        sourceLabel = source.label,
                        snippet = lyricSnippet(text, query, title),
                    ),
                    score = matchScore(query, title, artist, text),
                    views = item.optInt("views"),
                ),
            )
        }
        return out
    }

    private fun matchScore(query: String, title: String, artist: String, text: String): Int {
        val q = query.lowercase()
        val t = title.lowercase()
        val a = artist.lowercase()
        val lyrics = stripChordMarkup(text).lowercase()
        val words = q.split(Regex("\\s+")).filter { it.length >= 2 }
        var score = 0
        when {
            t == q -> score += 1000
            t.startsWith(q) -> score += 850
            q in t -> score += 700
        }
        if (q in a) score += 120
        if (words.isNotEmpty() && words.all { it in t }) score += 200
        else if (words.isNotEmpty() && words.all { it in "$t $a" }) score += 80
        if (q in lyrics) score += 150
        else if (words.isNotEmpty() && words.all { it in lyrics }) score += 90
        return score
    }

    private fun lyricSnippet(text: String, query: String, title: String): String {
        if (query.lowercase() in title.lowercase()) return ""
        val plain = stripChordMarkup(text).replace(Regex("\\s+"), " ").trim()
        if (plain.isEmpty()) return ""
        val hay = plain.lowercase()
        val q = query.lowercase()
        var idx = hay.indexOf(q)
        if (idx < 0) {
            val word = q.split(Regex("\\s+")).filter { it.length >= 3 }.maxByOrNull { it.length }
                ?: return ""
            idx = hay.indexOf(word)
            if (idx < 0) return ""
        }
        val start = (idx - 36).coerceAtLeast(0)
        val end = (idx + query.length + 44).coerceAtMost(plain.length)
        return buildString {
            if (start > 0) append("…")
            append(plain.substring(start, end).trim())
            if (end < plain.length) append("…")
        }
    }

    private fun stripChordMarkup(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.lineSequence()
            .filter { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() &&
                    !Regex("""^[A-G][#bmM0-9/susaddim\s]*$""").matches(trimmed)
            }
            .joinToString(" ")
            .replace(Regex("""\[[^\]]+]"""), " ")
            .replace(Regex("<[^>]+>"), " ")
    }

    private fun extractFonki(html: String): FonkiSong {
        val title = Regex("<h2[^>]*>([^<]+)</h2>")
            .find(html)?.groupValues?.get(1)?.trim() ?: "Песня"

        val artist = Regex("<title>([^<]+)</title>")
            .find(html)?.groupValues?.get(1)
            ?.substringBefore(" - ")?.trim()
            ?.takeIf { it.length < 80 }
            ?: Regex("<h5[^>]*>\\s*<a[^>]+>([^<]+)</a>")
                .find(html)?.groupValues?.get(1)?.trim()
            ?: ""

        val lyrics = extractFonkiLyrics(html)
        val tracks = extractFonkiTracks(html, title)

        return FonkiSong(
            title = title,
            artist = artist,
            lyrics = lyrics,
            tracks = tracks,
        )
    }

    private fun extractFonkiTracks(html: String, songTitle: String): List<AudioTrack> {
        val tracks = mutableListOf<AudioTrack>()
        val baseUrl = "https://fonki.pro"

        val waveSource = Regex("""data-source="([^"]+\.mp3)"""")
            .find(html)?.groupValues?.get(1)

        if (waveSource != null) {
            val url = if (waveSource.startsWith("http")) waveSource else "$baseUrl$waveSource"
            tracks.add(AudioTrack(url, "Минус (фонограмма)"))
        }

        val thisAudio = Regex(
            """data-audio-name="[^"]*${Regex.escape(songTitle.take(15))}[^"]*"\s*data-audio-file="([^"]+\.mp3)"""",
        ).find(html)?.groupValues?.get(1)

        if (thisAudio != null && tracks.none { it.url == thisAudio }) {
            val url = if (thisAudio.startsWith("http")) thisAudio else "$baseUrl$thisAudio"
            tracks.add(AudioTrack(url, "Плюс (оригинал)"))
        }

        val fonInputs = Regex("""name="fon_file\[uploaded_file]\[]"\s*value="([^"]+\.mp3)"""")
            .findAll(html)
            .map { it.groupValues[1] }
            .toList()

        for (fi in fonInputs) {
            val url = if (fi.startsWith("http")) fi else "$baseUrl$fi"
            if (tracks.none { it.url == url }) {
                tracks.add(AudioTrack(url, "Фонограмма"))
            }
        }

        if (tracks.isEmpty()) {
            val allMp3 = Regex("""(?:plugin/sounds/uploads|storage/music)/[^"'\s]+\.mp3""")
                .findAll(html)
                .map { baseUrl + "/" + it.value }
                .distinct()
                .toList()
            for ((i, url) in allMp3.withIndex()) {
                tracks.add(AudioTrack(url, "Трек ${i + 1}"))
            }
        }

        return tracks
    }

    private fun extractHolyChords(html: String, pageUrl: String): FonkiSong {
        val title = Regex("<h2[^>]*>([^<]+)</h2>")
            .find(html)?.groupValues?.get(1)?.trim() ?: "Песня"

        val pageTitle = Regex("<title>([^<]+)</title>").find(html)?.groupValues?.get(1) ?: ""
        val artist = pageTitle
            .substringBefore(title.take(10))
            .replace(Regex("\\s*\\|.*"), "")
            .trim()
            .ifBlank {
                Regex("""class="d-none info_song"[^>]*>(.*?)</pre>""", RegexOption.DOT_MATCHES_ALL)
                    .find(html)?.groupValues?.get(1)
                    ?.replace(Regex("<[^>]+>"), "")
                    ?.trim()
                    ?.removePrefix(title)
                    ?.trim()
                    ?: ""
            }

        val lyrics = extractHolyChordsLyrics(html)
        val baseUrl = "https://holychords.pro"
        val tracks = mutableListOf<AudioTrack>()

        val waveSource = Regex("""data-source="([^"]+\.mp3)"""")
            .find(html)?.groupValues?.get(1)
        if (waveSource != null) {
            val url = if (waveSource.startsWith("http")) waveSource else "$baseUrl$waveSource"
            tracks.add(AudioTrack(url, "Минус (фонограмма)"))
        }

        val dlLinks = Regex("""<a[^>]*href=["']([^"']+\.mp3)["'][^>]*>""")
            .findAll(html)
            .map { it.groupValues[1] }
            .toList()

        for ((i, path) in dlLinks.withIndex()) {
            val url = if (path.startsWith("http")) path else "$baseUrl$path"
            if (tracks.none { it.url == url }) {
                tracks.add(AudioTrack(url, if (i == 0) "Аудио" else "Аудио ${i + 1}"))
            }
        }

        if (tracks.isEmpty()) {
            val storageMp3 = Regex("""/storage/music/[^"'\s]+\.mp3""")
                .findAll(html).map { it.value }.toList()
            val uploadMp3 = Regex("""/uploads/music/[^"'\s]+\.mp3""")
                .findAll(html).map { it.value }.toList()
            for ((i, path) in (storageMp3 + uploadMp3).withIndex()) {
                tracks.add(AudioTrack("$baseUrl$path", "Трек ${i + 1}"))
            }
        }

        return FonkiSong(
            title = title,
            artist = artist,
            lyrics = lyrics,
            tracks = tracks,
        )
    }

    private fun extractHolyChordsLyrics(html: String): String {
        val preTag = Regex(
            """<pre\s+id="music_text"[^>]*>(.*?)</pre>""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(html)?.groupValues?.get(1) ?: return ""
        return SongChordMarkup.fromHtmlFragment(preTag)
    }

    /**
     * Сохраняет MP3 в [Context.getFilesDir]/songs_audio — тот же каталог, что и при ручном импорте песни.
     * Публичная папка «Загрузки» не используется: пути туда ломаются при очистке загрузок, смене доступа
     * и не переживают сброс данных приложения так же предсказуемо, как привязка к JSON.
     */
    suspend fun downloadAudio(
        context: Context,
        url: String,
        songTitle: String,
        songArtist: String = "",
        trackLabel: String = "",
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "songs_audio").apply { mkdirs() }

        val suffix = if (trackLabel.isNotBlank()) " ($trackLabel)" else ""
        val safeName = buildString {
            if (songArtist.isNotBlank()) append("$songArtist - ")
            append(songTitle)
            append(suffix)
        }
            .replace(Regex("[^\\w\\d._\\-() ]"), "_")
            .take(120)
            .trim()
        val filename = "$safeName.mp3"
        val outFile = File(dir, filename)

        if (outFile.exists() && outFile.length() > 1024) {
            onProgress(100)
            return@withContext outFile
        }

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.connect()

        val totalSize = conn.contentLength
        var downloaded = 0

        conn.inputStream.use { input ->
            outFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (totalSize > 0) {
                        onProgress((downloaded * 100L / totalSize).toInt())
                    }
                }
            }
        }

        outFile
    }

    suspend fun saveLyrics(song: FonkiSong): File = withContext(Dispatchers.IO) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Bible",
        )
        dir.mkdirs()

        val safeName = buildString {
            if (song.artist.isNotBlank()) append("${song.artist} - ")
            append(song.title)
        }
            .replace(Regex("[^\\w\\d._\\-() ]"), "_")
            .take(120)
            .trim()
        val file = File(dir, "$safeName.txt")
        val content = buildString {
            appendLine(song.title)
            if (song.artist.isNotBlank()) appendLine(song.artist)
            appendLine()
            append(song.lyrics)
        }
        file.writeText(content, Charsets.UTF_8)
        file
    }

    private fun extractFonkiLyrics(html: String): String {
        val preTag = Regex(
            """<pre\s+id="music_text"[^>]*>(.*?)</pre>""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(html)?.groupValues?.get(1)

        val raw = preTag ?: Regex(
            """class="tab-pane\s[^"]*active[^"]*"[^>]*>(.*?)</div>\s*</div>\s*</div>""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(html)?.groupValues?.get(1) ?: Regex(
            """class="music_text_format"[^>]*>(.*?)</pre>""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(html)?.groupValues?.get(1) ?: ""

        return SongChordMarkup.fromHtmlFragment(raw)
    }

    private fun jsonText(obj: JSONObject?, key: String): String {
        if (obj == null || obj.isNull(key)) return ""
        return obj.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }.orEmpty()
    }

    private fun fetchHtml(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.connect()

        if (conn.responseCode !in 200..299) {
            throw RuntimeException("HTTP ${conn.responseCode}")
        }

        return conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    }

    private fun fetchJson(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.connect()

        if (conn.responseCode !in 200..299) {
            throw RuntimeException("HTTP ${conn.responseCode}")
        }

        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
        if (!body.startsWith("{") && !body.startsWith("[")) {
            throw RuntimeException("Сайт не вернул результаты поиска")
        }
        return body
    }
}
