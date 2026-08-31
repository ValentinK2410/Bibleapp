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

/** Как текст поста ведёт себя рядом с картинкой. */
enum class MicroblogImageWrap {
    /** Картинка отдельным блоком, текст выше и ниже. */
    FULL,

    /** Картинка слева, текст обтекает справа. */
    LEFT,

    /** Картинка справа, текст обтекает слева. */
    RIGHT,
    ;

    val key: String get() = name.lowercase()

    companion object {
        fun fromKey(raw: String?): MicroblogImageWrap = when (raw?.trim()?.lowercase()) {
            "left" -> LEFT
            "right" -> RIGHT
            else -> FULL
        }
    }
}

data class MicroblogImage(
    val fileName: String,
    val displayScale: Float = 1f,
    val wrap: MicroblogImageWrap = MicroblogImageWrap.FULL,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("n", fileName)
        if (displayScale != 1f) put("s", displayScale.toDouble())
        if (wrap != MicroblogImageWrap.FULL) put("w", wrap.key)
    }

    companion object {
        fun fromJson(j: JSONObject): MicroblogImage? {
            val name = j.optString("n").trim().ifEmpty { j.optString("fileName").trim() }
            if (name.isEmpty()) return null
            val scale = j.optDouble("s", j.optDouble("displayScale", 1.0)).toFloat()
            return MicroblogImage(
                fileName = name,
                displayScale = scale.coerceIn(0.25f, 1f),
                wrap = MicroblogImageWrap.fromKey(j.optString("w")),
            )
        }
    }
}

data class MicroblogPost(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val spans: List<MicroblogSpan> = emptyList(),
    val images: List<MicroblogImage> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    val imageFileNames: List<String> get() = images.map { it.fileName }

    fun previewText(maxChars: Int = 140): String {
        val head = title.trim()
        if (head.isNotEmpty()) {
            return if (head.length <= maxChars) head else head.take(maxChars - 1) + "…"
        }
        val one = body.trim().replace(Regex("\\s+"), " ")
        if (one.isEmpty()) {
            return if (images.isNotEmpty()) "Изображение" else "Пустой пост"
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

fun imageNamesToJson(names: List<String>): String = imagesToJson(names.map { MicroblogImage(it) })

fun imagesToJson(images: List<MicroblogImage>): String {
    val arr = JSONArray()
    images.forEach { arr.put(it.toJson()) }
    return arr.toString()
}

fun imageNamesFromJson(raw: String): List<String> = imagesFromJson(raw).map { it.fileName }

fun imagesFromJson(raw: String): List<MicroblogImage> {
    if (raw.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            when (val item = arr.get(i)) {
                is String -> item.trim().takeIf { it.isNotEmpty() }?.let { MicroblogImage(it) }
                is JSONObject -> MicroblogImage.fromJson(item)
                else -> null
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
