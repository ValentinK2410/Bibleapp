package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Переопределение озвучки отправителя SMS по номеру (цифровые шаблоны, как в сценариях). */
data class SmsSpeechOverrideEntry(
    val id: String = UUID.randomUUID().toString(),
    /** Подпись в списке (не озвучивается). */
    val title: String,
    /** Фрагменты номера только из цифр; достаточно совпадения одного шаблона. */
    val senderDigitPatterns: List<String>,
    /** Текст, который произнесёт TTS вместо имени контакта и номера. */
    val utterance: String,
)

private object SmsSpeechOverrideJsonKeys {
    const val ENTRIES = "entries"
    const val ID = "id"
    const val TITLE = "title"
    const val SENDERS = "senders"
    const val UTTERANCE = "utterance"
}

object SmsSpeechOverrideJson {
    fun entriesToJson(list: List<SmsSpeechOverrideEntry>): String =
        JSONObject().apply {
            put(SmsSpeechOverrideJsonKeys.ENTRIES, JSONArray().apply {
                for (e in list) put(entryToJson(e))
            })
        }.toString()

    fun parseEntries(json: String): List<SmsSpeechOverrideEntry> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(json)
            val arr = root.optJSONArray(SmsSpeechOverrideJsonKeys.ENTRIES) ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    entryFromJson(o)?.let { add(it) }
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun entryToJson(e: SmsSpeechOverrideEntry): JSONObject =
        JSONObject().apply {
            put(SmsSpeechOverrideJsonKeys.ID, e.id)
            put(SmsSpeechOverrideJsonKeys.TITLE, e.title)
            put(SmsSpeechOverrideJsonKeys.SENDERS, JSONArray(e.senderDigitPatterns))
            put(SmsSpeechOverrideJsonKeys.UTTERANCE, e.utterance)
        }

    private fun entryFromJson(o: JSONObject): SmsSpeechOverrideEntry? {
        val id = o.optString(SmsSpeechOverrideJsonKeys.ID).trim().ifBlank { UUID.randomUUID().toString() }
        val title =
            o.optString(SmsSpeechOverrideJsonKeys.TITLE).trim().ifBlank { "Отправитель" }
        val senders = o.optJSONArray(SmsSpeechOverrideJsonKeys.SENDERS)?.toStringList().orEmpty()
        val utterance = o.optString(SmsSpeechOverrideJsonKeys.UTTERANCE, "").trim().ifBlank { return null }
        return SmsSpeechOverrideEntry(id, title, senders, utterance)
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) {
            val s = optString(i).trim()
            if (s.isNotEmpty()) add(s)
        }
    }
}

/** Первое совпадение по порядку записей в списке. */
fun List<SmsSpeechOverrideEntry>.speechUtteranceForDigits(originatingDigits: String): String? {
    if (originatingDigits.isEmpty()) return null
    for (entry in this) {
        val patterns =
            entry.senderDigitPatterns.map { it.normalizeSmsDigits() }.filter { it.isNotEmpty() }
        if (patterns.isEmpty()) continue
        val hit = patterns.any { p ->
            originatingDigits.endsWith(p) || originatingDigits.contains(p)
        }
        if (hit) return entry.utterance.trim().takeIf { it.isNotEmpty() }
    }
    return null
}
