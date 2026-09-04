package com.example.bible.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val TAG = "SongLinkBundle"

/** Переносимый JSON со ссылками на страницы песен и прямые URL дорожек (без аудиофайлов). */
object SongLinkBundle {

    const val FORMAT = "bible_song_links"
    private const val VERSION = 1
    const val FILE_NAME_PREFIX = "bible_song_links_"

    data class TrackLink(
        val url: String,
        val label: String,
    )

    data class SongLinkEntry(
        val title: String,
        val artist: String,
        val lyrics: String,
        val pageUrl: String?,
        val tracks: List<TrackLink>,
        val tags: List<String> = emptyList(),
        val lyricCues: List<SongLyricCue> = emptyList(),
    )

    sealed class ImportOutcome {
        data class Ok(val entries: List<SongLinkEntry>) : ImportOutcome()
        data object WrongFormat : ImportOutcome()
        data object Empty : ImportOutcome()
        data class Err(val message: String) : ImportOutcome()
    }

    /** Песня с сайта: есть страница и/или сохранённые URL дорожек. */
    fun canExportLinks(song: SongItem): Boolean {
        if (!song.sourceUrl.isNullOrBlank() && FonkiExtractor.isFonkiUrl(song.sourceUrl!!)) {
            return true
        }
        return song.audioSourceUrls.any { it.isNotBlank() && looksLikeRemoteAudio(it) }
    }

    fun linkableSongs(songs: List<SongItem>): List<SongItem> = songs.filter { canExportLinks(it) }

    fun songToLinkEntry(song: SongItem): SongLinkEntry? {
        if (!canExportLinks(song)) return null
        val tracks = buildTrackLinks(song)
        return SongLinkEntry(
            title = song.title,
            artist = song.artist,
            lyrics = song.lyrics,
            pageUrl = song.sourceUrl?.takeIf { it.isNotBlank() },
            tracks = tracks,
            tags = song.tags,
            lyricCues = song.lyricCues,
        )
    }

    private fun buildTrackLinks(song: SongItem): List<TrackLink> {
        val out = mutableListOf<TrackLink>()
        song.audioSourceUrls.forEachIndexed { i, url ->
            if (url.isBlank() || !looksLikeRemoteAudio(url)) return@forEachIndexed
            val label = song.audioLabels.getOrNull(i)?.takeIf { it.isNotBlank() }
                ?: "Дорожка ${i + 1}"
            out.add(TrackLink(url = url, label = label))
        }
        return out.distinctBy { it.url.lowercase() }
    }

    private fun looksLikeRemoteAudio(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    suspend fun exportToJsonFile(context: Context, songs: List<SongItem>): File = withContext(Dispatchers.IO) {
        val entries = linkableSongs(songs).mapNotNull { songToLinkEntry(it) }
        require(entries.isNotEmpty()) { "no linkable songs" }
        val root = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("count", entries.size)
            put(
                "songs",
                JSONArray().apply {
                    entries.forEach { put(entryToJson(it)) }
                },
            )
        }
        val file = File(
            context.cacheDir,
            "${FILE_NAME_PREFIX}${entries.size}_${System.currentTimeMillis()}.json",
        )
        file.writeText(root.toString(2), Charsets.UTF_8)
        file
    }

    fun parseJson(text: String): ImportOutcome {
        return try {
            val trimmed = text.trim().removePrefix("\uFEFF")
            if (trimmed.isBlank()) return ImportOutcome.Empty
            val root = JSONObject(trimmed)
            if (root.optString("format", "") != FORMAT) {
                return ImportOutcome.WrongFormat
            }
            val arr = root.optJSONArray("songs") ?: return ImportOutcome.Empty
            val entries = (0 until arr.length()).mapNotNull { i ->
                runCatching { jsonToEntry(arr.getJSONObject(i)) }.getOrNull()
            }
            if (entries.isEmpty()) ImportOutcome.Empty else ImportOutcome.Ok(entries)
        } catch (e: Exception) {
            Log.e(TAG, "parseJson", e)
            ImportOutcome.Err(e.message ?: "parse error")
        }
    }

