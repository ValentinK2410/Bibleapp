package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class DeepSeekMessage(
    val role: String,
    val content: String,
)

/**
 * Клиент [DeepSeek Chat Completions](https://api.deepseek.com) (совместим с OpenAI).
 * Ключ задаётся пользователем в настройках и не хранится в коде.
 */
object DeepSeekClient {

    const val CHAT_URL = "https://api.deepseek.com/chat/completions"
    const val DEFAULT_MODEL = "deepseek-chat"
    private const val USER_AGENT = "BibleApp/1.0 (Android; DeepSeek)"

    suspend fun chat(
        apiKey: String,
        messages: List<DeepSeekMessage>,
        model: String = DEFAULT_MODEL,
        timeoutMs: Int = 60_000,
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Нет API-ключа DeepSeek"))
        }
        if (messages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Пустой запрос"))
        }
        try {
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
            val conn = URL(CHAT_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 20_000
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException(errorMessage(code, raw)))
            }
            val text = JSONObject(raw)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()
            if (text.isEmpty()) {
                Result.failure(IllegalStateException("Пустой ответ DeepSeek"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testKey(apiKey: String): Result<String> =
        chat(
            apiKey = apiKey,
            messages = listOf(DeepSeekMessage("user", "Ответь одним словом: ок")),
            timeoutMs = 25_000,
        )

    private fun errorMessage(code: Int, body: String): String {
        val apiMsg = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return when (code) {
            401 -> "Ключ отклонён. Проверьте API-ключ DeepSeek."
            402 -> "На счёте DeepSeek недостаточно средств."
            429 -> "Слишком много запросов. Подождите и повторите."
            else -> apiMsg.ifBlank { "Ошибка DeepSeek (HTTP $code)" }
        }
    }
}
