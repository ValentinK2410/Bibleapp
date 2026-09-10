package com.example.bible.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TimemarkGithubItem(
    val id: String,
    val translationCode: String,
    val bookId: String,
    val chapter: Int,
    val title: String,
    val cueCount: Int,
    val narratorId: String,
    val file: String,
)

data class TimemarkGithubIndex(
    val updatedAt: String,
    val items: List<TimemarkGithubItem>,
)

sealed class TimemarkGithubDownloadResult {
    data class Ok(val imported: Int, val skipped: Int) : TimemarkGithubDownloadResult()
    data class Err(val message: String) : TimemarkGithubDownloadResult()
}

/**
 * Каталог таймкодов на GitHub: лёгкие JSON без аудио.
 * Аудио глава берётся из уже скачанной озвучки на телефоне.
 */
object TimemarkGithubCatalog {

    const val FORMAT = "bible_timemark_catalog"
    private const val TAG = "TimemarkGithub"
    private const val VERSION = 1

    val CATALOG_URLS = listOf(
        "https://raw.githubusercontent.com/ValentinK2410/Bibleapp/master/docs/timemarks/catalog.json",
        "https://github.com/ValentinK2410/Bibleapp/raw/master/docs/timemarks/catalog.json",
        "https://cdn.jsdelivr.net/gh/ValentinK2410/Bibleapp@master/docs/timemarks/catalog.json",
    )

    fun projectId(translationCode: String, bookId: String, chapter: Int): String =
        "gh_${translationCode.lowercase()}_${bookId.lowercase()}_$chapter"

    fun projectFileName(translationCode: String, bookId: String, chapter: Int): String =
        "${translationCode.lowercase()}_${bookId.lowercase()}_$chapter.json"

    fun itemJson(
        id: String,
        translationCode: String,
        bookId: String,
        chapter: Int,
        title: String,
        cueCount: Int,
        narratorId: String,
        file: String,
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("translationCode", translationCode)
        put("bookId", bookId)
        put("chapter", chapter)
        put("title", title)
        put("cueCount", cueCount)
        if (narratorId.isNotBlank()) put("narratorId", narratorId)
        put("file", file)
    }

    fun buildCatalogJson(items: JSONArray, updatedAt: String = todayUtc()): JSONObject =
        JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("updatedAt", updatedAt)
            put("items", items)
        }

    fun parseCatalog(raw: String): TimemarkGithubIndex {
        val o = JSONObject(raw)
        val fmt = o.optString("format", "")
        if (fmt.isNotBlank() && fmt != FORMAT) {
            throw IllegalArgumentException("Неизвестный каталог таймкодов")
        }
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                val bookId = it.optString("bookId", "").trim()
                val translation = it.optString("translationCode", "").trim()
                val chapter = it.optInt("chapter", 0)
                if (bookId.isBlank() || translation.isBlank() || chapter <= 0) continue
                val file = it.optString("file", "").trim().ifBlank {
                    "projects/${projectFileName(translation, bookId, chapter)}"
                }
                add(
                    TimemarkGithubItem(
                        id = it.optString("id").ifBlank { projectId(translation, bookId, chapter) },
                        translationCode = translation,
                        bookId = bookId,
                        chapter = chapter,
                        title = it.optString("title", ""),
                        cueCount = it.optInt("cueCount", 0),
                        narratorId = it.optString("narratorId", ""),
                        file = file,
                    ),
                )
            }
        }
        return TimemarkGithubIndex(
            updatedAt = o.optString("updatedAt", ""),
            items = items,
        )
    }

    fun fetchCatalog(): TimemarkGithubIndex {
        var lastError: Exception? = null
        for (url in CATALOG_URLS) {
            runCatching { parseCatalog(fetchText(url)) }
                .onSuccess { return it }
                .onFailure { lastError = it as? Exception ?: Exception(it) }
        }
        throw lastError ?: RuntimeException("Не удалось загрузить каталог таймкодов")
    }

    fun downloadItems(
        context: Context,
        items: List<TimemarkGithubItem>,
    ): TimemarkGithubDownloadResult {
        if (items.isEmpty()) {
            return TimemarkGithubDownloadResult.Ok(imported = 0, skipped = 0)
        }
        var imported = 0
        var skipped = 0
        for (item in items) {
            try {
                val raw = fetchFirst(projectUrls(item.file))
                val json = JSONObject(raw)
                val project = TimemarkSharePackage.importPortableProject(
                    context = context,
                    json = json,
                    projectId = item.id.ifBlank {
                        projectId(item.translationCode, item.bookId, item.chapter)
                    },
                )
                if (project == null) skipped++ else imported++
            } catch (e: Exception) {
                Log.e(TAG, "download ${item.file}", e)
                skipped++
            }
        }
        return if (imported == 0 && skipped > 0) {
            TimemarkGithubDownloadResult.Err("Не удалось скачать таймкоды. Проверьте интернет.")
        } else {
            TimemarkGithubDownloadResult.Ok(imported = imported, skipped = skipped)
        }
    }

    fun projectUrls(file: String): List<String> {
        val rel = file.trimStart('/').removePrefix("docs/timemarks/")
        return listOf(
            "https://raw.githubusercontent.com/ValentinK2410/Bibleapp/master/docs/timemarks/$rel",
            "https://github.com/ValentinK2410/Bibleapp/raw/master/docs/timemarks/$rel",
            "https://cdn.jsdelivr.net/gh/ValentinK2410/Bibleapp@master/docs/timemarks/$rel",
        )
    }

    fun isInstalled(context: Context, item: TimemarkGithubItem): Boolean {
        if (TimemarkStore.load(context, item.id) != null) return true
        return TimemarkStore.hasTimemarksForChapter(
            context,
            item.translationCode,
            item.bookId,
            item.chapter,
        )
    }

    private fun fetchFirst(urls: List<String>): String {
        var lastError: Exception? = null
        for (url in urls) {
            runCatching { fetchText(url) }
                .onSuccess { return it }
                .onFailure { lastError = it as? Exception ?: Exception(it) }
        }
        throw lastError ?: RuntimeException("Не удалось скачать файл")
    }

    private fun fetchText(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Bibleapp")
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code")
            }
            if (body.isBlank()) throw RuntimeException("Пустой ответ")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun todayUtc(): String {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        return "%04d-%02d-%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }
}
