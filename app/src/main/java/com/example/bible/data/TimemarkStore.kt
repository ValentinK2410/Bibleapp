package com.example.bible.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Сохранение проектов таймкодов в каталоге timemark_projects (JSON-файлы).
 */
object TimemarkStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "timemark_projects").also { it.mkdirs() }

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
    }

    fun delete(context: Context, id: String) {
        File(dir(context), "$id.json").delete()
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
