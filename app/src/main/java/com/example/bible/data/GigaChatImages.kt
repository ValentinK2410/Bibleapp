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

    private val srcInImg = Regex(
        """<img\b[^>]*?\bsrc\s*=\s*["']?\s*([^"'\s>]+)["']?""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val anyImg = Regex(
        """<img\b[^>]*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val uuidToken = Regex(
        """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""",
    )
    private val uuidExact = Regex(
        """^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""",
    )

    fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "gigachat_images").apply { mkdirs() }

    fun normalize(raw: String): String =
        raw.replace("\\\"", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")

    fun hasRemoteIds(content: String): Boolean = remoteIds(content).isNotEmpty()

    fun remoteIds(content: String): List<String> {
        val n = normalize(content)
        val fromSrc = srcInImg.findAll(n).map { it.groupValues[1].trim() }.filter { uuidExact.matches(it) }
        val fromTag = anyImg.findAll(n).flatMap { tag -> uuidToken.findAll(tag.value).map { it.value } }
        return (fromSrc + fromTag).distinct().toList()
    }

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
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    suspend fun materialize(
        content: String,
        download: suspend (fileId: String) -> ByteArray?,
        dir: File,
    ): String {
        var result = normalize(content)
        for (id in remoteIds(result)) {
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
        val n = normalize(content)
        val out = mutableListOf<GigaChatContentPart>()
        var last = 0
        val tags = anyImg.findAll(n).toList()
        for (match in tags) {
            val before = n.substring(last, match.range.first).trim()
            if (before.isNotEmpty()) out += GigaChatContentPart.Text(before)
            val src = srcInImg.find(match.value)?.groupValues?.get(1)?.trim().orEmpty()
            val local = fileFor(dir, src)
            val remote = src.takeIf { uuidExact.matches(it) }
                ?: uuidToken.find(match.value)?.value
            when {
                local != null -> out += GigaChatContentPart.Image(local)
                remote != null -> {
                    val cached = File(dir, "$remote.jpg").takeIf { it.isFile && it.length() > 0L }
                    out += if (cached != null) {
                        GigaChatContentPart.Image(cached)
                    } else {
                        GigaChatContentPart.MissingImage
                    }
                }
            }
            last = match.range.last + 1
        }
        val tail = n.substring(last).trim()
        if (tail.isNotEmpty() && !tail.contains("<img", ignoreCase = true)) {
            out += GigaChatContentPart.Text(tail)
        }
        if (out.isEmpty() && n.isNotBlank() && !n.contains("<img", ignoreCase = true)) {
            out += GigaChatContentPart.Text(n.trim())
        }
        if (out.none { it is GigaChatContentPart.Image || it is GigaChatContentPart.MissingImage }) {
            for (id in remoteIds(n)) {
                val cached = File(dir, "$id.jpg").takeIf { it.isFile && it.length() > 0L }
                out += if (cached != null) GigaChatContentPart.Image(cached) else GigaChatContentPart.MissingImage
            }
        }
        return out
    }

    fun stripForApi(content: String): String {
        val text = anyImg.replace(normalize(content), " ").replace(Regex("\\s+"), " ").trim()
        return text.ifBlank { "[изображение]" }
    }

    fun stripForSpeech(content: String): String =
        anyImg.replace(normalize(content), " ")

    fun referencedFiles(content: String, dir: File): List<File> =
        parts(content, dir).mapNotNull { (it as? GigaChatContentPart.Image)?.file }

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
