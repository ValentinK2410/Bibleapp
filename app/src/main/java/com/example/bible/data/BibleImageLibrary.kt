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
 * Копирование изображений в [MediaCatalogPaths.PICTURES].
 */
class BibleImageLibrary(private val context: Context) {

    private val dir: File
        get() = MediaCatalogPaths.picturesDir(context)

    fun fileFor(storedName: String): File = File(dir, storedName)

    suspend fun importFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("gif") -> "gif"
                else -> "jpg"
            }
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
            conn.readTimeout = 30_000
            conn.connect()
            if (conn.responseCode != 200) {
                return@withContext Result.failure(IllegalStateException("HTTP ${conn.responseCode}"))
            }
            val ext = when {
                urlStr.contains(".png", ignoreCase = true) -> "png"
                urlStr.contains(".webp", ignoreCase = true) -> "webp"
                urlStr.contains(".gif", ignoreCase = true) -> "gif"
                else -> "jpg"
            }
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

    /** Копия в [timemark_attachments] для метки в редакторе таймкодов. */
    suspend fun copyToTimemarkPath(image: BibleUserImage): Result<String> = withContext(Dispatchers.IO) {
        try {
            val src = File(dir, image.fileName)
            if (!src.isFile) {
                return@withContext Result.failure(IllegalStateException("Нет файла в базе"))
            }
            val outDir = File(context.filesDir, "timemark_attachments").apply { mkdirs() }
            val ext = src.extension.ifBlank { "jpg" }
            val dest = File(outDir, "img_${System.currentTimeMillis()}.$ext")
            src.copyTo(dest, overwrite = true)
            Result.success(dest.absolutePath)
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
        private const val USER_AGENT = "BibleApp/1.0 (Android; image library; not a bot)"
    }
}
