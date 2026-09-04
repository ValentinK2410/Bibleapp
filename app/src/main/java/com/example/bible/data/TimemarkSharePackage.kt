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

private const val TAG = "TimemarkShare"

enum class TimemarkShareScope {
    CHAPTER,
    BOOK,
    ALL,
}

enum class TimemarkShareImportError {
    MISSING_MANIFEST,
    WRONG_FORMAT,
    FULL_APP_BACKUP,
    NO_PROJECTS,
    IO_OR_PARSE,
}

sealed class TimemarkShareImportOutcome {
    data class Ok(val imported: Int) : TimemarkShareImportOutcome()
    data class Err(val error: TimemarkShareImportError) : TimemarkShareImportOutcome()
}

/**
 * Обмен проектами таймкодов Библии (глава / книга / всё) в одном ZIP:
 * manifest + projects (JSON) + media/audio + media/attachments.
 */
object TimemarkSharePackage {

    const val FORMAT = "bible_timemark_share"
    private const val VERSION = 1
    private const val MANIFEST_NAME = "manifest.json"
    private const val PROJECTS_PREFIX = "projects/"
    private const val MEDIA_AUDIO_PREFIX = "media/audio/"
    private const val MEDIA_ATTACH_PREFIX = "media/attachments/"

    class NothingToExportException : Exception()

    fun projectsForScope(
        context: Context,
        scope: TimemarkShareScope,
        translationCode: String,
        bookId: String,
        chapter: Int,
    ): List<TimemarkProject> {
        val all = TimemarkStore.listAllProjects(context)
        return when (scope) {
            TimemarkShareScope.CHAPTER -> all.filter {
                it.translationCode == translationCode && it.bookId == bookId && it.chapter == chapter
            }
            TimemarkShareScope.BOOK -> all.filter { it.bookId == bookId }
            TimemarkShareScope.ALL -> all
        }.filter { it.cues.isNotEmpty() }
    }

