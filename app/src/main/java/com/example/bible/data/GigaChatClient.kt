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
            put(
                "messages",
                JSONArray().apply {
                    messages.forEach { m ->
                        put(
                            JSONObject()
                                .put("role", m.role)
                                .put("content", m.content),
                        )
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
                return message?.optString("content").orEmpty().trim()
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

    private fun postForm(
        url: String,
        authKey: String,
        form: String,
        timeoutMs: Int,
    ): Pair<Int, String> {
        var current = url
        repeat(4) {
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
