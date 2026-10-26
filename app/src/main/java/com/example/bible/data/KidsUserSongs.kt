package com.example.bible.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Песни, которые родитель добавил сам, и его текст с фонограммами к песням приложения.
 * Хранится только на этом телефоне.
 */
object KidsUserSongsStore {
    data class Entry(
        val id: String,
        val title: String = "",
        /** null — оставить текст песни приложения. Для своей песни это сам текст. */
        val lyrics: String? = null,
        val audio: List<String> = emptyList(),
        val userMade: Boolean = false,
    )

    private val mutex = Mutex()
    private val flow = MutableStateFlow<List<Entry>>(emptyList())
    private var loaded = false

    fun entries(context: Context): StateFlow<List<Entry>> {
        ensureLoaded(context.applicationContext)
        return flow
    }

    fun peek(context: Context): List<Entry> {
        ensureLoaded(context.applicationContext)
        return flow.value
    }

    fun find(context: Context, id: String): Entry? = peek(context).firstOrNull { it.id == id }

    suspend fun save(context: Context, entry: Entry) {
        val app = context.applicationContext
        mutex.withLock {
            ensureLoaded(app)
            val clean = entry.copy(
                title = entry.title.trim(),
                lyrics = entry.lyrics?.trim()?.ifBlank { null },
                audio = entry.audio.distinct(),
            )
            val previous = flow.value.firstOrNull { it.id == clean.id }
            val next = if (!clean.userMade && clean.lyrics == null && clean.audio.isEmpty()) {
                flow.value.filterNot { it.id == clean.id }
            } else {
                flow.value.filterNot { it.id == clean.id } + clean
            }
            write(app, next)
            flow.value = next
            val stillUsed = next.flatMap { it.audio }.toSet()
            previous?.audio.orEmpty().filter { it !in stillUsed }.forEach { rel ->
                File(app.filesDir, rel).delete()
            }
        }
    }

    suspend fun delete(context: Context, id: String) {
        val app = context.applicationContext
        mutex.withLock {
            ensureLoaded(app)
            val previous = flow.value.firstOrNull { it.id == id } ?: return@withLock
            val next = flow.value.filterNot { it.id == id }
            write(app, next)
            flow.value = next
            val stillUsed = next.flatMap { it.audio }.toSet()
            previous.audio.filter { it !in stillUsed }.forEach { rel ->
                File(app.filesDir, rel).delete()
            }
        }
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            flow.value = read(context)
            loaded = true
        }
    }

    private fun file(context: Context) = File(context.filesDir, "kids_user_songs.json")

    private fun read(context: Context): List<Entry> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isBlank()) continue
                    val audioArr = o.optJSONArray("audio")
                    val audio = buildList {
                        if (audioArr != null) {
                            for (j in 0 until audioArr.length()) {
                                val path = audioArr.optString(j)
                                if (path.isNotBlank()) add(path)
                            }
                        }
                    }
                    val lyrics = if (o.has("lyrics") && !o.isNull("lyrics")) o.optString("lyrics") else null
                    add(
                        Entry(
                            id = id,
                            title = o.optString("title"),
                            lyrics = lyrics?.ifBlank { null },
                            audio = audio,
                            userMade = o.optBoolean("userMade"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("title", entry.title)
                    if (entry.lyrics != null) put("lyrics", entry.lyrics)
                    put("userMade", entry.userMade)
                    put(
                        "audio",
                        JSONArray().apply { entry.audio.forEach { put(it) } },
                    )
                },
            )
        }
        file(context).writeText(arr.toString())
    }
}

fun kidsSongLyricLines(text: String): List<KidsSongLine> =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { KidsSongLine(it) }
