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
                    "thinking",
                    JSONObject().put("type", if (thinking) "enabled" else "disabled"),
                )
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
