package com.example.bible.data.languagestudy

import com.example.bible.data.db.LangVocabWordEntity
import org.json.JSONObject

data class LangPackManifest(
    val lang: String,
    val packVersion: String,
    val schema: Int = 1,
) {
    companion object {
        fun parse(json: JSONObject): LangPackManifest = LangPackManifest(
            lang = json.getString("lang").trim(),
            packVersion = json.getString("version").trim(),
            schema = json.optInt("schema", 1),
        )
    }
}

private fun JSONObject.optTrimmedNullable(key: String): String? =
    optString(key, "").trim().ifBlank { null }

fun JSONObject.toLangPackWordDto(): Pair<String?, LangPackWordFields?> {
    val id = optString("id", "").trim()
    val lemma = optString("lemma", "").trim()
    val glossRu = optString("glossRu", "").trim()
    if (id.isEmpty() || lemma.isEmpty() || glossRu.isEmpty()) return Pair(null, null)
    return Pair(
        id,
        LangPackWordFields(
            lemma = lemma,
            display = optTrimmedNullable("display") ?: lemma,
            glossRu = glossRu,
            ipa = optTrimmedNullable("ipa"),
            pos = optTrimmedNullable("pos"),
            frequencyRank = if (has("frequencyRank")) optInt("frequencyRank") else null,
            exampleL2 = optTrimmedNullable("exampleL2"),
            exampleRu = optTrimmedNullable("exampleRu"),
            mnemonicHint = optTrimmedNullable("mnemonicHint"),
            morphologyNotes = optTrimmedNullable("morphologyNotes"),
        ),
    )
}

data class LangPackWordFields(
    val lemma: String,
    val display: String,
    val glossRu: String,
    val ipa: String?,
    val pos: String?,
    val frequencyRank: Int?,
    val exampleL2: String?,
    val exampleRu: String?,
    val mnemonicHint: String?,
    val morphologyNotes: String?,
) {
    fun toWordEntity(lang: String, stableId: String, packVersion: String): LangVocabWordEntity =
        LangVocabWordEntity(
            wordKey = LangPackImporter.wordKey(lang, stableId),
            langCode = lang,
            sourceStableId = stableId,
            lemma = lemma,
            display = display,
            glossRu = glossRu,
            ipa = ipa,
            pos = pos,
            frequencyRank = frequencyRank,
            exampleL2 = exampleL2,
            exampleRu = exampleRu,
            mnemonicHint = mnemonicHint,
            morphologyNotes = morphologyNotes,
            packVersion = packVersion,
        )
}
