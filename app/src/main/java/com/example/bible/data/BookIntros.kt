package com.example.bible.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Краткая справка о книге: суть, авторство, повод, дата и откуда взяты сведения.
 * Тексты лежат в assets, не в вёрстке. Файл читается один раз вне главного потока.
 */
data class BookIntro(
    val genre: String,
    val author: String,
    val occasion: String,
    val date: String,
    val source: String,
) {
    fun displayLines(): List<String> = listOf(
        "Суть: $genre",
        "Автор: $author",
        "Повод: $occasion",
        "Время: $date",
        "Источник: $source",
    )
}

object BookIntros {
    const val ASSET_PATH = "bible/book_intros.json"

    private val mutex = Mutex()

    @Volatile
    private var cached: Map<String, BookIntro>? = null

    fun peek(bookId: String): BookIntro? = cached?.get(bookId)

    suspend fun load(context: Context): Map<String, BookIntro> {
        cached?.let { return it }
        val app = context.applicationContext
        return mutex.withLock {
            cached ?: withContext(Dispatchers.IO) {
                app.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { reader ->
                    parse(reader.readText())
                }
            }.also { cached = it }
        }
    }

    fun parse(json: String): Map<String, BookIntro> {
        val root = JSONObject(json)
        val out = LinkedHashMap<String, BookIntro>(root.length())
        val keys = root.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val item = root.getJSONObject(id)
            out[id] = BookIntro(
                genre = item.getString("genre").trim(),
                author = item.getString("author").trim(),
                occasion = item.getString("occasion").trim(),
                date = item.getString("date").trim(),
                source = item.getString("source").trim(),
            )
        }
        return out
    }
}
