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

/** Якорь «после всего текста»: картинка всегда в конце записи. */
const val MICROBLOG_IMAGE_AT_END = Int.MAX_VALUE

data class MicroblogImage(
    val fileName: String,
    val displayScale: Float = 1f,
    val wrap: MicroblogImageWrap = MicroblogImageWrap.FULL,
    /** Смещение в теле поста: картинка стоит перед символом с этим индексом. [MICROBLOG_IMAGE_AT_END] — в конце. */
    val insertAt: Int = MICROBLOG_IMAGE_AT_END,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("n", fileName)
        if (displayScale != 1f) put("s", displayScale.toDouble())
        if (wrap != MicroblogImageWrap.FULL) put("w", wrap.key)
        put("at", if (insertAt == MICROBLOG_IMAGE_AT_END) -1 else insertAt.coerceAtLeast(0))
    }

    companion object {
        fun fromJson(j: JSONObject): MicroblogImage? {
            val name = j.optString("n").trim().ifEmpty { j.optString("fileName").trim() }
            if (name.isEmpty()) return null
            val scale = j.optDouble("s", j.optDouble("displayScale", 1.0)).toFloat()
            val wrap = MicroblogImageWrap.fromKey(j.optString("w"))
            val insertAt = if (j.has("at")) {
                val raw = j.optInt("at", -1)
                if (raw < 0) MICROBLOG_IMAGE_AT_END else raw
            } else {
                if (wrap != MicroblogImageWrap.FULL) 0 else MICROBLOG_IMAGE_AT_END
            }
            return MicroblogImage(
                fileName = name,
                displayScale = scale.coerceIn(0.25f, 1f),
                wrap = wrap,
                insertAt = insertAt,
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

fun microblogResolvedInsertAt(insertAt: Int, textLength: Int): Int =
    if (insertAt == MICROBLOG_IMAGE_AT_END) textLength else insertAt.coerceIn(0, textLength)

/** Границы «до/после абзаца»: начало записи, начало каждого следующего абзаца, конец. */
fun microblogParagraphSlots(text: String): List<Int> {
    if (text.isEmpty()) return listOf(0)
    val slots = mutableListOf(0)
    var paraStart = 0
    var i = 0
    while (i <= text.length) {
        if (i == text.length || text[i] == '\n') {
            if (paraStart < i && paraStart != 0) slots.add(paraStart)
            paraStart = i + 1
        }
        i++
    }
    slots.add(text.length)
    return slots.distinct()
}

fun microblogSnapInsertAt(text: String, cursor: Int): Int {
    val slots = microblogParagraphSlots(text)
    val c = cursor.coerceIn(0, text.length)
    val slot = slots.minByOrNull { kotlin.math.abs(it - c) } ?: text.length
    return if (slot >= text.length) MICROBLOG_IMAGE_AT_END else slot
}

fun microblogSlotIndex(text: String, insertAt: Int): Int {
    val slots = microblogParagraphSlots(text)
    val at = microblogResolvedInsertAt(insertAt, text.length)
    return slots.indexOfLast { it <= at }.coerceIn(0, slots.lastIndex)
}

fun microblogMoveInsertAt(text: String, insertAt: Int, delta: Int): Int {
    val slots = microblogParagraphSlots(text)
    val next = (microblogSlotIndex(text, insertAt) + delta).coerceIn(0, slots.lastIndex)
    val slot = slots[next]
    return if (slot >= text.length) MICROBLOG_IMAGE_AT_END else slot
}

fun microblogCanMoveInsertAt(text: String, insertAt: Int, delta: Int): Boolean {
    if (text.isBlank() || delta == 0) return false
    val slots = microblogParagraphSlots(text)
    val idx = microblogSlotIndex(text, insertAt)
    val next = idx + delta
    return next in slots.indices && next != idx
}

fun microblogDescribeInsertAt(text: String, insertAt: Int): String {
    if (text.isBlank()) return "под текстом — сначала напишите абзацы, затем сдвиньте фото стрелками."
    val slots = microblogParagraphSlots(text)
    val idx = microblogSlotIndex(text, insertAt)
    return when {
        idx <= 0 -> "до первого абзаца"
        idx >= slots.lastIndex -> "после последнего абзаца"
        else -> "после ${idx}-го абзаца, перед ${idx + 1}-м"
    }
}

fun adjustMicroblogImageAnchors(
    images: List<MicroblogImage>,
    changePos: Int,
    diff: Int,
    newTextLength: Int,
): List<MicroblogImage> {
    if (diff == 0) return images
    return images.map { img ->
        val at = img.insertAt
        if (at == MICROBLOG_IMAGE_AT_END) return@map img
        var newAt = if (diff > 0) {
            if (at > changePos) at + diff else at
        } else {
            val delEnd = changePos - diff
            when {
                at <= changePos -> at
                at >= delEnd -> at + diff
                else -> changePos
            }
        }
        newAt = newAt.coerceAtLeast(0)
        if (newAt >= newTextLength) {
            img.copy(insertAt = MICROBLOG_IMAGE_AT_END)
        } else if (newAt != at) {
            img.copy(insertAt = newAt)
        } else {
            img
        }
    }
}
