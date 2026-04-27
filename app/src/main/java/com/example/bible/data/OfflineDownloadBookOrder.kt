package com.example.bible.data

import org.json.JSONArray

/**
 * Порядок обхода книг при массовой офлайн-загрузке материалов «Изучение».
 * Первые в списке — скачиваются в первую очередь.
 */
object OfflineDownloadBookOrder {

    fun defaultOrder(): List<String> = BibleCanon.allBooks.map { it.id }

    fun parseJson(json: String?): List<String>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Восстанавливает полный список из 66 id: сохранённый порядок + недостающие в конце канона.
     */
    fun normalize(saved: List<String>?): List<String> {
        val canonical = defaultOrder()
        val canonSet = canonical.toSet()
        if (saved.isNullOrEmpty()) return canonical
        val seen = mutableSetOf<String>()
        val out = mutableListOf<String>()
        for (id in saved) {
            if (id in canonSet && id !in seen) {
                out.add(id)
                seen.add(id)
            }
        }
        for (id in canonical) {
            if (id !in seen) out.add(id)
        }
        return out
    }

    fun presetNewTestamentFirst(): List<String> {
        val nt = BibleCanon.allBooks.filter { it.id in BibleCanon.newTestamentIds }
        val ot = BibleCanon.allBooks.filter { it.id in BibleCanon.oldTestamentIds }
        return nt.map { it.id } + ot.map { it.id }
    }

    fun presetOldTestamentFirst(): List<String> {
        val ot = BibleCanon.allBooks.filter { it.id in BibleCanon.oldTestamentIds }
        val nt = BibleCanon.allBooks.filter { it.id in BibleCanon.newTestamentIds }
        return ot.map { it.id } + nt.map { it.id }
    }

    fun presetGospelsFirst(): List<String> {
        val gospels = listOf("matthew", "mark", "luke", "john")
        val rest = BibleCanon.allBooks.map { it.id }.filter { it !in gospels.toSet() }
        return gospels + rest
    }
}
