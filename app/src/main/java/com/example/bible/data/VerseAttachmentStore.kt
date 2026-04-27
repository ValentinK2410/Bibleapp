package com.example.bible.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Хранит метаданные вложений в JSON и копии файлов в [Context.getFilesDir]/verse_attachments/.
 */
class VerseAttachmentStore private constructor(private val context: Context) {

    private val jsonFile = File(context.filesDir, "verse_attachments_index.json")
    private val rootDir = File(context.filesDir, "verse_attachments").apply { mkdirs() }

    private val _changeVersion = MutableStateFlow(0)
    /** Увеличивается при добавлении/удалении вложения — для обновления индикаторов в читалке. */
    val attachmentIndexVersion: StateFlow<Int> = _changeVersion.asStateFlow()

    private fun bumpVersion() {
        _changeVersion.value++
    }

    companion object {
        @Volatile
        private var instance: VerseAttachmentStore? = null

        fun get(context: Context): VerseAttachmentStore =
            instance ?: synchronized(this) {
                instance ?: VerseAttachmentStore(context.applicationContext).also { instance = it }
            }

        /** После восстановления из резервной копии. */
        fun invalidateCache() {
            instance = null
        }
    }

    private fun safeKey(ref: VerseRef): String =
        ref.toKey().replace(Regex("[^a-zA-Z0-9_.\\-]"), "_")

    @Synchronized
    fun listFor(ref: VerseRef): List<VerseAttachment> {
        val map = loadMap()
        return map[safeKey(ref)]?.toList() ?: emptyList()
    }

    /**
     * Копирует файл из базы «Медиа → картинки» во вложения стиха.
     */
    @Synchronized
    fun addFromMediaLibrary(ref: VerseRef, image: BibleUserImage): VerseAttachment {
        val src = MediaCatalogPaths.pictureFile(context, image.fileName)
        if (!src.isFile) error("Файл не найден в базе")
        val mime = guessMimeImageFromFileName(src.name)
        val displayName = image.title.trim().ifBlank { image.fileName }
        return copyIntoVerseAttachments(ref, src, displayName, mime)
    }

    @Synchronized
    fun addFromVideoLibrary(ref: VerseRef, video: BibleUserVideo): VerseAttachment {
        val src = MediaCatalogPaths.videoFile(context, video.fileName)
        if (!src.isFile) error("Файл не найден в базе")
        val mime = guessMimeVideoFromFileName(src.name)
        val displayName = video.title.trim().ifBlank { video.fileName }
        return copyIntoVerseAttachments(ref, src, displayName, mime)
    }

    @Synchronized
    fun addFromAudioLibrary(ref: VerseRef, audio: BibleUserAudio): VerseAttachment {
        val src = MediaCatalogPaths.audioFile(context, audio.fileName)
        if (!src.isFile) error("Файл не найден в базе")
        val mime = guessMimeAudioFromFileName(src.name)
        val displayName = audio.title.trim().ifBlank { audio.fileName }
        return copyIntoVerseAttachments(ref, src, displayName, mime)
    }

    private fun copyIntoVerseAttachments(
        ref: VerseRef,
        src: File,
        displayName: String,
        mime: String,
    ): VerseAttachment {
        val id = UUID.randomUUID().toString()
        val ext = extensionFor(mime, src.name)
        val dir = File(rootDir, safeKey(ref)).apply { mkdirs() }
        val dest = File(dir, "$id.$ext")
        src.copyTo(dest, overwrite = true)
        val relative = dest.relativeToFilesDir()
        val att = VerseAttachment(
            id = id,
            displayName = displayName,
            mimeType = mime,
            relativePath = relative,
        )
        val map = loadMap()
        val list = map.getOrPut(safeKey(ref)) { mutableListOf() }
        list.add(att)
        saveMap(map)
        bumpVersion()
        return att
    }

