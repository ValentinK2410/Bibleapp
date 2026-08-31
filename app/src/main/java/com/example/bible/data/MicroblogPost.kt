package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MicroblogSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val fontSize: Int = 16,
    val colorArgb: Int = 0,
    val bgColorArgb: Int = 0,
    val linkUrl: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("s", start)
        put("e", end)
        if (bold) put("b", true)
        if (italic) put("i", true)
        if (underline) put("u", true)
        if (strikethrough) put("x", true)
        if (fontSize != 16) put("fs", fontSize)
        if (colorArgb != 0) put("c", colorArgb)
        if (bgColorArgb != 0) put("bg", bgColorArgb)
        if (!linkUrl.isNullOrBlank()) put("url", linkUrl)
    }

    companion object {
        fun fromJson(j: JSONObject): MicroblogSpan = MicroblogSpan(
            start = j.getInt("s"),
            end = j.getInt("e"),
            bold = j.optBoolean("b", false),
            italic = j.optBoolean("i", false),
            underline = j.optBoolean("u", false),
            strikethrough = j.optBoolean("x", false),
            fontSize = j.optInt("fs", 16),
            colorArgb = j.optInt("c", 0),
            bgColorArgb = j.optInt("bg", 0),
            linkUrl = j.optString("url").trim().takeIf { it.isNotEmpty() },
        )
    }
}

data class MicroblogPost(
    val id: String = UUID.randomUUID().toString(),
    val body: String = "",
    val spans: List<MicroblogSpan> = emptyList(),
    val imageFileNames: List<String> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    fun previewText(maxChars: Int = 140): String {
        val one = body.trim().replace(Regex("\\s+"), " ")
        if (one.isEmpty()) {
            return if (imageFileNames.isNotEmpty()) "Изображение" else "Пустой пост"
        }
        return if (one.length <= maxChars) one else one.take(maxChars - 1) + "…"
    }
}

fun spansToJson(spans: List<MicroblogSpan>): String {
    val arr = JSONArray()
    spans.forEach { arr.put(it.toJson()) }
    return arr.toString()
}

fun spansFromJson(raw: String): List<MicroblogSpan> {
    if (raw.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { MicroblogSpan.fromJson(arr.getJSONObject(it)) }
    } catch (_: Exception) {
        emptyList()
    }
}

fun imageNamesToJson(names: List<String>): String {
    val arr = JSONArray()
    names.forEach { arr.put(it) }
    return arr.toString()
}

fun imageNamesFromJson(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getString(it).trim() }.filter { it.isNotEmpty() }
    } catch (_: Exception) {
        emptyList()
    }
}
