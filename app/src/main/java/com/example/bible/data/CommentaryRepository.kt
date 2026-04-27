package com.example.bible.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class VerseCommentary(
    val bookId: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val audioUrl: String?,
)

class CommentaryRepository {

    suspend fun loadCommentary(
        translation: TranslationId,
        bookId: String,
        chapter: Int,
        verse: Int,
    ): VerseCommentary? = withContext(Dispatchers.IO) {
        val url = ServerConfig.commentaryUrl(translation, bookId, chapter, verse)
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                VerseCommentary(
                    bookId = bookId,
                    chapter = chapter,
                    verse = verse,
                    text = json.optString("text", ""),
                    audioUrl = if (json.has("audioUrl")) json.getString("audioUrl") else null,
                )
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
