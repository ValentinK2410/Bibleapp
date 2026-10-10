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
     * Клиенты плеера YouTube по очереди: сначала выбор самого yt-dlp, затем запасные.
     * YouTube регулярно перестаёт отдавать форматы то одному, то другому клиенту.
     */
    private val YoutubeClientStrategies: List<String?> = listOf(
        null,
        "youtube:player_client=default,mweb",
        "youtube:player_client=tv,web_safari",
        "youtube:player_client=android_vr",
        "youtube:player_client=android",
    )

    private fun YoutubeDLRequest.addYoutubeClient(url: String, strategy: String?) {
        if (!isYouTubeUrl(url) || strategy == null) return
        addOption("--extractor-args", strategy)
    }

    private fun YoutubeDLRequest.addYoutubeBotWorkaroundsIfNeeded(url: String) = addYoutubeClient(url, null)

    /** Ошибка, при которой стоит попробовать другой клиент YouTube. */
    private fun isClientProblem(msg: String): Boolean {
        val m = msg.lowercase()
        return "requested format is not available" in m ||
            "sign in to confirm" in m || "not a bot" in m ||
            "unable to extract" in m || "failed to extract any player response" in m ||
            "http error 403" in m || "po token" in m || "only images are available" in m ||
            "this video is not available" in m && "playlist" !in m
    }

    /** Обрыв связи: повторяем ту же попытку через IPv4. */
    private fun isNetworkGlitch(msg: String): Boolean {
        val m = msg.lowercase()
        return "unexpected_eof" in m || "eof occurred" in m || "ssl" in m ||
            "timed out" in m || "connection reset" in m || "remote end closed" in m ||
            "incompleteread" in m || "broken pipe" in m
    }

    fun isPlaylistUrl(url: String): Boolean {
        val l = url.lowercase()
        if (!isYouTubeUrl(l)) return false
        return "/playlist" in l || ("list=" in l && "v=" !in l && "youtu.be/" !in l)
    }

    @Volatile
    private var initialized = false

    @Volatile
    private var updated = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var retriedAfterUpdate = false

    fun init(context: Context) {
        appContext = context.applicationContext
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

    private const val UPDATE_INTERVAL_MS = 20L * 60 * 60 * 1000

    /** Обновляет yt-dlp не чаще раза в сутки: YouTube часто ломает старые версии. */
    suspend fun ensureUpdated(context: Context) = withContext(Dispatchers.IO) {
        if (updated) return@withContext
        init(context)
        val prefs = context.applicationContext.getSharedPreferences("ytdlp", Context.MODE_PRIVATE)
        val last = prefs.getLong("last_update", 0L)
        if (System.currentTimeMillis() - last > UPDATE_INTERVAL_MS) {
            runCatching { updateWithFallback(context) }
                .onSuccess { prefs.edit().putLong("last_update", System.currentTimeMillis()).apply() }
                .onFailure { Log.w(TAG, "yt-dlp update failed: ${it.message}") }
        }
        updated = true
    }

    /** Ночная сборка yt-dlp быстрее получает исправления для YouTube; если не вышло — стабильная. */
    private fun updateWithFallback(context: Context): YoutubeDL.UpdateStatus? =
        try {
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
        } catch (e: Exception) {
            Log.w(TAG, "nightly update failed, trying stable: ${e.message}")
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        }

    suspend fun updateYtDlp(context: Context): YoutubeDL.UpdateStatus? = withContext(Dispatchers.IO) {
        init(context)
        val result = updateWithFallback(context)
        context.applicationContext.getSharedPreferences("ytdlp", Context.MODE_PRIVATE)
            .edit().putLong("last_update", System.currentTimeMillis()).apply()
        updated = true
        result
    }

    suspend fun fetchInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        val info: YtVideoInfo = if (isYouTubeUrl(url)) {
            var lastError: Exception? = null
            var found: YtVideoInfo? = null
            for (strategy in YoutubeClientStrategies) {
                try {
                    val req = YoutubeDLRequest(url)
                    req.addYoutubeClient(url, strategy)
                    found = YoutubeDL.getInstance().getInfo(req)
                    break
                } catch (e: Exception) {
                    lastError = e
                    if (!isClientProblem(e.message.orEmpty())) break
                }
            }
            found ?: throw (lastError ?: RuntimeException("Не удалось получить сведения о ролике"))
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
                val ctx = appContext
                if (ctx != null && !retriedAfterUpdate && isClientProblem(e.message.orEmpty())) {
                    retriedAfterUpdate = true
                    runCatching { updateYtDlp(ctx) }
                    return@withContext inspectPlaylist(url, flatPlaylist)
                }
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

    /**
     * Останавливает работающий процесс yt-dlp по его [processId] — так делаются пауза и отмена.
     * Недокачанный файл остаётся на диске, повторный запуск продолжит его с того же места.
     */
    fun cancelProcess(processId: String): Boolean =
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }.getOrDefault(false)

    suspend fun download(
        url: String,
        audioOnly: Boolean = false,
        videoQuality: Int = 720,
        skipIfFileExists: Boolean = true,
        /** Метка процесса: по ней [cancelProcess] ставит эту загрузку на паузу. */
        processId: String? = null,
        onProgress: (Float, Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Bible",
        )
        dir.mkdirs()

        fun buildRequest(strategy: String?, forceIpv4: Boolean): YoutubeDLRequest {
            val request = YoutubeDLRequest(url)
            request.addYoutubeClient(url, strategy)
            request.addOption("-o", dir.absolutePath + "/%(title).100s.%(ext)s")
            if (skipIfFileExists) {
                request.addOption("--no-overwrites")
            }
            request.addOption("--no-mtime")
            request.addOption("--no-check-certificates")
            request.addOption("--no-warnings")
            request.addOption("--no-playlist")
            if (forceIpv4 || !isYouTubeUrl(url)) {
                request.addOption("--force-ipv4")
            }
            request.addOption("--socket-timeout", "30")
            request.addOption("--retries", "10")
            request.addOption("--fragment-retries", "10")
            request.addOption("--extractor-retries", "3")
            request.addOption("--retry-sleep", "3")
            // Докачиваем прерванный файл вместо повторного скачивания с нуля — нужно для паузы.
            request.addOption("--continue")
            if (audioOnly) {
                request.addOption("-f", "ba/b")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "0")
            } else {
                val q = videoQuality
                request.addOption("-f", "bv*[height<=$q]+ba/b[height<=$q]/bv*+ba/b")
                request.addOption("-S", "res:$q,ext:mp4:m4a")
                request.addOption("--merge-output-format", "mp4")
            }
            return request
        }

        val strategies = if (isYouTubeUrl(url)) YoutubeClientStrategies else listOf(null)
        var attempt: com.yausername.youtubedl_android.YoutubeDLResponse? = null
        var lastError: Throwable? = null
        loop@ for (strategy in strategies) {
            for ((attemptNo, forceIpv4) in listOf(false, true, true, true, true).withIndex()) {
                if (attemptNo > 1) Thread.sleep(attemptNo * 8_000L)
                Log.d(TAG, "yt-dlp: $url audioOnly=$audioOnly q=$videoQuality client=$strategy ipv4=$forceIpv4")
                try {
                    attempt = YoutubeDL.getInstance().execute(buildRequest(strategy, forceIpv4), processId) { progress, etaInSeconds, _ ->
                        try {
                            onProgress(progress, etaInSeconds)
                        } catch (t: Throwable) {
                            Log.w(TAG, "onProgress error: ${t.message}")
                        }
                    }
                    break@loop
                } catch (e: InterruptedException) {
                    throw RuntimeException("Скачивание прервано", e)
                } catch (e: YoutubeDL.CanceledException) {
                    throw RuntimeException("Скачивание прервано", e)
                } catch (e: Throwable) {
                    Log.w(TAG, "yt-dlp attempt failed: ${e.message?.take(300)}")
                    lastError = e
                    val msg = e.message.orEmpty()
                    when {
                        isNetworkGlitch(msg) -> continue
                        isClientProblem(msg) -> continue@loop
                        else -> break@loop
                    }
                }
            }
        }
        val response = attempt ?: throw RuntimeException(lastError?.message ?: "Ошибка yt-dlp", lastError)
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
        "Requested format is not available" in msg ->
            "YouTube не отдал этот ролик ни в одном формате. Обновите yt-dlp (↻) и повторите — или выберите «Аудио»."
        "UNEXPECTED_EOF" in msg || "EOF occurred" in msg || "timed out" in msg.lowercase() ||
            "handshake" in msg.lowercase() ->
            "Связь с YouTube постоянно обрывается — похоже, сеть замедляет YouTube. Нажмите «Повторить ошибки» позже, " +
                "попробуйте другую сеть (Wi\u2011Fi / мобильный интернет) или включите VPN."
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
