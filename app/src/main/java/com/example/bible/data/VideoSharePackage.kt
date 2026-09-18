package com.example.bible.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

enum class VideoShareImportError {
    MISSING_MANIFEST,
    FULL_APP_BACKUP,
    WRONG_FORMAT,
    EMPTY,
    IO_OR_PARSE,
}

sealed class VideoShareImportOutcome {
    data class Ok(
        val video: BibleUserVideo,
        val thoughts: List<VideoThought>,
        val videoAdded: Boolean,
        val filesCopied: Int,
    ) : VideoShareImportOutcome()

    data class Err(val error: VideoShareImportError) : VideoShareImportOutcome()
}

/**
 * Обмен одним видео и заметками между установками приложения.
 *
 * ZIP: manifest.json, video.json, thoughts.json, опционально media/video.*
 * JSON (только заметки): manifest в корне без media/.
 */
object VideoSharePackage {

    const val FORMAT = "bible_video_share"
    private const val VERSION = 1
    const val MANIFEST_NAME = "manifest.json"
    const val VIDEO_JSON_NAME = "video.json"
    const val THOUGHTS_JSON_NAME = "thoughts.json"
    private const val MEDIA_VIDEO_PREFIX = "media/video"

    fun readFormat(file: File): String? = try {
        when {
            file.name.endsWith(".json", ignoreCase = true) || looksLikeJson(file) -> {
                JSONObject(file.readText(Charsets.UTF_8)).optString("format").takeIf { it.isNotBlank() }
            }
            file.name.endsWith(".zip", ignoreCase = true) -> {
                ZipFile(file).use { zf ->
                    val entry = zf.entries().asSequence().firstOrNull { e ->
                        !e.isDirectory && e.name.replace('\\', '/').trimStart('/')
                            .equals(MANIFEST_NAME, ignoreCase = true)
                    } ?: return@use null
                    val text = zf.getInputStream(entry).use { it.readBytes().decodeToString() }
                    JSONObject(text).optString("format").takeIf { it.isNotBlank() }
                }
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    fun thoughtsPlainText(videoTitle: String, thoughts: List<VideoThought>): String {
        val lines = mutableListOf("Заметки к видео «$videoTitle»")
        if (thoughts.isEmpty()) {
            lines += "(заметок нет)"
            return lines.joinToString("\n")
        }
        thoughts.forEachIndexed { i, t ->
            val time = t.positionMs?.let { formatTimeMs(it) }
            lines += when {
                time != null -> "${i + 1}. [$time] ${t.text}"
                else -> "${i + 1}. ${t.text}"
            }
        }
        return lines.joinToString("\n")
    }

    suspend fun exportThoughtsJson(
        context: Context,
        video: BibleUserVideo,
        thoughts: List<VideoThought>,
    ): File = withContext(Dispatchers.IO) {
        val root = wrapRootJson(video, thoughts, includeVideo = false)
        val out = File(
            context.cacheDir,
            "video_notes_${safeFilePart(video.title)}_${System.currentTimeMillis()}.json",
        )
        out.writeText(root.toString(2), Charsets.UTF_8)
        out
    }

    suspend fun exportVideoWithThoughtsZip(
        context: Context,
        video: BibleUserVideo,
        localFile: File?,
        thoughts: List<VideoThought>,
    ): File = withContext(Dispatchers.IO) {
        val zipFile = File(
            context.cacheDir,
            "video_${safeFilePart(video.title)}_${System.currentTimeMillis()}.zip",
        )
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val manifest = JSONObject().apply {
                put("format", FORMAT)
                put("version", VERSION)
                put("includeVideo", localFile != null && localFile.isFile && localFile.length() > 64)
                put("includeThoughts", thoughts.isNotEmpty())
            }
            writeZipEntry(zos, MANIFEST_NAME, manifest.toString())
            writeZipEntry(zos, VIDEO_JSON_NAME, videoMetaJson(video).toString())
            writeZipEntry(zos, THOUGHTS_JSON_NAME, thoughtsArrayJson(thoughts).toString())
            if (localFile != null && localFile.isFile && localFile.length() > 64) {
                val ext = localFile.extension.ifBlank { "mp4" }.lowercase()
                val entryName = "$MEDIA_VIDEO_PREFIX.$ext"
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(localFile).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        zipFile
    }

    suspend fun importFromFile(
        context: Context,
        file: File,
        existingVideos: List<BibleUserVideo>,
        videoLibrary: BibleVideoLibrary,
    ): VideoShareImportOutcome = withContext(Dispatchers.IO) {
        try {
            if (file.name.endsWith(".json", ignoreCase = true) || looksLikeJson(file)) {
                val root = JSONObject(file.readText(Charsets.UTF_8))
                return@withContext importRoot(context, root, emptyMap(), existingVideos, videoLibrary)
            }
            ZipFile(file).use { zf ->
                var manifest: JSONObject? = null
                var videoJson: JSONObject? = null
                var thoughtsJson: JSONArray? = null
                val extracted = mutableMapOf<String, File>()
                val work = File(context.cacheDir, "video_share_${System.currentTimeMillis()}").apply { mkdirs() }
                try {
                    for (entry in zf.entries().asSequence()) {
                        if (entry.isDirectory) continue
                        val name = entry.name.replace('\\', '/').trimStart('/')
                        val bytes = zf.getInputStream(entry).use { it.readBytes() }
                        when {
                            name.equals(MANIFEST_NAME, ignoreCase = true) ->
                                manifest = JSONObject(String(bytes, Charsets.UTF_8))
                            name.equals(VIDEO_JSON_NAME, ignoreCase = true) ->
                                videoJson = JSONObject(String(bytes, Charsets.UTF_8))
                            name.equals(THOUGHTS_JSON_NAME, ignoreCase = true) ->
                                thoughtsJson = JSONArray(String(bytes, Charsets.UTF_8))
                            name.startsWith("media/") -> {
                                val dest = File(work, name.substringAfterLast('/').ifBlank { "video.bin" })
                                dest.parentFile?.mkdirs()
                                dest.outputStream().use { it.write(bytes) }
                                extracted[name] = dest
                            }
                        }
                    }
                    val man = manifest
                    if (man == null && videoJson == null) {
                        return@use VideoShareImportOutcome.Err(VideoShareImportError.MISSING_MANIFEST)
                    }
                    if (man != null) {
                        when (man.optString("format")) {
                            "bible_app_export" ->
                                return@use VideoShareImportOutcome.Err(VideoShareImportError.FULL_APP_BACKUP)
                            "" -> Unit
                            FORMAT -> Unit
                            else ->
                                return@use VideoShareImportOutcome.Err(VideoShareImportError.WRONG_FORMAT)
                        }
                    }
                    val root = JSONObject().apply {
                        put("format", FORMAT)
                        if (videoJson != null) put("video", videoJson)
                        if (thoughtsJson != null) put("thoughts", thoughtsJson)
                        put(
                            "includeVideo",
                            man?.optBoolean("includeVideo", false)
                                ?: extracted.isNotEmpty(),
                        )
                    }
                    importRoot(context, root, extracted, existingVideos, videoLibrary)
                } finally {
                    work.deleteRecursively()
                }
            }
        } catch (_: Exception) {
            VideoShareImportOutcome.Err(VideoShareImportError.IO_OR_PARSE)
        }
    }

    private suspend fun importRoot(
        context: Context,
        root: JSONObject,
        extracted: Map<String, File>,
        existingVideos: List<BibleUserVideo>,
        videoLibrary: BibleVideoLibrary,
    ): VideoShareImportOutcome {
        val fmt = root.optString("format")
        if (fmt.isNotBlank() && fmt != FORMAT) {
            return VideoShareImportOutcome.Err(VideoShareImportError.WRONG_FORMAT)
        }
        if (fmt == "bible_app_export") {
            return VideoShareImportOutcome.Err(VideoShareImportError.FULL_APP_BACKUP)
        }
        val videoObj = root.optJSONObject("video")
        val thoughts = parseThoughtsArray(root.optJSONArray("thoughts"))
        if (videoObj == null && thoughts.isEmpty()) {
            return VideoShareImportOutcome.Err(VideoShareImportError.EMPTY)
        }
        val title = videoObj?.optString("title", "Видео")?.ifBlank { "Видео" } ?: "Видео"
        val url = videoObj?.optString("url", "")?.takeIf { it.isNotBlank() }
        val src = videoObj?.optString("src", "share")?.ifBlank { "share" } ?: "share"
        val tags = if (videoObj != null && videoObj.has("tags")) {
            val t = videoObj.getJSONArray("tags")
            (0 until t.length()).map { t.getString(it).trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        val includeVideo = root.optBoolean("includeVideo", false)
        var filesCopied = 0
        val reused = existingVideos.firstOrNull { v ->
            !url.isNullOrBlank() && v.sourceUrl == url
        }
        var fileName = reused?.fileName.orEmpty()
        if (includeVideo) {
            val mediaFile = extracted.entries.firstOrNull { (k, _) ->
                k.startsWith("media/")
            }?.value ?: extracted.values.firstOrNull { it.isFile }
            if (mediaFile != null && mediaFile.isFile) {
                videoLibrary.importFromFile(mediaFile).onSuccess {
                    fileName = it
                    filesCopied++
                }
            }
        }
        val videoAdded: Boolean
        val video: BibleUserVideo = when {
            reused != null && (fileName.isBlank() || fileName == reused.fileName) -> {
                videoAdded = fileName.isNotBlank() && reused.fileName.isBlank()
                if (videoAdded) {
                    reused.copy(fileName = fileName, source = src, tags = tags.ifEmpty { reused.tags })
                } else {
                    reused.copy(
                        title = title.ifBlank { reused.title },
                        source = src,
                        tags = tags.ifEmpty { reused.tags },
                    )
                }
            }
            reused != null && fileName.isNotBlank() -> {
                videoAdded = true
                reused.copy(fileName = fileName, title = title, source = src, sourceUrl = url, tags = tags)
            }
            else -> {
                videoAdded = true
                BibleUserVideo(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    tags = tags,
                    fileName = fileName,
                    source = src,
                    sourceUrl = url,
                )
            }
        }
        return VideoShareImportOutcome.Ok(
            video = video,
            thoughts = thoughts,
            videoAdded = videoAdded,
            filesCopied = filesCopied,
        )
    }

    private fun parseThoughtsArray(arr: JSONArray?): List<VideoThought> {
        if (arr == null || arr.length() == 0) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            VideoThought.fromJson(o).takeIf { it.text.isNotBlank() }
        }
    }

    private fun wrapRootJson(
        video: BibleUserVideo,
        thoughts: List<VideoThought>,
        includeVideo: Boolean,
    ): JSONObject = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("includeVideo", includeVideo)
        put("includeThoughts", thoughts.isNotEmpty())
        put("video", videoMetaJson(video))
        put("thoughts", thoughtsArrayJson(thoughts))
    }

    private fun videoMetaJson(video: BibleUserVideo): JSONObject = JSONObject().apply {
        put("title", video.title)
        put("src", video.source)
        if (!video.sourceUrl.isNullOrBlank()) put("url", video.sourceUrl)
        if (video.tags.isNotEmpty()) {
            put("tags", JSONArray().apply { video.tags.forEach { put(it) } })
        }
    }

    private fun thoughtsArrayJson(thoughts: List<VideoThought>): JSONArray {
        val arr = JSONArray()
        thoughts.forEach { arr.put(it.toJson()) }
        return arr
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, text: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(text.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun looksLikeJson(file: File): Boolean = try {
        val head = file.inputStream().use { ins ->
            val buf = ByteArray(24)
            val n = ins.read(buf)
            if (n <= 0) "" else String(buf, 0, n, Charsets.UTF_8)
        }
        head.trimStart().startsWith("{")
    } catch (_: Exception) {
        false
    }

    private fun formatTimeMs(ms: Int): String {
        if (ms <= 0) return "0:00"
        val s = ms / 1000
        val m = s / 60
        val r = s % 60
        return "$m:${r.toString().padStart(2, '0')}"
    }

    private fun safeFilePart(name: String): String {
        val t = name.replace(Regex("[^\\p{L}\\p{N}_.-]+"), "_").trim('_')
        return t.take(40).ifBlank { "video" }
    }
}
