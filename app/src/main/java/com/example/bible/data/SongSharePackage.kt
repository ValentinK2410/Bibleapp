package com.example.bible.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private const val IMPORT_TAG = "SongShareImport"

/** Понятная причина, если [SongSharePackage.importFromZip] не смог разобрать архив. */
enum class SongShareImportError {
    /** Нет manifest.json или не читается. */
    MISSING_MANIFEST,
    /** В архиве manifest от полного бэкапа приложения — импорт через Настройки → Резервная копия. */
    FULL_APP_BACKUP,
    /** manifest не bible_song_share. */
    WRONG_FORMAT,
    /** Нет song.json или битый JSON. */
    MISSING_OR_BAD_SONG_JSON,
    /** В song.json нет ссылок на аудио. */
    NO_AUDIO_REFS,
    /** В ZIP нет файла из song.json (рассинхрон или обрезанный архив). */
    MEDIA_ENTRY_MISSING,
    /** Исключение при чтении ZIP/копировании. */
    IO_OR_PARSE,
}

sealed class SongShareImportOutcome {
    data class Ok(val results: List<SongShareImportResult>) : SongShareImportOutcome()
    data class Err(val error: SongShareImportError) : SongShareImportOutcome()
}

/** Результат импорта ZIP [FORMAT]; [highlightLineWhilePlayingHint] — пожелание отправителя из manifest (если было). */
data class SongShareImportResult(
    val song: SongItem,
    val highlightLineWhilePlayingHint: Boolean?,
)

/**
 * Переносимый пакет одной песни (текст + локальные аудиофайлы + таймкоды строк [SongLyricCue])
 * для обмена между установками этого приложения.
 *
 * Структура ZIP:
 * - `manifest.json` — [FORMAT], version, опция подсветки строк при воспроизведении;
 * - `song.json` — метаданные и **относительные** пути `media/track_N.ext`;
 * - каталог `media/` с файлами озвучки в том же порядке, что и в `song.json`.
 */
object SongSharePackage {

    const val FORMAT = "bible_song_share"
    const val FORMAT_MULTI = "bible_songs_share"
    private const val VERSION = 1
    const val MANIFEST_NAME = "manifest.json"
    const val SONG_JSON_NAME = "song.json"
    private const val MEDIA_DIR = "media/"

    /** Расширения озвучки, если архив без папки media (файлы в корне и т.п.). */
    private val LOOSE_AUDIO_EXT = setOf(
        "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "3gp", "amr", "wma",
    )

    fun canShareSong(song: SongItem): Boolean {
        if (song.lyrics.isBlank()) return false
        return song.audioPaths.any { path -> File(path).isFile }
    }

    fun shareableSongs(songs: List<SongItem>): List<SongItem> =
        songs.filter { canShareSong(it) }

    /**
     * @param highlightLineWhilePlaying подсказка получателю (сохраняется в manifest).
     */
    suspend fun exportToZip(
        context: Context,
        song: SongItem,
        highlightLineWhilePlaying: Boolean,
    ): File = exportSongsToZip(
        context,
        listOf(song),
        highlightLineWhilePlaying,
    )

