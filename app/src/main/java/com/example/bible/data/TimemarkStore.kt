package com.example.bible.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Сохранение проектов таймкодов в каталоге timemark_projects (JSON-файлы).
 */
object TimemarkStore {

    private data class PresenceCache(
        val epoch: Int,
        val dirPath: String,
        val index: TimemarkPresenceIndex,
    )

    private val presenceEpoch = AtomicInteger(0)

    @Volatile
    private var presenceCache: PresenceCache? = null

    private fun dir(context: Context): File =
        File(context.filesDir, "timemark_projects").also { it.mkdirs() }

    private fun invalidatePresenceCache() {
        presenceEpoch.incrementAndGet()
    }

    /**
     * Копирует готовые таймкоды из assets.
     * Уже лежащий на телефоне файл заменяется, только если в комплекте метка новее.
     * Правка пользователя новее комплекта и поэтому сохраняется.
     */
    fun importBundled(context: Context) {
        val names = context.assets.list("timemark_projects") ?: return
        val out = dir(context)
        var changed = false
        for (name in names) {
            if (!name.endsWith(".json")) continue
            val dest = File(out, name)
            context.assets.open("timemark_projects/$name").use { input ->
                val bytes = input.readBytes()
                val assetJson = bytes.toString(Charsets.UTF_8)
                val existing = if (dest.isFile) runCatching { dest.readText() }.getOrNull() else null
                if (!shouldReplaceBundled(existing, assetJson)) return@use
                dest.writeBytes(bytes)
                changed = true
            }
        }
        if (changed) invalidatePresenceCache()
    }

    /** Комплектный JSON заменяет местный, если его updatedAt новее или местный файл не читается. */
    fun shouldReplaceBundled(existingJson: String?, assetJson: String): Boolean {
        if (existingJson.isNullOrBlank()) return true
        val assetUpdated = updatedAtOrNull(assetJson) ?: return existingJson != assetJson
        val existingUpdated = updatedAtOrNull(existingJson) ?: return true
        return assetUpdated > existingUpdated
    }

    private fun updatedAtOrNull(json: String): Long? = runCatching {
        val obj = JSONObject(json)
        if (!obj.has("updatedAt")) null else obj.getLong("updatedAt")
    }.getOrNull()

    fun listProjectFiles(context: Context): List<File> =
        dir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun load(context: Context, id: String): TimemarkProject? {
        val f = File(dir(context), "$id.json")
        if (!f.exists()) return null
        return runCatching { parseJson(f.readText()) }.getOrNull()
    }

    fun save(context: Context, project: TimemarkProject) {
        val f = File(dir(context), "${project.id}.json")
        f.writeText(toJson(project.copy(updatedAt = System.currentTimeMillis())))
        invalidatePresenceCache()
    }

    fun delete(context: Context, id: String) {
        File(dir(context), "$id.json").delete()
        invalidatePresenceCache()
    }

    /** Удаляет все сохранённые проекты таймкодов для данной книги (любой перевод, любая глава). */
    fun deleteAllProjectsForBook(context: Context, bookId: String): Int {
        var n = 0
        for (f in listProjectFiles(context).toList()) {
            val p = load(context, f.nameWithoutExtension) ?: continue
            if (p.bookId == bookId) {
                delete(context, p.id)
                n++
            }
        }
        return n
    }

    /** Удаляет все проекты для главы (перевод + книга + номер главы). */
    fun deleteAllProjectsForChapter(
        context: Context,
        translationCode: String,
        bookId: String,
        chapter: Int,
    ): Int {
        var n = 0
        for (f in listProjectFiles(context).toList()) {
            val p = load(context, f.nameWithoutExtension) ?: continue
            if (p.translationCode == translationCode && p.bookId == bookId && p.chapter == chapter) {
                delete(context, p.id)
                n++
            }
        }
        return n
    }

    /**
     * Все проекты таймкодов для главы и перевода (новые сверху).
     */
    fun listProjectsForChapter(
        context: Context,
        translationCode: String,
        bookId: String,
        chapter: Int,
    ): List<TimemarkProject> {
        val out = mutableListOf<TimemarkProject>()
        for (f in listProjectFiles(context)) {
            val p = load(context, f.nameWithoutExtension) ?: continue
            if (p.translationCode == translationCode && p.bookId == bookId && p.chapter == chapter) {
                out.add(p)
            }
        }
        return out.sortedByDescending { it.updatedAt }
    }

