package com.example.bible.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Употребления номера Стронга в подстрочнике Винокурова (PODSTR), из [strongs_concordance_podstr.json].
 * При генерации индекса на один номер ограничено число вхождений (см. скрипт).
 */
data class StrongVerseRef(
    val bookId: String,
    val chapter: Int,
    val verse: Int,
)

class StrongsConcordance(private val context: Context) {

    private var root: JSONObject? = null

    private fun ensureLoadedSync() {
        if (root != null) return
        try {
            val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            root = JSONObject(text)
        } catch (_: Exception) {
            root = JSONObject()
        }
    }

    /**
     * Синхронная загрузка (вызывать с [Dispatchers.IO]).
     */
    fun occurrencesSync(strongCode: String?): List<StrongVerseRef> {
        if (strongCode.isNullOrBlank()) return emptyList()
        ensureLoadedSync()
        val key = StrongsDictionary.normalizeStrongCode(strongCode)
        val arr = root?.optJSONArray(key) ?: return emptyList()
        val out = ArrayList<StrongVerseRef>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                StrongVerseRef(
                    bookId = o.optString("b", ""),
                    chapter = o.optInt("c", 0),
                    verse = o.optInt("v", 0),
                ),
            )
        }
        return out
    }

    companion object {
        private const val ASSET_NAME = "strongs_concordance_podstr.json"

        suspend fun loadOccurrences(context: Context, strongCode: String?): List<StrongVerseRef> =
            withContext(Dispatchers.IO) {
                StrongsConcordance(context.applicationContext).occurrencesSync(strongCode)
            }
    }
}
