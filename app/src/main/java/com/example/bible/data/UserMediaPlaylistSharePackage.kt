package com.example.bible.data

import android.content.Context
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

enum class UserMediaPlaylistShareError {
    MISSING_MANIFEST,
    FULL_APP_BACKUP,
    WRONG_FORMAT,
    EMPTY,
    IO_OR_PARSE,
}

sealed class UserMediaPlaylistShareOutcome {
    data class Ok(
        val playlist: UserMediaPlaylist,
        val videos: List<BibleUserVideo>,
        val audios: List<BibleUserAudio>,
        val filesCopied: Int,
        val linksOnly: Boolean,
    ) : UserMediaPlaylistShareOutcome()

    data class Err(val error: UserMediaPlaylistShareError) : UserMediaPlaylistShareOutcome()
}

/**
 * Обмен пользовательским видео/аудио-плейлистом.
 *
 * ZIP: `manifest.json`, `playlist.json`, опционально `media/`.
 * «Только ссылки» — JSON без файлов, получатель качает медиа сам.
 */
object UserMediaPlaylistSharePackage {

    const val FORMAT = "bible_playlist_share"
    private const val VERSION = 1
    const val MANIFEST_NAME = "manifest.json"
    const val PLAYLIST_JSON_NAME = "playlist.json"

    fun linksText(
        playlist: UserMediaPlaylist,
        videos: List<BibleUserVideo>,
        audios: List<BibleUserAudio>,
    ): String {
        val videoById = videos.associateBy { it.id }
        val audioById = audios.associateBy { it.id }
        val kindLabel = if (playlist.kind == UserMediaPlaylistKind.VIDEO) "видео" else "аудио"
        val lines = mutableListOf("Плейлист «${playlist.name}» ($kindLabel)")
        playlist.itemIds.forEachIndexed { i, id ->
            val title: String
            val url: String?
            if (playlist.kind == UserMediaPlaylistKind.VIDEO) {
                val v = videoById[id]
                title = v?.title.orEmpty().ifBlank { id }
                url = v?.sourceUrl
            } else {
                val a = audioById[id]
                title = a?.title.orEmpty().ifBlank { id }
                url = a?.sourceUrl
            }
            lines += "${i + 1}. $title"
            if (!url.isNullOrBlank()) lines += url
        }
        return lines.joinToString("\n")
    }

    fun countItemsWithUrl(
        playlist: UserMediaPlaylist,
        videos: List<BibleUserVideo>,
        audios: List<BibleUserAudio>,
    ): Int {
        val videoById = videos.associateBy { it.id }
        val audioById = audios.associateBy { it.id }
        return playlist.itemIds.count { id ->
            if (playlist.kind == UserMediaPlaylistKind.VIDEO) {
                !videoById[id]?.sourceUrl.isNullOrBlank()
            } else {
                !audioById[id]?.sourceUrl.isNullOrBlank()
            }
        }
    }

    fun countLocalFiles(
        context: Context,
        playlist: UserMediaPlaylist,
        videos: List<BibleUserVideo>,
        audios: List<BibleUserAudio>,
    ): Int {
        val videoById = videos.associateBy { it.id }
        val audioById = audios.associateBy { it.id }
        return playlist.itemIds.count { id ->
            val f = if (playlist.kind == UserMediaPlaylistKind.VIDEO) {
                videoById[id]?.let { MediaCatalogPaths.videoFile(context, it.fileName) }
            } else {
                audioById[id]?.let { MediaCatalogPaths.audioFile(context, it.fileName) }
            }
            f != null && f.isFile && f.length() > 64
        }
    }

    suspend fun exportLinksJson(
        context: Context,
        playlist: UserMediaPlaylist,
        videos: List<BibleUserVideo>,
        audios: List<BibleUserAudio>,
    ): File = withContext(Dispatchers.IO) {
        val json = wrapManifestJson(
            buildPlaylistJson(context, playlist, videos, audios, includeFilePaths = false),
            includeFiles = false,
        )
        val out = File(
            context.cacheDir,
            "playlist_${safeFilePart(playlist.name)}_links_${System.currentTimeMillis()}.json",
        )
        out.writeText(json.toString(2), Charsets.UTF_8)
        out
    }

