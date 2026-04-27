package com.example.bible.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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

/** Результат поиска по каталогам holychords.pro и fonki.pro (раздел «Музыка» на сайтах). */
data class SongCatalogHit(
    val title: String,
    val artist: String,
    val pageUrl: String,
    val sourceLabel: String,
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
     * Поиск по публичным спискам [site]/musics (несколько страниц), фильтр по названию и исполнителю.
     * Полноценного API у сайтов нет — подбираются совпадения по открытым каталогам.
     */
    suspend fun searchSongCatalog(
        query: String,
        maxPagesPerSite: Int = 12,
        maxResults: Int = 40,
    ): List<SongCatalogHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val qLower = q.lowercase()
        val words = qLower.split(Regex("\\s+")).filter { it.isNotBlank() }

        fun matches(artist: String, title: String): Boolean {
            val hay = "$artist $title".lowercase()
            if (qLower in hay) return true
            if (words.isEmpty()) return false
            return words.all { it in hay }
        }

        val seen = mutableSetOf<String>()
        val out = mutableListOf<SongCatalogHit>()
        val sites = listOf(
            "https://holychords.pro" to "HolyChords",
            "https://fonki.pro" to "Fonki",
        )

        for ((base, label) in sites) {
            if (out.size >= maxResults) break
            for (page in 1..maxPagesPerSite) {
                if (out.size >= maxResults) break
                delay(45)
                val html = try {
                    fetchHtml("$base/musics?page=$page")
                } catch (_: Exception) {
                    break
                }
                val chunks = html.split("""class="music_item media""")
                for (chunk in chunks.drop(1)) {
                    if (out.size >= maxResults) break
                    val block = chunk.take(12_000)
                    val id = Regex("""data-audio-id="(\d+)"""").find(block)?.groupValues?.get(1) ?: continue
                    val artist = Regex("""data-artist-name="([^"]*)"""").find(block)?.groupValues?.get(1)?.trim().orEmpty()
                    val title = Regex("""data-audio-name="([^"]*)"""").find(block)?.groupValues?.get(1)?.trim().orEmpty()
                    if (title.isBlank()) continue
                    if (!matches(artist, title)) continue
                    val url = if ("fonki.pro" in base) {
                        "https://fonki.pro/minus/$id"
                    } else {
                        "https://holychords.pro/$id"
                    }
                    if (!seen.add(url)) continue
                    out.add(SongCatalogHit(title = title, artist = artist, pageUrl = url, sourceLabel = label))
                }
            }
        }
        out
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

        val lines = preTag.lines()
        val filtered = lines.filter { line ->
            val trimmed = line.trim()
            !Regex("""^\s*[A-G][#bmM]?\s""").containsMatchIn(trimmed) &&
                !Regex("""^[A-G][#bm/\d\s]*$""").matches(trimmed)
        }

        return filtered.joinToString("\n")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("&[a-z]+;"), " ")
            .replace(Regex(" {2,}"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
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

        return raw
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("&[a-z]+;"), " ")
            .replace(Regex("\\t+"), "")
            .replace(Regex(" {2,}"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
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
}
