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
    data class Ok(val result: SongShareImportResult) : SongShareImportOutcome()
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

    /**
     * @param highlightLineWhilePlaying подсказка получателю (сохраняется в manifest).
     */
    suspend fun exportToZip(
        context: Context,
        song: SongItem,
        highlightLineWhilePlaying: Boolean,
    ): File = withContext(Dispatchers.IO) {
        require(canShareSong(song)) { "need lyrics and at least one audio file on device" }
        val audioFiles = song.audioPaths.map { File(it) }.filter { it.isFile }
        require(audioFiles.isNotEmpty()) { "no audio files" }

        val zipFile = File(
            context.cacheDir,
            "bible_song_${song.id.take(8)}_${System.currentTimeMillis()}.zip",
        )

        val relativePaths = audioFiles.mapIndexed { i, src ->
            val ext = src.name.substringAfterLast('.', "").let { e ->
                if (e.isNotBlank() && e.length <= 8) ".$e" else ""
            }
            "${MEDIA_DIR.trimEnd('/')}track_$i$ext"
        }

        val manifest = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("highlightLineWhilePlaying", highlightLineWhilePlaying)
        }

        val songJson = songToPortableJson(song, relativePaths)

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry(MANIFEST_NAME))
            zos.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry(SONG_JSON_NAME))
            zos.write(songJson.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            audioFiles.forEachIndexed { i, src ->
                val entryName = relativePaths[i]
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(src).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        zipFile
    }

    /**
     * Импорт: новая песня с новым [SongItem.id], аудио копируются в `files/songs_audio/`.
     */
    suspend fun importFromZip(context: Context, zipFile: File): SongShareImportOutcome =
        withContext(Dispatchers.IO) {
            try {
                var manifest: JSONObject? = null
                var songJo: JSONObject? = null
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
                                            val text = ins.readBytes().utf8TextDropBom()
                                            manifest = JSONObject(text)
                                        }
                                    } catch (e: Exception) {
                                        Log.w(IMPORT_TAG, "bad manifest json", e)
                                    }
                                }
                                pathsEqualIgnoreCase(canonName, SONG_JSON_NAME) -> {
                                    try {
                                        zf.getInputStream(entry).use { ins ->
                                            val text = ins.readBytes().utf8TextDropBom()
                                            songJo = JSONObject(text)
                                        }
                                    } catch (e: Exception) {
                                        Log.w(IMPORT_TAG, "bad song.json", e)
                                    }
                                }
                                else -> {
                                    val mediaKey = mediaFolderKeyFromAnyPath(canonName)
                                        ?: looseAudioMediaKey(canonName)
                                    if (mediaKey == null) continue
                                    val short = mediaKey.removePrefix("media/").trimStart('/')
                                    if (short.isBlank()) continue
                                    val out = File(workDir, short.replace('/', '_'))
                                    out.parentFile?.mkdirs()
                                    try {
                                        zf.getInputStream(entry).use { ins ->
                                            FileOutputStream(out).use { os -> ins.copyTo(os) }
                                        }
                                        if (out.length() == 0L) {
                                            Log.w(IMPORT_TAG, "zero-length audio entry: $canonName")
                                            out.delete()
                                            continue
                                        }
                                        mediaExtracted[mediaKey] = out
                                    } catch (e: Exception) {
                                        Log.w(IMPORT_TAG, "extract fail: $canonName", e)
                                        runCatching { out.delete() }
                                    }
                                }
                            }
                        }
                    }
                    Log.i(
                        IMPORT_TAG,
                        "zip=${zipFile.length()}B mediaFiles=${mediaExtracted.size} keys=${mediaExtracted.keys}",
                    )

                    val man = manifest ?: run {
                        Log.w(IMPORT_TAG, "no manifest.json in zip")
                        return@withContext SongShareImportOutcome.Err(SongShareImportError.MISSING_MANIFEST)
                    }
                    val fmt = man.optString("format", "")
                    if (fmt == "bible_app_export") {
                        Log.w(IMPORT_TAG, "user opened full app backup in song importer")
                        return@withContext SongShareImportOutcome.Err(SongShareImportError.FULL_APP_BACKUP)
                    }
                    if (fmt != FORMAT) {
                        Log.w(IMPORT_TAG, "format=$fmt expected $FORMAT")
                        return@withContext SongShareImportOutcome.Err(SongShareImportError.WRONG_FORMAT)
                    }
                    val highlightHint = if (man.has("highlightLineWhilePlaying")) {
                        man.getBoolean("highlightLineWhilePlaying")
                    } else {
                        null
                    }
                    val sj = songJo
                    if (sj == null) {
                        Log.w(IMPORT_TAG, "missing or invalid song.json")
                        return@withContext SongShareImportOutcome.Err(SongShareImportError.MISSING_OR_BAD_SONG_JSON)
                    }

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
                    if (relList.isEmpty()) {
                        Log.w(IMPORT_TAG, "song.json has no audio refs")
                        return@withContext SongShareImportOutcome.Err(SongShareImportError.NO_AUDIO_REFS)
                    }

                    val destDir = File(context.filesDir, "songs_audio").apply { mkdirs() }
                    val stamp = System.currentTimeMillis()
                    val newAudioPaths = relList.mapIndexed { idx, rel ->
                        val extracted = resolveExtractedMedia(mediaExtracted, rel, idx, relList.size)
                            ?: run {
                                Log.w(
                                    IMPORT_TAG,
                                    "no zip entry for audio ref=$rel keys=${mediaExtracted.keys} relCount=${relList.size}",
                                )
                                return@withContext SongShareImportOutcome.Err(SongShareImportError.MEDIA_ENTRY_MISSING)
                            }
                        val ext = extracted.name.substringAfterLast('.', "").let { e ->
                            if (e.isNotBlank() && e.length <= 8) ".$e" else ""
                        }
                        val final = File(destDir, "import_${stamp}_${idx}$ext")
                        extracted.copyTo(final, overwrite = true)
                        final.absolutePath
                    }

                    SongShareImportOutcome.Ok(
                        SongShareImportResult(
                            song = songFromPortableJson(sj, newAudioPaths),
                            highlightLineWhilePlayingHint = highlightHint,
                        ),
                    )
                } finally {
                    workDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Log.e(IMPORT_TAG, "import failed", e)
                SongShareImportOutcome.Err(SongShareImportError.IO_OR_PARSE)
            }
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
     * Находит в пути сегмент `media` (без ложных срабатываний на `notmedia/`) и возвращает ключ `media/...`.
     * Поддерживает вложение: `папка/media/track_0.mp3`.
     */
    private fun mediaFolderKeyFromAnyPath(canonName: String): String? {
        val parts = canonicalZipEntryName(canonName).split('/').filter { it.isNotBlank() }
        val mi = parts.indexOfFirst { it.equals("media", ignoreCase = true) }
        if (mi < 0) return null
        val rest = parts.drop(mi + 1).joinToString("/")
        if (rest.isBlank()) return null
        return "media/${rest.lowercase()}"
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
        lookupMediaFile(map, normalizeAudioRefToMediaKey(rel))?.let { return it }
        val normRel = rel.replace('\\', '/').trimStart('/')
        val refTail = normRel.substringAfterLast('/').ifBlank { null }
        if (refTail != null) {
            map.entries.find { (_, f) -> f.name.equals(refTail, ignoreCase = true) }?.value?.let { return it }
        }
        Regex("""(?i)track_(\d+)""").find(normRel)?.groupValues?.get(1)?.toIntOrNull()?.let { trackNum ->
            map.entries.find { (k, _) -> Regex("""(?i)track_$trackNum(\.|$)""").containsMatchIn(k) }?.value?.let {
                return it
            }
        }
        val ordered = orderedMediaFiles(map)
        if (indexInList in ordered.indices && ordered.size >= relListSize) {
            return ordered[indexInList]
        }
        if (relListSize == 1 && ordered.isNotEmpty()) {
            return ordered[0]
        }
        if (relListSize == 1 && map.size == 1) {
            return map.values.first()
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
        if (Regex("""(?i)^track_\d+\.""").containsMatchIn(base)) {
            return "media/${base.lowercase()}"
        }
        val ext = base.substringAfterLast('.', "").lowercase()
        if (ext.isBlank() || ext.length > 8 || ext !in LOOSE_AUDIO_EXT) return null
        return "media/${base.lowercase()}"
    }

    private fun normalizeAudioRefToMediaKey(ref: String): String {
        val c = ref.replace('\\', '/').trimStart('/')
        val without = if (c.lowercase().startsWith("media/")) {
            c.drop(6).trimStart('/')
        } else {
            c.removePrefix("media/").trimStart('/')
        }
        return "media/${without.lowercase()}"
    }

    private fun lookupMediaFile(map: Map<String, File>, key: String): File? {
        map[key]?.let { return it }
        val keyNorm = key.trimEnd('/')
        map.entries.find { (k, _) ->
            k.trimEnd('/').equals(keyNorm, ignoreCase = true)
        }?.value?.let { return it }
        val tail = keyNorm.removePrefix("media/").trimStart('/')
        if (tail.isNotBlank()) {
            map.entries.find { (k, _) ->
                k.trimEnd('/').lowercase().endsWith("/${tail.lowercase()}")
            }?.value?.let { return it }
        }
        return null
    }

    private fun songToPortableJson(song: SongItem, relativeAudioPaths: List<String>): JSONObject {
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

    private fun songFromPortableJson(j: JSONObject, resolvedAudioPaths: List<String>): SongItem {
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
            videoPath = null,
            sourceUrl = j.optString("url", "").takeIf { it.isNotBlank() },
            tags = tags,
            createdAt = j.optLong("ca", System.currentTimeMillis()),
            lyricCues = lyricCues,
        )
    }
}
