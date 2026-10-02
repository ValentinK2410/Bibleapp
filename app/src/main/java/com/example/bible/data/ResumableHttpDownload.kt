package com.example.bible.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/**
 * Скачивание файла по прямой ссылке с докачкой: данные пишутся в `<ключ ссылки>.part`
 * рядом с целевым каталогом, поэтому после обрыва или перезапуска приложения загрузка
 * продолжается с того байта, на котором остановилась, а не начинается заново.
 */
object ResumableHttpDownload {

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 120_000

    /** Имя недокачанного файла стабильно для ссылки — так его находит следующая попытка. */
    fun partFile(dir: File, url: String): File = File(dir, "dl_${urlKey(url)}.part")

    fun urlKey(url: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.trim().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Есть недокачанный остаток по этой ссылке. */
    fun hasPartialDownload(dir: File, url: String): Boolean =
        partFile(dir, url).let { it.isFile && it.length() > 0 }

    fun dropPartialDownload(dir: File, url: String) {
        runCatching { partFile(dir, url).takeIf { it.isFile }?.delete() }
    }

    /**
     * Качает [url] в [dir] и возвращает имя сохранённого файла.
     * [extensionFor] выбирает расширение по MIME-типу ответа, [fallbackExtension] — если MIME не подошёл.
     */
    suspend fun download(
        dir: File,
        url: String,
        userAgent: String,
        fallbackExtension: String,
        extensionFor: (mime: String) -> String?,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            val part = partFile(dir, url)
            val alreadyHave = if (part.isFile) part.length() else 0L
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", userAgent)
                if (alreadyHave > 0) setRequestProperty("Range", "bytes=$alreadyHave-")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                connect()
            }
            val code = conn.responseCode
            if (code == HTTP_RANGE_NOT_SATISFIABLE && alreadyHave > 0) {
                // Сервер считает, что докачивать нечего: отдаём то, что уже лежит на диске.
                conn.disconnect()
                return@withContext finish(dir, part, conn.contentType, url, fallbackExtension, extensionFor)
            }
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                conn.disconnect()
                return@withContext Result.failure(IllegalStateException("HTTP $code"))
            }
            // Ответ 200 на запрос с Range означает, что сервер докачку не поддержал.
            val append = code == HttpURLConnection.HTTP_PARTIAL && alreadyHave > 0
            val startAt = if (append) alreadyHave else 0L
            val remaining = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            val totalBytes = if (remaining > 0) startAt + remaining else -1L
            var written = startAt
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress?.invoke(written, totalBytes)
                    }
                    output.flush()
                }
            }
            val contentType = conn.contentType
            conn.disconnect()
            finish(dir, part, contentType, url, fallbackExtension, extensionFor)
        } catch (e: Exception) {
            // .part не удаляем: он пригодится для докачки при следующей попытке.
            Result.failure(e)
        }
    }

    private fun finish(
        dir: File,
        part: File,
        contentType: String?,
        url: String,
        fallbackExtension: String,
        extensionFor: (mime: String) -> String?,
    ): Result<String> {
        if (!part.isFile || part.length() == 0L) {
            runCatching { part.delete() }
            return Result.failure(IllegalStateException("Пустой ответ"))
        }
        val mime = contentType?.substringBefore(';')?.trim().orEmpty()
        val ext = extensionFor(mime) ?: extensionFromUrl(url) ?: fallbackExtension
        val target = File(dir, "${UUID.randomUUID()}.$ext")
        if (!part.renameTo(target)) {
            part.inputStream().use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            runCatching { part.delete() }
        }
        return Result.success(target.name)
    }

    private fun extensionFromUrl(url: String): String? {
        val path = url.substringBefore('?').lowercase()
        val ext = path.substringAfterLast('.', "")
        return ext.takeIf { it.isNotEmpty() && it.length <= 4 && it.all { c -> c.isLetterOrDigit() } }
    }

    private const val HTTP_RANGE_NOT_SATISFIABLE = 416
}
