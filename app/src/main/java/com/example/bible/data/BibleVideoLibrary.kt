package com.example.bible.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Копирование видео в [MediaCatalogPaths.VIDEOS].
 */
class BibleVideoLibrary(private val context: Context) {

    private val dir: File
        get() = MediaCatalogPaths.videosDir(context)

    fun fileFor(storedName: String): File = File(dir, storedName)

    suspend fun importFromFile(sourceFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!sourceFile.isFile || !sourceFile.canRead()) {
                return@withContext Result.failure(IllegalStateException("Файл недоступен"))
            }
            val ext = sourceFile.extension.lowercase().ifBlank { "mp4" }
            val name = "${UUID.randomUUID()}.$ext"
            val out = File(dir, name)
            sourceFile.inputStream().use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            if (!out.exists() || out.length() == 0L) {
                out.delete()
                return@withContext Result.failure(IllegalStateException("Пустой файл"))
            }
            Result.success(name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Копирует файл из произвольного пути (например, «Загрузки/Bible/») в [dir],
     * затем удаляет исходный файл при успехе.
     */
    suspend fun importFromExternalFile(sourceFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!sourceFile.isFile || !sourceFile.canRead()) {
                return@withContext Result.failure(IllegalStateException("Файл недоступен"))
            }
            val ext = sourceFile.extension.lowercase().ifBlank { "mp4" }
            val name = "${UUID.randomUUID()}.$ext"
            val out = File(dir, name)
            sourceFile.inputStream().use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            if (!out.exists() || out.length() == 0L) {
                out.delete()
                return@withContext Result.failure(IllegalStateException("Пустой файл"))
            }
            try {
                sourceFile.delete()
            } catch (_: Exception) {
            }
            Result.success(name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val ext = extensionForMime(mime)
            val name = "${UUID.randomUUID()}.$ext"
            val out = File(dir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            } ?: return@withContext Result.failure(IllegalStateException("Не удалось открыть файл"))
            if (!out.exists() || out.length() == 0L) {
                out.delete()
                return@withContext Result.failure(IllegalStateException("Пустой файл"))
            }
            Result.success(name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFromUrl(urlStr: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 20_000
            conn.readTimeout = 120_000
            conn.connect()
            if (conn.responseCode != 200) {
                return@withContext Result.failure(IllegalStateException("HTTP ${conn.responseCode}"))
            }
            val mime = conn.contentType?.substringBefore(';')?.trim().orEmpty()
            val extFromMime = extensionForMime(mime).takeIf { mime.startsWith("video/") }
            val ext = extFromMime ?: extensionFromUrl(urlStr)
            val name = "${UUID.randomUUID()}.$ext"
            val out = File(dir, name)
            conn.inputStream.use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            if (out.length() == 0L) {
                out.delete()
                return@withContext Result.failure(IllegalStateException("Пустой ответ"))
            }
            Result.success(name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteStoredFile(fileName: String) {
        try {
            File(dir, fileName).takeIf { it.isFile }?.delete()
        } catch (_: Exception) { }
    }

    companion object {
        private const val USER_AGENT = "BibleApp/1.0 (Android; video library; not a bot)"

        fun extensionForMime(mime: String): String = when {
            mime.contains("mp4") -> "mp4"
            mime.contains("webm") -> "webm"
            mime.contains("ogg") || mime.contains("ogv") -> "ogv"
            mime.contains("quicktime") || mime.contains("mov") -> "mov"
            mime.contains("3gpp") -> "3gp"
            mime.contains("x-matroska") || mime.contains("mkv") -> "mkv"
            else -> "mp4"
        }

        private fun extensionFromUrl(urlStr: String): String {
            val path = urlStr.substringBefore('?').lowercase()
            return when {
                path.endsWith(".webm") -> "webm"
                path.endsWith(".ogv") -> "ogv"
                path.endsWith(".mov") -> "mov"
                path.endsWith(".mkv") -> "mkv"
                path.endsWith(".3gp") -> "3gp"
                path.endsWith(".ogg") -> "ogg"
                else -> "mp4"
            }
        }
    }
}