    suspend fun exportToZip(
        context: Context,
        scope: TimemarkShareScope,
        translationCode: String,
        bookId: String,
        chapter: Int,
    ): File = withContext(Dispatchers.IO) {
        val projects = projectsForScope(context, scope, translationCode, bookId, chapter)
        if (projects.isEmpty()) throw NothingToExportException()

        val zipFile = File(
            context.cacheDir,
            when (scope) {
                TimemarkShareScope.CHAPTER -> "timemark_${bookId}_${chapter}_${System.currentTimeMillis()}.zip"
                TimemarkShareScope.BOOK -> "timemark_${bookId}_${System.currentTimeMillis()}.zip"
                TimemarkShareScope.ALL -> "timemark_all_${System.currentTimeMillis()}.zip"
            },
        )

        val manifest = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("scope", scope.name.lowercase())
            put("count", projects.size)
            if (scope == TimemarkShareScope.CHAPTER) {
                put("translationCode", translationCode)
                put("bookId", bookId)
                put("chapter", chapter)
            } else if (scope == TimemarkShareScope.BOOK) {
                put("bookId", bookId)
            }
        }

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry(MANIFEST_NAME))
            zos.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            projects.forEachIndexed { index, project ->
                val portable = portableProjectJson(project)
                val entryName = "${PROJECTS_PREFIX}${index}.json"
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(portable.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                copyAudioToZip(zos, project)
                copyAttachmentsToZip(zos, project)
            }
        }
        zipFile
    }

    suspend fun importFromZip(context: Context, zipFile: File): TimemarkShareImportOutcome =
        withContext(Dispatchers.IO) {
            try {
                var manifest: JSONObject? = null
                val projectJsons = mutableListOf<JSONObject>()
                val extractedMedia = mutableMapOf<String, File>()
                val workDir = File(context.cacheDir, "timemark_import_${System.currentTimeMillis()}").apply { mkdirs() }

                try {
                    ZipFile(zipFile).use { zf ->
                        for (entry in zf.entries().asSequence()) {
                            if (entry.isDirectory) continue
                            val name = entry.name.replace('\\', '/').trimStart('/')
                            when {
                                name.equals(MANIFEST_NAME, ignoreCase = true) -> {
                                    manifest = JSONObject(
                                        zf.getInputStream(entry).readBytes().toString(Charsets.UTF_8),
                                    )
                                }
                                name.startsWith(PROJECTS_PREFIX) && name.endsWith(".json", ignoreCase = true) -> {
                                    projectJsons.add(
                                        JSONObject(
                                            zf.getInputStream(entry).readBytes().toString(Charsets.UTF_8),
                                        ),
                                    )
                                }
                                name.startsWith(MEDIA_AUDIO_PREFIX) || name.startsWith(MEDIA_ATTACH_PREFIX) -> {
                                    val out = File(workDir, name.replace('/', '_'))
                                    out.parentFile?.mkdirs()
                                    zf.getInputStream(entry).use { ins ->
                                        FileOutputStream(out).use { os -> ins.copyTo(os) }
                                    }
                                    extractedMedia[name.lowercase()] = out
                                }
                            }
                        }
                    }

                    val man = manifest ?: return@withContext TimemarkShareImportOutcome.Err(
                        TimemarkShareImportError.MISSING_MANIFEST,
                    )
                    val fmt = man.optString("format", "")
                    if (fmt == "bible_app_export") {
                        return@withContext TimemarkShareImportOutcome.Err(TimemarkShareImportError.FULL_APP_BACKUP)
                    }
                    if (fmt != FORMAT) {
                        return@withContext TimemarkShareImportOutcome.Err(TimemarkShareImportError.WRONG_FORMAT)
                    }
                    if (projectJsons.isEmpty()) {
                        return@withContext TimemarkShareImportOutcome.Err(TimemarkShareImportError.NO_PROJECTS)
                    }

                    val audioDir = File(context.filesDir, "timemark_audio").apply { mkdirs() }
                    val attachDir = File(context.filesDir, "timemark_attachments").apply { mkdirs() }
                    var imported = 0
                    for (pj in projectJsons.sortedBy { it.optString("title", "") }) {
                        val project = portableJsonToProject(context, pj, extractedMedia, audioDir, attachDir)
                            ?: continue
                        TimemarkStore.save(context, project)
                        imported++
                    }
                    if (imported == 0) {
                        TimemarkShareImportOutcome.Err(TimemarkShareImportError.NO_PROJECTS)
                    } else {
                        TimemarkShareImportOutcome.Ok(imported)
                    }
                } finally {
                    workDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Log.e(TAG, "import failed", e)
                TimemarkShareImportOutcome.Err(TimemarkShareImportError.IO_OR_PARSE)
            }
        }

    private fun portableProjectJson(project: TimemarkProject): JSONObject {
        val audioName = File(project.audioFilePath).name.takeIf { it.isNotBlank() } ?: ""
        val narratorId = detectNarratorIdFromAudioPath(project.audioFilePath)
        return JSONObject().apply {
            put("translationCode", project.translationCode)
            put("bookId", project.bookId)
            put("chapter", project.chapter)
            put("title", project.title)
            if (audioName.isNotBlank()) put("audioFileName", audioName)
            if (narratorId != null) put("narratorId", narratorId)
            put(
                "cues",
                JSONArray().apply {
                    project.cues.forEach { cue ->
                        put(
                            JSONObject().apply {
                                put("timeMs", cue.timeMs)
                                put("verseStart", cue.verseStart)
                                cue.verseEnd?.let { put("verseEnd", it) }
                                cue.note?.takeIf { it.isNotBlank() }?.let { put("note", it) }
                                if (cue.attachments.isNotEmpty()) {
                                    put(
                                        "attachments",
                                        JSONArray().apply {
                                            cue.attachments.forEach { att ->
                                                put(
                                                    JSONObject().apply {
                                                        put("kind", att.kind)
                                                        att.text?.takeIf { it.isNotBlank() }?.let { put("text", it) }
                                                        att.path?.takeIf { it.isNotBlank() }?.let { p ->
                                                            put("mediaPath", attachmentZipPath(p))
                                                        }
                                                    },
                                                )
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    private fun attachmentZipPath(localPath: String): String {
        val name = File(localPath).name
        return "$MEDIA_ATTACH_PREFIX$name"
    }

    private fun copyAudioToZip(zos: ZipOutputStream, project: TimemarkProject) {
        val src = File(project.audioFilePath)
        if (!src.isFile) return
        val entry = "$MEDIA_AUDIO_PREFIX${src.name}"
        zos.putNextEntry(ZipEntry(entry))
        FileInputStream(src).use { it.copyTo(zos) }
        zos.closeEntry()
    }

    private fun copyAttachmentsToZip(zos: ZipOutputStream, project: TimemarkProject) {
        val copied = mutableSetOf<String>()
        for (cue in project.cues) {
            for (att in cue.attachments) {
                val path = att.path ?: continue
                if (att.kind != "image") continue
                val src = File(path)
                if (!src.isFile) continue
                val entry = attachmentZipPath(path)
                if (!copied.add(entry.lowercase())) continue
                zos.putNextEntry(ZipEntry(entry))
                FileInputStream(src).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun portableJsonToProject(
        context: Context,
        json: JSONObject,
        extractedMedia: Map<String, File>,
        audioDir: File,
        attachDir: File,
    ): TimemarkProject? {
        val cuesJson = json.optJSONArray("cues") ?: JSONArray()
        if (cuesJson.length() == 0) return null

        val cues = buildList {
            for (i in 0 until cuesJson.length()) {
                val co = cuesJson.getJSONObject(i)
                val attArr = co.optJSONArray("attachments") ?: JSONArray()
                val atts = buildList {
                    for (j in 0 until attArr.length()) {
                        val ao = attArr.getJSONObject(j)
                        val kind = ao.optString("kind", "text")
                        val text = ao.optString("text", "").takeIf { it.isNotBlank() }
                        val mediaPath = ao.optString("mediaPath", "").takeIf { it.isNotBlank() }
                        val localPath = mediaPath?.let { resolveImportedAttachment(it, extractedMedia, attachDir) }
                        add(TimemarkAttachment(kind = kind, path = localPath, text = text))
                    }
                }
                add(
                    TimemarkCue(
                        timeMs = co.getLong("timeMs"),
                        verseStart = co.getInt("verseStart"),
                        verseEnd = if (co.has("verseEnd")) co.getInt("verseEnd") else null,
                        note = co.optString("note", "").takeIf { it.isNotBlank() },
                        attachments = atts,
                    ),
                )
            }
        }

        val audioFileName = json.optString("audioFileName", "").trim()
        val bookId = json.getString("bookId")
        val chapter = json.getInt("chapter")
        val translationCode = json.getString("translationCode")
        val audioPath = resolveImportedAudio(
            audioFileName = audioFileName,
            extractedMedia = extractedMedia,
            audioDir = audioDir,
            context = context,
            bookId = bookId,
            chapter = chapter,
            translationCode = translationCode,
            narratorId = json.optString("narratorId", "").trim(),
        )

        return TimemarkProject(
            id = UUID.randomUUID().toString(),
            translationCode = translationCode,
            bookId = bookId,
            chapter = chapter,
            title = json.optString("title", ""),
            audioFilePath = audioPath,
            cues = cues,
        )
    }

    private fun detectNarratorIdFromAudioPath(path: String): String? {
        if (path.isBlank()) return null
        val parts = path.replace('\\', '/').split('/').filter { it.isNotBlank() }
        val idx = parts.indexOfLast { it.equals("bible_audio", ignoreCase = true) }
        if (idx >= 0 && idx + 1 < parts.size) return parts[idx + 1]
        return null
    }

    private fun resolveImportedAudio(
        audioFileName: String,
        extractedMedia: Map<String, File>,
        audioDir: File,
        context: Context,
        bookId: String,
        chapter: Int,
        translationCode: String,
        narratorId: String,
    ): String {
        val srcFile = when {
            audioFileName.isBlank() -> null
            else -> {
                val key = "${MEDIA_AUDIO_PREFIX}${audioFileName}".lowercase()
                extractedMedia[key]?.let { src ->
                    val dest = File(audioDir, audioFileName)
                    if (!dest.exists() || dest.length() != src.length()) {
                        src.copyTo(dest, overwrite = true)
                    }
                    dest
                } ?: File(audioDir, audioFileName).takeIf { it.isFile }
            }
        }

        val narratorIds = buildList {
            if (narratorId.isNotBlank()) add(narratorId)
            val fromTranslation = runCatching {
                narratorForTranslation(
                    TranslationId.fromCode(translationCode),
                    narratorId.ifBlank { "bondarenko" },
                ).id
            }.getOrNull()
            if (fromTranslation != null && fromTranslation !in this) add(fromTranslation)
        }

        if (srcFile != null && srcFile.isFile) {
            for (nid in narratorIds) {
                val chapterAudio = localAudioFile(context, nid, bookId, chapter)
                if (chapterAudio.name.equals(srcFile.name, ignoreCase = true)) {
                    chapterAudio.parentFile?.mkdirs()
                    srcFile.copyTo(chapterAudio, overwrite = true)
                    return chapterAudio.absolutePath
                }
            }
            return srcFile.absolutePath
        }

        for (nid in narratorIds) {
            val chapterAudio = localAudioFile(context, nid, bookId, chapter)
            if (chapterAudio.isFile) return chapterAudio.absolutePath
        }
        return ""
    }

    private fun resolveImportedAttachment(
        mediaPath: String,
        extractedMedia: Map<String, File>,
        attachDir: File,
    ): String? {
        val key = mediaPath.trimStart('/').lowercase()
        val src = extractedMedia[key] ?: return null
        val dest = File(attachDir, File(mediaPath).name)
        if (!dest.exists() || dest.length() != src.length()) {
            src.copyTo(dest, overwrite = true)
        }
        return dest.absolutePath
    }
}
