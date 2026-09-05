package com.example.bible.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Картинки GigaChat приходят тегом `<img src="uuid" fuse="true"/>`.
 * Скачиваем файл и подменяем src на локальное имя `gigachat-file:…`.
 */
object GigaChatImages {

    const val FILE_PREFIX = "gigachat-file:"
    private val imgTag = Regex(
        """<img\s+[^>]*\bsrc\s*=\s*["']([^"']+)["'][^>]*/?>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val uuid = Regex(
        """^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""",
    )

    fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "gigachat_images").apply { mkdirs() }

    fun hasRemoteIds(content: String): Boolean = remoteIds(content).isNotEmpty()

    fun remoteIds(content: String): List<String> =
        imgTag.findAll(content).map { it.groupValues[1].trim() }.filter { uuid.matches(it) }.distinct()

    fun localFileName(src: String): String? {
        val value = src.trim()
        return when {
            value.startsWith(FILE_PREFIX) -> value.removePrefix(FILE_PREFIX)
            else -> null
        }
    }

    fun fileFor(dir: File, src: String): File? {
        val name = localFileName(src) ?: return null
        if (name.isEmpty() || name.contains('/') || name.contains('\\')) return null
        val file = File(dir, name)
        return file.takeIf { it.isFile }
    }

    suspend fun materialize(
        content: String,
        download: suspend (fileId: String) -> ByteArray?,
        dir: File,
    ): String {
        var result = content
        for (id in remoteIds(content)) {
            val bytes = download(id) ?: continue
            if (bytes.isEmpty()) continue
            val name = "${id}.jpg"
            val file = File(dir, name)
            runCatching { file.writeBytes(bytes) }.getOrElse { continue }
            result = result.replace(id, "$FILE_PREFIX$name")
        }
        return result
    }

    fun parts(content: String, dir: File): List<GigaChatContentPart> {
        val out = mutableListOf<GigaChatContentPart>()
        var last = 0
        for (match in imgTag.findAll(content)) {
            val before = content.substring(last, match.range.first).trim()
            if (before.isNotEmpty()) out += GigaChatContentPart.Text(before)
            val src = match.groupValues[1].trim()
            val file = fileFor(dir, src)
            if (file != null) {
                out += GigaChatContentPart.Image(file)
            } else if (uuid.matches(src)) {
                out += GigaChatContentPart.MissingImage
            }
            last = match.range.last + 1
        }
        val tail = content.substring(last).trim()
        if (tail.isNotEmpty()) out += GigaChatContentPart.Text(tail)
        if (out.isEmpty() && content.isNotBlank()) out += GigaChatContentPart.Text(content.trim())
        return out
    }

    fun stripForApi(content: String): String {
        val text = imgTag.replace(content, " ").replace(Regex("\\s+"), " ").trim()
        return text.ifBlank { "[изображение]" }
    }

    fun stripForSpeech(content: String): String =
        imgTag.replace(content, " ").replace(Regex("\\s+"), " ").trim()

    fun referencedFiles(content: String, dir: File): List<File> =
        imgTag.findAll(content).mapNotNull { fileFor(dir, it.groupValues[1].trim()) }.toList()

    fun saveJpeg(dir: File, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        dir.mkdirs()
        val name = "photo_${System.currentTimeMillis()}.jpg"
        val file = File(dir, name)
        runCatching { file.writeBytes(bytes) }.getOrElse { return null }
        return """<img src="$FILE_PREFIX$name" />"""
    }

    fun identifyUserMessage(jpegTag: String?): String {
        val label = "Определи, что на фото"
        return if (jpegTag.isNullOrBlank()) label else "$label\n$jpegTag"
    }

    fun saveToGallery(context: Context, file: File): Result<Unit> {
        if (!file.isFile || file.length() < 1) {
            return Result.failure(IllegalStateException("Файл изображения не найден"))
        }
        val name = "GigaChat_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Bible",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return Result.failure(IllegalStateException("Не удалось сохранить в галерею"))
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: error("Не удалось записать файл")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Unit
        }.onFailure {
            runCatching { resolver.delete(uri, null, null) }
        }
    }
}

sealed class GigaChatContentPart {
    data class Text(val value: String) : GigaChatContentPart()
    data class Image(val file: File) : GigaChatContentPart()
    data object MissingImage : GigaChatContentPart()
}
