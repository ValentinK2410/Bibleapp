package com.example.bible.data

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
import org.json.JSONObject

/**
 * [SaluteSpeech](https://developers.sber.ru/docs/ru/salutespeech/) — нейросетевой синтез речи Сбера.
 * Голоса 24 кГц, заметно естественнее системного TTS на Android.
 */
object SaluteSpeechClient {

    const val SCOPE_PERS = "SALUTE_SPEECH_PERS"
    const val SCOPE_B2B = "SALUTE_SPEECH_B2B"
    const val SCOPE_CORP = "SALUTE_SPEECH_CORP"

    const val DEFAULT_VOICE = "Nec_24000"

    private const val SYNTH_URL = "https://smartspeech.sber.ru/rest/v1/text:synthesize"
    private const val USER_AGENT = "BibleApp/1.0 (Android; SaluteSpeech)"
    private const val NETWORK_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 700L
    private const val TOKEN_SKEW_MS = 60_000L

    private val oauthUrls = listOf(
        "https://api.giga.chat/api/v2/oauth",
        "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
    )

    private val sberHosts = setOf(
        "api.giga.chat",
        "ngw.devices.sberbank.ru",
        "smartspeech.sber.ru",
    )

    @Volatile
    private var cachedAuthKey: String = ""

    @Volatile
    private var cachedScope: String = ""

    @Volatile
    private var cachedToken: String = ""

    @Volatile
    private var tokenExpiresAtMs: Long = 0

    val neuralVoices: List<AiChatTtsVoiceOption> = listOf(
        AiChatTtsVoiceOption("Nec_24000", "Наталья", network = true),
        AiChatTtsVoiceOption("May_24000", "Марфа", network = true),
        AiChatTtsVoiceOption("Ost_24000", "Александра", network = true),
        AiChatTtsVoiceOption("Bys_24000", "Борис", network = true),
        AiChatTtsVoiceOption("Tur_24000", "Тарас", network = true),
        AiChatTtsVoiceOption("Pon_24000", "Сергей", network = true),
    )

    suspend fun synthesize(
        authKey: String,
        text: String,
        voice: String = DEFAULT_VOICE,
        intonation: AiChatTtsIntonation = AiChatTtsIntonation.NORMAL,
        scope: String = SCOPE_PERS,
        timeoutMs: Int = 60_000,
    ): Result<ByteArray> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = GigaChatClient.normalizeAuthKey(authKey)
        val trimmed = text.trim()
        if (key.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Нет ключа SaluteSpeech"))
        }
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Пустой текст"))
        }
        try {
            val (body, contentType) = buildSynthBody(trimmed, intonation)
            val token = accessToken(key, scope)
            val voiceId = voice.trim().ifBlank { DEFAULT_VOICE }
            val url = "$SYNTH_URL?format=wav16&voice=$voiceId"
            var (code, bytes) = postSynth(url, token, body, contentType, timeoutMs)
            if (code == 401) {
                invalidateToken()
                val retryToken = accessToken(key, scope)
                val retry = postSynth(url, retryToken, body, contentType, timeoutMs)
                code = retry.first
                bytes = retry.second
            }
            if (code in 200..299 && bytes.isNotEmpty() && looksLikeWav(bytes)) {
                Result.success(bytes)
            } else {
                val err = if (bytes.isNotEmpty() && bytes[0] == '{'.code.toByte()) {
                    errorMessage(code, bytes.toString(StandardCharsets.UTF_8))
                } else {
                    errorMessage(code, "HTTP $code")
                }
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(networkErrorMessage(e), e))
        }
    }

    suspend fun testKey(authKey: String, scope: String = SCOPE_PERS): Result<String> =
        synthesize(
            authKey = authKey,
            text = "Ключ работает. Это нейросетевой голос SaluteSpeech.",
            voice = DEFAULT_VOICE,
            scope = scope,
            timeoutMs = 45_000,
        ).map { "Ключ SaluteSpeech работает. Голос «Наталья» готов к озвучке." }

    @Synchronized
    fun clearTokenCache() {
        cachedAuthKey = ""
        cachedScope = ""
        cachedToken = ""
        tokenExpiresAtMs = 0
    }

    fun resolveAuthKey(saluteKey: String, gigaChatKey: String): String {
        val direct = GigaChatClient.normalizeAuthKey(saluteKey)
        if (direct.isNotEmpty()) return direct
        return GigaChatClient.normalizeAuthKey(gigaChatKey)
    }

    internal fun buildSynthBody(text: String, intonation: AiChatTtsIntonation): Pair<String, String> {
        val ssml = wrapSsml(text, intonation)
        return if (ssml != null) {
            ssml to "application/ssml"
        } else {
            text to "application/text"
        }
    }

    internal fun wrapSsml(text: String, intonation: AiChatTtsIntonation): String? {
        val escaped = escapeXml(text)
        val inner = when (intonation) {
            AiChatTtsIntonation.NORMAL -> return null
            AiChatTtsIntonation.CALM ->
                """<prosody rate="92%" pitch="-4%">$escaped</prosody>"""
            AiChatTtsIntonation.JOYFUL ->
                """<prosody rate="108%" pitch="+12%">$escaped</prosody>"""
            AiChatTtsIntonation.BOLD ->
                """<prosody rate="106%" pitch="-2%" volume="loud">$escaped</prosody>"""
            AiChatTtsIntonation.WHISPER ->
                """<prosody rate="88%" pitch="-10%" volume="x-soft">$escaped</prosody>"""
        }
        return """<speak>$inner</speak>"""
    }

    internal fun escapeXml(text: String): String = buildString(text.length) {
        text.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    @Synchronized
    private fun invalidateToken() {
        cachedToken = ""
        tokenExpiresAtMs = 0
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
        val form = "scope=${if (scope.isBlank()) SCOPE_PERS else scope.trim()}"
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
                    lastError = IllegalStateException("SaluteSpeech не вернул токен доступа")
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
        throw lastError ?: IOException("Не удалось получить токен SaluteSpeech")
    }

    private fun postSynth(
        url: String,
        accessToken: String,
        body: String,
        contentType: String,
        timeoutMs: Int,
    ): Pair<Int, ByteArray> = withRetry {
        openConn(url).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Accept", "audio/wav,*/*")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Connection", "close")
            connectTimeout = 20_000
            readTimeout = timeoutMs
            doOutput = true
        }.useConn { conn ->
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            code to bytes
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

    private fun looksLikeWav(bytes: ByteArray): Boolean =
        bytes.size > 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte()

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
                "Нет доступа к SaluteSpeech. Проверьте интернет."
            e is SocketTimeoutException ->
                "SaluteSpeech не ответил вовремя."
            e is SSLException || e is SocketException || e is EOFException ||
                "connection reset" in lower || "unexpected end of stream" in lower ->
                "Сбой соединения с SaluteSpeech. Повторите."
            raw.isNotBlank() -> raw
            else -> "Не удалось синтезировать речь SaluteSpeech."
        }
    }

    private fun errorMessage(code: Int, raw: String): String {
        val msg = runCatching {
            JSONObject(raw).optString("message").trim()
                .ifBlank { JSONObject(raw).optString("error").trim() }
        }.getOrDefault("").ifBlank { raw.trim() }
        return when (code) {
            401, 403 -> "Неверный ключ SaluteSpeech или нет доступа к синтезу речи."
            429 -> "Лимит SaluteSpeech исчерпан. Попробуйте позже."
            else -> msg.ifBlank { "SaluteSpeech: ошибка $code" }
        }
    }
}