    /**
     * Один или несколько песенных пакетов в одном ZIP.
     */
    suspend fun exportSongsToZip(
        context: Context,
        songs: List<SongItem>,
        highlightLineWhilePlaying: Boolean,
    ): File = withContext(Dispatchers.IO) {
        val shareable = shareableSongs(songs)
        require(shareable.isNotEmpty()) { "no shareable songs" }

        val zipFile = File(
            context.cacheDir,
            if (shareable.size == 1) {
                "bible_song_${shareable[0].id.take(8)}_${System.currentTimeMillis()}.zip"
            } else {
                "bible_songs_${shareable.size}_${System.currentTimeMillis()}.zip"
            },
        )

        val manifest = JSONObject().apply {
            put("format", if (shareable.size == 1) FORMAT else FORMAT_MULTI)
            put("version", VERSION)
            put("highlightLineWhilePlaying", highlightLineWhilePlaying)
            if (shareable.size > 1) put("count", shareable.size)
        }

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry(MANIFEST_NAME))
            zos.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            shareable.forEachIndexed { songIdx, song ->
                val prefix = if (shareable.size == 1) "" else "songs/$songIdx/"
                writeSongEntries(zos, song, prefix, highlightLineWhilePlaying)
            }
        }
        zipFile
    }

    private fun writeSongEntries(
        zos: ZipOutputStream,
        song: SongItem,
        prefix: String,
        @Suppress("UNUSED_PARAMETER") highlightLineWhilePlaying: Boolean,
    ) {
        val audioFiles = song.audioPaths.map { File(it) }.filter { it.isFile }
        require(audioFiles.isNotEmpty()) { "no audio files" }

        val mediaPrefix = "${prefix}${MEDIA_DIR}"
        val labels = resolveAudioLabels(song, audioFiles)
        val usedZipNames = mutableSetOf<String>()
        val relativePaths = audioFiles.mapIndexed { i, src ->
            val zipFileName = uniqueMediaFileName(labels[i], src, i, usedZipNames)
            "${mediaPrefix}$zipFileName"
        }

        val songJson = songToPortableJson(song, relativePaths, labels)
        val songEntry = "${prefix}$SONG_JSON_NAME"
        zos.putNextEntry(ZipEntry(songEntry))
        zos.write(songJson.toString().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        audioFiles.forEachIndexed { i, src ->
            zos.putNextEntry(ZipEntry(relativePaths[i]))
            FileInputStream(src).use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    /**
     * Импорт: новые песни с новыми id, аудио копируются в `files/songs_audio/`.
     */
    suspend fun importFromZip(context: Context, zipFile: File): SongShareImportOutcome =
        withContext(Dispatchers.IO) {
            try {
                var manifest: JSONObject? = null
                val songJsonByPath = mutableMapOf<String, JSONObject>()
                val mediaExtracted = mutableMapOf<String, File>()

                val workDir = File(context.cacheDir, "song_pkg_${System.currentTimeMillis()}").apply { mkdirs() }
                try {
                    ZipFile(zipFile).use { zf ->
                        for (entry in zf.entries().asSequence()) {
                            if (entry.isDirectory) continue
                            val canonName = canonicalZipEntryName(entry.name)
                            when {
                                pathsEqualIgnoreCase(canonName, MANIFEST_NAME) -> {
                                    try {
                                        zf.getInputStream(entry).use { ins ->
                                            manifest = JSONObject(ins.readBytes().utf8TextDropBom())
                                        }
                                    } catch (e: Exception) {
                                        Log.w(IMPORT_TAG, "bad manifest json", e)
                                    }
                                }
                                pathsEqualIgnoreCase(canonName, SONG_JSON_NAME) ||
                                    canonName.endsWith("/$SONG_JSON_NAME", ignoreCase = true) -> {
                                    try {
                                        zf.getInputStream(entry).use { ins ->
                                            songJsonByPath[canonName.lowercase()] =
                                                JSONObject(ins.readBytes().utf8TextDropBom())
                                        }
                                    } catch (e: Exception) {
                                        Log.w(IMPORT_TAG, "bad song.json at $canonName", e)
                                    }
                                }
                                else -> {
                                    val mediaKey = mediaStorageKey(canonName)
                                        ?: looseAudioMediaKey(canonName)
                                    if (mediaKey == null) continue
                                    val out = File(workDir, mediaKey.replace('/', '_'))
                                    out.parentFile?.mkdirs()
                                    try {
                                        zf.getInputStream(entry).use { ins ->
                                            FileOutputStream(out).use { os -> ins.copyTo(os) }
                                        }
                                        if (out.length() == 0L) {
                                            out.delete()
                                            continue
                                        }
                                        mediaExtracted[mediaKey] = out
                                        mediaExtracted[canonicalZipEntryName(canonName).lowercase()] = out
                                    } catch (e: Exception) {
                                        Log.w(IMPORT_TAG, "extract fail: $canonName", e)
                                        runCatching { out.delete() }
                                    }
                                }
                            }
                        }
                    }

                    val man = manifest ?: run {
                        return@withContext SongShareImportOutcome.Err(SongShareImportError.MISSING_MANIFEST)
                    }
                    val fmt = man.optString("format", "")
                    if (fmt == "bible_app_export") {
                        return@withContext SongShareImportOutcome.Err(SongShareImportError.FULL_APP_BACKUP)
                    }
                    if (fmt != FORMAT && fmt != FORMAT_MULTI) {
                        return@withContext SongShareImportOutcome.Err(SongShareImportError.WRONG_FORMAT)
                    }
                    val highlightHint = if (man.has("highlightLineWhilePlaying")) {
                        man.getBoolean("highlightLineWhilePlaying")
                    } else {
                        null
                    }

                    val songEntries = when (fmt) {
                        FORMAT_MULTI -> songJsonByPath.entries
                            .filter { (path, _) ->
                                Regex("""(?i)songs/\d+/song\.json$""").containsMatchIn(path)
                            }
                            .sortedBy { it.key }
                            .map { it.value }
                        else -> {
                            val sj = songJsonByPath.entries
                                .firstOrNull { (path, _) -> pathsEqualIgnoreCase(path, SONG_JSON_NAME) }
                                ?.value
                                ?: songJsonByPath.values.firstOrNull()
                            if (sj == null) {
                                return@withContext SongShareImportOutcome.Err(SongShareImportError.MISSING_OR_BAD_SONG_JSON)
                            }
                            listOf(sj)
                        }
                    }
                    if (songEntries.isEmpty()) {
                        return@withContext SongShareImportOutcome.Err(SongShareImportError.MISSING_OR_BAD_SONG_JSON)
                    }

                    val destDir = File(context.filesDir, "songs_audio").apply { mkdirs() }
                    val baseStamp = System.currentTimeMillis()
                    val results = mutableListOf<SongShareImportResult>()
                    val isMulti = fmt == FORMAT_MULTI
                    songEntries.forEachIndexed { songIdx, sj ->
                        val songMediaPrefix = if (isMulti) "songs/$songIdx/" else ""
                        val scopedMedia = scopedMediaForSong(mediaExtracted, songMediaPrefix)
                        val imported = importSongJson(
                            sj = sj,
                            mediaExtracted = scopedMedia,
                            destDir = destDir,
                            stamp = baseStamp + songIdx,
                        ) ?: return@withContext SongShareImportOutcome.Err(SongShareImportError.MEDIA_ENTRY_MISSING)
                        results.add(
                            SongShareImportResult(
                                song = imported,
                                highlightLineWhilePlayingHint = highlightHint,
                            ),
                        )
                    }
                    SongShareImportOutcome.Ok(results)
                } finally {
                    workDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Log.e(IMPORT_TAG, "import failed", e)
                SongShareImportOutcome.Err(SongShareImportError.IO_OR_PARSE)
            }
        }

    private fun importSongJson(
        sj: JSONObject,
        mediaExtracted: Map<String, File>,
        destDir: File,
        stamp: Long,
    ): SongItem? {
        val relList = mutableListOf<String>()
        if (sj.has("audios")) {
            val arr = sj.getJSONArray("audios")
            for (i in 0 until arr.length()) {
                arr.optString(i)?.takeIf { it.isNotBlank() }?.let { relList.add(it) }
            }
        }
        if (sj.has("audio")) {
            val a = sj.optString("audio", "")
            if (a.isNotBlank() && a !in relList) relList.add(0, a)
        }
        if (relList.isEmpty()) return null

        val labels = readAudioLabels(sj, relList)
        val newAudioPaths = relList.mapIndexed { idx, rel ->
            val extracted = resolveExtractedMedia(mediaExtracted, rel, idx, relList.size) ?: return null
            val ext = extracted.name.substringAfterLast('.', "").let { e ->
                if (e.isNotBlank() && e.length <= 8) ".$e" else ""
            }
            val baseName = labels.getOrNull(idx)?.takeIf { it.isNotBlank() }
                ?: rel.substringAfterLast('/').substringBeforeLast('.').ifBlank { "track_$idx" }
            val final = uniqueMediaDestFile(destDir, baseName, ext, stamp, idx)
            extracted.copyTo(final, overwrite = true)
            final.absolutePath
        }
        return songFromPortableJson(sj, newAudioPaths, labels)
    }

    private fun readAudioLabels(sj: JSONObject, relList: List<String>): List<String> {
        val labels = mutableListOf<String>()
        if (sj.has("audioLabels")) {
            val arr = sj.getJSONArray("audioLabels")
            for (i in 0 until arr.length()) {
                labels.add(arr.optString(i, ""))
            }
        }
        while (labels.size < relList.size) {
            val rel = relList[labels.size]
            labels.add(rel.substringAfterLast('/').substringBeforeLast('.'))
        }
        return labels
    }

    /** Оставляет только медиа текущей песни (для multi-ZIP — без чужих `songs/N/`). */
    private fun scopedMediaForSong(
        allMedia: Map<String, File>,
        songPrefix: String,
    ): Map<String, File> {
        if (songPrefix.isBlank()) {
            return allMedia.filterKeys { key ->
                !Regex("""(?i)^songs/\d+/""").containsMatchIn(key)
            }
        }
        val prefixLower = songPrefix.lowercase()
        return allMedia.filterKeys { it.startsWith(prefixLower) }
    }

    private fun ByteArray.utf8TextDropBom(): String {
        var s = this.toString(Charsets.UTF_8)
        if (s.isNotEmpty() && s[0] == '\uFEFF') s = s.substring(1)
        return s
    }

    private fun canonicalZipEntryName(name: String): String =
        name.replace('\\', '/').trimStart('/').removePrefix("./")

    private fun pathsEqualIgnoreCase(a: String, b: String): Boolean =
        canonicalZipEntryName(a).equals(canonicalZipEntryName(b), ignoreCase = true)

    /**
     * Ключ медиа в архиве: полный путь от `songs/N/` или `media/`, чтобы дорожки разных песен не пересекались.
     */
    private fun mediaStorageKey(canonName: String): String? {
        val parts = canonicalZipEntryName(canonName).split('/').filter { it.isNotBlank() }
        val mi = parts.indexOfFirst { it.equals("media", ignoreCase = true) }
        if (mi < 0) return null
        val rest = parts.drop(mi + 1).joinToString("/")
        if (rest.isBlank()) return null
        val keyStart = if (mi >= 2 &&
            parts[mi - 2].equals("songs", ignoreCase = true) &&
            parts[mi - 1].all { it.isDigit() }
        ) {
            mi - 2
        } else {
            mi
        }
        return parts.drop(keyStart).joinToString("/").lowercase()
    }

    /** Порядок файлов как при экспорте: track_0, track_1, … иначе по пути в архиве. */
    private fun orderedMediaFiles(map: Map<String, File>): List<File> {
        val parsed = map.entries.mapNotNull { (k, f) ->
            Regex("""(?i)track_(\d+)""").find(k)?.groupValues?.get(1)?.toIntOrNull()?.let { it to f }
        }
        if (parsed.size == map.size && parsed.isNotEmpty()) {
            return parsed.sortedBy { it.first }.map { it.second }
        }
        return map.entries.sortedBy { it.key.lowercase() }.map { it.value }
    }

    /**
     * Сопоставляет запись из song.json с извлечённым файлом: точный путь, имя файла, track_N, порядок.
     */
    private fun resolveExtractedMedia(
        map: Map<String, File>,
        rel: String,
        indexInList: Int,
        relListSize: Int,
    ): File? {
        val normRel = rel.replace('\\', '/').trimStart('/').removePrefix("./").lowercase()
        lookupMediaFile(map, normRel)?.let { return it }

        val songPrefix = Regex("""(?i)^(songs/\d+/)""").find(normRel)?.groupValues?.get(1)?.lowercase() ?: ""
        val scoped = if (songPrefix.isBlank()) map else map.filterKeys { it.startsWith(songPrefix) }

        val refTail = normRel.substringAfterLast('/')
        if (refTail.isNotBlank()) {
            scoped.entries.find { (k, f) ->
                f.name.equals(refTail, ignoreCase = true) || k.endsWith("/$refTail")
            }?.value?.let { return it }
        }
        Regex("""(?i)track_(\d+)""").find(normRel)?.groupValues?.get(1)?.toIntOrNull()?.let { trackNum ->
            scoped.entries.find { (k, _) ->
                Regex("""(?i)track_$trackNum(\.|$)""").containsMatchIn(k)
            }?.value?.let { return it }
        }
        // Старые архивы без «/» между media и track: songs/0/mediarack_0.mp3
        Regex("""(?i)mediatrack_(\d+)""").find(normRel)?.groupValues?.get(1)?.toIntOrNull()?.let { trackNum ->
            scoped.entries.find { (k, _) ->
                Regex("""(?i)(mediatrack_|track_)$trackNum(\.|$)""").containsMatchIn(k)
            }?.value?.let { return it }
        }
        val ordered = orderedMediaFiles(scoped)
        if (indexInList in ordered.indices && ordered.size >= relListSize) {
            return ordered[indexInList]
        }
        if (relListSize == 1 && ordered.size == 1) {
            return ordered[0]
        }
        return null
    }

    /**
     * Файл озвучки без сегмента `media/` в пути (корень ZIP, мессенджер «сплющил» пути и т.д.).
     */
    private fun looseAudioMediaKey(canonName: String): String? {
        val full = canonicalZipEntryName(canonName)
        val lowerFull = full.lowercase()
        if ("__macosx" in lowerFull || lowerFull.endsWith(".ds_store")) return null
        val base = full.substringAfterLast('/')
        if (base.isBlank()) return null
        if (pathsEqualIgnoreCase(base, MANIFEST_NAME) || pathsEqualIgnoreCase(base, SONG_JSON_NAME)) return null
        if (base.equals("thumbs.db", ignoreCase = true)) return null
        val parts = full.split('/').filter { it.isNotBlank() }
        val songPrefix = if (parts.size >= 3 &&
            parts[0].equals("songs", ignoreCase = true) &&
            parts[1].all { it.isDigit() }
        ) {
            "${parts[0]}/${parts[1]}/"
        } else {
            ""
        }
        if (Regex("""(?i)^track_\d+\.""").containsMatchIn(base)) {
            return "${songPrefix}media/${base.lowercase()}"
        }
        val ext = base.substringAfterLast('.', "").lowercase()
        if (ext.isBlank() || ext.length > 8 || ext !in LOOSE_AUDIO_EXT) return null
        return "${songPrefix}media/${base.lowercase()}"
    }

    private fun normalizeAudioRefToMediaKey(ref: String): String =
        ref.replace('\\', '/').trimStart('/').removePrefix("./").lowercase()

    private fun lookupMediaFile(map: Map<String, File>, key: String): File? {
        map[key]?.let { return it }
        val keyNorm = key.trimEnd('/').lowercase()
        map.entries.find { (k, _) ->
            k.trimEnd('/').lowercase() == keyNorm
        }?.value?.let { return it }
        val tail = keyNorm.substringAfterLast('/')
        if (tail.isNotBlank()) {
            map.entries.find { (k, _) ->
                k.trimEnd('/').lowercase().endsWith("/$tail")
            }?.value?.let { return it }
        }
        return null
    }

    private fun resolveAudioLabels(song: SongItem, files: List<File>): List<String> =
        files.mapIndexed { i, file ->
            song.audioLabels.getOrNull(i)?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension.ifBlank { file.name }
        }

    private fun sanitizeMediaBaseName(raw: String): String =
        raw.replace(Regex("""[^\w\d._\-() ]"""), "_").take(120).trim().ifBlank { "track" }

    private fun uniqueMediaFileName(
        label: String,
        src: File,
        index: Int,
        used: MutableSet<String>,
    ): String {
        val ext = src.name.substringAfterLast('.', "").let { e ->
            if (e.isNotBlank() && e.length <= 8) ".$e" else ""
        }
        val base = sanitizeMediaBaseName(label.ifBlank { src.nameWithoutExtension.ifBlank { "track_$index" } })
        var name = "$base$ext"
        var n = 1
        while (name.lowercase() in used) {
            name = "${base}_$n$ext"
            n++
        }
        used.add(name.lowercase())
        return name
    }

    private fun uniqueMediaDestFile(dir: File, baseName: String, ext: String, stamp: Long, idx: Int): File {
        val base = sanitizeMediaBaseName(baseName)
        var file = File(dir, "$base$ext")
        if (!file.exists()) return file
        var n = 1
        while (file.exists()) {
            file = File(dir, "${base}_$n$ext")
            n++
        }
        if (file.exists()) {
            file = File(dir, "${base}_${stamp}_$idx$ext")
        }
        return file
    }

    private fun songToPortableJson(
        song: SongItem,
        relativeAudioPaths: List<String>,
        labels: List<String>,
    ): JSONObject {
        return JSONObject().apply {
            put("title", song.title)
            put("artist", song.artist)
            put("lyrics", song.lyrics)
            when {
                relativeAudioPaths.isEmpty() -> {}
                relativeAudioPaths.size == 1 -> put("audio", relativeAudioPaths[0])
                else -> {
                    put("audios", JSONArray().apply { relativeAudioPaths.forEach { put(it) } })
                    put("audio", relativeAudioPaths[0])
                }
            }
            if (labels.isNotEmpty()) {
                put("audioLabels", JSONArray().apply { labels.forEach { put(it) } })
            }
            if (song.tags.isNotEmpty()) {
                put("tags", JSONArray().apply { song.tags.forEach { put(it) } })
            }
            if (song.lyricCues.isNotEmpty()) {
                put(
                    "lyricCues",
                    JSONArray().apply {
                        song.lyricCues.forEach { c ->
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
            if (song.sourceUrl != null) put("url", song.sourceUrl)
            put("ca", song.createdAt)
        }
    }

    private fun songFromPortableJson(
        j: JSONObject,
        resolvedAudioPaths: List<String>,
        labels: List<String>,
    ): SongItem {
        val lyricCues = if (j.has("lyricCues")) {
            val arr = j.getJSONArray("lyricCues")
            (0 until arr.length()).map { idx ->
                val o = arr.getJSONObject(idx)
                SongLyricCue(timeMs = o.getLong("t"), lineIndex = o.getInt("i"))
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
        return SongItem(
            id = UUID.randomUUID().toString(),
            title = j.optString("title", ""),
            artist = j.optString("artist", ""),
            lyrics = j.optString("lyrics", ""),
            audioPaths = resolvedAudioPaths,
            audioLabels = labels.take(resolvedAudioPaths.size),
            videoPath = null,
            sourceUrl = j.optString("url", "").takeIf { it.isNotBlank() },
            tags = tags,
            createdAt = j.optLong("ca", System.currentTimeMillis()),
            lyricCues = lyricCues,
        )
    }
}
