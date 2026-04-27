package com.example.bible.data

import android.content.Context
import org.json.JSONObject

data class DictResult(
    val source: String,
    val word: String,
    val definition: String,
)

class DictionaryManager(private val context: Context) {

    private data class ExternalDict(
        val assetPath: String,
        val label: String,
        var data: Map<String, Pair<String, String>>? = null,
    )

    private val externalDicts = listOf(
        ExternalDict("dictionaries/brockhaus.json", "Брокгауз"),
        ExternalDict("dictionaries/vikhlyantsev.json", "Вихлянцев"),
        ExternalDict("dictionaries/nystrem.json", "Нюстрем"),
        ExternalDict("dictionaries/nikifor.json", "Никифор"),
    )

    private fun ensureLoaded(dict: ExternalDict) {
        if (dict.data != null) return
        try {
            val text = context.assets.open(dict.assetPath).bufferedReader().readText()
            val json = JSONObject(text)
            val map = mutableMapOf<String, Pair<String, String>>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = json.getJSONObject(key)
                val topic = obj.optString("t", key)
                val def = obj.optString("d", "")
                if (def.isNotBlank()) {
                    map[key] = topic to def
                }
            }
            dict.data = map
        } catch (_: Exception) {
            dict.data = emptyMap()
        }
    }

    fun searchAll(word: String): List<DictResult> {
        val normalized = word.trim().lowercase()
            .removeSuffix(".").removeSuffix(",").removeSuffix(";")
            .removeSuffix(":").removeSuffix("!").removeSuffix("?")
            .removeSuffix("»").removePrefix("«")

        if (normalized.isBlank()) return emptyList()

        val results = mutableListOf<DictResult>()

        // 1) Built-in BibleDictionary
        val builtIn = BibleDictionary.lookup(normalized)
        if (builtIn != null) {
            results.add(DictResult("Библейский словарь", builtIn.word, builtIn.definition))
        }

        // 2) External dictionaries
        for (dict in externalDicts) {
            ensureLoaded(dict)
            val data = dict.data ?: continue

            // Exact match
            val exact = data[normalized]
            if (exact != null) {
                results.add(DictResult(dict.label, exact.first, exact.second))
                continue
            }

            // Prefix match: word starts with key or key starts with word (3+ chars)
            if (normalized.length >= 3) {
                val found = data.entries.firstOrNull { (key, _) ->
                    key.startsWith(normalized) || normalized.startsWith(key)
                }
                if (found != null) {
                    results.add(DictResult(dict.label, found.value.first, found.value.second))
                }
            }
        }

        return results
    }

    companion object {
        @Volatile
        private var instance: DictionaryManager? = null

        fun getInstance(context: Context): DictionaryManager {
            return instance ?: synchronized(this) {
                instance ?: DictionaryManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
