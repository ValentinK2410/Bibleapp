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
import java.io.File

private const val TAG = "VideoExtractor"

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

    suspend fun download(
        url: String,
        audioOnly: Boolean = false,
        videoQuality: Int = 720,
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
