package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Клиент [GigaChat API](https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/gigachat-api).
 * Пользователь сохраняет Authorization Key из Studio; приложение само меняет его на access token.
 */
object GigaChatClient {

    /** Freemium для физлиц: https://developers.sber.ru/docs/ru/gigachat/models/gigachat-3-ultra */
    const val DEFAULT_MODEL = "GigaChat-3-Ultra"
    const val SCOPE_PERS = "GIGACHAT_API_PERS"
    const val SCOPE_B2B = "GIGACHAT_API_B2B"
    const val SCOPE_CORP = "GIGACHAT_API_CORP"

    private val chatUrls = listOf(
        "https://api.giga.chat/v1/chat/completions",
        "https://gigachat.devices.sberbank.ru/api/v1/chat/completions",
    )
    private val fileUrls = listOf(
        "https://api.giga.chat/v1/files",
        "https://gigachat.devices.sberbank.ru/api/v1/files",
    )
    private val oauthUrls = listOf(
        "https://api.giga.chat/api/v2/oauth",
        "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
    )
    private const val USER_AGENT = "BibleApp/1.0 (Android; GigaChat)"
    private const val NETWORK_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 700L
    private const val TOKEN_SKEW_MS = 60_000L

    private val sberHosts = setOf(
        "api.giga.chat",
        "ngw.devices.sberbank.ru",
        "gigachat.devices.sberbank.ru",
    )

    @Volatile
    private var cachedAuthKey: String = ""

    @Volatile
    private var cachedScope: String = ""

    @Volatile
    private var cachedToken: String = ""

    @Volatile
    private var tokenExpiresAtMs: Long = 0

