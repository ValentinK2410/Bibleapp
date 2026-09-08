package com.example.bible.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.zip.GZIPInputStream

data class PvHymn(
    val id: String,
    val number: Int,
    val title: String,
    val lyrics: String,
    val builtIn: Boolean,
    val audioPaths: List<String> = emptyList(),
    val audioLabels: List<String> = emptyList(),
    val audioSourceUrls: List<String> = emptyList(),
) {
    fun toSongItem(): SongItem = SongItem(
        id = id,
        title = if (number > 0) "$number. $title" else title,
        artist = "Песнь возрождения",
        lyrics = lyrics,
        audioPaths = audioPaths,
        audioLabels = audioLabels,
        audioSourceUrls = audioSourceUrls,
        tags = listOf("Песнь возрождения"),
    )
}

/** Пользовательские правки и дорожки к гимну каталога (в т.ч. добавленные вручную). */
data class PvHymnOverlay(
    val id: String,
    val number: Int = 0,
    val title: String = "",
    val lyrics: String = "",
    val audioPaths: List<String> = emptyList(),
    val audioLabels: List<String> = emptyList(),
    val audioSourceUrls: List<String> = emptyList(),
    val builtIn: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("n", number)
        if (title.isNotBlank()) put("t", title)
        if (lyrics.isNotBlank()) put("l", lyrics)
        if (audioPaths.isNotEmpty()) {
            put("a", JSONArray().apply { audioPaths.forEach { put(it) } })
        }
        if (audioLabels.isNotEmpty()) {
            put("al", JSONArray().apply { audioLabels.forEach { put(it) } })
        }
        if (audioSourceUrls.isNotEmpty()) {
            put("au", JSONArray().apply { audioSourceUrls.forEach { put(it) } })
        }
        if (!builtIn) put("u", true)
    }

    companion object {
        fun fromJson(j: JSONObject): PvHymnOverlay {
            fun arr(key: String): List<String> {
                if (!j.has(key)) return emptyList()
                val a = j.getJSONArray(key)
                return (0 until a.length()).map { a.optString(it, "") }
            }
            return PvHymnOverlay(
                id = j.optString("id"),
                number = j.optInt("n", 0),
                title = j.optString("t"),
                lyrics = j.optString("l"),
                audioPaths = arr("a"),
                audioLabels = arr("al"),
                audioSourceUrls = arr("au"),
                builtIn = !j.optBoolean("u", false),
            )
        }

        fun parseList(json: String): List<PvHymnOverlay> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    try {
                        fromJson(arr.getJSONObject(i))
                    } catch (_: Exception) {
                        null
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun toJsonArray(list: List<PvHymnOverlay>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}

object PesnVozrozhdeniyaCatalog {
    private const val ASSET_JSON = "songs/pesn_vozrozhdeniya_3300.json"
    private const val ASSET_GZ = "songs/pesn_vozrozhdeniya_3300.json.gz"

    @Volatile
    private var cached: List<PvHymn>? = null

    fun builtIn(context: Context): List<PvHymn> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val loaded = loadFromAssets(context)
            cached = loaded
            return loaded
        }
    }

    fun merge(builtIn: List<PvHymn>, overlays: List<PvHymnOverlay>): List<PvHymn> {
        val byId = overlays.associateBy { it.id }
        val merged = builtIn.map { hymn ->
            val o = byId[hymn.id] ?: return@map hymn
            hymn.copy(
                title = o.title.ifBlank { hymn.title },
                lyrics = o.lyrics.ifBlank { hymn.lyrics },
                audioPaths = o.audioPaths,
                audioLabels = o.audioLabels,
                audioSourceUrls = o.audioSourceUrls,
            )
        }
        val extra = overlays.filter { !it.builtIn }.map { o ->
            PvHymn(
                id = o.id,
                number = o.number,
                title = o.title,
                lyrics = o.lyrics,
                builtIn = false,
                audioPaths = o.audioPaths,
                audioLabels = o.audioLabels,
                audioSourceUrls = o.audioSourceUrls,
            )
        }
        return merged + extra.sortedBy { it.number }
    }

    fun nextUserNumber(existing: List<PvHymn>): Int =
        (existing.maxOfOrNull { it.number } ?: 3300) + 1

    fun newUserId(): String = "pvuser:${UUID.randomUUID()}"

    private fun loadFromAssets(context: Context): List<PvHymn> {
        for (name in listOf(ASSET_JSON, ASSET_GZ)) {
            val loaded = readAsset(context, name)
            if (loaded.isNotEmpty()) return loaded
        }
        return emptyList()
    }

    private fun readAsset(context: Context, name: String): List<PvHymn> {
        return try {
            val bytes = context.assets.open(name).use { it.readBytes() }
            if (bytes.isEmpty()) return emptyList()
            val json = decodeToJson(bytes)
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val n = o.optInt("n")
                PvHymn(
                    id = UserSongPlaylist.pvRef(n),
                    number = n,
                    title = o.optString("t"),
                    lyrics = o.optString("l"),
                    builtIn = true,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** aapt2 распаковывает .gz в assets — читаем и gzip, и обычный JSON. */
    private fun decodeToJson(bytes: ByteArray): String {
        val gzip = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        val raw = if (gzip) {
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
        } else {
            bytes
        }
        return String(raw, Charsets.UTF_8)
    }
}