    suspend fun exportZip(
        context: Context,
        playlist: UserMediaPlaylist,
        videos: List<BibleUserVideo>,
        audios: List<BibleUserAudio>,
        includeFiles: Boolean,
    ): File = withContext(Dispatchers.IO) {
        val suffix = if (includeFiles) "files" else "links"
        val zipFile = File(
            context.cacheDir,
            "playlist_${safeFilePart(playlist.name)}_${suffix}_${System.currentTimeMillis()}.zip",
        )
        val playlistJson = buildPlaylistJson(context, playlist, videos, audios, includeFilePaths = includeFiles)
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val manifest = JSONObject().apply {
                put("format", FORMAT)
                put("version", VERSION)
                put("includeFiles", includeFiles)
            }
            zos.putNextEntry(ZipEntry(MANIFEST_NAME))
            zos.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry(PLAYLIST_JSON_NAME))
            zos.write(playlistJson.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            if (includeFiles) {
                copyMediaIntoZip(context, zos, playlist, videos, audios)
            }
        }
        zipFile
    }

    suspend fun importFromFile(
        context: Context,
        file: File,
        existingVideos: List<BibleUserVideo>,
        existingAudios: List<BibleUserAudio>,
        videoLibrary: BibleVideoLibrary,
        audioLibrary: BibleAudioLibrary,
    ): UserMediaPlaylistShareOutcome = withContext(Dispatchers.IO) {
        try {
            if (file.name.endsWith(".json", ignoreCase = true) || looksLikeJson(file)) {
                val root = JSONObject(file.readText(Charsets.UTF_8))
                return@withContext importRoot(
                    root, emptyMap(),
                    existingVideos, existingAudios, videoLibrary, audioLibrary,
                )
            }
            ZipFile(file).use { zf ->
                var manifest: JSONObject? = null
                var playlistJson: JSONObject? = null
                val extracted = mutableMapOf<String, File>()
                val work = File(context.cacheDir, "pl_import_${System.currentTimeMillis()}").apply { mkdirs() }
                try {
                    for (entry in zf.entries().asSequence()) {
                        if (entry.isDirectory) continue
                        val name = entry.name.replace('\\', '/').trimStart('/')
                        val bytes = zf.getInputStream(entry).use { it.readBytes() }
                        when {
                            name.equals(MANIFEST_NAME, ignoreCase = true) ->
                                manifest = JSONObject(String(bytes, Charsets.UTF_8))
                            name.equals(PLAYLIST_JSON_NAME, ignoreCase = true) ->
                                playlistJson = JSONObject(String(bytes, Charsets.UTF_8))
                            else -> {
                                val dest = File(work, name.substringAfterLast('/').ifBlank { "f.bin" })
                                dest.parentFile?.mkdirs()
                                dest.outputStream().use { it.write(bytes) }
                                extracted[name] = dest
                                extracted[name.substringAfterLast('/')] = dest
                            }
                        }
                    }
                    val man = manifest
                    if (man == null && playlistJson == null) {
                        return@use UserMediaPlaylistShareOutcome.Err(UserMediaPlaylistShareError.MISSING_MANIFEST)
                    }
                    if (man != null) {
                        val fmt = man.optString("format")
                        if (fmt == "bible_app_export") {
                            return@use UserMediaPlaylistShareOutcome.Err(UserMediaPlaylistShareError.FULL_APP_BACKUP)
                        }
                        if (fmt.isNotBlank() && fmt != FORMAT) {
                            return@use UserMediaPlaylistShareOutcome.Err(UserMediaPlaylistShareError.WRONG_FORMAT)
                        }
                    }
                    val root = playlistJson
                        ?: man?.optJSONObject("playlist")
                        ?: return@use UserMediaPlaylistShareOutcome.Err(UserMediaPlaylistShareError.EMPTY)
                    importRoot(
                        root, extracted,
                        existingVideos, existingAudios, videoLibrary, audioLibrary,
                    )
                } finally {
                    work.deleteRecursively()
                }
            }
        } catch (_: Exception) {
            UserMediaPlaylistShareOutcome.Err(UserMediaPlaylistShareError.IO_OR_PARSE)
        }
    }

    private fun looksLikeJson(file: File): Boolean = try {
        val head = file.inputStream().use { ins ->
            val buf = ByteArray(24)
            val n = ins.read(buf)
            if (n <= 0) "" else String(buf, 0, n, Charsets.UTF_8)
        }
        head.trimStart().startsWith("{")
    } catch (_: Exception) {
        false
    }

    private fun wrapManifestJson(playlistJson: JSONObject, includeFiles: Boolean): JSONObject =
        JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("includeFiles", includeFiles)
            put("playlist", playlistJson)
        }

    private fun buildPlaylistJson(
        context: Context,
        playlist: UserMediaPlaylist,
        videos: List<BibleUserVideo>,
        audios: List<BibleUserAudio>,
        includeFilePaths: Boolean,
    ): JSONObject {
        val videoById = videos.associateBy { it.id }
        val audioById = audios.associateBy { it.id }
        val items = JSONArray()
        playlist.itemIds.forEachIndexed { index, id ->
            if (playlist.kind == UserMediaPlaylistKind.VIDEO) {
                val v = videoById[id] ?: return@forEachIndexed
                val local = MediaCatalogPaths.videoFile(context, v.fileName)
                items.put(
                    itemJson(
                        title = v.title,
                        source = v.source,
                        sourceUrl = v.sourceUrl,
                        tags = v.tags,
                        zipFileName = if (includeFilePaths && local.isFile && local.length() > 64) {
                            zipMediaName(index, v.fileName)
                        } else {
                            null
                        },
                    ),
                )
            } else {
                val a = audioById[id] ?: return@forEachIndexed
                val local = MediaCatalogPaths.audioFile(context, a.fileName)
                items.put(
                    itemJson(
                        title = a.title,
                        source = a.source,
                        sourceUrl = a.sourceUrl,
                        tags = a.tags,
                        zipFileName = if (includeFilePaths && local.isFile && local.length() > 64) {
                            zipMediaName(index, a.fileName)
                        } else {
                            null
                        },
                    ),
                )
            }
        }
        return JSONObject().apply {
            put("name", playlist.name)
            put("kind", if (playlist.kind == UserMediaPlaylistKind.AUDIO) "audio" else "video")
            put("items", items)
        }
    }

    private fun itemJson(
        title: String,
        source: String,
        sourceUrl: String?,
        tags: List<String>,
        zipFileName: String?,
    ): JSONObject = JSONObject().apply {
        put("title", title)
        put("src", source)
        if (!sourceUrl.isNullOrBlank()) put("url", sourceUrl)
        if (tags.isNotEmpty()) {
            put("tags", JSONArray().apply { tags.forEach { put(it) } })
        }
        if (!zipFileName.isNullOrBlank()) put("file", zipFileName)
    }

    private fun copyMediaIntoZip(
        context: Context,
        zos: ZipOutputStream,
        playlist: UserMediaPlaylist,
        videos: List<BibleUserVideo>,
        audios: List<BibleUserAudio>,
    ) {
        val videoById = videos.associateBy { it.id }
        val audioById = audios.associateBy { it.id }
        playlist.itemIds.forEachIndexed { index, id ->
            val local: File
            val storedName: String
            if (playlist.kind == UserMediaPlaylistKind.VIDEO) {
                val v = videoById[id] ?: return@forEachIndexed
                storedName = v.fileName
                local = MediaCatalogPaths.videoFile(context, storedName)
            } else {
                val a = audioById[id] ?: return@forEachIndexed
                storedName = a.fileName
                local = MediaCatalogPaths.audioFile(context, storedName)
            }
            if (!local.isFile || local.length() <= 64) return@forEachIndexed
            zos.putNextEntry(ZipEntry(zipMediaName(index, storedName)))
            FileInputStream(local).use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    private suspend fun importRoot(
        root: JSONObject,
        extracted: Map<String, File>,
        existingVideos: List<BibleUserVideo>,
        existingAudios: List<BibleUserAudio>,
        videoLibrary: BibleVideoLibrary,
        audioLibrary: BibleAudioLibrary,
    ): UserMediaPlaylistShareOutcome {
        val playlistObj = root.optJSONObject("playlist") ?: root
        if (playlistObj.optString("format") == "bible_app_export") {
            return UserMediaPlaylistShareOutcome.Err(UserMediaPlaylistShareError.FULL_APP_BACKUP)
        }
        val name = playlistObj.optString("name", "Плейлист").ifBlank { "Плейлист" }
        val kind = if (playlistObj.optString("kind") == "audio") {
            UserMediaPlaylistKind.AUDIO
        } else {
            UserMediaPlaylistKind.VIDEO
        }
        val itemsArr = playlistObj.optJSONArray("items") ?: JSONArray()
        if (itemsArr.length() == 0) {
            return UserMediaPlaylistShareOutcome.Err(UserMediaPlaylistShareError.EMPTY)
        }
        val newIds = mutableListOf<String>()
        val newVideos = mutableListOf<BibleUserVideo>()
        val newAudios = mutableListOf<BibleUserAudio>()
        var filesCopied = 0
        var anyFileRef = false
        for (i in 0 until itemsArr.length()) {
            val item = itemsArr.optJSONObject(i) ?: continue
            val title = item.optString("title", "Без названия")
            val url = item.optString("url", "").takeIf { it.isNotBlank() }
            val src = item.optString("src", "share").ifBlank { "share" }
            val tags = if (item.has("tags")) {
                val t = item.getJSONArray("tags")
                (0 until t.length()).map { t.getString(it).trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
            val rel = item.optString("file", "").trim()
            val extractedFile = if (rel.isNotEmpty()) {
                anyFileRef = true
                extracted[rel] ?: extracted[rel.substringAfterLast('/')]
            } else {
                null
            }
            if (kind == UserMediaPlaylistKind.VIDEO) {
                val reused = existingVideos.firstOrNull { v ->
                    !url.isNullOrBlank() && v.sourceUrl == url
                }
                if (reused != null) {
                    newIds += reused.id
                    continue
                }
                var fileName = ""
                if (extractedFile != null && extractedFile.isFile) {
                    videoLibrary.importFromFile(extractedFile).onSuccess {
                        fileName = it
                        filesCopied++
                    }
                }
                val video = BibleUserVideo(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    tags = tags,
                    fileName = fileName,
                    source = src,
                    sourceUrl = url,
                )
                newVideos += video
                newIds += video.id
            } else {
                val reused = existingAudios.firstOrNull { a ->
                    !url.isNullOrBlank() && a.sourceUrl == url
                }
                if (reused != null) {
                    newIds += reused.id
                    continue
                }
                var fileName = ""
                if (extractedFile != null && extractedFile.isFile) {
                    audioLibrary.importFromFile(extractedFile).onSuccess {
                        fileName = it
                        filesCopied++
                    }
                }
                val audio = BibleUserAudio(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    tags = tags,
                    fileName = fileName,
                    source = src,
                    sourceUrl = url,
                )
                newAudios += audio
                newIds += audio.id
            }
        }
        if (newIds.isEmpty()) {
            return UserMediaPlaylistShareOutcome.Err(UserMediaPlaylistShareError.EMPTY)
        }
        return UserMediaPlaylistShareOutcome.Ok(
            playlist = UserMediaPlaylist(name = name, kind = kind, itemIds = newIds),
            videos = newVideos,
            audios = newAudios,
            filesCopied = filesCopied,
            linksOnly = !anyFileRef || filesCopied == 0,
        )
    }

    private fun zipMediaName(index: Int, storedName: String): String {
        val ext = storedName.substringAfterLast('.', "").lowercase().ifBlank { "bin" }
        return "media/item_${index}.$ext"
    }

    private fun safeFilePart(name: String): String {
        val t = name.replace(Regex("[^\\p{L}\\p{N}_.-]+"), "_").trim('_')
        return t.take(40).ifBlank { "playlist" }
    }
}
