package com.example.bible.data

import android.content.Context
import android.os.Environment
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo as YtVideoInfo
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

private const val TAG = "VideoExtractor"

data class PlaylistMediaItem(
    /** Устойчивый ключ для UI и выборки */
    val stableId: String,
    val title: String,
    /** Страница ролика (для yt-dlp и предпросмотра во внешнем приложении) */
    val pageUrl: String,
    val thumbnail: String?,
    val durationSec: Long?,
)

data class PlaylistInspection(
    val playlistTitle: String?,
    val items: List<PlaylistMediaItem>,
)

data class VideoInfo(
    val title: String,
    val filename: String,
    val platform: String,
)

object VideoExtractor {

    private fun isYouTubeUrl(url: String): Boolean {
        val l = url.lowercase()
        return "youtube.com" in l || "youtu.be" in l
    }

    /**
     * Веб-клиент YouTube часто получает «Sign in to confirm you're not a bot»;
     * клиент android обычно проходит без cookies (см. FAQ yt-dlp).
     */
    private fun YoutubeDLRequest.addYoutubeBotWorkaroundsIfNeeded(url: String) {
        if (!isYouTubeUrl(url)) return
        addOption("--extractor-args", "youtube:player_client=android")
    }

    @Volatile
    private var initialized = false

    @Volatile
    private var updated = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
                initialized = true
            } catch (e: YoutubeDLException) {
                throw RuntimeException("Не удалось инициализировать yt-dlp: ${e.message}", e)
            }
        }
    }

    suspend fun ensureUpdated(context: Context) = withContext(Dispatchers.IO) {
        if (updated) return@withContext
        init(context)
        try {
            YoutubeDL.getInstance().updateYoutubeDL(
                context,
                YoutubeDL.UpdateChannel.STABLE,
            )
        } catch (_: Exception) {
            // Если нет интернета, продолжаем с текущей версией
        }
        updated = true
    }

    suspend fun updateYtDlp(context: Context): YoutubeDL.UpdateStatus? = withContext(Dispatchers.IO) {
        init(context)
        val result = YoutubeDL.getInstance().updateYoutubeDL(
            context,
            YoutubeDL.UpdateChannel.STABLE,
        )
        updated = true
        result
    }

    suspend fun fetchInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        val info: YtVideoInfo = if (isYouTubeUrl(url)) {
            val req = YoutubeDLRequest(url)
            req.addYoutubeBotWorkaroundsIfNeeded(url)
            YoutubeDL.getInstance().getInfo(req)
        } else {
            YoutubeDL.getInstance().getInfo(url)
        }
        val title = info.title ?: "media_${System.currentTimeMillis()}"
        val ext = info.ext ?: "mp4"
        VideoInfo(
            title = title,
            filename = "$title.$ext",
            platform = detectPlatform(url),
        )
    }

    /**
     * Список элементов плейлиста или один ролик по ссылке.
     * [flatPlaylist] без полной загрузки метаданных каждого ролика — быстрее для длинных списков.
     */
    suspend fun inspectPlaylist(url: String, flatPlaylist: Boolean = true): PlaylistInspection =
        withContext(Dispatchers.IO) {
            val trimmed = url.trim()
            val req = YoutubeDLRequest(trimmed)
            req.addYoutubeBotWorkaroundsIfNeeded(trimmed)
            req.addOption("--dump-single-json")
            req.addOption("--no-warnings")
            req.addOption("--skip-download")
            if (flatPlaylist) {
                req.addOption("--flat-playlist")
            }
            if (!isYouTubeUrl(trimmed)) {
                req.addOption("--force-ipv4")
            }
            req.addOption("--socket-timeout", "60")
            req.addOption("--retries", "3")

            Log.d(TAG, "inspectPlaylist: $trimmed flat=$flatPlaylist")
            val response = try {
                YoutubeDL.getInstance().execute(req, null, null)
            } catch (e: YoutubeDLException) {
                Log.e(TAG, "inspectPlaylist YoutubeDLException", e)
                throw RuntimeException(e.message ?: "Не удалось прочитать список", e)
            } catch (e: InterruptedException) {
                Log.e(TAG, "inspectPlaylist interrupted", e)
                throw RuntimeException("Операция прервана", e)
            }
            try {
                parsePlaylistDumpJson(response.out.trim(), pageUrlFallback = trimmed)
            } catch (e: JSONException) {
                Log.e(TAG, "Playlist JSON parse failed", e)
                throw RuntimeException("Не удалось разобрать ответ yt-dlp", e)
            }
        }

    fun parsePlaylistDumpJson(json: String, pageUrlFallback: String): PlaylistInspection {
        val root = JSONObject(json)
        val playlistTitle = root.optString("title").takeIf { it.isNotBlank() }
        val type = root.optString("_type", "").ifBlank { "" }

        return when {
            type == "playlist" ->
                PlaylistInspection(
                    playlistTitle = playlistTitle,
                    items = entriesFromPlaylistArray(root.optJSONArray("entries")),
                )

            root.optString("id").isNotBlank() ||
                root.optString("webpage_url").isNotBlank() ||
                root.optString("title").isNotBlank() ->
                PlaylistInspection(
                    playlistTitle = playlistTitle,
                    items = singleVideoAsItemList(root, pageUrlFallback),
                )

            else -> PlaylistInspection(null, emptyList())
        }
    }

    private fun entriesFromPlaylistArray(entries: JSONArray?): List<PlaylistMediaItem> {
        if (entries == null || entries.length() == 0) return emptyList()
        val out = ArrayList<PlaylistMediaItem>(entries.length())
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            toPlaylistMediaItem(entry, i)?.let { out += it }
        }
        return out
    }

    private fun singleVideoAsItemList(video: JSONObject, pageUrlFallback: String): List<PlaylistMediaItem> {
        val item =
            toPlaylistMediaItem(video, 0)
                ?: run {
                    val urlGuess = normalizeMediaPageUrlObj(video, pageUrlFallback) ?: pageUrlFallback
                    val idForKey = video.optString("id", "")
                    PlaylistMediaItem(
                        stableId = "${idForKey}_${urlGuess.hashCode()}",
                        title = video.optString("title", "").takeIf { it.isNotBlank() } ?: urlGuess,
                        pageUrl = urlGuess,
                        thumbnail = pickThumbnailObj(video),
                        durationSec = optDurationSeconds(video),
                    )
                }
        return listOf(item)
    }

    private fun optDurationSeconds(o: JSONObject): Long? {
        if (!o.has("duration") || o.isNull("duration")) return null
        val d = o.optDouble("duration", 0.0)
        if (d < 1.0) return null
        return d.toLong()
    }

    private fun toPlaylistMediaItem(o: JSONObject, fallbackOrdinal: Int): PlaylistMediaItem? {
        val avail = o.optString("availability", "")
        if (avail == "private" || avail == "subscriber_only") return null

        val id = o.optString("id", "")
        val rawTitle = o.optString("title", "").takeIf { it.isNotBlank() }
        val normalizedUrl =
            normalizeMediaPageUrlObj(o, "")
                ?: if (looksLikeYoutubeId(id)) {
                    "https://www.youtube.com/watch?v=$id"
                } else {
                    id.takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty()
                }

        if (normalizedUrl.isBlank()) return null

        val title = rawTitle ?: id.ifBlank { "Элемент ${fallbackOrdinal + 1}" }
        val stableId = "${id}_${normalizedUrl}_${title}".hashCode().toString()
        return PlaylistMediaItem(
            stableId = stableId,
            title = title,
            pageUrl = normalizedUrl,
            thumbnail = pickThumbnailObj(o)
                ?: id.takeIf { looksLikeYoutubeId(it) }?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" },
            durationSec = optDurationSeconds(o),
        )
    }

    private fun looksLikeYoutubeId(id: String): Boolean =
        id.length in 10..13 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private fun normalizeMediaPageUrlObj(o: JSONObject, fallback: String): String? {
        o.optString("webpage_url").takeIf { it.startsWith("http") }?.let { return it }
        o.optString("original_url").takeIf { it.startsWith("http") }?.let { return it }

        val u = o.optString("url", "")
        if (u.startsWith("http")) return u

        val id = o.optString("id", "")
        val ie = o.optString("ie_key", "").lowercase()
        val explicitHost =
            o.optString("url_host").takeIf { it.startsWith("http") }.orEmpty()
        val host = explicitHost.takeIf { it.isNotBlank() } ?: inferHostFromIe(ie).orEmpty()

        if (host.isNotBlank() && id.isNotBlank() &&
            ("youtube" in host || "youtu.be" in host || "youtube" in ie)
        ) {
            return "https://www.youtube.com/watch?v=$id"
        }
        if (host.isNotBlank() && ("/watch" in u || u.startsWith("watch"))) {
            return host.trimEnd('/') + if (u.startsWith("/")) u else "/$u"
        }
        if (looksLikeYoutubeId(id)) {
            return "https://www.youtube.com/watch?v=$id"
        }
        return fallback.trim().takeIf { it.startsWith("http") }
    }

    private fun inferHostFromIe(ie: String): String? = when {
        "youtube" in ie -> "https://www.youtube.com"
        "vk" in ie -> "https://vk.com"
        else -> null
    }

    private fun pickThumbnailObj(o: JSONObject): String? {
        o.optString("thumbnail").takeIf { it.startsWith("http") }?.let { return it }
        val thumbs = o.optJSONArray("thumbnails") ?: return null
        if (thumbs.length() == 0) return null
        val last = thumbs.optJSONObject(thumbs.length() - 1)
        return last?.optString("url")?.takeIf { it.startsWith("http") }
    }

    suspend fun download(
        url: String,
        audioOnly: Boolean = false,
        videoQuality: Int = 720,
        skipIfFileExists: Boolean = true,
        onProgress: (Float, Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Bible",
        )
        dir.mkdirs()

        val request = YoutubeDLRequest(url)
        request.addYoutubeBotWorkaroundsIfNeeded(url)
        request.addOption("-o", dir.absolutePath + "/%(title).100s.%(ext)s")
        if (skipIfFileExists) {
            request.addOption("--no-overwrites")
        }
        request.addOption("--no-mtime")
        request.addOption("--no-check-certificates")
        request.addOption("--no-warnings")
        if (!isYouTubeUrl(url)) {
            request.addOption("--force-ipv4")
        }
        request.addOption("--socket-timeout", "30")
        request.addOption("--retries", "3")

        if (audioOnly) {
            request.addOption("-x")
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", "0")
        } else {
            val q = videoQuality
            request.addOption(
                "-f",
                "bestvideo[height<=$q][ext=mp4]+bestaudio[ext=m4a]/best[height<=$q][ext=mp4]/best[height<=$q]/best",
            )
            request.addOption("--merge-output-format", "mp4")
        }

        Log.d(TAG, "Executing yt-dlp for: $url audioOnly=$audioOnly q=$videoQuality")
        val response = try {
            YoutubeDL.getInstance().execute(request, null) { progress, etaInSeconds, line ->
                try {
                    onProgress(progress, etaInSeconds)
                } catch (t: Throwable) {
                    Log.w(TAG, "onProgress error: ${t.message}")
                }
            }
        } catch (e: YoutubeDLException) {
            Log.e(TAG, "YoutubeDLException", e)
            throw RuntimeException(e.message ?: "Ошибка yt-dlp", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "InterruptedException", e)
            throw RuntimeException("Скачивание прервано", e)
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error in execute", e)
            throw RuntimeException(e.message ?: "Неизвестная ошибка", e)
        }
        Log.d(TAG, "yt-dlp finished. exitCode=${response.exitCode} outLen=${response.out.length}")

        val outputLine = response.out
            .lineSequence()
            .lastOrNull { it.contains("[download]") && it.contains("Destination:") }

        val downloadedFile = outputLine
            ?.substringAfter("Destination:")
            ?.trim()
            ?.let { File(it) }
            ?.takeIf { it.exists() }

        if (downloadedFile != null) return@withContext downloadedFile

        val mergedLine = response.out
            .lineSequence()
            .lastOrNull { it.contains("[Merger]") || it.contains("[ExtractAudio]") }

        val mergedFile = mergedLine
            ?.substringAfter("Merging formats into \"")
            ?.substringBefore("\"")
            ?.let { File(it) }
            ?.takeIf { it.exists() }
            ?: mergedLine
                ?.substringAfter("Destination: ")
                ?.trim()
                ?.let { File(it) }
                ?.takeIf { it.exists() }

        if (mergedFile != null) return@withContext mergedFile

        val newest = dir.listFiles()
            ?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }

        newest ?: throw RuntimeException("Файл не найден после скачивания. Лог:\n${response.out}")
    }

    /** Человеческий текст вместо технического вывода yt-dlp. */
    fun userMessage(msg: String): String = when {
        "No address associated with hostname" in msg ||
            "Unable to resolve host" in msg || "DNS" in msg.uppercase() ||
            "Network is unreachable" in msg || "Connection refused" in msg ->
            "Пожалуйста, подключитесь к сети Интернет"
        "HTTP Error 403" in msg ->
            "Доступ запрещён (403). Возможно, ссылка устарела."
        "HTTP Error 404" in msg ->
            "Видео не найдено (404). Проверьте ссылку."
        "is not a valid URL" in msg || "Unsupported URL" in msg ->
            "Неподдерживаемая ссылка."
        "Unable to extract" in msg || "please report this issue" in msg ->
            "Эта платформа временно не поддерживается.\nНажмите ↻ для обновления yt-dlp."
        "yt-dlp -U" in msg ->
            "Требуется обновление. Нажмите ↻ в правом верхнем углу."
        "not a bot" in msg.lowercase() || "sign in to confirm" in msg.lowercase() ->
            "YouTube запросил проверку (бот). Нажмите ↻ и обновите yt-dlp, затем повторите. " +
                "Если снова ошибка — попробуйте позже или другую сеть (Wi\u2011Fi / мобильный интернет)."
        else -> msg
    }

    fun detectPlatform(url: String): String {
        val lower = url.lowercase()
        return when {
            "youtube.com" in lower || "youtu.be" in lower -> "YouTube"
            "rutube.ru" in lower -> "Rutube"
            "vk.com/video" in lower || "vkvideo" in lower || "vk.com/clip" in lower -> "VK Video"
            "dailymotion" in lower -> "Dailymotion"
            "vimeo.com" in lower -> "Vimeo"
            "tiktok.com" in lower -> "TikTok"
            "instagram.com" in lower -> "Instagram"
            "twitter.com" in lower || "x.com" in lower -> "X (Twitter)"
            "ok.ru" in lower -> "Одноклассники"
            "dzen.ru" in lower || "zen.yandex" in lower -> "Дзен"
            "soundcloud.com" in lower -> "SoundCloud"
            else -> "Медиа"
        }
    }
}