    suspend fun chat(
        authKey: String,
        messages: List<DeepSeekMessage>,
        scope: String = SCOPE_PERS,
        model: String = DEFAULT_MODEL,
        timeoutMs: Int = 90_000,
        attachmentIds: List<String> = emptyList(),
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = normalizeAuthKey(authKey)
        if (key.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Нет ключа авторизации GigaChat"))
        }
        if (messages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Пустой запрос"))
        }
        val body = JSONObject().apply {
            put("model", model)
            put("stream", false)
            put("function_call", "auto")
            put(
                "messages",
                JSONArray().apply {
                    messages.forEachIndexed { index, m ->
                        val item = JSONObject()
                            .put("role", m.role)
                            .put("content", m.content)
                        if (attachmentIds.isNotEmpty() && index == messages.lastIndex && m.role == "user") {
                            item.put("attachments", JSONArray(attachmentIds))
                        }
                        put(item)
                    }
                },
            )
        }.toString()
        try {
            val text = completeChat(key, scope, body, timeoutMs)
            if (text.isEmpty()) {
                Result.failure(IllegalStateException("Пустой ответ GigaChat"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(networkErrorMessage(e), e))
        }
    }

    suspend fun testKey(
        authKey: String,
        scope: String = SCOPE_PERS,
    ): Result<String> = chat(
        authKey = authKey,
        messages = listOf(DeepSeekMessage("user", "Ответь одним словом: ок")),
        scope = scope,
        timeoutMs = 40_000,
    ).map { "Ключ работает. $DEFAULT_MODEL отвечает." }

    suspend fun uploadFile(
        authKey: String,
        bytes: ByteArray,
        fileName: String,
        mime: String,
        scope: String = SCOPE_PERS,
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = normalizeAuthKey(authKey)
        if (key.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Нет ключа авторизации GigaChat"))
        }
        if (bytes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Пустая запись"))
        }
        try {
            var token = accessToken(key, scope)
            var lastError: Exception? = null
            for (url in fileUrls) {
                var (code, raw) = postMultipart(url, token, bytes, fileName, mime, 60_000)
                if (code == 401) {
                    invalidateToken()
                    token = accessToken(key, scope)
                    val retry = postMultipart(url, token, bytes, fileName, mime, 60_000)
                    code = retry.first
                    raw = retry.second
                }
                if (code in 200..299) {
                    val id = JSONObject(raw).optString("id").trim()
                    if (id.isNotEmpty()) return@withContext Result.success(id)
                    lastError = IllegalStateException("GigaChat не вернул id файла")
                    continue
                }
                lastError = IllegalStateException(errorMessage(code, raw))
            }
            Result.failure(lastError ?: IOException("Не удалось загрузить запись в GigaChat"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(networkErrorMessage(e), e))
        }
    }

    suspend fun downloadFile(
        authKey: String,
        fileId: String,
        scope: String = SCOPE_PERS,
    ): Result<ByteArray> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = normalizeAuthKey(authKey)
        val id = fileId.trim()
        if (key.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Нет ключа авторизации GigaChat"))
        }
        if (id.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Нет идентификатора файла"))
        }
        try {
            var token = accessToken(key, scope)
            var lastError: Exception? = null
            val accepts = listOf("application/jpg", "application/jpeg", "image/jpeg", "image/png", "*/*")
            val methods = listOf("GET", "POST")
            for (base in fileUrls) {
                val url = "$base/$id/content"
                for (method in methods) {
                    for (accept in accepts) {
                        var (code, bytes) = getBinary(url, token, 60_000, accept, method)
                        if (code == 401) {
                            invalidateToken()
                            token = accessToken(key, scope)
                            val retry = getBinary(url, token, 60_000, accept, method)
                            code = retry.first
                            bytes = retry.second
                        }
                        if (code in 200..299 && looksLikeImage(bytes)) {
                            return@withContext Result.success(bytes)
                        }
                        if (bytes.isNotEmpty() && code !in 200..299) {
                            lastError = IllegalStateException(
                                errorMessage(code, bytes.toString(StandardCharsets.UTF_8)),
                            )
                        }
                    }
                }
            }
            Result.failure(lastError ?: IOException("Не удалось скачать изображение GigaChat"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(networkErrorMessage(e), e))
        }
    }

    suspend fun deleteFile(
        authKey: String,
        fileId: String,
        scope: String = SCOPE_PERS,
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = normalizeAuthKey(authKey)
        val id = fileId.trim()
        if (key.isEmpty() || id.isEmpty()) return@withContext
        try {
            val token = accessToken(key, scope)
            for (base in fileUrls) {
                val (code, _) = postJson("$base/$id/delete", token, "{}", 20_000)
                if (code in 200..299 || code == 404) return@withContext
            }
        } catch (_: Exception) {
        }
    }

    fun normalizeAuthKey(raw: String): String =
        raw.trim().removePrefix("Basic ").trim()

    @Synchronized
    fun clearTokenCache() {
        cachedAuthKey = ""
        cachedScope = ""
        cachedToken = ""
        tokenExpiresAtMs = 0
    }

    @Synchronized
    private fun invalidateToken() {
        cachedToken = ""
        tokenExpiresAtMs = 0
    }

    private fun completeChat(
        authKey: String,
        scope: String,
        body: String,
        timeoutMs: Int,
    ): String {
        var token = accessToken(authKey, scope)
        var lastError: Exception? = null
        for (url in chatUrls) {
            var (code, raw) = postJson(url, token, body, timeoutMs)
            if (code == 401) {
                invalidateToken()
                token = accessToken(authKey, scope)
                val retry = postJson(url, token, body, timeoutMs)
                code = retry.first
                raw = retry.second
            }
            if (code in 200..299) {
                val message = JSONObject(raw)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                val content = GigaChatImages.normalize(message?.optString("content").orEmpty()).trim()
                return content
            }
            lastError = IllegalStateException(errorMessage(code, raw))
        }
        throw lastError ?: IOException("Не удалось обратиться к GigaChat")
    }

    @Synchronized
    private fun accessToken(authKey: String, scope: String): String {
        val now = System.currentTimeMillis()
        if (
            authKey == cachedAuthKey &&
            scope == cachedScope &&
            cachedToken.isNotEmpty() &&
            now < tokenExpiresAtMs - TOKEN_SKEW_MS
        ) {
            return cachedToken
        }
        val form = "scope=${if (scope.isBlank()) SCOPE_PERS else scope}"
        var lastError: Exception? = null
        for (oauthUrl in oauthUrls) {
            try {
                val (code, raw) = postForm(oauthUrl, authKey, form, 30_000)
                if (code !in 200..299) {
                    lastError = IllegalStateException(errorMessage(code, raw))
                    continue
                }
                val json = JSONObject(raw)
                val token = json.optString("access_token").trim()
                if (token.isEmpty()) {
                    lastError = IllegalStateException("GigaChat не вернул токен доступа")
                    continue
                }
                cachedAuthKey = authKey
                cachedScope = scope
                cachedToken = token
                tokenExpiresAtMs = json.optLong("expires_at", 0L).takeIf { it > 0L }
                    ?: (now + 25 * 60_000L)
                return token
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Не удалось получить токен GigaChat")
    }

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val jpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val png = bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()
        val gif = bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte()
        val webp = bytes.size > 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte()
        val jsonOrHtml = bytes[0] == '{'.code.toByte() || bytes[0] == '<'.code.toByte()
        return jpeg || png || gif || webp || (!jsonOrHtml && bytes.size > 256)
    }

    private fun getBinary(
        url: String,
        accessToken: String,
        timeoutMs: Int,
        accept: String = "application/jpg",
        method: String = "GET",
    ): Pair<Int, ByteArray> = withRetry {
        openConn(url).apply {
            requestMethod = method
            instanceFollowRedirects = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Connection", "close")
            connectTimeout = 20_000
            readTimeout = timeoutMs
            if (method == "POST") {
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
        }.useConn { conn ->
            if (method == "POST") {
                conn.outputStream.use { it.write("{}".toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            code to bytes
        }
    }

    private fun postJson(
        url: String,
        accessToken: String,
        body: String,
        timeoutMs: Int,
    ): Pair<Int, String> = withRetry {
        openConn(url).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Connection", "close")
            connectTimeout = 20_000
            readTimeout = timeoutMs
            doOutput = true
        }.useConn { conn ->
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            readResponse(conn)
        }
    }

    private fun postMultipart(
        url: String,
        accessToken: String,
        fileBytes: ByteArray,
        fileName: String,
        mime: String,
        timeoutMs: Int,
    ): Pair<Int, String> {
        val boundary = "----BibleGiga${UUID.randomUUID().toString().replace("-", "")}"
        val crlf = "\r\n"
        val head = buildString {
            append("--").append(boundary).append(crlf)
            append("Content-Disposition: form-data; name=\"purpose\"").append(crlf).append(crlf)
            append("general").append(crlf)
            append("--").append(boundary).append(crlf)
            append("Content-Disposition: form-data; name=\"file\"; filename=\"")
            append(fileName)
            append("\"").append(crlf)
            append("Content-Type: ").append(mime).append(crlf).append(crlf)
        }.toByteArray(StandardCharsets.UTF_8)
        val tail = "$crlf--$boundary--$crlf".toByteArray(StandardCharsets.UTF_8)
        return withRetry {
            openConn(url).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Connection", "close")
                connectTimeout = 20_000
                readTimeout = timeoutMs
                doOutput = true
                setFixedLengthStreamingMode(head.size + fileBytes.size + tail.size)
            }.useConn { conn ->
                conn.outputStream.use { out ->
                    out.write(head)
                    out.write(fileBytes)
                    out.write(tail)
                }
                readResponse(conn)
            }
        }
    }

    private fun postForm(
        url: String,
        authKey: String,
        form: String,
        timeoutMs: Int,
    ): Pair<Int, String> {
        var current = url
        for (i in 0 until 4) {
            val (code, raw, location) = withRetry {
                openConn(current).apply {
                    requestMethod = "POST"
                    instanceFollowRedirects = false
                    setRequestProperty("Authorization", "Basic $authKey")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("RqUID", UUID.randomUUID().toString())
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Connection", "close")
                    connectTimeout = 20_000
                    readTimeout = timeoutMs
                    doOutput = true
                }.useConn { conn ->
                    conn.outputStream.use { it.write(form.toByteArray(StandardCharsets.UTF_8)) }
                    val loc = conn.getHeaderField("Location").orEmpty()
                    val (c, body) = readResponse(conn)
                    Triple(c, body, loc)
                }
            }
            if (code in 300..399 && location.isNotBlank()) {
                current = if (location.startsWith("http")) location else URL(URL(current), location).toString()
                continue
            }
            return code to raw
        }
        return 403 to """{"message":"oauth redirect loop"}"""
    }

    private fun <T> withRetry(block: () -> T): T {
        var lastError: IOException? = null
        for (attempt in 0 until NETWORK_ATTEMPTS) {
            try {
                return block()
            } catch (e: IOException) {
                if (!isRetriableNetworkError(e)) throw e
                lastError = e
                if (attempt < NETWORK_ATTEMPTS - 1) {
                    Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        throw lastError ?: IOException("Сеть недоступна")
    }

    private fun readResponse(conn: HttpURLConnection): Pair<Int, String> {
        val code = conn.responseCode
        val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
        return code to raw
    }

    private inline fun <T> HttpURLConnection.useConn(block: (HttpURLConnection) -> T): T {
        try {
            return block(this)
        } finally {
            disconnect()
        }
    }

    private fun openConn(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        if (conn is HttpsURLConnection && URL(url).host in sberHosts) {
            conn.sslSocketFactory = sberSslFactory
            conn.hostnameVerifier = HostnameVerifier { hostname, _ -> hostname in sberHosts }
        }
        return conn
    }

    /**
     * Сертификаты НУЦ Минцифры на части устройств Android не в системном хранилище,
     * из‑за этого рукопожатие к api.giga.chat и oauth Сбера падает. Доверяем только этим хостам.
     */
    private val sberSslFactory: SSLSocketFactory by lazy {
        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trust), SecureRandom())
        }.socketFactory
    }

    private fun isRetriableNetworkError(e: IOException): Boolean {
        if (e is SocketTimeoutException) return false
        if (e is SocketException || e is SSLException || e is EOFException || e is ProtocolException) {
            return true
        }
        val msg = e.message.orEmpty().lowercase()
        return "connection reset" in msg ||
            "unexpected end of stream" in msg ||
            "connection abort" in msg
    }

    private fun networkErrorMessage(e: Exception): String {
        val raw = e.message.orEmpty()
        val lower = raw.lowercase()
        return when {
            e is UnknownHostException ->
                "Нет доступа к GigaChat. Проверьте интернет и повторите."
            e is SocketTimeoutException ->
                "GigaChat не ответил вовремя. Повторите вопрос."
            e is SSLException || e is SocketException || e is EOFException ||
                "connection reset" in lower || "unexpected end of stream" in lower ->
                "Связь с GigaChat оборвалась. Проверьте интернет и нажмите «Повторить»."
            else -> raw.ifBlank { "Не удалось обратиться к GigaChat" }
        }
    }

    private fun errorMessage(code: Int, body: String): String {
        val apiMsg = runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message").orEmpty()
                .ifBlank { json.optString("message") }
        }.getOrDefault("").trim()
        val lower = apiMsg.lowercase()
        return when {
            "scope is empty" in lower || "scope data format" in lower ->
                "Не передана версия API. Оставьте scope «Физлицо» и проверьте ключ ещё раз."
            "scope from db" in lower ->
                "Ключ не подходит к выбранному scope. Для Ultra нужен проект физлица (GIGACHAT_API_PERS)."
            apiMsg.isNotBlank() -> apiMsg
            code == 401 -> "Ключ отклонён. Проверьте Authorization Key в Studio."
            code == 403 ->
                "GigaChat отказал в доступе. Для Ultra оставьте scope «Физлицо» и ключ из Freemium-проекта."
            code == 402 || code == 429 ->
                "Лимит GigaChat исчерпан или слишком много запросов. Подождите и повторите."
            else -> "Ошибка GigaChat (HTTP $code)"
        }
    }
}
