package com.example.bible.data

import com.example.bible.ui.SearchSettings

/**
 * LRU-кэш результатов поиска по Библии в памяти — повторный запрос с теми же параметрами мгновенный.
 */
internal class BibleSearchResultsCache(
    private val maxEntries: Int = 64,
) {
    private data class Key(
        val translations: List<String>,
        val query: String,
        val limit: Int,
        val settings: SearchSettings,
    )

    private val map =
        object : LinkedHashMap<Key, List<SearchHit>>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, List<SearchHit>>?): Boolean =
                size > maxEntries
        }

    fun get(translations: List<TranslationId>, query: String, limit: Int, settings: SearchSettings): List<SearchHit>? {
        val key = Key(translations.map { it.code }.sorted(), query, limit, settings)
        synchronized(map) { return map[key] }
    }

    fun put(
        translations: List<TranslationId>,
        query: String,
        limit: Int,
        settings: SearchSettings,
        results: List<SearchHit>,
    ) {
        val key = Key(translations.map { it.code }.sorted(), query, limit, settings)
        synchronized(map) { map[key] = results }
    }

    fun clear() {
        synchronized(map) { map.clear() }
    }
}
