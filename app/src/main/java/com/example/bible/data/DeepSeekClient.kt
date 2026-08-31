package com.example.bible.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
    const val RESPONSES_URL = "https://api.deepseek.com/responses"
    /** Флагман DeepSeek V4 Pro (самая сильная текстовая модель в API). */
    const val DEFAULT_MODEL = "deepseek-v4-pro"
    /** Единственная модель API, которая принимает изображения. */
    const val VISION_MODEL = "deepseek-v4-flash-vision-exp"
    private const val USER_AGENT = "BibleApp/1.0 (Android; DeepSeek)"
    private const val VISION_MAX_SIDE = 1600
    private const val VISION_JPEG_QUALITY = 82

    suspend fun chat(
        apiKey: String,
        messages: List<DeepSeekMessage>,
        model: String = DEFAULT_MODEL,
        timeoutMs: Int = 90_000,
        thinking: Boolean = true,
        reasoningEffort: String? = null,
        webSearch: Boolean = false,
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Нет API-ключа DeepSeek"))
        }
        if (messages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Пустой запрос"))
        }
        if (webSearch) {
            return@withContext respondWithWebSearch(
                apiKey = key,
                messages = messages,
                model = model,
                timeoutMs = timeoutMs,
                thinking = thinking,
                reasoningEffort = reasoningEffort,
            )
        }
        try {
            val body = JSONObject().apply {
                put("model", model)
                put("stream", false)
                put(
                    "thinking",
                    JSONObject().put("type", if (thinking) "enabled" else "disabled"),
                )
                if (thinking) {
                    put("reasoning_effort", reasoningEffort?.ifBlank { null } ?: "high")
                }
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
            val (code, raw) = postJson(CHAT_URL, key, body, timeoutMs)
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException(errorMessage(code, raw)))
            }
            val message = JSONObject(raw)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
            val text = message?.optString("content").orEmpty().trim()
                .ifBlank { message?.optString("reasoning_content").orEmpty().trim() }
            if (text.isEmpty()) {
                Result.failure(IllegalStateException("Пустой ответ DeepSeek"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun respondWithWebSearch(
        apiKey: String,
        messages: List<DeepSeekMessage>,
        model: String,
        timeoutMs: Int,
        thinking: Boolean,
        reasoningEffort: String?,
    ): Result<String> {
        val instructions = buildString {
            val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }.trim()
            if (system.isNotEmpty()) {
                append(system)
                append("\n\n")
            }
            append(
                "Используй поиск в интернете, чтобы опереться на актуальные источники. " +
                    "Не выдумывай новости, даты и цитаты. Если источники расходятся — скажи об этом.",
            )
        }
        val input = JSONArray().apply {
            messages.filter { it.role != "system" }.forEach { m ->
                put(
                    JSONObject()
                        .put("type", "message")
                        .put("role", m.role)
                        .put("content", m.content),
                )
            }
        }
        if (input.length() == 0) {
            return Result.failure(IllegalArgumentException("Пустой запрос"))
        }
        return try {
            val body = JSONObject().apply {
                put("model", model)
                put("stream", false)
                put("instructions", instructions)
                put("input", input)
                put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
                put("tool_choice", JSONObject().put("type", "web_search"))
                put(
                    "reasoning",
                    JSONObject().put(
                        "effort",
                        if (thinking) reasoningEffort?.ifBlank { null } ?: "max" else "none",
                    ),
                )
            }.toString()
            val (code, raw) = postJson(RESPONSES_URL, apiKey, body, timeoutMs)
            if (code !in 200..299) {
                return Result.failure(IllegalStateException(errorMessage(code, raw)))
            }
            val json = JSONObject(raw)
            if (json.optString("status") == "failed") {
                val msg = json.optJSONObject("error")?.optString("message").orEmpty()
                return Result.failure(IllegalStateException(msg.ifBlank { "Поиск в интернете не удался" }))
            }
            val text = extractResponseText(json)
            if (text.isEmpty()) {
                Result.failure(IllegalStateException("Пустой ответ DeepSeek"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractResponseText(json: JSONObject): String {
        val shortcut = json.optString("output_text").trim()
        if (shortcut.isNotEmpty() && shortcut != "null") return shortcut
        val output = json.optJSONArray("output") ?: return ""
        val parts = mutableListOf<String>()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type") != "message") continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                val type = part.optString("type")
                if (type == "output_text" || type == "text") {
                    val text = part.optString("text").trim()
                    if (text.isNotEmpty()) parts += text
                }
            }
        }
        return parts.joinToString("\n").trim()
    }

    private fun postJson(
        url: String,
        apiKey: String,
        body: String,
        timeoutMs: Int,
    ): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
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
        return code to raw
    }

    suspend fun testKey(apiKey: String): Result<String> =
        chat(
            apiKey = apiKey,
            messages = listOf(DeepSeekMessage("user", "Ответь одним словом: ок")),
            timeoutMs = 40_000,
            thinking = false,
        )

    /**
     * Расшифровка кадра камеры через vision-модель.
     * [jpegBytes] сжимаются перед отправкой, чтобы уложиться в лимит тела запроса.
     */
    suspend fun chatVision(
        apiKey: String,
        jpegBytes: ByteArray,
        prompt: String,
        timeoutMs: Int = 90_000,
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Нет API-ключа DeepSeek"))
        }
        if (jpegBytes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Пустой кадр"))
        }
        try {
            val packed = jpegForVision(jpegBytes)
            val b64 = Base64.encodeToString(packed, Base64.NO_WRAP)
            val content = JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", prompt))
                put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject().put("url", "data:image/jpeg;base64,$b64"),
                        ),
                )
            }
            val body = JSONObject().apply {
                put("model", VISION_MODEL)
                put("stream", false)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", content),
                    ),
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
            val message = JSONObject(raw)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
            val text = message?.optString("content").orEmpty().trim()
                .ifBlank { message?.optString("reasoning_content").orEmpty().trim() }
            if (text.isEmpty()) {
                Result.failure(IllegalStateException("Пустой ответ DeepSeek"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun jpegForVision(source: ByteArray): ByteArray {
        if (source.isEmpty()) return source
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val sample = maxOf(1, longest / VISION_MAX_SIDE)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, opts) ?: return source
        val side = maxOf(decoded.width, decoded.height)
        val bmp = if (side > VISION_MAX_SIDE) {
            val scale = VISION_MAX_SIDE.toFloat() / side
            val w = (decoded.width * scale).toInt().coerceAtLeast(1)
            val h = (decoded.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, w, h, true).also {
                if (it != decoded) decoded.recycle()
            }
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, VISION_JPEG_QUALITY, out)
        bmp.recycle()
        return out.toByteArray()
    }

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