    @Synchronized
    fun addFromUri(ref: VerseRef, uri: Uri): VerseAttachment {
        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val displayName = queryDisplayName(uri) ?: "file_${System.currentTimeMillis()}"
        val id = UUID.randomUUID().toString()
        val ext = extensionFor(mime, displayName)
        val dir = File(rootDir, safeKey(ref)).apply { mkdirs() }
        val dest = File(dir, "$id.$ext")
        cr.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Не удалось прочитать файл")
        val relative = dest.relativeToFilesDir()
        val att = VerseAttachment(
            id = id,
            displayName = displayName,
            mimeType = mime,
            relativePath = relative,
        )
        val map = loadMap()
        val list = map.getOrPut(safeKey(ref)) { mutableListOf() }
        list.add(att)
        saveMap(map)
        bumpVersion()
        return att
    }

    @Synchronized
    fun remove(ref: VerseRef, attachmentId: String) {
        val map = loadMap()
        val key = safeKey(ref)
        val list = map[key] ?: return
        val att = list.find { it.id == attachmentId } ?: return
        list.remove(att)
        if (list.isEmpty()) map.remove(key)
        File(context.filesDir, att.relativePath).delete()
        saveMap(map)
        bumpVersion()
    }

    private fun File.relativeToFilesDir(): String {
        val base = context.filesDir.absolutePath.let { if (it.endsWith("/")) it else "$it/" }
        val abs = absolutePath
        return if (abs.startsWith(base)) abs.removePrefix(base) else abs
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }

    private fun guessMimeImageFromFileName(name: String): String {
        val n = name.lowercase()
        return when {
            n.endsWith(".png") -> "image/png"
            n.endsWith(".webp") -> "image/webp"
            n.endsWith(".gif") -> "image/gif"
            n.endsWith(".jpg") || n.endsWith(".jpeg") -> "image/jpeg"
            else -> "image/jpeg"
        }
    }

    private fun guessMimeVideoFromFileName(name: String): String {
        val n = name.lowercase()
        return when {
            n.endsWith(".webm") -> "video/webm"
            n.endsWith(".ogv") -> "video/ogg"
            n.endsWith(".mov") -> "video/quicktime"
            n.endsWith(".mkv") -> "video/x-matroska"
            n.endsWith(".3gp") -> "video/3gpp"
            else -> "video/mp4"
        }
    }

    private fun guessMimeAudioFromFileName(name: String): String {
        val n = name.lowercase()
        return when {
            n.endsWith(".ogg") -> "audio/ogg"
            n.endsWith(".opus") -> "audio/opus"
            n.endsWith(".m4a") -> "audio/mp4"
            n.endsWith(".aac") -> "audio/aac"
            n.endsWith(".wav") -> "audio/wav"
            n.endsWith(".flac") -> "audio/flac"
            n.endsWith(".mp3") -> "audio/mpeg"
            else -> "audio/mpeg"
        }
    }

    private fun extensionFor(mime: String, displayName: String): String {
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { return it }
        val fromName = displayName.substringAfterLast('.', "")
        if (fromName.isNotBlank() && fromName.length <= 8) return fromName
        return "bin"
    }

    private fun loadMap(): MutableMap<String, MutableList<VerseAttachment>> {
        if (!jsonFile.exists()) return mutableMapOf()
        return try {
            val root = JSONObject(jsonFile.readText())
            val out = mutableMapOf<String, MutableList<VerseAttachment>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = root.getJSONArray(k)
                val list = mutableListOf<VerseAttachment>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        VerseAttachment(
                            id = o.getString("id"),
                            displayName = o.getString("displayName"),
                            mimeType = o.getString("mimeType"),
                            relativePath = o.getString("relativePath"),
                        ),
                    )
                }
                out[k] = list
            }
            out
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveMap(map: MutableMap<String, MutableList<VerseAttachment>>) {
        val root = JSONObject()
        for ((k, list) in map) {
            if (list.isEmpty()) continue
            val arr = JSONArray()
            for (a in list) {
                arr.put(
                    JSONObject().apply {
                        put("id", a.id)
                        put("displayName", a.displayName)
                        put("mimeType", a.mimeType)
                        put("relativePath", a.relativePath)
                    },
                )
            }
            root.put(k, arr)
        }
        jsonFile.writeText(root.toString())
    }
}

fun VerseAttachment.resolveFile(context: Context): File =
    File(context.filesDir, relativePath)