    fun listAllProjects(context: Context): List<TimemarkProject> =
        listProjectFiles(context).mapNotNull { f ->
            load(context, f.nameWithoutExtension)
        }.sortedByDescending { it.updatedAt }

    fun listProjectsForBook(context: Context, bookId: String): List<TimemarkProject> =
        listAllProjects(context).filter { it.bookId == bookId }

    /** Есть ли сохранённые метки (непустые cues) для главы и перевода. */
    fun hasTimemarksForChapter(
        context: Context,
        translationCode: String,
        bookId: String,
        chapter: Int,
    ): Boolean = listProjectsForChapter(context, translationCode, bookId, chapter)
        .any { it.cues.isNotEmpty() }

    /** Номера глав книги, для которых есть метки в данном переводе. */
    fun chaptersWithTimemarksForBook(
        context: Context,
        translationCode: String,
        bookId: String,
    ): Set<Int> = listProjectsForBook(context, bookId)
        .asSequence()
        .filter { it.translationCode == translationCode && it.cues.isNotEmpty() }
        .map { it.chapter }
        .toSet()

    /** Id книг, у которых есть хотя бы одна глава с метками в данном переводе. */
    fun booksWithTimemarks(
        context: Context,
        translationCode: String,
    ): Set<String> = listAllProjects(context)
        .asSequence()
        .filter { it.translationCode == translationCode && it.cues.isNotEmpty() }
        .map { it.bookId }
        .toSet()

    /**
     * Книги и главы с непустыми таймкодами сразу по всем переводам.
     * Открытый перевод не должен скрывать чужие метки.
     * Последний посчитанный индекс можно взять сразу, не читая файлы.
     */
    fun peekPresenceIndex(context: Context): TimemarkPresenceIndex {
        val cached = presenceCache ?: return TimemarkPresenceIndex.Empty
        if (cached.dirPath != dir(context).absolutePath) return TimemarkPresenceIndex.Empty
        return cached.index
    }

    /**
     * Индекс из памяти, если проекты с прошлого построения не сохраняли, не удаляли и не импортировали.
     * Иначе читает каталог заново.
     */
    fun presenceIndex(context: Context): TimemarkPresenceIndex {
        val dirPath = dir(context).absolutePath
        val epoch = presenceEpoch.get()
        val cached = presenceCache
        if (cached != null && cached.epoch == epoch && cached.dirPath == dirPath) {
            return cached.index
        }
        return reloadPresenceIndex(context)
    }

    /** Всегда перечитывает каталог. Экран вызывает это в фоне при возврате на передний план. */
    fun reloadPresenceIndex(context: Context): TimemarkPresenceIndex {
        val dirPath = dir(context).absolutePath
        val epoch = presenceEpoch.get()
        val index = buildPresenceIndex(context)
        if (presenceEpoch.get() == epoch) {
            presenceCache = PresenceCache(epoch, dirPath, index)
        }
        return index
    }

    private fun buildPresenceIndex(context: Context): TimemarkPresenceIndex {
        val byBook = mutableMapOf<String, MutableSet<String>>()
        val byChapter = mutableMapOf<String, MutableMap<Int, MutableSet<String>>>()
        for (file in listProjectFiles(context)) {
            val header = readPresenceHeader(file) ?: continue
            if (!header.hasCues) continue
            byBook.getOrPut(header.bookId) { mutableSetOf() }.add(header.translationCode)
            byChapter.getOrPut(header.bookId) { mutableMapOf() }
                .getOrPut(header.chapter) { mutableSetOf() }
                .add(header.translationCode)
        }
        return TimemarkPresenceIndex(
            translationsByBook = byBook.mapValues { it.value.toSet() },
            translationsByChapter = byChapter.mapValues { bookMap ->
                bookMap.value.mapValues { it.value.toSet() }
            },
        )
    }

    private fun readPresenceHeader(file: File): TimemarkPresenceHeader? =
        runCatching {
            file.bufferedReader(Charsets.UTF_8).use { reader -> scanTimemarkPresence(reader) }
        }.getOrNull()

    /**
     * Один проект (самый свежий), если нужна обратная совместимость.
     */
    fun findProjectForChapter(
        context: Context,
        translationCode: String,
        bookId: String,
        chapter: Int,
    ): TimemarkProject? =
        listProjectsForChapter(context, translationCode, bookId, chapter).firstOrNull()