    /** Скачивает дорожки по ссылкам из записи (или подтягивает список со страницы песни). */
    suspend fun materializeEntry(context: Context, entry: SongLinkEntry): SongItem =
        withContext(Dispatchers.IO) {
            var tracks = entry.tracks.filter { it.url.isNotBlank() }
            if (tracks.isEmpty()) {
                val page = entry.pageUrl?.takeIf { FonkiExtractor.isFonkiUrl(it) }
                if (page != null) {
                    val extracted = FonkiExtractor.extract(page)
                    tracks = extracted.tracks.map { TrackLink(it.url, it.label) }
                }
            }
            val paths = mutableListOf<String>()
            val labels = mutableListOf<String>()
            val sourceUrls = mutableListOf<String>()
            for (track in tracks) {
                val file = FonkiExtractor.downloadAudio(
                    context = context,
                    url = track.url,
                    songTitle = entry.title,
                    songArtist = entry.artist,
                    trackLabel = track.label,
                )
                paths.add(file.absolutePath)
                labels.add(track.label)
                sourceUrls.add(track.url)
            }
            SongItem(
                id = UUID.randomUUID().toString(),
                title = entry.title,
                artist = entry.artist,
                lyrics = entry.lyrics,
                audioPaths = paths,
                audioLabels = labels,
                audioSourceUrls = sourceUrls,
                sourceUrl = entry.pageUrl,
                tags = entry.tags,
                lyricCues = entry.lyricCues,
            )
        }

    fun entryToPendingSong(entry: SongLinkEntry): SongItem =
        SongItem(
            title = entry.title,
            artist = entry.artist,
            lyrics = entry.lyrics,
            sourceUrl = entry.pageUrl,
            audioSourceUrls = entry.tracks.map { it.url },
            audioLabels = entry.tracks.map { it.label },
            tags = entry.tags,
            lyricCues = entry.lyricCues,
        )

    private fun entryToJson(entry: SongLinkEntry): JSONObject = JSONObject().apply {
        put("title", entry.title)
        put("artist", entry.artist)
        put("lyrics", entry.lyrics)
        if (entry.pageUrl != null) put("pageUrl", entry.pageUrl)
        if (entry.tracks.isNotEmpty()) {
            put(
                "tracks",
                JSONArray().apply {
                    entry.tracks.forEach { t ->
                        put(
                            JSONObject().apply {
                                put("url", t.url)
                                put("label", t.label)
                            },
                        )
                    }
                },
            )
        }
        if (entry.tags.isNotEmpty()) {
            put("tags", JSONArray().apply { entry.tags.forEach { put(it) } })
        }
        if (entry.lyricCues.isNotEmpty()) {
            put(
                "lyricCues",
                JSONArray().apply {
                    entry.lyricCues.forEach { c ->
                        put(
                            JSONObject().apply {
                                put("t", c.timeMs)
                                put("i", c.lineIndex)
                            },
                        )
                    }
                },
            )
        }
    }

    private fun jsonToEntry(j: JSONObject): SongLinkEntry {
        val tracks = if (j.has("tracks")) {
            val arr = j.getJSONArray("tracks")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val url = o.optString("url", "").trim()
                if (url.isBlank()) null
                else TrackLink(url = url, label = o.optString("label", "").ifBlank { "Дорожка" })
            }
        } else {
            emptyList()
        }
        val tags = if (j.has("tags")) {
            val arr = j.getJSONArray("tags")
            (0 until arr.length()).map { arr.getString(it) }
        } else {
            emptyList()
        }
        val cues = if (j.has("lyricCues")) {
            val arr = j.getJSONArray("lyricCues")
            (0 until arr.length()).map { idx ->
                val o = arr.getJSONObject(idx)
                SongLyricCue(timeMs = o.getLong("t"), lineIndex = o.getInt("i"))
            }
        } else {
            emptyList()
        }
        return SongLinkEntry(
            title = j.optString("title", ""),
            artist = j.optString("artist", ""),
            lyrics = j.optString("lyrics", ""),
            pageUrl = j.optString("pageUrl", "").takeIf { it.isNotBlank() }
                ?: j.optString("url", "").takeIf { it.isNotBlank() },
            tracks = tracks,
            tags = tags,
            lyricCues = cues,
        )
    }
}