    /**
     * Проект таймкодов для главы, если [TimemarkProject.audioFilePath] указывает на ту же
     * озвучку, что и [localAudioFile] для выбранного диктора.
     */
    fun findProjectMatchingNarration(
        context: Context,
        translationCode: String,
        bookId: String,
        chapter: Int,
        narratorId: String,
    ): TimemarkProject? {
        val candidates = listProjectsForChapter(context, translationCode, bookId, chapter)
            .filter { it.cues.isNotEmpty() }
        if (candidates.isEmpty()) return null

        candidates
            .filter { audioFileMatchesChapterNarration(context, narratorId, bookId, chapter, it.audioFilePath) }
            .maxByOrNull { it.updatedAt }
            ?.let { return it }

        // Импорт с другого телефона: один проект на главу или метки без «своего» файла озвучки.
        val expectedName = localAudioFile(context, narratorId, bookId, chapter).name
        val chapterCompatible = candidates.filter { project ->
            chapterNarrationCompatible(project, expectedName)
        }
        return chapterCompatible.maxByOrNull { it.updatedAt }
    }

    /** Метки можно накладывать на озвучку главы, даже если файл лежит не в bible_audio. */
    private fun chapterNarrationCompatible(project: TimemarkProject, expectedAudioName: String): Boolean {
        val path = project.audioFilePath
        if (path.isBlank()) return true
        val file = File(path)
        if (!file.isFile) return true
        return file.name.equals(expectedAudioName, ignoreCase = true)
    }

    fun audioFileMatchesChapterNarration(
        context: Context,
        narratorId: String,
        bookId: String,
        chapter: Int,
        projectAudioPath: String,
    ): Boolean {
        if (projectAudioPath.isBlank()) return false
        val projectFile = File(projectAudioPath)
        if (!projectFile.isFile) return false
        val expected = localAudioFile(context, narratorId, bookId, chapter)
        if (projectFile.name.equals(expected.name, ignoreCase = true)) return true
        return sameChapterAudioFile(projectFile, expected)
    }

    private fun sameChapterAudioFile(projectFile: File, expected: File): Boolean {
        if (projectFile.exists() && expected.exists()) {
            return try {
                projectFile.canonicalPath == expected.canonicalPath
            } catch (_: Exception) {
                projectFile.absolutePath == expected.absolutePath
            }
        }
        return projectFile.name == expected.name &&
            projectFile.parentFile?.name == expected.parentFile?.name
    }

    private fun toJson(p: TimemarkProject): String {
        val o = JSONObject()
        o.put("id", p.id)
        o.put("translationCode", p.translationCode)
        o.put("bookId", p.bookId)
        o.put("chapter", p.chapter)
        o.put("title", p.title)
        o.put("audioFilePath", p.audioFilePath)
        o.put("updatedAt", p.updatedAt)
        val arr = JSONArray()
        for (c in p.cues) {
            val co = JSONObject()
            co.put("timeMs", c.timeMs)
            co.put("verseStart", c.verseStart)
            if (c.verseEnd != null) co.put("verseEnd", c.verseEnd)
            if (!c.note.isNullOrBlank()) co.put("note", c.note)
            val attArr = JSONArray()
            for (a in c.attachments) {
                val ao = JSONObject()
                ao.put("kind", a.kind)
                a.path?.let { ao.put("path", it) }
                a.text?.let { ao.put("text", it) }
                attArr.put(ao)
            }
            co.put("attachments", attArr)
            arr.put(co)
        }
        o.put("cues", arr)
        return o.toString(2)
    }

    private fun parseJson(s: String): TimemarkProject {
        val o = JSONObject(s)
        val cuesJson = o.optJSONArray("cues") ?: JSONArray()
        val cues = buildList {
            for (i in 0 until cuesJson.length()) {
                val co = cuesJson.getJSONObject(i)
                val attArr = co.optJSONArray("attachments") ?: JSONArray()
                val atts = buildList {
                    for (j in 0 until attArr.length()) {
                        val ao = attArr.getJSONObject(j)
                        add(
                            TimemarkAttachment(
                                kind = ao.optString("kind", "text"),
                                path = ao.optString("path", "").takeIf { it.isNotEmpty() },
                                text = ao.optString("text", "").takeIf { it.isNotEmpty() },
                            ),
                        )
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
        return TimemarkProject(
            id = o.getString("id"),
            translationCode = o.getString("translationCode"),
            bookId = o.getString("bookId"),
            chapter = o.getInt("chapter"),
            title = o.optString("title", ""),
            audioFilePath = o.getString("audioFilePath"),
            cues = cues,
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        )
    }
}
